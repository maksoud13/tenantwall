package com.tenantwall.agent;

import org.junit.jupiter.api.Test;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.insert.Insert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

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

        // [OK] FLEXIBLE: works with or without alias prefix
        // old: AND tenant_id = 'TENANT_A'
        // new: AND orders.tenant_id = 'TENANT_A'
        assertTrue(result.contains("tenant_id = 'TENANT_A'"),
                "SELECT should get filter. Got: " + result);
    }

    @Test
    void excludedTarget_insertSelect_selectBodyStillFiltered() throws Exception {
        TenantwallConfig.setExcludedTables(Set.of("audit_log"));
        try {
            String sql = "INSERT INTO audit_log (user_id, action) "
                    + "SELECT id, 'export' FROM users WHERE is_admin = true";
            String result = InsertHandler.handle(
                    (Insert) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

            assertThat(result)
                    .as("INSERT ... SELECT: source table MUST be filtered even when target is excluded")
                    .containsPattern("users\\s*\\.\\s*tenant_id");
            // And no tenant_id column injected into target (excluded)
            assertThat(result).doesNotContain("audit_log.tenant_id");
        } finally {
            TenantwallConfig.setExcludedTables(Set.of());
        }
    }

    @Test
    void nonExcludedTarget_insertSelect_sourceFiltered() throws Exception {
        String sql = "INSERT INTO archive (id, name) "
                + "SELECT id, name FROM products WHERE discontinued = true";
        String result = InsertHandler.handle(
                (Insert) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

        assertThat(result)
                .as("Source products MUST be filtered")
                .containsPattern("products\\s*\\.\\s*tenant_id");
    }

    @Test
    void excludedTarget_insertValues_noFilter() throws Exception {
        TenantwallConfig.setExcludedTables(Set.of("audit_log"));
        try {
            String sql = "INSERT INTO audit_log (user_id, action) VALUES (1, 'login')";
            String result = InsertHandler.handle(
                    (Insert) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");

            assertThat(result).doesNotContain("tenant_id");
        } finally {
            TenantwallConfig.setExcludedTables(Set.of());
        }
    }
    // ═══════════════════════════════════════════════════════════════
    // Mode 2: Schema-based injection
    // ═══════════════════════════════════════════════════════════════

    @Test
    void insertNoColumns_schemaConfigured_tenantAtMiddle_injectsAtPosition() throws Exception {
        TenantwallConfig.setSchema("products", java.util.List.of("id", "name", "tenant_id", "price"));
        try {
            String sql = "INSERT INTO products VALUES (1, 'Widget', 'IGNORED', 99)";
            String result = InsertHandler.handle(
                    (Insert) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");
            // tenant at position 2 → value replaced with 'ACME'
            assertThat(result).contains("'ACME'");
        } finally {
            // schema persists — reset by re-setting empty
            TenantwallConfig.setSchema("products", java.util.List.of());
        }
    }

    @Test
    void insertNoColumns_schemaConfigured_tenantIsLast_insertsNew() throws Exception {
        TenantwallConfig.setSchema("products", java.util.List.of("id", "name", "tenant_id"));
        try {
            String sql = "INSERT INTO products VALUES (1, 'Widget')";
            String result = InsertHandler.handle(
                    (Insert) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");
            // Insert 'ACME' at position 2 (last)
            assertThat(result).contains("'ACME'");
        } finally {
            TenantwallConfig.setSchema("products", java.util.List.of());
        }
    }

    @Test
    void insertNoColumns_schemaMissingTenantColumn_skips() throws Exception {
        TenantwallConfig.setSchema("products", java.util.List.of("id", "name", "price"));
        try {
            String sql = "INSERT INTO products VALUES (1, 'Widget', 99)";
            String result = InsertHandler.handle(
                    (Insert) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");
            // Warns and returns unchanged
            assertThat(result).doesNotContain("'ACME'");
        } finally {
            TenantwallConfig.setSchema("products", java.util.List.of());
        }
    }

    @Test
    void insertNoColumns_schemaColumnCountMismatch_skipsRow() throws Exception {
        TenantwallConfig.setSchema("products", java.util.List.of("id", "name", "tenant_id", "price"));
        try {
            // Only 2 values, 4 columns expected → mismatch
            String sql = "INSERT INTO products VALUES (1, 'Widget')";
            String result = InsertHandler.handle(
                    (Insert) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");
            // Can't inject, returns unchanged
            assertThat(result).doesNotContain("'ACME'");
        } finally {
            TenantwallConfig.setSchema("products", java.util.List.of());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Multi-row INSERT
    // ═══════════════════════════════════════════════════════════════

    @Test
    void multiRowInsert_explicitColumns_appendsToEachRow() throws Exception {
        String sql = "INSERT INTO products (id, name) VALUES (1, 'A'), (2, 'B'), (3, 'C')";
        String result = InsertHandler.handle(
                (Insert) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");
        // Three 'ACME' occurrences — one per row
        int occurrences = result.split("'ACME'", -1).length - 1;
        assertThat(occurrences).isEqualTo(3);
    }

    // ═══════════════════════════════════════════════════════════════
    // Mode 3: Append hack (opt-in)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void insertNoColumns_noSchema_appendHackEnabled_appends() throws Exception {
        System.setProperty("tenantwall.insert.appendTenant", "true");
        try {
            String sql = "INSERT INTO products VALUES (1, 'Widget', 99)";
            String result = InsertHandler.handle(
                    (Insert) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");
            assertThat(result).contains("'ACME'");
        } finally {
            System.clearProperty("tenantwall.insert.appendTenant");
        }
    }

    @Test
    void insertNoColumns_noSchema_appendHackDisabled_skips() throws Exception {
        System.clearProperty("tenantwall.insert.appendTenant");
        String sql = "INSERT INTO products VALUES (1, 'Widget', 99)";
        String result = InsertHandler.handle(
                (Insert) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");
        assertThat(result).doesNotContain("'ACME'");
    }

    // ═══════════════════════════════════════════════════════════════
    // Excluded table
    // ═══════════════════════════════════════════════════════════════

    @Test
    void excludedTable_skipsInsert() throws Exception {
        TenantwallConfig.setExcludedTables(java.util.Set.of("audit_log"));
        try {
            String sql = "INSERT INTO audit_log (user_id, action) VALUES (1, 'login')";
            String result = InsertHandler.handle(
                    (Insert) CCJSqlParserUtil.parse(sql), "tenant_id", "ACME");
            assertThat(result).doesNotContain("'ACME'");
        } finally {
            TenantwallConfig.setExcludedTables(java.util.Set.of());
        }
    }
    
}