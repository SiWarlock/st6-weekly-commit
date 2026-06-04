# Session 014 — Frontend: Cadence Drawer/manager surfaces (ST.6c→ST.7e) + alignment disputes end-to-end (9.11a + 9.14)

- **Date:** 2026-06-03
- **Track / area:** `st6-main` · `apps/wc-web/` (frontend implementer)
- **Phase:** Phase ST (ST.6c–ST.7e — Cadence styling-spine close-out) + Phase 9 (9.11a + 9.14 — alignment-disputes UI)
- **Predecessor:** [`011-2026-06-03-frontend-st-styling-spine.md`](011-2026-06-03-frontend-st-styling-spine.md) (ST.4→ST.6b)
- **Successor:** _(none yet — frontend track paused scope-complete; this impl retired after this doc. A fresh impl spins up on-demand for user-return real-browser QA / Phase-13 demo prep / fixes.)_

## Why this session existed

Fresh successor impl (predecessor cycled at 72% WARN before ST.6c). The Cadence styling spine needed its last manager-surface slice + the ST.7 a11y/QA close-out, and the disputes feature — blocked since Phase 9 on the backend's B.6 Option-A contract — unblocked mid-session as the backend landed 5.3b/5.4/5.5/5.5b. This session closed Phase ST and built the alignment-disputes frontend end-to-end (IC + manager).

## What was built

Eight `/tdd` slices, each RED→Step-2.5(APPROVED)→GREEN→commit. Full Vitest grew **196 → 233** green.

### Slice commits (impl-side; the `docs(wc-web)` commits are the orchestrator's hot-routing)

| Slice | Commit | What landed |
|---|---|---|
| ST.6c | `046b5f1` | Manager review + drilldown surfaces swapped from inline expand → themed Flowbite right-slide Drawers |
| ST.7a | `692e666` | Standalone-only MSW v2 mock-data layer (contract-typed, persona-spanning fixtures) — populates the backend-less app for QA/demo; tree-shaken from the MF remote (REQ-I-008) |
| ST.7c | `e9c9b01` | QA visual fixes — drop redundant in-bar StatusBadge, ConfidenceMeter LOW legibility (empty-segment ring), stepper width |
| ST.7d | `632d11d` | Persona-switch RTK cache reset (`resetApiState`) — fixes the stale-identity-data correctness bug |
| ST.7d | `794133a` | Sync flowbite-react's independent theme-mode to our `[data-theme]` single source (#6) |
| ST.7e | `31acd0f` | Manager Drawers render as right-slide full-height ~640px overlay + scrim (root cause: flowbite default position classes live in unscanned `.mjs`/`.cjs`) |
| 9.11a | `743bdf9` | Alignment-disputes UI — B.6 mirror + `disputesApi` + `DisputePanel` (lifecycle stepper + display + gated open/respond/resolve) + ST.4 dispute accent |
| 9.14 | `06910ba` | Manager plan-detail dispute surface — `ManagerRowReview` renders `CommitmentList`→`DisputePanel`; manager OPEN/RESOLVE controls light up via E4 `allowedActions` |

### Files created

- `src/standalone/mocks/{browser,handlers,fixtures}.ts` + `public/mockServiceWorker.js` (ST.7a) — the MSW layer.
- `src/standalone/FlowbiteThemeSync.tsx` (ST.7d) — drives flowbite's theme-mode from our `ThemeProvider`.
- `src/features/dispute/{disputesApi,DisputePanel,DisputeRespondForm,DisputeResolveAction}.tsx` (+ tests) (9.11a) — the disputes feature.

### Files modified (highlights)

