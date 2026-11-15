package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

public class InsertHandler {

    private static final Logger log = LoggerFactory.getLogger("TenantWall");

    public static String handle(Insert insert, String column, String value) {

        // ─── Excluded table check ───
        if (isExcluded(insert.getTable())) {
            log.debug("{} Table excluded, skipping: {}", LogTags.INSERT, insert.getTable().getName());
            return insert.toString();
        }

        Select select = insert.getSelect();
        boolean isInsertSelect = select != null
                && !"Values".equals(select.getClass().getSimpleName());

        if (isInsertSelect) {
            log.debug("{} INSERT ... SELECT detected", LogTags.INSERT);
            SelectHandler.handle(select, column, value);
            return insert.toString();
        }

        return handleValues(insert, column, value);
    }

    private static String handleValues(Insert insert, String column, String value) {
        List<Column> columns = insert.getColumns();
        boolean hasExplicitColumns = columns != null && !columns.isEmpty();

        // ─── Mode 1: explicit column list ───
        if (hasExplicitColumns) {
            for (Column col : columns) {
                if (col.getColumnName().equalsIgnoreCase(column)) {
                    log.debug("{} Filter column already exists", LogTags.INSERT);
                    return insert.toString();
                }
            }
            columns.add(new Column(column));
            return appendToEachRow(insert, column, value, "appended at end");
        }

        // ─── No column list → need schema ───
        String tableName = insert.getTable().getName();
        List<String> schema = TenantwallConfig.getSchema(tableName);

        // Mode 2: schema hint configured
        if (schema != null && !schema.isEmpty()) {
            return injectAtPosition(insert, tableName, schema, column, value);
        }

        // Mode 3: append hack — opt-in only
        if (Boolean.getBoolean("tenantwall.insert.appendTenant")) {
            log.warn("{} No schema for '{}' → appending {} as LAST column " +
                    "(DANGEROUS: only correct if {} is the last column!)",
                    LogTags.INSERT, tableName, column, column);
            return appendToEachRow(insert, column, value, "appended at end (hack mode)");
        }

        // Default: skip with actionable error
        log.error("{} Cannot inject into INSERT without column list for table '{}'. " +
                "Fix one of these:\n" +
                "   (a) Use an explicit column list: INSERT INTO {} (a,b,c) VALUES (?,?,?)\n" +
                "   (b) Configure the schema: -Dtenantwall.schema.{}=col1,col2,...,{}\n" +
                "   (c) If {} is guaranteed to be the LAST column: " +
                "-Dtenantwall.insert.appendTenant=true",
                LogTags.INSERT, tableName, tableName, tableName, column, column);
        return insert.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // Mode 1 / 3: append tenant to end of every row
    // ═══════════════════════════════════════════════════════════════
    private static String appendToEachRow(Insert insert, String column,
            String value, String label) {
        try {
            List<Expression> items = getValuesRows(insert);
            if (items == null || items.isEmpty())
                return insert.toString();

            boolean multiRow = items.get(0) instanceof ExpressionList;
            if (multiRow) {
                for (Expression row : items) {
                    if (row instanceof ExpressionList) {
                        ((ExpressionList) row).getExpressions().add(new StringValue(value));
                    }
                }
            } else {
                items.add(new StringValue(value));
            }
            log.debug("{} {} = '{}' {} ({} row(s))",
                    LogTags.INSERT, column, value, label, multiRow ? items.size() : 1);
        } catch (Exception e) {
            log.warn("{} Append error", LogTags.INSERT, e);
        }
        return insert.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // Mode 2: inject at exact position based on schema
    // ═══════════════════════════════════════════════════════════════
    private static String injectAtPosition(Insert insert, String tableName,
            List<String> schema, String column, String value) {
        int totalColumns = schema.size();
        int tenantPos = -1;
        for (int i = 0; i < totalColumns; i++) {
            if (column.equalsIgnoreCase(schema.get(i))) {
                tenantPos = i;
                break;
            }
        }
        if (tenantPos < 0) {
            log.warn("{} Column '{}' not in configured schema for '{}' → skipping",
                    LogTags.INSERT, column, tableName);
            return insert.toString();
        }

        try {
            List<Expression> items = getValuesRows(insert);
            if (items == null || items.isEmpty())
                return insert.toString();

            boolean multiRow = items.get(0) instanceof ExpressionList;
            if (multiRow) {
                for (Expression row : items) {
                    if (row instanceof ExpressionList) {
                        mutateRow(((ExpressionList) row).getExpressions(),
                                totalColumns, tenantPos, value, tableName);
                    }
                }
            } else {
                mutateRow(items, totalColumns, tenantPos, value, tableName);
            }
            log.debug("{} Injected {} = '{}' at position {} in {}",
                    LogTags.INSERT, column, value, tenantPos + 1, tableName);
        } catch (Exception e) {
            log.warn("{} Inject error", LogTags.INSERT, e);
        }
        return insert.toString();
    }

    private static void mutateRow(List<Expression> row, int totalColumns,
            int tenantPos, String value, String tableName) {
        int count = row.size();
        if (count == totalColumns) {
            log.warn("{} Overriding user-supplied tenant at pos {} in {}",
                    LogTags.INSERT, tenantPos + 1, tableName);
            row.set(tenantPos, new StringValue(value));
        } else if (count == totalColumns - 1) {
            row.add(tenantPos, new StringValue(value));
        } else {
            log.warn("{} Column count mismatch for {}: got {}, expected {} or {} → skipping row",
                    LogTags.INSERT, tableName, count, totalColumns, totalColumns - 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Expression> getValuesRows(Insert insert) throws Exception {
        Method getValuesMethod = insert.getClass().getMethod("getValues");
        Object values = getValuesMethod.invoke(insert);
        if (values == null)
            return null;

        Method getExpressionsMethod = values.getClass().getMethod("getExpressions");
        Object outerListObj = getExpressionsMethod.invoke(values);
        if (!(outerListObj instanceof ExpressionList))
            return null;

        return ((ExpressionList) outerListObj).getExpressions();
    }

    private static boolean isExcluded(Table table) {
        if (table == null || table.getName() == null)
            return false;
        return TenantwallConfig.getExcludedTables()
                .contains(table.getName().toLowerCase(Locale.ROOT));
    }
}