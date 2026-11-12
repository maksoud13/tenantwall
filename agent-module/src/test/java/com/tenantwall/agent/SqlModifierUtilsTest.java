package com.tenantwall.agent;

import com.tenantwall.agent.SqlModifierUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlModifierUtilsTest {

    // ═══════════════════════════════════════════════════════════════
    // createFilterCondition
    // ═══════════════════════════════════════════════════════════════

    @Test
    void createFilterCondition_noAlias() {
        Expression cond = SqlModifierUtils.createFilterCondition("tenant_id", "T1");
        assertThat(cond.toString()).isEqualTo("tenant_id = 'T1'");
    }

    @Test
    void createFilterCondition_withAlias() {
        Expression cond = SqlModifierUtils.createFilterCondition("tenant_id", "T1", "p");
        assertThat(cond.toString()).isEqualTo("p.tenant_id = 'T1'");
    }

    @Test
    void createFilterCondition_nullAlias_fallsBackToPlain() {
        Expression cond = SqlModifierUtils.createFilterCondition("tenant_id", "T1", null);
        assertThat(cond.toString()).isEqualTo("tenant_id = 'T1'");
    }

    @Test
    void createFilterCondition_emptyAlias_fallsBackToPlain() {
        Expression cond = SqlModifierUtils.createFilterCondition("tenant_id", "T1", "");
        assertThat(cond.toString()).isEqualTo("tenant_id = 'T1'");
    }

    // ═══════════════════════════════════════════════════════════════
    // addFilterToWhere — null handling
    // ═══════════════════════════════════════════════════════════════

    @Test
    void addFilterToWhere_nullWhere_returnsCondition() {
        Expression result = SqlModifierUtils.addFilterToWhere(null, "tenant_id", "T1");
        assertThat(result.toString()).isEqualTo("tenant_id = 'T1'");
    }

    @Test
    void addFilterToWhere_nullWhereWithAlias_returnsAliasCondition() {
        Expression result = SqlModifierUtils.addFilterToWhere(null, "tenant_id", "T1", "p");
        assertThat(result.toString()).isEqualTo("p.tenant_id = 'T1'");
    }

    // ═══════════════════════════════════════════════════════════════
    // addFilterToWhere — combining with existing where
    // ═══════════════════════════════════════════════════════════════

    @Test
    void addFilterToWhere_existingWhere_combinesWithAnd() throws Exception {
        Expression existing = CCJSqlParserUtil.parseCondExpression("status = 'active'");
        Expression result = SqlModifierUtils.addFilterToWhere(existing, "tenant_id", "T1");

        String s = result.toString();
        assertThat(s).contains("status = 'active'");
        assertThat(s).contains("tenant_id = 'T1'");
        assertThat(s).contains("AND");
    }

    @Test
    void addFilterToWhere_orExistingWhere_wrapsInParens() throws Exception {
        // CRITICAL: WHERE a=1 OR b=2 must become WHERE (a=1 OR b=2) AND tenant_id='T1'
        Expression existing = CCJSqlParserUtil.parseCondExpression("a = 1 OR b = 2");
        Expression result = SqlModifierUtils.addFilterToWhere(existing, "tenant_id", "T1");

        String s = result.toString();
        // The parenthesis around (a = 1 OR b = 2) is what makes this safe
        assertThat(s).matches(".*\\(a = 1 OR b = 2\\).*AND.*tenant_id = 'T1'.*");
    }

    @Test
    void addFilterToWhere_alreadyParenthesized_doesNotDoubleWrap() throws Exception {
        Expression existing = CCJSqlParserUtil.parseCondExpression("(a = 1)");
        Expression result = SqlModifierUtils.addFilterToWhere(existing, "tenant_id", "T1");
        String s = result.toString();

        // Should not produce ((a = 1))
        assertThat(s).doesNotContain("((a = 1))");
        assertThat(s).contains("tenant_id = 'T1'");
    }

    // ═══════════════════════════════════════════════════════════════
    // alreadyHasFilter
    // ═══════════════════════════════════════════════════════════════

    @Test
    void alreadyHasFilter_nullWhere_returnsFalse() {
        assertThat(SqlModifierUtils.alreadyHasFilter(null, "tenant_id")).isFalse();
    }

    @Test
    void alreadyHasFilter_simpleColumn_returnsTrue() throws Exception {
        Expression where = CCJSqlParserUtil.parseCondExpression("tenant_id = 'T1'");
        assertThat(SqlModifierUtils.alreadyHasFilter(where, "tenant_id")).isTrue();
    }

    @Test
    void alreadyHasFilter_differentColumn_returnsFalse() throws Exception {
        Expression where = CCJSqlParserUtil.parseCondExpression("status = 'active'");
        assertThat(SqlModifierUtils.alreadyHasFilter(where, "tenant_id")).isFalse();
    }

    @Test
    void alreadyHasFilter_caseInsensitive_returnsTrue() throws Exception {
        Expression where = CCJSqlParserUtil.parseCondExpression("TENANT_ID = 'T1'");
        assertThat(SqlModifierUtils.alreadyHasFilter(where, "tenant_id")).isTrue();
    }

    @Test
    void alreadyHasFilter_inAndExpression_returnsTrue() throws Exception {
        Expression where = CCJSqlParserUtil.parseCondExpression(
                "status = 'active' AND tenant_id = 'T1'");
        assertThat(SqlModifierUtils.alreadyHasFilter(where, "tenant_id")).isTrue();
    }

    @Test
    void alreadyHasFilter_inOrExpression_returnsTrue() throws Exception {
        Expression where = CCJSqlParserUtil.parseCondExpression(
                "status = 'active' OR tenant_id = 'T1'");
        assertThat(SqlModifierUtils.alreadyHasFilter(where, "tenant_id")).isTrue();
    }

    @Test
    void alreadyHasFilter_inParenthesis_returnsTrue() throws Exception {
        Expression where = CCJSqlParserUtil.parseCondExpression(
                "(tenant_id = 'T1')");
        assertThat(SqlModifierUtils.alreadyHasFilter(where, "tenant_id")).isTrue();
    }

    @Test
    void alreadyHasFilter_inNotExpression_returnsTrue() throws Exception {
        Expression where = CCJSqlParserUtil.parseCondExpression(
                "NOT tenant_id = 'T1'");
        assertThat(SqlModifierUtils.alreadyHasFilter(where, "tenant_id")).isTrue();
    }

    @Test
    void alreadyHasFilter_inLikeExpression_returnsTrue() throws Exception {
        Expression where = CCJSqlParserUtil.parseCondExpression(
                "tenant_id LIKE 'T%'");
        assertThat(SqlModifierUtils.alreadyHasFilter(where, "tenant_id")).isTrue();
    }

    @Test
    void alreadyHasFilter_inBetween_returnsTrue() throws Exception {
        Expression where = CCJSqlParserUtil.parseCondExpression(
                "tenant_id BETWEEN 'A' AND 'Z'");
        assertThat(SqlModifierUtils.alreadyHasFilter(where, "tenant_id")).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════
    // processExpression — recursion (limited coverage; full subquery tests
    // belong to SelectHandlerTest which exercises the whole path)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void processExpression_null_doesNothing() {
        // Must not throw
        SqlModifierUtils.processExpression(null, "tenant_id", "T1");
    }

    @Test
    void processExpression_simpleExpression_doesNotThrow() throws Exception {
        Expression expr = CCJSqlParserUtil.parseCondExpression("status = 'active'");
        // No subquery, no nested select — should be a no-op
        SqlModifierUtils.processExpression(expr, "tenant_id", "T1");
        // Nothing asserted — just verifying no exception
    }
}