"""
O.M.N.I.S. Gatekeeper G1-G6 Logical Layer (backend/gateway/gatekeeper.py)

Sekvenčně validuje a sanitizuje textová i tenzorová data před jejich vstupem do Matice dopadů.
Každá brána (G1 až G6) loguje svou operaci do auditního systému a v případě selhání
(např. NaN, Inf, nulové vektory, injekce, regulatorní porušení) okamžitě přeruší řetězec
vyhozením definované výjimky GatekeeperChainException.
"""

from __future__ import annotations

import hashlib
import logging
import math
import re
import time
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, List, Optional, Tuple

from backend.csr_graph import (
    DOMAINS_8D,
    CsrKnowledgeGraph,
    detect_corrupt_tensor_inputs,
    normalize_l2_64d,
)

audit_logger = logging.getLogger("omnis.gatekeeper.audit")

DEFAULT_MATRIX_WEIGHTS: Dict[str, float] = {
    "SYS": 0.88,
    "ECON": 0.82,
    "PSYCH": 0.80,
    "ECO": 0.85,
    "LAW": 0.86,
    "SEC": 0.90,
    "PHYS": 0.80,
    "SOC": 0.78,
}

_G1_INJECTION_PATTERNS = (
    "ignore previous instructions",
    "ignore all previous",
    "bypass security",
    "disregard system",
    "system override",
    "jailbreak",
    "dan mode",
    "sudo mode",
    "zapomeň na předchozí instrukce",
    "ignoruj pravidla",
    "obejdi bezpečnost",
    "vypni filtry",
    "<scratchpad>",
)

_G2_PROHIBITED_PATTERNS = (
    "subliminal manipulation",
    "podprahová manipulace",
    "social scoring",
    "sociální kredit",
    "sociální bodování občanů",
    "exploit mental vulnerability",
    "zneužití zranitelnosti dětí",
)

_G3_NIS2_PATTERNS = (
    "generate ransomware",
    "vytvoř ransomware",
    "vygeneruj malware",
    "exploit zero-day",
    "ddos attack tool",
    "napadni kritickou infrastrukturu",
    "sabotuj rozvodnou síť",
    "bypass authentication token",
)

_G4_ZERO_TRUST_PATTERNS = (
    "disable zero trust",
    "bypass rbac audit",
    "vygeneruj fiktivní soudní rozhodnutí",
    "vyrob falešný důkaz",
)

_EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
_API_KEY_RE = re.compile(r"AIza[0-9A-Za-z\-_]{20,}")
_CTRL_RE = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")
_SPACE_RE = re.compile(r"\s+")


@dataclass(frozen=True)
class GateAuditRecord:
    gate_id: str
    passed: bool
    regulatory_norm: str
    sanitized_summary: str
    reason: str
    audit_hash: str
    timestamp_ms: int = field(default_factory=lambda: int(time.time() * 1000))


@dataclass(frozen=True)
class SanitizedImpactPayload:
    sanitized_query: str
    sanitized_domain_scores: Dict[str, float]
    normalized_embedding: Optional[List[float]]
    system_stress_sigma: float
    shannon_entropy: float
    audit_trail: List[GateAuditRecord]
    chain_commitment_hash: str


class GatekeeperChainException(Exception):
    """Definovaná výjimka pro přerušení validačního řetězce Gatekeeper G1-G6."""

    def __init__(
        self,
        *,
        gate_id: str,
        regulatory_norm: str,
        reason: str,
        audit_hash: str,
        audit_trail: List[GateAuditRecord],
        status_code: int = 422,
    ) -> None:
        super().__init__(reason)
        self.gate_id = gate_id
        self.regulatory_norm = regulatory_norm
        self.reason = reason
        self.audit_hash = audit_hash
        self.audit_trail = list(audit_trail)
        self.status_code = status_code


