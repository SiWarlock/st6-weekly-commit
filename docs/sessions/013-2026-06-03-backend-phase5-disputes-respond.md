# Session 013 — Backend: Phase-4 close (4.6) → Phase-5 disputes through respond (E5 authz fix · 5.2 · 5.3 · 5.3b · 5.4)

- **Date:** 2026-06-03
- **Phase:** Phase 4 close-out (4.6) + Phase 5 (manager review / SLA / disputes) through the IC-respond command.
- **Role:** implementer `st6-main-wc-api-implementer` (`b7849b0e`) — **session doc authored by `st6-main-orchestrator` (`4570249c`)**. The impl cycled at **75% ACTION** at the clean 5.4 boundary; its `/session-end` was **skipped per the lead's call** (a full session-end — wiring audit + a doc for a 6-slice run — would likely push it past 80% mid-write into the confabulation zone, and all six slices are committed + green). The orchestrator reviewed 5.3b/5.4 directly (Step-2.5 + Step-9) and carries 4.6/E5/5.2/5.3 from handoff 008 + the briefs, so it captures the continuity here.
- **Predecessor:** [012](012-2026-06-03-backend-phase4-functional-surface.md) (4.4/4.4b/4.5) + orch handoff `docs/team-handoffs/008-*` (4.6/E5/5.2/5.3 routing).
- **Successor:** _(fresh full-budget backend impl — 5.5 resolve E19, then 5.5b dispute affordances → 5.6 → 5.7.)_

## Why this session existed

The standing backend impl (`b7849b0e`) ran the **Phase-4 closer + the Phase-5 disputes spine** across two orchestrator sessions: **4.6/E5/5.2/5.3** under the prior orch (`90dc6de7`, routed via handoff 008), then **5.3b/5.4** under the fresh orch (`4570249c`). Phase 4 is now CLOSED; Phase 5's manager-review + dispute lifecycle is live through `OPEN → IC_RESPONDED` (open + respond); only resolve (5.5) + the dispute affordances (5.5b) + manager visibility (5.6) + managerAlignmentNote (5.7) remain.

## What was built

### Slice 4.6 — REQ-E-005 baseline-immutability acceptance proof — `1cb53c6`
The Phase-4 closer: a Testcontainers full-reconciliation-pass integration test proving the locked planned baseline stays byte-identical across `LOCK → start → record outcomes → add unplanned → carry-forward → close`. **GREEN first-try** — the 4.1–4.5 implementation preserves the baseline end-to-end. **🎉 Phase 4 COMPLETE (4.1–4.6).**

### E5 authz fix (brief 055) — `e71b7b7` — the escalated §6 hole CLOSED
The shipped-3.4a E5 create (`POST /api/plans/{id}/commitments`) used `authorizePlanAccess` → a manager could author on a report's DRAFT plan (violates §6). Fixed: swap to `authorizePlanMutation` (owner-only) + a manager-of-owner-403 test. Security-reviewer clean PASS. **LESSONS §32** + forbidden-pattern #7 (authorship/mutation never uses `authorize…Access`).

### Slice 5.2 — mark-reviewed (E16, brief 056) — `bf814cb`
`POST /api/manager/reviews/{reviewId}/mark-reviewed` (direct manager, plan `LOCKED`+) → derives `REVIEWED` vs `REVIEWED_WITH_DISPUTES` from the plan's unresolved-dispute count (server-derived, never client-supplied). NEW `ReviewStatusDeriver` + the **manager-mutation authz** (`authorizeReviewMutation` — active-direct-manager-only, the IC-owner denied; the inverse of `authorize…Mutation`). Drains the `MANAGER_ROLE_REQUIRED` Carry-forward. Security-reviewer clean PASS. **LESSONS §33** (manager-capability authz + the 404-vs-403 namespace-legitimacy tree).

### Slice 5.3 — open-dispute (E17, brief 058) — `8e2c91f` — SAFETY-CRITICAL (rule #6)
`POST /api/commitments/{id}/disputes` (active direct manager, plan `LOCKED`+) creates an `OPEN` `alignment_dispute` (`flagType` + required `managerNote`); rejects a second unresolved dispute with `409 SECOND_OPEN_DISPUTE` (service pre-check + the V2 partial-unique DB backstop); re-derives the parent review (`REVIEWED → REVIEWED_WITH_DISPUTES`). Rule-#6 single-unresolved invariant + the manager-capability authz (`IC_CANNOT_OPEN_DISPUTE`, NEW B.21 code — the IC-owner attempting to open on their own commitment → 403; the §33 commitment-side variant). NEW `DisputeController`/`DisputeService`/`AlignmentDisputeDto`/`OpenDisputeRequest`/`DisputeMapper`. Security-reviewer clean PASS.

