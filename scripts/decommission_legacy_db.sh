#!/usr/bin/env bash
# ==============================================================================
# O.M.N.I.S. APEX v4.5 — FÁZE 3: ŘÍZENÉ ODSTRANĚNÍ STARÝCH SOUBORŮ A DECOMMISSION
# Decommission Script
# ==============================================================================
set -euo pipefail

echo "===================================================================="
echo "[O.M.N.I.S. APEX v4.5] Spouštím FÁZI 3: Řízený Decommission Protocol..."
echo "===================================================================="

# Krok 1: Spustit verifikační závoru Fáze 2
echo "Spouštím před-smazací závoru (Pre-Deletion Gate)..."
if ./scripts/pre_deletion_gate.sh; then
    echo "Závora prošla úspěšně. Zahajuji bezpečné odstranění legacy souborů..."
else
    echo "HARD STOP: Verifikační závora selhala! Smazání je zablokováno."
    exit 1
fi

# Krok 2: Seznam souborů k bezpečnému odstranění
LEGACY_FILES=(
    "main.tf"
    "vpc.tf"
    "iam.tf"
    "variables.tf"
    "firebase.json"
    "firebase-blueprint.json"
    "firestore.rules"
    "firestore.test.js"
    "cloudbuild.yaml"
    "GCP_SETUP_GUIDE.md"
)

for file in "${LEGACY_FILES[@]}"; do
    if [ -f "$file" ]; then
        rm -f "$file"
        echo "Odstraněn starý soubor: $file"
    else
        echo "Soubor již neexistuje nebo nebyl nalezen: $file"
    fi
done

echo "===================================================================="
echo "DECOMMISSION ÚSPĚŠNĚ DOKONČEN. Všechny staré cloudu a Firestore vrstvy odstraněny."
echo "===================================================================="
exit 0
