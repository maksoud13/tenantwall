package com.datashield.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SqlModifierTest {

    @Test
    public void testAddTenantId_SimpleSelect() {
        String sql = "SELECT * FROM users";
        String tenantId = "TENANT_A";
        String modifiedSql = SqlModifier.addTenantId(sql, tenantId);

        // لازم يتأكد إن الشرط اتحط صح
        assertTrue(modifiedSql.contains("WHERE"));
        assertTrue(modifiedSql.contains("tenant_id = 'TENANT_A'"));
        System.out.println("✅ Test 1 Passed: " + modifiedSql);
    }

    @Test
    public void testAddTenantId_SelectWithWhere() {
        String sql = "SELECT * FROM users WHERE name = 'Ahmed'";
        String tenantId = "TENANT_A";
        String modifiedSql = SqlModifier.addTenantId(sql, tenantId);

        // لازم يتأكد إن الدمج بـ AND حصل
        assertTrue(modifiedSql.contains("AND"));
        assertTrue(modifiedSql.contains("tenant_id = 'TENANT_A'"));
        System.out.println("✅ Test 2 Passed: " + modifiedSql);
    }

    @Test
    public void testAddTenantId_SelectWithJoin() {
        String sql = "SELECT o.* FROM orders o JOIN customers c ON o.customer_name = c.name";
        String tenantId = "TENANT_A";
        String modifiedSql = SqlModifier.addTenantId(sql, tenantId);

        // لازم يتأكد إن الشرط اتضاف في الآخر من غير ما يكسر الـ JOIN
        assertTrue(modifiedSql.contains("JOIN"));
        assertTrue(modifiedSql.contains("tenant_id = 'TENANT_A'"));
        System.out.println("✅ Test 3 Passed: " + modifiedSql);
    }
}