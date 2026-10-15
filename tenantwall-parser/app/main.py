from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pathlib import Path
import sqlglot

from app.models import ModifyRequest, ModifyResponse, HealthResponse
from app.modifier import SqlModifier, SUPPORTED_DIALECTS

app = FastAPI(
    title="TenantWall Parser",
    description="Parses SQL and injects tenant filters (SELECT, UPDATE, DELETE, INSERT).",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)


@app.get("/", response_model=HealthResponse)
def health():
    return HealthResponse(
        status="ok",
        service="TenantWall Parser",
        version="1.0.0",
        supported_dialects=SUPPORTED_DIALECTS,
    )


@app.post("/api/modify", response_model=ModifyResponse)
def modify(req: ModifyRequest):
    try:
        # Detect statement type (for the response)
        stmt_type = _detect_statement_type(req.sql, req.dialect)

        modifier = SqlModifier(
            dialect=req.dialect,
            excluded_tables=req.excluded_tables,
        )
        result = modifier.add_filter(req.sql, req.column, req.value)

        return ModifyResponse(
            success=True,
            result=result,
            dialect=req.dialect,
            skipped_tables=list(modifier.excluded_tables) if modifier.excluded_tables else None,
            statement_type=stmt_type,
        )
    except ValueError as e:
        return ModifyResponse(success=False, error=str(e))
    except Exception as e:
        return ModifyResponse(success=False, error=f"Unexpected error: {e}")


@app.get("/api/modify-from-file", response_model=ModifyResponse)
def modify_from_file(
    file: str = "complex.sql",
    column: str = "tenant_id",
    value: str = "maksoud",
    dialect: str = "oracle",
    excluded: str = "",
):
    base_dir = Path(__file__).resolve().parent.parent
    file_path = base_dir / file

    if not file_path.exists():
        return ModifyResponse(success=False, error=f"File not found: {file_path}")

    try:
        sql = file_path.read_text(encoding="utf-8")
    except Exception as e:
        return ModifyResponse(success=False, error=f"Failed to read file: {e}")

    excluded_tables = [t.strip() for t in excluded.split(",") if t.strip()]

    try:
        stmt_type = _detect_statement_type(sql, dialect)
        modifier = SqlModifier(dialect=dialect, excluded_tables=excluded_tables)
        result = modifier.add_filter(sql, column, value)
        return ModifyResponse(
            success=True,
            result=result,
            dialect=dialect,
            skipped_tables=excluded_tables if excluded_tables else None,
            statement_type=stmt_type,
        )
    except ValueError as e:
        return ModifyResponse(success=False, error=str(e))
    except Exception as e:
        return ModifyResponse(success=False, error=f"Unexpected error: {e}")


def _detect_statement_type(sql: str, dialect: str) -> str:
    """Return a short label for the SQL statement type."""
    try:
        tree = sqlglot.parse_one(sql, dialect=dialect)
        return type(tree).__name__
    except Exception:
        return "Unknown"