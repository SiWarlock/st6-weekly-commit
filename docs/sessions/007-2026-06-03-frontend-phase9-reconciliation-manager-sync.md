# Session 007 — Frontend Phase 9: IC reconciliation, manager surfaces, edit/delete, sync

- **Date:** 2026-06-03
- **Phase:** 9 (Frontend — `apps/wc-web/`)
- **Track:** `st6-main` frontend (`st6-main-wc-web-implementer`)
- **Predecessor:** [`005-2026-06-02-frontend-phase9-ic-workspace.md`](005-2026-06-02-frontend-phase9-ic-workspace.md)
- **Successor:** [011 — frontend Phase-ST styling spine (ST.4→ST.6b) + Phase-9 closeout](011-2026-06-03-frontend-st-styling-spine.md)

## Why this session existed

A fresh full-budget frontend implementer pair, cycled after 9.7. Charter: continue the Phase-9 frontend surface against the typed Appendix-B contract — IC reconciliation, the manager command-center + heatmap, the IC DRAFT edit/delete follow-up, and Outlook-sync visibility — each via `/tdd` with orchestrator Step-2.5 review. Disputes (9.11) was hit a hard contract gap mid-session and paused; 9.7b + 9.12 filled the unblocked queue.

## What was built (5 slices, 5 commits)

| Slice | Commit | Summary |
|---|---|---|
| 9.8 | `479732e` | IC reconciliation — outcome form, carry-forward, lifecycle (E9/E10), unplanned (E11) |
| 9.9 | `d25fd8e` | Manager command center — rows/filters (E13) + mark-reviewed (E16) |
| 9.10 | `67c38d1` | Manager heatmap grid (E14) + cell drilldown (E15) |
| 9.7b | `cbc526b` | IC commitment edit (E6) / delete (E7) UI — DRAFT baseline |
| 9.12 | `8883da8` | Outlook sync status badge (E22) + manual retry (E23) |

### Files created
- **9.8:** `commitment/ReconciliationOutcomeForm.tsx` (E6 outcome recorder — four completion outcomes only), `commitment/CarryForwardButton.tsx` (self-gated E12).
- **9.9:** `manager/managerApi.ts` (E13 getCommandCenter), `manager/CommandCenter.tsx` (rows + counts + pagination + per-row lazy review expand), `manager/CommandCenterFilters.tsx` (E13 filters), `review/reviewApi.ts` (E16 markReviewed), `review/MarkReviewedAction.tsx` (reusable, RETRY-style gated).
- **9.10:** `manager/HeatmapGrid.tsx` (`cells[]`→report×DO pivot + RiskBadges + cell-select), `manager/HeatmapCellDrilldown.tsx` (SO→commitment groups + max-totalPages pager).
- **9.12:** `sync/syncApi.ts` (E22/E23), `sync/SyncStatusBadge.tsx` (taxonomy + FAILED warning), `sync/SyncRetryAction.tsx` (E23 gated).
- (+ a `*.test.*` for each.)

### Files modified
- `shared/lib/dtos.ts` — verbatim B-mirror additions: B.11 `ManagerCommandCenterRowDto`, B.20 `PageEnvelope<T>`, B.7 `MarkReviewedRequest` (9.9); B.1 `RiskBadge` + B.12 `HeatmapCellDto`/`HeatmapResponseDto`/`HeatmapDrilldownDto`/`DrilldownOutcomeGroup` (9.10); B.1 `SyncStatus`/`EventKind`/`SyncRelatedType` + B.10 `OutlookSyncRecordDto` (9.12).
- `shared/lib/statusTaxonomy.ts` — `+SYNC_STATUS_TAXONOMY` (4th taxonomy; 9.12).
- `plan/plansApi.ts` (+E9/E10 lifecycle mutations, 9.8); `commitment/commitmentsApi.ts` (+E12 carry-forward, 9.8).
- `plan/PlanLifecycleBar.tsx` (START/CLOSE/ADD_UNPLANNED, 9.8); `commitment/CommitmentForm.tsx` (unplanned mode 9.8, edit mode 9.7b); `commitment/CommitmentList.tsx` (reconciliation controls 9.8, `planId` prop, Edit + DeleteCommitmentButton 9.7b); `commitment/ChessLayerFields.tsx` (`hideWorkType` 9.8); `commitment/DeleteCommitmentButton.tsx` (new, 9.7b).
- `plan/WeeklyPlanView.tsx` — accreted across slices: reconciliation surfaces (9.8), `planId`/ADD_UNPLANNED toggle (9.8), edit-flow wiring (9.7b), sync-surface mount (9.12).
- `routes/pages/CommandCenterPage.tsx` / `HeatmapPage.tsx` — placeholder → real (resolves the 9.4 carry-forward items).
- Test-only ripples: `routes/AppRoutes.test.tsx` + `remote/WeeklyCommitApp.test.tsx` (mock `managerApi`/`syncApi` as the real route tree began rendering those reads).

