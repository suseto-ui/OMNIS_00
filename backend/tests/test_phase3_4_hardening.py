"""
O.M.N.I.S. APEX v4.5.0-GA — Fáze 3 & 4 Unit & Hardening Testy
Ověřuje:
1. Sjednocenou bránu LLM inference (`LLMCascadeRouter`, `tools/gemma4/agent.yaml`, `ProviderCircuitBreaker` CLOSED->OPEN->HALF_OPEN->CLOSED).
2. Hranové ošetření 8D tenzorového jádra (`NaN`/`Inf` sanitizace, L2-normalizace 64D vektorů, Shannonova entropie v bráně G6).
3. Striktní izolaci rolí `STANDARD_USER` vs. `ADMIN_OPERATOR` a kryptografickou verifikaci ZK Audit Ledgeru.
"""

from __future__ import annotations

import asyncio
import importlib.util
import math
import sys
import unittest
from pathlib import Path

from backend.app.llm_cascade import (
    CircuitState,
    LLMCascadeRouter,
    ProviderCircuitBreaker,
    load_gemma4_agent_config,
)
from backend.csr_graph import (
    DOMAINS_8D,
    CsrKnowledgeGraph,
    _sanitize_unit_float,
    compute_advanced_metrics,
    normalize_l2_64d,
)

try:
    from backend.security.zk_ledger_service import ZkAuditLedgerService
except ImportError:
    _zk_path = Path(__file__).resolve().parents[1] / "security" / "zk_ledger_service.py"
    _spec = importlib.util.spec_from_file_location("zk_ledger_service_standalone", _zk_path)
    _mod = importlib.util.module_from_spec(_spec)
    assert _spec is not None and _spec.loader is not None
    sys.modules[_spec.name] = _mod
    _spec.loader.exec_module(_mod)
    ZkAuditLedgerService = _mod.ZkAuditLedgerService

HAS_PYDANTIC = importlib.util.find_spec("pydantic") is not None
HAS_FASTAPI = importlib.util.find_spec("fastapi") is not None


class TestPhase3LlmCascadeAndTensorEdgeCases(unittest.TestCase):
    def test_gemma4_agent_yaml_loaded_and_circuit_breaker_transitions(self):
        cfg = load_gemma4_agent_config()
        self.assertEqual(cfg["agent_name"], "gemma4-patch-specialist")
        self.assertEqual(cfg["context_window"], 8192)
        self.assertAlmostEqual(cfg["temperature"], 0.1, places=4)

        now_clock = [100.0]
        breaker = ProviderCircuitBreaker(
            name="gemma4_local",
            failure_threshold=3,
            recovery_timeout_seconds=60.0,
            clock=lambda: now_clock[0],
        )
        self.assertEqual(breaker.state, CircuitState.CLOSED)
        self.assertTrue(breaker.allow_request())

        breaker.record_failure("timeout-1")
        breaker.record_failure("timeout-2")
        self.assertEqual(breaker.state, CircuitState.CLOSED)
        breaker.record_failure("timeout-3")
        self.assertEqual(breaker.state, CircuitState.OPEN)
        self.assertFalse(breaker.allow_request())

        now_clock[0] += 61.0
        self.assertTrue(breaker.allow_request())
        self.assertEqual(breaker.state, CircuitState.HALF_OPEN)

        breaker.record_success()
        self.assertEqual(breaker.state, CircuitState.CLOSED)
        self.assertEqual(breaker.failure_count, 0)

    def test_llm_cascade_falls_back_deterministically_when_all_breakers_open(self):
        router = LLMCascadeRouter()
        for breaker in router.breakers.values():
            breaker.state = CircuitState.OPEN
            breaker.opened_at = 999999999.0
            breaker.clock = lambda: 999999999.0

        res = asyncio.run(router.generate_completion([{"role": "user", "content": "Verify resilience"}]))
        self.assertEqual(res["status"], "fallback")
        self.assertIn("gemma4_local", res["open_circuit_breakers"])

    def test_nan_inf_sanitization_and_64d_l2_normalization(self):
        self.assertEqual(_sanitize_unit_float(float("nan"), default=0.5), 0.5)
        self.assertEqual(_sanitize_unit_float(float("inf"), default=0.5), 0.5)
        self.assertEqual(_sanitize_unit_float(float("-inf"), default=0.5), 0.5)

        graph = CsrKnowledgeGraph.build_8d_tensor_graph(
            {d: float("nan") if idx % 2 == 0 else float("inf") for idx, d in enumerate(DOMAINS_8D)}
        )
        topo = graph.calculate_topological_invariants({d: float("nan") for d in DOMAINS_8D})
        self.assertTrue(math.isfinite(topo["shannon_entropy"]))
        self.assertGreaterEqual(topo["shannon_entropy"], 0.0)

        metrics = compute_advanced_metrics({d: float("nan") for d in DOMAINS_8D}, float("inf"), 18)
        for key in ("quantumEntanglementIndex", "selfHealingConfidence", "cognitiveAnomalyScore"):
            self.assertTrue(math.isfinite(metrics[key]))
            self.assertGreaterEqual(metrics[key], 0.0)
            self.assertLessEqual(metrics[key], 1.0)

        raw_64 = [float("nan"), 3.0, 4.0, float("inf")] + [0.0] * 60
        normed = normalize_l2_64d(raw_64)
        self.assertEqual(len(normed), 64)
        l2_sq = sum(x * x for x in normed)
        self.assertAlmostEqual(l2_sq, 1.0, places=5)
        self.assertEqual(normalize_l2_64d([0.0] * 64), [0.0] * 64)

    def test_zk_audit_ledger_canonical_verification(self):
        q = "Auditní záznam AKGE-8D"
        q_hash = ZkAuditLedgerService.hash_query(q)
        commit = ZkAuditLedgerService.compute_canonical_commitment(
            prev_hash="GENESIS",
            query_hash=q_hash,
            r_systemic=0.9123,
            tier="APPROVED",
            nonce="0x9A8B",
            override_active=False,
        )
        self.assertTrue(
            ZkAuditLedgerService.verify_canonical_commitment(
                prev_hash="GENESIS",
                query=q,
                r_systemic=0.9123,
                tier="APPROVED",
                nonce="0x9A8B",
                expected_hash=commit,
                override_active=False,
            )
        )
        self.assertFalse(
            ZkAuditLedgerService.verify_canonical_commitment(
                prev_hash="GENESIS",
                query=q,
                r_systemic=0.9123,
                tier="OVERRIDE",
                nonce="0x9A8B",
                expected_hash=commit,
                override_active=True,
            )
        )


