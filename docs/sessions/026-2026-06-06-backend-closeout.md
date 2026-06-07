# Session 026 — Backend close-out: week-parameterized demo-seed Job (107a/107b)

- **Date:** 2026-06-06
- **Phase:** Phase 10 (demo seed) — task **10.8**, the runtime/ops sibling of the fixed-week V5/V6 Flyway fixture. Terminal backend session (demo recorded, submission committed, project wrapping).
- **Role:** **implementer-authored** (`st6-main-wc-api-implementer`, the cycled successor to 023). Both slices ran the `/tdd` discipline with `st6-main-orchestrator` doing Step-2.5 review + Step-9 routing.
- **Predecessor:** [023](023-2026-06-06-backend-deploy-dominoes-worker-concurrency-graph-polish.md) (deploy dominoes + worker concurrency + Graph polish). **Successor:** terminal — none planned (project wrapping).
- **My implementer commits:** `dd51072` (107a — runner + week-param + FK-safe reset), `4af6ea4` (107b — persona-ensure + matrix + temporal + projections + idempotency + audit).

## Why this session existed

The demo is recorded **this weekend** (seed week **Jun 1–7**) and a live external review runs the **week of Jun 8–14** (seed week **Jun 8–14**). The app is single-week, and the V5/V6 demo fixture is hard-pinned to `2026-06-01` — so each demo week needs its own freshly-seeded matrix. Brief 107 delivered a one-shot, week-parameterized Java seed Job (`--app.job=seed-demo --week=YYYY-MM-DD`) that **reset-then-seeds** the 7-persona lifecycle matrix for any Monday week-start, with temporal correctness, projections recomputed from source, and full idempotency (re-runnable between takes).

## What was built