- `src/app/flowbiteTheme.ts` — Drawer theme: surface skin (ST.6c) then the full `position.right` + `backdrop` slot (ST.7e).
- `src/app/theme/themeBridge.test.ts`, `tailwind.config.ts`, `src/styles/theme.css` — `--drawer-w`/`--overlay-scrim` tokens (ST.7e).
- `src/features/manager/{CommandCenter,HeatmapGrid,HeatmapCellDrilldown}.tsx` — Drawers (ST.6c); CommandCenter `ManagerRowReview` renders CommitmentList (9.14).
- `src/features/plan/PlanLifecycleBar.tsx`, `src/features/commitment/ConfidenceMeter.tsx` — ST.7c visual fixes.
- `src/standalone/{main,DemoIdentityProvider,StandaloneShell,demoIdentity}.tsx` — MSW bootstrap (ST.7a), persona-refetch + theme-sync mount (ST.7d), 4-IC+manager persona expansion (ST.7a).
- `src/shared/lib/dtos.ts` — B.6 mirror (drop `hasUnresolvedDispute`, add `dispute?` + 6 dispute contracts) (9.11a).
- `src/shared/lib/statusTaxonomy.ts` — `DISPUTE_STATUS_TAXONOMY`/`FLAG_TYPE_LABEL` (9.11a).
- `src/features/commitment/CommitmentList.tsx` — dispute card-accent + `DisputePanel` mount (9.11a).
- 13-file `hasUnresolvedDispute` ripple (9.11a) — dropped the never-implemented boolean across test/fixture/handler files.

## Decisions made

- **ST.6c — conditional inner-mount inside the always-rendered Drawer.** flowbite-react 0.10.2's `Drawer` always renders its children (open = off-screen translate), so the inner data-component mounts only while open → preserves the lazy/skip-until-open fetch + makes open/close deterministically assertable.
- **ST.7a — MSW is standalone-only, boundary-proven.** Dynamic-imported in `main.tsx` (dev/`VITE_USE_MOCKS`-gated); the REQ-I-008 proof extended with a positive control so the absence-from-remote scan isn't vacuous. Fixtures contract-typed against `dtos.ts` (TS-compile-enforced).
- **ST.7d — `resetApiState()` on identity switch (§11/§10 corollary).** Argless identity-scoped queries (`/api/me`, `/api/plans/current`) don't re-key on a header change → must reset the cache on persona change (skip first mount). Banked as wc-web LESSONS (orch).
- **ST.7e — own the full Drawer theme slot, don't widen the Tailwind content glob.** Root cause: flowbite's default position/backdrop classes live in `.mjs`/`.cjs` that `*.{js,jsx,ts,tsx}` never scans → inert. Fixed token-native in our scanned `flowbiteTheme.ts` (widening the glob would emit all flowbite defaults → CSS bloat, REQ-NF-005).
- **9.11a/9.14 — dormant-until-emitted server-authoritative control + the §11 dividend.** Every dispute control gates on `allowedActions` via `can()`; they render dark until the backend (5.5b) emits the affordance, then auto-activate with zero frontend change. The SAME `allowedActions`-gated `CommitmentList` serves both IC and manager — per-actor `allowedActions` make it role-correct with zero client-side role branching. `DisputePanel` is `{commitment}`-portable (sources planId from `weeklyPlanId`) → the 9.14 manager mount was a one-liner.
- **Accent precedence:** dispute (failure) wins over unplanned (accent) in `cardAccent`.

## Decisions explicitly NOT made (deferred)

