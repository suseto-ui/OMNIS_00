import os
import logging
from unittest.mock import patch, MagicMock
from fastapi.testclient import TestClient

# Set dummy DATABASE_URL environment variable before importing main to prevent bootstrap failures
os.environ["OMNIS_XOR_KEY"] = "test-xor-key-for-unit-tests"
os.environ["DATABASE_URL"] = "sqlite+aiosqlite:///:memory:"

from backend.main import app

def test_aggregated_routers_are_mounted():
    """
    Verify that all aggregated architectural blueprint routers 
    are correctly initialized and registered onto the FastAPI application instance.
    """
    # Collect all registered route path endpoints from the live application instance
    routes = [route.path for route in app.routes]
    
    # Expected endpoints checking prefixes and path parameters matching the decoupled routers
    expected_paths = [
        "/healthz",
        "/api/health-check",
        "/api/query",
        "/api/query/stream",
        "/api/omnis/synthesize-5phases",
        "/api/feedback",
        "/api/autopoiesis/status",
        "/api/adversarial-review",
        "/api/multi-agent/agents",
        "/api/multi-agent/deliberate",
        "/api/memory",
        "/api/conversations",
        "/api/epistemic/ingest",
        "/api/epistemic/ingest-text",
        "/api/epistemic/stats",
        "/api/epistemic/export",
        "/omnis/phase-4/evaluate-matrix",
        "/api/dev/token-telemetry",
        "/api/dev/estimate-tokens",
        "/api/dev/reset-tokens",
        "/api/admin/set-role",
        "/api/audit/pon",
        "/api/v1/health",
        "/api/v1/sync/batch",
        "/api/v1/inference/analyze",
    ]
    
    for path in expected_paths:
        assert path in routes, f"Critical Architecture Blueprint Failure: Path '{path}' is not mounted."

def test_system_router_health():
    """
    Perform a live end-to-end integration check against the system health probe router.
    """
    with TestClient(app) as client:
        response = client.get("/healthz")
        assert response.status_code == 200
        payload = response.json()
        assert payload["status"] == "healthy"
        assert payload["service"] == "OMNIS by Suseto"

        api_response = client.get("/api/health-check")
        assert api_response.status_code == 200

def test_gemini_api_outage_zero_simulation_fallback():
    """
    Integration test verifying the Zero-Simulation Policy guardrail.
    When Gemini API is unavailable or unconfigured, the system must not hallucinate
    or fail with 500, but return a clean, structured diagnostic fallback with 0.0 scores.
    """
    from backend.auth_router import get_current_user
    from backend.models import User

    test_user = User(
        id="user-query-test-1",
        username="query_tester",
        email="query@example.com",
        role="STANDARD_USER",
        is_active=True,
    )
    app.dependency_overrides[get_current_user] = lambda: test_user
    try:
        # Force the cognitive service client to be None, simulating an API outage or missing credentials
        with patch("backend.gemini_service.cognitive_service._client", None):
            with TestClient(app) as client:
                payload = {
                    "query": "Kritický test integrity systému při výpadku LLM",
                    "ontology_domain": "CYBERNETICS",
                    "enable_thinking": True
                }
                response = client.post("/api/query", json=payload)

                assert response.status_code == 200
                data = response.json()
                assert "Zero-Simulation Policy" in data["answer"]
                assert "⚠️ **Upozornění:**" in data["answer"]
                assert data["impact_matrix"]["composite_score"] == 0.0
                assert "Falešná simulace je zakázána." in data["impact_matrix"]["reasoning"]
    finally:
        app.dependency_overrides.pop(get_current_user, None)

def test_spa_fallback_removed_for_api_only_backend():
    """
    Verify that legacy SPA wildcard routes are removed after TypeScript frontend cleanup,
    while valid API routes remain mounted.
    """
    with TestClient(app) as client:
        health_resp = client.get("/healthz")
        assert health_resp.status_code == 200
        spa_route_exists = any(
            route.path == "/{full_path:path}" and "serve_spa" in str(route.endpoint)
            for route in app.routes
        )
        assert not spa_route_exists, "Legacy SPA fallback route must be removed."
        assert client.get("/nonexistent-spa-route").status_code == 404

def test_secret_masking_integrity():
    """
    Verify that SecretMasker correctly redacts sensitive keys from log records.
    """
    from backend.gemini_service import SecretMasker
    
    sensitive_key = "x-gemini-key-12345-super-secret"
    masker = SecretMasker([sensitive_key])
    
    # Vytvoření falešného log záznamu obsahujícího klíč
    record = logging.LogRecord(
        name="omnis.test",
        level=logging.ERROR,
        pathname="test_routers.py",
        lineno=10,
        msg=f"Failed to connect using key {sensitive_key} to provider.",
        args=(),
        exc_info=None
    )
    
    # Aplikace filtru
    masker.filter(record)
    
    assert sensitive_key not in record.msg, "Sensitive key was leaked into the log message!"
    assert "[REDACTED_SECRET]" in record.msg, "SecretMasker failed to apply the redaction placeholder."