**Files created**
- `api/.../job/DemoSeedRunner.java` — one-shot `ApplicationRunner` gated on `--app.job=seed-demo`; parses/normalizes `--week` (current week's Monday from the injected `Clock` when absent), delegates to the seeder. Mirrors `ProjectionRebuildRunner`.
- `api/.../demoseed/DemoSeeder.java` — the seed logic: ensure personas → reset → seed matrix → recompute projections → audit. `@Transactional`.
- `api/.../demoseed/DemoSeeding.java` — the seed-entry interface the runner injects (interface-over-concrete, EI2 convention; also a clean mockable seam).
- `api/.../demoseed/DemoSeederResetTest.java` — isolated PG16 over V1–V6 (`§41` posture); proves the FK-safe `reset()` in isolation + `run()` week-normalization.
- `api/.../demoseed/DemoSeederSeedTest.java` — isolated PG16 over V1–V4 (clean slate); 11 tests proving the matrix, clock-anchored OVERDUE derivation, disputes + partial-unique, carry-forward chain, sync terminal/non-PII, projections-from-source (=5 reviewed reports), idempotency, week-portability, persona-ensure, and the SYSTEM audit row.
- `api/.../job/DemoSeedRunnerGatingTest.java` — `ApplicationContextRunner` gating + arg-parse delegation (mirrors `ProjectionRebuildRunnerGatingTest`).

**Files modified (within this session, across the two commits)**
- `DemoSeeder.java` — 107a shipped reset-only; 107b extended `run` into the full reset-then-seed, made it implement `DemoSeeding`, returned the seeded count, and exposed `reset()` package-private as a test seam.
- `DemoSeedRunner.java` — 107b switched the injected dependency from the concrete `DemoSeeder` to the `DemoSeeding` interface (EI2 resolution).
- `DemoSeederResetTest.java` — 107b adapted it: since `run()` now reset+seeds, the two isolation tests call the `reset()` seam and the normalization test asserts where the seed lands.

## Decisions made

- **Temporal anchoring (Q1, load-bearing).** The two NOT_REVIEWED review due-dates anchor to the injected `Clock`'s `now`: **Marco** = 17:00 org-tz on the previous business day before `now` (strictly past → OVERDUE at any view time); **Priya** = `ReviewSlaService.reviewDueAt(now)` (next business day → future → not overdue). Chosen over a fixed week-grid anchor because the latter left a Monday-morning-of-review-week gap (a Tue due-date is future → not overdue). All other timestamps stay on the week grid. OVERDUE is derived, never stored (rule #6).
- **Construction (Q3, load-bearing — rule #4).** Direct row insertion via the domain repos, sync records set to terminal SYNCED/FAILED directly, projections via `ProjectionRefresher.recomputeForPlan` only — **never** `PlanLifecycleService.lock` / `DisputeService`, whose `afterCommit` SNS publishes would fire real Outlook calendar syncs for every seeded persona.
- **Persona-ensure (Q2).** The Job idempotently upserts the 7 employees + 6 relationships (V5 literals incl. `external_subject` for OAuth resolution). This makes the Job self-contained AND lets the 107b seed test run on a clean V1–V4 slate (no V6 entanglement). Sequenced into 107b (its consumer) rather than 107a.
- **UUIDs (Q4):** fresh-random per run — reset-then-seed makes idempotency the reset's job, so no `ON CONFLICT` literals.
- **Reset mechanism:** scoped, FK-ordered SQL deletes (disputes→reviews→projections→sync→commitments→plans) over the two-week footprint {W, W−7}, via `NamedParameterJdbcOperations` (the interface, per the `AwsSnsLifecycleGateway` EI2 convention) — keeps the 7 domain repos free of demo-only bulk-delete methods.
- **EI2 resolution:** at ~13 deps SpotBugs `EI_EXPOSE_REP2` false-positived on the runner storing the concrete seeder; resolved by the `DemoSeeding` interface (the documented interface-over-concrete convention; interface-typed fields are EI2-safe).

## Decisions explicitly NOT made

- **No due-date↔lock coherence guard.** For a future-relative-to-`now` seed week, the overdue persona's due-date can precede the grid `lockedAt` — a cosmetic tooltip detail. A guard would conflict with the week-grid lock; the matrix renders correctly (the load-bearing requirement), so it was left out (orchestrator-confirmed).
- **No `ARCHITECTURE.md` Appendix-E note authored.** Whether to note that the runtime Job parameterizes the fixed-week V6 fixture is an orchestrator call at `/orchestrate-end` — likely unnecessary (the V6 fixture spec already covers the matrix). Cross-doc audit is CLEAN (no model/enum/schema/DTO change).

## TDD compliance

**Clean.** Both slices ran RED → Step-2.5 (orchestrator-approved) → GREEN → Step-10 commit. RED was confirmed for the right reason each time (107a: missing classes; 107b: assertion failures from an unseeded matrix). The `DemoSeeding` interface extraction + the package-private `reset()` seam were Step-6 refactors during GREEN (to resolve SpotBugs and re-enable reset-isolation testing) — no untested behavior introduced; all paths are covered by the 17 demoseed/gating tests.

## Reachability

`--app.job=seed-demo` Job → `DemoSeedRunner` (`@ConditionalOnProperty`) → `DemoSeeding.run` → ensure-personas / reset / seed / `recomputeForPlan` / audit. Pinned by `DemoSeedRunnerGatingTest` (wires-only-under-the-property + delegates the parsed Monday). No tested-but-unwired backend gap. The deployment entry — `infra/k8s/job-seed-demo.yaml` — is the infra companion (below).

## Open follow-ups (Step-9 categorized — orchestrator routes/verifies at `/orchestrate-end`)

- **Convention candidate → LESSONS §50** (orchestrator banks): the week-parameterized reset-then-seed Job pattern — two-week footprint, scoped FK-ordered SQL deletes, direct-row-insert-NOT-lifecycle-services (rule #4), `recomputeForPlan`-for-projections, Clock-anchored OVERDUE, + the EI2 interface-over-concrete sub-note (the `DemoSeeding` extraction, subsuming the `NamedParameterJdbcOperations` application — same convention, two sites). Composes §23 + §40 + §41.
- **Task tracker → MVP_TASKS 10.8 COMPLETE** (orchestrator ticks): runner + reset + matrix + temporal + projections + idempotency + persona-ensure.
- **Future TODO — operational:** `infra/k8s/job-seed-demo.yaml` (model on `job-rebuild-projections.yaml`; `--app.job=seed-demo --week=<date>`, `web-application-type=none`, `SPRING_FLYWAY_ENABLED=false`, db-only IRSA) → route to `st6-main-infra-implementer` at polish-deploy assembly; + runbook note "run the seed Job per target week before recording/review."
- **Architecture-doc note candidate (orchestrator confirm):** Appendix E Part 2 could note the runtime Job parameterizes the fixed-week V6 fixture — likely no edit needed.
- **Cross-doc invariant change:** NONE (audit CLEAN, both slices).

## How to use what was built

Run the one-shot Job with the target week's Monday (any day in the week is normalized):

```
--app.job=seed-demo --week=2026-06-08 --spring.main.web-application-type=none
```

Absent `--week` defaults to the current week's Monday (org timezone). It reset-then-seeds the 7-persona matrix for that week (+ Grace/Dana at W−7), so it is safely re-runnable between demo takes and across weeks (run for Jun 1–7 to record, then Jun 8–14 for the live review). Projections are recomputed from source, so Dana's command-center is honest after each run.
