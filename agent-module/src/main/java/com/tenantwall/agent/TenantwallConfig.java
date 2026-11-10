package com.tenantwall.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Central configuration for the TenantWall agent.
 *
 * <p>
 * Sources (highest priority first):
 * <ol>
 * <li>Agent args: {@code -javaagent:agent.jar=/path/config.properties}</li>
 * <li>System property: {@code -Dtenantwall.config=/path/config.properties}</li>
 * <li>System properties: {@code -Dtenantwall.filter.column=org_id}</li>
 * <li>Environment variables: {@code TENANTWALL_FILTER_COLUMN=org_id}</li>
 * <li>Classpath: {@code tenantwall.properties}</li>
 * <li>Defaults</li>
 * </ol>
 *
 * <p>
 * All fields are {@code volatile} so the user can override programmatically
 * at runtime (e.g., blue/green parser URL changes).
 */
public final class TenantwallConfig {

    // ═══ Filter ═══
    private static volatile boolean enabled = true;
    private static volatile String filterColumn = "tenant_id";
    private static volatile String defaultValue = null;
    private static volatile MissingValueStrategy onMissing = MissingValueStrategy.SKIP;
    private static volatile Set<String> excludedTables = Set.of();

    // ═══ Parser ═══
    private static volatile String parserUrl = null;
    private static volatile String dialect = "mysql";

    // ═══ INSERT ═══
    private static volatile InsertStrategy insertStrategy = InsertStrategy.SKIP;

    // ═══ Tenant Resolution: JWT (NEW) ═══
    private static volatile String tenantJwtClaim = "tenant_id";
    private static volatile String tenantJwtHeader = "Authorization";
    private static volatile String tenantJwtPrefix = "Bearer ";

    // ═══ Schema ═══
    private static final Map<String, List<String>> schemas = new HashMap<>();

    public enum MissingValueStrategy {
        SKIP, FAIL
    }

    public enum InsertStrategy {
        SKIP, APPEND, FAIL
    }

    // ═══ Tenant Resolution (NEW — for auto-config) ═══
    private static volatile String tenantStrategy = "header";
    private static volatile String tenantHeaderName = "X-Tenant-Id";
    private static volatile String tenantFixedValue = null;
    private static volatile String tenantSessionKey = "TENANT_ID";
    private static volatile String tenantCookieName = "tenant_id";

    private TenantwallConfig() {
    }

    // ═══════════════════════════════════════════════════════════════
    // Getters
    // ═══════════════════════════════════════════════════════════════

    public static boolean isEnabled() {
        return enabled;
    }

    public static String getFilterColumn() {
        return filterColumn;
    }

    public static String getDefaultValue() {
        return defaultValue;
    }

    public static MissingValueStrategy getOnMissing() {
        return onMissing;
    }

    public static Set<String> getExcludedTables() {
        return Set.copyOf(excludedTables);
    }

    public static String getParserUrl() {
        return parserUrl;
    }

    public static boolean isParserEnabled() {
        return parserUrl != null && !parserUrl.isBlank();
    }

    public static String getDialect() {
        return dialect;
    }

    public static InsertStrategy getInsertStrategy() {
        return insertStrategy;
    }

    public static List<String> getSchema(String table) {
        if (table == null)
            return null;
        return schemas.get(table.toLowerCase(Locale.ROOT));
    }

    public static String getTenantJwtClaim() {
        return tenantJwtClaim;
    }

    public static String getTenantJwtHeader() {
        return tenantJwtHeader;
    }

    public static String getTenantJwtPrefix() {
        return tenantJwtPrefix;
    }

    // ═══ Tenant Resolution Getters (NEW) ═══
    public static String getTenantStrategy() {
        return tenantStrategy;
    }

    public static String getTenantHeaderName() {
        return tenantHeaderName;
    }

    public static String getTenantFixedValue() {
        return tenantFixedValue;
    }

    public static String getTenantSessionKey() {
        return tenantSessionKey;
    }

    public static String getTenantCookieName() {
        return tenantCookieName;
    }
    // ═══════════════════════════════════════════════════════════════
    // Programmatic setters (runtime overrides)
    // ═══════════════════════════════════════════════════════════════

    public static void setEnabled(boolean v) {
        enabled = v;
    }

    public static void setFilterColumn(String v) {
        filterColumn = v;
        SmartSqlModifier.clearCache();
    }

    public static void setDefaultValue(String v) {
        defaultValue = v;
    }

    public static void setOnMissing(MissingValueStrategy v) {
        onMissing = v;
    }

