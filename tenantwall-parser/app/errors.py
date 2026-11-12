"""Error sanitization for the TenantWall parser.

The parser sees raw SQL (table names, columns, values). Those must NEVER
leak to the client in error messages — only a reference ID for log
correlation.

The full exception detail is logged locally, keyed by reference ID.
Clients see a generic message + the reference.
"""

import logging
import uuid

logger = logging.getLogger("tenantwall.parser")


def new_ref() -> str:
    """Short, unique reference ID (8 hex chars)."""
    return uuid.uuid4().hex[:8]


def safe_value_error(exc: Exception) -> tuple[str, str]:
    """Sanitize a ValueError from the modifier layer.

    Covers: parse errors, unsupported dialects, unsupported statement types.
    All throw ValueError from SqlModifier. The message may include SQL
    fragments or a list of valid dialects — we never expose those.

    Returns (client_message, reference_id).
    """
    ref = new_ref()
    logger.warning("[%s] Request rejected: %s", ref, exc)
    return f"Request rejected (ref: {ref})", ref


def safe_internal_error(exc: Exception) -> tuple[str, str]:
    """Sanitize any unexpected exception.

    Logs full stack trace locally. Client gets a generic message + ref.
    """
    ref = new_ref()
    logger.exception("[%s] Internal error", ref)
    return f"Internal error (ref: {ref})", ref