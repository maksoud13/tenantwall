package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.delete.Delete;

/**
 * Handles DELETE statements.
 *
 * <p>
 * Security note: a DELETE without a WHERE clause would wipe the entire
 * table. The filter injection makes it scoped to the current tenant only.
 */
public class DeleteHandler {

    public static String handle(Delete delete, String column, String value) {
        Expression currentWhere = delete.getWhere();

        if (SqlModifierUtils.alreadyHasFilter(currentWhere, column)) {
            System.out.println("✅ [DeleteHandler] Filter already exists, skipping");
        } else {
            if (currentWhere == null) {
                System.out.println("🛡️ [DeleteHandler] DELETE without WHERE detected");
            }

            Expression newWhere = SqlModifierUtils.addFilterToWhere(
                    currentWhere, column, value);
            delete.setWhere(newWhere);
        }

        if (delete.getWhere() != null) {
            SqlModifierUtils.processExpression(delete.getWhere(), column, value);
        }

        return delete.toString();
    }
}