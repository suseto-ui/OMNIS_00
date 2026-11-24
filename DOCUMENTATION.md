# OMNIS by Suseto – KOMPLETNÍ OPERÁTORSKÝ, TECHNICKÝ A METODICKÝ MANUÁL (v4.5 / v7.0 Production Ready)

**OMNIS by Suseto (Autonomous Polymathic Execution & Invariant Engine — AKGE-8D)** je transdisciplinární kognitivní architektura a analytický ekosystém pro Android (Kotlin 2.0 / Jetpack Compose Material 3) integrovaný s Python FastAPI backendem, sjednocenou **PostgreSQL 17** databází (`pgvector` 64D HNSW, `Apache AGE` `omnis_causal_graph`, `pgcrypto`), **ElectricSQL 1.8.1** synchronizací a **8D tenzorovou logikou** pro nelineární modelování systémových dopadů, kauzální intervence a autonomní orchestraci.

---

## 📖 ČÁST I: UŽIVATELSKÝ A OPERÁTORSKÝ MANUÁL

### 1.1 Dvoudimenzionální Systém Řízení Rozhraní (Dimenze A & B)

```mermaid
quadrantChart
    title Dvoudimenzionální Řízení Rozhraní O.M.N.I.S.
    x-axis Kognitivní Úroveň: BEGINNER --> EXPERT
    y-axis Bezpečnostní Role: STANDARD_USER --> ADMIN_OPERATOR
    quadrant-1 Správce Infrastruktury & Expert
    quadrant-2 Správce v Přehledovém Režimu
    quadrant-3 Běžný Uživatel & Začátečník
    quadrant-4 Analytik s Expertním POHLEDEM
```

Architektura rozhraní O.M.N.I.S. je řízena dvěma **ortogonálními (na sobě nezávislými) dimenzemi**:

| Dimenze | Typ Řízení | Hodnoty | Primární Účel |
| :--- | :--- | :--- | :--- |
| **Dimenze A** | **Bezpečnostní Role (RBAC)** | `STANDARD_USER` vs `ADMIN_OPERATOR` | Řízení přístupových práv k systémovým akcím, diagnostice infrastruktury a izolaci konverzací. |
| **Dimenze B** | **Kognitivní Úroveň Pokročilosti** | `BEGINNER` (`STANDARD`) vs `EXPERT` | Řízení hustoty informací, vizuální složitosti, formátu výstupů a dostupnosti pokročilých ovládacích prvků. |

---

### 1.2 Dimenze A: Logika Bezpečnostních Rolí a Vliv na Frontend

#### A. Běžný Uživatel (`STANDARD_USER`):
1. **Frontendový Routing & Navigation Layout:**
   - Směrování probíhá přes izolovaný `UserNavigationLayout` a reaktivní `UserTabRouter`.
   - `UserTopAppBar` a `UserDrawerContent` neobsahují systémové diagnostické odznaky ani servisní přepínače.
2. **Přístupné Záložky (`UserTabRouter`):**
   - **Multi-Agentní Debata (`MULTI_AGENT_DEBATE`)**, **Kokpit (`DASHBOARD`)**, **Chat (`CHAT`)**, **Neurální Nexus (`NEXUS`)**, **Průvodce (`GUIDE`)**, **Cíle (`GOALS`)**, **Scénáře (`SCENARIOS`)**, **Artefakty (`ARTIFACTS`)**, **Paměť (`MEMORY`)**, **8D Radar (`ANALYTICS`)**, **Kauzální Simulátor (`CAUSAL_SIMULATOR`)**, **Dialektika (`DIALECTICS`)**, **Tensor Oversight (`TENSOR_OVERSIGHT`)**, **Nastavení (`SETTINGS`)**, **Profil (`PROFILE`)**, **Reputace (`REPUTATION`)**.
3. **Izolace Dat (Multi-Tenant & RLS):**
   - Na klientu jsou vlákna filtrována dle `currentUsername`, na backendu jsou tabulky v PostgreSQL 17 chráněny politikami Row-Level Security (`omnis.current_user_id`, `omnis.current_role`).

