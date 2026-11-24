"""
OMNIS by Suseto — Sync Gateway Router
Poskytuje zabezpečené REST API endpointy pro idempotentní synchronizaci zpráv,
paměťových fragmentů, 8D telemetrie a Transactional Outbox dávek mezi Android klientem
a PostgreSQL 17 (pgvector + Apache AGE + ElectricSQL 1.8.1) v Non-GCP architektuře.
"""

from __future__ import annotations

import hmac
import json
import logging
import os
import time
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends, Header, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession

from backend.config import get_settings
from backend.database import DATABASE_URL, get_db
from backend.models import (
    OmnisSyncMemory,
    OmnisSyncMessage,
    OmnisSyncTelemetry,
    SyncOutboxReceipt,
)
from backend.security import (
    AuthenticatedCaller,
    decode_access_token,
    verify_firebase_and_appcheck,
)

logger = logging.getLogger("omnis.sync_gateway")

sync_router = APIRouter(tags=["Sync Gateway"])


# ==========================================================
# Autentizační závislost pro synchronizační bránu
# ==========================================================

async def verify_gateway_auth(
    authorization: Optional[str] = Header(None),
    x_omnis_api_key: Optional[str] = Header(None, alias="X-OMNIS-API-KEY"),
    x_firebase_appcheck: Optional[str] = Header(None, alias="X-Firebase-AppCheck"),
) -> AuthenticatedCaller:
    """
    Deleguje verifikaci na Firebase Auth + App Check guard (verify_firebase_and_appcheck),
    s podporou OMNIS_SYNC_API_KEY a interního OMNIS JWT pro zpětnou kompatibilitu.
    """
    return await verify_firebase_and_appcheck(
        authorization=authorization,
        x_firebase_appcheck=x_firebase_appcheck,
        x_omnis_api_key=x_omnis_api_key,
    )


# ==========================================================
# Pydantic Schémata pro synchronizaci
# ==========================================================

class SyncMessageItem(BaseModel):
    id: int
    idempotency_key: Optional[str] = None
    role: str
    content: str
    cognitive_process: Optional[str] = ""
    follow_up_questions: Optional[str] = ""
    val_sys: float = 0.5
    val_econ: float = 0.5
    val_psych: float = 0.5
    val_eco: float = 0.5
    val_law: float = 0.5
    val_sec: float = 0.5
    val_phys: float = 0.5
    val_soc: float = 0.5
    composite_score: float = 0.5
    domain: str = "SYSTEMS_INTELLIGENCE"
    attached_image_path: Optional[str] = None
    timestamp: int
    defense_tier: str = "APPROVED"
    defense_notes: Optional[str] = ""
    thread_id: str = "thread_main"
    thread_title: str = "Hlavní vlákno"
    user_name: str = "operator"


class SyncMessagesBatchRequest(BaseModel):
    messages: List[SyncMessageItem] = Field(default_factory=list)
    records: Optional[List[SyncMessageItem]] = None
    client_version: Optional[str] = "4.5"


class SyncMemoryItem(BaseModel):
    id: int
    idempotency_key: Optional[str] = None
    content: Optional[str] = ""
    title: Optional[str] = None
    summary: Optional[str] = None
    embedding_64: Optional[List[float]] = None
    domain: str = "SYSTEMS_INTELLIGENCE"
    val_sys: float = 0.5
    val_econ: float = 0.5
    val_psych: float = 0.5
    val_eco: float = 0.5
    val_law: float = 0.5
    val_sec: float = 0.5
    val_phys: float = 0.5
    val_soc: float = 0.5
    importance_score: float = 1.0
    timestamp: int
    user_name: str = "operator"


class SyncMemoryBatchRequest(BaseModel):
    fragments: List[SyncMemoryItem] = Field(default_factory=list)
    client_version: Optional[str] = "4.5"


class SyncTelemetryItem(BaseModel):
    level: str
    tag: str
    message: str
    metadata_json: Optional[str] = "{}"
    timestamp: int


class SyncTelemetryBatchRequest(BaseModel):
    logs: List[SyncTelemetryItem] = Field(default_factory=list)
    client_version: Optional[str] = "4.5"


class SyncOutboxItem(BaseModel):
    idempotency_key: str = Field(..., min_length=8, max_length=128)
    entity_type: str = Field(..., min_length=1, max_length=64)
    entity_id: int
    session_id: str = "thread_main"
    sequence_number: int = 0
    payload_json: str = "{}"
    created_at: int = Field(default_factory=lambda: int(time.time() * 1000))


class SyncOutboxBatchRequest(BaseModel):
    items: List[SyncOutboxItem] = Field(default_factory=list)
    client_version: Optional[str] = "4.5"


