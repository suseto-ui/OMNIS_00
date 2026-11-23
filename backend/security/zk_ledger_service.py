"""
O.M.N.I.S. ZK Audit Ledger Service (backend/security/zk_ledger_service.py)
Striktě append-only kryptografický řetězec nad PostgreSQL 17 tabulkou `zk_audit_ledger`.
Vzorec závazku (1:1 parita s triggerem `zk_audit_ledger_before_insert` v `backend/db/init.sql`):
    CommitmentHash = SHA-256("OMNIS-ZK-PROOF||" || prev_hash || "||" || QueryHash || "||" || R_systemic || "||" || Tier || "||" || Nonce [|| "||OVERRIDE_ACTIVE"])
"""

from __future__ import annotations

import hashlib
import hmac
import json
import math
import uuid
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Sequence

try:
    from sqlalchemy import select, text
    from sqlalchemy.ext.asyncio import AsyncSession
    from backend.models import ZkAuditLedgerEntry
except ImportError:  # pragma: no cover
    select = None  # type: ignore[assignment]
    text = None  # type: ignore[assignment]
    AsyncSession = Any  # type: ignore[assignment,misc]
    ZkAuditLedgerEntry = Any  # type: ignore[assignment,misc]


@dataclass(frozen=True)
class LedgerAppendRecord:
    seq_id: Optional[int]
    entry_id: str
    user_id: str
    query_hash: str
    r_systemic: float
    tier: str
    nonce: str
    override_active: bool
    prev_hash: str
    commitment_hash: str


@dataclass(frozen=True)
class LedgerVerificationResult:
    valid: bool
    total_entries: int
    broken_at_seq_id: Optional[int] = None
    reason: Optional[str] = None
    head_commitment_hash: Optional[str] = None