def test_autopoietic_status_and_resilient_feedback():
    """
    Verify that autopoietic status endpoint is operational and feedback submission
    handles transient message IDs gracefully without failing.
    """
    from backend.auth_router import get_current_user
    from backend.models import User

    test_user = User(
        id="user-auto-test-1",
        username="auto_tester",
        email="auto@example.com",
        role="STANDARD_USER",
        is_active=True,
    )
    app.dependency_overrides[get_current_user] = lambda: test_user
    try:
        with TestClient(app) as client:
            # 1. Test status endpoint
            resp = client.get("/api/autopoiesis/status")
            assert resp.status_code == 200
            data = resp.json()
            assert data["status"] == "active"
            assert "calibration_weights" in data

            # 2. Test resilient feedback submission
            feedback_payload = {
                "message_id": "00000000-0000-0000-0000-000000000001",
                "conversation_id": "00000000-0000-0000-0000-000000000002",
                "user_rating": 5,
                "feedback_text": "Vynikající syntéza s vysokou přesností"
            }
            fb_resp = client.post("/api/feedback", json=feedback_payload)
            assert fb_resp.status_code == 200
            fb_data = fb_resp.json()
            assert fb_data["status"] == "success"
            assert fb_data["adaptation_delta"] == 0.1
    finally:
        app.dependency_overrides.pop(get_current_user, None)


def test_multi_agent_deliberation_endpoint():
    """
    Verify that multi-agent deliberation endpoints return valid perspectives and consensus.
    """
    from backend.auth_router import get_current_user
    from backend.models import User

    test_user = User(
        id="user-ma-test-1",
        username="ma_tester",
        email="ma@example.com",
        role="STANDARD_USER",
        is_active=True,
    )
    app.dependency_overrides[get_current_user] = lambda: test_user
    try:
        with TestClient(app) as client:
            # 1. Test agents list
            resp = client.get("/api/multi-agent/agents")
            assert resp.status_code == 200
            agents_data = resp.json()
            assert "agents" in agents_data
            assert agents_data["total_count"] >= 4

            # 2. Test deliberation
            deliberate_payload = {
                "query": "Optimalizace architektury O.M.N.I.S. pro nulovou latenci a škálování na miliony vektorů",
                "current_answer": "Použijeme pgvector s HNSW indexem a SSE streaming pro průběžné vykreslování.",
                "ontology_domain": "SYSTEMS_INTELLIGENCE"
            }
            delib_resp = client.post("/api/multi-agent/deliberate", json=deliberate_payload)
            assert delib_resp.status_code == 200
            data = delib_resp.json()
            assert "consensus_score" in data
            assert "consensus_status" in data
            assert len(data["perspectives"]) >= 4
            assert "synthesis_action" in data
    finally:
        app.dependency_overrides.pop(get_current_user, None)


def test_epistemic_ingest_and_stats():
    """
    Verify that epistemic document chunking, stats, and admin-only export endpoints function properly.
    """
    from backend.auth_router import get_current_user
    from backend.models import User

    admin_user = User(
        id="user-epistemic-adm-1",
        username="epistemic_admin",
        email="epistemic_admin@example.com",
        role="ADMIN_OPERATOR",
        is_active=True,
    )
    app.dependency_overrides[get_current_user] = lambda: admin_user
    try:
        with TestClient(app) as client:
            # 1. Stats endpoint
            stats_resp = client.get("/api/epistemic/stats")
            assert stats_resp.status_code == 200
            stats_data = stats_resp.json()
            assert stats_data["status"] in ["healthy", "operational"]
            assert stats_data["vector_dimension"] == 768

            # 2. Ingest text endpoint
            ingest_payload = {
                "title": "Architektura O.M.N.I.S. 2026",
                "content": "O.M.N.I.S. implementuje 5 kognitivních fází s transdisciplinární maticí 8D.",
                "domain": "SYSTEMS_INTELLIGENCE",
                "chunk_size": 200,
                "chunk_overlap": 50,
                "importance_score": 1.5
            }
            ingest_resp = client.post("/api/epistemic/ingest-text", json=ingest_payload)
            assert ingest_resp.status_code == 200
            ingest_data = ingest_resp.json()
            assert ingest_data["status"] == "queued"
            assert ingest_data["total_chunks"] >= 1

            # 3. Export endpoint (admin)
            export_resp = client.get("/api/epistemic/export?limit=5")
            assert export_resp.status_code == 200
            export_data = export_resp.json()
            assert "export_count" in export_data
            assert "items" in export_data
    finally:
        app.dependency_overrides.pop(get_current_user, None)