class SyncOutboxBatchResponse(BaseModel):
    status: str = "SUCCESS"
    synced_count: int
    deduplicated_count: int = 0
    firestore_projected_count: int = 0
    accepted_keys: List[str] = Field(default_factory=list)
    message: str
    server_timestamp: int = Field(default_factory=lambda: int(time.time() * 1000))


class SyncResponse(BaseModel):
    status: str = "SUCCESS"
    synced_count: int
    message: str
    server_timestamp: int = Field(default_factory=lambda: int(time.time() * 1000))


class HealthResponse(BaseModel):
    status: str
    database: str
    service: str = "O.M.N.I.S. Core REST Gateway"
    version: str = "4.5"
    database_url_configured: bool
    connection_pool: Dict[str, Any]
    timestamp: int = Field(default_factory=lambda: int(time.time() * 1000))


async def _insert_receipt_idempotent(
    db: AsyncSession,
    receipt_values: Dict[str, Any],
) -> bool:
    """
    Vloží potvrzení Transactional Outboxu pomocí ON CONFLICT (idempotency_key) DO NOTHING.
    Vrací True, pokud byl záznam nově vložen; False při opakovaném doručení (deduplikace).
    """
    bind = getattr(db, "bind", None)
    dialect_name = getattr(getattr(bind, "dialect", None), "name", "")
    key = receipt_values["idempotency_key"]

    if dialect_name == "postgresql":
        from sqlalchemy.dialects.postgresql import insert as pg_insert
        stmt = (
            pg_insert(SyncOutboxReceipt)
            .values(**receipt_values)
            .on_conflict_do_nothing(index_elements=["idempotency_key"])
            .returning(SyncOutboxReceipt.idempotency_key)
        )
        res = await db.execute(stmt)
        return res.scalar_one_or_none() is not None

    if dialect_name == "sqlite":
        from sqlalchemy.dialects.sqlite import insert as sqlite_insert
        stmt = (
            sqlite_insert(SyncOutboxReceipt)
            .values(**receipt_values)
            .on_conflict_do_nothing(index_elements=["idempotency_key"])
        )
        res = await db.execute(stmt)
        return bool(getattr(res, "rowcount", 1))

    existing = await db.execute(
        select(SyncOutboxReceipt).where(SyncOutboxReceipt.idempotency_key == key)
    )
    if existing.scalar_one_or_none() is not None:
        return False
    db.add(SyncOutboxReceipt(**receipt_values))
    return True


# ==========================================================
# Health & Connection Check Endpointy
# ==========================================================

@sync_router.get("/health", response_model=HealthResponse)
@sync_router.get("/api/health", response_model=HealthResponse)
@sync_router.get("/api/v1/health", response_model=HealthResponse)
@sync_router.get("/sync/health/ping", response_model=HealthResponse)
@sync_router.get("/api/sync/health/ping", response_model=HealthResponse)
@sync_router.get("/api/v1/sync/health/ping", response_model=HealthResponse)
async def health_ping(db: AsyncSession = Depends(get_db)):
    """Rychlý diagnostický test spojení s PostgreSQL databází a stavu connection poolu."""
    is_db_connected = False
    error_detail = None
    try:
        res = await db.execute(text("SELECT 1"))
        if res:
            is_db_connected = True
    except Exception as e:
        logger.warning(f"Chyba při pingu databáze: {e}")
        error_detail = str(e)

    db_status = "CONNECTED" if is_db_connected else ("DISCONNECTED" if error_detail else "STANDBY (MOCK/LOCAL)")

    return HealthResponse(
        status="HEALTHY" if is_db_connected or not DATABASE_URL else "DEGRADED",
        database=db_status,
        database_url_configured=bool(DATABASE_URL),
        connection_pool={
            "pool_size": 10,
            "max_overflow": 10,
            "timeout_seconds": 30,
            "engine": "SQLAlchemy AsyncEngine (asyncpg)",
        },
    )


# ==========================================================
# Idempotentní Transactional Outbox Dávková Synchronizace
# ==========================================================