class GatekeeperG1G6:
    """Sekvenční validační a sanitizační vrstva G1 -> G2 -> G3 -> G4 -> G5 -> G6."""

    @classmethod
    def validate_and_sanitize(
        cls,
        query: str,
        domain_scores: Optional[Dict[str, Any]] = None,
        embedding_vector: Optional[List[Any]] = None,
        system_stress_sigma: float = 0.18,
        context_edges_count: int = 12,
        audit_sink: Optional[Callable[[Dict[str, Any]], None]] = None,
    ) -> SanitizedImpactPayload:
        trail: List[GateAuditRecord] = []
        prev_hash = "GENESIS"

        g1_query, prev_hash = cls._gate1(query, trail, prev_hash, audit_sink)
        prev_hash = cls._gate2(g1_query, trail, prev_hash, audit_sink)
        prev_hash = cls._gate3(g1_query, domain_scores, context_edges_count, trail, prev_hash, audit_sink)
        g4_query, prev_hash = cls._gate4(g1_query, trail, prev_hash, audit_sink)
        sanitized_scores, prev_hash = cls._gate5(
            domain_scores, system_stress_sigma, trail, prev_hash, audit_sink
        )
        norm_emb, entropy, prev_hash = cls._gate6(
            sanitized_scores, embedding_vector, trail, prev_hash, audit_sink
        )

        return SanitizedImpactPayload(
            sanitized_query=g4_query,
            sanitized_domain_scores=sanitized_scores,
            normalized_embedding=norm_emb,
            system_stress_sigma=float(system_stress_sigma),
            shannon_entropy=entropy,
            audit_trail=trail,
            chain_commitment_hash=prev_hash,
        )

    @classmethod
    def _gate1(
        cls,
        raw_query: str,
        trail: List[GateAuditRecord],
        prev_hash: str,
        audit_sink: Optional[Callable[[Dict[str, Any]], None]],
    ) -> Tuple[str, str]:
        norm = "ISO/IEC 42001 Sec. 8.2 (Epistemická sanitizace G1)"
        cleaned = _SPACE_RE.sub(" ", _CTRL_RE.sub(" ", str(raw_query or ""))).strip()
        lower = cleaned.lower()
        hit = next((pat for pat in _G1_INJECTION_PATTERNS if pat in lower), None)

        if len(cleaned) < 3 or hit is not None:
            reason = (
                f"G1 selhala: detekována injekce nebo únik direktiv ('{hit}')."
                if hit is not None
                else "G1 selhala: prázdný nebo příliš krátký vstup (< 3 znaky)."
            )
            cls._abort("G1", norm, reason, trail, prev_hash, audit_sink)

        new_hash = cls._record_pass(
            "G1", norm, f"sanitized_chars={len(cleaned)}", "Vstup sanitizován a ověřen.", trail, prev_hash, audit_sink
        )
        return cleaned, new_hash

    @classmethod
    def _gate2(
        cls,
        query: str,
        trail: List[GateAuditRecord],
        prev_hash: str,
        audit_sink: Optional[Callable[[Dict[str, Any]], None]],
    ) -> str:
        norm = "EU AI Act 2024/1689 Art. 5 (Klasifikace rizik G2)"
        lower = query.lower()
        hit = next((pat for pat in _G2_PROHIBITED_PATTERNS if pat in lower), None)
        token_count = sum(1 for tok in query.split(" ") if len(tok) >= 2)

        if hit is not None or token_count < 2:
            reason = (
                f"G2 selhala: zakázaná AI praktika dle EU AI Act ('{hit}')."
                if hit is not None
                else f"G2 selhala: nedostatečná sémantická hustota vstupu (tokeny={token_count} < 2)."
            )
            cls._abort("G2", norm, reason, trail, prev_hash, audit_sink)

        return cls._record_pass(
            "G2", norm, f"tokens={token_count}", "Sémantická a EU AI Act validace splněna.", trail, prev_hash, audit_sink
        )

    @classmethod
    def _gate3(
        cls,
        query: str,
        domain_scores: Optional[Dict[str, Any]],
        context_edges_count: int,
        trail: List[GateAuditRecord],
        prev_hash: str,
        audit_sink: Optional[Callable[[Dict[str, Any]], None]],
    ) -> str:
        norm = "Směrnice NIS2 Art. 21 & CSR Kauzalita (G3)"
        lower = query.lower()
        hit = next((pat for pat in _G3_NIS2_PATTERNS if pat in lower), None)

        safe_preview = dict(DEFAULT_MATRIX_WEIGHTS)
        if isinstance(domain_scores, dict):
            for k, v in domain_scores.items():
                key = str(k).strip().upper()
                if key in safe_preview:
                    try:
                        num = float(v)
                        if math.isfinite(num):
                            safe_preview[key] = min(1.0, max(0.0, num))
                    except (TypeError, ValueError):
                        pass

        graph = CsrKnowledgeGraph.build_8d_tensor_graph(safe_preview)
        path = graph.find_shortest_causal_path("SYS", "LAW")

        if hit is not None or context_edges_count < 1 or not path:
            reason = (
                f"G3 selhala: detekován kybernetický útočný vektor ('{hit}')."
                if hit is not None
                else f"G3 selhala: přerušená kauzální cesta v CSR grafu (hrany={context_edges_count})."
            )
            cls._abort("G3", norm, reason, trail, prev_hash, audit_sink)

        return cls._record_pass(
            "G3", norm, f"causal_hops={len(path)}", "NIS2 a CSR kauzální prostupnost ověřeny.", trail, prev_hash, audit_sink
        )

    @classmethod
    def _gate4(
        cls,
        query: str,
        trail: List[GateAuditRecord],
        prev_hash: str,
        audit_sink: Optional[Callable[[Dict[str, Any]], None]],
    ) -> Tuple[str, str]:
        norm = "ISO/IEC 42001 & NIST SP 800-207 Zero-Trust (G4)"
        lower = query.lower()
        hit = next((pat for pat in _G4_ZERO_TRUST_PATTERNS if pat in lower), None)
        if hit is not None:
            cls._abort("G4", norm, f"G4 selhala: porušení Zero-Trust politiky ('{hit}').", trail, prev_hash, audit_sink)

        redacted = _API_KEY_RE.sub("[REDACTED_KEY]", _EMAIL_RE.sub("[REDACTED_PII]", query))
        pii_redacted = int(redacted != query)
        new_hash = cls._record_pass(
            "G4", norm, f"pii_redacted={pii_redacted}", "Zero-Trust a PII sanitizace dokončena.", trail, prev_hash, audit_sink
        )
        return redacted, new_hash

    @classmethod
    def _gate5(
        cls,
        domain_scores: Optional[Dict[str, Any]],
        sigma: float,
        trail: List[GateAuditRecord],
        prev_hash: str,
        audit_sink: Optional[Callable[[Dict[str, Any]], None]],
    ) -> Tuple[Dict[str, float], str]:
        norm = "AKGE-8D Konečnost tenzorů & Invariant σ (G5)"
        corrupt = detect_corrupt_tensor_inputs(domain_scores=domain_scores, query_embedding=None)

        try:
            sigma_val = float(sigma)
        except (TypeError, ValueError):
            sigma_val = float("nan")

        if corrupt["non_finite_domains"] or not math.isfinite(sigma_val) or sigma_val < 0.0 or sigma_val >= 0.85:
            reason = (
                f"G5 selhala: ne-konečné hodnoty (NaN/Inf) v doménách {', '.join(corrupt['non_finite_domains'])}."
                if corrupt["non_finite_domains"]
                else f"G5 selhala: neplatné nebo kritické systémové pnutí σ={sigma}."
            )
            cls._abort("G5", norm, reason, trail, prev_hash, audit_sink)

        upper_scores: Dict[str, float] = {}
        if isinstance(domain_scores, dict):
            for k, v in domain_scores.items():
                upper_scores[str(k).strip().upper()] = float(v)

        sanitized = {
            d: round(min(1.0, max(0.0, upper_scores.get(d, DEFAULT_MATRIX_WEIGHTS[d]))), 6)
            for d in DOMAINS_8D
        }
        new_hash = cls._record_pass(
            "G5", norm, f"domains={len(sanitized)},sigma={sigma_val:.3f}", "8D váhy sanitizovány do [0,1].", trail, prev_hash, audit_sink
        )
        return sanitized, new_hash

    @classmethod
    def _gate6(
        cls,
        sanitized_scores: Dict[str, float],
        embedding_vector: Optional[List[Any]],
        trail: List[GateAuditRecord],
        prev_hash: str,
        audit_sink: Optional[Callable[[Dict[str, Any]], None]],
    ) -> Tuple[Optional[List[float]], float, str]:
        norm = "AKGE-8D L2 Vektorová integrita & Shannon S < 2.08 (G6)"
        normalized_emb: Optional[List[float]] = None

        if embedding_vector is not None:
            corrupt = detect_corrupt_tensor_inputs(domain_scores=None, query_embedding=embedding_vector)
            if corrupt["has_corrupt_input"]:
                cls._abort(
                    "G6",
                    norm,
                    f"G6 selhala: {corrupt['reason']}",
                    trail,
                    prev_hash,
                    audit_sink,
                )
            normalized_emb = normalize_l2_64d([float(x) for x in embedding_vector])

        vals = [max(0.01, min(1.0, sanitized_scores[d])) for d in DOMAINS_8D]
        total = max(0.001, sum(vals))
        entropy = 0.0
        for val in vals:
            p = val / total
            if p > 1e-4:
                entropy -= p * math.log(p)

        if not math.isfinite(entropy) or entropy >= 2.08:
            cls._abort(
                "G6",
                norm,
                f"G6 selhala: Shannonova entropie S={entropy:.4f} překročila bifurkační práh 2.08.",
                trail,
                prev_hash,
                audit_sink,
            )

        emb_dim = len(normalized_emb) if normalized_emb is not None else 0
        new_hash = cls._record_pass(
            "G6",
            norm,
            f"entropy={entropy:.4f},emb_dim={emb_dim}",
            "Integrita vektoru a entropie ověřena.",
            trail,
            prev_hash,
            audit_sink,
        )
        return normalized_emb, round(entropy, 6), new_hash

    @classmethod
    def _chain_hash(cls, prev_hash: str, gate_id: str, status: str, detail: str) -> str:
        payload = f"GATEKEEPER||{prev_hash}||{gate_id}||{status}||{detail}"
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16].upper()

    @classmethod
    def _emit_audit(
        cls,
        record: GateAuditRecord,
        audit_sink: Optional[Callable[[Dict[str, Any]], None]],
    ) -> None:
        event = {
            "component": f"GATEKEEPER_{record.gate_id}",
            "gate_id": record.gate_id,
            "passed": record.passed,
            "regulatory_norm": record.regulatory_norm,
            "sanitized_summary": record.sanitized_summary,
            "reason": record.reason,
            "audit_hash": record.audit_hash,
            "timestamp_ms": record.timestamp_ms,
        }
        if record.passed:
            audit_logger.info("[GATEKEEPER_%s] PASSED (%s) hash=%s", record.gate_id, record.sanitized_summary, record.audit_hash)
        else:
            audit_logger.error("[GATEKEEPER_%s] FAILED (%s) hash=%s", record.gate_id, record.reason, record.audit_hash)
        if audit_sink is not None:
            audit_sink(event)

    @classmethod
    def _record_pass(
        cls,
        gate_id: str,
        norm: str,
        summary: str,
        reason: str,
        trail: List[GateAuditRecord],
        prev_hash: str,
        audit_sink: Optional[Callable[[Dict[str, Any]], None]],
    ) -> str:
        digest = cls._chain_hash(prev_hash, gate_id, "PASSED", summary)
        rec = GateAuditRecord(
            gate_id=gate_id,
            passed=True,
            regulatory_norm=norm,
            sanitized_summary=summary,
            reason=reason,
            audit_hash=digest,
        )
        trail.append(rec)
        cls._emit_audit(rec, audit_sink)
        return digest

    @classmethod
    def _abort(
        cls,
        gate_id: str,
        norm: str,
        reason: str,
        trail: List[GateAuditRecord],
        prev_hash: str,
        audit_sink: Optional[Callable[[Dict[str, Any]], None]],
    ) -> None:
        digest = cls._chain_hash(prev_hash, gate_id, "FAILED", reason)
        rec = GateAuditRecord(
            gate_id=gate_id,
            passed=False,
            regulatory_norm=norm,
            sanitized_summary="ABORTED",
            reason=reason,
            audit_hash=digest,
        )
        trail.append(rec)
        cls._emit_audit(rec, audit_sink)
        raise GatekeeperChainException(
            gate_id=gate_id,
            regulatory_norm=norm,
            reason=reason,
            audit_hash=digest,
            audit_trail=trail,
        )


