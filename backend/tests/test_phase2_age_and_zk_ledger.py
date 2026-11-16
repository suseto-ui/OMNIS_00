"""
O.M.N.I.S. APEX v4.5 - Fáze 2 Unit & Integrační Testy
Ověřuje:
1. Perzistentní kauzální graf Apache AGE (`omnis_causal_graph`), kompilaci `CsrGraphSnapshot`
   a lock-free atomickou výměnu reference při `LISTEN omnis_graph_invalidated`.
2. Bránu G3 (Judea Pearl Do-Calculus `P(Y | do(X=x))`) včetně detekce přerušené kauzální cesty.
3. Striktě append-only `zk_audit_ledger` SHA-256 řetězec, detekci manipulace a blokaci UPDATE/DELETE.
"""

from __future__ import annotations

import asyncio
import hashlib
import os
import sqlite3
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

from backend.csr_graph import DOMAINS_8D
from backend.graph.age_repository import (
    AgeCausalGraphRepository,
    CsrGraphSnapshot,
    _parse_agtype,
    validate_domain_code,
)


class _DummyAsyncConn:
    """Lehká simulace asyncpg spojení pro ověření openCypher dotazů a atomické výměny."""

    def __init__(self, node_codes, edge_rows):
        self._node_codes = node_codes
        self._edge_rows = edge_rows
        self.executed_queries = []

    async def execute(self, sql: str, *args):
        self.executed_queries.append(sql)
        return "OK"

    async def fetch(self, sql: str, *args):
        self.executed_queries.append(sql)
        if "RETURN n.code" in sql:
            return [{"code": f'"{code}"'} for code in self._node_codes]
        if "RETURN a.code, b.code, r.weight, r.mechanism" in sql:
            return [
                {
                    "src": f'"{src}"',
                    "tgt": f'"{tgt}"',
                    "weight": f"{weight}",
                    "mechanism": f'"{mech}"',
                }
                for src, tgt, weight, mech in self._edge_rows
            ]
        return []

    async def close(self):
        return None


class TestPhase2AgeCausalGraph(unittest.TestCase):
    def test_snapshot_immutability_and_lock_free_swap(self):
        repo = AgeCausalGraphRepository()
        snap_v1 = repo.get_snapshot()
        self.assertIsInstance(snap_v1, CsrGraphSnapshot)
        self.assertEqual(snap_v1.version, 1)
        self.assertEqual(snap_v1.node_count, len(DOMAINS_8D))

        # Neměnnost objektu CsrGraphSnapshot (frozen dataclass)
        with self.assertRaises(Exception):
            snap_v1.version = 999  # type: ignore[misc]

        # Lock-free swap po přidání nové kauzální hrany
        snap_v2 = asyncio.run(
            repo.upsert_causal_edge("PSYCH", "LAW", weight=0.91, mechanism="cognitive_compliance")
        )
        self.assertIsNot(snap_v2, snap_v1)
        self.assertGreater(snap_v2.version, snap_v1.version)
        self.assertEqual(snap_v1.version, 1)
        self.assertEqual(repo.get_snapshot(), snap_v2)

        g3_res = repo.evaluate_g3_do_calculus("PSYCH", "LAW", intervention_value=0.95)
        self.assertTrue(g3_res["g3_passed"])
        self.assertEqual(g3_res["shortest_causal_path"][0], "PSYCH")
        self.assertEqual(g3_res["shortest_causal_path"][-1], "LAW")
        self.assertGreater(g3_res["post_intervention_p_y"], 0.50)

    def test_g3_do_calculus_fails_when_causal_path_is_disconnected(self):
        repo = AgeCausalGraphRepository()
        isolated_snapshot = repo.compile_snapshot_from_edges(
            nodes=list(DOMAINS_8D),
            edges=[("SYS", "SEC", 0.85, "telemetry_integrity")],
            source="isolated_test",
        )
        repo.swap_snapshot(isolated_snapshot)

        g3_connected = repo.evaluate_g3_do_calculus("SYS", "SEC", intervention_value=0.95)
        self.assertTrue(g3_connected["g3_passed"])
        self.assertEqual(g3_connected["shortest_causal_path"], ["SYS", "SEC"])

        g3_disconnected = repo.evaluate_g3_do_calculus("SYS", "LAW", intervention_value=0.95)
        self.assertFalse(g3_disconnected["g3_passed"])
        self.assertEqual(g3_disconnected["shortest_causal_path"], [])

    def test_load_from_postgres_opencypher_and_agtype_decoding(self):
        self.assertEqual(_parse_agtype('"SYS"'), "SYS")
        self.assertAlmostEqual(_parse_agtype("0.85::float"), 0.85)
        self.assertEqual(validate_domain_code("sys"), "SYS")
        with self.assertRaises(ValueError):
            validate_domain_code("SYS'}) MATCH (x) DETACH DELETE x //")

        dummy_conn = _DummyAsyncConn(
            node_codes=list(DOMAINS_8D),
            edge_rows=[
                ("SYS", "SEC", 0.92, "zero_trust_pipe"),
                ("SEC", "LAW", 0.88, "nis2_art21"),
            ],
        )
        repo = AgeCausalGraphRepository()
        with patch("backend.graph.age_repository.asyncpg") as mock_asyncpg:
            mock_asyncpg.connect = AsyncMock(return_value=dummy_conn)
            snapshot = asyncio.run(repo.load_from_postgres("postgresql+asyncpg://user:pass@localhost:5432/omnis"))

        self.assertEqual(snapshot.source, "apache_age")
        self.assertEqual(snapshot.edge_count, 2)
        self.assertTrue(any("ag_catalog.cypher('omnis_causal_graph'" in q for q in dummy_conn.executed_queries))
        g3 = repo.evaluate_g3_do_calculus("SYS", "LAW", intervention_value=0.95)
        self.assertTrue(g3["g3_passed"])
        self.assertEqual(g3["shortest_causal_path"], ["SYS", "SEC", "LAW"])
        self.assertEqual(g3["mechanisms"], ["zero_trust_pipe", "nis2_art21"])


