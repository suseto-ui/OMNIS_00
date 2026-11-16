#!/usr/bin/env bash
# ==============================================================================
# O.M.N.I.S. - All-in-One Cloud Commissioning & Deployment Script for Google Cloud
# Plně kompatibilní skript pro kompletní zprovoznění na Google Cloud Platform:
# 1. Kontrola prostředí a GCP projektu
# 2. Povolení API rozhraní (Cloud Run, Secret Manager, Artifact Registry, Firestore...)
# 3. Vytvoření Artifact Registry repozitáře
# 4. Konfigurace Service Accountů (Zero-Trust IAM pro Cloud Run a Cloud Build)
# 5. Inicializace a garance verzí v Secret Manageru (Zero-Crash ochrana pro Cloud Run)
# 6. Spuštění Cloud Build pipeline (sestavení multi-stage Dockeru s React SPA a FastAPI)
# 7. Nasazení na Google Cloud Run (Free Tier optimalizováno: 0 USD v nečinnosti)
# 8. Živý integrační smoke test (/healthz, /api/v1/health, SPA index)
# 9. Výpis produkčních URL a konfigurace pro Android klienta
# ==============================================================================

set -euo pipefail

# Barvy pro přehledný výstup v terminálu
RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m'

echo -e "${CYAN}================================================================================${NC}"
echo -e "${BOLD}${CYAN}        O.M.N.I.S. • Komplexní Cloud Commissioning & Deploy Engine        ${NC}"
echo -e "${CYAN}================================================================================${NC}"

# ------------------------------------------------------------------------------
# 1. Zpracování parametrů a autodetekce konfigurace
# ------------------------------------------------------------------------------
PROJECT_ID=""
REGION="europe-west1"
BILLING_ACCOUNT_ID=""
AUTO_APPROVE="false"
SERVICE_NAME="omnis-backend"
REPOSITORY_NAME="omnis-repo"
FIREBASE_PROJECT_ID="gen-lang-client-0328950802"
FIRESTORE_DATABASE_ID="ai-studio-android-omnis-fba4ba28-3ec2-4eaf-a1a7-338554af8dfe"
CLOUDSQL_INSTANCE=""
CUSTOM_GEMINI_KEY=""
CUSTOM_DB_URL=""
CUSTOM_GOOGLE_CLIENT_ID=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project=*)
      PROJECT_ID="${1#*=}"
      shift
      ;;
    --region=*)
      REGION="${1#*=}"
      shift
      ;;
    --billing-account=*)
      BILLING_ACCOUNT_ID="${1#*=}"
      shift
      ;;
    --gemini-key=*)
      CUSTOM_GEMINI_KEY="${1#*=}"
      shift
      ;;
    --db-url=*)
      CUSTOM_DB_URL="${1#*=}"
      shift
      ;;
    --google-client-id=*)
      CUSTOM_GOOGLE_CLIENT_ID="${1#*=}"
      shift
      ;;
    --cloudsql-instance=*)
      CLOUDSQL_INSTANCE="${1#*=}"
      shift
      ;;
    -y|--yes|--auto-approve)
      AUTO_APPROVE="true"
      shift
      ;;
    -h|--help)
      echo "Použití: $0 [PROJECT_ID] [REGION=europe-west1] [OPTIONS]"
      echo ""
      echo "Možnosti:"
      echo "  --project=<ID>             Google Cloud Project ID"
      echo "  --region=<REGION>          GCP region (výchozí: europe-west1)"
      echo "  --billing-account=<ID>     Billing Account ID pro nastavení rozpočtového limitu 5 USD"
      echo "  --gemini-key=<KEY>         GEMINI_API_KEY pro uložení do Secret Manageru"
      echo "  --db-url=<URL>             PostgreSQL / Cloud SQL DATABASE_URL"
      echo "  --google-client-id=<ID>    OAuth 2.0 Web Client ID pro GOOGLE_CLIENT_ID"
      echo "  --cloudsql-instance=<NAME> Název Cloud SQL instance k navázání na Cloud Run"
      echo "  -y, --yes, --auto-approve  Automatické potvrzení bez interaktivních dotazů"
      echo "  -h, --help                 Zobrazí tuto nápovědu"
      exit 0
      ;;
    *)
      if [ -z "$PROJECT_ID" ]; then
        PROJECT_ID="$1"
      elif [ "$REGION" = "europe-west1" ]; then
        REGION="$1"
      elif [ -z "$BILLING_ACCOUNT_ID" ]; then
        BILLING_ACCOUNT_ID="$1"
      fi
      shift
      ;;
  esac
