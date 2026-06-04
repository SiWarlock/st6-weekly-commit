# Handoff — st6-main backend ORCHESTRATOR cycle, deploy-demo V5 start (2026-06-04)

> Cycling at **ACTION (75%)** at a clean boundary — V5 (brief 084) is dispatched + in the fresh impl's hands. **TIGHT handoff (area-state only).** The lead carries the cross-cutting re-scope (the now-required real Outlook+SQS/SNS sync pipeline + M365 tenant path; the §9-summary defer) in your spawn prompt — NOT here. HEAD = `455bed8`; backend tree clean (only the web-orch's frontend regions + its `083` brief are uncommitted — DO NOT touch them).

## CONTEXT: Phase 6 is DONE; we pivoted to a DEPLOYED-BACKEND DEMO
The user wants a **deployed wc-api + RDS Postgres serving the React SPA, with REAL Auth0/OAuth2** (not the demo header). Full EKS+RDS. Phase-6 backend is complete + sealed (`6ec8c8a` + `5b3bbcf`; continuity doc 019). The deploy-demo critical path = **V5/V6 seed → datasource config → Dockerfile**.

## YOUR IMMEDIATE STATE: V5 (brief 084) in flight
- **Fresh impl `st6-main-wc-api-implementer`** (~12%, just spun) is running `/session-start` → `/tdd v5-demo-seed-personas`. **You take V5 from its Step-2.5.** Don't re-dispatch.
- **Brief 084** (`docs/briefs/084-10.2-v5-demo-seed-personas-oauth-identity.md`): V5 = Dana Okafor (MANAGER) + 6 IC reports + 6 active `manager_relationship` rows (Dana→each; Dana unmanaged). Mirror `V4__seed_rcdo.sql` (fixed UUID literals, `ON CONFLICT DO NOTHING`, anchor 2026-06-02, `@st6demo.com`, tz America/Chicago). 1 commit, **no security-reviewer** (seed).
- **⚠️ THE OAuth identity decision (LEAD-RATIFIED — hold it at Step-2.5):** `external_subject` is **POPULATED** (not NULL). Set each employee's `external_subject` = a **deterministic app-owned literal** (e.g. `st6|dana-okafor`, `st6|report-01`), distinct per employee. The Auth0 tenant emits it as the `employee_id` claim → `PrincipalResolver.findByExternalSubject` resolves the JWT to the seeded employee. **Reuses the resolver with ZERO backend code change** (you're only seeding the column). NOT email (rejected — mutable + needs a resolver change). The **load-bearing test**: `findByExternalSubject(<dana's literal>)` → Dana (active).
- **Step-2.5 Qs to adjudicate:** the literal format (readable `st6|dana` vs UUID-string — my lean: readable); `external_subject` uniqueness (seed-distinctness vs a unique index); the 6 persona names/emails (Appendix E Part 2 if specified, else human-readable — they show in the demo command-center/heatmap).

## SEQUENCE after V5 (the deploy-demo critical path)
1. **V6 — source fixtures only** (the lifecycle-state matrix: plans in DRAFT/LOCKED/RECONCILING/RECONCILED × commitments × disputes × reviews × reconciliation outcomes × carry-forward × sync records). **DO NOT hand-write the projection literals** — instead, run the **existing 6.7 `ProjectionRebuildRunner`** (`--app.job=rebuild-projections`) on deploy to populate `manager_plan_summary`/`manager_heatmap_cell` from the seeded source. ~2 slices. (The literal `rebuild==seed` R1–R6 verification test → DEFERRED, not a demo blocker.)
2. **Datasource/JPA deploy config** (~0.5 slice) — no `spring.datasource` exists in any profile ("later phases"); add env-driven `spring.datasource.url/username/password` + `ddl-auto=validate`/Hikari to the prod/demo profile (infra sets the values via Secrets).
3. **Dockerfile** for wc-api (~0.5 slice — multi-stage Gradle→JRE; OR coordinate with infra on image-build ownership). No Dockerfile exists today.