class TestPhase2ZkAuditLedger(unittest.TestCase):
    def _compute_canonical(self, prev_hash: str, query: str, r_sys: float, tier: str, nonce: str, override: bool) -> str:
        q_hash = hashlib.sha256(query.encode("utf-8")).hexdigest()
        payload = f"OMNIS-ZK-PROOF||{prev_hash}||{q_hash}||{r_sys:.4f}||{tier}||{nonce}"
        if override:
            payload += "||OVERRIDE_ACTIVE"
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()

    def test_zk_ledger_chain_continuity_and_tamper_detection_pure_python(self):
        # Ověření nezávisle na přítomnosti externích pip balíčků (FastAPI / SQLAlchemy apod.)
        try:
            from backend.security.zk_ledger_service import ZkAuditLedgerService
        except ImportError:
            import importlib.util
            import sys
            mod_path = Path(__file__).resolve().parents[1] / "security" / "zk_ledger_service.py"
            spec = importlib.util.spec_from_file_location("zk_ledger_service_standalone", mod_path)
            mod = importlib.util.module_from_spec(spec)
            assert spec is not None and spec.loader is not None
            sys.modules[spec.name] = mod
            spec.loader.exec_module(mod)
            ZkAuditLedgerService = mod.ZkAuditLedgerService

        q1 = "Dotaz 1: Analýza NIS2"
        q2 = "Dotaz 2: Executive Override pro izolaci"
        h1 = self._compute_canonical("GENESIS", q1, 0.9123, "APPROVED", "nonce_01", False)
        h2 = self._compute_canonical(h1, q2, 0.8500, "OVERRIDE", "nonce_02", True)

        entries = [
            {
                "seq_id": 1,
                "prev_hash": "GENESIS",
                "query_hash": ZkAuditLedgerService.hash_query(q1),
                "r_systemic": 0.9123,
                "tier": "APPROVED",
                "nonce": "nonce_01",
                "override_active": False,
                "commitment_hash": h1,
            },
            {
                "seq_id": 2,
                "prev_hash": h1,
                "query_hash": ZkAuditLedgerService.hash_query(q2),
                "r_systemic": 0.8500,
                "tier": "OVERRIDE",
                "nonce": "nonce_02",
                "override_active": True,
                "commitment_hash": h2,
            },
        ]

        res_ok = ZkAuditLedgerService.verify_chain_continuity(entries)
        self.assertTrue(res_ok.valid)
        self.assertEqual(res_ok.total_entries, 2)
        self.assertEqual(res_ok.head_commitment_hash, h2)

        # Podvržení hodnoty r_systemic v prvním záznamu musí selhat okamžitě na seq_id=1
        tampered = [dict(entries[0]), dict(entries[1])]
        tampered[0]["r_systemic"] = 0.1111
        res_bad = ZkAuditLedgerService.verify_chain_continuity(tampered)
        self.assertFalse(res_bad.valid)
        self.assertEqual(res_bad.broken_at_seq_id, 1)

    def test_sqlite_append_only_immutability_triggers_and_init_sql_invariants(self):
        # 1. Ověření immutability triggerů přímo nad SQLite (stdlib sqlite3)
        conn = sqlite3.connect(":memory:")
        try:
            conn.execute(
                """
                CREATE TABLE zk_audit_ledger (
                    seq_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    prev_hash TEXT NOT NULL,
                    commitment_hash TEXT NOT NULL UNIQUE
                );
                """
            )
            conn.execute(
                """
                CREATE TRIGGER trg_sqlite_zk_audit_no_update
                BEFORE UPDATE ON zk_audit_ledger
                BEGIN
                    SELECT RAISE(ABORT, 'SECURITY_VIOLATION: zk_audit_ledger is strictly append-only; UPDATE is prohibited.');
                END;
                """
            )
            conn.execute(
                """
                CREATE TRIGGER trg_sqlite_zk_audit_no_delete
                BEFORE DELETE ON zk_audit_ledger
                BEGIN
                    SELECT RAISE(ABORT, 'SECURITY_VIOLATION: zk_audit_ledger is strictly append-only; DELETE is prohibited.');
                END;
                """
            )
            conn.execute(
                "INSERT INTO zk_audit_ledger (prev_hash, commitment_hash) VALUES ('GENESIS', 'abc123')"
            )
            with self.assertRaises(sqlite3.IntegrityError):
                conn.execute("UPDATE zk_audit_ledger SET commitment_hash = 'tampered' WHERE seq_id = 1")
            with self.assertRaises(sqlite3.IntegrityError):
                conn.execute("DELETE FROM zk_audit_ledger WHERE seq_id = 1")
        finally:
            conn.close()

        # 2. Ověření existence produkčních PG17 triggerů a advisory locku v backend/db/init.sql
        init_sql_path = Path(__file__).resolve().parents[1] / "db" / "init.sql"
        sql_text = init_sql_path.read_text(encoding="utf-8")
        self.assertIn("pg_advisory_xact_lock", sql_text)
        self.assertIn("BEFORE UPDATE OR DELETE ON public.zk_audit_ledger", sql_text)
        self.assertIn("BEFORE TRUNCATE ON public.zk_audit_ledger", sql_text)
        self.assertIn("ag_catalog.create_graph('omnis_causal_graph')", sql_text)

    def test_async_sqlalchemy_zk_ledger_append_verify_and_immutability(self):
        try:
            from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
            from backend.models import Base
            from backend.security.zk_ledger_service import ZkAuditLedgerService
        except ImportError:
            self.skipTest("SQLAlchemy/aiosqlite není v tomto běhu k dispozici.")

        async def _run():
            eng = create_async_engine("sqlite+aiosqlite:///:memory:")
            async with eng.begin() as conn:
                await conn.run_sync(Base.metadata.create_all)

            session_maker = async_sessionmaker(bind=eng, class_=AsyncSession, expire_on_commit=False)
            async with session_maker() as db:
                rec1 = await ZkAuditLedgerService.append_entry(
                    db,
                    user_id="op-1",
                    query="Iniciální systémový audit",
                    r_systemic=0.9200,
                    tier="APPROVED",
                    nonce="n111",
                )
                rec2 = await ZkAuditLedgerService.append_entry(
                    db,
                    user_id="op-1",
                    query="Sekundární override audit",
                    r_systemic=0.8100,
                    tier="OVERRIDE",
                    nonce="n222",
                    override_active=True,
                )
                self.assertEqual(rec1.prev_hash, "GENESIS")
                self.assertEqual(rec2.prev_hash, rec1.commitment_hash)

                verification = await ZkAuditLedgerService.verify_database_chain(db)
                self.assertTrue(verification.valid)
                self.assertEqual(verification.total_entries, 2)
                self.assertEqual(verification.head_commitment_hash, rec2.commitment_hash)

                from sqlalchemy import text
                with self.assertRaises(Exception):
                    await db.execute(text("UPDATE zk_audit_ledger SET tier = 'TAMPERED' WHERE seq_id = 1"))
                await db.rollback()
                with self.assertRaises(Exception):
                    await db.execute(text("DELETE FROM zk_audit_ledger WHERE seq_id = 1"))
                await db.rollback()

            await eng.dispose()

        asyncio.run(_run())


