# Session 009 — Backend Phase 3 COMPLETE: commitment PATCH/DELETE → plan LOCK (3.4b–3.5)

- **Date:** 2026-06-03
- **Track:** st6-main (backend, `apps/wc-api/`)
- **Authored by:** st6-main-orchestrator (fresh successor orch — the predecessor cycled deliberately pre-3.5 so a full-budget orch reviewed the safety-critical lock). The implementer (`7ae8e1cd`) ran the `/tdd` cycles and is continuing into Phase 4 (no `/session-end` cycle this round), so the orchestrator captured this doc as part of `/orchestrate-end`.
- **Predecessor:** `008-2026-06-03-backend-phase3-rcdo-plan-commitment.md` (3.1–3.4a).
- **Successor:** _(next backend session — Phase 4 post-lock IC lifecycle)_

> **Why orchestrator-authored:** 3.4b was sealed in the predecessor orch's round commit (`9f71e70`) and 3.5 in this orch's round; the implementer is continuing into Phase 4 rather than ending its session, so there is no implementer `/session-end` doc this round. This doc captures the 3.4b–3.5 narrative (the Phase-3-complete milestone) faithfully from the slices the orchestrator reviewed at Step 2.5 + Step 9.

## What was built (2 slices, all `./gradlew check` green, security-reviewer PASS)

| Slice | Commit | What landed |
|---|---|---|
| **3.4b** | `dcad85c` | E6 `PATCH /api/commitments/{id}` + E7 `DELETE` (service method `discard` — SpotBugs mutator-name dodge) + the rule-#2 `LOCKED_BASELINE_EDIT` gate + the post-lock `alignmentStatus`→`ILLEGAL_STATE_TRANSITION` gate + the new `authorizeCommitmentMutation` (rule-#3 IC-owner-only; manager-direct-report read-but-not-edit → `403 COMMITMENT_OWNER_REQUIRED`+audit). PATCH 3-way presence via a **presence-flag POJO** (not an `Optional<>` record). **Task 3.4 complete.** Security-reviewer PASS. LESSONS §27. |
| **3.5** | `db18c74` | **E8 `POST /api/plans/{id}/lock` — the keystone.** The `DRAFT→LOCKED` transition in ONE `@Version`-guarded transaction: state + `lockedAt`; `manager_review` (`NOT_REVIEWED` + weekday-only SLA `reviewDueAt`); synchronous `manager_plan_summary` + per-DO `manager_heatmap_cell` (§9) upsert; `PLAN_LOCKED` audit; `IC_PLANNING` sync record; idempotent per-manager/week `MANAGER_REVIEW_BLOCK`. Then a **post-commit non-blocking** SNS publish (`afterCommit`→`REQUIRES_NEW`, failure leaves `PENDING_PUBLISH`). Returns `WeeklyPlanDto` with the mapped review (derived `isOverdue`, never stored). 17 new + 10 modified files; 31 tests. **Ad-hoc security-reviewer PASS — 0 findings across rules #1/#2/#3/#4/#6/#7.** **🎉 Backend Phase 3 (3.1→3.5) COMPLETE.** |

**Lessons banked:** §27 (PATCH 3-way presence + per-resource mutation authz + SpotBugs mutator-name trap), §28 (the plan-LOCK transaction pattern). **Cross-doc rows (orchestrator hot-routed at 3.5):** `ManagerReviewDto`↔B.7 (mapper/`isOverdue` realized), `ErrorCodes` (`EMPTY_PLAN_LOCK`/`UNLINKED_PLANNED_COMMITMENT` realized + `PLAN_OWNER_REQUIRED` authorizer-local + optimistic-lock→409), new `SyncJobPointer`↔F.2 row, `AllowedAction` LOCK-enforcement note; `ARCHITECTURE.md` B.21 += `PLAN_OWNER_REQUIRED`.

## The keystone — rule #1/#2/#4 at lock (3.5)

- **Rule #1 (required Supporting Outcome at lock — REQ-E-001, the demo headline):** the precondition reuses the REAL `AllowedActionResolver.canLock(actor, plan, commitments)` as the **single accept-gate** (§15 affordance↔enforcement single-source — no second copy), then a **fail-closed** diagnosis picks the specific 409: empty → `409 EMPTY_PLAN_LOCK`; any unlinked planned → `409 UNLINKED_PLANNED_COMMITMENT` + `fieldErrors[]` naming each unlinked `commitments[N].supportingOutcomeId` (+ `constraint=planned_commitment_requires_supporting_outcome_at_lock`, B.21). If `canLock` is false but no branch matches, it still throws a 409 (never a silent lock).
- **Rule #2 (locked-baseline immutability):** the lock just sets `state=LOCKED`; the 3.4b gates (`LOCKED_BASELINE_EDIT` / `alignmentStatus`→`ILLEGAL_STATE_TRANSITION`) are proven firing on the real locked state by an integration test.
- **Rule #4 (Outlook sync NEVER blocks the lock — NEVER-TRIM):** `TransactionSynchronizationManager.registerSynchronization(afterCommit → publisher.publish(recordId))` fires the SNS publish only after the core commit; the publisher is `@Transactional(REQUIRES_NEW)`, catches any exception, leaves the record `PENDING_PUBLISH` (retained/retryable), never rethrows. Pinned: a throwing `@MockBean` gateway → `200 LOCKED` + record `PENDING_PUBLISH`. Pointer payload `SyncJobPointer{syncRecordId,eventKind,env,traceId}` (rule #7).