@unittest.skipUnless(HAS_PYDANTIC and HAS_FASTAPI, "Requires pydantic and fastapi in CI environment")
class TestPhase4SchemasAndRbacIsolation(unittest.TestCase):
    def test_matrix8d_and_gate6_reject_nan_and_verify_zk_commitment(self):
        from backend.schemas.akge import (
            CryptographicAuditCommitment,
            GateEvaluationResult,
            Matrix8DState,
        )

        m = Matrix8DState(sys=float("nan"), econ=float("inf"), sec=0.9, law=0.85)
        self.assertTrue(math.isfinite(m.harmonic_mean_hw))
        self.assertEqual(m.sys, 0.5)
        self.assertEqual(m.econ, 0.5)

        g_nan = GateEvaluationResult(shannon_entropy=float("nan"))
        self.assertFalse(g_nan.g6_entailment_entropy)
        self.assertFalse(g_nan.all_passed)
        self.assertEqual(g_nan.failed_gate, "G6")

        c = CryptographicAuditCommitment.generate(
            query="ZK verify test",
            r_systemic=0.94,
            tier="APPROVED",
            nonce="deadbeef",
            hmac_key="ci-test-key",
        )
        self.assertTrue(
            CryptographicAuditCommitment.verify_commitment(
                query="ZK verify test",
                r_systemic=0.94,
                tier="APPROVED",
                nonce="deadbeef",
                expected_hash=c.commitment_hash,
                hmac_key="ci-test-key",
            )
        )

    def test_inference_projection_isolates_standard_user_vs_admin_operator(self):
        from backend.routers.inference import project_inference_for_role

        sample = {
            "status": "APPROVED",
            "topological_invariants": {"vertices": 8, "edges": 28, "shannon_entropy": 1.12},
            "advanced_metrics": {"quantumEntanglementIndex": 0.9, "selfHealingConfidence": 0.8},
            "proof_of_necessity": {
                "trigger_vector": "Caller(adm) -> InternalVector",
                "exclusion_proof": "Secret exclusion telemetry",
            },
        }
        std = project_inference_for_role(sample, "STANDARD_USER")
        self.assertEqual(std["topological_invariants"]["access_tier"], "STANDARD_USER")
        self.assertNotIn("vertices", std["topological_invariants"])
        self.assertEqual(std["proof_of_necessity"]["trigger_vector"], "REDACTED_STANDARD_USER")

        adm = project_inference_for_role(sample, "ADMIN_OPERATOR")
        self.assertEqual(adm["topological_invariants"]["vertices"], 8)
        self.assertEqual(adm["proof_of_necessity"]["trigger_vector"], "Caller(adm) -> InternalVector")


if __name__ == "__main__":
    unittest.main()