@sync_router.post("/api/v1/sync/batch", response_model=SyncOutboxBatchResponse)
async def sync_outbox_batch(
    payload: SyncOutboxBatchRequest,
    db: AsyncSession = Depends(get_db),
    caller: AuthenticatedCaller = Depends(verify_gateway_auth),
):
    """
    Idempotentní příjem Transactional Outbox položek z Android klienta.
    Opakované doručení se stejným idempotency_key je bezpečně deduplikováno.
    """
    synced = 0
    deduplicated = 0
    accepted_keys: List[str] = []

    for item in payload.items:
        inserted = await _insert_receipt_idempotent(
            db,
            {
                "idempotency_key": item.idempotency_key,
                "entity_type": item.entity_type,
                "entity_id": item.entity_id,
                "session_id": item.session_id,
                "caller_uid": caller.uid,
            },
        )
        accepted_keys.append(item.idempotency_key)
        if not inserted:
            deduplicated += 1
            continue

        try:
            body = json.loads(item.payload_json or "{}")
            if not isinstance(body, dict):
                body = {}
        except Exception:
            body = {}

        if item.entity_type.strip().lower() == "omnis_message":
            msg = OmnisSyncMessage(
                id=item.entity_id,
                idempotency_key=item.idempotency_key,
                role=str(body.get("role", "user")),
                content=str(body.get("content", "")),
                cognitive_process=str(body.get("cognitiveProcess") if "cognitiveProcess" in body else body.get("cognitive_process", "")),
                follow_up_questions=str(body.get("followUpQuestions") if "followUpQuestions" in body else body.get("follow_up_questions", "")),
                val_sys=float(body.get("valSys") if "valSys" in body else body.get("val_sys", 0.5)),
                val_econ=float(body.get("valEcon") if "valEcon" in body else body.get("val_econ", 0.5)),
                val_psych=float(body.get("valPsych") if "valPsych" in body else body.get("val_psych", 0.5)),
                val_eco=float(body.get("valEco") if "valEco" in body else body.get("val_eco", 0.5)),
                val_law=float(body.get("valLaw") if "valLaw" in body else body.get("val_law", 0.5)),
                val_sec=float(body.get("valSec") if "valSec" in body else body.get("val_sec", 0.5)),
                val_phys=float(body.get("valPhys") if "valPhys" in body else body.get("val_phys", 0.5)),
                val_soc=float(body.get("valSoc") if "valSoc" in body else body.get("val_soc", 0.5)),
                composite_score=float(body.get("compositeScore") if "compositeScore" in body else body.get("composite_score", 0.5)),
                domain=str(body.get("domain", "SYSTEMS_INTELLIGENCE")),
                attached_image_path=body.get("attachedImagePath") if "attachedImagePath" in body else body.get("attached_image_path"),
                timestamp=int(body.get("timestamp", item.created_at)),
                defense_tier=str(body.get("defenseTier") if "defenseTier" in body else body.get("defense_tier", "APPROVED")),
                defense_notes=str(body.get("defenseNotes") if "defenseNotes" in body else body.get("defense_notes", "")),
                is_synced_to_postgres=True,
                thread_id=str(body.get("threadId") if "threadId" in body else body.get("thread_id", item.session_id)),
                thread_title=str(body.get("threadTitle") if "threadTitle" in body else body.get("thread_title", "Hlavní vlákno")),
                user_name=str(body.get("userName") if "userName" in body else body.get("user_name", caller.uid)),
            )
            await db.merge(msg)
        synced += 1

    await db.commit()
    projected_count = 0
    settings = get_settings()
    if not settings.USE_OSS_POSTGRES_SYNC and settings.ENABLE_FIRESTORE_CQRS_PROJECTION:
        try:
            from backend.security.firestore_store import project_outbox_items_to_firestore

            projected_count = project_outbox_items_to_firestore(payload.items, caller.uid)
        except Exception as fs_exc:
            logger.warning("CQRS Firestore projection skipped: %s", fs_exc)

    return SyncOutboxBatchResponse(
        status="SUCCESS",
        synced_count=synced,
        deduplicated_count=deduplicated,
        firestore_projected_count=projected_count,
        accepted_keys=accepted_keys,
        message=f"Zpracováno {synced} nových outbox položek ({deduplicated} deduplikováno, {projected_count} CQRS projekcí).",
    )


# ==========================================================
# Synchronizace Zpráv (Messages / Records)
# ==========================================================

