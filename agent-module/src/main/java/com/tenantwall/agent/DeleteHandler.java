package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;

import java.util.Locale;

/**
 * Handles DELETE statements.
 *
 * <p>
 * Security note: a DELETE without a WHERE clause would wipe the entire
 * table. The filter injection makes it scoped to the current tenant only.
 */
public class DeleteHandler {

    public static String handle(Delete delete, String column, String value) {

        // ─── Excluded table check ───
        if (isExcluded(delete.getTable())) {
            System.out.println("⏭️ [DeleteHandler] Table excluded, skipping: "
                    + delete.getTable().getName());
            return delete.toString();
        }

        Expression currentWhere = delete.getWhere();

        if (SqlModifierUtils.alreadyHasFilter(currentWhere, column)) {
            System.out.println("✅ [DeleteHandler] Filter already exists, skipping");
        } else {
            if (currentWhere == null) {
                System.out.println("🛡️ [DeleteHandler] DELETE without WHERE detected");
            }

            String prefix = extractPrefix(delete.getTable());
            Expression newWhere = SqlModifierUtils.addFilterToWhere(
                    currentWhere, column, value, prefix);
            delete.setWhere(newWhere);
        }

        if (delete.getWhere() != null) {
            SqlModifierUtils.processExpression(delete.getWhere(), column, value);
        }

        return delete.toString();
    }

    private static String extractPrefix(Table table) {
        if (table == null)
            return null;
        if (table.getAlias() != null && table.getAlias().getName() != null) {
            return table.getAlias().getName();
        }
        return table.getName();
    }

    private static boolean isExcluded(Table table) {
        if (table == null || table.getName() == null)
            return false;
        return TenantwallConfig.getExcludedTables()
                .contains(table.getName().toLowerCase(Locale.ROOT));
    }
}