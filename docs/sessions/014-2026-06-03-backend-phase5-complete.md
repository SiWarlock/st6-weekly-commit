# Session 014 — Backend: Phase 5 COMPLETE — dispute resolve/affordances + draft-visibility + managerAlignmentNote (5.5/5.5b/5.6/5.7)

- **Date:** 2026-06-03
- **Phase:** Phase 5 (manager review / SLA / disputes) — close-out.
- **Role:** implementer `st6-main-wc-api-implementer` (`0095db66`, the fresh post-5.4 impl) — **session doc authored by `st6-main-orchestrator` (`4570249c`)**, which reviewed every Step-2.5 + Step-9 this round. The impl is healthy (not cycled); this is the orch's Phase-5-close continuity record (the impl continues to Phase 6 unless the lead cycles).
- **Predecessor:** [013](013-2026-06-03-backend-phase5-disputes-respond.md) (4.6/E5/5.2/5.3/5.3b/5.4) + the post-5.4 impl cycle.
- **Successor:** _(Phase 6 — manager command-center / projection-completeness.)_

## Why this session existed

The fresh backend impl (`0095db66`, spawned at the 5.4 cycle) ran the **Phase-5 tail to completion**: the dispute resolve command (5.5), the dispute affordance emission (5.5b — the frontend-activation bridge), the manager-draft-visibility acceptance proof (5.6), and the manager-owned alignment note (5.7). **Phase 5 (5.1–5.7) is now backend-COMPLETE.**

## What was built

### Slice 5.5 — resolve dispute (E19, brief 062) — `2d210d0` — closes the dispute loop
`POST /api/disputes/{id}/resolve` (active direct manager, dispute `OPEN`/`IC_RESPONDED`) → `RESOLVED` + `resolvedAt`. **Wires the pre-built `authorizeDisputeResolution`** chokepoint (IC-owner → `403 IC_CANNOT_RESOLVE_DISPUTE`, unrelated/non-direct → `404`; closing a previously-unwired branch). **Re-derives the parent review** via the shared `reDeriveReviewStatus` (the `NOT_REVIEWED`-guarded path from 5.3 — `derive()` is unconditional, so the caller guards): resolving the last unresolved dispute flips `REVIEWED_WITH_DISPUTES → REVIEWED`; a `NOT_REVIEWED` review is preserved. The `saveAndFlush(RESOLVED)`-before-re-derive ordering is load-bearing (the deriver's count query must see the resolved row). Body-less (`resolutionNote` unimplemented in MVP — no entity field). No projection (Phase 6). `DISPUTE_RESOLVED` audit. **Ad-hoc security-reviewer CLEAN PASS.** Completes the dispute loop (open 5.3 → respond 5.4 → resolve 5.5) + the R4 demo + REQ-F-017.

### Slice 5.5b — dispute affordances (read-path, brief 063) — `003394a` — the frontend control-activation bridge
Emits the three dispute `allowedActions` on the plan read (E3/E4) **per viewing actor**: `OPEN_DISPUTE` on `WeeklyCommitmentDto` (direct manager + `LOCKED`+ + no unresolved dispute), `RESPOND_DISPUTE` (owning IC + `OPEN`) + `RESOLVE_DISPUTE` (direct manager + `OPEN`/`IC_RESPONDED`) on the nested `AlignmentDisputeDto`. `PlanMapper` determines `viewerIsDirectManager` ONCE per read (one `findByDirectReportEmployeeIdAndActiveTrue` lookup) + threads the boolean down; the resolver stays repo-free; `OPEN_DISPUTE`'s "no unresolved dispute" reuses the 5.3b nested resolution (no N+1). Affordances **mirror — not share —** the void-throw authorizers (§24/§31, eligibility-sweep-pinned). Scoped to E3/E4 (E15 doesn't use `PlanMapper`); no MARK_REVIEWED/projection (Phase 6). Read-path (no security-reviewer). **LESSONS §35.** **This lit up the frontend's dormant 9.11a controls** (pinged `st6-main-wc-web-orchestrator` on landing → 9.14 manager dispute surface).

### Slice 5.6 — manager draft-visibility + post-lock gate (REQ-F-009/010, brief 064) — `19dd610` — acceptance proof
**Satisfied-by-prior-slices** (like 5.1/4.6) — a scope audit confirmed REQ-F-009 (manager reads a direct-report DRAFT plan — `authorizePlanAccess` is state-agnostic) and REQ-F-010 (manager mutations on DRAFT → `409` — `ReviewService`/`DisputeService` guards) are already shipped. The slice adds the **acceptance proof** the prior slices lacked: the manager-reads-direct-report-DRAFT-plan E4 test (200 + details + `managerReview` absent + no manager affordances + no audit on the authorized read) + the **no-audit-on-DRAFT-409 posture** test-pinned (`verify(auditService, never())`). Tests-only, no production change. No security-reviewer.

### Slice 5.7 — managerAlignmentNote write (E6 PATCH, brief 066) — `f31710c` — Phase-5 closer
`PATCH /api/commitments/{id}` now accepts `managerAlignmentNote` — the one **manager-owned, post-lock-mutable** commitment field (REQ-F-006), the deferred 3.4b item. Introduces **field-level authz on the shared E6 PATCH**: a `managerAlignmentNote` patch routes to a new `authorizeManagerAlignmentNote` chokepoint (manager-of-owner-only; IC-owner → `403 IC_CANNOT_WRITE_MANAGER_NOTE`; unrelated/non-direct → `404`; §33 commitment-field manager-capability), is **rejected if mixed with any IC field** (single-actor-per-patch → `400`, request-shape/IDOR-safe — no resource load), writable `LOCKED`+ only (DRAFT → `409`), validated ≤4000 cp, audited (`COMMITMENT_ALIGNMENT_NOTED`, no note body, §15). Present-null clears it; `@Version` txn; the IC path is **byte-for-byte unchanged**. **Ad-hoc security-reviewer CLEAN PASS.** **LESSONS §36.**

