package com.tenantwall.agent;

import org.junit.jupiter.api.Test;

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
}