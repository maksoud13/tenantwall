package com.tenantwall.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class UpdateHandlerTest {

    private static final String COLUMN = "tenant_id";
    private static final String VALUE = "TENANT_A";

    // ═══════════════════════════════════════════════════════════════
    // Basic UPDATE
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("UPDATE with WHERE — filter added with AND")
    void updateWithWhere() {
        String sql = "UPDATE orders SET status = 'DONE' WHERE id = 5";
        String result = SqlModifier.addFilter(sql, COLUMN, VALUE);

        assertTrue(result.contains("(id = 5)"),
                "Original WHERE should be wrapped in parenthesis. Got: " + result);
        assertTrue(result.contains("tenant_id = 'TENANT_A'"),
                "Filter should be added. Got: " + result);
        assertTrue(result.indexOf("(id = 5)") < result.indexOf("tenant_id"),
                "Filter should come AFTER original WHERE. Got: " + result);
    }

    // ═══════════════════════════════════════════════════════════════
    // Security: UPDATE without WHERE (dangerous!)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("SECURITY: UPDATE without WHERE — filter injected as only condition")
    void updateWithoutWhere() {
        String sql = "UPDATE orders SET status = 'DONE'";
        String result = SqlModifier.addFilter(sql, COLUMN, VALUE);

        assertTrue(result.contains("WHERE tenant_id = 'TENANT_A'"),
                "UPDATE without WHERE MUST get a WHERE clause. Got: " + result);
        assertFalse(result.contains("WHERE WHERE"),
                "Should not have double WHERE. Got: " + result);
    }

    // ═══════════════════════════════════════════════════════════════
    // OR Protection (critical)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CRITICAL: UPDATE with OR — protected with parenthesis")
    void updateWithOr() {
        String sql = "UPDATE orders SET status = 'DONE' WHERE id = 5 OR id = 10";
        String result = SqlModifier.addFilter(sql, COLUMN, VALUE);

        assertTrue(result.contains("(id = 5 OR id = 10)"),
                "OR must be wrapped in parenthesis. Got: " + result);
        assertFalse(result.matches(".*OR\\s+id\\s*=\\s*10\\s+AND\\s+tenant_id.*"),
                "Filter must NOT be inside the OR. Got: " + result);
    }

    // ═══════════════════════════════════════════════════════════════
    // Duplicate prevention
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("UPDATE with existing tenant_id filter — skipped")
    void updateAlreadyHasFilter() {
        String sql = "UPDATE orders SET status = 'DONE' WHERE tenant_id = 'TENANT_B'";
        String result = SqlModifier.addFilter(sql, COLUMN, VALUE);

        int count = countOccurrences(result, "tenant_id");
        assertEquals(1, count,
                "Should NOT duplicate tenant_id. Got: " + result);
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════

    private int countOccurrences(String str, String sub) {
        int count = 0, idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}