## Decisions made

- **The dispute-loop review re-derivation reuses 5.3's `reDeriveReviewStatus`** (the `NOT_REVIEWED`-guarded path) — `ReviewStatusDeriver.derive()` is unconditional, so the caller guards; the `saveAndFlush`-before-re-derive ordering is essential.
- **`resolutionNote` is unimplemented in MVP** (E19 body-less — no entity field; a manager uses a comment for resolution context). §5/B.8 noted.
- **Dispute affordances established the `viewerIsDirectManager` threading** (computed once at the `PlanMapper` root) — Phase-6 MARK_REVIEWED reuses it (LESSONS §35).
- **No audit on a DRAFT-state 409** — a manager's DRAFT-state mutation rejection is a workflow-state error, not an authorization denial (the manager IS authorized; the plan isn't locked). Audits are reserved for genuine denials (§6/§15 note; 5.6).
- **`managerAlignmentNote` does NOT generalize the manager-capability authorizers yet** — `authorizeReviewMutation` uses the `deny404` namespace tree (IC has no `/api/manager`), the commitment-keyed ones use `deny403` (IC uses `/api/commitments`); only 2 commitment-keyed instances → not a rule-of-three. Explicit parallel `authorizeManagerAlignmentNote` (LESSONS §36; generalization deferred to the 3rd instance).

## Decisions explicitly NOT made (deferred to Phase 6 / Carry-forward)

- **Projection completeness** (`unresolvedDisputeCount` + the §9 `misaligned_count` union + stale-cell deletion) + the **MARK_REVIEWED read-affordance** + real `unresolvedDisputeCount` on the plan read — all Phase 6 (the command center reads + recomputes-from-source; 5.5b's `viewerIsDirectManager` threading is the reuse point).
- **`authorizeCommitmentManagerCapability` extraction** — the 3rd commitment-keyed-403 manager-capability authorizer (after dispute-creation + managerAlignmentNote) → extract then. Note-if-recurs.
- **`ReconciliationProjectionRefresher` extraction** + the §9 `blocked_count` source pin — still live (before/at Phase 6).

## TDD compliance

**Clean — no violations.** Each slice ran RED→Step-2.5→GREEN (5.6 was GREEN-against-shipped-code, the correct acceptance-proof discipline); the orchestrator reviewed every Step-2.5 + Step-9. `./gradlew check` green on each. 5.5/5.7 each got the ad-hoc security-reviewer (CLEAN PASS); 5.5b/5.6 were read-path/proof (no reviewer, per policy).

## Reachability (Step 7.5)

All reachable from production HTTP entry points: 5.5 `POST /api/disputes/{id}/resolve`; 5.5b the dispute `allowedActions` on `GET /api/plans/{id}`+`/current`; 5.6 proves the shipped E4-read + E16/E17-DRAFT-409 wiring; 5.7 `PATCH /api/commitments/{id}` → `updateManagerAlignmentNote`. No tested-but-unwired gaps (5.5 wired the previously-unwired `authorizeDisputeResolution`).

## Security review

5.5 + 5.7 each got the ad-hoc security-reviewer — **both CLEAN PASS, 0 findings.** 5.5: rule #2/#3/#6 (the review re-derivation + the manager-only resolve). 5.7: rule #2/#3 (the field-level authz — the IC path is structurally unable to set managerAlignmentNote; the mixing-400 is IDOR-safe request-shape validation) + §15 (no note body). 5.5b read-path / 5.6 proof — no reviewer.

## Open follow-ups

### Step-9 items (routed hot — orchestrator-written, in the round commit)
- **ARCHITECTURE:** B.8 `resolutionNote`-unimplemented note (5.5); B.21 `IC_CANNOT_WRITE_MANAGER_NOTE` (5.7); §6/§15 no-audit-on-DRAFT-409 posture (5.6). **CLAUDE cross-doc:** `PatchCommitmentRequest`↔B.6 (5.7 managerAlignmentNote). **LESSONS:** §35 (per-viewer affordance threading), §36 (field-level authz) + index. **MVP_TASKS:** 5.5/5.5b/5.6/5.7 ticks + the Phase-5-close Log + Carry-forward.

### Carried obligations (live — Phase 6)
- Projection completeness + MARK_REVIEWED read-affordance + real `unresolvedDisputeCount` (Phase 6, reuse the 5.5b `viewerIsDirectManager` threading); `authorizeCommitmentManagerCapability` extraction (3rd instance); `ReconciliationProjectionRefresher` extraction; the §9 `blocked_count` source pin.

### Wiring tasks
None — all features wired; 5.5 closed the previously-unwired `authorizeDisputeResolution`.

## How to use what was built

Phase 5 is complete: a manager reviews (E16), opens an alignment dispute (E17), the IC responds — revising the SO or adding rationale (E18, the rule-#2 gated re-alignment) — the manager resolves (E19, re-deriving the review), and the plan read emits the per-viewer dispute affordances (5.5b) that drive the frontend's open/respond/resolve controls. The manager annotates a commitment via `managerAlignmentNote` (E6, post-lock). **Phase 6** picks up the manager command-center / projection-completeness, reusing the 5.5b `viewerIsDirectManager` threading for the MARK_REVIEWED affordance + the real dispute counts.
