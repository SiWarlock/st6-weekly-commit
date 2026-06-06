# Scheduled Jobs & Batch Entry Points

## Executive summary

This layer is the set of **non-HTTP ways the backend runs**. The same `wc-api` Docker image that normally serves the REST API can instead be launched as a one-shot batch task — and which batch it runs is decided entirely by Spring profiles and a single `--app.job=<name>` launch argument. Three batch entry points exist in the running system: a **weekly cron** that creates next week's empty (`DRAFT`) plan shells for every active employee, a **rebuild Job** that recomputes the manager dashboard read-models from source, and a **Flyway migration Job** that is the sole owner of database schema migration + seed. A fourth — a synthetic **perf-seed Job** — is described in the architecture and has a Kubernetes manifest, but its in-code runner is **not implemented** (a confirmed drift, see Gotchas). The whole point of this design is that no batch needs a second Docker image: the image stays one artifact, and a profile/arg flip turns it into "api OR generation-cron OR migration OR rebuild." This layer owns the *gating + activation* of those batches and their thin runner shells; it delegates the actual domain logic to other layers (plan-shell domain → layer 03, projection rebuild → layer 05) and all the cloud/IAM wiring → layer 10.

## Responsibilities

- **Accountable for:** the *activation seam* of every non-HTTP entry point — i.e. the Spring beans that decide whether a launched JVM behaves as the web API or as a one-shot batch, and the thin `ApplicationRunner` shells that fire the batch logic once and let the JVM exit. Specifically: `PlanShellGenerator` + `PlanShellGenerationRunner` (generation cron), `ProjectionRebuildRunner` (rebuild Job), the `flyway-migrate` profile config (migration Job), the base `app.job`/`spring.flyway.enabled` gating, and the matching k8s `CronJob`/`Job` manifests at a *what-it-runs* level.
- **NOT accountable for** (delegated):
  - The plan-shell *domain* rules — `WeeklyPlan` shape, lifecycle, `unique(employee_id, week_start_date)` constraint semantics → **layer 03** (Application & Lifecycle). This layer only owns the generation *loop + idempotency pre-filter*.
  - The projection *rebuild logic* — `ProjectionRebuilder.rebuild()`, `ProjectionRefresher.recomputeForPlan`, the count/badge derivation → **layer 05** (Manager Projections). This layer owns only the `ProjectionRebuildRunner` shell that calls it.
  - The k8s/IAM/IRSA wiring — service accounts, secret mounts (CSI/SPC), the ECR image, deploy ordering / pipeline wait-gates → **layer 10** (Infrastructure & Deployment).
  - The Flyway migration *content* (V1–V6 SQL, seed data) → **layer 01** (Domain & Persistence) / layer 10. This layer owns only the profile that *enables* Flyway.

## Key components

