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

# 4b. Explicitní idempotentní inicializace schématu a publikace (pro případ existujících volume pg17_data)
echo "Ověřuji inicializaci PostgreSQL 17 schématu, rolí a ElectricSQL publikace..."
for i in {1..30}; do
    if docker compose exec -T db pg_isready -U postgres -d omnis_db >/dev/null 2>&1; then
        echo "PostgreSQL 17 odpovídá, aplikuji případné chybějící prvky schématu a publikace..."
        docker compose exec -T db psql -U postgres -d omnis_db -f /docker-entrypoint-initdb.d/01-init.sql >/dev/null 2>&1 || true
        docker compose exec -T db bash /docker-entrypoint-initdb.d/02-apply-role-passwords.sh >/dev/null 2>&1 || true
        break
    fi
    sleep 2
done

# Restart ElectricSQL pokud byl v počáteční smyčce před vytvořením publikace
if docker compose ps electric 2>/dev/null | grep -qiE "restarting|unhealthy"; then
    echo "Restartuji ElectricSQL po synchronizaci publikace..."
    docker compose restart electric
fi

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