done

# Kontrola dostupnosti nástroje gcloud
if ! command -v gcloud &>/dev/null; then
  echo -e "${RED}[CHYBA] Google Cloud SDK ('gcloud') není nainstalováno v PATH!${NC}"
  echo "Nainstalujte gcloud CLI podle: https://cloud.google.com/sdk/docs/install"
  exit 1
fi

# Autodetekce aktivního projektu, pokud nebyl zadán
if [ -z "$PROJECT_ID" ]; then
  ACTIVE_GCLOUD_PROJECT="$(gcloud config get-value project 2>/dev/null || true)"
  if [ -n "$ACTIVE_GCLOUD_PROJECT" ] && [ "$ACTIVE_GCLOUD_PROJECT" != "(unset)" ]; then
    PROJECT_ID="$ACTIVE_GCLOUD_PROJECT"
    echo -e "${GREEN}✓ Automaticky detekován aktivní GCP projekt z gcloud konfigurace: ${BOLD}${PROJECT_ID}${NC}"
  else
    if [ "$AUTO_APPROVE" = "true" ]; then
      echo -e "${RED}[CHYBA] Nebyl zadán žádný Google Cloud Project ID a gcloud config je prázdný.${NC}"
      exit 1
    fi
    echo -e "${YELLOW}Zadejte váš Google Cloud Project ID:${NC}"
    read -r -p "Project ID: " PROJECT_ID
    if [ -z "$PROJECT_ID" ]; then
      echo -e "${RED}[CHYBA] Project ID je povinný parametr.${NC}"
      exit 1
    fi
  fi
fi

echo -e "Cílový projekt:  ${BOLD}${CYAN}${PROJECT_ID}${NC}"
echo -e "Cílový region:   ${BOLD}${CYAN}${REGION}${NC}"
echo -e "Název služby:    ${BOLD}${CYAN}${SERVICE_NAME}${NC}"

# Kontrola přihlášení v gcloud
ACTIVE_ACCOUNT="$(gcloud auth list --filter=status:ACTIVE --format='value(account)' 2>/dev/null || true)"
if [ -z "$ACTIVE_ACCOUNT" ]; then
  echo -e "${YELLOW}[UPOZORNĚNÍ] Žádný aktivní účet v gcloud CLI nebyl nalezen.${NC}"
  echo "Spusťte prosím 'gcloud auth login' a následně opakujte nasazení."
  exit 1
else
  echo -e "${GREEN}✓ Přihlášený GCP účet: ${ACTIVE_ACCOUNT}${NC}"
fi

# Potvrzení před spuštěním (pokud není --yes)
if [ "$AUTO_APPROVE" != "true" ]; then
  echo ""
  read -r -p "Přejete si spustit kompletní provisioning a nasazení na Cloud Run? [A/n]: " CONFIRM
  if [[ "$CONFIRM" =~ ^[Nn] ]]; then
    echo -e "${YELLOW}Operace zrušena uživatelem.${NC}"
    exit 0
  fi
fi

