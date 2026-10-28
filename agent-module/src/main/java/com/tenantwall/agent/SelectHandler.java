package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.schema.Table;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Handles SELECT statements:
 * <ul>
 * <li>Simple SELECT → {@link PlainSelect}</li>
 * <li>UNION / INTERSECT / EXCEPT → {@link SetOperationList}</li>
 * <li>Parenthesized SELECT → {@link ParenthesedSelect}</li>
 * </ul>
 */
public class SelectHandler {

    public static String handle(Select select, String column, String value) {
        // ─── NEW: excluded tables check ───
        if (allTablesExcluded(select)) {
            return select.toString();
        }
        process(select, column, value);
        return select.toString();
    }

    private static boolean allTablesExcluded(Select select) {
        Set<String> excluded = TenantwallConfig.getExcludedTables();
        if (excluded.isEmpty())
            return false;

        Set<String> tables = TableExtractor.getImmediateTables(select);
        if (tables.isEmpty())
            return false; // can't tell → don't skip

        for (String t : tables) {
            if (!excluded.contains(t)) {
                return false; // at least one non-excluded table → filter
            }
        }
        System.out.println("⏭️ [SelectHandler] All tables excluded, skipping: " + tables);
        return true;
    }

    private static void process(Select select, String column, String value) {
        if (select instanceof PlainSelect) {
            handlePlainSelect((PlainSelect) select, column, value);
        } else if (select instanceof SetOperationList) {
            handleSetOperationList((SetOperationList) select, column, value);
        } else if (select instanceof ParenthesedSelect) {
            ParenthesedSelect ps = (ParenthesedSelect) select;
            Select inner = ps.getSelect();
            if (inner != null) {
                process(inner, column, value);
            }
        } else {
            System.out.println("⚠️ [SelectHandler] Unsupported Select type: "
                    + select.getClass().getSimpleName());
        }
    }

    private static void handlePlainSelect(PlainSelect plainSelect,
            String column, String value) {

        // 0. CTE bodies أولاً
        processWithItems(plainSelect.getWithItemsList(), column, value);

        // 1. NEW: استخرج alias من FROM
        String fromAlias = extractFromAlias(plainSelect);

        // 2. WHERE — مع alias
        if (SqlModifierUtils.alreadyHasFilter(plainSelect.getWhere(), column)) {
            System.out.println("✅ [SelectHandler] Filter already exists, skipping WHERE");
        } else {
            Expression newWhere = SqlModifierUtils.addFilterToWhere(
                    plainSelect.getWhere(), column, value, fromAlias); // ← alias
            plainSelect.setWhere(newWhere);
        }

        // 3. Subqueries — زي ما هو
        if (plainSelect.getWhere() != null) {
            SqlModifierUtils.processExpression(plainSelect.getWhere(), column, value);
        }

        // 4. JOIN ON — زي ما هو
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

    // ═══════════════════════════════════════════════════════════════
    // NEW: extract FROM table alias
    // ═══════════════════════════════════════════════════════════════
    private static String extractFromAlias(PlainSelect ps) {
        FromItem from = ps.getFromItem();
        if (!(from instanceof Table t)) {
            return null; // subquery in FROM — skip prefix
        }
        // لو عندها alias استخدمه، وإلا استخدم اسم الجدول نفسه
        if (t.getAlias() != null && t.getAlias().getName() != null) {
            return t.getAlias().getName(); // p1_0
        }
        return t.getName(); // products
    }

    private static void handleSetOperationList(SetOperationList setOp,
            String column,
            String value) {

        // ─── 0. NEW: Process CTE bodies FIRST ───
        processWithItems(setOp.getWithItemsList(), column, value);

        List<Select> selects = setOp.getSelects();
        for (Select body : selects) {
            process(body, column, value);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // NEW: recursively filter CTE bodies
    // ═══════════════════════════════════════════════════════════════
    private static void processWithItems(List<WithItem> withItems,
            String column,
            String value) {
        if (withItems == null || withItems.isEmpty()) {
            return;
        }
        for (WithItem item : withItems) {
            if (item.getSelect() != null) {
                System.out.println("🟢 [SelectHandler] Processing CTE: " + item.getAlias());
                process(item.getSelect(), column, value);
            }
        }
    }
}