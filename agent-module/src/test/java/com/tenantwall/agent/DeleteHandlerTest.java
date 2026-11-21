package com.tenantwall.agent;

import org.junit.jupiter.api.Test;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.delete.Delete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

class DeleteHandlerTest {

        @Test
        void deleteWithWhere() {
                String sql = "DELETE FROM orders WHERE id = 5";
                String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

                assertTrue(result.contains("tenant_id = 'TENANT_A'"),
                                "DELETE should get tenant filter. Got: " + result);
                assertTrue(result.contains("id = 5"),
                                "Original WHERE should be preserved. Got: " + result);
        }

        @Test
        void deleteWithoutWhere() {
                String sql = "DELETE FROM orders";
                String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

                assertTrue(result.contains("WHERE"),
                                "DELETE without WHERE should get a WHERE clause. Got: " + result);
                assertTrue(result.contains("tenant_id = 'TENANT_A'"),
                                "Filter should be injected. Got: " + result);
        }

        @Test
        void deleteWithOr() {
                String sql = "DELETE FROM orders WHERE status = 'CANCELLED' OR status = 'EXPIRED'";
                String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

                assertTrue(result.contains("tenant_id = 'TENANT_A'"),
                                "Filter should be injected. Got: " + result);
                // OR should be wrapped in parens to prevent leaks
                assertTrue(result.matches("(?s).*\\(.*OR.*\\).*AND.*tenant_id.*"),
                                "OR should be wrapped in parens. Got: " + result);
        }

        @Test
        void deleteWithExistingTenant() {
                String sql = "DELETE FROM orders WHERE tenant_id = 'TENANT_A' AND id = 5";
                String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_B");

                assertEquals(sql, result,
                                "DELETE with existing tenant filter should be unchanged. Got: " + result);
        }

        @Test
        void excludedTarget_whereSubquery_mustStillBeFiltered() throws Exception {
                TenantwallConfig.setExcludedTables(Set.of("audit_log"));
                try {
                        String sql = "DELETE FROM audit_log "
                                        + "WHERE user_id IN (SELECT id FROM users WHERE is_admin = true)";
                        String result = DeleteHandler.handle(
                                        (Delete) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

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
                        String sql = "DELETE FROM audit_log";
                        String result = DeleteHandler.handle(
                                        (Delete) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

                        assertThat(result).doesNotContain("tenant_id");
                } finally {
                        TenantwallConfig.setExcludedTables(Set.of());
                }
        }

        @Test
        void nonExcludedTarget_whereSubquery_mustBeFiltered() throws Exception {
                String sql = "DELETE FROM products WHERE id IN "
                                + "(SELECT product_id FROM orders WHERE total > 100)";
                String result = DeleteHandler.handle(
                                (Delete) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

                assertThat(result)
                                .as("Subquery on orders MUST be filtered")
                                .containsPattern("orders\\s*\\.\\s*tenant_id");
        }
}