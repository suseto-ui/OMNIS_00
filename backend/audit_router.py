import logging
import os
import time
from typing import Any, Dict, List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

try:
    from google.cloud import firestore
except ImportError:
    firestore = None

from backend.auth_router import get_current_user
from backend.config import get_settings
from backend.database import get_audit_db, get_db, hybrid_search_knowledge
from backend.graph.csr_engine import (
    CsrKnowledgeGraph,
    DOMAINS_8D,
    _sanitize_unit_float,
    compute_advanced_metrics,
    compute_q_omnis,
    get_age_graph_repository,
)
from backend.models import User, ZkAuditLedgerEntry
from backend.schemas.akge import (
    CryptographicAuditCommitment,
    GateEvaluationResult,
    Matrix8DState,
    ProofOfNecessityLog,
)
from backend.security.zk_ledger_service import ZkAuditLedgerService

logger = logging.getLogger("omnis.audit")

router = APIRouter(prefix="/api/audit", tags=["Audit & Integrity"])
akge_router = APIRouter(prefix="/api/akge", tags=["AKGE-8D Orchestrator"])

FIRESTORE_DATABASE_ID = os.getenv(
    "FIRESTORE_DATABASE_ID",
    "ai-studio-android-omnis-fba4ba28-3ec2-4eaf-a1a7-338554af8dfe",
)


class PoNLogRequest(BaseModel):
    actionType: str
    details: Dict[str, Any] = Field(default_factory=dict)
    triggerVector: str
    exclusionProof: str
    minimizationCheck: str
    query: Optional[str] = None
    rSystemic: Optional[float] = None
    tier: Optional[str] = None
    nonce: Optional[str] = None
    prevCommitmentHash: str = "GENESIS"
    overrideActive: bool = False


class AkgeEvaluateRequest(BaseModel):
    query: str = Field(..., min_length=1)
    domain_scores: Dict[str, float] = Field(default_factory=dict)
    priority_profile: str = Field(default="Balanced Core")
    latency_ms: int = Field(default=18, ge=0)
    modify_system_state: bool = Field(default=False)
    nonce: Optional[str] = None
    query_embedding: Optional[List[float]] = None


@router.post("/pon")
async def log_proof_of_necessity(
    req: PoNLogRequest,
    current_user: User = Depends(get_current_user),
    audit_db: AsyncSession = Depends(get_audit_db),
):
    timestamp = int(time.time())
    is_admin = getattr(current_user, "role", "STANDARD_USER") == "ADMIN_OPERATOR"
    effective_override = bool(req.overrideActive and is_admin)

    pon_schema = ProofOfNecessityLog(
        trigger_vector=req.triggerVector,
        exclusion_proof=req.exclusionProof,
        minimization_check=req.minimizationCheck,
        system_tension_sigma=float(req.details.get("sigma", 0.18)) if isinstance(req.details, dict) else 0.18,
    )

    canonical_query = req.query or f"{req.actionType}|{req.triggerVector}|{req.exclusionProof}|{req.minimizationCheck}"
    r_sys = float(req.rSystemic if req.rSystemic is not None else 0.8800)
    tier = (req.tier or ("OVERRIDE" if effective_override else "APPROVED")).upper()
    zk_commitment = CryptographicAuditCommitment.generate(
        query=canonical_query,
        r_systemic=r_sys,
        tier=tier,
        nonce=req.nonce,
        override_active=effective_override,
        prev_commitment_hash=req.prevCommitmentHash,
    )

    ledger_record = None
    try:
        ledger_record = await ZkAuditLedgerService.append_entry(
            audit_db,
            user_id=str(current_user.id),
            query=canonical_query,
            r_systemic=r_sys,
            tier=tier,
            nonce=zk_commitment.nonce,
            override_active=effective_override,
            payload={"actionType": req.actionType, "details": req.details},
        )
    except Exception as ledger_exc:
        try:
            await audit_db.rollback()
        except Exception:
            pass
        logger.warning("zk_audit_ledger append skipped/fallback: %s", ledger_exc)

    doc_data = req.model_dump()
    doc_data["userId"] = str(current_user.id)
    doc_data["timestamp"] = timestamp
    doc_data["commitmentHash"] = ledger_record.commitment_hash if ledger_record else zk_commitment.commitment_hash
    doc_data["prevCommitmentHash"] = ledger_record.prev_hash if ledger_record else zk_commitment.prev_commitment_hash
    doc_data["commitmentAlgorithm"] = zk_commitment.algorithm
    doc_data["overrideActive"] = effective_override
    doc_data["proofOfNecessity"] = pon_schema.model_dump()

    if not get_settings().USE_OSS_POSTGRES_SYNC and firestore is not None:
        try:
            db_fs = firestore.Client(database=FIRESTORE_DATABASE_ID)
            doc_ref = db_fs.collection("audit_logs").document()
            doc_ref.set(doc_data)
        except Exception as exc:
            logger.warning("Firestore audit_logs write skipped: %s", exc)

    return {
        "status": "success",
        "commitmentHash": ledger_record.commitment_hash if ledger_record else zk_commitment.commitment_hash,
        "prevCommitmentHash": ledger_record.prev_hash if ledger_record else zk_commitment.prev_commitment_hash,
        "commitmentAlgorithm": zk_commitment.algorithm,
        "overrideActive": effective_override,
        "ledgerSeqId": ledger_record.seq_id if ledger_record else None,
        "proofOfNecessity": pon_schema.model_dump(),
        "auditCommitment": zk_commitment.model_dump(),
        "zkCommitment": zk_commitment.model_dump(),
    }


