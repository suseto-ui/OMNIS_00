from __future__ import annotations
import logging
import os
import urllib.parse
from typing import AsyncGenerator, Iterable, Optional, Dict, Any, List
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from backend.models import Base
from sqlalchemy import text
from backend.security.env_secrets import get_secret

logger = logging.getLogger("omnis.db")

# Primární databázové úložiště v OSS architektuře je PostgreSQL 17 (pgvector + Apache AGE + pgcrypto).
# Pro lokální testy bez DB instance se automaticky přepne do SQLite / in-memory režimu.
DATABASE_URL = None
raw_db_url = (get_secret("DATABASE_URL") or get_secret("OMNIS_DATABASE_URL") or "").strip()
if raw_db_url.startswith(("sqlite", "postgresql://", "postgresql+asyncpg://", "postgres://")):
    DATABASE_URL = raw_db_url
elif raw_db_url:
    logger.warning("Nepodporovaný formát DATABASE_URL; databáze poběží v mock režimu.")
else:
    logger.info("DATABASE_URL není nastaveno; databázová vrstva využije mock session nebo lokální fallback.")

AUDIT_DATABASE_URL = None
raw_audit_db_url = (get_secret("AUDIT_DATABASE_URL") or "").strip()
if raw_audit_db_url.startswith(("sqlite", "postgresql://", "postgresql+asyncpg://", "postgres://")):
    AUDIT_DATABASE_URL = raw_audit_db_url


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


def is_mock_session(session: Any) -> bool:
    """Vrací True, pokud SQLAlchemy běží bez připojené SQL databáze (lokální in-memory fallback)."""
    return isinstance(session, _MockSession)


engine = None
async_session_factory = None

if DATABASE_URL:
    logger.info("Database URL detected. Preparing PostgreSQL 17 / SQL connection pool...")
    try:
        url = DATABASE_URL
        if url.startswith("postgresql://"):
            url = url.replace("postgresql://", "postgresql+asyncpg://", 1)
        elif url.startswith("postgres://"):
            url = url.replace("postgres://", "postgresql+asyncpg://", 1)

        engine_kwargs = {
            "echo": False,
            "pool_pre_ping": True,
            "pool_recycle": 300,  # 5 min recycle for PostgreSQL TCP stability
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


audit_engine = None
audit_session_factory = None
if AUDIT_DATABASE_URL:
    try:
        audit_url = AUDIT_DATABASE_URL
        if audit_url.startswith("postgresql://"):
            audit_url = audit_url.replace("postgresql://", "postgresql+asyncpg://", 1)
        elif audit_url.startswith("postgres://"):
            audit_url = audit_url.replace("postgres://", "postgresql+asyncpg://", 1)
        audit_engine = create_async_engine(audit_url, echo=False, pool_pre_ping=True, pool_recycle=300)
        audit_session_factory = async_sessionmaker(bind=audit_engine, class_=AsyncSession, expire_on_commit=False)
        logger.info("Least-privilege Audit AsyncEngine configured.")
    except Exception as audit_err:
        logger.warning(f"Audit engine fallback to primary DB: {audit_err}")


async def init_db():
    if engine is not None:
        logger.info("Initializing PostgreSQL 17 / SQLite database schema...")
        try:
            async with engine.begin() as conn:
                dialect_name = conn.dialect.name
                if dialect_name != "sqlite":
                    for ext_name in ("vector", "age", "pgcrypto"):
                        try:
                            await conn.execute(text(f"CREATE EXTENSION IF NOT EXISTS {ext_name};"))
                        except Exception as ext_err:
                            logger.warning(
                                f"Could not create extension {ext_name} (might already exist or insufficient privileges): {ext_err}"
                            )

                await conn.run_sync(Base.metadata.create_all)

                # Bezpečná migrace sloupců pro existující databáze (PostgreSQL i SQLite)
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
                            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_rag_memory_64d_hnsw_cosine "
                            "ON rag_memory_64d USING hnsw (embedding vector_cosine_ops) "
                            "WITH (m = 16, ef_construction = 128);"
                        ))
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
            from backend.config import get_settings
            if get_settings().is_production:
                logger.error(f"Database connection unavailable in production: {conn_err}")
                raise
            logger.warning(f"Database connection unavailable, falling back to mock session: {conn_err}")
            yield _MockSession()
    else:
        yield _MockSession()


