"""
O.M.N.I.S. Core FastAPI Backend Application
Handles queries, cognitive processes, Impact Matrix evaluations, and autopoietic memory loops.
"""

from __future__ import annotations

import asyncio
import logging
import os
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import AsyncGenerator, List

from fastapi import FastAPI, APIRouter, Depends, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
try:
    from fastapi.routing import _IncludedRouter
except ImportError:
    _IncludedRouter = None
from sqlalchemy import select, delete as sql_delete
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from .database import get_db, init_db, is_mock_session
from backend.security.firestore_store import fs_delete_doc, fs_get_doc, fs_list_collection, fs_set_doc
from backend.models import Conversation, Message
from backend.schemas import (
    ThreadCreateRequest,
    ThreadUpdateRequest,
    ThreadDetailResponse,
    MessageSchema,
)
from backend import (
    autopoiesis_router,
    cognitive_router,
    dev_router,
    engine_router,
    epistemic_router,
    memory_router,
    multi_agent_router,
    phase4_router,
    system_router,
    sync_router,
    dashboard_router,
    admin_router,
    audit_router,
    apex_router,
)
from backend.auth_router import router as auth_router, user_router, get_current_user
from backend.config import get_settings
from backend.models import User
from backend.routers import inference_router

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("omnis.api")

# ==========================================================
# Thread Persistence Router (Database Persistence Layer)
# ==========================================================
thread_router = APIRouter(prefix="/api/threads", tags=["Thread Persistence"])


def _fs_thread_to_response(doc: dict) -> ThreadDetailResponse:
    created_raw = doc.get("created_at")
    updated_raw = doc.get("updated_at")
    created_dt = datetime.fromisoformat(created_raw) if isinstance(created_raw, str) else datetime.now(timezone.utc)
    updated_dt = datetime.fromisoformat(updated_raw) if isinstance(updated_raw, str) else created_dt
    return ThreadDetailResponse(
        id=str(doc.get("id") or doc.get("sessionId")),
        title=str(doc.get("title") or "Bez názvu"),
        ontology_domain=str(doc.get("ontology_domain") or doc.get("domain") or "SYSTEMS_INTELLIGENCE"),
        messages=[],
        created_at=created_dt,
        updated_at=updated_dt,
    )


@thread_router.get("", response_model=List[ThreadDetailResponse])
@thread_router.get("/", response_model=List[ThreadDetailResponse])
async def list_threads(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Seznam konverzačních vláken přihlášeného uživatele."""
    if is_mock_session(db):
        docs = fs_list_collection("omnis_sessions", filters={"user_id": str(current_user.id)})
        docs.sort(key=lambda d: str(d.get("updated_at") or ""), reverse=True)
        return [_fs_thread_to_response(d) for d in docs]

    stmt = (
        select(Conversation)
        .options(selectinload(Conversation.messages))
        .where(Conversation.user_id == current_user.id)
        .order_by(Conversation.updated_at.desc())
    )
    result = await db.execute(stmt)
    conversations = result.scalars().all()

    response = []
    for conv in conversations:
        response.append(
            ThreadDetailResponse(
                id=str(conv.id),
                title=conv.title or "Bez názvu",
                ontology_domain=conv.ontology_domain or "SYSTEMS_INTELLIGENCE",
                messages=[
                    MessageSchema(
                        id=str(m.id),
                        conversation_id=str(m.conversation_id),
                        role=m.role,
                        content=m.content,
                        cognitive_thoughts=m.cognitive_thoughts,
                        follow_up_questions=m.follow_up_questions,
                        created_at=m.created_at
                    ) for m in (conv.messages or [])
                ],
                created_at=conv.created_at,
                updated_at=conv.updated_at
            )
        )
    return response


@thread_router.post("", response_model=ThreadDetailResponse, status_code=status.HTTP_201_CREATED)
@thread_router.post("/", response_model=ThreadDetailResponse, status_code=status.HTTP_201_CREATED)
async def create_thread(
    req: ThreadCreateRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Vytvořit nové konverzační vlákno pro přihlášeného uživatele."""
    conv_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc)
    if is_mock_session(db):
        fs_set_doc(
            "omnis_sessions",
            conv_id,
            {
                "id": conv_id,
                "sessionId": conv_id,
                "user_id": str(current_user.id),
                "userId": str(current_user.id),
                "title": req.title,
                "ontology_domain": req.ontology_domain,
                "created_at": now.isoformat(),
                "updated_at": now.isoformat(),
            },
            merge=True,
        )
        return ThreadDetailResponse(
            id=conv_id,
            title=req.title,
            ontology_domain=req.ontology_domain,
            messages=[],
            created_at=now,
            updated_at=now,
        )

    conv = Conversation(
        id=conv_id,
        user_id=current_user.id,
        title=req.title,
        ontology_domain=req.ontology_domain,
        created_at=now,
        updated_at=now
    )
    db.add(conv)
    await db.commit()
    return ThreadDetailResponse(
        id=conv_id,
        title=req.title,
        ontology_domain=req.ontology_domain,
        messages=[],
        created_at=now,
        updated_at=now
    )


