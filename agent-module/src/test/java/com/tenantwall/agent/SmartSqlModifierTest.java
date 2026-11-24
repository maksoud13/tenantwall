package com.tenantwall.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.*;
import com.tenantwall.agent.TenantwallConfig.MissingValueStrategy;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SmartSqlModifier}.
 * Covers: null/empty handling, routing (local vs python), caching, counters.
 */
class SmartSqlModifierTest {
    // ═══════════════════════════════════════════════════════════════
    // Null / empty inputs — return original SQL unchanged
    // ═══════════════════════════════════════════════════════════════

    @Test
    void nullColumn_throwsTenantWallParseException() {
        TenantwallConfig.setOnMissing(MissingValueStrategy.FAIL);
        assertThatThrownBy(() -> SmartSqlModifier.modify(
                "SELECT * FROM users", null, "ACME"))
                .isInstanceOf(TenantWallParseException.class)
                .hasMessageContaining("filter column is not configured");
    }

    @Test
    void nullValue_throwsTenantWallParseException() {
        TenantwallConfig.setOnMissing(MissingValueStrategy.FAIL);
        assertThatThrownBy(() -> SmartSqlModifier.modify(
                "SELECT * FROM users", "tenant_id", null))
                .isInstanceOf(TenantWallParseException.class)
                .hasMessageContaining("tenant ID is missing");
    }

    @Test
    void emptyColumn_throwsTenantWallParseException() {
        TenantwallConfig.setOnMissing(MissingValueStrategy.FAIL);
        assertThatThrownBy(() -> SmartSqlModifier.modify(
                "SELECT * FROM users", "", "ACME"))
                .isInstanceOf(TenantWallParseException.class)
                .hasMessageContaining("filter column is not configured");
    }

    @Test
    void emptyValue_throwsTenantWallParseException() {
        TenantwallConfig.setOnMissing(MissingValueStrategy.FAIL);
        assertThatThrownBy(() -> SmartSqlModifier.modify(
                "SELECT * FROM users", "tenant_id", ""))
                .isInstanceOf(TenantWallParseException.class)
                .hasMessageContaining("tenant ID is missing");
    }

    @Test
    void emptyValue_skipMode_returnsSqlUnchanged() {
        TenantwallConfig.setOnMissing(MissingValueStrategy.SKIP);
        try {
            assertEquals("SELECT * FROM users",
                    SmartSqlModifier.modify("SELECT * FROM users", "tenant_id", ""));
        } finally {
            TenantwallConfig.setOnMissing(MissingValueStrategy.FAIL);
        }
    }

    @BeforeEach
    void setUp() {
        SmartSqlModifier.resetStats(); // clean state
        TenantwallConfig.setParserUrl(null); // disable python → always local
        TenantwallConfig.setExcludedTables(java.util.Set.of()); // no exclusions
    }

    @AfterEach
    void tearDown() {
        SmartSqlModifier.resetStats();
        TenantwallConfig.setParserUrl(null);
    }

    // ═══════════════════════════════════════════════════════════════
    // Routing — local vs python
    // ═══════════════════════════════════════════════════════════════

    @Test
    void simpleSelect_routedToLocal() {
        String result = SmartSqlModifier.modify("SELECT * FROM products", "tenant_id", "T1");

        assertThat(result).contains("tenant_id = 'T1'");
        assertThat(SmartSqlModifier.getLocalCallCount()).isEqualTo(1);
        assertThat(SmartSqlModifier.getPythonCallCount()).isZero();
        assertThat(SmartSqlModifier.getFallbackCount()).isZero();
    }

