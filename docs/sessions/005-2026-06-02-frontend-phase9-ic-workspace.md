# Session 005 — Frontend Phase 9: lazy route tree → me/rcdo → plans/commitments → IC workspace + lock

- **Date:** 2026-06-02
- **Phase:** 9 (frontend MFE — IC weekly-planning surface)
- **Area:** `apps/wc-web/`
- **Role:** implementer (`st6-main-wc-web-implementer`)
- **Predecessor session:** [002 — frontend styling foundation + Phase 9 spine](002-2026-06-02-frontend-styling-foundation-and-phase9-spine.md) (0.6/ST.1/ST.2 + 9.1/9.2/9.3)
- **Successor session:** [007 — frontend Phase 9: reconciliation, manager surfaces, edit/delete, sync](007-2026-06-03-frontend-phase9-reconciliation-manager-sync.md) (9.8/9.9/9.10/9.7b/9.12)
- **Slices landed:** 9.4, 9.5, 9.6, 9.7 — all green, `apps/wc-web/`-only, no push (orchestrator round-commit pending).

---

## Why this session existed

The Phase-9 spine (9.1 RTK Query base, 9.2 view-states/badges, 9.3 MFE boundary) had landed (session 002). This session built the **IC weekly-planning workspace** on top of it: the lazy route tree, the read-foundation + mutation RTK Query slices, and the IC workspace UI (plan view, commitment form, lifecycle/lock bar) — taking the frontend from "spine exists" to "an IC can view their plan, add commitments, and lock the week" (modulo the not-yet-live backend endpoints).

---

## What was built

### Slice 9.4 — lazy route tree + demo-header boundary split (commits `e9ee230`, `8fa030c`)
**Files created:** `routes/AppRoutes.tsx` (5 React.lazy code-split routes under one Suspense+RouteErrorBoundary), `routes/RouteErrorBoundary.tsx` (failed-lazy-import → leak-free ErrorState), `routes/isManager.ts` (gating seam, default-false context), `routes/pages/{WeeklyCommit,PlanHistory,CommandCenter,Heatmap}Page.tsx` (placeholders) + tests.
**Files modified:** `app/authAccessor.ts` (replaced the demo-id provider with a `DemoAuthHeaderApplier` seam), `app/baseApi.ts` (demo branch → `applyDemoAuthHeader`), `standalone/DemoIdentityProvider.tsx` (owns the `X-Demo-Employee-Id` literal), `remote/WeeklyCommitApp.tsx` (renders `<AppRoutes/>`), `remote/boundary.test.ts` (positive control).
**Why:** REQ-NF-005 code-split routes; REQ-I-008 — split the demo-header literal out of the now-remote-reachable `baseApi` (the applier-seam pattern, LESSONS §8).

### Slice 9.5 — me/rcdo read slices + real gating + RCDO UI (commits `03c5821`, `3b662c4`, `87cade1`)
**Files created:** `features/me/{meApi.ts,useCurrentUser.ts}`, `features/rcdo/{rcdoApi.ts,RcdoBrowser.tsx,SupportingOutcomePicker.tsx}` + tests, `routes/isManager.test.tsx`.
**Files modified:** `app/baseApi.ts` (`isJsonContentType` → parse `application/problem+json`), `routes/isManager.ts` (reads real `MeDto.isManager`, fail-closed), `routes/AppRoutes.tsx` (persona-aware `/` redirect, dropped the interim App landing), boundary + routing tests. **Deleted:** `App.tsx`/`App.test.tsx` (orphaned once `/` became a redirect).
**Why:** the read foundation + the real route gating; `baseApi` entered the eager remote closure → REQ-I-008 re-verified (static guard + auth0 build-grep PASS).

### Slice 9.6 — plans/commitments slices + cache-invalidation (commit `d0ef176`)
**Files created:** `shared/lib/dtos.ts` (the typed API contract: B.1 enums + B.5/B.6/B.7 DTOs + E5/E6/E11 request types), `features/plan/{plansApi.ts,plansApi.test.ts}`, `features/commitment/{commitmentsApi.ts,commitmentsApi.test.ts}`.
**Why:** the first mutation slice — per-id `Plans` tags (`{type:'plans',id}` + `'CURRENT'`); mutations invalidate on success only (`error ? [] : tags`); **no optimistic updates** (gated-refetch proof); RFC-7807 409s surface `safeMessage`.

