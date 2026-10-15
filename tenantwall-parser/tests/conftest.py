"""
Shared pytest fixtures for TenantWall Parser tests.
"""
import pytest
from app.modifier import SqlModifier


@pytest.fixture
def mysql_modifier():
    """SqlModifier with MySQL dialect (default)."""
    return SqlModifier(dialect="mysql")


@pytest.fixture
def oracle_modifier():
    """SqlModifier with Oracle dialect."""
    return SqlModifier(dialect="oracle")


@pytest.fixture
def postgres_modifier():
    """SqlModifier with PostgreSQL dialect."""
    return SqlModifier(dialect="postgres")


@pytest.fixture
def modifier_with_excluded():
    """SqlModifier that excludes 'countries' and 'currencies' tables."""
    return SqlModifier(
        dialect="mysql",
        excluded_tables=["countries", "currencies"]
    )


COLUMN = "tenant_id"
VALUE = "TENANT_A"