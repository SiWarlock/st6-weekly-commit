# MVP_TASKS.md — ST6 Weekly Commit Module

> **Phase note.** This is the single source of truth for build state + the phase plan for WC, a strategy-enforced weekly-alignment micro-frontend that replaces 15Five's weekly-planning slice. It decomposes the binding `ARCHITECTURE.md` (finalized by `/arch-finalize`; gap-audit in `docs/gap-audits/`) into 14 phases / ~111 tasks, ordered **invariants → lifecycle correctness → tests → local demo → polish**. Every phase cites its `ARCHITECTURE.md §` spec anchors; every task carries `Files:`, a `Cross-doc invariant:` tag, and happy/edge/error/integration test scenarios (the Step-2.5 test designs). The four locked `/arch-finalize` decisions hold: PostgreSQL major 16 (latest 16.x), Spring Boot 3.3 (EOL-documented), manager review-block per-manager/week key, flat one-level comments. Architecture is the contract — if a slice surfaces behavior the anchors don't cover, that's a cross-doc flag at TDD Step 9, not a silent change.

> **Session protocol:**
> - **At session start** — orchestrator runs `/orchestrate-start`; implementer runs `/session-start`. Confirm with the user what's targeted this session.
> - **At session end** (only when the user says we're done):
>   - **Implementer** runs `/session-end` — TDD audit + cross-doc audit + Step-9 list + create session doc + `/preflight`. Does NOT touch this doc.
>   - **Orchestrator** runs `/orchestrate-end` — verify hot routing landed, reconcile checkbox state, append Log entry, update Decisions / Carry-forward / Currently in progress, **triage Carry-forward**, round commit + push.

> **Reference deadlines:**
> - Assessment timebox: **1 week** (planning completed 2026-06-02).
> - Suggested checkpoints (soft): correctness spine (Phases 0–6) by mid-week; integration + frontend (7–9) + seed/test (10–11) by day 5; deploy + deliverables (12–13) by submission.

> **Spec-anchor convention (architecture-as-contract).** Each phase header carries a `**Spec anchors:**` block listing the `ARCHITECTURE.md` sections it implements. Orchestrator + implementer re-read the listed anchors at session start. If a slice surfaces a behavior the anchors don't cover, that's a cross-doc invariant flag at Step 9 — either the anchor is missing or the implementation has drifted. Architecture is contract; drift surfaces structurally, not silently.

---

## Currently in progress

**Backend** (st6-main-orchestrator). Landed: **0.1** (`555c2a8`) monorepo root; **0.2** (`d888f92`) Gradle build; **0.3** (`e8b5305`) `:shared` foundation (16 enums + base entities + Clock/OrgTime); **0.4+0.5** (`7c4b649`) bootable `wc-api`+`wc-sync-worker` skeletons; **1.2** (`b230ad0`) Flyway **V1 core schema**; **1.3** (`a2230c5`) Flyway **V2 partial uniques** (safety-relevant single-row invariants); **1.4** (slice 7) Flyway **V3 projection tables** (`manager_plan_summary`+`manager_heatmap_cell`, `risk_badges text[]` vocab). **Phase-1 migration trio V1/V2/V3 complete.** **1.5** (`1d43a01`) 14 JPA entities mirroring Appendix A + 14 bare repos; **1.6** (`8da6446`) 3 derived `Optional` finders + repo-layer constraint/`@Version` proofs — **Phase 1 entity/repo layer COMPLETE**; **2.1** (`922211e`) Auth0 JWT decoder (RS256-only, issuer+audience mandatory, eager OIDC discovery, secure-by-default `demo-auth.enabled` gate) — **Phase 2 BEGUN**. All backend slices 100% covered / `./gradlew check` green.

**Next backend target:** **`2.2`** (configurable Auth0 claim mapper, Appendix F.1 defaults). **⏸ TEAM PAUSED after 2.1** (user-on-demand, 2026-06-02 — round closed; resume from 2.2). **Phase 1 ✅** (1.1-by-0.3, 1.2/1.3/1.4, 1.5/1.6). **Phase 2 in progress:** 2.1 ✅; remaining 2.2→2.7, of which **2.3** (env-gated demo filter, rule #5) + **2.5** (central `DomainAuthorizationService`, rule #3 IDOR) are **SAFETY-CRITICAL standalone commits** (no bundling). `demo-auth.enabled` is the canonical mode property 2.2/2.3/2.6 inherit (LESSONS §12). `0.7`/`0.8` still parked (frontend-entangled). `DATA_MODEL.md` superseded-by-§4 (1.2).

**Frontend** (st6-main-wc-web-orchestrator). Landed: **0.6 (partial) + ST.1 + ST.2** (`e3c1cb7`) — `apps/wc-web` Cadence-themed shell (Vite 5 / React 18 / TS-strict / Vitest / ESLint 9 / Prettier), Cadence design tokens → `tailwind.config` theme + Flowbite custom theme (approach **A**, no bespoke `.wc-*` CSS), and dark-default + light-toggle `[data-theme]` theming (persisted pref; standalone-only `ThemeToggle`; `darkMode` bound to `[data-theme]`). 13 Vitest tests green; brand indigo `#5E6AD2` theme-stable. Render-only — no cross-doc invariant.

**Next frontend target:** **9.1** (RTK Query base: `baseApi` + nine tag types + `prepareHeaders` auth XOR + `getAccessToken` seam + store + RFC-7807 `problemDetails`) — **in flight** (brief `009`). Then **9.3** (MFE boundary: federation `expose` + standalone/remote split + `PersonaSwitcher`/`DemoIdentityProvider` + ThemeToggle bundle-absence proof — completes the 0.6 carve-outs) and **9.2** (view-state primitives + themed `StatusBadge`/`RiskBadge` — folds the Cadence atom skin in).

**Frontend sequencing (user, via lead, 2026-06-02).** UI design landed (the Cadence design system, committed `2a307b8`); the "no styling until design" gate is **lifted**. **Fork 2 = Option 2 (foundation-early, style-as-you-build):** ST.1/ST.2 token+theme foundation landed first; per-surface visual fidelity (ST.5/ST.6) folds into the Phase 9 component ACs; ST.3/ST.4 + ST.7 a11y/design-review are the distinct **Phase ST** spine (below). **Fork 1 = A** (Tailwind/Flowbite-native); **Fork 3 = indigo brand both themes**. Plan: `docs/planning/frontend-styling-proposal.md` (signed off).

**Sequencing direction (user, via lead, 2026-06-02).** **Frontend is deferred pending the UI design** (being designed in Claude design). The current backend implementer does **NOT** take any frontend slice — **`0.6` (wc-web shell), Phase 9, and the wc-web portions of `0.7`/`0.8`** wait for a **dedicated frontend implementer** the lead will spawn when the design lands. Frontend work, when it starts, is **structure-first**: scaffolding + typed RTK Query/logic layer are fine, but **no styling/theme tokens until the design is delivered**. Meanwhile keep the **backend spine** moving and **bundle Phase 0 backend slices where safe** (no safety invariants exist in Phase 0 — speed is prioritized for the 1-week timebox); flow into **Phase 1** to keep the backend unblocked rather than idling on the deferred frontend. Orchestrator owns the backend sequencing. Backend-only remainder of Phase 0: `0.3` (`:shared` enums/common — carries the first Appendix-A cross-doc invariant), `0.4`+`0.5` (api/worker Spring Boot app skeletons — bundle candidate), and the **backend portions** of `0.7` (Postgres + api + worker compose) / `0.8` (`./gradlew check` + JDK 21 CI). The wc-web service in `0.7` and the JS lint/vitest/vite-build steps in `0.8` are carved out as frontend-deferred.

---

## Carry-forward to upcoming briefs

Items the orchestrator MUST fold into upcoming slice briefs. **Triaged at every `/orchestrate-end`** — NOT append-only. New entries carry an origin marker `(origin: YYYY-MM-DD <slice-id>)`.

- **JPA auditing populator — wire in Phase 2 (after 2.4 PrincipalResolver).** RESOLVED-as-deferred at 1.5: the slice kept the audit columns nullable + did NOT wire `@EnableJpaAuditing` (the `@CreatedBy`/`@LastModifiedBy` populator needs an authenticated principal, which doesn't exist until Phase 2's `PrincipalResolver`, 2.4). When 2.4 lands, wire `@EnableJpaAuditing` + an `AuditorAware<String>` from the principal, and decide then whether `created_at`/`created_by` warrant `NOT NULL` (would need a V-next migration). _(origin: 2026-06-02 1.2; retargeted 1.5)_
- **(2.6) Prove the custom `JwtDecoder` wins over Boot's auto-configured `issuer-uri` decoder.** 2.1's `real_mode_builds_decoder_bean` proves the bean builds but not no-collision with `OAuth2ResourceServerAutoConfiguration`'s `@ConditionalOnMissingBean` decoder. Fold a no-collision proof (register the autoconfig in the test runner) into the **2.6** brief, where the resource server is wired into the `SecurityFilterChain`. _(origin: 2026-06-02 2.1)_
- **(§15 / 2.5 audit slice) Decide whether authn-layer JWT-validation rejections get an `audit_event` breadcrumb.** The §15 audit list is authorization-**denial**-focused; a wrong-audience/expired JWT is an authn failure (401). Decide at the §15/2.5 `AuthorizationDeniedAuditer` slice whether bad-token rejections warrant a (rate-limited, safe-metadata-only) audit breadcrumb or stay log-only. _(origin: 2026-06-02 2.1)_
- **(Phase 12 / infra) Pin `DEMO_AUTH_ENABLED` absent/false in deployed real-mode manifests.** The backend defaults to real mode (secure), but the deployed k8s/Secrets manifests must ensure `DEMO_AUTH_ENABLED` is absent or explicitly `false` in prod (no demo backdoor in real deployments — rule #5 operational half). Route to the infra area at Phase 12. _(origin: 2026-06-02 2.1)_
- **`/preflight` skill backend-step tweak (low priority).** The generic `/preflight` skill's backend steps don't fit this composite-Gradle project (must run from `apps/wc-api/`; `build -x test` invalid). Worked around via area `CLAUDE.md` Standard-commands + LESSONS §6; a proper fix is making the `/preflight` command cwd-aware for the backend (or documenting the `apps/wc-api` `./gradlew check` gate in the command). _(origin: 2026-06-02 session-001)_
- **(Frontend) Expose layout tokens into the Tailwind theme when layout work lands.** ST.1 mapped color/spacing/radii/shadow/motion tokens into `tailwind.config`, but `--reading-col`/`--content-max` aren't yet in the theme (`maxWidth`); `App.tsx` uses one arbitrary-value escape hatch `max-w-[var(--reading-col)]`. Fold into the first Phase 9 layout/shell slice (9.2/9.7). _(origin: 2026-06-02 ST.1)_

> _All other round-1 Step-9 items were routed inline during the round (real task checkboxes for 0.7/0.8/D.5-migration-Job; LESSONS §1–§6; cross-doc rows; the DATA_MODEL.md reconciliation) — Carry-forward stays drained._

---

## Deliverable map

| Deliverable | Status | Delivered by |
|---|---|---|
| Source code (api + worker + web + e2e + infra) | ❌ | Phases 0–13 |
| Technical documentation (ARCHITECTURE.md ✓ + README + run/deploy docs) | 🟡 | Phase 13 (`ARCHITECTURE.md` already final) |
| Deployed frontend + API + worker on AWS custom domains | ❌ | Phase 12 |
| Demo video (covers every EVALUATION_CRITERIA demo signal) | ❌ | Phase 13 |
| Test results (unit/integration/E2E BDD + p95 perf + deployed smoke) | ❌ | Phase 11 (+ CI in Phase 12) |
| AI usage log (`AI_USAGE.md`, Markdown — REQ-O-010) | ❌ | Phase 13 |

---

## Phase exit checklist (template — applies to every phase)

Before ticking a phase complete:

- [ ] **All phase task checkboxes ticked.** Conservative — partial work stays unchecked with a Log entry note.
- [ ] **Acceptance criterion met.** `/preflight` clean + manual smoke if there's runtime behavior to validate.
- [ ] **`/preflight` clean.** Includes any architecture-invariant tests.
- [ ] **Cross-doc invariants verified.** No model field changes without an `ARCHITECTURE.md` (§ + Appendix A) edit in the same round.
- [ ] **Session doc(s) for this phase exist** and list every file created/modified.
- [ ] **Commits pushed to origin.**

---

## Final-submission acceptance criteria (project-level)

The project is "done" when (these mirror the EVALUATION_CRITERIA disqualifying-gap list):

- [ ] A planned commitment **cannot** lock without a Supporting Outcome (BDD `ic-lock-blocked-unlinked` green — REQ-E-001).
- [ ] **No IDOR:** a manager cannot reach a non-direct-report plan/dispute/heatmap cell, and an IC cannot reach another IC's plan (IDOR integration matrix + `unauthorized-manager-denial` BDD green — REQ-S-002).
- [ ] Locked planned baseline fields **cannot** be silently mutated (immutability test green — RISK-002).
- [ ] Outlook sync failure **never** blocks lock/review/reconcile/carry-forward (non-blocking integration test + `outlook-failed-retry` BDD green — REQ-I-004).
- [ ] Manager command center shows direct-report-only roll-up **with** heatmap + review SLA/overdue + disputes + reconciliation/carry-forward risk (not a bare list — REQ-F-019..023).
- [ ] Deployed `wc.<ROOT_DOMAIN>` (frontend) and `api.wc.<ROOT_DOMAIN>` (API) reachable; deployed smoke suite green (REQ-O-008, REQ-E-006).
- [ ] All CI quality gates green: JaCoCo ≥80%/module, Spotless, SpotBugs, ESLint 9, Prettier 3.3, Vitest, Cypress/Cucumber (REQ-T-013/014/015).
- [ ] p95 < 200ms for plan retrieval + manager command-center against the 2,000-row synthetic seed, recorded in the test-results artifact (REQ-NF-001, REQ-T-012).
- [ ] All six required deliverables present (deliverable map all 🟢).

---

## Phase 0 — Monorepo & toolchain foundation

**Goal:** Stand up the empty-but-green skeleton so every later WC phase has a home and a passing local + CI pipeline, with module boundaries proven and no domain logic written. Deliver the Yarn Workspaces + Nx root, the Gradle multi-module (`shared`/`api`/`worker`) build with Java 21 toolchain and the four quality gates wired and enforced (JaCoCo ≥80%/module, Spotless, SpotBugs, plus the JS-side ESLint 9 / Prettier 3.3 / Vitest), a runnable `wc-api` Spring Boot app and `wc-sync-worker` Spring Boot app each exposing actuator health/readiness, a standalone-runnable `wc-web` Vite 5 + React 18 + TS-strict + Tailwind + Flowbite shell, a `docker-compose.yml` bringing up PostgreSQL on 5432 and the full stack with the profile-switched in-process async transport declared (`SPRING_PROFILES_ACTIVE=local|demo`), and a minimal GitHub Actions skeleton that lint/builds both stacks with no deploy. The phase enforces the locked decisions that ASM-001 (lightweight Yarn+Nx, not a PA workspace replica) and REQ-I-008 (no duplication of PA shell-owned concerns) demand: standalone-capable remote with no hardcoded shell nav/global routing/LogRocket/Loki ownership.

**Spec anchors:** `ARCHITECTURE.md §13`, §21, Appendix C (C.1–C.7), Appendix D (D.1–D.6).

### 0.1 — Monorepo root: Yarn Workspaces + Nx + Gradle root wiring ✅ (slice 1, 2026-06-02)
- [x] Root `package.json` declares Yarn Workspaces over `apps/*` (`wc-web`, `wc-e2e`) and pins Node/Yarn engines (`engines.node ">=20"` documentary + `.nvmrc` 20 + `packageManager: yarn@4.5.3` via Corepack); `nx.json` configures Nx project graph + cache (assessment-light, ASM-001) and does NOT add PA-shell ownership (no global routing/LogRocket/Loki/shell nav config), satisfying REQ-I-008.
- [x] Top-level dir skeleton created per Appendix C.1: `apps/{wc-web,wc-api,wc-e2e}/`, `infra/{terraform,k8s}/` (empty placeholders with `.gitkeep`), `docs/` already present; root holds `nx.json`, `package.json`. (Root `settings.gradle`/`build.gradle`/`gradle.properties` → task 0.2; `docker-compose.yml` → task 0.7.)
- [x] `yarn install` succeeds (Yarn Berry 4.5.3 via Corepack) and `nx show projects` lists the JS projects (`wc-web`, `wc-e2e`) with no errors; no domain code.
- [x] Files (realized): NEW `package.json` (root), NEW `nx.json` (root), NEW `.yarnrc.yml` (`nodeLinker: node-modules`, telemetry off), NEW `.nvmrc`, NEW `apps/wc-web/package.json` + NEW `apps/wc-e2e/package.json` (minimal stubs — supersede the planned `apps/.gitkeep`, approved Step-2.5 Q1; fleshed out by 0.6 / e2e task), NEW `infra/terraform/.gitkeep`, NEW `infra/k8s/.gitkeep`, NEW `scripts/verify-workspace.sh` (5 verification gates), generated `yarn.lock`; modified `.gitignore` (+`node_modules/`, `.yarn/`, `.pnp.*`, `.nx/`). Realized tree reconciled into ARCHITECTURE.md Appendix C.1.
- [x] Cross-doc invariant: none (no typed model)
- [x] Tests — happy: `yarn install` clean + `nx show projects` lists `wc-web` and `wc-e2e`; edge: re-run of install is idempotent (`yarn install --immutable`, Berry YN0028 — no lockfile drift); error: a **malformed** workspace member fails fast (NOTE: Yarn Berry silently *skips* a missing/empty workspace dir rather than failing — gate reframed to malformed-member; exact-membership gate added to catch silent drops); integration: `yarn workspaces list --json` resolves to exactly root + `wc-web` + `wc-e2e` (`wc-api` correctly skipped — Gradle-only, no `package.json`)
- [x] Requirements: ASM-001, REQ-I-008

### 0.2 — Gradle multi-module build + Java 21 toolchain + quality gates enforced ✅ (slice 2, 2026-06-02)
- [x] `apps/wc-api/settings.gradle` sets `rootProject.name='wc-api'` and `include 'shared','api','worker'`; `apps/wc-api/build.gradle` configures Java 21 toolchain (foojay + `JAVA_HOME`), Lombok with `@Getter @Setter @Builder` only (never `@Data`), and applies Spotless (google-java-format), SpotBugs (effort MAX), JaCoCo to every subproject; `gradle.properties` pins `springBootVersion=3.3.5` and version-catalog pins (Appendix C.2).
- [x] JaCoCo coverage verification gate is wired **per Gradle module** (api/worker/shared) at ≥80% line+branch and fails the build below threshold (§13/§17); Spotless `check` and SpotBugs are bound into `check` so `./gradlew check` runs all three gates — **aggregation pinned** by gate_1b (all 9 `:<module>:<gate-task>` scheduled under root `check`).
- [x] `:shared` `build.gradle` carries the JPA/DTO/enum dep baseline and is depended on by `:api` and `:worker`; `:api` and `:worker` declare `implementation project(':shared')`; module dependency direction is shared←api, shared←worker only (no api↔worker edge), proving the boundary (REQ-O-016) via a `checkModuleBoundaries` task bound into `check`.
- [x] A single throwaway `BuildSkeletonMarker` placeholder + covering test per module (no domain logic) exists so JaCoCo has classes to measure and the ≥80% gate is exercised green on the skeleton (replaced by real typed code in 0.3/0.4/0.5).
- [x] Files (realized): NEW `apps/wc-api/{settings,build}.gradle` + `gradle.properties`, NEW `apps/wc-api/{shared,api,worker}/build.gradle` (Appendix C.2/C.3); root NEW `settings.gradle` (`includeBuild('apps/wc-api')` + foojay) / `build.gradle` (aggregate check/build) / `gradle.properties`; NEW dual Gradle wrappers (root + apps/wc-api, Gradle 8.10.2 final + sha256); NEW `apps/wc-api/gradle/libs.versions.toml`, `apps/wc-api/lombok.config`, `apps/wc-api/config/spotbugs/exclude.xml`; NEW `scripts/verify-gradle.sh` (6 gates / 8 checks); modified `.gitignore` (+`.gradle/`, `build/`). Realized tree reconciled into ARCHITECTURE.md Appendix C.2/C.3; backend gate-wiring recipe banked as `apps/wc-api/LESSONS.md` §1 (+ §2 SpotBugs `Confidence` Groovy gotcha).
- [x] Cross-doc invariant: none (build config only; no model fields)
- [x] Tests — happy: `./gradlew check` green with all three gates active (root composite + standalone apps/wc-api) and modules building shared→api/worker; aggregation: all 9 gate tasks scheduled under root `check`; edge: a sub-80% module fails `jacocoTestCoverageVerification`; error: a `@Data` usage fails `forbidLombokData` + a Spotless violation fails `check`; integration: `:api` and `:worker` both resolve `:shared` classes at compile time; boundary: an injected api↔worker edge fails `checkModuleBoundaries`
- [x] Requirements: REQ-O-016, ASM-001

### 0.3 — `:shared` module skeleton: package root + enums + base entity classes (no logic) ✅ (slice 3, 2026-06-02)
- [x] Created `com.st6.wc` package root and the 16 typed enums: `enums/PlanState`, `RoleType`, `CommitmentKind`, `Priority`, `WorkType`, `Confidence`, `AlignmentStatus`, `ReviewStatus`, `DisputeStatus`, `FlagType`, `ReconciliationOutcome`, `CommentTargetType`, `SyncRelatedType`, `EventKind`, `SyncStatus`, `RiskBadge` — values copied EXACTLY from Appendix A / Appendix B.1, incl. the 3 drift traps (`ReviewStatus` no stored `OVERDUE`, `CommentTargetType{PLAN,COMMITMENT}` only, `SyncRelatedType` uses `MANAGER_REVIEW_WEEK`).
- [x] Created the `common/` base scaffolding with NO domain behavior: `common/AbstractAuditingEntity.java` (`@MappedSuperclass` created/updated by+at), `common/PersistableUuidEntity.java` (UUID PK + `@Version` base; **composition: `extends AbstractAuditingEntity`** — concrete entities `extends PersistableUuidEntity`), `config/ClockConfig.java` (injectable `java.time.Clock` `@Bean` per §3/§17, in **`:shared`**), `common/OrgTimeConfig.java` (org tz default `America/Chicago` + functional Mon–Sun week resolver). `:shared` gained Spring (BOM + spring-context) to host `ClockConfig`.
- [x] Domain entity classes are NOT created in this phase (deferred to Phase 1); only enums + common base + config. Lombok `@Getter/@Setter` only (no `@Data`). **0.3 fully implements the enum/common/config layer → task 1.1 is satisfied-by-0.3 (verify-against-Appendix-A only, not a re-write).**
- [x] Files (realized): NEW `apps/wc-api/shared/src/main/java/com/st6/wc/enums/*.java` (16 enums), NEW `.../common/{AbstractAuditingEntity,PersistableUuidEntity,OrgTimeConfig}.java`, NEW `.../config/ClockConfig.java`; modified `apps/wc-api/shared/build.gradle` (Spring BOM + spring-context + spring-boot-starter-test) and `apps/wc-api/build.gradle` (`forbidLombokData` comment-strip); removed the `:shared` `BuildSkeletonMarker`(+test); de-referenced it from the api/worker placeholders. (`ClockConfig` placed in `:shared`, not `:api` — task 1.1's `:api` Files line was the outlier, superseded.)
- [x] Cross-doc invariant: NEW (the 16-enum vocabulary mirrors Appendix A / Appendix B.1 — recorded in `apps/wc-api/CLAUDE.md` cross-doc table; B.1 reconciled to add the missing `RoleType` row + annotate `AllowedAction` as computed DTO vocab).
- [x] Tests — happy: `EnumVocabularyTest` parameterized over all 16 enums asserts exact value sets; edge: `RiskBadge` exactly 6; error: `valueOf("OVERDUE")`/`valueOf("MANAGER_REVIEW")` throw; base-entity shape (reflective + concrete `SampleEntity`); `Clock` bean injects + a fixed `Clock` overrides; OrgTime default/override/week-resolver; integration: `ClockConfig` Clock bean injects in a minimal Spring context. `:shared` coverage 100%.
- [x] Requirements: REQ-O-016

### 0.4 — `wc-api` base Spring Boot app: actuator health/readiness, profiles, no domain ✅ (slice 4 — bundled with 0.5, 2026-06-02)
- [x] `:api` produces a runnable Spring Boot 3.3 app `WcApiApplication.java` (`@SpringBootApplication`) that boots with no domain endpoints; exposes `GET /actuator/health/liveness` and `GET /actuator/health/readiness` (`management.endpoint.health.probes.enabled=true` + exposure; k8s probes per §15/§12/E24), returning UP on the skeleton.
- [x] Profile config files exist per Appendix C.2: `application.yml` (base), `application-prod.yml`, `application-local.yml`, `application-demo.yml`, `application-flyway-migrate.yml`; `spring.flyway.enabled=false` in base/api (only the migrate profile sets it true, §12/D.5); no Flyway migration SQL authored (Phase 1).
- [x] Config keys declared per Appendix D.2 — `SPRING_PROFILES_ACTIVE`, `app.org.timezone` (`ORG_TIMEZONE`, `${ORG_TIMEZONE:}` blank-passthrough → `OrgTimeBindingConfig.resolveZone` fail-safe to `America/Chicago` + WARN, never UTC) — NO auth/CORS/SNS (later-phase). App starts under `local` and `demo`.
- [x] **`WcApiApplication` component-scans `com.st6.wc.config.ClockConfig`** (from `:shared`; sibling of the api root, so within scan) so the `Clock` bean is in the production context — asserted via `@SpringBootTest` (a `Clock` autowires). _(0.3 flag 6 — CLOSED.)_
- [x] Files (realized): NEW `apps/wc-api/api/src/main/java/com/st6/wc/WcApiApplication.java`, NEW `.../config/OrgTimeBindingConfig.java` (static `resolveZone` fail-safe, reused by 3.2/D.4), NEW `apps/wc-api/api/src/main/resources/{application,application-prod,application-local,application-demo,application-flyway-migrate}.yml`; modified `api/build.gradle` (Spring Boot plugin + web/actuator starters); 4 tests (`OrgTimezoneFailsafeTest`, `WcApiApplicationTest`, `WcApiDemoBootTest`, `FlywayProfilePropertyTest`). api coverage 100% (`*Application` JaCoCo-excluded).
- [x] Cross-doc invariant: none (no model fields introduced)
- [x] Tests — happy: `@SpringBootTest`(local) `/actuator/health/readiness` 200/UP; edge: `flyway-migrate` resolves `flyway.enabled=true` vs base `false` (property-resolution via `ApplicationContextRunner`); error: undefined/blank/invalid `ORG_TIMEZONE` → `America/Chicago` + WARN (≠ UTC); integration: boots under `local` + `demo`
- [x] Requirements: REQ-O-015, REQ-O-016

### 0.5 — `wc-sync-worker` base Spring Boot app: separate deployable, actuator, no consume logic ✅ (slice 4 — bundled with 0.4, 2026-06-02)
- [x] `:worker` produces a separate runnable Spring Boot app `WcSyncWorkerApplication.java` (distinct deployable per REQ-O-014/§2) depending on `:shared`, booting with NO SQS listener wired and NO Graph adapter behavior (deferred); exposes actuator `health/liveness` + `health/readiness` (§15; `starter-web` carried solely for the probe server, D.3).
- [x] Worker profile resources exist per Appendix C.3: `application.yml`, `application-local.yml`, `application-demo.yml`, `application-prod.yml`; `spring.flyway.enabled=false` always (worker never migrates, §12/D.3); worker declares NO Auth0/SNS/CORS/timezone config (per D.3 note).
- [x] The in-process async dispatcher (`InProcessSyncDispatcher`) and SQS listener are NOT implemented here; only the deployable + health surface + profile skeleton, proving the worker is a separate image boundary.
- [x] **`WcSyncWorkerApplication` wires `com.st6.wc.config.ClockConfig`** via `worker/config/WorkerSharedConfig.java` (`@Import` — `ClockConfig` is in a sibling package outside the worker's scan root) so the worker context has the `Clock` bean for §10 time transitions — asserted via the worker `@SpringBootTest`. _(0.3 flag 6 — CLOSED; `WorkerSharedConfig` also gives the worker module a covered non-`*Application` class for JaCoCo.)_
- [x] Files (realized): NEW `apps/wc-api/worker/src/main/java/com/st6/wc/worker/WcSyncWorkerApplication.java`, NEW `.../worker/config/WorkerSharedConfig.java`, NEW `apps/wc-api/worker/src/main/resources/{application,application-local,application-demo,application-prod}.yml`; modified `worker/build.gradle` (Spring Boot plugin + web/actuator starters); 2 tests (`WcSyncWorkerApplicationTest`, `WorkerFlywayPropertyTest`). worker coverage 100%. `verify-gradle.sh` gate 6 asserts distinct `:api`/`:worker` bootJars.
- [x] Cross-doc invariant: none
- [x] Tests — happy: worker `@SpringBootTest`(local) `/actuator/health/readiness` UP + `Clock` autowires; edge: `spring.flyway.enabled=false` in every worker profile; error: worker boots with no SQS/Graph env; integration: `:worker` bootJar builds as a separate artifact from `:api` (gate 6)
- [x] Requirements: REQ-O-014, REQ-O-016, REQ-O-015

### 0.6 — `wc-web` standalone shell: Vite 5 + React 18 + TS-strict + Tailwind + Flowbite, MFE-ready, no domain 🟡 PARTIAL (shell + TS-strict + Tailwind/Flowbite + Vitest/ESLint/Prettier landed in **ST.1** `e3c1cb7`; **federation `expose` + full standalone/remote split + `PersonaSwitcher`/`DemoIdentityProvider` → 9.3**; **`baseApi`/`store`/`authAccessor` XOR → 9.1**, in flight)
- [ ] `apps/wc-web` runs standalone via `yarn dev` rendering an empty WC shell; TypeScript `strict` is on (`tsconfig.json`), Tailwind + Flowbite React are wired, and the federation plugin (`@originjs/vite-plugin-federation`) declares `expose: { './WeeklyCommitApp': './src/remote/WeeklyCommitApp.tsx' }` with React/ReactDOM/RTK/React-Redux as shared singletons (REQ-I-007, §7).
- [ ] Standalone/remote split is in place with NO domain features: `src/standalone/main.tsx` owns `BrowserRouter` + store provider + identity provider + persona-switcher chrome; `src/remote/WeeklyCommitApp.tsx` consumes a host router context (creates none); `PersonaSwitcher.tsx`/`DemoIdentityProvider.tsx` live ONLY under `src/standalone/` and are tree-shaken/compiled out of the exposed remote build — proving REQ-I-008 (no PA shell-owned concerns leak into the remote).
- [ ] `app/store.ts` + `app/baseApi.ts` + `app/authAccessor.ts` exist as wiring stubs only: `baseApi` configures RTK Query base with `prepareHeaders` honoring `VITE_AUTH_MODE` (`demo`⇒`X-Demo-Employee-Id` XOR `auth0`⇒`Authorization: Bearer`, never combined) and `VITE_API_BASE_URL`; `.env.example` documents `VITE_AUTH_MODE=demo|auth0` and `VITE_API_BASE_URL` (Appendix D.1). No API slices/queries defined yet.
- [x] `vitest` setup file exists and ESLint 9 / Prettier 3.3 configs are present and pass on the shell. _(ST.1 `e3c1cb7`)_
- [ ] Files: NEW `apps/wc-web/{vite.config.ts,tsconfig.json,.env.example,package.json,tailwind.config.*,postcss.config.*}`, NEW `apps/wc-web/src/remote/WeeklyCommitApp.tsx`, NEW `apps/wc-web/src/standalone/{main.tsx,PersonaSwitcher.tsx,DemoIdentityProvider.tsx}`, NEW `apps/wc-web/src/app/{store.ts,baseApi.ts,authAccessor.ts,tags.ts}`, NEW `apps/wc-web/src/test/setup.ts` (Appendix C.4)
- [ ] Cross-doc invariant: none (no typed domain model; `VITE_AUTH_MODE`/`VITE_API_BASE_URL` are config-contract values from Appendix D.1)
- [ ] Tests — happy (Vitest): standalone build (`vite build`) succeeds and a smoke render of the shell mounts without error; edge: `VITE_AUTH_MODE=demo` makes `prepareHeaders` set `X-Demo-Employee-Id` and NOT `Authorization` (and inverse for `auth0`) — asserted as a unit test; error: combining demo + bearer is unreachable (the branch is exclusive) — asserted; integration: the remote `expose` entry builds and `PersonaSwitcher`/`DemoIdentityProvider` are absent from the remote bundle (tree-shaken)
- [ ] Requirements: REQ-I-007, REQ-I-008, ASM-001

### 0.7 — `docker-compose.yml`: PostgreSQL 5432 + full stack, profile-switched in-process async
- [ ] `docker-compose.yml` at repo root brings up PostgreSQL exposed on host port 5432 (DB name `wc`, app role per D.2) plus `wc-api`, `wc-sync-worker`, and `wc-web` services wired for local dev (REQ-O-015), with `wc-api`/`wc-web` reachable for a future Cypress run.
- [ ] The async transport is profile-switched per §13: compose sets `SPRING_PROFILES_ACTIVE=local` (or `demo`) on the backend services so the local/demo profile is the path that will run the worker consume logic in-process (no live SNS/SQS locally); the actual in-process dispatcher is a later phase, so compose only declares the profile + service wiring (no SNS/SQS containers).
- [ ] Backend services read DB connection from compose env (matching `spring.datasource.url=jdbc:postgresql://<db>:5432/wc`, Appendix D.2), and `wc-web` is configured with `VITE_API_BASE_URL=http://localhost:8080` (D.1); no secrets baked into images.
- [ ] Files: NEW `docker-compose.yml` (repo root, per Appendix C.1)
- [ ] Cross-doc invariant: none
- [ ] Tests — happy: `docker compose config` validates and `docker compose up postgres` exposes 5432 with the `wc` database reachable; edge: backend services start with `SPRING_PROFILES_ACTIVE=local` and connect to the compose Postgres (not an external DB); error: a missing required DB env var fails the service fast rather than booting against a wrong DB; integration: `wc-api` `/actuator/health/readiness` returns UP against the compose Postgres
- [ ] Requirements: REQ-O-015

### 0.8 — GitHub Actions CI skeleton: lint + build both stacks, no deploy
- [ ] A single CI workflow runs on push/PR and executes the lint/format + build gates only (no Terraform/EKS/S3/deploy steps — those are later, per the phase scope and §13's ordering): JS side `eslint`/`prettier --check` + `vitest` + `vite build`; Java side `./gradlew check` (Spotless + SpotBugs + JaCoCo ≥80%/module) + assemble both api & worker JARs.
- [ ] CI **explicitly pins Node 20** (`actions/setup-node` node-version 20) — local `engines` is non-strict (dev boxes run Node 22), so the Node-20 contract is enforced only in CI — AND invokes `scripts/verify-workspace.sh` (the 5 monorepo-root workspace gates: install-clean, nx-lists-projects, `--immutable` idempotency, malformed-member-fails-fast, exact-membership) as part of the JS gate. _(origin: 2026-06-02 0.1)_
- [ ] CI **explicitly provisions JDK 21** (`actions/setup-java` `distribution: temurin`, `java-version: 21`) — do NOT rely on a local/keg-only JDK. The local build machine uses a keg-only Homebrew `openjdk@21` (not in `/Library/Java/JavaVirtualMachines`); the build resolves the toolchain via the `foojay-resolver-convention` plugin + `JAVA_HOME` (no machine-specific path committed) — see `docs/runbooks/jdk21-toolchain-setup.md`. _(origin: 2026-06-02 0.2 pre-flight blocker)_
- [ ] CI uses Docker for the Gradle build environment as needed but does NOT yet run Testcontainers integration tests, Cypress E2E, image push, migration Job, or `terraform plan/apply` (explicitly deferred to later phases); the workflow is green on the skeleton.
- [ ] No long-lived AWS keys and no OIDC role assumption are added in this phase (no AWS interaction yet), keeping the skeleton deploy-free while leaving the §13 OIDC/ECR/deploy stages for a later phase.
- [ ] Files: NEW `.github/workflows/ci.yml`
- [ ] Cross-doc invariant: none
- [ ] Tests — happy: the workflow runs `nx`/`yarn` JS lint+build and `./gradlew check` and both pass on the empty skeleton; edge: a Spotless or ESLint violation introduced anywhere fails CI; error: a sub-80% JaCoCo module fails the CI build; integration: CI builds `wc-web` standalone bundle + both Gradle JARs in one run with no deploy step present
- [ ] Requirements: REQ-O-016, ASM-001

### Acceptance criteria (0)
- [ ] All 0.X task checkboxes ticked.
- [ ] `yarn install` + `nx show projects` + `./gradlew check` all pass locally with the four backend gates (JaCoCo ≥80%/module, Spotless, SpotBugs) and JS gates (ESLint 9, Prettier 3.3, Vitest) enforced and green on an empty skeleton.
- [ ] `wc-api` and `wc-sync-worker` boot as separate deployables and each return UP on `/actuator/health/{liveness,readiness}`; `wc-web` runs standalone and its exposed `./WeeklyCommitApp` remote builds with `PersonaSwitcher`/`DemoIdentityProvider` tree-shaken out (REQ-I-007/REQ-I-008 boundary proven).
- [ ] `docker compose up` brings up PostgreSQL on 5432 + the full stack with `SPRING_PROFILES_ACTIVE=local|demo` declaring the in-process async path (REQ-O-015), and `wc-api` reaches the compose DB.
- [ ] The GitHub Actions skeleton runs lint+build for both stacks green with NO deploy/Terraform/E2E/image-push steps present.
- [ ] No domain logic, entities, endpoints, migrations, or auth/CORS/SNS behavior were implemented — only the typed enum/`common` skeleton (0.3) whose values mirror Appendix A exactly. Gradle module graph is shared←api, shared←worker with no api↔worker edge (REQ-O-016).

---

## Phase 1 — Physical schema, enums & JPA entities (Flyway V1–V3)

**Goal:** Lay the binding data-model foundation for WC: the full enum vocabulary, the three Flyway DDL migrations (V1 core schema with `VARCHAR`+`CHECK` status columns, FKs, `AbstractAuditingEntity` columns and `@Version`; V2 partial unique indexes that make the single-row review/dispute/review-block invariants provably consistent; V3 synchronous projection tables), and the JPA entities + Spring Data repositories that mirror Appendix A *exactly*. This is the layer every other phase builds on, so it encodes the four contract deltas verbatim — `manager_alignment_note` (NEW), `week_start_date` on the sync record (NEW), `progress_status` ABSENT, `comment.target_type` narrowed to `{PLAN,COMMITMENT}` and flat-but-nestable — and proves at the DB level (Testcontainers PostgreSQL, never H2) that the locked invariants actually fire.

**Spec anchors:** `ARCHITECTURE.md §4`, §3, §9 (projection table shapes only), Appendix A, Appendix B.1 (enum wire vocabulary), Appendix C.2 (file tree), Appendix E Part 1 (column caps), §22 (locked decisions: progress_status drop, comment narrowing, MANAGER_REVIEW_WEEK key, @Version).

### 1.1 — Enum types, common base entities & Clock/Org-time config ✅ SATISFIED-BY-0.3 (slice 3, `e8b5305`)
> **Already delivered by Phase-0 slice 0.3** (enums + `common/` base + `ClockConfig`/`OrgTimeConfig`, 100% covered, `EnumVocabularyTest` pins the value sets). This task is now **verify-against-Appendix-A only** — no re-implementation. One realized delta vs the Files line below: `ClockConfig` lives in **`:shared`** `com.st6.wc.config` (not `:api` — cross-cutting; the `:api` Files entry below was the outlier, superseded). Boxes ticked to reflect 0.3.
- [x] Create all 16 enum types as Java enums whose constant names equal the Appendix B.1 / Appendix A wire values exactly: `PlanState{DRAFT,LOCKED,RECONCILING,RECONCILED}`, `RoleType{IC,MANAGER}`, `CommitmentKind{PLANNED,UNPLANNED}`, `Priority{P0,P1,P2}`, `WorkType{STRATEGIC,MAINTENANCE,BLOCKER,UNPLANNED}`, `Confidence{HIGH,MEDIUM,LOW}`, `AlignmentStatus{ALIGNED,NEEDS_REVIEW,MISALIGNED}`, `ReviewStatus{NOT_REVIEWED,REVIEWED_WITH_DISPUTES,REVIEWED}` (NO `OVERDUE` constant — derived only), `DisputeStatus{OPEN,IC_RESPONDED,RESOLVED}`, `ReconciliationOutcome{COMPLETED,PARTIALLY_COMPLETED,BLOCKED,CANCELED,CARRIED_FORWARD}`, `FlagType{NEEDS_REVISION,MISALIGNED}`, `CommentTargetType{PLAN,COMMITMENT}` (only two — not 4), `SyncRelatedType{WEEKLY_PLAN,MANAGER_REVIEW_WEEK}`, `EventKind{IC_PLANNING,IC_RECONCILIATION,MANAGER_REVIEW_BLOCK}`, `SyncStatus{PENDING_PUBLISH,QUEUED,SYNCING,SYNCED,FAILED,RETRY_REQUESTED}`, `RiskBadge{MISALIGNED,NEEDS_REVIEW,BLOCKED,CARRY_FORWARD,UNREVIEWED,OVERDUE_REVIEW}`.
- [x] `AbstractAuditingEntity` (`@MappedSuperclass`) exposes created_by/created_at/updated_by/updated_at audit columns; `PersistableUuidEntity` provides the UUID PK + `@Version` optimistic-lock token base (realized: composition, `PersistableUuidEntity extends AbstractAuditingEntity`); Lombok `@Getter @Setter` (never `@Data`; `@SuperBuilder` deferred to concrete entities).
- [x] `ClockConfig` exposes an injectable `java.time.Clock` bean (system default) for §3/§17 derived-state computation; `OrgTimeConfig` exposes org tz (`America/Chicago` default, env-overridable) and a Monday–Sunday week resolver.
- [x] Files (realized): NEW `apps/wc-api/shared/src/main/java/com/st6/wc/enums/{16 enums}.java`; NEW `.../common/{AbstractAuditingEntity,PersistableUuidEntity,OrgTimeConfig}.java`; NEW `apps/wc-api/shared/src/main/java/com/st6/wc/config/ClockConfig.java` (**`:shared`**, not `:api`).
- [x] Cross-doc invariant: NEW (enum vocabulary — recorded in `apps/wc-api/CLAUDE.md` cross-doc table; B.1 reconciled to add `RoleType`).
- [x] Tests — `EnumVocabularyTest` (parameterized, all 16) + the 3 drift-trap negatives + base-entity shape + Clock inject/override + OrgTime resolver. (0.3, 100% `:shared` coverage.)
- [x] Requirements: REQ-D-010.

### 1.2 — Flyway V1: core schema DDL (12 tables, VARCHAR+CHECK, FKs, audit cols) ✅ (slice 5, 2026-06-02)
- [x] `V1__core_schema.sql` creates all 12 core tables with UUID PKs (REQ-D-009): `employee`, `manager_relationship`, `rally_cry`, `defining_objective`, `supporting_outcome`, `weekly_plan`, `weekly_commitment`, `manager_review`, `alignment_dispute`, `comment`, `outlook_calendar_sync_record`, `audit_event` — `AbstractAuditingEntity` columns (nullable until JPA auditing wires) on the 11 domain tables; `audit_event` carries its own `created_at` only (append-only).
- [x] All status columns are `VARCHAR`+`CHECK` over the EXACT 0.3 enum sets (REQ-D-010), pinned by the `enum↔CHECK` test (`pg_get_constraintdef` parse, all 15 status columns): `weekly_plan.state`, `weekly_commitment.{commitment_kind,priority,work_type,confidence,alignment_status,reconciliation_outcome}`, `manager_review.status` (3, no `OVERDUE`), `alignment_dispute.{status,flag_type}`, `comment.target_type` (`PLAN`/`COMMITMENT`), `sync.{related_type(incl MANAGER_REVIEW_WEEK),event_kind,status}`, `employee.role`.
- [x] The four contract deltas encoded verbatim (`ARCHITECTURE.md` §4 binding; `DATA_MODEL.md` superseded): `manager_alignment_note text null` PRESENT; `progress_status` ABSENT; `comment` flat-but-nestable (`parent_comment_id`/`path` nullable, `depth default 0`); `sync.week_start_date date null` (NEW) + `related_type` `MANAGER_REVIEW_WEEK`.
- [x] `@Version` = **`version bigint not null default 0`** (matches `PersistableUuidEntity.version : Long`, not `integer`) on the 5 mutable tables: `weekly_plan`, `weekly_commitment`, `manager_review`, `alignment_dispute`, `outlook_calendar_sync_record`.
- [x] FKs per Appendix A (incl. nullable `supporting_outcome_id`→SO, nullable self-FK `carry_forward_source_commitment_id`, nullable SYSTEM `audit.actor_employee_id`).
- [x] §4 non-unique indexes (none referencing dropped columns/stale vocab; consistent with the V2 partial uniques coming in 1.3).
- [x] Full uniques: `weekly_plan unique(employee_id, week_start_date)` (REQ-D-002); `employee.email` unique; `manager_relationship unique(mgr,report)` + CHECK manager≠report; `manager_review.weekly_plan_id` unique; sync `unique(owner,related_type,related_id,event_kind)`. (Partial uniques → 1.3.)
- [x] Files (realized): NEW `apps/wc-api/shared/src/main/resources/db/migration/V1__core_schema.sql`, NEW `:shared/src/test/.../migration/V1CoreSchemaMigrationTest.java`; modified `:shared/build.gradle` (Flyway+Testcontainers test deps, `testcontainers-bom:1.21.4` override) + `config/spotbugs/exclude.xml` (narrow migration-harness SQL exclusion).
- [x] Cross-doc invariant: NEW — core-schema row added to `apps/wc-api/CLAUDE.md`; `DATA_MODEL.md` stamped superseded-by-§4. No `ARCHITECTURE.md` field edits (schema-catches-up-to-contract).
- [x] Tests (20/20 on Testcontainers PG16, never H2): migrate-clean + all 12 tables; CHECK rejects out-of-vocab; the 4 deltas; unique/FK/CHECK violations (SQLSTATE 23505/23503/23514); SYSTEM null-actor insert; enum↔CHECK over all 15 status columns.
- [x] Requirements: REQ-D-001, REQ-D-002, REQ-D-009, REQ-D-010, REQ-D-011.

### 1.3 — Flyway V2: partial unique indexes (single-active-manager, one-open-dispute, per-manager/week review-block) ✅ (slice 6, 2026-06-02)
- [x] `V2__partial_unique_indexes.sql` — `uq_active_manager_per_report` on `manager_relationship (direct_report_employee_id) WHERE active = true` (single active manager per report, §4/§6).
- [x] `uq_one_unresolved_dispute_per_commitment` on `alignment_dispute (commitment_id) WHERE status IN ('OPEN','IC_RESPONDED')` (at most one unresolved dispute per commitment, §3/§4 — safety rule #6).
- [x] `uq_one_review_block_per_manager_week` on `outlook_calendar_sync_record (owner_employee_id, week_start_date) WHERE event_kind = 'MANAGER_REVIEW_BLOCK'` (one review-block/manager/week, §4/§10). The full unique `(owner,related_type,related_id,event_kind)` stays in V1 (V2 adds only the partials).
- [x] Files: NEW `apps/wc-api/shared/src/main/resources/db/migration/V2__partial_unique_indexes.sql`, NEW `:shared/src/test/.../migration/V2PartialUniqueIndexTest.java` (no build/config changes — 1.2's TC 1.21.4 override + package-scoped SpotBugs exclusion cover it).
- [x] Cross-doc invariant: extended (the 3 partial-unique constraint clauses — recorded in `apps/wc-api/CLAUDE.md`; §4 specifies them verbatim, no `ARCHITECTURE.md` edit).
- [x] Tests (5/5 on Testcontainers PG16): V2 migrates clean after V1 + 3 indexes present; each partial **firing** (23505) AND **non-firing/partial-scope** (inactive relationship / RESOLVED dispute / IC_PLANNING+IC_RECONCILIATION coexist); review-block test uses a **distinct `related_id`** to isolate the V2 grain from the V1 full unique; V1 full sync unique still holds.
- [x] Requirements: REQ-D-001, REQ-D-002.

### 1.4 — Flyway V3: synchronous projection tables (manager_plan_summary, manager_heatmap_cell) ✅ (slice 7, 2026-06-02)
- [x] `V3__projection_tables.sql` creates `manager_plan_summary` (one row per manager/report/week): `plan_state`, `review_status` (nullable), `review_due_at`, `is_review_overdue boolean not null default false` (§9 read-model column — not safety-rule-#6), the 7 count columns, `updated_at`, `unique(manager_employee_id, employee_id, week_start_date)` per §9/Appendix A.
- [x] `manager_heatmap_cell` (manager × report × week × DO): `commitment_count` + the 7 count columns, **`risk_badges text[] not null default '{}'`** constrained to the 6-badge vocabulary via a `<@` containment CHECK (pinned to `RiskBadge.values()`), `updated_at`, `unique(manager_employee_id, employee_id, week_start_date, defining_objective_id)`. Read models carry `updated_at` only (no audit quartet, no `@Version`).
- [x] Supporting indexes per §9/DATA_MODEL: summary `(manager,week,plan_state)`, `(manager,review_status)`; heatmap `(manager,week)`, `(manager,defining_objective_id)`; FKs to employee/weekly_plan/defining_objective.
- [x] Files (realized): NEW `apps/wc-api/shared/src/main/resources/db/migration/V3__projection_tables.sql`, NEW `:shared/src/test/.../migration/V3ProjectionTablesTest.java`; modified `V1CoreSchemaMigrationTest.java` + `V2PartialUniqueIndexTest.java` (Flyway `.target()` version-scoping — forward-fragility fix, LESSONS §5).
- [x] Cross-doc invariant: NEW (ManagerPlanSummary, ManagerHeatmapCell + `risk_badges` vocabulary — projections cross-doc row in `apps/wc-api/CLAUDE.md`; §9/Appendix A already specify them).
- [x] Tests (6/6 on Testcontainers PG16): V3 migrates clean after V1/V2; both tables + all count columns; `risk_badges` is `text[]`, round-trips a valid array / defaults `'{}'` / rejects out-of-vocab (23514); both uniques (23505); FKs (23503); `risk_badges` CHECK↔`RiskBadge.values()` pin.
- [x] Requirements: REQ-D-001, REQ-D-009, REQ-D-010.

> **Phase 1 migration trio (V1/V2/V3) COMPLETE.** Next: 1.5 JPA entities mirroring Appendix A (map to V1–V3 tables) + repos.

### 1.5 — JPA entities mirroring Appendix A (mapped to V1–V3 tables) ✅ (`1d43a01`, 2026-06-02)
- [x] Author one JPA entity per table, each extending `PersistableUuidEntity`/`AbstractAuditingEntity`, with field names matching Appendix A and `@Enumerated(EnumType.STRING)` columns mapped to the VARCHAR+CHECK columns (entity enum ↔ DB string): `Employee`, `ManagerRelationship`, `RallyCry`, `DefiningObjective`, `SupportingOutcome`, `WeeklyPlan`, `WeeklyCommitment`, `ManagerReview`, `AlignmentDispute`, `Comment`, `OutlookCalendarSyncRecord`, `AuditEvent`, `ManagerPlanSummary`, `ManagerHeatmapCell`.
- [x] `@Version` field present on `WeeklyPlan`, `WeeklyCommitment`, `ManagerReview`, `AlignmentDispute`, `OutlookCalendarSyncRecord` (optimistic locking, §4); the five entities map their version column to the V1 DDL.
- [x] `WeeklyCommitment` includes `managerAlignmentNote` (NEW, manager-owned, post-lock-mutable, nullable), `alignmentStatus` (AlignmentStatus), `reconciliationOutcome`, `carryForwardSourceCommitmentId` self-reference, and has NO `progressStatus` field. `OutlookCalendarSyncRecord` includes `weekStartDate` (NEW) and `relatedType` mapping `MANAGER_REVIEW_WEEK`. `Comment` includes `parentCommentId`/`path`/`depth` fields (flat MVP defaults: parent NULL, depth 0) and `targetType` over the 2-value enum. `AuditEvent.actorEmployeeId` nullable.
- [x] `ManagerHeatmapCell.riskBadges` maps the `text[]` column to a typed list of `RiskBadge` (JPA array/converter), default empty.
- [x] Files: NEW `apps/wc-api/shared/src/main/java/com/st6/wc/{employee/Employee.java, relationship/ManagerRelationship.java, rcdo/RallyCry.java, rcdo/DefiningObjective.java, rcdo/SupportingOutcome.java, plan/WeeklyPlan.java, commitment/WeeklyCommitment.java, review/ManagerReview.java, dispute/AlignmentDispute.java, comment/Comment.java, sync/OutlookCalendarSyncRecord.java, audit/AuditEvent.java, projection/ManagerPlanSummary.java, projection/ManagerHeatmapCell.java}`.
- [x] Cross-doc invariant: NEW (all 14 Appendix A models — every field name and enum binding must match Appendix A; `manager_alignment_note` NEW, `week_start_date` NEW, `progress_status` ABSENT, comment `target_type` {PLAN,COMMITMENT}).
- [x] Tests — happy: each entity persists and reloads via its repository against the Flyway-migrated Testcontainers schema; enum fields round-trip as their string value; edge: `WeeklyCommitment` with NULL `managerAlignmentNote`/`supportingOutcomeId` persists; `Comment` persists with `parentCommentId` NULL and `depth=0`; `ManagerHeatmapCell` persists with a multi-element `riskBadges` list; error: persisting an entity whose enum maps to an out-of-CHECK string (forced via native query) is rejected by the DB; integration: a `WeeklyCommitment` self-referencing `carryForwardSourceCommitmentId` to another persisted commitment loads its source link.
- [x] Requirements: REQ-D-001, REQ-D-006, REQ-D-009, REQ-D-010, REQ-D-011, REQ-D-014.

### 1.6 — Spring Data repositories + Testcontainers constraint-proof tests ✅ (`8da6446`, 2026-06-02)
- [x] One Spring Data JPA repository per entity under each `<domain>/repo/` package; repositories include the partial-unique-aware finder queries needed by later phases (e.g. active-manager-for-report lookup, unresolved-dispute-for-commitment lookup, review-block-for-manager/week lookup) without yet implementing business logic.
- [x] A shared Testcontainers PostgreSQL 16.x base test (no H2, per §13/§17) that runs the V1–V3 Flyway migrations against a real PG container; all repository tests extend it.
- [x] Constraint-proof integration tests assert each locked invariant fires at the DB level: unique `(employee_id, week_start_date)` on plan; single-active-manager partial unique; one-unresolved-dispute partial unique; per-manager/week review-block partial unique; CHECK rejection on out-of-vocabulary status values; optimistic-lock `@Version` increments on update (and a stale-version update raises `OptimisticLockException`).
- [x] Files: NEW `apps/wc-api/shared/src/main/java/com/st6/wc/{employee,relationship,rcdo,plan,commitment,review,dispute,comment,sync,projection,audit}/repo/<Entity>Repository.java`; NEW `apps/wc-api/api/src/test/java/com/st6/wc/` Testcontainers base + per-table constraint tests.
- [x] Cross-doc invariant: extended (repositories and constraint tests are the executable proof of the Appendix A constraint clauses; no new model surface).
- [x] Tests — happy: every repository saves+finds against the migrated schema; edge: finder query for "active manager of report" returns exactly one row when one active + one inactive relationship exist; error: each partial-unique and CHECK violation raises the expected `DataIntegrityViolationException`; stale `@Version` update raises optimistic-lock conflict; integration: V1→V2→V3 apply in order on a fresh container and a re-run of the suite is repeatable (deterministic schema).
- [x] Requirements: REQ-D-001, REQ-D-002, REQ-D-009, REQ-D-010, REQ-D-011.

### Acceptance criteria (1)
- [x] All 1.X task checkboxes ticked.
- [x] Flyway V1–V3 migrate clean and in order against a Testcontainers PostgreSQL 16.x container (no H2 anywhere).
- [x] Every enum in `com.st6.wc.enums` matches Appendix B.1 / Appendix A constant-for-constant (incl. `ReviewStatus` having NO stored `OVERDUE`, `CommentTargetType` having exactly `{PLAN,COMMITMENT}`, `SyncRelatedType` using `MANAGER_REVIEW_WEEK`).
- [x] The four contract deltas are encoded and tested: `manager_alignment_note` PRESENT, `progress_status` ABSENT, `outlook_calendar_sync_record.week_start_date` PRESENT, comments flat-but-nestable.
- [x] All three partial unique indexes (single-active-manager, one-open-dispute, per-manager/week review-block) provably fire in DB-level tests.
- [x] `@Version` optimistic locking is present and proven on the five mutable lifecycle entities.
- [x] All 14 Appendix A models exist as JPA entities with matching field names + repositories, tagged NEW in the cross-doc-invariants table.

---

## Phase 2 — Identity, authentication & central authorization

**Goal:** Build the safety-critical identity, authentication, and central authorization spine *before any domain endpoint exists* (§6 ordering, RISK-001/008). Stand up a Spring Security OAuth2 Resource Server pinned to Auth0 RS256 JWTs with mandatory issuer **and** custom audience validation; an env-gated demo identity header (`X-Demo-Employee-Id`, accepted only when `DEMO_AUTH_ENABLED=true`); a `PrincipalResolver` mapping JWT-claim OR demo-header to an `Employee`, plus a no-HTTP `SYSTEM` principal for the CronJob/worker; a single central `DomainAuthorizationService` that owns every IC-self-access / manager-active-direct-report-scope check with IDOR-safe `404`s and authorization-denial audit events; an env-driven CORS allow-list (exact origin + localhost:5173, `allowCredentials=false`, `OPTIONS` preflight bypass, never relaxed in demo); and the `GET /api/me` identity probe. This layer is relationship-driven (not role-driven), never blocks on Outlook, and is the foundation every later phase's authorization calls into.

**Spec anchors:** `ARCHITECTURE.md §6`, §16, §15 (audit/log hygiene), §5 (RFC-7807 status map / IDOR-safe 404), Appendix A (Employee, ManagerRelationship, AuditEvent), Appendix B.0/B.2 (E1)/B.3 (MeDto)/B.21, Appendix D.2 (auth/CORS/demo env), Appendix F.1 (Auth0 claim defaults + demo identity contract). Risks: RISK-001, RISK-008, RISK-016 (no secret leakage in safe failure paths).

### 2.1 — Auth0 JWT decoder: RS256-only, issuer + custom audience mandatory ✅ (`922211e`, 2026-06-02)
- [x] `JwtConfig` exposes a `JwtDecoder` built from `JwtValidators.createDefaultWithIssuer(issuerUri)` (issuer mandatory, `exp`/`nbf` enforced with bounded clock skew).
- [x] A custom `AudienceValidator` (implements `OAuth2TokenValidator<Jwt>`) checks the `aud` claim contains the configured `auth0.audience`; combined with the default+issuer validators via `DelegatingOAuth2TokenValidator` (Auth0 does not validate audience by default — §6).
- [x] Decoder rejects `alg=none` and any symmetric (HS*) algorithm; only `RS256` is accepted; tokens missing `iss` or `aud` are rejected.
- [x] Reads `spring.security.oauth2.resourceserver.jwt.issuer-uri`, optional `jwk-set-uri`, and `auth0.audience` from Spring properties (Appendix D.2); both issuer and audience are required in real mode (D.6) and absence fails fast with a clear startup error (not a silent insecure default).
- [x] Real-mode auth path is active only when not in demo mode; demo mode leaves the validated JWT path inactive (D.6).
- [x] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/config/JwtConfig.java`, NEW `apps/wc-api/api/src/main/java/com/st6/wc/config/AudienceValidator.java`; extended `apps/wc-api/api/src/main/resources/application.yml` + `application-prod.yml` (jwt/audience keys).
- [x] Cross-doc invariant: none (config/validator behavior; no Appendix-A model field change).
- [x] Tests — happy: valid RS256 JWT with correct issuer + audience passes validation. edge: token with extra audiences containing the required one still passes; expired (`exp` past) within skew vs beyond skew. error: `alg=none` rejected; HS256 (symmetric) rejected; wrong-issuer rejected; missing/wrong `aud` rejected; missing `aud` claim rejected. integration: `OAuth2TokenValidatorResult` carries a failure for the wrong-audience case (asserted by the §17 "wrong-audience JWT rejected" security test).
- [x] Requirements: REQ-S-007, REQ-S-009.

### 2.2 — Configurable Auth0 claim mapper (Appendix F.1 defaults)
- [ ] `Auth0ClaimMapperConfig` binds configurable claim names via `@ConfigurationProperties` (`auth0.claims.employee-id`, `auth0.claims.role`, `auth0.claims.email`) with the Appendix F.1 namespaced defaults (`https://wc.${ROOT_DOMAIN}/employee_id`, `/role`, standard `email`).
- [ ] Maps a validated `Jwt` to stable identity fields: employee-id claim → `Employee.external_subject` row resolution, falling back to the standard `sub` claim when the configured employee-id claim is absent; `role` claim → `Employee.role {IC,MANAGER}`; `email` used for display/reconciliation fallback.
- [ ] Required identity fields (employee id, role, relationship lookup input) are stable while claim *names* are configuration-driven (REQ-S-009); no claim name is hardcoded in resolution logic.
- [ ] Authorization remains relationship-driven — the mapped `role` only gates heatmap/team reads, never substitutes for the direct-report check (§6, F.1).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/config/Auth0ClaimMapperConfig.java`; extended `application.yml`/`application-prod.yml` (`auth0.claims.*` defaults).
- [ ] Cross-doc invariant: extended (Employee — external_subject / role mapping inputs, Appendix A; no field change, mapping contract only).
- [ ] Tests — happy: JWT with namespaced employee-id + role claims maps to the right external_subject and role. edge: employee-id claim absent → falls back to `sub`; custom claim names overridden via config resolve correctly. error: claim present but role value not in `{IC,MANAGER}` is rejected as an unmappable identity (no silent default). integration: mapper output feeds `PrincipalResolver` (2.4) to find the seeded `Employee` by external_subject.
- [ ] Requirements: REQ-S-007, REQ-S-009.

### 2.3 — Env-gated demo identity filter (`X-Demo-Employee-Id`)
- [ ] `DemoAuthFilter` (servlet filter in the security chain) reads `X-Demo-Employee-Id`; the header is honored as the principal **only** when `DEMO_AUTH_ENABLED=true` (Appendix F.1 demo identity contract: the header value *is* the principal, no JWT/claim mapping).
- [ ] When `DEMO_AUTH_ENABLED=false`, a request bearing `X-Demo-Employee-Id` is rejected with `403` and an authorization/security `audit_event` is written (§6/§15, REQ-S-008, RISK-008) — the rejection is the STRIDE "production backdoor" control.
- [ ] When `DEMO_AUTH_ENABLED=true`, the header value must be an existing `employee.id`; a non-existent/blank/malformed id is denied IDOR-safely (no existence disclosure) per §5 status map.
- [ ] Demo and bearer-token identity are never combined on one request (§7 mirror): if both are present in demo mode, the contract's single-source rule applies (demo branch wins per F.1; combination is not a valid authenticated state).
- [ ] `DEMO_AUTH_ENABLED` is read from env (Appendix D.2/D.6, default `false` for deployed real mode); enabling demo must not relax CORS (handled in 2.7).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/config/DemoAuthFilter.java`; extended `application-demo.yml`/`application-local.yml` (`DEMO_AUTH_ENABLED=true`), `application-prod.yml` (`false`).
- [ ] Cross-doc invariant: none (filter behavior; principal mapping reuses Employee from Appendix A).
- [ ] Tests — happy: with `DEMO_AUTH_ENABLED=true`, `X-Demo-Employee-Id=<seeded id>` authenticates as that employee. edge: blank header value with demo enabled → unauthenticated/denied; demo + bearer both present resolves to the single contract source. error: demo header present with `DEMO_AUTH_ENABLED=false` → `403` + audit_event written (the §17 "demo-header rejected when disabled" security test); unknown employee id with demo enabled → IDOR-safe denial, no existence leak. integration: rejection writes one `audit_event` with safe metadata only (no token/secret/PII, §15).
- [ ] Requirements: REQ-S-008, REQ-F-032.

### 2.4 — PrincipalResolver + three principals (IC / Manager / SYSTEM)
- [ ] `PrincipalResolver` produces an `AuthenticatedPrincipal` (resolved `employeeId`, `role`, and relationship-driven `isManager`) from **either** the validated JWT claim mapping (2.2) **or** the demo header (2.3) — identity resolution is separated from domain authorization (REQ-S-007, §6).
- [ ] `AuthenticatedPrincipal` carries the IC/Manager principal surface; a distinct `SystemPrincipal` represents the no-HTTP SYSTEM actor for the generation CronJob and sync worker (§6/§8/§10) — it carries no user request and is exempt from self/direct-report checks (consumed by later phases; defined here as the boundary).
- [ ] `isManager` is derived from having ≥1 active `manager_relationship` row as manager (relationship-driven, §4/§6), not solely from the JWT role claim.
- [ ] MVP exposes only IC and direct-manager human workflow principals; no HR/admin/skip-level/leadership principal is resolvable (REQ-F-031).
- [ ] A validly-authenticated identity that resolves to no `Employee` row is treated as unauthenticated/denied (written only as far as the IDOR-safe convention supports — see archGapsFlagged for the unpinned authn-time status).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/identity/PrincipalResolver.java`, NEW `.../identity/AuthenticatedPrincipal.java`, NEW `.../identity/SystemPrincipal.java`.
- [ ] Cross-doc invariant: extended (Employee + ManagerRelationship from Appendix A — `isManager` derivation uses the single-active-manager relationship grain; no field change).
- [ ] Tests — happy: JWT-mode request resolves to the correct IC principal; demo-mode request resolves to the matching employee. edge: employee with an active managed report → `isManager=true`; employee with only a role=MANAGER claim but no active relationship → `isManager` false for relationship-gated reads. error: identity that maps to no employee row is denied; no admin/skip-level role is resolvable. integration: resolved principal is the single input the `DomainAuthorizationService` (2.5) reads.
- [ ] Requirements: REQ-S-007, REQ-F-031, REQ-F-032.

### 2.5 — Central DomainAuthorizationService (IC self + manager direct-report scope, IDOR-safe 404, denial audit)
- [ ] `DomainAuthorizationService` is the **single** authorizer for every resource check before any repository read/mutation (§6, REQ-S-011); controller annotations are coarse authn/role gates only (asserted by structure: no `@PreAuthorize` carries resource-ownership logic).
- [ ] IC self-access: an IC may read/mutate only its own plans/commitments/disputes/comments; touching another IC's resource by id → IDOR-safe `404` (never reveal existence, §5 status map) + an authorization-denial `audit_event` (REQ-S-001, RISK-001).
- [ ] Manager direct-report scope: a manager may read/mutate only resources owned by an **active** direct report; single-resource reads authorize on the row's own `manager_employee_id`/owning-employee (not just list filtering), and projection-style scoping uses the active-direct-report set (§6/§9).
- [ ] Implements the §6 required-denial-case set, each producing `403`/`404` + a denial `audit_event`: IC reads/mutates another IC's plan; manager touches a non-direct-report plan/review/dispute/comment; manager opens a heatmap drill-down cell not their own; IC resolves a dispute (`IC_CANNOT_RESOLVE_DISPUTE` path support); IC accesses the team heatmap; comment on an unauthorized `target_id`; sync-retry on an unowned record; (demo-header-when-disabled is handled in 2.3 and also audited).
- [ ] `AuthorizationDeniedAuditer` writes the denial `audit_event` under the acting principal (or SYSTEM) with safe metadata only — IDs/action/entity type and a safe summary, never notes/bodies/tokens/PII (§15, RISK-016).
- [ ] Authorization is server-side and never relies on frontend route hiding (REQ-S-003); checks cover both reads and mutations.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/auth/DomainAuthorizationService.java`, NEW `.../auth/AuthorizationDeniedAuditer.java`; depends on `audit/AuditService.java` (audit_event writer) and the `employee`/`manager_relationship` repositories (assumed delivered by the schema/entity phase — see archGapsFlagged for the unpinned denial-action constant).
- [ ] Cross-doc invariant: extended (ManagerRelationship single-active-manager scope + AuditEvent actor-nullable from Appendix A; no field change).
- [ ] Tests — happy: IC authorized on own plan; manager authorized on an active direct report's plan/review/dispute/comment. edge: manager who also owns a plan is authorized on that plan as IC (relationship-driven, §4); a relationship flipped `active=false` removes scope. error: full per-denial-case matrix (each §6 case) → correct `403`/`404` + exactly one denial `audit_event` per denial, asserting no existence leak and no sensitive metadata. integration: Testcontainers PG IDOR matrix — IC-cross-IC, manager-cross-team, heatmap-drilldown-not-own, IC-resolve-dispute, IC-team-heatmap, unauthorized-comment-target, unowned-sync-retry (the §17 per-denial-case IDOR matrix).
- [ ] Requirements: REQ-S-001, REQ-S-002, REQ-S-003, REQ-S-011.

### 2.6 — SecurityConfig: filter chain, coarse gates, problem-details denial responses
- [ ] `SecurityConfig` wires the OAuth2 resource server (2.1/2.2) and the `DemoAuthFilter` (2.3) into one chain; the chain selects bearer vs demo identity per mode and never authenticates via both at once.
- [ ] Controller-level authorization is coarse only (authenticated + role gate where applicable, e.g. team/heatmap reads require a manager); all resource-ownership decisions are delegated to `DomainAuthorizationService` (2.5) — enforces the §6 "controllers carry coarse gates only" invariant.
- [ ] `actuator/health/liveness` and `/readiness` are public (probe scope, B.2 E24); all `/api/**` require an authenticated principal except the preflight `OPTIONS` bypass (2.7).
- [ ] Authentication/authorization failures render as RFC-7807 `application/problem+json` via `ProblemDetailsExceptionHandler`: `401`/`403` for authn/authz denial, `404` for IDOR-safe not-found-or-not-authorized, with `safeMessage` + `traceId` and never a secret/stack detail (§5 status map, B.21, RISK-016).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/config/SecurityConfig.java`; extended `apps/wc-api/api/src/main/java/com/st6/wc/web/ProblemDetailsExceptionHandler.java` (authn/authz mappings) — base handler assumed scaffolded; this task adds the security-denial cases.
- [ ] Cross-doc invariant: none (wiring; reuses B.21 error model already pinned).
- [ ] Tests — happy: authenticated request to a protected endpoint reaches the controller; health endpoints reachable unauthenticated. edge: manager-only endpoint with an IC principal → coarse `403` before resource logic. error: unauthenticated `/api/**` → `401` problem+json; denied resource → `403`/`404` problem+json with `safeMessage` + `traceId`, no secret leakage. integration: end-to-end with Testcontainers — a denied request produces both the correct problem+json body and the denial `audit_event` (composes 2.5).
- [ ] Requirements: REQ-S-003, REQ-S-011.

### 2.7 — CORS config + `GET /api/me`
- [ ] `CorsConfig` builds an exact-origin allow-list from `ROOT_DOMAIN` → `https://wc.${ROOT_DOMAIN}` plus dev `http://localhost:5173` (Appendix F.7 / D.2); no wildcard origin; methods `GET/POST/PATCH/DELETE/OPTIONS`; allowed headers `Authorization, Content-Type, X-Demo-Employee-Id`; `allowCredentials=false` (bearer transport).
- [ ] Preflight `OPTIONS` bypasses JWT validation (no auth required for preflight); demo mode must **not** relax the CORS allow-list (§12/§16, RISK-008 corollary).
- [ ] `GET /api/me` returns `MeDto` (Appendix B.3: `employeeId`, `email`, `displayName`, `role`, `persona`, `isManager`, `timezone`) for the resolved principal; in `auth0` mode `persona` = `email`, in demo mode = active persona key; `isManager` is the relationship-driven value from 2.4.
- [ ] `GET /api/me` proves persona switching changes identity context while backend authorization still enforces ownership/scoping (REQ-F-032) — a switched persona yields a different `MeDto` and the same authorization rules apply.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/config/CorsConfig.java`, NEW `apps/wc-api/api/src/main/java/com/st6/wc/me/MeController.java`, NEW `.../me/dto/MeDto.java`, NEW `.../me/MeService.java` (or mapper); extended `application*.yml` (`app.cors.allowed-origins` / `CORS_ALLOWED_ORIGINS`).
- [ ] Cross-doc invariant: new (MeDto is the E1 response contract, Appendix B.3 — fields bind to Employee/relationship in Appendix A).
- [ ] Tests — happy: `GET /api/me` returns correct `MeDto` for an IC and a manager (manager has `isManager=true`); preflight `OPTIONS` from `https://wc.${ROOT_DOMAIN}` succeeds without auth. edge: dev origin `http://localhost:5173` allowed; persona switch (demo mode) returns a different employeeId/role. error: disallowed origin not echoed in CORS headers; `allowCredentials=false` asserted; demo mode does not widen the allow-list; unauthenticated `GET /api/me` → `401`. integration: switched-persona `GET /api/me` followed by a cross-persona resource access is still denied by 2.5 (REQ-F-032 end-to-end with the central authorizer).
- [ ] Requirements: REQ-F-031, REQ-F-032, REQ-S-008.

### Acceptance criteria (2)
- [ ] All 2.X task checkboxes ticked.
- [ ] No domain endpoint is reachable without passing through the authn chain + central `DomainAuthorizationService`; controllers carry coarse gates only (§6 verified structurally).
- [ ] Security suite green: `alg=none`/symmetric/wrong-issuer/wrong-audience JWTs rejected; demo header rejected with `403` + audit_event when `DEMO_AUTH_ENABLED=false`; every §6 required-denial case returns the correct `403`/`404` + exactly one denial `audit_event` with safe-metadata-only (no token/secret/PII/notes — §15/RISK-016).
- [ ] The Testcontainers per-denial-case IDOR matrix (RISK-001) and the demo-backdoor rejection (RISK-008) both pass; `GET /api/me` returns a relationship-driven `MeDto` and persona switching does not bypass scoping (REQ-F-032).
- [ ] CORS allow-list is exact-origin (+localhost:5173), `allowCredentials=false`, `OPTIONS` bypasses JWT, and is not relaxed in demo mode.

---

## Phase 3 — RCDO read + plan generation + draft commitment CRUD + LOCK

**Goal:** Build the strategy-enforcement spine of WC. Stand up the read-only RCDO hierarchy read path, the idempotent SYSTEM-principal weekly-shell generation job, IC current/by-id plan reads, draft commitment CRUD with chess-layer metadata, and the load-bearing `lock` transition owned by `PlanLifecycleService`. Lock is the core demo: it must reject empty plans and unlinked planned commitments (REQ-E-001), and on success atomically freeze the planned baseline (RISK-002), create the `manager_review` row with a weekday-only `reviewDueAt`, perform synchronous projection upserts, write an audit event, and create the `IC_PLANNING` sync record (`PENDING_PUBLISH`) plus the manager's per-week `MANAGER_REVIEW_BLOCK` record — with Outlook strictly non-blocking. The frontend surfaces RCDO browse/search, the Supporting-Outcome picker, the chess-layer form, and clear lifecycle-state labels.

**Spec anchors:** `ARCHITECTURE.md §3`, §4, §5, §6, §8, §9 (lock-trigger upsert only), §10 (sync-record write only), §17, Appendix A, Appendix B (B.1, B.2 E2–E8, B.4–B.7), Appendix C (C.2–C.4), Appendix D (D.2/D.4), Appendix E (Part 1 validation; Part 2 seed coverage), Appendix F (F.3 SLA clock, F.4 action map), Diagrams (1) IC draft→lock.

### 3.1 — RCDO read service + `GET /api/rcdo` (RcdoTreeDto, read-only)
- [ ] `GET /api/rcdo` (E2) returns `RcdoTreeDto = { rallyCries: RallyCryNode[] }` — an object wrapper, NOT a bare array — with the full nested `RallyCryNode → DefiningObjectiveNode → SupportingOutcomeNode` hierarchy (fields per B.4: `id`, `title`, `description?`, `active`, child arrays; `rallyCryId`/`definingObjectiveId` parent ids on children).
- [ ] Endpoint is read-only for both IC and Manager scope; NO create/update/delete RCDO endpoint exists anywhere (REQ-D-003); the `RcdoReadService` exposes only query methods.
- [ ] Inactive RCDO nodes still serialize with `active=false` (the demo seed is all active; do not filter — the field is the contract).
- [ ] A `SupportingOutcome` lookup-by-id method backs commitment linking (used by 3.4/3.5) and rejects unknown SO ids.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/rcdo/RcdoController.java`, `rcdo/RcdoReadService.java`, `rcdo/dto/RcdoTreeDto.java`, `rcdo/dto/RallyCryNode.java`, `rcdo/dto/DefiningObjectiveNode.java`, `rcdo/dto/SupportingOutcomeNode.java`, `rcdo/mapper/RcdoMapper.java`; extended `shared/.../rcdo/repo/RallyCryRepository.java`, `DefiningObjectiveRepository.java`, `SupportingOutcomeRepository.java`.
- [ ] Cross-doc invariant: none new (reads RallyCry/DefiningObjective/SupportingOutcome — Appendix A, read-only seeded).
- [ ] Tests — happy: seeded 1/3/9 tree returns nested wrapper with correct parent ids; edge: empty-DB returns `{rallyCries:[]}`, SO-lookup-by-id for a valid id resolves breadcrumb; error: unknown SO id → not-found path; integration (Testcontainers PG): assert tree shape against V4 seed, assert no mutation endpoint is registered.
- [ ] Requirements: REQ-D-003, REQ-UX-001.

### 3.2 — Weekly-shell generation job (SYSTEM principal, idempotent, zero sync records)
- [ ] `PlanShellGenerationRunner` activates only under `--app.job=generate-plan-shells` (and is inert in the normal web image) — the SAME `wc-api` image, generation profile, no third app (§8/Appendix C).
- [ ] Resolves the target Monday–Sunday week in org tz via the `OrgTimeConfig` resolver; `week_start_date`=Monday, `week_end_date`=Sunday (REQ-F-002).
- [ ] Org-tz fail-safe: if `app.org.timezone`/`ORG_TIMEZONE` is unset/blank/unparseable as a `ZoneId`, fall back to `America/Chicago` and log WARN — never start with an undefined zone, never silently use UTC (Appendix D.4). **Reuse `com.st6.wc.config.OrgTimeBindingConfig.resolveZone` (the static fail-safe from 0.4) — do NOT duplicate the logic.** _(origin: 2026-06-02 0.4+0.5)_
- [ ] Creates one DRAFT `weekly_plan` shell per `active=true` employee; idempotent via the `unique(employee_id, week_start_date)` constraint — re-running for the same employee/week creates NO duplicate (REQ-F-001).
- [ ] Creates ZERO `outlook_calendar_sync_record` rows and ZERO commitments (REQ-I-002) — shells only.
- [ ] Runs under the SYSTEM principal (no user request, exempt from self/direct-report checks, limited to plan-shell writes) and writes an `audit_event` under a SYSTEM (null `actor_employee_id`) actor for the generation run (§3 invariant, §6).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/job/PlanShellGenerationRunner.java`; extended `shared/.../common/OrgTimeConfig.java`, `identity/SystemPrincipal.java`, `plan/repo/WeeklyPlanRepository.java`, `audit/AuditService.java`; config `apps/wc-api/api/src/main/resources/application.yml` (`app.job`, generation profile), `infra/k8s/cronjob-generation.yaml`.
- [ ] Cross-doc invariant: extended (WeeklyPlan — one plan per (employee, week_start_date); AuditEvent — SYSTEM actor).
- [ ] Tests — happy: generation creates one DRAFT shell per active employee with correct Mon–Sun bounds in org tz; edge: inactive employee gets no shell, blank/garbage `ORG_TIMEZONE` falls back to `America/Chicago` + WARN; error: rerun (idempotency) creates no duplicate and surfaces no constraint exception to the caller; integration (Testcontainers PG): assert exactly one shell/employee/week, rerun = no-dup, ZERO sync records and ZERO commitments after run, SYSTEM audit row written.
- [ ] Requirements: REQ-F-001, REQ-F-002, REQ-I-002.

### 3.3 — Plan reads: `GET /api/plans/current` + `GET /api/plans/{id}` (WeeklyPlanDto)
- [ ] `GET /api/plans/current` (E3, IC scope) returns the authenticated IC's own current-week `WeeklyPlanDto` (per B.5: ids, `weekStartDate`/`weekEndDate`, `state`, lifecycle timestamps, `plannedCount`/`unplannedCount`, nested `commitments[]`, `managerReview` null while DRAFT, `allowedActions[]`, `version`).
- [ ] `GET /api/plans/{id}` (E4) authorizes via the central `DomainAuthorizationService`: IC may read own plan; Manager may read a direct-report plan; any other actor → `404` (IDOR-safe, never reveal existence) + an authorization-denial `audit_event` (§5/§6).
- [ ] `allowedActions[]` on the DTO is computed per current actor + row state per B.1 (e.g. `LOCK` granted only when actor=owning IC, plan `DRAFT`, ≥1 planned, all planned linked) — UI affordance only, never the authorization source.
- [ ] Each commitment serializes its `supportingOutcomeBreadcrumb` (RC→DO→SO labels) when linked (B.6), `hasUnresolvedDispute` derived, and `version`.
- [ ] DTOs (never JPA entities) cross the boundary; all `*Id` are string UUID, timestamps ISO-8601 UTC.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/plan/PlanController.java`, `plan/PlanService.java`, `plan/dto/WeeklyPlanDto.java`, `plan/mapper/PlanMapper.java`, `plan/AllowedActionResolver.java`, `commitment/dto/WeeklyCommitmentDto.java`, `commitment/dto/RcdoBreadcrumbDto.java`; extended `auth/DomainAuthorizationService.java`, `plan/repo/WeeklyPlanRepository.java`.
- [ ] Cross-doc invariant: none new (reads WeeklyPlan + WeeklyCommitment per Appendix A).
- [ ] Tests — happy: IC reads own current shell; manager reads direct-report plan; edge: IC with no shell yet for current week (returns/creates-view per E3 semantics); `allowedActions` excludes `LOCK` when a planned commitment is unlinked; error: IC reads another IC's plan → `404`+audit, manager reads non-direct-report plan → `404`+audit; integration (Testcontainers PG): authorization matrix (IC-own ok, manager-direct-report ok, cross-IC `404`, non-direct-report-manager `404`) each with the audit row asserted.
- [ ] Requirements: REQ-F-003, REQ-UX-002.

### 3.4 — Draft commitment CRUD with chess metadata (DRAFT-only baseline edits)
- [ ] `POST /api/plans/{id}/commitments` (E5, IC own, plan `DRAFT`) creates a PLANNED commitment from `CreateCommitmentRequest` (`title` required 1–255, `description?`, `supportingOutcomeId?`, `priority`, `workType`, `confidence`, `alignmentStatus?` default `NEEDS_REVIEW`); server forces `commitmentKind=PLANNED` and rejects `workType=UNPLANNED` on this endpoint (B.6).
- [ ] `PATCH /api/commitments/{id}` (E6) while plan `DRAFT` (owning IC) edits baseline + chess fields (`title`,`description`,`supportingOutcomeId`,`priority`,`workType`,`confidence`,`alignmentStatus`); `supportingOutcomeId` links exactly one Supporting Outcome from the RCDO hierarchy (REQ-F-005) validated via 3.1's SO lookup.
- [ ] `DELETE /api/commitments/{id}` (E7, IC own, plan `DRAFT`) → `204 No Content`; allowed only while `DRAFT` (REQ-F-004).
- [ ] Chess-layer metadata persists `priority {P0,P1,P2}`, `workType {STRATEGIC,MAINTENANCE,BLOCKER,UNPLANNED}`, `confidence {HIGH,MEDIUM,LOW}`, `alignmentStatus {ALIGNED,NEEDS_REVIEW,MISALIGNED}` as IC self-assessment, and the `managerAlignmentNote` column exists (manager-owned, NOT writable by IC here) (REQ-F-006).
- [ ] Server-side validation per Appendix E Part 1: `title` `@NotBlank` ≥1 cp after trim + `@Size` 255 cp (code points, NFC, whitespace-collapsed); `description` ≤4000 cp, blank→NULL; unknown enum value → `400 VALIDATION_ERROR` (never 500); text stored RAW, no HTML stripping, React-escapes on render; `400/422` RFC-7807 with `fieldErrors[]`.
- [ ] All three operations authorize through `DomainAuthorizationService` (IC owns parent plan else `404`+audit); any mutation when plan is not `DRAFT` → `409 LOCKED_BASELINE_EDIT` (baseline) or the appropriate state error.
- [ ] Mutations occur inside `CommitmentService` transactions (not controller patches); each create/update/delete invokes the synchronous projection upsert path (§9) so manager counts stay consistent — flag-bound to 3.5's ProjectionService availability.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/commitment/CommitmentController.java`, `commitment/CommitmentService.java`, `commitment/dto/CreateCommitmentRequest.java`, `commitment/dto/PatchCommitmentRequest.java`, `commitment/mapper/CommitmentMapper.java`, `web/ProblemDetailsExceptionHandler.java`, `web/ErrorCodes.java`; extended `shared/.../commitment/repo/WeeklyCommitmentRepository.java`, `auth/DomainAuthorizationService.java`.
- [ ] Cross-doc invariant: extended (WeeklyCommitment — chess fields, `supporting_outcome_id` nullable-until-lock, `alignment_status` IC self-assessment, `manager_alignment_note` present-not-IC-writable).
- [ ] Tests — happy: create→patch→delete a draft planned commitment; link a Supporting Outcome; edge: blank/whitespace title → `400`, 256-cp title → `400`, unknown `priority` → `400`, description-only-whitespace → stored NULL, XSS probe `<img src=x onerror=alert(1)>` + `🚩مرحبا` stored verbatim; error: another IC mutates → `404`+audit, `workType=UNPLANNED` on E5 → rejected, CRUD on a non-DRAFT plan → `409`; integration (Testcontainers PG): CRUD + RCDO link round-trip, owner-only enforcement, code-point length boundary at 255.
- [ ] Requirements: REQ-F-004, REQ-F-005, REQ-F-006.

### 3.5 — `POST /api/plans/{id}/lock` — PlanLifecycleService transition (the demo core)
- [ ] `PlanLifecycleService` (NOT the controller) owns the `lock` transition; `POST /api/plans/{id}/lock` (E8) takes no body (optional `If-Match` version) and returns the updated `WeeklyPlanDto` (B.5).
- [ ] Precondition validation, in one `@Version`-guarded service transaction: require `DRAFT` + ≥1 PLANNED commitment + EVERY planned commitment linked to a Supporting Outcome (REQ-F-007).
- [ ] Empty plan → `409 EMPTY_PLAN_LOCK`; any unlinked planned commitment → `409 UNLINKED_PLANNED_COMMITMENT` with `fieldErrors[]` naming each unlinked commitment's `supportingOutcomeId` (B.21 example) — this blocks the core demo case (REQ-E-001).
- [ ] On valid lock, atomically: set `weekly_plan.state=LOCKED` + `lockedAt`; FREEZE the planned baseline so subsequent edits of title/description/supportingOutcome/priority/workType/confidence/commitmentKind/plan-week-ownership are rejected `409 LOCKED_BASELINE_EDIT` (RISK-002, REQ-F-008); `alignmentStatus` becomes read-only post-lock rejected with `409 ILLEGAL_STATE_TRANSITION` (`constraint=alignment_status_read_only_post_lock`, NOT the baseline-edit code — Appendix E rule 2).
- [ ] Same txn inserts `manager_review` (status `NOT_REVIEWED`, `review_due_at` via `ReviewSlaService` = 17:00 org tz next business day weekdays-only after `lockedAt`, computed from injectable `Clock` — Appendix F.3); performs synchronous `manager_plan_summary` + `manager_heatmap_cell` upsert (§9 count-derivation); inserts `audit_event` (action `PLAN_LOCKED`, actor=IC); inserts `outlook_calendar_sync_record` `event_kind=IC_PLANNING`/`related_type=WEEKLY_PLAN`/`status=PENDING_PUBLISH`; and idempotently upserts the locking IC's direct manager's `MANAGER_REVIEW_BLOCK` record (`related_type=MANAGER_REVIEW_WEEK`, `owner_employee_id`=manager, `week_start_date`, partial-unique grain) — manager resolved via `manager_relationship`.
- [ ] Outlook sync is strictly non-blocking: the SNS publish (`PENDING_PUBLISH→QUEUED`) happens after the core commit; an SNS/publish failure is logged and NEVER rolls back the lock (§3 invariant; Diagram 1) — this slice owns the sync-RECORD write + state transition only, not the worker.
- [ ] An IC who does not own the plan → `404`+audit; a concurrent double-lock → `409` via optimistic lock (`@Version`).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/plan/PlanLifecycleService.java`, `review/ReviewSlaService.java`, `sync/SyncRecordService.java`, `sync/ManagerReviewBlockResolver.java`, `projection/ProjectionService.java`, `sns/SnsLifecyclePublisher.java`, `sns/payload/SyncJobPointer.java` (shared), `review/dto/ManagerReviewDto.java`, `review/mapper/ReviewMapper.java`; extended `plan/PlanController.java`, `web/ErrorCodes.java`, `auth/DomainAuthorizationService.java`, `audit/AuditService.java`, `shared/.../config/ClockConfig.java`.
- [ ] Cross-doc invariant: extended (WeeklyPlan state `DRAFT→LOCKED` + `@Version`, planned baseline immutable; NEW ManagerReview row + derived `isOverdue`; NEW OutlookCalendarSyncRecord `IC_PLANNING` + per-manager/week `MANAGER_REVIEW_BLOCK`; ManagerPlanSummary/ManagerHeatmapCell synchronous upsert; AuditEvent `PLAN_LOCKED`).
- [ ] Tests — happy: lock a plan with ≥1 linked planned commitment → `LOCKED`, `manager_review` NOT_REVIEWED, `review_due_at` weekday-correct, `IC_PLANNING` record PENDING_PUBLISH then QUEUED, MANAGER_REVIEW_BLOCK upserted, projections + audit written; edge: lock on Friday → `reviewDueAt` rolls to Monday 17:00 (weekday-only across weekend, injectable Clock), second lock attempt on same plan → `409`; error: empty plan → `409 EMPTY_PLAN_LOCK`, one unlinked planned commitment → `409 UNLINKED_PLANNED_COMMITMENT` + `fieldErrors[]` (REQ-E-001), post-lock baseline edit → `409 LOCKED_BASELINE_EDIT`, post-lock `alignmentStatus` patch → `409 ILLEGAL_STATE_TRANSITION`, simulated SNS publish failure leaves plan `LOCKED` + sync record retained (non-blocking); integration (Testcontainers PG): full lock txn deltas (all rows created atomically), baseline-immutability matrix over every frozen field, SLA/Clock cases via injectable `java.time.Clock`, second MANAGER_REVIEW_BLOCK for same manager/week is a no-op (idempotent partial-unique).
- [ ] Requirements: REQ-F-007, REQ-F-008, REQ-E-001.

### 3.6 — Frontend: RCDO browser + Supporting-Outcome picker + chess-layer + draft CRUD wiring
- [ ] `rcdoApi.ts` (RTK Query) reads `GET /api/rcdo` into the RCDO tag; `RcdoBrowser.tsx` lets the IC browse/search Rally Cry → Defining Objective → Supporting Outcome so every planned commitment can be linked (REQ-UX-001).
- [ ] `SupportingOutcomePicker.tsx` selects exactly one Supporting Outcome per commitment and shows the RC→DO→SO breadcrumb; `ChessLayerFields.tsx` edits priority/workType/confidence/alignmentStatus.
- [ ] `CommitmentForm.tsx` + `CommitmentList.tsx` wire create/patch/delete via `commitmentsApi.ts`; mutations invalidate the plan/commitment cache tags (no optimistic updates — refetch/invalidate per §7).
- [ ] Every data view renders explicit loading / empty / error / success states (view-state contract §7); RFC-7807 `safeMessage` renders as Cypress-assertable error text; user text is React default-escaped (no `dangerouslySetInnerHTML`, §16).
- [ ] Demo branch (`X-Demo-Employee-Id`) lives only in `src/standalone/`; the exposed remote consumes the host router context and `getAccessToken()` — the persona switcher / demo identity provider are tree-shaken out of the remote build (§7); `VITE_AUTH_MODE` is the single source of truth, demo and bearer headers never combined.
- [ ] Files: NEW `apps/wc-web/src/features/rcdo/rcdoApi.ts`, `rcdo/RcdoBrowser.tsx`, `rcdo/SupportingOutcomePicker.tsx`, `commitment/commitmentsApi.ts`, `commitment/CommitmentForm.tsx`, `commitment/CommitmentList.tsx`, `commitment/ChessLayerFields.tsx`, `plan/plansApi.ts`, `plan/WeeklyPlanView.tsx`, `shared/components/{LoadingState,EmptyState,ErrorState}.tsx`, `shared/lib/problemDetails.ts`; extended `apps/wc-web/src/app/baseApi.ts`, `app/tags.ts`.
- [ ] Cross-doc invariant: none new (consumes RcdoTreeDto/WeeklyPlanDto/WeeklyCommitmentDto contracts).
- [ ] Tests — happy (Vitest): RCDO tree renders nested, SO picker selects one SO and stores breadcrumb, create/patch/delete invalidate the correct cache tags; edge: empty plan shows EmptyState, search filters RCDO; error: `UNLINKED_PLANNED_COMMITMENT` `safeMessage` renders as asserted text, XSS/Unicode probe renders escaped; integration (RTK Query cache-invalidation tests): commitment mutation refetches the plan query.
- [ ] Requirements: REQ-F-004, REQ-F-005, REQ-F-006, REQ-UX-001, REQ-UX-002.

### 3.7 — Frontend: plan lifecycle bar + lock action + lifecycle-state labeling
- [ ] `PlanLifecycleBar.tsx` clearly labels and distinguishes the plan state — DRAFT / LOCKED / RECONCILING / RECONCILED — and labels UNPLANNED and CARRIED_FORWARD commitments via `StatusBadge`/`WeekRangeLabel` (REQ-UX-002).
- [ ] `LockButton.tsx` is shown only when the plan DTO's `allowedActions[]` includes `LOCK` (driven by `F.4` action→endpoint map → `POST /api/plans/{id}/lock`); the button is hidden/disabled when any planned commitment is unlinked or the plan is empty.
- [ ] On lock success the view transitions to the LOCKED state, shows the manager review-due date, and reflects the frozen baseline (fields become read-only); on lock failure the RFC-7807 `safeMessage` (e.g. "Every planned commitment must link to a Supporting Outcome before you can lock this plan.") renders as Cypress-assertable error text (REQ-E-001).
- [ ] `reviewApi.ts`/plan mutations invalidate plan + manager-summary cache tags after lock (no optimistic update).
- [ ] Files: NEW `apps/wc-web/src/features/plan/PlanLifecycleBar.tsx`, `plan/LockButton.tsx`, `shared/components/{StatusBadge,WeekRangeLabel}.tsx`; extended `apps/wc-web/src/features/plan/WeeklyPlanView.tsx`, `plan/plansApi.ts`, `routes/AppRoutes.tsx` (lazy `/weekly-commit`).
- [ ] Cross-doc invariant: none new (consumes WeeklyPlanDto `state`/`allowedActions[]`/`managerReview`).
- [ ] Tests — happy (Vitest): lifecycle bar renders the correct label per `state`; LockButton visible only when `allowedActions` includes `LOCK`; on lock success shows LOCKED + review-due date; edge: empty plan hides LockButton, unlinked commitment hides/disables LockButton; error: lock `409 UNLINKED_PLANNED_COMMITMENT` renders the `safeMessage` text; integration (RTK Query): lock mutation invalidates the plan query and re-renders LOCKED.
- [ ] Requirements: REQ-F-007, REQ-E-001, REQ-UX-002.

### Acceptance criteria (3)
- [ ] All 3.X task checkboxes ticked.
- [ ] `GET /api/rcdo` returns the read-only `RcdoTreeDto` wrapper; no RCDO mutation surface exists anywhere (REQ-D-003).
- [ ] Generation job is idempotent per (employee_id, week_start_date), resolves Mon–Sun in org tz with `America/Chicago` fail-safe, and creates ZERO sync records and ZERO commitments (REQ-F-001, REQ-F-002, REQ-I-002).
- [ ] Draft commitment CRUD works for the owner and is denied (`404`+audit) for other users; chess metadata + one Supporting-Outcome link persist; baseline edits are DRAFT-only (REQ-F-004/005/006).
- [ ] `POST /plans/{id}/lock` blocks on an empty plan (`409 EMPTY_PLAN_LOCK`) and on any unlinked planned commitment (`409 UNLINKED_PLANNED_COMMITMENT`) — the core demo signal (REQ-E-001) — and on success freezes the baseline (RISK-002), creates the NOT_REVIEWED review with weekday-only `reviewDueAt`, synchronous projections, `PLAN_LOCKED` audit, and the `IC_PLANNING` + per-manager/week `MANAGER_REVIEW_BLOCK` sync records, with Outlook strictly non-blocking (REQ-F-007/008).
- [ ] The IC workspace makes RCDO selection clear (browse/search → one SO) and visibly distinguishes DRAFT/LOCKED/RECONCILING/RECONCILED + UNPLANNED/CARRIED_FORWARD work (REQ-UX-001, REQ-UX-002).
- [ ] All new backend code is covered by Testcontainers PG integration tests (no H2); JaCoCo ≥80%/module holds for the api/shared modules touched.

---

## Phase 4 — Reconciliation, unplanned work & carry-forward

**Goal:** Implement the IC reconciliation lifecycle segment `LOCKED → RECONCILING → RECONCILED` on a locked plan whose planned baseline is provably immutable: open reconciliation (E9), record per-commitment outcomes through the `PATCH /commitments/{id}` outcome contract (E6 under `RECONCILING`) under the single-outcome rule, add explicit `UNPLANNED` commitments (E11) whose Supporting-Outcome link is optional at create but required before close, carry unfinished work forward into the next Monday–Sunday DRAFT plan idempotently per source commitment (E12) with a self-link back to source, and close reconciliation only when every planned commitment has an outcome and every unplanned commitment has both an outcome and a Supporting Outcome (E10). Every write keeps the synchronous manager projections in lockstep, emits the SYSTEM/IC audit events, and creates the `IC_RECONCILIATION` Outlook sync record on start (§10) without ever blocking the lifecycle. The phase culminates in proving unplanned + carry-forward leave the locked baseline unchanged (REQ-E-005).

**Spec anchors:** `ARCHITECTURE.md §3` (plan lifecycle, commitment-outcome state, single-outcome rule, invariants), §5 (E6/E9/E10/E11/E12 preconditions + named error codes), §8 (`PlanLifecycleService`, `CarryForwardService`, `ProjectionService` txn boundaries), §9 (synchronous projection trigger set incl. reconciliation start/close + carry-forward), `Appendix A` (WeeklyPlan / WeeklyCommitment / ManagerPlanSummary / ManagerHeatmapCell / AuditEvent), `Appendix B` (B.1 enum vocabulary, B.2 E9–E12, B.5 WeeklyPlanDto, B.6 WeeklyCommitmentDto + E6/E11/E12 requests, B.21 ProblemDetail), `Appendix C` (C.2/C.3 file trees), `Appendix E` (Part 1 outcome_note validation; Part 2 carry-forward chain fixture).

### 4.1 — Outcome-recording contract on `PATCH /commitments/{id}` during RECONCILING (single-outcome rule + locked-baseline guard)
- [ ] Extend the existing commitment-`PATCH` service path so that when the parent plan is `RECONCILING` and actor is the owning IC, it accepts `{reconciliationOutcome, outcomeNote}` (B.6/E6) and persists them to `weekly_commitment.reconciliation_outcome` / `outcome_note`.
- [ ] Enforce the single-outcome rule (§3): `CARRIED_FORWARD` is mutually exclusive with completion outcomes — a `PATCH` setting `reconciliationOutcome=CARRIED_FORWARD` directly is rejected (carry-forward is reached only via E12, §4.4); valid direct outcomes via this path are `COMPLETED | PARTIALLY_COMPLETED | BLOCKED | CANCELED`. Unknown enum value → `400 VALIDATION_ERROR` (never 500).
- [ ] Reject any immutable planned-baseline field (`title, description, supportingOutcomeId, priority, workType, confidence, commitmentKind`) on a `PATCH` once the parent plan is past `DRAFT` with `409 LOCKED_BASELINE_EDIT` (the named code; `constraint=planned_commitment_baseline_immutable`).
- [ ] Reject outcome fields when the plan is not `RECONCILING` (e.g. `LOCKED`) with `409 ILLEGAL_STATE_TRANSITION`; deny a non-owner / non-direct-manager actor IDOR-safe (`404`).
- [ ] `outcomeNote` validated server-side: ≤4000 code points after NFC+trim, blank→NULL (Appendix E Part 1).
- [ ] Each successful outcome write performs the synchronous `ProjectionService` upsert (summary + heatmap counts: blocked/carry-forward/etc.) in the same transaction (§9), writes an audit_event (IC actor), and is `@Version`-guarded (`409` on concurrent conflict).
- [ ] Files: extended `apps/wc-api/api/src/main/java/com/st6/wc/commitment/CommitmentService.java`, `commitment/dto/PatchCommitmentRequest.java`, `commitment/mapper/CommitmentMapper.java`; extended `web/ErrorCodes.java` (reuse `LOCKED_BASELINE_EDIT`, `ILLEGAL_STATE_TRANSITION`); extended `projection/ProjectionService.java`, `audit/AuditService.java`. NEW tests `commitment/CommitmentOutcomePatchTest.java`.
- [ ] Cross-doc invariant: extended (WeeklyCommitment — `reconciliation_outcome`/`outcome_note`; single-outcome rule; planned baseline immutable after lock).
- [ ] Tests — happy: PATCH `COMPLETED`+note in `RECONCILING` persists + projection blocked/etc. counts update; edge: blank outcomeNote→NULL, 4000-cp boundary accepted, NFC/control-char normalization; error: direct `CARRIED_FORWARD` rejected, baseline-field edit post-lock → `409 LOCKED_BASELINE_EDIT`, outcome PATCH while `LOCKED` → `409 ILLEGAL_STATE_TRANSITION`, unknown enum → `400`, non-owner → `404`, stale `@Version` → `409`; integration (Testcontainers PG): outcome write + projection delta in one txn, rollback leaves projection unchanged.
- [ ] Requirements: REQ-F-027.

### 4.2 — `POST /plans/{id}/start-reconciliation` (LOCKED → RECONCILING + IC_RECONCILIATION sync record)
- [ ] Implement E9 command in `PlanLifecycleService`: requires plan `LOCKED` and actor = owning IC; transitions `state LOCKED → RECONCILING`, stamps `reconciliation_started_at`, returns the updated `WeeklyPlanDto` (no body; optional `If-Match` version).
- [ ] Reject any non-`LOCKED` source state (`DRAFT`, `RECONCILING`, `RECONCILED`, and self/backward transitions) with `409 ILLEGAL_STATE_TRANSITION` (REQ-F-024 acceptance: succeeds for locked, fails for draft/reconciled).
- [ ] Authorization via `DomainAuthorizationService` only; non-owner IC / unauthorized actor → `404` (IDOR-safe) + authorization-denial audit_event; manager cannot start reconciliation on a report's plan.
- [ ] Create one `IC_RECONCILIATION` Outlook sync record (`related_type=WEEKLY_PLAN`, `related_id=planId`, `owner_employee_id=`IC, `status=PENDING_PUBLISH`) in the core txn and publish a pointer to SNS via the single publish path; sync failure never rolls back the state transition (§10, REQ-I-004). [Note: §10/E9 anchor `IC_RECONCILIATION` to start-reconciliation; the phase-scope sentence places it under close — start is authoritative.]
- [ ] Synchronous projection refresh (plan_state→`RECONCILING`) in the same txn (§9); audit_event (IC actor) emitted; `@Version`-guarded.
- [ ] `WeeklyPlanDto.allowedActions[]` recomputed for the new state (e.g. `START_RECONCILIATION` removed; `ADD_UNPLANNED`, `CLOSE_RECONCILIATION`, per-commitment `CARRY_FORWARD` now eligible — B.1).
- [ ] Files: extended `apps/wc-api/api/src/main/java/com/st6/wc/plan/PlanLifecycleService.java`, `plan/PlanController.java`, `plan/mapper/PlanMapper.java` (allowedActions for `RECONCILING`); extended `sync/SyncRecordService.java`, `sns/SnsLifecyclePublisher.java`, `projection/ProjectionService.java`, `audit/AuditService.java`. NEW test `plan/StartReconciliationServiceTest.java`.
- [ ] Cross-doc invariant: extended (WeeklyPlan — state transition + `reconciliation_started_at`; OutlookCalendarSyncRecord — `IC_RECONCILIATION` kind).
- [ ] Tests — happy: `LOCKED`→`RECONCILING` stamps timestamp, returns dto, creates `IC_RECONCILIATION` sync record `PENDING_PUBLISH`→`QUEUED`; edge: `allowedActions[]` flips to reconciliation set; error: start on `DRAFT`/`RECONCILED`/`RECONCILING` → `409 ILLEGAL_STATE_TRANSITION`, non-owner → `404`+audit, stale version → `409`; integration: simulated SNS publish failure leaves plan `RECONCILING` (non-blocking) and sync row `FAILED`/`PENDING_PUBLISH`, projection plan_state updated in same txn.
- [ ] Requirements: REQ-F-024.

### 4.3 — `POST /plans/{id}/unplanned-commitments` (UNPLANNED create, SO optional at create)
- [ ] Implement E11 in `CommitmentService`: actor = owning IC, plan `LOCKED` or `RECONCILING`; create a `weekly_commitment` with server-forced `commitment_kind=UNPLANNED` and `work_type=UNPLANNED` (ignore/reject any client `workType≠UNPLANNED`), accepting `CreateUnplannedCommitmentRequest` (B.6: `title` req, `description`, `supportingOutcomeId` optional, `priority`, `confidence`, `alignmentStatus`).
- [ ] `supportingOutcomeId` is optional at creation (REQ-F-026) — no link required to create the unplanned commitment; link is enforced only at close (§4.5).
- [ ] Reject create when plan is `DRAFT`/`RECONCILED` with `409 ILLEGAL_STATE_TRANSITION`; non-owner → `404`.
- [ ] Validate title (req, ≤255 cp, NFC/trim/collapse, XSS/RTL probes stored raw) and description (≤4000 cp) per Appendix E Part 1; unknown enum → `400`.
- [ ] Returns the created `WeeklyCommitmentDto` with `commitmentKind=UNPLANNED`, `workType=UNPLANNED`; nested into `WeeklyPlanDto.commitments[]` and `unplannedCount` incremented on reads.
- [ ] Adding unplanned does NOT touch any planned-commitment row (REQ-F-025 — baseline unmutated); synchronous projection `unplanned_count` upsert in the same txn (§9); audit_event (IC actor); `@Version`.
- [ ] Files: extended `apps/wc-api/api/src/main/java/com/st6/wc/commitment/CommitmentService.java`, `commitment/CommitmentController.java`, NEW `commitment/dto/CreateUnplannedCommitmentRequest.java`, extended `commitment/mapper/CommitmentMapper.java`, `projection/ProjectionService.java`. NEW test `commitment/UnplannedCommitmentServiceTest.java`.
- [ ] Cross-doc invariant: extended (WeeklyCommitment — `commitment_kind=UNPLANNED`, `work_type=UNPLANNED`; unplanned labeled, link required pre-close).
- [ ] Tests — happy: create unplanned without SO in `RECONCILING` succeeds, `workType=UNPLANNED` forced, `unplanned_count` projection +1; also succeeds when plan `LOCKED`; edge: client `workType=STRATEGIC` coerced/rejected to `UNPLANNED`, optional SO null accepted, XSS/`🚩مرحبا` probe stored verbatim; error: create on `DRAFT`/`RECONCILED` → `409`, blank title → `400`, non-owner → `404`; integration: planned baseline rows byte-identical before/after unplanned create (REQ-F-025), projection delta in same txn.
- [ ] Requirements: REQ-F-025, REQ-F-026.

### 4.4 — `POST /commitments/{id}/carry-forward` (idempotent per source; next-week DRAFT shell create-if-absent; self-link)
- [ ] Implement E12 in `CarryForwardService` (`apps/wc-api/api/.../commitment/CarryForwardService.java`): actor = owning IC, parent plan `RECONCILING`; no request body.
- [ ] On the source commitment, set `reconciliation_outcome=CARRIED_FORWARD` (single-outcome rule — mutually exclusive with completion outcomes; if a completion outcome already set, this overwrites to CARRIED_FORWARD as the explicit carry action) and create a successor `weekly_commitment` in the next Monday–Sunday plan with `carry_forward_source_commitment_id = source.id` (REQ-D-006 self-link).
- [ ] Resolve the next Monday–Sunday week in org tz via the `OrgTimeConfig` resolver; create the next-week DRAFT plan shell only if absent for `(employee_id, next_week_start_date)`, reusing the existing shell on unique-constraint conflict (`weekly_plan` unique `(employee_id, week_start_date)`) (REQ-F-028).
- [ ] Idempotent per source: re-invoking carry-forward for the same source commitment returns the existing linked next-week commitment (keyed on `carry_forward_source_commitment_id`), creating no duplicate and no second shell.
- [ ] Returned successor is an unlinked DRAFT-equivalent commitment (no lock immutability; `supportingOutcomeId` may be null; `commitmentKind=PLANNED` per the carry-forward chain fixture in Appendix E Part 2) — response is the next-week `WeeklyCommitmentDto`.
- [ ] Reject when parent plan not `RECONCILING` → `409 ILLEGAL_STATE_TRANSITION`; non-owner → `404`; `@Version`-guarded on source.
- [ ] Synchronous projection upsert for BOTH affected weeks (source week `carry_forward_count`++; next-week shell summary/heatmap rows created/updated) in the same txn (§9); audit_event (IC actor, action carried-forward) emitted (§15 emitted-signals list); SNS/Outlook untouched by carry-forward (no new sync record — §10 has no carry-forward trigger).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/commitment/CarryForwardService.java`, extended `commitment/CommitmentController.java`, NEW `commitment/dto/CarryForwardRequest.java` (empty marker), extended `commitment/mapper/CommitmentMapper.java`, reuse `common/OrgTimeConfig.java` week resolver, extended `plan/repo/WeeklyPlanRepository.java` (find/create-by-(employee,week)), `projection/ProjectionService.java`, `audit/AuditService.java`. NEW tests `commitment/CarryForwardServiceTest.java`.
- [ ] Cross-doc invariant: extended (WeeklyCommitment — `reconciliation_outcome=CARRIED_FORWARD`, `carry_forward_source_commitment_id` self-link; WeeklyPlan — next-week DRAFT shell create-if-absent; ManagerPlanSummary/ManagerHeatmapCell — `carry_forward_count`).
- [ ] Tests — happy: carry-forward sets source `CARRIED_FORWARD`, creates next-week DRAFT shell + linked successor with `carryForwardSourceCommitmentId`, source-week `carry_forward_count`++; edge: next-week shell already exists → reused (no dup plan); idempotency: second carry-forward call returns the same successor commitment id, zero duplicates, single shell; week-boundary: source plan week Mon→Sun resolves correct next Monday in org tz (weekend/year-boundary cases); error: carry-forward on `LOCKED`/`RECONCILED`/`DRAFT` parent → `409`, non-owner → `404`, stale source version → `409`; integration (Testcontainers PG): two-week chain matching Appendix E R5 fixture — prior-week source unchanged (baseline), successor linked, projections for both weeks correct in one txn.
- [ ] Requirements: REQ-F-027, REQ-F-028, REQ-D-006.

### 4.5 — `POST /plans/{id}/close-reconciliation` (RECONCILING → RECONCILED with completeness validation)
- [ ] Implement E10 in `PlanLifecycleService`: actor = owning IC, plan `RECONCILING`, no body; transitions `state RECONCILING → RECONCILED` and stamps `reconciled_at`, returns updated `WeeklyPlanDto`.
- [ ] Completeness precondition (§5): every PLANNED commitment has a non-null `reconciliation_outcome` AND every UNPLANNED commitment has both a non-null `reconciliation_outcome` AND a non-null `supporting_outcome_id` — else `422 UNPLANNED_MISSING_LINK_AT_CLOSE` (the named code) with `fieldErrors[]` naming the offending commitment(s) (REQ-F-026, REQ-F-029).
- [ ] Distinguish failure surfaces: a planned commitment missing an outcome and an unplanned missing outcome/link both block close; the unplanned-missing-link case carries `code=UNPLANNED_MISSING_LINK_AT_CLOSE`.
- [ ] Reject close when plan not `RECONCILING` (e.g. `LOCKED`, already `RECONCILED`) → `409 ILLEGAL_STATE_TRANSITION`; non-owner → `404` + audit.
- [ ] Note: linking an unplanned commitment's Supporting Outcome before close is done via `PATCH /commitments/{id}` with `supportingOutcomeId` while `RECONCILING` (E6 row) — verify that path accepts SO link for unplanned commitments and rejects SO edits on planned (locked-baseline) commitments.
- [ ] Synchronous projection refresh (plan_state→`RECONCILED`, review-overdue/risk recompute) in same txn (§9); audit_event (IC actor, plan reconciled) emitted (§15); `@Version`-guarded; close does NOT require manager review (REQ-F-024 non-blocking, USER_FLOWS §"Reconciliation does not require manager review").
- [ ] `allowedActions[]` recomputed (reconciliation actions removed at `RECONCILED`).
- [ ] Files: extended `apps/wc-api/api/src/main/java/com/st6/wc/plan/PlanLifecycleService.java`, `plan/PlanController.java`, `plan/mapper/PlanMapper.java`; extended `web/ErrorCodes.java` (`UNPLANNED_MISSING_LINK_AT_CLOSE`), `web/ProblemDetailsExceptionHandler.java`; extended `commitment/CommitmentService.java` (unplanned SO-link-via-PATCH path), `projection/ProjectionService.java`, `audit/AuditService.java`. NEW test `plan/CloseReconciliationServiceTest.java`.
- [ ] Cross-doc invariant: extended (WeeklyPlan — state→`RECONCILED`, `reconciled_at`; WeeklyCommitment — unplanned SO required pre-close).
- [ ] Tests — happy: all planned have outcomes + all unplanned have outcome+SO → `RECONCILED`, `reconciled_at` stamped, projection plan_state updated; edge: linking unplanned SO via PATCH in `RECONCILING` then close succeeds; error: unplanned without SO → `422 UNPLANNED_MISSING_LINK_AT_CLOSE`+fieldErrors, planned without outcome → `422`, close on `LOCKED` → `409 ILLEGAL_STATE_TRANSITION`, close on `RECONCILED` → `409`, non-owner → `404`; integration: close with manager review still `NOT_REVIEWED` succeeds (non-blocking), projection deltas in one txn, rollback on validation failure leaves state `RECONCILING`.
- [ ] Requirements: REQ-F-026, REQ-F-029.

### 4.6 — Baseline-immutability proof under unplanned + carry-forward (REQ-E-005 acceptance)
- [ ] Add an integration assertion (Testcontainers PG, deterministic) that proves: after a full reconciliation pass (start → add unplanned → record planned/unplanned outcomes → carry-forward a planned commitment → close), every PLANNED commitment's baseline fields (`title, description, supporting_outcome_id, priority, work_type, confidence, commitment_kind`) on the locked plan are byte-identical to their values captured immediately after lock (no rewrite by unplanned or carry-forward).
- [ ] Prove carry-forward writes ONLY the source commitment's `reconciliation_outcome`/`carry_forward_source_commitment_id` on the original plan (no baseline field touched) and creates the successor in a distinct next-week plan, leaving the prior-week locked baseline unchanged (mirrors Appendix E R5 two-week chain).
- [ ] Author the Cypress + Cucumber acceptance feature `ic-reconcile-carry-forward.feature` proving the reconciliation view renders the locked planned baseline, the labeled unplanned work, and the linked carry-forward commitment — driven against the seeded R5 Grace fixture (REQ-E-005).
- [ ] Files: NEW `apps/wc-api/api/src/test/java/com/st6/wc/reconciliation/BaselineImmutabilityIntegrationTest.java`; NEW `apps/wc-e2e/cypress/features/ic-reconcile-carry-forward.feature`, extended `apps/wc-e2e/cypress/support/step_definitions/reconcile.steps.ts`.
- [ ] Cross-doc invariant: none (assertion over existing WeeklyPlan/WeeklyCommitment invariants).
- [ ] Tests — happy: full reconcile pass leaves planned baseline unchanged + successor linked (REQ-E-005); edge: re-running carry-forward mid-pass still leaves baseline untouched; error: attempt to mutate a planned baseline field during the pass → `409 LOCKED_BASELINE_EDIT`; integration/E2E: Gherkin scenario asserts baseline + unplanned + carry-forward all visible in reconciliation view against seeded fixture.
- [ ] Requirements: REQ-E-005, REQ-D-006, REQ-F-028.

### Acceptance criteria (4)
- [ ] All 4.X task checkboxes ticked.
- [ ] Plan lifecycle advances `LOCKED → RECONCILING → RECONCILED` only via E9/E10 with the §5 preconditions; every illegal/backward/self transition rejected with `409 ILLEGAL_STATE_TRANSITION`.
- [ ] Outcome recording obeys the single-outcome rule; locked planned-baseline edits rejected with `409 LOCKED_BASELINE_EDIT`; close blocked with `422 UNPLANNED_MISSING_LINK_AT_CLOSE` until every planned has an outcome and every unplanned has outcome + Supporting Outcome.
- [ ] Carry-forward is idempotent per source commitment (re-invoke returns the same successor), creates the next-week DRAFT shell only if absent (reuse on unique conflict), and self-links via `carry_forward_source_commitment_id` (REQ-D-006).
- [ ] All reconciliation writes (start, outcome PATCH, unplanned create, carry-forward, close) update `manager_plan_summary` + `manager_heatmap_cell` synchronously in the same transaction (§9) and emit IC-actor audit events; Outlook `IC_RECONCILIATION` sync on start never blocks the lifecycle.
- [ ] REQ-E-005 demonstrated: unplanned + carry-forward leave the locked planned baseline unchanged, proven by integration test and the `ic-reconcile-carry-forward.feature` Cypress/Cucumber scenario against the seeded R5 fixture.

---

## Phase 5 — Manager review, SLA/overdue & alignment disputes

**Goal:** Implement the manager-side, parallel-and-non-blocking accountability layer on a `LOCKED`+ weekly plan: a `ReviewSlaService` that computes `reviewDueAt` as 17:00 org-tz on the next business day (weekday-only) from an injectable `java.time.Clock` and derives `isOverdue` at read time (never stored); the `mark-reviewed` command that derives `REVIEWED` vs `REVIEWED_WITH_DISPUTES` server-side from unresolved-dispute count; the full alignment-dispute loop (`OPEN → IC_RESPONDED → RESOLVED`) with manager-open / IC-respond / manager-only-resolve authorization and the at-most-one-unresolved-dispute invariant; the manager-owned post-lock-mutable `managerAlignmentNote`; and the visibility gates that let a manager *read* direct-report drafts but only *act* (review/dispute) after lock. Every dispute and review mutation is audit-logged with safe metadata only, and resolving the last dispute re-derives review status. All work is invariants-first (Clock/SLA math → derivation → command surface), deterministic, and Testcontainers/unit-testable.

**Spec anchors:** `ARCHITECTURE.md §3`, §5, §6, §8, §9 (count derivation only — projection wiring owned by the projection phase), §15, Appendix A (ManagerReview, AlignmentDispute, WeeklyCommitment, AuditEvent), Appendix B (E16–E19, B.7, B.8, B.21), Appendix C (C.2/C.3 review+dispute packages), Appendix E (Part 1 validation rows: `alignment_dispute.manager_note`, `ic_response`, `manager_alignment_note`, `manager_review.summary_note`), Appendix F (F.3 SLA clock, F.4 action→endpoint map).

### 5.1 — ReviewSlaService: weekday-only `reviewDueAt` + derived `isOverdue` (injectable Clock)
- [ ] `ReviewSlaService.computeReviewDueAt(Instant lockedAt)` returns the `Instant` for **17:00 (5:00 PM) in org tz** on the **next business day** (Mon–Fri only; holidays deferred) after `lockedAt`, resolved via `OrgTimeConfig` zone (`America/Chicago` default + fail-safe per Appendix D.6) and the injectable `java.time.Clock` from `ClockConfig`.
- [ ] Weekend roll-over correct: lock on Friday → due Monday 17:00 org-tz; lock on Saturday/Sunday → due Monday 17:00; lock Mon–Thu → next calendar weekday 17:00.
- [ ] `ReviewSlaService.isOverdue(ManagerReview review)` returns `true` iff `now() (from injected Clock) > review_due_at AND status == NOT_REVIEWED`; `false` for `REVIEWED_WITH_DISPUTES` and `REVIEWED` regardless of time; result is **computed at read time, never persisted** (no `is_review_overdue` column on `manager_review`).
- [ ] `reviewDueAt` is computed once and persisted at lock; the computation function is pure/injectable so tests pin a fixed `Clock`.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/review/ReviewSlaService.java`; depends on existing `common/config/ClockConfig.java` + `common/OrgTimeConfig.java` (shared module). Unit test NEW `apps/wc-api/api/src/test/java/com/st6/wc/review/ReviewSlaServiceTest.java`.
- [ ] Cross-doc invariant: extended (ManagerReview — `review_due_at` persisted, `isOverdue` derived `now>due ∧ NOT_REVIEWED` per Appendix A; no stored `OVERDUE`).
- [ ] Tests — happy: lock Tue 10:00 → due Wed 17:00 org-tz; `NOT_REVIEWED` + now>due ⇒ overdue=true. edge: lock Fri/Sat/Sun all roll to Mon 17:00; now exactly == due ⇒ not overdue (strict `>`); DST boundary in `America/Chicago` keeps 17:00 wall-clock. error: blank/invalid `ORG_TIMEZONE` falls back to `America/Chicago` + WARN (no UTC, no crash). integration: none (pure unit with fixed `Clock`).
- [ ] Requirements: REQ-F-012, REQ-O-003

### 5.2 — ReviewStatusDeriver + `mark-reviewed` command (E16)
- [ ] NEW `ReviewStatusDeriver.derive(planId)` returns `REVIEWED_WITH_DISPUTES` when the plan's unresolved-dispute count (disputes on the plan's commitments with status ∈ `OPEN,IC_RESPONDED`) > 0, else `REVIEWED`; status is **never accepted from the client** (B.7).
- [ ] `POST /api/manager/reviews/{reviewId}/mark-reviewed` (E16): precondition plan `LOCKED`+; actor must be the direct manager of the plan owner (else IDOR-safe `404`); accepts optional validated `summaryNote` (≤4000 cp, trim/NFC, blank→NULL per Appendix E); sets `reviewed_at` (Clock) and derives status via `ReviewStatusDeriver`; returns updated `ManagerReviewDto` (B.7) with `isOverdue` (5.1), `unresolvedDisputeCount`, `allowedActions[]`.
- [ ] `REVIEWED_WITH_DISPUTES` satisfies the SLA: after mark-reviewed-with-disputes, `isOverdue=false` while `unresolvedDisputeCount>0` still surfaces (REQ-F-013).
- [ ] Each `mark-reviewed` writes an `audit_event` (action e.g. `REVIEW_MARKED`, actor=manager, entity_type=ManagerReview, safe metadata: ids/status only — no `summary_note` body, §15/REQ-S-006).
- [ ] Optimistic-concurrency guarded (`@Version` on `manager_review`); conflicting concurrent transition → `409` `ILLEGAL_STATE_TRANSITION`.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/review/ReviewStatusDeriver.java`, `review/ReviewController.java`, `review/ReviewService.java`, `review/dto/MarkReviewedRequest.java`, `review/dto/ManagerReviewResponse.java` (`ManagerReviewDto`), `review/mapper/ReviewMapper.java`. Extends existing `audit/AuditService.java`, `auth/DomainAuthorizationService.java`, `web/ErrorCodes.java`.
- [ ] Cross-doc invariant: extended (ManagerReview status `{NOT_REVIEWED,REVIEWED_WITH_DISPUTES,REVIEWED}`; AuditEvent).
- [ ] Tests — happy: mark-reviewed with 0 unresolved disputes ⇒ `REVIEWED`, `reviewed_at` set, audit row written. edge: mark-reviewed with ≥1 unresolved ⇒ `REVIEWED_WITH_DISPUTES`, `isOverdue=false`; re-mark idempotent re-derivation. error: client cannot force status; plan still `DRAFT` ⇒ `409`; non-direct-report manager ⇒ `404` + denial audit; oversize `summaryNote` ⇒ `400` VALIDATION_ERROR. integration (Testcontainers PG): mark-reviewed then resolve last dispute re-derives to `REVIEWED`; audit row asserts no note body in `metadata_json`.
- [ ] Requirements: REQ-F-010, REQ-F-011, REQ-F-013, REQ-S-006

### 5.3 — Open alignment dispute (E17) + single-unresolved invariant
- [ ] `POST /api/commitments/{id}/disputes` (E17): preconditions plan `LOCKED`+, actor = direct manager of the commitment's plan owner; required validated `managerNote` (NOT NULL, ≤4000 cp, ≥1 cp after trim/NFC → else `400/422`); `flagType ∈ {NEEDS_REVISION, MISALIGNED}` validated against Appendix-A set (unknown ⇒ `400`).
- [ ] Single-unresolved-dispute invariant: if an `OPEN`/`IC_RESPONDED` dispute already exists on the commitment ⇒ `409` `SECOND_OPEN_DISPUTE`; enforced by the partial unique index `(commitment_id) WHERE status IN ('OPEN','IC_RESPONDED')` AND a service-level pre-check so the DB constraint is the backstop, not the only guard.
- [ ] Creates `alignment_dispute` (status=`OPEN`, manager_employee_id=actor); returns `AlignmentDisputeDto` (B.8); commitment's derived `hasUnresolvedDispute` becomes true.
- [ ] Opening a dispute on a plan whose review is `REVIEWED` re-derives `manager_review.status → REVIEWED_WITH_DISPUTES` (via 5.2 deriver) in the same transaction.
- [ ] Writes an `audit_event` (action e.g. `DISPUTE_OPENED`, actor=manager, entity=AlignmentDispute; safe metadata only — **no `manager_note` body**, §15/REQ-S-006).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/dispute/DisputeController.java`, `dispute/DisputeService.java`, `dispute/dto/OpenDisputeRequest.java`, `dispute/dto/AlignmentDisputeResponse.java` (`AlignmentDisputeDto`), `dispute/mapper/DisputeMapper.java`, `dispute/repo/AlignmentDisputeRepository.java` (partial-unique-aware unresolved-count query). Extends `auth/DomainAuthorizationService.java`, `audit/AuditService.java`, `web/ErrorCodes.java` (`SECOND_OPEN_DISPUTE`).
- [ ] Cross-doc invariant: NEW (AlignmentDispute — status `{OPEN,IC_RESPONDED,RESOLVED}`, flag_type `{NEEDS_REVISION,MISALIGNED}`, manager_note NOT NULL, partial-unique WHERE status IN(OPEN,IC_RESPONDED)).
- [ ] Tests — happy: manager flags `NEEDS_REVISION` with note ⇒ `OPEN` dispute, audit row, `hasUnresolvedDispute=true`. edge: open dispute on a `REVIEWED` plan flips review to `REVIEWED_WITH_DISPUTES`; many historical RESOLVED disputes + one new open is allowed. error: missing/blank `managerNote` ⇒ `400/422`; second unresolved on same commitment ⇒ `409 SECOND_OPEN_DISPUTE`; plan `DRAFT` ⇒ `409` (REQ-F-010); manager on non-direct-report commitment ⇒ `404` + denial audit; unknown `flagType` ⇒ `400`. integration (Testcontainers PG): concurrent double-open hits the partial unique index → exactly one `OPEN` row, the other `409`; audit `metadata_json` carries no note body.
- [ ] Requirements: REQ-F-010, REQ-F-015, REQ-F-018, REQ-S-006

### 5.4 — IC respond to dispute (E18) — does not resolve
- [ ] `POST /api/disputes/{id}/respond` (E18): preconditions dispute status `OPEN`, actor = owning IC (the dispute's commitment's plan owner; manager/other ⇒ `404`/`403` per §6); body requires **at least one of** `icResponse` (≤4000 cp validated, non-blank if present) / `newSupportingOutcomeId` (uuid) → else `400/422`.
- [ ] When `newSupportingOutcomeId` is provided, the IC's revision updates the commitment's `supporting_outcome_id` link (the sanctioned IC-revision path so a manager never silently rewrites the IC's SO link); `icResponse` is written to `alignment_dispute.ic_response` (NOT to comments).
- [ ] Transition `OPEN → IC_RESPONDED`; response **does NOT resolve** the dispute; commitment still `hasUnresolvedDispute=true`; review status stays `REVIEWED_WITH_DISPUTES` if previously derived.
- [ ] Writes an `audit_event` (action e.g. `DISPUTE_IC_RESPONDED`, actor=IC; safe metadata only — **no `ic_response` body**, §15/REQ-S-006).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/dispute/dto/RespondDisputeRequest.java`. Extends `dispute/DisputeController.java`, `dispute/DisputeService.java`, `dispute/mapper/DisputeMapper.java`, `commitment/CommitmentService.java` (SO-link revision via dispute path) or `dispute/DisputeService.java` directly, `audit/AuditService.java`.
- [ ] Cross-doc invariant: extended (AlignmentDispute `OPEN→IC_RESPONDED`; WeeklyCommitment.supporting_outcome_id revised via IC respond only).
- [ ] Tests — happy: IC responds with rationale only ⇒ `IC_RESPONDED`, `ic_response` stored, not resolved; IC responds with new SO id ⇒ commitment SO link updated + `IC_RESPONDED`. edge: response with both fields accepted; carries `IC_RESPONDED` count into the still-unresolved set (review stays WITH_DISPUTES). error: neither field provided ⇒ `400/422`; respond when status `IC_RESPONDED`/`RESOLVED` ⇒ `409`; non-owning IC / manager attempting respond ⇒ `404`/`403`; oversize `icResponse` ⇒ `400`. integration (Testcontainers PG): respond keeps the partial-unique unresolved slot occupied (a new open still ⇒ `409`); audit row has no response body.
- [ ] Requirements: REQ-F-016, REQ-S-006

### 5.5 — Resolve dispute (E19), manager-only + review re-evaluation
- [ ] `POST /api/disputes/{id}/resolve` (E19): preconditions dispute status `OPEN` or `IC_RESPONDED`, actor = direct manager; an IC attempt ⇒ `403` `IC_CANNOT_RESOLVE_DISPUTE` (distinct named code, not a generic 404 — the IC owns the resource but is forbidden the action per §6/B.8); optional `resolutionNote` ignored-or-stored per body shape (no required body).
- [ ] Transition to `RESOLVED`, set `resolved_at` (Clock); after resolution re-run `ReviewStatusDeriver` (5.2): 0 unresolved disputes on the plan ⇒ `REVIEWED_WITH_DISPUTES → REVIEWED`, else stays `REVIEWED_WITH_DISPUTES`; commitment `hasUnresolvedDispute` recomputed.
- [ ] Resolving frees the partial-unique slot so a subsequent new dispute on the same commitment is permitted (historical disputes accumulate; only the unresolved one is constrained).
- [ ] Writes an `audit_event` (action e.g. `DISPUTE_RESOLVED`, actor=manager; safe metadata only, §15/REQ-S-006).
- [ ] Optimistic-concurrency guarded; conflicting resolve/respond → `409`.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/dispute/dto/ResolveDisputeRequest.java`. Extends `dispute/DisputeController.java`, `dispute/DisputeService.java`, `review/ReviewStatusDeriver.java` (re-eval call), `auth/DomainAuthorizationService.java`, `audit/AuditService.java`, `web/ErrorCodes.java` (`IC_CANNOT_RESOLVE_DISPUTE`).
- [ ] Cross-doc invariant: extended (AlignmentDispute `OPEN|IC_RESPONDED → RESOLVED`; ManagerReview re-derived `REVIEWED_WITH_DISPUTES→REVIEWED`).
- [ ] Tests — happy: manager resolves the last unresolved dispute ⇒ `RESOLVED` + review re-derived to `REVIEWED`. edge: resolve one of two unresolved ⇒ review stays `REVIEWED_WITH_DISPUTES`; resolve from `IC_RESPONDED` and from `OPEN` both valid. error: IC resolves ⇒ `403 IC_CANNOT_RESOLVE_DISPUTE` + denial audit; manager on non-direct-report dispute ⇒ `404`; resolve an already-`RESOLVED` dispute ⇒ `409`. integration (Testcontainers PG): full loop open→respond→resolve drives review `NOT_REVIEWED`→(mark)`REVIEWED_WITH_DISPUTES`→`REVIEWED`; after resolve a new dispute on the same commitment is allowed (no `409`); 4 audit rows (open/respond/resolve/mark) all body-free.
- [ ] Requirements: REQ-F-017, REQ-F-011, REQ-F-013, REQ-S-006

### 5.6 — Manager draft-read visibility + post-lock action gate (authorization)
- [ ] `GET /api/plans/{id}` (E4) authorizes a manager to **read** a direct report's plan in any state including `DRAFT` (manager sees draft commitment details — REQ-F-009), scoped to active direct reports only via `DomainAuthorizationService` (non-direct-report ⇒ IDOR-safe `404`).
- [ ] All formal manager **actions** (`mark-reviewed` E16, open-dispute E17, resolve-dispute E19, `managerAlignmentNote` patch) reject when the plan is `DRAFT` with `409` (precondition `LOCKED`+) — read is allowed pre-lock, mutation is not (REQ-F-010); centralize the `LOCKED`+ gate in the authorization/service layer, not the controller.
- [ ] `allowedActions[]` on `WeeklyPlanDto`/`WeeklyCommitmentDto`/`ManagerReviewDto` reflect actor+state per Appendix B.1 table (`MARK_REVIEWED`/`OPEN_DISPUTE`/`RESOLVE_DISPUTE` present only for the direct manager on `LOCKED`+; absent on `DRAFT`) — affordance only, never the authorization source.
- [ ] Every manager denial (read of non-direct-report plan; action on `DRAFT`; IC resolving a dispute; manager touching a non-direct-report dispute/review) emits an authorization-denial `audit_event` via `AuthorizationDeniedAuditer` (§6/§15).
- [ ] Files: extends `apps/wc-api/api/src/main/java/com/st6/wc/auth/DomainAuthorizationService.java`, `auth/AuthorizationDeniedAuditer.java`, `plan/PlanController.java`/`plan/dto` (manager-state-aware `allowedActions[]`), `review/mapper/ReviewMapper.java`, `dispute/mapper/DisputeMapper.java`.
- [ ] Cross-doc invariant: none (relationship-driven authorization rules; no new model fields).
- [ ] Tests — happy: direct manager `GET /plans/{id}` on a `DRAFT` report plan ⇒ `200` with commitment details, `allowedActions[]` has no `MARK_REVIEWED`/`OPEN_DISPUTE`. edge: same plan after lock ⇒ `allowedActions[]` includes `MARK_REVIEWED`/`OPEN_DISPUTE` for the manager. error: non-direct-report manager read ⇒ `404` + denial audit; manager open-dispute/mark-reviewed on `DRAFT` ⇒ `409` + (denial where applicable) audit. integration (Testcontainers PG): per-denial IDOR matrix slice for review/dispute resources (each case → `403`/`404` + exactly one audit row).
- [ ] Requirements: REQ-F-009, REQ-F-010

### 5.7 — `managerAlignmentNote`: manager-owned, post-lock-mutable, audit-logged (E6)
- [ ] `PATCH /api/commitments/{id}` (E6) accepts `managerAlignmentNote` only when plan is `LOCKED`+ AND actor is the direct manager (else IDOR-safe `404`/`403`); validated ≤4000 cp, trim/NFC, blank→NULL (Appendix E row); the IC and other principals cannot set it.
- [ ] `managerAlignmentNote` is the single manager-owned commitment field that is mutable after lock — it is **not** part of the planned-baseline-immutable set, so a manager patch of it does NOT trigger `LOCKED_BASELINE_EDIT`; an IC patch of any baseline field still → `409 LOCKED_BASELINE_EDIT` (owned by the lock/baseline phase, asserted here for the manager-note carve-out).
- [ ] Each `managerAlignmentNote` mutation writes an `audit_event` (action e.g. `MANAGER_ALIGNMENT_NOTE_UPDATED`, actor=manager; safe metadata only — **note body never in `metadata_json` or CloudWatch logs**, §15/REQ-S-006).
- [ ] Stored raw, rendered React-escaped (no `dangerouslySetInnerHTML`); XSS/Unicode probe stored verbatim and asserted escaped downstream (Appendix E §16 contract).
- [ ] Files: extends `apps/wc-api/api/src/main/java/com/st6/wc/commitment/CommitmentService.java` (field-level authz for `managerAlignmentNote`), `commitment/dto/PatchCommitmentRequest.java` (already has the field), `commitment/mapper/CommitmentMapper.java`, `auth/DomainAuthorizationService.java`, `audit/AuditService.java`.
- [ ] Cross-doc invariant: extended (WeeklyCommitment.manager_alignment_note — manager-owned, post-lock-mutable, audit-logged; NOT in baseline-immutable set).
- [ ] Tests — happy: direct manager sets `managerAlignmentNote` on a `LOCKED` commitment ⇒ `200`, stored, audit row written. edge: clearing to blank ⇒ stored `NULL`; setting on `RECONCILING` plan also allowed. error: IC attempts to set it ⇒ `403`/`404` denial + audit; manager on non-direct-report commitment ⇒ `404`; oversize note ⇒ `400`; manager patch of a baseline field (title/SO/priority) still ⇒ `409 LOCKED_BASELINE_EDIT`. integration (Testcontainers PG): patch note → audit `metadata_json` contains no note text; manager-note change does not flip any baseline-immutable guard.
- [ ] Requirements: REQ-F-010, REQ-S-006

### Acceptance criteria (5)
- [ ] All 5.X task checkboxes ticked.
- [ ] `reviewDueAt` is 17:00 org-tz next business day (weekday-only) computed once at lock from an injectable `Clock`; `isOverdue` is derived at read time and never stored (REQ-F-012, REQ-O-003).
- [ ] `mark-reviewed` derives `REVIEWED` vs `REVIEWED_WITH_DISPUTES` server-side from unresolved-dispute count; `REVIEWED_WITH_DISPUTES` yields `isOverdue=false` while `unresolvedDisputeCount>0` still surfaces (REQ-F-011, REQ-F-013).
- [ ] Full dispute loop is enforceable end-to-end: manager-only open (required note, ≤1 unresolved ⇒ `SECOND_OPEN_DISPUTE`), IC respond (at-least-one-of, does not resolve), manager-only resolve (`IC_CANNOT_RESOLVE_DISPUTE` for IC), with review re-evaluation `REVIEWED_WITH_DISPUTES↔REVIEWED` (REQ-F-015/016/017/018).
- [ ] Managers can read direct-report drafts but every formal review/dispute/note mutation is gated to `LOCKED`+ direct-report scope (REQ-F-009, REQ-F-010).
- [ ] Every dispute and review/note action emits an `audit_event` with safe metadata only — no `manager_note`, `ic_response`, `summary_note`, or `manager_alignment_note` bodies in `metadata_json` or logs (REQ-S-006).
- [ ] Demo/eval signal: the BDD `manager-dispute-loop.feature` (flag → IC respond → IC-cannot-resolve → manager resolve, REQ-F-017) is fully backed by these endpoints, and the SLA/overdue unit suite passes with a fixed `Clock` across a weekend boundary.

---

## Phase 6 — Manager projections, command center & heatmap

**Goal:** Build the synchronous manager read-model spine (`manager_plan_summary` + `manager_heatmap_cell`) that updates inside every affected service transaction, plus the manager-facing read surfaces (command center, heatmap, drill-down) that consume it. Projection rows must update transactionally with commitment CRUD, plan lock, reconciliation start/close, dispute open/respond/resolve, mark-reviewed, and carry-forward — with pinned count derivation (`misaligned_count` = `alignment_status=MISALIGNED` OR open dispute `flag_type=MISALIGNED`; `is_review_overdue=false` while `REVIEWED_WITH_DISPUTES`) and enumerated `risk_badges`. The read endpoints are direct-report-scoped (no IDOR: drill-down own-cell `404`, IC denied team heatmap), N+1-free over the indexed projection columns, paginated where the contract requires, and backed by an internal rebuild job/CLI (no UI). This phase closes RISK-003 (projection drift) and RISK-014 (badge correctness).

**Spec anchors:** `ARCHITECTURE.md §9`, §5 (E13/E14/E15, B.11/B.12, B.20, F.5), §14, §6 (direct-report scoping + IDOR `404`), §15 (authorization-denial audit), Appendix A (ManagerPlanSummary, ManagerHeatmapCell), Appendix B (E13–E15), Appendix C.2 (`projection/`, `manager/`), Appendix E (fixture state matrix + heatmap/command-center coverage), Appendix F.5 (pagination/default sort). Diagrams supplement (1) IC lock projections, (2) manager/dispute projection deltas, (4) reconcile/carry-forward projection refresh.

### 6.1 — Projection entities + tables + indexes (invariants-first)
- [ ] JPA entity `ManagerPlanSummary` mirrors Appendix A / DATA_MODEL exactly: `id`, `managerEmployeeId`, `employeeId`, `weeklyPlanId`, `weekStartDate`, `planState`, `reviewStatus` (nullable), `reviewDueAt` (nullable), `isReviewOverdue` (not-null default false), `plannedCount`, `unplannedCount`, `misalignedCount`, `needsReviewCount`, `blockedCount`, `carryForwardCount`, `unresolvedDisputeCount`, `updatedAt`.
- [ ] JPA entity `ManagerHeatmapCell` mirrors Appendix A / DATA_MODEL exactly: adds `definingObjectiveId`, `commitmentCount`, and `riskBadges` as `text[]` (not-null default `{}`); keeps the 7 shared counts.
- [ ] Flyway `V3__projection_tables.sql` creates both tables with `VARCHAR` status columns (no native enums, §4 convention), the unique constraints `manager_plan_summary(manager_employee_id, employee_id, week_start_date)` and `manager_heatmap_cell(manager_employee_id, employee_id, week_start_date, defining_objective_id)`, and the read indexes `manager_plan_summary(manager_employee_id, week_start_date, plan_state)`, `(manager_employee_id, review_status)`, `manager_heatmap_cell(manager_employee_id, week_start_date)`, `(manager_employee_id, defining_objective_id)`.
- [ ] `riskBadges` values constrained to the enumerated `RiskBadge` vocabulary `{MISALIGNED, NEEDS_REVIEW, BLOCKED, CARRY_FORWARD, UNREVIEWED, OVERDUE_REVIEW}` (enum in `enums/RiskBadge.java`).
- [ ] Files: NEW `apps/wc-api/shared/src/main/java/com/st6/wc/projection/ManagerPlanSummary.java`, NEW `apps/wc-api/shared/src/main/java/com/st6/wc/projection/ManagerHeatmapCell.java`, NEW `apps/wc-api/shared/src/main/java/com/st6/wc/enums/RiskBadge.java`, NEW `apps/wc-api/shared/src/main/java/com/st6/wc/projection/repo/ManagerPlanSummaryRepository.java`, NEW `apps/wc-api/shared/src/main/java/com/st6/wc/projection/repo/ManagerHeatmapCellRepository.java`, extended `apps/wc-api/shared/src/main/resources/db/migration/V3__projection_tables.sql`.
- [ ] Cross-doc invariant: NEW (ManagerPlanSummary, ManagerHeatmapCell — Appendix A; field names must match §9 + DATA_MODEL columns verbatim).
- [ ] Tests — happy: persist + read back a summary row and a heatmap cell with all columns; edge: empty `risk_badges` array round-trips as `{}` and a multi-badge array preserves order/membership; error: violating the summary unique `(manager,employee,week)` or the cell unique `(…,DO)` raises a DB constraint; integration (Testcontainers PG): both unique indexes + the four read indexes exist (`pg_indexes` assertion) and `RiskBadge` enum values match the migration CHECK/array domain.
- [ ] Requirements: REQ-D-012

### 6.2 — ProjectionService: pinned count + status derivation from source
- [ ] `ProjectionService` exposes a single `recomputeForPlan(weeklyPlanId)`-style entrypoint that derives, from source tables for one plan, the `manager_plan_summary` counts and the per-Defining-Objective `manager_heatmap_cell` counts, and upserts both (insert-or-update keyed on the unique constraints from 6.1).
- [ ] `misalignedCount` derivation pinned (§9): a commitment counts as misaligned iff `alignment_status=MISALIGNED` OR it has an OPEN/IC_RESPONDED dispute with `flag_type=MISALIGNED` (deduplicated per commitment so a commitment that is both does not double-count).
- [ ] `reviewStatus`, `reviewDueAt` copied from the plan's `manager_review`; `isReviewOverdue` derived = `now > reviewDueAt AND review_status = NOT_REVIEWED` using the injectable `java.time.Clock` (§3/§17) — and pinned `false` while `review_status = REVIEWED_WITH_DISPUTES` even when past due (REQ-F-013).
- [ ] `unresolvedDisputeCount` = disputes in `OPEN`/`IC_RESPONDED`; `blockedCount` = commitments with `reconciliation_outcome=BLOCKED`; `carryForwardCount` = commitments with `reconciliation_outcome=CARRIED_FORWARD`; `needsReviewCount` = commitments with `alignment_status=NEEDS_REVIEW`; `plannedCount`/`unplannedCount` by `commitment_kind`.
- [ ] Heatmap cell grain = manager × report × week × `defining_objective_id` (resolved via each commitment's `supporting_outcome_id → SO → DO`); commitments with no SO link contribute to the summary but not to any DO cell.
- [ ] `manager_employee_id` resolved from the plan owner's active `manager_relationship` (single active manager, §6/§4); a plan whose owner has no manager (e.g. Dana self-plan) produces NO summary/heatmap row.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/projection/ProjectionService.java`.
- [ ] Cross-doc invariant: extended (ManagerPlanSummary, ManagerHeatmapCell — count/derivation semantics pinned in §9).
- [ ] Tests — happy: a plan with mixed commitments yields exact summary counts + one heatmap cell per touched DO; edge: a commitment that is both `alignment_status=MISALIGNED` and has a MISALIGNED dispute counts once in `misalignedCount`; `REVIEWED_WITH_DISPUTES` past `reviewDueAt` ⇒ `isReviewOverdue=false`; `NOT_REVIEWED` past due ⇒ `true` (injectable Clock); plan owner without active manager ⇒ no rows; error: recompute for a non-existent plan id is a safe no-op (no orphan rows); integration (Testcontainers PG): seeded Appendix E fixtures (R1–R6) recompute to the literal seeded summary/cell counts.
- [ ] Requirements: REQ-D-012, REQ-F-019, REQ-F-021

### 6.3 — Synchronous projection wiring into every affected service transaction (RISK-003)
- [ ] `ProjectionService` is invoked inside the SAME service-method transaction (never a controller patch, §8) for every affected write: commitment create/update/delete, plan lock, start-reconciliation, close-reconciliation, dispute open/respond/resolve, mark-reviewed, carry-forward.
- [ ] At lock, projection rows are created/updated in the lock transaction alongside the `manager_review` + audit + `IC_PLANNING` sync record (diagram §1) — never as a follow-up call.
- [ ] Dispute open increments `unresolved_dispute_count` and (when the commitment/dispute is MISALIGNED) recomputes `misaligned_count`, in the same txn (diagram §2 steps); dispute resolve decrements `unresolved_dispute_count` and re-derives; mark-reviewed updates `review_status`/`is_review_overdue`.
- [ ] Reconciliation outcome PATCH refreshes `blocked_count`/`carry_forward_count`; carry-forward (which creates a next-week DRAFT commitment) refreshes the source week's `carry_forward_count` (diagram §4).
- [ ] Transactional atomicity: if the projection upsert fails, the whole domain mutation rolls back (single txn); no partial drift.
- [ ] Files: extended `apps/wc-api/api/src/main/java/com/st6/wc/commitment/CommitmentService.java`, extended `apps/wc-api/api/src/main/java/com/st6/wc/plan/PlanLifecycleService.java`, extended `apps/wc-api/api/src/main/java/com/st6/wc/commitment/CarryForwardService.java`, extended `apps/wc-api/api/src/main/java/com/st6/wc/dispute/*Service.java`, extended `apps/wc-api/api/src/main/java/com/st6/wc/review/*Service.java`.
- [ ] Cross-doc invariant: extended (ManagerPlanSummary, ManagerHeatmapCell — §9 trigger set).
- [ ] Tests — happy: each of the 9 trigger writes leaves the projection row matching a fresh recompute (delta == recompute); edge: opening then resolving a dispute returns `unresolved_dispute_count` to its prior value; carry-forward bumps the source-week `carry_forward_count` by 1; error/rollback (Testcontainers PG): inject a projection-upsert failure during lock and assert the plan stays `DRAFT` with no projection row written (transactional rollback); integration: full lock→dispute→resolve→mark-reviewed sequence yields the §3-correct review status and `is_review_overdue=false` after `REVIEWED_WITH_DISPUTES`.
- [ ] Requirements: REQ-D-012, REQ-F-013, REQ-F-019

### 6.4 — Risk-badge derivation (RISK-014)
- [ ] `manager_heatmap_cell.riskBadges` populated only from the enumerated `RiskBadge` vocabulary, derived deterministically from the same source state as the cell counts (badges must never conflict with the underlying commitment/review/dispute/reconciliation state — RISK-014 / USER_FLOWS §547).
- [ ] Pinned mappings the contract supports: `MISALIGNED` when `misaligned_count>0`; `NEEDS_REVIEW` when `needs_review_count>0`; `BLOCKED` when `blocked_count>0`; `CARRY_FORWARD` when `carry_forward_count>0`; `UNREVIEWED` when the cell's plan review is `NOT_REVIEWED`; `OVERDUE_REVIEW` when the derived `is_review_overdue=true` (co-emits with `UNREVIEWED` per the R2 Marco fixture).
- [ ] Badge set is recomputed in the same path as 6.2 so incremental and rebuild produce identical arrays.
- [ ] Files: extended `apps/wc-api/api/src/main/java/com/st6/wc/projection/ProjectionService.java`, NEW `apps/wc-api/api/src/main/java/com/st6/wc/projection/RiskBadgeDeriver.java`.
- [ ] Cross-doc invariant: extended (ManagerHeatmapCell.risk_badges — Appendix A enumerated vocabulary).
- [ ] Tests — happy: R3 Aisha cell → contains `MISALIGNED` + `NEEDS_REVIEW`; R2 Marco cell → contains `OVERDUE_REVIEW` + `UNREVIEWED`; R5 Grace cell → contains `BLOCKED` + `CARRY_FORWARD`; edge: a fully `REVIEWED`, aligned, completed cell → empty `{}` badge array; `REVIEWED_WITH_DISPUTES` cell does NOT carry `OVERDUE_REVIEW`; error: no badge value outside the enum ever emitted; integration (Testcontainers PG): seeded fixtures reproduce exactly the Appendix E badge coverage set; badges match a fresh recompute (no drift).
- [ ] Requirements: REQ-F-021, REQ-E-003

### 6.5 — Manager command center + heatmap + drill-down read endpoints (E13/E14/E15)
- [ ] `GET /api/manager/command-center` (E13): `weekStart` required; Spring Data `Pageable` (page default 0, size default 25, max 100); default sort `weekStartDate DESC` then `employeeDisplayName ASC` (F.5); returns the B.20 paginated envelope of `ManagerCommandCenterRowDto` (mirrors `manager_plan_summary`, all B.11 fields incl. `isReviewOverdue`, `misalignedCount`, `unresolvedDisputeCount`, `carryForwardCount`).
- [ ] E13 filters (REQ-F-023) served from indexed columns: `employeeId`, `planState`, `reviewState` (incl. derived `OVERDUE` filtering on `isReviewOverdue`), `definingObjectiveId` (via heatmap grain), `supportingOutcomeId`, `priority`, `workType`, `alignmentStatus` — all combinable; no client-side filtering.
- [ ] `GET /api/manager/heatmap` (E14): `weekStart` + optional `definingObjectiveId`/`supportingOutcomeId`; returns `HeatmapResponseDto { weekStart, cells: HeatmapCellDto[] }`, NOT paginated (bounded by reports × DOs, F.5); cells scoped to the manager's ACTIVE direct reports only.
- [ ] `GET /api/manager/heatmap/{cellId}/drilldown` (E15): own-cell only — drill-down on a cell whose `manager_employee_id != caller` returns `404` (IDOR-safe, never reveals existence) + an authorization-denial `audit_event` (§6/§15); returns `HeatmapDrilldownDto` with `supportingOutcomes[] DrilldownOutcomeGroup`, each group's `commitments` as the B.20 paginated envelope of `WeeklyCommitmentDto` (default sort `priority ASC` then `createdAt ASC`, F.5).
- [ ] All three reads route through `DomainAuthorizationService` (sole resource authorizer, §6): scope by the authenticated manager's active `manager_relationship` rows; controllers carry coarse authn/role gates only.
- [ ] Heatmap/command-center reads are N+1-free: single indexed query per endpoint over the projection tables (no per-row lookups, no live aggregation — §14/REQ-NF-003).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/manager/ManagerController.java`, NEW `apps/wc-api/api/src/main/java/com/st6/wc/manager/ManagerQueryService.java`, NEW `apps/wc-api/api/src/main/java/com/st6/wc/manager/dto/ManagerCommandCenterRowDto.java`, NEW `apps/wc-api/api/src/main/java/com/st6/wc/manager/dto/HeatmapResponseDto.java`, NEW `apps/wc-api/api/src/main/java/com/st6/wc/manager/dto/HeatmapCellDto.java`, NEW `apps/wc-api/api/src/main/java/com/st6/wc/manager/dto/HeatmapDrilldownDto.java`, NEW `apps/wc-api/api/src/main/java/com/st6/wc/manager/dto/DrilldownOutcomeGroup.java`, NEW `apps/wc-api/api/src/main/java/com/st6/wc/manager/mapper/ManagerProjectionMapper.java`, extended `apps/wc-api/api/src/main/java/com/st6/wc/auth/DomainAuthorizationService.java`.
- [ ] Cross-doc invariant: none new (consumes ManagerPlanSummary/ManagerHeatmapCell from 6.1; DTOs mirror Appendix B.11/B.12).
- [ ] Tests — happy: manager sees exactly their N active reports' rows for the week; heatmap returns cells per report × DO; drill-down on own cell returns paginated commitments sorted P0→P2; edge: each E13 filter narrows results correctly, `reviewState=OVERDUE` returns only `isReviewOverdue=true` rows, combined filters AND together, empty result returns an empty envelope (`totalElements=0`); pagination page/size honored, size>100 clamped; error: drill-down on a non-own cell ⇒ `404` + audit row; command-center/heatmap for a non-manager principal ⇒ denial; missing `weekStart` ⇒ `400/422`; integration (Testcontainers PG): seeded fixtures return Appendix E coverage; default sort verified; N+1-free (query-count assertion on a single projection query per endpoint).
- [ ] Requirements: REQ-F-019, REQ-F-020, REQ-F-022, REQ-F-023, REQ-UX-003, REQ-NF-002, REQ-NF-003, REQ-E-002, REQ-E-003

### 6.6 — IC team-heatmap denial (REQ-F-030 / REQ-UX-005) + manager-scope IDOR matrix
- [ ] An IC (principal with no active direct report) calling `GET /api/manager/command-center`, `GET /api/manager/heatmap`, or `GET /api/manager/heatmap/{cellId}/drilldown` is denied server-side via `DomainAuthorizationService` (not just hidden in the UI) with the §6 status and an authorization-denial `audit_event`.
- [ ] A manager attempting another manager's command-center/heatmap rows or a drill-down cell that is not their own is denied `404` (IDOR-safe) + audit (the Appendix E negative-auth fixture: any cross-report manager access in the demo is denied because no report manages another).
- [ ] Denial decisions emit the structured `authorization denial` observability signal (§15) and durably write `audit_event` (safe metadata only — IDs/state, no notes/PII bodies, §15).
- [ ] Files: extended `apps/wc-api/api/src/main/java/com/st6/wc/auth/DomainAuthorizationService.java`, extended `apps/wc-api/api/src/main/java/com/st6/wc/auth/AuthorizationDeniedAuditer.java`.
- [ ] Cross-doc invariant: none (consumes ManagerRelationship single-active-manager scope, AuditEvent SYSTEM/actor model).
- [ ] Tests — happy: a true manager passes all three reads for their own scope; edge: an IC who is also a plan owner (relationship-driven, not role) but has no reports is still denied team heatmap; error/IDOR matrix (Testcontainers PG): IC→command-center denied + audit; IC→heatmap denied + audit; IC→drill-down denied `404` + audit; manager→another manager's drill-down cell `404` + audit; integration: each denial writes exactly one `audit_event` with no sensitive fields and emits the denial signal.
- [ ] Requirements: REQ-F-030, REQ-UX-005, REQ-E-002

### 6.7 — Projection rebuild job/CLI (REQ-D-013) — no UI
- [ ] `ProjectionRebuildRunner` is an internal job/CLI entrypoint (e.g. ApplicationRunner gated on an `--app.job=` arg, mirroring the generation runner pattern §8) that truncates/recomputes BOTH `manager_plan_summary` and `manager_heatmap_cell` from source tables — no user-facing admin UI (REQ-D-013 / DATA_MODEL §355–356 / §9).
- [ ] Rebuild reuses the SAME count + badge derivation as the incremental path (6.2/6.4) so a full rebuild equals the accumulated incremental projections (rebuild==incremental, §17 / RISK-003).
- [ ] Rebuild is deterministic and idempotent (rerun produces identical rows); runs under the SYSTEM principal context (audit-logged under SYSTEM actor, §6).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/projection/ProjectionRebuildRunner.java`.
- [ ] Cross-doc invariant: extended (ManagerPlanSummary, ManagerHeatmapCell — recomputed-from-source equality).
- [ ] Tests — happy: after running the live trigger writes (6.3), a full rebuild produces byte-identical summary + heatmap rows (rebuild == incremental); edge: rebuild on an empty DB yields zero rows; rerun is idempotent (no duplicates, no row churn); error: rebuild does not touch domain/source tables (read-only over source); integration (Testcontainers PG): seeded Appendix E fixtures rebuild to the literal seeded projection values (§9/§17).
- [ ] Requirements: REQ-D-013

### Acceptance criteria (6)
- [ ] All 6.X task checkboxes ticked.
- [ ] `manager_plan_summary` + `manager_heatmap_cell` update synchronously inside every one of the 9 affected service transactions, with a rollback test proving no partial drift (RISK-003).
- [ ] Count derivation matches §9 exactly: `misaligned_count` = `alignment_status=MISALIGNED` OR open MISALIGNED dispute (deduped); `is_review_overdue=false` while `REVIEWED_WITH_DISPUTES` (REQ-F-013).
- [ ] `risk_badges` use only the enumerated vocabulary and reproduce the Appendix E demo coverage; badges never conflict with underlying state (RISK-014).
- [ ] E13 returns the B.20 envelope with default sort `weekStartDate DESC, employeeDisplayName ASC` and honors all REQ-F-023 filters incl. derived `OVERDUE`; E14 is bounded/unpaginated and scoped to active reports; E15 drill-down is paginated with `priority ASC, createdAt ASC`.
- [ ] Own-cell-only drill-down: non-own cell ⇒ `404` + audit; IC ⇒ team heatmap denied server-side + audit (REQ-F-030/UX-005/E-002).
- [ ] Manager reads are N+1-free over the §4 indexes (query-count assertion).
- [ ] `ProjectionRebuildRunner` rebuild == incremental projection (equality test green); no admin UI exists (REQ-D-013).

---

## Phase 7 — Comments (flat)

**Goal:** Build the flat (one-level) commenting vertical slice for `target_type ∈ {PLAN, COMMITMENT}` only. Every comment is created with `parent_comment_id = NULL`, `depth = 0`, and **no path maintenance** (schema columns retained so nesting can be enabled later — §22 decision 4). Deliver `POST /api/comments` and `GET /api/comments?targetType=&targetId=` (paginated, `createdAt` ASC) behind a central comment authorizer that resolves the target to its owning plan and enforces the exact §6/§11 self/direct-report rule: ICs comment on their own plan/commitment; managers comment only on direct-report targets when the plan is `LOCKED`-or-later; an unseeable or nonexistent target returns IDOR-safe `404` (never reveal existence). Comment `body` is server-side validated per Appendix E (required, ≤4000 code points after trim, NFC, Unicode-safe, HTML/script stored raw and React-escaped on render — never `dangerouslySetInnerHTML`) and is treated as sensitive (bodies never logged to CloudWatch or `metadata_json`, §15). The dispute IC-response path stays on `alignment_dispute.ic_response` and is explicitly NOT routed through comments.

**Spec anchors:** `ARCHITECTURE.md §11`, §5, §6, §16, §15, Appendix A (Comment), Appendix B (B.1 `CommentTargetType`, B.2 E20/E21, B.9 `CommentDto`/`CreateCommentRequest`, B.20 envelope, B.21 ProblemDetail), Appendix C (C.2 comment package + V-migration, C.4 `features/comment/`), Appendix E Part 1 (`comment.body` row + cross-cutting rules), §22 decision 4.

### 7.1 — Comment schema (flat MVP), entity, enum & repository
- [ ] Migration creates/finalizes the `comment` table with the **MVP-narrowed** CHECK: `target_type varchar(32) not null check (target_type in ('PLAN','COMMITMENT'))` — the draft DATA_MODEL.md `('PLAN','COMMITMENT','MANAGER_REVIEW','ALIGNMENT_DISPUTE')` is superseded by this contract (§4, B.21 "this contract wins").
- [ ] Columns present and flat-defaulted: `id uuid pk`, `target_id uuid not null`, `author_employee_id uuid not null → employee(id)`, `parent_comment_id uuid null` (retained, always NULL in MVP), `path` (retained; MVP writes a deterministic non-null placeholder, no ancestry maintenance), `depth integer not null default 0`, `body text not null`, auditing columns; indexes `(target_type, target_id)` and `(author_employee_id)` to back the E20 list query ordered by `createdAt` ASC.
- [ ] `enums/CommentTargetType.java` with values exactly `PLAN`, `COMMITMENT` (B.1) — mirrors Appendix A; unknown wire value rejected as `400 VALIDATION_ERROR`, never 500 (Appendix E cross-cutting rule 1).
- [ ] `comment/Comment.java` JPA entity extends `AbstractAuditingEntity` / `PersistableUuidEntity`, `target_type` as `VARCHAR` + CHECK (not native enum, §4); `comment/repo/CommentRepository.java` exposes a `Page` finder by `(targetType, targetId)` sorted `createdAt` ASC.
- [ ] Files: NEW `apps/wc-api/shared/src/main/java/com/st6/wc/comment/Comment.java`, `apps/wc-api/shared/src/main/java/com/st6/wc/comment/repo/CommentRepository.java`, `apps/wc-api/shared/src/main/java/com/st6/wc/enums/CommentTargetType.java`; extend `apps/wc-api/shared/src/main/resources/db/migration/V1__core_schema.sql` (comment table within core schema per C.2).
- [ ] Cross-doc invariant: extended (Comment — flat MVP, nestable schema; `target_type {PLAN,COMMITMENT}`, `parent_comment_id` NULL, `depth=0`).
- [ ] Tests — happy: persist a `PLAN` and a `COMMITMENT` comment with `depth=0`, `parent_comment_id=NULL`, retrieve via repo paginated `createdAt` ASC (Testcontainers PG); edge: two comments same target ordered oldest-first; error: insert `target_type='MANAGER_REVIEW'` rejected by CHECK constraint; integration: repo pagination envelope honors `size`/`page`.
- [ ] Requirements: REQ-D-014.

### 7.2 — `body` validation (Appendix E) + sensitive-field log hygiene
- [ ] `CreateCommentRequest` DTO carries `targetType` (required), `targetId` (required uuid), `body` (required); Jakarta Bean Validation on the **request DTO** (never the entity, §5): `@NotBlank` + length cap counted in Unicode code points (`String.codePointCount`) ≤ 4000 after `strip()`; NFC normalize; preserve internal newlines; trim/normalize happens once at the DTO boundary (Appendix E cross-cutting rule 5).
- [ ] Empty/whitespace-only body → `400`/`422` RFC-7807 `VALIDATION_ERROR` with `fieldErrors[]` (field `body`); body >4000 cp → same; valid Unicode (e.g. `🚩مرحبا`) accepted and stored verbatim.
- [ ] HTML/script body (`<img src=x onerror=alert(1)>`) is stored **raw** server-side — no HTML stripping; system never renders user text as HTML (Appendix E rule 3, §16).
- [ ] Comment `body` is sensitive: never written to CloudWatch logs or `audit_event.metadata_json` (§15); request/response body logging stays off.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/comment/dto/CreateCommentRequest.java`, `apps/wc-api/api/src/main/java/com/st6/wc/comment/dto/CommentDto.java`, `apps/wc-api/api/src/main/java/com/st6/wc/comment/mapper/CommentMapper.java`; reuses `web/ProblemDetailsExceptionHandler.java` + `web/ErrorCodes.java`.
- [ ] Cross-doc invariant: none (DTO/validation layer; binds to Appendix A Comment field names).
- [ ] Tests — happy: 1-cp and 4000-cp bodies accepted; edge: 4001 cp rejected, leading/trailing whitespace trimmed before cap count, NFC normalization applied once; error: empty + whitespace-only → `400 VALIDATION_ERROR` with `body` fieldError; integration: posted `<img src=x onerror=alert(1)>` round-trips raw via GET and assert no body text appears in captured audit/log output.
- [ ] Requirements: REQ-S-005.

### 7.3 — Comment authorizer (target→owning-plan resolution, IDOR-safe 404)
- [ ] Add a comment authorization path to the central `DomainAuthorizationService` (§6 central authorizer — never a controller annotation): resolves `(targetType, targetId)` to the owning plan (`COMMITMENT` → its `weekly_plan`; `PLAN` → itself).
- [ ] Nonexistent `target_id`, or a target not visible to the actor, returns **`404`** (IDOR-safe — never reveal existence, B.0/B.9); an authorization-denial `audit_event` is written for the denial (§6/§15, IDs/state only, no body).
- [ ] Self/direct-report rule enforced identically for read (E20) and write (E21): owning IC may comment on/read their own plan or commitment in any state; a direct manager may comment on/read a direct-report target **only when the plan is `LOCKED`-or-later** (manager comment on a `DRAFT` target → denied); a manager touching a non-direct-report target → `404` (REQ-S-002, RISK-001).
- [ ] Files: extend `apps/wc-api/api/src/main/java/com/st6/wc/auth/DomainAuthorizationService.java`; reuses `auth/AuthorizationDeniedAuditer.java`, `relationship/ManagerRelationship` lookup, `sync`/`comment` repos for resolution.
- [ ] Cross-doc invariant: none (authorization service; consumes Comment + WeeklyPlan + ManagerRelationship invariants).
- [ ] Tests — happy: owning IC authorized for own PLAN + COMMITMENT (any state); direct manager authorized for LOCKED direct-report PLAN/COMMITMENT; edge: manager on `DRAFT` direct-report target denied; integration (Testcontainers PG IDOR matrix): IC comments on another IC's target → `404`; manager comments on non-direct-report target → `404`; nonexistent `targetId` → `404`; error: each denial emits exactly one authorization-denial `audit_event` with no body in metadata.
- [ ] Requirements: REQ-F-014, REQ-S-005.

### 7.4 — POST /api/comments + GET /api/comments (service, controller, endpoints)
- [ ] `POST /api/comments` (E21): coarse authn gate on controller; service-method transaction calls the 7.3 authorizer first, validates 7.2 body, persists with `parent_comment_id=NULL`, `depth=0`, `author_employee_id` = authenticated principal; returns `CommentDto` (B.9) with `parentCommentId=null`, `depth=0`. Manager targets require plan `LOCKED`+ (E21 precondition).
- [ ] `GET /api/comments?targetType=&targetId=&page=&size=` (E20): authorizer gate (same rule); returns the B.20 paginated envelope of `CommentDto` ordered `createdAt` **ASC**; `page` default 0, `size` default 25 max 100.
- [ ] `allowedActions[]` `COMMENT` affordance surfaces on `WeeklyPlanDto`/`WeeklyCommitmentDto` per B.1 (owning IC always; direct manager when `LOCKED`+) — UI affordance only, never the authorization source (§5/§6).
- [ ] Dispute IC-response is explicitly NOT a comment: no comment endpoint accepts dispute targets (CHECK forbids it); IC rationale lives on `alignment_dispute.ic_response` (§11) — assert no `MANAGER_REVIEW`/`ALIGNMENT_DISPUTE` target is reachable.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/comment/CommentController.java`, `apps/wc-api/api/src/main/java/com/st6/wc/comment/CommentService.java`.
- [ ] Cross-doc invariant: none (service/controller; binds B.9 DTO + B.2 E20/E21 contracts).
- [ ] Tests — happy: IC posts then GETs own plan comment, gets B.20 envelope, `createdAt` ASC, `depth=0`/`parentCommentId=null`; edge: pagination `size=1` across 3 comments returns correct `totalElements`/`totalPages`, empty target list returns empty `content`; error: manager POST to `DRAFT` direct-report plan denied, unknown `targetType` → `400`; integration: full POST→GET round-trip through MVC with demo identity header asserts ordering + flat fields.
- [ ] Requirements: REQ-F-014, REQ-D-014.

### 7.5 — Frontend comment list + form (flat, React-escaped)
- [ ] `commentsApi.ts` RTK Query slice: `getComments` query (paginated, `createdAt` ASC) and `addComment` mutation; mutation **invalidates** the comment tag for the target (no optimistic update — refetch/invalidate per §7); query/mutation parameterized by `targetType`+`targetId`.
- [ ] `CommentList.tsx` renders flat comments (no indentation/threading) with author display name + timestamp; renders explicit loading / empty / error / success view states (§7 view-state contract); `safeMessage` from RFC-7807 errors rendered as Cypress-assertable text.
- [ ] `CommentForm.tsx` posts `body`; body rendered via React default-escaping only — **no `dangerouslySetInnerHTML`** (§16); the XSS probe `<img src=x onerror=alert(1)>` and Unicode probe `🚩مرحبا` render as escaped literal text.
- [ ] Comment surfaces are gated by the `COMMENT` `allowedAction` from the plan/commitment DTO (affordance), not by client-side authorization logic.
- [ ] Files: NEW `apps/wc-web/src/features/comment/commentsApi.ts`, `apps/wc-web/src/features/comment/CommentList.tsx`, `apps/wc-web/src/features/comment/CommentForm.tsx`; reuses `shared/components/{LoadingState,EmptyState,ErrorState,Pagination}.tsx`, `shared/lib/problemDetails.ts`, `app/tags.ts`.
- [ ] Cross-doc invariant: none (frontend; consumes B.9 DTO + B.20 envelope).
- [ ] Tests — happy (Vitest): list renders comments oldest-first, form submit triggers mutation + cache invalidation/refetch; edge: empty state when no comments; error: API `400`/`404` renders `safeMessage` text; integration (Vitest, §16/§17): XSS + Unicode probe bodies render escaped (no script execution, literal `<img …>` text present in DOM, no `dangerouslySetInnerHTML`).
- [ ] Requirements: REQ-S-005, REQ-F-014.

### Acceptance criteria (7)
- [ ] All 7.X task checkboxes ticked.
- [ ] `comment.target_type` CHECK admits **only** `PLAN`,`COMMITMENT`; every created comment has `parent_comment_id=NULL` and `depth=0` with no path maintenance, schema columns retained nestable (§22 decision 4).
- [ ] Comment authorizer returns IDOR-safe `404` for nonexistent/unseeable targets and for cross-IC / non-direct-report access, each emitting an authorization-denial `audit_event` (no body in metadata) — IDOR matrix green (RISK-001).
- [ ] Manager comments succeed only on `LOCKED`+ direct-report targets; ICs comment on own targets in any state (REQ-F-014).
- [ ] `body` validation covers empty/length≤4000-code-points/Unicode/HTML-script, stored raw and React-escaped; XSS + Unicode probes render escaped in the frontend (REQ-S-005, §16).
- [ ] `GET /api/comments` returns the B.20 paginated envelope ordered `createdAt` ASC; dispute IC-response remains on `alignment_dispute.ic_response`, not comments.
- [ ] Comment bodies appear in no CloudWatch log or `audit_event.metadata_json` (§15).

---

## Phase 8 — Outlook calendar sync (non-blocking integration)

**Goal:** Implement the full Outlook Graph calendar sync slice as a durable-outbox + state-machine pipeline that is provably non-blocking on the core IC/manager lifecycle. Lifecycle triggers (plan lock, start-reconciliation, first direct-report lock of a manager/week) write a `outlook_calendar_sync_record` in `PENDING_PUBLISH` *inside the core transaction* (§10 outbox); a single `SnsLifecyclePublisher` pointer-payload path (`{syncRecordId,eventKind,env,traceId}`, F.2) feeds an `@SqsListener` worker that loads authoritative data by `syncRecordId`, guards redelivery, and drives `PENDING_PUBLISH→QUEUED→SYNCING→SYNCED|FAILED` (manual retry `FAILED→RETRY_REQUESTED` re-publishing the same pointer with no duplicates). A `GraphCalendarAdapter` interface with real/demo-success/demo-failure implementations plus a hybrid fail-safe selector ensures missing/blank creds degrade to demo and never block or crash; failures are visible (`GET /api/outlook-sync`) and retryable (`POST .../retry`). Local/CI exercise the worker via the in-process dispatch profile (§13). The cardinal invariant: a Graph/SNS failure NEVER propagates an exception into, or rolls back, the core lifecycle transaction (REQ-I-004, NEVER-TRIM, §20).

**Spec anchors:** `ARCHITECTURE.md §10`, §8, §12, §13, §15, §16, Appendix A (`OutlookCalendarSyncRecord`), Appendix B (B.1 enums, B.2 E22–E23, B.10 `OutlookSyncRecordDto`), Appendix C (C.2 `sns/`, `sync/`; C.3 worker `graph/`, `listener/`, `payload/`, `service/`), Appendix D (D.2/D.3 config + fail-safe rules), Appendix F (F.2 pointer JSON, F.4 `RETRY_SYNC`→endpoint), Diagrams supplement (3).

### 8.1 — Sync-record entity, enums, and outbox-state invariants
- [ ] `OutlookCalendarSyncRecord` JPA entity maps every Appendix A field verbatim: `owner_employee_id`, `related_type {WEEKLY_PLAN,MANAGER_REVIEW_WEEK}`, `related_id`, `event_kind {IC_PLANNING,IC_RECONCILIATION,MANAGER_REVIEW_BLOCK}`, `status {PENDING_PUBLISH,QUEUED,SYNCING,SYNCED,FAILED,RETRY_REQUESTED}`, `graph_event_id`, `failure_code`, `safe_message`, `retry_count`, `trace_id`, `week_start_date` (NEW), `@Version`.
- [ ] Enums `SyncRelatedType`, `EventKind`, `SyncStatus` are created with exactly the B.1 wire values (no extras, no `MANAGER_REVIEW` legacy value — the contract's `MANAGER_REVIEW_WEEK` supersedes DATA_MODEL.md).
- [ ] Flyway DDL pins both uniqueness rules: `unique(owner_employee_id, related_type, related_id, event_kind)` for plan kinds AND partial-unique `(owner_employee_id, week_start_date) WHERE event_kind='MANAGER_REVIEW_BLOCK'` (the per-manager/week grain) — repository exposes a partial-unique-aware upsert/lookup.
- [ ] A state-transition helper rejects illegal transitions (e.g. `SYNCED→SYNCING`, `PENDING_PUBLISH` skipping `QUEUED` on the publish path) so the §10 owners are enforced in code, not just by convention.
- [ ] Files: extended `apps/wc-api/shared/src/main/java/com/st6/wc/sync/OutlookCalendarSyncRecord.java`; NEW `.../enums/SyncRelatedType.java`, `.../enums/EventKind.java`, `.../enums/SyncStatus.java`, `.../sync/repo/OutlookCalendarSyncRecordRepository.java`; extended `.../resources/db/migration/V1__core_schema.sql` + `V2__partial_unique_indexes.sql`.
- [ ] Cross-doc invariant: extended (`OutlookCalendarSyncRecord` — adds `week_start_date`, `MANAGER_REVIEW_WEEK`, partial-unique per-manager/week to the Appendix A row).
- [ ] Tests — happy: persist + reload a plan-kind record and a review-block record with all fields; edge: second `MANAGER_REVIEW_BLOCK` for same `(owner,week_start_date)` hits partial-unique → upsert returns existing row (no duplicate); error: illegal transition `SYNCED→SYNCING` rejected; integration (Testcontainers PG): both unique constraints enforced at the DB layer, `@Version` increments on update.
- [ ] Requirements: REQ-I-006, REQ-I-010, REQ-I-015, REQ-D-001, REQ-D-009, REQ-D-010.

### 8.2 — `SnsLifecyclePublisher`: the single publish path (pointer payload only)
- [ ] `SnsLifecyclePublisher` is the ONE method that publishes to `SNS_TOPIC_ARN`; both initial publish (after `PENDING_PUBLISH`) and manual-retry republish go through it — no second publish site exists anywhere.
- [ ] Payload is exactly the four F.2 fields `{syncRecordId, eventKind, env, traceId}` serialized as the wire JSON; `env ∈ {local,aws}`; NO calendar bodies, notes, secrets, or tokens are ever placed in the message (REQ-I-014, §16).
- [ ] On successful publish the publisher (via `SyncRecordService`) sets `status=QUEUED`; an SNS publish exception is caught, logged with a safe message, and NEVER rethrown into the caller's core transaction (REQ-I-004) — record stays `PENDING_PUBLISH`/`RETRY_REQUESTED` and remains retryable.
- [ ] `SyncJobPointer` (worker payload type) round-trips the same four fields; serialization is asserted byte-shape stable so the in-process and SNS paths are identical (§13).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/sns/SnsLifecyclePublisher.java`, `.../config/SnsConfig.java`; NEW `apps/wc-api/worker/src/main/java/com/st6/wc/worker/payload/SyncJobPointer.java`.
- [ ] Cross-doc invariant: none (publisher is behavior over the §8.1 model).
- [ ] Tests — happy: publish serializes exactly the F.2 JSON, sets `QUEUED`; edge: trace id propagated unchanged; error: SNS client throws → caught, logged safe, status NOT advanced, no exception escapes; integration: payload contains none of {title, note, secret, token} (assert by scanning serialized body).
- [ ] Requirements: REQ-I-009, REQ-I-014, REQ-I-004, REQ-S-004.

### 8.3 — `SyncRecordService` (API side): outbox writes, QUEUED transition, manual-retry republish
- [ ] `SyncRecordService` writes `PENDING_PUBLISH` records strictly within the caller's core service transaction (consumed by 8.6 triggers); publish→`QUEUED` happens after the core commit semantics so a failed publish cannot roll the core txn back.
- [ ] Manual retry path: `FAILED → RETRY_REQUESTED` then re-publish the SAME pointer (same `syncRecordId`, same `eventKind`) via `SnsLifecyclePublisher` → `QUEUED`; the existing row is reused, never duplicated (REQ-I-010); `retry_count` is left as the worker's informational counter.
- [ ] Retry is rejected (no-op + safe state) for any status other than `FAILED`, matching E23's `record FAILED` precondition.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/sync/SyncRecordService.java`.
- [ ] Cross-doc invariant: none (orchestration over 8.1/8.2).
- [ ] Tests — happy: trigger writes `PENDING_PUBLISH`, then `QUEUED` after publish; retry on `FAILED` → `RETRY_REQUESTED` → `QUEUED` reusing the same id; edge: retry called twice concurrently → optimistic-lock `@Version` guards, exactly one republish; error: retry on `SYNCED`/`QUEUED` rejected; integration: core mutation commits even when publish throws (record left retryable, core row LOCKED/RECONCILING).
- [ ] Requirements: REQ-I-010, REQ-I-004, REQ-I-006.

### 8.4 — `ManagerReviewBlockResolver` (owner = locking IC's direct manager, per-manager/week key)
- [ ] On a direct-report plan lock, resolve the locking IC's single active direct manager via `manager_relationship WHERE active` (single-active-manager invariant, §6) to set the review-block record's `owner_employee_id` = that manager.
- [ ] The review-block record is written with `related_type='MANAGER_REVIEW_WEEK'`, `related_id` = the manager's employee id, `event_kind='MANAGER_REVIEW_BLOCK'`, `week_start_date` = the plan's week; the upsert keys on the partial-unique `(owner_employee_id, week_start_date)` so the **first** direct-report lock of a manager/week creates it and subsequent locks are idempotent no-ops (one event per manager/week, REQ-I-015).
- [ ] If the locking IC has no active manager (e.g. the self-owning manager persona, Appendix E "Dana") NO review-block record is created and lock still succeeds (degrades cleanly — §20 trim-order item 1).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/sync/ManagerReviewBlockResolver.java`.
- [ ] Cross-doc invariant: extended (`OutlookCalendarSyncRecord` review-block grain; consumes `ManagerRelationship` single-active-manager partial-unique).
- [ ] Tests — happy: first report lock for manager M in week W creates one `MANAGER_REVIEW_BLOCK` owned by M; edge: second/third report lock same M/W → partial-unique upsert, still one row; edge: IC with no active manager → zero review-block records, lock OK; error: two active managers would violate the §6 invariant (assert resolver depends on the single-active partial-unique); integration: per-manager/week grain holds across two reports of the same manager in Testcontainers.
- [ ] Requirements: REQ-I-015, REQ-I-001, REQ-I-004.

### 8.5 — `DeepLinkBuilder` (worker, `WC_FRONTEND_BASE_URL`)
- [ ] `DeepLinkBuilder` reads `WC_FRONTEND_BASE_URL` (= `https://wc.<ROOT_DOMAIN>`, D.3) and builds: IC events → `{base}/weekly-commit` (current) or `{base}/weekly-commit/history/{planId}`; review-block → `{base}/manager/command-center` (§10, REQ-I-003).
- [ ] Link selection is keyed on `event_kind`: `IC_PLANNING`/`IC_RECONCILIATION` → IC routes, `MANAGER_REVIEW_BLOCK` → command-center; no per-report manager link is produced (per-report manager events are deferred, §20).
- [ ] Base URL is normalized (single trailing-slash handling) so links are well-formed regardless of `WC_FRONTEND_BASE_URL` trailing slash.
- [ ] Files: NEW `apps/wc-api/worker/src/main/java/com/st6/wc/worker/graph/DeepLinkBuilder.java`.
- [ ] Cross-doc invariant: none (uses route strings pinned in §7/C.4).
- [ ] Tests — happy: each `event_kind` → expected URL; edge: base with and without trailing slash both yield one clean slash; edge: history link includes `{planId}` only for the history variant; error: unknown `event_kind` → guarded (defensive default, never NPE).
- [ ] Requirements: REQ-I-003.

### 8.6 — Lifecycle triggers: create sync records inside the core transaction
- [ ] Plan `lock` (E8) creates an `IC_PLANNING` record (`related_type='WEEKLY_PLAN'`, `related_id`=planId, `PENDING_PUBLISH`) AND invokes `ManagerReviewBlockResolver` (8.4) — all inside the same `PlanLifecycleService` lock transaction that freezes baseline + creates the review/projection/audit rows (Diagram 1).
- [ ] `start-reconciliation` (E9) creates an `IC_RECONCILIATION` record (`PENDING_PUBLISH`) inside the start-reconciliation transaction (Diagram 4).
- [ ] Weekly-shell **generation** (CronJob, §8) creates ZERO sync records — assert no `outlook_calendar_sync_record` rows result from generation (REQ-I-002).
- [ ] Non-blocking guarantee is structural: sync-record creation + publish are arranged so an SNS/publish failure cannot roll back the core lifecycle txn (the core commit owns LOCKED/RECONCILING; publish failure leaves the record retryable) — a forced publish failure leaves the plan LOCKED/RECONCILING and the record `PENDING_PUBLISH` (REQ-I-004, NEVER-TRIM).
- [ ] Files: extended `apps/wc-api/api/src/main/java/com/st6/wc/plan/PlanLifecycleService.java` (call sites only — no transition logic duplicated here).
- [ ] Cross-doc invariant: extended (`OutlookCalendarSyncRecord` rows created by `WeeklyPlan` transitions; `ManagerReview` co-created at lock per §10).
- [ ] Tests — happy: lock creates exactly one `IC_PLANNING` + (if managed) one review-block; start-reconciliation creates one `IC_RECONCILIATION`; edge: re-lock attempt rejected upstream so no duplicate `IC_PLANNING`; error (NEVER-TRIM): stubbed publish failure → plan still LOCKED, record present and retryable, no exception to caller; integration: CronJob generation run produces zero sync records (REQ-I-002).
- [ ] Requirements: REQ-I-001, REQ-I-002, REQ-I-004, REQ-I-009.

### 8.7 — Worker `@SqsListener` + in-process dispatch + redelivery guard
- [ ] `SyncJobSqsListener` consumes the SQS pointer (`@SqsListener` on `SQS_QUEUE_URL`, raw delivery), loads the row by `syncRecordId`, and delegates to `WorkerSyncRecordService`; authoritative data is reloaded from PostgreSQL (REQ-I-014) — the message is never trusted for content.
- [ ] Redelivery guard: status `SYNCED` or active-`SYNCING` ⇒ no-op; the worker only (re)attempts Graph when status ∈ `{QUEUED, RETRY_REQUESTED}`, then sets `SYNCING` (§10, Diagram 3 guard).
- [ ] `InProcessSyncDispatcher` (local/demo profile, §13) lets `wc-api` invoke the identical consume logic in-process after the outbox is written, so worker + Outlook E2E run with no live SNS/SQS; the `aws` profile uses the real `@SqsListener`.
- [ ] DLQ is message-level only via `maxReceiveCount` redrive; the sync row stays `FAILED` (user-visible retryable terminal) regardless of DLQ landing — there is no admin redrive UI (REQ-I-011, REQ-X-011, §20).
- [ ] Files: NEW `apps/wc-api/worker/src/main/java/com/st6/wc/worker/listener/SyncJobSqsListener.java`, `.../listener/InProcessSyncDispatcher.java`, `.../service/WorkerSyncRecordService.java`, `.../config/SqsConfig.java`, `.../config/WorkerSecurityContextConfig.java` (SYSTEM principal).
- [ ] Cross-doc invariant: none (worker writes only sync-record state + `graph_event_id` — §6 SYSTEM write surface).
- [ ] Tests — happy: pointer for `QUEUED` row → `SYNCING`→`SYNCED`; edge: redelivery of `SYNCED` row → no-op (no second Graph call); edge: `RETRY_REQUESTED` row re-attempted; error: redelivery of active-`SYNCING` → no-op; integration: in-process dispatcher reproduces the same transitions as the `@SqsListener` path with the demo adapter (§13/§17 async-worker handler test).
- [ ] Requirements: REQ-I-009, REQ-I-011, REQ-I-014, REQ-I-006.

### 8.8 — Graph adapters + hybrid fail-safe selector (idempotent create-vs-update)
- [ ] `GraphCalendarAdapter` interface (`createOrUpdateEvent(SyncContext) → graphEventId`); `WorkerSyncRecordService` reuses an existing `graph_event_id` to UPDATE rather than create, so retry produces zero duplicate events (REQ-I-006, Diagram 3).
- [ ] `RealGraphCalendarAdapter` uses app-only `Calendars.ReadWrite` application permission (§10; mailbox-scoping OQ-005 noted, not blocking); `DemoSuccessGraphAdapter` returns a deterministic `graph_event_id`; `DemoFailureGraphAdapter` returns a deterministic safe failure (`failure_code` + `safe_message`, no token/secret leakage — REQ-S-004, §16).
- [ ] `GraphAdapterSelector` chooses by `GRAPH_MODE {demo-success,demo-failure,real}`; **hybrid fail-safe**: in `real` mode with any of `GRAPH_TENANT_ID/CLIENT_ID/CLIENT_SECRET` missing/blank, it degrades to the demo adapter, records a safe failure on the record, surfaces `safe_message` + manual-retry, and NEVER crashes the consumer or blocks the core lifecycle (D.6 fail-safe, REQ-E-007, REQ-I-004, REQ-I-012).
- [ ] Success → `SYNCED` + `graph_event_id`; failure → `FAILED` + `failure_code` + `safe_message` + `retry_count++`; failure messages carry no token/secret detail (§15/§16).
- [ ] Files: NEW `apps/wc-api/worker/src/main/java/com/st6/wc/worker/graph/GraphCalendarAdapter.java`, `.../RealGraphCalendarAdapter.java`, `.../DemoSuccessGraphAdapter.java`, `.../DemoFailureGraphAdapter.java`, `.../GraphAdapterSelector.java`, `.../config/GraphConfig.java`. *(Gap note: the Graph SDK/transport library for `RealGraphCalendarAdapter` is not pinned in the contract beyond "app-only Calendars.ReadWrite"; OQ-005 keeps the real tenant/mailbox-scoping open — implement the interface + demo adapters fully and stub the real HTTP call behind the same interface, marking it OQ-005-pending without inventing a tenant config.)*
- [ ] Cross-doc invariant: none (adapters write only `graph_event_id`/failure fields on the §8.1 model).
- [ ] Tests — happy: demo-success → `SYNCED` + `graph_event_id`; edge: retry with existing `graph_event_id` → adapter UPDATE branch, zero duplicates, `retry_count++` (demo adapter exposes create-vs-update, §17 idempotent-retry test); error: demo-failure → `FAILED` + safe message, no secret in message; integration: `real` mode + blank creds → selector degrades to demo, record `FAILED` safe, worker does not crash (REQ-E-007/REQ-I-012).
- [ ] Requirements: REQ-I-006, REQ-I-012, REQ-E-004, REQ-E-007, REQ-S-004, REQ-I-004.

### 8.9 — Sync-status endpoints (E22 read + E23 retry) with IDOR-safe authorization
- [ ] `GET /api/outlook-sync?planId=` returns a plain (non-paginated, bounded-per-plan, F.5) array of `OutlookSyncRecordDto` for the owning IC's own plan; the central `DomainAuthorizationService` scopes to ownership and returns `404` IDOR-safe for an unowned/nonexistent plan (E22, §6).
- [ ] `POST /api/outlook-sync/{syncRecordId}/retry` (no body) requires `status=FAILED`, allowed when actor owns (IC) or manages (manager) the record's `owner_employee_id`; transitions `FAILED→RETRY_REQUESTED` and re-publishes via `SyncRecordService`/`SnsLifecyclePublisher` (single path), returning the updated DTO (E23, F.4 `RETRY_SYNC`).
- [ ] `OutlookSyncRecordDto` maps B.10 verbatim including `safeMessage` (Cypress-assertable text, no token leak), `weekStartDate`, `retryCount`, and `allowedActions[]` containing `RETRY_SYNC` iff `FAILED` + actor owns/manages owner.
- [ ] Required denial cases each return `403`/`404` + an authorization-denial `audit_event`: IC reads another IC's plan sync; sync-retry on an unowned/unmanaged record (§6 IDOR matrix).
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/sync/SyncController.java`, `.../sync/dto/OutlookSyncRecordDto.java`, `.../sync/mapper/SyncRecordMapper.java`; extended `.../auth/DomainAuthorizationService.java` (sync scoping).
- [ ] Cross-doc invariant: extended (`OutlookSyncRecordDto` mirrors `OutlookCalendarSyncRecord` per B.10).
- [ ] Tests — happy: owner IC lists own sync records; manager retries a FAILED record of a direct report; edge: `allowedActions` omits `RETRY_SYNC` for non-FAILED rows; error: IC retries unowned record → `404` + audit; error: retry on non-FAILED → `409`/rejected; integration: E22 array bounded per plan, `safeMessage` contains no token/secret substring (security test, §17).
- [ ] Requirements: REQ-I-005, REQ-I-010, REQ-UX-004, REQ-E-004, REQ-S-001, REQ-S-002, REQ-S-011.

### 8.10 — Worker config, IRSA boundary, and audit/observability for sync
- [ ] Worker config wires `SQS_QUEUE_URL`, `SQS_DLQ_URL`, `WC_FRONTEND_BASE_URL`, `GRAPH_MODE` + optional Graph secrets, `spring.flyway.enabled=false`, `SPRING_PROFILES_ACTIVE` (D.3); worker carries NO Auth0/SNS/CORS/timezone config (SYSTEM principal, pointer-only consumer).
- [ ] Worker writes only sync-record state + `graph_event_id` (never commitment/review/dispute content, §6) and emits structured Outlook sync success/failure signals + an `audit_event` under the SYSTEM actor (nullable `actor_employee_id`) for sync outcomes (§15); `audit_event.metadata_json` and CloudWatch logs carry IDs/state only — never tokens, secrets, notes, or PII (§15/§16).
- [ ] k8s manifests grant worker SA least-privilege IRSA: `sqs:{ReceiveMessage,DeleteMessage,GetQueueAttributes}` on queue+DLQ + `GetSecretValue` (graph only); SNS topic + queue + DLQ + `maxReceiveCount` redrive provisioned in Terraform (`sns_sqs.tf`).
- [ ] Actuator `health/liveness` + `health/readiness` exposed on the worker for k8s probes (§15, REQ-O-009).
- [ ] Files: extended `apps/wc-api/worker/src/main/resources/application.yml` + `application-local.yml`/`application-demo.yml`/`application-prod.yml`; extended `infra/terraform/sns_sqs.tf`, `infra/terraform/iam_irsa.tf`; extended `infra/k8s/deployment-worker.yaml`, `infra/k8s/serviceaccount-worker.yaml`; reuse `apps/wc-api/api/src/main/java/com/st6/wc/audit/AuditService.java`.
- [ ] Cross-doc invariant: extended (`AuditEvent` SYSTEM-actor rows for sync outcomes).
- [ ] Tests — happy: sync success/failure each emit one SYSTEM `audit_event` with IDs/state only; edge: `auth0`-less worker boot succeeds in `aws` profile; error: assert no token/secret/note text appears in any emitted sync log line or `metadata_json` (security/log-hygiene test, §15/§16); integration: DLQ redrive policy + `maxReceiveCount` present in Terraform plan and sync row stays `FAILED`.
- [ ] Requirements: REQ-I-011, REQ-S-004, REQ-S-010, REQ-O-001, REQ-O-009, REQ-D-007, REQ-S-006.

### Acceptance criteria (8)
- [ ] All 8.1–8.10 task checkboxes ticked.
- [ ] **Non-blocking proof (NEVER-TRIM, REQ-I-004):** an integration test forces SNS/Graph failure at lock and at start-reconciliation; the plan still commits to LOCKED/RECONCILING, the sync record is left in a retryable state, and no exception reaches the core caller — verified by both an api-side test and the `outlook-failed-retry.feature` E2E (REQ-E-004).
- [ ] **Single publish path:** static/structural check that `SnsLifecyclePublisher` is the only SNS publish site, used by both initial publish and manual retry (REQ-I-009/010).
- [ ] **Idempotency:** retry of a `FAILED` `SYNCED`-history record reuses `graph_event_id` (UPDATE, not create) with zero duplicate Graph events and `retry_count++`; re-locks/redeliveries create no duplicate sync rows (REQ-I-006/010, §17 idempotent-retry test).
- [ ] **Pointer-only & safe failure:** every queue message contains exactly the F.2 four fields with no calendar bodies/secrets, and every `safe_message`/failure log is free of tokens/secrets (REQ-I-014, REQ-S-004, §16).
- [ ] **Hybrid fail-safe:** `GRAPH_MODE=real` with missing/blank creds degrades to the demo adapter, records a safe failure, and never crashes the worker or blocks the lifecycle (REQ-E-007, REQ-I-012).
- [ ] **Per-manager/week grain:** first direct-report lock of a manager/week upserts one `MANAGER_REVIEW_BLOCK` owned by the direct manager; subsequent reports' locks are idempotent no-ops; CronJob generation creates zero sync records (REQ-I-015, REQ-I-002).
- [ ] **Demo visibility:** seeded R2 `FAILED` `IC_PLANNING` record + Dana's `SYNCED` `MANAGER_REVIEW_BLOCK` (Appendix E) render the FAILED warning + manual-retry affordance via E22/E23, demonstrating the non-blocking, recoverable path on the manager command center (REQ-E-004, REQ-UX-004).
---

## Phase 9 — Frontend — IC workspace, manager command center & MFE boundary

**Goal:** Build the `apps/wc-web` React 18 / Vite 5 (TS strict, Flowbite + Tailwind) surface as a Module Federation remote that also runs standalone, consuming the §5/Appendix B API entirely through RTK Query (no Saga/Thunk, no optimistic updates). Establish the auth/identity boundary (`VITE_AUTH_MODE` demo↔auth0 XOR header rule, host `getAccessToken` accessor), the standalone-only persona switcher tree-shaken out of the remote build, lazy-loaded routes, and the explicit loading/empty/error/success/partial view-state contract with Cypress-assertable `safeMessage`. Then deliver the IC workspace (commitment form + chess fields + Supporting-Outcome picker + lock + reconciliation/carry-forward), the manager command center + heatmap + drilldown + filters + dispute panel, the Outlook sync status badge + retry, all wired through `allowedActions[]` and the Appendix F.4 action→endpoint map, with Vitest component + cache-invalidation tests and a documented PA host integration contract.

**Spec anchors:** `ARCHITECTURE.md §7`, §5, §3, §10, §11, §16, Appendix B (DTOs + B.1 enums + B.20 envelope + B.21 ProblemDetail), Appendix C.4 (web tree), Appendix D.1 (Vite env), Appendix F.1–F.7 (constants, action→endpoint map, pagination, ports).

### 9.1 — App shell: baseApi, store, auth accessor, header XOR
- [ ] `app/baseApi.ts` creates the RTK Query base via `fetchBaseQuery({ baseUrl: import.meta.env.VITE_API_BASE_URL })`; `tagTypes` registered from `app/tags.ts` for all nine domains (me, rcdo, plans, commitments, review, disputes, manager, comments, sync).
- [ ] `prepareHeaders` branches on `VITE_AUTH_MODE`: `auth0` ⇒ awaits `getAccessToken()` and sets `Authorization: Bearer <jwt>` ONLY; `demo` ⇒ sets `X-Demo-Employee-Id: <persona>` ONLY; the two header forms are NEVER both attached (XOR enforced and unit-tested).
- [ ] `app/authAccessor.ts` defines the `getAccessToken(): Promise<string>` contract resolved from either the host (remote mode) or the standalone identity provider (standalone mode); `app/store.ts` wires `configureStore` with the baseApi reducer + middleware.
- [ ] Errors are normalized through `shared/lib/problemDetails.ts` which parses the RFC-7807 body (B.21) and exposes `safeMessage`, `code`, `constraint`, `fieldErrors[]` for rendering.
- [ ] Files: NEW `apps/wc-web/src/app/baseApi.ts`, `app/store.ts`, `app/authAccessor.ts`, `app/tags.ts`, `shared/lib/problemDetails.ts`, `.env.example` (VITE_AUTH_MODE, VITE_API_BASE_URL per Appendix D.1), `vite.config.ts` shell (federation plugin added in 9.3)
- [ ] Cross-doc invariant: none (consumes Appendix B wire contract; adds no model surface)
- [ ] Tests — happy: demo mode attaches `X-Demo-Employee-Id` and no `Authorization`; auth0 mode attaches `Bearer` from accessor and no demo header; edge: missing `getAccessToken` in auth0 mode surfaces an error state, not a crash; error: a 409 `application/problem+json` body is parsed into `{safeMessage, code, fieldErrors}`; integration: store dispatch of a query through baseApi sets the correct single header per mode
- [ ] Requirements: REQ-NF-004, REQ-S-005

### 9.2 — Shared view-state primitives + StatusBadge/RiskBadge/Pagination/WeekRangeLabel
- [ ] `shared/components/` provides explicit `LoadingState`, `EmptyState`, `ErrorState`, `PartialState`, `StatusBadge`, `RiskBadge`, `Pagination`, `WeekRangeLabel` components; every data view in later tasks renders exactly one of loading/empty/error/success/partial (the §7 view-state contract).
- [ ] `ErrorState` renders the parsed `safeMessage` as plain, Cypress-assertable text (stable `data-cy`/role hook); never renders raw HTML and never uses `dangerouslySetInnerHTML` (§16).
- [ ] `RiskBadge` maps ONLY the enumerated `RiskBadge` vocabulary `{MISALIGNED, NEEDS_REVIEW, BLOCKED, CARRY_FORWARD, UNREVIEWED, OVERDUE_REVIEW}` (B.1); `StatusBadge` renders `PlanState`/`ReviewStatus` labels reflecting lifecycle so DRAFT/LOCKED/RECONCILING/RECONCILED/UNPLANNED/CARRIED_FORWARD are visually distinguished (REQ-UX-002).
- [ ] `Pagination` consumes the B.20 envelope (`content`, `page.{number,size,totalElements,totalPages}`, `sort`); `WeekRangeLabel` formats a Monday–Sunday range via `shared/lib/formatWeek.ts`.
- [ ] Files: NEW `apps/wc-web/src/shared/components/{LoadingState,EmptyState,ErrorState,PartialState,StatusBadge,RiskBadge,Pagination,WeekRangeLabel}.tsx`, `shared/lib/formatWeek.ts`, `test/setup.ts`
- [ ] Cross-doc invariant: none (renders Appendix A enum vocab verbatim; `RiskBadge`/`PlanState`/`ReviewStatus` values must match B.1)
- [ ] Tests — happy: each view-state component renders its slot; `RiskBadge` renders all six vocabulary values; edge: unknown badge string is ignored, not thrown; error: `ErrorState` renders `safeMessage` text and never injects HTML (XSS probe `<img src=x onerror=alert(1)>` rendered escaped); integration: `Pagination` derives page controls from a B.20 envelope
- [ ] Requirements: REQ-UX-002, REQ-S-005

### 9.3 — Module Federation boundary: WeeklyCommitApp remote + standalone main + tree-shaken persona switcher
- [ ] `vite.config.ts` uses `@originjs/vite-plugin-federation` to `expose: { './WeeklyCommitApp': './src/remote/WeeklyCommitApp.tsx' }` and shares singletons React, React DOM, Redux Toolkit, React-Redux.
- [ ] `src/remote/WeeklyCommitApp.tsx` is the single mountable module: it CONSUMES a router context passed by the host and renders the WC route subtree; it creates NO `BrowserRouter`, owns NO persona switcher, and reads its auth accessor from a host-provided prop/shared singleton (§7 host integration contract).
- [ ] `src/standalone/main.tsx` owns `BrowserRouter` + Redux store provider + identity provider + persona-switcher chrome and mounts the exposed module; `standalone/PersonaSwitcher.tsx` and `standalone/DemoIdentityProvider.tsx` live ONLY under `src/standalone/` and are tree-shaken / compiled out of the exposed remote build (REQ-I-008, mirrors backend `DEMO_AUTH_ENABLED` gate).
- [ ] `DemoIdentityProvider` supplies `getAccessToken()` and, in the demo branch, the active persona id consumed by `prepareHeaders`; the remote build contains no demo/persona code path.
- [ ] Files: NEW `apps/wc-web/src/remote/WeeklyCommitApp.tsx`, `standalone/main.tsx`, `standalone/PersonaSwitcher.tsx`, `standalone/DemoIdentityProvider.tsx`; extended `apps/wc-web/vite.config.ts`
- [ ] Cross-doc invariant: none (boundary contract per §7; no Appendix A model)
- [ ] Tests — happy: standalone `main.tsx` mounts `WeeklyCommitApp` inside a `BrowserRouter` + store + identity provider; edge: `WeeklyCommitApp` rendered with a host-supplied router context does not instantiate its own router; error: rendering the remote module without a host auth accessor surfaces an error state; integration: a build-output / module-graph assertion confirms `PersonaSwitcher` + demo branch are absent from the exposed remote entry (REQ-I-008)
- [ ] Requirements: REQ-I-007, REQ-I-008, REQ-NF-004

### 9.4 — Lazy-loaded route tree (REQ-NF-005)
- [ ] `routes/AppRoutes.tsx` registers all five routes via `React.lazy` + dynamic `import()`: `/` (persona-aware default), `/weekly-commit`, `/weekly-commit/history/:planId`, `/manager/command-center`, `/manager/heatmap`.
- [ ] Each route element is code-split into its own chunk so the initial render does not pull the full app (sub-second initial render intent); a `Suspense` fallback uses `LoadingState`.
- [ ] Manager routes (`/manager/command-center`, `/manager/heatmap`) are gated so an IC persona has no team-heatmap entry point in the UI (REQ-UX-005); `isManager` from `MeDto` (B.3) drives gating.
- [ ] Files: NEW `apps/wc-web/src/routes/AppRoutes.tsx`; extended `src/remote/WeeklyCommitApp.tsx` (renders `<AppRoutes/>`)
- [ ] Cross-doc invariant: none
- [ ] Tests — happy: each route renders its lazy chunk behind `Suspense`; edge: an IC-role identity renders no manager route entry point (REQ-UX-005); error: a failed lazy import shows `ErrorState` not a blank screen; integration: route changes resolve the correct lazy module
- [ ] Requirements: REQ-NF-005, REQ-UX-005

### 9.5 — RTK Query slices: me & rcdo (read foundation + SupportingOutcomePicker)
- [ ] `features/me/meApi.ts` exposes `GET /api/me` (E1) returning `MeDto`; `me/useCurrentUser.ts` surfaces `role`, `isManager`, `persona` for route gating + persona-aware default route.
- [ ] `features/rcdo/rcdoApi.ts` exposes `GET /api/rcdo` (E2) returning the `RcdoTreeDto` object wrapper (`{ rallyCries: RallyCryNode[] }`, NOT a bare array); tagged `RCDO` and never invalidated by mutations (read-only seeded data).
- [ ] `rcdo/RcdoBrowser.tsx` lets the user browse/search the Rally Cry → Defining Objective → Supporting Outcome hierarchy; `rcdo/SupportingOutcomePicker.tsx` selects exactly one Supporting Outcome and renders its RC→DO→SO breadcrumb so any planned commitment can be linked (REQ-UX-001).
- [ ] Files: NEW `apps/wc-web/src/features/me/meApi.ts`, `me/useCurrentUser.ts`, `features/rcdo/rcdoApi.ts`, `rcdo/RcdoBrowser.tsx`, `rcdo/SupportingOutcomePicker.tsx`
- [ ] Cross-doc invariant: none (consumes `MeDto` B.3, `RcdoTreeDto` B.4 verbatim)
- [ ] Tests — happy: `meApi` returns role/isManager; `RcdoBrowser` renders 1 RC / 3 DO / 9 SO from a fixture tree; picker emits the chosen `supportingOutcomeId` + breadcrumb; edge: empty RCDO renders `EmptyState`; search narrows the tree; error: rcdo fetch failure renders `ErrorState`; integration: picking an SO supplies the id consumed by the commitment form (9.6)
- [ ] Requirements: REQ-UX-001, REQ-NF-004

### 9.6 — RTK Query slices: plans & commitments + cache-invalidation wiring
- [ ] `features/plan/plansApi.ts` exposes `GET /api/plans/current` (E3) and `GET /api/plans/{id}` (E4) returning `WeeklyPlanDto` (B.5, including nested `commitments[]`, `managerReview`, `allowedActions[]`, `version`); query results tagged `Plans`.
- [ ] `features/commitment/commitmentsApi.ts` exposes the mutations `POST /api/plans/{id}/commitments` (E5), `PATCH /api/commitments/{id}` (E6), `DELETE /api/commitments/{id}` (E7), `POST /api/plans/{id}/unplanned-commitments` (E11) using the B.6 request DTOs; every mutation invalidates the affected `Plans` (+ `Manager`/`Heatmap`/`Sync` where the server projection changes) tags so the view refetches — NO optimistic updates.
- [ ] Mutations surface RFC-7807 errors (e.g. `409 LOCKED_BASELINE_EDIT`, `409 ILLEGAL_STATE_TRANSITION` for post-lock `alignmentStatus`) via the parsed `safeMessage`.
- [ ] Files: NEW `apps/wc-web/src/features/plan/plansApi.ts`, `features/commitment/commitmentsApi.ts`; extended `app/tags.ts`
- [ ] Cross-doc invariant: none (consumes `WeeklyPlanDto` B.5 / `WeeklyCommitmentDto` B.6 / E5/E6/E11 request DTOs verbatim)
- [ ] Tests — happy: `GET /plans/current` populates the plan with commitments + `allowedActions`; create/patch/delete return updated DTOs; edge: deleting in non-DRAFT surfaces the server `409` `safeMessage`; error: patching an immutable baseline field renders the `LOCKED_BASELINE_EDIT` `safeMessage`; integration (cache-invalidation): a successful create commitment invalidates `Plans` and triggers a refetch of `GET /plans/current` (assert no stale list, no optimistic write)
- [ ] Requirements: REQ-NF-004, REQ-UX-002, REQ-S-005

### 9.7 — IC workspace: WeeklyPlanView + CommitmentForm + ChessLayerFields + CommitmentList + lifecycle/lock bar
- [ ] `plan/WeeklyPlanView.tsx` renders the current plan with explicit loading/empty(not-started shell)/error/success states; an empty shell shows `EmptyState` prompting the first commitment.
- [ ] `commitment/CommitmentForm.tsx` + `commitment/ChessLayerFields.tsx` capture title, description, `SupportingOutcomePicker` link, and the chess fields `priority {P0,P1,P2}`, `workType {STRATEGIC,MAINTENANCE,BLOCKER,UNPLANNED}` (planned form rejects/omits `UNPLANNED`), `confidence {HIGH,MEDIUM,LOW}`, `alignmentStatus {ALIGNED,NEEDS_REVIEW,MISALIGNED}` (default `NEEDS_REVIEW`); `alignmentStatus` field is read-only once the plan is past DRAFT (§3).
- [ ] `commitment/CommitmentList.tsx` distinguishes PLANNED vs UNPLANNED and carried-forward items via badges (REQ-UX-002); each row’s controls are driven by `allowedActions[]` on the `WeeklyCommitmentDto`.
- [ ] `plan/PlanLifecycleBar.tsx` + `plan/LockButton.tsx` render lifecycle actions; `LockButton` is enabled ONLY when `LOCK ∈ plan.allowedActions[]` and on click invokes `POST /api/plans/{id}/lock` (E8) per the F.4 map; a blocked lock (e.g. `UNLINKED_PLANNED_COMMITMENT` / `EMPTY_PLAN_LOCK`) renders the server `safeMessage` and `fieldErrors[]` as Cypress-assertable text.
- [ ] Files: NEW `apps/wc-web/src/features/plan/WeeklyPlanView.tsx`, `plan/PlanLifecycleBar.tsx`, `plan/LockButton.tsx`, `commitment/CommitmentForm.tsx`, `commitment/ChessLayerFields.tsx`, `commitment/CommitmentList.tsx`
- [ ] Cross-doc invariant: none (renders `WeeklyPlanDto`/`WeeklyCommitmentDto` + `AllowedAction` vocab + B.1 chess enums verbatim)
- [ ] Tests — happy: form submits a valid commitment with linked SO + chess fields; `LOCK` action enabled when present and disabled when absent; edge: `alignmentStatus` field is read-only when plan not DRAFT; UNPLANNED badge shown for unplanned items; error: lock on an unlinked plan renders `UNLINKED_PLANNED_COMMITMENT` `safeMessage` + per-field errors; XSS probe in title renders escaped (REQ-S-005); integration: lock success refetches the plan into LOCKED state (no optimistic flip)
- [ ] Requirements: REQ-UX-001, REQ-UX-002, REQ-S-005, REQ-NF-004

### 9.8 — IC reconciliation: outcome form + unplanned + carry-forward
- [ ] `commitment/ReconciliationOutcomeForm.tsx` records `reconciliationOutcome {COMPLETED,PARTIALLY_COMPLETED,BLOCKED,CANCELED,CARRIED_FORWARD}` + `outcomeNote` via `PATCH /api/commitments/{id}` (E6); enforces the single-outcome rule in the UI (`CARRIED_FORWARD` mutually exclusive with completion outcomes, §3) and, for UNPLANNED commitments, requires a `supportingOutcomeId` before close.
- [ ] `plan/PlanLifecycleBar.tsx` exposes `START_RECONCILIATION` / `ADD_UNPLANNED` / `CLOSE_RECONCILIATION` driven by `allowedActions[]`, mapping to E9 / E11 / E10 per F.4; close surfaces `UNPLANNED_MISSING_LINK_AT_CLOSE` `safeMessage` when an unplanned outcome lacks a link.
- [ ] `commitment/CarryForwardButton.tsx` is enabled only when `CARRY_FORWARD ∈ commitment.allowedActions[]`, invokes `POST /api/commitments/{id}/carry-forward` (E12, no body), and reflects idempotency (re-invoke returns the existing next-week commitment); UI shows the carried-forward successor and leaves the locked baseline visually unchanged (REQ-UX-002, source REQ-E-005 behavior is exercised in E2E).
- [ ] All reconciliation mutations invalidate `Plans` (+ `Manager`/`Heatmap`) tags; no optimistic updates.
- [ ] Files: NEW `apps/wc-web/src/features/commitment/ReconciliationOutcomeForm.tsx`, `commitment/CarryForwardButton.tsx`; extended `plan/PlanLifecycleBar.tsx`, `features/commitment/commitmentsApi.ts` (carry-forward + lifecycle mutations)
- [ ] Cross-doc invariant: none (renders `ReconciliationOutcome` B.1 + E12 contract verbatim)
- [ ] Tests — happy: recording each outcome persists + refetches; carry-forward enabled only in RECONCILING; edge: selecting `CARRIED_FORWARD` disables completion outcomes in the form (single-outcome rule); error: close with an unlinked unplanned renders `UNPLANNED_MISSING_LINK_AT_CLOSE`; integration (cache-invalidation): carry-forward success invalidates `Plans` and the successor appears without an optimistic write; double-invoke surfaces the same successor (idempotent)
- [ ] Requirements: REQ-UX-002, REQ-NF-004, REQ-S-005

### 9.9 — Manager command center: rows, filters, mark-reviewed
- [ ] `manager/managerApi.ts` exposes `GET /api/manager/command-center` (E13) returning the B.20 paginated envelope of `ManagerCommandCenterRowDto` (B.11), tagged `Manager`; `manager/CommandCenter.tsx` renders each report’s `planState`, `reviewStatus`, `isReviewOverdue`, and the misaligned/needs-review/blocked/carry-forward/unresolved-dispute counts as actionable signals without opening each plan (REQ-UX-003).
- [ ] `manager/CommandCenterFilters.tsx` drives the E13 query params `employeeId`, `planState`, `reviewState` (incl. `OVERDUE` filtering on derived `isReviewOverdue`), `definingObjectiveId`, `priority`, `workType`, `alignmentStatus`, plus `page`/`size`/`sort` (default `weekStartDate DESC, employeeDisplayName ASC` per F.5); `weekStart` is required.
- [ ] `review/reviewApi.ts` + `review/MarkReviewedAction.tsx` invoke `POST /api/manager/reviews/{reviewId}/mark-reviewed` (E16) with optional `summaryNote`; the action is enabled only when `MARK_REVIEWED ∈ allowedActions[]`; the client never sends the derived `REVIEWED`/`REVIEWED_WITH_DISPUTES` status (server-derived); success invalidates `Manager` + `Heatmap` + `Plans` tags.
- [ ] Command center surfaces explicit loading/empty(no reports)/error/partial states and paginates (does not load all rows client-side, REQ-NF-002 intent satisfied via envelope).
- [ ] Files: NEW `apps/wc-web/src/features/manager/managerApi.ts`, `manager/CommandCenter.tsx`, `manager/CommandCenterFilters.tsx`, `features/review/reviewApi.ts`, `review/MarkReviewedAction.tsx`
- [ ] Cross-doc invariant: none (renders `ManagerCommandCenterRowDto` B.11 + `ReviewStatus` B.1 + `ManagerReviewDto` B.7 verbatim)
- [ ] Tests — happy: command center renders rows with counts + overdue flag; mark-reviewed enabled only when allowed and sends only `summaryNote`; edge: `reviewState=OVERDUE` filter requests the derived-overdue param; empty report set renders `EmptyState`; error: command-center fetch failure renders `ErrorState`; integration (cache-invalidation): mark-reviewed invalidates `Manager`/`Heatmap` and refetches the row
- [ ] Requirements: REQ-UX-003, REQ-NF-004, REQ-NF-005

### 9.10 — Manager heatmap grid + drilldown
- [ ] `manager/managerApi.ts` exposes `GET /api/manager/heatmap` (E14) returning `HeatmapResponseDto` (`{ weekStart, cells }`, NOT paginated) and `GET /api/manager/heatmap/{cellId}/drilldown` (E15) returning `HeatmapDrilldownDto` with a B.20 paginated `commitments` envelope per Supporting Outcome group (default sort `priority ASC, createdAt ASC` per F.5).
- [ ] `manager/HeatmapGrid.tsx` renders report × Defining-Objective cells with counts and explicit `riskBadges[]` from the enumerated vocabulary (no opaque score); `manager/HeatmapCellDrilldown.tsx` shows the Supporting-Outcome → commitment breakdown explaining why a cell is risky (REQ-UX-003, drilldown behavior exercised in E2E REQ-E-003).
- [ ] Drilldown is requested by `cellId`; a `404` (cell not the manager’s own) renders `ErrorState` with the server `safeMessage` (IDOR-safe, never reveals existence).
- [ ] Heatmap + drilldown queries are tagged `Heatmap` and invalidated by the reconciliation/dispute/review mutations.
- [ ] Files: NEW `apps/wc-web/src/features/manager/HeatmapGrid.tsx`, `manager/HeatmapCellDrilldown.tsx`; extended `features/manager/managerApi.ts`
- [ ] Cross-doc invariant: none (renders `HeatmapCellDto`/`HeatmapDrilldownDto` B.12 + `RiskBadge` B.1 verbatim)
- [ ] Tests — happy: grid renders cells with the six-value badge vocabulary; drilldown renders SO groups + paginated commitments; edge: cell with zero risk renders no badges; empty heatmap renders `EmptyState`; error: drilldown `404` renders `safeMessage` without leaking existence; integration: clicking a cell fetches its drilldown by `cellId`
- [ ] Requirements: REQ-UX-003, REQ-NF-004, REQ-NF-005

### 9.11 — Disputes slice + panel (open / respond / resolve) and comments slice
- [ ] `dispute/disputesApi.ts` exposes `POST /api/commitments/{id}/disputes` (E17, `OpenDisputeRequest` {flagType, managerNote}), `POST /api/disputes/{id}/respond` (E18, `RespondDisputeRequest` requires at least one of `icResponse`/`newSupportingOutcomeId`), `POST /api/disputes/{id}/resolve` (E19); each mutation invalidates `Disputes` + `Plans` + `Manager` + `Heatmap` tags.
- [ ] `dispute/DisputePanel.tsx` (manager open, enabled only when `OPEN_DISPUTE ∈ allowedActions[]`), `dispute/DisputeRespondForm.tsx` (IC respond, enabled only when `RESPOND_DISPUTE` present), `dispute/DisputeResolveAction.tsx` (manager resolve, enabled only when `RESOLVE_DISPUTE` present); the IC respond control never resolves and the IC resolve attempt is not surfaced (server `403 IC_CANNOT_RESOLVE_DISPUTE` is rendered as `safeMessage` if attempted).
- [ ] Open with an existing unresolved dispute surfaces `409 SECOND_OPEN_DISPUTE`; missing `managerNote` surfaces the validation `safeMessage`.
- [ ] `comment/commentsApi.ts` + `comment/CommentList.tsx` + `comment/CommentForm.tsx` implement flat (one-level) comments for `targetType {PLAN, COMMITMENT}` via `GET /api/comments` (E20, B.20 envelope, default sort `createdAt ASC`) and `POST /api/comments` (E21); `COMMENT` action enablement follows `allowedActions[]`; bodies render React-escaped (no `dangerouslySetInnerHTML`).
- [ ] Files: NEW `apps/wc-web/src/features/dispute/disputesApi.ts`, `dispute/DisputePanel.tsx`, `dispute/DisputeRespondForm.tsx`, `dispute/DisputeResolveAction.tsx`, `features/comment/commentsApi.ts`, `comment/CommentList.tsx`, `comment/CommentForm.tsx`
- [ ] Cross-doc invariant: none (renders `AlignmentDisputeDto` B.8 + `CommentDto` B.9 + `DisputeStatus`/`FlagType`/`CommentTargetType` B.1 verbatim; flat-comments invariant per §11)
- [ ] Tests — happy: open/respond/resolve invoke the F.4 endpoints with correct bodies; respond requires one of icResponse/newSupportingOutcomeId; comments post + list flat; edge: open-dispute disabled when an unresolved dispute exists; comment body XSS probe rendered escaped (REQ-S-005); error: `SECOND_OPEN_DISPUTE` and `IC_CANNOT_RESOLVE_DISPUTE` render `safeMessage`; integration (cache-invalidation): resolving the last dispute invalidates `Manager`/`Heatmap`/`Plans` and refetches (review re-derivation reflected without optimistic write)
- [ ] Requirements: REQ-UX-002, REQ-UX-003, REQ-S-005, REQ-NF-004

### 9.12 — Sync slice: SyncStatusBadge + manual retry (FAILED warning path)
- [ ] `sync/syncApi.ts` exposes `GET /api/outlook-sync?planId=` (E22, plain bounded array, NOT paginated) returning `OutlookSyncRecordDto[]` (B.10) and `POST /api/outlook-sync/{syncRecordId}/retry` (E23, no body), tagged `Sync`.
- [ ] `sync/SyncStatusBadge.tsx` renders the `SyncStatus` value; a `FAILED` record renders a visible warning showing the record’s `safeMessage` as Cypress-assertable text (never tokens/secrets, §15/§16) and the IC weekly lifecycle remains usable (non-blocking, REQ-UX-004 / REQ-E-004 behavior).
- [ ] `sync/SyncRetryAction.tsx` is enabled only when `RETRY_SYNC ∈ record.allowedActions[]` (i.e. `status=FAILED` + actor owns/manages owner), invokes the E23 retry per F.4, and on success invalidates `Sync` so the badge refetches (transitions toward `RETRY_REQUESTED`/`QUEUED`); no optimistic flip.
- [ ] Files: NEW `apps/wc-web/src/features/sync/syncApi.ts`, `sync/SyncStatusBadge.tsx`, `sync/SyncRetryAction.tsx`
- [ ] Cross-doc invariant: none (renders `OutlookSyncRecordDto` B.10 + `SyncStatus` B.1 verbatim)
- [ ] Tests — happy: SYNCED renders success, FAILED renders warning + `safeMessage`; retry enabled only when `RETRY_SYNC` present; edge: non-FAILED record shows no retry control; error: retry on a non-FAILED record surfaces the server `409` `safeMessage`; integration (cache-invalidation): retry success invalidates `Sync` and refetches the badge (assert no optimistic state change; FAILED warning + retry proves the non-blocking path)
- [ ] Requirements: REQ-UX-004, REQ-E-004, REQ-NF-004

### 9.13 — PA host integration contract documentation
- [ ] Author a host integration contract doc (README section under `apps/wc-web`) covering the §7 / REQ-I-007 / REQ-I-013 surface: remote name, `remoteEntry.js`, exposed module `./WeeklyCommitApp`, the consumed router context, the host-injected `getAccessToken(): Promise<string>` accessor, host-overridable `VITE_API_BASE_URL`, and the shared singletons (React, React DOM, Redux Toolkit, React-Redux).
- [ ] Document that the contract uses a generic Vite remote pattern until the real PM remote pattern is verified (REQ-I-013, OQ-004), and that demo/persona concerns are standalone-only and absent from the remote build (REQ-I-008).
- [ ] Document the standalone-vs-remote auth boundary (`VITE_AUTH_MODE` demo↔auth0 XOR header rule) and confirm the remote does not duplicate PA shell-owned concerns (no hardcoded shell nav/global routing/LogRocket/Loki/Nx ownership, REQ-I-008).
- [ ] Files: NEW `apps/wc-web/README.md` (host integration contract section); extended `apps/wc-web/.env.example`
- [ ] Cross-doc invariant: none (documents §7 boundary; no model surface)
- [ ] Tests — happy: doc presence/lint check (markdown), and an assertion that documented shared singletons match `vite.config.ts`; integration: the documented `getAccessToken` signature matches `app/authAccessor.ts`
- [ ] Requirements: REQ-I-007, REQ-I-013, REQ-I-008

### Acceptance criteria (9)
- [ ] All 9.X task checkboxes ticked.
- [ ] All API access goes through RTK Query slices (me, rcdo, plans, commitments, review, disputes, manager, comments, sync); no Saga/Thunk; no optimistic updates; every mutation invalidates the correct cache tags (verified by Vitest cache-invalidation tests).
- [ ] `prepareHeaders` attaches exactly one of `Authorization: Bearer` (auth0) XOR `X-Demo-Employee-Id` (demo) per `VITE_AUTH_MODE`; the persona switcher + demo branch are provably absent from the exposed remote build (REQ-I-008).
- [ ] `WeeklyCommitApp` consumes the host router (no `BrowserRouter`/persona inside the remote); `standalone/main.tsx` owns router + store + identity + persona switcher; all five routes are lazy-loaded (REQ-NF-005).
- [ ] Every data view renders explicit loading/empty/error/success/partial states; `safeMessage` renders as Cypress-assertable text and never via `dangerouslySetInnerHTML`; XSS/Unicode probes render escaped (REQ-S-005).
- [ ] `allowedActions[]` drives all action enablement and each maps to its Appendix F.4 endpoint; IC UI has no team-heatmap entry point (REQ-UX-005); the Outlook FAILED warning + manual-retry affordance is visible and recoverable (REQ-UX-004/REQ-E-004).
- [ ] The PA host integration contract is documented (remote name, remoteEntry, exposed module, router context, `getAccessToken`, base URL, shared deps) per REQ-I-007/REQ-I-013/REQ-I-008.
- [ ] **Styling (Cadence design system — Phase ST fold-in, Fork-2 Option 2):** every component renders per `docs/design/cadence-design-system/` — tokens from the ST.1 Tailwind/Flowbite theme, status/risk/chess via the §4.2/§4.3 six-tone taxonomy (**glyph + text + color, never color alone**), density/elevation/motion per §4.4–4.7; **both `data-theme` values (dark default + light) render**; the standalone `ThemeToggle` is absent from the exposed remote build (REQ-I-008-style). Per-surface fidelity (ST.5/ST.6) folds into the 9.x component tasks; the cross-cutting atom/surface skin (ST.3/ST.4) + a11y/design-review (ST.7) live in **Phase ST**.

---

## Phase ST — Styling & theming (Cadence design system)

**Goal:** Apply the delivered **Cadence design system** (`docs/design/cadence-design-system/` — Linear-dark, six-tone semantic taxonomy, committed `2a307b8`) as the binding styling source of truth for `apps/wc-web`, via **approach A** (Tailwind/Flowbite-native: Cadence tokens → `tailwind.config` theme + a Flowbite-React custom theme; **no bespoke `.wc-*` CSS**). Dark is the default; a persisted light-mode toggle flips a `[data-theme]` attribute (standalone-only toggle, tree-shaken from the remote). **Sequencing = Fork-2 Option 2 (foundation-early, style-as-you-build; user-approved 2026-06-02):** ST.1+ST.2 land the token+theme foundation first; per-surface visual fidelity (ST.5/ST.6) folds into the Phase 9 component task ACs rather than running as separate late slices; ST.3/ST.4 (cross-cutting atom + surface skin) + ST.7 (a11y/design-review) are the distinct Phase ST spine. The color override is **render-only** — foundation (light→dark) + brand (`blue-600`→indigo `#5E6AD2`); the six-tone semantic taxonomy + every enum→tone mapping are preserved 1:1, so **no enum/Appendix-A cross-doc invariant changes** (`RiskBadge`/`AlignmentStatus`/`ReviewStatus` vocabularies untouched). Full rationale + reconciliation: `docs/planning/frontend-styling-proposal.md`.

**Spec anchors:** `ARCHITECTURE.md §7` (styling-SoT note); `docs/design/cadence-design-system/` (`colors_and_type.css` tokens, `components.css`, README VISUAL FOUNDATIONS, `atoms.jsx` enum→tone maps); `docs/design/UI_UX_SPEC.md §4` (taxonomy/density; §4.1 foundation superseded by Cadence); REQ-UX-002, REQ-S-005 (a11y / color-not-only-signal), REQ-NF-005 (no CSS bloat on initial render). **No safety invariant** — ST slices may bundle.

### ST.1 — Cadence token foundation + Tailwind/Flowbite-native bridge ✅ (`e3c1cb7`, 2026-06-02)
- [x] Cadence tokens (`colors_and_type.css`) ported as CSS custom properties in one token stylesheet (`src/styles/theme.css`, dark under `[data-theme="dark"]` default); `tailwind.config` `theme.extend` maps every semantic scale (colors surface/ink/border/brand/`tone-*`, spacing, radii, fontFamily/fontSize, boxShadow, transitionDuration/timing) to `var(--…)` (never hex — walk-every-leaf test).
- [x] Flowbite-React custom theme (`src/app/flowbiteTheme.ts`, `createTheme` + `<Flowbite theme={{ theme }}>`) skins badge/button/table/drawer/modal/tooltip via the token utilities; **no bespoke `.wc-*` component CSS** (approach A). _(Bundled into the 0.6+ST.1+ST.2 foundation slice `e3c1cb7`.)_

### ST.2 — Dark/light theming mechanism ✅ (`e3c1cb7`, 2026-06-02)
- [x] `[data-theme="light"]` token block authored net-new (light base re-contrasted for AA; **brand indigo `#5E6AD2` retained both themes** — Fork 3); `darkMode: ['selector','[data-theme="dark"]']` binds Flowbite's baked-in `dark:` to the attribute (not OS media).
- [x] `ThemeProvider` + `useThemePreference` (localStorage → `prefers-color-scheme` → dark; persisted; pre-paint FOUC guard; declarative `prefers-reduced-motion`); standalone-only `ThemeToggle` (tree-shaken from remote — **bundle-absence proof deferred to 9.3** with `PersonaSwitcher`).
- [x] Lessons banked: wc-web LESSONS §3 (CSS-vars→Tailwind-theme `[data-theme]` flip) + §4 (flowbite-react 0.10.2 `createTheme`/`<Flowbite>` + `darkMode` selector-binding); forbidden-pattern #3 narrow exception (token-var stylesheet only) in wc-web CLAUDE.md.

### ST.3 — Status / risk / chess atom skin (folds into 9.2/9.7)
- [ ] Skin `StatusBadge`, `RiskBadge`, `PriorityTag`, `WorkTypeTag`, `ConfidenceMeter` (3-segment bar, not number/stars), `AlignmentChip`, `SyncStatusBadge` per `UI_UX_SPEC §4.2/§4.3` + the Cadence `atoms.jsx` enum→tone+icon maps — six tones, **ring variant** for the 2nd of a same-color risk pair (`BLOCKED`, `CARRY_FORWARD`), pinned Heroicons via `react-icons/hi`; no game/chess glyphs.
- [ ] Cross-doc invariant: none (renders B.1 `RiskBadge`/`AlignmentStatus`/`ReviewStatus`/`SyncStatus`/`ReconciliationOutcome` vocab verbatim — render-only).
- [ ] **Folds into Phase 9.2** (the `StatusBadge`/`RiskBadge` components) — not a separate late slice.

### ST.4 — Surface / density / elevation / motion skin (folds across 9.7–9.11)
- [ ] Cards (flat, hairline border, 8px radius, 16px pad, **left-accent only where earned**: violet unplanned / red-amber disputed), tables (~44px rows, sticky header, striped/hoverable), drawers/modals (raised surface + shadow + scrim), focus ring (3px indigo, never removed), motion tokens (≤150/200ms, no bounce), solid backgrounds (no gradients/imagery on data surfaces) — all via the ST.1 token utilities.
- [ ] **Folds into the Phase 9 component slices** as each surface is built.

### ST.5 — IC workspace visual composition (folds into 9.7/9.8)
- [ ] `PlanLifecycleBar` 4-node forward-only stepper; `CommitmentCard` modes (draft/locked/reconciling/readOnly) + left-accent; `RcdoPicker`/`RcdoBreadcrumb`; non-blocking sync warning strip — per the Cadence `WeeklyPlanView`/`CommitmentCard`/`overlays` reference.

### ST.6 — Manager surfaces visual composition (folds into 9.9/9.10/9.11)
- [ ] `CommandCenter` dense table + at-a-glance SLA strip + filter chips + review Drawer; `HeatmapGrid` with **volume neutral-fill decoupled from risk badges** + a11y hatch/dot high-contrast pattern-mode toggle; drilldown Drawer; `DisputePanel` 3-step stepper — per the Cadence `CommandCenter`/`Heatmap` reference.

### ST.7 — A11y + responsive + design-review pass
- [ ] Color-never-the-only-signal (glyph + text + color, grayscale/colorblind legible) across all status/risk; heatmap high-contrast pattern mode; `focus-visible` rings; desktop-first responsive (`content-max` 1440 / `reading-col` 1040); `prefers-reduced-motion`; **formal AA-contrast verification of the net-new light status tones** (carried from ST.2).
- [ ] Run `/design-review` against `docs/design/cadence-design-system/preview/*.html` for visual-fidelity QA before the frontend phase exits.
- [ ] Requirements: REQ-UX-002, REQ-S-005.

### Acceptance criteria (ST)
- [ ] Cadence tokens are the single styling source of truth (`tailwind.config` + Flowbite theme reference them; no hardcoded hex; no bespoke `.wc-*` CSS beyond the one token-var stylesheet).
- [ ] Both `data-theme` values render every surface; the standalone `ThemeToggle` is absent from the exposed remote build (proven in 9.3); brand indigo is theme-stable.
- [ ] Every status/risk conveys meaning via glyph + text + color (never color alone); the heatmap a11y pattern-mode toggle works; `prefers-reduced-motion` respected.
- [ ] `/design-review` against the Cadence specimens passes; no enum/Appendix-A cross-doc invariant changed (render-only).

---

## Phase 10 — Seed data (deterministic demo + opt-in perf)

**Goal:** Deliver the deterministic, idempotent demo seed and the isolated opt-in performance seed for WC. Three ordered Flyway migrations (V4 RCDO, V5 personas+relationships, V6 fixture plans/state) run after the V1–V3 schema/index/projection DDL inside the sole pre-deploy migration Job, every row using fixed UUID literals and timestamps anchored to demo anchor date 2026-06-02 (current week 2026-06-01→06-07, prior week 2026-05-25→05-31, org tz America/Chicago), all `INSERT … ON CONFLICT (<unique key>) DO NOTHING` so a re-run is a deterministic no-op. The fixture state matrix spans every plan lifecycle state, the overdue/dispute/carry-forward/sync-failure cases, and seeds `manager_plan_summary` + `manager_heatmap_cell` projection rows as literals that the rebuild job must reproduce identically — exercising every Demo Success Signal including denied-manager-view. The ~2,000-row synthetic perf seed is a separate opt-in Job (`--app.job=generate-perf-seed` / Gradle `generatePerfSeed` / `job-perf-seed.yaml`) in an isolated `perf+<n>@st6demo.com` namespace that reuses the seeded RCDO and is never a deploy-chain migration.

**Spec anchors:** `ARCHITECTURE.md §4`, §12, §13, Appendix A, Appendix C (C.2/C.7), Appendix E (Part 2 + perf seed), Appendix F (F.6).

### 10.1 — V4__seed_rcdo.sql (read-only RCDO copy, 1/3/9)
- [ ] Seeds exactly 1 `rally_cry` ("Become the system of record every execution-driven team trusts by end of FY26."), 3 `defining_objective` rows (DO-1 Win customer adoption & expansion; DO-2 Operational excellence in delivery; DO-3 Platform reliability & trust), and 9 `supporting_outcome` rows (3 per DO) with the exact Appendix E titles (SO-1.1/1.2/1.3, SO-2.1/2.2/2.3, SO-3.1/3.2/3.3), `active=true`.
- [ ] All rows use fixed literal UUID constants; `defining_objective.rally_cry_id` and `supporting_outcome.defining_objective_id` reference the seeded FK rows by their literal UUIDs.
- [ ] Every `INSERT` uses `ON CONFLICT (<pk/unique key>) DO NOTHING`; re-running the migration against an already-seeded DB inserts zero new rows (idempotent no-op).
- [ ] No mutation path is added — RCDO stays read-only seeded reference data (REQ-D-003 invariant honored; no admin UI).
- [ ] Files: NEW `apps/wc-api/shared/src/main/resources/db/migration/V4__seed_rcdo.sql`.
- [ ] Cross-doc invariant: none NEW (consumes existing `RallyCry`/`DefiningObjective`/`SupportingOutcome` models, Appendix A — read-only seeded shape 1/3/9).
- [ ] Tests — happy: Testcontainers PG runs V1–V4, assert counts (1 RC, 3 DO, 9 SO) and the exact RC title + DO-1/2/3 titles; edge: re-apply V4 logic a second time (re-execute the INSERTs) yields no duplicate rows and stable counts; error: a malformed FK reference would fail migration (assert FK integrity — every SO maps to a seeded DO, every DO to the seeded RC); integration: `GET /api/rcdo` returns the seeded `RcdoTreeDto` with 1/3/9 nesting (REQ-D-004 shape) using the seeded UUIDs.
- [ ] Requirements: REQ-D-004, REQ-O-002.

### 10.2 — V5__seed_personas_and_relationships.sql (Dana + 6 reports + relationships)
- [ ] Seeds 7 `employee` rows with fixed literal UUIDs: Dana Okafor (`MANAGER`) + R1 Priya Raman, R2 Marco Bellini, R3 Aisha Khan, R4 Tomas Novak, R5 Grace Liu, R6 Sam Carter (all `IC`); all `email` `@st6demo.com` (unique), `timezone='America/Chicago'`, `active=true`, `external_subject` NULL.
- [ ] Seeds 6 `manager_relationship` rows `(manager=Dana, direct_report=Rn, active=true)`, one per report, each satisfying `unique(manager_employee_id, direct_report_employee_id)` and the partial-unique `(direct_report_employee_id) WHERE active=true` (single active manager per report).
- [ ] No relationship makes any report manage another report or makes anyone manage Dana — Dana's own plan is IC-only (relationship-driven authorization; "manager who also owns a plan" path). This is the negative-auth fixture for denied-manager-view.
- [ ] All `INSERT … ON CONFLICT DO NOTHING`; second application is a no-op; demo-mode `X-Demo-Employee-Id` = the employee `id` (relies on these fixed UUIDs).
- [ ] Files: NEW `apps/wc-api/shared/src/main/resources/db/migration/V5__seed_personas_and_relationships.sql`.
- [ ] Cross-doc invariant: none NEW (consumes `Employee` role {IC,MANAGER} + `ManagerRelationship` single-active-manager partial-unique, Appendix A).
- [ ] Tests — happy: after V5, assert 7 employees (1 MANAGER + 6 IC) and 6 relationships all `(Dana, Rn, active)`; edge: re-apply V5 → no duplicate employees/relationships; error: a second active relationship for any report violates the partial-unique index (assert the invariant is enforced by the schema, demonstrating the fixture is provably single-active-manager); integration: in demo mode (`DEMO_AUTH_ENABLED=true`), `X-Demo-Employee-Id=<R1 uuid>` resolves to Priya; an R1 request for Dana's command center / a non-direct-report resource is denied `403/404` (REQ-E-002 denied-manager-view; no relationship exists).
- [ ] Requirements: REQ-D-005, REQ-E-002, REQ-O-002.

### 10.3 — V6__seed_fixture_plans_and_state.sql (lifecycle state matrix: plans, commitments, reviews, disputes)
- [ ] Seeds current-week (`2026-06-01`) plans realizing the Appendix E state matrix: R1 LOCKED (3 planned, all linked SO-1.1/SO-2.3/SO-3.2); R2 LOCKED with overdue review; R3 LOCKED with 1 OPEN dispute; R4 LOCKED with 1 RESOLVED dispute; R5 RECONCILING (partial outcomes + 1 UNPLANNED linked SO-2.2); R6 DRAFT (2 planned, 1 deliberately UNLINKED `supporting_outcome_id` NULL); Dana RECONCILED self-plan with all outcomes set. All `weekly_plan` rows satisfy `unique(employee_id, week_start_date)` with `week_end_date` = Sunday.
- [ ] Seeds `manager_review` rows for the locked plans: R1 `NOT_REVIEWED` `review_due_at='2026-06-02 17:00 CT'` (not overdue at anchor); R2 `NOT_REVIEWED` `review_due_at='2026-05-29 17:00 CT'` (derived OVERDUE at anchor); R3 `REVIEWED_WITH_DISPUTES`; R4 `REVIEWED`; R5 `REVIEWED`. No stored `OVERDUE` — overdue is purely derived from `review_due_at` + `NOT_REVIEWED` (§3). Dana has no review row (no manager).
- [ ] Seeds `alignment_dispute` rows: R3 one `OPEN`, `flag_type=MISALIGNED`, `manager_note` set, `ic_response` NULL, on a commitment with `alignment_status=NEEDS_REVIEW` (satisfies partial-unique one-open-per-commitment); R4 one full lifecycle `OPEN→IC_RESPONDED→RESOLVED` with `resolved_at` set and `ic_response` populated.
- [ ] All commitment/plan/review/dispute rows use fixed literal UUIDs + fixed timestamps anchored to 2026-06-02; locked plans' planned commitments represent a frozen baseline (lifecycle timestamps consistent: `locked_at`, `reconciliation_started_at`, `reconciled_at` set as the state requires). Every `INSERT … ON CONFLICT DO NOTHING`; re-application is a no-op.
- [ ] Files: extended `apps/wc-api/shared/src/main/resources/db/migration/V6__seed_fixture_plans_and_state.sql` (single file; this task seeds the plans/commitments/reviews/disputes portion — sync records 10.4, carry-forward 10.5, and projections 10.6 are the remaining portions of the same V6 file, authored as one ordered migration).
- [ ] Cross-doc invariant: none NEW (consumes `WeeklyPlan` state {DRAFT,LOCKED,RECONCILING,RECONCILED}, `WeeklyCommitment` commitment_kind/alignment_status/reconciliation_outcome, `ManagerReview` status {NOT_REVIEWED,REVIEWED_WITH_DISPUTES,REVIEWED} no stored OVERDUE, `AlignmentDispute` status {OPEN,IC_RESPONDED,RESOLVED} — all Appendix A).
- [ ] Tests — happy: after V6, assert one plan per report in the matrix's state, R1 has 3 linked planned commitments, R6 has exactly one planned commitment with NULL `supporting_outcome_id`; edge: re-apply V6 → no duplicate plans/commitments/reviews/disputes; error: with an injectable `Clock` pinned to anchor 2026-06-02 morning, R2 review derives `isOverdue=true` while R1 derives `isOverdue=false`, and R3 `REVIEWED_WITH_DISPUTES` derives `isOverdue=false` (SLA satisfied) — proving no stored OVERDUE; integration: a manager command-center read by Dana shows R6 DRAFT as not-lockable and R3's unresolved-dispute count surfaces as alignment risk distinct from R2's overdue badge.
- [ ] Requirements: REQ-D-005, REQ-O-002.

### 10.4 — V6 sync records: seeded FAILED IC_PLANNING + SYNCED + MANAGER_REVIEW_BLOCK
- [ ] Seeds for R2 Marco one `outlook_calendar_sync_record`: `event_kind='IC_PLANNING'`, `related_type='WEEKLY_PLAN'`, `related_id`=R2's plan id, `status='FAILED'`, `failure_code='GRAPH_FORBIDDEN'`, `safe_message='Calendar sync failed; you can retry.'`, `retry_count=1`, `graph_event_id` NULL, `week_start_date='2026-06-01'` — contains no token/secret detail (§15/§16).
- [ ] Seeds for R1 one `IC_PLANNING` `WEEKLY_PLAN` sync record with `status='SYNCED'` (and a `graph_event_id` literal) as the success contrast.
- [ ] Seeds one `MANAGER_REVIEW_BLOCK` record owned by Dana: `owner_employee_id`=Dana, `related_type='MANAGER_REVIEW_WEEK'`, `related_id`=Dana's employee id, `week_start_date='2026-06-01'`, `status='SYNCED'` — satisfying the partial-unique `(owner_employee_id, week_start_date) WHERE event_kind='MANAGER_REVIEW_BLOCK'` (one-event-per-manager/week grain) AND the base `unique(owner, related_type, related_id, event_kind)`.
- [ ] All sync rows use fixed literal UUIDs + `ON CONFLICT DO NOTHING`; re-application is a no-op.
- [ ] Files: extended `apps/wc-api/shared/src/main/resources/db/migration/V6__seed_fixture_plans_and_state.sql` (sync-record portion of the same migration).
- [ ] Cross-doc invariant: none NEW (consumes `OutlookCalendarSyncRecord` related_type/event_kind/status enums + dual uniqueness incl. per-manager/week partial-unique + `week_start_date`, Appendix A).
- [ ] Tests — happy: after V6, R2 has exactly one `FAILED` `IC_PLANNING` record with the safe message and NULL `graph_event_id`; Dana has exactly one `MANAGER_REVIEW_BLOCK` for week `2026-06-01`; edge: re-apply V6 → no duplicate sync rows (both uniqueness keys hold); error: attempting to seed a second `MANAGER_REVIEW_BLOCK` for Dana same week is rejected by the partial-unique index (assert grain enforcement); integration: `GET /api/outlook-sync?planId=<R2 plan>` (as R2) returns the FAILED record with `allowedActions` including `RETRY_SYNC`, and `POST /api/outlook-sync/{id}/retry` transitions FAILED→RETRY_REQUESTED (REQ-E-004 demoable from seed).
- [ ] Requirements: REQ-D-005, REQ-O-002.

### 10.5 — V6 carry-forward chain across prior + current week (R5 Grace)
- [ ] Seeds R5's prior-week plan `2026-05-25` as `RECONCILED` containing planned commitment `C_src` (title "Draft the activation-onboarding runbook", linked SO-1.2) with `reconciliation_outcome='CARRIED_FORWARD'` (single-outcome rule: CARRIED_FORWARD is the sole outcome, not combined with a completion outcome).
- [ ] Seeds R5's current-week `2026-06-01` RECONCILING plan with successor commitment `C_next`: `commitment_kind='PLANNED'`, `carry_forward_source_commitment_id = C_src.id` (self-link back to source), linked to SO-1.2.
- [ ] The prior-week `C_src` baseline is unchanged by the existence of `C_next` (proves carry-forward links back to source and locked prior-week baseline immutability holds).
- [ ] Fixed literal UUIDs + anchored timestamps; `ON CONFLICT DO NOTHING`; re-application is a no-op.
- [ ] Files: extended `apps/wc-api/shared/src/main/resources/db/migration/V6__seed_fixture_plans_and_state.sql` (carry-forward portion of the same migration).
- [ ] Cross-doc invariant: none NEW (consumes `WeeklyCommitment.carry_forward_source_commitment_id` self-link + `reconciliation_outcome=CARRIED_FORWARD` single-outcome rule, Appendix A).
- [ ] Tests — happy: after V6, `C_next.carry_forward_source_commitment_id == C_src.id` and both link SO-1.2; assert R5 prior-week plan is `RECONCILED` and current-week is `RECONCILING`; edge: re-apply V6 → no duplicate C_src/C_next; error: assert `C_src.reconciliation_outcome` is exactly `CARRIED_FORWARD` (not a completion outcome) — single-outcome rule holds; integration: reading R5's current plan shows `C_next` carrying forward with `carryForwardSourceCommitmentId` populated while the prior-week locked baseline reads unchanged (REQ-D-006, REQ-E-005).
- [ ] Requirements: REQ-D-005, REQ-D-006, REQ-E-005, REQ-O-002.

### 10.6 — V6 projection rows: heatmap cells (3 DOs, varied risk_badges) + plan summaries
- [ ] Seeds `manager_heatmap_cell` rows for Dana spanning all 3 Defining Objectives (manager × report × week `2026-06-01` × DO), with `risk_badges text[]` drawn only from the enumerated vocabulary and matching the fixtures: `MISALIGNED` + `NEEDS_REVIEW` (R3), `OVERDUE_REVIEW` + `UNREVIEWED` (R2), `BLOCKED` (R5), `CARRY_FORWARD` (R5); counts (commitment/planned/unplanned/misaligned/needs_review/blocked/carry_forward/unresolved_dispute) match the source commitment fixtures.
- [ ] Seeds `manager_plan_summary` rows (one per Dana × report × week) mirroring those counts plus `plan_state`, `review_status`, `review_due_at`, `is_review_overdue` (R2 true, R1/R3 false), `unresolved_dispute_count` (R3=1, others 0) — `misaligned_count` honoring `alignment_status=MISALIGNED` OR open dispute `flag_type=MISALIGNED` (R3).
- [ ] All projection rows use fixed literal UUIDs satisfying `unique(manager,employee,week_start_date)` (summary) and `unique(manager,employee,week_start_date,defining_objective_id)` (heatmap); `ON CONFLICT DO NOTHING`; the seeded literals must equal what the projection rebuild job would recompute from source (rebuild==seed equivalence is the cross-check, REQ-D-013 owned elsewhere).
- [ ] Files: extended `apps/wc-api/shared/src/main/resources/db/migration/V6__seed_fixture_plans_and_state.sql` (projection portion of the same migration).
- [ ] Cross-doc invariant: none NEW (consumes `ManagerPlanSummary` + `ManagerHeatmapCell` projections incl. enumerated `risk_badges` {MISALIGNED,NEEDS_REVIEW,BLOCKED,CARRY_FORWARD,UNREVIEWED,OVERDUE_REVIEW} and pinned count derivation §9, Appendix A).
- [ ] Tests — happy: after V6, Dana's heatmap cells cover all 3 DOs and the badge set matches the table exactly; `manager_plan_summary` R2 `is_review_overdue=true`, R3 `unresolved_dispute_count=1`; edge: re-apply V6 → no duplicate projection rows (both unique keys hold); error: assert every value in any `risk_badges[]` is a member of the enumerated `RiskBadge` vocabulary (no free-form badge); integration: `GET /api/manager/heatmap?weekStart=2026-06-01` (as Dana) returns cells with counts + risk badges and a drill-down on a Dana-owned cell succeeds while a drill-down on a non-Dana cell returns `404` (REQ-E-003 heatmap with counts/badges/drilldown; REQ-E-002 denied cell).
- [ ] Requirements: REQ-D-005, REQ-E-002, REQ-E-003, REQ-O-002.

### 10.7 — Opt-in synthetic perf seed (~2,000 projection rows, isolated namespace)
- [ ] Adds an opt-in generation entrypoint gated on `--app.job=generate-perf-seed` (the established `app.job` switch pattern; NOT a Flyway versioned migration in the deploy chain), runnable as Gradle task `generatePerfSeed` and k8s Job `job-perf-seed.yaml`, distinct from the `flyway-migrate` Job.
- [ ] Generates one synthetic manager with enough direct reports × weeks × commitments to reach ~2,000 `manager_plan_summary`/`manager_heatmap_cell` projection rows (e.g. ~40 reports × ~10 weeks × 5 commitments fanned across the 3 Defining Objectives), targeting the §14 p95<200ms measurement for `GET /plans/current` and `GET /api/manager/command-center`.
- [ ] Reuses the same seeded read-only RCDO (DO/SO) — does not duplicate strategy rows; all synthetic employees use the isolated `perf+<n>@st6demo.com` email namespace so they never collide with the human-readable demo seed.
- [ ] Deterministic via a fixed RNG seed and UUIDs derived from a fixed namespace so reruns are idempotent (`ON CONFLICT DO NOTHING`); teardown is a scoped `DELETE` by the `perf+...@st6demo.com` namespace only.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/job/PerfSeedGenerationRunner.java` (ApplicationRunner gated on `--app.job=generate-perf-seed`, following the `job/PlanShellGenerationRunner.java` pattern — see archGapsFlagged); NEW `infra/k8s/job-perf-seed.yaml`; extended `apps/wc-api/api/build.gradle` (`generatePerfSeed` Gradle task).
- [ ] Cross-doc invariant: none NEW (writes `ManagerPlanSummary`/`ManagerHeatmapCell` projections + `Employee`/`WeeklyPlan`/`WeeklyCommitment` reusing existing models; isolated namespace).
- [ ] Tests — happy: run `generate-perf-seed` against Testcontainers PG, assert ~2,000 combined projection rows under the `perf+...` namespace and that the seeded RCDO row counts (1/3/9) are unchanged (no strategy duplication); edge: run twice with the same fixed RNG seed → idempotent, no duplicate rows and identical row count; error: scoped teardown `DELETE` removes only `perf+...@st6demo.com` employees and their plans/commitments/projections, leaving the human-readable demo seed (Dana + R1–R6) intact; integration: with perf seed loaded, `GET /api/manager/command-center` for the synthetic manager paginates (B.20 envelope) and the §14 timing harness records a p95 number against the 2,000-row dataset.
- [ ] Requirements: REQ-D-008, REQ-O-002.

### Acceptance criteria (10)
- [ ] All 10.X task checkboxes ticked.
- [ ] V4→V5→V6 apply cleanly in order after V1–V3 inside the migration Job, are individually idempotent (full re-run inserts zero rows), and use only fixed UUID literals + timestamps anchored to 2026-06-02 (no `now()`/random).
- [ ] The seeded fixture exercises every Demo Success Signal: IC linked-lock + baseline freeze (R1), overdue review distinct from dispute risk (R2 vs R3), manager flags misalignment / REVIEWED_WITH_DISPUTES satisfies SLA (R3), IC-responds-cannot-resolve / manager-resolves loop persisted (R4), reconcile outcomes + unplanned + carry-forward leaving baseline unchanged (R5), lock-blocked-on-unlinked (R6), closed-lifecycle exemplar (Dana), seeded Outlook FAILED + retry affordance, heatmap counts/badges/drilldown across 3 DOs, and denied-manager-view (REQ-E-002 — no cross-report relationship exists).
- [ ] The perf seed is provably separate from the deploy chain (no `V*` migration), isolated to `perf+<n>@st6demo.com`, reuses the seeded RCDO, is idempotent, and is teardown-scoped.
- [ ] Seeded projection rows (10.6) equal what a rebuild from source would compute (rebuild==seed cross-check, supports REQ-D-013 owned elsewhere).

---

## Phase 11 — Testing, BDD acceptance & quality gates

**Goal:** Deliver the cross-cutting test infrastructure, the measured acceptance suite, and the CI quality gates that prove the WC correctness spine end-to-end. Per-task unit/integration tests already live in each prior phase's task scenarios; this phase delivers the *shared* harness (Testcontainers PostgreSQL base, deterministic seed-reset, injectable `Clock`), the cross-cutting integration suites that cannot live inside one slice (the full IDOR denial matrix, projection trigger/rollback/rebuild-equality, idempotent-retry, CronJob generation idempotency, the security suite), the 7 Cypress+Cucumber BDD feature files mapped 1:1 to REQ IDs, the Micrometer/JUnit perf harness recording p95<200ms into the test-results artifact, and the CI pipeline that enforces JaCoCo ≥80%/module + Spotless + SpotBugs + ESLint 9 + Prettier 3.3 + Vitest with a local-full-suite / deployed-smoke split. All tests must be deterministic (no wall-clock, no H2, fixed seed data).

**Spec anchors:** `ARCHITECTURE.md §17`, §14, §16, §13, §9, §3, §6, §10, Appendix E (validation + deterministic seed), Appendix F (SLA clock, perf-seed names, ports), Appendix B (error codes / DTOs), Appendix C.2/C.3/C.5/C.7.

### 11.1 — Testcontainers PostgreSQL integration base + deterministic seed-reset harness
- [ ] Provide a shared Testcontainers PostgreSQL **major 16** base (Spring Boot test slice or `@DynamicPropertySource`) that every backend integration test extends; **H2 is forbidden** — no H2 dependency on any classpath (REQ-T-017).
- [ ] The base runs the real Flyway migrations V1–V6 against the container so integration tests exercise the production schema, constraints, partial-unique indexes, and the deterministic demo seed (Appendix E Part 2; fixed UUIDs, anchor date 2026-06-02).
- [ ] Container is reused across the suite (singleton/static) for speed; a per-test seed-reset helper restores the deterministic fixture state between tests so ordering is irrelevant.
- [ ] Docker-in-CI is wired so the base works in GitHub Actions (REQ-T-013/§13).
- [ ] Files: NEW `apps/wc-api/api/src/test/java/com/st6/wc/support/PostgresIntegrationTestBase.java`, NEW `apps/wc-api/api/src/test/java/com/st6/wc/support/SeedResetSupport.java`, NEW `apps/wc-api/worker/src/test/java/com/st6/wc/worker/support/PostgresWorkerTestBase.java`; extended `apps/wc-api/build.gradle` (testcontainers postgresql + junit-jupiter deps, exclude H2).
- [ ] Cross-doc invariant: none (test infra; consumes the existing schema/seed contract).
- [ ] Tests — happy: base starts a PG16 container, Flyway V1–V6 applied, `SELECT` on seeded RallyCry/personas returns the fixed rows; edge: suite reuses one container across ≥2 test classes; error: assert no H2 driver resolvable on the test classpath (dependency-absence guard); integration: a trivial repository test passes only against the container.
- [ ] Requirements: REQ-T-017, REQ-T-006.

### 11.2 — Injectable-Clock SLA / derived-overdue test suite
- [ ] Drive the SLA/overdue derivation through the injectable `java.time.Clock` (§3/§17, Appendix C.2 `config/ClockConfig.java`) so no test reads wall time; org tz fixed to `America/Chicago` (Appendix E/F.3).
- [ ] Assert `reviewDueAt` = 17:00 org-tz on the **next business day** after `lockedAt`, weekdays-only: a Friday lock → due the following Monday; a mid-week lock → due next weekday (Appendix F.3).
- [ ] Assert derived `isOverdue`: `NOT_REVIEWED` + `now > reviewDueAt` ⇒ true; `now ≤ reviewDueAt` ⇒ false; `REVIEWED_WITH_DISPUTES` + past-due ⇒ **false** (SLA satisfied, REQ-F-013); `REVIEWED` ⇒ never overdue.
- [ ] Assert the projection `is_review_overdue` mirror matches the DTO-derived `isOverdue` for the same clock instant.
- [ ] Files: NEW `apps/wc-api/api/src/test/java/com/st6/wc/review/ReviewSlaClockTest.java`, NEW `apps/wc-api/api/src/test/java/com/st6/wc/review/ReviewOverdueDerivationTest.java`; uses `review/ReviewSlaService.java`, `review/ReviewStatusDeriver.java`.
- [ ] Cross-doc invariant: none new (asserts ManagerReview / ManagerPlanSummary invariants from Appendix A — `isOverdue` derived, never stored).
- [ ] Tests — happy: weekday lock → due next weekday 17:00 CT; edge: Friday/Saturday/Sunday lock all → Monday due; weekend straddle for overdue computation; error: stored `OVERDUE` status must never appear (assert enum has no OVERDUE value); integration: lock via `PlanLifecycleService` with a fixed `Clock`, read `ManagerReviewDto.isOverdue`/`reviewDueAt`.
- [ ] Requirements: REQ-T-004.

### 11.3 — IDOR / authorization-denial integration matrix
- [ ] One parameterized integration suite covering **every §6 denial case**, each asserting the correct status (`403` authorization-denial / `404` not-found-or-not-authorized IDOR-safe per §5/Appendix B.0) **and** that an authorization-denial `audit_event` row is written (§15/§6).
- [ ] Cases (one per §6 enumerated denial): IC reads/mutates another IC's plan (`404`); manager touches a non-direct-report plan/review/dispute/comment (`404`); manager opens a heatmap **drill-down cell not their own** (`404`, E15); IC resolves a dispute (`403 IC_CANNOT_RESOLVE_DISPUTE`, E19); IC accesses the team heatmap/command-center (`403`); comment on an unauthorized/nonexistent `targetId` (`404`, target-id IDOR, E21); sync-retry on an unowned record (`404`/`403`, E23); demo header when `DEMO_AUTH_ENABLED=false` (`403` — see 11.6).
- [ ] Each case asserts the response is `application/problem+json` and **never reveals existence** of the foreign resource.
- [ ] Uses the negative-auth fixture (Appendix E: no report manages another, so cross-report access is denied) plus the seeded personas.
- [ ] Files: NEW `apps/wc-api/api/src/test/java/com/st6/wc/auth/IdorAuthorizationMatrixIT.java`, NEW `apps/wc-api/api/src/test/java/com/st6/wc/auth/AuthorizationDenialAuditIT.java`; exercises `auth/DomainAuthorizationService.java`, `auth/AuthorizationDeniedAuditer.java`.
- [ ] Cross-doc invariant: none new (asserts AuditEvent + the §6 authorization model).
- [ ] Tests — happy: each authorized actor still succeeds (control case per denial); edge: drill-down + sync-retry scoping (single-resource row-level authz, not just list filtering); error: each denial returns the exact `403/404` + writes exactly one denial `audit_event`; integration: full matrix runs against the Testcontainers base (11.1) with the seeded relationships.
- [ ] Requirements: REQ-T-007, REQ-S-001, REQ-S-002, REQ-S-003.

### 11.4 — Projection trigger / rollback / rebuild==incremental integration suite
- [ ] Assert synchronous projection deltas: each of commitment create/update/delete, plan lock, start/close-reconciliation, dispute open/respond/resolve, mark-reviewed, and carry-forward updates `manager_plan_summary` + `manager_heatmap_cell` **in the same transaction** (§9), with `misaligned_count` = `alignment_status=MISALIGNED` OR open dispute `flag_type=MISALIGNED`, and `is_review_overdue=false` while `REVIEWED_WITH_DISPUTES` (RISK-014).
- [ ] Assert **transactional rollback**: when the core mutation's transaction is forced to roll back, the projection rows are not partially updated (no drift) — RISK-003.
- [ ] Assert **rebuild == incremental**: invoke `ProjectionRebuildRunner` (REQ-D-013, internal job/CLI, no UI) on the seeded DB and assert the recomputed `manager_plan_summary`/`manager_heatmap_cell` rows are **byte-for-byte equal** to the incrementally-maintained rows (including `risk_badges[]` from the enumerated vocabulary).
- [ ] Assert heatmap aggregation vs the seeded cases: counts and `risk_badges` match the Appendix E fixture (MISALIGNED/OVERDUE_REVIEW/UNREVIEWED/BLOCKED/CARRY_FORWARD/NEEDS_REVIEW) — REQ-T-008/RISK-014.
- [ ] Files: NEW `apps/wc-api/api/src/test/java/com/st6/wc/projection/ProjectionTriggerDeltaIT.java`, NEW `apps/wc-api/api/src/test/java/com/st6/wc/projection/ProjectionRollbackIT.java`, NEW `apps/wc-api/api/src/test/java/com/st6/wc/projection/ProjectionRebuildEqualityIT.java`, NEW `apps/wc-api/api/src/test/java/com/st6/wc/projection/HeatmapAggregationIT.java`.
- [ ] Cross-doc invariant: none new (asserts ManagerPlanSummary + ManagerHeatmapCell invariants, Appendix A).
- [ ] Tests — happy: each mutation produces the expected count/badge delta; edge: `REVIEWED_WITH_DISPUTES` keeps `is_review_overdue=false` while `unresolved_dispute_count>0`; error: forced rollback leaves projections unchanged; integration: rebuild over the full seed equals incremental for all six reports + Dana.
- [ ] Requirements: REQ-T-008, REQ-D-013, REQ-NF-003.

### 11.5 — Idempotency suites: async worker, idempotent-retry, CronJob generation
- [ ] **Async worker handler test:** invoke the consume logic with a `SyncJobPointer` (`{syncRecordId, eventKind, env, traceId}`, Appendix F.2) + the demo adapter and assert the §10 transitions (`QUEUED→SYNCING→SYNCED` with `graph_event_id`; failure → `FAILED` + `failure_code`/`safe_message`/`retry_count++`) and the **redelivery guard** (`SYNCED`/active-`SYNCING` ⇒ no-op).
- [ ] **Idempotent-retry test:** demo adapter exposes create-vs-update; first sync creates and records `graph_event_id`; a retry (`FAILED→RETRY_REQUESTED→QUEUED`, single SNS publish path) **reuses** `graph_event_id` to **update**, produces **zero duplicate** Graph events, and increments `retry_count` (REQ-T-009, §10).
- [ ] **CronJob generation idempotency test:** run `PlanShellGenerationRunner` (`--app.job=generate-plan-shells`, SYSTEM principal) → one DRAFT shell per active employee per Monday–Sunday week; **rerun produces no duplicates** (via `unique(employee_id, week_start_date)`); generation creates **zero** Outlook sync records (REQ-I-002); writes audit under SYSTEM actor.
- [ ] Files: NEW `apps/wc-api/worker/src/test/java/com/st6/wc/worker/SyncWorkerHandlerIT.java`, NEW `apps/wc-api/worker/src/test/java/com/st6/wc/worker/IdempotentRetryIT.java`, NEW `apps/wc-api/api/src/test/java/com/st6/wc/job/PlanShellGenerationIdempotencyIT.java`; uses `graph/DemoSuccessGraphAdapter.java`, `graph/DemoFailureGraphAdapter.java`, `worker/service/WorkerSyncRecordService.java`.
- [ ] Cross-doc invariant: none new (asserts OutlookCalendarSyncRecord state machine + WeeklyPlan generation invariants, Appendix A).
- [ ] Tests — happy: pointer → `SYNCED` + graphEventId; edge: redelivery of a `SYNCED` row is a no-op; CronJob second run = no-dup; error: demo-failure adapter → `FAILED` + safe_message, retry reuses id with no duplicate; integration: generation run over seeded active employees creates exactly N shells + 0 sync records.
- [ ] Requirements: REQ-T-009, REQ-I-002.

### 11.6 — Security test suite (demo-header gate, JWT audience, input validation/escaping, safe Graph failure)
- [ ] **Demo-header rejection:** with `DEMO_AUTH_ENABLED=false`, a request carrying `X-Demo-Employee-Id` is rejected `403` and writes a demo-header-rejection `audit_event` (REQ-S-008, §6/§15); with `=true` the same header authenticates.
- [ ] **Wrong-audience JWT rejected:** a token whose `aud` does not match `auth0.audience` is rejected by the custom `AudienceValidator` (§6); also assert `alg=none`/symmetric and expired (`exp`) tokens are rejected (RS256 + issuer+audience mandatory).
- [ ] **Input validation / escaping (REQ-S-005):** server-side caps and rejection on commitment title (≥1 cp after trim, ≤255), description/outcome_note/manager_alignment_note/manager_note/ic_response/comment.body (≤4000 cp), enum-typed inputs reject unknown values with `400 VALIDATION_ERROR` (never 500); the canonical XSS probe `<img src=x onerror=alert(1)>` and the emoji+RTL probe `🚩مرحبا` are **stored verbatim** (Appendix E Part 1 rules 4–5).
- [ ] **Safe Graph failure:** a Graph failure surfaces only `safe_message` / `failure_code` on the sync record and in logs — assert **no** token/secret/PII substring appears (REQ-S-004, RISK-016, §16).
- [ ] Files: NEW `apps/wc-api/api/src/test/java/com/st6/wc/security/DemoHeaderGateIT.java`, NEW `apps/wc-api/api/src/test/java/com/st6/wc/security/JwtAudienceValidationIT.java`, NEW `apps/wc-api/api/src/test/java/com/st6/wc/security/InputValidationEscapingIT.java`, NEW `apps/wc-api/worker/src/test/java/com/st6/wc/worker/SafeGraphFailureIT.java`; exercises `config/DemoAuthFilter.java`, `config/AudienceValidator.java`, request DTO validators, `web/ProblemDetailsExceptionHandler.java`.
- [ ] Cross-doc invariant: none new (asserts §6 identity + Appendix E validation contract + AuditEvent safe-metadata rule).
- [ ] Tests — happy: valid token (correct aud/iss/RS256) + enabled demo header authenticate; edge: probe strings persist verbatim, max-length boundary (255/4000 cp counted in code points after trim); error: wrong-aud/none/expired JWT → 401/403, disabled demo header → 403 + audit, over-cap/blank-required → 400 `VALIDATION_ERROR` with `fieldErrors[]`, Graph failure body contains no secret; integration: all run against the Testcontainers base.
- [ ] Requirements: REQ-S-005, REQ-S-008, REQ-S-004, REQ-S-007, REQ-S-006.

### 11.7 — Cypress + Cucumber BDD acceptance suite (7 features, 1:1 to REQ)
- [ ] Author the 7 Gherkin feature files in `apps/wc-e2e` (Appendix C.5), each mapped 1:1 to its REQ ID, with TypeScript step definitions; persona login via `X-Demo-Employee-Id` and seed-reset helpers (`commands.ts`); assert `safeMessage` text from RFC-7807 as Cypress-visible error text.
- [ ] `ic-lock-blocked-unlinked.feature` (REQ-E-001/T-010): lock blocked on an unlinked planned commitment, surfaces the `UNLINKED_PLANNED_COMMITMENT` safe message (uses seeded R6 Sam DRAFT with 1 unlinked).
- [ ] `ic-lock-success.feature` (REQ-F-007): IC links all planned + locks; baseline freezes; review-due date appears.
- [ ] `ic-reconcile-carry-forward.feature` (REQ-E-005/F-028): record outcomes, add unplanned, carry-forward; **locked baseline unchanged** (uses seeded R5 Grace carry-forward chain).
- [ ] `manager-dispute-loop.feature` (REQ-T-011/F-017): manager flag → IC respond (revise SO or rationale) → **IC-cannot-resolve** (`IC_CANNOT_RESOLVE_DISPUTE`) → manager resolve → review re-derives `REVIEWED_WITH_DISPUTES→REVIEWED`.
- [ ] `manager-heatmap-drilldown.feature` (REQ-E-003/F-022): open command center → heatmap → drill into own cell → Supporting-Outcome breakdown.
- [ ] `outlook-failed-retry.feature` (REQ-E-004/I-005): FAILED warning shown (seeded R2 Marco FAILED sync) + manual retry; core workflow stays successful (non-blocking).
- [ ] `unauthorized-manager-denial.feature` (REQ-S-002/E-002): a non-direct-report manager view is denied (persona switcher shows allowed vs denied).
- [ ] Keep scenarios focused on core acceptance flows against local Compose services to bound CI brittleness (RISK-013).
- [ ] Files: NEW `apps/wc-e2e/cypress/features/{ic-lock-blocked-unlinked,ic-lock-success,ic-reconcile-carry-forward,manager-dispute-loop,manager-heatmap-drilldown,outlook-failed-retry,unauthorized-manager-denial}.feature`, NEW `apps/wc-e2e/cypress/support/step_definitions/{ic_plan,reconcile,dispute,heatmap,sync,authz}.steps.ts`, NEW `apps/wc-e2e/cypress/support/{commands.ts,e2e.ts}`, NEW `apps/wc-e2e/cypress.config.ts`.
- [ ] Cross-doc invariant: none new (asserts lifecycle/dispute/sync invariants + named error codes from Appendix A/B).
- [ ] Tests — happy: each of the 7 flows completes green against Compose; edge: dispute loop includes the IC-response leg + IC-cannot-resolve; error: unlinked-lock + unauthorized-manager assert the exact safe message / denial; integration: full suite runs against the local Compose stack (in-process worker, demo Graph adapters).
- [ ] Requirements: REQ-T-010, REQ-T-011, REQ-T-015.

### 11.8 — Performance harness (p95 < 200ms, recorded artifact)
- [ ] Provide a perf harness measuring **p95 server-side latency** for `GET /api/plans/current` and `GET /api/manager/command-center` — **warm-JVM, single-client**, against the ~2,000-row synthetic perf seed (REQ-D-008; `--app.job=generate-perf-seed` / Gradle `generatePerfSeed`, isolated `perf+<n>@st6demo.com` namespace, Appendix F.6).
- [ ] Measurement via a Micrometer/Actuator timer **or** a JUnit+Testcontainers timing harness; assert **p95 < 200ms** as a defined pass/fail (turns REQ-T-012/NF-001 into a gate, §14).
- [ ] Assert the manager view is **paginated** (`Pageable` envelope, B.20) and not loading all rows client-side (REQ-NF-002), and that the heatmap aggregation is backend-owned/indexed (no N+1, REQ-NF-003).
- [ ] **Record the measured numbers in the test-results artifact** (§14/§18) so they appear in deliverables.
- [ ] Files: NEW `apps/wc-api/api/src/test/java/com/st6/wc/perf/CurrentPlanLatencyPerfTest.java`, NEW `apps/wc-api/api/src/test/java/com/st6/wc/perf/CommandCenterLatencyPerfTest.java`, NEW `apps/wc-api/api/src/test/java/com/st6/wc/perf/PerfSeedSupport.java`; extended k8s `infra/k8s/job-perf-seed.yaml` reference for the deployed/opt-in path.
- [ ] Cross-doc invariant: none (perf measurement against existing read-model + pagination contract).
- [ ] Tests — happy: warm-run p95 under 200ms for both endpoints at 2,000-row scale; edge: pagination caps `size` at 100 and returns the B.20 envelope; error: a perf run exceeding 200ms fails the assertion; integration: perf seed generated deterministically (fixed RNG/namespace), numbers written to the artifact path.
- [ ] Requirements: REQ-NF-001, REQ-T-012, REQ-NF-002, REQ-NF-003.

### 11.9 — CI quality gates + local-full / deployed-smoke split
- [ ] Configure CI (GitHub Actions, §13) to enforce the gates in order: lint/format (**ESLint 9, Prettier 3.3, Spotless**) → unit + coverage (**Vitest** incl. RTK Query cache-invalidation tests; **JaCoCo ≥80% per Gradle module** across api/worker/shared — build **fails below threshold**) → **SpotBugs** → local **Cypress/Cucumber** E2E against Compose services.
- [ ] Wire Gradle: JaCoCo `violationRules` at 0.80 per module; Spotless + SpotBugs tasks fail the build on violation (REQ-T-013). Wire frontend: ESLint 9 + Prettier 3.3 + Vitest fail the build on violation (REQ-T-014).
- [ ] Implement the **local-full vs deployed-smoke split** (REQ-T-016): the full Cypress/Cucumber suite runs against local CI/Compose; a `smoke/` subset runs **post-deploy against the deployed custom domains** (`wc.`/`api.wc.${ROOT_DOMAIN}`), producing both local E2E evidence and post-deploy smoke evidence.
- [ ] Aggregate all evidence (unit/integration/E2E + the §14 perf numbers from 11.8 + smoke) into the test-results deliverable (§18).
- [ ] Files: NEW `.github/workflows/ci.yml` (lint→test→coverage→spotbugs→e2e→build→migrate→deploy→smoke stages), extended `apps/wc-api/build.gradle` (JaCoCo/Spotless/SpotBugs gate config), extended `apps/wc-web/` ESLint 9 + Prettier 3.3 + Vitest config, NEW `apps/wc-e2e/smoke/` deployed-domain subset.
- [ ] Cross-doc invariant: none (CI/build configuration enforcing the §17 gates).
- [ ] Tests — happy: full pipeline green on a clean tree; edge: smoke subset runs only post-deploy against custom domains; error: a module under 80% JaCoCo / a Spotless or SpotBugs violation / an ESLint or Prettier failure fails CI; integration: local E2E + deployed smoke both emit artifacts collected into test-results.
- [ ] Requirements: REQ-T-013, REQ-T-014, REQ-T-016.

### Acceptance criteria (11)
- [ ] All 11.X task checkboxes ticked.
- [ ] Backend integration tests run on Testcontainers PostgreSQL 16 with the real Flyway schema+seed; no H2 anywhere (REQ-T-017).
- [ ] The full §6 IDOR denial matrix passes (every case → correct `403/404` + authorization-denial `audit_event`), and the security suite proves demo-header rejection, JWT audience/none/expired rejection, input-validation/escaping, and no-secret-leak Graph failures.
- [ ] Projection trigger/rollback hold and **rebuild == incremental** for the full seed; idempotent-retry reuses `graph_event_id` with zero duplicates; CronJob generation is idempotent with zero sync records.
- [ ] All 7 Cypress+Cucumber feature files pass green against local Compose, including the dispute loop's IC-response + IC-cannot-resolve legs and the carry-forward baseline-unchanged assertion.
- [ ] Measured p95 < 200ms for `/plans/current` and `/manager/command-center` at 2,000-row scale, recorded in the test-results artifact.
- [ ] CI enforces JaCoCo ≥80%/module, Spotless, SpotBugs, ESLint 9, Prettier 3.3, Vitest; local full suite + deployed smoke split produce separate evidence (REQ-T-016).

---

## Phase 12 — AWS infrastructure, deployment & CI/CD

**Goal:** Stand up the complete production-shaped AWS topology for WC as Terraform IaC (`infra/terraform`) and Kubernetes manifests (`infra/k8s`), then wire a GitHub Actions OIDC-federated pipeline that gates → builds/pushes both ECR images → applies Terraform → runs the sole-owner Flyway migration Job pre-deploy → rolls the api/worker Deployments and the generation CronJob → syncs the Vite bundle to S3 with a CloudFront invalidation → runs a deployed smoke suite against the custom domains. Every workload reads its secrets from Secrets Manager via the CSI driver under per-workload IRSA least privilege (no static AWS keys anywhere), and the deploy proves `wc.${ROOT_DOMAIN}` (frontend) and `api.wc.${ROOT_DOMAIN}` (API) over ACM TLS. Tasks are ordered infrastructure-foundation-first (remote state → network → compute → data/registry → messaging → CDN → DNS/certs → secrets → IRSA) before the k8s and CI layers that consume them, honoring RISK-009 (thin Terraform), RISK-010 (CloudFront SPA/cert), RISK-011 (RDS 16.x availability).

**Spec anchors:** `ARCHITECTURE.md §12`, §13, §2, §15, Appendix C.6, Appendix C.7, Appendix D (D.1–D.6), Appendix F (F.6, F.7).

### 12.1 — Terraform remote state, providers & root variables
- [ ] `backend.tf` configures an S3 backend for TF state with a DynamoDB lock table; bucket + lock table names parameterized, versioning + encryption on the state bucket.
- [ ] `versions.tf` pins required_version and the `aws`/`kubernetes`/`helm` provider version constraints; default AWS provider `region = var.region` (default `us-east-1`, overridable — REQ-O-011) PLUS a second aliased `aws.us_east_1` provider fixed to `us-east-1` for the CloudFront ACM cert (§12).
- [ ] `variables.tf` declares `ROOT_DOMAIN` (required, no default — OQ-001), `region` (default `us-east-1`), `rds_minor_version` (the latest available 16.x minor, ≥16.13), `env` (`local|aws`); `outputs.tf` exports RDS endpoint, ECR repo URLs, SNS topic ARN, SQS queue/DLQ URLs, S3 bucket name, CloudFront distribution id, hosted-zone id.
- [ ] Files: NEW `infra/terraform/backend.tf`, `infra/terraform/versions.tf`, `infra/terraform/main.tf`, `infra/terraform/variables.tf`, `infra/terraform/outputs.tf`
- [ ] Cross-doc invariant: none (IaC scaffolding; no Appendix A model)
- [ ] Tests — happy: `terraform init` succeeds against the S3 backend, `terraform validate` passes, `terraform fmt -check` clean; edge: missing `ROOT_DOMAIN` fails plan with a required-variable error; error: `terraform plan` with an unparseable region surfaces a provider error (no silent default); integration: `terraform plan` produces a non-empty, lock-acquiring plan with DynamoDB state lock held.
- [ ] Requirements: REQ-O-006, REQ-O-011

### 12.2 — VPC network + EKS cluster + managed node group + ALB controller
- [ ] `vpc.tf` provisions a VPC with public + private subnets across ≥2 AZs, NAT for private egress, and tags required by the AWS Load Balancer Controller (subnet `kubernetes.io/role/elb` + `internal-elb`).
- [ ] `eks.tf` provisions an EKS cluster, ONE small managed node group sized for api + worker + CronJob + migration Job (REQ-O-013), OIDC provider enabled on the cluster for IRSA, and installs the AWS Load Balancer Controller (REQ-O-012); adds an EKS access entry mapping the CI deploy role (§13) so CI can `aws eks update-kubeconfig`.
- [ ] Node group min/max kept thin per RISK-009 (no autoscaler complexity); cluster + node-group versions pinned via variable.
- [ ] Files: NEW `infra/terraform/vpc.tf`, `infra/terraform/eks.tf`
- [ ] Cross-doc invariant: none (infrastructure; no Appendix A model)
- [ ] Tests — happy: `terraform validate` passes; plan shows exactly one managed node group and an OIDC provider resource; edge: plan asserts AZ count ≥2 and private-subnet ELB tags present; error: plan with a node group desired-size of 0 is flagged/rejected by a validation/precondition; integration: plan graph shows the ALB controller IAM policy + service-account role wired to the cluster OIDC provider, and the CI-role EKS access entry present.
- [ ] Requirements: REQ-O-005, REQ-O-012, REQ-O-013, REQ-NF-007

### 12.3 — RDS PostgreSQL major 16 (latest 16.x minor, auto minor upgrade)
- [ ] `rds.tf` provisions an RDS PostgreSQL instance: `engine = "postgres"`, `engine_version = var.rds_minor_version` (latest available 16.x minor, ≥16.13 — supersedes PRD literal "16.4" per OQ-007/§4), `auto_minor_version_upgrade = true`, DB name `wc`, app user `wc_app` (least privilege), placed in private subnets, security group allowing 5432 only from the EKS node group SG.
- [ ] Connection coordinates (host, db, username, password) are written into the db secret (12.6), never an output in plaintext logs (RISK-016/§15).
- [ ] RISK-011 mitigation: `rds_minor_version` is a variable so a region-availability mismatch is changed in one place, not hardcoded.
- [ ] Files: NEW `infra/terraform/rds.tf`
- [ ] Cross-doc invariant: none (provisions the store behind WeeklyPlan/WeeklyCommitment/etc.; no schema here — Flyway Job owns schema, §12)
- [ ] Tests — happy: `terraform validate` passes; plan shows `engine_version` resolving to a 16.x value and `auto_minor_version_upgrade = true`; edge: plan asserts the instance is not publicly accessible and SG ingress is restricted to the node-group SG only; error: a non-16.x `rds_minor_version` value fails a Terraform precondition; integration: plan wires RDS host/credentials into the db Secrets Manager secret (12.6), not into a plaintext output.
- [ ] Requirements: REQ-O-005, REQ-O-011

### 12.4 — ECR repositories for api + worker images
- [ ] `ecr.tf` provisions exactly two ECR repositories — one for `wc-api`, one for `wc-sync-worker` (REQ-O-014); repo URLs exported as outputs for CI push.
- [ ] CronJob and migration Job reuse the `wc-api` image (no third repo, §8/§12); image tags = commit SHA (§13).
- [ ] Files: NEW `infra/terraform/ecr.tf`
- [ ] Cross-doc invariant: none (registry infra; no Appendix A model)
- [ ] Tests — happy: `terraform validate` passes; plan shows exactly two repositories named for api + worker; edge: plan asserts NO third image repository exists (CronJob/migration reuse api); error: duplicate repo name in config fails validate; integration: repo URLs appear in `outputs.tf` and are consumed by the CI build/push step (12.8).
- [ ] Requirements: REQ-O-005, REQ-O-014, REQ-NF-007

### 12.5 — SNS topic + SQS queue (raw delivery) + DLQ redrive
- [ ] `sns_sqs.tf` provisions one SNS lifecycle topic, one standard SQS sync queue subscribed to the topic with **raw message delivery enabled**, and one DLQ; the queue redrive policy sets `maxReceiveCount` to the DLQ (§10/§12, REQ-I-011).
- [ ] SQS access policy permits the topic to publish to the queue; queue + DLQ URLs and topic ARN exported as outputs for k8s env injection (Appendix D.2 `SNS_TOPIC_ARN`, D.3 `SQS_QUEUE_URL`/`SQS_DLQ_URL`).
- [ ] Pointer-payload-only transport is enforced at the app layer (Appendix F.2); this task provisions the topology that REQ-I-009 (SNS fanout → SQS to the separate EKS worker) rides on.
- [ ] Files: NEW `infra/terraform/sns_sqs.tf`
- [ ] Cross-doc invariant: none here (the OutlookCalendarSyncRecord outbox model is owned by the sync domain, §10/Appendix A — this task only provisions the transport)
- [ ] Tests — happy: `terraform validate` passes; plan shows topic + queue + DLQ and the SNS→SQS subscription with `raw_message_delivery = true`; edge: plan asserts the queue redrive policy targets the DLQ with a finite `maxReceiveCount`; error: a queue without a redrive policy is flagged by a validation/precondition; integration: topic ARN + queue/DLQ URLs are exported and match the Appendix D.2/D.3 env keys the deployments inject.
- [ ] Requirements: REQ-O-005, REQ-I-009, REQ-I-011

### 12.6 — Private S3 + CloudFront (OAC, SPA fallback) and Secrets Manager
- [ ] `s3_cloudfront.tf` provisions a PRIVATE S3 bucket (no public ACL, block-public-access on) for the Vite assets and a CloudFront distribution using Origin Access Control (OAC) — not legacy OAI — with the bucket policy scoped to the distribution; configures an SPA fallback custom-error response mapping 403/404 → `/index.html` 200 (REQ-NF-006, RISK-010).
- [ ] `secrets.tf` provisions four Secrets Manager secrets — `db`, `auth0`, `graph`, `demo` (§12) — populated from Terraform inputs/RDS outputs; secret ARNs exported for the SecretProviderClass + IRSA policies. Secrets are never baked into images or the Vite bundle (REQ-S-010, RISK-016).
- [ ] Files: NEW `infra/terraform/s3_cloudfront.tf`, `infra/terraform/secrets.tf`
- [ ] Cross-doc invariant: none (CDN + secret stores; no Appendix A model)
- [ ] Tests — happy: `terraform validate` passes; plan shows the bucket with public access blocked, OAC attached, and the 403/404→index.html SPA fallback; edge: plan asserts the bucket policy grants read ONLY to the CloudFront OAC principal (no `*`); error: a wildcard/public bucket policy fails a validation/precondition; integration: the four secret ARNs (db/auth0/graph/demo) export and are referenced by the SecretProviderClass (12.10) and the IRSA policies (12.7).
- [ ] Requirements: REQ-O-005, REQ-NF-006, REQ-S-010

### 12.7 — Route53 + ACM certs and per-workload IRSA + CI OIDC role
- [ ] `route53_acm.tf` creates Route53 alias records for `wc.${ROOT_DOMAIN}` (→ CloudFront) and `api.wc.${ROOT_DOMAIN}` (→ ALB) (REQ-O-008), plus ACM certs with DNS validation: the **CloudFront cert in `us-east-1`** (via the aliased provider) and the **ALB cert regional** (§12, REQ-O-011, RISK-010).
- [ ] `iam_irsa.tf` creates per-workload IRSA roles+policies scoped exactly: api SA → `sns:Publish` + `secretsmanager:GetSecretValue` on db/auth0/graph; worker SA → `sqs:ReceiveMessage,DeleteMessage,GetQueueAttributes` on queue+DLQ + `GetSecretValue` on graph only; CronJob SA → `GetSecretValue` on db only; migration Job SA → `GetSecretValue` on db only (§12).
- [ ] Also provisions the GitHub Actions OIDC identity provider + a single least-privilege CI deploy role assumable only by the repo's OIDC subject (no long-lived keys, REQ-S-010, RISK-016) — flagged: exact file split for the CI role is not pinned by Appendix C.6.
- [ ] Files: NEW `infra/terraform/route53_acm.tf`, `infra/terraform/iam_irsa.tf`
- [ ] Cross-doc invariant: none (DNS/cert/identity infra; no Appendix A model)
- [ ] Tests — happy: `terraform validate` passes; plan shows the CloudFront cert in `us-east-1` and the ALB cert in `var.region`; edge: plan asserts each IRSA policy is least-privilege (worker has NO db/auth0 secret access; CronJob/migration have db-only); error: an IRSA policy granting `secretsmanager:GetSecretValue` on `*` fails a validation/precondition; integration: the four SA role ARNs map 1:1 to the C.7 ServiceAccount manifests (12.10) and the CI OIDC role matches the EKS access entry from 12.2.
- [ ] Requirements: REQ-O-005, REQ-O-008, REQ-O-011, REQ-S-010, REQ-E-006

### 12.8 — k8s ServiceAccounts, SecretProviderClass & service+ingress
- [ ] `serviceaccount-api.yaml`, `serviceaccount-worker.yaml`, `serviceaccount-cronjob.yaml`, `serviceaccount-migration.yaml` annotate `eks.amazonaws.com/role-arn` with the matching 12.7 IRSA role; `secretproviderclass.yaml` uses the Secrets Store CSI Driver + ASCP to mount the per-workload secrets to `/mnt/secrets` (consumed via `spring.config.import=optional:file:/mnt/secrets/...`, §12/Appendix D).
- [ ] `service-api.yaml` is a ClusterIP fronted by the ALB; `ingress-api.yaml` is an ALB ingress referencing the regional ACM cert and host `api.wc.${ROOT_DOMAIN}` (REQ-O-012, REQ-O-008); `namespace.yaml` declares the WC namespace.
- [ ] Each SA's SecretProviderClass slice exposes ONLY the secrets that SA's IRSA permits (worker → graph only; CronJob/migration → db only) — manifest matches IRSA least privilege.
- [ ] Files: NEW `infra/k8s/namespace.yaml`, `infra/k8s/serviceaccount-api.yaml`, `infra/k8s/serviceaccount-worker.yaml`, `infra/k8s/serviceaccount-cronjob.yaml`, `infra/k8s/serviceaccount-migration.yaml`, `infra/k8s/secretproviderclass.yaml`, `infra/k8s/service-api.yaml`, `infra/k8s/ingress-api.yaml`
- [ ] Cross-doc invariant: none (k8s plumbing; no Appendix A model)
- [ ] Tests — happy: `kubectl apply --dry-run=server` (or `kubeconform`) validates every manifest; ingress host = `api.wc.${ROOT_DOMAIN}` and references the ALB scheme + ACM cert annotation; edge: assert the worker SecretProviderClass references graph only (no db/auth0) and migration/cronjob reference db only; error: a SA missing its `role-arn` annotation fails the lint/validation; integration: the four `role-arn` values resolve to the 12.7 IRSA outputs and the SecretProviderClass mount path equals the `spring.config.import` file path in Appendix D.
- [ ] Requirements: REQ-O-008, REQ-O-012, REQ-S-010, REQ-NF-007

### 12.9 — k8s Deployments, generation CronJob & pre-deploy migration Job
- [ ] `deployment-api.yaml` (wc-api image, `SPRING_PROFILES_ACTIVE=aws`, env from Appendix D.2, `spring.flyway.enabled=false`, liveness=`/actuator/health/liveness` + readiness=`/actuator/health/readiness` probes, SA=api) and `deployment-worker.yaml` (wc-sync-worker image, env from D.3, **flyway disabled**, actuator probes, SA=worker) (REQ-NF-007).
- [ ] `cronjob-generation.yaml` runs the **wc-api image** with args `--app.job=generate-plan-shells` (D.4), SA=cronjob (db secret only), no SNS/SQS/Graph env; `job-migration.yaml` runs the **wc-api image** in the `flyway-migrate` profile (`spring.flyway.enabled=true`), SA=migration (db secret only) — the SOLE schema owner, run PRE-DEPLOY before any Deployment/CronJob roll (§12).
- [ ] `job-perf-seed.yaml` runs the wc-api image with `--app.job=generate-perf-seed` (Appendix F.6) — opt-in, NOT part of the normal deploy chain.
- [ ] Files: NEW `infra/k8s/deployment-api.yaml`, `infra/k8s/deployment-worker.yaml`, `infra/k8s/cronjob-generation.yaml`, `infra/k8s/job-migration.yaml`, `infra/k8s/job-perf-seed.yaml`
- [ ] Cross-doc invariant: none (manifests reference the §8 generation entrypoint + §12 migration profile; no Appendix A field change)
- [ ] Tests — happy: dry-run/kubeconform validates all five; api+worker have both liveness+readiness actuator probes; migration Job has `flyway.enabled=true` and api/worker/cronjob have it `false`; edge: assert CronJob + migration Job + perf-seed Job all use the wc-api image (no third image) and the perf-seed Job is absent from the normal deploy ordering; error: a worker manifest with `flyway.enabled=true` fails a policy/lint check; integration: env keys on each manifest exactly match Appendix D.2/D.3/D.4/D.5 (required keys present, secrets sourced from the `/mnt/secrets` CSI mount, never plaintext).
- [ ] Requirements: REQ-O-005, REQ-NF-007, REQ-O-013

### 12.10 — CloudWatch logs/metrics provisioning
- [ ] `cloudwatch.tf` provisions CloudWatch log groups (with retention) and any metric namespaces for api, worker, CronJob, and the migration Job, so container logs and Actuator/Micrometer metrics are CloudWatch-visible (REQ-O-009/§15).
- [ ] Log hygiene contract is honored downstream (no notes/responses/comment bodies/secrets in logs, §15) — this task only provisions the destination log groups, not app log content.
- [ ] Flagged: the pod→CloudWatch log-forwarding AGENT (Fluent Bit DaemonSet / Container Insights) is not named in Appendix C.7; this task stops at provisioning log groups/metrics destinations.
- [ ] Files: NEW `infra/terraform/cloudwatch.tf`
- [ ] Cross-doc invariant: none (observability infra; no Appendix A model)
- [ ] Tests — happy: `terraform validate` passes; plan shows log groups for each workload with a finite retention; edge: plan asserts retention is set (not never-expire) and group names are workload-scoped; error: a log group with an invalid retention value fails validate; integration: log-group names/ARNs export as outputs for downstream wiring.
- [ ] Requirements: REQ-O-005

### 12.11 — GitHub Actions OIDC pipeline + deployed smoke
- [ ] CI authenticates to AWS via `aws-actions/configure-aws-credentials` OIDC federation assuming the single least-privilege CI deploy role (NO long-lived/static keys — REQ-S-010, RISK-016); ordered pipeline: (1) gates [ESLint 9, Prettier 3.3, Spotless, Vitest, JaCoCo ≥80%/module, SpotBugs, local Cypress/Cucumber E2E against Compose] → (2) build+push wc-api + wc-sync-worker images to ECR tagged commit SHA → (3) `terraform plan/apply` (S3+DynamoDB remote state) → (4) `aws eks update-kubeconfig` via the CI EKS access entry then run the **migration Job and wait for completion** → (5) deploy/roll api + worker Deployments + generation CronJob → (6) sync the Vite bundle to S3 + issue a CloudFront invalidation → (7) run the deployed smoke suite against the custom domains (REQ-O-007).
- [ ] Build sets `VITE_AUTH_MODE` + `VITE_API_BASE_URL=https://api.wc.${ROOT_DOMAIN}` at build time (Appendix D.1); migration Job runs and succeeds BEFORE the Deployments/CronJob roll (§12 ordering); ordering failures (e.g. deploy before migrate) are not reachable in the workflow graph.
- [ ] Deployed smoke (subset under `apps/wc-e2e/smoke/`, REQ-T-016) asserts `https://wc.${ROOT_DOMAIN}` serves the SPA and `https://api.wc.${ROOT_DOMAIN}/actuator/health/readiness` responds 200 over ACM TLS — the evidence for REQ-E-006/REQ-O-008.
- [ ] Files: NEW `.github/workflows/deploy.yml` (path conventional — flagged: not pinned by Appendix C); extended `apps/wc-e2e/smoke/` (deployed-domain smoke subset, Appendix C.5)
- [ ] Cross-doc invariant: none (CI pipeline; no Appendix A model)
- [ ] Tests — happy: workflow lint (`actionlint`) passes; a dry-run/CI-on-PR executes gates → build → (plan only) and the job graph shows the migration Job step strictly before the Deployment roll; edge: assert no `aws-access-key-id`/static-secret inputs anywhere in the workflow (OIDC `id-token: write` permission present instead); error: a workflow that orders `apply`/deploy before gates fails an ordering assertion; integration: the deployed smoke step targets `wc.${ROOT_DOMAIN}` + `api.wc.${ROOT_DOMAIN}` health and the S3 sync step is followed by a CloudFront invalidation step.
- [ ] Requirements: REQ-O-007, REQ-O-008, REQ-S-010, REQ-NF-006, REQ-NF-007, REQ-E-006

### Acceptance criteria (12)
- [ ] All 12.X task checkboxes ticked.
- [ ] `terraform validate` + `terraform fmt -check` pass for the full `infra/terraform` tree; a `terraform plan` against S3+DynamoDB remote state provisions the complete §12 service graph (VPC, EKS+ALB controller+managed node group, RDS PG 16.x, two ECR repos, SNS+SQS+DLQ redrive, private S3+CloudFront OAC+SPA fallback, Route53 wc./api.wc. records, ACM CloudFront-us-east-1 + ALB-regional, four Secrets Manager secrets, per-workload IRSA + CI OIDC role, CloudWatch log groups).
- [ ] Every `infra/k8s` manifest validates (kubeconform/server dry-run); the migration Job is the sole `flyway.enabled=true` workload and CronJob/migration both reuse the wc-api image; per-workload SecretProviderClass + IRSA enforce least privilege (worker→graph only, cron/migration→db only, api→db/auth0/graph).
- [ ] The GitHub Actions pipeline authenticates by OIDC with no static keys, runs in the §13 order (gates → build/push → terraform apply → migration Job → deploy api/worker+cron → S3 sync + CloudFront invalidation → deployed smoke), and the deployed smoke proves `wc.${ROOT_DOMAIN}` + `api.wc.${ROOT_DOMAIN}` over ACM TLS (REQ-E-006).
- [ ] RISK-009 (thin/managed Terraform), RISK-010 (CloudFront SPA fallback + us-east-1 cert), RISK-011 (RDS minor as a single variable) mitigations are visibly in place.


---

## Phase 13 — Deliverables, observability & polish

**Goal:** Close out the build with the cross-cutting observability spine and the non-code deliverables the contract mandates. Wire the §15 lifecycle signals as structured logs (IDs + state only) with strict log hygiene, complete `audit_event` coverage of the sensitive-action list, and expose Actuator liveness/readiness on api + worker so k8s probes and CloudWatch work. Then produce the required Markdown/documentation artifacts: `AI_USAGE.md`, technical documentation (README + run/deploy docs), the demo-video script that hits every EVALUATION_CRITERIA Demo Success Signal, the seeded Trims/Nice-to-Haves catalog (the §20 trim order + deferred list), and the documented weekday-only-SLA holiday-deferred fallback. Observability/audit tasks are sequenced first (they are testable invariants and gate log-leak safety); documentation tasks follow.

**Spec anchors:** `ARCHITECTURE.md §15`, §18, §20; supporting `§3` (derived OVERDUE), `§6` (authorization-denial + demo-header rejection audit), Appendix A (`AuditEvent`), Appendix C (C.2 `audit/`, `auth/`, C.7 manifests), Appendix D.2 (`app.org.timezone` fail-safe), Appendix F.3 (SLA clock), EVALUATION_CRITERIA.md (Demo Success Signals).

### 13.1 — Structured lifecycle-signal logging (IDs + state only) with log hygiene
- [ ] Emit a structured (key/value, JSON-friendly) log line for every §15 signal at its source service/transaction: `PLAN_LOCKED`; `REVIEW_DUE` + `REVIEW_OVERDUE` (overdue emitted at read time per §3, never on a write) + `REVIEW_REVIEWED`; `RECONCILIATION_STARTED` + `RECONCILIATION_CLOSED`; `COMMITMENT_CARRIED_FORWARD`; `DISPUTE_OPENED` + `DISPUTE_RESPONDED` + `DISPUTE_RESOLVED`; `OUTLOOK_SYNC_SUCCESS` + `OUTLOOK_SYNC_FAILURE`; `AUTHORIZATION_DENIAL`.
- [ ] Each log line carries only entity ids, actor id (or `SYSTEM`), entity_type, and the state/transition (e.g. `from`/`to`) plus `traceId` — never note/response/body/title/token/PII content (§15).
- [ ] Request/response body logging is OFF by default (no Spring `CommonsRequestLoggingFilter`/payload dump enabled); a single shared logging helper enforces the allowed-field set so call sites cannot leak.
- [ ] System-initiated signals (generation, worker sync success/failure) log under the `SYSTEM` actor.
- [ ] Files: NEW `apps/wc-api/api/src/main/java/com/st6/wc/observability/LifecycleSignalLogger.java` (shared safe-field emitter); extended `plan/PlanLifecycleService.java`, `commitment/CarryForwardService.java`, `review/ReviewStatusDeriver.java`, `dispute/*Service.java`, `sync/SyncRecordService.java`, `worker/service/WorkerSyncRecordService.java`, `auth/AuthorizationDeniedAuditer.java` (add denial signal line). Extended `application.yml` logging config (body logging off).
- [ ] Cross-doc invariant: none (no model field change; consumes existing enums/ids).
- [ ] Tests — happy: each signal type produces exactly one structured line with the expected event key + ids + state (capture via a test log appender / `OutputCaptureExtension`); edge: read-time `REVIEW_OVERDUE` emitted only when `now>reviewDueAt AND NOT_REVIEWED` (injectable Clock), `REVIEWED_WITH_DISPUTES` does not emit overdue; error: a log-hygiene assertion that feeds a commitment with manager_note / ic_response / comment body / a fake bearer token and asserts none of those substrings appear in any emitted line; integration: a lock→reconcile→dispute flow over Testcontainers emits the full ordered signal set with no body content.
- [ ] Requirements: REQ-O-001

### 13.2 — Complete `audit_event` coverage of the sensitive-action list
- [ ] Every sensitive action writes one `audit_event` row via `AuditService` with `action`, `entity_type`, `entity_id`, `actor_employee_id` (nullable→SYSTEM), `summary`, `metadata_json` (safe-only): plan lock; mark-reviewed; dispute open/respond/resolve; reconciliation start/close; carry-forward; Graph/Outlook sync failure; authorization denial; demo-header rejection when `DEMO_AUTH_ENABLED=false`.
- [ ] `metadata_json` contains only ids + state transitions — never manager notes, `ic_response`, comment bodies, dispute rationale, tokens/secrets, or PII bodies (§15); a single safe-metadata builder is the only writer path.
- [ ] SYSTEM-actor rows (generation shells, worker sync state changes) write with `actor_employee_id = NULL`.
- [ ] Files: extended `apps/wc-api/api/src/main/java/com/st6/wc/audit/AuditService.java` (safe-metadata builder + per-action helpers), `auth/AuthorizationDeniedAuditer.java`, `config/DemoAuthFilter.java` (rejection audit), plan/review/dispute/commitment/sync services (audit call sites); reuses `shared/.../audit/AuditEvent.java`.
- [ ] Cross-doc invariant: none (uses existing `AuditEvent` Appendix A model; no new field).
- [ ] Tests — happy: each sensitive action inserts exactly one `audit_event` with correct `action`/`entity_type`/`entity_id`/actor (Testcontainers); edge: SYSTEM actions store `actor_employee_id=NULL`; demo-header rejection while `DEMO_AUTH_ENABLED=false` writes a denial/rejection audit row; error: assert `metadata_json` for a dispute-open and a manager-note PATCH contains no note/rationale/body/token substring; integration: full IDOR denial matrix (each §6 denial case) produces a corresponding authorization-denial audit row (ties to the §17 IDOR matrix).
- [ ] Requirements: REQ-D-007, REQ-O-001

### 13.3 — Actuator health/readiness on api + worker for k8s probes + CloudWatch
- [ ] `wc-api` exposes `GET /actuator/health/liveness` and `GET /actuator/health/readiness` (E24, `public` probe scope — preflight/probe path bypasses JWT and the demo filter; never gated behind auth).
- [ ] `wc-sync-worker` exposes the same liveness/readiness endpoints; readiness reflects DB + SQS-consumer readiness (no Auth0/SNS/CORS surface on the worker).
- [ ] Readiness reports DB connectivity (and is wired so the migration Job ordering in §12 keeps readiness honest); liveness stays independent of downstream Graph availability so an Outlook outage never flips liveness.
- [ ] api + worker + CronJob + migration Job write CloudWatch-visible structured logs (stdout JSON layout) so the §15 signals and audit context are observable.
- [ ] Files: extended `apps/wc-api/api/src/main/resources/application.yml` (`management.endpoint.health.probes.enabled=true`, group exposure), `apps/wc-api/worker/src/main/resources/application.yml`; extended `infra/k8s/deployment-api.yaml`, `infra/k8s/deployment-worker.yaml` (livenessProbe/readinessProbe → the actuator paths); NEW `apps/wc-api/api/src/main/java/com/st6/wc/observability/ReadinessConfig.java` if a custom readiness contributor is needed.
- [ ] Cross-doc invariant: none.
- [ ] Tests — happy: `GET /actuator/health/readiness` and `/liveness` return `200` with status `UP` (api + worker, Testcontainers); edge: probe path reachable without `Authorization`/`X-Demo-Employee-Id` (public, no audit denial); error: readiness reports `DOWN`/`503` when the datasource is unavailable; integration: deployed smoke suite hits `https://api.wc.${ROOT_DOMAIN}/actuator/health/readiness` post-deploy (links to the §13 smoke/REQ-E-006 evidence).
- [ ] Requirements: REQ-O-009

### 13.4 — Document the weekday-only-SLA holiday-deferred fallback
- [ ] Write the run/ops note documenting that `reviewDueAt` = 17:00 org-tz on the next business day, weekdays-only, holidays deferred (Appendix F.3), and that holiday-aware SLA is an explicit deferred non-goal (§1/§20).
- [ ] Document the `app.org.timezone` (`ORG_TIMEZONE`) fail-safe: unset/blank/invalid `ZoneId` ⇒ fall back to `America/Chicago` + WARN, never UTC, never undefined (Appendix D.2/D.6).
- [ ] State the come-back path (where holiday-aware SLA would live: `review/ReviewSlaService.java`) so the deferral is recoverable; cross-reference the Trims catalog entry (13.7).
- [ ] Files: NEW `docs/runbook.md` (SLA fallback section) or extended README ops section; cross-linked from the §13.7 Trims catalog entry.
- [ ] Cross-doc invariant: none (documentation of existing `ReviewSlaService` behavior; no model change).
- [ ] Tests — happy: doc names the weekday-only rule, the 17:00 org-tz time, and the holiday-deferred status (verifiable by review against F.3); error: doc states the timezone fail-safe (fallback to `America/Chicago`, WARN, never UTC) matching D.2/D.6. (Documentation task — assertion is presence/consistency, not runtime; the runtime weekday-only behavior is already covered by the §17 SLA/Clock unit tests.)
- [ ] Requirements: REQ-O-004

### 13.5 — `AI_USAGE.md` deliverable
- [ ] Author `AI_USAGE.md` (Markdown) at repo root capturing: tools/models used; AI-assisted task summaries; human-review notes; and the list of generated artifacts (§18, REQ-O-010).
- [ ] Structure as stable Markdown sections so it can accrete across build phases without reformatting; no secrets/tokens/PII in the log (consistent with §15 hygiene).
- [ ] Files: NEW `AI_USAGE.md` (repo root, per §21 scaffold).
- [ ] Cross-doc invariant: none.
- [ ] Tests — happy: file exists at repo root and contains the four required sections (tool/model usage, AI-assisted task summaries, human-review notes, generated artifacts) — verifiable by a docs-presence check; error: no token/secret/PII content. (Documentation deliverable; acceptance is section-presence per REQ-O-010.)
- [ ] Requirements: REQ-O-010

### 13.6 — Technical documentation (README + run/deploy docs)
- [ ] Author repo `README.md` (project overview, the architecture sentence, stack, local-run quickstart via Docker Compose with the §13 profile-switched async transport, ports per F.7) and `docs/` run/deploy docs (env/secret catalog pointer to Appendix D, Terraform + GitHub Actions OIDC deploy flow per §12/§13, migration-Job-first ordering, custom-domain wiring `wc.`/`api.wc.`).
- [ ] Document how to run the test suites (unit/Testcontainers integration, Vitest, Cypress/Cucumber local + deployed smoke) and where the §14 perf numbers + test-results artifact live (§18 test-results deliverable pointer).
- [ ] No secrets in docs; reference Secrets Manager / CSI mount, not literal values (§12/§16).
- [ ] Files: NEW `README.md` (repo root), `docs/run-local.md`, `docs/deploy.md` (extend `docs/runbook.md` from 13.4 if consolidating ops notes).
- [ ] Cross-doc invariant: none.
- [ ] Tests — happy: README + run + deploy docs exist and cover local-run, deploy flow, custom domains, and test execution — verifiable by docs-presence/consistency review against §12/§13/Appendix D; error: no secret literals present. (Documentation deliverable per §18.)
- [ ] Requirements: REQ-O-009, REQ-O-010

### 13.7 — Seed the Trims / Nice-to-Haves catalog (§20 trim order + deferred items)
- [ ] Populate the `MVP_TASKS.md` "Trims / Nice-to-Haves Catalog" section with the §20 trim order in priority sequence: (1) Outlook `MANAGER_REVIEW_BLOCK` automation (designated first pressure-release — degrades to no review-block event, no impact on lock/review SLA); (2) non-essential UI filters/polish; (3) projection rebuild niceties (keep synchronous projections); (4) comment depth (already flat).
- [ ] List the §20 deferred items as catalog entries with come-back guidance (why deferred, where it belongs, files to modify, tests to add, cross-doc-invariant impact): holiday-aware SLA (link 13.4; `review/ReviewSlaService.java`), nested-comment threading + review/dispute comment targets (schema already nestable), API rate limiting/abuse control, SQS DLQ admin redrive UI, per-report manager calendar events, true live updates, unlock/amend, RCDO admin, pass-up/escalation & leadership rollups, user-local weeks, full PA LogRocket/Loki/Nx.
- [ ] Record the "Never trim" guardrails verbatim (required Supporting-Outcome enforcement, direct-report authorization, locked-baseline immutability, manager command-center heatmap/review/dispute visibility, AWS deployment fidelity).
- [ ] Files: extended `MVP_TASKS.md` (Trims / Nice-to-Haves Catalog section).
- [ ] Cross-doc invariant: none (planning-doc population; no model change).
- [ ] Tests — happy: catalog lists all four §20 trim-order items in order plus the deferred set, each with come-back guidance; error: the "Never trim" set is recorded and not contradicted. (Planning deliverable; acceptance is content-consistency against §20.)
- [ ] Requirements: REQ-O-004

### 13.8 — Demo-video script covering every EVALUATION_CRITERIA Demo Success Signal
- [ ] Author a demo-video script (`docs/demo-script.md`) with an ordered beat per EVALUATION_CRITERIA Demo Success Signal: IC tries to lock unlinked work (blocked + missing-SO explanation); IC locks linked plan (locked, baseline frozen, review-due date appears); manager dashboard opens (direct-report-only, plan states + heatmap risk badges); manager flags misalignment (dispute opens with required note, `REVIEWED_WITH_DISPUTES` satisfies SLA); IC responds (revise/rationalize, cannot resolve); manager resolves (leaves current-risk state); IC reconciles (outcomes + unplanned + carry-forward persisted); Outlook failure path (warning/retry visible, core workflow complete); unauthorized manager attempts access (denied); deployed custom domains work (`wc.${ROOT_DOMAIN}` serves frontend, `api.wc.${ROOT_DOMAIN}` serves API health/smoke).
- [ ] Map each beat to the seed fixture that demonstrates it (Appendix E state matrix: R6 unlinked-lock-block, R1 success-lock, R3 `REVIEWED_WITH_DISPUTES`, R4 resolved-dispute loop, R5 reconcile/carry-forward, R2 FAILED sync + overdue review, cross-report denial) so the script is runnable against the deterministic demo seed.
- [ ] Explicitly include the deployed-custom-domain proof beat (REQ-E-006): show `wc.${ROOT_DOMAIN}` loading and `api.wc.${ROOT_DOMAIN}/actuator/health/readiness` (from 13.3) responding.
- [ ] Files: NEW `docs/demo-script.md`.
- [ ] Cross-doc invariant: none.
- [ ] Tests — happy: script contains one beat per Demo Success Signal and the deployed-domains beat, each tied to a named Appendix E fixture — verifiable against EVALUATION_CRITERIA.md + Appendix E; error: no beat references undeployed/non-existent state. (Deliverable per REQ-E-006/§18.)
- [ ] Requirements: REQ-E-006

### Acceptance criteria (13)
- [ ] All 13.X task checkboxes ticked.
- [ ] Every §15 emitted signal is produced as a structured, IDs+state-only log line, and the log-hygiene assertion proves no notes/responses/bodies/tokens/PII leak into logs or `audit_event.metadata_json` (REQ-O-001/REQ-D-007).
- [ ] `audit_event` rows exist for the full sensitive-action list incl. authorization denials and demo-header rejection, SYSTEM actions under a null actor (REQ-D-007).
- [ ] `/actuator/health/liveness` + `/readiness` return UP on api + worker, are reachable without auth, and the deployed smoke suite confirms `api.wc.${ROOT_DOMAIN}` readiness (REQ-O-009).
- [ ] `AI_USAGE.md`, README + run/deploy docs, and the demo-video script exist and cover their required content; the demo script exercises every EVALUATION_CRITERIA Demo Success Signal incl. deployed custom domains (REQ-O-010, REQ-E-006).
- [ ] The Trims/Nice-to-Haves catalog is seeded with the §20 trim order, deferred items, and the "Never trim" guardrails; the weekday-only-SLA holiday-deferred fallback (+ timezone fail-safe) is documented (REQ-O-004).

---

## Trims / Nice-to-Haves Catalog

Deferred items with come-back guidance: why deferred, where it belongs, files to modify, tests to add, cross-doc invariant impact.

_(Empty at project start; populated as scope cuts surface. The `ARCHITECTURE.md §20` trim order is the standing guidance: trim (1) Outlook `MANAGER_REVIEW_BLOCK` automation first, then (2) non-essential UI filters, then (3) projection-rebuild niceties, then (4) comment depth — never the strategy-enforcement thesis, direct-report authorization, locked-baseline immutability, manager command-center core, or AWS fidelity.)_

---

## Decisions tabled

Open scope/design questions awaiting resolution, with rationale.

_(Empty at project start. Carried-but-non-blocking architecture open questions live in `ARCHITECTURE.md §22` — OQ-001 ROOT_DOMAIN, OQ-003 Auth0 claim names, OQ-005 Graph tenant/mailbox scoping. The decomposition surfaced a few implementation-level points the contract intentionally leaves to the implementer — audit `action` string constants, the heatmap risk-badge predicate table, the flat-comment `path` placeholder value, the CloudWatch log-shipping agent, and the GitHub Actions workflow filename; these are resolved in-slice and flagged at TDD Step 9 if they touch the contract.)_

---

## Log

Append-only, date-stamped, the orchestrator's framing of each round.

### 2026-06-02 — Round 1: Phase 0 backend spine + Phase 1 schema trio (7 slices)

- **Landed (backend, 7 slices / 7 implementer commits):** 0.1 monorepo root (`555c2a8`) · 0.2 Gradle multi-module + 3 quality gates (`d888f92`) · 0.3 `:shared` 16 enums + base entities + Clock/OrgTime (`e8b5305`) · 0.4+0.5 bootable wc-api + wc-sync-worker apps (`7c4b649`) · 1.2 Flyway V1 core schema, 12 tables (`b230ad0`) · 1.3 Flyway V2 partial uniques (`a2230c5`) · 1.4 Flyway V3 projection tables (`c1ae1f1`). Plus implementer close-out commits `1a34f3b` (JaCoCo task-dep fix surfaced by preflight) + `15afd68` (session doc 001). **Phase 0 backend (0.1–0.5) complete; Phase-1 V1/V2/V3 migration trio complete** — full physical-schema layer (12 core tables + 3 partial uniques + 2 projection read-models) proven on real Testcontainers PG16. All slices 100% covered, all gates green.
- **Decisions made:** Yarn Berry 4.5.3 (Corepack); Gradle 8.10.2 + foojay/JAVA_HOME toolchain; `ClockConfig` in `:shared`; `PersistableUuidEntity extends AbstractAuditingEntity` (composition); `ORG_TIMEZONE` fail-safe as reusable static `resolveZone`; schema per §4 + the 4 deltas (NOT the stale DATA_MODEL.md); `@Version`=`bigint`; `risk_badges` via `<@` array CHECK pinned to `RiskBadge`; Testcontainers BOM→1.21.4 (Docker 29); version-scoped migration tests.
- **Escalation handled:** the **no-JDK blocker** (Finding) — machine had no JVM; escalated to the human via lead; resolved (Homebrew openjdk@21); captured in `docs/runbooks/jdk21-toolchain-setup.md`.
- **Cross-doc work (orchestrator hot-routed):** Appendix C.1/C.2/C.3 + B.1 (`RoleType` added) reconciliations; 4 cross-doc-invariant rows in `apps/wc-api/CLAUDE.md` (enum vocab, core schema, partial uniques, projections); `docs/planning/DATA_MODEL.md` stamped **superseded by §4 + Appendix A + the 4 deltas** (it was the stale pre-delta draft); backend `LESSONS.md` §1–§6; wc-web `LESSONS.md` §1–§2 (JS-monorepo, banked in the JS area).
- **Scope shifts (resequencing, not cuts):** `1.1` **satisfied-by-0.3** (enums/common/Clock-OrgTime built in 0.3 — verify-only at Phase 1); `0.7`/`0.8` **deferred** (frontend-entangled shared files; not blocking Phase 1 which uses Testcontainers, not compose) — parked, not cut. Frontend (`0.6`, Phase 9, wc-web parts of 0.7/0.8) is the parallel `st6-main-wc-web-*` track (deferred-then-active; its 0.6/ST.1/ST.2 landed `e3c1cb7` on that track).
- **Coordination:** established the shared-doc collision-avoidance protocol with `st6-main-wc-web-orchestrator` (section ownership; labeled Backend:/Frontend: sub-blocks; ping-before-staging; RiskBadge values are a backend invariant, rendering is frontend). This round-commit clears `MVP_TASKS.md`/`ARCHITECTURE.md` for the frontend orch's Phase ST staging.
- **Tooling finding → fixed:** backend gate is `./gradlew check` from `apps/wc-api/` (not the generic `/preflight` per-task list; not `build -x test`) — area `CLAUDE.md` Standard-commands updated + LESSONS §6.
- **Next session target:** **1.5 — JPA entities** mirroring Appendix A, mapped to V1–V3 (`@Enumerated(STRING)`, `@Version` on the 5 mutable entities, extend `PersistableUuidEntity`/`AbstractAuditingEntity`) + repos. Then 1.x continues; `0.7`/`0.8` rejoin once the frontend stack is ready.
- **Reference:** implementer session doc `001-2026-06-02-phase0-backend-and-phase1-schema.md` for technical detail.

### 2026-06-02 — Frontend round 1: design review + styling foundation (ST.1/ST.2)

- **Design review + sign-off:** reviewed the delivered Cadence design system + weekly-commit UI kit (`docs/design/cadence-design-system/`, committed `2a307b8`); authored `docs/planning/frontend-styling-proposal.md`; user signed off (via lead) on the color reconciliation, the dark-default + light-toggle theming, the Phase ST shape, and 3 forks: **Fork 1 = A** (Tailwind/Flowbite-native), **Fork 2 = Option 2** (foundation-early, style-as-you-build), **Fork 3 = indigo brand in both themes**.
- **Landed (frontend, 1 slice `e3c1cb7`):** bundled **0.6 (partial) + ST.1 + ST.2** — `apps/wc-web` Cadence-themed shell (Vite 5/React 18/TS-strict/Vitest/ESLint 9/Prettier), Cadence tokens → `tailwind.config` + Flowbite custom theme (approach A, no `.wc-*` CSS), dark-default + light-toggle `[data-theme]` mechanism (persisted; standalone-only `ThemeToggle`; `darkMode` selector-bound). 13 Vitest tests; preflight + production build green.
- **Color reconciliation:** override is **foundation (light→dark) + brand (`blue-600`→indigo `#5E6AD2`) only**; six-tone semantic taxonomy + every enum→tone mapping preserved 1:1 → **no enum/Appendix-A cross-doc invariant changed** (confirmed with st6-main-orchestrator; `EnumVocabularyTest` unaffected). Resolved a spec self-inconsistency: `MISALIGNED`=red, `UNPLANNED`=violet (Cadence §4.2 canonical).
- **Cross-doc work (orchestrator hot-routed):** `ARCHITECTURE.md §7` styling-SoT note; `UI_UX_SPEC §4.1` superseded-by-Cadence note; **Phase ST** section + Phase 9 styling fold-in AC; wc-web `LESSONS §3/§4` + the forbidden-pattern #3 narrow exception (token-var stylesheet only) in wc-web `CLAUDE.md`.
- **Carve-outs (0.6 → 9.1/9.3):** federation `expose` + full standalone/remote split + `PersonaSwitcher`/`DemoIdentityProvider` + `baseApi` XOR + ThemeToggle bundle-absence proof.
- **Coordination:** the shared-doc protocol with st6-main-orchestrator held under a crossed commit-hold race — ST.1 (`e3c1cb7`) committed clean (only wc-web files + yarn.lock, zero backend sweep); these shared-doc edits applied after the backend round-commit `463ffd8` cleared the files.
- **Next frontend target:** **9.1** (RTK Query base + auth XOR — brief `009`, in flight), then 9.3 (MFE boundary) + 9.2 (themed primitives).

### 2026-06-02 — Round 2 (backend): Phase 1 JPA entities/repos + Phase 2 JWT decoder (3 slices) — PAUSED after 2.1

- **Landed (backend, 3 slices / 3 implementer commits):** 1.5 (`1d43a01`) 14 JPA entities mirroring Appendix A onto V1–V3 + 14 bare repos + the `@DataJpaTest`+Testcontainers PG16 `ddl-auto=validate` fidelity harness (8 tests); 1.6 (`8da6446`) 3 derived `Optional` finders + `RepositoryConstraintTest` proving the unique/partial-uniques fire as Spring `DataIntegrityViolationException` + behavioral `@Version` (increment + `ObjectOptimisticLockingFailureException`) (10 tests) — **Phase 1 entity/repo layer COMPLETE**; 2.1 (`922211e`) Auth0 JWT decoder (RS256-only, eager OIDC discovery, issuer+audience mandatory + startup fail-fast, custom `AudienceValidator`, secure-by-default `demo-auth.enabled` gate) (12 tests, no live Auth0) — **Phase 2 BEGUN**. Plus implementer session doc 003 (`5f2a6c6`). All 100% covered; `./gradlew check` green across all 3 modules.
- **Decisions made:** three entity base-class shapes (`PersistableUuidEntity` mutable+versioned / `AbstractAuditingEntity`+inline `@Id` audited-non-versioned / inline `@Id`+own-timestamp for AuditEvent+projections — no 0.3-base refactor); flat-`UUID` FKs throughout (no `@ManyToOne`); Hibernate-6 `@JdbcTypeCode` for `text[]`→`List<RiskBadge>` + `jsonb`→`String` (needs `starter-data-jpa`, not bare `spring-data-jpa`); JPA auditing populator **deferred to Phase 2** (needs the 2.4 principal — resolves the 1.2 carry-forward); **Decision A on the Auth0 decoder** — eager `withIssuerLocation` discovery (fail-fast on issuer misconfig; lazy `withJwkSetUri` documented fallback); `demo-auth.enabled` is the canonical mode property (2.1/2.3/2.6) with **fail-secure** gating + base/prod=real (secure-by-default, fails closed), local/demo=demo mode.
- **Finding handled (orch↔impl, no human escalation):** the Step-2.5 premise "`withIssuerLocation` is lazy" was wrong — it does **eager** OIDC discovery at build (impl caught it via a test failure; deferred to the evidence). Resolved as **Decision A** (keep eager + MockWebServer-stub the discovery endpoint to cover the bean build on real code, not a JaCoCo exclusion). Documented in ARCHITECTURE Appendix C.2 realized-tree + LESSONS §12.
- **Cross-doc work (orchestrator hot-routed):** `apps/wc-api/LESSONS.md` **§7–§12** (JPA entity mapping conventions; Hibernate-6 array/jsonb recipes; `@DataJpaTest` fidelity harness; SpotBugs EI/EI2-on-Lombok-mutable-collections; repo-layer finders + `@DataJpaTest` constraint/`@Version` patterns; SS6 OAuth2 resource-server decoder recipe); `apps/wc-api/CLAUDE.md` index rows §7–§12 + a JPA-entity-layer cross-doc table row + repo-layer-proof notes on the partial-unique/`@Version` rows; `ARCHITECTURE.md` Appendix C.2 realized-tree reconciliation (`:shared`/`:api` JPA + oauth2 dep additions, entity base-class strategy, the eager-discovery auth realization, the `demo-auth.enabled` fail-secure gating + profile semantics); briefs `010`-1.5 / `013`-1.6 / `014`-2.1.
- **Coordination:** resolved a **brief-number collision** with `st6-main-wc-web-orchestrator` (both incremented from 009) — frontend keeps `011`-9.3/`012`-9.2, backend took `013`/`014`, frontend resumes brief numbering at **015**. Frontend took **Path B** (round-2 doc commit `9f17c3c` staged frontend-owned files only, deferring its MVP_TASKS/ARCHITECTURE edits) → this backend round-commit owns the shared-doc round; on resume the frontend pings before staging its deferred Phase-ST/9.x ticks + §7/D.1 notes (handshake set; frontend idling-in-place = continuity).
- **Carry-forward triage:** drained the 1.2 JPA-auditing item (retargeted → Phase 2 after 2.4 `PrincipalResolver`); added 3 items from 2.1 — (2.6) custom-decoder-wins-over-Boot-autoconfig no-collision proof; (§15/2.5) decide whether authn-layer JWT rejections get an audit breadcrumb; (Phase 12/infra) pin `DEMO_AUTH_ENABLED` absent/false in real-mode manifests.
- **⏸ PAUSE (user-on-demand, via lead):** team paused after 2.1; orchestrator HOLDS (idle in place, no teardown), no Phase-2 continuation until the user resumes.
- **Next session target (on resume):** **2.2** (configurable Auth0 claim mapper, F.1 defaults), then 2.3 (env-gated demo filter — **SAFETY-CRITICAL, rule #5**) + 2.4 (PrincipalResolver) + 2.5 (central `DomainAuthorizationService` — **SAFETY-CRITICAL, rule #3 IDOR**) + 2.6 (SecurityConfig — wires the 2.1 decoder) + 2.7 (CORS + `GET /api/me`). `0.7`/`0.8` still parked.
- **Reference:** implementer session doc `003-2026-06-02-phase1-entities-repos-and-phase2-jwt.md` for technical detail.
