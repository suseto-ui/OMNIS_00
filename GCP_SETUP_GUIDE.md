# O.M.N.I.S. APEX v4.5 • Google Cloud & Production Deployment Guide

Kompletní průvodce integrací a produkčním nasazením systému **O.M.N.I.S. APEX v4.5** (Android Jetpack Compose klient + Python FastAPI backend) na Google Cloud Platform (GCP) a Firebase při striktním dodržení rozpočtového limitu **do 5 USD / měsíc** (*Scale-to-Zero* v nečinnosti, **Cloud Firestore** jako primární cloudová databáze, **Cloud SQL pgvector** jako volitelné analytické rozšíření).

---

## 1. Architektura nákladové optimalizace (< 5 USD/měsíc)

| Komponenta | Konfigurace pro úsporu & Zero-Idle | Odhadované měsíční náklady |
| :--- | :--- | :--- |
| **Cloud Run (`omnis-backend`)** | `min-instances = 0`, `cpu_idle = true`, 512 MiB RAM, 1 vCPU | **0,00 USD** (Free Tier: 2 mil. dotazů zdarma) |
| **Cloud Firestore (Primární DB)** | Pojmenovaná DB `ai-studio-android-omnis-fba4ba28-3ec2-4eaf-a1a7-338554af8dfe`, Zero-Trust RBAC | **0,00 USD** (Spark/Free Tier kvóty v klidu) |
| **Secret Manager** | Automatická replikace, 7 tajných klíčů (včetně `AUDIT_HMAC_KEY`), per-secret IAM | **0,00 USD** (blízké Free Tier) |
| **Artifact Registry** | 1 repozitář `omnis-repo` (`europe-west1`) | **0,00 USD** (Free Tier: 0,5 GB zdarma) |
| **Cloud Build** | Multi-stage Python 3.11 Docker image + ASGI smoke test | **0,00 USD** (Free Tier: 120 minut denně) |
| **VPC / Cloud SQL `pgvector` (Volitelné)** | Uzamčeno pod `enable_cloud_sql = false` (nulové poplatky za VPC konektor v klidu) | **0,00 USD** (neaktivní) |
| **Budget Alert** | Automatické varování při 50 %, 80 % a 100 % limitu 5 USD | **Ochrana před přečerpáním** |

---

## 2. Oddělené Project ID a proměnné prostředí

Platforma podporuje oddělení **GCP Cloud Run projektu** (`PROJECT_ID`) a **Firebase projektu** (`FIREBASE_PROJECT_ID`) pomocí proměnných prostředí a substitucí:

| Proměnná | Význam | Výchozí / Ukázková hodnota |
| :--- | :--- | :--- |
| `PROJECT_ID` | Cílový GCP projekt pro Cloud Run, Artifact Registry a Secret Manager | Nastavte podle vašeho GCP projektu |
| `FIREBASE_PROJECT_ID` | Projekt pro Firebase Auth, App Check a Cloud Firestore | Např. `gen-lang-client-0328950802` (nebo vlastní Firebase projekt) |
| `FIRESTORE_DATABASE_ID` | Identifikátor pojmenované Firestore databáze | `ai-studio-android-omnis-fba4ba28-3ec2-4eaf-a1a7-338554af8dfe` |
| `ENABLE_CLOUD_KMS` | Volitelné hardwarové podepisování auditních stop ECDSA P-256 (`true`/`false`) | `false` (HMAC-SHA256-HASH-CHAIN) |
| `OMNIS_BACKEND_URL` | Produkční HTTPS URL nasazené služby Cloud Run pro Android `BuildConfig` | `https://ais-dev-43vgrwdupsfj4ht5yohxcz-291037164760.europe-west3.run.app` |

---

## 3. Automatické nasazení Cloud Run backendu

### Možnost A: All-in-One provisioning & deploy skript

V Google Cloud Shell nebo terminálu s přihlášeným `gcloud`:

```bash
chmod +x scripts/deploy_cloud_run.sh
export PROJECT_ID="<VAS_GCP_PROJECT_ID>"
export FIREBASE_PROJECT_ID="<VAS_FIREBASE_PROJECT_ID>"

./scripts/deploy_cloud_run.sh --project="$PROJECT_ID" --firebase-project="$FIREBASE_PROJECT_ID" --region=europe-west1 -y
```

