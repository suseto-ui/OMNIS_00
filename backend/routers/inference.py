"""
O.M.N.I.S. Inference Router (backend/routers/inference.py)
Poskytuje zabezpečený endpoint POST /api/v1/inference/analyze s Q_omnis PII/injection sanitizací,
AKGE-8D Matrix8DState, CSR grafem, 6-bránovým Refusal Ladderem a HMAC-SHA256 hash řetězením.
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from backend.graph.csr_engine import CsrKnowledgeGraph, DOMAINS_8D, compute_advanced_metrics, compute_q_omnis
from backend.schemas.akge import (
    CryptographicAuditCommitment,
    GateEvaluationResult,
    Matrix8DState,
    ProofOfNecessityLog,
)
from backend.security import AuthenticatedCaller, sanitize_prompt, verify_firebase_and_appcheck

inference_router = APIRouter(prefix="/api/v1/inference", tags=["AKGE-8D Inference"])


class InferenceAnalyzeRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=16000)
    domain_scores: Dict[str, float] = Field(default_factory=dict)
    priority_profile: str = Field(default="Balanced Core")
    prev_commitment_hash: str = Field(default="GENESIS")
    latency_ms: int = Field(default=16, ge=0)
    nonce: Optional[str] = None


class InferenceAnalyzeResponse(BaseModel):
    status: str
    sanitized_query: str
    pii_redacted_types: List[str]
    detected_injections: List[str]
    matrix_8d: Dict[str, Any]
    topological_invariants: Dict[str, Any]
    percolation: Dict[str, Any]
    advanced_metrics: Dict[str, Any]
    q_omnis: float
    gate_evaluation: Dict[str, Any]
    proof_of_necessity: Dict[str, Any]
    audit_commitment: Dict[str, Any]


@inference_router.post("/analyze", response_model=InferenceAnalyzeResponse)
async def analyze_inference(
    req: InferenceAnalyzeRequest,
    caller: AuthenticatedCaller = Depends(verify_firebase_and_appcheck),
) -> InferenceAnalyzeResponse:
    sanitized = sanitize_prompt(req.query)
    scores = {
        d: float(req.domain_scores.get(d, 0.86 if d in ("SYS", "SEC", "LAW") else 0.78))
        for d in DOMAINS_8D
    }
    matrix = Matrix8DState(
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

    clean_q = sanitized.sanitized_text
    g1_ok = len(clean_q) >= 3
    g2_ok = len(clean_q.split()) >= 2
    g3_ok = bool(do_sys_law.get("shortest_causal_path"))
    g4_ok = not any(
        bad in clean_q.lower()
        for bad in ("ransomware", "malware", "bypass security", "social scoring")
    )
    g5_ok = sanitized.is_safe
    g6_ok = topo["shannon_entropy"] < 2.08

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
    pon = ProofOfNecessityLog(
        trigger_vector=f"Caller({caller.uid}) -> InferenceAnalyze(profile={matrix.priority_profile})",
        exclusion_proof="Ungrounded single-pass generation rejected; CSR 8D + 6-Gate ladder enforced.",
        minimization_check=f"Sanitized PII={len(sanitized.pii_redacted_types)}; Bounded to 8 invariant domains.",
        system_tension_sigma=sigma,
    )

    tier = "APPROVED" if gates.all_passed else "BLOCKED"
    commitment = CryptographicAuditCommitment.generate(
        query=clean_q,
        r_systemic=matrix.harmonic_mean_hw,
        tier=tier,
        nonce=req.nonce,
        prev_commitment_hash=req.prev_commitment_hash,
    )

    try:
        from backend.security.firestore_store import fs_set_doc

        fs_set_doc(
            "proof_of_necessity",
            commitment.commitment_hash,
            {
                "callerUid": caller.uid,
                "status": "APPROVED" if gates.all_passed else "REFUSED",
                "qOmnis": q_omnis,
                "sigma": sigma,
                "proofOfNecessity": pon.model_dump(),
                "gateEvaluation": gates.model_dump(),
                "auditCommitment": commitment.model_dump(),
            },
            merge=True,
        )
    except Exception:
        pass

    return InferenceAnalyzeResponse(
        status="APPROVED" if gates.all_passed else "REFUSED",
        sanitized_query=clean_q,
        pii_redacted_types=sanitized.pii_redacted_types,
        detected_injections=sanitized.detected_injections,
        matrix_8d=matrix.model_dump(),
        topological_invariants=topo,
        percolation={
            "critical_threshold": 0.38,
            "bypass_active": percolation_critical,
            "diagnostic": percolation_msg,
        },
        advanced_metrics=adv_metrics,
        q_omnis=q_omnis,
        gate_evaluation=gates.model_dump(),
        proof_of_necessity=pon.model_dump(),
        audit_commitment=commitment.model_dump(),
    )
