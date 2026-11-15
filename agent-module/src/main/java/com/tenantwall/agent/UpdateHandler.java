package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.update.Update;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles UPDATE statements.
 *
 * <p>
 * Security note: an UPDATE without a WHERE clause would modify every row
 * in the table. We always inject the tenant filter in that case, so the
 * statement is at least scoped to a single tenant.
 */
public class UpdateHandler {
    private static final Logger log = LoggerFactory.getLogger("TenantWall");

    public static String handle(Update update, String column, String value) {

        // ─── Excluded table check ───
        if (isExcluded(update.getTable())) {
            log.debug("{} Table excluded, skipping: {}", LogTags.UPDATE, update.getTable().getName());
            return update.toString();
        }

        Expression currentWhere = update.getWhere();

        if (SqlModifierUtils.alreadyHasFilter(currentWhere, column)) {
            log.debug("{} Filter already exists, skipping", LogTags.UPDATE);
        } else {
            if (currentWhere == null) {
                log.warn("{} UPDATE without WHERE detected", LogTags.UPDATE);
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