"""
Q_omnis Deterministický PII & Prompt-Injection Sanitizer (backend/security/sanitizer.py)
Anonymizuje osobní údaje (PII) a blokuje injekční / G5 manipulační vektory před LLM voláním.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import List, Pattern, Tuple


_PII_PATTERNS: List[Tuple[str, Pattern[str], str]] = [
    ("EMAIL", re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"), "[REDACTED_EMAIL]"),
    ("IBAN", re.compile(r"\b[A-Z]{2}\d{2}[A-Z0-9]{11,30}\b"), "[REDACTED_IBAN]"),
    ("CZ_BIRTH_NUM", re.compile(r"\b\d{6}/\d{3,4}\b"), "[REDACTED_RC]"),
    ("PHONE", re.compile(r"(?<!\w)(?:\+\d{1,3}[\s.-]?)?\d{3}[\s.-]?\d{3}[\s.-]?\d{3,4}\b"), "[REDACTED_PHONE]"),
    ("IPV4", re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b"), "[REDACTED_IP]"),
    ("JWT", re.compile(r"\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b"), "[REDACTED_TOKEN]"),
]

_INJECTION_PATTERNS: List[Tuple[str, Pattern[str]]] = [
    ("IGNORE_INSTRUCTIONS", re.compile(r"ignore\s+(?:all\s+)?previous\s+instructions", re.IGNORECASE)),
    ("SYSTEM_OVERRIDE", re.compile(r"\bsystem\s+override\b|\bjailbreak\b", re.IGNORECASE)),
    ("BYPASS_SECURITY", re.compile(r"bypass\s+(?:all\s+)?(?:security|filters|guardrails|nis2)", re.IGNORECASE)),
    ("PROMPT_LEAK", re.compile(r"reveal\s+(?:your\s+)?system\s+prompt", re.IGNORECASE)),
]


@dataclass(frozen=True)
class SanitizedPromptResult:
    sanitized_text: str
    is_safe: bool
    pii_redacted_types: List[str] = field(default_factory=list)
    detected_injections: List[str] = field(default_factory=list)


def sanitize_prompt(raw_text: str) -> SanitizedPromptResult:
    """
    Anonymizuje PII a neutralizuje injekční vektory bez ztráty doménového kontextu.
    """
    text = (raw_text or "").strip()
    pii_hits: List[str] = []
    injection_hits: List[str] = []

    for label, pattern, replacement in _PII_PATTERNS:
        if pattern.search(text):
            pii_hits.append(label)
            text = pattern.sub(replacement, text)

    for label, pattern in _INJECTION_PATTERNS:
        if pattern.search(text):
            injection_hits.append(label)
            text = pattern.sub("[ODSTRANĚN_INJEKČNÍ_VEKTOR]", text)

    return SanitizedPromptResult(
        sanitized_text=text,
        is_safe=len(injection_hits) == 0,
        pii_redacted_types=pii_hits,
        detected_injections=injection_hits,
    )
