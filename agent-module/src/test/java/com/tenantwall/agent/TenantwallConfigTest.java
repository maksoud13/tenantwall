package com.tenantwall.agent;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TenantwallConfig} — targets ~80% line coverage of the
 * class.
 */
class TenantwallConfigTest {

    private Map<String, String> savedSysProps;

    @TempDir
    Path tempDir;

    // ═══════════════════════════════════════════════════════════════
    // Setup / teardown — isolate global static state + system props
    // ═══════════════════════════════════════════════════════════════

    @BeforeEach
    void saveAndClearSysProps() {
        savedSysProps = new HashMap<>();
        System.getProperties().stringPropertyNames().stream()
                .filter(k -> k.startsWith("tenantwall."))
                .forEach(k -> savedSysProps.put(k, System.getProperty(k)));
        savedSysProps.keySet().forEach(System::clearProperty);
    }

    @AfterEach
    void restoreSysProps() {
        System.getProperties().stringPropertyNames().stream()
                .filter(k -> k.startsWith("tenantwall."))
                .toList()
                .forEach(System::clearProperty);
        savedSysProps.forEach(System::setProperty);
    }

    @BeforeEach
    void resetState() throws Exception {
        TenantwallConfig.setEnabled(true);
        TenantwallConfig.setFilterColumn("tenant_id");
        TenantwallConfig.setDefaultValue(null);
        TenantwallConfig.setOnMissing(TenantwallConfig.MissingValueStrategy.SKIP);
        TenantwallConfig.setExcludedTables(List.of());
        TenantwallConfig.setParserUrl(null);
        TenantwallConfig.setDialect("mysql");
        TenantwallConfig.setParserSecret(null);
        TenantwallConfig.setParserTokenTtlSeconds(60);
        TenantwallConfig.setParserEnforceAuth(false);
        TenantwallConfig.setInsertStrategy(TenantwallConfig.InsertStrategy.SKIP);
        TenantwallConfig.resetTenantResolution();
        clearSchemas();
    }

