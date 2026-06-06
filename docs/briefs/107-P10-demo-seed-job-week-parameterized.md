# /tdd brief — week-parameterized demo-seed Job (`DemoSeedRunner`)

> **URGENT / time-sensitive.** The demo is recorded **this weekend** (run the seed for week **Jun 1–7**) and a live external review runs **the week of Jun 8–14** (run the seed for **Jun 8–14**). The app is single-week, so each week needs its own freshly-seeded matrix. This is the biggest backend slice in a while — authored as **ONE brief, TWO commits** (107a runner+reset / 107b matrix+temporal+projections+idempotency). Ship each slice through its own `/tdd` cycle (RED → 2.5 → GREEN → Step-9 → Step-10 commit), then proceed to the next.

## Feature
A one-shot, week-parameterized Java seed Job (`--app.job=seed-demo --week=YYYY-MM-DD`) that **reset-then-seeds** the deterministic 7-persona demo lifecycle matrix for a target week — replicating the V5/V6 fixture (which is fixed to week 2026-06-01) but **parameterized to any Monday week-start**, with temporal correctness via an injectable `Clock` so OVERDUE/reconciling/reconciled render right for the current AND next week, projections recomputed from source, and full idempotency (re-runnable between takes).

## Use case + traceability
- **Task ID:** P10 (demo seed — the runtime/ops sibling of the V5/V6 fixture seed, tasks 10.2–10.5). New subtask **10.8** (week-parameterized demo-seed Job). _(NOTE: 10.6 is already taken — the discharged projection-rebuild reframe; this Job is 10.8.)_
- **Architecture sections it implements:** `ARCHITECTURE.md` Appendix E Part 2 (the binding fixture matrix), §3 (plan lifecycle), §9 (manager projections), §10 (Outlook sync records), §17 (review SLA / overdue derivation), §23 (one-shot `--app.job` runner pattern).
- **Related context:**
  - **The matrix to replicate:** `apps/wc-api/shared/src/main/resources/db/demo-seed/V5__seed_personas_and_relationships.sql` (personas + relationships) + `V6__seed_fixture_plans_and_state.sql` (the fixed-week lifecycle matrix). **The Job re-creates V6's matrix shape parameterized to the target week** — read both end-to-end before Step 2.5.
  - **The verification gold-standard:** `apps/wc-api/shared/src/test/java/com/st6/wc/migration/V6FixtureStateMigrationTest.java` — the Job's TDD assertions mirror this test's per-persona state / dispute / carry-forward / sync / overdue-derivation checks, but against the **parameterized week** and via the **Java seeder** (not the SQL migration).
  - **The runner pattern to mirror:** `ProjectionRebuildRunner` (`api/.../job/`) + `ProjectionRebuilder` (`api/.../projection/`) + their gating test `ProjectionRebuildRunnerGatingTest`. LESSONS §23 (one-shot `--app.job` runner) + §40 (truncate+recompute via the incremental entrypoint) + §41 (demo-seed Flyway location + isolated-Testcontainer verification).
  - **Projection refresh:** `ProjectionRefresher.recomputeForPlan(plan)` (`api/.../projection/`) — the §9 source-loading entrypoint. **Reuse it per plan**; do NOT hand-insert projection rows.
  - **Temporal:** `ReviewSlaService.reviewDueAt(Instant lockedAt)` (17:00 org-tz next-business-day) + `OrgTimeConfig.weekStartDate(...)` / `weekEndDate(...)` (Monday/Sunday resolvers).
  - **Sync record shapes:** `SyncRecordService` (the IC_PLANNING / MANAGER_REVIEW_BLOCK terminal-state shapes — though the Job sets sync records to their **terminal demo states directly**, see Step-2.5 Q3).

