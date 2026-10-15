package com.tenantwall.demo.service;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.*;

import java.util.Collection;
import java.util.List;

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
        process(select, column, value);
        return select.toString();
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
            String column,
            String value) {
        // 1. Add filter to WHERE (if not already present)
        if (SqlModifierUtils.alreadyHasFilter(plainSelect.getWhere(), column)) {
            System.out.println("✅ [SelectHandler] Filter already exists, skipping WHERE");
        } else {
            Expression newWhere = SqlModifierUtils.addFilterToWhere(
                    plainSelect.getWhere(), column, value);
            plainSelect.setWhere(newWhere);
        }

        // 2. Process subqueries in WHERE
        if (plainSelect.getWhere() != null) {
            SqlModifierUtils.processExpression(plainSelect.getWhere(), column, value);
        }

        // 3. Process subqueries in JOIN ON clauses
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

    private static void handleSetOperationList(SetOperationList setOp,
            String column,
            String value) {
        List<Select> selects = setOp.getSelects();
        for (Select body : selects) {
            process(body, column, value);
        }
    }
}