package com.tenantwall.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
}