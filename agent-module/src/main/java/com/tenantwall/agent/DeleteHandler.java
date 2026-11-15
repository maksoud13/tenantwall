package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles DELETE statements.
 *
 * <p>
 * Security note: a DELETE without a WHERE clause would wipe the entire
 * table. The filter injection makes it scoped to the current tenant only.
 */
public class DeleteHandler {
private static final Logger log = LoggerFactory.getLogger("TenantWall");
    public static String handle(Delete delete, String column, String value) {

        // ─── Excluded table check ───
        if (isExcluded(delete.getTable())) {
           log.debug("{} Table excluded, skipping: {}", LogTags.DELETE, delete.getTable().getName());
            return delete.toString();
        }

        Expression currentWhere = delete.getWhere();

        if (SqlModifierUtils.alreadyHasFilter(currentWhere, column)) {
           log.debug("{} Filter already exists, skipping", LogTags.DELETE);
        } else {
            if (currentWhere == null) {
               log.warn("{} DELETE without WHERE detected", LogTags.DELETE);
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