## OAuth backend = ✅ BUILT (0 new slices)
Resource-server JWT validation (`application.yml`: `issuer-uri: ${AUTH0_ISSUER_URI:}` + `auth0.audience: ${AUTH0_AUDIENCE:}` + the `AudienceValidator` + RS256 decoder, LESSONS §12), the `Auth0ClaimMapper` (claims→`Auth0Identity`; `externalSubject` = the `employee_id` claim else `sub`) → `PrincipalJwtAuthenticationConverter` → `PrincipalResolver.findByExternalSubject` → `Employee`, the real-mode `SecurityFilterChain` (§19), env-driven CORS — ALL built (Phase 2). The only OAuth "gap" = populate `external_subject` (V5/084 does it). Deploy real mode: `SPRING_PROFILES_ACTIVE=prod`, `DEMO_AUTH_ENABLED` off, set `AUTH0_*`/`ROOT_DOMAIN`/`CORS_ALLOWED_ORIGINS`. **Demo runs wc-api + Postgres only** — the SNS gateway is the no-op `LoggingLifecycleSnsGateway` (BUT see the lead's re-scope: real Outlook+SQS/SNS may now be required — lead-carried).

## Auth0 tenant runbook — DRAFTED, needs a values-fill
`docs/runbooks/auth0-tenant-setup.md` (committed `455bed8`) — the user's HITL guide (API/audience, SPA app, env vars, 7 test users with seeded emails + `app_metadata.employee_id`, the post-login Action JS emitting `https://wc.<domain>/employee_id`, a verify section). **The email/`external_subject` values in its tables are PLACEHOLDERS** — do a **values-fill pass from the landed V5 migration at V5's Step-9** so the user maps the right Auth0 users.

## CONVENTIONS / lane / carry-forwards
- **Brief lane:** **084 = backend (V5)**, 083 = frontend (9.16), 082 frontend, 081 backend. **next-free = 085.** ⚠️ **RE-SYNC next-free two-way with `st6-main-wc-web-orchestrator` BEFORE authoring** — we collided on 083 (both grabbed it). Both tracks were paused; the web-orch is also cycling now.
- **Shared-doc serialize (two-way)** with the web-orch — claim the `MVP_TASKS`/`ARCHITECTURE` window, confirm-clean, `git add` backend/orch regions only (never `-A`), git-verify the shared tree clean, release. The lead's `team-lead` handle for sends is **`team-lead`** (NOT the registry-qualified name — a silent-delivery trap I hit; sends to `st6-main-team-lead` go to an unread inbox).
- **Reviewer policy:** per-slice reviewers OFF; ad-hoc security on rule-#3/IDOR slices only (V5/V6 seeds = none). Backend gate = `./gradlew check` from `apps/wc-api/`. Co-Author trailer: `Claude Opus 4.8 (1M context)`. Push deferred (no remote).
- **Per-slice flow:** Step-2.5 review (`APPROVED.`/`TWEAK:`/`ADD:` header) → Step-9 commit-message-first → after Step-10 run `/context-check st6-main` + ping `team-lead`. `/orchestrate-end` only on user/auto-cycle trigger.
- **Carry-forwards (parked):** the web-orch's **3 B.11/§9 follow-ups** (`resolvedDisputeCount`/`unlinkedCount`/`reviewedAt`, origin ST.8b) — next backend round, **bundle with the §9 `summary` field** as one B.11/§9 command-center-row shape-change round (one coordinated `dtos.ts` mirror). The `rebuild==seed` literal verification (pends V5/V6). §9 `summary` deferred (lead-carried). The JPA-auditing populator (still unblocked-standalone).
- **Phase-6 doc trail:** session doc 019; LESSONS through §40; CLAUDE rows current through 6.8.

## FIRST ACTIONS (fresh backend orch)
1. Register (the team-registry `jq` one-liner) → `/orchestrate-start` to orient (this handoff + brief 084 + session doc 019 + the OAuth resolver path + the Phase-10 seed spec).
2. **Await/review the impl's V5 Step-2.5** (hold the external_subject decision + the seed conventions). Then drive V5 → Step-9 (the OAuth-mapping CLAUDE/MVP note + the runbook values-fill) → seal/continue.
3. Then sequence V6 → datasource config → Dockerfile per above. Re-sync the brief lane with the web-orch before authoring 085.
