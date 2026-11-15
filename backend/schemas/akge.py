"""
O.M.N.I.S. AKGE-8D Pydantic v2 Schémata
Definuje striktní neměnné (frozen=True) datové kontrakty pro 8D tenzorovou matici,
Proof of Necessity (PoN), 6-úrovňový Refusal Ladder a Cryptographic Audit Commitment
(HMAC-SHA256 Hash Chain).
"""

from __future__ import annotations

import hashlib
import hmac
import os
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, ConfigDict, Field, model_validator


PRIORITY_PROFILES: Dict[str, Dict[str, float]] = {
    "Balanced Core": {
        "SYS": 0.125,
        "ECON": 0.125,
        "PSYCH": 0.125,
        "ECO": 0.125,
        "LAW": 0.125,
        "SEC": 0.125,
        "PHYS": 0.125,
        "SOC": 0.125,
    },
    "High Sec & Phys": {
        "SEC": 0.25,
        "PHYS": 0.25,
        "SYS": 0.15,
        "LAW": 0.15,
        "ECON": 0.05,
        "PSYCH": 0.05,
        "ECO": 0.05,
        "SOC": 0.05,
    },
    "Lean Econ": {
        "ECON": 0.35,
        "SYS": 0.20,
        "SEC": 0.15,
        "LAW": 0.10,
        "PSYCH": 0.05,
        "ECO": 0.05,
        "PHYS": 0.05,
        "SOC": 0.05,
    },
}

DOMAIN_KEYS = ["SYS", "ECON", "PSYCH", "ECO", "LAW", "SEC", "PHYS", "SOC"]


class Matrix8DState(BaseModel):
    """
    Neměnný stavový model 8D doménové matice a 28-párového korelačního tenzoru.
    Počítá vážený harmonický průměr H_w dle profilů: Balanced Core, High Sec & Phys, Lean Econ.
    """
    model_config = ConfigDict(frozen=True)

    sys: float = Field(default=0.5, ge=0.0, le=1.0, description="Systémové inženýrství & Kybernetika")
    econ: float = Field(default=0.5, ge=0.0, le=1.0, description="Teorie her & Asymetrická ekonomie")
    psych: float = Field(default=0.5, ge=0.0, le=1.0, description="Kognitivní vědy & Neuro-ergonomie")
    eco: float = Field(default=0.5, ge=0.0, le=1.0, description="Regenerativní dynamika & Ekologie")
    law: float = Field(default=0.5, ge=0.0, le=1.0, description="Regulace, Právo & AI Governance")
    sec: float = Field(default=0.5, ge=0.0, le=1.0, description="Zero-Trust Bezpečnost & Kryptografie")
    phys: float = Field(default=0.5, ge=0.0, le=1.0, description="Fyzikální termodynamika & Výpočetní meze")
    soc: float = Field(default=0.5, ge=0.0, le=1.0, description="Socio-kulturní dynamika & Etika")

    priority_profile: str = Field(default="Balanced Core", description="Aktivní váhový profil H_w")
    correlation_tensor_28: Dict[str, float] = Field(default_factory=dict, description="28-párový tenzor r_ij in [-1, 1]")
    harmonic_mean_hw: float = Field(default=0.5, ge=0.0, le=1.0, description="Vážený harmonický průměr H_w")
    timestamp: int = Field(default_factory=lambda: int(datetime.now(timezone.utc).timestamp() * 1000))

    @model_validator(mode="after")
    def compute_hw_and_tensor(self) -> Matrix8DState:
        weights = PRIORITY_PROFILES.get(self.priority_profile, PRIORITY_PROFILES["Balanced Core"])
        values = {
            "SYS": max(0.001, float(self.sys)),
            "ECON": max(0.001, float(self.econ)),
            "PSYCH": max(0.001, float(self.psych)),
            "ECO": max(0.001, float(self.eco)),
            "LAW": max(0.001, float(self.law)),
            "SEC": max(0.001, float(self.sec)),
            "PHYS": max(0.001, float(self.phys)),
            "SOC": max(0.001, float(self.soc)),
        }

        sum_w = sum(weights.values())
        sum_w_over_v = sum(weights[k] / values[k] for k in DOMAIN_KEYS)
        hw = sum_w / sum_w_over_v if sum_w_over_v > 0 else 0.5
        object.__setattr__(self, "harmonic_mean_hw", round(min(1.0, max(0.0, hw)), 4))

        if not self.correlation_tensor_28:
            tensor: Dict[str, float] = {}
            for i in range(len(DOMAIN_KEYS)):
                for j in range(i + 1, len(DOMAIN_KEYS)):
                    k1, k2 = DOMAIN_KEYS[i], DOMAIN_KEYS[j]
                    r_val = 1.0 - 2.0 * abs(values[k1] - values[k2])
                    tensor[f"{k1}_{k2}"] = round(max(-1.0, min(1.0, r_val)), 4)
            object.__setattr__(self, "correlation_tensor_28", tensor)

        return self

    def to_vector(self) -> List[float]:
        return [self.sys, self.econ, self.psych, self.eco, self.law, self.sec, self.phys, self.soc]


