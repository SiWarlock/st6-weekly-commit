# Session 002 — Frontend styling foundation + Phase 9 spine (wc-web)

- **Date:** 2026-06-02
- **Area / role:** `apps/wc-web/` · frontend implementer (`st6-main-wc-web-implementer`)
- **Phase:** Phase ST (ST.1/ST.2) + Phase 9 (9.1, 9.3, 9.2 / ST.3 fold-in)
- **Predecessor session:** [001 — Phase 0 backend + Phase 1 schema](001-2026-06-02-phase0-backend-and-phase1-schema.md) (backend track; this is the first **frontend** session)
- **Successor session:** _(next frontend session — to be linked; next slice is 9.5 me/rcdo)_

> Cross-track note: 001 is the backend track's session doc. Per shared-tree discipline this doc does not edit 001's successor link (another track's committed file); it references 001 as the chronological predecessor only.

## Why this session existed

The UI design (Cadence design system, `docs/design/cadence-design-system/`) landed and was signed off, lifting the styling gate. Frontend work started in parallel with the backend: stand up `apps/wc-web` themed from Cadence at birth, then build the Phase 9 data-layer + MFE-boundary + shared-presentational spine that every later view composes against.

## What was built

Four slices, each a clean `/tdd` cycle (RED → Step-2.5 orchestrator review → GREEN → review → Step-9 → commit):

### ST.1 + ST.2 — Cadence-themed shell + dark/light theming (`e3c1cb7`)
- **Files created:** `apps/wc-web/` shell — `package.json`, `tsconfig.json`, `vite.config.ts` (+ Vitest/jsdom), `tailwind.config.ts` (token bridge), `postcss.config.cjs`, `eslint.config.js` (ESLint 9 flat), `.prettierrc`/`.prettierignore`, `.gitignore`, `.env.example`, `project.json` (Nx targets), `index.html`; `src/styles/theme.css` (the ONE token-var stylesheet); `src/app/flowbiteTheme.ts`; `src/app/theme/{ThemeProvider.tsx, useThemePreference.ts}`; `src/standalone/{main.tsx, ThemeToggle.tsx}`; `src/App.tsx`; `src/test/{setup.ts, util.ts}` + 4 test files.
- Approach A (Tailwind/Flowbite-native): Cadence tokens → CSS vars → `tailwind.config` `theme.extend` (`var(--…)`, no hex) + a Flowbite `createTheme` object applied via `<Flowbite>`. Dark default + light toggle via `[data-theme]` flip; persisted (`localStorage` → `prefers-color-scheme` → dark); `darkMode: ['selector','[data-theme="dark"]']` binds Flowbite's `dark:` to our attribute. Indigo `#5E6AD2` theme-stable. No bespoke `.wc-*` CSS.

### 9.1 — RTK Query base + auth header XOR + problem-details (`4efd1e6`)
- **Files created:** `src/app/{tags.ts, authAccessor.ts, baseApi.ts, store.ts}`, `src/shared/lib/problemDetails.ts`, `src/vite-env.d.ts` + 3 test files. **Modified:** `src/standalone/main.tsx` (+`<Provider store>`).
- `baseApi` (nine tagTypes); `prepareHeaders` attaches EXACTLY ONE auth header per `VITE_AUTH_MODE` (auth0 Bearer XOR demo `X-Demo-Employee-Id`); injectable accessor seam (`getAccessToken`/`getDemoEmployeeId`, no demo logic — host/standalone inject providers); auth0-failure normalized (raw cause hidden from message); pure/total RFC-7807 parser (never surfaces `detail`/`type`/`traceId`).

### 9.3 — Module Federation boundary + tree-shaken demo/persona (`1e4f5eb`)
- **Files created:** `src/remote/{WeeklyCommitApp.tsx, WeeklyCommitApp.test.tsx, boundary.test.ts}`, `src/standalone/{DemoIdentityProvider.tsx, demoIdentity.ts, PersonaSwitcher.tsx, StandaloneShell.tsx}` + tests. **Modified:** `vite.config.ts` (federation expose, Vitest-gated), `src/standalone/main.tsx` (→ StandaloneShell), `src/app/baseApi.ts` (unknown-mode throw + `resolveApiBaseUrl`), `src/app/authAccessor.ts` (+`hasAccessTokenProvider`), `src/test/util.ts` (+`importGraph`).
- Exposed `./WeeklyCommitApp` (consumes host router + accessor, no chrome/no demo path); full standalone shell owns the chrome; demo/persona providers wire the 9.1 seam. **REQ-I-008 (frontend mirror of safety rule #5) proven** via static import-graph test + fail-closed literal scan + an auth0 build-output grep over the federation-exposed chunk (positive-control verified, gate PASS).

### 9.2 + ST.3 — shared view-state primitives + themed atoms (`1479ea6`)
- **Files created:** `src/shared/components/{LoadingState, EmptyState, ErrorState, PartialState, StatusBadge, RiskBadge, Badge, Pagination, WeekRangeLabel}.tsx` + 4 test files; `src/shared/lib/{statusTaxonomy.ts, formatWeek.ts}`. **Modified:** `src/app/flowbiteTheme.ts` (+`alert` slot).
- View-states (§7); `statusTaxonomy.ts` ports the §4.2 six-tone+icon maps (single source of visual truth); StatusBadge/RiskBadge (six B.1 values, ring for BLOCKED/CARRY_FORWARD) via a token-driven `Badge` (glyph+text+color always); ErrorState renders `safeMessage` React-escaped (REQ-S-005, never `dangerouslySetInnerHTML`); Pagination (B.20); WeekRangeLabel/formatWeek (org-tz Mon–Sun).

**Deps added across the round:** `@reduxjs/toolkit` 2.12, `react-redux` 9.3, `react-router-dom` 6.30, `@originjs/vite-plugin-federation` 1.4.1 (dev), `react-icons` 5.6, plus the shell toolchain (vite 5.4, vitest 2.1, tailwindcss 3.4, flowbite 2.5, flowbite-react 0.10.2). All in root `yarn.lock`.

## Decisions made

- **Approach A token bridge** (tokens → Tailwind theme `var(--…)` + Flowbite custom theme; flip via `[data-theme]`, no Tailwind `dark:` of our own) — Context7-confirmed for Tailwind 3 / flowbite-react 0.10.2.
- **flowbite-react 0.10.2** uses `createTheme` + `<Flowbite theme={…}>` (NOT the newer `ThemeProvider`/Tailwind-4/CLI line).
- **Auth seam** is a thin injectable contract (token + demo-id providers); no demo/persona logic in shared code → remote build stays demo-branch-free.
- **`resolveApiBaseUrl`** fail-fast in prod builds when `VITE_API_BASE_URL` unset (D.1 `R`); `/` in dev/test (Vitest supplies an absolute value).
- **REQ-I-008 proof = layered:** fast fail-open static import-graph guard + fail-closed literal scan + the authoritative auth0 build-output grep over the exposed chunk.
- **Custom token-driven `Badge` atom** for status/risk (vs Flowbite Badge) — matches the Cadence design (custom token span), enables the ring variant + grayscale data-hooks; still approach A.
- **`statusTaxonomy.ts`** is the single source of visual truth (enum→tone+icon); unknown value → render nothing; OVERDUE is a derived overlay, never stored.
- **`formatWeek`** UTC-date-only formatting of the server's org-tz Monday (avoids negative-offset drift); year-boundary aware; injectable reference for `relative`.

## Decisions explicitly NOT made (deferred)

- **9.4 demo-branch SPLIT** — `baseApi.prepareHeaders` keeps both auth0+demo branches in one fn. Before 9.4 wires the store into the remote graph, the demo-header attach must be split into a standalone-only injected seam so the fail-closed REQ-I-008 static/literal guard stays viable. (Orchestrator decision: SPLIT; folded into the 9.4 brief as a required pre-step.)
- **Single-React-instance guarantee** — @originjs v1.4.1's `singleton` key is unimplemented; sharing dedups version-matched chunks. The real guarantee is a host+remote shared-scope agreement → belongs in the **9.13 host-integration contract**, not this config.
- **`WeeklyCommitApp` singleton-fallback readiness** — only the prop-based host-accessor path is wired/tested; a host populating the seam via the shared singleton (not the prop) could render the error before the seam is set. → 9.13 (OQ-004): document "seam before mount" or make readiness reactive.
- **`formatWeek` non-Monday normalization** — explicit/compact assume the server B.20 Monday precondition (now documented); not normalized. Revisit only if a non-Monday caller appears.

## TDD compliance

**Clean — no violations.** All four slices were strict test-first: tests written at Step 2, RED confirmed for the right reason (missing modules / unmet behavior), GREEN written to pass, Step-2.5 orchestrator review before GREEN each time. GREEN-time changes (9.3 auth0-gated `ready` + test #3 alignment; 9.2 strict-typing fixes) were design refinements with the test still authored first; flagged to the orchestrator. Review-driven fixes (FOUC guard, reduced-motion, formatWeek year-boundary, etc.) added with tests where deterministic. Visual fidelity (exact colors/skeleton markup) is non-deterministic → exempt, deferred to the ST.7 design-review pass.

## Cross-doc invariant audit

**No drift.** The `apps/wc-web/CLAUDE.md` cross-doc invariants table is still placeholder (no models registered). All four slices are render-only / config / boundary — no Appendix-A model, enum, or DTO field changed. `statusTaxonomy.ts` and the badge atoms render the B.1 vocab + §4.2 taxonomy **verbatim** (the styling SoT), not a new contract; `EnumVocabularyTest` (backend) unaffected.

## Reachability

- **ST.1/ST.2, 9.1, 9.3** — reachable from `src/standalone/main.tsx` (the `index.html` entry) via `StandaloneShell` → `<Provider>`/`ThemeProvider`/`<Flowbite>`/`WeeklyCommitApp`; the remote is reachable via the federation `expose`. ✓
- **9.2 (view-state primitives + badges)** — foundation library, **NOT yet wired to a production entry path**. Consumers are existing phase tasks: **9.4** (`LoadingState` Suspense fallback + `ErrorState` on failed lazy import) and **9.5+** (every data view's loading/empty/error/success/partial; StatusBadge/RiskBadge on plan + manager surfaces). Expected "library before consumers" shape — no new tracker item (covered by the phase plan).

## Open follow-ups (Step-9 categorized list + wiring — for the orchestrator to verify)

**Convention candidates (→ LESSONS, orchestrator writes):**
- The CSS-vars→Tailwind-theme + `[data-theme]` flip multi-theme pattern; flowbite-react 0.10.2 `createTheme`+`<Flowbite>` + `darkMode` selector-binding. _(banked LESSONS §5 per orch)_
- The RTK Query mode-XOR test harness + the undici relative-`baseUrl`/`new Request()` gotcha (absolute `VITE_API_BASE_URL` in Vitest env + late-bound `fetchFn`).
- The REQ-I-008 boundary harness (fail-open static `importGraph` + fail-closed auth0 build-grep over the exposed chunk w/ positive control).
- `statusTaxonomy.ts` = single source of visual truth (never re-map a status inline; unknown→nothing; OVERDUE derived). _(LESSONS §7 per orch)_

**Architecture-doc notes (orchestrator writes):**
- `ARCHITECTURE.md §7` styling SoT = `docs/design/cadence-design-system/` (dark default + light toggle).
- `resolveApiBaseUrl` prod-enforcement of `VITE_API_BASE_URL` (§7/D.1 one-liner).
- Single-React-instance guarantee belongs in the 9.13 host contract (not @originjs shared config).

**Future TODO — belongs to a phase:**
- **9.4 (required pre-step):** split the demo-header attach out of `baseApi` into a standalone-only injected seam (before wiring the store/route-tree into the remote graph). Then the lazy route tree renders inside `WeeklyCommitApp`.
- **9.5+:** wire the 9.2 view-state primitives + badges into each data view; `transformErrorResponse` wires `parseProblemDetail` onto endpoints.
- **9.13:** host-integration contract — `WeeklyCommitApp` singleton-fallback readiness + the single-React-instance shared-scope agreement (OQ-004).
- **Phase 11:** promote the REQ-I-008 auth0 build-output grep to a CI-enforced guard (fail-closed backstop to the fail-open static walker).
- **Phase 10/11:** persona ids in `demoIdentity.ts` (`demo-employee-*`) must align with the V5 seed employee UUIDs (the demo breaks otherwise).
- **ST.7:** formal AA-contrast verification of the net-new light status tones; a11y/design-review pass.

**Cross-doc invariant change:** NONE.

## How to use what was built

- `corepack yarn nx dev wc-web` runs the standalone shell (dark default; toggle in the header). `nx test/lint/typecheck/build wc-web` are the area targets; `/preflight` chains lint+typecheck+test.
- New views compose exactly one of `LoadingState`/`EmptyState`/`ErrorState`/`PartialState`; status/risk render only via `StatusBadge`/`RiskBadge` (driven by `statusTaxonomy.ts`); never re-map a status inline.
