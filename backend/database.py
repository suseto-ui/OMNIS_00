from __future__ import annotations
import logging
import os
import urllib.parse
from typing import AsyncGenerator, Iterable, Optional, Dict, Any, List
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from backend.models import Base
from sqlalchemy import text
from backend.gcp_secrets import get_secret

logger = logging.getLogger("omnis.db")

# Primární cloudové databázové úložiště je Firebase Firestore (google-cloud-firestore).
# SQLAlchemy a relační schémata jsou udržována v in-memory režimu pro zpětnou kompatibilitu modelů.
DATABASE_URL = None
raw_db_url = (get_secret("DATABASE_URL") or get_secret("OMNIS_DATABASE_URL") or "").strip()
if raw_db_url.startswith(("sqlite", "postgresql://", "postgresql+asyncpg://", "postgres://")):
    DATABASE_URL = raw_db_url
elif raw_db_url:
    logger.warning("Nepodporovaný formát DATABASE_URL; databáze poběží v mock režimu.")
else:
    logger.info("DATABASE_URL není nastaveno; databázová vrstva využije mock session nebo Firestore.")


class _QueryResult:
    def __init__(self, rows: Optional[Iterable] = None):
        self._rows = list(rows or [])

    def scalar_one_or_none(self):
        return self._rows[0] if self._rows else None

    def all(self):
        return list(self._rows)

    def scalars(self):
        return self

    def __iter__(self):
        return iter(self._rows)


class _MockSession:
    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        return False

    def add(self, obj):
        return None

    def add_all(self, objs):
        return None

    async def commit(self):
        return None

    async def rollback(self):
        return None

    async def flush(self):
        return None

    async def get(self, model, ident):
        return None

    async def execute(self, *args, **kwargs):
        return _QueryResult()

    def __getattr__(self, name):
        return None


class _NullSessionFactory:
    def __call__(self):
        return _MockSession()


engine = None
async_session_factory = None

if DATABASE_URL:
    logger.info("Database URL detected. Preparing Google Cloud SQL connection pool...")
    try:
        url = DATABASE_URL
        if url.startswith("postgresql://"):
            url = url.replace("postgresql://", "postgresql+asyncpg://", 1)
        elif url.startswith("postgres://"):
            url = url.replace("postgres://", "postgresql+asyncpg://", 1)

        engine_kwargs = {
            "echo": False,
            "pool_pre_ping": True,
            "pool_recycle": 300,  # 5 min recycle for Cloud SQL TCP stability
        }

        if "sqlite" not in url:
            engine_kwargs["pool_size"] = 10
            engine_kwargs["max_overflow"] = 10
            engine_kwargs["pool_timeout"] = 30

            import ssl
            connect_args = {}
            allow_self_signed = os.getenv("DB_SSL_ALLOW_SELF_SIGNED", "").lower() in ("1", "true", "yes")
            if os.getenv("DB_SSL_CA"):
                ssl_ctx = ssl.create_default_context(cafile=os.getenv("DB_SSL_CA"))
                connect_args["ssl"] = ssl_ctx
            elif "neon.tech" in url or "sslmode=require" in url or "ssl=require" in url:
                ssl_ctx = ssl.create_default_context()
                if allow_self_signed:
                    ssl_ctx.check_hostname = False
                    ssl_ctx.verify_mode = ssl.CERT_NONE
                connect_args["ssl"] = ssl_ctx

            if connect_args:
                engine_kwargs["connect_args"] = connect_args

        engine = create_async_engine(url, **engine_kwargs)

        async_session_factory = async_sessionmaker(
            bind=engine,
            class_=AsyncSession,
            expire_on_commit=False,
        )
        logger.info("PostgreSQL AsyncEngine configured with asyncpg connection pool.")
    except Exception as e:
        logger.error(
            f"Failed to create async database engine: {e}. Falling back to mock session."
        )
        async_session_factory = _NullSessionFactory()
else:
    logger.info(
        "No DATABASE_URL environment variable found. Falling back to in-memory mock session."
    )
    async_session_factory = _NullSessionFactory()


class _SyncMockSession:
    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def query(self, *args, **kwargs):
        return _QueryResult()

    def add(self, *args, **kwargs):
        pass

    def commit(self):
        pass

    def rollback(self):
        pass


def SessionLocal():
    return _SyncMockSession()


