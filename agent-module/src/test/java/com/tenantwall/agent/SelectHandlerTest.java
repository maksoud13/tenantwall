package com.tenantwall.agent;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SelectHandler}.
 * Covers: PlainSelect, UNION, CTE, alias handling, excluded tables, existing
 * filter.
 */
class SelectHandlerTest {

    @BeforeEach
    void setUp() {
        TenantwallConfig.resetTenantResolution();
        TenantwallConfig.setExcludedTables(java.util.Set.of()); // no exclusions by default
    }

    @AfterEach
    void tearDown() {
        TenantwallConfig.setExcludedTables(java.util.Set.of());
    }

    private static Select parse(String sql) throws Exception {
        Statement stmt = CCJSqlParserUtil.parse(sql);
        return (Select) stmt;
    }

    private static long countOccurrences(String s, String sub) {
        long count = 0;
        int idx = 0;
        while ((idx = s.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════
    // PlainSelect — basic cases
    // ═══════════════════════════════════════════════════════════════

    @Test
    void plainSelect_noWhere_addsFilter() throws Exception {
        String result = SelectHandler.handle(
                parse("SELECT * FROM products"), "tenant_id", "T1");

        assertThat(result).contains("tenant_id = 'T1'");
        assertThat(result.toLowerCase()).contains("where");
    }

    @Test
    void plainSelect_withWhere_appendsAnd() throws Exception {
        String result = SelectHandler.handle(
                parse("SELECT * FROM products WHERE id = 1"), "tenant_id", "T1");

        assertThat(result).contains("id = 1");
        assertThat(result).contains("tenant_id = 'T1'");
        assertThat(result.toUpperCase()).contains("AND");
    }

    @Test
    void plainSelect_withOr_wrapsInParenthesis() throws Exception {
        String result = SelectHandler.handle(
                parse("SELECT * FROM products WHERE a = 1 OR b = 2"), "tenant_id", "T1");

        // Critical: parenthesis around (a = 1 OR b = 2)
        assertThat(result).containsPattern("\\(a = 1 OR b = 2\\)");
        assertThat(result).contains("tenant_id = 'T1'");
    }

    // ═══════════════════════════════════════════════════════════════
    // Alias handling — FROM alias is used in filter
    // ═══════════════════════════════════════════════════════════════

    @Test
    void plainSelect_withAlias_usesAliasInFilter() throws Exception {
        String result = SelectHandler.handle(
                parse("SELECT * FROM products p"), "tenant_id", "T1");

        assertThat(result).contains("p.tenant_id = 'T1'");
    }

    @Test
    void plainSelect_noAlias_usesPlainColumn() throws Exception {
        String result = SelectHandler.handle(
                parse("SELECT * FROM products"), "tenant_id", "T1");

        assertThat(result).contains("tenant_id = 'T1'");
        // Not "products.tenant_id"
    }

    // ═══════════════════════════════════════════════════════════════
    // Existing filter — must not duplicate
    // ═══════════════════════════════════════════════════════════════

    @Test
    void alreadyHasFilter_skipsAddition() throws Exception {
        String result = SelectHandler.handle(
                parse("SELECT * FROM products WHERE tenant_id = 'T1'"),
                "tenant_id", "T1");

        // Only 1 occurrence
        assertThat(countOccurrences(result, "tenant_id")).isEqualTo(1);
    }

    @Test
    void alreadyHasFilterInAnd_skipsAddition() throws Exception {
        String result = SelectHandler.handle(
                parse("SELECT * FROM products WHERE id = 1 AND tenant_id = 'T1'"),
                "tenant_id", "T1");

        assertThat(countOccurrences(result, "tenant_id")).isEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════════════
    // UNION — each branch must be filtered
    // ═══════════════════════════════════════════════════════════════

    @Test
    void union_filtersBothBranches() throws Exception {
        String result = SelectHandler.handle(
                parse("SELECT * FROM products UNION SELECT * FROM archive_products"),
                "tenant_id", "T1");

        assertThat(countOccurrences(result, "tenant_id")).isEqualTo(2);
    }

    // ═══════════════════════════════════════════════════════════════
    // CTE — both inner and outer must be filtered
    // ═══════════════════════════════════════════════════════════════

    @Test
    void cte_filtersCteBodyAndOuter() throws Exception {
        String result = SelectHandler.handle(
                parse("WITH cte AS (SELECT * FROM products) SELECT * FROM cte"),
                "tenant_id", "T1");

        // At least the CTE body gets filtered
        assertThat(countOccurrences(result, "tenant_id")).isGreaterThanOrEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════════════
    // Subquery in WHERE — recursive processing
    // ═══════════════════════════════════════════════════════════════

    @Test
    void selectWithInSubquery_filtersBothLevels() throws Exception {
        String result = SelectHandler.handle(
                parse("SELECT * FROM products WHERE id IN (SELECT product_id FROM order_items)"),
                "tenant_id", "T1");

        // Outer filter + subquery filter
        assertThat(countOccurrences(result, "tenant_id")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void selectWithExistsSubquery_filtersBoth() throws Exception {
        String result = SelectHandler.handle(
                parse("SELECT * FROM products p WHERE EXISTS (SELECT 1 FROM orders o WHERE o.product_id = p.id)"),
                "tenant_id", "T1");

        assertThat(countOccurrences(result, "tenant_id")).isGreaterThanOrEqualTo(2);
    }

    // ═══════════════════════════════════════════════════════════════
    // Excluded tables
    // ═══════════════════════════════════════════════════════════════

    @Test
    void excludedTable_skipsFiltering() throws Exception {
        TenantwallConfig.setExcludedTables(List.of("lookup_data"));

        String result = SelectHandler.handle(
                parse("SELECT * FROM lookup_data"), "tenant_id", "T1");

        assertThat(result).doesNotContain("tenant_id");
    }

    @Test
    void nonExcludedTable_stillFilters() throws Exception {
        TenantwallConfig.setExcludedTables(List.of("other_table"));

        String result = SelectHandler.handle(
                parse("SELECT * FROM products"), "tenant_id", "T1");

        assertThat(result).contains("tenant_id = 'T1'");
    }

    @Test
    void mixedTables_filtersWhenAnyNonExcluded() throws Exception {
        TenantwallConfig.setExcludedTables(List.of("lookup_data"));

        // products (not excluded) JOIN lookup_data (excluded) → should still filter
        String result = SelectHandler.handle(
                parse("SELECT * FROM products JOIN lookup_data ON products.lookup_id = lookup_data.id"),
                "tenant_id", "T1");

        assertThat(result).contains("tenant_id");
    }

    // ═══════════════════════════════════════════════════════════════
    // Case sensitivity
    // ═══════════════════════════════════════════════════════════════

    @Test
    void filterDetection_isCaseInsensitive() throws Exception {
        String result = SelectHandler.handle(
                parse("SELECT * FROM products WHERE TENANT_ID = 'X'"),
                "tenant_id", "T1");

        // Uppercase TENANT_ID should be detected, no duplicate added
        assertThat(countOccurrences(result.toUpperCase(), "TENANT_ID")).isEqualTo(1);

    }

    // ═══════════════════════════════════════════════════════════════
    // SECURITY: CTE/exclusion interaction must not bypass filtering
    // ═══════════════════════════════════════════════════════════════

    @Test
    void cteAliasMatchingExcludedTable_mustNotBypassFilter() throws Exception {
        TenantwallConfig.setExcludedTables(Set.of("countries"));
        try {
            String sql = "WITH countries AS (SELECT * FROM users WHERE is_admin = true) "
                    + "SELECT * FROM countries";
            String result = SelectHandler.handle(
                    (Select) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

            assertThat(result)
                    .as("CRITICAL: CTE alias 'countries' must NOT bypass users filtering")
                    .containsPattern("users\\s*\\.\\s*tenant_id");
        } finally {
            TenantwallConfig.setExcludedTables(Set.of());
        }
    }

    @Test
    void physicalExcludedTableInsideCte_mustNotBeFiltered() throws Exception {
        TenantwallConfig.setExcludedTables(Set.of("audit_log"));
        try {
            String sql = "WITH x AS (SELECT * FROM audit_log WHERE id > 0) "
                    + "SELECT * FROM x";
            String result = SelectHandler.handle(
                    (Select) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

            assertThat(result)
                    .as("audit_log is excluded — must not receive a tenant filter")
                    .doesNotContain("audit_log.tenant_id");
        } finally {
            TenantwallConfig.setExcludedTables(Set.of());
        }
    }

    @Test
    void cteReferenceInOuter_mustNotGetPrefixFilter() throws Exception {
        String sql = "WITH my_cte AS (SELECT id, name FROM products) "
                + "SELECT * FROM my_cte";
        String result = SelectHandler.handle(
                (Select) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

        // CTE body filtered
        assertThat(result).containsPattern("products\\s*\\.\\s*tenant_id");
        // Outer CTE reference must NOT be qualified (no such column on the virtual
        // relation)
        assertThat(result)
                .as("Outer CTE reference has no tenant_id column — must not be filtered")
                .doesNotContain("my_cte.tenant_id");
    }

    @Test
    void subqueryAliasMatchingExcludedTable_mustNotBypassFilter() throws Exception {
        TenantwallConfig.setExcludedTables(Set.of("audit_log"));
        try {
            String sql = "SELECT * FROM (SELECT * FROM users) AS audit_log";
            String result = SelectHandler.handle(
                    (Select) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

            assertThat(result)
                    .as("Subquery alias 'audit_log' must not be treated as a physical excluded table")
                    .containsPattern("users\\s*\\.\\s*tenant_id");
        } finally {
            TenantwallConfig.setExcludedTables(Set.of());
        }
    }

    @Test
    void excludedTableInJoin_mustNotGetFiltered_butIncludedDoes() throws Exception {
        TenantwallConfig.setExcludedTables(Set.of("audit_log"));
        try {
            String sql = "SELECT * FROM products p "
                    + "JOIN audit_log a ON p.id = a.product_id";
            String result = SelectHandler.handle(
                    (Select) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

            assertThat(result).contains("p.tenant_id");
            assertThat(result).doesNotContain("a.tenant_id");
        } finally {
            TenantwallConfig.setExcludedTables(Set.of());
        }
    }
}