@thread_router.get("/{thread_id}", response_model=ThreadDetailResponse)
async def get_thread(
    thread_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Detail konkrétního vlákna včetně historie zpráv."""
    if is_mock_session(db):
        doc = fs_get_doc("omnis_sessions", thread_id)
        if not doc:
            raise HTTPException(status_code=404, detail=f"Vlákno {thread_id} nebylo nalezeno.")
        if doc.get("user_id") != str(current_user.id) and current_user.role != "ADMIN_OPERATOR":
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Přístup k vláknu zamítnut.")
        return _fs_thread_to_response(doc)

    stmt = (
        select(Conversation)
        .options(selectinload(Conversation.messages))
        .where(Conversation.id == thread_id)
    )
    result = await db.execute(stmt)
    conv = result.scalar_one_or_none()
    if not conv:
        raise HTTPException(status_code=404, detail=f"Vlákno {thread_id} nebylo nalezeno.")
    if conv.user_id != current_user.id and current_user.role != "ADMIN_OPERATOR":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Přístup k vláknu zamítnut.")

    return ThreadDetailResponse(
        id=str(conv.id),
        title=conv.title or "Bez názvu",
        ontology_domain=conv.ontology_domain or "SYSTEMS_INTELLIGENCE",
        messages=[
            MessageSchema(
                id=str(m.id),
                conversation_id=str(m.conversation_id),
                role=m.role,
                content=m.content,
                cognitive_thoughts=m.cognitive_thoughts,
                follow_up_questions=m.follow_up_questions,
                created_at=m.created_at
            ) for m in (conv.messages or [])
        ],
        created_at=conv.created_at,
        updated_at=conv.updated_at
    )


@thread_router.put("/{thread_id}", response_model=ThreadDetailResponse)
async def update_thread(
    thread_id: str,
    req: ThreadUpdateRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Aktualizace názvu nebo domény vlákna."""
    if is_mock_session(db):
        doc = fs_get_doc("omnis_sessions", thread_id)
        if not doc:
            raise HTTPException(status_code=404, detail=f"Vlákno {thread_id} nebylo nalezeno.")
        if doc.get("user_id") != str(current_user.id) and current_user.role != "ADMIN_OPERATOR":
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Přístup k vláknu zamítnut.")
        if req.title is not None:
            doc["title"] = req.title
        if req.ontology_domain is not None:
            doc["ontology_domain"] = req.ontology_domain
        doc["updated_at"] = datetime.now(timezone.utc).isoformat()
        fs_set_doc("omnis_sessions", thread_id, doc, merge=True)
        return _fs_thread_to_response(doc)

    stmt = (
        select(Conversation)
        .options(selectinload(Conversation.messages))
        .where(Conversation.id == thread_id)
    )
    result = await db.execute(stmt)
    conv = result.scalar_one_or_none()
    if not conv:
        raise HTTPException(status_code=404, detail=f"Vlákno {thread_id} nebylo nalezeno.")
    if conv.user_id != current_user.id and current_user.role != "ADMIN_OPERATOR":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Přístup k vláknu zamítnut.")

    if req.title is not None:
        conv.title = req.title
    if req.ontology_domain is not None:
        conv.ontology_domain = req.ontology_domain
    conv.updated_at = datetime.now(timezone.utc)

    db.add(conv)
    await db.commit()

    return ThreadDetailResponse(
        id=str(conv.id),
        title=conv.title or "Bez názvu",
        ontology_domain=conv.ontology_domain or "SYSTEMS_INTELLIGENCE",
        messages=[
            MessageSchema(
                id=str(m.id),
                conversation_id=str(m.conversation_id),
                role=m.role,
                content=m.content,
                cognitive_thoughts=m.cognitive_thoughts,
                follow_up_questions=m.follow_up_questions,
                created_at=m.created_at
            ) for m in (conv.messages or [])
        ],
        created_at=conv.created_at,
        updated_at=conv.updated_at
    )


@thread_router.delete("/{thread_id}")
async def delete_thread(
    thread_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Smazání vlákna a všech přidružených zpráv."""
    if is_mock_session(db):
        doc = fs_get_doc("omnis_sessions", thread_id)
        if not doc:
            raise HTTPException(status_code=404, detail=f"Vlákno {thread_id} nebylo nalezeno.")
        if doc.get("user_id") != str(current_user.id) and current_user.role != "ADMIN_OPERATOR":
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Přístup k vláknu zamítnut.")
        fs_delete_doc("omnis_sessions", thread_id)
        return {"status": "success", "message": f"Vlákno {thread_id} bylo úspěšně smazáno.", "thread_id": thread_id}

    stmt = select(Conversation).where(Conversation.id == thread_id)
    result = await db.execute(stmt)
    conv = result.scalar_one_or_none()
    if not conv:
        raise HTTPException(status_code=404, detail=f"Vlákno {thread_id} nebylo nalezeno.")
    if conv.user_id != current_user.id and current_user.role != "ADMIN_OPERATOR":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Přístup k vláknu zamítnut.")

    await db.execute(sql_delete(Message).where(Message.conversation_id == thread_id))
    await db.delete(conv)
    await db.commit()

    return {"status": "success", "message": f"Vlákno {thread_id} bylo úspěšně smazáno.", "thread_id": thread_id}


async def _safe_init_db():
    try:
        await asyncio.wait_for(init_db(), timeout=5.0)
        logger.info("Database schema initialized successfully in background.")
    except Exception as exc:
        logger.warning(f"Initial DB connect deferred (queries will reconnect): {exc}")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Startup and shutdown lifecycle handler."""
    from backend.database import DATABASE_URL
    from backend.graph.csr_engine import get_age_graph_repository

    logger.info("Starting O.M.N.I.S. backend (PostgreSQL 17 + Apache AGE + pgvector)...")
    get_settings().validate_production_secrets()
    asyncio.create_task(_safe_init_db())

    age_repo = get_age_graph_repository()
    if DATABASE_URL and DATABASE_URL.startswith(("postgresql://", "postgresql+asyncpg://", "postgres://")):
        await age_repo.start_invalidation_listener(DATABASE_URL)

    yield
    await age_repo.stop_listener()
    logger.info("Shutting down O.M.N.I.S. backend.")


app = FastAPI(
    title="O.M.N.I.S. Cognitive Architecture API",
    description="Omni-Modal Network for Integrated Synthesis - Backend & Impact Matrix Engine",
    version="4.5.0",
    lifespan=lifespan,
)

_allowed_origins = [
    origin.strip()
    for origin in os.getenv(
        "ALLOWED_ORIGINS",
        "http://localhost:3000,http://localhost:5173,http://localhost:8000",
    ).split(",")
    if origin.strip()
]
_allowed_origin_regex = os.getenv(
    "ALLOWED_ORIGIN_REGEX",
    r"^https://(.*\.duckdns\.org|.*\.run\.app)$|^http://localhost(:\d+)?$",
).strip() or None

app.add_middleware(
    CORSMiddleware,
    allow_origins=_allowed_origins,
    allow_origin_regex=_allowed_origin_regex,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)


@app.middleware("http")
async def add_owasp_security_headers(request, call_next):
    """Enforces strict OWASP security headers across all API responses."""
    response = await call_next(request)
    response.headers["Content-Security-Policy"] = "default-src 'self' https:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline';"
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["X-XSS-Protection"] = "1; mode=block"
    response.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"
    return response


@app.exception_handler(SQLAlchemyError)
async def sqlalchemy_exception_handler(request, exc: SQLAlchemyError):
    """Intercepts and gracefully sanitizes database-level exceptions globally."""
    logger.exception("CRITICAL: Database exception intercepted across router boundaries: %s", exc)
    return JSONResponse(
        status_code=500,
        content={
            "status": "error",
            "type": "DatabaseOperationalFailure",
            "message": "A critical storage plane or relational integrity constraint error occurred within O.M.N.I.S.",
        },
    )


app.include_router(system_router)
app.include_router(cognitive_router)
app.include_router(engine_router)
app.include_router(autopoiesis_router)
app.include_router(memory_router)
app.include_router(epistemic_router)
app.include_router(multi_agent_router)
app.include_router(phase4_router)
app.include_router(dashboard_router)
app.include_router(dev_router)
app.include_router(auth_router)
app.include_router(user_router)
app.include_router(thread_router)
app.include_router(admin_router)
app.include_router(sync_router)
from backend.audit_router import akge_router
app.include_router(audit_router)
app.include_router(akge_router)
app.include_router(apex_router)
app.include_router(inference_router)

# ==========================================================
# Matematický & Topologický Rámec (FÁZE 12)
# ==========================================================
topology_router = APIRouter(prefix="/api/topology", tags=["Topological & Stability Metrics"])

@topology_router.get("/metrics")
async def get_topology_metrics(v: int = 8, e: int = 28, f: int = 8):
    """Vrací Eulerovu charakteristiku a Bettiho čísla kognitivního prostoru."""
    try:
        from backend.topology import calculate_euler_characteristic, calculate_betti_numbers
        chi = calculate_euler_characteristic(v, e, f)
        betti = calculate_betti_numbers(v, e, f)
        return {"status": "success", "euler_characteristic_chi": chi, "betti_numbers": betti}
    except Exception as exc:
        return {"status": "error", "detail": str(exc)}

@topology_router.post("/cascade-risk")
async def evaluate_cascade_risk(payload: dict):
    """Vyhodnotí perkolační kaskádové riziko napříč doménami."""
    try:
        from backend.percolation import eval_cascade_risk
        status = eval_cascade_risk(payload.get("domains_status", {}))
        return {"status": "success", "cascade_status": status}
    except Exception as exc:
        return {"status": "error", "detail": str(exc)}

@topology_router.get("/token-budget")
async def get_token_budget_recommendation(sigma: float = 0.15):
    """Vrátí adaptivní alokaci tokenů na základě systémového pnutí sigma."""
    try:
        from backend.token_manager import calculate_token_budget
        budget = calculate_token_budget(sigma)
        return {"status": "success", "sigma": sigma, "budget": budget}
    except Exception as exc:
        return {"status": "error", "detail": str(exc)}

app.include_router(topology_router)

@app.post("/api/v1/inference/cascade", tags=["LLM Cascade"])
async def run_unified_llm_cascade(payload: dict):
    """Jednotná LLM brána propojující lokální Gemma 4 (tools/gemma4/agent.yaml) s cloud failoverem a Circuit Breakerem."""
    from backend.app.llm_cascade import get_llm_cascade

    messages = payload.get("messages", [{"role": "user", "content": str(payload.get("prompt", "Hello OMNIS"))}])
    mode = payload.get("mode")
    cascade = get_llm_cascade()
    return await cascade.generate_completion(messages, override_mode=mode)


@app.get("/api/v1/inference/cascade/status", tags=["LLM Cascade"])
async def get_llm_cascade_status():
    """Vrací aktuální stav Circuit Breakerů a načtenou konfiguraci Gemma 4 agenta."""
    from backend.app.llm_cascade import get_llm_cascade

    return get_llm_cascade().get_breaker_status()


@app.get("/download/apk", tags=["System"])
async def download_apk():
    """Endpoint pro přímé stažení nejnovějšího APK balíčku do mobilního zařízení."""
    candidates = [
        os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "Omnis-install.apk")),
        os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "public", "Omnis-install.apk")),
        os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "app", "build", "outputs", "apk", "debug", "app-debug.apk")),
    ]
    for candidate in candidates:
        if os.path.isfile(candidate):
            return FileResponse(
                candidate,
                media_type="application/vnd.android.package-archive",
                filename="Omnis-install.apk",
            )
    return JSONResponse(status_code=404, content={"error": "APK soubor nebyl nalezen."})

# Expand included routers into concrete routes so route inspection and URL matching work reliably.
flattened_routes = []
for route in app.router.routes:
    if _IncludedRouter is not None and isinstance(route, _IncludedRouter):
        flattened_routes.extend(getattr(route.original_router, "routes", []))
    else:
        flattened_routes.append(route)
app.router.routes[:] = flattened_routes
