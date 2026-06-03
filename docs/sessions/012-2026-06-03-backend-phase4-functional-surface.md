# Session 012 — Backend: Phase-4 functional surface complete (4.4 carry-forward + 4.4b affordance + 4.5 close-reconciliation)

- **Date:** 2026-06-03
- **Phase:** Phase 4 (post-lock reconciliation lifecycle) — functional-surface close-out
- **Role:** implementer `st6-main-wc-api-implementer` (`0e5d37f3`) — **session doc authored by `st6-main-orchestrator` (`90dc6de7`)**. The impl cycled at **79% ACTION** at the clean 4.5 boundary (before 4.6 would breach HARD-STOP mid-slice); its `/session-end` was **skipped per the lead's call** (79% is the near-HARD-STOP confabulation zone, and the 4.5 work is committed). The orchestrator reviewed every slice (Step-2.5 + Step-9), so it captures the continuity here.
- **Predecessor:** [010](010-2026-06-03-backend-phase3-close-and-phase4-entry.md) (3.4b/3.5/4.2) + orch handoff `docs/team-handoffs/005-*` (4.1/4.3 routing, committed `ccb2619`).
- **Successor:** _(fresh backend impl — 4.6 REQ-E-005 full-pass proof + R5 Cypress, brief 052)._

## Why this session existed

The standing backend impl (`0e5d37f3`) ran the **entire Phase-4 functional surface** across two orchestrator sessions without cycling: **4.1** (`a35961a`) + **4.3** (`5af8356`) under the prior orch (`16e30b32`, routed in `ccb2619`/handoff 005), then **4.4 / 4.4b / 4.5** under the fresh orch (`90dc6de7`). This doc captures 4.4/4.4b/4.5 (4.1/4.3 are in handoff 005 + the tracker). Phase-4 lifecycle is now functionally complete (`LOCKED→RECONCILING→RECONCILED` + outcome-recording + unplanned + carry-forward + close); only **4.6** (the REQ-E-005 acceptance proof) remains.

## What was built

### Slice 4.4 — carry-forward (E12) — `c48c6fc` — the most complex Phase-4 slice
`POST /api/commitments/{id}/carry-forward` (owning IC, parent plan `RECONCILING`, no body) in a new `CarryForwardService`: sets the source `reconciliation_outcome=CARRIED_FORWARD` (the ONLY path that sets it — 4.1 rejects a direct PATCH→CARRIED_FORWARD `400`; overwrites a prior completion outcome), creates a linked successor (`carry_forward_source_commitment_id=source`) in the next Mon–Sun DRAFT plan — **creating that shell only if absent**, reusing on the V1 `unique(employee_id, week_start_date)` conflict. **Idempotent per source** (existence pre-filter on the self-link → re-invoke returns the existing successor, no second shell, source not re-touched); concurrent double-carry serialized by the source `@Version` (→409). Successor starts unlinked-DRAFT, `commitmentKind=PLANNED`, content copied, `workType` copied if planned else `STRATEGIC`. Recomputes the SOURCE plan's projection in-txn (§9 lockstep); **no `ProjectionService` change** (see the carried-IN pin below); IC audit `COMMITMENT_CARRIED_FORWARD`; **no Outlook sync record** (§10 has no carry-forward trigger). Owner-only via `authorizeCommitmentMutation`. **Ad-hoc security-reviewer PASS (0 critical/high; 2 medium → Carry-forward hardening).** NEW `CarryForwardService` + `WeeklyCommitmentRepository.findByCarryForwardSourceCommitmentId`; 11 unit + 8 endpoint tests (incl. the R5-style two-week chain / REQ-E-005 byte-identity + the no-sync-record pin).

### Slice 4.4b — per-commitment `CARRY_FORWARD` affordance — `f0f415a`
Emits `CARRY_FORWARD` in `WeeklyCommitmentDto.allowedActions` (empty since 3.3a) for an owning IC's carry-forward-eligible commitments **on the plan read (E3/E4)** — via a new `AllowedActionResolver.commitmentActions` + a context-aware `CommitmentMapper.toDto(c, plan, actor)` overload threaded through `PlanMapper` (the no-arg `toDto` stays empty for write responses; the UI re-reads). **Closes the carry-forward UX loop** (4.4 enforcement + 4.4b affordance → the frontend `CarryForwardButton`, which gates on `allowedActions.includes('CARRY_FORWARD')`, is now UI-reachable). Read-path slice (no security-reviewer).

