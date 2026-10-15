package com.tenantwall.agent;

public class SqlModifierDebugger {
    private static final String TENANT = "TENANT_A";

    public static void main(String[] args) {
        // Command-line mode: java SqlModifierDebugger "SQL" "TENANT_ID"
        if (args.length >= 2) {
            debugSql(args[0], args[1]);
            return;
        }

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║       TenantWall - SqlModifier Debugger              ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        // ═══════════════════════════════════════════════════════
        // SELECT Tests
        // ═══════════════════════════════════════════════════════
        System.out.println("━━━━━━━━━━━━━━━━━ SELECT Tests ━━━━━━━━━━━━━━━━━\n");

        run("Simple SELECT",
                "SELECT * FROM users");

        run("SELECT with WHERE",
                "SELECT * FROM users WHERE name = 'Ahmed'");

        run("SELECT with OR (leak protection)",
                "SELECT * FROM orders WHERE status = 'PENDING' OR status = 'SHIPPED'");

        run("SELECT with JOIN",
                "SELECT o.* FROM orders o JOIN customers c ON o.customer_id = c.id");

        run("Subquery with Comparison",
                "SELECT * FROM orders WHERE total > (SELECT AVG(total) FROM orders)");

        run("Subquery with IN",
                "SELECT * FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE level = 'GOLD')");

        run("Subquery with EXISTS",
                "SELECT * FROM orders o WHERE EXISTS (SELECT 1 FROM customers c WHERE c.id = o.customer_id AND c.level = 'GOLD')");

        run("Subquery with NOT IN",
                "SELECT * FROM orders WHERE customer_id NOT IN (SELECT id FROM customers WHERE level = 'GOLD')");

        run("Subquery with NOT EXISTS",
                "SELECT * FROM orders o WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.id = o.customer_id AND c.level = 'GOLD')");

        run("UNION",
                "SELECT product_name FROM orders WHERE total < 50 UNION SELECT product_name FROM orders WHERE total > 500");

        run("BETWEEN",
                "SELECT * FROM orders WHERE total BETWEEN 100 AND 500");

        run("LIKE",
                "SELECT * FROM orders WHERE product_name LIKE '%Phone%'");

        // ═══════════════════════════════════════════════════════
        // UPDATE Tests
        // ═══════════════════════════════════════════════════════
        System.out.println("\n━━━━━━━━━━━━━━━━━ UPDATE Tests ━━━━━━━━━━━━━━━━━\n");

        run("UPDATE with WHERE",
                "UPDATE orders SET status = 'DONE' WHERE id = 5");

        run("UPDATE without WHERE (⚠️ dangerous)",
                "UPDATE orders SET status = 'DONE'");

        run("UPDATE with OR",
                "UPDATE orders SET status = 'DONE' WHERE id = 5 OR id = 10");

        // ═══════════════════════════════════════════════════════
        // DELETE Tests
        // ═══════════════════════════════════════════════════════
        System.out.println("\n━━━━━━━━━━━━━━━━━ DELETE Tests ━━━━━━━━━━━━━━━━━\n");

        run("DELETE with WHERE",
                "DELETE FROM orders WHERE id = 5");

        run("DELETE without WHERE (⚠️ dangerous)",
                "DELETE FROM orders");

        run("DELETE with OR",
                "DELETE FROM orders WHERE status = 'CANCELLED' OR status = 'EXPIRED'");

        // ═══════════════════════════════════════════════════════
        // INSERT Tests
        // ═══════════════════════════════════════════════════════
        System.out.println("\n━━━━━━━━━━━━━━━━━ INSERT Tests ━━━━━━━━━━━━━━━━━\n");

        run("INSERT with columns",
                "INSERT INTO orders (id, total) VALUES (1, 500)");

        run("INSERT without columns (⚠️ skip)",
                "INSERT INTO orders VALUES (1, 500)");

        run("INSERT with multiple rows",
                "INSERT INTO orders (id, total) VALUES (1, 500), (2, 600)");

        run("INSERT ... SELECT",
                "INSERT INTO orders_backup (id, total) SELECT id, total FROM orders WHERE total > 100");

        // ═══════════════════════════════════════════════════════
        // Summary
        // ═══════════════════════════════════════════════════════
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║                    ✅ Tests Complete                  ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
    }

    // ═══════════════════════════════════════════════════════════
    // Helper: run a single test
    // ═══════════════════════════════════════════════════════════
    private static void run(String testName, String sql) {
        System.out.println("🔹 Test: " + testName);
        debugSql(sql, TENANT);
        System.out.println();
    }

    private static void debugSql(String sql, String tenantId) {
        System.out.println("   Input:  " + sql);
        try {
            String modified = SqlModifier.addFilter(sql, "tenant_id", tenantId);
            if (modified.equals(sql)) {
                System.out.println("   Output: " + modified + "   ⚠️ (unchanged)");
            } else {
                System.out.println("   Output: " + modified);
            }
        } catch (Exception e) {
            System.out.println("   ❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}