"""
O.M.N.I.S. APEX v4.5.0-GA - Unified Asynchronous Multi-Provider LLM Cascade & Circuit Breaker Gateway
Tiers:
  1. Local Edge (Ollama / Gemma 4 guided by tools/gemma4/agent.yaml)
  2. GroqCloud API (Free tier, ultra-fast Llama 3 / Gemma)
  3. Google AI Studio (Gemini REST API free tier)
  4. OpenRouter API (OpenRouter free model router)
"""

from __future__ import annotations

import logging
import os
import re
import time
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional

try:
    import httpx
except ImportError:  # pragma: no cover
    httpx = None  # type: ignore[assignment]

try:
    from tenacity import retry, retry_if_exception_type, stop_after_attempt, wait_exponential
except ImportError:  # pragma: no cover
    def retry(*args, **kwargs):
        def decorator(fn):
            return fn
        return decorator

    def stop_after_attempt(*args, **kwargs):
        return None

    def wait_exponential(*args, **kwargs):
        return None

    def retry_if_exception_type(*args, **kwargs):
        return None

logger = logging.getLogger("omnis.llm_cascade")


class CircuitState(str, Enum):
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"


@dataclass
class ProviderCircuitBreaker:
    """Deterministický Circuit Breaker pro externí i lokální LLM poskytovatele."""

    name: str
    failure_threshold: int = 3
    recovery_timeout_seconds: float = 60.0
    clock: Callable[[], float] = time.monotonic

    state: CircuitState = CircuitState.CLOSED
    failure_count: int = 0
    opened_at: Optional[float] = None
    last_error: Optional[str] = None

    def allow_request(self) -> bool:
        if self.state == CircuitState.CLOSED:
            return True
        if self.state == CircuitState.OPEN:
            now = self.clock()
            if self.opened_at is not None and (now - self.opened_at) >= self.recovery_timeout_seconds:
                self.state = CircuitState.HALF_OPEN
                return True
            return False
        # HALF_OPEN: povolí jeden zkušební požadavek
        return True

    def record_success(self) -> None:
        self.failure_count = 0
        self.opened_at = None
        self.last_error = None
        self.state = CircuitState.CLOSED

    def record_failure(self, error: str = "") -> None:
        self.failure_count += 1
        self.last_error = error[:240] if error else self.last_error
        if self.state == CircuitState.HALF_OPEN or self.failure_count >= self.failure_threshold:
            self.state = CircuitState.OPEN
            self.opened_at = self.clock()

    def snapshot(self) -> Dict[str, Any]:
        return {
            "provider": self.name,
            "state": self.state.value,
            "failure_count": self.failure_count,
            "failure_threshold": self.failure_threshold,
            "recovery_timeout_seconds": self.recovery_timeout_seconds,
            "last_error": self.last_error,
        }


def load_gemma4_agent_config(config_path: Optional[Path] = None) -> Dict[str, Any]:
    """Načte konfiguraci Gemma 4 z `tools/gemma4/agent.yaml` bez tvrdé závislosti na PyYAML."""
    defaults: Dict[str, Any] = {
        "agent_name": "gemma4-patch-specialist",
        "adapter_path": "adapters/adapter_model.safetensors",
        "context_window": 8192,
        "temperature": 0.1,
    }
    target = config_path or (Path(__file__).resolve().parents[2] / "tools" / "gemma4" / "agent.yaml")
    if not target.is_file():
        return defaults

    raw_text = target.read_text(encoding="utf-8")
    name_match = re.search(r'^\s*agent_name:\s*"?([^"\n#]+)"?', raw_text, re.MULTILINE)
    adapter_match = re.search(r'^\s*adapter_path:\s*"?([^"\n#]+)"?', raw_text, re.MULTILINE)
    ctx_match = re.search(r'^\s*context_window:\s*(\d+)', raw_text, re.MULTILINE)
    temp_match = re.search(r'^\s*temperature:\s*([0-9]+(?:\.[0-9]+)?)', raw_text, re.MULTILINE)

    if name_match:
        defaults["agent_name"] = name_match.group(1).strip()
    if adapter_match:
        defaults["adapter_path"] = adapter_match.group(1).strip()
    if ctx_match:
        defaults["context_window"] = max(512, int(ctx_match.group(1)))
    if temp_match:
        defaults["temperature"] = min(2.0, max(0.0, float(temp_match.group(1))))
    return defaults


