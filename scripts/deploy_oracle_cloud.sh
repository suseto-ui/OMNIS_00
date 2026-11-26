#!/usr/bin/env bash
# ==============================================================================
# O.M.N.I.S. APEX v4.5 — Oracle Cloud ARM64 / x86_64 Production Deployment Script
# Cílový server: ubuntu@92.6.161.242 (/opt/omnis)
# Doména: omnisbysuseto.duckdns.org
# ==============================================================================
set -euo pipefail

TARGET_DIR="/opt/omnis"
LOCK_FILE="/tmp/omnis_deploy.lock"

# Prevence souběžného spuštění duplicitních nasazovacích skriptů
exec 200>$LOCK_FILE
flock -n 200 || {
    echo "ERROR: Jiná instance nasazovacího skriptu již běží." >&2
    exit 1
}

echo "===================================================================="
echo "[O.M.N.I.S. APEX v4.5] Spouštím produkční nasazení na Oracle Cloud..."
echo "===================================================================="

# 1. Kontrola oprávnění a adresáře
if [ ! -d "$TARGET_DIR" ]; then
    echo "Vytvářím cílový adresář $TARGET_DIR..."
    sudo mkdir -p "$TARGET_DIR"
    sudo chown -R $USER:$USER "$TARGET_DIR"
fi

cd "$TARGET_DIR"

# 2. Záloha existujícího .env souboru (ochrana proti přepsání)
if [ -f ".env" ]; then
    echo "Zálohuji stávající produkční .env..."
    cp .env .env.bak.$(date +%Y%m%d_%H%M%S)
    chmod 600 .env
fi

# 3. Spuštění lokální verifikační závory před buildem
if [ -f "scripts/pre_deletion_gate.sh" ]; then
    echo "Spouštím před-nasazovací verifikační závoru..."
    bash scripts/pre_deletion_gate.sh
fi

# 4. Sestavení a spuštění Docker Compose stacku bez měsíčních nákladů
echo "Sestavuji a spouštím Docker Compose stack (ARM64/amd64 optimální)..."
docker compose down --remove-orphans || true
docker compose build --pull
docker compose up -d --remove-orphans

# 5. Kontrola stavu služeb (Healthchecks)
echo "Ověřuji health status kontejnerů..."
for i in {1..30}; do
    if docker compose ps | grep -q "healthy"; then
        echo "Služby úspěšně nastartovaly a prošly healthcheckem."
        break
    fi
    echo "Čekám na nastartování kontejnerů ($i/30)..."
    sleep 3
done

docker compose ps

echo "===================================================================="
echo "STATUS: DEPLOYMENT ÚSPĚŠNĚ DOKONČEN NA https://omnisbysuseto.duckdns.org"
echo "===================================================================="
