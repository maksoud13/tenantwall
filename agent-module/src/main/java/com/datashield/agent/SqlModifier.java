package com.datashield.agent;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.io.StringReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SqlModifier {

    private static final CCJSqlParserManager parserManager = new CCJSqlParserManager();

    // =========================================================
    // 🎯 Caffeine Cache
    // - Max 10,000 entries
    // - Expire after 30 minutes
    // - Track statistics (hits/misses)
    // =========================================================
    private static volatile Cache<String, String> SQL_CACHE = buildCache();

    private static Cache<String, String> buildCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    // =========================================================
    // 🎯 1. الدالة العامة (المدخل الرئيسي)
    // =========================================================
    public static String addFilter(String sql, String columnName, String value) {
        if (sql == null || columnName == null || columnName.isEmpty()
                || value == null || value.isEmpty()) {
            return sql;
        }

        // 🎯 1.1. بناء مفتاح الـ Cache
        String cacheKey = sql + "|" + columnName + "|" + value;

        // 🎯 1.2. البحث في الـ Cache
        String cached = SQL_CACHE.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 🎯 1.3. تعديل الاستعلام (المنطق القديم)
        String result = doModify(sql, columnName, value);

        // 🎯 1.4. تخزين النتيجة في الـ Cache
        if (result != null && !result.equals(sql)) {
            SQL_CACHE.put(cacheKey, result);
        }

        return result != null ? result : sql;
    }

    // =========================================================
    // 🎯 2. المنطق الفعلي لتعديل الاستعلام
    // =========================================================
    private static String doModify(String sql, String columnName, String value) {
        System.out.println("🔴 [CACHE MISS] Parsing SQL: " + sql);
        String trimmed = sql.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT")) {
            return sql;
        }

        try {
            Statement stmt = parserManager.parse(new StringReader(sql));
            if (stmt instanceof Select) {
                Select select = (Select) stmt;
                handleSelect(select, columnName, value);
                return select.toString();
            }
        } catch (JSQLParserException e) {
            System.err.println("⚠️ [Modifier] Failed to parse SQL: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("⚠️ [Modifier] Unexpected error: " + e.getMessage());
        }
        return sql;
    }

    // Wrapper للحفاظ على التوافق مع الكود القديم
    public static String addTenantId(String sql, String tenantId) {
        return addFilter(sql, "tenant_id", tenantId);
    }

    // =========================================================
    // 🎯 3. توزيع المعالجة على أنواع Select
    // =========================================================
    private static void handleSelect(Select select, String columnName, String value) {
        if (select instanceof PlainSelect) {
            handlePlainSelect((PlainSelect) select, columnName, value);
        } else if (select instanceof SetOperationList) {
            handleSetOperationList((SetOperationList) select, columnName, value);
        } else if (select instanceof ParenthesedSelect) {
            ParenthesedSelect ps = (ParenthesedSelect) select;
            Select inner = ps.getSelect();
            if (inner != null) {
                handleSelect(inner, columnName, value);
            }
        } else {
            System.out.println("⚠️ [Modifier] Unsupported Select type: "
                    + select.getClass().getSimpleName());
        }
    }

    // =========================================================
    // 🎯 4. معالجة PlainSelect (استعلام بسيط + JOIN + Subquery)
    // =========================================================
    private static void handlePlainSelect(PlainSelect plainSelect, String columnName, String value) {
        // 4.1. نبني شرط columnName = 'value'
        Expression filterCondition = createFilterCondition(columnName, value);
        Expression currentWhere = plainSelect.getWhere();

        if (currentWhere == null) {
            // مفيش WHERE أصلاً → نضيف الشرط لوحده
            plainSelect.setWhere(filterCondition);
        } else {
            // 🛡️ نلف الـ WHERE الأصلي في Parenthesis لحماية الـ OR
            Parenthesis protectedWhere;
            if (currentWhere instanceof Parenthesis) {
                protectedWhere = (Parenthesis) currentWhere;
            } else {
                protectedWhere = new Parenthesis(currentWhere);
            }

            // نضيف الشرط الجديد بـ AND بعد الـ Parenthesis
            plainSelect.setWhere(new AndExpression(protectedWhere, filterCondition));
        }

        // 4.2. نبحث عن Subqueries في الـ WHERE
        if (plainSelect.getWhere() != null) {
            processExpression(plainSelect.getWhere(), columnName, value);
        }

        // 4.3. نبحث عن Subqueries في شروط الـ JOIN (ON)
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                if (join.getOnExpression() != null) {
                    processExpression(join.getOnExpression(), columnName, value);
                }
            }
        }
    }

    // =========================================================
    // 🎯 5. معالجة SetOperationList (UNION / INTERSECT / EXCEPT)
    // =========================================================
    private static void handleSetOperationList(SetOperationList setOp, String columnName, String value) {
        List<Select> selects = setOp.getSelects();
        for (Select body : selects) {
            handleSelect(body, columnName, value);
        }
    }

    // =========================================================
    // 🎯 6. معالجة التعبيرات (Recursive)
    // =========================================================
    private static void processExpression(Expression expr, String columnName, String value) {
        if (expr == null)
            return;

        // 6.1. ParenthesedSelect
        if (expr instanceof ParenthesedSelect) {
            ParenthesedSelect ps = (ParenthesedSelect) expr;
            Select subSelectBody = ps.getSelect();
            if (subSelectBody != null) {
                System.out.println("🟢 [Modifier] Found Subquery (ParenthesedSelect)!");
                handleSelect(subSelectBody, columnName, value);
            }
            return;
        }

        // 6.2. LateralSubSelect
        if (expr instanceof LateralSubSelect) {
            LateralSubSelect lss = (LateralSubSelect) expr;
            Select subSelectBody = lss.getSelect();
            if (subSelectBody != null) {
                System.out.println("🟢 [Modifier] Found Lateral Subquery!");
                handleSelect(subSelectBody, columnName, value);
            }
            return;
        }

        // 6.3. Parenthesis
        if (expr instanceof Parenthesis) {
            Parenthesis paren = (Parenthesis) expr;
            processExpression(paren.getExpression(), columnName, value);
            return;
        }

        // 6.4. InExpression (IN / NOT IN)
        if (expr instanceof InExpression) {
            InExpression in = (InExpression) expr;
            processExpression(in.getLeftExpression(), columnName, value);

            Expression right = in.getRightExpression();
            if (right instanceof ParenthesedSelect) {
                ParenthesedSelect ps = (ParenthesedSelect) right;
                Select subBody = ps.getSelect();
                if (subBody != null) {
                    System.out.println("🟢 [Modifier] Found Subquery inside IN!");
                    handleSelect(subBody, columnName, value);
                }
            } else if (right instanceof ExpressionList) {
                ExpressionList exprList = (ExpressionList) right;
                List<?> items = exprList.getExpressions();
                if (items != null) {
                    for (Object item : items) {
                        if (item instanceof Expression) {
                            processExpression((Expression) item, columnName, value);
                        }
                    }
                }
            }
            return;
        }

        // 6.5. ExistsExpression (EXISTS / NOT EXISTS)
        if (expr instanceof ExistsExpression) {
            ExistsExpression exists = (ExistsExpression) expr;
            Expression right = exists.getRightExpression();
            if (right instanceof ParenthesedSelect) {
                ParenthesedSelect ps = (ParenthesedSelect) right;
                Select subBody = ps.getSelect();
                if (subBody != null) {
                    System.out.println("🟢 [Modifier] Found Subquery inside EXISTS!");
                    handleSelect(subBody, columnName, value);
                }
            }
            return;
        }

        // 6.6. NotExpression (NOT EXISTS, NOT IN, ...)
        if (expr instanceof NotExpression) {
            NotExpression not = (NotExpression) expr;
            processExpression(not.getExpression(), columnName, value);
            return;
        }

        // 6.7. ComparisonOperator (>, <, =, >=, <=, ...)
        if (expr instanceof ComparisonOperator) {
            ComparisonOperator comp = (ComparisonOperator) expr;
            processExpression(comp.getLeftExpression(), columnName, value);
            processExpression(comp.getRightExpression(), columnName, value);
            return;
        }

        // 6.8. AndExpression
        if (expr instanceof AndExpression) {
            AndExpression and = (AndExpression) expr;
            processExpression(and.getLeftExpression(), columnName, value);
            processExpression(and.getRightExpression(), columnName, value);
            return;
        }

        // 6.9. OrExpression
        if (expr instanceof OrExpression) {
            OrExpression or = (OrExpression) expr;
            processExpression(or.getLeftExpression(), columnName, value);
            processExpression(or.getRightExpression(), columnName, value);
            return;
        }

        // 6.10. Between
        if (expr instanceof Between) {
            Between between = (Between) expr;
            processExpression(between.getLeftExpression(), columnName, value);
            processExpression(between.getBetweenExpressionStart(), columnName, value);
            processExpression(between.getBetweenExpressionEnd(), columnName, value);
            return;
        }

        // 6.11. LikeExpression
        if (expr instanceof LikeExpression) {
            LikeExpression like = (LikeExpression) expr;
            processExpression(like.getLeftExpression(), columnName, value);
            processExpression(like.getRightExpression(), columnName, value);
            return;
        }
    }

    // =========================================================
    // 🎯 7. دالة مساعدة لإنشاء شرط columnName = 'value'
    // =========================================================
    private static Expression createFilterCondition(String columnName, String value) {
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(new Column(columnName));
        equalsTo.setRightExpression(new StringValue(value));
        return equalsTo;
    }

    // =========================================================
    // 🎯 8. دوال المراقبة والتحكم في الـ Cache
    // =========================================================
    public static CacheStats getCacheStats() {

        return SQL_CACHE.stats();
    }

    public static void clearCache() {
        SQL_CACHE = buildCache();
    }

    public static long getCacheSize() {
        return SQL_CACHE.estimatedSize();
    }
}