package com.tenantwall.agent;

import com.tenantwall.agent.QueryAnalyzer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryAnalyzerTest {

    // ═══════════════════════════════════════════════════════════════
    // Null / empty — must not throw
    // ═══════════════════════════════════════════════════════════════

    @Test
    void null_returnsFalse() {
        assertThat(QueryAnalyzer.isComplex(null)).isFalse();
    }

    @Test
    void empty_returnsFalse() {
        assertThat(QueryAnalyzer.isComplex("")).isFalse();
    }

    // ═══════════════════════════════════════════════════════════════
    // Simple queries — should be handled locally
    // ═══════════════════════════════════════════════════════════════

    @Test
    void simpleSelect_returnsFalse() {
        assertThat(QueryAnalyzer.isComplex("SELECT * FROM users")).isFalse();
    }

    @Test
    void selectWithWhere_returnsFalse() {
        assertThat(QueryAnalyzer.isComplex(
                "SELECT * FROM users WHERE id = 1")).isFalse();
    }

    @Test
    void selectWithJoin_returnsFalse() {
        assertThat(QueryAnalyzer.isComplex(
                "SELECT * FROM users u JOIN orders o ON u.id = o.user_id")).isFalse();
    }

    @Test
    void selectWithGroupBy_returnsFalse() {
        assertThat(QueryAnalyzer.isComplex(
                "SELECT category, COUNT(*) FROM products GROUP BY category")).isFalse();
    }

    @Test
    void insert_returnsFalse() {
        assertThat(QueryAnalyzer.isComplex(
                "INSERT INTO users (name) VALUES ('a')")).isFalse();
    }

    @Test
    void update_returnsFalse() {
        assertThat(QueryAnalyzer.isComplex(
                "UPDATE users SET name = 'x' WHERE id = 1")).isFalse();
    }

    @Test
    void delete_returnsFalse() {
        assertThat(QueryAnalyzer.isComplex(
                "DELETE FROM users WHERE id = 1")).isFalse();
    }

    // ═══════════════════════════════════════════════════════════════
    // Complex patterns — must go to Python
    // ═══════════════════════════════════════════════════════════════

    @Test
    void withClause_returnsTrue() {
        assertThat(QueryAnalyzer.isComplex(
                "WITH cte AS (SELECT 1) SELECT * FROM cte")).isTrue();
    }

    @Test
    void windowOver_returnsTrue() {
        assertThat(QueryAnalyzer.isComplex(
                "SELECT ROW_NUMBER() OVER (ORDER BY id) FROM users")).isTrue();
    }

    @Test
    void partitionBy_returnsTrue() {
        assertThat(QueryAnalyzer.isComplex(
                "SELECT SUM(x) OVER (PARTITION BY category) FROM products")).isTrue();
    }

    @Test
    void rowNumber_returnsTrue() {
        assertThat(QueryAnalyzer.isComplex(
                "SELECT ROW_NUMBER() FROM users")).isTrue();
    }

    @Test
    void rank_returnsTrue() {
        assertThat(QueryAnalyzer.isComplex(
                "SELECT RANK() FROM users")).isTrue();
    }

    @Test
    void oracleNvl_returnsTrue() {
        assertThat(QueryAnalyzer.isComplex(
                "SELECT NVL(name, 'unknown') FROM users")).isTrue();
    }

    @Test
    void oracleDecode_returnsTrue() {
        assertThat(QueryAnalyzer.isComplex(
                "SELECT DECODE(status, 1, 'active') FROM users")).isTrue();
    }

    @Test
    void oracleConnectBy_returnsTrue() {
        assertThat(QueryAnalyzer.isComplex(
                "SELECT * FROM employees CONNECT BY PRIOR id = manager_id")).isTrue();
    }

    @Test
    void oracleRownum_returnsTrue() {
        assertThat(QueryAnalyzer.isComplex(
                "SELECT * FROM users WHERE ROWNUM <= 10")).isTrue();
    }

    @Test
    void oracleStartWith_returnsTrue() {
        assertThat(QueryAnalyzer.isComplex(
                "SELECT * FROM employees START WITH manager_id IS NULL")).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════
    // Case insensitivity
    // ═══════════════════════════════════════════════════════════════

    @Test
    void complexPatterns_areCaseInsensitive() {
        assertThat(QueryAnalyzer.isComplex(
                "with cte as (select 1) select * from cte")).isTrue();
        assertThat(QueryAnalyzer.isComplex(
                "With cte as (select 1) select * from cte")).isTrue();
        assertThat(QueryAnalyzer.isComplex(
                "SELECT nvl(x, 0) FROM t")).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════
    // Word boundaries — must NOT match substrings
    // ═══════════════════════════════════════════════════════════════

    @Test
    void withSubstring_returnsFalse() {
        // "WITHOUT" contains "WITH" but should not match due to word boundary
        assertThat(QueryAnalyzer.isComplex(
                "SELECT * FROM users WHERE name = 'WITHOUT'")).isFalse();
    }

    @Test
    void rownumAsSubstring_returnsFalse() {
        // Column named "prownum_id" — should not trigger ROWNUM pattern
        assertThat(QueryAnalyzer.isComplex(
                "SELECT prownum_id FROM users")).isFalse();
    }
}