async def init_db():
    if engine is not None:
        logger.info("Initializing Google Cloud SQL database schema...")
        try:
            async with engine.begin() as conn:
                try:
                    await conn.execute(text("CREATE EXTENSION IF NOT EXISTS vector;"))
                    logger.info("pgvector extension installed/verified.")
                except Exception as ext_err:
                    logger.warning(
                        f"Could not create vector extension (might be already installed or insufficient privileges): {ext_err}"
                    )

                await conn.run_sync(Base.metadata.create_all)

                dialect_name = conn.dialect.name
                # Bezpečná migrace sloupce is_approved pro existující databáze (PostgreSQL i SQLite)
                try:
                    if dialect_name == "sqlite":
                        pragma_res = await conn.execute(text("PRAGMA table_info(users)"))
                        existing_cols = {row[1] for row in pragma_res.fetchall()}
                        if "is_approved" not in existing_cols:
                            await conn.execute(text("ALTER TABLE users ADD COLUMN is_approved BOOLEAN DEFAULT 0"))
                    else:
                        await conn.execute(text(
                            "ALTER TABLE users ADD COLUMN IF NOT EXISTS is_approved BOOLEAN DEFAULT FALSE"
                        ))
                        await conn.execute(text(
                            "ALTER TABLE vector_memories ADD COLUMN IF NOT EXISTS embedding_64 vector(64)"
                        ))
                        await conn.execute(text(
                            "ALTER TABLE omnis_memory_records ADD COLUMN IF NOT EXISTS embedding_64 vector(64)"
                        ))
                        await conn.execute(text(
                            "ALTER TABLE omnis_messages ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128)"
                        ))
                        await conn.execute(text(
                            "ALTER TABLE omnis_memory_records ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128)"
                        ))
                except Exception as mig_err:
                    logger.warning(f"Migrace sloupců přeskočena: {mig_err}")

                # Migrace pro hlavního vlastníka (role + automatické schválení)
                await conn.execute(text(
                    "UPDATE users SET role = 'ADMIN_OPERATOR', is_approved = TRUE WHERE LOWER(email) = 'susetofu@gmail.com'"
                ))

            # Concurrent HNSW 64D indexy mimo transakci (vyžadováno PostgreSQL AUTOCOMMIT)
            if engine.dialect.name != "sqlite":
                try:
                    async with engine.connect() as idx_conn:
                        autocommit_conn = await idx_conn.execution_options(isolation_level="AUTOCOMMIT")
                        await autocommit_conn.execute(text(
                            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_vector_memories_hnsw_64 "
                            "ON vector_memories USING hnsw (embedding_64 vector_cosine_ops) "
                            "WITH (m = 16, ef_construction = 64);"
                        ))
                        await autocommit_conn.execute(text(
                            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_omnis_memory_hnsw_64 "
                            "ON omnis_memory_records USING hnsw (embedding_64 vector_cosine_ops) "
                            "WITH (m = 16, ef_construction = 64);"
                        ))
                except Exception as idx_err:
                    logger.warning(f"Concurrent HNSW 64D index creation skipped: {idx_err}")

            logger.info("Database schema tables created/verified and admin migration applied.")
        except Exception as err:
            logger.error(f"Database schema initialization failed: {err}")
            raise err
    else:
        logger.info("Mock database environment: init_db is a no-op.")


async def get_db() -> AsyncGenerator:
    """Provide either a real SQLAlchemy AsyncSession or a mock fallback session."""
    if engine is not None and async_session_factory is not None:
        try:
            async with async_session_factory() as session:
                try:
                    yield session
                except Exception as e:
                    logger.error(f"Error during database session operation: {e}")
                    await session.rollback()
                    raise
                finally:
                    await session.close()
        except Exception as conn_err:
            logger.warning(f"Database connection unavailable, falling back to mock session: {conn_err}")
            yield _MockSession()
    else:
        yield _MockSession()


def get_pool_status() -> Dict[str, Any]:
    """Vrací diagnostiku stavu connection poolu pro /api/v1/health."""
    if engine is None:
        return {
            "driver": "mock",
            "status": "in_memory_emulation",
            "pool_size": 0,
            "checked_in": 0,
            "checked_out": 0,
            "overflow": 0,
        }

    try:
        pool = engine.pool
        return {
            "driver": "postgresql+asyncpg",
            "status": "connected",
            "pool_size": getattr(pool, "size", lambda: 20)(),
            "checked_in": getattr(pool, "checkedin", lambda: 0)(),
            "checked_out": getattr(pool, "checkedout", lambda: 0)(),
            "overflow": getattr(pool, "overflow", lambda: 0)(),
        }
    except Exception as e:
        return {
            "driver": "postgresql+asyncpg",
            "status": "error",
            "error": str(e),
        }


# ==========================================================
# Hybridní vyhledávání (HNSW Cosine Distance + RRF Fusion)
# ==========================================================

