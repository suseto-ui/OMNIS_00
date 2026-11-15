#!/usr/bin/env bash
# ==============================================================================
# O.M.N.I.S. - Git Synchronizační a Exportní Skript
# Podpora pro GitHub (Personal Access Token) + Google Cloud Repositories
# ==============================================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}=== [O.M.N.I.S.] Git Synchronizační Nástroj ===${NC}"

# 0. Načtení lokálních proměnných z .env
if [ -f ".env" ]; then
    set -o allexport
    source .env
    set +o allexport
    echo -e "${GREEN}✓ Načteny lokální konfigurační proměnné z .env${NC}"
fi

# 1. Kontrola bezpečného adresáře a inicializace Git repozitáře
git config --global --add safe.directory /app/applet 2>/dev/null || true
git config --global --add safe.directory "$(pwd)" 2>/dev/null || true

if [ ! -d ".git" ]; then
    echo -e "${YELLOW}>> Inicializuji lokální Git repozitář...${NC}"
    git init -b main
else
    echo -e "${GREEN}✓ Git repozitář je již inicializován.${NC}"
fi

# 2. Výchozí konfigurace identity
git config user.name "suseto-ui"
git config user.email "susetofu@gmail.com"
echo -e "${GREEN}✓ Nastavena identita uživatele: suseto-ui <susetofu@gmail.com>${NC}"

# 3. Zabezpečený Git Credential Helper
git config --global credential.helper "cache --timeout=7200"

# 4. Načtení GitHub PAT tokenu a repozitáře (bez zápisu PAT do .git/config)
GITHUB_TOKEN="${GITHUB_TOKEN:-${1:-}}"
GITHUB_REPO="${GITHUB_REPO:-${2:-https://github.com/suseto-ui/OMNIS.git}}"
GCP_REPO="${GCP_REPO:-${3:-}}"

ASKPASS_SCRIPT=""
cleanup_askpass() {
    if [ -n "$ASKPASS_SCRIPT" ] && [ -f "$ASKPASS_SCRIPT" ]; then
        rm -f "$ASKPASS_SCRIPT"
    fi
}
trap cleanup_askpass EXIT

if [ -n "$GITHUB_REPO" ]; then
    CLEAN_REPO=$(echo "$GITHUB_REPO" | sed -E 's#^https?://##' | sed -E 's#^[^@]+@##')
    CLEAN_REMOTE_URL="https://${CLEAN_REPO}"

    if git remote get-url origin >/dev/null 2>&1; then
        git remote set-url origin "$CLEAN_REMOTE_URL"
    else
        git remote add origin "$CLEAN_REMOTE_URL"
    fi
    echo -e "${GREEN}✓ Vzdálený repozitář origin (GitHub: ${CLEAN_REMOTE_URL}) nastaven bez uložení tokenu do .git/config.${NC}"
fi

if [ -n "$GITHUB_TOKEN" ]; then
    ASKPASS_SCRIPT=$(mktemp)
    chmod 700 "$ASKPASS_SCRIPT"
    cat > "$ASKPASS_SCRIPT" << 'EOF'
#!/usr/bin/env bash
case "$1" in
  *Username*) printf '%s\n' "suseto-ui" ;;
  *) printf '%s\n' "${GITHUB_TOKEN}" ;;
esac
EOF
    export GIT_ASKPASS="$ASKPASS_SCRIPT"
    export GIT_TERMINAL_PROMPT=0
    export GITHUB_TOKEN
fi

if [ -n "$GCP_REPO" ]; then
    if git remote get-url google >/dev/null 2>&1; then
        git remote set-url google "$GCP_REPO"
    else
        git remote add google "$GCP_REPO"
    fi
    echo -e "${GREEN}✓ Vzdálený repozitář google (Google Cloud) nastaven.${NC}"
fi

# 5. Staging změn s respektováním .gitignore
echo -e "${YELLOW}>> Přidávám soubory do stagingu...${NC}"
git add .

# 6. Kontrola změn k commitu
if git diff-index --quiet HEAD -- 2>/dev/null; then
    echo -e "${GREEN}✓ Žádné nové změny k commitu. Pracovní strom je čistý.${NC}"
else
    COMMIT_MSG="feat(omnis): full production release - backend cloud run, google auth & android sync"
    git commit -m "$COMMIT_MSG"
    echo -e "${GREEN}✓ Commit vytvořen: $COMMIT_MSG${NC}"
fi

# 7. Push na vzdálené repozitáře
if git remote get-url origin >/dev/null 2>&1; then
    echo -e "${YELLOW}>> Odesílám větve na GitHub (origin/main)...${NC}"
    if git push -u origin main 2>&1; then
        echo -e "${GREEN}✓ Úspěšně odesláno na GitHub do suseto-ui/OMNIS!${NC}"
    else
        echo -e "${RED}Varování: Push na GitHub selhal. Zkontrolujte oprávnění.${NC}"
    fi
fi

if git remote get-url google >/dev/null 2>&1; then
    echo -e "${YELLOW}>> Odesílám větve na Google Cloud (google/main)...${NC}"
    git push -u google main || echo -e "${RED}Varování: Push na Google Cloud selhal.${NC}"
fi

echo -e "${GREEN}=== Hotovo. O.M.N.I.S. synchronizace dokončena. ===${NC}"