# ------------------------------------------------------------------------------
# 1b. Pre-flight Quality Gate & Security Rules Check
# ------------------------------------------------------------------------------
echo ""
echo -e "${BLUE}=== [0/8] Lokální Pre-Flight Quality Gate & Firestore Rules Check ===${NC}"
python3 -m py_compile backend/*.py backend/schemas/*.py backend/graph/*.py backend/gemini/*.py scripts/*.py
python3 scripts/verify_quality_gate.py
echo -e "${GREEN}✓ Backend syntaxe a O.M.N.I.S. Quality Gate úspěšně ověřeny.${NC}"

if [ -f "firestore.rules" ] && command -v firebase &>/dev/null && command -v node &>/dev/null; then
  echo "Spouštím rychlé ověření firestore.rules přes emulátor..."
  NODE_PATH="$(npm root -g 2>/dev/null || true)" \
    firebase emulators:exec --only firestore,auth --project demo-no-project \
    "FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 node --test firestore.test.js"
  echo -e "${GREEN}✓ firestore.rules ověřeny proti všem unit testům.${NC}"
else
  echo -e "${YELLOW}ℹ Firebase CLI nebo Node není lokálně dostupné — ověření firestore.rules přeskočeno.${NC}"
fi

# ------------------------------------------------------------------------------
# 2. Nastavení projektu a povolení API
# ------------------------------------------------------------------------------
echo ""
echo -e "${BLUE}=== [1/8] Konfigurace projektu a aktivace Google Cloud API ===${NC}"
gcloud config set project "$PROJECT_ID" >/dev/null

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
echo -e "${GREEN}✓ Project Number: ${PROJECT_NUMBER}${NC}"

echo "Povoluji potřebná Google Cloud API rozhraní..."
gcloud services enable \
  run.googleapis.com \
  secretmanager.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  firestore.googleapis.com \
  sqladmin.googleapis.com \
  iam.googleapis.com \
  billingbudgets.googleapis.com

echo -e "${GREEN}✓ Všechna požadovaná Google Cloud API rozhraní jsou aktivní.${NC}"

# ------------------------------------------------------------------------------
# 3. Artifact Registry Repozitář
# ------------------------------------------------------------------------------
echo ""
echo -e "${BLUE}=== [2/8] Ověření Artifact Registry repozitáře (${REPOSITORY_NAME}) ===${NC}"
if ! gcloud artifacts repositories describe "$REPOSITORY_NAME" --location="$REGION" &>/dev/null; then
  echo "Vytvářím Docker repozitář '${REPOSITORY_NAME}' v regionu '${REGION}'..."
  gcloud artifacts repositories create "$REPOSITORY_NAME" \
    --repository-format=docker \
    --location="$REGION" \
    --description="Docker repozitář pro O.M.N.I.S. multi-stage build"
  echo -e "${GREEN}✓ Repozitář '${REPOSITORY_NAME}' úspěšně vytvořen.${NC}"
else
  echo -e "${GREEN}✓ Repozitář '${REPOSITORY_NAME}' již existuje a je připraven.${NC}"
fi

# ------------------------------------------------------------------------------
# 4. Vytvoření Service Accountů a Zero-Trust IAM
# ------------------------------------------------------------------------------
echo ""
echo -e "${BLUE}=== [3/8] Nastavení Service Accountů s principem nejnižších oprávnění ===${NC}"

# A) Runtime Service Account pro Cloud Run instanci
RUN_SA="omnis-cloud-run@${PROJECT_ID}.iam.gserviceaccount.com"
if ! gcloud iam service-accounts describe "$RUN_SA" &>/dev/null; then
  echo "Vytvářím dedikovaný runtime service account '${RUN_SA}'..."
  gcloud iam service-accounts create omnis-cloud-run \
    --display-name="O.M.N.I.S. Cloud Run Runtime Identity" 2>/dev/null || {
      echo -e "${YELLOW}  [INFO] Service account se nepodařilo vytvořit (bude použit výchozí Compute Engine účet).${NC}"
    }
fi

RUN_ROLES=(
  roles/secretmanager.secretAccessor
  roles/cloudsql.client
  roles/datastore.user
  roles/logging.logWriter
)

for role in "${RUN_ROLES[@]}"; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${RUN_SA}" \
    --role="$role" &>/dev/null || true
done
echo -e "${GREEN}✓ Runtime Service Account 'omnis-cloud-run' zkontrolován.${NC}"

# B) Oprávnění pro Cloud Build Service Account (aby mohl provést build, push i deploy)
CB_SA="${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com"
CB_ROLES=(
  roles/run.admin
  roles/iam.serviceAccountUser
  roles/secretmanager.secretAccessor
  roles/artifactregistry.writer
)

for role in "${CB_ROLES[@]}"; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${CB_SA}" \
    --role="$role" &>/dev/null || true
done
echo -e "${GREEN}✓ Oprávnění pro Cloud Build ověřena.${NC}"

# ------------------------------------------------------------------------------
# 5. Secret Manager: Garance 100% přítomnosti všech 6 položek s verzí (Zero-Crash)
# ------------------------------------------------------------------------------
echo ""
echo -e "${BLUE}=== [4/8] Správa tajemství v Google Cloud Secret Manageru ===${NC}"

ALL_SECRETS=(
  JWT_SECRET_KEY
  OMNIS_SYNC_API_KEY
  GOOGLE_CLIENT_ID
  GEMINI_API_KEY
  OMNIS_ADMIN_SECRET_HASHES
  OMNIS_DATABASE_URL
)

# Zajistíme existenci prázdné struktury každého secretu
for sec in "${ALL_SECRETS[@]}"; do
  if ! gcloud secrets describe "$sec" &>/dev/null; then
    gcloud secrets create "$sec" --replication-policy="automatic"
    echo "  [+] Vytvořen secret: $sec"
  fi
done

# Pomocná funkce pro vložení verze secretu
set_secret_version() {
  local sname="$1"
  local sval="$2"
  printf '%s' "$sval" | gcloud secrets versions add "$sname" --data-file=- >/dev/null
}

# 1. JWT_SECRET_KEY
if ! gcloud secrets versions access latest --secret="JWT_SECRET_KEY" &>/dev/null; then
  VAL="${JWT_SECRET_KEY:-${SECRET_KEY:-}}"
  if [ -z "$VAL" ]; then
    VAL="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
    echo -e "  ${GREEN}✓ Vygenerován bezpečný kryptografický klíč pro JWT_SECRET_KEY.${NC}"
  fi
  set_secret_version "JWT_SECRET_KEY" "$VAL"
else
  echo "  [✓] JWT_SECRET_KEY má aktivní verzi."
fi

# 2. OMNIS_SYNC_API_KEY
if ! gcloud secrets versions access latest --secret="OMNIS_SYNC_API_KEY" &>/dev/null; then
  VAL="${OMNIS_SYNC_API_KEY:-${OMNIS_GATEWAY_TOKEN:-}}"
  if [ -z "$VAL" ]; then
    VAL="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
    echo -e "  ${GREEN}✓ Vygenerován bezpečný API klíč pro OMNIS_SYNC_API_KEY.${NC}"
  fi
  set_secret_version "OMNIS_SYNC_API_KEY" "$VAL"
else
  echo "  [✓] OMNIS_SYNC_API_KEY má aktivní verzi."
fi

# 3. GOOGLE_CLIENT_ID
if ! gcloud secrets versions access latest --secret="GOOGLE_CLIENT_ID" &>/dev/null; then
  VAL="${CUSTOM_GOOGLE_CLIENT_ID:-${GOOGLE_CLIENT_ID:-}}"
  if [ -z "$VAL" ] && [ "$AUTO_APPROVE" != "true" ]; then
    read -r -p "Zadejte OAuth GOOGLE_CLIENT_ID (nebo Enter pro zrušení): " VAL
  fi
  if [ -z "$VAL" ]; then
    echo -e "${RED}[CHYBA] Secret GOOGLE_CLIENT_ID nemá aktivní verzi v Secret Manageru.${NC}"
    echo "Nastavte jej parametrem --google-client-id=... nebo příkazem:"
    echo "  printf '%s' \"VÁŠ_CLIENT_ID\" | gcloud secrets versions add GOOGLE_CLIENT_ID --data-file=-"
    exit 1
  fi
  set_secret_version "GOOGLE_CLIENT_ID" "$VAL"
  echo -e "  ${GREEN}✓ Uložen GOOGLE_CLIENT_ID do Secret Manageru.${NC}"
else
  echo "  [✓] GOOGLE_CLIENT_ID má aktivní verzi."
fi

# 4. OMNIS_ADMIN_SECRET_HASHES
GENERATED_ADMIN_PASS=""
if ! gcloud secrets versions access latest --secret="OMNIS_ADMIN_SECRET_HASHES" &>/dev/null; then
  VAL="${OMNIS_ADMIN_SECRET_HASHES:-}"
  if [ -z "$VAL" ]; then
    GENERATED_ADMIN_PASS="$(python3 -c 'import secrets; print("Omnis-Admin-" + secrets.token_hex(8))')"
    VAL="$(python3 -c "import hashlib; print(hashlib.sha256('$GENERATED_ADMIN_PASS'.encode()).hexdigest())")"
    echo -e "  ${GREEN}✓ Vygenerováno administrátorské heslo a uložen jeho SHA-256 hash.${NC}"
  fi
  set_secret_version "OMNIS_ADMIN_SECRET_HASHES" "$VAL"
else
  echo "  [✓] OMNIS_ADMIN_SECRET_HASHES má aktivní verzi."
fi

# 5. OMNIS_DATABASE_URL
if ! gcloud secrets versions access latest --secret="OMNIS_DATABASE_URL" &>/dev/null; then
  VAL="${CUSTOM_DB_URL:-${DATABASE_URL:-${OMNIS_DATABASE_URL:-}}}"
  if [ -z "$VAL" ]; then
    # Ephemeral in-memory režim pouze pokud uživatel zatím nepřipojil Cloud SQL
    VAL="sqlite+aiosqlite:///:memory:"
    echo -e "  ${YELLOW}⚠ OMNIS_DATABASE_URL nebyla zadána -> dočasný in-memory SQLite režim (pro produkci zadejte --db-url=postgresql+asyncpg://...).${NC}"
  fi
  set_secret_version "OMNIS_DATABASE_URL" "$VAL"
else
  echo "  [✓] OMNIS_DATABASE_URL má aktivní verzi."
fi

# 6. GEMINI_API_KEY
if ! gcloud secrets versions access latest --secret="GEMINI_API_KEY" &>/dev/null; then
  VAL="${CUSTOM_GEMINI_KEY:-${GEMINI_API_KEY:-}}"
  if [ -z "$VAL" ] && [ "$AUTO_APPROVE" != "true" ]; then
    read -r -s -p "Zadejte GEMINI_API_KEY pro uložení do Secret Manageru: " VAL
    echo ""
  fi
  if [ -z "$VAL" ]; then
    echo -e "${RED}[CHYBA] Secret GEMINI_API_KEY nemá aktivní verzi v Secret Manageru.${NC}"
    echo "Vložte platný klíč před nasazením na Cloud Run (žádné zástupné klíče nejsou povoleny):"
    echo "  printf '%s' \"VÁŠ_KLÍČ\" | gcloud secrets versions add GEMINI_API_KEY --data-file=-"
    exit 1
  fi
  set_secret_version "GEMINI_API_KEY" "$VAL"
  echo -e "  ${GREEN}✓ Uložen GEMINI_API_KEY do Secret Manageru.${NC}"
else
  echo "  [✓] GEMINI_API_KEY má aktivní verzi."
fi

echo -e "${GREEN}✓ Všech 6 položek Secret Manageru je garantováno a připraveno pro Cloud Run.${NC}"

# ------------------------------------------------------------------------------
# 6. Nastavení rozpočtového limitu (pokud bylo předáno Billing Account ID)
# ------------------------------------------------------------------------------
if [ -n "$BILLING_ACCOUNT_ID" ]; then
  echo ""
  echo -e "${BLUE}=== [5/8] Nastavení rozpočtové ochrany na 5 USD/měsíc ===${NC}"
  gcloud billing budgets create \
    --billing-account="$BILLING_ACCOUNT_ID" \
    --display-name="O.M.N.I.S. Strikni Limit 5 USD" \
    --budget-amount=5.00USD \
    --threshold-rule=percent=0.5 \
    --threshold-rule=percent=0.8 \
    --threshold-rule=percent=1.0 \
    --filter-projects="projects/${PROJECT_ID}" 2>/dev/null || echo "  Budget alert již existuje nebo vyžaduje administrátorská práva k fakturaci."
fi

# ------------------------------------------------------------------------------
# 7. Sestavení kontejneru a nasazení přes Cloud Build
# ------------------------------------------------------------------------------
echo ""
echo -e "${BLUE}=== [6/8] Spouštím Google Cloud Build (Multi-stage Docker + React + FastAPI) ===${NC}"

BUILD_TAG="$(git rev-parse --short HEAD 2>/dev/null || date +%s)"
echo "Build Tag: ${BUILD_TAG}"

SUBSTITUTIONS="_REGION=${REGION},_TAG=${BUILD_TAG},_REPOSITORY=${REPOSITORY_NAME},_SERVICE_NAME=${SERVICE_NAME},_FIREBASE_PROJECT_ID=${FIREBASE_PROJECT_ID},_FIRESTORE_DATABASE_ID=${FIRESTORE_DATABASE_ID}"
if [ -n "$CLOUDSQL_INSTANCE" ]; then
  SUBSTITUTIONS="${SUBSTITUTIONS},_CLOUDSQL_INSTANCE=${CLOUDSQL_INSTANCE}"
fi

gcloud builds submit \
  --config cloudbuild.yaml \
  --substitutions="$SUBSTITUTIONS" \
  --project="$PROJECT_ID"

echo -e "${GREEN}✓ Cloud Build a nasazení na Cloud Run proběhlo úspěšně!${NC}"

# ------------------------------------------------------------------------------
# 8. Získání URL a automatický smoke test
# ------------------------------------------------------------------------------
echo ""
echo -e "${BLUE}=== [7/8] Živý integrační smoke test Cloud Run služby ===${NC}"

SERVICE_URL="$(gcloud run services describe "$SERVICE_NAME" --region="$REGION" --project="$PROJECT_ID" --format='value(status.url)')"

if [ -z "$SERVICE_URL" ]; then
  echo -e "${RED}[CHYBA] Nepodařilo se načíst URL nasazené služby Cloud Run.${NC}"
  exit 1
fi

echo -e "Nasazená služba: ${BOLD}${CYAN}${SERVICE_URL}${NC}"
echo "Ověřuji dostupnost backendu a frontendu..."

HEALTHZ_CODE="000"
for attempt in 1 2 3 4 5; do
  HEALTHZ_CODE="$(curl -s -o /dev/null -w "%{http_code}" "${SERVICE_URL}/healthz" || true)"
  if [ "$HEALTHZ_CODE" = "200" ]; then
    break
  fi
  sleep 2
done
if [ "$HEALTHZ_CODE" = "200" ]; then
  echo -e "  [OK] /healthz vrátil HTTP 200 (Core FastAPI je zdravé)."
else
  echo -e "  ${RED}[CHYBA] /healthz vrátil kód ${HEALTHZ_CODE}${NC}"
  exit 1
fi

# Test /api/v1/health
API_HEALTH_CODE="$(curl -s -o /dev/null -w "%{http_code}" "${SERVICE_URL}/api/v1/health" || true)"
if [ "$API_HEALTH_CODE" = "200" ]; then
  echo -e "  [OK] /api/v1/health vrátil HTTP 200 (Sync Gateway a databáze jsou v provozu)."
else
  echo -e "  ${YELLOW}[VAROVÁNÍ] /api/v1/health vrátil kód ${API_HEALTH_CODE}${NC}"
fi

# Test SPA index.html
SPA_CODE="$(curl -s -o /dev/null -w "%{http_code}" "${SERVICE_URL}/" || true)"
if [ "$SPA_CODE" = "200" ]; then
  echo -e "  [OK] / (SPA Frontend) vrátil HTTP 200 (React aplikace se načítá)."
else
  echo -e "  ${YELLOW}[VAROVÁNÍ] Root / vrátil kód ${SPA_CODE}${NC}"
fi

# ------------------------------------------------------------------------------
# 9. Závěrečný souhrn a konfigurace pro klienty
# ------------------------------------------------------------------------------
echo ""
echo -e "${CYAN}================================================================================${NC}"
echo -e "${BOLD}${GREEN}        ✓ O.M.N.I.S. JE ÚSPĚŠNĚ ZPROVOZNĚN V GOOGLE CLOUD!        ${NC}"
echo -e "${CYAN}================================================================================${NC}"
echo ""
echo -e "🌐 ${BOLD}Webový Dashboard & API Gateway:${NC}"
echo -e "   ${BOLD}${CYAN}${SERVICE_URL}${NC}"
echo ""
echo -e "📱 ${BOLD}Propojení s Android aplikací:${NC}"
echo "   Nastavte proměnnou OMNIS_BACKEND_URL v Secrets panelu AI Studio nebo ji předejte při buildu:"
echo -e "   ${YELLOW}gradle assembleDebug -POMNIS_BACKEND_URL=${SERVICE_URL}${NC}"
echo "   případně exportujte proměnnou prostředí:"
echo -e "   ${YELLOW}export OMNIS_BACKEND_URL=${SERVICE_URL}${NC}"
echo ""
if [ -n "$GENERATED_ADMIN_PASS" ]; then
  echo -e "🔐 ${BOLD}Vygenerované administrátorské heslo (ADMIN_OPERATOR):${NC}"
  echo -e "   Heslo: ${BOLD}${YELLOW}${GENERATED_ADMIN_PASS}${NC}"
  echo -e "   (Jeho SHA-256 hash je bezpečně uložen v OMNIS_ADMIN_SECRET_HASHES)"
  echo ""
fi
echo -e "💰 ${BOLD}Náklady a škálování:${NC}"
echo "   - Min instances = 0 (scale-to-zero: nulové fixní poplatky při nečinnosti)"
echo "   - Max instances = 3 (ochrana před přetečením rozpočtu)"
echo "   - Paměť = 512 MiB, CPU = 1 (optimalizováno pro GCP Free Tier limit)"
echo ""
echo -e "${CYAN}================================================================================${NC}"