### Slice 5.3b — `WeeklyCommitmentDto.dispute?` nest (B.6 Option-A, brief 059) — `1991a91` — the frontend 9.11a unblock
Nests `dispute?: AlignmentDisputeDto` (the commitment's current `OPEN`/`IC_RESPONDED` dispute, else null) in `WeeklyCommitmentDto`, **discharging the documented transitional subset** and dropping the never-implemented `hasUnresolvedDispute` from the contract (the user-approved Option-A, `docs/planning/023` §6). `CommitmentMapper`'s single private core resolves the unresolved dispute via the existing `DisputeMapper` + `AlignmentDisputeRepository.findByCommitmentIdAndStatusIn({OPEN,IC_RESPONDED})`. Read-path slice (no security-reviewer) — the dispute rides the existing E3/E4 authz (no new IDOR surface). **Unblocked frontend 9.11a** (the backend orch claimed the ARCHITECTURE B.6 window + pinged the frontend orch to mirror `dtos.ts`). The mapper-unit "resolved-only" case correctly relocated to the endpoint layer (a mocked finder can't distinguish resolved-only from none); query-side bucket-scoping pinned via an `ArgumentCaptor`.

### Slice 5.4 — IC respond to dispute (E18, brief 060) — `95559ed` — SAFETY-ADJACENT (rule-#2 exception)
`POST /api/disputes/{id}/respond` (owning IC, dispute `OPEN`) → `OPEN → IC_RESPONDED` (sets `icResponse`) and/or revises the disputed commitment's `supportingOutcomeId`. The **SO revision is a deliberate, tightly-gated exception to rule #2 (locked-baseline immutability)** — the only post-lock SO-write path besides the 4.5 `(RECONCILING,UNPLANNED)` cell: reachable only via respond-on-an-`OPEN`-dispute by the owning IC, the commitment loaded by `dispute.getCommitmentId()` (no cross-commitment vector), touching ONLY `supportingOutcomeId`. Respond does NOT resolve (manager-only, E19) and does NOT re-derive the review or touch projections (the dispute stays unresolved). Authz = the §33 **respond-variant** (`authorizeDisputeResponse`, IC-owner-only; the direct manager → `403 MANAGER_CANNOT_RESPOND_DISPUTE` capability, NEW B.21 code; unrelated/non-owning → `404`). `DISPUTE_RESPONDED` audit carries `supportingOutcomeRevised` + the new SO id (§15-safe), never `icResponse`. NEW `RespondDisputeRequest`. **Ad-hoc security-reviewer CLEAN PASS (0 findings)** — adversarially verified the rule-#2 exception is escape-proof. **LESSONS §34.**

## Decisions made

- **B.6 Option-A realized + `hasUnresolvedDispute` dropped from the contract** (5.3b). The dispute is an entity inside the commitment aggregate (≤1 unresolved, rule #6) → exposed through the aggregate root, no separate `GET /api/disputes` endpoint, no extra round-trip, inherits the commitment's authz scoping.
- **Single private-core lookup site** for the dispute nest (5.3b) — all response paths (write + read) carry `dispute?` consistently; per-commitment finder (consistent with the shipped mapper's per-commitment `resolveBreadcrumb`; batch deferrable to Phase 6).
- **The rule-#2 SO-revision exception is escape-proof** (5.4) — exactly one writable field, double-gated (authz chokepoint + `OPEN` state), target loaded by an owning id from the trusted entity (never the request), audited, and the carve-out kept local to the dispute path (E6 PATCH gate untouched). Banked as the §34 reusable checklist.
- **Respond authz = the §33 IC-owner-capability variant** (5.4) — the exact mirror of resolve's `IC_CANNOT_RESOLVE_DISPUTE`: respond is IC-only, so the manager (legitimate `/api/disputes` user) → 403 capability, unrelated → 404 IDOR.
- **Respond `at-least-one-of {icResponse, supportingOutcomeId}` required** (5.4, service-checked) — an empty respond is a no-op (REQ-F-016 = "respond BY changing the SO OR adding rationale").
- **5.4 gets a security-reviewer** (orch override of handoff "likely no") — a new path mutating a baseline-immutable field is a safety surface.

## Decisions explicitly NOT made (deferred)

- **The dispute `allowedActions` affordances** (`OPEN_DISPUTE`/`RESPOND_DISPUTE`/`RESOLVE_DISPUTE`) — NOT emitted in 5.3/5.4 (the enum values exist; `DisputeMapper` is context-free, `commitmentActions` emits only `CARRY_FORWARD`). Deferred to a dedicated read-path slice **5.5b** after 5.5 (the 4.4→4.4b enforcement→affordance split; never bundle a read affordance into a safety-mutation commit). 5.5b is the frontend control-activation point. (Carry-forward entry added; frontend aligned — building dormant controls gated on `allowedActions`.)
- **Projection completeness** (`unresolvedDisputeCount` + the §9 `misaligned_count` union + stale-cell deletion) — re-sequenced to Phase 6 (where the command center reads + recomputes-from-source); the dispute slices do NOT touch `ProjectionService`.
- **Dispute-bucket DRY** — 3 private `{OPEN,IC_RESPONDED}` copies (`ReviewStatusDeriver`/`DisputeService`/`CommitmentMapper`); a shared `DisputeStatus.UNRESOLVED`-style constant deferred (fold into the `ReconciliationProjectionRefresher` refactor).

## TDD compliance

**Clean — no violations.** Each slice ran RED→Step-2.5→GREEN; the orchestrator reviewed every Step-2.5 + Step-9 (5.3b/5.4 by the fresh orch; 4.6/E5/5.2/5.3 by the prior orch per handoff 008). `./gradlew check` green on each slice (5.3b: +7/−1 tests; 5.4: +20).

## Reachability (Step 7.5)

All features reachable from production HTTP entry points: 4.6 via the full reconciliation integration test; E5 via `POST /api/plans/{id}/commitments`; 5.2 via `POST /api/manager/reviews/{id}/mark-reviewed`; 5.3 via `POST /api/commitments/{id}/disputes`; 5.3b via the `dispute?` nest on `GET /api/plans/{id}`+`/current`; 5.4 via `POST /api/disputes/{id}/respond`. The dispute `allowedActions` stay empty (5.5b owns them) — no tested-but-unwired branch.

## Security review

E5/5.2/5.3/5.4 each got the ad-hoc security-reviewer — **all CLEAN PASS, 0 critical/high.** 5.4's review adversarially verified the rule-#2 exception is escape-proof (single writable field, double-gate, load-by-owning-id-not-request, audited) + the §33 respond-authz mapping is correct + non-leaking. 5.3b was read-path (no reviewer, per policy). 4.6 was an acceptance proof (no new safety surface).

## Open follow-ups

### Step-9 items (routed hot — orchestrator-written, in the round commit)
- **ARCHITECTURE:** B.6 dispute nest (5.3b); B.21 `MANAGER_CANNOT_RESPOND_DISPUTE` (5.4); §3 rule-#2-dispute-gated-SO-revision note (5.4). **CLAUDE cross-doc:** `WeeklyCommitmentDto`↔B.6 (discharged subset), `RespondDisputeRequest`↔B.2-E18, `ErrorCodes` row. **LESSONS:** §34 (gated-exception checklist + IC-owner-capability authz) + index. **MVP_TASKS:** 5.5b Carry-forward + Phase-5 ticks.

### Carried obligations (still live)
- **5.5b dispute affordances** (read-path, after 5.5) — emit the three dispute `allowedActions` on E3/E4; the frontend control-activation point; ping the frontend orch on landing.
- **Phase-6 manager-read:** `MARK_REVIEWED` read-affordance + real `unresolvedDisputeCount` on the plan-read; projection completeness; `blocked_count` source pin.
- **`ReconciliationProjectionRefresher` extraction** (refactor slice, before the Phase-6 recompute surfaces).
- **5.7 `managerAlignmentNote`** write (E6, manager-only, post-lock-mutable, audited).

### Wiring tasks
None — all features wired this session.

## How to use what was built

The dispute lifecycle is live through `open → respond`: a manager flags a direct report's locked commitment (E17), the IC responds — revising the Supporting Outcome and/or adding rationale (E18, the rule-#2 gated re-alignment) — and the plan+commitments read (E3/E4) nests the active dispute so the UI can display + act. **5.5** (resolve, E19, manager-only) completes the loop (`→ RESOLVED` + review re-derivation), then **5.5b** lights up the frontend dispute controls by emitting the dispute `allowedActions`.
