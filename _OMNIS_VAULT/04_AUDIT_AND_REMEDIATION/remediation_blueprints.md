# `[FIX-001]` O.M.N.I.S. Definitivní Návrhy Náprav (Remediation Blueprints)

Tento dokument definuje uzamčené produkční vzory (`DEFINITIVE_LOCKED`) odstraňující zjištěné defekty `ERR-001` až `ERR-008`.

## 1. Kryptografická serializace a imunita `zk_audit_ledger` (`FIX-001`)
- **Zámek zápisu:** Každý `INSERT` do `public.zk_audit_ledger` spouští `BEFORE INSERT` trigger `trg_zk_audit_ledger_before_insert`, který získá transakční poradní zámek `pg_advisory_xact_lock(hashtext('omnis_zk_audit_ledger_chain_lock'))`.
- **Neměnnost:** Triggery `trg_zk_audit_ledger_immutable` (`BEFORE UPDATE OR DELETE`) a `trg_zk_audit_ledger_no_truncate` (`BEFORE TRUNCATE`) vyvolávají výjimku `ERRCODE = '42501'` (`SECURITY_VIOLATION`) i pro superuživatele.
- **Role:** Zápisová služba backendu používá `AUDIT_DATABASE_URL` s rolí `omnis_audit_append` (`INSERT, SELECT` pouze nad `zk_audit_ledger`).

## 2. Sjednocená perzistence PostgreSQL 17 + ElectricSQL + Room v18 (`FIX-002`)
- **Jediný zdroj pravdy:** `backend/db/init.sql` kombinuje rozšíření `vector`, `age`, `pgcrypto`, relační tabulky (`rag_memory_64d`, `tensor_states_8d`, `messages`, `goals`, `rag_fragments`, `causal_nodes`, `causal_edges`, `retraction_watch_blacklist`), graf Apache AGE `omnis_causal_graph`, RLS politiky a publikační replikaci `electric_publication_default`.
- **WAL ochrana:** `docker-compose.yml` vynucuje `-c max_slot_wal_keep_size=2GB` proti zaplnění disku při dlouhodobém výpadku mobilních klientů.
- **Lokální Android šifrování:** `OmnisDatabase` (Room v18) šifruje lokální databázové stránky přes `SQLCipher` AES-256-CBC s klíčem uloženým v `Android Keystore` (`DatabasePassphraseProvider`) a změny atomicky zapisuje do `omnis_sync_outbox`.

## 3. Lock-free Apache AGE -> CSR výměna (`FIX-003`)
- `AgeCausalGraphRepository` kompiluje neměnný `CsrGraphSnapshot` (`frozen=True`) a při události `LISTEN omnis_graph_invalidated` provede atomickou výměnu reference bez blokování čtecích vláken brány G3 (`Do-Calculus`).

## 4. Reprodukovatelný build a CI závory (`FIX-004`)
- `gradle/libs.versions.toml` zůstává striktně v kořenovém adresáři `gradle/`.
- GitHub Actions workflow `omnis-ci.yml` ověřuje backendové testy, sjednocenou DB závoru (`unified-db-integrity-gate` přes `scripts/pre_deletion_gate.sh`), Android unit/Robolectric testy (`:app:testDebugUnitTest`) i Release AAB (`:app:bundleRelease`).
