package com.tenantwall.agent;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SmartSqlModifier}.
 * Covers: null/empty handling, routing (local vs python), caching, counters.
 */
class SmartSqlModifierTest {

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
    // Null / empty inputs — return original SQL unchanged
    // ═══════════════════════════════════════════════════════════════

    @Test
    void nullSql_returnsNull() {
        assertThat(SmartSqlModifier.modify(null, "tenant_id", "T1")).isNull();
    }

    @Test
    void nullColumn_returnsSqlUnchanged() {
        String sql = "SELECT * FROM users";
        assertThat(SmartSqlModifier.modify(sql, null, "T1")).isEqualTo(sql);
    }

    @Test
    void nullValue_returnsSqlUnchanged() {
        String sql = "SELECT * FROM users";
        assertThat(SmartSqlModifier.modify(sql, "tenant_id", null)).isEqualTo(sql);
    }

    @Test
    void emptyColumn_returnsSqlUnchanged() {
        String sql = "SELECT * FROM users";
        assertThat(SmartSqlModifier.modify(sql, "", "T1")).isEqualTo(sql);
    }

    @Test
    void emptyValue_returnsSqlUnchanged() {
        String sql = "SELECT * FROM users";
        assertThat(SmartSqlModifier.modify(sql, "tenant_id", "")).isEqualTo(sql);
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
}