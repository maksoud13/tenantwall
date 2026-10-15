package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;

import java.util.List;

/**
 * Shared utilities for all SQL handlers.
 *
 * Contains the logic that is used by SELECT, UPDATE, DELETE, and INSERT
 * handlers:
 * <ul>
 * <li>Creating the filter condition: {@code column = 'value'}</li>
 * <li>Checking if a filter already exists</li>
 * <li>Adding the filter to a WHERE clause (with parenthesis protection)</li>
 * <li>Recursively processing subqueries inside expressions</li>
 * </ul>
 */
public class SqlModifierUtils {

    // ═══════════════════════════════════════════════════════════════
    // 1. Filter condition
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates a filter condition: {@code column = 'value'}.
     */
    public static Expression createFilterCondition(String column, String value) {
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(new Column(column));
        equalsTo.setRightExpression(new StringValue(value));
        return equalsTo;
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Adding filter to WHERE (with parenthesis protection)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Adds the filter to a WHERE expression.
     * <p>
     * If the WHERE is {@code null}, the filter becomes the WHERE.
     * If the WHERE exists, it is wrapped in parentheses and combined with AND.
     * <p>
     * The parenthesis wrapping is critical: it prevents OR leaks.
     * Example: {@code WHERE a = 1 OR b = 2} becomes
     * {@code WHERE (a = 1 OR b = 2) AND tenant_id = 'X'}
     */
    public static Expression addFilterToWhere(Expression currentWhere,
            String column,
            String value) {
        Expression condition = createFilterCondition(column, value);

        if (currentWhere == null) {
            return condition;
        }

        // Wrap in parenthesis to protect OR
        Parenthesis protectedWhere;
        if (currentWhere instanceof Parenthesis) {
            protectedWhere = (Parenthesis) currentWhere;
        } else {
            protectedWhere = new Parenthesis(currentWhere);
        }

        return new AndExpression(protectedWhere, condition);
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Filter existence check
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns true if the WHERE expression already contains the filter column.
     * Prevents duplicate filter injection.
     */
    public static boolean alreadyHasFilter(Expression where, String column) {
        if (where == null)
            return false;
        return containsColumn(where, column);
    }

    private static boolean containsColumn(Expression expr, String column) {
        if (expr == null)
            return false;

        if (expr instanceof Column) {
            return column.equalsIgnoreCase(((Column) expr).getColumnName());
        }
        if (expr instanceof Parenthesis) {
            return containsColumn(((Parenthesis) expr).getExpression(), column);
        }
        if (expr instanceof AndExpression) {
            AndExpression and = (AndExpression) expr;
            return containsColumn(and.getLeftExpression(), column)
                    || containsColumn(and.getRightExpression(), column);
        }
        if (expr instanceof OrExpression) {
            OrExpression or = (OrExpression) expr;
            return containsColumn(or.getLeftExpression(), column)
                    || containsColumn(or.getRightExpression(), column);
        }
        if (expr instanceof ComparisonOperator) {
            ComparisonOperator comp = (ComparisonOperator) expr;
            return containsColumn(comp.getLeftExpression(), column)
                    || containsColumn(comp.getRightExpression(), column);
        }
        if (expr instanceof Between) {
            Between between = (Between) expr;
            return containsColumn(between.getLeftExpression(), column)
                    || containsColumn(between.getBetweenExpressionStart(), column)
                    || containsColumn(between.getBetweenExpressionEnd(), column);
        }
        if (expr instanceof LikeExpression) {
            LikeExpression like = (LikeExpression) expr;
            return containsColumn(like.getLeftExpression(), column)
                    || containsColumn(like.getRightExpression(), column);
        }
        if (expr instanceof NotExpression) {
            return containsColumn(((NotExpression) expr).getExpression(), column);
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Recursive subquery processing
    // ═══════════════════════════════════════════════════════════════

    /**
     * Recursively processes an expression to find and modify subqueries.
     * Called by handlers AFTER the filter is added to the outer query.
     */
    public static void processExpression(Expression expr, String column, String value) {
        if (expr == null)
            return;

        // Parenthesized SELECT (e.g., IN (SELECT ...))
        if (expr instanceof ParenthesedSelect) {
            ParenthesedSelect ps = (ParenthesedSelect) expr;
            Select subSelect = ps.getSelect();
            if (subSelect != null) {
                System.out.println("🟢 [Utils] Found Subquery (ParenthesedSelect)");
                SelectHandler.handle(subSelect, column, value);
            }
            return;
        }

        // LATERAL subquery
        if (expr instanceof LateralSubSelect) {
            LateralSubSelect lss = (LateralSubSelect) expr;
            Select subSelect = lss.getSelect();
            if (subSelect != null) {
                System.out.println("🟢 [Utils] Found Lateral Subquery");
                SelectHandler.handle(subSelect, column, value);
            }
            return;
        }

        if (expr instanceof Parenthesis) {
            processExpression(((Parenthesis) expr).getExpression(), column, value);
            return;
        }

        if (expr instanceof InExpression) {
            InExpression in = (InExpression) expr;
            processExpression(in.getLeftExpression(), column, value);

            Expression right = in.getRightExpression();
            if (right instanceof ParenthesedSelect) {
                SelectHandler.handle(((ParenthesedSelect) right).getSelect(), column, value);
            } else if (right instanceof ExpressionList) {
                ExpressionList exprList = (ExpressionList) right;
                List<?> items = exprList.getExpressions();
                if (items != null) {
                    for (Object item : items) {
                        if (item instanceof Expression) {
                            processExpression((Expression) item, column, value);
                        }
                    }
                }
            }
            return;
        }

        if (expr instanceof ExistsExpression) {
            ExistsExpression exists = (ExistsExpression) expr;
            Expression right = exists.getRightExpression();
            if (right instanceof ParenthesedSelect) {
                SelectHandler.handle(((ParenthesedSelect) right).getSelect(), column, value);
            }
            return;
        }

        if (expr instanceof NotExpression) {
            processExpression(((NotExpression) expr).getExpression(), column, value);
            return;
        }

        if (expr instanceof ComparisonOperator) {
            ComparisonOperator comp = (ComparisonOperator) expr;
            processExpression(comp.getLeftExpression(), column, value);
            processExpression(comp.getRightExpression(), column, value);
            return;
        }

        if (expr instanceof AndExpression) {
            AndExpression and = (AndExpression) expr;
            processExpression(and.getLeftExpression(), column, value);
            processExpression(and.getRightExpression(), column, value);
            return;
        }

        if (expr instanceof OrExpression) {
            OrExpression or = (OrExpression) expr;
            processExpression(or.getLeftExpression(), column, value);
            processExpression(or.getRightExpression(), column, value);
            return;
        }

        if (expr instanceof Between) {
            Between between = (Between) expr;
            processExpression(between.getLeftExpression(), column, value);
            processExpression(between.getBetweenExpressionStart(), column, value);
            processExpression(between.getBetweenExpressionEnd(), column, value);
            return;
        }

        if (expr instanceof LikeExpression) {
            LikeExpression like = (LikeExpression) expr;
            processExpression(like.getLeftExpression(), column, value);
            processExpression(like.getRightExpression(), column, value);
            return;
        }
    }
}