async def get_audit_db() -> AsyncGenerator:
    """Poskytuje session s rolí omnis_audit_append (pokud je AUDIT_DATABASE_URL nastaveno), jinak primární DB."""
    if audit_engine is not None and audit_session_factory is not None:
        async with audit_session_factory() as session:
            try:
                yield session
            except Exception:
                await session.rollback()
                raise
            finally:
                await session.close()
        return
    async for session in get_db():
        yield session


async def set_pg_rls_context(db: AsyncSession, user_id: str, role: str = "STANDARD_USER") -> None:
    """Nastaví lokální PostgreSQL GUC proměnné pro RLS politiky v rámci aktuální transakce."""
    if db is None or isinstance(db, _MockSession):
        return
    bind = getattr(db, "bind", None)
    dialect_name = getattr(getattr(bind, "dialect", None), "name", "")
    if dialect_name != "postgresql":
        return
    await db.execute(text("SELECT set_config('omnis.current_user_id', :uid, true)"), {"uid": str(user_id)})
    await db.execute(text("SELECT set_config('omnis.current_role', :role, true)"), {"role": str(role)})


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
            "driver": "postgresql+asyncpg" if engine.dialect.name != "sqlite" else "sqlite+aiosqlite",
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

async def upsert_rag_memory_64d(
    db: AsyncSession,
    *,
    user_id: str,
    content: str,
    embedding_64: List[float],
    domain: str = "SYSTEMS_INTELLIGENCE",
    thread_id: str = "thread_main",
    importance_score: float = 1.0,
    metadata_json: Optional[Dict[str, Any]] = None,
    idempotency_key: Optional[str] = None,
) -> bool:
    """
    Asynchronně a idempotentně uloží 64D vektor do tabulky rag_memory_64d.
    Vrací True při novém vložení, False při deduplikaci dle idempotency_key.
    """
    if db is None or isinstance(db, _MockSession):
        return False
    if len(embedding_64) != 64:
        raise ValueError(f"Expected 64D embedding vector, got {len(embedding_64)} dimensions.")

    import json
    import uuid as _uuid

    bind = getattr(db, "bind", None)
    dialect_name = getattr(getattr(bind, "dialect", None), "name", "")
    record_id = str(_uuid.uuid4())
    meta = metadata_json or {}

    if dialect_name == "postgresql":
        vec_literal = json.dumps([float(v) for v in embedding_64])
        sql = text("""
            INSERT INTO public.rag_memory_64d
                (id, user_id, thread_id, domain, content, embedding, importance_score, metadata_json, idempotency_key)
            VALUES
                (:id, :user_id, :thread_id, :domain, :content, CAST(:embedding AS vector(64)),
                 :importance_score, CAST(:metadata_json AS jsonb), :idempotency_key)
            ON CONFLICT (idempotency_key) DO NOTHING
            RETURNING id;
        """)
        res = await db.execute(
            sql,
            {
                "id": record_id,
                "user_id": user_id,
                "thread_id": thread_id,
                "domain": domain,
                "content": content,
                "embedding": vec_literal,
                "importance_score": float(importance_score),
                "metadata_json": json.dumps(meta),
                "idempotency_key": idempotency_key,
            },
        )
        return res.scalar_one_or_none() is not None

    from backend.models import RagMemory64D
    from sqlalchemy import select as sa_select

    if idempotency_key:
        existing = await db.execute(
            sa_select(RagMemory64D.id).where(RagMemory64D.idempotency_key == idempotency_key)
        )
        if existing.scalar_one_or_none() is not None:
            return False

    entry = RagMemory64D(
        id=record_id,
        user_id=user_id,
        thread_id=thread_id,
        domain=domain,
        content=content,
        embedding=[float(v) for v in embedding_64],
        importance_score=float(importance_score),
        metadata_json=meta,
        idempotency_key=idempotency_key,
    )
    db.add(entry)
    await db.flush()
    return True


