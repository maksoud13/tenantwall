package com.tenantwall.agent;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Routes each SQL query to the fastest correct rewriter.
 *
 * <h2>Failure semantics (v1.3.0+)</h2>
 *
 * <p>If a query cannot be safely rewritten, the behavior depends on
 * {@link TenantwallConfig#getParserFailureMode()}:
 * <ul>
 *   <li>{@code FAIL_CLOSED} (default) — throw {@link TenantWallParseException}</li>
 *   <li>{@code LOCAL_FALLBACK} — try JSqlParser; if that also fails, throw</li>
 *   <li>{@code FAIL_OPEN} — return the original SQL (unsafe)</li>
 * </ul>
 */
public class SmartSqlModifier {
    private static final Logger log = LoggerFactory.getLogger("TenantWall");

    private static final Cache<String, String> CACHE = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

    private static long pythonCallCount = 0;
    private static long localCallCount = 0;
    private static long fallbackCount = 0;
    private static long failClosedCount = 0;

    public static String modify(String sql, String column, String value) {
        // ─── Fail-closed validation of inputs ───
        if (sql == null) {
            // No SQL — nothing to rewrite. This is normal for JDBC metadata calls.
            return null;
        }
        if (column == null || column.isEmpty()) {
            throw new TenantWallParseException(
                    "TenantWall: filter column is not configured. "
                    + "Set tenantwall.filter.column.");
        }
        if (value == null || value.isEmpty()) {
            // Tenant ID missing — this is the critical fail-open path.
           TenantwallConfig.MissingValueStrategy strategy = TenantwallConfig.getOnMissing();
            if (strategy == strategy.FAIL) {
                throw new TenantWallParseException(
                        "TenantWall: tenant ID is missing for this request. "
                        + "Query was NOT executed (fail-closed). "
                        + "Check the tenant resolution strategy.");
            }
            log.warn("{} onMissing=SKIP — query runs UNFILTERED (unsafe)", LogTags.MODIFIER);
            return sql;
        }

        // DDL is intentionally untouched (migrations must not be rewritten)
        if (QueryAnalyzer.isDdl(sql)) {
            return sql;
        }

        String cacheKey = sql + "|" + column + "|" + value;
        String cached = CACHE.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        boolean complex = QueryAnalyzer.isComplex(sql);
        boolean pythonEnabled = PythonServiceClient.isEnabled();

        String result;

        if (complex && pythonEnabled) {
            result = tryPythonThenDecide(sql, column, value);
        } else {
            result = tryLocalOrFail(sql, column, value);
            localCallCount++;
        }

        CACHE.put(cacheKey, result);
        return result;
    }

    /**
     * Python path. On failure, applies ParserFailureMode.
     */
    private static String tryPythonThenDecide(String sql, String column, String value) {
        try {
            String result = PythonServiceClient.modify(
                    sql, column, value, TenantwallConfig.getDialect());
            pythonCallCount++;
            return result;
        } catch (PythonServiceClient.PythonServiceException e) {
            fallbackCount++;
            log.warn("{} Python parser failed: {}", LogTags.MODIFIER, e.getMessage());
            return handleParserFailure(sql, column, value, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupt status
            fallbackCount++;
            log.warn("{} Python call interrupted", LogTags.MODIFIER);
            return handleParserFailure(sql, column, value, e);
        }
    }

    /**
     * Local path. Throws on failure — never falls back to the original SQL.
     */
    private static String tryLocalOrFail(String sql, String column, String value) {
        try {
            return SqlModifier.addFilter(sql, column, value);
        } catch (Exception e) {
            log.error("{} Local parser failed: {}", LogTags.MODIFIER, e.getMessage());
            return handleParserFailure(sql, column, value, e);
        }
    }

    /**
     * Applies the configured ParserFailureMode.
     */
    private static String handleParserFailure(String sql, String column,
                                               String value, Exception cause) {
       TenantwallConfig.ParserFailureMode mode = TenantwallConfig.getParserFailureMode();
        switch (mode) {
            case FAIL_CLOSED:
                failClosedCount++;
                throw new TenantWallParseException(
                        "TenantWall: cannot safely rewrite query. "
                        + "Query was NOT executed (fail-closed). "
                        + "Cause: " + cause.getMessage(), cause);

            case LOCAL_FALLBACK:
                try {
                    String local = SqlModifier.addFilter(sql, column, value);
                    localCallCount++;
                    log.info("{} Recovered via local parser after Python failure",
                            LogTags.MODIFIER);
                    return local;
                } catch (Exception localEx) {
                    failClosedCount++;
                    throw new TenantWallParseException(
                            "TenantWall: both Python and local parsers failed. "
                            + "Query was NOT executed (fail-closed).",
                            localEx);
                }

            case FAIL_OPEN:
                failClosedCount++;
                log.error("{} FAIL_OPEN — query runs UNFILTERED. Data-leak risk!",
                        LogTags.MODIFIER);
                return sql;

            default:
                throw new TenantWallParseException(
                        "TenantWall: unknown ParserFailureMode: " + mode, cause);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Stats (unchanged)
    // ═══════════════════════════════════════════════════════════════

    public static CacheStats getCacheStats() { return CACHE.stats(); }
    public static void clearCache() { CACHE.invalidateAll(); }
    public static long getCacheSize() { return CACHE.estimatedSize(); }
    public static long getPythonCallCount() { return pythonCallCount; }
    public static long getLocalCallCount() { return localCallCount; }
    public static long getFallbackCount() { return fallbackCount; }
    public static long getFailClosedCount() { return failClosedCount; }

    public static void resetCounters() {
        pythonCallCount = 0;
        localCallCount = 0;
        fallbackCount = 0;
        failClosedCount = 0;
    }

    public static void resetStats() {
        CACHE.invalidateAll();
        resetCounters();
    }
}