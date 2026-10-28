package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.update.Update;

import java.util.Locale;

/**
 * Handles UPDATE statements.
 *
 * <p>
 * Security note: an UPDATE without a WHERE clause would modify every row
 * in the table. We always inject the tenant filter in that case, so the
 * statement is at least scoped to a single tenant.
 */
public class UpdateHandler {

    public static String handle(Update update, String column, String value) {

        // ─── Excluded table check ───
        if (isExcluded(update.getTable())) {
            System.out.println("⏭️ [UpdateHandler] Table excluded, skipping: "
                    + update.getTable().getName());
            return update.toString();
        }

        Expression currentWhere = update.getWhere();

        if (SqlModifierUtils.alreadyHasFilter(currentWhere, column)) {
            System.out.println("✅ [UpdateHandler] Filter already exists, skipping");
        } else {
            if (currentWhere == null) {
                System.out.println("🛡️ [UpdateHandler] UPDATE without WHERE detected");
            }

            String prefix = extractPrefix(update.getTable());
            Expression newWhere = SqlModifierUtils.addFilterToWhere(
                    currentWhere, column, value, prefix);
            update.setWhere(newWhere);
        }

        if (update.getWhere() != null) {
            SqlModifierUtils.processExpression(update.getWhere(), column, value);
        }

        return update.toString();
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