class ZkAuditLedgerService:
    """
    Služba pro bezpečný zápis a deterministickou verifikaci kontinuity
    kryptografického řetězce v `zk_audit_ledger`.
    """

    @staticmethod
    def hash_query(query: str) -> str:
        return hashlib.sha256((query or "").encode("utf-8")).hexdigest()

    @staticmethod
    def compute_canonical_commitment(
        prev_hash: str,
        query_hash: str,
        r_systemic: float,
        tier: str,
        nonce: str,
        override_active: bool = False,
    ) -> str:
        r_safe = min(1.0, max(0.0, float(r_systemic))) if math.isfinite(float(r_systemic)) else 0.5
        canonical = (
            f"OMNIS-ZK-PROOF||{prev_hash}||{query_hash}||{r_safe:.4f}||{tier}||{nonce}"
        )
        if override_active:
            canonical += "||OVERRIDE_ACTIVE"
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()

    @classmethod
    def verify_canonical_commitment(
        cls,
        *,
        prev_hash: str,
        query: str,
        r_systemic: float,
        tier: str,
        nonce: str,
        expected_hash: str,
        override_active: bool = False,
    ) -> bool:
        q_hash = cls.hash_query(query)
        recomputed = cls.compute_canonical_commitment(
            prev_hash=prev_hash,
            query_hash=q_hash,
            r_systemic=r_systemic,
            tier=tier,
            nonce=nonce,
            override_active=override_active,
        )
        return hmac.compare_digest(recomputed, str(expected_hash or "").strip())

    @staticmethod
    async def ensure_sqlite_immutability_triggers(db: AsyncSession) -> None:
        """
        Vytvoří v SQLite testovacím prostředí ekvivalentní immutability triggery
        proti UPDATE a DELETE na `zk_audit_ledger` (v PostgreSQL je zajišťuje `init.sql`).
        """
        bind = getattr(db, "bind", None)
        dialect_name = getattr(getattr(bind, "dialect", None), "name", "")
        if dialect_name != "sqlite":
            return
        await db.execute(
            text(
                """
                CREATE TRIGGER IF NOT EXISTS trg_sqlite_zk_audit_no_update
                BEFORE UPDATE ON zk_audit_ledger
                BEGIN
                    SELECT RAISE(ABORT, 'SECURITY_VIOLATION: zk_audit_ledger is strictly append-only; UPDATE is prohibited.');
                END;
                """
            )
        )
        await db.execute(
            text(
                """
                CREATE TRIGGER IF NOT EXISTS trg_sqlite_zk_audit_no_delete
                BEFORE DELETE ON zk_audit_ledger
                BEGIN
                    SELECT RAISE(ABORT, 'SECURITY_VIOLATION: zk_audit_ledger is strictly append-only; DELETE is prohibited.');
                END;
                """
            )
        )

    @classmethod
    async def append_entry(
        cls,
        db: Optional[AsyncSession],
        *,
        user_id: str,
        query: str,
        r_systemic: float,
        tier: str,
        nonce: Optional[str] = None,
        override_active: bool = False,
        payload: Optional[Dict[str, Any]] = None,
    ) -> LedgerAppendRecord:
        q_hash = cls.hash_query(query)
        clean_nonce = (nonce or uuid.uuid4().hex[:16]).strip()[:64]
        clean_tier = (tier or "APPROVED").strip().upper()[:32]
        r_sys = round(float(r_systemic), 4)
        entry_uuid = str(uuid.uuid4())
        payload_dict = payload if isinstance(payload, dict) else {}

        # Fallback pro MockSession / bezdatabázový běh
        if db is None or not hasattr(db, "bind") or getattr(db, "bind", None) is None:
            commitment = cls.compute_canonical_commitment(
                prev_hash="GENESIS",
                query_hash=q_hash,
                r_systemic=r_sys,
                tier=clean_tier,
                nonce=clean_nonce,
                override_active=bool(override_active),
            )
            return LedgerAppendRecord(
                seq_id=None,
                entry_id=entry_uuid,
                user_id=user_id,
                query_hash=q_hash,
                r_systemic=r_sys,
                tier=clean_tier,
                nonce=clean_nonce,
                override_active=bool(override_active),
                prev_hash="GENESIS",
                commitment_hash=commitment,
            )

        bind = getattr(db, "bind", None)
        dialect_name = getattr(getattr(bind, "dialect", None), "name", "")

        if dialect_name == "postgresql":
            # V PostgreSQL 17 vypočítá prev_hash a commitment_hash trigger `zk_audit_ledger_before_insert`
            # pod `pg_advisory_xact_lock`, čímž zaručí lineární bezmezerový řetězec při souběhu.
            insert_sql = text(
                """
                INSERT INTO public.zk_audit_ledger
                    (entry_id, user_id, query_hash, r_systemic, tier, nonce, override_active, payload_json)
                VALUES
                    (CAST(:entry_id AS uuid), :user_id, :query_hash, :r_systemic, :tier, :nonce, :override_active, CAST(:payload_json AS jsonb))
                RETURNING seq_id, entry_id::text, prev_hash, commitment_hash, nonce;
                """
            )
            res = await db.execute(
                insert_sql,
                {
                    "entry_id": entry_uuid,
                    "user_id": user_id,
                    "query_hash": q_hash,
                    "r_systemic": r_sys,
                    "tier": clean_tier,
                    "nonce": clean_nonce,
                    "override_active": bool(override_active),
                    "payload_json": json.dumps(payload_dict),
                },
            )
            row = res.fetchone()
            await db.commit()
            return LedgerAppendRecord(
                seq_id=int(row[0]),
                entry_id=str(row[1]),
                user_id=user_id,
                query_hash=q_hash,
                r_systemic=r_sys,
                tier=clean_tier,
                nonce=str(row[4]),
                override_active=bool(override_active),
                prev_hash=str(row[2]),
                commitment_hash=str(row[3]),
            )

        # SQLite / obecný SQLAlchemy fallback
        await cls.ensure_sqlite_immutability_triggers(db)
        last_res = await db.execute(
            select(ZkAuditLedgerEntry.commitment_hash).order_by(ZkAuditLedgerEntry.seq_id.desc()).limit(1)
        )
        prev_hash = last_res.scalar_one_or_none() or "GENESIS"
        commitment = cls.compute_canonical_commitment(
            prev_hash=prev_hash,
            query_hash=q_hash,
            r_systemic=r_sys,
            tier=clean_tier,
            nonce=clean_nonce,
            override_active=bool(override_active),
        )
        entry = ZkAuditLedgerEntry(
            entry_id=entry_uuid,
            user_id=user_id,
            query_hash=q_hash,
            r_systemic=r_sys,
            tier=clean_tier,
            nonce=clean_nonce,
            override_active=bool(override_active),
            prev_hash=prev_hash,
            commitment_hash=commitment,
            payload_json=payload_dict,
        )
        db.add(entry)
        await db.commit()
        await db.refresh(entry)
        return LedgerAppendRecord(
            seq_id=int(entry.seq_id),
            entry_id=str(entry.entry_id),
            user_id=str(entry.user_id),
            query_hash=str(entry.query_hash),
            r_systemic=float(entry.r_systemic),
            tier=str(entry.tier),
            nonce=str(entry.nonce),
            override_active=bool(entry.override_active),
            prev_hash=str(entry.prev_hash),
            commitment_hash=str(entry.commitment_hash),
        )

    @classmethod
    def verify_chain_continuity(
        cls, entries: Sequence[LedgerAppendRecord | ZkAuditLedgerEntry | Dict[str, Any]]
    ) -> LedgerVerificationResult:
        """
        Deterministicky ověří kryptografickou kontinuitu SHA-256 řetězce od GENESIS po hlavu řetězce.
        """
        if not entries:
            return LedgerVerificationResult(
                valid=True,
                total_entries=0,
                broken_at_seq_id=None,
                reason=None,
                head_commitment_hash="GENESIS",
            )

        expected_prev = "GENESIS"
        head_hash = "GENESIS"

        for idx, item in enumerate(entries):
            if isinstance(item, dict):
                seq_id = item.get("seq_id", idx + 1)
                prev_hash = str(item["prev_hash"])
                query_hash = str(item["query_hash"])
                r_systemic = float(item["r_systemic"])
                tier = str(item["tier"])
                nonce = str(item["nonce"])
                override_active = bool(item.get("override_active", False))
                commitment_hash = str(item["commitment_hash"])
            else:
                seq_id = getattr(item, "seq_id", idx + 1)
                prev_hash = str(getattr(item, "prev_hash"))
                query_hash = str(getattr(item, "query_hash"))
                r_systemic = float(getattr(item, "r_systemic"))
                tier = str(getattr(item, "tier"))
                nonce = str(getattr(item, "nonce"))
                override_active = bool(getattr(item, "override_active", False))
                commitment_hash = str(getattr(item, "commitment_hash"))

            if prev_hash != expected_prev:
                return LedgerVerificationResult(
                    valid=False,
                    total_entries=len(entries),
                    broken_at_seq_id=int(seq_id) if seq_id is not None else idx + 1,
                    reason=f"Chain link mismatch at seq_id={seq_id}: expected prev_hash={expected_prev}, got={prev_hash}",
                    head_commitment_hash=head_hash,
                )

            recomputed = cls.compute_canonical_commitment(
                prev_hash=prev_hash,
                query_hash=query_hash,
                r_systemic=r_systemic,
                tier=tier,
                nonce=nonce,
                override_active=override_active,
            )
            if recomputed != commitment_hash:
                return LedgerVerificationResult(
                    valid=False,
                    total_entries=len(entries),
                    broken_at_seq_id=int(seq_id) if seq_id is not None else idx + 1,
                    reason=f"SHA-256 commitment integrity violation at seq_id={seq_id}",
                    head_commitment_hash=head_hash,
                )

            expected_prev = commitment_hash
            head_hash = commitment_hash

        return LedgerVerificationResult(
            valid=True,
            total_entries=len(entries),
            broken_at_seq_id=None,
            reason=None,
            head_commitment_hash=head_hash,
        )

    @classmethod
    async def verify_database_chain(cls, db: AsyncSession) -> LedgerVerificationResult:
        """Načte záznamy `zk_audit_ledger` seřazené podle `seq_id ASC` a ověří integritu řetězce."""
        if db is None or getattr(db, "bind", None) is None:
            return cls.verify_chain_continuity([])
        res = await db.execute(
            select(ZkAuditLedgerEntry).order_by(ZkAuditLedgerEntry.seq_id.asc())
        )
        rows = res.scalars().all()
        return cls.verify_chain_continuity(rows)