async def hybrid_search_knowledge(
    query_text: str,
    query_embedding: Optional[List[float]] = None,
    limit: int = 5,
    rrf_k: int = 60,
    db: Optional[AsyncSession] = None,
) -> List[Dict[str, Any]]:
    """
    Hybridní vyhledávání kombinující HNSW kosinovou vzdálenost (pgvector) a Reciprocal Rank Fusion (RRF).
    Vzorec: RRF_score(d) = sum_{m in {dense, sparse}} (1 / (rrf_k + rank_m(d)))
    """
    results_map: Dict[str, Dict[str, Any]] = {}
    dense_ranks: Dict[str, int] = {}
    sparse_ranks: Dict[str, int] = {}

    if db is not None and not isinstance(db, _MockSession) and engine is not None:
        try:
            # 1. HNSW kosinová vzdálenost (Dense retrieval)
            if query_embedding:
                import json
                query_vec_str = json.dumps(query_embedding)
                if len(query_embedding) == 64:
                    try:
                        await db.execute(text("SET LOCAL hnsw.ef_search = 40;"))
                    except Exception:
                        pass
                    dense_sql = text("""
                        SELECT id, content, memory_type, importance_score, metadata_json,
                               (embedding_64 <=> CAST(:query_vec AS vector)) as cosine_distance
                        FROM vector_memories
                        WHERE embedding_64 IS NOT NULL
                        ORDER BY cosine_distance ASC
                        LIMIT :search_limit;
                    """)
                else:
                    dense_sql = text("""
                        SELECT id, content, memory_type, importance_score, metadata_json,
                               (CAST(embedding AS text)::vector <=> CAST(:query_vec AS vector)) as cosine_distance
                        FROM vector_memories
                        WHERE embedding IS NOT NULL
                        ORDER BY cosine_distance ASC
                        LIMIT :search_limit;
                    """)
                dense_res = await db.execute(dense_sql, {"query_vec": query_vec_str, "search_limit": limit * 2})
                for rank, row in enumerate(dense_res.fetchall(), start=1):
                    doc_id = str(row[0])
                    dense_ranks[doc_id] = rank
                    if doc_id not in results_map:
                        results_map[doc_id] = {
                            "id": doc_id,
                            "content": row[1],
                            "memory_type": row[2],
                            "importance_score": float(row[3] or 1.0),
                            "metadata": row[4] or {},
                            "cosine_distance": float(row[5] or 0.0),
                        }

            # 2. Textové / BM25 vyhledávání (Sparse retrieval)
            sparse_sql = text("""
                SELECT id, content, memory_type, importance_score, metadata_json
                FROM vector_memories
                WHERE content ILIKE :pattern
                ORDER BY importance_score DESC
                LIMIT :search_limit;
            """)
            sparse_res = await db.execute(sparse_sql, {"pattern": f"%{query_text}%", "search_limit": limit * 2})
            for rank, row in enumerate(sparse_res.fetchall(), start=1):
                doc_id = str(row[0])
                sparse_ranks[doc_id] = rank
                if doc_id not in results_map:
                    results_map[doc_id] = {
                        "id": doc_id,
                        "content": row[1],
                        "memory_type": row[2],
                        "importance_score": float(row[3] or 1.0),
                        "metadata": row[4] or {},
                        "cosine_distance": 0.5,
                    }
        except Exception as query_err:
            logger.warning(f"Chyba při pgvector hybridním dotazování: {query_err}. Použije se záložní syntéza.")

    # 3. Fallback pokud databáze nevrátila výsledky (např. prázdná DB nebo mock režim)
    if not results_map:
        fallback_docs = [
            {
                "id": f"mem-core-{i+1}",
                "content": f"O.M.N.I.S. AKGE-8D Invariant: {doc}",
                "memory_type": "epistemic_axiom",
                "importance_score": 0.95 - (i * 0.1),
                "metadata": {"domain": "SYSTEMS_INTELLIGENCE", "source": "bootstrap"},
                "cosine_distance": 0.05 * (i + 1),
            }
            for i, doc in enumerate([
                "Transdisciplinární invariance 8D tenzoru a nulová entropická tolerance.",
                "Deterministický HMAC-SHA256 auditní řetězec zaručuje nevratnost systémových rozhodnutí.",
                "Do-Calculus intervence P(Y|do(X=x)) odlišuje reálnou kauzalitu od korelací.",
            ])
        ]
        for rank, item in enumerate(fallback_docs, start=1):
            doc_id = item["id"]
            dense_ranks[doc_id] = rank
            sparse_ranks[doc_id] = rank
            results_map[doc_id] = item

    # 4. Aplikace Reciprocal Rank Fusion (RRF)
    # RRF(d) = sum_{m} 1 / (rrf_k + rank_m(d))
    scored_results = []
    for doc_id, doc in results_map.items():
        dense_rank = dense_ranks.get(doc_id, 1000)
        sparse_rank = sparse_ranks.get(doc_id, 1000)
        rrf_score = (1.0 / (rrf_k + dense_rank)) + (1.0 / (rrf_k + sparse_rank))
        doc["rrf_score"] = round(rrf_score, 6)
        scored_results.append(doc)

    scored_results.sort(key=lambda x: x["rrf_score"], reverse=True)
    return scored_results[:limit]

