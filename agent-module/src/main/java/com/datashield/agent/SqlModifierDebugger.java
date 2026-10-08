package com.datashield.agent;

public class SqlModifierDebugger {

    public static void main(String[] args) {
        // لو في args من سطر الأوامر، استخدمهم
        if (args.length >= 2) {
            String sql = args[0];
            String tenantId = args[1];
            debugSql(sql, tenantId);
            return;
        }

        // وإلا شغل الاختبارات السريعة
        System.out.println("=== Running predefined tests ===\n");

        String[][] tests = {
                { "SELECT * FROM users", "TENANT_A" },
                { "SELECT * FROM users WHERE name = 'Ahmed'", "TENANT_A" },
                { "SELECT o.* FROM orders o JOIN customers c ON o.customer_id = c.id", "TENANT_B" },
                { "SELECT * FROM orders WHERE total > (SELECT AVG(total) FROM orders)", "TENANT_A" },
                { "SELECT * FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE level = 'GOLD')",
                        "TENANT_A" },
                { "SELECT * FROM orders o WHERE EXISTS (SELECT 1 FROM customers c WHERE c.id = o.customer_id AND c.level = 'GOLD')",
                        "TENANT_A" },
                { "SELECT product_name, total FROM orders WHERE total < 50 UNION SELECT product_name, total FROM orders WHERE total > 500",
                        "TENANT_A" },
                { "SELECT * FROM orders WHERE customer_id NOT IN (SELECT id FROM customers WHERE level = 'GOLD')",
                        "TENANT_A" },
                { "SELECT * FROM orders o WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.id = o.customer_id AND c.level = 'GOLD')",
                        "TENANT_A" },
        };

        for (String[] test : tests) {
            debugSql(test[0], test[1]);
            System.out.println("-----------------------------------");
        }
    }

    private static void debugSql(String sql, String tenantId) {
        System.out.println("🔹 Tenant: " + tenantId);
        System.out.println("🔸 Original: " + sql);
        String modified = SqlModifier.addTenantId(sql, tenantId);
        System.out.println("🔹 Modified: " + modified);
        System.out.println();
    }
}