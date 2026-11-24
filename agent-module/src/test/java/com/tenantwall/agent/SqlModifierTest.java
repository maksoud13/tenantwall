package com.tenantwall.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class SqlModifierTest {

        @Test
        void test1_selectWithoutWhere() {
                String sql = "SELECT * FROM users";
                String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

                assertTrue(result.contains("tenant_id = 'TENANT_A'"),
                                "Should add tenant filter. Got: " + result);
                assertTrue(result.contains("WHERE"),
                                "Should add WHERE clause. Got: " + result);
        }

        @Test
        void test2_selectWithWhere() {
                String sql = "SELECT * FROM users WHERE name = 'Ahmed'";
                String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

                assertTrue(result.contains("tenant_id = 'TENANT_A'"),
                                "Should add tenant filter. Got: " + result);
                assertTrue(result.contains("name = 'Ahmed'"),
                                "Original filter should be preserved. Got: " + result);
        }

        @Test
        void test3_selectWithJoin() {
                String sql = "SELECT o.* FROM orders o JOIN customers c ON o.customer_name = c.name";
                String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

                assertTrue(result.contains("tenant_id = 'TENANT_A'"),
                                "Should add tenant filter. Got: " + result);
                // Should use alias prefix (o.tenant_id) to avoid ambiguity
                assertTrue(result.matches("(?s).*\\bo\\.tenant_id = 'TENANT_A'.*"),
                                "Should use alias prefix. Got: " + result);
        }

        @Test
        void updateStatement_dispatchesToUpdateHandler() throws Exception {
                String result = SqlModifier.addFilter(
                                "UPDATE products SET price = 100 WHERE id = 1",
                                "tenant_id", "ACME");
                assertThat(result).contains("tenant_id = 'ACME'");
        }

        @Test
        void deleteStatement_dispatchesToDeleteHandler() throws Exception {
                String result = SqlModifier.addFilter(
                                "DELETE FROM products WHERE id = 1",
                                "tenant_id", "ACME");
                assertThat(result).contains("tenant_id = 'ACME'");
        }

        @Test
        void insertStatement_dispatchesToInsertHandler() throws Exception {
                String result = SqlModifier.addFilter(
                                "INSERT INTO products (id, name) VALUES (1, 'Widget')",
                                "tenant_id", "ACME");
                assertThat(result).contains("tenant_id");
        }

        @Test
        void parseError_throwsTenantWallParseException() {
                assertThatThrownBy(() -> SqlModifier.addFilter(
                                "SELECT FROM WHERE", "tenant_id", "ACME"))
                                .isInstanceOf(TenantWallParseException.class)
                                .hasMessageContaining("failed to parse");
        }

        @Test
        void nullSql_returnsNull() {
                assertThat(SqlModifier.addFilter(null, "tenant_id", "ACME")).isNull();
        }

        @Test
        void nullColumn_throws() {
                assertThatThrownBy(() -> SqlModifier.addFilter("SELECT 1", null, "ACME"))
                                .isInstanceOf(TenantWallParseException.class)
                                .hasMessageContaining("filter column is not configured");
        }

        @Test
        void emptyValue_throws() {
                assertThatThrownBy(() -> SqlModifier.addFilter("SELECT 1", "tenant_id", ""))
                                .isInstanceOf(TenantWallParseException.class)
                                .hasMessageContaining("tenant ID is missing");
        }

        @Test
        void addTenantId_legacyAlias_works() {
                String result = SqlModifier.addTenantId("SELECT * FROM products", "ACME");
                assertThat(result).contains("tenant_id = 'ACME'");
        }

        @Test
        void unsupportedStatement_returnsOriginal() {
                String sql = "CREATE TABLE foo (id INT)";
                String result = SqlModifier.addFilter(sql, "tenant_id", "ACME");
                assertThat(result).isEqualTo(sql);
        }
}