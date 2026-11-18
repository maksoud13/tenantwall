package com.tenantwall.agent;

import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TableSchemaRegistryTest {

    private Map<String, String> savedSysProps;

    @BeforeEach
    void setUp() {
        savedSysProps = new HashMap<>();
        System.getProperties().stringPropertyNames().stream()
                .filter(k -> k.startsWith("tenantwall.schema."))
                .forEach(k -> savedSysProps.put(k, System.getProperty(k)));
        savedSysProps.keySet().forEach(System::clearProperty);
        TableSchemaRegistry.set(new NoOpTableSchemaProvider());
    }

    @AfterEach
    void tearDown() {
        System.getProperties().stringPropertyNames().stream()
                .filter(k -> k.startsWith("tenantwall.schema."))
                .toList()
                .forEach(System::clearProperty);
        savedSysProps.forEach(System::setProperty);
        TableSchemaRegistry.set(new NoOpTableSchemaProvider());
    }

    // ═══════════════════════════════════════════════════════════════
    // get / set
    // ═══════════════════════════════════════════════════════════════

    @Test
    void getNeverReturnsNull() {
        assertNotNull(TableSchemaRegistry.get());
    }

    @Test
    void setNullFallsBackToNoOp() {
        TableSchemaRegistry.set(null);
        assertInstanceOf(NoOpTableSchemaProvider.class, TableSchemaRegistry.get());
    }

    @Test
    void setCustomProviderIsReturnedByGet() {
        TableSchemaProvider custom = table -> List.of("c1");
        TableSchemaRegistry.set(custom);
        assertSame(custom, TableSchemaRegistry.get());
    }

    // ═══════════════════════════════════════════════════════════════
    // lookup — package-private delegation
    // ═══════════════════════════════════════════════════════════════

    @Test
    void lookupDelegatesToActiveProvider() {
        TableSchemaProvider custom = table -> table == null ? null : List.of("x");
        TableSchemaRegistry.set(custom);

        assertEquals(List.of("x"), TableSchemaRegistry.lookup("any"));
        assertNull(TableSchemaRegistry.lookup(null));
    }

    // ═══════════════════════════════════════════════════════════════
    // resolve() — 3 branches (via reflection)
    // ═══════════════════════════════════════════════════════════════

    private static TableSchemaProvider invokeResolve() throws Exception {
        Method m = TableSchemaRegistry.class.getDeclaredMethod("resolve");
        m.setAccessible(true);
        return (TableSchemaProvider) m.invoke(null);
    }

    @Test
    void resolveReturnsNoOpWhenNoSchemasConfigured() throws Exception {
        // No tenantwall.schema.* sys props, no classpath resource, no SPI
        TableSchemaProvider resolved = invokeResolve();
        assertInstanceOf(NoOpTableSchemaProvider.class, resolved);
    }

    @Test
    void resolveReturnsPropertiesProviderWhenSchemasPresent() throws Exception {
        System.setProperty("tenantwall.schema.orders", "id,total");

        TableSchemaProvider resolved = invokeResolve();

        assertInstanceOf(PropertiesTableSchemaProvider.class, resolved);
        assertEquals(List.of("id", "total"), resolved.getColumns("orders"));
    }

    @Test
    void resolveIgnoresSchemaFileKeyWhenDeciding() throws Exception {
        // Only the magic "file" key set → PropertiesTableSchemaProvider is empty
        // → resolver must fall back to NoOp
        System.setProperty("tenantwall.schema.file",
                System.getProperty("java.io.tmpdir") + "/no-such-schema-file.properties");

        TableSchemaProvider resolved = invokeResolve();

        assertInstanceOf(NoOpTableSchemaProvider.class, resolved);
    }
}