@router.get("/ledger/verify")
async def verify_zk_audit_ledger(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Ověří kryptografickou kontinuitu SHA-256 řetězce v `zk_audit_ledger` (pouze ADMIN_OPERATOR)."""
    if getattr(current_user, "role", "STANDARD_USER") != "ADMIN_OPERATOR":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Nedostatečná oprávnění: verifikace zk_audit_ledger vyžaduje roli ADMIN_OPERATOR.",
        )
    verification = await ZkAuditLedgerService.verify_database_chain(db)
    return {
        "status": "VALID" if verification.valid else "COMPROMISED",
        "valid": verification.valid,
        "total_entries": verification.total_entries,
        "broken_at_seq_id": verification.broken_at_seq_id,
        "reason": verification.reason,
        "head_commitment_hash": verification.head_commitment_hash,
    }


@router.get("/ledger/entries")
async def list_zk_audit_ledger_entries(
    limit: int = Query(50, ge=1, le=500),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Vrátí posledních N záznamů append-only řetězce `zk_audit_ledger` (pouze ADMIN_OPERATOR)."""
    if getattr(current_user, "role", "STANDARD_USER") != "ADMIN_OPERATOR":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Nedostatečná oprávnění: čtení zk_audit_ledger vyžaduje roli ADMIN_OPERATOR.",
        )
    if getattr(db, "bind", None) is None:
        return {"entries": [], "count": 0}
    res = await db.execute(
        select(ZkAuditLedgerEntry).order_by(ZkAuditLedgerEntry.seq_id.desc()).limit(limit)
    )
    rows = res.scalars().all()
    return {
        "count": len(rows),
        "entries": [
            {
                "seq_id": int(r.seq_id),
                "entry_id": str(r.entry_id),
                "user_id": str(r.user_id),
                "query_hash": str(r.query_hash),
                "r_systemic": float(r.r_systemic),
                "tier": str(r.tier),
                "nonce": str(r.nonce),
                "override_active": bool(r.override_active),
                "prev_hash": str(r.prev_hash),
                "commitment_hash": str(r.commitment_hash),
            }
            for r in rows
        ],
    }


