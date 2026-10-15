package com.tenantwall.demo.service;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;

import java.lang.reflect.Method;
import java.util.List;

public class InsertHandler {

    public static String handle(Insert insert, String column, String value) {
        Select select = insert.getSelect();

        // ⚠️ JSqlParser 4.9: getSelect() may return a "Values" node for INSERT VALUES.
        // We detect that by the class simple name to avoid version-specific imports.
        boolean isInsertSelect = select != null
                && !"Values".equals(select.getClass().getSimpleName());

        if (isInsertSelect) {
            System.out.println("🟢 [InsertHandler] INSERT ... SELECT detected");
            SelectHandler.handle(select, column, value);
            return insert.toString();
        }

        // ─── INSERT ... VALUES ───
        return handleValues(insert, column, value);
    }

    private static String handleValues(Insert insert, String column, String value) {
        List<Column> columns = insert.getColumns();
        if (columns == null || columns.isEmpty()) {
            System.out.println("⚠️ [InsertHandler] INSERT without column list → skipping");
            return insert.toString();
        }

        // Check if column already exists
        for (Column col : columns) {
            if (col.getColumnName().equalsIgnoreCase(column)) {
                System.out.println("✅ [InsertHandler] Filter column already exists");
                return insert.toString();
            }
        }

        // Add the filter column
        columns.add(new Column(column));

        // Use reflection to call getValues()
        try {
            Method getValuesMethod = insert.getClass().getMethod("getValues");
            Object values = getValuesMethod.invoke(insert);

            if (values == null) {
                System.out.println("⚠️ [InsertHandler] No VALUES clause");
                return insert.toString();
            }

            Method getExpressionsMethod = values.getClass().getMethod("getExpressions");
            Object outerListObj = getExpressionsMethod.invoke(values);

            if (!(outerListObj instanceof ExpressionList)) {
                System.out.println("⚠️ [InsertHandler] Unexpected Values structure");
                return insert.toString();
            }

            ExpressionList outerList = (ExpressionList) outerListObj;
            List<Expression> items = outerList.getExpressions();

            if (items == null || items.isEmpty()) {
                return insert.toString();
            }

            // Single row vs multi row
            boolean multiRow = items.get(0) instanceof ExpressionList;

            if (multiRow) {
                for (Expression row : items) {
                    if (row instanceof ExpressionList) {
                        ((ExpressionList) row).getExpressions()
                                .add(new StringValue(value));
                    }
                }
            } else {
                items.add(new StringValue(value));
            }

            System.out.println("✅ [InsertHandler] Added " + column + " = '" + value + "'");

        } catch (NoSuchMethodException e) {
            System.err.println("⚠️ [InsertHandler] getValues() not found: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("⚠️ [InsertHandler] Error: " + e.getMessage());
        }

        return insert.toString();
    }
}