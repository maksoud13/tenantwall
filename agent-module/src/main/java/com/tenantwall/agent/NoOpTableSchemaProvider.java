package com.tenantwall.agent;

import java.util.List;

public class NoOpTableSchemaProvider implements TableSchemaProvider {
    @Override
    public List<String> getColumns(String tableName) {
        return null;
    }
}