| Component | What it does | Where |
|-----------|--------------|-------|
| `PlanShellGenerator` | The inert generation logic: creates one `DRAFT` `WeeklyPlan` shell per `active=true` employee for the current org-tz week; idempotent via an existence pre-filter; writes one SYSTEM-actor audit row. One `@Transactional` run. | `apps/wc-api/api/src/main/java/com/st6/wc/job/PlanShellGenerator.java:69` |
| `PlanShellGenerationRunner` | Thin `ApplicationRunner` gated on `--app.job=generate-plan-shells`; delegates to `generate()` then logs. Inert in the web image. | `apps/wc-api/api/src/main/java/com/st6/wc/job/PlanShellGenerationRunner.java:31` |
| `ProjectionRebuildRunner` | Thin `ApplicationRunner` gated on `--app.job=rebuild-projections`; delegates to `ProjectionRebuilder.rebuild()` (cross-link layer 05). Inert in the web image. | `apps/wc-api/api/src/main/java/com/st6/wc/job/ProjectionRebuildRunner.java:24` |
| `ProjectionRebuilder` | (Owned by layer 05) Truncate-and-recompute both manager projection tables from source. Invoked *by* `ProjectionRebuildRunner`. | `apps/wc-api/api/src/main/java/com/st6/wc/projection/ProjectionRebuilder.java:58` |
| `WcApiApplication` | The single Spring Boot entry point (`main`) shared by api + every batch. Component scan at `com.st6.wc` picks up the runners + the gating config. | `apps/wc-api/api/src/main/java/com/st6/wc/WcApiApplication.java:15` |
| `app.job` / `spring.flyway.enabled` base gating | Base config defaults `app.job` blank (runners inert) and `spring.flyway.enabled=false` (api never migrates). | `apps/wc-api/api/src/main/resources/application.yml:14`, `:40` |
| `application-flyway-migrate.yml` | The migration profile: `flyway.enabled=true` over **two** locations (`db/migration` + `db/demo-seed`); `web-application-type=none`. The ONLY profile that migrates. | `apps/wc-api/api/src/main/resources/application-flyway-migrate.yml:8` |
| `OrgTimeBindingConfig.resolveZone` | The org-tz fail-safe reused by generation: blank/garbage zone ⇒ `America/Chicago` + WARN, never UTC, never throws. | `apps/wc-api/api/src/main/java/com/st6/wc/config/OrgTimeBindingConfig.java:36` |
| `cronjob-generation.yaml` | k8s `CronJob` — Monday 06:00, `--app.job=generate-plan-shells`, `concurrencyPolicy: Forbid`, `restartPolicy: Never`. | `infra/k8s/cronjob-generation.yaml:18` |
| `job-migration.yaml` | k8s `Job` — `flyway-migrate` profile, `SPRING_FLYWAY_ENABLED=true`, `restartPolicy: Never`. Sole schema owner. | `infra/k8s/job-migration.yaml:18` |
| `job-rebuild-projections.yaml` | k8s `Job` — `--app.job=rebuild-projections`, `restartPolicy: Never`, `activeDeadlineSeconds: 600`. | `infra/k8s/job-rebuild-projections.yaml:26` |
| `job-perf-seed.yaml` | k8s `Job` — opt-in synthetic seed, `--app.job=generate-perf-seed`. **NOTE: no matching in-code runner exists** (see Gotchas). | `infra/k8s/job-perf-seed.yaml:34` |

## Interfaces & contracts

**Activation contract (the load-bearing seam).** A batch is selected by **one launch argument** plus a profile, fed into the *same* `WcApiApplication.main`:

```
--app.job=generate-plan-shells   + SPRING_PROFILES_ACTIVE=aws,generation       → PlanShellGenerationRunner fires
--app.job=rebuild-projections    + SPRING_PROFILES_ACTIVE=aws                  → ProjectionRebuildRunner fires
(no --app.job)                   + SPRING_PROFILES_ACTIVE=aws,flyway-migrate   → Flyway runs, no runner, web off
(no --app.job)                   + (api profile)                              → normal REST web server
```

Each runner is gated by `@ConditionalOnProperty`:

```java
@ConditionalOnProperty(name = "app.job", havingValue = "generate-plan-shells")  // PlanShellGenerationRunner:30
@ConditionalOnProperty(name = "app.job", havingValue = "rebuild-projections")   // ProjectionRebuildRunner:23
```

**Runner method contract** (`org.springframework.boot.ApplicationRunner`):

```java
void run(ApplicationArguments args)   // delegates once to the logic component, logs the count
```

`PlanShellGenerationRunner.run` calls `generator.generate()` (`PlanShellGenerationRunner.java:43`); `ProjectionRebuildRunner.run` calls `rebuilder.rebuild()` (`ProjectionRebuildRunner.java:36`). Neither holds the `ApplicationContext` nor calls `SpringApplication.exit` — termination is delegated to the launch arg (see below).

**Generation logic contract** (`PlanShellGenerator.generate`, `PlanShellGenerator.java:69`):

```java
@Transactional int generate()   // returns count of shells created (0 on rerun / empty active roster)
```

