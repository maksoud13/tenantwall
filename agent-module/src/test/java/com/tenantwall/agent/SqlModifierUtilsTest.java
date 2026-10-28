package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlModifierUtilsTest {

    @Test
    void createFilterCondition_withoutAlias() {
        Expression filter = SqlModifierUtils.createFilterCondition("tenant_id", "TENANT_A");

        assertInstanceOf(EqualsTo.class, filter);
        EqualsTo eq = (EqualsTo) filter;
        assertEquals("tenant_id", ((Column) eq.getLeftExpression()).getColumnName());
        assertEquals("TENANT_A", ((StringValue) eq.getRightExpression()).getValue());
    }

    @Test
    void createFilterCondition_withAlias() {
        Expression filter = SqlModifierUtils.createFilterCondition("tenant_id", "TENANT_A", "p");

        assertInstanceOf(EqualsTo.class, filter);
        EqualsTo eq = (EqualsTo) filter;
        Column left = (Column) eq.getLeftExpression();
        assertEquals("tenant_id", left.getColumnName());
        assertNotNull(left.getTable());
        assertEquals("p", left.getTable().getName());
    }

    @Test
    void addFilterToWhere_nullCurrent() {
        Expression result = SqlModifierUtils.addFilterToWhere(null, "tenant_id", "TENANT_A");

        assertInstanceOf(EqualsTo.class, result);
    }

    @Test
    void addFilterToWhere_existingWhere() {
        EqualsTo existing = new EqualsTo();
        existing.setLeftExpression(new Column("id"));
        existing.setRightExpression(new StringValue("5"));

        Expression result = SqlModifierUtils.addFilterToWhere(existing, "tenant_id", "TENANT_A");

        // Should wrap in parens and AND with tenant filter
        assertInstanceOf(AndExpression.class, result);
        String sql = result.toString();
        assertTrue(sql.contains("id = '5'"));
        assertTrue(sql.contains("tenant_id = 'TENANT_A'"));
    }

    @Test
    void alreadyHasFilter_detectsTenant() {
        EqualsTo existing = new EqualsTo();
        existing.setLeftExpression(new Column("tenant_id"));
        existing.setRightExpression(new StringValue("TENANT_A"));

        assertTrue(SqlModifierUtils.alreadyHasFilter(existing, "tenant_id"));
        assertFalse(SqlModifierUtils.alreadyHasFilter(existing, "other_col"));
    }
}