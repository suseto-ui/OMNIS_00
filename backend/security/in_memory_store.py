"""
OMNIS by Suseto — Thread-safe In-Memory Store pro mock/unit-test běh bez aktivní DB session.
Zajišťuje jak rozhraní store_* tak kompatibilitní aliasy fs_* pro kompletní paritu napříč backendem.
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


def store_get_doc(collection: str, doc_id: str | int | Any) -> Optional[Dict[str, Any]]:
    """Vrátí dokument z kolekce dle ID jako kopii slovníku nebo None."""
    with _STORE_LOCK:
        item = _COLLECTIONS.setdefault(collection, {}).get(str(doc_id))
        return dict(item) if item is not None else None


def store_set_doc(
    collection: str,
    doc_id: str | int | Any,
    payload: Dict[str, Any],
    merge: bool = True,
) -> bool:
    """Uloží nebo sloučí dokument do kolekce pod daným ID."""
    clean_payload = dict(payload)
    clean_payload.setdefault("updatedAtMs", int(time.time() * 1000))
    with _STORE_LOCK:
        target = _COLLECTIONS.setdefault(collection, {})
        str_id = str(doc_id)
        if merge and str_id in target:
            merged = dict(target[str_id])
            merged.update(clean_payload)
            target[str_id] = merged
        else:
            target[str_id] = clean_payload
    return True


def store_delete_doc(collection: str, doc_id: str | int | Any) -> bool:
    """Odstraní dokument z kolekce. Vrací True."""
    with _STORE_LOCK:
        _COLLECTIONS.setdefault(collection, {}).pop(str(doc_id), None)
    return True


def store_list_collection(
    collection: str,
    filters: Optional[Dict[str, Any]] = None,
    **kwargs: Any,
) -> List[Dict[str, Any]]:
    """
    Vrátí seznam dokumentů z kolekce.
    Podporuje jak předání dict do 'filters', tak kwargs filtry (např. username='operator').
    """
    merged_filters: Dict[str, Any] = {}
    if filters and isinstance(filters, dict):
        merged_filters.update(filters)
    if kwargs:
        merged_filters.update(kwargs)

    with _STORE_LOCK:
        rows = list(_COLLECTIONS.setdefault(collection, {}).values())

    if not merged_filters:
        return [dict(r) for r in rows]

    return [
        dict(r) for r in rows
        if all(r.get(k) == v for k, v in merged_filters.items())
    ]


def store_find_one(
    collection: str,
    filters: Optional[Dict[str, Any]] = None,
    **kwargs: Any,
) -> Optional[Dict[str, Any]]:
    """Vyhledá a vrátí první dokument odpovídající filtrům nebo None."""
    matches = store_list_collection(collection, filters=filters, **kwargs)
    return matches[0] if matches else None


# 1:1 Parita veřejného rozhraní pro všechny existující importy fs_* v backendu
fs_get_doc = store_get_doc
fs_set_doc = store_set_doc
fs_delete_doc = store_delete_doc
fs_list_collection = store_list_collection
fs_find_one = store_find_one

__all__ = [
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
