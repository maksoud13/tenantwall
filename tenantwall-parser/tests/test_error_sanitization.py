"""Tests for error message sanitization.

The parser must never leak SQL fragments (table names, columns, values)
or config details (dialect lists) in error responses. Clients get a
reference ID; full detail is logged server-side.
"""

import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


@pytest.fixture(autouse=True)
def auth_off(monkeypatch):
    """Auth disabled — we test error handling, not auth."""
    monkeypatch.setenv("TENANTWALL_PARSER_ENFORCE_AUTH", "0")


def post(body: dict):
    return client.post("/api/modify", json=body)


class TestErrorSanitization:
    def test_parse_error_does_not_leak_sql(self):
        """Bad SQL must not appear in the error message."""
        r = post({
            "sql": "SELEKT * FROM secret_table_xyz",
            "column": "tenant_id",
            "value": "T1",
            "dialect": "mysql",
        })
        data = r.json()
        assert data["success"] is False
        err = data["error"].lower()
        assert "selekt" not in err
        assert "secret_table" not in err

    def test_parse_error_has_reference(self):
        """Every error must carry a reference ID for support."""
        r = post({
            "sql": "SELEKT * FROM",
            "column": "tenant_id",
            "value": "T1",
            "dialect": "mysql",
        })
        data = r.json()
        assert data["success"] is False
        assert data.get("reference") is not None
        assert len(data["reference"]) >= 4
        assert f"ref: {data['reference']}" in data["error"]

    def test_unsupported_dialect_is_sanitized(self):
        """Unsupported dialect must not leak the dialect list."""
        r = post({
            "sql": "SELECT 1",
            "column": "tenant_id",
            "value": "T1",
            "dialect": "bogus_dialect",
        })
        data = r.json()
        assert data["success"] is False
        err = data["error"].lower()
        assert "bogus_dialect" not in err
        assert "use one of" not in err   # no dialect list leaked

    def test_internal_error_is_sanitized(self, monkeypatch):
        """Unexpected exceptions must not leak internal details."""
        from app import main as main_module

        def boom(*args, **kwargs):
            raise RuntimeError(
                "Internal failure reading /secret/path/credentials.json"
            )

        monkeypatch.setattr(main_module.SqlModifier, "__init__", boom)

        r = post({
            "sql": "SELECT * FROM users",
            "column": "tenant_id",
            "value": "T1",
            "dialect": "mysql",
        })
        data = r.json()
        assert data["success"] is False
        err = data["error"].lower()
        assert "credentials" not in err
        assert "/secret/path" not in err
        assert "ref:" in err