class LLMCascadeRouter:
    def __init__(self, agent_config_path: Optional[Path] = None):
        self.gemma_config = load_gemma4_agent_config(agent_config_path)
        self.gemma_base_url = os.getenv("GEMMA4_API_BASE_URL", "http://host.docker.internal:11434/v1")
        self.gemma_model = os.getenv("GEMMA4_MODEL_NAME", "gemma4:e4b")
        self.groq_key = os.getenv("GROQ_API_KEY", "")
        self.gemini_key = os.getenv("GEMINI_API_KEY", "")
        self.openrouter_key = os.getenv("OPENROUTER_API_KEY", "")
        self.mode = os.getenv("LLM_PROVIDER_MODE", "gemma4_local").lower()
        self.breakers: Dict[str, ProviderCircuitBreaker] = {
            "gemma4_local": ProviderCircuitBreaker(name="gemma4_local"),
            "groq": ProviderCircuitBreaker(name="groq"),
            "gemini": ProviderCircuitBreaker(name="gemini"),
            "openrouter": ProviderCircuitBreaker(name="openrouter"),
        }

    def get_breaker_status(self) -> Dict[str, Any]:
        return {
            "mode": self.mode,
            "gemma4_agent": self.gemma_config,
            "breakers": {k: b.snapshot() for k, b in self.breakers.items()},
        }

    @retry(
        stop=stop_after_attempt(2),
        wait=wait_exponential(multiplier=1, min=1, max=4),
        retry=retry_if_exception_type(
            (httpx.RequestError, httpx.TimeoutException) if httpx is not None else (OSError, TimeoutError)
        ),
        reraise=False,
    )
    async def _call_openai_compatible(
        self,
        base_url: str,
        api_key: str,
        model: str,
        messages: List[Dict[str, str]],
        timeout: float = 15.0,
        temperature: float = 0.3,
        max_tokens: int = 1500,
    ) -> Optional[str]:
        if httpx is None:
            return None
        headers = {"Content-Type": "application/json"}
        if api_key:
            headers["Authorization"] = f"Bearer {api_key}"

        payload = {
            "model": model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
        }

        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.post(f"{base_url.rstrip('/')}/chat/completions", json=payload, headers=headers)
            if resp.status_code == 200:
                data = resp.json()
                return data["choices"][0]["message"]["content"]
            logger.warning("Provider at %s returned status %s", base_url, resp.status_code)
        return None

    @retry(
        stop=stop_after_attempt(2),
        wait=wait_exponential(multiplier=1, min=1, max=4),
        retry=retry_if_exception_type(
            (httpx.RequestError, httpx.TimeoutException) if httpx is not None else (OSError, TimeoutError)
        ),
        reraise=False,
    )
    async def _call_gemini_rest(
        self, api_key: str, messages: List[Dict[str, str]], timeout: float = 15.0
    ) -> Optional[str]:
        if not api_key or httpx is None:
            return None
        model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
        headers = {"x-goog-api-key": api_key, "Content-Type": "application/json"}

        formatted_contents = []
        for m in messages:
            role = "user" if m.get("role") == "user" else "model"
            formatted_contents.append({"role": role, "parts": [{"text": m.get("content", "")}]})

        payload = {
            "contents": formatted_contents,
            "generationConfig": {"temperature": float(self.gemma_config.get("temperature", 0.1))},
        }
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.post(url, json=payload, headers=headers)
            if resp.status_code == 200:
                data = resp.json()
                candidates = data.get("candidates", [])
                if candidates:
                    parts = candidates[0].get("content", {}).get("parts", [])
                    if parts:
                        return parts[0].get("text", "")
            logger.warning("Gemini REST API returned status %s", resp.status_code)
        return None

    async def generate_completion(
        self, messages: List[Dict[str, str]], override_mode: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Spustí kaskádové volání LLM poskytovatelů s Circuit Breakerem a automatickým failoverem.
        """
        active_mode = (override_mode or self.mode).lower()
        tiers_tried: List[str] = []
        skipped_open_breakers: List[str] = []

        # Tier 1: Local Ollama / Gemma 4 (řízeno přes tools/gemma4/agent.yaml)
        if active_mode in ("gemma4_local", "all") or not active_mode:
            breaker = self.breakers["gemma4_local"]
            if breaker.allow_request():
                tiers_tried.append("Tier 1 (Local Ollama / Gemma 4)")
                try:
                    local_max_tokens = min(2048, int(self.gemma_config.get("context_window", 8192)) // 4)
                    res = await self._call_openai_compatible(
                        self.gemma_base_url,
                        "",
                        self.gemma_model,
                        messages,
                        timeout=10.0,
                        temperature=float(self.gemma_config.get("temperature", 0.1)),
                        max_tokens=local_max_tokens,
                    )
                    if res:
                        breaker.record_success()
                        return {
                            "status": "success",
                            "provider": "Tier 1: Local Gemma 4",
                            "agent_config": self.gemma_config,
                            "content": res,
                        }
                    breaker.record_failure("Empty or non-200 response from Local Gemma 4")
                except Exception as exc:
                    breaker.record_failure(str(exc))
                    logger.info("Tier 1 local inference failed: %s", exc)
            else:
                skipped_open_breakers.append("gemma4_local")

        # Tier 2: GroqCloud API
        if self.groq_key and active_mode in ("gemma4_local", "groq", "all"):
            breaker = self.breakers["groq"]
            if breaker.allow_request():
                tiers_tried.append("Tier 2 (GroqCloud)")
                try:
                    res = await self._call_openai_compatible(
                        "https://api.groq.com/openai/v1",
                        self.groq_key,
                        "llama-3.3-70b-versatile",
                        messages,
                        timeout=12.0,
                    )
                    if res:
                        breaker.record_success()
                        return {"status": "success", "provider": "Tier 2: GroqCloud", "content": res}
                    breaker.record_failure("Empty or non-200 response from GroqCloud")
                except Exception as exc:
                    breaker.record_failure(str(exc))
                    logger.info("Tier 2 GroqCloud failed: %s", exc)
            else:
                skipped_open_breakers.append("groq")

        # Tier 3: Google AI Studio Gemini Free Tier
        if self.gemini_key and active_mode in ("gemma4_local", "gemini", "all"):
            breaker = self.breakers["gemini"]
            if breaker.allow_request():
                tiers_tried.append("Tier 3 (Google Gemini Free Tier)")
                try:
                    res = await self._call_gemini_rest(self.gemini_key, messages, timeout=12.0)
                    if res:
                        breaker.record_success()
                        return {"status": "success", "provider": "Tier 3: Google Gemini", "content": res}
                    breaker.record_failure("Empty or non-200 response from Gemini REST")
                except Exception as exc:
                    breaker.record_failure(str(exc))
                    logger.info("Tier 3 Gemini failed: %s", exc)
            else:
                skipped_open_breakers.append("gemini")

        # Tier 4: OpenRouter Free Router
        if self.openrouter_key and active_mode in ("gemma4_local", "openrouter", "all"):
            breaker = self.breakers["openrouter"]
            if breaker.allow_request():
                tiers_tried.append("Tier 4 (OpenRouter Free)")
                try:
                    res = await self._call_openai_compatible(
                        "https://openrouter.ai/api/v1",
                        self.openrouter_key,
                        "openrouter/free",
                        messages,
                        timeout=15.0,
                    )
                    if res:
                        breaker.record_success()
                        return {"status": "success", "provider": "Tier 4: OpenRouter Free", "content": res}
                    breaker.record_failure("Empty or non-200 response from OpenRouter")
                except Exception as exc:
                    breaker.record_failure(str(exc))
                    logger.info("Tier 4 OpenRouter failed: %s", exc)
            else:
                skipped_open_breakers.append("openrouter")

        return {
            "status": "fallback",
            "provider": "Deterministic OMNIS Sovereign Fallback",
            "tiers_tried": tiers_tried,
            "open_circuit_breakers": skipped_open_breakers,
            "content": (
                "O.M.N.I.S. APEX Security Kernel: All external LLM cascade providers "
                f"(Tiers tried: {', '.join(tiers_tried) or 'NONE'}; Open breakers: {', '.join(skipped_open_breakers) or 'NONE'}) "
                "are currently unreachable or isolated. Executing local sovereign deterministic synthesis based on 8D tensor invariants."
            ),
        }


_llm_cascade_router: Optional[LLMCascadeRouter] = None


def get_llm_cascade() -> LLMCascadeRouter:
    global _llm_cascade_router
    if _llm_cascade_router is None:
        _llm_cascade_router = LLMCascadeRouter()
    return _llm_cascade_router
