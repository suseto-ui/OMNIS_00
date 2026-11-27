"""
OMNIS by Suseto — Security & Cryptographic Subsystem Exports
"""

try:
    from backend.security.auth_utils import (
        ACCESS_TOKEN_EXPIRE_MINUTES,
        ALGORITHM,
        SECRET_KEY,
        create_access_token,
        decode_access_token,
        get_password_hash,
        pwd_context,
        verify_password,
    )
except ImportError:
    ACCESS_TOKEN_EXPIRE_MINUTES = 60
    ALGORITHM = "HS256"
    SECRET_KEY = ""
    create_access_token = None
    decode_access_token = None
    get_password_hash = None
    pwd_context = None
    verify_password = None

try:
    from backend.security.firebase_guard import AuthenticatedCaller, verify_firebase_and_appcheck
except ImportError:
    AuthenticatedCaller = None
    verify_firebase_and_appcheck = None

try:
    from backend.security.kms_signer import KmsSignatureEnvelope, sign_audit_digest_with_kms
except ImportError:
    KmsSignatureEnvelope = None
    sign_audit_digest_with_kms = None

try:
    from backend.security.sanitizer import SanitizedPromptResult, sanitize_prompt
except ImportError:
    SanitizedPromptResult = None
    sanitize_prompt = None

try:
    from backend.security.zk_ledger_service import (
        LedgerAppendRecord,
        LedgerVerificationResult,
        ZkAuditLedgerService,
    )
except ImportError:
    LedgerAppendRecord = None
    LedgerVerificationResult = None
    ZkAuditLedgerService = None

from backend.security.in_memory_store import (
    store_get_doc,
    store_set_doc,
    store_delete_doc,
    store_list_collection,
    store_find_one,
    fs_get_doc,
    fs_set_doc,
    fs_delete_doc,
    fs_list_collection,
    fs_find_one,
)

__all__ = [
    "ACCESS_TOKEN_EXPIRE_MINUTES",
    "ALGORITHM",
    "AuthenticatedCaller",
    "KmsSignatureEnvelope",
    "LedgerAppendRecord",
    "LedgerVerificationResult",
    "SECRET_KEY",
    "SanitizedPromptResult",
    "ZkAuditLedgerService",
    "create_access_token",
    "decode_access_token",
    "get_password_hash",
    "pwd_context",
    "sanitize_prompt",
    "sign_audit_digest_with_kms",
    "verify_firebase_and_appcheck",
    "verify_password",
    "store_get_doc",
    "store_set_doc",
    "store_delete_doc",
    "store_list_collection",
    "store_find_one",
    "fs_get_doc",
    "fs_set_doc",
    "fs_delete_doc",
    "fs_list_collection",
    "fs_find_one",
]