### Slice 9.7 — IC workspace + lifecycle/lock (commits `58d8c5e`, `029ff53`, `5ff60d2`)
**Files created:** `features/plan/{WeeklyPlanView,PlanLifecycleBar,LockButton}.tsx`, `features/commitment/{CommitmentList,CommitmentForm,ChessLayerFields}.tsx`, `shared/lib/allowedActions.ts` (`can()`) + tests.
**Files modified:** `features/plan/plansApi.ts` (+`lockPlan` E8), `app/tags.ts` (extracted shared `planTags`), `features/commitment/commitmentsApi.ts` (use shared `planTags`), `routes/pages/WeeklyCommitPage.tsx` (renders `<WeeklyPlanView/>` — resolves the 9.4 placeholder carry-forward), routing tests (ripple: mock the plan query, assert `/weekly commitments/`).
**Why:** the IC workspace — plan view (view-states), commitment create form (chess + SO picker), lifecycle/lock bar. Every control gates ONLY on server `allowedActions[]` via `can()`; lock invalidates→refetch into LOCKED (no optimistic flip); 409 `UNLINKED_PLANNED_COMMITMENT`/`EMPTY_PLAN_LOCK` + `fieldErrors[]` render verbatim (rule #1, server-enforced).

---

## Decisions made

- **Applier-seam pattern (9.4):** moved the `X-Demo-Employee-Id` literal into a standalone-only injected applier (`DemoAuthHeaderApplier`), keeping `baseApi`/`authAccessor` demo-literal-free as they entered the remote graph. (LESSONS §8.)
- **`isJsonContentType` for problem+json (9.5):** `fetchBaseQuery`'s default predicate doesn't match `application/problem+json`, so RFC-7807 bodies were arriving as text → generic message. Extended the predicate so `transformErrorResponse`→`parseProblemDetail` surfaces the safe message.
- **Read-only-never-invalidated tags (9.5/9.6):** `me`/`rcdo` are fetched once, never invalidated (REQ-D-003). (LESSONS §9.)
- **Per-id Plans tag + invalidate-on-success-only (9.6):** per-id precision + the `error ? [] : tags` guard (RTK Query otherwise invalidates on error too); `.select()` exposes raw `.status`, not the hook-derived `.isFetching`. (LESSONS §10.)
- **No `heatmap` tag (9.6):** `tags.ts` registers 9 lowercase tags (no `heatmap`); mutations invalidate `plans`+general `manager` (§9: command-center + heatmap projections co-change).
- **Server-authoritative control gating (9.7):** never re-derive lock eligibility/authz/lifecycle legality client-side — gate ONLY via `can(action, allowedActions[])`, surface server `409`/`safeMessage`/`fieldErrors[]` verbatim, no optimistic state. (LESSONS §11.)
- **`planTags` → `app/tags.ts` (9.7):** `lockPlan` and the commitment mutations share the same invalidation set; centralized in the neutral tag home (no duplication, no cross-feature import).
- **`/` reuses `App` then redirect (9.4→9.5):** 9.4 used `App` as the interim `/` landing; 9.5 replaced it with a persona-aware redirect and deleted the orphaned `App`.
- **Collapsible CommitmentForm (9.7):** the form (+ its RCDO fetch) mounts only when the IC opens it — avoids cascading the store-ripple into every test that renders `WeeklyPlanView`.
- **9.7 commit split (whole-file cumulative):** `WeeklyPlanView` composes the form + lifecycle bar, so it + route + ripple land in C3 (display-primitives → form → wiring+lock); approved over an incremental re-edit.

## Decisions explicitly NOT made (deferred)

- **IC commitment edit (E6) / delete (E7) UI** — needed for a usable DRAFT workflow but not an `AllowedAction` (would gate on `plan.state==='DRAFT'`, server-enforced via `LOCKED_BASELINE_EDIT`); no current phase home. Orchestrator is escalating where it lands → **new Phase-9 task**.
- **Inline carry-forward / comment row handlers** — `CommitmentList`'s `can()`-gated controls are built + tested (with a handler), but production passes no handlers yet → wired by **9.8** (carry-forward) / **9.11** (comment/dispute).
- **`RcdoBrowser` consumer** — built + tested (9.5) but not wired to any route/surface yet (the controlled `SupportingOutcomePicker` IS wired via the 9.7 form). Needs an RCDO-explorer surface.
- **Host-store provision contract (9.13)** — the remote now hard-requires a host-provided Redux `<Provider>` for the eager gating `getMe`; the §7/9.13 host-integration contract must document "host provides store + accessor before mount" (OQ-004).
- **Phase-11 build-grep CI guard** — promote the fail-closed auth0 build-grep to CI; positive control must key on an auth0-surviving demo string (`demo-token`/persona seed), not `X-Demo-Employee-Id` (DCE'd in auth0). (LESSONS §6 refinement.)
- **ST.7 visual-fidelity pass** — ST.5 structure landed via tokens+badges; exact pixel/contrast/a11y fidelity is the ST.7 design-review (visual non-deterministic, exempt from strict TDD).

---

## TDD compliance

**Clean — no violations.** Every slice followed RED → Step-2.5 orchestrator review → GREEN. All non-trivial code had a failing test first; the few in-cycle test corrections (e.g. the `CommitmentList` fixture-title/badge-label collision, `.isFetching`→`.status`, the RcdoBrowser guard-order) were RED/GREEN fixes, not post-hoc backfills. Visual fidelity (ST.5/ST.7) is non-deterministic and correctly exempt — deterministic tests pin behavior/actions/validation.

## Cross-doc invariant audit

**No drift.** `dtos.ts` (9.6) defines the B.1 enums + B.3/B.4/B.5/B.6/B.7 DTOs + E5/E6/E11 request types as **verbatim** mirrors of Appendix B — no field added/removed/renamed in any slice. `lockPlan` (9.7) returns the already-registered `WeeklyPlanDto`. The orchestrator registered the cross-doc table rows hot (flagged at each Step 9). **Cross-doc invariant changes this session: NONE.** (Note: orchestrator commit `cc0bb6b` corrected `ARCHITECTURE.md`'s `UNLINKED_PLANNED_COMMITMENT` lock rejection 422→409, aligning the contract with the 9.7 `LockButton` 409-surfacing test.)

## Reachability

- **9.4 route tree** — reachable from `WeeklyCommitApp` (federation `./WeeklyCommitApp` expose) → `<AppRoutes/>`; standalone via `main → StandaloneShell → WeeklyCommitApp`. ✓
- **9.5 meApi/useCurrentUser** — eager via the gating chain (`AppRoutes`→`useIsManager`→`useCurrentUser`→`meApi`→`baseApi`); confirmed in the auth0 build chunk + boundary positive control. ✓
- **9.5 SupportingOutcomePicker** — now wired (9.7 `CommitmentForm`). ✓
- **9.6 plansApi/commitmentsApi** — now wired (9.7 `WeeklyPlanView`/`CommitmentForm`/`LockButton`). ✓
- **9.7 WeeklyPlanView** — wired into `/weekly-commit` (`WeeklyCommitPage`); `CommitmentList`/`CommitmentForm`/`PlanLifecycleBar`/`LockButton` mount inside; `can()` via `LockButton`+`CommitmentList`; `lockPlan` via `LockButton`. ✓
- **Tested-but-unwired gaps (open follow-ups):** `RcdoBrowser` (no consumer surface yet); `CommitmentList` inline carry-forward/comment controls (handlers deferred 9.8/9.11). Both are "Future TODO — belongs to a phase".

## Open follow-ups (Step-9 categorized list — orchestrator verifies routed at /orchestrate-end)

| Item | Category | Destination |
|---|---|---|
| Applier-seam pattern | Convention candidate | wc-web LESSONS §8 (done) |
| auth0 build-grep positive-control-string refinement | Convention/LESSONS §6 refinement | wc-web LESSONS §6 (done) + Phase-11 CI guard |
| Read-only-never-invalidated tags (me/rcdo) | Convention candidate | wc-web LESSONS §9 (done) |
| Cache-invalidation harness + RTK gotchas (error-guard, `.select().status`) | Convention candidate | wc-web LESSONS §10 (done) |
| Server-authoritative control gating (`can()`, 409-verbatim, no-optimistic) | Convention candidate | wc-web LESSONS §11 (done) |
| `dtos.ts` Appendix-B mirrors (B.3/B.4/B.5/B.6/B.7 + request DTOs) | Cross-doc rows | `apps/wc-web/CLAUDE.md` cross-doc table (orchestrator) |
| §7/9.13 host requires a Redux `<Provider>`; `lockPlan` placement; `planTags` home; alignmentStatus-read-only | Architecture note | `ARCHITECTURE.md §7`/9.13 (orchestrator) |
| IC commitment edit (E6) / delete (E7) UI | Future TODO — belongs to a phase | new Phase-9 task (orchestrator escalating home) |
| Inline carry-forward / comment row handlers | Future TODO — belongs to a phase | 9.8 / 9.11 |
| `RcdoBrowser` consumer surface | Future TODO — belongs to a phase | RCDO-explorer / later 9.x |
| Host-store-before-mount contract | Future TODO — belongs to a phase | 9.13 (OQ-004) |

**Cross-doc invariant change:** NONE.
