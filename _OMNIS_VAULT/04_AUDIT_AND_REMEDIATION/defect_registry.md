# `[ERR-001]` O.M.N.I.S. Forensic Defect Registry

Tento registr dokumentuje zjištěné defekty, architektonické rozkoly a jejich stav v rámci 7-vrstvého auditu aplikace.

| ID | Závažnost | Komponenta / Soubor | Popis defektu | Stav |
|---|---|---|---|---|
| `ERR-001` | `RESOLVED` | `_OMNIS_VAULT/03_PRAXEOLOGICAL_PLANE/code_artifacts/OmnisOrchestrator.kt` & `backend/security/zk_ledger_service.py` | Souběhový zápis (race condition) v paralelní orchestraci a auditním řetězení bez serializačního zámku. | `RESOLVED` (Kotlin `Mutex` + PostgreSQL `pg_advisory_xact_lock`) |
| `ERR-002` | `BLOCKER -> RESOLVED` | `gradle/libs.versions.toml` | Přesun `libs.versions.toml` do `backend/` znemožnil sestavení Android projektu (`compile_applet`). | `RESOLVED` (Obnoveno do `gradle/libs.versions.toml`) |
| `ERR-003` | `BLOCKER -> RESOLVED` | `.github/workflows/omnis-ci.yml`, `scripts/deploy_cloud_run.sh`, `scripts/setup_gcp_project.sh` | Rozbité CI a deploy pipeline odkazující na dekomisionované soubory `firestore.test.js`, `firestore.rules` a `cloudbuild.yaml`. | `RESOLVED` (Nahrazeno `unified-db-integrity-gate` a Dockerfile buildem) |
| `ERR-004` | `BLOCKER -> RESOLVED` | `docker-compose.yml` | Chybějící `healthcheck` u služby `electric` blokující `depends_on: condition: service_healthy`, chybějící `max_slot_wal_keep_size=2GB`, připojení neúplného `init-omnis-db.sql`. | `RESOLVED` (Doplněn healthcheck, WAL limit a sjednocený `init.sql`) |
| `ERR-005` | `HIGH -> RESOLVED` | `backend/db/init.sql` vs `backend/db/init-omnis-db.sql` | Divergence schémat: v `init.sql` chyběla tabulka `retraction_watch_blacklist`, `causal_nodes` a `causal_edges`, které používá `backend/app/db_engine.py`. | `RESOLVED` (Schémata sjednocena do `backend/db/init.sql`) |
| `ERR-006` | `HIGH -> RESOLVED` | `backend/requirements.txt`, `backend/config.py`, `backend/database.py` | Reziduální závislosti na `google-cloud-firestore`, `firebase-admin` a `google-cloud-secret-manager` + výchozí `ENABLE_FIRESTORE_CQRS_PROJECTION=true`. | `RESOLVED` (Vyčištěno, CQRS projekce defaultně `false`) |
| `ERR-007` | `HIGH -> RESOLVED` | `_OMNIS_VAULT/` (`01_*` až `04_*`) | 7 souborů indexovaných v `00_OMNIS_MASTER_INDEX.md` a `00_TOPOLOGY_GRAPH.json` chybělo na disku a hashe byly zkrácené zástupné řetězce. | `RESOLVED` (Všech 7 artefaktů obnoveno s reálnými SHA-256 otisky) |
| `ERR-008` | `TECH_DEBT -> RESOLVED` | `scripts/pre_deletion_gate.sh`, `scripts/test_database_connectivity.py`, `.dockerignore`, `README.md`, `DOCUMENTATION.md` | Zastaralá dokumentace (Firestore/Cloud SQL), překlep v `pre_deletion_gate.sh`, zastaralá diagnostická sonda. | `RESOLVED` (Aktualizováno dle OSS PostgreSQL 17 architektury) |