def test_protected_endpoints_enforce_authentication_and_rbac():
    """
    Verify that protected admin, dev, audit, cognitive, epistemic, system, and thread endpoints
    reject unauthenticated requests with 401 and enforce ADMIN_OPERATOR RBAC (403).
    """
    from backend.auth_router import get_current_user
    from backend.models import User

    with TestClient(app) as client:
        assert client.get("/api/threads").status_code == 401
        assert client.get("/api/conversations").status_code == 401
        assert client.get("/api/memory").status_code == 401
        assert client.post("/api/query", json={"query": "test"}).status_code == 401
        assert client.get("/api/epistemic/stats").status_code == 401
        assert client.get("/api/epistemic/export").status_code == 401
        assert client.get("/api/system/db-check").status_code == 401
        assert client.get("/api/system/gemini-check").status_code == 401
        assert client.get("/api/autopoiesis/status").status_code == 401
        assert client.get("/api/multi-agent/agents").status_code == 401
        assert client.get("/api/v1/inference/cascade/status").status_code == 401
        assert client.get("/api/dev/token-telemetry").status_code == 401
        assert client.post("/api/dev/reset-tokens").status_code == 401
        assert client.post("/api/admin/set-role?target_email=x@example.com&new_role=ADMIN_OPERATOR").status_code == 401
        assert client.post(
            "/api/audit/pon",
            json={
                "actionType": "TEST",
                "details": {},
                "triggerVector": "v",
                "exclusionProof": "p",
                "minimizationCheck": "m",
            },
        ).status_code == 401

        standard_user = User(
            id="user-std-1",
            username="std_tester",
            email="std@example.com",
            role="STANDARD_USER",
            is_active=True,
        )
        admin_user = User(
            id="user-adm-1",
            username="adm_tester",
            email="adm@example.com",
            role="ADMIN_OPERATOR",
            is_active=True,
        )

        try:
            app.dependency_overrides[get_current_user] = lambda: standard_user
            assert client.get("/api/dev/token-telemetry").status_code == 200
            assert client.post("/api/dev/reset-tokens").status_code == 403
            assert client.get("/api/epistemic/export").status_code == 403
            assert client.get("/api/system/db-check").status_code == 403
            assert client.get("/api/system/gemini-check").status_code == 403
            assert (
                client.post(
                    "/api/admin/set-role?target_email=other@example.com&new_role=ADMIN_OPERATOR"
                ).status_code
                == 403
            )

            app.dependency_overrides[get_current_user] = lambda: admin_user
            reset_resp = client.post("/api/dev/reset-tokens")
            assert reset_resp.status_code == 200
            assert reset_resp.json()["status"] == "reset_successful"
        finally:
            app.dependency_overrides.pop(get_current_user, None)


def test_google_auth_supports_firebase_and_oauth_tokens():
    """Verify _verify_google_or_firebase_token falls back to Firebase ID token verification."""
    from backend.auth_router import _verify_google_or_firebase_token

    mock_claims = {
        "email": "susetofu@gmail.com",
        "email_verified": True,
        "sub": "firebase-uid-123",
    }
    with patch("backend.auth_router.id_token.verify_oauth2_token", side_effect=ValueError("Wrong aud")), \
         patch("backend.auth_router.id_token.verify_firebase_token", return_value=mock_claims) as fb_mock:
        claims = _verify_google_or_firebase_token("dummy.firebase.token")
        assert claims["email"] == "susetofu@gmail.com"
        fb_mock.assert_called_once()


def test_password_register_blocks_root_admin_escalation():
    """Verify password registration rejects ROOT_ADMIN_EMAIL and defaults regular signups to pending STANDARD_USER."""
    with TestClient(app) as client:
        admin_attempt = client.post(
            "/api/auth/register",
            json={
                "username": "spoof_admin",
                "email": "susetofu@gmail.com",
                "password": "StrongPassword123!",
            },
        )
        assert admin_attempt.status_code == 403

        std_attempt = client.post(
            "/api/auth/register",
            json={
                "username": "pending_user_test",
                "email": "pending_user_test@example.com",
                "password": "StrongPassword123!",
            },
        )
        assert std_attempt.status_code == 200
        body = std_attempt.json()
        assert body["role"] == "STANDARD_USER"
        assert body["status"] == "pending"
        assert body["access_token"] == ""





