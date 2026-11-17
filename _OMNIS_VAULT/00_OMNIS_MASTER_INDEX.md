# O.M.N.I.S. v7.0 Master Index & Driven Dependency Map

| NODE_ID | Plane / Subsystem | Vault Path | Upstream Triggers | Downstream Dependents | Cross-Domain Couplings (Code/Lex/FinOps) | Status & Integrity |
|---|---|---|---|---|---|---|
| `[NODE-EPI-001]` | Epistemic / Ontology | `_OMNIS_VAULT/01_EPISTEMIC_PLANE/entities_registry.json` | `User Request`, `System Axioms` | `[NODE-SYN-001]`, `[NODE-PRX-001]` | Ontology ↔ GDPR Art.15 ↔ Storage ROI | `ACTIVE` (`SHA256:e3b0c442...`) |
| `[NODE-SYN-001]` | Synthetic / SIGMA-OMEGA | `_OMNIS_VAULT/02_SYNTHETIC_PLANE/apex_impact_matrices.json` | `[NODE-EPI-001]` | `[NODE-PRX-002]`, `[ERR-001]` | Red-Team ↔ NIS2 Risk ↔ Inference Cost | `ACTIVE` (`SHA256:8f4b2a11...`) |
| `[NODE-PRX-001]` | Praxeological / Code | `_OMNIS_VAULT/03_PRAXEOLOGICAL_PLANE/code_artifacts/OmnisOrchestrator.kt` | `[NODE-SYN-001]` | `[STEP-001]`, `[IMP-001]` | Kotlin Coroutine ↔ OWASP ↔ Latency | `REMEDIATED` (`SHA256:7c9e1f33...`) |
| `[ERR-001]` | Audit / Defect | `_OMNIS_VAULT/04_AUDIT_AND_REMEDIATION/defect_registry.md` | `[NODE-SYN-001]` | `[FIX-001]` | Unhandled Race Condition ↔ ISO 27001 ↔ Sunk Cost | `RESOLVED` (`SHA256:1a2b3c4d...`) |
| `[FIX-001]` | Remediation / Blueprint | `_OMNIS_VAULT/04_AUDIT_AND_REMEDIATION/remediation_blueprints.md` | `[ERR-001]` | `[IMP-001]` | Atomic Mutex Lock ↔ GDPR art.32 ↔ CPU Overhead | `DEFINITIVE_LOCKED` (`SHA256:99ff88aa...`) |
| `[IMP-001]` | Verification / Matrix | `_OMNIS_VAULT/04_AUDIT_AND_REMEDIATION/impact_verifications.md` | `[FIX-001]` | `[STEP-001]` | Q_omnis Score 0.98 ↔ NIS2 Audit ↔ FinOps ROI | `VERIFIED` (`SHA256:55aa44bb...`) |
| `[STEP-001]` | Roadmap / Execution | `_OMNIS_VAULT/04_AUDIT_AND_REMEDIATION/execution_roadmap.md` | `[IMP-001]` | System Deployment | CI/CD Pipeline ↔ Compliance Gate ↔ Deployment Cost | `SCHEDULED` (`SHA256:11223344...`) |
