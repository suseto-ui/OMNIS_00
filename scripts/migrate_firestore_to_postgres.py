#!/usr/bin/env python3
"""
O.M.N.I.S. APEX v4.5 - Firestore to PostgreSQL 17 Migration Script
Idempotentně přenáší existující data z Firebase Firestore do PostgreSQL 17 / pgvector
před definitivním odpojením starého backendu.
"""

from __future__ import annotations

import os
import sys
import logging
import asyncio
import json
from typing import Any, Dict, List

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("omnis.migration.firestore_to_pg")

try:
    from google.cloud import firestore
except ImportError:
    firestore = None

try:
    import asyncpg
except ImportError:
    asyncpg = None


async def migrate_data() -> None:
    database_url = os.getenv("DATABASE_URL") or os.getenv("OMNIS_DATABASE_URL")
    firestore_db_id = os.getenv("FIRESTORE_DATABASE_ID", "ai-studio-android-omnis-fba4ba28-3ec2-4eaf-a1a7-338554af8dfe")

    if not database_url:
        logger.error("ERROR: DATABASE_URL není nastaveno. Nelze provést migraci do PostgreSQL.")
        sys.exit(1)

    if firestore is None:
        logger.error("ERROR: Google Cloud Firestore knihovna není nainstalována.")
        sys.exit(1)

    if asyncpg is None:
        logger.error("ERROR: asyncpg ovladač není nainstalován.")
        sys.exit(1)

    logger.info("Zahajuji migraci z Firestore databáze '%s' do PostgreSQL...", firestore_db_id)
    fs_client = firestore.Client(database=firestore_db_id)
    pg_dsn = database_url
    if pg_dsn.startswith("postgresql+asyncpg://"):
        pg_dsn = "postgresql://" + pg_dsn[len("postgresql+asyncpg://"):]

    conn = await asyncpg.connect(pg_dsn)
    try:
        # 1. Migrace relací / konverzací (omnis_sessions -> messages)
        sessions_ref = fs_client.collection("omnis_sessions").stream()
        sessions_migrated = 0
        for doc in sessions_ref:
            data = doc.to_dict() or {}
            session_id = data.get("id") or doc.id
            user_id = data.get("user_id") or data.get("userId") or "operator"
            title = data.get("title", "Synchronizované Vlákno")
            domain = data.get("ontology_domain") or data.get("domain") or "SYSTEMS_INTELLIGENCE"

            # Migrovat zprávy ve vlákně
            messages_ref = fs_client.collection("omnis_sessions").document(doc.id).collection("messages").stream()
            for m_doc in messages_ref:
                m_data = m_doc.to_dict() or {}
                msg_id = m_data.get("id") or m_doc.id
                # Převod UUID stringu na bigint pro kompatibilitu s omnis_messages
                try:
                    num_id = int(msg_id)
                except ValueError:
                    num_id = (abs(hash(msg_id)) % (2**63 - 1))

                await conn.execute(
                    """
                    INSERT INTO public.messages
                        (id, user_id, thread_id, thread_title, role, content, cognitive_process, domain, composite_score, idempotency_key, timestamp_ms)
                    VALUES
                        (gen_random_uuid(), $1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
                    ON CONFLICT (idempotency_key) DO NOTHING;
                    """,
                    str(user_id),
                    str(session_id),
                    str(title),
                    str(m_data.get("role", "user")),
                    str(m_data.get("content", "")),
                    str(m_data.get("cognitive_thoughts", "")),
                    str(domain),
                    float(m_data.get("composite_score", 0.5)),
                    f"fs_migrated_{msg_id}",
                    int(m_data.get("timestamp", 1700000000000)),
                )
                sessions_migrated += 1

        logger.info("Úspěšně migrováno %d zpráv/relací ze Firestore do PostgreSQL.", sessions_migrated)

        # 2. Migrace RAG fragmentů (proof_of_necessity / vector_memories -> rag_memory_64d)
        pon_ref = fs_client.collection("proof_of_necessity").stream()
        pon_migrated = 0
        for doc in pon_ref:
            data = doc.to_dict() or {}
            commitment = data.get("auditCommitment", {}).get("commitment_hash") or doc.id
            caller_uid = data.get("callerUid", "operator")
            pon_obj = data.get("proofOfNecessity", {})
            content = f"PoN: {pon_obj.get('trigger_vector', '')} | {pon_obj.get('exclusion_proof', '')}"
            dummy_vec = [0.01] * 64

            await conn.execute(
                """
                INSERT INTO public.rag_memory_64d
                    (user_id, thread_id, domain, content, embedding, importance_score, metadata_json, idempotency_key)
                VALUES
                    ($1, 'thread_migration', 'SYSTEMS_INTELLIGENCE', $2, CAST($3 AS vector(64)), 1.0, CAST($4 AS jsonb), $5)
                ON CONFLICT (idempotency_key) DO NOTHING;
                """,
                str(caller_uid),
                content,
                json.dumps(dummy_vec),
                json.dumps(data),
                f"pon_migrated_{commitment}",
            )
            pon_migrated += 1

        logger.info("Úspěšně migrováno %d Proof of Necessity záznamů do rag_memory_64d.", pon_migrated)
        logger.info("Migrace z Firestore do PostgreSQL 17 proběhla úspěšně.")
    finally:
        await conn.close()


if __name__ == "__main__":
    asyncio.run(migrate_data())
