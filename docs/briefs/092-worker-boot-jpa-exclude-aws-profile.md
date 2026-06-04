# /tdd brief — worker-boot JPA/datasource exclude + aws-profile config

## Feature
Make `wc-sync-worker` boot cleanly in the deployed `aws` profile by excluding the transitively-activated JPA/datasource auto-config at the **production** level (the worker is DB-less until Wave-2) and renaming the dead `application-prod.yml` → `application-aws.yml` so its config actually loads under `SPRING_PROFILES_ACTIVE=aws`.

## Use case + traceability
- **Task ID:** 092 (Wave-1 deploy-completion item; finding from 091 boot-smoke).
- **Architecture sections it implements:** §12 (deploy topology — worker Deployment), Appendix D.3 (worker config contract — SYSTEM principal, graph-only secret mount, no datasource), §13 (profiles). No new contract surface.
- **Related context:**
  - **`docs/planning/025`** (deploy-demo plan), **handoff `015`** (the 092 finding statement).
  - **LESSONS §9** — "a JPA starter on a shared lib activates `DataSourceAutoConfiguration` in every downstream module → `spring.autoconfigure.exclude` it on DB-less skeleton boots." This slice is the **production** realization of §9 (today the exclude exists ONLY as a test-only `@SpringBootTest` property → the prod gap).
  - **LESSONS §42** — the api's 090 fix: `application-prod.yml` was **DEAD** under `SPRING_PROFILES_ACTIVE=aws`; the config filename MUST match the active profile. The worker carries the identical latent bug (only `application-prod.yml`, no `application-aws.yml`).
  - **`apps/wc-api/api/src/main/resources/application-aws.yml`** — the 090 output; mirror its header-comment style for the worker's new `application-aws.yml` (minus the datasource/auth0/cors blocks — the worker needs none of those in Wave-1).
  - **`infra/k8s/deployment-worker.yaml`** — confirms `SPRING_PROFILES_ACTIVE=aws` (line 38) + the `wc-worker-secrets` SPC mount is **graph-only** (no `spring.datasource.*` keys → no driver → the crashloop this slice fixes).

## Root cause (confirmed)
`WcSyncWorkerApplication` is a bare `@SpringBootApplication`. `:worker → :shared`, and `:shared` carries `spring-boot-starter-data-jpa` (the entity layer, task 1.5) → `DataSourceAutoConfiguration` + `HibernateJpaAutoConfiguration` activate transitively in the worker context. The worker mounts **graph-only** secrets (no `spring.datasource.*`), so in `aws` the datasource init fails ("Failed to determine a suitable driver class") → the pod crashloops → the deploy's worker `rollout status` never goes Ready. The worker tests pass **only** because `WcSyncWorkerApplicationTest` sets a **test-only** `spring.autoconfigure.exclude` property — there is no production-side exclude.

## Acceptance criteria (what "done" means)
- [ ] The worker boots under `@ActiveProfiles("aws")` with **NO** `spring.autoconfigure.exclude` test property — context loads, k8s probes UP, `:shared` `Clock` bean present.
- [ ] No `DataSource` bean and no `EntityManagerFactory` bean exist in the worker context under any profile (the exclude is structural, asserted by bean-absence).
- [ ] The production-side exclude lives in the worker's own config/app class (not a test property) — the worker is DB-less in every profile in Wave-1.
- [ ] `application-prod.yml` is renamed to `application-aws.yml`; its `logging.level.com.st6.wc.worker: INFO` now actually applies under `SPRING_PROFILES_ACTIVE=aws`.
- [ ] `WorkerFlywayPropertyTest` asserts flyway stays `false` under the **`aws`** profile (the renamed profile), not the removed `prod`.
- [ ] The existing `WcSyncWorkerApplicationTest` boot assertions stay green with the test-only exclude property **removed** (proving the production exclude alone keeps the boot DB-less).
- [ ] `./gradlew check` clean from `apps/wc-api/` (worker JaCoCo ≥80% preserved).

