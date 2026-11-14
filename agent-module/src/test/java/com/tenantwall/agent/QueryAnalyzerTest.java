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
    // ═══════════════════════════════════════════════════════════════
    // isDdl — migration bypass (Task 4)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void isDdl_createTable_returnsTrue() {
        assertThat(QueryAnalyzer.isDdl(
                "CREATE TABLE products (id INT, name VARCHAR(100))")).isTrue();
    }

    @Test
    void isDdl_lowercase_returnsTrue() {
        assertThat(QueryAnalyzer.isDdl(
                "create table products (id int)")).isTrue();
    }

    @Test
    void isDdl_createIndex_returnsTrue() {
        assertThat(QueryAnalyzer.isDdl(
                "CREATE INDEX idx_tenant ON products(tenant_id)")).isTrue();
    }

    @Test
    void isDdl_createOrReplace_returnsTrue() {
        assertThat(QueryAnalyzer.isDdl(
                "CREATE OR REPLACE VIEW v AS SELECT * FROM products")).isTrue();
    }

    @Test
    void isDdl_alterTable_returnsTrue() {
        assertThat(QueryAnalyzer.isDdl(
                "ALTER TABLE products ADD COLUMN category_id INT")).isTrue();
    }

    @Test
    void isDdl_dropTable_returnsTrue() {
        assertThat(QueryAnalyzer.isDdl("DROP TABLE products")).isTrue();
    }

    @Test
    void isDdl_dropIndex_returnsTrue() {
        assertThat(QueryAnalyzer.isDdl("DROP INDEX idx_tenant")).isTrue();
    }

    @Test
    void isDdl_truncate_returnsTrue() {
        assertThat(QueryAnalyzer.isDdl("TRUNCATE TABLE products")).isTrue();
    }

    @Test
    void isDdl_rename_returnsTrue() {
        assertThat(QueryAnalyzer.isDdl("RENAME TABLE a TO b")).isTrue();
    }

    @Test
    void isDdl_leadingLineComment_returnsTrue() {
        assertThat(QueryAnalyzer.isDdl(
                "-- Flyway migration V1\nCREATE TABLE products (id INT)")).isTrue();
    }

    @Test
    void isDdl_leadingBlockComment_returnsTrue() {
        assertThat(QueryAnalyzer.isDdl(
                "/* init schema */ CREATE TABLE products (id INT)")).isTrue();
    }

    @Test
    void isDdl_multipleComments_returnsTrue() {
        assertThat(QueryAnalyzer.isDdl(
                "-- migration 001\n/* init */\n-- another line\nALTER TABLE t ADD x INT")).isTrue();
    }

    @Test
    void isDdl_select_returnsFalse() {
        assertThat(QueryAnalyzer.isDdl("SELECT * FROM products")).isFalse();
    }

    @Test
    void isDdl_insert_returnsFalse() {
        assertThat(QueryAnalyzer.isDdl(
                "INSERT INTO products (name) VALUES ('x')")).isFalse();
    }

    @Test
    void isDdl_update_returnsFalse() {
        assertThat(QueryAnalyzer.isDdl(
                "UPDATE products SET name = 'x' WHERE id = 1")).isFalse();
    }

    @Test
    void isDdl_delete_returnsFalse() {
        assertThat(QueryAnalyzer.isDdl(
                "DELETE FROM products WHERE id = 1")).isFalse();
    }

    @Test
    void isDdl_null_returnsFalse() {
        assertThat(QueryAnalyzer.isDdl(null)).isFalse();
    }

    @Test
    void isDdl_empty_returnsFalse() {
        assertThat(QueryAnalyzer.isDdl("")).isFalse();
    }

    @Test
    void isDdl_blank_returnsFalse() {
        assertThat(QueryAnalyzer.isDdl("   ")).isFalse();
    }

    @Test
    void isDdl_onlyComments_returnsFalse() {
        assertThat(QueryAnalyzer.isDdl("-- just a comment\n/* another */")).isFalse();
    }

    @Test
    void isDdl_createdAtColumn_returnsFalse() {
        // "created_at" — first word is CREATED_AT (with _ breaking) → no match
        assertThat(QueryAnalyzer.isDdl(
                "SELECT created_at FROM products")).isFalse();
    }

    @Test
    void isDdl_dropRateTable_returnsFalse() {
        // Table name "drop_rate" — first word is SELECT
        assertThat(QueryAnalyzer.isDdl(
                "SELECT * FROM drop_rate")).isFalse();
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers (stripLeadingComments, firstWord) — direct tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void stripLeadingComments_noComments_returnsAsIs() {
        assertThat(QueryAnalyzer.stripLeadingComments("SELECT * FROM t"))
                .isEqualTo("SELECT * FROM t");
    }

    @Test
    void stripLeadingComments_lineComment_removed() {
        assertThat(QueryAnalyzer.stripLeadingComments("-- c\nSELECT * FROM t"))
                .isEqualTo("SELECT * FROM t");
    }

    @Test
    void stripLeadingComments_blockComment_removed() {
        assertThat(QueryAnalyzer.stripLeadingComments("/* c */ SELECT * FROM t"))
                .isEqualTo("SELECT * FROM t");
    }

    @Test
    void stripLeadingComments_onlyComments_returnsEmpty() {
        assertThat(QueryAnalyzer.stripLeadingComments("-- c\n/* d */")).isEmpty();
    }

    @Test
    void firstWord_simple_returnsFirstWord() {
        assertThat(QueryAnalyzer.firstWord("SELECT * FROM t")).isEqualTo("SELECT");
    }

    @Test
    void firstWord_lowercase_returnsUppercase() {
        assertThat(QueryAnalyzer.firstWord("create table t"))
                .isEqualTo("CREATE");
    }

    @Test
    void firstWord_startsWithNonLetter_skipsToLetters() {
        assertThat(QueryAnalyzer.firstWord("   (SELECT ..."))
                .isEqualTo("SELECT");
    }

    @Test
    void firstWord_noLetters_returnsEmpty() {
        assertThat(QueryAnalyzer.firstWord("123")).isEmpty();
    }
}