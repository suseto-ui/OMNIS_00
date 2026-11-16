"""
O.M.N.I.S. Optional Cloud KMS Asymmetric Signer (ECDSA P-256 SHA-256)
Poskytuje volitelný HW/KMS podpis auditních závazků při nastavení CLOUD_KMS_KEY_NAME
a deterministický HMAC-SHA256-HASH-CHAIN fallback bez externí závislosti.
"""

from __future__ import annotations

import base64
import logging
import os
from dataclasses import dataclass
from typing import Optional

logger = logging.getLogger("omnis.security.kms")


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
    Pokud je ENABLE_CLOUD_KMS=true a je nakonfigurován CLOUD_KMS_KEY_NAME
    (např. projects/.../locations/.../keyRings/.../cryptoKeys/.../cryptoKeyVersions/1),
    podepíše SHA-256 otisk pomocí Google Cloud KMS ECDSA_P256_SHA256.
    Jinak bezpečně vrátí deterministický HMAC-SHA256-HASH-CHAIN fallback.
    """
    is_enabled = (
        enabled
        if enabled is not None
        else os.getenv("ENABLE_CLOUD_KMS", "false").strip().lower() in ("1", "true", "yes")
    )
    resolved_key = (kms_key_name or os.getenv("CLOUD_KMS_KEY_NAME", "")).strip()

    if not is_enabled or not resolved_key:
        return KmsSignatureEnvelope(
            algorithm="HMAC-SHA256-HASH-CHAIN",
            digest_hex=digest_hex,
            kms_key_version=None,
            kms_signature_b64=None,
            kms_signed=False,
        )

    try:
        from google.cloud import kms  # type: ignore[import-untyped]

        raw_digest = bytes.fromhex(digest_hex)
        client = kms.KeyManagementServiceClient()
        response = client.asymmetric_sign(
            request={
                "name": resolved_key,
                "digest": {"sha256": raw_digest},
            },
            timeout=2.0,
        )
        sig_b64 = base64.b64encode(response.signature).decode("ascii")
        return KmsSignatureEnvelope(
            algorithm="CLOUD-KMS-ECDSA-P256-SHA256",
            digest_hex=digest_hex,
            kms_key_version=resolved_key,
            kms_signature_b64=sig_b64,
            kms_signed=True,
        )
    except Exception as exc:
        logger.warning("Cloud KMS signature unavailable (%s); falling back to HMAC-SHA256-HASH-CHAIN.", exc)
        return KmsSignatureEnvelope(
            algorithm="HMAC-SHA256-HASH-CHAIN",
            digest_hex=digest_hex,
            kms_key_version=None,
            kms_signature_b64=None,
            kms_signed=False,
        )
