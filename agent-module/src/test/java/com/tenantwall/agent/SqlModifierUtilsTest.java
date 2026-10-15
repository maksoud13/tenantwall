package com.tenantwall.agent;

import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class SqlModifierUtilsTest {

    private final CCJSqlParserManager parser = new CCJSqlParserManager();

    @Test
    @DisplayName("createFilterCondition — builds 'col = value'")
    void testCreateFilterCondition() {
        var result = SqlModifierUtils.createFilterCondition("tenant_id", "TENANT_A");
        assertInstanceOf(EqualsTo.class, result);
        assertEquals("tenant_id = 'TENANT_A'", result.toString());
    }

    @Test
    @DisplayName("addFilterToWhere — null WHERE becomes filter alone")
    void testAddFilterToNullWhere() {
        var result = SqlModifierUtils.addFilterToWhere(null, "tenant_id", "TENANT_A");
        assertEquals("tenant_id = 'TENANT_A'", result.toString());
    }

    @Test
    @DisplayName("addFilterToWhere — existing WHERE wrapped in parenthesis")
    void testAddFilterWrapsExisting() throws Exception {
        // Parse "id = 5" as a WHERE expression
        Select select = (Select) parser.parse(new StringReader("SELECT * FROM t WHERE id = 5"));
        PlainSelect ps = (PlainSelect) select.getSelectBody();

        var result = SqlModifierUtils.addFilterToWhere(ps.getWhere(), "tenant_id", "TENANT_A");

        assertInstanceOf(AndExpression.class, result);
        AndExpression and = (AndExpression) result;
        assertInstanceOf(Parenthesis.class, and.getLeftExpression());
        assertTrue(result.toString().contains("(id = 5)"));
        assertTrue(result.toString().contains("tenant_id = 'TENANT_A'"));
    }

    @Test
    @DisplayName("alreadyHasFilter — detects existing filter")
    void testAlreadyHasFilter() throws Exception {
        Select select = (Select) parser.parse(
            new StringReader("SELECT * FROM t WHERE tenant_id = 'TENANT_B'"));
        PlainSelect ps = (PlainSelect) select.getSelectBody();

        assertTrue(SqlModifierUtils.alreadyHasFilter(ps.getWhere(), "tenant_id"));
        assertFalse(SqlModifierUtils.alreadyHasFilter(ps.getWhere(), "user_id"));
    }

    @Test
    @DisplayName("alreadyHasFilter — null WHERE returns false")
    void testAlreadyHasFilterNull() {
        assertFalse(SqlModifierUtils.alreadyHasFilter(null, "tenant_id"));
    }
}