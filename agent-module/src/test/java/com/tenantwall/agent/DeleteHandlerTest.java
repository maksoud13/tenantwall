package com.tenantwall.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
}