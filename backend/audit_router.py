import logging
import os
import time
from typing import Any, Dict, List, Optional
from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

try:
    from google.cloud import firestore
except ImportError:
    firestore = None

from backend.auth_router import get_current_user
from backend.database import get_db, hybrid_search_knowledge
from backend.graph.csr_engine import CsrKnowledgeGraph, DOMAINS_8D, compute_advanced_metrics, compute_q_omnis
from backend.models import User
from backend.schemas.akge import (
    CryptographicAuditCommitment,
    GateEvaluationResult,
    Matrix8DState,
    ProofOfNecessityLog,
)

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

    doc_data = req.model_dump()
    doc_data["userId"] = str(current_user.id)
    doc_data["timestamp"] = timestamp
    doc_data["commitmentHash"] = zk_commitment.commitment_hash
    doc_data["prevCommitmentHash"] = zk_commitment.prev_commitment_hash
    doc_data["commitmentAlgorithm"] = zk_commitment.algorithm
    doc_data["overrideActive"] = effective_override
    doc_data["proofOfNecessity"] = pon_schema.model_dump()

    if firestore is not None:
        try:
            db_fs = firestore.Client(database=FIRESTORE_DATABASE_ID)
            doc_ref = db_fs.collection("audit_logs").document()
            doc_ref.set(doc_data)
        except Exception as exc:
            logger.warning("Firestore audit_logs write skipped: %s", exc)

    return {
        "status": "success",
        "commitmentHash": zk_commitment.commitment_hash,
        "prevCommitmentHash": zk_commitment.prev_commitment_hash,
        "commitmentAlgorithm": zk_commitment.algorithm,
        "overrideActive": effective_override,
        "proofOfNecessity": pon_schema.model_dump(),
        "auditCommitment": zk_commitment.model_dump(),
        "zkCommitment": zk_commitment.model_dump(),
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
        d: float(req.domain_scores.get(d, 0.85 if d in ("SYS", "SEC", "LAW") else 0.78))
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

    graph = CsrKnowledgeGraph.build_8d_tensor_graph(scores)
    topo = graph.calculate_topological_invariants(scores)
    percolation_critical, percolation_msg = graph.evaluate_percolation_bypass(scores)
    do_sys_law = graph.evaluate_do_calculus("SYS", "LAW")
    adv_metrics = compute_advanced_metrics(scores, topo["shannon_entropy"], req.latency_ms)
    q_omnis = compute_q_omnis(scores, topo["shannon_entropy"], topo["betti_1"])

    q_lower = req.query.lower()
    is_admin = getattr(current_user, "role", "STANDARD_USER") == "ADMIN_OPERATOR"
    override_active = bool(req.modify_system_state and is_admin)

    g1_ok = len(req.query.strip()) >= 3
    g2_ok = len(req.query.strip().split()) >= 2
    g3_ok = bool(do_sys_law.get("shortest_causal_path"))
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
        exclusion_proof="Dense tensor O(V^2) and ungrounded LLM pass-through rejected; CSR + 6-Gate Ladder enforced.",
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

    rag_hits = await hybrid_search_knowledge(
        query_text=req.query,
        query_embedding=req.query_embedding,
        limit=3,
        rrf_k=60,
        db=db,
    )

    return {
        "status": "OVERRIDE_ACTIVE" if override_active else ("APPROVED" if gates.all_passed else "REFUSED"),
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
