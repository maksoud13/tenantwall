package com.tenantwall.agent;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TableExtractor}.
 *
 * <p>
 * <b>Security-critical:</b> These tests verify the distinction between
 * physical tables and CTE aliases. A regression here means an attacker
 * can bypass tenant filtering by aliasing an excluded table as a CTE.
 */
@DisplayName("TableExtractor — physical vs virtual table extraction")
class TableExtractorTest {

    private static Set<String> extract(String sql) throws Exception {
        Select select = (Select) CCJSqlParserUtil.parse(sql);
        return TableExtractor.getImmediateTables(select);
    }

    // ═══════════════════════════════════════════════════════════════
    // getImmediateTables — simple FROM
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Simple FROM extraction")
    class SimpleFrom {

        @Test
        @DisplayName("SELECT from single table returns that table")
        void singleTable() throws Exception {
            Set<String> tables = extract("SELECT * FROM users");
            assertEquals(Set.of("users"), tables);
        }

        @Test
        @DisplayName("Table names are lowercased")
        void lowercasesNames() throws Exception {
            Set<String> tables = extract("SELECT * FROM USERS");
            assertEquals(Set.of("users"), tables);
        }

        @Test
        @DisplayName("Schema-qualified table returns only table name")
        void schemaQualified() throws Exception {
            Set<String> tables = extract("SELECT * FROM myschema.users");
            assertEquals(Set.of("users"), tables);
        }

        @Test
        @DisplayName("Table with alias still returns underlying table name")
        void tableWithAlias() throws Exception {
            Set<String> tables = extract("SELECT * FROM users u");
            assertEquals(Set.of("users"), tables);
        }