class ProofOfNecessityLog(BaseModel):
    """
    Povinný neměnný PoN log ke každé systémové akci,
    alokaci zdrojů nebo eskalaci kontextu.
    """
    model_config = ConfigDict(frozen=True)

    trigger_vector: str = Field(..., description="Přesný důvod spuštění akce")
    exclusion_proof: str = Field(..., description="Zdůvodnění zamítnutí jednodušší/levnější alternativy")
    minimization_check: str = Field(..., description="Důkaz dodržení principu nejnižších oprávnění a minimální entropie")
    context_id: Optional[str] = Field(default_factory=lambda: str(uuid.uuid4()))
    system_tension_sigma: float = Field(default=0.15, ge=0.0, le=1.0, description="Systémové pnutí sigma")
    timestamp: int = Field(default_factory=lambda: int(datetime.now(timezone.utc).timestamp() * 1000))

    def format_log_entry(self) -> str:
        return (
            f"[PoN-LOG:{self.context_id}] σ={self.system_tension_sigma:.2f} | "
            f"TRIGGER={self.trigger_vector} | EXCLUSION={self.exclusion_proof} | "
            f"MINIMIZATION={self.minimization_check}"
        )


class GateEvaluationResult(BaseModel):
    """
    Neměnný výsledek vyhodnocení 6 bran (G1–G6) s výpočtem Shannonovy entropie S
    a JSON-LD Conformity Passportem pro EU AI Act & NIS2.
    """
    model_config = ConfigDict(frozen=True)

    g1_entry_point: bool = Field(default=True, description="G1: Mapování vstupních uzlů do 8D ontologie")
    g2_concept_coupling: bool = Field(default=True, description="G2: Sémantické provázání min. 2 konceptů")
    g3_causal_path: bool = Field(default=True, description="G3: Existence spojité kauzální cesty v CsrKnowledgeGraph")
    g4_quotable_evidence: bool = Field(default=True, description="G4: Regulatorní shoda EU AI Act & NIS2")
    g5_retraction_integrity: bool = Field(default=True, description="G5: Ochrana proti falzifikovaným/zastaralým premisám")
    g6_entailment_entropy: bool = Field(default=True, description="G6: Logická dedukce & Shannonova entropie")

    all_passed: bool = Field(default=True)
    failed_gate: Optional[str] = None
    shannon_entropy: float = Field(default=0.12, description="S = -sum(p_i * ln(p_i))")
    refusal_reason: Optional[str] = None
    suggested_remediation: Optional[str] = None
    conformity_passport: Dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def evaluate_gates(self) -> GateEvaluationResult:
        gates = [
            ("G1", self.g1_entry_point, "Mapování uzlů selhalo - vstup neodpovídá 8D ontologii"),
            ("G2", self.g2_concept_coupling, "Nedostatečné provázání konceptů - chybí min. 2 sémantické relace"),
            ("G3", self.g3_causal_path, "Chybí spojitá kauzální cesta v CsrKnowledgeGraph"),
            ("G4", self.g4_quotable_evidence, "Regulatorní neshoda s EU AI Act nebo NIS2 směrnicí"),
            ("G5", self.g5_retraction_integrity, "Detekovány falzifikované nebo zastaralé premisy"),
            ("G6", self.g6_entailment_entropy, "Vysoká entropie / logický rozpad dedukce"),
        ]

        all_ok = True
        failed: Optional[str] = None
        reason_text = self.refusal_reason
        for name, passed, reason in gates:
            if not passed:
                all_ok = False
                failed = name
                if not reason_text:
                    reason_text = f"Explicit Grounded Refusal na bráně {name}: {reason}"
                break

        object.__setattr__(self, "all_passed", all_ok)
        object.__setattr__(self, "failed_gate", failed)
        object.__setattr__(self, "refusal_reason", reason_text)

        if not self.conformity_passport:
            passport = {
                "@context": "https://schema.org/omnis-compliance-v1.jsonld",
                "@type": "AiCompliancePassport",
                "frameworks": ["EU_AI_ACT_HIGH_RISK_ANNEX_IV", "NIS2_DIRECTIVE_ART_21"],
                "gate_status": {
                    "G1": self.g1_entry_point,
                    "G2": self.g2_concept_coupling,
                    "G3": self.g3_causal_path,
                    "G4": self.g4_quotable_evidence,
                    "G5": self.g5_retraction_integrity,
                    "G6": self.g6_entailment_entropy,
                },
                "shannon_entropy": self.shannon_entropy,
                "compliance_valid": self.g4_quotable_evidence and all_ok,
                "issued_at": datetime.now(timezone.utc).isoformat(),
            }
            object.__setattr__(self, "conformity_passport", passport)

        return self


