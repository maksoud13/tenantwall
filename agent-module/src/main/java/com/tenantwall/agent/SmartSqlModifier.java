package com.tenantwall.agent;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.util.concurrent.TimeUnit;

/**
 * Smart router that decides how to modify each SQL query:
 * 
 * 1. Check cache first (fastest)
 * 2. If complex → Python service
 * 3. If simple → Local JSqlParser
 * 4. If Python fails → fallback to local (with warning)
 */
public class SmartSqlModifier {

    private static final Cache<String, String> CACHE = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

    // Track how many queries went to Python vs Local
    private static long pythonCallCount = 0;
    private static long localCallCount = 0;
    private static long fallbackCount = 0;

    /**
     * Main entry point. Replaces SqlModifier.addFilter.
     */
    public static String modify(String sql, String column, String value) {
        if (sql == null || column == null || value == null
                || column.isEmpty() || value.isEmpty()) {
            return sql;
        }

        String cacheKey = sql + "|" + column + "|" + value;

        // 1. Check cache
        String cached = CACHE.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. Decide route
        boolean complex = QueryAnalyzer.isComplex(sql);
        String result = null;

        if (complex) {
            result = tryPython(sql, column, value);
            if (result == null) {
                // Python failed → fallback to local
                fallbackCount++;
                System.out.println("⚠️ [SmartModifier] Python failed, falling back to local");
                result = tryLocal(sql, column, value);
            } else {
                pythonCallCount++;
            }
        } else {
            result = tryLocal(sql, column, value);
            localCallCount++;
        }

        if (result == null) {
            result = sql; // Give up, return original
        }

        // 3. Cache result
        if (!result.equals(sql)) {
            CACHE.put(cacheKey, result);
        }

        return result;
    }

    private static String tryPython(String sql, String column, String value) {
        try {
            String dialect = System.getProperty("tenantwall.dialect", "mysql");
            String result = PythonServiceClient.modify(sql, column, value, dialect);
            System.out.println("🐍 [SmartModifier] Processed by Python service");
            return result;
        } catch (PythonServiceClient.PythonServiceException e) {
            System.err.println("❌ [SmartModifier] Python error: " + e.getMessage());
            return null;
        }
    }

    private static String tryLocal(String sql, String column, String value) {
        try {
            String result = SqlModifier.addFilter(sql, column, value);
            System.out.println("⚡ [SmartModifier] Processed locally");
            return result;
        } catch (Exception e) {
            System.err.println("❌ [SmartModifier] Local error: " + e.getMessage());
            return null;
        }
    }

    // =========================================================
    // Stats and monitoring
    // =========================================================

    public static CacheStats getCacheStats() {
        return CACHE.stats();
    }

    public static void clearCache() {
        CACHE.invalidateAll();
    }

    public static long getCacheSize() {
        return CACHE.estimatedSize();
    }

    public static long getPythonCallCount() {
        return pythonCallCount;
    }

    public static long getLocalCallCount() {
        return localCallCount;
    }

    public static long getFallbackCount() {
        return fallbackCount;
    }

    public static void resetStats() {
        CACHE.invalidateAll();
        pythonCallCount = 0;
        localCallCount = 0;
        fallbackCount = 0;
    }
}