- **Input:** the current `Clock.instant()` (injectable) + the active employee roster + existing shells for the target week.
- **Output:** N new `DRAFT` `WeeklyPlan` rows (Mon–Sun org-tz bounds) + exactly one `audit_event` (action `PLAN_SHELLS_GENERATED`, SYSTEM/null actor, safe metadata `{week_start_date, shells_created_count}`).
- **Expects from others:** `EmployeeRepository.findByActiveTrue()`, `WeeklyPlanRepository.findByWeekStartDate(weekStart)`, `OrgTimeConfig.weekStartDate/weekEndDate`, `AuditService.record(...)`.

**One-shot termination contract.** A batch container does NOT self-exit in code. It is launched with `--spring.main.web-application-type=none`; with no web server, once the `ApplicationRunner` returns, `SpringApplication.run` returns, `main` exits, the JVM terminates (only daemon threads remain). This is documented in the runner javadoc (`PlanShellGenerationRunner.java:18-22`), the base yaml comment (`application.yml:36-39`), and pinned as infra LESSONS §22 (a Job launching such a runner WITHOUT the arg starts a web server and hangs to `backoffLimit`).

## Data & state

- **`WeeklyPlan` shells written by generation:** `id` (random UUID), `employeeId`, `weekStartDate` (Monday), `weekEndDate` (Sunday), `state=DRAFT`, `generatedAt=now` (`PlanShellGenerator.java:85-92`). No commitments, no sync records, no notes (REQ-I-002, pinned by `createsZeroSyncRecordsAndZeroCommitments`).
- **Idempotency state:** the set of `employeeId`s that already have a shell for the target week, loaded once into a `Set<UUID>` (`PlanShellGenerator.java:75-78`) and used as a skip pre-filter. The DB backstop is the `unique(employee_id, week_start_date)` constraint from V1 (Domain layer 01 / cross-doc invariant `Core schema`).
- **Audit rows:** SYSTEM-actor (`actor_employee_id=null`) `audit_event` rows — `PLAN_SHELLS_GENERATED` per generation run (`PlanShellGenerator.java:40`, `:109`), `PROJECTIONS_REBUILT` per rebuild run (`ProjectionRebuilder.java:30`, `:87`). Metadata is built via an escaped Jackson `ObjectNode`, never string-concat (rule #7 safe-metadata; wc-api LESSONS §18).
- **Profile state:** `app.job` (`${APP_JOB:}` blank default, `application.yml:40`), `spring.flyway.enabled` (`false` base, `application.yml:14`; `true` only in `application-flyway-migrate.yml:8`), `app.org.timezone` (`${ORG_TIMEZONE:}` blank passthrough → fail-safe, `application.yml:43`).
- **Migration locations:** `classpath:db/migration` (V1–V4 schema + RCDO reference) **and** `classpath:db/demo-seed` (V5/V6 personas + fixtures), loaded only by the migrate profile (`application-flyway-migrate.yml:9`; cross-doc invariant wc-api LESSONS §41).

## Dependencies

- **Depends on:**
  - **Layer 03 (Application & Lifecycle / Domain):** `WeeklyPlanRepository`, `EmployeeRepository`, `WeeklyPlan`/`Employee` entities, `PlanState.DRAFT` — generation writes plan-shell rows only.
  - **Layer 05 (Manager Projections):** `ProjectionRebuilder.rebuild()` — the rebuild runner is a pure delegator into layer 05's logic.
  - **Layer 04 (Audit):** `AuditService.record(...)` for the SYSTEM-actor run-audit.
  - **Shared `OrgTimeConfig` + `OrgTimeBindingConfig`:** org-tz week resolution + fail-safe.
  - **Spring Boot `ApplicationRunner` / `@ConditionalOnProperty` / `Clock`:** the framework gating + injectable clock.
  - **Layer 10 (Infrastructure):** the k8s `CronJob`/`Job` manifests, the SAME ECR `wc-api` image, IRSA SAs + CSI secret mounts that actually *launch* these batches in the cluster.
- **Used by:**
  - **Kubernetes scheduler** — the `wc-generation` `CronJob` fires `PlanShellGenerationRunner` weekly.
  - **The deploy pipeline** (layer 10) — runs `job-migration` (gated wait), then `job-rebuild-projections`, then rolls Deployments (§12 ordering).
  - **Operators on demand** — `job-perf-seed` is applied manually (excluded from the deploy chain).
  - **Downstream of generation:** the IC weekly lifecycle (layer 03) — every locked plan starts as one of these `DRAFT` shells. Downstream of rebuild: the manager read surface (layer 05).

## How it works (flow)

**One image, four behaviors — chosen by profile + `app.job`:**

```
                  SAME wc-api image  +  WcApiApplication.main(args)
                                  │
        ┌─────────────────┬───────┴────────┬──────────────────────┐
   (no --app.job)   --app.job=         --app.job=          (flyway-migrate
   api profile      generate-plan-     rebuild-projections  profile, no job)
        │           shells                  │                     │
   web server   PlanShellGen-        ProjectionRebuild-      Flyway runs V1–V6
   stays up     Runner.run()         Runner.run()            web off → JVM exits
                → generate()         → rebuild()  (layer 05)
                → JVM exits          → JVM exits
```

**Generation cron path** (the main one this layer owns):

1. k8s `CronJob` `wc-generation` fires at `0 6 * * 1` (Monday 06:00), launching the `wc-api` image with `--app.job=generate-plan-shells --spring.main.web-application-type=none`, profiles `aws,generation` (`cronjob-generation.yaml:18`, `:38-45`).
2. `@ConditionalOnProperty` matches → `PlanShellGenerationRunner` becomes a bean; it auto-fires after context startup (`PlanShellGenerationRunner.java:42`).
3. `run()` calls `generator.generate()` (`PlanShellGenerationRunner.java:43`).
4. `generate()` resolves `now`, then `weekStart` (Monday) + `weekEnd` (Sunday) via `OrgTimeConfig` — org-tz, fail-safe-resolved (`PlanShellGenerator.java:70-72`).
5. **Idempotency pre-filter:** load the `employeeId`s that already have a shell for `weekStart` into a `Set` (`PlanShellGenerator.java:75-78`).
6. For each `active=true` employee not in that set, build + save a `DRAFT` shell (`PlanShellGenerator.java:81-94`).
7. Write one SYSTEM-actor audit row + log the count (`PlanShellGenerator.java:96-97`).
8. `run()` returns → no web server → `main` returns → JVM exits one-shot (`PlanShellGenerationRunner.java:44`).

**Rebuild Job path:** same shape — `job-rebuild-projections.yaml:40-42` launches `--app.job=rebuild-projections`; `ProjectionRebuildRunner.run` calls `rebuilder.rebuild()` (truncate + recompute-from-source, layer 05); JVM exits.

**Migration Job path:** `job-migration.yaml:32-35` sets profiles `aws,flyway-migrate` + `SPRING_FLYWAY_ENABLED=true`; no `--app.job`, so no runner. Flyway runs V1–V6 across both locations; `web-application-type: none` (`application-flyway-migrate.yml:11`) means the JVM exits after migration. App services do not start in this profile (D.5).

**Inertness proof:** on a normal web boot (no `--app.job`), `PlanShellGenerationRunner` is *not* a bean — pinned by `inertWithoutAppJobArg` (`PlanShellGenerationRunnerTest.java:103`) asserting an `@Autowired(required=false)` runner is `null`, and by the gating tests using `ApplicationContextRunner` (which evaluates `@ConditionalOnProperty` *without* auto-firing the runner — `PlanShellGenerationRunnerGatingTest.java:28`, `ProjectionRebuildRunnerGatingTest.java:29`).

## Design decisions & rationale

- **One image, profile/arg-switched (no third image).** Forbidden-pattern #5 (infra) + §8/§19 reject a separate CronJob/migration image. The same `wc-api` ECR image is reused with `--app.job` + profiles; every manifest comment restates this (`cronjob-generation.yaml:2-3`, `job-migration.yaml:3-4`, `job-rebuild-projections.yaml:2-3`, `job-perf-seed.yaml:5-6`). Anchors: **§13 / §8 / Appendix D.4–D.5**.
- **EKS CronJob over Spring `@Scheduled`.** §19 explicitly rejects `@Scheduled` to avoid multi-replica scheduler races — the cluster scheduler owns timing, the app owns only the one-shot logic. `concurrencyPolicy: Forbid` (`cronjob-generation.yaml:19`) guarantees runs never overlap.
- **Thin runner + plain logic component (split for testability).** All logic lives in a plain `@Component` (`PlanShellGenerator`/`ProjectionRebuilder`); the runner is a thin `@ConditionalOnProperty` `ApplicationRunner`. The split lets `ApplicationContextRunner` prove gating *without* auto-firing the runner mid-test (a `@SpringBootTest` can't host an auto-firing runner). Documented in `PlanShellGenerationRunner.java:24-27`; wc-api LESSONS §23 / §40.
- **`web-application-type=none` termination, NOT `SpringApplication.exit`.** Holding the context to call `exit` stores an externally-mutable singleton (SpotBugs `EI_EXPOSE_REP2`) for no benefit over the no-web-server exit (`PlanShellGenerationRunner.java:18-22`). The launch arg is therefore mandatory; `activeDeadlineSeconds` is the operational backstop (`cronjob-generation.yaml:26`, `job-rebuild-projections.yaml:28`). Infra LESSONS §22.
- **Idempotency via existence pre-filter + unique backstop (no exception control-flow).** Generation skips already-present employees up front rather than catching constraint violations; the `unique(employee_id, week_start_date)` index is only the DB safety net (`PlanShellGenerator.java:27-29`). Pinned by `rerun_isIdempotent_noDuplicateNoException` (`PlanShellGenerationRunnerTest.java:133`). Anchors: **Appendix D.4** ("Idempotent via `unique(employee_id, week_start_date)`").
- **Re-runnable migrations.** Flyway is versioned + the seed is `INSERT … ON CONFLICT DO NOTHING` (Appendix E Part 2) — re-running the migration Job on an existing DB is a no-op. The migrate profile is the *only* one with `flyway.enabled=true` (forbidden-pattern #3 / §12 single-owner race resolution), pinned as production config. Anchors: **Appendix D.5 / D.6 / §13**.
- **Org-tz fail-safe at generation.** Generation reuses the SAME `OrgTimeBindingConfig.resolveZone` fail-safe as the web app — blank/garbage `ORG_TIMEZONE` ⇒ `America/Chicago` + WARN, never UTC, never throws (`OrgTimeBindingConfig.java:36`), so week boundaries stay deterministic. Pinned by `PlanShellGenerationFailsafeTest` (`PlanShellGenerationFailsafeTest.java:61`). Anchor: **Appendix D.6**.
- **SYSTEM actor, plan-shell-only writes.** The cron runs as the `SystemPrincipal` actor (no HTTP request, exempt from self/direct-report scoping) and writes only plan-shell rows — never commitments or sync records (REQ-I-002, `PlanShellGenerator.java:24-32`). The audit is null-actor with safe-only metadata (rule #7).

## Gotchas & sharp edges

- **DRIFT (high): the perf-seed Job has no in-code runner.** `infra/k8s/job-perf-seed.yaml:34-35` launches the `wc-api` image with `--app.job=generate-perf-seed`, and ARCHITECTURE.md **F.6 / D.5 / Appendix E Part 2** specify this Job + arg (Gradle task `generatePerfSeed`). But **no `@ConditionalOnProperty(app.job=generate-perf-seed)` runner exists** anywhere in `apps/wc-api/*/src/main` — a repo-wide search found only `PlanShellGenerationRunner` (`generate-plan-shells`) and `ProjectionRebuildRunner` (`rebuild-projections`) as `ApplicationRunner`s. Consequently, if `job-perf-seed.yaml` were applied, the launched JVM would match no runner; worse, the manifest does **NOT** pass `--spring.main.web-application-type=none` (unlike the generation/rebuild manifests), so the container would **start a web server and hang** rather than exit — exactly the failure infra LESSONS §22 warns about. The perf seed is opt-in / excluded from the deploy chain (`job-perf-seed.yaml:18-20`), so this drift does not affect normal deploys, but the Job as written cannot do its stated job. (Documented in driftFindings.)
- **The migration Job carries no `--app.job` and no runner — it relies purely on the profile.** Flyway runs as a Spring Boot autoconfiguration side-effect under `flyway-migrate`; there is no batch *runner* class for it. Its termination is `web-application-type: none` set in the profile yaml (`application-flyway-migrate.yml:11`), not a launch arg, and not an `activeDeadlineSeconds` (the migration Job manifest has none — only `backoffLimit: 2`, `job-migration.yaml:19`).
- **Generation env redundantly disables Flyway twice.** The CronJob sets `SPRING_FLYWAY_ENABLED=false` (`cronjob-generation.yaml:46-47`) even though the base `application.yml:14` already defaults it `false`. Belt-and-suspenders against any profile that might flip it; harmless.
- **`activeDeadlineSeconds` is a backstop, not the exit mechanism.** If the launch arg `--spring.main.web-application-type=none` is ever dropped from the generation/rebuild manifest, the Job will run a web server forever and only be killed by `activeDeadlineSeconds: 600` after hitting `backoffLimit`. Always audit `--app.job` Jobs for the web-type arg (infra LESSONS §22 caught exactly this gap).
- **Generation runs as one transaction.** Shells + the audit row commit atomically (`@Transactional` on `generate()`, `PlanShellGenerator.java:68`). A failure mid-run rolls back *all* shells for that week, not a partial set — the next run re-creates them all via the idempotency filter.
- **Empty active roster still writes an audit row.** A run with zero active employees creates zero shells but still records one `PLAN_SHELLS_GENERATED` audit (count 0) — an operational trail (`PlanShellGenerator.java:96`; pinned `emptyActiveRoster_createsNoShells_stillAuditsRun`, `PlanShellGenerationRunnerTest.java:176`). Same for rebuild on 0 plans (`ProjectionRebuilder.java:69`).
- **No live-SNS / Graph / Auth0 / CORS config on the batch profiles.** The generation/migration/rebuild manifests deliberately omit SNS/SQS/Graph/Auth0/CORS env (`cronjob-generation.yaml:5`, `job-migration.yaml:7`, `job-rebuild-projections.yaml:9`) — these batches are DB-only; the `spring.cloud.aws.region.static` fallback (`application.yml:23`) is what keeps the SNS starter from failing at boot even though no publish happens.

## Connects to

- **[03-application-lifecycle.md](03-application-lifecycle.md)** — generation creates the `DRAFT` `WeeklyPlan` shells that the IC weekly lifecycle (`PlanLifecycleService`) then drives `DRAFT→LOCKED→…`. The plan-shell *domain* rules + `unique(employee_id, week_start_date)` constraint semantics live there. Handoff point: `PlanShellGenerator.generate()` writes the shell rows that lifecycle services later mutate.
- **[05-manager-projections.md](05-manager-projections.md)** — `ProjectionRebuildRunner.run()` (`ProjectionRebuildRunner.java:36`) calls `ProjectionRebuilder.rebuild()` (truncate + recompute-from-source via `ProjectionRefresher.recomputeForPlan`). The rebuild *logic*, projection tables, and count/badge derivation are owned there.
- **[04-authorization-identity-audit.md](04-authorization-identity-audit.md)** — both batch runs emit SYSTEM-actor (null-actor) `audit_event` rows via `AuditService.record(...)`; the `SystemPrincipal` no-HTTP boundary + audit shape are owned there.
- **[01-domain-persistence.md](01-domain-persistence.md)** — the Flyway migration *content* (V1–V6 schema + seed) that the `flyway-migrate` Job applies; the `WeeklyPlan`/`Employee` entities + repositories generation writes through.
- **[10-infrastructure-deployment.md](10-infrastructure-deployment.md)** — the k8s `CronJob`/`Job` manifests' SA/IRSA/CSI-secret/image wiring, the §12 deploy ordering (migration Job → rebuild Job → Deployment roll, pipeline-enforced), and the perf-seed Job's exclusion from the deploy chain.

---
_Generated by `/layer-docs` (initial run) against commit `3b919a9` on 2026-06-04. Claims are anchored to code; `UNVERIFIED` marks anything not confirmed._
