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
from dataclasses import dataclass, field
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


def parse_retry_after_seconds(header_value: Optional[str], default: float = 15.0) -> float:
    """Extrahuje Retry-After (v sekundách) a bezpečně jej omezí do [5.0, 300.0]."""
    if not header_value:
        return min(300.0, max(5.0, float(default)))
    try:
        parsed = float(str(header_value).strip())
        if parsed > 0:
            return min(300.0, max(5.0, parsed))
    except (TypeError, ValueError):
        pass
    return min(300.0, max(5.0, float(default)))


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
    cooldown_until: Optional[float] = None
    last_error: Optional[str] = None
    last_status_code: Optional[int] = None
    retry_after_seconds: Optional[float] = None
    events: List[Dict[str, Any]] = field(default_factory=list)

    def _append_event(self, event_type: str, detail: str, status_code: Optional[int] = None) -> None:
        entry = {
            "provider": self.name,
            "state": self.state.value,
            "event": event_type,
            "status_code": status_code,
            "detail": detail[:240],
            "cooldown_remaining_seconds": self.cooldown_remaining_seconds(),
        }
        self.events.append(entry)
        if len(self.events) > 20:
            self.events = self.events[-20:]

    def cooldown_remaining_seconds(self) -> float:
        if self.state != CircuitState.OPEN:
            return 0.0
        now = self.clock()
        if self.cooldown_until is not None:
            return round(max(0.0, self.cooldown_until - now), 2)
        if self.opened_at is not None:
            return round(max(0.0, (self.opened_at + self.recovery_timeout_seconds) - now), 2)
        return 0.0

    def allow_request(self) -> bool:
        if self.state == CircuitState.CLOSED:
            return True
        if self.state == CircuitState.OPEN:
            now = self.clock()
            deadline = self.cooldown_until
            if deadline is None and self.opened_at is not None:
                deadline = self.opened_at + self.recovery_timeout_seconds
            if deadline is not None and now >= deadline:
                self.state = CircuitState.HALF_OPEN
                self.cooldown_until = None
                self._append_event("HALF_OPEN_PROBE", "Cooldown elapsed; testing recovery")
                return True
            return False
        # HALF_OPEN: povolí jeden zkušební požadavek
        return True

    def record_success(self) -> None:
        was_degraded = self.state != CircuitState.CLOSED
        self.failure_count = 0
        self.opened_at = None
        self.cooldown_until = None
        self.last_error = None
        self.last_status_code = None
        self.retry_after_seconds = None
        self.state = CircuitState.CLOSED
        if was_degraded:
            self._append_event("RECOVERED_CLOSED", "Provider recovered to CLOSED")

    def record_failure(
        self,
        error: str = "",
        status_code: Optional[int] = None,
        retry_after_seconds: Optional[float] = None,
    ) -> None:
        self.failure_count += 1
        self.last_error = error[:240] if error else self.last_error
        if status_code is not None:
            self.last_status_code = int(status_code)

        should_open = (
            self.state == CircuitState.HALF_OPEN
            or self.failure_count >= self.failure_threshold
            or status_code == 429
        )
        if should_open:
            now = self.clock()
            effective_cooldown = (
                min(300.0, max(5.0, float(retry_after_seconds)))
                if retry_after_seconds is not None
                else float(self.recovery_timeout_seconds)
            )
            self.retry_after_seconds = effective_cooldown if retry_after_seconds is not None else None
            self.state = CircuitState.OPEN
            self.opened_at = now
            self.cooldown_until = now + effective_cooldown
            self._append_event(
                "CIRCUIT_OPENED",
                error or f"Tripped (HTTP {status_code})",
                status_code=status_code,
            )
            logger.warning(
                "CircuitBreaker OPEN provider=%s status_code=%s failures=%s cooldown_s=%.1f",
                self.name,
                status_code,
                self.failure_count,
                effective_cooldown,
            )
        else:
            self._append_event("FAILURE_RECORDED", error or "Transient provider error", status_code=status_code)

    def snapshot(self) -> Dict[str, Any]:
        return {
            "provider": self.name,
            "state": self.state.value,
            "failure_count": self.failure_count,
            "failure_threshold": self.failure_threshold,
            "recovery_timeout_seconds": self.recovery_timeout_seconds,
            "cooldown_remaining_seconds": self.cooldown_remaining_seconds(),
            "last_status_code": self.last_status_code,
            "retry_after_seconds": self.retry_after_seconds,
            "last_error": self.last_error,
            "recent_events": list(self.events[-5:]),
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
        self._last_http_failures: Dict[str, Dict[str, Any]] = {}

    def _record_http_failure_meta(self, provider: str, status_code: int, retry_after_header: Optional[str]) -> None:
        retry_after = parse_retry_after_seconds(retry_after_header) if status_code == 429 else None
        self._last_http_failures[provider] = {
            "status_code": status_code,
            "retry_after_seconds": retry_after,
        }

    def get_breaker_status(self) -> Dict[str, Any]:
        snapshots = {k: b.snapshot() for k, b in self.breakers.items()}
        tripped = [k for k, b in self.breakers.items() if b.state != CircuitState.CLOSED]
        max_cooldown = max((s["cooldown_remaining_seconds"] for s in snapshots.values()), default=0.0)
        return {
            "mode": self.mode,
            "degraded_mode": len(tripped) > 0,
            "tripped_providers": tripped,
            "max_cooldown_remaining_seconds": max_cooldown,
            "gemma4_agent": self.gemma_config,
            "breakers": snapshots,
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
        provider_key: Optional[str] = None,
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
                if provider_key:
                    self._last_http_failures.pop(provider_key, None)
                data = resp.json()
                return data["choices"][0]["message"]["content"]
            if provider_key:
                self._record_http_failure_meta(provider_key, resp.status_code, resp.headers.get("Retry-After"))
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
                self._last_http_failures.pop("gemini", None)
                data = resp.json()
                candidates = data.get("candidates", [])
                if candidates:
                    parts = candidates[0].get("content", {}).get("parts", [])
                    if parts:
                        return parts[0].get("text", "")
            self._record_http_failure_meta("gemini", resp.status_code, resp.headers.get("Retry-After"))
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
                        provider_key="gemma4_local",
                    )
                    if res:
                        breaker.record_success()
                        return {
                            "status": "success",
                            "provider": "Tier 1: Local Gemma 4",
                            "agent_config": self.gemma_config,
                            "content": res,
                        }
                    meta = self._last_http_failures.pop("gemma4_local", {})
                    breaker.record_failure(
                        "Empty or non-200 response from Local Gemma 4",
                        status_code=meta.get("status_code"),
                        retry_after_seconds=meta.get("retry_after_seconds"),
                    )
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
                        provider_key="groq",
                    )
                    if res:
                        breaker.record_success()
                        return {"status": "success", "provider": "Tier 2: GroqCloud", "content": res}
                    meta = self._last_http_failures.pop("groq", {})
                    breaker.record_failure(
                        "Empty or non-200 response from GroqCloud",
                        status_code=meta.get("status_code"),
                        retry_after_seconds=meta.get("retry_after_seconds"),
                    )
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
                    meta = self._last_http_failures.pop("gemini", {})
                    breaker.record_failure(
                        "Empty or non-200 response from Gemini REST",
                        status_code=meta.get("status_code"),
                        retry_after_seconds=meta.get("retry_after_seconds"),
                    )
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
                        provider_key="openrouter",
                    )
                    if res:
                        breaker.record_success()
                        return {"status": "success", "provider": "Tier 4: OpenRouter Free", "content": res}
                    meta = self._last_http_failures.pop("openrouter", {})
                    breaker.record_failure(
                        "Empty or non-200 response from OpenRouter",
                        status_code=meta.get("status_code"),
                        retry_after_seconds=meta.get("retry_after_seconds"),
                    )
                except Exception as exc:
                    breaker.record_failure(str(exc))
                    logger.info("Tier 4 OpenRouter failed: %s", exc)
            else:
                skipped_open_breakers.append("openrouter")

        breaker_status = self.get_breaker_status()
        return {
            "status": "fallback",
            "degraded_mode": True,
            "provider": "Deterministic OMNIS Sovereign Fallback",
            "tiers_tried": tiers_tried,
            "open_circuit_breakers": skipped_open_breakers,
            "breaker_status": breaker_status,
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
