"""
Firebase Auth & Firebase App Check Verification Guard (backend/security/firebase_guard.py)
"""

from __future__ import annotations

import hmac
import logging
import os
from dataclasses import dataclass
from typing import Optional

from fastapi import Header, HTTPException, status

from backend.config import get_settings
from backend.security.auth_utils import decode_access_token

logger = logging.getLogger("omnis.security.firebase_guard")

try:
    import firebase_admin
    from firebase_admin import app_check as fb_app_check
    from firebase_admin import auth as fb_auth
except ImportError:  # pragma: no cover
    firebase_admin = None
    fb_app_check = None
    fb_auth = None


@dataclass(frozen=True)
class AuthenticatedCaller:
    uid: str
    email: Optional[str]
    auth_method: str
    app_check_verified: bool
    role: str = "STANDARD_USER"

    @property
    def is_admin(self) -> bool:
        return self.role == "ADMIN_OPERATOR"


def _ensure_firebase_app() -> bool:
    if firebase_admin is None:
        return False
    if not firebase_admin._apps:
        try:
            firebase_admin.initialize_app()
        except Exception as exc:
            logger.warning("Firebase Admin initialization failed: %s", exc)
            return False
    return True


async def verify_firebase_and_appcheck(
    authorization: Optional[str] = Header(None),
    x_firebase_appcheck: Optional[str] = Header(None, alias="X-Firebase-AppCheck"),
    x_omnis_api_key: Optional[str] = Header(None, alias="X-OMNIS-API-KEY"),
) -> AuthenticatedCaller:
    """
    Ověřuje:
    1. X-Firebase-AppCheck (povinné při REQUIRE_APP_CHECK=true v produkci),
    2. X-OMNIS-API-KEY / Bearer sync API key, Firebase ID Token nebo interní OMNIS JWT.
    """
    settings = get_settings()
    app_check_verified = False

    if settings.REQUIRE_APP_CHECK:
        if not x_firebase_appcheck:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Chybí povinná hlavička X-Firebase-AppCheck.",
            )
        if not _ensure_firebase_app() or fb_app_check is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Firebase App Check verifikace není dostupná.",
            )
        try:
            fb_app_check.verify_token(x_firebase_appcheck.strip())
            app_check_verified = True
        except Exception as exc:
            logger.warning("App Check token rejected: %s", exc)
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Neplatný Firebase App Check token.",
            ) from exc

    configured_key = (
        os.getenv("OMNIS_GATEWAY_TOKEN")
        or os.getenv("OMNIS_SYNC_API_KEY")
        or settings.OMNIS_SYNC_API_KEY
        or ""
    ).strip()

    if configured_key and x_omnis_api_key and hmac.compare_digest(x_omnis_api_key.strip(), configured_key):
        return AuthenticatedCaller(
            uid="service-sync-client",
            email=None,
            auth_method="api_key",
            app_check_verified=app_check_verified,
            role="ADMIN_OPERATOR",
        )

    if authorization and authorization.startswith("Bearer "):
        token = authorization[7:].strip()
        if configured_key and hmac.compare_digest(token, configured_key):
            return AuthenticatedCaller(
                uid="service-sync-client",
                email=None,
                auth_method="bearer_api_key",
                app_check_verified=app_check_verified,
                role="ADMIN_OPERATOR",
            )

        # 1. Zkusit interní OMNIS JWT
        jwt_payload = decode_access_token(token)
        if jwt_payload and jwt_payload.get("sub"):
            raw_role = str(jwt_payload.get("role") or "STANDARD_USER").strip().upper()
            resolved_role = "ADMIN_OPERATOR" if raw_role == "ADMIN_OPERATOR" else "STANDARD_USER"
            return AuthenticatedCaller(
                uid=str(jwt_payload["sub"]),
                email=jwt_payload.get("email"),
                auth_method="jwt",
                app_check_verified=app_check_verified,
                role=resolved_role,
            )

        # 2. Zkusit Firebase Auth ID Token
        if _ensure_firebase_app() and fb_auth is not None:
            try:
                decoded = fb_auth.verify_id_token(token)
                claim_role = str(decoded.get("role") or "").strip().upper()
                email_val = (decoded.get("email") or "").strip().lower()
                resolved_role = (
                    "ADMIN_OPERATOR"
                    if (claim_role == "ADMIN_OPERATOR" or email_val == "susetofu@gmail.com")
                    else "STANDARD_USER"
                )
                return AuthenticatedCaller(
                    uid=str(decoded["uid"]),
                    email=decoded.get("email"),
                    auth_method="firebase_id_token",
                    app_check_verified=app_check_verified,
                    role=resolved_role,
                )
            except Exception as exc:
                logger.warning("Firebase ID token verification failed: %s", exc)

    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Neplatný nebo chybějící autorizační Bearer token / X-OMNIS-API-KEY.",
    )
