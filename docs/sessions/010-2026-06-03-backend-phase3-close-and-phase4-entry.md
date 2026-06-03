# Session 010 — Backend: Phase-3 close (3.4b + 3.5) + Phase-4 entry (4.2)

- **Date:** 2026-06-03
- **Phase:** Phase 3 (commitment CRUD + LOCK) close-out → Phase 4 (post-lock reconciliation lifecycle) entry
- **Role:** `st6-main-wc-api-implementer` (fresh successor pair after the prior impl cycled at 81% on 3.4a)
- **Predecessor:** [008](008-2026-06-03-backend-phase3-rcdo-plan-commitment.md) (3.1–3.4a, impl-perspective) · orchestrator companion: [009](009-2026-06-03-backend-phase3-complete-plan-lock.md) (3.4b/3.5 decisions, cross-doc, reachability — **read 009 for 3.4b/3.5 depth**)
- **Successor:** [012](012-2026-06-03-backend-phase4-functional-surface.md) (4.4/4.4b/4.5 — Phase-4 functional surface complete; 4.1/4.3 routed in handoff 005). _(4.1 landed `a35961a`, 4.3 `5af8356` under the prior orch before this doc's successor session.)_

## Why this session existed

A fresh full-budget implementer to take the safety-dense tail of Phase 3 (3.4b locked-baseline immutability, 3.5 the LOCK keystone) and open Phase 4 (4.2 start-reconciliation). Three slices landed, each standalone with a green `./gradlew check`.

## What was built

### Slice 3.4b — commitment PATCH (E6) + DELETE (E7) + baseline gates — `dcad85c`
**Impl recap (full decisions in [009](009-2026-06-03-backend-phase3-complete-plan-lock.md)).** `PATCH`/`DELETE /api/commitments/{id}` for the owning IC on a DRAFT plan; rule-#2 locked-baseline-immutability gate (`409 LOCKED_BASELINE_EDIT`) + `alignmentStatus`-post-lock (`409 ILLEGAL_STATE_TRANSITION`); rule-#3 IC-owner-only mutation via a new `authorizeCommitmentMutation` (`403 COMMITMENT_OWNER_REQUIRED`). Security-reviewer PASS.
- **NEW:** `commitment/dto/PatchCommitmentRequest`, `web/LockedBaselineEditException`.
- **MOD:** `CommitmentController`, `CommitmentService`, `DomainAuthorizationService`, `web/ErrorCodes`, `web/IllegalStateTransitionException` (+`constraint`), `web/ProblemDetailsExceptionHandler`.
- **Two engineering pivots (→ LESSONS, orch-routed):** (a) PATCH 3-way presence needs a **presence-flag POJO**, not an `Optional<>` record (Jackson collapses absent + present-null into `Optional.empty()`, and Hibernate Validator's `OptionalValueExtractor` feeds null into `@NotBlank`); (b) the service method is named `discard` (not `delete`) to dodge SpotBugs 4.8.6 `MutableClasses` mutator-name `EI_EXPOSE_REP2` on the injecting controller — no suppression (§16).

### Slice 3.5 — plan LOCK transition (E8) — `db18c74` — Phase 3 keystone
**Impl recap (full decisions in [009](009-2026-06-03-backend-phase3-complete-plan-lock.md)).** `POST /api/plans/{id}/lock` — the safety culmination (rules #1/#2/#4) in one `@Version` txn. Security-reviewer PASS (0 findings across rules #1/#2/#3/#4/#6/#7).
- **NEW:** `plan/PlanLifecycleService`, `review/ReviewSlaService`, `review/mapper/ReviewMapper`, `projection/ProjectionService`, `sync/SyncRecordService`, `sns/{SnsLifecyclePublisher,LifecycleSnsGateway,LoggingLifecycleSnsGateway}`, `shared/sns/payload/SyncJobPointer`, `web/{EmptyPlanLockException,UnlinkedPlannedCommitmentException}`.
- **MOD:** `PlanController` (+E8), `plan/mapper/PlanMapper` (maps the review for LOCKED+), `DomainAuthorizationService` (+`authorizePlanMutation`/`PLAN_OWNER_REQUIRED`), `web/ErrorCodes` (+`EMPTY_PLAN_LOCK`/`UNLINKED_PLANNED_COMMITMENT`), `web/ProblemDetailsExceptionHandler` (+empty/unlinked/**optimistic-lock→409**), the 3 projection/review repo finders.
- **The §28 lock-transaction pattern** (the durable convention for this + all Phase-4 transitions): precondition reuses `AllowedActionResolver.canLock` as the single accept-gate + diagnose-only-on-false + fail-closed; the `afterCommit`→`REQUIRES_NEW` non-blocking publish (swallow-never-rethrow, leaves `PENDING_PUBLISH`); `OptimisticLockingFailureException→409`; §9 projection recompute-from-source.
- **Step-9 flag self-corrected (close-out audit):** my 3.5 flag claimed `ReconciliationOutcome` lacks `CARRIED_FORWARD` — **mistaken** (a `grep` artifact: my regex required a trailing comma and missed the last value). The enum **does** include `CARRIED_FORWARD` (COMPLETED/PARTIALLY_COMPLETED/BLOCKED/CANCELED/CARRIED_FORWARD); the orch verified this ([009](009-2026-06-03-backend-phase3-complete-plan-lock.md) line 41 — no drift). 3.5's projection derives carry-forward from `carryForwardSourceCommitmentId != null` (the only signal at lock; count = 0 there regardless of field choice) — **harmless at lock**; whether the reconciliation-week count keys on the source-link or the `reconciliationOutcome` is an open question pinned when reconciliation lands.

### Slice 4.2 — start-reconciliation transition (E9) — `e19a39a` — **substantive new content**
`POST /api/plans/{id}/start-reconciliation` (E9, owning IC, LOCKED plan) — the forward-only `LOCKED→RECONCILING` lifecycle entry that unblocks the rest of Phase 4 (4.1/4.3/4.4/4.5). One `@Version`-guarded transaction: sets `RECONCILING` + `reconciliation_started_at`; synchronously refreshes the manager projection's `plan_state` (§9, when the IC has a manager + review); writes the `RECONCILIATION_STARTED` audit; creates the `IC_RECONCILIATION` sync record; then publishes the pointer **after commit** (non-blocking, rule #4). Returns the updated `WeeklyPlanDto` with `allowedActions[]` recomputed.

**Files modified (4 — pure reuse, NO new production files):**
- `plan/PlanLifecycleService` — `+startReconciliation(actor, planId)` mirroring `lock()`: authorize-first chokepoint → forward-only state guard (`LOCKED`-only, else `ILLEGAL_STATE_TRANSITION`) → `@Version` save → IC_RECONCILIATION sync + (manager-scoped) projection refresh + `RECONCILIATION_STARTED` audit → `publishAfterCommit`.
- `plan/PlanController` — `+POST /api/plans/{id}/start-reconciliation` (E9).
- `plan/AllowedActionResolver` — `+canStartReconciliation(actor, plan)` (owner + `LOCKED`) and `planActions` now emits `START_RECONCILIATION` for a LOCKED owning-IC plan.
- `sync/SyncRecordService` — DRY refactor: private `createWeeklyPlanRecord(plan, eventKind, traceId)`; `createIcPlanningRecord` + new `createIcReconciliationRecord` delegate. IC_PLANNING + IC_RECONCILIATION coexist (V1 sync unique includes `event_kind`).

**Tests added (2 files, 12 fns):** `plan/StartReconciliationServiceTest` (5 — transition/orchestration, authz chokepoint, all-3-non-LOCKED→409, no-manager skip, OLE→409), `plan/StartReconciliationEndpointTest` (7 — happy 200 + DB deltas, DRAFT→409, rule-#4 non-blocking via throwing `@MockBean` gateway, non-owner 404 / manager 403, the `START_RECONCILIATION` affordance pin, 401).
**Test modified (1):** `plan/PlanLockEndpointTest.lock_validPlan` — `allowedActions isEmpty` → `contains START_RECONCILIATION` (the affordance↔enforcement progression: a LOCKED plan now genuinely offers start-reconciliation — a *stronger* assertion).

## Decisions made

- **4.2 reuses 3.5's §28 shape verbatim** — `startReconciliation` mirrors `lock` (same `@Version` txn + `afterCommit`-publish + `ProjectionService.recompute` + `authorizePlanMutation`). No new safety surface → no security-reviewer (orch-confirmed).
- **`allowedActions[]` — only `START_RECONCILIATION` on LOCKED** (no affordance without enforcement, §15 / LESSONS §24). RECONCILING emits nothing new — the `ADD_UNPLANNED`/`CLOSE_RECONCILIATION`/`CARRY_FORWARD` actions land with their enforcing slices (4.3/4.4/4.5). This corrects the MVP_TASKS 4.2 note that lists them as "now eligible."
- **Sync-row status on publish failure = `PENDING_PUBLISH`** (API-side retained/retryable; `FAILED` is worker/Graph-side, §10/§28).
- **No-manager IC** → transition + `RECONCILIATION_STARTED` audit + IC_RECONCILIATION still happen; the projection recompute is skipped (mirrors lock).
- **SyncRecordService DRY** via `createWeeklyPlanRecord(plan, eventKind, traceId)` — both legs are WEEKLY_PLAN/owner=IC, differ only in event kind.

## Decisions explicitly NOT made (deferred)

- **A shared `transition(...)` helper** across `lock`/`startReconciliation`/(future) `closeReconciliation` — deferred to **4.5** (close-reconciliation, the symmetric `RECONCILING→RECONCILED`): extract then IF the duplication is real (no premature abstraction, brief Q3).
- **The lock→start→close coexistence E2E** (IC_PLANNING + IC_RECONCILIATION over a real chain) — deferred to **4.6** full-reconciliation-pass integration test (orch heads-up); 4.2 uses the direct-seeded LOCKED precondition (the isolated-slice pattern).
- **Projection completeness** (unresolved-dispute count + misaligned-OR-open-dispute union + stale-cell deletion) — the disputes/reconciliation recompute slices extend `ProjectionService.recompute`; 4.2/3.5 pass `unresolvedDisputeCount=0` and use the insert path.

## TDD compliance

**Clean — no violations.** All three slices ran RED→Step-2.5→GREEN: every production symbol was first referenced by a failing test (compile-RED confirmed for the right reason before any implementation), the orchestrator reviewed the test designs at Step 2.5 (`APPROVED.`/`ADD:` headers), and GREEN followed. Both 3.5 ADDs (fail-closed gate, optimistic-lock→409 decomposition) were added at Step 2.5 before implementation.

## Reachability (Step 7.5)

All features reachable from real production HTTP entry points — endpoint tests drive the full demo-mode 2.6 chain:
- **3.4b:** `PATCH`/`DELETE /api/commitments/{id}` → `CommitmentController` → `CommitmentService.update`/`discard` → `DomainAuthorizationService.authorizeCommitmentMutation`.
- **3.5:** `POST /api/plans/{id}/lock` → `PlanController.lock` → `PlanLifecycleService.lock` → SLA/projection/sync helpers + afterCommit publisher (`LoggingLifecycleSnsGateway` is the default `LifecycleSnsGateway` bean).
- **4.2:** `POST /api/plans/{id}/start-reconciliation` → `PlanController.startReconciliation` → `PlanLifecycleService.startReconciliation`; `START_RECONCILIATION` consumed by `PlanMapper` for E3/E4/E8/E9.

No tested-but-unwired gaps.

## Open follow-ups

### Step-9 categorized items (surfaced for the orchestrator to verify routed — NOT re-routed here)
**3.4b / 3.5** — routed during their rounds (companion doc [009](009-2026-06-03-backend-phase3-complete-plan-lock.md) is the durable record): cross-doc rows (`PatchCommitmentRequest`↔B.6, `LOCKED_BASELINE_EDIT`/`EMPTY_PLAN_LOCK`/`UNLINKED_PLANNED_COMMITMENT`, verify `COMMITMENT_OWNER_REQUIRED`/`PLAN_OWNER_REQUIRED` in §5/§6, `ManagerReviewDto`↔B.7 mapper, `SyncJobPointer`/§10, optimistic-lock→409); **LESSONS §27** (PATCH presence-flag POJO + SpotBugs mutator-name) + **§28** (lock-transaction pattern); Carry-forward (Phase-4 per-state PATCH allow-list, projection completeness, real AWS SNS gateway @ Phase 12; the projection's carry-forward field-choice — `carryForwardSourceCommitmentId` vs `reconciliationOutcome=CARRIED_FORWARD` — pinned when reconciliation lands, per 009 line 41).
**4.2 (light — deferred to the orch's Phase-4 round close-out):**
- **Architecture doc note** → `IC_RECONCILIATION` sync trigger now produced (§10); `START_RECONCILIATION` affordance now enforced (§15/F.4); WeeklyPlan `LOCKED→RECONCILING`+`reconciliation_started_at` set (§3). All vocab pre-exists — "already-documented, now-realized."
- **Cross-doc** → no new Appendix-A field; the `AllowedAction` row note reflects `START_RECONCILIATION` enforced-at-4.2.
- **Convention** → none new (§28 covers the transition shape).
- **Future TODO — belongs to a phase** → the shared `transition(...)` helper decision at 4.5; the lock→start→close E2E at 4.6.

### Wiring tasks
None — all features wired this session.

## How to use what was built

Phase-4 slices (4.1/4.3/4.4/4.5) need a `RECONCILING` plan: `POST /lock` then `POST /start-reconciliation`. The next reconciliation transition (4.5 close) should mirror `startReconciliation`; if a 3rd transition's duplication is real, extract a shared `transition(...)` helper (per §28's shape). Outcome-PATCH (4.1) and unplanned-create (4.3) mutate a RECONCILING plan's commitments + re-call `ProjectionService.recompute`.
