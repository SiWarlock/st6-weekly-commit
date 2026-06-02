# Session 001 — Phase 0 backend spine + Phase 1 schema trio (V1/V2/V3)

- **Date:** 2026-06-02
- **Phase:** 0 (Monorepo & toolchain foundation) → 1 (Physical schema)
- **Role:** `st6-main-wc-api-implementer` (backend)
- **Predecessor session:** _(none — first implementer session; follows bootstrap commit `72c149a`)_
- **Successor session:** _(TBD — next backend slice is 1.5 JPA entities)_
- **Commits this session (7 slices):** `555c2a8` (0.1) · `d888f92` (0.2) · `e8b5305` (0.3) · `7c4b649` (0.4+0.5) · `b230ad0` (1.2) · `a2230c5` (1.3) · `c1ae1f1` (1.4). _(The frontend track's `2a307b8 docs(design)` interleaved — not this session's.)_

## Why this session existed

Stand up the backend from nothing: the JS monorepo root, the Gradle multi-module build + quality gates, the `:shared` enum/config/base-entity foundation, the two bootable Spring Boot apps, and the full physical-schema layer (Flyway V1/V2/V3) proven on real PostgreSQL 16. Frontend was deferred pending the design system (handled on a parallel track).

## What was built

### Files created (by slice)
- **0.1 monorepo-root** — root `package.json` (Yarn Workspaces over `apps/*`), `nx.json`, `.yarnrc.yml`, `.nvmrc`, `apps/wc-web/package.json` + `apps/wc-e2e/package.json` stubs, `infra/{terraform,k8s}/.gitkeep`, `scripts/verify-workspace.sh`, generated `yarn.lock`.
- **0.2 gradle-multimodule** — repo-root + `apps/wc-api` Gradle composite (`settings.gradle`/`build.gradle`/`gradle.properties`), dual Gradle wrappers (8.10.2), per-module `shared/api/worker` build files, `gradle/libs.versions.toml`, `lombok.config`, `config/spotbugs/exclude.xml`, `scripts/verify-gradle.sh`, throwaway `BuildSkeletonMarker`(+test) per module.
- **0.3 shared-enums-common** — 16 enums (`com.st6.wc.enums`), `common/{AbstractAuditingEntity,PersistableUuidEntity,OrgTimeConfig}.java`, `config/ClockConfig.java`, 5 test files.
- **0.4+0.5 spring-boot-apps** — `WcApiApplication`, `config/OrgTimeBindingConfig` (ORG_TIMEZONE fail-safe), 5 api YAMLs; `WcSyncWorkerApplication`, `worker/config/WorkerSharedConfig`, 4 worker YAMLs; 6 test files.
- **1.2 flyway-v1-core-schema** — `db/migration/V1__core_schema.sql` (12 core tables), `migration/V1CoreSchemaMigrationTest.java`.
- **1.3 flyway-v2-partial-unique-indexes** — `db/migration/V2__partial_unique_indexes.sql` (3 partial uniques), `migration/V2PartialUniqueIndexTest.java`.
- **1.4 flyway-v3-projection-tables** — `db/migration/V3__projection_tables.sql` (2 projection read-models), `migration/V3ProjectionTablesTest.java`.

### Files modified (within this session's own commits)
- `.gitignore` — JS/Yarn/Nx ignores (0.1) + Gradle ignores (0.2).
- `apps/wc-api/build.gradle` — `forbidLombokData` comment-strip narrowing (0.3); JaCoCo `*Application` exclusion (0.4+0.5).
- `apps/wc-api/shared/build.gradle` — Spring BOM + spring-context + starter-test (0.3); Flyway/Testcontainers/PG test deps + `testcontainers-bom:1.21.4` override (1.2).
- `apps/wc-api/{api,worker}/build.gradle` — Spring Boot plugin + web/actuator/test starters (0.4+0.5).
- `apps/wc-api/gradle/libs.versions.toml` — spring-boot plugin alias (0.4+0.5).
- `apps/wc-api/config/spotbugs/exclude.xml` — `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE` exclusion for `com.st6.wc.migration.*` (1.2).
- `apps/wc-api/{api,worker}/.../BuildSkeletonMarker.java` — self-contained de-reference (0.3); then removed (0.4+0.5).
- `V1CoreSchemaMigrationTest` + `V2PartialUniqueIndexTest` — Flyway `.target()` version-scoping (1.4).
- _(Removed across slices: the `:shared`/`:api`/`:worker` `BuildSkeletonMarker`(+test) placeholders, replaced by real code.)_