@akge_router.post("/evaluate")
async def evaluate_akge_8d(
    req: AkgeEvaluateRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Kompletní AKGE-8D orchestrace napříč Pydantic v2, CsrKnowledgeGraph,
    6-úrovňovým Refusal Ladderem (EU AI Act / NIS2 Passport), hybridním RRF vyhledáváním
    a deterministickým Cryptographic Audit Commitment řetězcem (včetně Executive Override).
    """
    scores = {
        d: _sanitize_unit_float(
            req.domain_scores.get(d, 0.85 if d in ("SYS", "SEC", "LAW") else 0.78),
            default=0.78,
        )
        for d in DOMAINS_8D
    }
    matrix_state = Matrix8DState(
        sys=scores["SYS"],
        econ=scores["ECON"],
        psych=scores["PSYCH"],
        eco=scores["ECO"],
        law=scores["LAW"],
        sec=scores["SEC"],
        phys=scores["PHYS"],
        soc=scores["SOC"],
        priority_profile=req.priority_profile or "Balanced Core",
    )

    age_repo = get_age_graph_repository()
    graph = CsrKnowledgeGraph.build_8d_tensor_graph(scores)
    topo = graph.calculate_topological_invariants(scores)
    percolation_critical, percolation_msg = graph.evaluate_percolation_bypass(scores)
    do_sys_law = age_repo.evaluate_g3_do_calculus("SYS", "LAW", intervention_value=0.95, current_values=scores)
    adv_metrics = compute_advanced_metrics(scores, topo["shannon_entropy"], req.latency_ms)
    q_omnis = compute_q_omnis(scores, topo["shannon_entropy"], topo["betti_1"])

    q_lower = req.query.lower()
    is_admin = getattr(current_user, "role", "STANDARD_USER") == "ADMIN_OPERATOR"
    override_active = bool(req.modify_system_state and is_admin)

    g1_ok = len(req.query.strip()) >= 3
    g2_ok = len(req.query.strip().split()) >= 2
    g3_ok = bool(do_sys_law.get("g3_passed")) and bool(do_sys_law.get("shortest_causal_path"))
    g4_ok = not any(bad in q_lower for bad in ("ransomware", "malware", "bypass security", "social scoring"))
    g5_ok = "ignore previous" not in q_lower and "system override" not in q_lower
    g6_ok = topo["shannon_entropy"] < 2.08

    if override_active and g4_ok is False:
        # Executive Override převádí regulatorní veto na varování a zapisuje OVERRIDE_ACTIVE
        g4_ok = True

    gates = GateEvaluationResult(
        g1_entry_point=g1_ok,
        g2_concept_coupling=g2_ok,
        g3_causal_path=g3_ok,
        g4_quotable_evidence=g4_ok,
        g5_retraction_integrity=g5_ok,
        g6_entailment_entropy=g6_ok,
        shannon_entropy=topo["shannon_entropy"],
    )

    sigma = round(sum(max(0.0, (0.5 - v) * 2.0) for v in scores.values()) / 8.0, 4)
    compression_rate = 0.70 if sigma < 0.20 else (0.30 if sigma < 0.60 else 0.0)
    pon = ProofOfNecessityLog(
        trigger_vector=f"Query('{req.query[:48]}') -> AKGE-8D[profile={matrix_state.priority_profile}]",
        exclusion_proof="Dense tensor O(V^2) and ungrounded LLM pass-through rejected; Apache AGE CSR + 6-Gate Ladder enforced.",
        minimization_check=f"Bounded to 8 invariant domains; compression={int(compression_rate * 100)}%, sigma={sigma:.2f}.",
        system_tension_sigma=sigma,
    )

    tier = "OVERRIDE" if override_active else ("APPROVED" if gates.all_passed else "BLOCKED")
    zk = CryptographicAuditCommitment.generate(
        query=req.query,
        r_systemic=matrix_state.harmonic_mean_hw,
        tier=tier,
        nonce=req.nonce,
        override_active=override_active,
    )

    try:
        await ZkAuditLedgerService.append_entry(
            db,
            user_id=str(current_user.id),
            query=req.query,
            r_systemic=matrix_state.harmonic_mean_hw,
            tier=tier,
            nonce=zk.nonce,
            override_active=override_active,
            payload={"q_omnis": q_omnis, "g3_snapshot_version": do_sys_law.get("snapshot_version")},
        )
    except Exception as ledger_exc:
        try:
            await db.rollback()
        except Exception:
            pass
        logger.warning("AKGE ledger append skipped: %s", ledger_exc)

    rag_hits = await hybrid_search_knowledge(
        query_text=req.query,
        query_embedding=req.query_embedding,
        limit=3,
        rrf_k=60,
        db=db,
        user_id=str(current_user.id),
    )

    if not is_admin:
        return {
            "status": "APPROVED" if gates.all_passed else "REFUSED",
            "access_tier": "STANDARD_USER",
            "q_omnis": q_omnis,
            "gate_evaluation": {
                "all_passed": gates.all_passed,
                "failed_gate": gates.failed_gate,
                "refusal_reason": gates.refusal_reason,
                "shannon_entropy": gates.shannon_entropy,
            },
            "audit_commitment": {
                "commitment_hash": zk.commitment_hash,
                "tier": zk.tier,
                "algorithm": zk.algorithm,
            },
            "hybrid_rag_results": [
                {"id": h.get("id"), "title": h.get("title"), "rrf_score": h.get("rrf_score")}
                for h in rag_hits
            ],
        }

    return {
        "status": "OVERRIDE_ACTIVE" if override_active else ("APPROVED" if gates.all_passed else "REFUSED"),
        "access_tier": "ADMIN_OPERATOR",
        "matrix_8d": matrix_state.model_dump(),
        "topological_invariants": topo,
        "percolation": {
            "critical_threshold": 0.38,
            "bypass_active": percolation_critical,
            "diagnostic": percolation_msg,
        },
        "do_calculus": do_sys_law,
        "advanced_metrics": adv_metrics,
        "q_omnis": q_omnis,
        "token_compression_rate": compression_rate,
        "gate_evaluation": gates.model_dump(),
        "proof_of_necessity": pon.model_dump(),
        "audit_commitment": zk.model_dump(),
        "zk_commitment": zk.model_dump(),
        "hybrid_rag_results": rag_hits,
    }
