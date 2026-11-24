"""
O.M.N.I.S. Inference Router (backend/routers/inference.py)
Poskytuje zabezpečený endpoint POST /api/v1/inference/analyze s Q_omnis PII/injection sanitizací,
AKGE-8D Matrix8DState, CSR grafem, 6-bránovým Refusal Ladderem a HMAC-SHA256 hash řetězením.
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.config import get_settings
from backend.database import get_audit_db
from backend.graph.csr_engine import (
    CsrKnowledgeGraph,
    DOMAINS_8D,
    _sanitize_unit_float,
    compute_advanced_metrics,
    compute_q_omnis,
    detect_corrupt_tensor_inputs,
    get_age_graph_repository,
)
from backend.schemas.akge import (
    CryptographicAuditCommitment,
    GateEvaluationResult,
    Matrix8DState,
    ProofOfNecessityLog,
    evaluate_output_gates,
)
from backend.gateway import GatekeeperChainException, GatekeeperG1G6
from backend.security import AuthenticatedCaller, sanitize_prompt, verify_firebase_and_appcheck
from backend.security.zk_ledger_service import ZkAuditLedgerService

inference_router = APIRouter(prefix="/api/v1/inference", tags=["AKGE-8D Inference"])


class InferenceAnalyzeRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=16000)
    domain_scores: Dict[str, float] = Field(default_factory=dict)
    priority_profile: str = Field(default="Balanced Core")
    prev_commitment_hash: str = Field(default="GENESIS")
    latency_ms: int = Field(default=16, ge=0)
    nonce: Optional[str] = None
    query_embedding: Optional[List[float]] = None
    candidate_output: Optional[str] = None


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


def project_inference_for_role(
    payload: Dict[str, Any],
    role: str,
) -> Dict[str, Any]:
    """Izoluje citlivou interní forenzní telemetrii pro STANDARD_USER oproti ADMIN_OPERATOR."""
    if str(role).upper() == "ADMIN_OPERATOR":
        return payload

    projected = dict(payload)
    projected["topological_invariants"] = {
        "shannon_entropy": payload.get("topological_invariants", {}).get("shannon_entropy", 0.0),
        "access_tier": "STANDARD_USER",
    }
    projected["advanced_metrics"] = {
        "selfHealingConfidence": payload.get("advanced_metrics", {}).get("selfHealingConfidence", 0.0),
        "access_tier": "STANDARD_USER",
    }
    pon_raw = dict(payload.get("proof_of_necessity", {}))
    pon_raw["trigger_vector"] = "REDACTED_STANDARD_USER"
    pon_raw["exclusion_proof"] = "REDACTED_STANDARD_USER"
    projected["proof_of_necessity"] = pon_raw
    return projected


@inference_router.post("/analyze", response_model=InferenceAnalyzeResponse)
async def analyze_inference(
    req: InferenceAnalyzeRequest,
    caller: AuthenticatedCaller = Depends(verify_firebase_and_appcheck),
    audit_db: AsyncSession = Depends(get_audit_db),
) -> InferenceAnalyzeResponse:
    corrupt_check = detect_corrupt_tensor_inputs(
        domain_scores=req.domain_scores,
        query_embedding=req.query_embedding,
    )
    sanitized = sanitize_prompt(req.query)
    gatekeeper_trail: List[Dict[str, Any]] = []
    gatekeeper_failed_gate: Optional[str] = None
    gatekeeper_failure_reason: Optional[str] = None
    try:
        gk_payload = GatekeeperG1G6.validate_and_sanitize(
            query=sanitized.sanitized_text,
            domain_scores=req.domain_scores or None,
            embedding_vector=req.query_embedding,
        )
        gatekeeper_trail = [
            {"gate_id": r.gate_id, "passed": r.passed, "audit_hash": r.audit_hash, "reason": r.reason}
            for r in gk_payload.audit_trail
        ]
    except GatekeeperChainException as gk_exc:
        gatekeeper_failed_gate = gk_exc.gate_id
        gatekeeper_failure_reason = gk_exc.reason
        gatekeeper_trail = [
            {"gate_id": r.gate_id, "passed": r.passed, "audit_hash": r.audit_hash, "reason": r.reason}
            for r in gk_exc.audit_trail
        ]
    scores = {
        d: _sanitize_unit_float(
            req.domain_scores.get(d, 0.86 if d in ("SYS", "SEC", "LAW") else 0.78),
            default=0.78,
        )
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

    age_repo = get_age_graph_repository()
    graph = CsrKnowledgeGraph.build_8d_tensor_graph(scores)
    topo = graph.calculate_topological_invariants(scores)
    percolation_critical, percolation_msg = graph.evaluate_percolation_bypass(scores)
    do_sys_law = age_repo.evaluate_g3_do_calculus("SYS", "LAW", intervention_value=0.95, current_values=scores)
    adv_metrics = compute_advanced_metrics(scores, topo["shannon_entropy"], req.latency_ms)
    q_omnis = compute_q_omnis(scores, topo["shannon_entropy"], topo["betti_1"])

    clean_q = sanitized.sanitized_text
    g1_ok = len(clean_q) >= 3
    g2_ok = len(clean_q.split()) >= 2
    g3_ok = bool(do_sys_law.get("g3_passed")) and bool(do_sys_law.get("shortest_causal_path"))
    g4_ok = not any(
        bad in clean_q.lower()
        for bad in ("ransomware", "malware", "bypass security", "social scoring")
    )
    g5_ok = sanitized.is_safe
    g6_ok = topo["shannon_entropy"] < 2.08 and not corrupt_check["has_corrupt_input"]

    if req.candidate_output:
        output_gates = evaluate_output_gates(
            output_text=req.candidate_output,
            shannon_entropy=topo["shannon_entropy"],
            domain_scores=req.domain_scores,
            output_embedding=req.query_embedding,
            g3_causal_ok=g3_ok,
        )
        g1_ok = g1_ok and output_gates.g1_entry_point
        g2_ok = g2_ok and output_gates.g2_concept_coupling
        g3_ok = g3_ok and output_gates.g3_causal_path
        g4_ok = g4_ok and output_gates.g4_quotable_evidence
        g5_ok = g5_ok and output_gates.g5_retraction_integrity
        g6_ok = g6_ok and output_gates.g6_entailment_entropy

    gates = GateEvaluationResult(
        g1_entry_point=g1_ok and gatekeeper_failed_gate != "G1",
        g2_concept_coupling=g2_ok and gatekeeper_failed_gate != "G2",
        g3_causal_path=g3_ok and gatekeeper_failed_gate != "G3",
        g4_quotable_evidence=g4_ok and gatekeeper_failed_gate != "G4",
        g5_retraction_integrity=g5_ok and gatekeeper_failed_gate != "G5",
        g6_entailment_entropy=g6_ok and gatekeeper_failed_gate != "G6",
        shannon_entropy=topo["shannon_entropy"],
        refusal_reason=corrupt_check["reason"] or gatekeeper_failure_reason,
    )

    sigma = round(sum(max(0.0, (0.5 - v) * 2.0) for v in scores.values()) / 8.0, 4)
    pon = ProofOfNecessityLog(
        trigger_vector=f"Caller({caller.uid}) -> InferenceAnalyze(profile={matrix.priority_profile})",
        exclusion_proof="Ungrounded single-pass generation rejected; Apache AGE CSR 8D + 6-Gate ladder enforced.",
        minimization_check=f"Sanitized PII={len(sanitized.pii_redacted_types)}; Bounded to 8 invariant domains.",
        system_tension_sigma=sigma,
    )

    tier = (
        f"REFUSED_{gatekeeper_failed_gate}_BOUNDARY"
        if (corrupt_check["has_corrupt_input"] or gatekeeper_failed_gate is not None)
        else ("APPROVED" if gates.all_passed else "BLOCKED")
    )
    audit_r_systemic = (
        0.0
        if (corrupt_check["has_corrupt_input"] or gatekeeper_failed_gate is not None)
        else matrix.harmonic_mean_hw
    )
    commitment = CryptographicAuditCommitment.generate(
        query=clean_q,
        r_systemic=audit_r_systemic,
        tier=tier,
        nonce=req.nonce,
        prev_commitment_hash=req.prev_commitment_hash,
    )

    try:
        await ZkAuditLedgerService.append_entry(
            audit_db,
            user_id=caller.uid,
            query=clean_q,
            r_systemic=audit_r_systemic,
            tier=tier,
            nonce=commitment.nonce,
            override_active=False,
            payload={
                "q_omnis": q_omnis,
                "g3_snapshot_version": do_sys_law.get("snapshot_version"),
                "corrupt_input_check": corrupt_check,
                "gatekeeper_audit_trail": gatekeeper_trail,
            },
        )
    except Exception:
        try:
            await audit_db.rollback()
        except Exception:
            pass

    if not get_settings().USE_OSS_POSTGRES_SYNC:
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

    response_dict = {
        "status": "APPROVED" if gates.all_passed else "REFUSED",
        "sanitized_query": clean_q,
        "pii_redacted_types": sanitized.pii_redacted_types,
        "detected_injections": sanitized.detected_injections,
        "matrix_8d": matrix.model_dump(),
        "topological_invariants": topo,
        "percolation": {
            "critical_threshold": 0.38,
            "bypass_active": percolation_critical,
            "diagnostic": percolation_msg if caller.is_admin else ("ISOLATED" if percolation_critical else "STABLE"),
        },
        "advanced_metrics": adv_metrics,
        "q_omnis": q_omnis,
        "gate_evaluation": gates.model_dump(),
        "proof_of_necessity": pon.model_dump(),
        "audit_commitment": commitment.model_dump(),
    }
    return InferenceAnalyzeResponse(**project_inference_for_role(response_dict, caller.role))
