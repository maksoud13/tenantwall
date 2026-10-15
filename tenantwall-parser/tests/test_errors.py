"""
Tests for error handling and dialect support.
"""
import pytest
from app.modifier import SqlModifier


class TestErrors:

    def test_unsupported_dialect_raises(self):
        with pytest.raises(ValueError, match="Unsupported dialect"):
            SqlModifier(dialect="fake-dialect-9000")

    def test_invalid_sql_raises(self, mysql_modifier):
        with pytest.raises(ValueError, match="Failed to parse SQL"):
            mysql_modifier.add_filter("SELEKT * FROM", "tenant_id", "A")

    def test_unsupported_statement_type(self, mysql_modifier):
        with pytest.raises(ValueError, match="Unsupported statement type"):
            mysql_modifier.add_filter("CREATE TABLE foo (id INT)", "tenant_id", "A")


class TestDialects:

    def test_all_supported_dialects(self):
        from app.modifier import SUPPORTED_DIALECTS
        for dialect in SUPPORTED_DIALECTS:
            modifier = SqlModifier(dialect=dialect)
            assert modifier.dialect == dialect

    def test_oracle_dialect_parses_nvl(self):
        modifier = SqlModifier(dialect="oracle")
        sql = "SELECT NVL(name, 'unknown') FROM users"
        result = modifier.add_filter(sql, "tenant_id", "TENANT_A")
        assert "NVL" in result