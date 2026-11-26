package com.tenantwall.demo.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

public class SmartSqlModifier {

    private static final Cache<String, String> CACHE = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    public static String modify(String sql, String column, String value) {
        if (sql == null || column == null || value == null) return sql;
        if (column.isEmpty() || value.isEmpty()) return sql;

        String cacheKey = sql + "|" + column + "|" + value;

        // Check cache
        String cached = CACHE.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        boolean complex = QueryAnalyzer.isComplex(sql);
        String result = null;

        if (complex) {
            result = tryPython(sql, column, value);
            if (result == null) {
                // Fallback
                System.out.println("Python failed, falling back to local");
                result = tryLocal(sql, column, value);
            }
        } else {
            result = tryLocal(sql, column, value);
        }

        if (result == null) result = sql;

        // Cache
        if (!result.equals(sql)) {
            CACHE.put(cacheKey, result);
        }

        return result;
    }

    private static String tryPython(String sql, String column, String value) {
        try {
            String dialect = System.getenv().getOrDefault("TENANTWALL_DIALECT", "mysql");
            String result = PythonServiceClient.modify(sql, column, value, dialect);
            return result;
        } catch (Exception e) {
            System.err.println("Python service error: " + e.getMessage());
            return null;
        }
    }

    private static String tryLocal(String sql, String column, String value) {
        try {
            return SqlModifier.addFilter(sql, column, value);
        } catch (Exception e) {
            System.err.println("Local modifier error: " + e.getMessage());
            return null;
        }
    }

    public static long getCacheSize() {
        return CACHE.estimatedSize();
    }
}