from __future__ import annotations

from fastapi import HTTPException
from fastapi.testclient import TestClient

from snapsell import auth
from snapsell.config import Settings
from snapsell.main import create_app
from tests.conftest import FakeEbay, FakeLlm


def _app(monkeypatch, allowed: str, email: str | None):
    settings = Settings(
        auth_disabled=False, allowed_emails=allowed, ebay_client_id="x", ebay_client_secret="y"
    )
    # Replace the Firebase verifier so no network or credentials are needed.
    monkeypatch.setattr(auth, "_verify", lambda token: auth.User(uid="u1", email=email))
    return create_app(
        settings=settings,
        llm=FakeLlm(),
        ebay=FakeEbay("ebay_search_ipad_good.json", "ebay_search_ipad_all.json"),
    )


def test_missing_token_is_401(monkeypatch) -> None:
    with TestClient(_app(monkeypatch, "a@x.com", "a@x.com")) as c:
        assert c.get("/me").status_code == 401


def test_allowlisted_email_passes(monkeypatch) -> None:
    with TestClient(_app(monkeypatch, "A@X.com, b@x.com", "a@x.com")) as c:
        r = c.get("/me", headers={"Authorization": "Bearer tok"})
        assert r.status_code == 200 and r.json()["email"] == "a@x.com"


def test_unlisted_email_is_403(monkeypatch) -> None:
    with TestClient(_app(monkeypatch, "a@x.com", "stranger@x.com")) as c:
        assert c.get("/me", headers={"Authorization": "Bearer tok"}).status_code == 403


def test_empty_allowlist_admits_any_verified_user(monkeypatch) -> None:
    with TestClient(_app(monkeypatch, "", "anyone@x.com")) as c:
        assert c.get("/me", headers={"Authorization": "Bearer tok"}).status_code == 200


def test_invalid_token_is_401(monkeypatch) -> None:
    def boom(token: str):
        raise HTTPException(status_code=401, detail="invalid or expired token")

    settings = Settings(auth_disabled=False, ebay_client_id="x", ebay_client_secret="y")
    monkeypatch.setattr(auth, "_verify", boom)
    app = create_app(
        settings=settings,
        llm=FakeLlm(),
        ebay=FakeEbay("ebay_search_ipad_good.json", "ebay_search_ipad_all.json"),
    )
    with TestClient(app) as c:
        assert c.get("/me", headers={"Authorization": "Bearer bad"}).status_code == 401