    private void clearSchemas() throws Exception {
        Field f = TenantwallConfig.class.getDeclaredField("schemas");
        f.setAccessible(true);
        ((Map<?, ?>) f.get(null)).clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // Defaults
    // ═══════════════════════════════════════════════════════════════

    @Test
    void defaultsAreExpected() {
        assertTrue(TenantwallConfig.isEnabled());
        assertEquals("tenant_id", TenantwallConfig.getFilterColumn());
        assertNull(TenantwallConfig.getDefaultValue());
        assertEquals(TenantwallConfig.MissingValueStrategy.SKIP, TenantwallConfig.getOnMissing());
        assertTrue(TenantwallConfig.getExcludedTables().isEmpty());
        assertNull(TenantwallConfig.getParserUrl());
        assertFalse(TenantwallConfig.isParserEnabled());
        assertEquals("mysql", TenantwallConfig.getDialect());
        assertEquals(TenantwallConfig.InsertStrategy.SKIP, TenantwallConfig.getInsertStrategy());
        assertEquals("header", TenantwallConfig.getTenantStrategy());
        assertEquals("X-Tenant-Id", TenantwallConfig.getTenantHeaderName());
        assertNull(TenantwallConfig.getTenantFixedValue());
        assertEquals("TENANT_ID", TenantwallConfig.getTenantSessionKey());
        assertEquals("tenant_id", TenantwallConfig.getTenantCookieName());
        assertEquals("tenant_id", TenantwallConfig.getTenantJwtClaim());
        assertEquals("Authorization", TenantwallConfig.getTenantJwtHeader());
        assertEquals("Bearer ", TenantwallConfig.getTenantJwtPrefix());
        assertNull(TenantwallConfig.getParserSecret());
        assertEquals(60, TenantwallConfig.getParserTokenTtlSeconds());
        assertFalse(TenantwallConfig.isParserEnforceAuth());
    }

    // ═══════════════════════════════════════════════════════════════
    // Setters
    // ═══════════════════════════════════════════════════════════════

    @Test
    void settersRoundTrip() {
        TenantwallConfig.setEnabled(false);
        TenantwallConfig.setFilterColumn("org_id");
        TenantwallConfig.setDefaultValue("T_DEFAULT");
        TenantwallConfig.setOnMissing(TenantwallConfig.MissingValueStrategy.FAIL);
        TenantwallConfig.setInsertStrategy(TenantwallConfig.InsertStrategy.APPEND);
        TenantwallConfig.setDialect("postgres");
        TenantwallConfig.setParserUrl("http://parser:8000");

        assertFalse(TenantwallConfig.isEnabled());
        assertEquals("org_id", TenantwallConfig.getFilterColumn());
        assertEquals("T_DEFAULT", TenantwallConfig.getDefaultValue());
        assertEquals(TenantwallConfig.MissingValueStrategy.FAIL, TenantwallConfig.getOnMissing());
        assertEquals(TenantwallConfig.InsertStrategy.APPEND, TenantwallConfig.getInsertStrategy());
        assertEquals("postgres", TenantwallConfig.getDialect());
        assertTrue(TenantwallConfig.isParserEnabled());
    }

    @Test
    void parserEnabledHandlesNullAndBlank() {
        TenantwallConfig.setParserUrl(null);
        assertFalse(TenantwallConfig.isParserEnabled());
        TenantwallConfig.setParserUrl("");
        assertFalse(TenantwallConfig.isParserEnabled());
        TenantwallConfig.setParserUrl("   ");
        assertFalse(TenantwallConfig.isParserEnabled());
        TenantwallConfig.setParserUrl("http://x");
        assertTrue(TenantwallConfig.isParserEnabled());
    }

    @Test
    void excludedTablesNormalizesAndSkipsNulls() {
        TenantwallConfig.setExcludedTables(Arrays.asList("USERS", "Orders", null, "  "));
        Set<String> ex = TenantwallConfig.getExcludedTables();
        assertEquals(Set.of("users", "orders"), ex);
        assertThrows(UnsupportedOperationException.class, () -> ex.add("x"));
    }

    @Test
    void schemaIsCaseInsensitiveAndImmutable() {
        TenantwallConfig.setSchema("PRODUCTS", List.of("id", "name"));
        assertEquals(List.of("id", "name"), TenantwallConfig.getSchema("products"));
        assertEquals(List.of("id", "name"), TenantwallConfig.getSchema("Products"));
        assertNull(TenantwallConfig.getSchema(null));
        assertNull(TenantwallConfig.getSchema("unknown"));
        assertThrows(UnsupportedOperationException.class,
                () -> TenantwallConfig.getSchema("products").add("x"));
    }

    @Test
    void tenantSettersRoundTrip() {
        TenantwallConfig.setTenantStrategy("  JWT ");
        TenantwallConfig.setTenantHeaderName("X-T");
        TenantwallConfig.setTenantFixedValue("FIXED");
        TenantwallConfig.setTenantSessionKey("SK");
        TenantwallConfig.setTenantCookieName("ck");
        TenantwallConfig.setTenantJwtClaim("org");
        TenantwallConfig.setTenantJwtHeader("X-Auth");
        TenantwallConfig.setTenantJwtPrefix("Token ");

        assertEquals("jwt", TenantwallConfig.getTenantStrategy());
        assertEquals("X-T", TenantwallConfig.getTenantHeaderName());
        assertEquals("FIXED", TenantwallConfig.getTenantFixedValue());
        assertEquals("SK", TenantwallConfig.getTenantSessionKey());
        assertEquals("ck", TenantwallConfig.getTenantCookieName());
        assertEquals("org", TenantwallConfig.getTenantJwtClaim());
        assertEquals("X-Auth", TenantwallConfig.getTenantJwtHeader());
        assertEquals("Token ", TenantwallConfig.getTenantJwtPrefix());
    }

    @Test
    void setTenantStrategyNullFallsBackToHeader() {
        TenantwallConfig.setTenantStrategy("fixed");
        TenantwallConfig.setTenantStrategy(null);
        assertEquals("header", TenantwallConfig.getTenantStrategy());
    }

    @Test
    void parserSecuritySettersRoundTrip() {
        TenantwallConfig.setParserSecret("x".repeat(40));
        TenantwallConfig.setParserTokenTtlSeconds(120);
        TenantwallConfig.setParserEnforceAuth(true);

        assertEquals("x".repeat(40), TenantwallConfig.getParserSecret());
        assertEquals(120, TenantwallConfig.getParserTokenTtlSeconds());
        assertTrue(TenantwallConfig.isParserEnforceAuth());
    }

    @Test
    void resetTenantResolutionRestoresDefaults() {
        TenantwallConfig.setTenantStrategy("jwt");
        TenantwallConfig.setTenantHeaderName("X-Custom");
        TenantwallConfig.setTenantFixedValue("v");
        TenantwallConfig.setTenantSessionKey("k");
        TenantwallConfig.setTenantCookieName("c");
        TenantwallConfig.setTenantJwtClaim("cl");
        TenantwallConfig.setTenantJwtHeader("h");
        TenantwallConfig.setTenantJwtPrefix("p");

        TenantwallConfig.resetTenantResolution();

        assertEquals("header", TenantwallConfig.getTenantStrategy());
        assertEquals("X-Tenant-Id", TenantwallConfig.getTenantHeaderName());
        assertNull(TenantwallConfig.getTenantFixedValue());
        assertEquals("TENANT_ID", TenantwallConfig.getTenantSessionKey());
        assertEquals("tenant_id", TenantwallConfig.getTenantCookieName());
        assertEquals("tenant_id", TenantwallConfig.getTenantJwtClaim());
        assertEquals("Authorization", TenantwallConfig.getTenantJwtHeader());
        assertEquals("Bearer ", TenantwallConfig.getTenantJwtPrefix());
    }

    // ═══════════════════════════════════════════════════════════════
    // initialize() — sources and precedence
    // ═══════════════════════════════════════════════════════════════

    @Test
    void initializeReadsFromSystemProperties() {
        System.setProperty("tenantwall.filter.column", "org_id");
        System.setProperty("tenantwall.enabled", "false");
        System.setProperty("tenantwall.parser.url", "http://p:8000");
        System.setProperty("tenantwall.filter.onMissing", "FAIL");
        System.setProperty("tenantwall.insert.strategy", "APPEND");
        System.setProperty("tenantwall.tenant.strategy", "fixed");
        System.setProperty("tenantwall.tenant.fixedValue", "T1");
        System.setProperty("tenantwall.schema.products", "id, name, tenant_id");

        TenantwallConfig.initialize(null);

        assertEquals("org_id", TenantwallConfig.getFilterColumn());
        assertFalse(TenantwallConfig.isEnabled());
        assertEquals("http://p:8000", TenantwallConfig.getParserUrl());
        assertEquals(TenantwallConfig.MissingValueStrategy.FAIL, TenantwallConfig.getOnMissing());
        assertEquals(TenantwallConfig.InsertStrategy.APPEND, TenantwallConfig.getInsertStrategy());
        assertEquals("fixed", TenantwallConfig.getTenantStrategy());
        assertEquals("T1", TenantwallConfig.getTenantFixedValue());
        assertEquals(List.of("id", "name", "tenant_id"),
                TenantwallConfig.getSchema("products"));
    }

    @Test
    void initializeReadsFromConfigFileAndOverridesSystemProperties() throws Exception {
        Path cfg = tempDir.resolve("cfg.properties");
        try (OutputStream out = Files.newOutputStream(cfg)) {
            Properties p = new Properties();
            p.setProperty("tenantwall.filter.column", "from_file");
            p.setProperty("tenantwall.enabled", "false");
            p.store(out, null);
        }
        System.setProperty("tenantwall.config", cfg.toString());
        System.setProperty("tenantwall.filter.column", "from_sysprop");

        TenantwallConfig.initialize(null);

        // File has higher priority than sys props
        assertEquals("from_file", TenantwallConfig.getFilterColumn());
        assertFalse(TenantwallConfig.isEnabled());
    }

    @Test
    void initializeReadsAgentArgAsFilePath() throws Exception {
        Path cfg = tempDir.resolve("agent.properties");
        try (OutputStream out = Files.newOutputStream(cfg)) {
            Properties p = new Properties();
            p.setProperty("tenantwall.filter.column", "from_agent_path");
            p.store(out, null);
        }

        TenantwallConfig.initialize(cfg.toString());

        assertEquals("from_agent_path", TenantwallConfig.getFilterColumn());
    }

    @Test
    void initializeReadsAgentArgKeyValuePairs() {
        TenantwallConfig.initialize(
                "tenantwall.filter.column=from_agent_kv;tenantwall.enabled=false,tenantwall.parser.dialect=postgres");

        assertEquals("from_agent_kv", TenantwallConfig.getFilterColumn());
        assertFalse(TenantwallConfig.isEnabled());
        assertEquals("postgres", TenantwallConfig.getDialect());
    }

    @Test
    void initializeIgnoresAgentArgsWhenConfigSysPropIsSet() throws Exception {
        Path cfg = tempDir.resolve("p.properties");
        try (OutputStream out = Files.newOutputStream(cfg)) {
            Properties p = new Properties();
            p.setProperty("tenantwall.filter.column", "from_file");
            p.store(out, null);
        }
        System.setProperty("tenantwall.config", cfg.toString());

        TenantwallConfig.initialize("tenantwall.filter.column=ignored");

        assertEquals("from_file", TenantwallConfig.getFilterColumn());
    }

    @Test
    void initializeHandlesMissingConfigFileGracefully() {
        System.setProperty("tenantwall.config", tempDir.resolve("nope.properties").toString());
        assertDoesNotThrow(() -> TenantwallConfig.initialize(null));
        // default kept
        assertEquals("tenant_id", TenantwallConfig.getFilterColumn());
    }

    @Test
    void initializeReadsSchemaProperties() {
        System.setProperty("tenantwall.schema.orders", "id, total");
        System.setProperty("tenantwall.schema.users", "id,email");

        TenantwallConfig.initialize(null);

        assertEquals(List.of("id", "total"), TenantwallConfig.getSchema("ORDERS"));
        assertEquals(List.of("id", "email"), TenantwallConfig.getSchema("users"));
    }

    @Test
    void initializeAppliesInvalidEnumValuesAsDefaults() {
        System.setProperty("tenantwall.filter.onMissing", "NONSENSE");
        System.setProperty("tenantwall.insert.strategy", "NONSENSE");

        TenantwallConfig.initialize(null);

        assertEquals(TenantwallConfig.MissingValueStrategy.SKIP, TenantwallConfig.getOnMissing());
        assertEquals(TenantwallConfig.InsertStrategy.SKIP, TenantwallConfig.getInsertStrategy());
    }

    @Test
    void initializeReadsIntTtlAndFallsBackOnInvalidValue() {
        System.setProperty("tenantwall.parser.token.ttlSeconds", "not-an-int");
        TenantwallConfig.initialize(null);
        assertEquals(60, TenantwallConfig.getParserTokenTtlSeconds());
    }

    @Test
    void initializeWithConflictingStrategyKeysDoesNotThrow() {
        System.setProperty("tenantwall.tenant.strategy", "header");
        System.setProperty("tenantwall.tenant.headerName", "X-Tenant-Id");
        System.setProperty("tenantwall.tenant.fixedValue", "leftover"); // conflicting
        assertDoesNotThrow(() -> TenantwallConfig.initialize(null));
    }

    // ═══════════════════════════════════════════════════════════════
    // loadClasspath — via custom context class loader
    // ═══════════════════════════════════════════════════════════════

    @Test
    void loadClasspathLoadsTenantwallProperties() throws Exception {
        Path props = tempDir.resolve("tenantwall.properties");
        try (OutputStream out = Files.newOutputStream(props)) {
            Properties p = new Properties();
            p.setProperty("tenantwall.filter.column", "from_classpath");
            p.store(out, null);
        }

        URL url = tempDir.toUri().toURL();
        try (URLClassLoader cl = new URLClassLoader(new URL[]{url},
                Thread.currentThread().getContextClassLoader())) {
            Thread t = Thread.currentThread();
            ClassLoader prev = t.getContextClassLoader();
            try {
                t.setContextClassLoader(cl);
                Method m = TenantwallConfig.class.getDeclaredMethod("loadClasspath", Properties.class);
                m.setAccessible(true);
                Properties out = new Properties();
                m.invoke(null, out);
                assertEquals("from_classpath", out.getProperty("tenantwall.filter.column"));
            } finally {
                t.setContextClassLoader(prev);
            }
        }
    }

    @Test
    void loadClasspathNoopWhenResourceAbsent() throws Exception {
        Method m = TenantwallConfig.class.getDeclaredMethod("loadClasspath", Properties.class);
        m.setAccessible(true);
        Properties out = new Properties();
        // Should not throw even if no tenantwall.properties on classpath
        assertDoesNotThrow(() -> m.invoke(null, out));
    }

    // ═══════════════════════════════════════════════════════════════
    // Reflection helpers
    // ═══════════════════════════════════════════════════════════════

    @Test
    void toDotKeyConvertsEnvStyleKeys() throws Exception {
        Method m = TenantwallConfig.class.getDeclaredMethod("toDotKey", String.class);
        m.setAccessible(true);
        assertEquals("tenantwall.filter.column", m.invoke(null, "TENANTWALL_FILTER_COLUMN"));
        assertEquals("tenantwall.parser.url", m.invoke(null, "TENANTWALL_PARSER_URL"));
    }

    @Test
    void loadEnvRunsWithoutThrowing() throws Exception {
        Method m = TenantwallConfig.class.getDeclaredMethod("loadEnv", Properties.class);
        m.setAccessible(true);
        Properties out = new Properties();
        assertDoesNotThrow(() -> m.invoke(null, out));
        // Any TENANTWALL_* env var ends up as dotted key (validated when set)
        // We can only assert on keys if present:
        out.stringPropertyNames().forEach(k ->
                assertTrue(k.startsWith("tenantwall."), "unexpected key: " + k));
    }

    // ═══════════════════════════════════════════════════════════════
    // validate() — happy paths + parser auth
    // ═══════════════════════════════════════════════════════════════

    @ParameterizedTest
    @ValueSource(strings = {"header", "session", "cookie"})
    void validatePassesForValidDefaultStrategies(String strategy) {
        TenantwallConfig.setTenantStrategy(strategy);
        assertDoesNotThrow(TenantwallConfig::validate);
    }

    @Test
    void validateFixedPassesWhenFixedValueSet() {
        TenantwallConfig.setTenantStrategy("fixed");
        TenantwallConfig.setTenantFixedValue("T1");
        assertDoesNotThrow(TenantwallConfig::validate);
    }

    @Test
    void validateJwtPassesWithDefaults() {
        TenantwallConfig.setTenantStrategy("jwt");
        assertDoesNotThrow(TenantwallConfig::validate);
    }

    // ── Failure paths ──

    @Test
    void validateHeaderRequiresHeaderName() {
        TenantwallConfig.setTenantStrategy("header");
        TenantwallConfig.setTenantHeaderName(null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                TenantwallConfig::validate);
        assertTrue(ex.getMessage().contains("headerName"));
    }

    @Test
    void validateFixedRequiresFixedValue() {
        TenantwallConfig.setTenantStrategy("fixed");
        TenantwallConfig.setTenantFixedValue(null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                TenantwallConfig::validate);
        assertTrue(ex.getMessage().contains("fixedValue"));
    }

    @Test
    void validateSessionRequiresSessionKey() {
        TenantwallConfig.setTenantStrategy("session");
        TenantwallConfig.setTenantSessionKey("   ");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                TenantwallConfig::validate);
        assertTrue(ex.getMessage().contains("sessionKey"));
    }

