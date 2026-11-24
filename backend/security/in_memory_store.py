"""
OMNIS by Suseto — Thread-safe In-Memory Store pro mock/unit-test běh bez aktivní DB session.
"""

from __future__ import annotations

import threading
import time
from typing import Any, Dict, List, Optional

_STORE_LOCK = threading.Lock()
_COLLECTIONS: Dict[str, Dict[str, Dict[str, Any]]] = {
    "users": {},
    "user_profiles": {},
    "omnis_sessions": {},
    "audit_logs": {},
    "proof_of_necessity": {},
    "sync_outbox_receipts": {},
}


def store_get_doc(collection: str, doc_id: str) -> Optional[Dict[str, Any]]:
    with _STORE_LOCK:
        item = _COLLECTIONS.setdefault(collection, {}).get(doc_id)
        return dict(item) if item is not None else None


def store_set_doc(collection: str, doc_id: str, payload: Dict[str, Any], merge: bool = True) -> bool:
    clean_payload = dict(payload)
    clean_payload.setdefault("updatedAtMs", int(time.time() * 1000))
    with _STORE_LOCK:
        target = _COLLECTIONS.setdefault(collection, {})
        if merge and doc_id in target:
            merged = dict(target[doc_id])
            merged.update(clean_payload)
            target[doc_id] = merged
        else:
            target[doc_id] = clean_payload
    return True


def store_delete_doc(collection: str, doc_id: str) -> bool:
    with _STORE_LOCK:
        _COLLECTIONS.setdefault(collection, {}).pop(doc_id, None)
    return True


def store_list_collection(collection: str, filters: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
    with _STORE_LOCK:
        rows = list(_COLLECTIONS.setdefault(collection, {}).values())
    if not filters:
        return [dict(r) for r in rows]
    return [dict(r) for r in rows if all(r.get(k) == v for k, v in filters.items())]


def store_find_one(collection: str, **filters: Any) -> Optional[Dict[str, Any]]:
    matches = store_list_collection(collection, filters=filters)
    return matches[0] if matches else None
