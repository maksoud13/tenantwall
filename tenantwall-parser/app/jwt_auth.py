import hashlib
import threading
import time

import jwt
from fastapi import HTTPException, Request

from app.config import ISSUER, get_secret, is_enforce_auth


# ═══ Nonce replay protection (in-memory, TTL) ═══
_NONCE_CACHE: dict[str, float] = {}
_NONCE_LOCK = threading.Lock()


def _cleanup_nonces(now: float) -> None:
    expired = [n for n, exp in _NONCE_CACHE.items() if exp < now]
    for n in expired:
        del _NONCE_CACHE[n]


def _consume_nonce(nonce: str, ttl_seconds: int) -> bool:
    """Returns True if fresh (not seen), False if replay."""
    now = time.time()
    with _NONCE_LOCK:
        _cleanup_nonces(now)
        if nonce in _NONCE_CACHE:
            return False
        _NONCE_CACHE[nonce] = now + ttl_seconds + 10  # small buffer
    return True


def _clear_nonce_cache() -> None:
    """For tests."""
    with _NONCE_LOCK:
        _NONCE_CACHE.clear()


# ═══ Verification ═══

def verify_token(token: str, body: bytes) -> dict:
    """Verify JWT signature, expiry, issuer, body hash, nonce."""
    secret = get_secret()
    if not secret:
        raise HTTPException(status_code=500, detail="Server auth misconfigured")

    try:
        payload = jwt.decode(
            token,
            secret,
            algorithms=["HS256"],
            issuer=ISSUER,
            options={"require": ["exp", "iat", "iss", "nonce", "body_sha256"]},
        )
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token expired")
    except jwt.InvalidIssuerError:
        raise HTTPException(status_code=401, detail="Invalid issuer")
    except jwt.InvalidSignatureError:
        raise HTTPException(status_code=401, detail="Invalid signature")
    except jwt.PyJWTError as e:
        raise HTTPException(status_code=401, detail=f"Invalid token: {e}")

    # Body integrity
    expected_hash = hashlib.sha256(body).hexdigest()
 
    
    
    
    if payload.get("body_sha256") != expected_hash:
        raise HTTPException(status_code=401, detail="Body hash mismatch")

    # Replay protection
    nonce = payload.get("nonce")
    if not nonce or not isinstance(nonce, str):
        raise HTTPException(status_code=401, detail="Missing nonce")

    ttl = max(1, int(payload["exp"]) - int(payload["iat"]))
    if not _consume_nonce(nonce, ttl):
        raise HTTPException(status_code=401, detail="Nonce already used (replay)")

    return payload


def extract_bearer_token(request: Request) -> str | None:
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return None
    token = auth[7:].strip()
    return token or None


async def require_jwt(request: Request) -> dict | None:
    """FastAPI dependency: verifies JWT if ENFORCE_AUTH is on."""
    if not is_enforce_auth():
        return None

    token = extract_bearer_token(request)
    if token is None:
        raise HTTPException(
            status_code=401,
            detail="Missing Authorization: Bearer <token>",
            headers={"WWW-Authenticate": "Bearer"},
        )

    body = await request.body()
    return verify_token(token, body)