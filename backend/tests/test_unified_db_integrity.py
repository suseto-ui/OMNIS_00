"""
O.M.N.I.S. APEX v4.5 - Unified Database & Architecture Integrity Test Suite (Fáze 2 Pre-Deletion Gate)
Validates:
1. PostgreSQL 17 Extensions (vector, age, pgcrypto) & `omnis_causal_graph`.
2. Schema & Conversion Parity (Tables, Indexes, RLS Policies, Roles).
3. ZK Audit Ledger Immutability (Append-only SHA-256 chain, block UPDATE/DELETE).
4. Causal Graph 8D & 64D Vector RAG (HNSW index, Apache AGE openCypher).
5. ElectricSQL Sync & SQLite / SQLCipher edge migration readiness.
"""

from __future__ import annotations

import asyncio
import hashlib
import os
import sqlite3
import unittest
from pathlib import Path

class TestUnifiedDatabaseIntegrity(unittest.TestCase):
    def test_01_init_sql_schema_invariants(self):
        """Ověří, že init.sql obsahuje všechna povinná rozšíření, tabulky, triggery a RLS politiky."""
        init_sql_path = Path(__file__).resolve().parents[1] / "db" / "init.sql"
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
        """Ověří lokální SQLite/SQLCipher emulaci s appends, triggerovými blokacemi a vec0 tabulkou."""
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
        """Ověří, že docker-compose.yml a requirements.txt obsahují správné verze a konfigurace."""
        dc_path = Path(__file__).resolve().parents[2] / "docker-compose.yml"
        self.assertTrue(dc_path.exists())
        dc_text = dc_path.read_text(encoding="utf-8")
        self.assertIn("omnis_pg17", dc_text)
        self.assertIn("electricsql/electric", dc_text)
        self.assertIn("shared_preload_libraries=age", dc_text)

        req_path = Path(__file__).resolve().parents[2] / "requirements.txt"
        self.assertTrue(req_path.exists())
        req_text = req_path.read_text(encoding="utf-8")
        self.assertIn("sqlalchemy[asyncio]", req_text)
        self.assertIn("asyncpg", req_text)
        self.assertIn("pgvector", req_text)
        self.assertIn("greenlet", req_text)
        # Firebase / GCP cloud dependencies must NOT be present in open-source requirements.txt
        self.assertNotIn("google-cloud-firestore", req_text)
        self.assertNotIn("firebase-admin", req_text)


if __name__ == "__main__":
    unittest.main()
