package com.tenantwall.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Holds the active {@link TableSchemaProvider}.
 * Resolution order:
 * 1. {@link ServiceLoader} (user can ship their own implementation)
 * 2. {@link PropertiesTableSchemaProvider} if any schema is configured
 * 3. {@link NoOpTableSchemaProvider}
 */
public final class TableSchemaRegistry {

    private static final Logger log = LoggerFactory.getLogger("TenantWall");

    private static volatile TableSchemaProvider provider = resolve();

    private TableSchemaRegistry() {
    }

    public static TableSchemaProvider get() {
        return provider;
    }

    public static void set(TableSchemaProvider p) {
        provider = (p != null) ? p : new NoOpTableSchemaProvider();
    }

    private static TableSchemaProvider resolve() {
        // 1) ServiceLoader — user-supplied SPI
        for (TableSchemaProvider p : ServiceLoader.load(TableSchemaProvider.class)) {
            log.info("{} Using custom TableSchemaProvider: {}",
                    LogTags.CONFIG, p.getClass().getName());
            return p;
        }
        // 2) Properties-based
        PropertiesTableSchemaProvider props = new PropertiesTableSchemaProvider();
        if (!props.isEmpty()) {
            log.info("{} Loaded schemas for: {}",
                    LogTags.CONFIG, props.getKnownTables());
            return props;
        }
        // 3) NoOp — safe default
        log.info("{} No table schemas configured. INSERT without column list will be skipped.",
                LogTags.CONFIG);
        return new NoOpTableSchemaProvider();
    }

    // Test hook
    static List<String> lookup(String table) {
        return provider.getColumns(table);
    }
}