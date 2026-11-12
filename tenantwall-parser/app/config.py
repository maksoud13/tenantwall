import os


ISSUER = "tenantwall-agent"


def get_secret() -> str | None:
    return os.getenv("TENANTWALL_PARSER_SECRET")


def is_enforce_auth() -> bool:
    v = os.getenv("TENANTWALL_PARSER_ENFORCE_AUTH", "0")
    return v.strip().lower() in ("1", "true", "yes", "on")


def get_token_ttl_seconds() -> int:
    try:
        return int(os.getenv("TENANTWALL_PARSER_TOKEN_TTL_SECONDS", "60"))
    except ValueError:
        return 60


def validate_config() -> None:
    """Fail fast on startup if auth is required but misconfigured."""
    if is_enforce_auth():
        s = get_secret()
        if not s or len(s) < 32:
            raise RuntimeError(
                "TENANTWALL_PARSER_ENFORCE_AUTH=1 requires "
                "TENANTWALL_PARSER_SECRET (min 32 chars)"
            )