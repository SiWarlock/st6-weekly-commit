# ST6 Weekly Commit Module `apps/wc-web/` — Build Guide

> **You're in `apps/wc-web/`.** This file plus root `CLAUDE.md` both load. The root file covers global project conventions + shared comm rules (track-prefix, escalation taxonomy, messaging budget); this file owns code-area conventions for frontend.
>
> The `apps/wc-e2e/` Cypress + Cucumber/Gherkin acceptance suite rides with this area — it shares the frontend toolchain (Yarn Workspaces + Nx) and is driven via `yarn nx e2e wc-e2e`.

## Launch protocol

| Working on... | cwd | Loads |
|---|---|---|
| Planning / docs / commits | repo root (`ST6/`) | root `CLAUDE.md` only |
| frontend code | `apps/wc-web/` | this `CLAUDE.md` + root |

<!-- For a multi-area project, add a row per additional code area. -->

If you find yourself fighting the wrong conventions, check your cwd.

## Session start/end protocol

**At session start:**
1. Read `MVP_TASKS.md` (repo root) → "Currently in progress" section.
2. Confirm with the user what feature this session is targeting.
3. Read the relevant section of `ARCHITECTURE.md` from the lookup table below.

**At session end** (only when the user explicitly says we're done):

1. **Implementer runs `/session-end`.** Implementer writes ONLY:
   - `apps/wc-web/` code files (the slice's implementation)
   - test files (the slice's tests)
   - dependency manifest / lockfile (deps the slice adds)
   - `docs/sessions/<NNN>-<date>-<topic>.md` (session doc, created at `/session-end` Step 5)

   **Implementer must NOT touch (all orchestrator territory):**
   - `MVP_TASKS.md`
   - `apps/wc-web/LESSONS.md`
   - `apps/wc-web/CLAUDE.md` (entire file — both the Cross-doc invariants table AND the Lessons logged index)
   - `ARCHITECTURE.md`
   - `docs/orchestrator-briefing.md` / `docs/tdd-brief-template.md` / `docs/briefs/` / `docs/runbooks/`
   - other top-level deliverable / design docs
   - `.gitignore` and root-level dotfiles (unless adding a new artifact to ignore, flagged at Step 9)

   At the slice's Step 10 commit, **explicit `git add <path>` for each slice file**; **never `git add -A`** or `git add .`; **never stage an orchestrator-territory file**. If the slice surfaces a change to any orchestrator-territory file (new model needing a cross-doc table row, a lesson candidate, an architecture note), the implementer **flags it at Step 9** per the routing matrix in `docs/orchestrator-briefing.md`. The orchestrator writes the change hot during the same session — working-tree state stays aligned within the round even though commits stagger.

2. **Orchestrator runs `/orchestrate-end`** for round close-out + Carry-forward triage + round terminal commit + push.

## Lookup table — where to find canonical info

Don't paste these sections into the prompt. Grep the file:section, read only what you need. `/check-arch <topic>` dispatches off this table.

| Topic | File (relative to repo root) | Section |
|---|---|---|
| <subsystem A> | `ARCHITECTURE.md` | §X |
| <subsystem B> | `ARCHITECTURE.md` | §Y |
| Lessons logged (full prose) | `apps/wc-web/LESSONS.md` | by lesson # |

<!-- Starts near-empty. Add a row whenever a topic is looked up twice. -->

**Code intelligence & docs (when available):** prefer a code-intelligence MCP (e.g. CodeGraph) for code navigation / callers / traces over `grep`+read loops, and a docs MCP (e.g. Context7) for up-to-date library/API docs — see root `CLAUDE.md` "Code intelligence & docs." No-op if not installed.

## Stack

<!-- ▼ EXAMPLE BLOCK [id=area-stack]: stack quick-reference for implementer sessions. Canonical stack lives in root CLAUDE.md + ARCHITECTURE.md; this is the cheat sheet. ▼ -->

- **Runtime:** Node 20 LTS · Yarn Workspaces + Nx
- **Framework:** React 18 + Vite 5 (Module Federation remote) · RTK Query · Flowbite React + Tailwind
- **Validation:** TypeScript types (RTK Query slice types are the API contract)
- **Lint / types / tests:** ESLint 9 + Prettier 3.3 / tsc --noEmit (TS strict) / Vitest (unit) + Cypress/Cucumber (E2E in `apps/wc-e2e/`)

<!-- ▲ END EXAMPLE BLOCK [id=area-stack] ▲ -->

## Standard commands

```bash
# Install deps (run once; re-run when the manifest changes)
yarn install

# Run the dev server (if applicable)
yarn nx dev wc-web

# Tests
yarn nx test wc-web

# Quality
yarn nx lint wc-web
yarn prettier --check .
yarn nx typecheck wc-web

# Preflight (use before saying "done" with a feature)
yarn nx lint wc-web && yarn nx typecheck wc-web && yarn nx test wc-web
```

## TDD protocol

**Write the failing test first.** Applies to deterministic code — see the TDD posture in root `CLAUDE.md` for what is test-first vs. exempt.

**Commit per slice when practical.** Never bundle a safety-critical slice with anything else.

## Forbidden patterns

<!-- ▼ EXAMPLE BLOCK [id=forbidden-patterns]: forbidden patterns — 3-5 narrow, enforceable, domain-specific rules. Shape: "Don't <pattern X> because <reason / past incident>; use <alternative Y>." Test-pin them where possible. Starts small; accretes as lessons surface. ▼ -->

Do not:

1. **Write a component/slice without a failing Vitest test first** (deterministic logic; visual-only is exempt).
2. **Use Redux Saga or Thunk** — RTK Query only; mutations invalidate cache tags.
3. **Use CSS Modules or styled-components** — Tailwind utility classes + Flowbite React only. **One narrow exception (ST.1/ST.2, brief 006; see LESSONS #3):** a single CSS custom-property **token** stylesheet — `src/styles/theme.css` (token vars + `@tailwind` directives + a var-driven `body{}` base rule + the `@media (prefers-reduced-motion: reduce)` rule; read by `tailwind.config` + the Flowbite theme for the `[data-theme]` dark/light flip) — is permitted. **`.wc-*` component CSS, CSS Modules, styled-components, and any second stylesheet remain forbidden.**
4. **Use `dangerouslySetInnerHTML`** — render user text via React default escaping (stored-XSS surface).
5. **Apply optimistic updates** — mutations refetch/invalidate; render explicit loading/empty/error/success states; surface the API `safeMessage` as Cypress-assertable error text.
6. **Leak demo/persona logic into the exposed remote** — `PersonaSwitcher` + demo-header branch live only in `src/standalone/` and are tree-shaken out of the Module Federation build.

<!-- ▲ END EXAMPLE BLOCK [id=forbidden-patterns] ▲ -->

## Cross-doc invariants — schema/docs mirroring

Several typed models in this codebase are **contracts** mirrored in `ARCHITECTURE.md` and indexed in the table below. The architecture doc is the canonical contract; the model is the executable enforcement. Drift produces silent disagreement.

**Authoring discipline (orchestrator owns this table).** When the implementer adds, removes, or renames a field on one of these models, the implementer **flags it at Step 9 categorized as `Cross-doc invariant change`** per the routing matrix in `docs/orchestrator-briefing.md`. The implementer does NOT edit `apps/wc-web/CLAUDE.md` or `ARCHITECTURE.md` directly — the orchestrator writes the table row + the architecture edit hot during the same session. Working-tree state aligns within the round; commits stagger (implementer's slice commit lands code+tests; orchestrator's round commit lands the doc rows).

| Model | `ARCHITECTURE.md` section | Notes |
|---|---|---|
| `MeDto` (`shared/lib/dtos.ts`) | Appendix B.3 | identity: `employeeId`/`email`/`displayName`/`role`/`persona`/`isManager`/`timezone?` (9.5) |
| `RcdoTreeDto` + `RallyCryNode`/`DefiningObjectiveNode`/`SupportingOutcomeNode` (`shared/lib/dtos.ts`) | Appendix B.4 | `{ rallyCries: RallyCryNode[] }` object wrapper; read-only RC→DO→SO hierarchy (9.5) |
| `WeeklyPlanDto` (`shared/lib/dtos.ts`) | Appendix B.5 | plan + nested `commitments[]`/`managerReview?`/`allowedActions[]`/`version` (9.6) |
| `WeeklyCommitmentDto` (`shared/lib/dtos.ts`) | Appendix B.6 | chess fields + `supportingOutcomeId?`/`reconciliationOutcome?`/`alignmentStatus`/`dispute?: AlignmentDisputeDto`/`allowedActions[]`/`version` (9.6; **B.6 Option-A `dispute?` nest replaced the dropped `hasUnresolvedDispute`** at 9.11a) |
| `ManagerReviewDto` (`shared/lib/dtos.ts`) | Appendix B.7 | nested in B.5; forms own it at 9.9 (9.6) |
| `RcdoBreadcrumbDto` (`shared/lib/dtos.ts`) | Appendix B.5 / §5 | RC→DO→SO labels for display (9.6) |
| `CreateCommitmentRequest` / `PatchCommitmentRequest` / `CreateUnplannedCommitmentRequest` (`shared/lib/dtos.ts`) | Appendix B.6 (E5/E6/E11) | request DTOs; no `version` (per Appendix B) (9.6) |
| B.1 enum unions (`PlanState`/`CommitmentKind`/`Priority`/`WorkType`/`Confidence`/`AlignmentStatus`/`ReconciliationOutcome`/`ReviewStatus`/`AllowedAction`) (`shared/lib/dtos.ts`) | Appendix B.1 | typed unions mirroring the B.1 wire vocab verbatim (9.6) |
| `ManagerCommandCenterRowDto` (`shared/lib/dtos.ts`) | Appendix B.11 | manager command-center roll-up row; mirrors `manager_plan_summary` §9 (no `reviewId`/`allowedActions`) (9.9) |
| `PageEnvelope<T>` (`shared/lib/dtos.ts`) | Appendix B.20 | generic Spring `Page<T>` envelope `{content, page, sort}`; reused by command-center / drilldown / comments (9.9) |
| `MarkReviewedRequest` (`shared/lib/dtos.ts`) | Appendix B.7 (E16) | `{ summaryNote? }`; status is server-derived, never sent (9.9) |
| B.1 enums `RiskBadge` / `SyncStatus` / `EventKind` / `SyncRelatedType` (`shared/lib/dtos.ts`) | Appendix B.1 | typed unions mirroring the B.1 wire vocab verbatim (`RiskBadge` 9.10; sync trio 9.12) |
| `HeatmapCellDto` / `HeatmapResponseDto` / `HeatmapDrilldownDto` / `DrilldownOutcomeGroup` (`shared/lib/dtos.ts`) | Appendix B.12 | manager heatmap grid + per-cell SO→commitment drilldown (drilldown commitments = `PageEnvelope<WeeklyCommitmentDto>`) (9.10) |
| `OutlookSyncRecordDto` (`shared/lib/dtos.ts`) | Appendix B.10 | IC sync record; `safeMessage` is the only user-visible failure text (rule #7); `RETRY_SYNC` iff `FAILED` (9.12) |
| `CommentDto` (`shared/lib/dtos.ts`) | Appendix B.9 | flat one-level comment; `parentCommentId: string \| null` (always null MVP), `depth: number` (always 0); body React-escaped, no `dangerouslySetInnerHTML` (REQ-S-005) (9.11b) |
| `CommentTargetType` (`shared/lib/dtos.ts`) | Appendix B.1 | `PLAN \| COMMITMENT` comment target (9.11b) |
| `CreateCommentRequest` (`shared/lib/dtos.ts`) | Appendix B.9 (E21) | `{ targetType, targetId, body }`; unseeable/nonexistent target → `404` (target-id IDOR) (9.11b) |
| `AlignmentDisputeDto` (`shared/lib/dtos.ts`) | Appendix B.8 | nested in B.6 `dispute?`; `{ id, commitmentId, managerEmployeeId, status, flagType, managerNote, icResponse?, resolvedAt?, allowedActions[], version }`; holds the **current `OPEN`/`IC_RESPONDED`** dispute only (null once `RESOLVED`); `RESPOND_DISPUTE`/`RESOLVE_DISPUTE` gate on its `allowedActions` (9.11a) |
| `DisputeStatus` / `FlagType` (`shared/lib/dtos.ts`) | Appendix B.1 | `OPEN \| IC_RESPONDED \| RESOLVED` / `NEEDS_REVISION \| MISALIGNED`; typed unions mirroring B.1 verbatim (9.11a) |
| `OpenDisputeRequest` / `RespondDisputeRequest` / `ResolveDisputeRequest` (`shared/lib/dtos.ts`) | Appendix B.8 (E17/E18/E19) | `{ flagType, managerNote }` / `{ icResponse?, newSupportingOutcomeId? }` (≥1 required) / `{ resolutionNote? }`; request DTOs, no `version` (9.11a) |

<!-- Starts empty (or with the first model if one exists). Populated as contract models land. -->

## Module organization

<!-- ▼ EXAMPLE BLOCK [id=module-layout]: module layout + layer dependency rule. Replace with the project's real directory tree and import-direction DAG. ▼ -->

```
apps/wc-web/src/
  remote/WeeklyCommitApp.tsx     # exposed Module Federation module (consumes host router; no BrowserRouter/persona)
  standalone/main.tsx            # owns BrowserRouter + store + identity provider + PersonaSwitcher (demo-only)
  app/{store,baseApi,authAccessor,tags}.ts
  routes/AppRoutes.tsx           # lazy routes
  features/<domain>/             # per-domain RTK Query api slice + components
  shared/components|lib/
```

Layer dependency direction (top depends on bottom, never reverse):

```
features/* → app/baseApi + shared/*
remote + standalone mount features
no feature imports another feature's internals (cross-feature via the api slices/store only)
```

Cross-cutting layers can be imported from anywhere. Enforce the rule mechanically with a test where possible — the test *is* the spec for the rule.

<!-- ▲ END EXAMPLE BLOCK [id=module-layout] ▲ -->

## Subagents

See `.claude/agents/README.md` for the canonical inventory + integration points.

<!-- ▼ EXAMPLE BLOCK [id=area-subagent-candidates]: area-specific subagent candidates — list candidates that would earn their keep specifically in this area (e.g. an ABI/types syncer for a frontend area, a Pyth/feed verifier for a contracts area). Build only on real friction. ▼ -->

Candidates (build only on real friction): an **RTK-Query / DTO type syncer** (keeps slice types aligned with `ARCHITECTURE.md` Appendix B); a **view-state coverage checker** (asserts each data view renders loading/empty/error/success).

<!-- ▲ END EXAMPLE BLOCK [id=area-subagent-candidates] ▲ -->

## Lessons logged from prior sessions

The full prose for each lesson lives in `apps/wc-web/LESSONS.md`. This index is the compact orientation surface.

**Lesson numbers are stable IDs** — once assigned, they don't change. New lessons get the next sequential number. `/session-end` proposes additions when it detects them; the user approves before the entry is written and a row is added here.

Lessons start at §1.

| # | Date | Topic | Rule (one-liner) |
|--:|---|---|---|
| 1 | 2026-06-02 | [JS monorepo-root toolchain pin](LESSONS.md#1) | Pin Yarn via Corepack `packageManager`, node-modules linker, commit `yarn.lock`, `yarn install --immutable` is the CI idempotency check. |
| 2 | 2026-06-02 | [Shell launcher footgun](LESSONS.md#2) | Resolve executables with `type -P`, not `command -v`, when a shell function may share the binary's name. |
| 3 | 2026-06-02 | [Multi-theme via CSS-vars + `[data-theme]`](LESSONS.md#3) | Bind design tokens into the Tailwind theme as `var(--…)` and switch themes by flipping `[data-theme]`; never author your own Tailwind `dark:` utilities; keep CSS to the one token-var stylesheet. |
| 4 | 2026-06-02 | [flowbite-react 0.10.2 theming](LESSONS.md#4) | Skin via `createTheme` + `<Flowbite theme={{ theme }}>` (not `ThemeProvider`); set `darkMode: ['selector','[data-theme="dark"]']` so Flowbite's baked-in `dark:` binds to the theme attribute, not OS media. |
| 5 | 2026-06-02 | [RTK Query base testing](LESSONS.md#5) | Test the `prepareHeaders` XOR by injecting both accessor-seam providers + asserting exactly-one-header (+ no-leak); give store integration tests an absolute `VITE_API_BASE_URL` + late-bound `fetchFn` so undici's `new Request()` doesn't throw before the mocked `fetch`. |
| 6 | 2026-06-02 | [REQ-I-008 boundary proof](LESSONS.md#6) | Prove the remote build is demo-free with BOTH a fast fail-open static import-graph assertion AND a fail-closed auth0 build-output grep over the federation-exposed chunk (+ positive control); keep demo-header source out of remote-reachable modules via a standalone-only injected seam. |
| 7 | 2026-06-02 | [Status-taxonomy single source of visual truth](LESSONS.md#7) | Port the §4.2/§4.3 enum→`{tone,icon,label,ring}` maps once into `statusTaxonomy.ts` + consume everywhere; never re-map a status inline; unknown→render nothing; `OVERDUE` is a derived overlay; every badge is glyph+text+color. |
| 8 | 2026-06-02 | [Applier-seam pattern](LESSONS.md#8) | Keep a safety-mirror literal (e.g. `X-Demo-Employee-Id`) out of shared/remote-reachable modules by injecting it from `src/standalone/` via a no-op-default applier seam (`apply*`/`set*Applier`, try/catch-degrade); the literal + its falsy-value guard live only in the standalone closure; split in its own commit + pin with a source-literal scan + a positive-controlled §6 boundary walk. |
| 9 | 2026-06-02 | [Query slices: read-only tags + §5 harness + problem+json](LESSONS.md#9) | Read-only query domains (`RCDO`/`Me`) tag-once-never-invalidate (pin the no-`*Mutation` half); reuse the §5 store harness; give `fetchBaseQuery` an `isJsonContentType` matching `application/problem+json` or RFC-7807 error bodies won't parse. |
| 10 | 2026-06-02 | [Mutation cache-invalidation](LESSONS.md#10) | Per-id `{type:'plans',id}`+`'CURRENT'` tags (`planId` arg = invalidation key); guard `invalidatesTags` with `(_r,error)=>error?[]:tags` for success-only; assert via `endpoint.select()` raw `.status` not `.isFetching`; prove no-optimistic behaviorally (gated refetch) + structurally (no `updateQueryData`). |
| 11 | 2026-06-02 | [Server-authoritative control gating](LESSONS.md#11) | Never re-derive eligibility/authz/lifecycle-legality client-side; gate controls only on server `allowedActions[]` (one `can()` helper) or server state; surface `409`/`safeMessage`/`fieldErrors[]` verbatim; lifecycle transition = invalidate→refetch-into-new-state, no optimistic flip. |
| 12 | 2026-06-03 | [exactOptionalPropertyTypes clear-via-patch](LESSONS.md#12) | A clearable optional field (params/patch shapes set to `undefined`) must be typed `field?: T \| undefined`, not `field?: T`, under `exactOptionalPropertyTypes`. |
| 13 | 2026-06-03 | [Manager read-surface conventions](LESSONS.md#13) | One generic `PageEnvelope<T>` (B.20) + omit-undefined Pageable params; tag manager reads `manager` (no `heatmap` tag); reach a row's missing action id/allowedActions via the aggregate root (lazy `getPlanById`, not a new endpoint); span a grouped-drilldown pager off `max(totalPages)`. |
| 14 | 2026-06-03 | [Expand-in-place → themed Flowbite Drawer](LESSONS.md#14) | A `flowbite-react` `Drawer` always renders its children (open = off-screen translate) — conditionally mount the inner lazy/data component on the trigger state (preserves skip-until-open + assertability); hand-roll the header (Material-icon clash); the prop is `position` not `placement`. |
| 15 | 2026-06-03 | [Standalone-only MSW mock layer](LESSONS.md#15) | Backend-less standalone via a standalone-only MSW v2 layer (contract-typed fixtures compile-enforced vs `dtos.ts`; persona-routed via `X-Demo-Employee-Id`; dynamic-imported + awaited in `main.tsx`); prove it tree-shaken from the remote with a fail-closed literal scan + a positive control; keep `*/api` globs out of JSDoc block comments. |
| 16 | 2026-06-03 | [Identity switch → resetApiState()](LESSONS.md#16) | An identity/persona switch must `dispatch(baseApi.util.resetApiState())` (skip first mount) — argless identity-scoped queries (`/api/me`, `/api/plans/current`) don't auto-invalidate on a header/identity change, so a tag-invalidate is insufficient; reset the whole cache. |
| 17 | 2026-06-03 | [Flowbite theme-mode single source](LESSONS.md#17) | `flowbite-react`'s `<Flowbite>` runs its own `useThemeMode()` (persists `flowbite-theme-mode` + toggles `.dark` independently) — drive it from our `[data-theme]` single source via a sync component inside `<Flowbite>`, else Flowbite primitives drift from the app theme. (Extends §4.) |
| 18 | 2026-06-03 | [Flowbite .mjs/.cjs content-glob gap + real-browser overlay QA](LESSONS.md#18) | A *partially*-overridden `flowbite-react` primitive silently loses its default positioning/backdrop — those classes live in `.mjs`/`.cjs` outside a `*.{js,jsx,ts,tsx}` content glob (inert). Own the full theme slot in the scanned config (don't widen the glob → CSS bloat); real-browser QA, not jsdom, catches overlay computed-position regressions. |
| 19 | 2026-06-03 | [Dormant-until-emitted server-authoritative control](LESSONS.md#19) | A control may gate on a server `allowedAction` the backend emits **empty today** — built + unit-tested (mock the action present), it renders dark in the live app until the backend ships the emission, then auto-activates with zero frontend change. Lets the frontend lead the backend WITHOUT re-deriving authz/lifecycle client-side (the §11 corollary); never gate on `status`+role as an interim. |
| 20 | 2026-06-03 | [One gated component serves all actor roles (§11 dividend)](LESSONS.md#20) | Render the SAME `allowedActions`-gated component for every actor role instead of forking per role — the server's per-actor `allowedActions` make it role-correct with zero client-side role branching; the read endpoint (E3 own vs E4 report) differentiates the actor, not the component. |
| 21 | 2026-06-04 | [Standalone MSW mutable-db pattern](LESSONS.md#21) | Take the standalone MSW mutable via a deep-cloned-from-fixtures in-memory `db` (persist + seed-once + `resetDb()` for tests) + write-through mutations (`MockDbError`→RFC-7807, backend codes mirrored) + live read-selectors behind the existing handlers; overlay only affected derived fields on read surfaces (no full rollup mid-slice); type-only `dtos` import keeps REQ-I-008 green. (Extends §15.) |
| 22 | 2026-06-04 | [MSW cold-install boot — await SW control](LESSONS.md#22) | Await SW *control* (not just `worker.start()`'s registration) before the first query via a pure `waitForServiceWorkerControl()` helper (resolve-now-if-controlling / on-`controllerchange` / on-timeout-backstop; jsdom-unit-testable); the demo must use a relative base URL (an absolute base bypasses the same-origin SW). (Adjacent §18.) |
| 23 | 2026-06-04 | [Standalone demo app-shell mirrors the host chrome](LESSONS.md#23) | When the host owns the chrome (§7 MFE), don't add chrome to the exposed remote — simulate it in a standalone-only app-shell (`src/standalone/shell/*`) around the remote, driving the EXISTING routes via the standalone-owned router (tree-shaken automatically; `boundary.test.ts` covers it). Keep role-landing server-authoritative (gate nav on `useIsManager`; reroute `'/'`→`RootRedirect`, no role read in the switcher) + token-native (no hex). (§7/REQ-I-008 corollary.) |
| 24 | 2026-06-04 | [Client-computed at-a-glance summary (D-1)](LESSONS.md#24) | For a roll-up that's backend-field-correct but demo-computable, build a PURE unit-tested derivation over the full loaded rows now (D-1) — pin the discrimination edges (reviewed-clean vs reviewed-with-disputes) — and queue the backend field as the production follow-up; never compute the roll-up inline in the component. (Pairs with §21.) |
| 25 | 2026-06-04 | [Per-surface tone map (§7 clarification)](LESSONS.md#25) | When two surfaces tone the same enum differently per the canon mockups, add a DISTINCT named map in the single-source `statusTaxonomy.ts` (consumed once) — NOT an inline re-map and NOT a mutation of the shared map; "single source of visual truth" (§7) is the file, not one-map-per-enum. Confirm the divergence is intentional canon first. (Refines §7.) |
| 26 | 2026-06-04 | [Standalone demo needs `.env.local` `VITE_AUTH_MODE=demo`](LESSONS.md#26) | The standalone demo requires a gitignored `.env.local` with `VITE_AUTH_MODE=demo` — an undefined mode makes `prepareHeaders` throw → the app hangs on the loading skeleton with no surfaced error; inline `yarn dev` env doesn't reach `import.meta.env`. Documented in `.env.example`. (Demo/dev-only.) |
| 27 | 2026-06-04 | [Solo headless agent-browser QA — §18 obsolete](LESSONS.md#27) | Solo headless gstack `agent-browser` now drives the persona switch + drawers + theme toggle (ST.8a's custom dropdown replaced the native `<select>`) — the §18 "headed-only / user-present" persona-`<select>` constraint is obsolete; drive persona/drawer/theme interaction QA solo + headless (a real-browser spot-check still warrants §18's computed-position caveat). (Updates §18.) |
| 28 | 2026-06-04 | [Two build targets from one Vite config + the `.gitignore`-`build/` gotcha](LESSONS.md#28) | Build BOTH the MF remote (default `build`, federation ON, REQ-I-008 demo-free) AND the deployable standalone SPA (`build:standalone` → `VITE_BUILD_TARGET=standalone` → federation OFF, `index.html` entry) from one `vite.config.ts` via a pure env-injected `shouldEnableFederation` resolver — fail-safe-to-remote (only the exact `'standalone'` opts out). Unit-test the resolver; build-verify the dist. ⚠️ Never name a committed dir/file `build/…` — the repo `.gitignore` `build/` rule swallows it silently. |
| 29 | 2026-06-04 | [Wire the accessor seam synchronously before first-render readers](LESSONS.md#29) | If a child reads an injected seam (`hasAccessTokenProvider()`, context, a global) on its FIRST render, register it synchronously during the provider's render (`useState` lazy-init), not a mount `useEffect` (too late for same-commit children); cleanup in an unmount effect, post-render reactions (§16 reset) in effects; pin the composed provider→children nest, not just isolated pieces. |
| 30 | 2026-06-04 | [REQ-I-008 build-grep scopes to the exposed chunk](LESSONS.md#30) | Scope any REQ-I-008 build-output grep to the federation-exposed chunk (`remoteEntry`+`__federation_expose_*`), never all of `dist/` — the same `vite build` also emits the standalone SPA bundle (demo + auth0 SDK by design); drive the source proof from `WeeklyCommitApp`'s import-graph, not `main.tsx`. |
| 31 | 2026-06-06 | [Wire the host accessor synchronously ACROSS the federation boundary](LESSONS.md#31) | When a host passes `getAccessToken` into a federation-exposed module, register it SYNCHRONOUSLY in the EXPOSED module's render (`useState` lazy-init) — the remote's child first-render readers (RTK Query `prepareHeaders`) run before any host mount effect; a too-late effect → "No access-token provider configured". Pin with a first-render seam-read probe. (Extends §29 to host↔remote.) |
| 32 | 2026-06-06 | [@originjs federated CSS broken under absolute `--base` → `?url` + manual `<link>`](LESSONS.md#32) | @originjs auto CSS-injection drops `assetsDir` under an absolute `--base` (`base+bare-filename` → 404, unstyled embed); import the stylesheet as a base-resolved `?url` + inject a manual `<link>` on mount in the exposed module, keep the expose CSS array empty. Federation styling is live-only — verify on the deployed embed, not jsdom. |
| 33 | 2026-06-06 | [@originjs host↔remote cascade — single exposed ensure, all React-deps shared, remote self-provides store](LESSONS.md#33) | For an @originjs MF remote: expose ONE component + fold its store into that ensure (a separate `import('remote/store')` deadlocks shareScope → host hangs "Connecting…"); keep ALL 5 React-coupled deps shared singletons (react, react-dom, react-redux, @reduxjs/toolkit, react-router-dom — unshare any → dual-React `useRef`-null); the remote may self-provide its `<Provider store>` while redux stays shared (orthogonal). Realized surface: 1 expose + 5 shared singletons + same-origin `/portal/`. Verify live (not jsdom). |

<!-- Starts empty. Each row links to its `LESSONS.md` anchor. -->

<!-- Slash commands: see root CLAUDE.md "Slash commands available." Implementer pair: /session-start + /session-end. -->