## Decisions made
- **Server-authoritative gating everywhere** — every affordance reads server `allowedActions[]`/`plan.state` via `can()`; invalidate→refetch; no optimistic updates; server `safeMessage`/`fieldErrors[]` rendered verbatim (LESSONS §11).
- **9.8 Q1 / coexist gating (orch TWEAK):** the outcome form (4 completion outcomes) and carry-forward (E12) are the mutually-exclusive choice set for an unresolved RECONCILING commitment; gated **independently** (recording either resolves it server-side → both vanish on refetch). `CARRIED_FORWARD` is set *only* via E12, never the form (backend 4.1).
- **9.9 Q2 / mark-reviewed wiring:** B.11 rows carry no `reviewId`/`allowedActions`; mark-reviewed is reached via a per-row expand that lazily `getPlanById`s (E4 authorizes the direct manager — `§5` line 382) to obtain `managerReview` (B.7). No `plansApi` change.
- **9.10 pager (orch TWEAK):** the E15 drilldown's single `page`/`size` paginates every SO group together; the client pager spans `max(totalPages across groups)` so a deeper group's commitments aren't hidden.
- **9.7b delete UX:** non-blocking in-app two-step confirm (Delete → Confirm/Cancel, re-armable), never `window.confirm`, no optimistic removal; new self-contained `DeleteCommitmentButton`.
- **9.12 non-blocking (rule #4):** the sync surface mounts in `WeeklyPlanView` and renders independently — a FAILED sync is visible while the lifecycle/list stay usable. Only `safeMessage` is rendered (rule #7); `SYNC_STATUS_TAXONOMY` keeps the §7 single-source-of-visual-truth pattern.
- **Taxonomy/tag reuse:** no `heatmap` tag (the `manager` tag covers command-center + heatmap projections, §9); a dedicated `SyncStatusBadge`/`RiskBadge` rather than a 4th `StatusBadge` kind.
- **`exactOptionalPropertyTypes` gotcha:** query-param interfaces whose optional fields are *cleared via patch* must declare `field?: T | undefined` (the `CommandCenterParams` fix).

## Decisions explicitly NOT made (deferred)
- **9.11 disputes — PAUSED on a contract Finding** (no read path for a dispute's `id`/`managerNote`: B.6 had only `hasUnresolvedDispute: boolean`, no GET-disputes endpoint). Escalated; user approved **Option A** (backend nests `dispute?: AlignmentDisputeDto` in B.6 + drops `hasUnresolvedDispute`). 9.11 split → **9.11a (disputes, waits on the B.6 contract edit)** / **9.11b (comments, has the E20 read path → buildable)**.
- **Manager `MANAGER_REVIEW_BLOCK` sync visibility** — out of scope for 9.12 (no plan-keyed read path); needs a new read path → later phase.
- **`weekStart` util extraction** — `currentWeekStartIso` now duplicated (CommandCenter + HeatmapGrid); extract a shared util on the 3rd recurrence (agreed), not now.
- **Remaining Phase-9 surfaces:** `RcdoBrowser` consumer surface (built 9.5, still unwired), `PlanHistoryPage` placeholder (9.4), 9.13 host-integration contract (OQ-004), ST.7 a11y/design-review.

## TDD compliance
**Clean.** All 5 slices were test-first via `/tdd` — RED confirmed (import/assert failures) before GREEN each time; Step-2.5 orchestrator review on every slice (1 TWEAK on 9.8, 1 TWEAK on 9.10, 1 ADD on 9.9, 1 ADD on 9.7b, APPROVED first-pass on 9.12). No implementation-before-test. Two GREEN-phase fixes were test-quality/type fixes, not violations: a too-loose `/locked/i`↔"Blocked" substring collision (9.10 test tightened) and the `exactOptionalPropertyTypes` clear-via-patch typing (9.9). Full suite: 85 → **147** green across the session.

## Reachability (Step 7.5, all satisfied)
- **9.8** reconciliation surfaces — `/weekly-commit` → WeeklyPlanView → PlanLifecycleBar (E9/E10/E11) + CommitmentList (CarryForwardButton E12 / ReconciliationOutcomeForm E6). ✓
- **9.9** command center — `/manager/command-center` (manager-gated) → CommandCenterPage → CommandCenter; mark-reviewed via per-row expand → ManagerRowReview → MarkReviewedAction (E16). ✓
- **9.10** heatmap — `/manager/heatmap` (manager-gated) → HeatmapPage → HeatmapGrid; drilldown via cell-select → HeatmapCellDrilldown (E15). ✓
- **9.7b** edit/delete — `/weekly-commit` → CommitmentList per-DRAFT-row Edit → CommitmentForm edit mode; Delete → DeleteCommitmentButton (E7). ✓
- **9.12** sync — `/weekly-commit` → WeeklyPlanView sync panel → SyncStatusBadge + SyncRetryAction (E23). ✓
- **No tested-but-unwired gaps introduced this session.** Pre-existing unwired surfaces remain open follow-ups (below).

## Open follow-ups

### Step-9 categorized items (orchestrator routes hot at `/orchestrate-end` — surfaced for verification)
- **New cross-doc CLAUDE.md table rows (orchestrator writes):** B.11 `ManagerCommandCenterRowDto`, B.20 `PageEnvelope<T>`, B.7 `MarkReviewedRequest` (9.9); B.1 `RiskBadge` + B.12 ×4 (9.10); B.10 `OutlookSyncRecordDto` + B.1 `SyncStatus`/`EventKind`/`SyncRelatedType` (9.12). **All verbatim B-mirrors — Appendix-B CONTENT change: NONE.**
- **Architecture-doc notes:** "B.11 rows carry no `reviewId`/`allowedActions`; mark-reviewed driven from `managerReview` (B.7) via lazy E4 on expand" (9.9 §5/§9); "E15 single page/size paginates all SO groups → client pager spans max-totalPages" (9.10 §5/§14).
- **LESSONS candidates (orch decides §-entry vs. fold):** lifecycle-mutation test harness (E8/E9/E10/E12); `PageEnvelope<T>`/Pageable + skip-until-selected drilldown; **`exactOptionalPropertyTypes` clear-via-patch gotcha** (recurring TS trap); `cells[]`→matrix pivot; non-blocking two-step destructive-confirm; `SYNC_STATUS_TAXONOMY` as the 4th §7 taxonomy.
- **Task-text corrections (orch-territory):** 9.8 line-991 `CARRIED_FORWARD`-in-form wording + E9/E10→`plansApi` placement; 9.9 + 9.10 "Heatmap tag" → `manager`; tick 9.8/9.9/9.10/9.7b/9.12.
- **Resolved carry-forward items:** 9.4 `CommandCenterPage` + `HeatmapPage` placeholders → real views; 9.7 "inline CommitmentList carry-forward handler" → wired at 9.8.

### Future TODO — belongs to a phase
- **9.11a (disputes)** — blocked on the Option-A B.6 contract edit (backend); **9.11b (comments)** — unblocked (E20 read path), next.
- **Manager review-block sync visibility** — needs a new (non-plan-keyed) read path.
- **`RcdoBrowser` consumer surface** (built 9.5, unwired); **`PlanHistoryPage`** real view (9.4 placeholder); **9.13** host-store-before-mount contract (OQ-004); **ST.7** a11y/design-review; **`weekStart` util extraction** on 3rd recurrence.

## Cross-doc invariant audit
**Clean — no drift.** Every `dtos.ts` addition this session was a **verbatim mirror** of an existing `ARCHITECTURE.md` Appendix-B section (B.7/B.10/B.11/B.12/B.20 + B.1 enums); no field added/removed/renamed on any existing model, and no Appendix-B CONTENT change. The new CLAUDE.md cross-doc *table rows* (tracking the new mirrors) are orchestrator-territory and were flagged at each slice's Step 9.
