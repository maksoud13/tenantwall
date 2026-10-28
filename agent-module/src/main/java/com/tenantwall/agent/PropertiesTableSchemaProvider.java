package com.tenantwall.agent;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves table schemas from configuration. No DB access.
 *
 * <p>
 * Sources (all optional):
 * <ol>
 * <li>System properties:
 * {@code -Dtenantwall.schema.orders=id,customer_id,total,tenant_id}</li>
 * <li>File:
 * {@code -Dtenantwall.schema.file=/etc/tenantwall-schema.properties}</li>
 * <li>Classpath: {@code tenantwall-schema.properties}</li>
 * </ol>
 */
public class PropertiesTableSchemaProvider implements TableSchemaProvider {

    private final Map<String, List<String>> schemas = new ConcurrentHashMap<>();

    public PropertiesTableSchemaProvider() {
        loadFromSystemProperties();
        loadFromFile(System.getProperty("tenantwall.schema.file"));
        loadFromClasspath("tenantwall-schema.properties");
    }

    @Override
    public List<String> getColumns(String tableName) {
        if (tableName == null)
            return null;
        return schemas.get(tableName.toLowerCase(Locale.ROOT));
    }

    public void put(String table, List<String> columns) {
        schemas.put(table.toLowerCase(Locale.ROOT), columns);
    }

    public boolean isEmpty() {
        return schemas.isEmpty();
    }

    public Set<String> getKnownTables() {
        return Collections.unmodifiableSet(schemas.keySet());
    }

    private void loadFromSystemProperties() {
        System.getProperties().forEach((k, v) -> {
            String key = k.toString();
            String prefix = "tenantwall.schema.";
            if (key.startsWith(prefix) && !key.equals(prefix + "file")) {
                String table = key.substring(prefix.length());
                schemas.put(table.toLowerCase(Locale.ROOT), parseList(v.toString()));
            }
        });
    }

    private void loadFromFile(String path) {
        if (path == null || path.isBlank())
            return;
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            loadProps(in);
        } catch (Exception e) {
            System.err.println("⚠️ [TenantWall] Cannot load schema file " + path + ": " + e.getMessage());
        }
    }

    private void loadFromClasspath(String resource) {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resource)) {
            if (in != null)
                loadProps(in);
        } catch (Exception ignore) {
            /* classpath source is best-effort */ }
    }

    private void loadProps(InputStream in) throws Exception {
        Properties p = new Properties();
        p.load(in);
        for (String table : p.stringPropertyNames()) {
            schemas.put(table.toLowerCase(Locale.ROOT), parseList(p.getProperty(table)));
        }
    }

    private List<String> parseList(String csv) {
        List<String> out = new ArrayList<>();
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty())
                out.add(t);
        }
        return out;
    }
}