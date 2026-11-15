#!/usr/bin/env bash
# ==============================================================================
# O.M.N.I.S. - Automatizovaný Setup pro Google Cloud Console & Cloud Run + Secret Manager
# Rozpočtový strop: < 5.00 USD / měsíc (Zero Cost v nečinnosti)
# ==============================================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m'

AUTO_GENERATE_KEYS="false"
ARGS=()
for arg in "$@"; do
  if [ "$arg" = "--auto-generate-keys" ]; then
    AUTO_GENERATE_KEYS="true"
  else
    ARGS+=("$arg")
  fi
done

echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}      O.M.N.I.S. • Google Cloud Console Provisioning Engine     ${NC}"
echo -e "${CYAN}================================================================${NC}"

if [ "${#ARGS[@]}" -lt 1 ]; then
  echo -e "${RED}[CHYBA] Zadejte Google Cloud Project ID jako první parametr!${NC}"
  echo "Použití: ./scripts/setup_gcp_project.sh <PROJECT_ID> [REGION=europe-west1] [BILLING_ACCOUNT_ID] [--auto-generate-keys]"
  exit 1
fi

PROJECT_ID="${ARGS[0]}"
REGION="${ARGS[1]:-europe-west1}"
BILLING_ACCOUNT_ID="${ARGS[2]:-}"

echo -e "${GREEN}[1/7] Nastavení aktivního GCP projektu: ${PROJECT_ID}${NC}"
gcloud config set project "$PROJECT_ID"
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"

echo -e "${GREEN}[2/7] Povolení Google Cloud API rozhraní...${NC}"
gcloud services enable \
  run.googleapis.com \
  secretmanager.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  firestore.googleapis.com \
  sqladmin.googleapis.com \
  iam.googleapis.com \
  billingbudgets.googleapis.com

echo -e "${GREEN}[3/7] Konfigurace Artifact Registry (omnis-repo)...${NC}"
if ! gcloud artifacts repositories describe omnis-repo --location="$REGION" &>/dev/null; then
  gcloud artifacts repositories create omnis-repo \
    --repository-format=docker \
    --location="$REGION" \
    --description="Docker repozitář pro O.M.N.I.S."
  echo "Repozitář omnis-repo vytvořen."
else
  echo "Repozitář omnis-repo již existuje."
fi

echo -e "${GREEN}[4/7] Vytvoření Service Accountů s principem nejnižších privilegií...${NC}"

# 1. Runtime Service Account pro Cloud Run
RUN_SA="omnis-cloud-run@${PROJECT_ID}.iam.gserviceaccount.com"
if ! gcloud iam service-accounts describe "$RUN_SA" &>/dev/null; then
  gcloud iam service-accounts create omnis-cloud-run \
    --display-name="O.M.N.I.S. Cloud Run Runtime Service Account"
  echo "Service account omnis-cloud-run vytvořen."
fi

for role in roles/secretmanager.secretAccessor roles/cloudsql.client roles/datastore.user roles/logging.logWriter; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${RUN_SA}" \
    --role="$role" &>/dev/null || true
done

# 2. CI/CD Service Account pro GitHub Actions
CI_SA="github-actions-deployer@${PROJECT_ID}.iam.gserviceaccount.com"
if ! gcloud iam service-accounts describe "$CI_SA" &>/dev/null; then
  gcloud iam service-accounts create github-actions-deployer \
    --display-name="O.M.N.I.S. CI/CD Service Account"
fi

for role in roles/artifactregistry.writer roles/run.developer roles/iam.serviceAccountUser roles/secretmanager.secretAccessor roles/cloudbuild.builds.editor; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${CI_SA}" \
    --role="$role" &>/dev/null
done

# 3. Cloud Build Service Account oprávnění pro automatizované nasazení Cloud Run revize
CB_SA="${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com"
for role in roles/run.admin roles/iam.serviceAccountUser roles/secretmanager.secretAccessor roles/artifactregistry.writer; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${CB_SA}" \
    --role="$role" &>/dev/null || true
done

echo -e "${GREEN}[5/7] Příprava Google Cloud Secret Manageru...${NC}"
SECRETS=(OMNIS_DATABASE_URL JWT_SECRET_KEY GOOGLE_CLIENT_ID GEMINI_API_KEY OMNIS_SYNC_API_KEY OMNIS_ADMIN_SECRET_HASHES)
for secret in "${SECRETS[@]}"; do
  if ! gcloud secrets describe "$secret" &>/dev/null; then
    gcloud secrets create "$secret" --replication-policy="automatic"
    echo "Secret '$secret' vytvořen v Secret Manageru."
  else
    echo "Secret '$secret' již existuje."
  fi
