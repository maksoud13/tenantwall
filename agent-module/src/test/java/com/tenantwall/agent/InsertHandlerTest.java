package com.tenantwall.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class InsertHandlerTest {

    private static final String COLUMN = "tenant_id";
    private static final String VALUE = "TENANT_A";

    @Test
    @DisplayName("INSERT single row — column and value added")
    void insertSingleRow() {
        String sql = "INSERT INTO orders (id, total) VALUES (1, 500)";
        String result = SqlModifier.addFilter(sql, COLUMN, VALUE);

        assertTrue(result.contains("tenant_id"), "Column must be added. Got: " + result);
        assertTrue(result.contains("'TENANT_A'"), "Value must be added. Got: " + result);
        assertTrue(result.contains("(id, total, tenant_id)"),
                "Column list should include tenant_id. Got: " + result);
    }

    @Test
    @DisplayName("INSERT multi-row — value added to each row")
    void insertMultiRow() {
        String sql = "INSERT INTO orders (id, total) VALUES (1, 500), (2, 600)";
        String result = SqlModifier.addFilter(sql, COLUMN, VALUE);

        int count = countOccurrences(result, "'TENANT_A'");
        assertEquals(2, count,
                "Value must be added to EACH row. Got: " + result);
    }

    @Test
    @DisplayName("INSERT without column list — skipped (cannot safely inject)")
    void insertWithoutColumns() {
        String sql = "INSERT INTO orders VALUES (1, 500)";
        String result = SqlModifier.addFilter(sql, COLUMN, VALUE);

        assertEquals(sql, result,
                "Should be unchanged when no column list. Got: " + result);
    }

    @Test
    @DisplayName("INSERT with existing tenant_id — not duplicated")
    void insertAlreadyHasTenant() {
        String sql = "INSERT INTO orders (id, total, tenant_id) VALUES (1, 500, 'TENANT_B')";
        String result = SqlModifier.addFilter(sql, COLUMN, VALUE);

        int count = countOccurrences(result, "tenant_id");
        assertEquals(1, count,
                "Should NOT duplicate. Got: " + result);
    }

    @Test
    @DisplayName("INSERT ... SELECT — subquery gets filter, no column injection")
    void insertSelect() {
        String sql = "INSERT INTO orders_backup (id, total) SELECT id, total FROM orders WHERE total > 100";
        String result = SqlModifier.addFilter(sql, COLUMN, VALUE);

        assertTrue(result.contains("WHERE (total > 100) AND tenant_id = 'TENANT_A'"),
                "SELECT should get filter. Got: " + result);
        // Should NOT add tenant_id to the target column list
        assertFalse(result.contains("orders_backup (id, total, tenant_id)"),
                "Should NOT inject into INSERT target for INSERT...SELECT. Got: " + result);
    }

    private int countOccurrences(String str, String sub) {
        int count = 0, idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}