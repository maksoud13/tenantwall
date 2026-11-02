package com.megaexample.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * Seeds ~1.77M rows across 20 tenants.
 * Run once: mvn spring-boot:run -Dspring-boot.run.profiles=seed
 *
 * Uses raw JDBC (not JPA) for max throughput.
 * Agent overhead is negligible during seeding (no TenantContext set).
 */
@Component
@Profile("seed")
public class DataSeeder implements CommandLineRunner {

    private static final int TENANTS = 20;
    private static final int PRODUCTS_PER_TENANT = 40_000;
    private static final int VARIANTS_PER_TENANT = 40_000;
    private static final int CUSTOMERS_PER_TENANT = 500;
    private static final int ORDERS_PER_TENANT = 500;
    private static final int ITEMS_PER_ORDER = 3;
    private static final int BATCH_SIZE = 2000;

    private final DataSource dataSource;
    private final Random rnd = new Random(42);

    public DataSeeder(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  [START] Data Seeder — 20 tenants × ~88K rows = ~1.77M rows    ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        long start = System.currentTimeMillis();

        // Step 1: Check if already seeded
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM products")) {
            if (rs.next() && rs.getLong(1) > 0) {
                System.out.printf("[WARN]  Already seeded: %,d products. Skipping.%n%n", rs.getLong(1));
                printSummary();
                return;
            }
        }