## Decisions made

- **Single-source `canLock` accept-gate + fail-closed diagnosis** — the lock precondition is the SAME predicate the affordance emits; diagnosis runs only on rejection and is fail-closed (§15).
- **Non-blocking publish via `afterCommit`→`REQUIRES_NEW`** swallow-never-rethrow, with an immediate-publish fallback when no synchronization is active (unit context). Synchronously testable (afterCommit runs in-thread before the controller returns).
- **Concurrent double-lock→409 proven by decomposition**, not a flaky threaded race: `@Version` generates the `OptimisticLockingFailureException` (§11/1.6) + the service propagates it (doesn't swallow) + `ProblemDetailsExceptionHandler` maps it to `409 ILLEGAL_STATE_TRANSITION` without leaking the stack. Three deterministic links.
- **No active manager** (e.g. a manager locking their own plan) → the lock + `PLAN_LOCKED` audit + `IC_PLANNING` record still happen; review + projections + review-block are skipped — **forced** by `manager_review.manager_employee_id` being NOT NULL (verified in V1 DDL + the entity).
- **IC-owner-only lock authz** = new `authorizePlanMutation` (access-chokepoint → codeless 404; owner-check → `403 PLAN_OWNER_REQUIRED`+audit for a manager-direct-report who can read but not lock) — the plan-level parallel of 3.4b's `authorizeCommitmentMutation`. `PLAN_OWNER_REQUIRED` is authorizer-local (not an `ErrorCodes` constant), added to B.21.
- **`PlanMapper` maps the review for LOCKED+ plans** (null while DRAFT — the 3.3a/3.3b read tests stay green); `unresolvedDisputeCount=0` until the disputes slice.
- **§9 projection at lock = recompute-from-source INSERT** (`plannedCount`, `misalignedCount`=`alignment_status=MISALIGNED`, `needsReviewCount`, `blockedCount`=`work_type=BLOCKER`, badges by SO→DO grain); stale-cell deletion deferred to reconciliation/dispute recomputes.
- **`ManagerReviewBlockResolver` folded into `PlanLifecycleService`**; `ClockConfig` reused (the task's "NEW ClockConfig" note was stale); `LifecycleSnsGateway` seam + a no-op `LoggingLifecycleSnsGateway` default (real SNS = Phase 12).

## Decisions explicitly NOT made (deferred)

- **Disputes Option-A B.6 edit** (`dispute?: AlignmentDisputeDto`, drop `hasUnresolvedDispute`) — orchestrator-owned, HELD for the disputes slice (Phase 5); the lead is surfacing it to the user as a possible reprioritization (it unblocks frontend 9.11a).
- **Projection completeness** — 3.5 ships the §9 recompute as an INSERT at lock; `unresolvedDisputeCount` + the misaligned-OR-open-dispute union + stale-cell deletion land at the disputes/reconciliation slices.
- **§9 `carry_forward_count` source field** — 3.5 derives carry-forward from `carryForwardSourceCommitmentId != null` (the only signal at lock). The `ReconciliationOutcome` enum **does** include `CARRIED_FORWARD` (a 3.5 Step-9 flag claiming otherwise was verified mistaken — no drift). Whether a reconciliation week's count uses the source-outcome or the successor-link is pinned when reconciliation lands.
- **Real AWS SNS gateway + demo-failure adapter** — Phase 12 / §10.

## Open follow-ups (next session)

- **Phase 4 — post-lock IC lifecycle** (brief 038, the lead-directed default): start-reconciliation (E9), add-unplanned-commitments (E10), carry-forward, close-reconciliation (E11). Folds in the Carry-forward Phase-4 items: re-derive the PATCH editable-field allow-list **per plan state** (don't widen `!= DRAFT`); reconciliation E6 fields (`reconciliationOutcome`/`outcomeNote`) + the projection upsert on commitment mutation.
- **HELD:** disputes slice (Phase 5, the B.6 Option-A edit + `dtos.ts` mirror coordination); 3.1b V5/V6 demo seed.
- **Carry-forward (backend):** `MANAGER_ROLE_REQUIRED` guard (manager-API surface), `managerAlignmentNote` write (manager-review slice), JPA-auditing populator (+ commitment `createdAt`-asc ordering consumer), the §9 carry-forward-count source-field pin, projection completeness.

## Reachability

`POST /api/plans/{id}/lock` is proven reachable E2E by `PlanLockEndpointTest` (`@SpringBootTest` + Testcontainers PG16) through the 2.6 security chain → `PlanController` → `PlanLifecycleService.lock` → `authorizePlanMutation` + `canLock` + the SLA/projection/sync/review-block helpers + the afterCommit publisher; `LoggingLifecycleSnsGateway` is the default `LifecycleSnsGateway` bean. The rule-#4 non-blocking path is pinned by a throwing `@MockBean LifecycleSnsGateway` (200 LOCKED + `PENDING_PUBLISH`). The optimistic-lock→409 mapping is pinned at the handler + service-propagation layers. No separate `/wired` run needed — the per-slice E2E tests ARE the reachability proof.