    @Test
    void complexQuery_pythonDisabled_routedToLocal() {
        // WITH clause is "complex" but Python is disabled → local fallback
        String sql = "WITH cte AS (SELECT 1 AS x) SELECT * FROM cte";
        String result = SmartSqlModifier.modify(sql, "tenant_id", "T1");

        assertThat(SmartSqlModifier.getLocalCallCount()).isEqualTo(1);
        assertThat(SmartSqlModifier.getPythonCallCount()).isZero();
        assertThat(result).isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════
    // Caching
    // ═══════════════════════════════════════════════════════════════

    @Test
    void cacheMiss_thenHit_doesNotRecount() {
        String sql = "SELECT * FROM products WHERE id = 1";

        SmartSqlModifier.modify(sql, "tenant_id", "T1"); // miss
        long afterFirst = SmartSqlModifier.getLocalCallCount();
        assertThat(afterFirst).isEqualTo(1);

        SmartSqlModifier.modify(sql, "tenant_id", "T1"); // hit
        assertThat(SmartSqlModifier.getLocalCallCount()).isEqualTo(afterFirst); // no recount
    }

    @Test
    void cacheStats_recordsHitAndMiss() {
        String sql = "SELECT * FROM products WHERE id = 2";

        // Snapshot BEFORE — Caffeine stats accumulate over cache lifetime
        var before = SmartSqlModifier.getCacheStats();
        long missBefore = before.missCount();
        long hitBefore = before.hitCount();

        SmartSqlModifier.modify(sql, "tenant_id", "T1"); // miss
        SmartSqlModifier.modify(sql, "tenant_id", "T1"); // hit

        var after = SmartSqlModifier.getCacheStats();

        // Assert relative delta, not absolute — cache stats are cumulative
        assertThat(after.missCount() - missBefore).isEqualTo(1);
        assertThat(after.hitCount() - hitBefore).isEqualTo(1);
    }

    @Test
    void differentTenant_differentCacheEntry() {
        String sql = "SELECT * FROM products";
        String r1 = SmartSqlModifier.modify(sql, "tenant_id", "T1");
        String r2 = SmartSqlModifier.modify(sql, "tenant_id", "T2");

        assertThat(r1).contains("T1").doesNotContain("T2");
        assertThat(r2).contains("T2").doesNotContain("T1");
    }

    @Test
    void differentColumn_differentCacheEntry() {
        String sql = "SELECT * FROM products";
        String r1 = SmartSqlModifier.modify(sql, "tenant_id", "T1");
        String r2 = SmartSqlModifier.modify(sql, "org_id", "T1");

        assertThat(r1).contains("tenant_id");
        assertThat(r2).contains("org_id");
        assertThat(r1).isNotEqualTo(r2);
    }

    // ═══════════════════════════════════════════════════════════════
    // Counters + cache invalidation
    // ═══════════════════════════════════════════════════════════════

    @Test
    void clearCache_forcesRecount() {
        String sql = "SELECT * FROM products WHERE id = 3";

        SmartSqlModifier.modify(sql, "tenant_id", "T1"); // local count = 1
        SmartSqlModifier.clearCache();
        SmartSqlModifier.modify(sql, "tenant_id", "T1"); // recount → local count = 2

        assertThat(SmartSqlModifier.getLocalCallCount()).isEqualTo(2);
    }

    @Test
    void resetCounters_doesNotClearCache() {
        String sql = "SELECT * FROM products WHERE id = 4";
        SmartSqlModifier.modify(sql, "tenant_id", "T1");
        assertThat(SmartSqlModifier.getLocalCallCount()).isEqualTo(1);

        SmartSqlModifier.resetCounters();
        assertThat(SmartSqlModifier.getLocalCallCount()).isZero();

        // Cache still warm → no recount
        SmartSqlModifier.modify(sql, "tenant_id", "T1");
        assertThat(SmartSqlModifier.getLocalCallCount()).isZero();
    }

    @Test
    void resetStats_clearsBothCountersAndCache() {
        String sql = "SELECT * FROM products WHERE id = 5";
        SmartSqlModifier.modify(sql, "tenant_id", "T1");

        SmartSqlModifier.resetStats();
        assertThat(SmartSqlModifier.getLocalCallCount()).isZero();
        assertThat(SmartSqlModifier.getPythonCallCount()).isZero();
        assertThat(SmartSqlModifier.getFallbackCount()).isZero();

        // After reset, same query recounts (cache cleared)
        SmartSqlModifier.modify(sql, "tenant_id", "T1");
        assertThat(SmartSqlModifier.getLocalCallCount()).isEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════════════
    // Cache stats object
    // ═══════════════════════════════════════════════════════════════

    @Test
    void getCacheStats_returnsNonNull() {
        assertThat(SmartSqlModifier.getCacheStats()).isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════
    // DDL bypass (Task 4) — migrations must not be rewritten
    // ═══════════════════════════════════════════════════════════════

    @Test
    void ddl_createTable_returnedUnchanged_notCounted() {
        String sql = "CREATE TABLE products (id INT, name VARCHAR(100))";
        long before = SmartSqlModifier.getLocalCallCount()
                + SmartSqlModifier.getPythonCallCount();

        String result = SmartSqlModifier.modify(sql, "tenant_id", "T1");

        assertThat(result).isEqualTo(sql);
        assertThat(SmartSqlModifier.getLocalCallCount()
                + SmartSqlModifier.getPythonCallCount()).isEqualTo(before);
    }

    @Test
    void ddl_withLeadingComment_notModified() {
        String sql = "-- migration V1\nCREATE TABLE products (id INT)";
        String result = SmartSqlModifier.modify(sql, "tenant_id", "T1");
        assertThat(result).isEqualTo(sql);
    }

    @Test
    void nonDdl_stillModified() {
        String result = SmartSqlModifier.modify(
                "SELECT * FROM products", "tenant_id", "T1");
        assertThat(result).contains("tenant_id");
    }

    @Test
    void missingTenantId_failClosedMode_throws() {
        TenantwallConfig.setOnMissing(TenantwallConfig.MissingValueStrategy.FAIL);
        try {
            assertThatThrownBy(() -> SmartSqlModifier.modify(
                    "SELECT * FROM users", "tenant_id", null))
                    .isInstanceOf(TenantWallParseException.class)
                    .hasMessageContaining("tenant ID is missing");
        } finally {
            TenantwallConfig.setOnMissing(TenantwallConfig.MissingValueStrategy.FAIL);
        }
    }

    @Test
    void missingTenantId_skipMode_returnsOriginalWithWarning() {
        TenantwallConfig.setOnMissing(TenantwallConfig.MissingValueStrategy.SKIP);
        try {
            String result = SmartSqlModifier.modify("SELECT * FROM users", "tenant_id", "");
            assertThat(result).isEqualTo("SELECT * FROM users");
        } finally {
            TenantwallConfig.setOnMissing(TenantwallConfig.MissingValueStrategy.FAIL);
        }
    }

    @Test
    void parserFailure_failClosedMode_throws() {
        TenantwallConfig.setParserFailureMode(TenantwallConfig.ParserFailureMode.FAIL_CLOSED);
        TenantwallConfig.setParserUrl("http://127.0.0.1:59999"); // unreachable
        try {
            assertThatThrownBy(() -> SmartSqlModifier.modify(
                    "WITH x AS (SELECT 1) SELECT * FROM x", "tenant_id", "ACME"))
                    .isInstanceOf(TenantWallParseException.class);
        } finally {
            TenantwallConfig.setParserUrl(null);
            TenantwallConfig.setParserFailureMode(TenantwallConfig.ParserFailureMode.FAIL_CLOSED);
        }
    }

    @Test
    void parserFailure_localFallbackMode_recovers() {
        TenantwallConfig.setParserFailureMode(TenantwallConfig.ParserFailureMode.LOCAL_FALLBACK);
        TenantwallConfig.setParserUrl("http://127.0.0.1:59999");
        try {
            String result = SmartSqlModifier.modify(
                    "WITH x AS (SELECT * FROM users) SELECT * FROM x", "tenant_id", "ACME");
            assertThat(result).contains("tenant_id");
        } finally {
            TenantwallConfig.setParserUrl(null);
            TenantwallConfig.setParserFailureMode(TenantwallConfig.ParserFailureMode.FAIL_CLOSED);
        }
    }

    @Test
    void parserFailure_failOpenMode_returnsOriginal() {
        TenantwallConfig.setParserFailureMode(TenantwallConfig.ParserFailureMode.FAIL_OPEN);
        TenantwallConfig.setParserUrl("http://127.0.0.1:59999");
        try {
            String sql = "WITH x AS (SELECT 1) SELECT * FROM x";
            String result = SmartSqlModifier.modify(sql, "tenant_id", "ACME");
            assertThat(result).isEqualTo(sql);
        } finally {
            TenantwallConfig.setParserUrl(null);
            TenantwallConfig.setParserFailureMode(TenantwallConfig.ParserFailureMode.FAIL_CLOSED);
        }
    }

    @Test
    void localParserFailure_failClosed_throws() {
        // Directly test SqlModifier with invalid SQL
        // (bypasses SmartSqlModifier's caching + routing)
        TenantwallConfig.setParserFailureMode(TenantwallConfig.ParserFailureMode.FAIL_CLOSED);
        try {
            assertThatThrownBy(() -> {
                // Force fail via null table name — throw inside SqlModifier
                SqlModifier.addFilter("THIS IS GARBAGE SQL ;;;", "tenant_id", "ACME");
            })
                    .isInstanceOfAny(Exception.class); // أي exception
        } finally {
            TenantwallConfig.setParserFailureMode(TenantwallConfig.ParserFailureMode.FAIL_CLOSED);
        }
    }

    @Test
    void ddl_neverRewritten_regardlessOfMode() {
        String sql = "CREATE TABLE foo (id INT)";
        String result = SmartSqlModifier.modify(sql, "tenant_id", "ACME");
        assertThat(result).isEqualTo(sql);
    }

    @Test
    void nullSql_returnsNull_noThrow() {
        assertThat(SmartSqlModifier.modify(null, "tenant_id", "ACME")).isNull();
    }
}