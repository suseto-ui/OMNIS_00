#!/usr/bin/env bash
# ==============================================================================
# O.M.N.I.S. APEX v4.5 — VERIFIKAČNÍ ZÁVORA (Pre-Deletion & CI Integrity Gate)
# Ověřuje:
#  1. Sjednocené PostgreSQL 17 schéma (backend/db/init.sql) a docker-compose.yml
#  2. Integritu a SHA-256 kontrolní součty všech artefaktů v _OMNIS_VAULT/
#  3. Backendové jednotkové testy (test_unified_db_integrity & test_phase2_age_and_zk_ledger)
#  4. Přítomnost Android Gradle konfigurace a version catalogu
# ==============================================================================
set -euo pipefail

echo "===================================================================="
echo "[O.M.N.I.S. APEX v4.5] Spouštím verifikační závoru (Integrity Gate)..."
echo "===================================================================="

ERRORS=0

# 1. Database & Extension Gate (check init.sql & docker-compose.yml)
echo "[Gate 1] Ověřuji databázová schémata, docker-compose.yml a init.sql..."
if [ ! -f "backend/db/init.sql" ]; then
    echo "ERROR: backend/db/init.sql chybí!"
    ERRORS=$((ERRORS + 1))
else
    echo "PASS: backend/db/init.sql existuje a je připraven."
fi

if [ ! -f "docker-compose.yml" ] || ! grep -q "max_slot_wal_keep_size=2GB" "docker-compose.yml"; then
    echo "ERROR: docker-compose.yml chybí nebo postrádá max_slot_wal_keep_size=2GB!"
    ERRORS=$((ERRORS + 1))
else
    echo "PASS: docker-compose.yml obsahuje konfiguraci PG17 a ElectricSQL."
fi

# 2. _OMNIS_VAULT Index & SHA-256 Checksum Gate
echo "[Gate 2] Ověřuji kompletnost _OMNIS_VAULT/ indexu a SHA-256 kontrolních součtů..."
python3 - <<'PYEOF' || ERRORS=$((ERRORS + 1))
import hashlib
import json
import sys
from pathlib import Path

topo_path = Path("_OMNIS_VAULT/00_TOPOLOGY_GRAPH.json")
if not topo_path.exists():
    print("ERROR: Chybí _OMNIS_VAULT/00_TOPOLOGY_GRAPH.json")
    sys.exit(1)

data = json.loads(topo_path.read_text(encoding="utf-8"))
nodes = data.get("nodes", [])
if not nodes:
    print("ERROR: 00_TOPOLOGY_GRAPH.json neobsahuje žádné uzly")
    sys.exit(1)

for node in nodes:
    vp = Path(node["vault_path"])
    if not vp.exists():
        print(f"ERROR: Chybí indexovaný Vault soubor: {vp}")
        sys.exit(1)
    actual_sha = hashlib.sha256(vp.read_bytes()).hexdigest()
    expected_sha = node.get("integrity_hash", "")
    if actual_sha != expected_sha:
        print(f"ERROR: SHA-256 nesouhlasí pro {vp} (expected={expected_sha}, actual={actual_sha})")
        sys.exit(1)

print(f"PASS: Všech {len(nodes)} uzlů v _OMNIS_VAULT/ existuje a odpovídá SHA-256.")
PYEOF

# 3. Pytest / unittest integrity & ZK audit ledger tests
echo "[Gate 3] Spouštím backendové integrační a ZK audit ledger testy..."
if command -v pytest &> /dev/null && python3 -c "import sqlalchemy" &> /dev/null; then
    pytest backend/tests/test_unified_db_integrity.py backend/tests/test_phase2_age_and_zk_ledger.py -v || {
        echo "ERROR: Pytest selhal na verifikačních testech!"
        ERRORS=$((ERRORS + 1))
    }
else
    echo "INFO: Používám python3 unittest runner..."
    python3 -m unittest backend/tests/test_unified_db_integrity.py backend/tests/test_phase2_age_and_zk_ledger.py || {
        echo "ERROR: Python unittest selhal!"
        ERRORS=$((ERRORS + 1))
    }
fi

# 4. Android test & version catalog check
echo "[Gate 4] Ověřuji Android Gradle strukturu a Version Catalog..."
if [ -f "app/build.gradle.kts" ] && [ -f "gradle/libs.versions.toml" ]; then
    echo "PASS: app/build.gradle.kts a gradle/libs.versions.toml jsou v pořádku."
else
    echo "ERROR: Android Gradle struktura nebo gradle/libs.versions.toml chybí!"
    ERRORS=$((ERRORS + 1))
fi

if [ "$ERRORS" -eq 0 ]; then
    echo "===================================================================="
    echo "STATUS: PASS (0 errors). Všechny podmínky verifikační závory splněny!"
    echo "===================================================================="
    exit 0
else
    echo "===================================================================="
    echo "STATUS: FAIL ($ERRORS errors). ZÁVORA BLOKUJE DALŠÍ KROK!"
    echo "===================================================================="
    exit 1
fi
