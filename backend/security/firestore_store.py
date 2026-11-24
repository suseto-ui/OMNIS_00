"""
OMNIS by Suseto — Volitelný Firestore CQRS Projection & Process-Fallback Store.
V Sovereign Non-GCP režimu (PostgreSQL 17 + ElectricSQL) je Firestore výchozím
nastavením vypnutý (`ENABLE_FIRESTORE_CQRS_PROJECTION=false`) a lokální testování
používá deterministický procesní fallback `_FALLBACK_COLLECTIONS`.
"""

from __future__ import annotations

import json
import logging
import os
import threading
import time
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

logger = logging.getLogger("omnis.firestore_store")

try:
    from google.cloud import firestore  # type: ignore[import-untyped]
except ImportError:  # pragma: no cover
    firestore = None


def _is_firestore_projection_enabled() -> bool:
    return os.getenv("ENABLE_FIRESTORE_CQRS_PROJECTION", "false").strip().lower() in ("1", "true", "yes")


def _get_database_id() -> str:
    return os.getenv("FIRESTORE_DATABASE_ID", "").strip()


def _get_firestore_client() -> Optional[Any]:
    if firestore is None or not _is_firestore_projection_enabled():
        return None
    db_id = _get_database_id()
    if not db_id:
        return None
    try:
        return firestore.Client(database=db_id)
    except Exception as exc:
        logger.debug("Firestore client initialization skipped (%s); using process fallback.", exc)
        return None


# Procesní fallback pro čistě lokální/unit-test běh bez Cloud SQL i bez GCP credentials
_FALLBACK_LOCK = threading.Lock()
_FALLBACK_COLLECTIONS: Dict[str, Dict[str, Dict[str, Any]]] = {
    "users": {},
    "user_profiles": {},
    "omnis_sessions": {},
    "audit_logs": {},
    "proof_of_necessity": {},
    "sync_outbox_receipts": {},
}


def fs_get_doc(collection: str, doc_id: str) -> Optional[Dict[str, Any]]:
    client = _get_firestore_client()
    if client is not None:
        try:
            snap = client.collection(collection).document(doc_id).get()
            if snap.exists:
                data = snap.to_dict() or {}
                data.setdefault("id", snap.id)
                return data
            return None
        except Exception as exc:
            logger.warning("Firestore get(%s/%s) failed: %s", collection, doc_id, exc)
    with _FALLBACK_LOCK:
        item = _FALLBACK_COLLECTIONS.setdefault(collection, {}).get(doc_id)
        return dict(item) if item is not None else None


def fs_set_doc(collection: str, doc_id: str, payload: Dict[str, Any], merge: bool = True) -> bool:
    clean_payload = dict(payload)
    clean_payload.setdefault("updatedAtMs", int(time.time() * 1000))
    client = _get_firestore_client()
    if client is not None:
        try:
            client.collection(collection).document(doc_id).set(clean_payload, merge=merge)
            return True
        except Exception as exc:
            logger.warning("Firestore set(%s/%s) failed: %s", collection, doc_id, exc)
    with _FALLBACK_LOCK:
        target = _FALLBACK_COLLECTIONS.setdefault(collection, {})
        if merge and doc_id in target:
            merged = dict(target[doc_id])
            merged.update(clean_payload)
            target[doc_id] = merged
        else:
            target[doc_id] = clean_payload
    return False


def fs_delete_doc(collection: str, doc_id: str) -> bool:
    client = _get_firestore_client()
    if client is not None:
        try:
            client.collection(collection).document(doc_id).delete()
            return True
        except Exception as exc:
            logger.warning("Firestore delete(%s/%s) failed: %s", collection, doc_id, exc)
    with _FALLBACK_LOCK:
        _FALLBACK_COLLECTIONS.setdefault(collection, {}).pop(doc_id, None)
    return False


def fs_list_collection(collection: str, filters: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
    client = _get_firestore_client()
    if client is not None:
        try:
            query = client.collection(collection)
            if filters:
                for key, val in filters.items():
                    query = query.where(key, "==", val)
            docs = []
            for snap in query.stream():
                row = snap.to_dict() or {}
                row.setdefault("id", snap.id)
                docs.append(row)
            return docs
        except Exception as exc:
            logger.warning("Firestore list(%s) failed: %s", collection, exc)
    with _FALLBACK_LOCK:
        rows = list(_FALLBACK_COLLECTIONS.setdefault(collection, {}).values())
    if not filters:
        return [dict(r) for r in rows]
    return [dict(r) for r in rows if all(r.get(k) == v for k, v in filters.items())]


def fs_find_one(collection: str, **filters: Any) -> Optional[Dict[str, Any]]:
    matches = fs_list_collection(collection, filters=filters)
    return matches[0] if matches else None


def project_outbox_items_to_firestore(items: List[Any], caller_uid: str) -> int:
    """
    Volitelná CQRS projekce přijatých Transactional Outbox položek do Cloud Firestore.
    V Non-GCP režimu je výchozím nastavením vypnuta (`ENABLE_FIRESTORE_CQRS_PROJECTION=false`).
    """
    if not _is_firestore_projection_enabled():
        return 0

    projected = 0
    now_iso = datetime.now(timezone.utc).isoformat()
    for item in items:
        try:
            body = json.loads(getattr(item, "payload_json", "{}") or "{}")
            if not isinstance(body, dict):
                body = {}
        except Exception:
            body = {}

        session_id = str(getattr(item, "session_id", "thread_main") or "thread_main")
        idem_key = str(getattr(item, "idempotency_key", ""))
        entity_type = str(getattr(item, "entity_type", "unknown"))

        if idem_key:
            fs_set_doc(
                "sync_outbox_receipts",
                idem_key,
                {
                    "idempotencyKey": idem_key,
                    "entityType": entity_type,
                    "entityId": int(getattr(item, "entity_id", 0)),
                    "sessionId": session_id,
                    "callerUid": caller_uid,
                    "projectedAt": now_iso,
                },
                merge=True,
            )

        session_payload = {
            "sessionId": session_id,
            "userId": str(body.get("userName", caller_uid)),
            "lastSequenceNumber": int(getattr(item, "sequence_number", 0)),
            "lastEntityType": entity_type,
            "domain": str(body.get("domain", "SYSTEMS_INTELLIGENCE")),
            "compositeScore": float(body.get("compositeScore", 0.5)),
            "defenseTier": str(body.get("defenseTier", "APPROVED")),
            "updatedAt": now_iso,
        }
        if body.get("threadTitle"):
            session_payload["title"] = str(body["threadTitle"])
        fs_set_doc("omnis_sessions", session_id, session_payload, merge=True)

        if entity_type in ("proof_of_necessity", "pon", "telemetry_event"):
            fs_set_doc(
                "proof_of_necessity",
                idem_key or f"{session_id}_{getattr(item, 'entity_id', 0)}",
                {
                    "sessionId": session_id,
                    "callerUid": caller_uid,
                    "payload": body,
                    "projectedAt": now_iso,
                },
                merge=True,
            )
        projected += 1
    return projected