def evaluate_impact_matrix_guarded(
    query: str,
    domain_scores: Optional[Dict[str, Any]] = None,
    embedding_vector: Optional[List[Any]] = None,
    system_stress_sigma: float = 0.18,
    audit_sink: Optional[Callable[[Dict[str, Any]], None]] = None,
) -> Dict[str, Any]:
    """Vyhodnotí Matici dopadů až po průchodu sekvenční vrstvou Gatekeeper G1-G6."""
    validated = GatekeeperG1G6.validate_and_sanitize(
        query=query,
        domain_scores=domain_scores,
        embedding_vector=embedding_vector,
        system_stress_sigma=system_stress_sigma,
        audit_sink=audit_sink,
    )
    w = validated.sanitized_domain_scores
    econ = min(99.0, max(40.0, w["ECON"] * 90.0 + 5.0))
    tech = min(99.0, max(50.0, w["SYS"] * 92.0 + 6.0))
    eco = min(99.0, max(45.0, w["ECO"] * 88.0 + 8.0))
    psych = min(99.0, max(40.0, w["PSYCH"] * 85.0 + 10.0))
    win_win_win = round((econ * 0.25 + tech * 0.30 + eco * 0.25 + psych * 0.20) / 100.0, 4)
    return {
        "sanitized_query": validated.sanitized_query,
        "sanitized_domain_scores": w,
        "normalized_embedding": validated.normalized_embedding,
        "shannon_entropy": validated.shannon_entropy,
        "economic_viability": round(econ, 2),
        "technical_elegance": round(tech, 2),
        "eco_social_regeneration": round(eco, 2),
        "psychological_acceptance": round(psych, 2),
        "win_win_win_score": min(1.0, max(0.0, win_win_win)),
        "chain_commitment_hash": validated.chain_commitment_hash,
        "gate_audit_trail": [
            {
                "gate_id": r.gate_id,
                "passed": r.passed,
                "regulatory_norm": r.regulatory_norm,
                "sanitized_summary": r.sanitized_summary,
                "reason": r.reason,
                "audit_hash": r.audit_hash,
            }
            for r in validated.audit_trail
        ],
    }