class CryptographicAuditCommitment(BaseModel):
    """
    Neměnný Cryptographic Audit Commitment (HMAC-SHA256 Hash Chain / SHA-256 Commitment).
    Podporuje řetězení přes prev_commitment_hash a kryptografický podpis pomocí AUDIT_HMAC_KEY.
    """
    model_config = ConfigDict(frozen=True)

    query_hash: str = Field(..., description="SHA-256 hash uživatelského dotazu")
    r_systemic: float = Field(..., description="Index systémové odolnosti H_w")
    tier: str = Field(default="APPROVED", description="Bezpečnostní stupeň (APPROVED, RESTRICTED, ESCALATED, OVERRIDE)")
    nonce: str = Field(default_factory=lambda: uuid.uuid4().hex[:16])
    override_active: bool = Field(default=False, description="Příznak administrátorského přepsání")
    prev_commitment_hash: str = Field(default="GENESIS", description="Odkaz na předchozí auditní závazek v řetězci")
    algorithm: str = Field(default="HMAC-SHA256-HASH-CHAIN", description="Použitý kryptografický algoritmus")
    hmac_key: Optional[str] = Field(default=None, exclude=True, repr=False)
    commitment_hash: str = Field(default="", description="Výsledný kryptografický otisk")
    timestamp: int = Field(default_factory=lambda: int(datetime.now(timezone.utc).timestamp() * 1000))

    @model_validator(mode="after")
    def calculate_commitment(self) -> CryptographicAuditCommitment:
        effective_key = (self.hmac_key or "").strip()
        if effective_key:
            payload = (
                f"OMNIS-AUDIT-CHAIN||{self.prev_commitment_hash}||"
                f"{self.query_hash}||{self.r_systemic:.4f}||{self.tier}||{self.nonce}"
            )
            if self.override_active:
                payload += "||OVERRIDE_ACTIVE"
            digest = hmac.new(
                effective_key.encode("utf-8"),
                payload.encode("utf-8"),
                hashlib.sha256,
            ).hexdigest()
            algo = "HMAC-SHA256-HASH-CHAIN"
        elif self.prev_commitment_hash != "GENESIS":
            payload = (
                f"OMNIS-AUDIT-CHAIN||{self.prev_commitment_hash}||"
                f"{self.query_hash}||{self.r_systemic:.4f}||{self.tier}||{self.nonce}"
            )
            if self.override_active:
                payload += "||OVERRIDE_ACTIVE"
            digest = hashlib.sha256(payload.encode("utf-8")).hexdigest()
            algo = "SHA-256-HASH-CHAIN"
        else:
            payload = f"OMNIS-ZK-PROOF||{self.query_hash}||{self.r_systemic:.4f}||{self.tier}||{self.nonce}"
            if self.override_active:
                payload += "||OVERRIDE_ACTIVE"
            digest = hashlib.sha256(payload.encode("utf-8")).hexdigest()
            algo = "SHA-256-COMMITMENT"

        object.__setattr__(self, "commitment_hash", digest)
        object.__setattr__(self, "algorithm", algo)
        return self

    @classmethod
    def generate(
        cls,
        query: str,
        r_systemic: float,
        tier: str = "APPROVED",
        nonce: Optional[str] = None,
        override_active: bool = False,
        prev_commitment_hash: str = "GENESIS",
        hmac_key: Optional[str] = None,
    ) -> CryptographicAuditCommitment:
        q_hash = hashlib.sha256(query.encode("utf-8")).hexdigest()
        n = nonce or uuid.uuid4().hex[:16]
        resolved_key = hmac_key if hmac_key is not None else os.getenv("AUDIT_HMAC_KEY")
        return cls(
            query_hash=q_hash,
            r_systemic=r_systemic,
            tier=tier,
            nonce=n,
            override_active=override_active,
            prev_commitment_hash=prev_commitment_hash,
            hmac_key=resolved_key or None,
        )


# Zpětná kompatibilita názvu pro stávající volající moduly
ZkAuditCommitment = CryptographicAuditCommitment