    @Test
    void validateCookieRequiresCookieName() {
        TenantwallConfig.setTenantStrategy("cookie");
        TenantwallConfig.setTenantCookieName("");
        assertThrows(IllegalStateException.class, TenantwallConfig::validate);
    }

    @Test
    void validateJwtRequiresClaim() {
        TenantwallConfig.setTenantStrategy("jwt");
        TenantwallConfig.setTenantJwtClaim(null);
        assertThrows(IllegalStateException.class, TenantwallConfig::validate);
    }

    @Test
    void validateJwtRequiresHeader() {
        TenantwallConfig.setTenantStrategy("jwt");
        TenantwallConfig.setTenantJwtHeader(" ");
        assertThrows(IllegalStateException.class, TenantwallConfig::validate);
    }

    @Test
    void validateRejectsUnknownStrategy() {
        TenantwallConfig.setTenantStrategy("banana");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                TenantwallConfig::validate);
        assertTrue(ex.getMessage().contains("banana"));
    }

    @Test
    void validateParserEnforceAuthRequiresSecret() {
        TenantwallConfig.setParserEnforceAuth(true);
        TenantwallConfig.setParserSecret(null);
        assertThrows(IllegalStateException.class, TenantwallConfig::validate);
    }

    @Test
    void validateParserEnforceAuthRejectsShortSecret() {
        TenantwallConfig.setParserEnforceAuth(true);
        TenantwallConfig.setParserSecret("short");
        assertThrows(IllegalStateException.class, TenantwallConfig::validate);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "301", "1000"})
    void validateParserEnforceAuthRejectsBadTtl(int ttl) {
        TenantwallConfig.setParserEnforceAuth(true);
        TenantwallConfig.setParserSecret("x".repeat(40));
        TenantwallConfig.setParserTokenTtlSeconds(ttl);
        assertThrows(IllegalStateException.class, TenantwallConfig::validate);
    }

    @ParameterizedTest
    @CsvSource({"1", "60", "300"})
    void validateParserEnforceAuthAcceptsValidTtl(int ttl) {
        TenantwallConfig.setParserEnforceAuth(true);
        TenantwallConfig.setParserSecret("x".repeat(40));
        TenantwallConfig.setParserTokenTtlSeconds(ttl);
        assertDoesNotThrow(TenantwallConfig::validate);
    }

    @Test
    void validateParserEnforceAuthFalseSkipsSecretCheck() {
        TenantwallConfig.setParserEnforceAuth(false);
        TenantwallConfig.setParserSecret(null);
        TenantwallConfig.setParserTokenTtlSeconds(0);
        assertDoesNotThrow(TenantwallConfig::validate);
    }
}