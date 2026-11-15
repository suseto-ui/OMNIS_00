import hmac
import logging
import os
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Header, status
from sqlalchemy.ext.asyncio import AsyncSession

from backend.database import get_db, get_pool_status
from backend.security import decode_access_token
from backend.sync_service import SyncService
from backend.schemas import (
    SyncMessagesRequest,
    SyncMessagesResponse,
    SyncMemoryRequest,
    SyncMemoryResponse,
    SyncTelemetryRequest,
    SyncTelemetryResponse,
    HealthCheckResponse,
)

logger = logging.getLogger("omnis.sync_router")

router = APIRouter(prefix="/api/v1", tags=["Data Sync & Gateway"])


async def verify_sync_auth(
    authorization: Optional[str] = Header(None),
    x_omnis_api_key: Optional[str] = Header(None)
):
    """
    Vyžaduje platný X-OMNIS-API-KEY (pokud je nakonfigurován OMNIS_SYNC_API_KEY)
    nebo platný podepsaný uživatelský JWT v Authorization: Bearer <jwt>.
    """
    expected_api_key = os.getenv("OMNIS_SYNC_API_KEY", "").strip()
    candidate_key = x_omnis_api_key.strip() if x_omnis_api_key else None

    if expected_api_key and candidate_key and hmac.compare_digest(candidate_key, expected_api_key):
        return

    if authorization and authorization.startswith("Bearer "):
        bearer_token = authorization.split("Bearer ", 1)[1].strip()
        if expected_api_key and hmac.compare_digest(bearer_token, expected_api_key):
            return
        payload = decode_access_token(bearer_token)
        if payload and payload.get("sub"):
            return

    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Neplatný nebo chybějící autorizační Bearer token / X-OMNIS-API-KEY."
    )


@router.get("/health", response_model=HealthCheckResponse)
async def health_check():
    """
    Diagnostika zdraví API Gateway mezivrstvy a stavu connection poolu PostgreSQL.
    """
    pool_info = get_pool_status()
    db_status = "HEALTHY" if pool_info.get("status") in ["connected", "in_memory_emulation"] else "DEGRADED"

    return HealthCheckResponse(
        status=db_status,
        database=pool_info,
        service="O.M.N.I.S. Core REST Gateway",
        version="4.5"
    )


@router.post("/sync/messages", response_model=SyncMessagesResponse)
async def sync_messages(
    payload: SyncMessagesRequest,
    session: AsyncSession = Depends(get_db),
    _: None = Depends(verify_sync_auth)
):
    """
    Dávková synchronizace zpráv z mobilního klienta do PostgreSQL tabulky omnis_messages.
    """
    try:
        count, msg = await SyncService.upsert_messages(session, payload.messages)
        return SyncMessagesResponse(
            synced_count=count,
            status="SUCCESS",
            message=msg
        )
    except Exception as e:
        logger.exception("Sync messages failed: %s", e)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Interní chyba při ukládání zpráv do PostgreSQL."
        )


@router.post("/sync/memory", response_model=SyncMemoryResponse)
async def sync_memory(
    payload: SyncMemoryRequest,
    session: AsyncSession = Depends(get_db),
    _: None = Depends(verify_sync_auth)
):
    """
    Dávková synchronizace paměťových fragmentů do PostgreSQL tabulky omnis_memory_records.
    """
    try:
        count, msg = await SyncService.upsert_memory_fragments(session, payload.fragments)
        return SyncMemoryResponse(
            synced_count=count,
            status="SUCCESS",
            message=msg
        )
    except Exception as e:
        logger.exception("Sync memory failed: %s", e)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Interní chyba při ukládání paměťových fragmentů do PostgreSQL."
        )


@router.post("/sync/telemetry", response_model=SyncTelemetryResponse)
async def sync_telemetry(
    payload: SyncTelemetryRequest,
    session: AsyncSession = Depends(get_db),
    _: None = Depends(verify_sync_auth)
):
    """
    Uložení diagnostických záznamů telemetrie do PostgreSQL tabulky omnis_telemetry.
    """
    try:
        count, _ = await SyncService.insert_telemetry_logs(session, payload.logs)
        return SyncTelemetryResponse(
            synced_count=count,
            status="SUCCESS"
        )
    except Exception as e:
        logger.exception("Sync telemetry failed: %s", e)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Interní chyba při ukládání telemetrie."
        )
