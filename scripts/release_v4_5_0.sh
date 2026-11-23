#!/usr/bin/env bash
# ==============================================================================
# O.M.N.I.S. APEX v4.5.0-GA — Production Release Pipeline
# 1. Ověří všechny statické, databázové a kryptografické brány (Fáze 1–4)
# 2. Spustí Python & Android (Unit + Robolectric) testovací sady
# 3. Sestaví R8/ProGuard release APK & AAB a vygeneruje SHA-256 kontrolní součty
# ==============================================================================
set -euo pipefail

VERSION="4.5.0-GA"
RELEASE_DIR="release-v4.5.0"
GRADLE_BIN="gradle"
if [[ -x "./gradlew" ]]; then
    GRADLE_BIN="./gradlew"
fi

echo "===================================================================="
echo "[O.M.N.I.S. ${VERSION}] Zahajuji produkční release pipeline..."
echo "===================================================================="

# 1. Syntaktická a bezpečnostní brána (Python)
echo "[Gate 1/5] Python syntax check & quality/DB integrity gates..."
python3 -m py_compile $(find backend scripts -name "*.py")
python3 scripts/verify_quality_gate.py
chmod +x scripts/pre_deletion_gate.sh scripts/verify_and_clean_legacy.sh
./scripts/verify_and_clean_legacy.sh --verify-only

# 2. Spuštění Phase 3 & 4 hardening testů
echo "[Gate 2/5] Spouštím Python Phase 2–4 unittest suite..."
PYTHONPATH=. python3 -m unittest \
    backend/tests/test_unified_db_integrity.py \
    backend/tests/test_phase2_age_and_zk_ledger.py \
    backend/tests/test_phase3_4_hardening.py -v

# 3. Android Unit & Robolectric testy
echo "[Gate 3/5] Spouštím kompletní Android unit & Robolectric test suite..."
"$GRADLE_BIN" :app:testDebugUnitTest --no-daemon

# 4. Sestavení R8/ProGuard Release APK & AAB
echo "[Gate 4/5] Sestavuji produkční Release APK & AAB (R8 Minification & ShrinkResources)..."
if [[ -n "${OMNIS_KEYSTORE_FILE:-}" && -f "${OMNIS_KEYSTORE_FILE:-}" ]]; then
    echo "INFO: Detekován produkční Android Keystore (${OMNIS_KEYSTORE_FILE}) — artefakty budou kryptograficky podepsány."
else
    echo "WARN: OMNIS_KEYSTORE_FILE není nastaven — sestavuji nepodepsaný R8 release build."
fi
"$GRADLE_BIN" :app:assembleRelease :app:bundleRelease --no-daemon

# 5. Shromáždění artefaktů a generování SHA-256 kontrolních součtů
echo "[Gate 5/5] Generuji ${RELEASE_DIR}/SHA256SUMS.txt a RELEASE_NOTES.md..."
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"

while IFS= read -r -d '' artifact; do
    cp "$artifact" "$RELEASE_DIR/"
done < <(find app/build/outputs/apk/release app/build/outputs/bundle/release -type f \( -name "*.apk" -o -name "*.aab" \) -print0 2>/dev/null || true)

if [[ -z "$(ls -A "$RELEASE_DIR")" ]]; then
    echo "ERROR: Nebyly nalezeny žádné vygenerované release APK/AAB artefakty." >&2
    exit 1
fi

(
    cd "$RELEASE_DIR"
    sha256sum * > SHA256SUMS.txt
)

cat > "${RELEASE_DIR}/RELEASE_NOTES.md" <<'EOF'
# O.M.N.I.S. APEX v4.5.0-GA Release Notes

- **Sjednocená Free Sovereign Architektura ($0/měsíc):** PostgreSQL 17 (`pgvector` + `Apache AGE` + `pgcrypto`), `ElectricSQL 1.8.1`, `Caddy` Auto-TLS pro DuckDNS, lokální Edge LLM `Gemma 4` (`tools/gemma4/agent.yaml`) s kaskádovým failoverem (`ProviderCircuitBreaker` CLOSED/OPEN/HALF_OPEN).
- **Android Edge Persistence (Room v19 + SQLCipher AES-256):** Přidána entita `OmnisLocalVectorEntity` (`omnis_local_vectors_64d`), migrace `MIGRATION_18_19` a transakční outbox (`omnis_sync_outbox`).
- **Kognitivní Jádro AKGE-8D & Gatekeeper G1–G6:** Ošetření `NaN`/`Inf` napříč 8D maticí, deterministická L2-normalizace 64D vektorů, konečná Shannonova entropie $S = -\sum p_i \ln(p_i)$ v bráně G6, konstantně-časová verifikace `ZK Audit Ledger` (`OMNIS-ZK-PROOF`) a striktní RBAC izolace `STANDARD_USER` vs. `ADMIN_OPERATOR`.
- **Produkční Android Release:** `versionCode = 450`, `versionName = 4.5.0`, aktivovaný R8/ProGuard (`isMinifyEnabled = true`, `isShrinkResources = true`) a podpis přes proměnné prostředí (`OMNIS_KEYSTORE_*`).
EOF

echo "===================================================================="
echo "STATUS: RELEASE ${VERSION} ÚSPĚŠNĚ SESTAVEN A OVĚŘEN (${RELEASE_DIR}/)"
cat "${RELEASE_DIR}/SHA256SUMS.txt"
echo "===================================================================="
