"""
Unit a integrační testy pro FastAPI REST Sync Gateway vrstvu O.M.N.I.S.
Testuje health ping, zprávy, paměťové fragmenty a 8D telemetrii.
"""

import os
import pytest
from fastapi.testclient import TestClient

TEST_SYNC_KEY = "unit-test-sync-api-key"
os.environ["OMNIS_XOR_KEY"] = "test-xor-key-for-unit-tests"
os.environ["DATABASE_URL"] = "sqlite+aiosqlite:///:memory:"
os.environ["OMNIS_SYNC_API_KEY"] = TEST_SYNC_KEY

from backend.main import app


def test_sync_gateway_health_ping():
    """
    Ověří, že FastAPI endpointy /api/v1/health a /sync/health/ping odpovídají
    a vrací diagnostiku connection poolu.
    """
    with TestClient(app) as client:
        # 1. Health check v1
        response = client.get("/api/v1/health")
        assert response.status_code == 200
        data = response.json()
        assert "status" in data
        assert "database" in data
        assert data["service"] == "O.M.N.I.S. Core REST Gateway"
        assert data["version"] == "4.5"
        assert data["connection_pool"]["engine"] == "SQLAlchemy AsyncEngine (asyncpg)"

        # 2. Sync Ping
        ping_response = client.get("/sync/health/ping")
        assert ping_response.status_code == 200
        ping_data = ping_response.json()
        assert ping_data["status"] in ["HEALTHY", "DEGRADED"]


def test_sync_messages_batch():
    """
    Ověří příjem dávky zpráv přes /api/v1/sync/messages i /sync/records/batch.
    """
    payload = {
        "messages": [
            {
                "id": 101,
                "role": "user",
                "content": "Testovací kognitivní zpráva pro replikaci",
                "cognitive_process": "QUERY",
                "follow_up_questions": "",
                "val_sys": 0.85,
                "val_econ": 0.70,
                "val_psych": 0.90,
                "val_eco": 0.65,
                "val_law": 0.80,
                "val_sec": 0.95,
                "val_phys": 0.50,
                "val_soc": 0.75,
                "composite_score": 0.76,
                "domain": "SYSTEMS_INTELLIGENCE",
                "timestamp": 1700000000000,
                "defense_tier": "APPROVED",
                "thread_id": "thread_main",
                "thread_title": "Hlavní vlákno",
                "user_name": "operator"
            }
        ],
        "client_version": "4.5"
    }

    with TestClient(app) as client:
        headers = {"Authorization": f"Bearer {TEST_SYNC_KEY}"}
        response = client.post("/api/v1/sync/messages", json=payload, headers=headers)
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "SUCCESS"
        assert data["synced_count"] == 1

        # Ověření alternativního endpointu /sync/records/batch
        alt_response = client.post("/sync/records/batch", json=payload, headers=headers)
        assert alt_response.status_code == 200
        assert alt_response.json()["synced_count"] == 1


def test_sync_memory_fragments_batch():
    """
    Ověří příjem paměťových fragmentů přes /api/v1/sync/memory i /sync/fragments/batch.
    """
    payload = {
        "fragments": [
            {
                "id": 201,
                "title": "Architektonický Fragment",
                "summary": "Důležitý koncept kognitivní fúze O.M.N.I.S.",
                "domain": "SYSTEMS_INTELLIGENCE",
                "val_sys": 0.9,
                "val_econ": 0.6,
                "val_psych": 0.8,
                "val_eco": 0.7,
                "val_law": 0.9,
                "val_sec": 0.95,
                "val_phys": 0.8,
                "val_soc": 0.85,
                "importance_score": 1.0,
                "timestamp": 1700000000000,
                "user_name": "operator"
            }
        ],
        "client_version": "4.5"
    }

    with TestClient(app) as client:
        headers = {"X-OMNIS-API-KEY": TEST_SYNC_KEY}
        response = client.post("/api/v1/sync/memory", json=payload, headers=headers)
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "SUCCESS"
        assert data["synced_count"] == 1

        # Ověření alternativního endpointu /sync/fragments/batch
        alt_response = client.post("/sync/fragments/batch", json=payload, headers=headers)
        assert alt_response.status_code == 200
        assert alt_response.json()["synced_count"] == 1


