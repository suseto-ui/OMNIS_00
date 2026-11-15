from __future__ import annotations

import logging
import os
import secrets
from datetime import datetime, timedelta, timezone
from typing import Optional

from jose import jwt
from passlib.context import CryptContext

from backend.gcp_secrets import get_secret

_sec_logger = logging.getLogger("omnis.security.auth")

_configured_secret = (get_secret("SECRET_KEY") or get_secret("JWT_SECRET_KEY") or "").strip()
if not _configured_secret:
    if os.getenv("ENV", "").strip().lower() == "production":
        raise RuntimeError("SECRET_KEY musí být v produkčním prostředí explicitně nastaven.")
    _sec_logger.warning("SECRET_KEY is not configured; generating ephemeral per-process dev signing key.")
    _configured_secret = secrets.token_urlsafe(32)

SECRET_KEY = _configured_secret
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 24 * 7

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    return pwd_context.verify(plain_password, hashed_password)


def get_password_hash(password: str) -> str:
    return pwd_context.hash(password)


def create_access_token(data: dict, expires_delta: Optional[timedelta] = None) -> str:
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.now(timezone.utc) + expires_delta
    else:
        expire = datetime.now(timezone.utc) + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)


def decode_access_token(token: str) -> Optional[dict]:
    try:
        return jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
    except Exception:
        return None
