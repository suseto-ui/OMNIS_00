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
    REQUIRE_APP_CHECK: bool = os.getenv("REQUIRE_APP_CHECK", "false").lower() in ("1", "true", "yes")
    OMNIS_SYNC_API_KEY: str = os.getenv("OMNIS_SYNC_API_KEY", "")
    USE_OSS_POSTGRES_SYNC: bool = os.getenv("USE_OSS_POSTGRES_SYNC", "true").lower() in ("1", "true", "yes")
    AUDIT_DATABASE_URL: str = os.getenv("AUDIT_DATABASE_URL", "")
    ELECTRIC_URL: str = os.getenv("ELECTRIC_URL", "http://electric:3000")
    AGE_GRAPH_NAME: str = os.getenv("AGE_GRAPH_NAME", "omnis_causal_graph")
    LLM_PROVIDER_MODE: str = os.getenv("LLM_PROVIDER_MODE", "gemma4_local")
    GEMMA4_API_BASE_URL: str = os.getenv("GEMMA4_API_BASE_URL", "http://host.docker.internal:11434/v1")
    GEMMA4_MODEL_NAME: str = os.getenv("GEMMA4_MODEL_NAME", "gemma4:e4b")

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
        if _is_missing_or_placeholder(self.DATABASE_URL):
            missing.append("DATABASE_URL")
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
        REQUIRE_APP_CHECK=os.getenv("REQUIRE_APP_CHECK", "false").lower() in ("1", "true", "yes"),
        OMNIS_SYNC_API_KEY=os.getenv("OMNIS_SYNC_API_KEY", ""),
        USE_OSS_POSTGRES_SYNC=os.getenv("USE_OSS_POSTGRES_SYNC", "true").lower() in ("1", "true", "yes"),
        AUDIT_DATABASE_URL=os.getenv("AUDIT_DATABASE_URL", ""),
        ELECTRIC_URL=os.getenv("ELECTRIC_URL", "http://electric:3000"),
        AGE_GRAPH_NAME=os.getenv("AGE_GRAPH_NAME", "omnis_causal_graph"),
        LLM_PROVIDER_MODE=os.getenv("LLM_PROVIDER_MODE", "gemma4_local"),
        GEMMA4_API_BASE_URL=os.getenv("GEMMA4_API_BASE_URL", "http://host.docker.internal:11434/v1"),
        GEMMA4_MODEL_NAME=os.getenv("GEMMA4_MODEL_NAME", "gemma4:e4b"),
    )
    settings.validate_production_secrets()
    return settings