@sync_router.post("/api/v1/sync/messages", response_model=SyncResponse)
@sync_router.post("/api/sync/records/batch", response_model=SyncResponse)
@sync_router.post("/sync/records/batch", response_model=SyncResponse)
@sync_router.post("/api/v1/sync/records/batch", response_model=SyncResponse)
async def sync_messages_batch(
    payload: SyncMessagesBatchRequest,
    db: AsyncSession = Depends(get_db),
    caller: AuthenticatedCaller = Depends(verify_gateway_auth),
):
    """Příjem dávky zpráv z mobilního klienta a jejich bezpečné uložení do PostgreSQL."""
    items = payload.messages or payload.records or []
    synced = 0

    for item in items:
        try:
            msg = OmnisSyncMessage(
                id=item.id,
                idempotency_key=item.idempotency_key,
                role=item.role,
                content=item.content,
                cognitive_process=item.cognitive_process or "",
                follow_up_questions=item.follow_up_questions or "",
                val_sys=item.val_sys,
                val_econ=item.val_econ,
                val_psych=item.val_psych,
                val_eco=item.val_eco,
                val_law=item.val_law,
                val_sec=item.val_sec,
                val_phys=item.val_phys,
                val_soc=item.val_soc,
                composite_score=item.composite_score,
                domain=item.domain,
                attached_image_path=item.attached_image_path,
                timestamp=item.timestamp,
                defense_tier=item.defense_tier,
                defense_notes=item.defense_notes or "",
                is_synced_to_postgres=True,
                thread_id=item.thread_id,
                thread_title=item.thread_title,
                user_name=item.user_name,
            )
            await db.merge(msg)
            synced += 1
        except Exception as e:
            logger.error(f"Chyba při ukládání zprávy {item.id}: {e}")

    await db.commit()
    return SyncResponse(
        status="SUCCESS",
        synced_count=synced,
        message=f"Úspěšně synchronizováno {synced} zpráv do PostgreSQL (caller={caller.uid}).",
    )


# ==========================================================
# Synchronizace Paměťových Fragmentů (Memory Fragments)
# ==========================================================

@sync_router.post("/api/v1/sync/memory", response_model=SyncResponse)
@sync_router.post("/api/sync/fragments/batch", response_model=SyncResponse)
@sync_router.post("/sync/fragments/batch", response_model=SyncResponse)
@sync_router.post("/api/v1/sync/fragments", response_model=SyncResponse)
async def sync_memory_batch(
    payload: SyncMemoryBatchRequest,
    db: AsyncSession = Depends(get_db),
    caller: AuthenticatedCaller = Depends(verify_gateway_auth),
):
    """Příjem dávky paměťových fragmentů z mobilního klienta a jejich bezpečné uložení do PostgreSQL."""
    items = payload.fragments or []
    synced = 0

    for item in items:
        try:
            content_text = item.content or f"{item.title or ''} {item.summary or ''}".strip()
            vec_64 = item.embedding_64 if (item.embedding_64 and len(item.embedding_64) == 64) else None
            mem = OmnisSyncMemory(
                id=item.id,
                idempotency_key=item.idempotency_key,
                content=content_text,
                embedding_64=vec_64,
                domain=item.domain,
                val_sys=item.val_sys,
                val_econ=item.val_econ,
                val_psych=item.val_psych,
                val_eco=item.val_eco,
                val_law=item.val_law,
                val_sec=item.val_sec,
                val_phys=item.val_phys,
                val_soc=item.val_soc,
                importance_score=item.importance_score,
                timestamp=item.timestamp,
                user_name=item.user_name,
            )
            await db.merge(mem)
            synced += 1
        except Exception as e:
            logger.error(f"Chyba při ukládání paměťového fragmentu {item.id}: {e}")

    await db.commit()
    return SyncResponse(
        status="SUCCESS",
        synced_count=synced,
        message=f"Úspěšně synchronizováno {synced} paměťových fragmentů do PostgreSQL (caller={caller.uid}).",
    )


# ==========================================================
# Synchronizace 8D Telemetrie (Telemetry Logs)
# ==========================================================

@sync_router.post("/api/v1/sync/telemetry", response_model=SyncResponse)
@sync_router.post("/api/sync/telemetry/batch", response_model=SyncResponse)
@sync_router.post("/sync/telemetry/batch", response_model=SyncResponse)
@sync_router.post("/api/v1/sync/telemetry/batch", response_model=SyncResponse)
async def sync_telemetry_batch(
    payload: SyncTelemetryBatchRequest,
    db: AsyncSession = Depends(get_db),
    caller: AuthenticatedCaller = Depends(verify_gateway_auth),
):
    """Příjem telemetrických záznamů a systémových logů z mobilního zařízení."""
    items = payload.logs or []
    synced = 0

    for item in items:
        try:
            log_entry = OmnisSyncTelemetry(
                level=item.level,
                tag=item.tag,
                message=item.message,
                metadata_json=item.metadata_json or "{}",
                timestamp=item.timestamp,
            )
            db.add(log_entry)
            synced += 1
        except Exception as e:
            logger.error(f"Chyba při ukládání telemetrie: {e}")

    await db.commit()
    return SyncResponse(
        status="SUCCESS",
        synced_count=synced,
        message=f"Úspěšně synchronizováno {synced} telemetrických záznamů do PostgreSQL (caller={caller.uid}).",
    )