async def hybrid_search_knowledge(
    query_text: str,
    query_embedding: Optional[List[float]] = None,
    limit: int = 5,
    rrf_k: int = 60,
    db: Optional[AsyncSession] = None,
    user_id: Optional[str] = None,
) -> List[Dict[str, Any]]:
    """
    Hybridní vyhledávání kombinující HNSW kosinovou vzdálenost (pgvector rag_memory_64d / vector_memories)
    a textové sparse vyhledávání pomocí Reciprocal Rank Fusion (RRF).
    Vzorec: RRF_score(d) = sum_{m in {dense, sparse}} (1 / (rrf_k + rank_m(d)))
    """
    results_map: Dict[str, Dict[str, Any]] = {}
    dense_ranks: Dict[str, int] = {}
    sparse_ranks: Dict[str, int] = {}

    if db is not None and not isinstance(db, _MockSession) and engine is not None:
        dialect_name = getattr(getattr(db, "bind", None), "dialect", None)
        dialect_str = getattr(dialect_name, "name", "") if dialect_name else engine.dialect.name
        try:
            # 1. HNSW kosinová vzdálenost (Dense retrieval) prioritně nad rag_memory_64d (PostgreSQL)
            if query_embedding and dialect_str != "sqlite":
                import json
                query_vec_str = json.dumps([float(v) for v in query_embedding])
                if len(query_embedding) == 64:
                    try:
                        await db.execute(text("SET LOCAL hnsw.ef_search = 64;"))
                    except Exception:
                        pass

                    if user_id:
                        rag64_sql = text("""
                            SELECT id::text, content, domain, importance_score, metadata_json,
                                   (embedding <=> CAST(:query_vec AS vector(64))) AS cosine_distance
                            FROM public.rag_memory_64d
                            WHERE user_id = :user_id
                            ORDER BY cosine_distance ASC
                            LIMIT :search_limit;
                        """)
                        rag_params = {"query_vec": query_vec_str, "user_id": user_id, "search_limit": limit * 2}
                    else:
                        rag64_sql = text("""
                            SELECT id::text, content, domain, importance_score, metadata_json,
                                   (embedding <=> CAST(:query_vec AS vector(64))) AS cosine_distance
                            FROM public.rag_memory_64d
                            ORDER BY cosine_distance ASC
                            LIMIT :search_limit;
                        """)
                        rag_params = {"query_vec": query_vec_str, "search_limit": limit * 2}

                    try:
                        rag_res = await db.execute(rag64_sql, rag_params)
                        for rank, row in enumerate(rag_res.fetchall(), start=1):
                            doc_id = str(row[0])
                            dense_ranks[doc_id] = rank
                            if doc_id not in results_map:
                                results_map[doc_id] = {
                                    "id": doc_id,
                                    "content": row[1],
                                    "memory_type": row[2] or "rag_64d",
                                    "importance_score": float(row[3] or 1.0),
                                    "metadata": row[4] or {},
                                    "cosine_distance": float(row[5] or 0.0),
                                }
                    except Exception as rag_err:
                        logger.debug(f"rag_memory_64d dense lookup fallback to vector_memories: {rag_err}")

                    if not dense_ranks:
                        if user_id:
                            dense_sql = text("""
                                SELECT vm.id, vm.content, vm.memory_type, vm.importance_score, vm.metadata_json,
                                       (vm.embedding_64 <=> CAST(:query_vec AS vector)) as cosine_distance
                                FROM vector_memories vm
                                JOIN conversations c ON vm.conversation_id = c.id
                                WHERE vm.embedding_64 IS NOT NULL AND c.user_id = :user_id
                                ORDER BY cosine_distance ASC
                                LIMIT :search_limit;
                            """)
                            dense_params = {"query_vec": query_vec_str, "user_id": user_id, "search_limit": limit * 2}
                        else:
                            dense_sql = text("""
                                SELECT id, content, memory_type, importance_score, metadata_json,
                                       (embedding_64 <=> CAST(:query_vec AS vector)) as cosine_distance
                                FROM vector_memories
                                WHERE embedding_64 IS NOT NULL
                                ORDER BY cosine_distance ASC
                                LIMIT :search_limit;
                            """)
                            dense_params = {"query_vec": query_vec_str, "search_limit": limit * 2}
                        dense_res = await db.execute(dense_sql, dense_params)
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
                else:
                    if user_id:
                        dense_sql = text("""
                            SELECT vm.id, vm.content, vm.memory_type, vm.importance_score, vm.metadata_json,
                                   (CAST(vm.embedding AS text)::vector <=> CAST(:query_vec AS vector)) as cosine_distance
                            FROM vector_memories vm
                            JOIN conversations c ON vm.conversation_id = c.id
                            WHERE vm.embedding IS NOT NULL AND c.user_id = :user_id
                            ORDER BY cosine_distance ASC
                            LIMIT :search_limit;
                        """)
                        dense_params = {"query_vec": query_vec_str, "user_id": user_id, "search_limit": limit * 2}
                    else:
                        dense_sql = text("""
                            SELECT id, content, memory_type, importance_score, metadata_json,
                                   (CAST(embedding AS text)::vector <=> CAST(:query_vec AS vector)) as cosine_distance
                            FROM vector_memories
                            WHERE embedding IS NOT NULL
                            ORDER BY cosine_distance ASC
                            LIMIT :search_limit;
                        """)
                        dense_params = {"query_vec": query_vec_str, "search_limit": limit * 2}
                    dense_res = await db.execute(dense_sql, dense_params)
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

            # 2. Textové / Sparse vyhledávání nad rag_memory_64d (pokud existuje) a vector_memories
            like_op = "ILIKE" if dialect_str != "sqlite" else "LIKE"
            try:
                if user_id:
                    sparse_rag_sql = text(f"""
                        SELECT id, content, domain, importance_score, metadata_json
                        FROM rag_memory_64d
                        WHERE user_id = :user_id AND content {like_op} :pattern
                        ORDER BY importance_score DESC
                        LIMIT :search_limit;
                    """)
                    sparse_rag_params = {"user_id": user_id, "pattern": f"%{query_text}%", "search_limit": limit * 2}
                else:
                    sparse_rag_sql = text(f"""
                        SELECT id, content, domain, importance_score, metadata_json
                        FROM rag_memory_64d
                        WHERE content {like_op} :pattern
                        ORDER BY importance_score DESC
                        LIMIT :search_limit;
                    """)
                    sparse_rag_params = {"pattern": f"%{query_text}%", "search_limit": limit * 2}
                sparse_rag_res = await db.execute(sparse_rag_sql, sparse_rag_params)
                for rank, row in enumerate(sparse_rag_res.fetchall(), start=1):
                    doc_id = str(row[0])
                    sparse_ranks[doc_id] = rank
                    if doc_id not in results_map:
                        results_map[doc_id] = {
                            "id": doc_id,
                            "content": row[1],
                            "memory_type": row[2] or "rag_64d",
                            "importance_score": float(row[3] or 1.0),
                            "metadata": row[4] or {},
                            "cosine_distance": 0.5,
                        }
            except Exception:
                pass

            if user_id:
                sparse_sql = text(f"""
                    SELECT vm.id, vm.content, vm.memory_type, vm.importance_score, vm.metadata_json
                    FROM vector_memories vm
                    JOIN conversations c ON vm.conversation_id = c.id
                    WHERE c.user_id = :user_id AND vm.content {like_op} :pattern
                    ORDER BY vm.importance_score DESC
                    LIMIT :search_limit;
                """)
                sparse_params = {"user_id": user_id, "pattern": f"%{query_text}%", "search_limit": limit * 2}
            else:
                sparse_sql = text(f"""
                    SELECT id, content, memory_type, importance_score, metadata_json
                    FROM vector_memories
                    WHERE content {like_op} :pattern
                    ORDER BY importance_score DESC
                    LIMIT :search_limit;
                """)
                sparse_params = {"pattern": f"%{query_text}%", "search_limit": limit * 2}
            sparse_res = await db.execute(sparse_sql, sparse_params)
            for rank, row in enumerate(sparse_res.fetchall(), start=1):
                doc_id = str(row[0])
                if doc_id not in sparse_ranks:
                    sparse_ranks[doc_id] = len(sparse_ranks) + rank
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

