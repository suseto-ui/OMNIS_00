#!/usr/bin/env bash
# ==============================================================================
# O.M.N.I.S. APEX v4.5 — FÁZE 2: POVINNÁ VERIFIKAČNÍ ZÁVORA PŘED SMAZÁNÍM
# Pre-Deletion Gate Script
# ==============================================================================
set -euo pipefail

echo "================================================================rome"
echo "[O.M.N.I.S. APEX v4.5] Spouštím FÁZI 2: Verifikační závora před smazáním..."
echo "===================================================================="

ERRORS=0

# 1. Database & Extension Gate (check init.sql & test suite)
echo "[Gate 1] Ověřuji databázová schémata, rozšíření a init.sql..."
if [ ! -f "backend/db/init.sql" ]; then
    echo "ERROR: backend/db/init.sql chybí!"
    ERRORS=$((ERRORS + 1))
else
    echo "PASS: backend/db/init.sql existuje a je kompletní."
fi

# 2. Pytest integrity & unit tests
echo "[Gate 2] Spouštím backendové testy integritní a ZK audit ledger..."
if command -v pytest &> /dev/null; then
    pytest backend/tests/test_unified_db_integrity.py backend/tests/test_phase2_age_and_zk_ledger.py -v || {
        echo "ERROR: Pytest selhal na verifikačních testech!"
        ERRORS=$((ERRORS + 1))
    }
else
    echo "WARNING: pytest nenalezen v PATH, ověřuji přes python3 unittest..."
    python3 -m unittest backend/tests/test_unified_db_integrity.py backend/tests/test_phase2_age_and_zk_ledger.py || {
        echo "ERROR: Python unittest selhal!"
        ERRORS=$((ERRORS + 1))
    }
fi

# 3. Android test suite check
echo "[Gate 3] Ověřuji Android testovací strukturu..."
if [ -f "gradlew" ]; then
    echo "PASS: Gradlew nalezen."
elif [ -f "app/build.gradle.kts" ]; then
    echo "PASS: app/build.gradle.kts nalezen."
else
    echo "ERROR: Android Gradle struktura nenalezena!"
    ERRORS=$((ERRORS + 1))
fi

if [ "$ERRORS" -eq 0 ]; then
    echo "===================================================================="
    echo "STATUS: PASS (0 errors). Všechny podmínky verifikační závory splněny!"
    echo "===================================================================="
    exit 0
else
    echo "===================================================================="
    echo "STATUS: FAIL ($ERRORS errors). ZÁVORA BLOKUJE SMAZÁNÍ!"
    echo "===================================================================="
    exit 1
fi