#### B. Administrátor / Operátor (`ADMIN_OPERATOR`):
1. **Vyhrazený Layout:** `AdminNavigationLayout`, `AdminTabRouter`, `AdminTopAppBar`, `AdminDrawerContent`.
2. **Exkluzivní Administrátorské Moduly (navíc oproti uživatelským záložkám):**
   - **APEX Dashboard (`APEX_DASHBOARD`)**: Agregovaná telemetrie a stabilita systému.
   - **Admin Hub (`ADMIN`)**: Diagnostika `AdminDbHealthCard`, stav `omnis_encrypted_db` (SQLCipher AES-256) a `omnis_sync_outbox`, synchronizace do PostgreSQL 17 / ElectricSQL, biometrický test a test JWT interceptoru.
   - **Kognitivní Uzly (`NODES`)**, **Matrix / Octagon (`MATRIX`)**, **Telemetrie (`TELEMETRY`)**, **Sémantická Testovací Laboratoř (`TEST_SEMANTIC`)**, **Dev Prompt Lab (`DEV_PROMPT_LAB`)**, **Produkční Audit (`PRODUCTION_AUDIT`)**.

---

## 🛡️ ČÁST II: LOGICKÁ VRSTVA GATEKEEPER G1–G6 (DUAL-GATE) & PERZISTENCE

```mermaid
flowchart TD
    INPUT[Vstupní Prompt / Výstup před 8D Maticí] --> G1[G1: Entry Point & Typová Sanitizace]
    G1 -->|AUDIT + OK| G2[G2: Concept Coupling Min 2 Koncepty]
    G2 -->|AUDIT + OK| G3[G3: Causal Path Do-Calculus & NaN/Inf Guard]
    G3 -->|AUDIT + OK| G4[G4: Quotable Evidence & Nenulový Vektor]
    G4 -->|AUDIT + OK| G5[G5: Retraction Blacklist & Injection Integrity]
    G5 -->|AUDIT + OK| G6[G6: Shannon Entropy S < 2.08 & ZK Hash]

    G1 -->|FAIL| EXC[GatekeeperChainException & Audit Stopa]
    G2 -->|FAIL| EXC
    G3 -->|FAIL| EXC
    G4 -->|FAIL| EXC
    G5 -->|FAIL| EXC
    G6 -->|FAIL| EXC

    G6 -->|PASS| MAT[8D Matice Dopadů & Odpověď]
```

- **Sekvenční logická vrstva `Gatekeeper G1–G6` (`GatekeeperG1G6.kt` / `backend/gateway/gatekeeper.py`):** Data jsou před vstupem do 8D Matice dopadů postupně sanitizována a ověřována branami `G1` až `G6` (Dual-Gate na vstupu i výstupu v `OmnisFivePhaseExecutor`, `UnifiedResponseEvaluator`, `PgVectorImpactMatrixView` a v backendových routerech/službách). Každá brána zaznamenává svůj průchod do auditního systému (s řetězeným SHA-256 otiskem) a při selhání (`NaN`, `Inf`, nulový vektor, injection) přeruší řetězec vyhozením `GatekeeperChainException`.

### 2.1 Sjednocená Perzistenční Vrstva
1. **Android Edge (`OmnisDatabase` v19 + SQLCipher):**
   - Šifrovaná databáze `omnis_encrypted_db` využívající AES-256-CBC a klíče spravované v Android Keystore (`DatabasePassphraseProvider`).
   - Atomický zápis zpráv a událostí do `omnis_sync_outbox` a lokální 64D L2-normalizované vektorové úložiště `omnis_local_vectors_64d` (`MIGRATION_18_19`), odbavované přes `OmnisBatchSyncWorker` a `OmnisSyncRepository` (`/api/v1/sync/batch`).
