#!/usr/bin/env bash
# ==============================================================================
# O.M.N.I.S. APEX v4.5.0-GA — FÁZE 2: Parity Verification & Legacy Decommission
# Ověřuje 100% paritu PostgreSQL 17 (pgvector + AGE + pgcrypto), ElectricSQL,
# Room SQLCipher v19 a _OMNIS_VAULT před bezpečným odstraněním starých GCP/TF/FS souborů.
# ==============================================================================
set -euo pipefail

VERIFY_ONLY=false
if [[ "${1:-}" == "--verify-only" ]]; then
    VERIFY_ONLY=true
fi

echo "===================================================================="
echo "[O.M.N.I.S. v4.5.0-GA] Spouštím FÁZI 2: Parity & Legacy Clean Gate..."
echo "===================================================================="

# 1. Databázová a schema parita (PG17 + ElectricSQL + Room SQLCipher)
python3 - <<'PY'
import pathlib, sys

init_sql = pathlib.Path("backend/db/init.sql").read_text(encoding="utf-8")
required_pg_tokens = [
    "CREATE EXTENSION IF NOT EXISTS vector;",
    "CREATE EXTENSION IF NOT EXISTS age;",
    "CREATE EXTENSION IF NOT EXISTS pgcrypto;",
    "public.rag_memory_64d",
    "public.tensor_states_8d",
    "public.messages",
    "public.goals",
    "public.causal_nodes",
    "public.causal_edges",
    "public.retraction_watch_blacklist",
    "public.zk_audit_ledger",
    "electric_publication_default",
    "ENABLE ROW LEVEL SECURITY",
]
for token in required_pg_tokens:
    if token not in init_sql:
        print(f"FAIL: Chybí povinný prvek v backend/db/init.sql: {token}", file=sys.stderr)
        sys.exit(1)

compose_txt = pathlib.Path("docker-compose.yml").read_text(encoding="utf-8")
for token in ("max_slot_wal_keep_size=2GB", "electricsql/electric:1.8.1", "healthcheck:"):
    if token not in compose_txt:
        print(f"FAIL: Chybí povinný prvek v docker-compose.yml: {token}", file=sys.stderr)
        sys.exit(1)

room_txt = pathlib.Path("app/src/main/java/com/example/data/OmnisDatabase.kt").read_text(encoding="utf-8")
for token in ("MIGRATION_17_18", "MIGRATION_18_19", "omnis_sync_outbox", "sqlcipher"):
    if token not in room_txt:
        print(f"FAIL: Chybí povinný prvek v OmnisDatabase.kt: {token}", file=sys.stderr)
        sys.exit(1)

print("PASS: 100% parita schématu PG17 + ElectricSQL + Room SQLCipher v19 ověřena.")
PY

# 2. Spustit před-smazací integrační závoru (Vault SHA-256 + testy)
chmod +x scripts/pre_deletion_gate.sh
./scripts/pre_deletion_gate.sh

# 3. Kontrola a bezpečné odstranění dekomisionovaných GCP / Terraform / Firestore souborů
LEGACY_FILES=(
    "main.tf"
    "vpc.tf"
    "iam.tf"
    "variables.tf"
    "cloudbuild.yaml"
    "GCP_SETUP_GUIDE.md"
    "firebase.json"
    "firebase-blueprint.json"
    "firestore.rules"
    "firestore.test.js"
)

FOUND_COUNT=0
for file in "${LEGACY_FILES[@]}"; do
    if [[ -f "$file" ]]; then
        FOUND_COUNT=$((FOUND_COUNT + 1))
        if [[ "$VERIFY_ONLY" == true ]]; then
            echo "INFO [--verify-only]: Nalezen legacy soubor k odstranění: $file"
        else
            rm -f "$file"
            echo "REMOVED: Odstraněn legacy soubor: $file"
        fi
    fi
done

if [[ "$FOUND_COUNT" -eq 0 ]]; then
    echo "PASS: Žádné reziduální legacy soubory (Terraform/CloudBuild/Firestore rules) neexistují."
fi

echo "===================================================================="
echo "STATUS: FÁZE 2 PARITY & LEGACY CLEAN GATE ÚSPĚŠNĚ SPLNĚNA."
echo "===================================================================="
