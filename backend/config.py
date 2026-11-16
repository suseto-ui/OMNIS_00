"""
O.M.N.I.S. Centrální Konfigurace (Pydantic v2 BaseSettings + Fail-Fast Production Guard)
"""

from __future__ import annotations

import os
from functools import lru_cache
from typing import List

try:
    from pydantic_settings import BaseSettings, SettingsConfigDict
except ImportError:  # pragma: no cover - fallback pro čistou stdlib/Starlette bez pydantic-settings
    from pydantic import BaseModel as BaseSettings

    def SettingsConfigDict(**kwargs):
        return kwargs


_PLACEHOLDER_PREFIXES = ("your-secure-", "replace-with-", "changeme", "dummy")


def _is_missing_or_placeholder(value: str) -> bool:
    cleaned = (value or "").strip()
    if not cleaned:
        return True
    lowered = cleaned.lower()
    return any(lowered.startswith(prefix) for prefix in _PLACEHOLDER_PREFIXES)


class OmnisSettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    ENV: str = os.getenv("ENV", "development")
    SECRET_KEY: str = os.getenv("SECRET_KEY", "")
    DATABASE_URL: str = os.getenv("DATABASE_URL", "")
    GEMINI_API_KEYS: str = os.getenv("GEMINI_API_KEYS", os.getenv("GEMINI_API_KEY", ""))
    AUDIT_HMAC_KEY: str = os.getenv("AUDIT_HMAC_KEY", "")
    FIREBASE_PROJECT_ID: str = os.getenv("FIREBASE_PROJECT_ID", "gen-lang-client-0328950802")
    FIRESTORE_DATABASE_ID: str = os.getenv(
        "FIRESTORE_DATABASE_ID",
        "ai-studio-android-omnis-fba4ba28-3ec2-4eaf-a1a7-338554af8dfe",
    )
    REQUIRE_APP_CHECK: bool = os.getenv("REQUIRE_APP_CHECK", "false").lower() in ("1", "true", "yes")
    OMNIS_SYNC_API_KEY: str = os.getenv("OMNIS_SYNC_API_KEY", "")
    ENABLE_CLOUD_KMS: bool = os.getenv("ENABLE_CLOUD_KMS", "false").lower() in ("1", "true", "yes")
    CLOUD_KMS_KEY_NAME: str = os.getenv("CLOUD_KMS_KEY_NAME", "")
    ENABLE_FIRESTORE_CQRS_PROJECTION: bool = os.getenv("ENABLE_FIRESTORE_CQRS_PROJECTION", "true").lower() in (
        "1",
        "true",
        "yes",
    )

    @property
    def is_production(self) -> bool:
        return self.ENV.strip().lower() == "production"

    def gemini_key_list(self) -> List[str]:
        candidates = [k.strip() for k in self.GEMINI_API_KEYS.split(",") if k.strip()]
        for env_key in ("GEMINI_API_KEY", "GEMINI_API_KEY_2", "GEMINI_API_KEY_3"):
            val = os.getenv(env_key, "").strip()
            if val and val not in candidates and not _is_missing_or_placeholder(val):
                candidates.append(val)
        return candidates

    def validate_production_secrets(self) -> None:
        if not self.is_production:
            return
        missing: List[str] = []
        if _is_missing_or_placeholder(self.SECRET_KEY):
            missing.append("SECRET_KEY")
        if _is_missing_or_placeholder(self.AUDIT_HMAC_KEY):
            missing.append("AUDIT_HMAC_KEY")
        if _is_missing_or_placeholder(self.OMNIS_SYNC_API_KEY):
            missing.append("OMNIS_SYNC_API_KEY")
        has_firestore = not _is_missing_or_placeholder(self.FIRESTORE_DATABASE_ID)
        has_sql = not _is_missing_or_placeholder(self.DATABASE_URL)
        if not has_firestore and not has_sql:
            missing.append("DATABASE_URL or FIRESTORE_DATABASE_ID")
        if missing:
            raise RuntimeError(
                f"CRITICAL STARTUP FAILURE: Missing required production secrets: {', '.join(missing)}"
            )


@lru_cache(maxsize=1)
def get_settings() -> OmnisSettings:
    settings = OmnisSettings(
        ENV=os.getenv("ENV", "development"),
        SECRET_KEY=os.getenv("SECRET_KEY", ""),
        DATABASE_URL=os.getenv("DATABASE_URL", ""),
        GEMINI_API_KEYS=os.getenv("GEMINI_API_KEYS", os.getenv("GEMINI_API_KEY", "")),
        AUDIT_HMAC_KEY=os.getenv("AUDIT_HMAC_KEY", ""),
        FIREBASE_PROJECT_ID=os.getenv("FIREBASE_PROJECT_ID", "gen-lang-client-0328950802"),
        FIRESTORE_DATABASE_ID=os.getenv(
            "FIRESTORE_DATABASE_ID",
            "ai-studio-android-omnis-fba4ba28-3ec2-4eaf-a1a7-338554af8dfe",
        ),
        REQUIRE_APP_CHECK=os.getenv("REQUIRE_APP_CHECK", "false").lower() in ("1", "true", "yes"),
        OMNIS_SYNC_API_KEY=os.getenv("OMNIS_SYNC_API_KEY", ""),
        ENABLE_CLOUD_KMS=os.getenv("ENABLE_CLOUD_KMS", "false").lower() in ("1", "true", "yes"),
        CLOUD_KMS_KEY_NAME=os.getenv("CLOUD_KMS_KEY_NAME", ""),
        ENABLE_FIRESTORE_CQRS_PROJECTION=os.getenv("ENABLE_FIRESTORE_CQRS_PROJECTION", "true").lower()
        in ("1", "true", "yes"),
    )
    settings.validate_production_secrets()
    return settings
