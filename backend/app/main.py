"""
O.M.N.I.S. APEX v4.5 - Sovereign FastAPI Backend Application (backend/app/main.py)
"""

from __future__ import annotations

import asyncio
import logging
import os
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI, APIRouter, Depends, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse

from backend.config import get_settings
from backend.database import get_db, init_db, is_mock_session
from backend.app.llm_cascade import get_llm_cascade
from backend.app.db_engine import verify_retraction_watch, append_zk_audit_ledger_entry

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("omnis.app.main")

@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    logger.info("Starting O.M.N.I.S. APEX v4.5 Zero-Cost Sovereign Backend...")
    get_settings().validate_production_secrets()
    try:
        await init_db()
        logger.info("Database initialized successfully.")
    except Exception as e:
        logger.warning(f"Database initialization deferred: {e}")
    yield
    logger.info("Shutting down O.M.N.I.S. backend.")

app = FastAPI(
    title="O.M.N.I.S. APEX v4.5 Sovereign Core API",
    description="100% Free Open-Source Sovereign Infrastructure for Autonomous Polymathic Execution",
    version="4.5.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/healthz", tags=["System"])
async def healthz():
    return {"status": "healthy", "infrastructure": "100_percent_open_source_zero_cost"}

@app.post("/api/v1/inference/cascade", tags=["LLM Cascade"])
async def run_llm_cascade(payload: dict):
    """Spustí kaskádové LLM volání (Tier 1 Local Gemma 4 -> Groq -> Gemini -> OpenRouter)."""
    messages = payload.get("messages", [{"role": "user", "content": payload.get("prompt", "Hello OMNIS")}])
    mode = payload.get("mode")
    cascade = get_llm_cascade()
    result = await cascade.generate_completion(messages, override_mode=mode)
    return result

@app.get("/download/apk", tags=["System"])
async def download_apk():
    candidates = [
        os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "Omnis-install.apk")),
        os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "public", "Omnis-install.apk")),
        os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "app", "build", "outputs", "apk", "debug", "app-debug.apk")),
    ]
    for candidate in candidates:
        if os.path.isfile(candidate):
            return FileResponse(candidate, media_type="application/vnd.android.package-archive", filename="Omnis-install.apk")
    return JSONResponse(status_code=404, content={"error": "APK not found."})
