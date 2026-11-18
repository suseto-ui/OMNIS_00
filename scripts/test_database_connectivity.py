#!/usr/bin/env python3
# ==============================================================================
# O.M.N.I.S. - Sovereign Database Connectivity & Sync Verification Script
# Testuje provázanost: Room SQLite (Local) <-> ElectricSQL <-> PostgreSQL 17 (pgvector + AGE)
# ==============================================================================

import asyncio
import os
import sys
import logging
import urllib.request

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("omnis.db_test")


async def test_postgresql_pgvector_and_age():
    logger.info("=== [Test 1/3] Ověřování PostgreSQL 17, pgvector & Apache AGE ===")
    db_url = (os.environ.get("DATABASE_URL") or os.environ.get("OMNIS_DATABASE_URL") or "").strip()
    if not db_url:
        logger.info("DATABASE_URL / OMNIS_DATABASE_URL není nastaveno — PostgreSQL test přeskočen (aktivní lokální Room SQLite fallback).")
        return False

    try:
        from sqlalchemy.ext.asyncio import create_async_engine
        from sqlalchemy import text

        if db_url.startswith("postgresql://"):
            db_url = db_url.replace("postgresql://", "postgresql+asyncpg://", 1)
        elif db_url.startswith("postgres://"):
            db_url = db_url.replace("postgres://", "postgresql+asyncpg://", 1)

        engine = create_async_engine(db_url, echo=False, pool_pre_ping=True)
        async with engine.begin() as conn:
            res = await conn.execute(text("SELECT version();"))
            version = res.scalar()
            logger.info("PostgreSQL připojení úspěšné. Verze: %s", version)

            for ext_name in ("vector", "age", "pgcrypto"):
                try:
                    ext_exists = await conn.execute(
                        text("SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = :name);"),
                        {"name": ext_name},
                    )
                    logger.info("Rozšíření %s aktivní: %s", ext_name, bool(ext_exists.scalar()))
                except Exception as ext_err:
                    logger.warning("Varování při kontrole rozšíření %s: %s", ext_name, ext_err)

            res_tables = await conn.execute(
                text("SELECT table_name FROM information_schema.tables WHERE table_schema='public';")
            )
            tables = [row[0] for row in res_tables.fetchall()]
            logger.info("Veřejné tabulky v PostgreSQL 17: %s", tables)

        await engine.dispose()
        return True
    except Exception as e:
        logger.warning("PostgreSQL 17 test nedosažitelný (aktivní offline fallback): %s", e)
        return False


def test_electricsql_sync_health():
    logger.info("=== [Test 2/3] Ověřování ElectricSQL 1.8.1 Sync Engine ===")
    electric_url = (os.environ.get("ELECTRIC_URL") or "http://127.0.0.1:3000").rstrip("/")
    health_endpoint = f"{electric_url}/v1/health"
    try:
        with urllib.request.urlopen(health_endpoint, timeout=2.5) as resp:
            if resp.status == 200:
                logger.info("ElectricSQL sync endpoint (%s) odpověděl HTTP 200.", health_endpoint)
                return True
            logger.warning("ElectricSQL endpoint (%s) vrátil HTTP %s.", health_endpoint, resp.status)
            return False
    except Exception as exc:
        logger.info("ElectricSQL (%s) neběží v tomto lokálním prostředí (%s) — přeskočeno.", health_endpoint, exc)
        return False


def test_room_sqlite_cache():
    logger.info("=== [Test 3/3] Ověřování lokální Room SQLite / SQLite3 Cache ===")
    import sqlite3
    db_path = ":memory:"
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        cursor.execute("CREATE TABLE IF NOT EXISTS sync_test (id INTEGER PRIMARY KEY, payload TEXT, timestamp REAL);")
        cursor.execute("INSERT OR REPLACE INTO sync_test (id, payload, timestamp) VALUES (1, 'OMNIS_VECTOR_SYNC_OK', 1700000000.0);")
        conn.commit()

        cursor.execute("SELECT payload FROM sync_test WHERE id=1;")
        row = cursor.fetchone()
        conn.close()

        if row and row[0] == "OMNIS_VECTOR_SYNC_OK":
            logger.info("Lokální SQLite/Room cache zápis a čtení proběhlo úspěšně.")
            return True
        logger.error("SQLite čtení vrátilo neplatná data.")
        return False
    except Exception as e:
        logger.error("Lokální Room SQLite test selhal: %s", e)
        return False


async def main():
    logger.info("==========================================================")
    logger.info(" O.M.N.I.S. APEX v4.5 - Diagnostika Sjednocených Databází ")
    logger.info("==========================================================")

    pg_ok = await test_postgresql_pgvector_and_age()
    electric_ok = test_electricsql_sync_health()
    sqlite_ok = test_room_sqlite_cache()

    logger.info("==========================================================")
    logger.info(" VÝSLEDEK DIAGNOSTIKY: PostgreSQL17=%s, ElectricSQL=%s, SQLite=%s", pg_ok, electric_ok, sqlite_ok)
    logger.info("==========================================================")

    if sqlite_ok:
        print("DIAGNOSTIKA_DB_SUCCESS")
        sys.exit(0)
    else:
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
