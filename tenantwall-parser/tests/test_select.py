"""
Unit tests for SELECT statement modification.
"""
import pytest
from app.modifier import SqlModifier

COLUMN = "tenant_id"
VALUE = "TENANT_A"


class TestSimpleSelect:

    def test_simple_select(self, mysql_modifier):
        sql = "SELECT * FROM users"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert "WHERE tenant_id = 'TENANT_A'" in result

    def test_select_with_where(self, mysql_modifier):
        sql = "SELECT * FROM orders WHERE total > 100"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert "(total > 100)" in result
        assert "tenant_id = 'TENANT_A'" in result


class TestOrProtection:

    def test_or_protected(self, mysql_modifier):
        sql = "SELECT * FROM orders WHERE status = 'PENDING' OR status = 'SHIPPED'"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        # Must be wrapped in parenthesis
        assert "(status = 'PENDING' OR status = 'SHIPPED')" in result
        assert "tenant_id = 'TENANT_A'" in result

    def test_or_not_leaked(self, mysql_modifier):
        sql = "SELECT * FROM orders WHERE status = 'PENDING' OR status = 'SHIPPED'"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        # The AND should NOT be inside the OR
        assert "OR status = 'SHIPPED' AND tenant_id" not in result


class TestJoin:

    def test_join_gets_filter(self, mysql_modifier):
        sql = "SELECT o.* FROM orders o JOIN customers c ON o.customer_id = c.id WHERE o.total > 100"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert "tenant_id = 'TENANT_A'" in result


class TestSubqueries:

    def test_comparison_subquery(self, mysql_modifier):
        sql = "SELECT * FROM orders WHERE total > (SELECT AVG(total) FROM orders)"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        # Filter should be in both outer and inner query
        assert result.count("tenant_id = 'TENANT_A'") >= 2

    def test_in_subquery(self, mysql_modifier):
        sql = "SELECT * FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE level = 'GOLD')"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert result.count("tenant_id = 'TENANT_A'") >= 2

    def test_exists_subquery(self, mysql_modifier):
        sql = "SELECT * FROM orders o WHERE EXISTS (SELECT 1 FROM customers c WHERE c.id = o.customer_id)"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert result.count("tenant_id = 'TENANT_A'") >= 2

    def test_not_exists_subquery(self, mysql_modifier):
        sql = "SELECT * FROM orders o WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.id = o.customer_id)"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert result.count("tenant_id = 'TENANT_A'") >= 2


class TestUnion:

    def test_union_both_sides(self, mysql_modifier):
        sql = "SELECT name FROM users WHERE age > 18 UNION SELECT name FROM users WHERE age < 5"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        # Both sides should have the filter
        assert result.count("tenant_id = 'TENANT_A'") == 2


class TestCte:

    def test_cte_gets_filter(self, mysql_modifier):
        sql = "WITH d AS (SELECT id, name FROM users) SELECT * FROM d WHERE id > 5"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        # Filter in the CTE
        assert "FROM users WHERE tenant_id = 'TENANT_A'" in result

    def test_cte_with_window_function(self, mysql_modifier):
        sql = (
            "WITH ranked AS ("
            "  SELECT id, ROW_NUMBER() OVER (PARTITION BY contract_id ORDER BY id DESC) AS rn "
            "  FROM orders"
            ") SELECT * FROM ranked WHERE rn = 1"
        )
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert "ROW_NUMBER()" in result  # Preserved
        assert "tenant_id = 'TENANT_A'" in result

    def test_oracle_nvl(self, oracle_modifier):
        sql = "SELECT NVL(total, 0) FROM orders"
        result = oracle_modifier.add_filter(sql, COLUMN, VALUE)
        assert "NVL" in result
        assert "tenant_id = 'TENANT_A'" in result


class TestDuplicatePrevention:

    def test_existing_filter_not_duplicated(self, mysql_modifier):
        sql = "SELECT * FROM users WHERE tenant_id = 'TENANT_B'"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert result.count("tenant_id") == 1


class TestExcludedTables:

    def test_excluded_table_skipped(self, modifier_with_excluded):
        sql = "SELECT * FROM countries"
        result = modifier_with_excluded.add_filter(sql, COLUMN, VALUE)
        assert "WHERE" not in result  # Nothing injected

    def test_non_excluded_table_gets_filter(self, modifier_with_excluded):
        sql = "SELECT * FROM orders"
        result = modifier_with_excluded.add_filter(sql, COLUMN, VALUE)
        assert "tenant_id = 'TENANT_A'" in result