## Files expected to touch
**Modified:**
- `apps/wc-api/worker/src/main/java/com/st6/wc/worker/WcSyncWorkerApplication.java` — add `@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})` (the production, profile-independent exclude). *(See Step-2.5 Q1 — annotation vs base-yaml `spring.autoconfigure.exclude`; default = annotation.)*
- `apps/wc-api/worker/src/test/java/com/st6/wc/worker/WcSyncWorkerApplicationTest.java` — drop the test-only `spring.autoconfigure.exclude` property (now redundant); add an `aws`-profile boot + bean-absence assertions (or add a sibling test class — see Q2).
- `apps/wc-api/worker/src/test/java/com/st6/wc/worker/WorkerFlywayPropertyTest.java` — `flywayEnabledUnder("prod")` → `flywayEnabledUnder("aws")`.

**Renamed:**
- `apps/wc-api/worker/src/main/resources/application-prod.yml` → `application-aws.yml` — update the header comment to the `aws`-profile framing (mirror the api's `application-aws.yml` opening comment; the worker variant needs no datasource/auth0/cors — Wave-1 has no DB and SQS/Graph wiring lands Wave-2). Keep the `logging.level` block.

If implementation needs files beyond this list, **flag at Step 2.5** before going GREEN.

## RED test outline (Step 2)
Tests in `apps/wc-api/worker/src/test/java/com/st6/wc/worker/`:

1. **`WcSyncWorkerApplicationTest` (revised)** — boots WITHOUT the test-only exclude property.
   - Asserts: `readinessProbe_isUp` / `livenessProbe_isUp` / `clock != null` stay green with the `spring.autoconfigure.exclude` `@SpringBootTest` property **deleted**.
   - Why: the production exclude (app class) alone must keep the boot DB-less — removing the test prop is the proof the prod gap is closed (LESSONS §9 production realization). **RED today** (boot fails on the datasource driver once the property is gone).

2. **`worker_awsProfile_bootsDbLess` (NEW — in the revised test or a sibling `@ActiveProfiles("aws")` class)** — the deployment-fidelity pin.
   - Asserts: context loads under `@ActiveProfiles("aws")` (the profile the worker Deployment actually runs) with no exclude property; probes UP; `Clock` present.
   - Why: pins the exact deployed profile so the §42-style dead-profile bug can't regress; this is the boot path the crashloop occurs on. **RED today**.

3. **`worker_hasNoDataSourceOrJpaBeans` (NEW)** — structural bean-absence.
   - Asserts: `ctx.getBeanNamesForType(javax.sql.DataSource.class)` is empty AND no `EntityManagerFactory` bean (e.g. via `getBeanNamesForType` / `assertThatThrownBy(() -> ctx.getBean(DataSource.class))`).
   - Why: the exclude is structural, not an accident of missing config — so Wave-2 wiring the worker's DB is a deliberate, visible change. Pins LESSONS §9. **RED today** (the beans exist, mis-configured).

4. **`WorkerFlywayPropertyTest.flywayDisabled_underEveryWorkerProfile` (revised)** — `aws` replaces `prod`.
   - Asserts: `flywayEnabledUnder("aws")` == `"false"` (plus the existing base/local/demo cases).
   - Why: the profile rename's test-side mirror; flyway stays worker-disabled (§12/D.3, forbidden-#3). Base yaml sets `enabled: false` so `aws` inherits it — green after the rename, RED-by-reference-to-`prod` before (the removed profile).

## Cross-doc invariant impact (implementer flags at Step 9; orchestrator writes the docs)
- **Model field changes:** **NONE.** No contract DTO / entity / enum / `SyncJobPointer` field changes. The worker module carries no cross-doc invariant table rows.
- **Orchestrator doc rows to write hot (Step 9 routing):** none required. **Candidate** (orchestrator's call at Step 9): a LESSONS **§9-addendum or §42-addendum** capturing "the test-only `spring.autoconfigure.exclude` masks a production crashloop — realize the exclude in prod config + pin the deployed profile by a bean-absence + `@ActiveProfiles(aws)` boot test." No new lesson number unless it stands alone.

## Things to flag at Step 2.5
1. **Where does the production exclude live — app-class annotation vs base-yaml `spring.autoconfigure.exclude`?** Options: (a) `@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})` on `WcSyncWorkerApplication`; (b) `spring.autoconfigure.exclude: <two classes>` in base `application.yml`. **My default vote: (a) the annotation.** It's profile-independent (the worker is DB-less in local/demo/aws alike in Wave-1), self-documenting on the app class, type-safe (class refs not strings), and lets us delete the test-only property cleanly. Wave-2 removes the annotation at the exact slice that adds the worker datasource — a visible one-line diff. (b) is equivalent but buries it in yaml.
2. **Revise the existing test in place, or add a sibling `aws`-profile test class?** **My default vote: revise `WcSyncWorkerApplicationTest` in place** — drop the test-only property, keep it `@ActiveProfiles("local")` for the probe/Clock assertions, and ADD one `@ActiveProfiles("aws")` boot + the bean-absence assertions (a nested `@Nested` class or a small sibling class is fine to avoid `@ActiveProfiles` collisions). Keep the worker test footprint minimal (it's a 3-test skeleton today).
3. **Exclude set — the two classes the existing test already proves sufficient, or add `JpaRepositoriesAutoConfiguration`/`DataSourceTransactionManagerAutoConfiguration`?** **My default vote: just the two** (`DataSourceAutoConfiguration` + `HibernateJpaAutoConfiguration`) — the existing test boots green with exactly these, the worker doesn't `@EnableJpaRepositories` (no repo scan), and the api excludes nothing, so mirroring the proven-sufficient pair keeps it minimal. Add more only if a boot actually complains.
4. **Wave-2 forward-note:** the worker WILL need a datasource at Wave-2 (the SQS consumer reloads `SyncRecord` by id). Confirm we're comfortable that the exclude is removed/narrowed at that slice (not now). Default: yes — Wave-1 worker is intentionally DB-less; document the removal point in the session doc.

## Dependencies + sequencing
- **Depends on:** 091 (Dockerfiles — `bee7626`); 090 (the api's `application-aws.yml` pattern this mirrors — `6870a4f`).
- **Blocks:** the first deploy's worker `rollout status` (HITL-gated, so not time-critical) and the Wave-2 worker SQS-consumer slice (which removes the exclude + adds the datasource).

## Estimated commit count
**1.** Not a safety-invariant slice (rules #4/#7 are Wave-2; this is a deploy-boot config fix touching no authorization / lifecycle / sync-record write path). The app-class exclude + the profile rename + the test updates are one logical unit (the same boot path), bisectable as one commit. **No security review** per the reviewer policy (no rule-touching surface).

## Lessons-logged candidates anticipated
- **Convention candidate (LESSONS §9/§42 addendum)** — a test-only `spring.autoconfigure.exclude` can mask a production crashloop; realize the exclude in prod config + pin the deployed profile with a bean-absence + `@ActiveProfiles(<deployed profile>)` boot test. Folds into §9 (the JPA-on-shared-lib lesson) or §42 (the dead-profile-filename lesson).
- **Architecture-doc note candidate (low)** — Appendix D.3 could note the worker is JPA-excluded until Wave-2 wires its datasource. Orchestrator's call at Step 9.

## How to invoke
1. Read this brief end-to-end (esp. Step-2.5 Q1 — the exclude placement).
2. `/tdd worker-boot-jpa-exclude-aws-profile` in the implementer session.
3. Step 0 (Restate) — confirm it matches the Feature line.
4. Step 1 (Identify files) — confirm against Files expected to touch.
5. Step 2.5 — ping back with answers to the 4 design questions (or take defaults).
6. Step 9 — surface anything beyond the anticipated lessons-logged candidates.