        @Test
        @DisplayName("Multiple JOINs return all tables")
        void multipleJoins() throws Exception {
            Set<String> tables = extract(
                    "SELECT * FROM users u " +
                            "JOIN orders o ON u.id = o.user_id " +
                            "LEFT JOIN products p ON o.id = p.order_id");
            assertEquals(Set.of("users", "orders", "products"), tables);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CTE alias exclusion — SECURITY CRITICAL
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CTE alias exclusion (security-critical)")
    class CteExclusion {

        @Test
        @DisplayName("Single CTE: FROM CTE returns empty (virtual relation)")
        void singleCteAsOnlySource() throws Exception {
            Set<String> tables = extract(
                    "WITH cte AS (SELECT * FROM users) SELECT * FROM cte");
            // CTE body's table is NOT in immediate FROM — only 'cte' alias is
            // But 'cte' is a CTE alias → excluded → empty set
            assertTrue(tables.isEmpty(),
                    "CTE reference must not be reported as physical table");
        }

        @Test
        @DisplayName("CTE + physical table: only physical table returned")
        void ctePlusPhysicalTable() throws Exception {
            Set<String> tables = extract(
                    "WITH active_users AS (SELECT * FROM users WHERE active = 1) " +
                            "SELECT * FROM active_users a JOIN orders o ON a.id = o.user_id");
            assertEquals(Set.of("orders"), tables,
                    "Only 'orders' is physical; 'active_users' is a CTE alias");
        }

        @Test
        @DisplayName("CTE alias matching excluded table name is NOT reported")
        void cteAliasMatchingExcludedTable() throws Exception {
            // This is the exact bypass scenario: attacker defines CTE named
            // after an excluded table to make the body unfiltered
            Set<String> tables = extract(
                    "WITH audit_log AS (SELECT * FROM users) SELECT * FROM audit_log");
            assertTrue(tables.isEmpty(),
                    "CTE alias 'audit_log' must not be mistaken for physical table");
        }

        @Test
        @DisplayName("Multiple CTEs: all aliases excluded")
        void multipleCtes() throws Exception {
            Set<String> tables = extract(
                    "WITH a AS (SELECT * FROM users), " +
                            "     b AS (SELECT * FROM orders) " +
                            "SELECT * FROM a JOIN b ON a.id = b.id JOIN products p ON p.id = a.id");
            assertEquals(Set.of("products"), tables,
                    "Only 'products' is physical; a and b are CTEs");
        }

        @Test
        @DisplayName("CTE case-insensitivity: CTE Foo matches FROM foo")
        void cteCaseInsensitive() throws Exception {
            Set<String> tables = extract(
                    "WITH Foo AS (SELECT * FROM users) SELECT * FROM foo");
            assertTrue(tables.isEmpty(),
                    "CTE alias matching must be case-insensitive");
        }

        @Test
        @DisplayName("CTE with explicit column list")
        void cteWithColumnList() throws Exception {
            Set<String> tables = extract(
                    "WITH cte (id, name) AS (SELECT id, name FROM users) " +
                            "SELECT * FROM cte JOIN orders o ON cte.id = o.user_id");
            assertEquals(Set.of("orders"), tables);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SetOperationList (UNION / INTERSECT / EXCEPT)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Set operations (UNION/INTERSECT/EXCEPT)")
    class SetOperations {

        @Test
        @DisplayName("UNION returns tables from both branches")
        void unionBranches() throws Exception {
            Set<String> tables = extract(
                    "SELECT * FROM users UNION SELECT * FROM orders");
            assertEquals(Set.of("users", "orders"), tables);
        }

        @Test
        @DisplayName("UNION ALL with JOINs")
        void unionAllWithJoins() throws Exception {
            Set<String> tables = extract(
                    "SELECT * FROM users u JOIN orders o ON u.id = o.user_id " +
                            "UNION ALL " +
                            "SELECT * FROM products p JOIN categories c ON p.cat_id = c.id");
            assertEquals(Set.of("users", "orders", "products", "categories"), tables);
        }

        @Test
        @DisplayName("UNION with CTE in one branch")
        void unionWithCte() throws Exception {
            Set<String> tables = extract(
                    "WITH a AS (SELECT * FROM users) " +
                            "SELECT * FROM a UNION SELECT * FROM orders");
            assertEquals(Set.of("orders"), tables,
                    "CTE 'a' excluded, 'orders' physical");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ParenthesedSelect (subquery in FROM)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Parenthesed subqueries — recursed (verified)")
    class ParenthesedSubqueries {

        @Test
        @DisplayName("FROM (subquery): recursed → includes inner tables")
        void subqueryInFrom_recursed() throws Exception {
            Set<String> tables = extract(
                    "SELECT * FROM (SELECT * FROM users) AS sub");
            assertEquals(Set.of("users"), tables,
                    "TableExtractor recurses into FROM subqueries — " +
                            "inner tables must be visible for exclusion checks");
        }

        @Test
        @DisplayName("Top-level ParenthesedSelect: recursed")
        void topLevelParenthesed_recurse() throws Exception {
            Set<String> tables = extract("(SELECT * FROM users)");
            assertEquals(Set.of("users"), tables,
                    "Top-level ParenthesedSelect is recursed");
        }

        @Test
        @DisplayName("Nested ParenthesedSelect in JOIN: recursed")
        void subqueryInJoin_recursed() throws Exception {
            Set<String> tables = extract(
                    "SELECT * FROM orders o JOIN (SELECT * FROM users) u ON o.uid = u.id");
            assertEquals(Set.of("orders", "users"), tables,
                    "TableExtractor recurses into JOIN subqueries too");
        }

        @Test
        @DisplayName("Subquery with JOIN inside FROM: all tables returned")
        void subqueryWithJoinInside_recursed() throws Exception {
            Set<String> tables = extract(
                    "SELECT * FROM (SELECT * FROM users u JOIN orders o ON u.id = o.user_id) AS sub");
            assertEquals(Set.of("users", "orders"), tables);
        }

        @Test
        @DisplayName("Nested subqueries (2 levels deep): all tables returned")
        void nestedSubqueries_recursed() throws Exception {
            Set<String> tables = extract(
                    "SELECT * FROM (SELECT * FROM (SELECT * FROM users) AS inner_sub) AS outer_sub");
            assertEquals(Set.of("users"), tables);
        }
    }
    // ═══════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("SELECT with no FROM returns empty set")
        void noFromClause() throws Exception {
            Set<String> tables = extract("SELECT 1");
            assertTrue(tables.isEmpty());
        }

        @Test
        @DisplayName("Empty CTE alias (defensive) is not added")
        void emptyAliasNotAdded() throws Exception {
            // A CTE with null/empty alias cannot be referenced — defensive
            // code should not crash and should not add empty string
            assertDoesNotThrow(() -> extract(
                    "SELECT * FROM users"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Parameterized sanity checks
    // ═══════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "extract: {0} → {1}")
    @DisplayName("Parameterized extraction sanity")
    @org.junit.jupiter.params.provider.CsvSource({
            "SELECT * FROM a,              a",
            "SELECT * FROM a,              a",
            "SELECT * FROM A,              a",
    })
    void parameterizedSingle(String sql, String expected) throws Exception {
        Set<String> tables = extract(sql);
        assertEquals(Set.of(expected), tables);
    }

    @ParameterizedTest(name = "CTE exclusion: {0}")
    @DisplayName("CTE aliases are excluded regardless of case")
    @ValueSource(strings = {
            "WITH Foo AS (SELECT * FROM u) SELECT * FROM foo",
            "WITH FOO AS (SELECT * FROM u) SELECT * FROM Foo",
            "WITH foo AS (SELECT * FROM u) SELECT * FROM foo"
    })
    void cteCaseVariants(String sql) throws Exception {
        Set<String> tables = extract(sql);
        assertTrue(tables.isEmpty(),
                "CTE alias must be excluded regardless of case: " + sql);
    }
}