        // Step 2: Seed lookups (small, fixed data)
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            seedLookups(conn);
            conn.commit();
        }

        // Step 3: Seed per-tenant data
        for (int t = 1; t <= TENANTS; t++) {
            String tenant = String.format("TENANT_%03d", t);
            long tStart = System.currentTimeMillis();
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                seedTenant(conn, tenant);
                conn.commit();
            }
            long tElapsed = System.currentTimeMillis() - tStart;
            System.out.printf("  [OK] %s seeded in %.1fs%n", tenant, tElapsed / 1000.0);
        }

        long total = System.currentTimeMillis() - start;
        System.out.printf("%n[OK] Seeding complete in %.1fs (%d minutes)%n%n",
                total / 1000.0, total / 60000);
        printSummary();
    }

    // ═══════════════════════════════════════════════════════════
    //  LOOKUP TABLES (small, fixed data)
    // ═══════════════════════════════════════════════════════════
    private void seedLookups(Connection conn) throws SQLException {
        System.out.println("[COLS] Seeding lookup tables...");

        // countries
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO countries (id, iso_code, name) VALUES (?, ?, ?)")) {
            String[][] countries = {
                    {"1", "US", "United States"}, {"2", "GB", "United Kingdom"},
                    {"3", "DE", "Germany"}, {"4", "FR", "France"},
                    {"5", "JP", "Japan"}, {"6", "EG", "Egypt"},
                    {"7", "AE", "UAE"}, {"8", "IN", "India"},
                    {"9", "BR", "Brazil"}, {"10", "CN", "China"}
            };
            for (String[] c : countries) {
                ps.setInt(1, Integer.parseInt(c[0]));
                ps.setString(2, c[1]);
                ps.setString(3, c[2]);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // currencies
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO currencies (id, iso_code, name, symbol) VALUES (?, ?, ?, ?)")) {
            Object[][] currencies = {
                    {1, "USD", "US Dollar", "$"}, {2, "EUR", "Euro", "€"},
                    {3, "GBP", "British Pound", "£"}, {4, "JPY", "Japanese Yen", "¥"},
                    {5, "EGP", "Egyptian Pound", "E£"}
            };
            for (Object[] c : currencies) {
                ps.setInt(1, (Integer) c[0]);
                ps.setString(2, (String) c[1]);
                ps.setString(3, (String) c[2]);
                ps.setString(4, (String) c[3]);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // payment_methods
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO payment_methods (id, code, name) VALUES (?, ?, ?)")) {
            Object[][] methods = {
                    {1, "CARD", "Credit Card"}, {2, "CASH", "Cash"},
                    {3, "BANK", "Bank Transfer"}, {4, "WALLET", "Digital Wallet"}
            };
            for (Object[] m : methods) {
                ps.setInt(1, (Integer) m[0]);
                ps.setString(2, (String) m[1]);
                ps.setString(3, (String) m[2]);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // shipping_carriers
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO shipping_carriers (id, code, name, tracking_url) VALUES (?, ?, ?, ?)")) {
            Object[][] carriers = {
                    {1, "FEDEX", "FedEx", "https://fedex.com/track"},
                    {2, "DHL", "DHL", "https://dhl.com/track"},
                    {3, "UPS", "UPS", "https://ups.com/track"}
            };
            for (Object[] c : carriers) {
                ps.setInt(1, (Integer) c[0]);
                ps.setString(2, (String) c[1]);
                ps.setString(3, (String) c[2]);
                ps.setString(4, (String) c[3]);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // product_attributes
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO product_attributes (id, code, name, type) VALUES (?, ?, ?, ?)")) {
            Object[][] attrs = {
                    {1, "COLOR", "Color", "select"}, {2, "SIZE", "Size", "select"},
                    {3, "WEIGHT", "Weight", "number"}, {4, "MATERIAL", "Material", "text"},
                    {5, "WARRANTY", "Warranty Period", "text"}
            };
            for (Object[] a : attrs) {
                ps.setInt(1, (Integer) a[0]);
                ps.setString(2, (String) a[1]);
                ps.setString(3, (String) a[2]);
                ps.setString(4, (String) a[3]);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // roles
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO roles (id, code, name) VALUES (?, ?, ?)")) {
            Object[][] roles = {
                    {1, "OWNER", "Owner"}, {2, "ADMIN", "Admin"}, {3, "STAFF", "Staff"}
            };
            for (Object[] r : roles) {
                ps.setInt(1, (Integer) r[0]);
                ps.setString(2, (String) r[1]);
                ps.setString(3, (String) r[2]);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // permissions
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO permissions (id, code, name) VALUES (?, ?, ?)")) {
            Object[][] perms = {
                    {1, "READ", "Read"}, {2, "WRITE", "Write"},
                    {3, "DELETE", "Delete"}, {4, "ADMIN", "Admin"},
                    {5, "EXPORT", "Export"}
            };
            for (Object[] p : perms) {
                ps.setInt(1, (Integer) p[0]);
                ps.setString(2, (String) p[1]);
                ps.setString(3, (String) p[2]);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        System.out.println("  [OK] Lookup tables seeded (7 tables)\n");
    }

    // ═══════════════════════════════════════════════════════════
    //  PER-TENANT DATA
    // ═══════════════════════════════════════════════════════════
    private void seedTenant(Connection conn, String tenant) throws SQLException {
        long catStart = System.currentTimeMillis();

        // Step 1: Get category/supplier/warehouse ID ranges for this tenant
        long[] catIds = seedCategories(conn, tenant);
        long[] supIds = seedSuppliers(conn, tenant);
        long[] whIds = seedWarehouses(conn, tenant);

        // Step 2: Products + Variants
        long[] prodIds = seedProductsAndVariants(conn, tenant, catIds, supIds);

        // Step 3: Customers
        long[] custIds = seedCustomers(conn, tenant);

        // Step 4: Orders + Items
        long[] orderIds = seedOrdersAndItems(conn, tenant, custIds, prodIds, whIds);

        // Step 5: Payments + Transactions
        long[] payIds = seedPaymentsAndTransactions(conn, tenant, orderIds);

        // Step 6: Invoices + Lines
        seedInvoicesAndLines(conn, tenant, orderIds);

        // Step 7: Order status history
        seedOrderStatusHistory(conn, tenant, orderIds);

        // Step 8: Coupons + Redemptions
        seedCoupons(conn, tenant, custIds, orderIds);

        // Step 9: Audit logs
        seedAuditLogs(conn, tenant, orderIds, custIds);

        System.out.printf("    - %s: %dms for all tables%n",
                tenant, System.currentTimeMillis() - catStart);
    }

    private long[] seedCategories(Connection conn, String tenant) throws SQLException {
        long[] ids = new long[10];
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO categories (tenant_id, name, slug) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 1; i <= 10; i++) {
                ps.setString(1, tenant);
                ps.setString(2, "Category " + i);
                ps.setString(3, "category-" + i);
                ps.addBatch();
            }
            ps.executeBatch();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                int i = 0;
                while (rs.next()) ids[i++] = rs.getLong(1);
            }
        }
        return ids;
    }

    private long[] seedSuppliers(Connection conn, String tenant) throws SQLException {
        long[] ids = new long[5];
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO suppliers (tenant_id, name, email, country_id) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 1; i <= 5; i++) {
                ps.setString(1, tenant);
                ps.setString(2, "Supplier " + i);
                ps.setString(3, "supplier" + i + "@" + tenant.toLowerCase() + ".com");
                ps.setInt(4, (i % 10) + 1);
                ps.addBatch();
            }
            ps.executeBatch();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                int i = 0;
                while (rs.next()) ids[i++] = rs.getLong(1);
            }
        }
        return ids;
    }

    private long[] seedWarehouses(Connection conn, String tenant) throws SQLException {
        long[] ids = new long[2];
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO warehouses (tenant_id, name, country_id) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 1; i <= 2; i++) {
                ps.setString(1, tenant);
                ps.setString(2, "Warehouse " + i);
                ps.setInt(3, i);
                ps.addBatch();
            }
            ps.executeBatch();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                int i = 0;
                while (rs.next()) ids[i++] = rs.getLong(1);
            }
        }
        return ids;
    }

    private long[] seedProductsAndVariants(Connection conn, String tenant,
                                           long[] catIds, long[] supIds) throws SQLException {
        long[] prodIds = new long[PRODUCTS_PER_TENANT];
        long[] varIds = new long[VARIANTS_PER_TENANT];
        int pCount = 0, vCount = 0;

        // Insert products in batches
        try (PreparedStatement pPs = conn.prepareStatement(
                "INSERT INTO products (tenant_id, category_id, supplier_id, sku, name, base_price, active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < PRODUCTS_PER_TENANT; i++) {
                pPs.setString(1, tenant);
                pPs.setLong(2, catIds[i % catIds.length]);
                pPs.setLong(3, supIds[i % supIds.length]);
                pPs.setString(4, String.format("P-%s-%06d", tenant, i));
                pPs.setString(5, "Product " + i);
                pPs.setBigDecimal(6, BigDecimal.valueOf(rnd.nextInt(10000) + 10)
                        .setScale(2, RoundingMode.HALF_UP));
                pPs.setBoolean(7, true);
                pPs.addBatch();

                if ((i + 1) % BATCH_SIZE == 0) {
                    pPs.executeBatch();
                    try (ResultSet rs = pPs.getGeneratedKeys()) {
                        while (rs.next()) prodIds[pCount++] = rs.getLong(1);
                    }
                }
            }
            pPs.executeBatch();
            try (ResultSet rs = pPs.getGeneratedKeys()) {
                while (rs.next() && pCount < PRODUCTS_PER_TENANT) prodIds[pCount++] = rs.getLong(1);
            }
        }

        // Insert variants (1 per product)
        try (PreparedStatement vPs = conn.prepareStatement(
                "INSERT INTO product_variants (tenant_id, product_id, sku, variant_name, price, active) " +
                "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < VARIANTS_PER_TENANT; i++) {
                vPs.setString(1, tenant);
                vPs.setLong(2, prodIds[i]);
                vPs.setString(3, String.format("V-%s-%06d", tenant, i));
                vPs.setString(4, "Standard");
                vPs.setBigDecimal(5, BigDecimal.valueOf(rnd.nextInt(10000) + 10)
                        .setScale(2, RoundingMode.HALF_UP));
                vPs.setBoolean(6, true);
                vPs.addBatch();

                if ((i + 1) % BATCH_SIZE == 0) {
                    vPs.executeBatch();
                    try (ResultSet rs = vPs.getGeneratedKeys()) {
                        while (rs.next() && vCount < VARIANTS_PER_TENANT) varIds[vCount++] = rs.getLong(1);
                    }
                }
            }
            vPs.executeBatch();
            try (ResultSet rs = vPs.getGeneratedKeys()) {
                while (rs.next() && vCount < VARIANTS_PER_TENANT) varIds[vCount++] = rs.getLong(1);
            }
        }

        return varIds; // used as product IDs for foreign keys
    }

    private long[] seedCustomers(Connection conn, String tenant) throws SQLException {
        long[] ids = new long[CUSTOMERS_PER_TENANT];
        int count = 0;

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO customers (tenant_id, email, full_name, country_id) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < CUSTOMERS_PER_TENANT; i++) {
                ps.setString(1, tenant);
                ps.setString(2, String.format("cust%d@%s.com", i, tenant.toLowerCase()));
                ps.setString(3, "Customer " + i);
                ps.setInt(4, (i % 10) + 1);
                ps.addBatch();

                if ((i + 1) % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        while (rs.next() && count < CUSTOMERS_PER_TENANT) ids[count++] = rs.getLong(1);
                    }
                }
            }
            ps.executeBatch();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next() && count < CUSTOMERS_PER_TENANT) ids[count++] = rs.getLong(1);
            }
        }
        return ids;
    }

    private long[] seedOrdersAndItems(Connection conn, String tenant,
                                       long[] custIds, long[] varIds,
                                       long[] whIds) throws SQLException {
        long[] orderIds = new long[ORDERS_PER_TENANT];
        int count = 0;
        String[] statuses = {"PENDING", "PAID", "SHIPPED", "DELIVERED", "CANCELLED"};

        try (PreparedStatement oPs = conn.prepareStatement(
                "INSERT INTO orders (tenant_id, customer_id, order_number, status, " +
                "subtotal, tax, shipping_cost, total, currency_id, placed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
             PreparedStatement iPs = conn.prepareStatement(
                "INSERT INTO order_items (tenant_id, order_id, variant_id, quantity, unit_price, line_total) " +
                "VALUES (?, ?, ?, ?, ?, ?)")) {

            for (int i = 0; i < ORDERS_PER_TENANT; i++) {
                BigDecimal sub = BigDecimal.valueOf(rnd.nextInt(1000) + 20).setScale(2, RoundingMode.HALF_UP);
                BigDecimal tax = sub.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal ship = BigDecimal.valueOf(rnd.nextInt(20) + 5).setScale(2, RoundingMode.HALF_UP);
                BigDecimal total = sub.add(tax).add(ship);

                oPs.setString(1, tenant);
                oPs.setLong(2, custIds[i % custIds.length]);
                oPs.setString(3, String.format("ORD-%s-%06d", tenant, i));
                oPs.setString(4, statuses[rnd.nextInt(statuses.length)]);
                oPs.setBigDecimal(5, sub);
                oPs.setBigDecimal(6, tax);
                oPs.setBigDecimal(7, ship);
                oPs.setBigDecimal(8, total);
                oPs.setInt(9, 1);
                oPs.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now().minusDays(rnd.nextInt(365))));
                oPs.addBatch();

                if ((i + 1) % BATCH_SIZE == 0) {
                    oPs.executeBatch();
                    try (ResultSet rs = oPs.getGeneratedKeys()) {
                        while (rs.next() && count < ORDERS_PER_TENANT) orderIds[count++] = rs.getLong(1);
                    }
                }
            }
            oPs.executeBatch();
            try (ResultSet rs = oPs.getGeneratedKeys()) {
                while (rs.next() && count < ORDERS_PER_TENANT) orderIds[count++] = rs.getLong(1);
            }

            // Insert items
            for (int i = 0; i < ORDERS_PER_TENANT; i++) {
                for (int j = 0; j < ITEMS_PER_ORDER; j++) {
                    int qty = rnd.nextInt(5) + 1;
                    BigDecimal price = BigDecimal.valueOf(rnd.nextInt(500) + 10)
                            .setScale(2, RoundingMode.HALF_UP);
                    iPs.setString(1, tenant);
                    iPs.setLong(2, orderIds[i]);
                    iPs.setLong(3, varIds[(i * ITEMS_PER_ORDER + j) % varIds.length]);
                    iPs.setInt(4, qty);
                    iPs.setBigDecimal(5, price);
                    iPs.setBigDecimal(6, price.multiply(BigDecimal.valueOf(qty)));
                    iPs.addBatch();
                }
                if ((i + 1) % BATCH_SIZE == 0) iPs.executeBatch();
            }
            iPs.executeBatch();
        }
        return orderIds;
    }

    private long[] seedPaymentsAndTransactions(Connection conn, String tenant,
                                                long[] orderIds) throws SQLException {
        long[] payIds = new long[ORDERS_PER_TENANT];
        int count = 0;

        try (PreparedStatement pPs = conn.prepareStatement(
                "INSERT INTO payments (tenant_id, order_id, method_id, amount, currency_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
             PreparedStatement tPs = conn.prepareStatement(
                "INSERT INTO payment_transactions (tenant_id, payment_id, transaction_ref, amount, status, gateway_response) " +
                "VALUES (?, ?, ?, ?, ?, ?)")) {

            for (int i = 0; i < ORDERS_PER_TENANT; i++) {
                BigDecimal amt = BigDecimal.valueOf(rnd.nextInt(2000) + 20).setScale(2, RoundingMode.HALF_UP);
                pPs.setString(1, tenant);
                pPs.setLong(2, orderIds[i]);
                pPs.setInt(3, rnd.nextInt(4) + 1);
                pPs.setBigDecimal(4, amt);
                pPs.setInt(5, 1);
                pPs.setString(6, rnd.nextBoolean() ? "SUCCESS" : "PENDING");
                pPs.addBatch();

                if ((i + 1) % BATCH_SIZE == 0) {
                    pPs.executeBatch();
                    try (ResultSet rs = pPs.getGeneratedKeys()) {
                        while (rs.next() && count < ORDERS_PER_TENANT) payIds[count++] = rs.getLong(1);
                    }
                }
            }
            pPs.executeBatch();
            try (ResultSet rs = pPs.getGeneratedKeys()) {
                while (rs.next() && count < ORDERS_PER_TENANT) payIds[count++] = rs.getLong(1);
            }

            // Transactions (1-2 per payment)
            for (int i = 0; i < ORDERS_PER_TENANT; i++) {
                for (int j = 0; j < 2; j++) {
                    tPs.setString(1, tenant);
                    tPs.setLong(2, payIds[i]);
                    tPs.setString(3, String.format("TXN-%s-%06d-%d", tenant, i, j));
                    tPs.setBigDecimal(4, BigDecimal.valueOf(rnd.nextInt(2000) + 20).setScale(2, RoundingMode.HALF_UP));
                    tPs.setString(5, j == 0 ? "CAPTURED" : "AUTHORIZED");
                    tPs.setString(6, "{\"gateway\":\"stripe\",\"code\":\"00\"}");
                    tPs.addBatch();
                }
                if ((i + 1) % BATCH_SIZE == 0) tPs.executeBatch();
            }
            tPs.executeBatch();
        }
        return payIds;
    }

    private void seedInvoicesAndLines(Connection conn, String tenant, long[] orderIds) throws SQLException {
        try (PreparedStatement iPs = conn.prepareStatement(
                "INSERT INTO invoices (tenant_id, order_id, invoice_number, total, currency_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
             PreparedStatement lPs = conn.prepareStatement(
                "INSERT INTO invoice_lines (tenant_id, invoice_id, description, quantity, unit_price, line_total) " +
                "VALUES (?, ?, ?, ?, ?, ?)")) {

            long[] invIds = new long[ORDERS_PER_TENANT];
            int count = 0;

            for (int i = 0; i < ORDERS_PER_TENANT; i++) {
                iPs.setString(1, tenant);
                iPs.setLong(2, orderIds[i]);
                iPs.setString(3, String.format("INV-%s-%06d", tenant, i));
                iPs.setBigDecimal(4, BigDecimal.valueOf(rnd.nextInt(2000) + 20).setScale(2, RoundingMode.HALF_UP));
                iPs.setInt(5, 1);
                iPs.setString(6, "ISSUED");
                iPs.addBatch();

                if ((i + 1) % BATCH_SIZE == 0) {
                    iPs.executeBatch();
                    try (ResultSet rs = iPs.getGeneratedKeys()) {
                        while (rs.next() && count < ORDERS_PER_TENANT) invIds[count++] = rs.getLong(1);
                    }
                }
            }
            iPs.executeBatch();
            try (ResultSet rs = iPs.getGeneratedKeys()) {
                while (rs.next() && count < ORDERS_PER_TENANT) invIds[count++] = rs.getLong(1);
            }

            for (int i = 0; i < ORDERS_PER_TENANT; i++) {
                for (int j = 0; j < 2; j++) {
                    lPs.setString(1, tenant);
                    lPs.setLong(2, invIds[i]);
                    lPs.setString(3, "Line item " + j);
                    lPs.setInt(4, 1);
                    lPs.setBigDecimal(5, BigDecimal.valueOf(50).setScale(2, RoundingMode.HALF_UP));
                    lPs.setBigDecimal(6, BigDecimal.valueOf(50).setScale(2, RoundingMode.HALF_UP));
                    lPs.addBatch();
                }
                if ((i + 1) % BATCH_SIZE == 0) lPs.executeBatch();
            }
            lPs.executeBatch();
        }
    }

    private void seedOrderStatusHistory(Connection conn, String tenant, long[] orderIds) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO order_status_history (tenant_id, order_id, from_status, to_status, notes) " +
                "VALUES (?, ?, ?, ?, ?)")) {
            String[] chain = {"PENDING", "PAID", "SHIPPED", "DELIVERED"};
            for (int i = 0; i < ORDERS_PER_TENANT; i++) {
                for (int j = 1; j < chain.length; j++) {
                    ps.setString(1, tenant);
                    ps.setLong(2, orderIds[i]);
                    ps.setString(3, chain[j - 1]);
                    ps.setString(4, chain[j]);
                    ps.setString(5, "Status changed");
                    ps.addBatch();
                }
                if ((i + 1) % BATCH_SIZE == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    private void seedCoupons(Connection conn, String tenant, long[] custIds, long[] orderIds) throws SQLException {
        long[] couponIds = new long[5];
        int count = 0;

        try (PreparedStatement cPs = conn.prepareStatement(
                "INSERT INTO coupons (tenant_id, code, discount_type, discount_value, max_uses, " +
                "valid_from, valid_until, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
             PreparedStatement rPs = conn.prepareStatement(
                "INSERT INTO coupon_redemptions (tenant_id, coupon_id, customer_id, order_id, discount_amount) " +
                "VALUES (?, ?, ?, ?, ?)")) {

            for (int i = 0; i < 5; i++) {
                cPs.setString(1, tenant);
                cPs.setString(2, String.format("COUP-%s-%02d", tenant, i));
                cPs.setString(3, rnd.nextBoolean() ? "PERCENT" : "FIXED");
                cPs.setBigDecimal(4, BigDecimal.valueOf(rnd.nextInt(50) + 5).setScale(2, RoundingMode.HALF_UP));
                cPs.setInt(5, 100);
                cPs.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now().minusDays(30)));
                cPs.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now().plusDays(30)));
                cPs.setBoolean(8, true);
                cPs.addBatch();
            }
            cPs.executeBatch();
            try (ResultSet rs = cPs.getGeneratedKeys()) {
                while (rs.next()) couponIds[count++] = rs.getLong(1);
            }

            // 25 redemptions per tenant
            for (int i = 0; i < 25; i++) {
                rPs.setString(1, tenant);
                rPs.setLong(2, couponIds[i % couponIds.length]);
                rPs.setLong(3, custIds[i % custIds.length]);
                rPs.setLong(4, orderIds[i % orderIds.length]);
                rPs.setBigDecimal(5, BigDecimal.valueOf(10).setScale(2, RoundingMode.HALF_UP));
                rPs.addBatch();
            }
            rPs.executeBatch();
        }
    }

    private void seedAuditLogs(Connection conn, String tenant,
                                long[] orderIds, long[] custIds) throws SQLException {
        String[] entities = {"order", "product", "payment"};
        String[] actions = {"CREATE", "UPDATE", "DELETE"};

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO audit_logs (tenant_id, user_id, entity_type, entity_id, action, changes) " +
                "VALUES (?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < 2000; i++) {
                ps.setString(1, tenant);
                ps.setLong(2, custIds[i % custIds.length]);
                ps.setString(3, entities[rnd.nextInt(entities.length)]);
                ps.setLong(4, orderIds[rnd.nextInt(orderIds.length)]);
                ps.setString(5, actions[rnd.nextInt(actions.length)]);
                ps.setString(6, "{\"field\":\"value\"}");
                ps.addBatch();
                if ((i + 1) % BATCH_SIZE == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    private void printSummary() throws SQLException {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  [DATA] Data Summary                                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        String[] tables = {"products", "product_variants", "customers", "orders",
                "order_items", "payments", "payment_transactions",
                "invoices", "invoice_lines", "audit_logs"};
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            long grand = 0;
            for (String t : tables) {
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + t)) {
                    if (rs.next()) {
                        long c = rs.getLong(1);
                        grand += c;
                        System.out.printf("  %-25s %,12d%n", t, c);
                    }
                }
            }
            System.out.printf("  %-25s %,12d%n", "TOTAL", grand);
        }
        System.out.println();
    }
}
