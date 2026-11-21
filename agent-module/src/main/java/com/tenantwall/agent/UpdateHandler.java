package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles UPDATE statements.
 *
 * <h2>Exclusion semantics</h2>
 *
 * <p>
 * When the target table is excluded, the tenant filter is NOT injected
 * on the WHERE clause. However, any subqueries inside the WHERE clause or
 * SET expressions are still processed recursively — their tables must be
 * filtered to prevent cross-tenant data leakage through subquery results.
 *
 * <p>
 * Security note: an UPDATE without a WHERE clause would modify every row
 * in the table. We always inject the tenant filter in that case, so the
 * statement is at least scoped to a single tenant.
 */
public class UpdateHandler {
    private static final Logger log = LoggerFactory.getLogger("TenantWall");

    public static String handle(Update update, String column, String value) {
        boolean targetExcluded = isExcluded(update.getTable());

        if (targetExcluded) {
            log.debug("{} Target table excluded, skipping filter injection: {}",
                    LogTags.UPDATE, update.getTable().getName());
        } else {
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
        }

        // ─── ALWAYS process WHERE subqueries (even when target excluded) ───
        if (update.getWhere() != null) {
            SqlModifierUtils.processExpression(update.getWhere(), column, value);
        }

        // ─── ALWAYS process SET subqueries ───
        processSetExpressions(update, column, value);

        return update.toString();
    }

    /**
     * Processes subqueries inside SET expressions.
     *
     * <p>
     * {@code UpdateSet.getValues()} returns an {@link ExpressionList}, not a
     * single {@link Expression}. Calling {@code processExpression()} on it
     * directly is a no-op — the subquery inside is never visited. We must
     * unwrap the list and process each inner expression individually.
     *
     * <p>
     * Uses reflection to stay compatible with future JSqlParser versions
     * where the wrapping type may change.
     */
    private static void processSetExpressions(Update update, String column, String value) {
        try {
            List<UpdateSet> updateSets = update.getUpdateSets();
            if (updateSets == null)
                return;
            for (UpdateSet us : updateSets) {
                Object raw = us.getValues();
                if (raw == null)
                    continue;

                boolean handled = false;
                try {
                    java.lang.reflect.Method m = raw.getClass().getMethod("getExpressions");
                    Object inner = m.invoke(raw);
                    if (inner instanceof java.util.List<?> list) {
                        for (Object e : list) {
                            if (e instanceof Expression expr) {
                                SqlModifierUtils.processExpression(expr, column, value);
                            }
                        }
                        handled = true;
                    }
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
                    // Not an ExpressionList — fall through
                }

                if (!handled && raw instanceof Expression expr) {
                    SqlModifierUtils.processExpression(expr, column, value);
                }
            }
        } catch (RuntimeException e) {
            log.warn("{} SET expression processing failed: {}", LogTags.UPDATE, e.getMessage());
        }
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