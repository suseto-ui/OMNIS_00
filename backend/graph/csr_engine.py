"""
O.M.N.I.S. CSR Knowledge Graph Engine (backend/graph/csr_engine.py)
Kanonická vrstva [BE-02] poskytující Compressed Sparse Row graf, Do-Calculus intervence
P(Y | do(X=x)), perkolační bypass (p_c = 0.38), metriku Q_omnis a AdvancedMetrics.
"""

from __future__ import annotations
from typing import Dict

from backend.csr_graph import (
    CsrKnowledgeGraph,
    CsrKnowledgeGraphSchema,
    DOMAINS_8D,
    ProofOfNecessityLog,
    _sanitize_unit_float,
    compute_advanced_metrics,
    generate_proof_of_necessity,
    normalize_l2_64d,
)


from backend.graph.age_repository import (
    AgeCausalGraphRepository,
    CsrGraphSnapshot,
    get_age_graph_repository,
)


def compute_q_omnis(domain_scores: Dict[str, float], entropy: float, beta_1: int = 2) -> float:
    """
    Výpočet celkové systémové kvality inference Q_omnis v intervalu [0.0, 1.0]
    kombinující harmonickou odolnost, nízkou Shannonovu entropii a topologickou stabilitu.
    """
    vals = [_sanitize_unit_float(domain_scores.get(d, 0.5), default=0.5, low=0.01, high=1.0) for d in DOMAINS_8D]
    harmonic = len(vals) / sum(1.0 / v for v in vals)
    safe_entropy = _sanitize_unit_float(entropy, default=0.5, low=0.0, high=5.0)
    entropy_penalty = min(0.45, max(0.0, safe_entropy / 5.0))
    loop_bonus = min(0.10, max(0.0, max(0, int(beta_1)) * 0.02))
    return round(_sanitize_unit_float(harmonic * (1.0 - entropy_penalty) + loop_bonus, default=0.5), 4)


__all__ = [
    "AgeCausalGraphRepository",
    "CsrGraphSnapshot",
    "CsrKnowledgeGraph",
    "CsrKnowledgeGraphSchema",
    "DOMAINS_8D",
    "ProofOfNecessityLog",
    "_sanitize_unit_float",
    "compute_advanced_metrics",
    "compute_q_omnis",
    "generate_proof_of_necessity",
    "get_age_graph_repository",
    "normalize_l2_64d",
]
