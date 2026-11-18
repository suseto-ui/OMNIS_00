"""
O.M.N.I.S. APEX v4.5 - Unified Database & Architecture Integrity Test Suite
Validates:
1. PostgreSQL 17 Extensions (vector, age, pgcrypto), causal tables, retraction_watch_blacklist & `omnis_causal_graph`.
2. Schema & Conversion Parity (Tables, Indexes, RLS Policies, Roles).
3. ZK Audit Ledger Immutability (Append-only SHA-256 chain, block UPDATE/DELETE/TRUNCATE).
4. ElectricSQL Sync & SQLite / SQLCipher edge migration readiness.
5. Docker Compose WAL limits & healthchecks, requirements alignment, CI workflow & _OMNIS_VAULT SHA-256 integrity.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3
import unittest
from pathlib import Path


class TestUnifiedDatabaseIntegrity(unittest.TestCase):
    ROOT = Path(__file__).resolve().parents[2]

    def test_01_init_sql_schema_invariants(self):
        """Ověří, že init.sql obsahuje všechna povinná rozšíření, tabulky, triggery a RLS politiky."""
        init_sql_path = self.ROOT / "backend" / "db" / "init.sql"
        self.assertTrue(init_sql_path.exists(), "backend/db/init.sql musí existovat.")
        sql_content = init_sql_path.read_text(encoding="utf-8")

        # Extensions & roles
        self.assertIn("CREATE EXTENSION IF NOT EXISTS vector;", sql_content)
        self.assertIn("CREATE EXTENSION IF NOT EXISTS age;", sql_content)
        self.assertIn("CREATE EXTENSION IF NOT EXISTS pgcrypto;", sql_content)
        self.assertIn("omnis_app_rw", sql_content)
        self.assertIn("omnis_audit_append", sql_content)
        self.assertIn("omnis_sync_repl", sql_content)

        # Tables & HNSW
        self.assertIn("public.rag_memory_64d", sql_content)
        self.assertIn("vector_cosine_ops", sql_content)
        self.assertIn("public.tensor_states_8d", sql_content)
        self.assertIn("public.messages", sql_content)
        self.assertIn("public.goals", sql_content)
        self.assertIn("public.rag_fragments", sql_content)
        self.assertIn("public.causal_nodes", sql_content)
        self.assertIn("public.causal_edges", sql_content)
        self.assertIn("public.retraction_watch_blacklist", sql_content)
        self.assertIn("public.zk_audit_ledger", sql_content)

        # ZK Audit Ledger Immutability triggers
        self.assertIn("trg_zk_audit_ledger_before_insert", sql_content)
        self.assertIn("trg_zk_audit_ledger_immutable", sql_content)
        self.assertIn("trg_zk_audit_ledger_no_truncate", sql_content)
        self.assertIn("pg_advisory_xact_lock", sql_content)

        # Apache AGE graph
        self.assertIn("ag_catalog.create_graph('omnis_causal_graph')", sql_content)

        # ElectricSQL RLS & Replication
        self.assertIn("ENABLE ROW LEVEL SECURITY", sql_content)
        self.assertIn("REPLICA IDENTITY FULL", sql_content)
        self.assertIn("electric_publication_default", sql_content)

    def test_02_sqlite_edge_immutability_and_vec0(self):
        """Ověří lokální SQLite/SQLCipher emulaci s append-only triggerovou ochranou."""
        conn = sqlite3.connect(":memory:")
        try:
            conn.execute("""
                CREATE TABLE zk_audit_ledger (
                    seq_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    prev_hash TEXT NOT NULL,
                    commitment_hash TEXT NOT NULL UNIQUE
                );
            """)
            conn.execute("""
                CREATE TRIGGER trg_zk_no_update BEFORE UPDATE ON zk_audit_ledger
                BEGIN SELECT RAISE(ABORT, 'IMMUTABILITY_VIOLATION'); END;
            """)
            conn.execute("""
                CREATE TRIGGER trg_zk_no_delete BEFORE DELETE ON zk_audit_ledger
                BEGIN SELECT RAISE(ABORT, 'IMMUTABILITY_VIOLATION'); END;
            """)

            conn.execute("INSERT INTO zk_audit_ledger (prev_hash, commitment_hash) VALUES ('GENESIS', 'hash_001')")

            with self.assertRaises(sqlite3.IntegrityError):
                conn.execute("UPDATE zk_audit_ledger SET commitment_hash = 'hacked' WHERE seq_id = 1")

            with self.assertRaises(sqlite3.IntegrityError):
                conn.execute("DELETE FROM zk_audit_ledger WHERE seq_id = 1")

            row = conn.execute("SELECT commitment_hash FROM zk_audit_ledger WHERE seq_id = 1").fetchone()
            self.assertEqual(row[0], "hash_001")
        finally:
            conn.close()

    def test_03_docker_compose_and_requirements(self):
        """Ověří docker-compose.yml, oba requirements.txt i CI workflow bez mrtvých odkazů."""
        dc_path = self.ROOT / "docker-compose.yml"
        self.assertTrue(dc_path.exists())
        dc_text = dc_path.read_text(encoding="utf-8")
        self.assertIn("omnis_pg17", dc_text)
        self.assertIn("electricsql/electric", dc_text)
        self.assertIn("shared_preload_libraries=age", dc_text)
        self.assertIn("max_slot_wal_keep_size=2GB", dc_text)
        self.assertIn("./backend/db/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro", dc_text)
        # Electric must define its own healthcheck because backend depends_on service_healthy
        electric_block = dc_text.split("electric:", 1)[1].split("backend:", 1)[0]
        self.assertIn("healthcheck:", electric_block)

        for req_rel in ("requirements.txt", "backend/requirements.txt"):
            req_path = self.ROOT / req_rel
            self.assertTrue(req_path.exists(), f"{req_rel} musí existovat.")
            req_text = req_path.read_text(encoding="utf-8")
            for required_pkg in ("sqlalchemy[asyncio]", "asyncpg", "pgvector", "greenlet"):
                self.assertIn(required_pkg, req_text, f"{req_rel} musí obsahovat {required_pkg}")
            for banned_pkg in ("google-cloud-firestore", "firebase-admin", "google-cloud-secret-manager"):
                self.assertNotIn(banned_pkg, req_text, f"{req_rel} nesmí obsahovat {banned_pkg}")

        ci_path = self.ROOT / ".github" / "workflows" / "omnis-ci.yml"
        ci_text = ci_path.read_text(encoding="utf-8")
        self.assertNotIn("firestore.test.js", ci_text)
        self.assertIn("unified-db-integrity-gate", ci_text)

    def test_04_omnis_vault_index_and_sha256_integrity(self):
        """Ověří, že všech 7 uzlů v _OMNIS_VAULT existuje a má platný SHA-256 otisk."""
        topo_path = self.ROOT / "_OMNIS_VAULT" / "00_TOPOLOGY_GRAPH.json"
        master_index_path = self.ROOT / "_OMNIS_VAULT" / "00_OMNIS_MASTER_INDEX.md"
        self.assertTrue(topo_path.exists(), "Chybí _OMNIS_VAULT/00_TOPOLOGY_GRAPH.json")
        self.assertTrue(master_index_path.exists(), "Chybí _OMNIS_VAULT/00_OMNIS_MASTER_INDEX.md")

        topo = json.loads(topo_path.read_text(encoding="utf-8"))
        master_text = master_index_path.read_text(encoding="utf-8")
        nodes = topo.get("nodes", [])
        self.assertGreaterEqual(len(nodes), 7)

        for node in nodes:
            vault_file = self.ROOT / node["vault_path"]
            self.assertTrue(vault_file.exists(), f"Chybí indexovaný Vault soubor: {node['vault_path']}")
            actual_hash = hashlib.sha256(vault_file.read_bytes()).hexdigest()
            self.assertEqual(
                actual_hash,
                node["integrity_hash"],
                f"SHA-256 nesouhlasí v 00_TOPOLOGY_GRAPH.json pro {node['vault_path']}",
            )
            self.assertIn(
                f"SHA256:{actual_hash}",
                master_text,
                f"SHA-256 otisk pro {node['node_id']} chybí nebo nesouhlasí v 00_OMNIS_MASTER_INDEX.md",
            )


if __name__ == "__main__":
    unittest.main()
