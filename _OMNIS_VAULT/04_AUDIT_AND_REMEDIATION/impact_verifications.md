# `[IMP-001]` Verifikace Dopadů, Quality Gate & FinOps Matice

## 1. Metrika Kvality a Testovací Brána ($Q_{\text{omnis}}$ & SonarQube Formula)
- **Vzorec kombinovaného pokrytí:**
  $$\text{Coverage} = \frac{CT + CF + LC}{2 \cdot B + EL} \ge 80.0\%$$
  Ověřováno skriptem `scripts/verify_quality_gate.py` nad Cobertura XML výstupem (`coverage.xml`).
- **Cyklomatická složitost:** Maximum $\le 15$ na metodu (ověřováno nástrojem `radon cc` v CI).
- **Kognitivní skóre $Q_{\text{omnis}}$:** Cílový práh $\ge 0.90$ při normální entropii ($S < 2.08$ nats) a nulovém počtu kritických děr v topologii ($b_1 = 0$ nebo stabilizovaný cyklus).

## 2. Normativní Pokrytí (Compliance Verification)
| Standard / Nařízení | Implementovaný Mechanismus | Soubor / Důkaz |
|---|---|---|
| **GDPR čl. 15 a 32** | SQLCipher AES-256-CBC, RLS `omnis.current_user_id`, PII redakce | `OmnisDatabase.kt`, `backend/db/init.sql`, `backend/security/sanitizer.py` |
| **NIS2 čl. 21** | Neměnný SHA-256 `zk_audit_ledger`, least-privilege DB role | `backend/security/zk_ledger_service.py`, `backend/db/init.sql` |
| **EU AI Act** | 6-bránový Refusal Ladder G1–G6, blokace Social Scoring, C2PA provenance | `backend/audit_router.py`, `backend/routers/inference.py`, `AiActTransparencyTest.kt` |
| **NIST SP 800-207** | Zero-Trust ověření každého požadavku (`X-OMNIS-API-KEY` / JWT / RBAC) | `backend/security/firebase_guard.py`, `backend/auth_router.py` |
| **Google Play Policy** | Nulová nebezpečná oprávnění úložiště, Photo Picker, bez DCL (`.dex`/`.so`/`.jar`) | `HybridInferenceOrchestrator.kt`, `AndroidManifest.xml` |

## 3. FinOps & TCO Bilance ($0/měsíc Sovereign OSS vs Cloud)
| Vrstva | Odpojené Cloud Služby | Cílová OSS Architektura | Měsíční Náklad (TCO) |
|---|---|---|---|
| **Relační + Vektorová DB** | Cloud SQL + Cloud Firestore | PostgreSQL 17 + `pgvector` HNSW 64D | **0 USD** (Self-hosted / OCI Always Free) |
| **Znalostní graf** | Externí Graph DB | Apache AGE (`omnis_causal_graph`) v témže PG17 | **0 USD** |
| **Offline-First Sync** | Firestore Listeners + Cloud Functions | ElectricSQL 1.8.1 + Room Outbox (`omnis_sync_outbox`) | **0 USD** |
| **LLM Inference** | Placené cloudové API bez fallbacku | Lokální Gemma 4 + Groq / Gemini / OpenRouter Free Tier kaskáda + Firebase AI Logic na Androidu | **0 USD** (v rámci Free Tier limitů) |
