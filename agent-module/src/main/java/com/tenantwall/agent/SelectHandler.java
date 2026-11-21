package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.schema.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Handles SELECT statements:
 * <ul>
 * <li>Simple SELECT → {@link PlainSelect}</li>
 * <li>UNION / INTERSECT / EXCEPT → {@link SetOperationList}</li>
 * <li>Parenthesized SELECT → {@link ParenthesedSelect}</li>
 * </ul>
 *
 * <h2>Exclusion semantics</h2>
 *
 * <p>
 * Table exclusion is evaluated <b>per physical FROM item</b>, not per query.
 * A SELECT is never skipped wholesale — each individual FROM is decided
 * separately based on whether it is:
 * <ul>
 * <li>A <b>physical table</b> that is listed in
 * {@code tenantwall.filter.excludedTables} → no filter</li>
 * <li>A <b>CTE reference</b> (name matches a CTE alias in scope) → no filter
 * on the reference itself (CTE body is filtered separately)</li>
 * <li>A <b>physical table</b> not excluded → tenant filter is injected</li>
 * <li>A <b>subquery</b> → its SELECT is processed recursively</li>
 * </ul>
 *
 * <p>
 * <b>Security rationale:</b> an early-return "skip whole query" approach
 * would let an attacker bypass filtering by defining a CTE whose alias matches
 * an excluded table name. E.g. with {@code excludedTables=countries}:
 * 
 * <pre>{@code
 * WITH countries AS (SELECT * FROM users) SELECT * FROM countries
 * }</pre>
 * 
 * If the outer {@code FROM countries} were treated as a physical excluded
 * table, the entire query (including the {@code users} body) would skip
 * filtering. The per-item model prevents this.
 */
public class SelectHandler {
    private static final Logger log = LoggerFactory.getLogger("TenantWall");