@unittest.skipUnless(
    os.getenv("RUN_PG_INTEGRATION_TESTS") == "1" and bool(os.getenv("DATABASE_URL")),
    "Vyžaduje běžící PostgreSQL 17 s Apache AGE, pgvector a pgcrypto (RUN_PG_INTEGRATION_TESTS=1).",
)
class TestPhase2Postgres17LiveIntegration(unittest.TestCase):
    def test_live_pg17_age_graph_and_zk_ledger_triggers(self):
        from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
        from sqlalchemy import text
        from backend.security.zk_ledger_service import ZkAuditLedgerService

        db_url = os.environ["DATABASE_URL"]

        async def _run_pg():
            repo = AgeCausalGraphRepository()
            snap = await repo.load_from_postgres(db_url)
            self.assertEqual(snap.source, "apache_age")
            g3 = repo.evaluate_g3_do_calculus("SYS", "LAW", intervention_value=0.95)
            self.assertTrue(g3["g3_passed"])

            eng = create_async_engine(db_url)
            session_maker = async_sessionmaker(bind=eng, class_=AsyncSession, expire_on_commit=False)
            async with session_maker() as db:
                r1 = await ZkAuditLedgerService.append_entry(
                    db,
                    user_id="pg-ci-user",
                    query="PG17 live immutability check",
                    r_systemic=0.9500,
                    tier="APPROVED",
                )
                with self.assertRaises(Exception):
                    await db.execute(
                        text("DELETE FROM public.zk_audit_ledger WHERE seq_id = :seq_id"),
                        {"seq_id": r1.seq_id},
                    )
                await db.rollback()
            await eng.dispose()

        asyncio.run(_run_pg())


if __name__ == "__main__":
    unittest.main()
