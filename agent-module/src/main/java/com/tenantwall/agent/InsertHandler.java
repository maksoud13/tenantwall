package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

public class InsertHandler {

    public static String handle(Insert insert, String column, String value) {

        // ─── Excluded table check ───
        if (isExcluded(insert.getTable())) {
            System.out.println("⏭️ [InsertHandler] Table excluded, skipping: "
                    + insert.getTable().getName());
            return insert.toString();
        }

        Select select = insert.getSelect();
        boolean isInsertSelect = select != null
                && !"Values".equals(select.getClass().getSimpleName());

        if (isInsertSelect) {
            System.out.println("🟢 [InsertHandler] INSERT ... SELECT detected");
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
                    System.out.println("✅ [InsertHandler] Filter column already exists");
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
            System.err.println("⚠️ [InsertHandler] No schema for '" + tableName
                    + "' → appending " + column + " as LAST column "
                    + "(DANGEROUS: only correct if " + column + " is the last column!)");
            return appendToEachRow(insert, column, value, "appended at end (hack mode)");
        }

        // Default: skip with actionable error
        System.err.println("❌ [InsertHandler] Cannot inject into INSERT without column list "
                + "for table '" + tableName + "'. Fix one of these:\n"
                + "   (a) Use an explicit column list: INSERT INTO " + tableName
                + " (a,b,c) VALUES (?,?,?)\n"
                + "   (b) Configure the schema: -Dtenantwall.schema." + tableName
                + "=col1,col2,...," + column + "\n"
                + "   (c) If " + column + " is guaranteed to be the LAST column: "
                + "-Dtenantwall.insert.appendTenant=true");
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
            System.out.println("✅ [InsertHandler] " + column + " = '" + value + "' "
                    + label + " (" + (multiRow ? items.size() : 1) + " row(s))");
        } catch (Exception e) {
            System.err.println("⚠️ [InsertHandler] append error: " + e.getMessage());
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
            System.err.println("⚠️ [InsertHandler] Column '" + column
                    + "' not in configured schema for '" + tableName + "' → skipping");
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
            System.out.println("✅ [InsertHandler] Injected " + column + " = '" + value
                    + "' at position " + (tenantPos + 1) + " in " + tableName);
        } catch (Exception e) {
            System.err.println("⚠️ [InsertHandler] inject error: " + e.getMessage());
        }
        return insert.toString();
    }

    private static void mutateRow(List<Expression> row, int totalColumns,
            int tenantPos, String value, String tableName) {
        int count = row.size();
        if (count == totalColumns) {
            System.out.println("🔒 [InsertHandler] Overriding user-supplied tenant at pos "
                    + (tenantPos + 1) + " in " + tableName);
            row.set(tenantPos, new StringValue(value));
        } else if (count == totalColumns - 1) {
            row.add(tenantPos, new StringValue(value));
        } else {
            System.err.println("⚠️ [InsertHandler] Column count mismatch for " + tableName
                    + ": got " + count + ", expected " + totalColumns
                    + " or " + (totalColumns - 1) + " → skipping row");
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