    public static void setExcludedTables(Collection<String> v) {
        Set<String> normalized = new java.util.HashSet<>();
        for (String s : v) {
            if (s != null)
                normalized.add(s.toLowerCase(java.util.Locale.ROOT));
        }
        excludedTables = Set.copyOf(normalized);
        SmartSqlModifier.clearCache(); // ← NEW
    }

    public static void setParserUrl(String v) {
        parserUrl = v;
        System.out.println("[TenantWall] Parser URL set to: "
                + (v == null ? "DISABLED" : v));
    }

    public static void setDialect(String v) {
        dialect = v;
    }

    public static void setInsertStrategy(InsertStrategy v) {
        insertStrategy = v;
    }

    public static void setSchema(String table, List<String> columns) {
        schemas.put(table.toLowerCase(Locale.ROOT), List.copyOf(columns));
    }

    // ═══════════════════════════════════════════════════════════════
    // Initialization (called once from premain)
    // ═══════════════════════════════════════════════════════════════

    static synchronized void initialize(String agentArgs) {
        Properties merged = new Properties();

        // 1) Classpath (lowest priority — may not be visible in fat jar)
        loadClasspath(merged);

        // 2) Env vars
        loadEnv(merged);

        // 3) System properties
        loadSysProps(merged);

        // 4) Config file (path from -D or agent arg — highest file priority)
        loadConfigFile(merged, agentArgs);

        // 5) Apply
        applyAll(merged);

        // 6) Validate + warn ← السطرين دول هما اللي ناقصين
        validate();
        warnConflictingStrategies(merged);

        System.out.println("[TenantWall] Config: enabled=" + enabled
                + ", column=" + filterColumn
                + ", parser=" + (isParserEnabled() ? parserUrl : "disabled")
                + ", excluded=" + excludedTables);
    }

    // ─── Sources ───

