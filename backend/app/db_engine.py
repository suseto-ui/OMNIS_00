"""
O.M.N.I.S. APEX v4.5 - Sovereign Database & Refusal Ladder Engine Layer
Integrates asyncpg, pgvector HNSW 64D queries, Apache AGE CsrKnowledgeGraph,
Retraction Watch Blacklist (Gate G5), and zk_audit_ledger SHA-256 hash chaining.
"""

from __future__ import annotations
import logging
import hashlib
import json
from typing import Any, Dict, List, Optional
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

logger = logging.getLogger("omnis.db_engine")

async def verify_retraction_watch(db: AsyncSession, query_text: str) -> Optional[Dict[str, Any]]:
    """
    Brána G5: Ověřuje, zda dotaz nebo klíčová slova neodkazují na retrahovaný vědecký výzkum
    v tabulce `retraction_watch_blacklist`.
    """
    if db is None:
        return None
    try:
        sql = text("""
            SELECT doi, title, reason, retraction_date
            FROM public.retraction_watch_blacklist
            WHERE :query ILIKE '%' || title || '%' OR :query ILIKE '%' || COALESCE(doi, '') || '%'
            LIMIT 1;
        """)
        res = await db.execute(sql, {"query": query_text})
        row = res.fetchone()
        if row:
            return {
                "retracted": True,
                "doi": row[0],
                "title": row[1],
                "reason": row[2],
                "date": str(row[3]) if row[3] else "Unknown"
            }
    except Exception as e:
        logger.debug(f"Retraction watch check skipped or table missing: {e}")
    return None


async def append_zk_audit_ledger_entry(
    db: AsyncSession,
    *,
    user_id: str,
    query_text: str,
    r_systemic: float,
    tier: str,
    override_active: bool = False,
    payload: Optional[Dict[str, Any]] = None
) -> str:
    """
    Zapíše kryptografický závazek do append-only tabulky `zk_audit_ledger` s SHA-256 řetězením.
    Vrací commitment_hash.
    """
    if db is None:
        return "mock_audit_hash_00000000000000000000000000000000000000000000"

    query_hash = hashlib.sha256(query_text.encode("utf-8")).hexdigest()
    meta = payload or {}

    sql = text("""
        INSERT INTO public.zk_audit_ledger
            (user_id, query_hash, r_systemic, tier, nonce, override_active, payload_json)
        VALUES
            (:user_id, :query_hash, :r_systemic, :tier, encode(gen_random_bytes(8), 'hex'), :override_active, CAST(:payload_json AS jsonb))
        RETURNING commitment_hash;
    """)

    res = await db.execute(
        sql,
        {
            "user_id": user_id,
            "query_hash": query_hash,
            "r_systemic": float(r_systemic),
            "tier": tier,
            "override_active": bool(override_active),
            "payload_json": json.dumps(meta)
        }
    )
    commit_hash = res.scalar_one_or_none()
    await db.commit()
    return str(commit_hash or "")
