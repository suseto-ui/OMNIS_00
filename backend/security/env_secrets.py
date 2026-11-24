import os
from typing import Dict, Optional

_SECRET_CACHE: Dict[str, Optional[str]] = {}


def get_secret(secret_id: str, default: Optional[str] = None) -> Optional[str]:
    """
    Získá tajný klíč z in-memory cache nebo proměnných prostředí (.env / OS env).
    """
    if secret_id in _SECRET_CACHE:
        val = _SECRET_CACHE[secret_id]
        return val if val is not None else default

    env_val = os.getenv(secret_id)
    if env_val and env_val.strip():
        cleaned = env_val.strip()
        _SECRET_CACHE[secret_id] = cleaned
        return cleaned

    _SECRET_CACHE[secret_id] = None
    return default
