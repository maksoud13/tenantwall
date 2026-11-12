import hashlib
import time

import jwt
import pytest
from fastapi.testclient import TestClient

from app.config import ISSUER
from app.jwt_auth import _clear_nonce_cache
from app.main import app

SECRET = "x" * 32
URL = "/api/modify"
VALID_BODY = b'{"sql":"SELECT * FROM products","column":"tenant_id","value":"T1","dialect":"mysql"}'

client = TestClient(app)


# ═══ Fixtures ═══

@pytest.fixture(autouse=True)
def auth_on(monkeypatch):
    monkeypatch.setenv("TENANTWALL_PARSER_SECRET", SECRET)
    monkeypatch.setenv("TENANTWALL_PARSER_ENFORCE_AUTH", "1")
    _clear_nonce_cache()
    yield
    _clear_nonce_cache()


def make_jwt(
    body: bytes,
    *,
    exp_delta: int = 60,
    nonce: str = "test-nonce",
    issuer: str = ISSUER,
    secret: str = None,
) -> str:
    now = int(time.time())
    payload = {
        "iss": issuer,
        "iat": now,
        "exp": now + exp_delta,
        "nonce": nonce,
        "body_sha256": hashlib.sha256(body).hexdigest(),
    }
    return jwt.encode(payload, secret or SECRET, algorithm="HS256")


def post(body: bytes, token: str | None = None):
    headers = {"Content-Type": "application/json"}
    if token is not None:
        headers["Authorization"] = f"Bearer {token}"
    return client.post(URL, content=body, headers=headers)


# ═══ Tests ═══

def test_health_no_auth():
    r = client.get("/")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_no_auth_returns_401():
    r = post(VALID_BODY)
    assert r.status_code == 401
    assert "Missing Authorization" in r.json()["detail"]


def test_valid_jwt_returns_200():
    token = make_jwt(VALID_BODY)
    r = post(VALID_BODY, token)
    assert r.status_code == 200
    data = r.json()
    assert data["success"] is True
    assert "tenant_id" in data["result"]


def test_expired_token_returns_401():
    token = make_jwt(VALID_BODY, exp_delta=-10)
    r = post(VALID_BODY, token)
    assert r.status_code == 401
    assert "expired" in r.json()["detail"].lower()


def test_wrong_issuer_returns_401():
    token = make_jwt(VALID_BODY, issuer="not-tenantwall")
    r = post(VALID_BODY, token)
    assert r.status_code == 401
    assert "issuer" in r.json()["detail"].lower()


def test_wrong_signature_returns_401():
    token = make_jwt(VALID_BODY, secret="wrong-secret-wrong-secret-wrong-secret-wrong")
    r = post(VALID_BODY, token)
    assert r.status_code == 401
    assert "signature" in r.json()["detail"].lower()


def test_body_tampered_returns_401():
    token = make_jwt(VALID_BODY)
    tampered = b'{"sql":"SELECT * FROM products","column":"tenant_id","value":"T2","dialect":"mysql"}'
    r = post(tampered, token)
    assert r.status_code == 401
    assert "hash mismatch" in r.json()["detail"].lower()


def test_replay_returns_401():
    token = make_jwt(VALID_BODY, nonce="fixed-nonce-replay")
    r1 = post(VALID_BODY, token)
    assert r1.status_code == 200
    r2 = post(VALID_BODY, token)
    assert r2.status_code == 401
    assert "replay" in r2.json()["detail"].lower()


def test_malformed_token_returns_401():
    r = post(VALID_BODY, "not-a-jwt")
    assert r.status_code == 401


def test_auth_disabled_still_works(monkeypatch):
    monkeypatch.setenv("TENANTWALL_PARSER_ENFORCE_AUTH", "0")
    r = post(VALID_BODY)  # no token
    assert r.status_code == 200