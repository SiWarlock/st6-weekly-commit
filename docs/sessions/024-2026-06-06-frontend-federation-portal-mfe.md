# Session 024 — Frontend: Module Federation host (Acme Portal) + the live-portal integration-bug cascade

- **Date:** 2026-06-06
- **Phase:** Post-deploy demo hardening (federation MFE demonstration)
- **Predecessor:** [023 — backend deploy dominoes](023-2026-06-06-backend-deploy-dominoes-worker-concurrency-graph-polish.md)
- **Successor:** _(TBD)_
- **Area:** `apps/wc-host` (new) + `apps/wc-web` (remote contract)

## Why this session existed

Demonstrate that `wc-web` is a real Module Federation **remote** that plugs into a parent host. Built a new host shell (`apps/wc-host`, "Acme Portal") that loads the `wc_web` remote and mounts the exposed `./WeeklyCommitApp` inside parent chrome, authenticated against the **same live Auth0 tenant** so the embedded remote calls the **live API**. Then drove it to actually work on the deployed portal — which surfaced a cascade of four federation integration bugs, each revealed only after the previous was cleared.

## What was built

### Files created

- `apps/wc-host/**` — the host package: `vite.config.ts` (`@originjs` federation host; `remotes:{wc_web}`; shared scope; `loadEnv` for the remote URL; `--base` driven), `src/main.tsx`, `src/App.tsx` (`<BrowserRouter basename=BASE_URL>` → Auth0Provider), `src/auth/authConfig.ts` (resolve `VITE_AUTH0_*`, redirect = `<origin><BASE_URL>callback`), `src/portal/Portal.tsx` (Acme chrome + auth gate), `src/portal/WeeklyCommitMount.tsx` (lazy remote in `<Suspense>`), `src/portal/portal.css`, `src/types/remotes.d.ts`, `src/vite-env.d.ts`, `index.html`, `README.md`, `.env.example`, `.gitignore`, `project.json`, `tsconfig*.json`, `package.json`.
- `apps/wc-web/src/remote/WeeklyCommitApp.accessor-timing.test.tsx` — §29 regression pin (probe asserts the accessor seam is wired at the child's first render).

### Files modified

- `apps/wc-web/src/remote/WeeklyCommitApp.tsx` — now SELF-PROVIDES its store (`<Provider store>`), registers the host accessor SYNCHRONOUSLY (`useState` lazy-init, §29), and injects its stylesheet via a base-resolved `?url` `<link>`.
- `apps/wc-web/vite.config.ts` — federation surface: ended at **one** expose (`./WeeklyCommitApp`, no `./store`) + **five** shared singletons (`react`, `react-dom`, `@reduxjs/toolkit`, `react-redux`, `react-router-dom`).
- `apps/wc-web/src/remote/host-integration.test.ts` — `SHARED_SINGLETONS` tracked the shared-scope changes (ended at all five).
- `apps/wc-web/src/remote/WeeklyCommitApp.test.tsx` — added the federated-CSS `<link>` injection pin + cleanup.
- `apps/wc-web/README.md` — host-integration contract aligned to the realized surface.
- `apps/wc-web/src/standalone/shell/PrimaryNav.tsx` — week-indicator polish (disabled chevron button → static labeled chip; demo cosmetics).

### Local commits (this session, NOT pushed)

`14cdb39` host pkg · `f9157b0` week-indicator polish · `5da4d6b` Option-B `/portal/` base · `2c89708` self-provided store (deadlock) · `58a56f9` README · `2f817b8` re-share redux (dual-React) · `faef62d` README fix · `b8dc412` §29 sync accessor · `1b3854a` federated CSS `?url` · `9091f31` CSS-injection test.

## Decisions made

- **Cross-origin → same-origin (Option B).** AWS account-verification gate blocked a new `portal.*` CloudFront, so the portal is served from a PATH on the existing `wc.` distribution (`wc.st6weeklycommit.com/portal/`), remote at `wc./remote/`. Same-origin ⇒ **no CORS needed**; host built with `--base=/portal/`, basename/redirect derived from `import.meta.env.BASE_URL`.
- **Remote self-provides its store** (no `./store` federation expose). A separate `import('wc_web/store')` ran a top-level `await importShared('@reduxjs/toolkit')` BEFORE the WeeklyCommitApp ensure and deadlocked the shared-scope init → host hung on "Connecting…". Folding state into the single exposed-component ensure fixed it.
- **All React-coupled deps stay shared** (react, react-dom, @reduxjs/toolkit, react-redux, react-router-dom). Unsharing redux (an interim attempt) made the remote bundle react-redux against a 2nd React → `Cannot read properties of null (reading 'useRef')` dual-React crash. The store is self-provided AND redux is shared — independent concerns.
- **Synchronous accessor registration (§29).** The host's `getAccessToken` is registered in a `useState` lazy-init during render, not a mount effect — child `<AppRoutes/>`'s eager first RTK Query (`prepareHeaders→getAccessToken()`) runs before parent effects, so an effect registered too late → "No access-token provider configured".
- **Federated CSS via `?url` manual injection.** @originjs's auto CSS-injection is broken with our absolute `--base`: it builds `base + bare-filename` and DROPS the `assets/` dir → 404. Importing the stylesheet as a base-resolved `?url` yields the correct `…/remote/assets/theme-<hash>.css`, injected as a `<link>` on mount.

## Decisions explicitly NOT made

- **Did not make the remote own its `<BrowserRouter>`** (the candidate 2b "routing escape" fix). The escape turned out to be a dual-React crash artifact — once that cleared, routing under `/portal/` worked, so the contract-violating remote-owns-router change was avoided.
- **Did not pin the deploy to `build:standalone`.** Left the existing `nx build wc-web` (remote) for the root site; the standalone-vs-remote SPA delta was verified safe rather than changing the deploy target mid-flight.

## TDD compliance

The four federation bugs were **live production incidents** (runtime federation behavior is not reproducible in the jsdom unit env), diagnosed against the deployed portal and the build artifacts, then fixed. Regression tests were added wherever the unit env permits: the §29 accessor-timing probe and the CSS `<link>` injection pin. The config-level fixes (store self-provide, shared scope) are verified by build-artifact inspection + the live re-test (not unit-reproducible). Not red-green-first — this was the incident/bug-hunt path. `apps/wc-host` is a demo integration shell (visual/wiring + verified via build + live QA), exempt per the visual/non-deterministic posture. wc-web suite: **314/314 green**, host `tsc` clean.

## Reachability

All fixes are reachable from the **`WeeklyCommitApp` federation production entry** (`vite.config.ts` `exposes:'./WeeklyCommitApp'`), and standalone reaches the same component via `StandaloneShell`:

- §29 sync accessor registration → `WeeklyCommitApp.tsx` render (`useState` init). Proven live (`/api/me` fired with the host token → real data).
- self-provided store (`<Provider store>`) + re-shared redux → render path + shared scope. Proven live (no dual-React; real data rendered).
- federated CSS `<link>` injection → `WeeklyCommitApp.tsx` mount effect; correct URL verified in the build artifact (live-pending the final `/remote/` resync).
  No dead code; no tested-but-unwired gaps.

## Open follow-ups

- **Lesson candidates (route to orchestrator → `LESSONS.md`; I did not write it):**
  1. **§29 across the federation boundary** — the host-provided accessor must be registered SYNCHRONOUSLY (`useState` lazy-init) in the exposed module, not a mount effect; child effects (RTK Query first-query) run before parent effects. (Extends Lesson §29 to the host path.)
  2. **@originjs federated CSS is broken with an absolute `--base`** (drops `assetsDir` → 404). Inject the stylesheet via a `?url` base-resolved import + manual `<link>` on mount; verify the expose's `y([])` stays empty so no competing 404 link fires.
  3. **@originjs host↔remote integration cascade** — (a) never do a separate pre-component `import('remote/store')` (races shareScope init → deadlock); fold state into the single exposed-component ensure; (b) keep ALL React-coupled deps shared (unsharing redux → dual-React `useRef`-null); (c) the remote may self-provide its store while redux stays shared.
- **Cross-doc / contract (orchestrator territory):** the realized federation surface (1 expose `./WeeklyCommitApp`; remote self-provides store + CSS; 5 shared singletons; same-origin `/portal/` host) should be reflected in `ARCHITECTURE.md` §7 / §22 / OQ-004. The host-integration contract READMEs (`apps/wc-web/README.md`, `apps/wc-host/README.md`) are current.
- **Final live confirmation pending:** bug-4 styling needs the user-authorized `/remote/` resync + a hard-refresh visual check (the embedded remote should render fully themed). Bugs 1–3 are confirmed live with real data.
- **Infra DX:** the prod `s3 sync`/`cloudfront create-invalidation` is classifier-gated to direct user auth — a scoped one-time Bash allow-rule would collapse the per-fix round-trip (flagged to the lead/user).

## How to use what was built

Run the host locally: build the remote (`apps/wc-web`: `VITE_AUTH_MODE=auth0 VITE_API_BASE_URL=… yarn vite build --base=https://wc.st6weeklycommit.com/remote/`), serve its `dist`; build the host (`apps/wc-host`: `VITE_WC_REMOTE_URL=…/remote/assets/remoteEntry.js yarn build --base=/portal/`), serve `dist`. Full walkthrough + the deployed-publish + Auth0/CloudFront prerequisites are in `apps/wc-host/README.md`.
