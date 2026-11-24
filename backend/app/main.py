"""
OMNIS by Suseto — Unified FastAPI Entrypoint Compatibility Bridge (backend/app/main.py).
Deleguje na sjednocenou instanci `app` v `backend.main` tak, aby neexistovaly
dva rozdílné ASGI servery bez sync/auth/AKGE routerů.
"""

from __future__ import annotations

from backend.main import app, healthz, lifespan

__all__ = ["app", "healthz", "lifespan"]
