# `[STEP-001]` Fázovaný Exekuční Plán a Produkční Roadmapa (Gate-Driven Delivery)

## Fáze 1: Databázová & Docker Konsolidace (`GATE-1`)
1. Sjednocení `backend/db/init.sql` (obsahuje `vector`, `age`, `pgcrypto`, všechny produkční tabulky, `retraction_watch_blacklist`, `causal_nodes`, `causal_edges`, RLS a `electric_publication_default`).
2. Oprava `docker-compose.yml`: doplnění `healthcheck` pro `electric` a `backend`, přidání `-c max_slot_wal_keep_size=2GB`, připojení `./backend/db/init.sql`.
3. **Verifikace:** `python3 -m unittest backend/tests/test_unified_db_integrity.py`.

## Fáze 2: Backend, Závislosti & Skripty (`GATE-2`)
1. Synchronizace `backend/requirements.txt` s `requirements.txt` (bez `google-cloud-firestore`, `firebase-admin`, `google-cloud-secret-manager`; zachování `sqlalchemy[asyncio]`, `greenlet`, `asyncpg`, `pgvector`).
2. Úprava výchozí konfigurace v `backend/config.py` (`ENABLE_FIRESTORE_CQRS_PROJECTION=false`) a vyčištění logů v `backend/database.py`.
3. Oprava skriptů `scripts/pre_deletion_gate.sh`, `scripts/test_database_connectivity.py`, `scripts/deploy_cloud_run.sh` a `scripts/setup_gcp_project.sh`.
4. **Verifikace:** `python3 -m py_compile $(find backend scripts -name "*.py")` a `./scripts/pre_deletion_gate.sh`.

## Fáze 3: Vault Index Integrita & Dokumentace (`GATE-3`)
1. Obnovení všech 7 artefaktů v `_OMNIS_VAULT/` a přepočet reálných SHA-256 kontrolních součtů v `00_OMNIS_MASTER_INDEX.md` a `00_TOPOLOGY_GRAPH.json`.
2. Aktualizace `README.md`, `DOCUMENTATION.md` a `.dockerignore` dle aktuálního stavu sjednoceného PostgreSQL 17 stacku.
3. **Verifikace:** Kontrola existence a SHA-256 shody všech uzlů v `test_unified_db_integrity.py`.

## Fáze 4: Android Verifikace & CI/CD Uzávěra (`GATE-4`)
1. Aktualizace CI workflow `.github/workflows/omnis-ci.yml` (`unified-db-integrity-gate`).
2. Spuštění všech Android unit a Robolectric testů (`gradle :app:testDebugUnitTest --no-daemon`) a plná kompilace APK/AAB (`compile_applet`).
3. Koncová kontrola na živém stacku po spuštění `docker compose up -d` pomocí `./scripts/verify_cutover.sh`.
