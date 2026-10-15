"""
Unit tests for INSERT statement modification.
"""
import pytest

COLUMN = "tenant_id"
VALUE = "TENANT_A"


class TestInsertValues:

    def test_insert_single_row(self, mysql_modifier):
        sql = "INSERT INTO orders (id, total) VALUES (1, 500)"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert "tenant_id" in result
        assert "'TENANT_A'" in result

    def test_insert_multi_row(self, mysql_modifier):
        sql = "INSERT INTO orders (id, total) VALUES (1, 500), (2, 600)"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        # Value added to EACH row
        assert result.count("'TENANT_A'") == 2

    def test_insert_without_columns_skipped(self, mysql_modifier):
        """Cannot safely inject without knowing column order."""
        sql = "INSERT INTO orders VALUES (1, 500)"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        # Unchanged
        assert result == sql

    def test_insert_existing_filter_not_duplicated(self, mysql_modifier):
        sql = "INSERT INTO orders (id, total, tenant_id) VALUES (1, 500, 'TENANT_B')"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert result.count("tenant_id") == 1


class TestInsertSelect:

    def test_insert_select_gets_filter_in_subquery(self, mysql_modifier):
        sql = (
            "INSERT INTO orders_backup (id, total) "
            "SELECT id, total FROM orders WHERE total > 100"
        )
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert "tenant_id = 'TENANT_A'" in result
        # Should NOT inject into INSERT target
        assert "orders_backup (id, total, tenant_id)" not in result