2. **Backend Core (`PostgreSQL 17` Multi-Engine — `backend/db/init.sql`):**
   - **`pgvector`**: Tabulka `public.rag_memory_64d` s HNSW kosinovým indexem (`m = 16, ef_construction = 128`) a hybridním RRF vyhledáváním (`hybrid_search_knowledge`).
   - **`Apache AGE`**: Perzistentní graf `omnis_causal_graph` synchronizovaný s `public.causal_nodes` a `public.causal_edges`. `AgeCausalGraphRepository` kompiluje neměnné `CsrGraphSnapshot` a při `LISTEN omnis_graph_invalidated` provádí lock-free atomickou výměnu reference.
   - **`pgcrypto` & `zk_audit_ledger`**: Striktě append-only auditní řetězec s transakční serializací (`pg_advisory_xact_lock`) a blokací `UPDATE`, `DELETE` i `TRUNCATE` pomocí databázových triggerů.
   - **`ElectricSQL 1.8.1`**: Logická replikace nad publikací `electric_publication_default` pro tabulky `messages`, `goals`, `rag_fragments` a `tensor_states_8d` (`REPLICA IDENTITY FULL`) s ochranným limitem `max_slot_wal_keep_size=2GB`.
3. **Ochrana Brány G5 (`retraction_watch_blacklist`):**
   - Funkce `verify_retraction_watch()` v `backend/app/db_engine.py` ověřuje dotazy proti tabulce `public.retraction_watch_blacklist`.

### 2.2 Inferenční Kaskáda, Circuit Breaker & Release Provenance
- **Android klient:** `FirebaseAiService` (`gemini-3.8-flash` / `gemini-3.5-flash-lite`) a lokální `HybridInferenceOrchestrator` (>= 4 GB RAM práh, 1500 ms timeout, bez dynamického načítání `.so`/`.dex`/`.jar`). `OmnisCircuitBreaker` při HTTP 429 respektuje hlavičku `Retry-After` v bezpečném intervalu **5–300 s** a propaguje degraded stav do UI přes `ChatStatusBanner`.
- **Backend server:** `backend/app/llm_cascade.py` (Tier 1 lokální Gemma 4 dle `tools/gemma4/agent.yaml` -> Groq -> Gemini -> OpenRouter s `ProviderCircuitBreaker` `CLOSED`/`OPEN`/`HALF-OPEN` a HTTP 429 `Retry-After` `[5, 300]` s), vystaveno přes `/api/v1/inference/cascade` a `/api/v1/inference/cascade/status`, s volitelným ECDSA P-256 podepisováním při `ENABLE_CLOUD_KMS=true` (výchozí je deterministický `HMAC-SHA256-HASH-CHAIN`).
- **Release `v4.5.0-GA` Pipeline:** `./scripts/release_v4_5_0.sh` i `.github/workflows/production-ci-cd.yml` ověří všechny brány, spustí Android & Python testy, sestaví R8/ProGuard APK/AAB (`versionCode = 450`, `versionName = "4.5.0"`) a vygenerují `SIGNING_STATUS.txt` (`SIGNED` při přítomnosti keystore, jinak `UNSIGNED_R8_ONLY`) společně s `SHA256SUMS.txt`.

---

## 📂 ČÁST III: TOPOLOGICKÝ VAULT INDEX (`_OMNIS_VAULT/`)

Veškeré ontologické registry, matice dopadů, referenční orchestrátor a auditní záznamy jsou evidovány v `_OMNIS_VAULT/` pod kontrolou SHA-256 kontrolních součtů:
- `_OMNIS_VAULT/00_OMNIS_MASTER_INDEX.md` — Hlavní index závislostí a SHA-256 otisků
- `_OMNIS_VAULT/00_TOPOLOGY_GRAPH.json` — Strojově čitelný topologický graf uzlů a hran
- `_OMNIS_VAULT/01_EPISTEMIC_PLANE/entities_registry.json` (`NODE-EPI-001`)
- `_OMNIS_VAULT/02_SYNTHETIC_PLANE/apex_impact_matrices.json` (`NODE-SYN-001`)
- `_OMNIS_VAULT/03_PRAXEOLOGICAL_PLANE/code_artifacts/OmnisOrchestrator.kt` (`NODE-PRX-001`)
- `_OMNIS_VAULT/04_AUDIT_AND_REMEDIATION/defect_registry.md` (`ERR-001`)
- `_OMNIS_VAULT/04_AUDIT_AND_REMEDIATION/remediation_blueprints.md` (`FIX-001`)
- `_OMNIS_VAULT/04_AUDIT_AND_REMEDIATION/impact_verifications.md` (`IMP-001`)
- `_OMNIS_VAULT/04_AUDIT_AND_REMEDIATION/execution_roadmap.md` (`STEP-001`)
