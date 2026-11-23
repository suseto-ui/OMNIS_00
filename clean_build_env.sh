#!/usr/bin/env bash
set -euo pipefail

echo "=== [O.M.N.I.S. APEX v4.5.0-GA] Android Build Environment Diagnostic & Cleaning Script ==="

echo "[1/5] Cleaning local build outputs, .cxx, and stale transform caches..."
rm -rf build app/build .cxx app/.cxx .gradle/caches/transforms/ .gradle/caches/journal-1/

echo "[2/5] Verifying app/build.gradle.kts SDK & R8 configuration..."
if grep -q "compileSdk = 35" app/build.gradle.kts && grep -q "targetSdk = 35" app/build.gradle.kts; then
    echo "PASS: SDK 35 compatibility verified in app/build.gradle.kts"
else
    echo "ERROR: SDK version mismatch (expected compileSdk = 35 and targetSdk = 35)."
    exit 1
fi

if grep -q "isMinifyEnabled = true" app/build.gradle.kts && grep -q "isShrinkResources = true" app/build.gradle.kts; then
    echo "PASS: R8 minification and resource shrinking enabled for release builds."
fi

echo "[3/5] Running Unified DB & Vault Integrity Gate..."
if [ -x "./scripts/pre_deletion_gate.sh" ]; then
    ./scripts/pre_deletion_gate.sh
fi

echo "[4/5] Executing gradle clean..."
gradle clean --no-daemon

echo "[5/5] Executing compile verification (gradle :app:compileDebugKotlin --no-daemon)..."
gradle :app:compileDebugKotlin --no-daemon

echo "=== [O.M.N.I.S. APEX v4.5.0-GA] Build environment cleaned and verified! ==="