## Personas + ids (from V5; stable literals)
| Persona | Employee id | Target-week matrix state |
|---|---|---|
| Dana Okafor (manager) | `d0…01` | command-center roll-up; her OWN IC plan RECONCILED at the **prior** week (W−7) — exercises "manager who also owns a plan", unmanaged → no review |
| Priya Raman (IC) | `d0…02` | **LOCKED · SYNCED** sync · review NOT_REVIEWED **not-overdue** (3 planned, all linked) |
| Marco Bellini (IC) | `d0…03` | **LOCKED · FAILED** sync · review NOT_REVIEWED **OVERDUE** (2 planned, all linked) |
| Aisha Khan (IC) | `d0…04` | **LOCKED · OPEN dispute** (MISALIGNED) → review REVIEWED_WITH_DISPUTES |
| Tomas Novak (IC) | `d0…05` | **LOCKED · RESOLVED dispute** → review REVIEWED |
| Grace Liu (IC) | `d0…06` | **RECONCILING** at W (+ a carry-forward target) · **RECONCILED** at W−7 (the carry-forward SOURCE); review REVIEWED |
| Sam Carter (IC) | `d0…07` | **DRAFT** with one deliberately-**unlinked** planned commitment (the can't-lock fixture) |

SO link targets are the V4 RCDO reference ids `c0…01`–`c0…09` (SO-1.2 = `c0…02` is the carry-forward link target). RCDO (V4) + personas (V5) are present from the migration chain.

## The two-week footprint (load-bearing — read carefully)
Seeding "week **W**" writes rows in **two** weeks:
- **W** — the reports' current matrix (Priya/Marco/Aisha/Tomas LOCKED, Grace RECONCILING, Sam DRAFT).
- **W−7** — Grace's RECONCILED carry-forward **source** plan + Dana's own RECONCILED IC plan.

Therefore the **reset footprint = derived data at weeks {W, W−7} scoped to the 7 personas.** A reset that only clears W would leave Grace's/Dana's W−7 rows to duplicate on re-run. (Deleting W−7 for all 7 personas is harmless — only Grace+Dana have W−7 rows.) Per-week demo runs are sequential (record Jun 1–7, then review Jun 8–14); the two primary weeks need NOT coexist, so seeding Jun 8–14 resetting Jun 1–7's footprint is acceptable (the recording is already done).

## Acceptance criteria (what "done" means)

### Slice 107a — runner + week-param + FK-safe reset
- [ ] `DemoSeedRunner` wires as a bean **only** under `--app.job=seed-demo` (inert in the normal web image); `run()` parses `--week=YYYY-MM-DD` and delegates to `DemoSeeder.run(weekStart)`.
- [ ] Week resolution: a provided `--week` is **normalized to the Monday** of its week (`OrgTimeConfig.weekStartDate`); **absent → current week's Monday** via the injected `Clock`.
- [ ] `DemoSeeder.run(weekStart)` (107a scope) ensures personas exist (see Step-2.5 Q2) + performs the **FK-safe reset** of the {W, W−7} footprint across the 7 personas, in delete order: `alignment_dispute` → `manager_review` → projections (`manager_heatmap_cell`, `manager_plan_summary`) → `outlook_calendar_sync_record` → `weekly_commitment` → `weekly_plan`.
- [ ] Reset is **scoped** — it deletes only the 7 personas' rows at weeks {W, W−7}; personas, RCDO (V4), manager relationships, and any other week's rows are untouched.
- [ ] All unit/integration tests in `api/.../job/` + `api/.../demoseed/` pass; `./gradlew check` (from `apps/wc-api/`) clean.

### Slice 107b — 7-persona matrix + temporal correctness + projections + idempotency
- [ ] `DemoSeeder.run(weekStart)` (extended) inserts the full 7-persona matrix (table above) for week W (+ Grace/Dana at W−7), with all timestamps **relative to weekStart** (locked_at, generated_at, reconciliation_started_at/reconciled_at, created_at on the week grid).
- [ ] **Temporal correctness:** the two SLA-sensitive review due-dates are anchored so OVERDUE derives correctly at job-run (≈view) time — **Marco overdue, Priya not-overdue** at the injected `Clock`'s `now` (see Step-2.5 Q1). OVERDUE is **derived, never stored** (rule #6).
- [ ] **Projections** populated by calling `ProjectionRefresher.recomputeForPlan(plan)` per seeded plan — **never hand-insert** `manager_plan_summary`/`manager_heatmap_cell` rows. Projections == source (Dana's command-center is honest).
- [ ] **Invariants respected** (asserted): valid LOCKED baselines (≥1 linked planned commitment — rule #1); ≤1 unresolved dispute per commitment (rule #6 partial-unique safe); OVERDUE derived-not-stored (rule #6); sync records carry fixed **non-PII** `failureCode`/`safeMessage` (rule #7).
- [ ] **Idempotent:** running the full `run(W)` twice yields the identical matrix (same per-persona states, same row counts, projections==source) — the reset clears the prior take.
- [ ] **Temporal portability:** seeding a **different** week (e.g. W+7) renders the same matrix correctly for that week (Marco overdue, Grace RECONCILING with the carry-forward source at the new W−7).
- [ ] One SYSTEM (null-actor) audit row per run (reuse `AuditService.record`, safe metadata — §23).
- [ ] All tests pass; `./gradlew check` clean.

## Files expected to touch
**New:**
- `api/src/main/java/com/st6/wc/job/DemoSeedRunner.java` — `@ConditionalOnProperty(name="app.job", havingValue="seed-demo")` thin `ApplicationRunner`; parses `--week`, delegates. Mirrors `ProjectionRebuildRunner`.
- `api/src/main/java/com/st6/wc/demoseed/DemoSeeder.java` — `@Component`; `@Transactional run(LocalDate weekStart)`; the reset (107a) + matrix/temporal/projection seed (107b). (Package — Step-2.5 Q5.)
- `api/src/test/java/com/st6/wc/job/DemoSeedRunnerGatingTest.java` — `ApplicationContextRunner` gating + delegation (mirrors `ProjectionRebuildRunnerGatingTest`).
- `api/src/test/java/com/st6/wc/demoseed/DemoSeederResetTest.java` (107a) + `DemoSeederSeedTest.java` (107b) — isolated per-class Testcontainer, **both** Flyway locations (`classpath:db/migration,classpath:db/demo-seed`), §41 posture.

**Modified:**
- Possibly none in production code beyond the two new classes. (`DemoSeeder` reuses existing repos + `ProjectionRefresher` + `ReviewSlaService` + `OrgTimeConfig` via constructor injection.)

**Infra companion (NOT this brief — routed separately to `st6-main-infra-implementer`):**
- `infra/k8s/job-seed-demo.yaml` — a one-shot Job parameterized `--app.job=seed-demo --week=<date>` modeled on `infra/k8s/job-rebuild-projections.yaml` (reuse the `wc-api` image — forbidden-pattern #5; `web-application-type=none`; `SPRING_FLYWAY_ENABLED=false`; db-only secrets/IRSA via `wc-cronjob`). **§22 (infra LESSONS): the `--spring.main.web-application-type=none` arg is mandatory** or the Job hangs. The orchestrator routes this at polish-deploy assembly.

If implementation needs files beyond this list, **flag at Step 2.5** before going GREEN.

## RED test outline

### Slice 107a
1. **`DemoSeedRunnerGatingTest.runnerAbsent_withoutAppJobProperty`** — no `app.job` ⇒ no bean. (Mirror `ProjectionRebuildRunnerGatingTest`.)
2. **`DemoSeedRunnerGatingTest.runnerPresent_andRunDelegatesWithParsedWeek`** — `app.job=seed-demo` ⇒ bean present; `run()` with `--week=2026-06-08` delegates `DemoSeeder.run(LocalDate.of(2026,6,8))`; **absent `--week`** ⇒ delegates the current-week Monday (fixed `Clock`).
   - Asserts: `verify(seeder).run(eq(expectedMonday))`.
3. **`DemoSeederResetTest.normalizesWeekArgToMonday`** — `run(2026-06-10 /* Wed */)` resets the week whose Monday is `2026-06-08`.
4. **`DemoSeederResetTest.resetClearsTargetWeekFootprint`** — load V1–V6 (seeds week `2026-06-01` + prior `2026-05-25`); `run(2026-06-01)`; assert ALL derived rows for the 7 personas at weeks {`2026-06-01`, `2026-05-25`} are gone (plans/commitments/reviews/disputes/sync/projections counts → 0 for that footprint).
   - Why: the two-week footprint + FK-safe order (Appendix E Part 2; the reset half of the lead-approved reset-then-seed).
5. **`DemoSeederResetTest.resetLeavesIdentityAndOtherDataIntact`** — after the reset above, the 7 employees + 6 manager_relationships + the V4 RCDO rows still exist.
   - Why: reset clears **derived** data only, never identity/reference (§41 separation).

### Slice 107b
6. **`DemoSeederSeedTest.seedsSevenPersonaMatrixForTargetWeek`** — `run(2026-06-08)` on a clean DB (V1–V5 only, or post-reset); assert each persona's plan **state** at W (and Grace/Dana at W−7) per the table; R1 has 3 linked planned commitments, R6 has exactly one unlinked + one linked planned commitment.
   - Why: Appendix E Part 2 matrix (parameterized). Mirror `V6FixtureStateMigrationTest.v6_seedsPlanPerPersonaInMatrixState`.
7. **`DemoSeederSeedTest.reviewStatesAndOverdueDeriveAtClockNow`** — stored review statuses match (Priya/Marco NOT_REVIEWED, Aisha REVIEWED_WITH_DISPUTES, Tomas/Grace REVIEWED, Dana none); at the injected `Clock`'s `now`, **Marco derives OVERDUE and Priya does NOT** (`status=NOT_REVIEWED ∧ now > review_due_at`).
   - Why: §17 / rule #6 — OVERDUE derived not stored; the temporal anchoring (Q1) is the load-bearing pin. Mirror `v6_derivedReviewStatesMatchMatrix`.
8. **`DemoSeederSeedTest.disputesOpenAndResolvedPartialUniqueHolds`** — Aisha one OPEN MISALIGNED dispute (ic_response null, resolved_at null) on a NEEDS_REVIEW commitment; Tomas one RESOLVED dispute (ic_response + resolved_at set); a 2nd unresolved dispute on Aisha's commitment is rejected (SQLSTATE 23505).
   - Why: §3 + rule #6. Mirror `v6_disputesOpenAndResolved`.
9. **`DemoSeederSeedTest.carryForwardChainLinksToPriorWeekSource`** — Grace's W−7 plan has a CARRIED_FORWARD commitment linked SO-1.2; Grace's W plan has a commitment whose `carry_forward_source_commitment_id` == that source, linked SO-1.2, PLANNED.
   - Why: §3 carry-forward chain. Mirror `v6_carryForwardChainLinksToSource`.
10. **`DemoSeederSeedTest.syncRecordsTerminalStatesNonPii`** — Marco one FAILED IC_PLANNING (safe_message present, graph_event_id null, failure_code fixed, retry_count≥1); Priya one SYNCED IC_PLANNING; Dana one SYNCED MANAGER_REVIEW_BLOCK at W; the per-manager/week review-block partial-unique holds. **`failureCode`/`safeMessage` are fixed non-PII constants** (rule #7).
    - Why: §10 + rule #7. Mirror `v6_syncRecordsFailedSyncedAndReviewBlock`.
11. **`DemoSeederSeedTest.projectionsRecomputedFromSourceNotHandSeeded`** — after `run(W)`, `manager_plan_summary`/`manager_heatmap_cell` rows exist for Dana's reports and **match the source** (e.g. Aisha's cell shows the unresolved-dispute/MISALIGNED count); assert the projections were produced by `recomputeForPlan` (e.g. counts agree with the seeded commitments/disputes) — not arbitrary literals.
    - Why: §9 — projections==source (LESSONS §40). The Job must call `recomputeForPlan` per plan.
12. **`DemoSeederSeedTest.idempotentReRun`** — `run(W)` twice; the per-persona states + total row counts (plans/commitments/reviews/disputes/sync/projections) are identical after the 2nd run.
    - Why: reset-then-seed idempotency. Mirror `v6_idempotentReRun` (but via the Java Job, not re-applying SQL).
13. **`DemoSeederSeedTest.temporalPortabilityAcrossWeeks`** — `run(2026-06-08)` then assert the matrix renders for that week (Marco overdue at the corresponding `Clock` now; Grace's carry-forward source at `2026-06-01`).
    - Why: the parameterization is the whole point — proves the current (Jun 1–7) AND next (Jun 8–14) week both render.

## Cross-doc invariant impact (implementer flags at Step 9; orchestrator writes the docs)
- **Model field changes:** **none expected.** `DemoSeeder`/`DemoSeedRunner` are new components reusing existing entities/repos/services; no Appendix-A/B model, enum, schema column, or contract-DTO field changes. (If the seed surfaces a need for a new repo finder, that's a repo method — not a cross-doc invariant.)
- **Orchestrator doc rows to write hot (Step-9 routing):** likely a **new wc-api lesson** (the week-parameterized reset-then-seed Job: two-week footprint, direct-row-insert-not-lifecycle-services, recomputeForPlan-for-projections, Clock-anchored overdue) + an `MVP_TASKS.md` task checkbox (**10.8** — 10.6 is the discharged projection-rebuild reframe) + the `job-seed-demo.yaml` infra companion routed to `st6-main-infra-implementer`. **No Appendix-A/B edit anticipated** — confirm at Step 9.

> **Implementer never edits `apps/wc-api/CLAUDE.md`, `ARCHITECTURE.md`, `MVP_TASKS.md`, or `apps/wc-api/LESSONS.md`.** Flag at Step 9 categorized; orchestrator writes hot.

## Things to flag at Step 2.5
1. **Temporal anchoring of the two SLA-sensitive review due-dates (THE load-bearing question).** V6 uses fixed week-grid due-dates + a fixed anchor "now" (2026-06-02). At a real **weekend** recording (e.g. Sat Jun 6 for week Jun 1–7), a fixed week-grid due-date would make **both** Marco and Priya overdue — breaking the matrix. The OVERDUE/not-overdue split must hold at job-run (≈view) time.
   - **My default vote:** anchor relative to the injected `Clock`'s `now` — **Marco (overdue):** `reviewDueAt = weekStart + 1 business day @ 17:00 org-tz` (reliably past for any view from mid-week onward, incl. the weekend recording; week-grid-natural). **Priya (not-overdue):** `reviewDueAt = ReviewSlaService.reviewDueAt(clock.instant())` (next business day after the actual run → always future). All other timestamps stay on the week grid (weekStart-relative). The test injects a fixed `Clock` and asserts the **derivation** (Marco overdue, Priya not) at that `Clock` — so the exact formula is testable regardless of which anchoring we pick. Rationale: bulletproof for the weekend recording; keeps everything else on the clean week grid. (Acceptable assumption: the seed is run shortly before recording/review, within/at the target week — note it in the session doc.)
2. **Persona-ensure: does the Job upsert the 7 employees + 6 relationships, or assume V5 ran?** V5 personas come from the deploy migration chain.
   - **My default vote:** **defensively upsert** them idempotently (insert-if-absent the 7 employees + 6 relationships with the V5 literal ids) so the Job is self-contained and runnable on any DB where the migrations ran (which always includes V5 in this deploy) — but personas are **not** week-scoped, so the reset never deletes them. Cheap insurance; keeps the Job runnable in a test DB seeded with V1–V4 only. (If you prefer "assume V5," the reset/seed tests must load the demo-seed location — they do anyway — so either works; upsert is strictly safer.)
3. **Construct the matrix by DIRECT row insertion (like V6) or by driving the lifecycle services (lock/dispute/etc.)?**
   - **My default vote: DIRECT row insertion** (mirror V6's shapes), set sync records to their **terminal demo states directly** (SYNCED/FAILED — no SNS publish), then `recomputeForPlan` per plan for projections. **Do NOT drive `PlanLifecycleService.lock`/`DisputeService`/etc.** — those register `afterCommit` SNS publishes that would fire **real Outlook calendar syncs for every seeded persona** (operational hazard) and use the real `Clock` at call time. Direct insertion is deterministic, side-effect-free, and matches the V6 fidelity the demo needs. The ONLY service reuse is `ProjectionRefresher.recomputeForPlan` (read-only over source → writes projections; no publish).
4. **UUIDs: fresh random per run, or deterministic per (persona, week)?**
   - **My default vote: fresh random UUIDs** — reset-then-seed makes idempotency the reset's job, so fixed literals aren't needed (unlike V6's `ON CONFLICT` approach). Simpler. (Deterministic-per-(persona,week) is an alternative that would allow `ON CONFLICT` idempotency without a reset, but the lead's approved design is reset-then-seed.)
5. **Package for `DemoSeeder`.** `com.st6.wc.demoseed` (new) vs `com.st6.wc.job` (alongside the runner) vs `com.st6.wc.projection`-style domain pkg.
   - **My default vote:** runner in `com.st6.wc.job` (consistent with `ProjectionRebuildRunner`), logic in a new `com.st6.wc.demoseed` package (the seeder touches many domains — a neutral home, like `ProjectionRebuilder` lives in `projection/`). Minor; take the default unless you see a cleaner home.

## Dependencies + sequencing
- **Depends on:** V4 RCDO + V5 personas (migration chain — present in any deployed/migrated DB and in the isolated Testcontainer with both Flyway locations); `ProjectionRefresher.recomputeForPlan` (6.3a, shipped); `ReviewSlaService` (3.5, shipped); the `--app.job` runner pattern (§23).
- **107b depends on 107a** (the reset is run-then-seed's first half; 107a ships the runner+reset, 107b adds the matrix+projections+idempotency).
- **Blocks:** the polish deploy's demo-readiness (one deploy ships 106 + the chevron fix + `WC_FRONTEND_BASE_URL` + the seed-job runner/manifest; then the Job is **run per week** post-deploy). The `job-seed-demo.yaml` infra companion (routed to `st6-main-infra-implementer`).

## Estimated commit count
**2** — (107a) `DemoSeedRunner` + `DemoSeeder` reset half; (107b) the 7-persona matrix + temporal + projections + idempotency. **Do NOT bundle into one commit** — each slice is large (≥30 lines), independently RED→GREEN testable, and bisectable on its own. This is **not** a safety-invariant-enforcement slice (the seed *respects* invariants but changes no authorization/lifecycle gate), so no security-reviewer is required by default — but the seed must be **asserted** to produce invariant-valid data (rule #1/#6/#7 pins in the 107b tests). If you judge a rule-#7 (sync-record PII) pass warranted, flag it; otherwise the leak-free fixed-constant assertions are the teeth.

## Lessons-logged candidates anticipated
- **Convention candidate** — the week-parameterized reset-then-seed Job: the **two-week footprint** reset, **direct-row-insert (not lifecycle-services, to avoid real SNS/Graph side-effects)**, `recomputeForPlan`-for-projections, and **Clock-anchored overdue** (past for the overdue persona, future for the not-overdue one) so OVERDUE renders at view time. Composes §23 + §40 + §41.
- **Future TODO — operational** — the `job-seed-demo.yaml` infra companion (route to infra at polish-deploy assembly) + the runbook note: "run the Job per target week before recording/review."
- **Architecture-doc note candidate** — Appendix E Part 2 could note that the runtime demo-seed Job parameterizes the (fixed-week) V6 fixture matrix. Confirm at Step 9 whether an Appendix note is warranted or the V6 fixture spec already covers it.

## How to invoke
1. **Read this brief end-to-end** + skim V5/V6 SQL + `V6FixtureStateMigrationTest` + `ProjectionRebuildRunner`/`ProjectionRebuilder`.
2. **Slice 107a:** run `/tdd demo-seed runner + reset` → Step 0 restate → Step 1 files → **Step 2.5 ping** (answer Q1–Q5; Q1 + Q3 are load-bearing) → GREEN → Step 9 → Step 10 commit.
3. **Slice 107b:** run `/tdd demo-seed matrix + temporal + projections + idempotency` → through Step 10.
4. **Step 9 (both):** surface anything beyond the anticipated lessons-logged candidates; confirm the cross-doc invariant audit is CLEAN (no model change expected).
