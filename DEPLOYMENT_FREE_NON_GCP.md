# OMNIS by Suseto (O.M.N.I.S. APEX v4.5) — Zero-Cost ($0/měsíc) Deployment & Operator Manual

Tento manuál obsahuje všechny kroky vyžadující lidskou interakci (registrace bezplatných účtů, generování kryptografických klíčů, nastavení firewallu a vložení tajných klíčů do GitHub Secrets), které AI agent nemůže provést autonomně. Všechny názvy služeb, proměnných prostředí, endpointů a CI secrets jsou ověřeny proti aktuálnímu kódu repozitáře.

---

## Přehled architektury, stavu přechodu a nulových nákladů ($0/měsíc)

| Vrstva systému | Původní placená služba | Nová bezplatná alternativa ($0/měsíc) | Stav v kódu & odkaz |
| :--- | :--- | :--- | :--- |
| **Cloud Server & DB** | Google Cloud SQL + Cloud Run | **Oracle Cloud Always Free ARM Ampere A1** (2 OCPU, 12 GB RAM, 100–200 GB disk) + **PostgreSQL 17 (`pgvector` + `Apache AGE` + `pgcrypto`)** | **100% připraveno** (`docker-compose.yml` služba `db`, sjednocený `backend/main.py`) · [oracle.com/europe/cloud/free](https://www.oracle.com/europe/cloud/free/) |
| **Realtime Sync** | Firebase Firestore | **ElectricSQL 1.8.1** (self-hosted nad PostgreSQL WAL) + **Room v19 SQLCipher AES-256** | **100% připraveno** (`omnis_electric` + `omnis_sync_outbox`) · [electric-sql.com](https://electric-sql.com/) |
| **Doména & HTTPS** | Cloud Load Balancer / Managed SSL | **DuckDNS** (zdarma subdoména) + **Caddy 2** (automatický Let's Encrypt TLS přes HTTP-01/TLS-ALPN-01) | **100% připraveno** (`Caddyfile` s výchozím `omnis.duckdns.org` přepsatelným přes `DOMAIN_NAME`) · [duckdns.org](https://www.duckdns.org/) |
| **Serverová LLM Kaskáda** | Placené Gemini / Vertex AI | **Ollama (`gemma4:e4b`)** -> **GroqCloud Free Tier** -> **Google AI Studio Free Tier** -> **OpenRouter `:free`** | **100% připraveno** (`backend/app/llm_cascade.py`) · [aistudio.google.com](https://aistudio.google.com/) / [console.groq.com](https://console.groq.com/) / [openrouter.ai](https://openrouter.ai/) |
| **Android Client Auth & URL** | Cloud Run URL & Firebase Auth | **Sovereign server přes `-POMNIS_BACKEND_URL`** + volitelně Google Sign-In (`CredentialManager`) nebo OMNIS JWT (`/api/auth/login`) | **100% připraveno** (`app/build.gradle.kts` výchozí adresa míří na `https://omnis-apex-core.duckdns.org`, Room Outbox -> PostgreSQL 17 Gateway) |
| **CI/CD & Analýza** | Google Cloud Build + SonarQube Paid | **GitHub Actions** (`production-ci-cd.yml`, `omnis-ci.yml`, `omnis-zero-cost-ci.yml`) + **SonarQube Cloud Free** | **100% připraveno** · [sonarcloud.io](https://sonarcloud.io/) |

---

## Krok 1: Vygenerování SSH klíčů na vašem lokálním počítači

Před vytvořením virtuálního serveru si vygenerujte moderní `ed25519` SSH klíč, kterým se budete přihlašovat na server i přes GitHub Actions:

```bash
#!/usr/bin/env bash
set -euo pipefail

mkdir -p "$HOME/.ssh"
chmod 700 "$HOME/.ssh"
ssh-keygen -t ed25519 -C "omnis-apex-oci-deploy" -f "$HOME/.ssh/omnis_oci_ed25519" -N ""
chmod 600 "$HOME/.ssh/omnis_oci_ed25519"
chmod 644 "$HOME/.ssh/omnis_oci_ed25519.pub"

echo "=== VEREJNY KLIC (vlozte do Oracle Cloud pri tvorbe VM) ==="
cat "$HOME/.ssh/omnis_oci_ed25519.pub"
```

---

## Krok 2: Založení Oracle Cloud Always Free účtu a ARM Ampere A1 VM

1. **Registrace účtu:**
   - Otevřete [https://www.oracle.com/europe/cloud/free/](https://www.oracle.com/europe/cloud/free/) a klikněte na **Start for free**.
   - **Home Region (kritické pro GDPR a latenci z ČR):** Zvolte **Germany Central (Frankfurt)** (`eu-frankfurt-1`) nebo **Netherlands Northwest (Amsterdam)** (`eu-amsterdam-1`). Domovský region nelze později změnit.
   - Ověřte identitu platební kartou (Oracle provede pouze dočasnou autorizaci ~1 EUR, kterou vrátí; dokud účet ručně nepovýšíte na *Pay As You Go*, nic nenaúčtuje).
2. **Vytvoření virtuálního serveru (Compute Instance):**
   - V OCI Konzoli otevřete menu vlevo nahoře: **Compute -> Instances -> Create instance**.
   - **Name:** `omnis-apex-core-01`
   - **Image and shape -> Edit:**
     - **Image:** Klikněte *Change image* -> vyberte **Canonical Ubuntu** -> verze **24.04 Minimal aarch64** (ARM64).
     - **Shape:** Klikněte *Change shape* -> vyberte **Ampere** -> zaškrtněte **VM.Standard.A1.Flex** -> nastavte **2 OCPUs** a **12 GB memory** (hluboko v limitu Always Free: 4 OCPU + 24 GB RAM zdarma 24/7).
   - **Networking:** Ponechte výchozí VCN a zaškrtněte **Assign a public IPv4 address**.
   - **Add SSH keys:** Vyberte *Paste public keys* a vložte obsah souboru `~/.ssh/omnis_oci_ed25519.pub` z Kroku 1.
   - **Boot volume:** Zaškrtněte *Specify a custom boot volume size* a zadejte **100 GB** (Always Free povoluje až 200 GB zdarma).
   - Klikněte na **Create** a poznamenejte si **Public IP address** (např. `130.61.xxx.xxx`).
   - *Tip při hlášce "Out of capacity":* Změňte *Availability Domain* (`AD-1`, `AD-2`, `AD-3`) v sekci Placement.

---

## Krok 3: Otevření portů 80 a 443 v Oracle Cloud VCN Firewallu

Oracle Cloud blokuje HTTP (`80`) a HTTPS (`443`) na dvou úrovních — v cloudovém VCN Security Listu a uvnitř Ubuntu `iptables`. Porty `5432`, `3000` a `8000` z internetu neotvírejte (veškerý provoz jde bezpečně přes Caddy na `443`).

1. V detailu instance `omnis-apex-core-01` klikněte na odkaz u **Subnet** (např. `subnet-xxxxxxxx`).
2. Klikněte na **Default Security List for vcn-xxxxxxxx** -> **Add Ingress Rules**.
3. Přidejte dvě pravidla:
   - **Pravidlo 1 (HTTP pro Let's Encrypt ACME):** Source CIDR `0.0.0.0/0`, IP Protocol `TCP`, Destination Port Range `80`.
   - **Pravidlo 2 (HTTPS pro O.M.N.I.S. API & ElectricSQL):** Source CIDR `0.0.0.0/0`, IP Protocol `TCP`, Destination Port Range `443`.
4. Klikněte **Add Ingress Rules**.

---

## Krok 4: Získání bezplatné domény (DuckDNS) pro automatické HTTPS

Android zakazuje nešifrovaný HTTP provoz. Doména DuckDNS umožní kontejneru `caddy` automaticky vystavit platný Let's Encrypt TLS certifikát (bez potřeby speciálního DNS pluginu).

1. Otevřete [https://www.duckdns.org/](https://www.duckdns.org/) a přihlaste se přes GitHub nebo Google účet.
2. Zkopírujte svůj **token** z horní části stránky (`DUCKDNS_TOKEN`).
3. Do pole **sub domain** zadejte unikátní název (např. `omnis-apex-core`) a klikněte **add domain** -> vznikne `omnis-apex-core.duckdns.org`.
4. Do pole **current ip** vložte **Public IPv4 adresu** svého OCI serveru z Kroku 2 a klikněte **update ip**.

---

## Krok 5: Získání bezplatných API klíčů pro LLM kaskádu ($0)

Zaregistrujte si bezplatné API klíče pro automatický multi-provider failover v `backend/app/llm_cascade.py`:

1. **GroqCloud (Tier 2 — ultra-rychlá inference zdarma):**
   - [https://console.groq.com/keys](https://console.groq.com/keys) -> **Create API Key** -> uložte jako `GROQ_API_KEY`.
2. **Google AI Studio (Tier 3 — volitelný Gemini Free Tier):**
   - [https://aistudio.google.com/apikey](https://aistudio.google.com/apikey) -> **Create API key** -> uložte jako `GEMINI_API_KEY`.
3. **OpenRouter (Tier 4 — záložní router `:free` modelů):**
   - [https://openrouter.ai/settings/keys](https://openrouter.ai/settings/keys) -> **Create Key** -> uložte jako `OPENROUTER_API_KEY`.
4. **Ollama (Tier 1 — volitelně 100% lokálně na serveru):**
   - [https://ollama.com/](https://ollama.com/) (`ollama pull gemma4:e4b`). Pokud Ollama na serveru nepoběží, nastavte `LLM_PROVIDER_MODE=all` a router automaticky přepne na Groq / Gemini / OpenRouter.

---

## Krok 6: Inicializace OCI Serveru (Docker, Firewall & Klonování Repozitáře)

Připojte se přes SSH na váš OCI server (nahraďte `<OCI_PUBLIC_IP>` skutečnou IP adresou):

```bash
ssh -i "$HOME/.ssh/omnis_oci_ed25519" ubuntu@<OCI_PUBLIC_IP>
```

Na serveru spusťte:

```bash
#!/usr/bin/env bash
set -euo pipefail

# 1. Aktualizace systemu a zakladni nastroje
sudo apt-get update && sudo apt-get upgrade -y
sudo apt-get install -y ca-certificates curl git iptables-persistent openssl

# 2. Instalace oficialniho Docker Engine a Docker Compose v2 (ARM64)
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"

# 3. Otevreni portu 80 a 443 v OCI Ubuntu iptables
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save

# 4. Klonovani repozitare do /opt/omnis
sudo mkdir -p /opt/omnis
sudo chown -R "$USER":"$USER" /opt/omnis
git clone https://github.com/suseto-ui/OMNIS.git /opt/omnis
```

*(Pokud používáte vlastní fork repozitáře, upravte URL v `git clone`.)* Odhlaste se (`exit`) a znovu se přihlaste přes SSH, aby se aktivovalo členství ve skupině `docker`.

---

## Krok 7: Konfigurace `.env` a spuštění stacku na serveru

Na OCI serveru v `/opt/omnis` vygenerujte `.env` podle proměnných vyžadovaných soubory `docker-compose.yml`, `backend/config.py` a `Caddyfile`:

```bash
#!/usr/bin/env bash
set -euo pipefail

cd /opt/omnis

PG_MASTER_PW="$(openssl rand -hex 16)"
PG_APP_PW="$(openssl rand -hex 16)"
PG_AUDIT_PW="$(openssl rand -hex 16)"
PG_SYNC_PW="$(openssl rand -hex 16)"

cat <<EOF > .env
# === O.M.N.I.S. APEX v4.5 PRODUCTION ENVIRONMENT ($0 OSS) ===
ENV=production
USE_OSS_POSTGRES_SYNC=true
REQUIRE_APP_CHECK=false

DOMAIN_NAME=omnis-apex-core.duckdns.org
DUCKDNS_TOKEN=<VAS_DUCKDNS_TOKEN>

# PostgreSQL 17 (služba 'db' v docker-compose.yml, DB 'omnis_db', least-privilege role)
POSTGRES_DB=omnis_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=${PG_MASTER_PW}
POSTGRES_APP_USER=omnis_app_rw
POSTGRES_APP_PASSWORD=${PG_APP_PW}
POSTGRES_AUDIT_USER=omnis_audit_append
POSTGRES_AUDIT_PASSWORD=${PG_AUDIT_PW}
POSTGRES_SYNC_USER=omnis_sync_repl
POSTGRES_SYNC_PASSWORD=${PG_SYNC_PW}

DATABASE_URL=postgresql+asyncpg://omnis_app_rw:${PG_APP_PW}@db:5432/omnis_db
AUDIT_DATABASE_URL=postgresql+asyncpg://omnis_audit_append:${PG_AUDIT_PW}@db:5432/omnis_db

# ElectricSQL 1.8.1 Sync Engine
ELECTRIC_URL=http://electric:3000
ELECTRIC_INSECURE=false

# Zero-Cost Multi-Provider LLM Cascade
LLM_PROVIDER_MODE=all
GEMMA4_API_BASE_URL=http://host.docker.internal:11434/v1
GEMMA4_MODEL_NAME=gemma4:e4b
GROQ_API_KEY=<VAS_GROQ_API_KEY>
GEMINI_API_KEY=<VAS_GEMINI_API_KEY>
OPENROUTER_API_KEY=<VAS_OPENROUTER_API_KEY>

# Povinné produkční kryptografické klíče (ověřováno v backend/config.py)
SECRET_KEY=$(openssl rand -hex 32)
AUDIT_HMAC_KEY=$(openssl rand -hex 32)
OMNIS_SYNC_API_KEY=$(openssl rand -hex 24)
EOF

chmod 600 .env
```

*(Nahraďte `<VAS_DUCKDNS_TOKEN>` a API klíče svými skutečnými hodnotami.)*

Nastavte cron job pro udržování DuckDNS záznamu (každých 5 minut) a denní zálohování PostgreSQL 17:

```bash
mkdir -p /opt/omnis/duckdns /opt/omnis/backups
cat <<'EOF' > /opt/omnis/duckdns/duck.sh
#!/usr/bin/env bash
set -euo pipefail
source /opt/omnis/.env
SUBDOMAIN="${DOMAIN_NAME%%.duckdns.org}"
curl -fsS "https://www.duckdns.org/update?domains=${SUBDOMAIN}&token=${DUCKDNS_TOKEN}&ip=" -o /opt/omnis/duckdns/duck.log
EOF
chmod +x /opt/omnis/duckdns/duck.sh
(crontab -l 2>/dev/null; echo "*/5 * * * * /opt/omnis/duckdns/duck.sh >/dev/null 2>&1") | crontab -
(crontab -l 2>/dev/null; echo "0 3 * * * docker exec omnis_pg17 pg_dump -U postgres omnis_db | gzip > /opt/omnis/backups/omnis_db_\$(date +\%F).sql.gz") | crontab -
/opt/omnis/duckdns/duck.sh
```

Spusťte sestavení a start všech 4 kontejnerů (`db`, `electric`, `backend`, `caddy`) a naplňte otevřené datové sady (Retraction Watch, CISA KEV/MITRE, OpenAlex):

```bash
cd /opt/omnis
chmod +x scripts/pre_deletion_gate.sh scripts/verify_cutover.sh
./scripts/pre_deletion_gate.sh

docker compose up -d --build
docker compose ps

# Ověření rozšíření vector, age a pgcrypto v PostgreSQL 17 (služba 'db')
docker compose exec db psql -U postgres -d omnis_db -c "SELECT extname, extversion FROM pg_extension;"

# Naplnění veřejných databází (Brána G5, kauzální hrany G3, 64D RAG rag_memory_64d)
docker compose exec backend python3 /app/scripts/ingest_open_data.py
```

Ověřte HTTPS endpointy přes vaši doménu:

```bash
curl -fsSL https://omnis-apex-core.duckdns.org/health
curl -fsSL https://omnis-apex-core.duckdns.org/healthz
curl -fsSL https://omnis-apex-core.duckdns.org/api/v1/health

source /opt/omnis/.env
OMNIS_BACKEND_URL="https://omnis-apex-core.duckdns.org" \
OMNIS_SYNC_API_KEY="${OMNIS_SYNC_API_KEY}" \
./scripts/verify_cutover.sh
```

---

## Krok 8: Nastavení GitHub Secrets pro automatické CI/CD ($0)

V repozitáři na GitHubu otevřete **Settings -> Secrets and variables -> Actions -> New repository secret** a přidejte klíče podle workflows `.github/workflows/production-ci-cd.yml`, `.github/workflows/omnis-zero-cost-ci.yml` a `.github/workflows/omnis-ci.yml`:

| Název Secretu | Hodnota | Použití |
| :--- | :--- | :--- |
| `OCI_VM_HOST` | Veřejná IPv4 adresa vašeho OCI serveru (např. `130.61.xxx.xxx`) | `omnis-zero-cost-ci.yml` SSH deploy |
| `OCI_VM_USER` | `ubuntu` | `omnis-zero-cost-ci.yml` SSH deploy |
| `OCI_SSH_PRIVATE_KEY` | Kompletní obsah privátního klíče `~/.ssh/omnis_oci_ed25519` | `omnis-zero-cost-ci.yml` SSH deploy |
| `OMNIS_BACKEND_URL` | `https://omnis-apex-core.duckdns.org` | Build Android Release AAB/APK ve všech CI workflows |
| `OMNIS_KEYSTORE_BASE64` *(volitelně)* | Base64 zakódovaný `.jks` keystore (`base64 -w0 release.jks`) | `production-ci-cd.yml` kryptografický podpis Release APK/AAB (`SIGNING_STATUS=SIGNED`; bez něj `UNSIGNED_R8_ONLY`) |
| `OMNIS_KEYSTORE_PASSWORD` *(volitelně)* | Heslo ke keystore souboru | `production-ci-cd.yml` Release signing |
| `OMNIS_KEY_ALIAS` *(volitelně)* | Alias klíče v keystore | `production-ci-cd.yml` Release signing |
| `OMNIS_KEY_PASSWORD` *(volitelně)* | Heslo ke klíči v keystore | `production-ci-cd.yml` Release signing |

> Každý běh `production-ci-cd.yml` i lokální skript `./scripts/release_v4_5_0.sh` ukládá do výstupních artefaktů soubor `SIGNING_STATUS.txt` (`SIGNED` vs. `UNSIGNED_R8_ONLY`) a kontrolní součty `SHA256SUMS.txt`.

### Volitelné: Bezplatná statická analýza kódu (SonarQube Cloud Free)
1. Přihlaste se přes GitHub na [https://sonarcloud.io/](https://sonarcloud.io/).
2. Importujte repozitář (analýza veřejných repozitářů je trvale zdarma).
3. Zkopírujte vygenerovaný `SONAR_TOKEN` do GitHub Secrets.

---

## Krok 9: Propojení a kompilace Android klienta (Kotlin 2.0)

Soubor `app/build.gradle.kts` čte cílovou adresu serveru z Gradle property `-POMNIS_BACKEND_URL` nebo proměnné prostředí `OMNIS_BACKEND_URL` (nikoli z `local.properties`).

Spusťte testy a sestavení APK/AAB nasměrované na váš nový server:

```bash
gradle :app:testDebugUnitTest --no-daemon
gradle :app:assembleDebug -POMNIS_BACKEND_URL=https://omnis-apex-core.duckdns.org --no-daemon
gradle :app:bundleRelease -POMNIS_BACKEND_URL=https://omnis-apex-core.duckdns.org --no-daemon
```

Hotový APK balíček najdete v `app/build/outputs/apk/debug/app-debug.apk`. Heslová registrace (`POST /api/auth/register`) pro hlavní administrátorský e-mail `susetofu@gmail.com` je z bezpečnostních důvodů blokována (HTTP 403); automatická elevace na roli `ADMIN_OPERATOR` probíhá výhradně po ověřeném přihlášení přes Google Sign-In (`POST /api/auth/google` s `email_verified: true`).

### Poznámka k úplnému opuštění Firebase na straně Androidu
- Serverový stack (`PostgreSQL 17 + ElectricSQL + FastAPI + Caddy`) již běží **100 % mimo Google Cloud**.
- Na straně Android aplikace zatím zůstává `app/google-services.json` pro Google Sign-In (`GoogleAuthManager.kt`) a klientskou AI (`FirebaseAiService.kt`), zatímco synchronizace zpráv a telemetrie míří přes `OmnisSyncRepository` a `OmnisBatchSyncWorker` na `BuildConfig.OMNIS_BACKEND_URL`.
- Pokud byste chtěli odstranit i Google Sign-In na Androidu, používejte přímo OMNIS přihlášení (`POST /api/auth/register` a `POST /api/auth/login` v `UserAuthService.kt`).
