# O.M.N.I.S. • Google Cloud Console & Platform Integration Guide

Kompletní průvodce integrací systému **O.M.N.I.S.** s Google Cloud Platform (GCP) při striktním dodržení rozpočtového limitu **do 5 USD / měsíc** (Zero Cost v nečinnosti).

---

## 1. Architektura nákladové optimalizace (< 5 USD/měsíc)

| Komponenta | Konfigurace pro úsporu | Odhadované měsíční náklady |
| :--- | :--- | :--- |
| **Cloud Run** | `min-instances = 0`, `cpu_idle = true`, 512 MiB RAM | **0,00 USD** (Free Tier: 2 mil. dotazů zdarma) |
| **Secret Manager** | Automatické verzování, in-memory caching | **0,00 USD** (Free Tier: 6 secretů, 10 tis. operací) |
| **Artifact Registry** | 1 repozitář `omnis-repo` | **0,00 USD** (Free Tier: 0,5 GB zdarma) |
| **Cloud Build** | Cache optimalizované Docker vrstvy | **0,00 USD** (Free Tier: 120 minut denně) |
| **Cloud SQL / DB** | `db-f1-micro` se škálováním a budget guardem | **0,00 – 4,50 USD** (hlídáno rozpočtovým alarmem) |
| **Budget Alert** | Automatické varování při 50 %, 80 % a 100 % (5 USD) | **Ochrana před přečerpáním** |

---

## 2. Automatické nasazení a zprovoznění (All-in-One skript)

### Doporučená možnost: Jednokrokový commissioning skript

V Google Cloud Shell nebo terminálu s nainstalovaným a přihlášeným `gcloud`:

```bash
chmod +x scripts/deploy_cloud_run.sh
./scripts/deploy_cloud_run.sh [VAS_PROJECT_ID] [REGION=europe-west1]
```

Nebo s automatickým potvrzením a zadáním doplňkových klíčů:
```bash
./scripts/deploy_cloud_run.sh --project=<VAS_PROJECT_ID> --gemini-key="<VAS_GEMINI_KEY>" -y
```

Tento skript automaticky a idempotentně provede kompletní zprovoznění:
1. **Ověření a autodetekce:** Zkontroluje přihlášení v `gcloud`, nastaví aktivní projekt a region.
2. **Aktivace Google Cloud API:** Povolí `run`, `secretmanager`, `artifactregistry`, `cloudbuild`, `firestore`, `sqladmin`, `iam`, `billingbudgets`.
3. **Artifact Registry:** Vytvoří nebo ověří Docker repozitář `omnis-repo`.
4. **Service Accounts & Zero-Trust IAM:** Vytvoří `omnis-cloud-run` s minimálními rolemi (Secret Manager, Firestore, Cloud SQL, Logging) a udělí Cloud Build účtu práva pro build a nasazení revize.
5. **Garance Secret Manageru (Zero-Crash):** Zkontroluje všech 6 položek (`JWT_SECRET_KEY`, `OMNIS_SYNC_API_KEY`, `GOOGLE_CLIENT_ID`, `GEMINI_API_KEY`, `OMNIS_ADMIN_SECRET_HASHES`, `OMNIS_DATABASE_URL`). Pokud chybí, automaticky vygeneruje silné kryptografické klíče a admin hash tak, aby Cloud Run nasazení nikdy nespadlo na chybějící secret!
6. **Cloud Build & Multi-stage Docker:** Zkompiluje React SPA frontend (`tsc && vite build`), nainstaluje produkční Python backend a vytvoří optimalizovaný kontejner (< 120 MB).
7. **Nasazení na Cloud Run:** Zprovozní službu `omnis-backend` (512 MiB, 1 CPU, scale-to-zero = 0 USD v nečinnosti).
8. **Integrační smoke test:** Okamžitě otestuje `/healthz`, `/api/v1/health` i načítání frontendu.
9. **Konfigurace pro Android:** Vypíše přesné URL a příkaz pro sestavení Android aplikace.

### Možnost B: Terraform infrastruktura jako kód (IaC)

```bash
terraform init
terraform plan -var="project_id=<VAS_PROJECT_ID>" -var="billing_account_id=<VAS_BILLING_ACCOUNT_ID>"
terraform apply -auto-approve
```

---

## 3. Nastavení Google Cloud Console (OAuth 2.0 & Sign in with Google)

Pro bezproblémové přihlašování uživatelů na Androidu i na Webu:

1. Otevřete [Google Cloud Console](https://console.cloud.google.com/).
2. Vyberte váš projekt a přejděte do **APIs & Services -> OAuth consent screen**:
   * Zvolte **External** a zadejte název aplikace: `O.M.N.I.S.`.
   * Vyplňte kontaktní e-mail a uložte.
3. Přejděte do **APIs & Services -> Credentials**:
   * Klikněte na **+ CREATE CREDENTIALS -> OAuth client ID**.
   * Typ aplikace: **Web application**.
   * Název: `OMNIS Unified Web & Android Auth`.
   * Do **Authorized JavaScript origins** přidejte:
     - `https://ais-dev-ex6yxewfd4hwymxojrmgxr-291037164760.europe-west3.run.app`
     - `https://ais-pre-ex6yxewfd4hwymxojrmgxr-291037164760.europe-west3.run.app`
     - `http://localhost:5173`
   * Klikněte na **CREATE**.
4. Zkopírujte vygenerovaný **Client ID**:
   * Vložte jej do **Secrets panelu v AI Studio** pod klíčem `GOOGLE_CLIENT_ID`.
   * Vložte jej do Google Secret Manageru:
     ```bash
     echo -n "VAS_CLIENT_ID" | gcloud secrets versions add GOOGLE_CLIENT_ID --data-file=-
     ```

---

## 4. Přístup z aplikace a správa tajemství

Aplikace O.M.N.I.S. automaticky využívá hybridní vyhodnocení tajemství:
1. **Při lokálním vývoji:** Čte proměnné z `.env` (spravované přes Secrets panel v AI Studio).
2. **Při běhu v Google Cloud Run:** Runtime Service Account `omnis-cloud-run` bezpečně načítá hodnoty přímo z Google Cloud Secret Manageru bez jakéhokoliv hardcodování.
3. **Při nečinnosti:** Instance Cloud Run se okamžitě uspí na 0 kontejnerů, čímž nevznikají žádné zbytečné provozní poplatky.