done

upload_secret_if_env_set() {
  local secret_name="$1"
  local env_value="$2"
  if [ -n "$env_value" ]; then
    printf '%s' "$env_value" | gcloud secrets versions add "$secret_name" --data-file=- >/dev/null
    echo "  -> Nahrána nová verze pro secret '$secret_name' z proměnné prostředí."
  fi
}

upload_secret_if_env_set "OMNIS_DATABASE_URL" "${OMNIS_DATABASE_URL:-${DATABASE_URL:-}}"
upload_secret_if_env_set "JWT_SECRET_KEY" "${JWT_SECRET_KEY:-${SECRET_KEY:-}}"
upload_secret_if_env_set "GOOGLE_CLIENT_ID" "${GOOGLE_CLIENT_ID:-}"
upload_secret_if_env_set "GEMINI_API_KEY" "${GEMINI_API_KEY:-}"
upload_secret_if_env_set "OMNIS_SYNC_API_KEY" "${OMNIS_SYNC_API_KEY:-}"
upload_secret_if_env_set "OMNIS_ADMIN_SECRET_HASHES" "${OMNIS_ADMIN_SECRET_HASHES:-}"

if [ "$AUTO_GENERATE_KEYS" = "true" ]; then
  for gen_secret in JWT_SECRET_KEY OMNIS_SYNC_API_KEY; do
    if ! gcloud secrets versions access latest --secret="$gen_secret" &>/dev/null; then
      RAND_VAL="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
      printf '%s' "$RAND_VAL" | gcloud secrets versions add "$gen_secret" --data-file=- >/dev/null
      echo "  -> Automaticky vygenerována bezpečná hodnota pro '$gen_secret'."
    fi
  done
fi

echo -e "${GREEN}[6/7] Audit verzí v Secret Manageru (povinné před Cloud Run deployem)...${NC}"
MISSING_SECRETS=()
for secret in "${SECRETS[@]}"; do
  if gcloud secrets versions access latest --secret="$secret" &>/dev/null; then
    echo -e "  [OK] $secret má aktivní verzi."
  else
    echo -e "  ${YELLOW}[CHYBÍ VERZE] $secret nemá zatím žádnou hodnotu!${NC}"
    MISSING_SECRETS+=("$secret")
  fi
done

echo -e "${GREEN}[7/7] Rozpočtová ochrana do 5.00 USD měsíčně...${NC}"
if [ -n "$BILLING_ACCOUNT_ID" ]; then
  echo "Vytvářím rozpočtový alarm pro účet $BILLING_ACCOUNT_ID..."
  gcloud billing budgets create \
    --billing-account="$BILLING_ACCOUNT_ID" \
    --display-name="O.M.N.I.S. Strikni Limit 5 USD" \
    --budget-amount=5.00USD \
    --threshold-rule=percent=0.5 \
    --threshold-rule=percent=0.8 \
    --threshold-rule=percent=1.0 \
    --filter-projects="projects/${PROJECT_ID}" || echo "Budget alert již nastaven nebo vyžaduje administrátorská práva."
else
  echo -e "${YELLOW}[UPOZORNĚNÍ] Nebylo zadáno BILLING_ACCOUNT_ID. Nastavte rozpočet 5 USD manuálně v Google Cloud Console -> Billing -> Budgets & alerts.${NC}"
fi

echo -e "${CYAN}================================================================${NC}"
echo -e "${GREEN}✓ Google Cloud Platform byla úspěšně nakonfigurována pro O.M.N.I.S.!${NC}"
echo -e "${CYAN}================================================================${NC}"
if [ "${#MISSING_SECRETS[@]}" -gt 0 ]; then
  echo -e "${YELLOW}Před nasazením nahrajte chybějící verze secretů: ${MISSING_SECRETS[*]}${NC}"
  echo "Příklad: printf '%s' \"HODNOTA\" | gcloud secrets versions add GEMINI_API_KEY --data-file=-"
fi
echo "Následné spuštění buildu a nasazení na Cloud Run:"
echo "  gcloud builds submit --config cloudbuild.yaml --substitutions=_REGION=${REGION},_TAG=\$(git rev-parse --short HEAD)"
echo "Po nasazení získáte produkční URL příkazem:"
echo "  gcloud run services describe omnis-backend --region=${REGION} --format='value(status.url)'"
echo -e "${CYAN}================================================================${NC}"
