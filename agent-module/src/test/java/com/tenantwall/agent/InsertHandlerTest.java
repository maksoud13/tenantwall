package com.tenantwall.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InsertHandlerTest {

    @Test
    void insertWithColumns() {
        String sql = "INSERT INTO orders (id, total) VALUES (1, 500)";
        String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

        assertTrue(result.contains("tenant_id"),
                "INSERT should include tenant_id column. Got: " + result);
        assertTrue(result.contains("'TENANT_A'"),
                "INSERT should include tenant value. Got: " + result);
    }

    @Test
    void insertWithoutColumns() {
        // No schema configured, no explicit columns → should be skipped
        String sql = "INSERT INTO orders VALUES (1, 500)";
        String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

        assertEquals(sql, result,
                "INSERT without columns and no schema should be unchanged. Got: " + result);
    }

    @Test
    void insertWithExistingTenant() {
        String sql = "INSERT INTO orders (id, total, tenant_id) VALUES (1, 500, 'OTHER')";
        String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

        assertEquals(sql, result,
                "INSERT with existing tenant_id should be unchanged. Got: " + result);
    }

    @Test
    void insertMultipleRows() {
        String sql = "INSERT INTO orders (id, total) VALUES (1, 500), (2, 600)";
        String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

        assertTrue(result.contains("'TENANT_A'"),
                "Multi-row INSERT should get tenant value. Got: " + result);

        // Count occurrences of tenant value — should be 2 (one per row)
        int count = result.split("'TENANT_A'", -1).length - 1;
        assertEquals(2, count, "Tenant value should appear once per row. Got: " + result);
    }

    @Test
    void insertSelect() {
        String sql = "INSERT INTO orders_backup (id, total) SELECT id, total FROM orders WHERE total > 100";
        String result = SqlModifier.addFilter(sql, "tenant_id", "TENANT_A");

        // ✅ FLEXIBLE: works with or without alias prefix
        // old: AND tenant_id = 'TENANT_A'
        // new: AND orders.tenant_id = 'TENANT_A'
        assertTrue(result.contains("tenant_id = 'TENANT_A'"),
                "SELECT should get filter. Got: " + result);
    }
}