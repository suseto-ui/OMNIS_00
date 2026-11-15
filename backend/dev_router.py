from fastapi import APIRouter, Depends
from typing import Dict, Any
from backend.auth_router import get_current_user
from backend.admin_router import get_current_admin
from backend.models import User

router = APIRouter(prefix="/api/dev", tags=["Development"])

# In-memory úložiště pro lokální demo/vývoj
telemetry_state = {
    "cumulative_prompt_tokens": 0,
    "cumulative_completion_tokens": 0,
    "cumulative_total_tokens": 0,
    "total_queries_executed": 0,
    "estimated_total_cost_usd": 0.0,
    "estimated_total_cost_czk": 0.0,
    "recent_records": []
}

def track_token_usage(usage: Dict[str, Any]):
    """Pomocná funkce volaná z engine_routeru."""
    telemetry_state["cumulative_prompt_tokens"] += usage["prompt_tokens"]
    telemetry_state["cumulative_completion_tokens"] += usage["completion_tokens"]
    telemetry_state["cumulative_total_tokens"] += usage["total_tokens"]
    telemetry_state["total_queries_executed"] += 1
    telemetry_state["estimated_total_cost_usd"] += usage["cost_usd"]
    telemetry_state["estimated_total_cost_czk"] = telemetry_state["estimated_total_cost_usd"] * 23.5

@router.get("/token-telemetry")
async def get_telemetry(current_user: User = Depends(get_current_user)):
    return telemetry_state

@router.post("/reset-tokens")
async def reset_telemetry(current_admin: User = Depends(get_current_admin)):
    global telemetry_state
    telemetry_state = {
        "cumulative_prompt_tokens": 0,
        "cumulative_completion_tokens": 0,
        "cumulative_total_tokens": 0,
        "total_queries_executed": 0,
        "estimated_total_cost_usd": 0.0,
        "estimated_total_cost_czk": 0.0,
        "recent_records": []
    }
    return {"status": "reset_successful"}

@router.post("/feedback")
async def log_feedback(feedback: dict, current_user: User = Depends(get_current_user)):
    return {"status": "captured"}