- **Real-browser *visual/behavioral* QA across personas** — deferred to user-return. Both available tools can't drive a React controlled `<select>` (MCP extension was disconnected; headless `/browse` can't fire `onChange`); the orchestrator runs the connected-browser QA via gstack `/connect-chrome` (ST.7b/7e re-checks passed; the 9.14 both-persona QA is the orch's post-commit pass). Owned by the orch, not this impl.
- **Interactive MSW mutation round-trip (mutable in-memory `db`)** — Phase-13 demo prep. This session ships static-coherent mutation handlers + per-persona dispute affordances (controls render + fire the correct E17/E18/E19); the open-persists→resolve-clears loop is deferred.
- **#5 ~1s refetch loop (ST.7d)** — no source cause found (no polling/retry); diagnosed as #4-race/headless-artifact; runtime-confirm rides the orch's re-QA.

## TDD compliance

**Clean — no violations.** Every deterministic slice wrote the failing test first, paused at Step 2.5 for orchestrator `APPROVED.`, then GREEN. ST.7d #4 (MSW await-ordering) was **verified already-correct from ST.7a — no code change, no fabricated test** (correct: don't write a failing test for already-correct behavior). ST.7e's computed-position symptom is the documented jsdom gap → pinned at the config-shape level (not a faked computed-position test), visual confirmed by the orch's connected-browser re-check.

## Reachability (Step 7.5 per slice)

- ST.6c/7e Drawers — `/manager/command-center` + `/manager/heatmap` (pre-wired triggers).
- ST.7a MSW — standalone entry `main.tsx` (dev-gated); boundary-proven absent from the remote.
- ST.7c — `/weekly-commit` (PlanLifecycleBar/ConfidenceMeter under WeeklyPlanView).
- ST.7d — persona-refetch in DemoIdentityProvider (StandaloneShell); FlowbiteThemeSync inside `<Flowbite>`.
- 9.11a — `/weekly-commit` → CommitmentList → DisputePanel (IC display/respond + accent live; controls dormant until 5.5b, now active).
- 9.14 — `/manager/command-center` → review Drawer → ManagerRowReview → CommitmentList → DisputePanel (manager open/resolve).

No tested-but-unwired gaps. One **Future-TODO (belongs to a phase):** a manager plan-detail surface beyond the command-center Drawer is NOT needed (9.14 covered it via the Drawer); the heatmap drilldown is deliberately NOT wired for disputes (backend emits affordances on E3/E4 only, not E15 → would be permanently dark).

## Open follow-ups (Step-9 categorized — orchestrator routes via `/orchestrate-end`)

All items below were flagged hot at each slice's Step 9; the orchestrator hot-routed them during the session (LESSONS §14–§20, cross-doc rows, carry-forwards). Surfaced here for `/orchestrate-end` verification.

- **Convention candidates → wc-web LESSONS (orch-banked):** §14 expand-in-place→Drawer (ST.6c) · §15 standalone MSW layer (ST.7a) · §16 resetApiState-on-identity-switch (ST.7d) · §17 flowbite theme-mode single-source (ST.7d) · §18 flowbite `.mjs` content-glob gap / jsdom-can't-see-position (ST.7e) · §19 dormant-until-emitted server-authoritative control (9.11a) · §20 one `allowedActions`-gated component serves all actor roles (9.14). _(Numbers per the orch's routing.)_
- **Cross-doc invariant change (orch-written, `1742a69`):** `WeeklyCommitmentDto` (drop `hasUnresolvedDispute` / add `dispute?`) + `AlignmentDisputeDto`/`DisputeStatus`/`FlagType`/3 request DTOs → `apps/wc-web/CLAUDE.md` rows. ARCHITECTURE B.6/B.8 is backend-owned (authored at 5.3b). **No drift.**
- **Future TODO — Phase-13:** interactive MSW mutable-`db` (open→respond→resolve round-trip + theatrical demo loop). Standing carry-forward.
- **Future TODO — out of scope / user-return:** the full connected-browser both-persona disputes QA (orch tooling).
- **`yarn.lock` corrective diff (ST.7a):** the `msw` add materialized the previously-deferred wc-e2e devDeps (test-track 11.9 lock-materialization) — net-corrective, already documented in `692e666`.

## How to use what was built

- **Run the populated app:** `yarn nx dev wc-web` (mocks on by default in dev, or `VITE_USE_MOCKS=true`). Switch personas via the PersonaSwitcher: Ivy (DRAFT) → Ravi (LOCKED) → Lena (RECONCILING, has the seeded OPEN dispute) → Tom (RECONCILED) → Morgan (manager).
- **See disputes:** as **Lena** on `/weekly-commit`, the SOC2 commitment shows the dispute panel + Respond form; as **Morgan** on `/manager/command-center` → Review a report → the Drawer shows commitments with Open-dispute (undisputed) / Resolve (Lena's disputed) controls.
- Single-file test runs need `VITEST=true` (jsdom env); full gate is `yarn nx lint && typecheck && test wc-web`.
