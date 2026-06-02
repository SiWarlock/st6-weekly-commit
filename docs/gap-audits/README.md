# Gap audits — ST6 Weekly Commit Module

Adversarial gap-audit trail produced by `/arch-finalize` (Brain 2). Machine-readable detail lives alongside this file; the binding resolutions live in the root `ARCHITECTURE.md`.

## 001-arch-finalize-gap-audit.json

16-dimension adversarial audit of `docs/planning/ARCHITECTURE_DRAFT.md` against the PRD + planning corpus (Opus 4.8, 33-agent workflow, every finding re-verified against the artifacts to kill false positives). Schema per dimension: `{ dimension, verified_findings:[{ id, title, severity, verdict, verification_evidence, finding, proposed_fix, arch_anchor }] }` plus a completeness `critic`.

**Outcome:** 110 confirmed/partial findings (13 candidate findings rejected as already-covered). 2 critical, 60 important, 43 nice-to-have, 3 proposed-edit, 2 question-for-human.

### Critical → resolution in ARCHITECTURE.md
- Remote-mode Auth0 token handoff undefined → **§7 Host integration contract** (`getAccessToken()` accessor + `VITE_AUTH_MODE`).
- No Flyway migration owner (API+worker race) → **§12 dedicated pre-deploy migration Job**, `flyway.enabled=false` elsewhere.

### Four human-confirmed load-bearing decisions (2026-06-02)
1. **PostgreSQL** → major 16, latest available RDS 16.x minor (16.4 uncreatable). (§4, OQ-007)
2. **Spring Boot 3.3** → keep the pin, document OSS-EOL. (§Locked decisions, OQ-008)
3. **Manager review-block Outlook event** → keep, fixed per-manager/week key. (§4, §10)
4. **Comments** → flat one-level for MVP, schema kept nestable. (§4, §11, §19)

### Important themes folded into the contract
API error model + per-command preconditions (§5); SYSTEM principal + IDOR denial cases (§6); CORS / IRSA / OIDC CI / Secrets CSI / CronJob image (§12–§13); `alignment_status` ownership + `manager_alignment_note` + dropped `progress_status` + enumerated `risk_badges` + single-active-manager index + `@Version` (§3–§4); Auth0 audience `AudienceValidator` (§6); demo-seed home (§12); deliverables (§18); UX view-state contract (§7); injectable `Clock` + async/worker/IDOR/security test plan (§17); Appendix A model inventory.

Nice-to-have items not folded in are listed as deferred in **ARCHITECTURE.md §20**.
