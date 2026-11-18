package com.tenantwall.agent;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesTableSchemaProviderTest {

    @TempDir
    Path tempDir;

    private Map<String, String> savedSysProps;

    @BeforeEach
    void saveAndClearSysProps() {
        savedSysProps = new HashMap<>();
        System.getProperties().stringPropertyNames().stream()
                .filter(k -> k.startsWith("tenantwall.schema."))
                .forEach(k -> savedSysProps.put(k, System.getProperty(k)));
        savedSysProps.keySet().forEach(System::clearProperty);
    }

    @AfterEach
    void restoreSysProps() {
        System.getProperties().stringPropertyNames().stream()
                .filter(k -> k.startsWith("tenantwall.schema."))
                .toList()
                .forEach(System::clearProperty);
        savedSysProps.forEach(System::setProperty);
    }

    // ═══════════════════════════════════════════════════════════════
    // Constructor — system properties source
    // ═══════════════════════════════════════════════════════════════

    @Test
    void constructorLoadsFromSystemProperties() {
        System.setProperty("tenantwall.schema.orders", "id, customer_id, total");
        System.setProperty("tenantwall.schema.users", "id,email");

        PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();

        assertEquals(List.of("id", "customer_id", "total"), p.getColumns("orders"));
        assertEquals(List.of("id", "email"), p.getColumns("users"));
        assertFalse(p.isEmpty());
        assertEquals(Set.of("orders", "users"), p.getKnownTables());
    }

    @Test
    void constructorIgnoresSchemaFileKey() {
        System.setProperty("tenantwall.schema.file",
                tempDir.resolve("nonexistent.properties").toString());

        PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();

        // "file" is a magic key, must NOT be treated as a table name
        assertNull(p.getColumns("file"));
        assertTrue(p.isEmpty());
    }

    @Test
    void constructorSkipsBlankAndExtraCommasInValues() {
        System.setProperty("tenantwall.schema.orders", "  id , , total ,,  ");

        PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();

        assertEquals(List.of("id", "total"), p.getColumns("orders"));
    }

    @Test
    void constructorWithNoSourcesYieldsEmpty() {
        PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();
        assertTrue(p.isEmpty());
        assertTrue(p.getKnownTables().isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════
    // Constructor — file source
    // ═══════════════════════════════════════════════════════════════

    @Test
    void constructorLoadsFromFile() throws Exception {
        Path file = tempDir.resolve("schema.properties");
        try (OutputStream out = Files.newOutputStream(file)) {
            Properties props = new Properties();
            props.setProperty("orders", "id,total,tenant_id");
            props.setProperty("users", "id,email");
            props.store(out, null);
        }
        System.setProperty("tenantwall.schema.file", file.toString());

        PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();

        assertEquals(List.of("id", "total", "tenant_id"), p.getColumns("orders"));
        assertEquals(List.of("id", "email"), p.getColumns("users"));
    }

    @Test
    void constructorWithMissingFileDoesNotThrow() {
        System.setProperty("tenantwall.schema.file",
                tempDir.resolve("does-not-exist.properties").toString());

        assertDoesNotThrow(PropertiesTableSchemaProvider::new);
        assertTrue(new PropertiesTableSchemaProvider().isEmpty());
    }

    @Test
    void constructorWithBlankFilePathSkipsFileLoading() {
        System.setProperty("tenantwall.schema.file", "   ");

        PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();
        assertTrue(p.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════
    // Constructor — classpath source (via custom TCCL)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void constructorLoadsFromClasspath() throws Exception {
        Path cpDir = tempDir.resolve("cp");
        Files.createDirectories(cpDir);
        Path file = cpDir.resolve("tenantwall-schema.properties");
        try (OutputStream out = Files.newOutputStream(file)) {
            Properties props = new Properties();
            props.setProperty("products", "id,name,tenant_id");
            props.store(out, null);
        }

        try (URLClassLoader cl = new URLClassLoader(
                new URL[] { cpDir.toUri().toURL() },
                Thread.currentThread().getContextClassLoader())) {

            Thread t = Thread.currentThread();
            ClassLoader prev = t.getContextClassLoader();
            try {
                t.setContextClassLoader(cl);
                PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();
                assertEquals(List.of("id", "name", "tenant_id"), p.getColumns("products"));
            } finally {
                t.setContextClassLoader(prev);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    @Test
    void getColumnsIsCaseInsensitive() {
        System.setProperty("tenantwall.schema.Orders", "id");

        PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();

        assertEquals(List.of("id"), p.getColumns("orders"));
        assertEquals(List.of("id"), p.getColumns("ORDERS"));
    }

    @Test
    void getColumnsNullReturnsNull() {
        PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();
        assertNull(p.getColumns(null));
    }

    @Test
    void getColumnsUnknownTableReturnsNull() {
        PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();
        assertNull(p.getColumns("never-configured"));
    }

    @Test
    void putOverridesAndNormalizesKey() {
        PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();
        p.put("PRODUCTS", List.of("id", "name"));
        assertEquals(List.of("id", "name"), p.getColumns("products"));
        assertEquals(List.of("id", "name"), p.getColumns("Products"));

        p.put("products", List.of("id"));
        assertEquals(List.of("id"), p.getColumns("products"));
    }

    @Test
    void isEmptyReflectsInternalState() {
        PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();
        assertTrue(p.isEmpty());
        p.put("t", List.of("c"));
        assertFalse(p.isEmpty());
    }

    @Test
    void getKnownTablesIsUnmodifiable() {
        System.setProperty("tenantwall.schema.t1", "a,b");
        PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();

        Set<String> tables = p.getKnownTables();
        assertEquals(Set.of("t1"), tables);
        assertThrows(UnsupportedOperationException.class, () -> tables.add("x"));
    }

    @Test
    void combinedSourcesLastWriteWins() throws Exception {
        // sys property first...
        System.setProperty("tenantwall.schema.orders", "from-sys");
        // ...then file overrides
        Path file = tempDir.resolve("schema.properties");
        try (OutputStream out = Files.newOutputStream(file)) {
            Properties props = new Properties();
            props.setProperty("orders", "from-file");
            props.store(out, null);
        }
        System.setProperty("tenantwall.schema.file", file.toString());

        PropertiesTableSchemaProvider p = new PropertiesTableSchemaProvider();

        // Constructor order: sysProps → file → classpath. File wins over sys prop.
        assertEquals(List.of("from-file"), p.getColumns("orders"));
    }
}