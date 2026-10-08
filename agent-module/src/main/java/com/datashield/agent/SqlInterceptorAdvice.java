package com.datashield.agent;

import net.bytebuddy.asm.Advice;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.io.StringReader;

public class SqlInterceptorAdvice {

    private static final CCJSqlParserManager parserManager = new CCJSqlParserManager();
    private static final String TENANT_ID = "TENANT_A";

    // @Advice.OnMethodEnter
    // @SuppressWarnings("deprecation")
    // public static void intercept(@Advice.AllArguments Object[] args) {
    // if (args == null || args.length == 0 || !(args[0] instanceof String)) {
    // return;
    // }

    // String sql = (String) args[0];
    // String trimmedSql = sql.trim().toUpperCase();

    // if (!trimmedSql.startsWith("SELECT")) {
    // return;
    // }

    // try {
    // Statement stmt = parserManager.parse(new StringReader(sql));

    // if (stmt instanceof Select) {
    // Select select = (Select) stmt;
    // SelectBody selectBody = select.getSelectBody();

    // // حالة الاستعلام البسيط (الأكثر شيوعاً)
    // if (selectBody instanceof PlainSelect) {
    // PlainSelect plainSelect = (PlainSelect) selectBody;

    // // بناء شرط tenant_id = 'TENANT_A'
    // EqualsTo equalsTo = new EqualsTo();
    // equalsTo.setLeftExpression(new Column("tenant_id"));
    // equalsTo.setRightExpression(new StringValue(TENANT_ID));

    // Expression currentWhere = plainSelect.getWhere();
    // if (currentWhere == null) {
    // plainSelect.setWhere(equalsTo);
    // } else {
    // AndExpression andExpression = new AndExpression(currentWhere, equalsTo);
    // plainSelect.setWhere(andExpression);
    // }

    // String newSql = select.toString();
    // args[0] = newSql;

    // System.out.println("✅ [فرج] SQL مُعدّل:");
    // System.out.println(" " + newSql);
    // System.out.flush();

    // }
    // // حالة استعلام UNION / INTERSECT / EXCEPT
    // else if (selectBody instanceof SetOperationList) {
    // System.out.println("⚠️ [فرج] استعلام UNION/EXCEPT غير مدعوم حالياً، يتم
    // تخطيه.");
    // }
    // // أي نوع آخر (مثل VALUES) – نتخطى
    // else {
    // System.out.println("⚠️ [فرج] نوع استعلام غير مدعوم، يتم تخطيه.");
    // }
    // }

    // } catch (JSQLParserException e) {
    // System.err.println("⚠️ [فرج] فشل تحليل SQL (يتم تخطيه): " + sql);
    // System.err.println(" السبب: " + e.getMessage());
    // } catch (Exception e) {
    // System.err.println("⚠️ [فرج] خطأ غير متوقع:");
    // e.printStackTrace();
    // }
    // }

    @Advice.OnMethodEnter
    public static void intercept(@Advice.AllArguments Object[] args,
            @Advice.Local(value = "Localintercept") boolean Localintercept) {
        if (Localintercept) {
            return;
        }
        if (args == null || args.length == 0 || !(args[0] instanceof String)) {
            return;
        }

        String sql = (String) args[0];
        String tenantId = TenantContext.getTenant(); // هنربطها بعدين

        if (tenantId == null) {
            tenantId = "TENANT_A"; // مؤقت
        }

        String modifiedSql = SqlModifier.addTenantId(sql, tenantId);
        if (!modifiedSql.equals(sql)) {
            args[0] = modifiedSql;
            System.out.println("✅ [Advice] Modified SQL (Statement): " + modifiedSql);
        }
        Localintercept = true;
    }
}