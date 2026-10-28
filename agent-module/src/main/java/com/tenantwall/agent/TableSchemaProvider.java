package com.tenantwall.agent;

import java.util.List;

/**
 * SPI for resolving table column order.
 * Used only when INSERT has no explicit column list.
 *
 * Implementations MUST NOT open DB connections or perform I/O.
 * Return null if the schema for the table is unknown.
 */
public interface TableSchemaProvider {

    /**
     * @param tableName the table name as it appears in the SQL
     * @return ordered column names, or {@code null} if unknown
     */
    List<String> getColumns(String tableName);
}