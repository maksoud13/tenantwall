package com.tenantwall.agent;

import org.junit.jupiter.api.Test;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.update.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

class UpdateHandlerTest {

        @Test
        void updateWithWhere() {
                String sql = "UPDATE orders SET status = 'DONE' WHERE id = 5";
                String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

                assertTrue(result.contains("tenant_id = 'TENANT_A'"),
                                "UPDATE should get tenant filter. Got: " + result);
                assertTrue(result.contains("id = 5"),
                                "Original WHERE should be preserved. Got: " + result);
        }

        @Test
        void updateWithoutWhere() {
                String sql = "UPDATE orders SET status = 'DONE'";
                String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

                assertTrue(result.contains("WHERE"),
                                "UPDATE without WHERE should get a WHERE clause. Got: " + result);
                assertTrue(result.contains("tenant_id = 'TENANT_A'"),
                                "Filter should be injected. Got: " + result);
        }

        @Test
        void updateWithOr() {
                String sql = "UPDATE orders SET status = 'DONE' WHERE id = 5 OR id = 10";
                String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

                assertTrue(result.contains("tenant_id = 'TENANT_A'"),
                                "Filter should be injected. Got: " + result);
                // OR should be wrapped in parens
                assertTrue(result.matches("(?s).*\\(.*OR.*\\).*AND.*tenant_id.*"),
                                "OR should be wrapped in parens. Got: " + result);
        }

        @Test
        void updateWithExistingTenant() {
                String sql = "UPDATE orders SET status = 'DONE' WHERE tenant_id = 'TENANT_A' AND id = 5";
                String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_B");

                assertEquals(sql, result,
                                "UPDATE with existing tenant filter should be unchanged. Got: " + result);
        }

        @Test
        void excludedTarget_whereSubquery_mustStillBeFiltered() throws Exception {
                TenantwallConfig.setExcludedTables(Set.of("audit_log"));
                try {
                        String sql = "UPDATE audit_log SET status = 'reviewed' "
                                        + "WHERE user_id IN (SELECT id FROM users WHERE is_admin = true)";
                        String result = UpdateHandler.handle(
                                        (Update) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

                        assertThat(result)
                                        .as("Subquery on users MUST be filtered even when target is excluded")
                                        .containsPattern("users\\s*\\.\\s*tenant_id");
                } finally {
                        TenantwallConfig.setExcludedTables(Set.of());
                }
        }

        @Test
        void excludedTarget_noWhere_noFilterAdded() throws Exception {
                TenantwallConfig.setExcludedTables(Set.of("audit_log"));
                try {
                        String sql = "UPDATE audit_log SET status = 'reviewed'";
                        String result = UpdateHandler.handle(
                                        (Update) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

                        assertThat(result)
                                        .as("No WHERE added when target is excluded")
                                        .doesNotContain("tenant_id");
                } finally {
                        TenantwallConfig.setExcludedTables(Set.of());
                }
        }

        @Test
        void nonExcludedTarget_setSubquery_mustBeFiltered() throws Exception {
                String sql = "UPDATE products SET price = "
                                + "(SELECT MAX(price) FROM competitors) WHERE id = 1";
                String result = UpdateHandler.handle(
                                (Update) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

                assertThat(result)
                                .as("SET subquery on competitors MUST be filtered")
                                .containsPattern("competitors\\s*\\.\\s*tenant_id");
        }

        @Test
        void excludedTarget_whereSubqueryAndSetSubquery_bothFiltered() throws Exception {
                TenantwallConfig.setExcludedTables(Set.of("audit_log"));
                try {
                        String sql = "UPDATE audit_log SET value = (SELECT MAX(x) FROM products) "
                                        + "WHERE user_id IN (SELECT id FROM users)";
                        String result = UpdateHandler.handle(
                                        (Update) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

                        assertThat(result).containsPattern("users\\s*\\.\\s*tenant_id");
                        assertThat(result).containsPattern("products\\s*\\.\\s*tenant_id");
                } finally {
                        TenantwallConfig.setExcludedTables(Set.of());
                }
        }
}