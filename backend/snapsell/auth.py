"""Firebase ID-token verification plus an email allowlist.

The app sends `Authorization: Bearer <Firebase ID token>`. We verify it with the
Admin SDK (which needs GOOGLE_APPLICATION_CREDENTIALS locally, or the runtime
service account on Cloud Run) and then check the email against SNAPSELL_ALLOWED_EMAILS.

SNAPSELL_AUTH_DISABLED=1 short-circuits everything for local development.
"""

from __future__ import annotations

import logging
import threading
from collections import defaultdict
from dataclasses import dataclass
from datetime import UTC, datetime

from fastapi import Depends, HTTPException, Request
from fastapi.concurrency import run_in_threadpool

from snapsell.config import Settings

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class User:
    uid: str
    email: str | None


DEV_USER = User(uid="dev", email="dev@localhost")

_firebase_lock = threading.Lock()
_firebase_ready = False


def _ensure_firebase() -> None:
    global _firebase_ready
    if _firebase_ready:
        return
    with _firebase_lock:
        if _firebase_ready:
            return
        import firebase_admin

        if not firebase_admin._apps:
            firebase_admin.initialize_app()
        _firebase_ready = True


def _verify(token: str) -> User:
    from firebase_admin import auth as fb_auth

    _ensure_firebase()
    try:
        claims = fb_auth.verify_id_token(token)
    except Exception as e:  # noqa: BLE001 - every failure here is a 401
        raise HTTPException(status_code=401, detail="invalid or expired token") from e
    email = claims.get("email")
    return User(uid=claims["uid"], email=email.lower() if email else None)


class DailyCap:
    """Best-effort per-user cap on paid calls. In-memory; resets on restart and at UTC midnight."""

    def __init__(self, limit: int) -> None:
        self._limit = limit
        self._day = ""
        self._counts: dict[str, int] = defaultdict(int)
        self._lock = threading.Lock()

    def check_and_increment(self, uid: str) -> None:
        today = datetime.now(UTC).strftime("%Y-%m-%d")
        with self._lock:
            if today != self._day:
                self._day = today
                self._counts.clear()
            if self._counts[uid] >= self._limit:
                raise HTTPException(status_code=429, detail="daily limit reached")
            self._counts[uid] += 1


def get_settings_dep(request: Request) -> Settings:
    return request.app.state.settings


async def current_user(request: Request, settings: Settings = Depends(get_settings_dep)) -> User:
    if settings.auth_disabled:
        return DEV_USER
    header = request.headers.get("authorization", "")
    scheme, _, token = header.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(status_code=401, detail="missing bearer token")
    user = await run_in_threadpool(_verify, token)
    allowed = settings.allowed_email_set
    if allowed and (user.email is None or user.email not in allowed):
        log.info("rejected uid=%s email=%s: not on allowlist", user.uid, user.email)
        raise HTTPException(status_code=403, detail="not on the allowlist")
    return user


async def metered_user(request: Request, user: User = Depends(current_user)) -> User:
    """current_user plus the daily cap, for the endpoints that spend money."""
    request.app.state.daily_cap.check_and_increment(user.uid)
    return user