    public static String handle(Select select, String column, String value) {
        process(select, column, value, Collections.emptySet());
        return select.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // Recursive processing with CTE context propagation
    // ═══════════════════════════════════════════════════════════════

    /**
     * @param inheritedCtes CTE aliases visible from the parent scope.
     *                      CTEs are visible to nested subqueries, so we must
     *                      thread them through the recursion to avoid
     *                      misidentifying a CTE reference as a physical table.
     */
    private static void process(Select select, String column, String value,
            Set<String> inheritedCtes) {
        if (select instanceof PlainSelect ps) {
            Set<String> ctes = mergeCteContext(inheritedCtes, ps.getWithItemsList());
            processWithItems(ps.getWithItemsList(), column, value, inheritedCtes);
            handlePlainSelect(ps, column, value, ctes);
        } else if (select instanceof SetOperationList sol) {
            Set<String> ctes = mergeCteContext(inheritedCtes, sol.getWithItemsList());
            processWithItems(sol.getWithItemsList(), column, value, inheritedCtes);
            List<Select> branches = sol.getSelects();
            if (branches != null) {
                for (Select branch : branches) {
                    process(branch, column, value, ctes);
                }
            }
        } else if (select instanceof ParenthesedSelect ps) {
            if (ps.getSelect() != null) {
                process(ps.getSelect(), column, value, inheritedCtes);
            }
        } else {
            log.warn("{} Unsupported Select type: {}",
                    LogTags.SELECT, select.getClass().getSimpleName());
        }
    }

    private static Set<String> mergeCteContext(Set<String> inherited, List<WithItem> local) {
        if (local == null || local.isEmpty()) {
            return inherited;
        }
        Set<String> result = new HashSet<>(inherited);
        for (WithItem item : local) {
            if (item.getAlias() != null && item.getAlias().getName() != null) {
                result.add(item.getAlias().getName().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    /**
     * Processes CTE bodies in definition order. Each CTE's body sees only
     * CTEs defined <b>before</b> it (standard non-recursive SQL semantics).
     */
    private static void processWithItems(List<WithItem> withItems, String column,
            String value, Set<String> inheritedCtes) {
        if (withItems == null || withItems.isEmpty()) {
            return;
        }
        Set<String> visibleCtes = new HashSet<>(inheritedCtes);
        for (WithItem item : withItems) {
            if (item.getSelect() != null) {
                log.debug("{} Processing CTE: {}", LogTags.SELECT, item.getAlias());
                process(item.getSelect(), column, value, visibleCtes);
            }
            // After processing, this CTE's alias becomes visible to later CTEs
            if (item.getAlias() != null && item.getAlias().getName() != null) {
                visibleCtes.add(item.getAlias().getName().toLowerCase(Locale.ROOT));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PlainSelect handling
    // ═══════════════════════════════════════════════════════════════

    private static void handlePlainSelect(PlainSelect plainSelect, String column,
            String value, Set<String> cteContext) {
        FromItem from = plainSelect.getFromItem();

        // ─── CRITICAL: recurse into FROM subqueries FIRST ───
        // The outer subquery alias (e.g. "AS audit_log") is a virtual relation,
        // not a physical table — it must not receive a WHERE filter. But the
        // tables INSIDE the subquery are physical and MUST be filtered.
        // This was the bug: original code never recursed into FROM subqueries,
        // so SELECT * FROM (SELECT * FROM users) AS x leaked users unfiltered.
        if (from instanceof ParenthesedSelect ps && ps.getSelect() != null) {
            process(ps.getSelect(), column, value, cteContext);
        }
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                FromItem right = join.getRightItem();
                if (right instanceof LateralSubSelect lat && lat.getSelect() != null) {
                    // LATERAL in FROM — filter its body recursively
                    process(lat.getSelect(), column, value, cteContext);
                } else if (right instanceof ParenthesedSelect ps && ps.getSelect() != null) {
                    process(ps.getSelect(), column, value, cteContext);
                }
            }
        }
        // ─── Decide whether to add WHERE filter at the OUTER level ───
        if (shouldFilterFromItem(from, cteContext)) {
            String fromAlias = extractFromAlias(plainSelect);
            if (SqlModifierUtils.alreadyHasFilter(plainSelect.getWhere(), column)) {
                log.debug("{} Filter already exists, skipping WHERE", LogTags.SELECT);
            } else {
                Expression newWhere = SqlModifierUtils.addFilterToWhere(
                        plainSelect.getWhere(), column, value, fromAlias);
                plainSelect.setWhere(newWhere);
            }
        } else {
            log.debug("{} FROM item skipped (excluded/CTE/subquery): {}",
                    LogTags.SELECT, from);
        }
          SqlModifierUtils.processSelectItems(plainSelect, column, value);

        // ─── Process subqueries inside WHERE ───
        if (plainSelect.getWhere() != null) {
            SqlModifierUtils.processExpression(plainSelect.getWhere(), column, value);
        }

        // ─── Process subqueries inside JOIN ON expressions ───
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                Collection<Expression> onExprs = join.getOnExpressions();
                if (onExprs != null) {
                    for (Expression onExpr : onExprs) {
                        SqlModifierUtils.processExpression(onExpr, column, value);
                    }
                }
            }
        }
    }

    /**
     * Decides whether the WHERE tenant filter should be added for the FROM item.
     *
     * @return {@code false} for CTE references, excluded physical tables, and
     *         subqueries (subqueries are handled recursively); {@code true}
     *         for plain physical tables that are not excluded.
     */
    private static boolean shouldFilterFromItem(FromItem from, Set<String> cteContext) {
        if (from == null) {
            return false;
        }
        if (!(from instanceof Table table)) {
            // Subquery in FROM — its own SELECT is processed recursively
            return false;
        }
        String name = table.getName();
        if (name == null || name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);

        // CTE reference — virtual relation, no physical tenant_id column
        if (cteContext.contains(lower)) {
            return false;
        }

        // Explicitly excluded physical table
        if (TenantwallConfig.getExcludedTables().contains(lower)) {
            return false;
        }

        return true;
    }

    private static String extractFromAlias(PlainSelect ps) {
        FromItem from = ps.getFromItem();
        if (!(from instanceof Table t)) {
            return null;
        }
        if (t.getAlias() != null && t.getAlias().getName() != null) {
            return t.getAlias().getName();
        }
        return t.getName();
    }
}