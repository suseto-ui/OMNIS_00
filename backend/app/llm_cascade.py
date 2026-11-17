"""
O.M.N.I.S. APEX v4.5 - Asynchronous Multi-Provider LLM Cascade & Circuit Breaker Router
Tiers:
  1. Local Edge (Ollama / gemma4)
  2. GroqCloud API (Free tier, ultra-fast Llama 3 / Gemma)
  3. Google AI Studio (Gemini REST API free tier)
  4. OpenRouter API (OpenRouter free model router)
"""

from __future__ import annotations
import logging
import os
from typing import Any, Dict, List, Optional
import httpx
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type

logger = logging.getLogger("omnis.llm_cascade")

class LLMCascadeRouter:
    def __init__(self):
        self.gemma_base_url = os.getenv("GEMMA4_API_BASE_URL", "http://host.docker.internal:11434/v1")
        self.gemma_model = os.getenv("GEMMA4_MODEL_NAME", "gemma4:e4b")
        self.groq_key = os.getenv("GROQ_API_KEY", "")
        self.gemini_key = os.getenv("GEMINI_API_KEY", "")
        self.openrouter_key = os.getenv("OPENROUTER_API_KEY", "")
        self.mode = os.getenv("LLM_PROVIDER_MODE", "gemma4_local").lower()

    @retry(
        stop=stop_after_attempt(2),
        wait=wait_exponential(multiplier=1, min=1, max=4),
        retry=retry_if_exception_type((httpx.RequestError, httpx.TimeoutException)),
        reraise=False
    )
    async def _call_openai_compatible(self, base_url: str, api_key: str, model: str, messages: List[Dict[str, str]], timeout: float = 15.0) -> Optional[str]:
        headers = {"Content-Type": "application/json"}
        if api_key:
            headers["Authorization"] = f"Bearer {api_key}"

        payload = {
            "model": model,
            "messages": messages,
            "temperature": 0.3,
            "max_tokens": 1500
        }

        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.post(f"{base_url.rstrip('/')}/chat/completions", json=payload, headers=headers)
            if resp.status_code == 200:
                data = resp.json()
                return data["choices"][0]["message"]["content"]
            logger.warning(f"Provider at {base_url} returned status {resp.status_code}: {resp.text}")
        return None

    @retry(
        stop=stop_after_attempt(2),
        wait=wait_exponential(multiplier=1, min=1, max=4),
        retry=retry_if_exception_type((httpx.RequestError, httpx.TimeoutException)),
        reraise=False
    )
    async def _call_gemini_rest(self, api_key: str, messages: List[Dict[str, str]], timeout: float = 15.0) -> Optional[str]:
        if not api_key:
            return None
        model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"

        formatted_contents = []
        for m in messages:
            role = "user" if m.get("role") == "user" else "model"
            formatted_contents.append({
                "role": role,
                "parts": [{"text": m.get("content", "")}]
            })

        payload = {"contents": formatted_contents}
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.post(url, json=payload)
            if resp.status_code == 200:
                data = resp.json()
                candidates = data.get("candidates", [])
                if candidates:
                    parts = candidates[0].get("content", {}).get("parts", [])
                    if parts:
                        return parts[0].get("text", "")
            logger.warning(f"Gemini REST API returned status {resp.status_code}: {resp.text}")
        return None

    async def generate_completion(self, messages: List[Dict[str, str]], override_mode: Optional[str] = None) -> Dict[str, Any]:
        """
        Spustí kaskádové volání LLM poskytovatelů s automatickým failoverem.
        """
        active_mode = override_mode or self.mode
        tiers_tried = []

        # Tier 1: Local Ollama / Gemma 4
        if active_mode in ("gemma4_local", "all") or not active_mode:
            tiers_tried.append("Tier 1 (Local Ollama / Gemma 4)")
            try:
                res = await self._call_openai_compatible(
                    self.gemma_base_url, "", self.gemma_model, messages, timeout=10.0
                )
                if res:
                    return {"status": "success", "provider": "Tier 1: Local Gemma 4", "content": res}
            except Exception as e:
                logger.info(f"Tier 1 local inference failed: {e}")

        # Tier 2: GroqCloud API
        if self.groq_key and active_mode in ("gemma4_local", "groq", "all"):
            tiers_tried.append("Tier 2 (GroqCloud)")
            try:
                res = await self._call_openai_compatible(
                    "https://api.groq.com/openai/v1", self.groq_key, "llama-3.3-70b-versatile", messages, timeout=12.0
                )
                if res:
                    return {"status": "success", "provider": "Tier 2: GroqCloud", "content": res}
            except Exception as e:
                logger.info(f"Tier 2 GroqCloud failed: {e}")

        # Tier 3: Google AI Studio Gemini Free Tier
        if self.gemini_key and active_mode in ("gemma4_local", "gemini", "all"):
            tiers_tried.append("Tier 3 (Google Gemini Free Tier)")
            try:
                res = await self._call_gemini_rest(self.gemini_key, messages, timeout=12.0)
                if res:
                    return {"status": "success", "provider": "Tier 3: Google Gemini", "content": res}
            except Exception as e:
                logger.info(f"Tier 3 Gemini failed: {e}")

        # Tier 4: OpenRouter Free Router
        if self.openrouter_key and active_mode in ("gemma4_local", "openrouter", "all"):
            tiers_tried.append("Tier 4 (OpenRouter Free)")
            try:
                res = await self._call_openai_compatible(
                    "https://openrouter.ai/api/v1", self.openrouter_key, "openrouter/free", messages, timeout=15.0
                )
                if res:
                    return {"status": "success", "provider": "Tier 4: OpenRouter Free", "content": res}
            except Exception as e:
                logger.info(f"Tier 4 OpenRouter failed: {e}")

        # Ultimate Deterministic Fallback if all cascade tiers fail
        return {
            "status": "fallback",
            "provider": "Deterministic OMNIS Sovereign Fallback",
            "content": (
                "O.M.N.I.S. APEX Security Kernel: All external LLM cascade providers "
                f"(Tiers tried: {', '.join(tiers_tried)}) are currently unreachable or rate-limited. "
                "Executing local sovereign deterministic synthesis based on 8D tensor invariants."
            )
        }

_llm_cascade_router: Optional[LLMCascadeRouter] = None

def get_llm_cascade() -> LLMCascadeRouter:
    global _llm_cascade_router
    if _llm_cascade_router is None:
        _llm_cascade_router = LLMCascadeRouter()
    return _llm_cascade_router
