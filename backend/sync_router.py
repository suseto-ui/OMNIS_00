"""
OMNIS by Suseto — Legacy Sync Router Compatibility Shim.
Všechny synchronizační endpointy jsou sjednoceny v `backend/sync_gateway.py` (`sync_router`).
"""

from __future__ import annotations

from backend.sync_gateway import sync_router as router, verify_gateway_auth as verify_sync_auth

__all__ = ["router", "verify_sync_auth"]
