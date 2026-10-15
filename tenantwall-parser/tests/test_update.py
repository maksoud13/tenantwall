"""
Unit tests for UPDATE statement modification.
"""
import pytest

COLUMN = "tenant_id"
VALUE = "TENANT_A"


class TestUpdate:

    def test_update_with_where(self, mysql_modifier):
        sql = "UPDATE orders SET status = 'DONE' WHERE id = 5"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert "(id = 5)" in result
        assert "tenant_id = 'TENANT_A'" in result

    def test_update_without_where(self, mysql_modifier):
        """SECURITY: UPDATE without WHERE must get scoped to a tenant."""
        sql = "UPDATE orders SET status = 'DONE'"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert "WHERE tenant_id = 'TENANT_A'" in result
        assert "WHERE WHERE" not in result

    def test_update_with_or(self, mysql_modifier):
        sql = "UPDATE orders SET status = 'DONE' WHERE id = 5 OR id = 10"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert "(id = 5 OR id = 10)" in result
        assert "tenant_id = 'TENANT_A'" in result

    def test_update_existing_filter_not_duplicated(self, mysql_modifier):
        sql = "UPDATE orders SET status = 'DONE' WHERE tenant_id = 'TENANT_B'"
        result = mysql_modifier.add_filter(sql, COLUMN, VALUE)
        assert result.count("tenant_id") == 1