Tento skript idempotentně provede:
1. Aktivaci API (`run`, `secretmanager`, `artifactregistry`, `cloudbuild`, `firestore`, `iam`, `billingbudgets`; `sqladmin` dle potřeby).
2. Vytvoření Docker repozitáře `omnis-repo` v Artifact Registry.
3. Nastavení Service Accountu `omnis-cloud-run` s minimálními oprávněními (*Zero-Trust Per-Secret IAM*).
4. Garanci všech 7 položek v Secret Manageru (`JWT_SECRET_KEY`, `AUDIT_HMAC_KEY`, `OMNIS_SYNC_API_KEY`, `GOOGLE_CLIENT_ID`, `GEMINI_API_KEY`, `OMNIS_ADMIN_SECRET_HASHES`, volitelně `OMNIS_DATABASE_URL`).
5. Sestavení produkčního multi-stage Python 3.11 Docker image v Cloud Buildu včetně **ASGI import smoke testu** (`from backend.main import app`).
6. Nasazení služby `omnis-backend` na Cloud Run (`ENV=production`, `REQUIRE_APP_CHECK=true`, `min-instances=0`, `max-instances=3`, `512Mi`).
7. Ověření živých endpointů `/healthz`, `/api/v1/health` a `/docs`.

### Možnost B: Terraform infrastruktura jako kód (IaC)

```bash
terraform init
terraform validate
terraform plan \
  -var="project_id=<VAS_GCP_PROJECT_ID>" \
  -var="firebase_project_id=<VAS_FIREBASE_PROJECT_ID>" \
  -var="enable_cloud_sql=false" \
  -var="billing_account_id=<VAS_BILLING_ACCOUNT_ID>"
terraform apply
```

---

## 4. Nasazení Firestore pravidel & Google Sign-In (Android Credential Manager)

1. **Nasazení `firestore.rules`:** Ověřte pravidla v emulátoru (`FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`) a nasaďte je do Firebase projektu.
2. **OAuth 2.0 Web Client ID (pro Android `GetSignInWithGoogleOption` & backend verifikaci):**
   - V [Google Cloud Console](https://console.cloud.google.com/) vyberte váš projekt a přejděte do **APIs & Services -> Credentials -> Create OAuth client ID (Web application)**.
   - Pro produkční i náhledové prostředí registrujte aktuální původy:
     - `https://ais-dev-43vgrwdupsfj4ht5yohxcz-291037164760.europe-west3.run.app`
     - `https://ais-pre-43vgrwdupsfj4ht5yohxcz-291037164760.europe-west3.run.app`
   - Pro nativní Android aplikaci registrujte také Android OAuth klienta s `packageName = com.aistudio.omnis.app.bnndfx` a SHA-1/SHA-256 otiskem vašeho produkčního podpisového klíče (Play App Signing).
   - Vygenerovaný **Web Client ID** vložte do Google Cloud Secret Manageru (`GOOGLE_CLIENT_ID`) a v AI Studio **Secrets panelu**.

---

## 5. Sestavení podepsaného Android Release balíčku (`AAB` / `APK`)

1. **Verifikace unit a Robolectric testů (včetně migrací Room v12->v18):**
   ```bash
   gradle :app:testDebugUnitTest
   ```
2. **Sestavení produkčního Android App Bundle (`.aab`) s R8 minifikací a aktuální URL backendu:**
   ```bash
   gradle :app:bundleRelease -POMNIS_BACKEND_URL="https://<VASE_CLOUD_RUN_URL>"
   ```
3. **Finální kontrolní body před publikací na Google Play:**
   - Aktivovaný produkční **Firebase App Check** (Play Integrity API) a `REQUIRE_APP_CHECK=true` na backendu.
   - Podepsání přes Play App Signing / zabezpečený release keystore v CI tajemstvích (nikdy neukládat produkční keystore do Gitu).
   - Ověřená pravidla zálohování (`data_extraction_rules.xml` vylučuje lokální šifrovanou Room DB a API prefs).
