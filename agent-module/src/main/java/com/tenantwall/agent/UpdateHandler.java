package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.update.Update;

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
        Expression currentWhere = update.getWhere();

        if (SqlModifierUtils.alreadyHasFilter(currentWhere, column)) {
            System.out.println("✅ [UpdateHandler] Filter already exists, skipping");
        } else {
            if (currentWhere == null) {
                System.out.println("🛡️ [UpdateHandler] UPDATE without WHERE detected");
            }

            Expression newWhere = SqlModifierUtils.addFilterToWhere(
                    currentWhere, column, value);
            update.setWhere(newWhere);
        }

        // Process subqueries in WHERE
        if (update.getWhere() != null) {
            SqlModifierUtils.processExpression(update.getWhere(), column, value);
        }

        return update.toString();
    }
}