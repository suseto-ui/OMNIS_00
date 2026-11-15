from fastapi import APIRouter, Depends
from backend.admin_router import get_current_admin
from backend.auth_router import get_current_user
from backend.models import User
from backend.token_service import token_telemetry_service

dev_router = APIRouter(prefix="/api/dev", tags=["Development & Diagnostics"])

@dev_router.get("/token-telemetry")
async def get_token_telemetry(current_user: User = Depends(get_current_user)):
    """Vrací kumulativní statistiky spotřeby tokenů pro aktuální instanci."""
    return token_telemetry_service.get_telemetry()

@dev_router.post("/estimate-tokens")
async def estimate_query_tokens(payload: dict, current_user: User = Depends(get_current_user)):
    """Vypočítá předpokládanou spotřebu tokenů pro zadaný dotaz."""
    query = payload.get("query", "")
    include_sys = payload.get("include_system_prompt", True)
    include_mem = payload.get("include_memory_context", True)
    return token_telemetry_service.estimate_query_tokens(
        query=query,
        include_system_prompt=include_sys,
        include_memory_context=include_mem,
    )

@dev_router.post("/reset-tokens")
async def reset_token_telemetry(current_admin: User = Depends(get_current_admin)):
    """Resetuje relaci počítadla tokenů (pouze ADMIN_OPERATOR)."""
    token_telemetry_service.reset_telemetry()
    return {"status": "reset_successful", "message": "Počítadlo tokenů bylo vynulováno."}