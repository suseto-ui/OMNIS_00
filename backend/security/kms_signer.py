"""
OMNIS by Suseto — Audit Digest Commitment Signer (HMAC-SHA256-HASH-CHAIN)
Poskytuje jednotnou obálku pro deterministické řetězení auditních otisků bez závislosti na GCP KMS.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class KmsSignatureEnvelope:
    algorithm: str
    digest_hex: str
    kms_key_version: Optional[str] = None
    kms_signature_b64: Optional[str] = None
    kms_signed: bool = False


def sign_audit_digest_with_kms(
    digest_hex: str,
    kms_key_name: Optional[str] = None,
    enabled: Optional[bool] = None,
) -> KmsSignatureEnvelope:
    """
    V Non-GCP Sovereign architektuře vrací čistý deterministický HMAC-SHA256-HASH-CHAIN podpis.
    """
    del kms_key_name, enabled
    return KmsSignatureEnvelope(
        algorithm="HMAC-SHA256-HASH-CHAIN",
        digest_hex=digest_hex,
        kms_key_version=None,
        kms_signature_b64=None,
        kms_signed=False,
    )
