#!/usr/bin/env bash
set -e

echo "=== [O.M.N.I.S.] Android Build Environment Diagnostic & Cleaning Script ==="

echo "[1/4] Cleaning local build outputs, .cxx, and KSP caches..."
rm -rf build app/build .cxx app/.cxx .gradle/caches/transforms/ .gradle/caches/journal-1/

echo "[2/4] Inspecting app/build.gradle.kts SDK configuration..."
if grep -q "compileSdk = 35" app/build.gradle.kts && grep -q "targetSdk = 35" app/build.gradle.kts; then
    echo "✓ SDK 35 compatibility verified successfully in app/build.gradle.kts"
else
    echo "⚠ Warning: SDK version mismatch or not set to 35."
fi

echo "[3/4] Executing gradle clean..."
gradle clean

echo "[4/4] Executing test compile build (gradle :app:compileDebugKotlin)..."
gradle :app:compileDebugKotlin

echo "=== [O.M.N.I.S.] Build environment successfully cleaned and verified! ==="