### Slice 4.5 — close-reconciliation (E10) — `e41aa6e` — Phase-4 functional surface complete
`POST /api/plans/{id}/close-reconciliation` (owning IC, `RECONCILING`, no body) → `RECONCILED` + `reconciledAt`, gated on the **completeness precondition** (every PLANNED has an outcome AND every UNPLANNED has both an outcome AND a Supporting-Outcome link) → else `422 UNPLANNED_MISSING_LINK_AT_CLOSE` + per-commitment `fieldErrors[]` (keyed `commitments[<id>].<field>` → a per-violation constraint). A `CARRIED_FORWARD` counts as an outcome (§30). Mirrors `startReconciliation`; recomputes the projection `plan_state` + `PLAN_RECONCILED` audit in one `@Version` txn; **no sync record**; non-blocking on manager review (REQ-F-024). **Plus the E6 PATCH allow-list extension to a (state × kind) matrix:** `supportingOutcomeId` becomes editable in `RECONCILING` for an **UNPLANNED** commitment only (the pre-close link), PLANNED stays frozen (`LOCKED_BASELINE_EDIT`) — never widened (LESSONS §27-ext). Emits the `CLOSE_RECONCILIATION` (RECONCILING) + `ADD_UNPLANNED` (LOCKED ∨ RECONCILING — 4.3 enforces both) plan affordances. **Ad-hoc security-reviewer PASS (0 findings; the 6-cell (state×kind) trace confirmed the allow-list opens exactly the one cell, all prior freezes preserved).** NEW `web/UnplannedMissingLinkAtCloseException` + `ErrorCodes.UNPLANNED_MISSING_LINK_AT_CLOSE`; 10 unit + 10 endpoint tests (incl. the link→close flow).

## Decisions made