    private static void loadClasspath(Properties p) {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("tenantwall.properties")) {
            if (in != null) {
                p.load(in);
                System.out.println("[TenantWall] Loaded tenantwall.properties from classpath");
            }
        } catch (IOException ignore) {
            // Classpath resource not found or unreadable — proceed with other sources
        }
    }

    private static void loadEnv(Properties p) {
        System.getenv().forEach((k, v) -> {
            if (k.startsWith("TENANTWALL_") && !k.equals("TENANTWALL_CONFIG")) {
                p.setProperty(toDotKey(k), v);
            }
        });
    }

    private static void loadSysProps(Properties p) {
        System.getProperties().forEach((k, v) -> {
            String key = k.toString();
            if (key.startsWith("tenantwall.") && !key.equals("tenantwall.config")) {
                p.setProperty(key, v.toString());
            }
        });
    }

    private static void loadConfigFile(Properties p, String agentArgs) {
        String path = System.getProperty("tenantwall.config");
        if (path == null && agentArgs != null && !agentArgs.isBlank()) {
            // Support -javaagent:agent.jar=key=val;key2=val2
            if (agentArgs.contains("=")) {
                for (String pair : agentArgs.split("[;,]")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        p.setProperty(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                    }
                }
                return;
            }
            path = agentArgs.trim();
        }
        if (path == null)
            return;

        try (InputStream in = Files.newInputStream(Path.of(path))) {
            p.load(in);
            System.out.println("[TenantWall] Loaded config file: " + path);
        } catch (Exception e) {
            System.err.println("[TenantWall] Cannot load config file " + path + ": " + e.getMessage());
        }
    }

    // ─── Apply ───

    private static void applyAll(Properties p) {
        enabled = getBool(p, "tenantwall.enabled", enabled);
        filterColumn = getStr(p, "tenantwall.filter.column", filterColumn);
        defaultValue = getStr(p, "tenantwall.filter.defaultValue", defaultValue);
        onMissing = parseMissing(p.getProperty("tenantwall.filter.onMissing", onMissing.name()));
        excludedTables = parseCsv(getStr(p, "tenantwall.filter.excludedTables", null));

        parserUrl = getStr(p, "tenantwall.parser.url", parserUrl);
        dialect = getStr(p, "tenantwall.parser.dialect", dialect);

        insertStrategy = parseInsert(p.getProperty("tenantwall.insert.strategy", insertStrategy.name()));
        // ═══ Tenant Resolution (NEW) ═══
        tenantHeaderName = getStr(p, "tenantwall.tenant.headerName", tenantHeaderName);
        tenantFixedValue = getStr(p, "tenantwall.tenant.fixedValue", tenantFixedValue);
        tenantSessionKey = getStr(p, "tenantwall.tenant.sessionKey", tenantSessionKey);
        tenantCookieName = getStr(p, "tenantwall.tenant.cookieName", tenantCookieName);
        tenantStrategy = getStr(p, "tenantwall.tenant.strategy", tenantStrategy).trim().toLowerCase(Locale.ROOT);
        tenantJwtClaim = getStr(p, "tenantwall.tenant.jwtClaim", tenantJwtClaim);
        tenantJwtHeader = getStr(p, "tenantwall.tenant.jwtHeader", tenantJwtHeader);
        tenantJwtPrefix = getStr(p, "tenantwall.tenant.jwtPrefix", tenantJwtPrefix);

        // Schemas
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("tenantwall.schema.")) {
                String table = key.substring("tenantwall.schema.".length());
                setSchema(table, parseList(p.getProperty(key)));
            }
        }
    }

    private static boolean getBool(Properties p, String k, boolean def) {
        String v = p.getProperty(k);
        return (v == null) ? def : Boolean.parseBoolean(v);
    }

    private static String getStr(Properties p, String k, String def) {
        return p.getProperty(k, def);
    }

    private static Set<String> parseCsv(String v) {
        if (v == null || v.isBlank())
            return Set.of();
        Set<String> out = new CopyOnWriteArraySet<>();
        for (String s : v.split(",")) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty())
                out.add(t);
        }
        return out;
    }

    private static List<String> parseList(String v) {
        List<String> out = new ArrayList<>();
        for (String s : v.split(",")) {
            String t = s.trim();
            if (!t.isEmpty())
                out.add(t);
        }
        return out;
    }

    private static MissingValueStrategy parseMissing(String v) {
        try {
            return MissingValueStrategy.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return MissingValueStrategy.SKIP;
        }
    }

    private static InsertStrategy parseInsert(String v) {
        try {
            return InsertStrategy.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return InsertStrategy.SKIP;
        }
    }

    private static String toDotKey(String envKey) {
        // TENANTWALL_FILTER_COLUMN → tenantwall.filter.column
        return envKey.toLowerCase(Locale.ROOT).replace('_', '.');
    }

    public static void validate() {
        String s = (tenantStrategy == null) ? "" : tenantStrategy.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "header" -> {
                if (isBlank(tenantHeaderName))
                    throw new IllegalStateException(
                            "[TenantWall] strategy=header requires tenantwall.tenant.headerName");
            }
            case "fixed" -> {
                if (isBlank(tenantFixedValue))
                    throw new IllegalStateException(
                            "[TenantWall] strategy=fixed requires tenantwall.tenant.fixedValue");
            }
            case "session" -> {
                if (isBlank(tenantSessionKey))
                    throw new IllegalStateException(
                            "[TenantWall] strategy=session requires tenantwall.tenant.sessionKey");
            }
            case "cookie" -> {
                if (isBlank(tenantCookieName))
                    throw new IllegalStateException(
                            "[TenantWall] strategy=cookie requires tenantwall.tenant.cookieName");
            }
            case "jwt" -> {
                if (isBlank(tenantJwtClaim))
                    throw new IllegalStateException(
                            "[TenantWall] strategy=jwt requires tenantwall.tenant.jwtClaim");
                if (isBlank(tenantJwtHeader))
                    throw new IllegalStateException(
                            "[TenantWall] strategy=jwt requires tenantwall.tenant.jwtHeader");
            }
            default -> throw new IllegalStateException(
                    "[TenantWall] Invalid tenantwall.tenant.strategy='" + tenantStrategy + "'. " +
                            "Must be one of: header, fixed, session, cookie, jwt");
        }
    }

    /**
     * Warns when config keys for a non-selected strategy are also present,
     * to prevent developer confusion ("pick only one").
     */
    private static void warnConflictingStrategies(Properties p) {
        String active = tenantStrategy;
        Map<String, String> keys = Map.of(
                "header", "tenantwall.tenant.headerName",
                "fixed", "tenantwall.tenant.fixedValue",
                "session", "tenantwall.tenant.sessionKey",
                "cookie", "tenantwall.tenant.cookieName",
                "jwt", "tenantwall.tenant.jwtClaim");
        for (Map.Entry<String, String> e : keys.entrySet()) {
            if (!e.getKey().equals(active) && p.getProperty(e.getValue()) != null) {
                System.err.println("[TenantWall] WARNING: strategy=" + active
                        + " but " + e.getValue() + " is also set (ignored). "
                        + "Pick only one strategy.");
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

}