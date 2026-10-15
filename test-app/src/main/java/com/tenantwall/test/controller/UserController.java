package com.tenantwall.test.controller;

import com.tenantwall.test.entity.User;
import com.tenantwall.test.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "tenantwall Demo", description = "Test multi-tenant isolation and SQL filter injection")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @PostConstruct
    public void init() {
        userRepository.save(new User("Ahmed", "TENANT_A"));
        userRepository.save(new User("Mohamed", "TENANT_B"));
        userRepository.save(new User("Sara", "TENANT_A"));
        System.out.println("✅ Added 3 test users.");
    }

    // =========================================================
    // 1. Simple SELECT
    // =========================================================
    @Operation(summary = "Simple SELECT", description = "Tests basic SELECT with automatic tenant_id injection.")
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // =========================================================
    // 2. Native Query
    // =========================================================
    @Operation(summary = "Native Query with parameter", description = "Tests PreparedStatement interception with parameters.")
    @GetMapping("/users/native")
    public List<User> getUsersNative() {
        return userRepository.findUsersByNameNative("Ahmed");
    }

    // =========================================================
    // 3. JOIN
    // =========================================================
    @Operation(summary = "SELECT with JOIN", description = "Tests filter injection into JOIN queries.")
    @SuppressWarnings("unchecked")
    @GetMapping("/orders-with-join")
    public List<Object[]> getOrdersWithJoin() {
        String sql = "SELECT o.* FROM orders o JOIN customers c ON o.customer_id = c.id WHERE o.total > 100";
        try {
            return entityManager.createNativeQuery(sql).getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // =========================================================
    // 4. UNION
    // =========================================================
    @Operation(summary = "UNION query", description = "Tests filter injection into every part of UNION.")
    @SuppressWarnings("unchecked")
    @GetMapping("/orders-union")
    public List<Object[]> getOrdersUnion() {
        String sql = "SELECT product_name, total FROM orders WHERE total < 50 " +
                "UNION " +
                "SELECT product_name, total FROM orders WHERE total > 500";
        try {
            return entityManager.createNativeQuery(sql).getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // =========================================================
    // 5. Subquery (Comparison)
    // =========================================================
    @Operation(summary = "Subquery with comparison", description = "Tests filter injection inside and outside subqueries.")
    @SuppressWarnings("unchecked")
    @GetMapping("/orders-subquery")
    public List<Object[]> getOrdersSubquery() {
        String sql = "SELECT * FROM orders WHERE total > (SELECT AVG(total) FROM orders)";
        try {
            return entityManager.createNativeQuery(sql).getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // =========================================================
    // 6. Subquery (IN)
    // =========================================================
    @Operation(summary = "Subquery with IN", description = "Tests filter injection inside IN subquery.")
    @SuppressWarnings("unchecked")
    @GetMapping("/orders-subquery-in")
    public List<Object[]> getOrdersSubqueryIn() {
        String sql = "SELECT * FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE level = 'GOLD')";
        try {
            return entityManager.createNativeQuery(sql).getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // =========================================================
    // 7. Subquery (EXISTS)
    // =========================================================
    @Operation(summary = "Subquery with EXISTS", description = "Tests filter injection inside EXISTS subquery.")
    @SuppressWarnings("unchecked")
    @GetMapping("/orders-subquery-exists")
    public List<Object[]> getOrdersSubqueryExists() {
        String sql = "SELECT * FROM orders o WHERE EXISTS (SELECT 1 FROM customers c WHERE c.id = o.customer_id AND c.level = 'GOLD')";
        try {
            return entityManager.createNativeQuery(sql).getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // =========================================================
    // 8. Cache Stats
    // =========================================================
    @Operation(summary = "Cache statistics", description = "Shows Caffeine cache performance stats (hits, misses, hit rate).")
    @GetMapping("/cache-stats")
    public java.util.Map<String, Object> getCacheStats() {
        var stats = com.tenantwall.agent.SmartSqlModifier.getCacheStats();
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("hits", stats.hitCount());
        result.put("misses", stats.missCount());
        result.put("hitRate", String.format("%.2f%%", stats.hitRate() * 100));
        result.put("size", com.tenantwall.agent.SmartSqlModifier.getCacheSize());
        result.put("evictions", stats.evictionCount());
        result.put("loadTime", stats.totalLoadTime() / 1_000_000 + " ms");
        return result;
    }

    @Operation(summary = "Reset cache statistics", description = "Clears the cache and resets stats. Useful for benchmarking.")
    @PostMapping("/cache-reset")
    public Map<String, String> resetCache() {
        com.tenantwall.agent.SmartSqlModifier.clearCache();
        return Map.of("status", "cache cleared");
    }

    @GetMapping("/smart-stats")
    public Map<String, Object> getSmartStats() {
        Map<String, Object> result = new HashMap<>();
        var stats = com.tenantwall.agent.SmartSqlModifier.getCacheStats();
        result.put("cacheHits", stats.hitCount());
        result.put("cacheMisses", stats.missCount());
        result.put("cacheSize", com.tenantwall.agent.SmartSqlModifier.getCacheSize());
        result.put("pythonCalls", com.tenantwall.agent.SmartSqlModifier.getPythonCallCount());
        result.put("localCalls", com.tenantwall.agent.SmartSqlModifier.getLocalCallCount());
        result.put("fallbacks", com.tenantwall.agent.SmartSqlModifier.getFallbackCount());
        return result;
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/test-cte")
    public List<Object[]> testCte() {
        String sql = "WITH d AS (SELECT id, name, tenant_id FROM users) SELECT * FROM d";
        return entityManager.createNativeQuery(sql).getResultList();
    }

    @GetMapping("/users-pure-sql")
    public List<Object[]> getUsersPureSql() {
        org.hibernate.Session session = entityManager.unwrap(org.hibernate.Session.class);

        return session.doReturningWork(connection -> {
            List<Object[]> result = new java.util.ArrayList<>();
            try (java.sql.Statement stmt = connection.createStatement()) {
                java.sql.ResultSet rs = stmt.executeQuery(
                        "SELECT id, name, tenant_id FROM users WHERE tenant_id = 'TENANT_A'");
                while (rs.next()) {
                    result.add(new Object[] {
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("tenant_id")
                    });
                }
            }
            return result;
        });
    }

    @GetMapping("/users-pure-sql-old")
    public List<Object[]> getUsersPureSqlold() {
        // Random parameter value forces new PreparedStatement
        String sql = "SELECT id, name, tenant_id FROM users";
        return entityManager.createNativeQuery(sql).getResultList();
    }

    @GetMapping("/users-direct-jdbc")
    public List<Object[]> getUsersDirectJdbc() throws Exception {
        java.sql.Connection conn = entityManager.unwrap(java.sql.Connection.class);
        try (java.sql.PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, name, tenant_id FROM users")) {
            java.sql.ResultSet rs = stmt.executeQuery();
            List<Object[]> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new Object[] { rs.getLong(1), rs.getString(2), rs.getString(3) });
            }
            return result;
        }
    }
}