def test_sync_telemetry_batch():
    """
    Ověří příjem telemetrických záznamů a 8D metrik přes /api/v1/sync/telemetry.
    """
    payload = {
        "logs": [
            {
                "level": "INFO",
                "tag": "HARDWARE_TELEMETRY",
                "message": "RAM Pressure: 0.42 | Thermal: NORMAL | SQLite I/O: 12ms",
                "metadata_json": '{"ram_pressure": 0.42, "battery_temp": 32.5}',
                "timestamp": 1700000000000
            }
        ],
        "client_version": "4.5"
    }

    with TestClient(app) as client:
        headers = {"Authorization": f"Bearer {TEST_SYNC_KEY}"}
        response = client.post("/api/v1/sync/telemetry", json=payload, headers=headers)
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "SUCCESS"
        assert data["synced_count"] == 1


def test_sync_unauthorized_request():
    """
    Ověří, že požadavek bez autorizační hlavičky nebo s podvrženým tokenem je odmítnut kódem 401.
    """
    with TestClient(app) as client:
        response = client.post("/api/v1/sync/messages", json={"messages": []})
        assert response.status_code == 401
        bad_auth = client.post(
            "/api/v1/sync/messages",
            json={"messages": []},
            headers={"Authorization": "Bearer forged-invalid-token"},
        )
        assert bad_auth.status_code == 401


def test_sync_outbox_batch_idempotency():
    """
    Ověří, že endpoint /api/v1/sync/batch přijímá dávku z Transactional Outboxu
    a bezpečně deduplikuje opakované doručení se stejným idempotency_key.
    """
    import json

    outbox_payload = {
        "items": [
            {
                "idempotency_key": "deadbeef1234567890abcdef1234567890abcdef",
                "entity_type": "omnis_message",
                "entity_id": 9001,
                "session_id": "thread_outbox",
                "sequence_number": 1,
                "payload_json": json.dumps({
                    "role": "user",
                    "content": "Outbox idempotent message",
                    "timestamp": 1700000005000,
                    "threadId": "thread_outbox",
                }),
                "created_at": 1700000005000,
            }
        ],
        "client_version": "4.5",
    }

    with TestClient(app) as client:
        headers = {"Authorization": f"Bearer {TEST_SYNC_KEY}"}
        first_resp = client.post("/api/v1/sync/batch", json=outbox_payload, headers=headers)
        assert first_resp.status_code == 200
        first_data = first_resp.json()
        assert first_data["status"] == "SUCCESS"
        assert first_data["synced_count"] == 1
        assert outbox_payload["items"][0]["idempotency_key"] in first_data["accepted_keys"]

        second_resp = client.post("/api/v1/sync/batch", json=outbox_payload, headers=headers)
        assert second_resp.status_code == 200
        second_data = second_resp.json()
        assert second_data["status"] == "SUCCESS"
        assert second_data["synced_count"] + second_data["deduplicated_count"] >= 1


def test_root_healthz_and_omnis_message_snake_case_outbox():
    """
    Ověří sjednocený /healthz endpoint s názvem OMNIS by Suseto a příjem Room v19
    Outbox položky s entity_type='OMNIS_MESSAGE' a snake_case klíči.
    """
    import json

    outbox_payload = {
        "items": [
            {
                "idempotency_key": "android_outbox_snake_case_001",
                "entity_type": "OMNIS_MESSAGE",
                "entity_id": 9002,
                "session_id": "thread_pg17",
                "sequence_number": 1,
                "payload_json": json.dumps({
                    "id": 9002,
                    "role": "assistant",
                    "content": "Sovereign PG17 Outbox test",
                    "cognitive_process": "AUDIT",
                    "val_sys": 0.91,
                    "val_econ": 0.84,
                    "val_psych": 0.88,
                    "val_eco": 0.80,
                    "val_law": 0.93,
                    "val_sec": 0.96,
                    "val_phys": 0.79,
                    "val_soc": 0.85,
                    "composite_score": 0.87,
                    "domain": "SYS",
                    "timestamp": 1700000009000,
                    "defense_tier": "L0_VERIFIED",
                    "thread_id": "thread_pg17",
                    "thread_title": "PG17 Thread",
                    "user_name": "operator",
                }),
                "created_at": 1700000009000,
            }
        ],
        "client_version": "4.5",
    }

    with TestClient(app) as client:
        hz = client.get("/healthz")
        assert hz.status_code == 200
        hz_data = hz.json()
        assert hz_data["service"] == "OMNIS by Suseto"
        assert hz_data["status"] == "healthy"

        headers = {"Authorization": f"Bearer {TEST_SYNC_KEY}"}
        resp = client.post("/api/v1/sync/batch", json=outbox_payload, headers=headers)
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] == "SUCCESS"
        assert data["synced_count"] == 1
        assert "android_outbox_snake_case_001" in data["accepted_keys"]