- **The §9 `carry_forward_count` source-field pin → the carried-IN (successor-link) reading** (the existing 3.5 `ProjectionService.isCarryForward` predicate, `carryForwardSourceCommitmentId != null`), confirmed by the Appendix-E R5 fixture (CARRY_FORWARD badge on the week holding the successor). The source-week count does NOT change at carry-forward (no count keys on `reconciliation_outcome`); it materializes at the next-week lock. **This corrected the 4.4 task-spec wording "source week ++"** and means 4.4 needs **no `ProjectionService` change**. (LESSONS §30.)
- **Carry-forward authz = `authorizeCommitmentMutation`** (commitment-keyed chokepoint), NOT `authorizePlanMutation` (the handoff's suggestion) — E12 is commitment-keyed, so the commitment chokepoint is chokepoint-first-correct (mirrors 4.1/3.4b). Close (E10) uses `authorizePlanMutation` (plan-keyed).
- **Idempotency = existence pre-filter + source `@Version`, no migration.** The optional `uq(carry_forward_source_commitment_id) WHERE NOT NULL` backstop is deferred (Carry-forward hardening) — the `@Version` + V1 `unique(employee,week)` cover the races today.
- **The affordance is a UX-narrowed SUBSET of enforcement, not literal-identity single-source** (LESSONS §31). E12 idempotent-accepts an already-carried re-invoke; the affordance hides it (`reconciliationOutcome != CARRIED_FORWARD`). The invariant held is the subset direction (`eligible ⟹ enforcement-accepts`), pinned by an eligibility⟹preconditions sweep — **`CarryForwardService` is NOT changed to reuse `canCarryForward`** (that would break E12 idempotency/REQ-D-006).
- **The E6 editable-field allow-list is now keyed on (plan state × commitment kind)** (LESSONS §27-ext): `supportingOutcomeId` split out of the always-frozen `touchesBaseline` set + a separate SO gate opening ONLY `(RECONCILING, UNPLANNED)`. Never widen a gate — add a narrower cell.
- **Close completeness = ONE code** `UNPLANNED_MISSING_LINK_AT_CLOSE` (the architecture's only close code) with granular per-commitment/per-violation `fieldErrors` (id-keyed, consistent with the lock-time `UNLINKED_PLANNED_COMMITMENT`).

## Decisions explicitly NOT made (deferred)

- **The shared `transition(...)` skeleton across lock/start/close — NOT extracted (leaky).** The three transitions vary heavily (canLock + manager-review + 2 sync records vs IC_RECONCILIATION sync vs completeness validation + no sync); a shared template would need too many hooks. Left as three mirror methods.
- **`ReconciliationProjectionRefresher` extraction — deferred to a dedicated refactor slice** (Carry-forward). The manager-lookup→review→recompute pattern recurs across 5 call sites (PlanLifecycleService ×3 inline + CommitmentService + CarryForwardService) — cleanly DRY-able (rule-of-three) but cross-3-class + test-mock rewire, so not bundled into a safety-adjacent feature slice.
- **The §9 `blocked_count` source-field pin** (work_type=BLOCKER only, or union reconciliation_outcome=BLOCKED) — pin at the manager-projection/reconciliation slice, with the carry_forward_count pin.
- **The partial-unique carry-forward-source backstop** — optional hardening (Carry-forward).

## TDD compliance

**Clean — no violations.** Each slice ran RED→Step-2.5→GREEN; the orchestrator reviewed every Step-2.5 test design (`APPROVED.`/`ADD:` headers — 4.4 added the no-sync-record pin, 4.4b the UNPLANNED-affordance pin, 4.5 the ADD_UNPLANNED-affordance) and every Step-9 summary before the commit. `./gradlew check` green on each slice.

## Reachability (Step 7.5)

All features reachable from production HTTP entry points: 4.4 `POST /api/commitments/{id}/carry-forward` → `CarryForwardService`; 4.4b the `CARRY_FORWARD` affordance via `GET /api/plans/{id}`+`/current` → `PlanMapper` → `commitmentActions`; 4.5 `POST /api/plans/{id}/close-reconciliation` → `PlanLifecycleService.closeReconciliation` + the E6 ext via the existing PATCH route + the `CLOSE_RECONCILIATION`/`ADD_UNPLANNED` affordances on the plan read. No tested-but-unwired gaps.

## Security review

4.4 + 4.5 each got the ad-hoc security-reviewer — **both PASS, 0 critical/high** (4.4: rule #2/#3/#7/§9/idempotency; 4.5: the 6-cell (state×kind) allow-list trace + the unbypassable close gate). 4.4b was read-path (no reviewer, per policy). 4.4's 2 medium findings (the optional partial-unique backstop) routed as Carry-forward hardening.

## Open follow-ups

### Step-9 items (routed hot this session — orchestrator-written)
- LESSONS **§30** (carry-forward transaction pattern), **§31** (affordance-subset pattern), **§27 extension** ((state×kind) allow-list matrix) + their CLAUDE index rows; CLAUDE cross-doc rows: `CreateUnplannedCommitmentRequest`↔B.6 (4.3), `PatchCommitmentRequest`↔B.6 (4.5 ext), `ErrorCodes`↔B.21 (close code realized + a lock-fieldErrors `[N]`→`[id]` drift fix). Audit actions `COMMITMENT_CARRIED_FORWARD` + `PLAN_RECONCILED` (free-string, no §15 edit). No new Appendix-A field; no `ARCHITECTURE.md` content edit (all "documented, now-realized").

### Carried obligations (still live — lead-gated)
- **E5 authz Finding** — shipped 3.4a E5 `POST /plans/{id}/commitments` create uses `authorizePlanAccess` → a manager can author on a report's DRAFT plan (violates §6); fix = swap to `authorizePlanMutation` + a manager-403 test, own slice + security-reviewer. **HELD pending the lead's relay of the user's approval.**
- **Disputes Option-A B.6 edit** (orch-owned) — nest `dispute?: AlignmentDisputeDto`, drop `hasUnresolvedDispute`; HELD behind Phase 4; unblocks frontend 9.11a.
- **`ReconciliationProjectionRefresher` extraction** (refactor slice, before Phase 5/6 which also recompute); the §9 `blocked_count`/`carry_forward_count` source pins; the partial-unique backstop.

### Wiring tasks
None — all features wired this session.

## How to use what was built

The Phase-4 lifecycle is functionally complete: an IC can lock → start-reconciliation → record outcomes (E6) → add unplanned (E11) → link an unplanned's SO (E6, RECONCILING+UNPLANNED) → carry forward unfinished work (E12) → close (E10) when complete. **4.6** proves REQ-E-005 (the locked planned baseline stays byte-identical across a full reconciliation pass) via a Testcontainers integration test + the R5 `ic-reconcile-carry-forward.feature` Cypress/Cucumber scenario — that's the Phase-4 closer.
