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
    compute_advanced_metrics,
    generate_proof_of_necessity,
)


def compute_q_omnis(domain_scores: Dict[str, float], entropy: float, beta_1: int = 2) -> float:
    """
    Výpočet celkové systémové kvality inference Q_omnis v intervalu [0.0, 1.0]
    kombinující harmonickou odolnost, nízkou Shannonovu entropii a topologickou stabilitu.
    """
    vals = [max(0.01, min(1.0, float(domain_scores.get(d, 0.5)))) for d in DOMAINS_8D]
    harmonic = len(vals) / sum(1.0 / v for v in vals)
    entropy_penalty = min(0.45, max(0.0, float(entropy) / 5.0))
    loop_bonus = min(0.10, max(0.0, beta_1 * 0.02))
    return round(min(1.0, max(0.0, harmonic * (1.0 - entropy_penalty) + loop_bonus)), 4)


__all__ = [
    "CsrKnowledgeGraph",
    "CsrKnowledgeGraphSchema",
    "DOMAINS_8D",
    "ProofOfNecessityLog",
    "compute_advanced_metrics",
    "compute_q_omnis",
    "generate_proof_of_necessity",
]
