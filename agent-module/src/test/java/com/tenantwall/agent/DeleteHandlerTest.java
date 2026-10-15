package com.tenantwall.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class DeleteHandlerTest {

    private static final String COLUMN = "tenant_id";
    private static final String VALUE = "TENANT_A";

    @Test
    @DisplayName("DELETE with WHERE — filter added")
    void deleteWithWhere() {
        String sql = "DELETE FROM orders WHERE id = 5";
        String result = SqlModifier.addFilter(sql, COLUMN, VALUE);

        assertTrue(result.contains("(id = 5)"), "Got: " + result);
        assertTrue(result.contains("tenant_id = 'TENANT_A'"), "Got: " + result);
    }

    @Test
    @DisplayName("SECURITY: DELETE without WHERE — filter injected (prevents mass delete)")
    void deleteWithoutWhere() {
        String sql = "DELETE FROM orders";
        String result = SqlModifier.addFilter(sql, COLUMN, VALUE);

        assertTrue(result.contains("WHERE tenant_id = 'TENANT_A'"),
                "Mass delete must be scoped to tenant. Got: " + result);
    }

    @Test
    @DisplayName("CRITICAL: DELETE with OR — parenthesis protection")
    void deleteWithOr() {
        String sql = "DELETE FROM orders WHERE status = 'CANCELLED' OR status = 'EXPIRED'";
        String result = SqlModifier.addFilter(sql, COLUMN, VALUE);

        assertTrue(result.contains("(status = 'CANCELLED' OR status = 'EXPIRED')"),
                "OR must be wrapped. Got: " + result);
    }

    @Test
    @DisplayName("DELETE with subquery in WHERE")
    void deleteWithSubquery() {
        String sql = "DELETE FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE level = 'GOLD')";
        String result = SqlModifier.addFilter(sql, COLUMN, VALUE);

        assertTrue(result.contains("tenant_id = 'TENANT_A'"), "Got: " + result);
        // Subquery should also have the filter
        assertTrue(result.contains("level = 'GOLD'") && result.contains("customer_id IN"),
                "Subquery should be preserved. Got: " + result);
    }
}