## Decisions made
- **JS toolchain:** Yarn Berry 4.5.3 via Corepack (`packageManager` pin), `nodeLinker: node-modules`; determinism via committed `yarn.lock`.
- **Gradle:** 8.10.2 wrapper pinned (final release + sha256) at root + `apps/wc-api`; foojay-resolver + `JAVA_HOME` for the JDK-21 toolchain (no committed machine path); per-module Spotless(google-java-format)/SpotBugs(MAX)/JaCoCo ≥80% line+branch + `forbidLombokData` + `checkModuleBoundaries` bound into `check`.
- **`ClockConfig` in `:shared`** (cross-cutting); `:shared` carries Spring (BOM + spring-context) — consistent with its Phase-1 destiny.
- **`PersistableUuidEntity extends AbstractAuditingEntity`** (composition) — one `@MappedSuperclass` chain for PK + version + audit.
- **`ORG_TIMEZONE` fail-safe** as a context-free static `OrgTimeBindingConfig.resolveZone` (unset/blank/invalid ⇒ America/Chicago + WARN, never UTC) — reusable by the D.4 CronJob.
- **Schema encoded per §4 + the 4 contract deltas + the 0.3 enums** (NOT the stale `DATA_MODEL.md`): `manager_alignment_note` present, `progress_status` absent, comment flat-but-nestable ({PLAN,COMMITMENT}, path nullable), sync `week_start_date` + `MANAGER_REVIEW_WEEK`. `@Version` = `bigint`; audit cols nullable; `audit_event`/projections carry only their own timestamp (no audit quartet/version).
- **`risk_badges`** vocabulary DB-enforced via a `<@` array-containment CHECK + pinned to `RiskBadge.values()`.
- **Testcontainers BOM overridden to 1.21.4** (over Spring Boot 3.3.5's 1.19.8) — older docker-java 400s on the local Docker Engine 29 API.
- **Version-scoped migration tests** — each test `Flyway.target("N")` so it validates its own version's schema state (future-proofs against V4–V6).

## Decisions explicitly NOT made (deferred)
- **Cross-column `unplanned ⇒ work_type='UNPLANNED'` CHECK** — deferred to service validation (the commitment-service slice decides if it's a real invariant); not in V1 DDL.
- **JPA entities/repos** — Phase 1 task 1.5 (entities map V1–V3 tables; `extends PersistableUuidEntity`).
- **Projection-population logic / `ProjectionService`** — much later phase (the V3 tables are DDL-only).
- **Seed migrations (V4–V6)** + the `:api FlywayMigrateRunner` (D.5 Migration Job) — later phases.
- **`is_review_overdue` derivation** — read-time service derivation (§9/safety-rule-#6); the V3 column is denormalized read-model only.

## TDD compliance
**Clean — no violations.** Every slice was test-first with a confirmed RED before GREEN: executable verification-gate scripts for the build-wiring slices (0.1/0.2), compile-fail/red-green JUnit for 0.3/0.4+0.5, and real-Testcontainers red-green for 1.2/1.3/1.4. No orphaned RED; no implementation-before-test.

_`/session-end` audit fix (build-config correctness, not new behavior):_ the preflight gate surfaced a latent Gradle task-dependency issue in `apps/wc-api/build.gradle` — the 0.4+0.5 JaCoCo `*Application`-exclusion reconfiguration built `classDirectories` from `sourceSets.main.output` (which includes `resources/main`) and didn't declare the class-producer dependency, so `jacocoTestCoverageVerification` "used" `compileJava`/`processResources` outputs without a declared dependency (fails `build -x test`; masked under `check`). Fixed: base `classDirectories` on `output.classesDirs` only + `dependsOn classes`. `clean check` + the 9 verify-gradle gates stay green.

## Preflight
**Clean via the project's real gate.** From `apps/wc-api`: SpotBugs ✓ + Spotless ✓ + `compileJava` ✓ + `test` ✓ (83 backend tests, Testcontainers PG16) + `clean check` ✓; `verify-gradle.sh` 9/9. See the tooling follow-up below re: the preflight-skill backend steps.

## Reachability
- **0.1/0.2** — reachable via `yarn`/`nx` + `./gradlew check` (root composite + standalone `apps/wc-api`); both verification scripts re-runnable; CI wiring is task 0.8 (sequenced).
- **0.3** — `:shared` foundation; `ClockConfig` now in both production app contexts (0.4+0.5 closed the flag-6 wiring TODO); enums consumed by V1 CHECKs + future entities; base superclasses reachable when Phase-1 entities extend them (1.5).
- **0.4+0.5** — both apps boot; actuator liveness/readiness served over HTTP; distinct bootJars build; CI assembly is 0.8.
- **1.2/1.3/1.4** — migrations applied by the Flyway test harness (proven). **Standing sequenced follow-up:** production application is the `:api FlywayMigrateRunner` / D.5 Migration Job (later phase) — see Open follow-ups. No tested-but-orphaned code (all placeholders were removed/replaced).

## Open follow-ups

### Step-9 categorized items (surfaced for `/orchestrate-end` to verify routing — the orchestrator hot-routed these during the session)
- **Cross-doc invariant — NEW/extended** (→ `apps/wc-api/CLAUDE.md` + §-confirm): enum vocabulary (0.3); core schema → §4/Appendix A (1.2); 3 partial-unique clauses → extended (1.3); 2 projection models + `risk_badges` vocab (1.4).
- **Doc reconciliation** (→ orchestrator): `docs/planning/DATA_MODEL.md` stamped "superseded by §4 + Appendix A + the 4 deltas" (surfaced 1.2).
- **Architecture-doc notes** (→ Appendix C.1/C.2/C.3/D.2/D.3 realized-tree reconciliations): dual wrappers/version catalog/foojay/composite (0.1/0.2); `:shared` Spring + composition + `ClockConfig` placement (0.3); Spring Boot plugin/worker starter-web/`OrgTimeBindingConfig`/`WorkerSharedConfig`/JaCoCo `*Application` exclude (0.4+0.5).
- **Convention candidates** (→ `apps/wc-api/LESSONS.md` §1–§5): Yarn-Corepack pin + `command -v`/`type -P` footgun (0.1); Gradle gate-wiring recipe + SpotBugs `Confidence.valueOf` Groovy/Kotlin-enum gotcha (0.2); enum value-set parameterized-test + `forbidLombokData` comment false-positive (0.3); Spring Boot skeleton pattern + `ConfigDataApplicationContextInitializer` package gotcha (0.4+0.5); Flyway+Testcontainers harness + enum↔CHECK + **`testcontainers-bom:1.21.4` override (Docker 29)** + `text[]` vocab pattern + version-scoped migration tests (1.2–1.4).
- **`.gitignore` changes** (pre-approved area-rule exception): JS/Yarn/Nx (0.1) + Gradle (0.2).
- **Future TODO — belongs to a phase:** 0.8 CI must invoke the verification scripts + `./gradlew check` + pin JDK 21 + assemble bootJars; D.4 CronJob + D.5 Migration Job reuse `OrgTimeBindingConfig.resolveZone` (no dup); **the `:api FlywayMigrateRunner` (D.5) is the production entry point that applies V1/V2/V3** in deploy.

### Reachability follow-up
- The V1/V2/V3 migrations are reachable from the test harness only until the **`:api FlywayMigrateRunner` / D.5 Migration Job** slice wires production application. Categorized "Future TODO — belongs to a phase" (already in the orchestrator's tracking).

### Tooling note (Convention candidate — for the orchestrator)
- **The `/preflight` backend steps don't fit this project's Gradle shape.** (1) They must run from `apps/wc-api/` — the repo-root composite (`st6-wc`) only exposes the aggregate `check`/`build`, not `compileJava`/`spotbugsMain`/`spotlessCheck`/`test`. (2) `build -x test` is not a valid gate here: `jacocoTestCoverageVerification` is bound to `check` and cannot verify coverage without running tests. **Recommendation:** the backend preflight gate should be `./gradlew check` (run from `apps/wc-api`), which is this project's real CI gate (§13). Worth banking as a convention + possibly a `/preflight` skill tweak.

## How to use what was built
- Backend build: `./gradlew check` (repo root composite or from `apps/wc-api`). Verification gates: `scripts/verify-workspace.sh`, `scripts/verify-gradle.sh`.
- Migration tests need a running Docker daemon (Testcontainers; `testcontainers-bom:1.21.4` for Docker 29). JDK 21 via Homebrew (`~/.zshenv`) — see `docs/runbooks/jdk21-toolchain-setup.md`.
- Next backend slice: **1.5 — JPA entities** mapping the V1–V3 tables (`extends PersistableUuidEntity`; mirror the enum vocabulary + the 4 deltas).
