"""
Unit tests for DELETE statement modification.
"""
import pytest

COLUMN = "tenant_id"
VALUE = "TENANT_A"


class TestDelete:

    def test_delete_with_where(self, mysql_modifier):
        sql = "DELETE FROM orders WHERE id = 5"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert "(id = 5)" in result
        assert "tenant_id = 'TENANT_A'" in result

    def test_delete_without_where(self, mysql_modifier):
        """SECURITY: mass DELETE must be scoped to a single tenant."""
        sql = "DELETE FROM orders"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert "WHERE tenant_id = 'TENANT_A'" in result

    def test_delete_with_or(self, mysql_modifier):
        sql = "DELETE FROM orders WHERE status = 'CANCELLED' OR status = 'EXPIRED'"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert "(status = 'CANCELLED' OR status = 'EXPIRED')" in result

    def test_delete_with_subquery(self, mysql_modifier):
        sql = "DELETE FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE level = 'GOLD')"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert result.count("tenant_id = 'TENANT_A'") >= 2