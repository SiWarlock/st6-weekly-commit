# Session 020 — Frontend deploy-demo round (standalone SPA build 9.16 + Auth0 OAuth login 9.17)

> **Orchestrator-authored session doc** (NOT a `/session-end` output). The frontend implementer shipped 9.17 cleanly (`531000d`) and is being **retired by the lead** post-seal (deploy-phase coding done; a fresh impl spins for the HITL live-demo QA after the user deploys) — so it did not run `/session-end`, and the orchestrator (`st6-main-wc-web-orchestrator`, persisting) captured this. Date: 2026-06-04. Track: st6-main. Predecessor frontend doc: `018-2026-06-04-frontend-styling-fidelity-9.15-st8a-c.md`.

## Round context — the deployed-backend demo with real OAuth
User decision (via lead, 2026-06-04): the demo runs against a **real wc-api + RDS** with **real Auth0 OAuth** (production-honest, NOT the `X-Demo-Employee-Id` header). The MSW/persona path stays for local dev. The backend OAuth2 resource-server + the frontend Bearer-send seam were already built (Phase 2 + 9.1/9.3) — only the **frontend login producer side** was the gap. Two frontend slices close it.

## Slices shipped (the deploy-demo frontend round)

| Slice | Commit | Brief | What |
|---|---|---|---|
| **9.16** standalone SPA build | `1c04e3c` | 083 (predecessor) | `build:standalone` (`VITE_BUILD_TARGET=standalone`) flips Module Federation OFF → a deployable static SPA (`dist/index.html` + hashed assets, no `remoteEntry`) via a pure env-injected `shouldEnableFederation` resolver (`vite.buildTarget.ts`), **fail-safe-to-remote**. `build` (=`build:remote`) unchanged. LESSONS §28. (Prior-landed; no session doc until now.) |
| **9.17** Auth0 OAuth login | `531000d` | 085 (this session) | `@auth0/auth0-react` PKCE SPA login producer (below). 310 Vitest green; typecheck + lint clean; REQ-I-008 verified two ways. |

### 9.17 detail (12 new files + 9 modified)
- **`Auth0IdentityProvider`** (standalone-only, mirrors `DemoIdentityProvider`): `<Auth0Provider>` (the `…WithNavigate` pattern) + a child that feeds `getAccessTokenSilently` into the existing `setAccessTokenProvider` seam **synchronously** (a `useState` lazy-initializer — see Decisions) and clears it on unmount; §16 `resetApiState` on `user.sub` change.
- **`Auth0LoginGate`** (loading → branded `LoginScreen` → app) on `/*`; **`CallbackRoute`** on `/callback` renders OUTSIDE the gate (so the SDK code-exchange isn't shadowed). Both wired via a standalone `<Routes>` in `StandaloneShell` — NOT the remote `AppRoutes` (REQ-I-008).
- **`Auth0IdentitySlot`** ("signed in as X" + logout) injected into `AppBar` via a new `identitySlot` prop (defaults to `PersonaSwitcher`; `AppShell` forwards it) — the PersonaSwitcher is retired in auth0 mode.
- **`auth0Config`** fail-fast resolver over `VITE_AUTH0_DOMAIN/CLIENT_ID/AUDIENCE` (+ `vite-env.d.ts` types + the `.env.example` contract block); `StandaloneShell` branches on `VITE_AUTH_MODE` (demo path unchanged).
- **REQ-I-008** verified two ways: `boundary.test.ts` (import-graph + fail-closed grep, extended for auth0) + a real federation-exposed-chunk build-grep (the auth0 SDK is only in the standalone `index.html` bundle, never `remoteEntry`/`__federation_expose_*`).

## Decisions made
- **Real Auth0 OAuth, real test users per V5 persona** (user/lead) — the PersonaSwitcher retires for the deployed demo (stays on the dev/MSW path).
- **OAuth login is standalone-only** — the remote still gets its token from the host (§7); the auth0 SDK never enters the exposed remote (REQ-I-008 now asserts auth0-absence too).
- **Dedicated `/callback` route** (`redirect_uri=<origin>/callback`) — avoids racing `RootRedirect` on `/`. Auth0 Allowed-Callback must list `<origin>/callback`; CloudFront SPA-fallback already satisfied by infra 12.6.
- **Synchronous seam wiring** (`useState` lazy-init, not a mount `useEffect`) — `WeeklyCommitApp` reads `hasAccessTokenProvider()` on first render; an effect runs too late → "no accessor" alert. (LESSONS §29; caught by the Step-2.5 fold-in authenticated-nest test.)
- **§16 reset keyed on `user.sub`**; **let `getAccessTokenSilently` failures throw** (the existing `prepareHeaders` generic-error path; no re-login from inside the header builder — a later enhancement); **1 commit** (cohesive, no safety invariant).

## Decisions explicitly NOT made / deferred
- **`CallbackRoute` auth-error UX** — renders processing only (YAGNI per TDD); a branded callback-error display is a later enhancement (Carry-forward).
- **Re-auth-on-401** (`getAccessTokenSilently` `login_required` → re-prompt) — deferred (Carry-forward / later slice).
- **Root README** — not done; joint with the fresh backend orch (Carry-forward / next frontend work).

## Open follow-ups
- The **root README** (local + AWS, cross-linking the runbooks) — coordinate with the fresh backend orch.
- The **HITL live-demo QA** (after the user deploys to AWS + stands up the Auth0 tenant per `docs/runbooks/auth0-tenant-setup.md`) — a fresh impl spins for any QA fixes then.
- Carry-forward: the `CallbackRoute` error display; the `yarn add` wc-e2e workspace-normalization watch; + the inherited ST.8/9.15 demo follow-ups.

## Cross-area coordination (this round)
- Auth0 claim/audience contract verified against the backend `application.yml`/`application-prod.yml` + the V5 seed (claim `…/employee_id`, audience `https://api.wc.<ROOT_DOMAIN>`, `external_subject st6|<first>-<last>` — Dana + 6 ICs).
- `docs/runbooks/auth0-tenant-setup.md` split section-disjoint (frontend SPA-app/SDK detail; infra owns API/users/Action/env) — infra has since expanded it comprehensively (Part 1 Auth0 + Part 2 M365/Graph).
- Brief lane: 085 frontend / 086 backend-V6 / next-free 092 (re-sync with the fresh backend orch).
- Seal landed cleanly on top of the backend's round commit `8984d26` (disjoint regions, no clobber).

## LESSONS banked this round
wc-web **§28** (two build targets from one Vite config + the `.gitignore`-`build/` gotcha — 9.16) · **§29** (wire the accessor seam synchronously before first-render readers) · **§30** (REQ-I-008 build-grep scopes to the federation-exposed chunk). All in `apps/wc-web/LESSONS.md` + the `CLAUDE.md` index (orchestrator hot-routing; committed at this round seal).

## Round-seal note
This round's `/orchestrate-end` (orchestrator) committed: brief 085 + LESSONS §29/§30 + the CLAUDE index rows + the extended `apps/wc-web/README.md` + the MVP_TASKS deploy-demo block (9.16/9.17 rows + status + Log + Carry-forward triage) + the ARCHITECTURE §7 "Realized (9.16/9.17)" note + the runbook §1.2 frontend-SDK pointer. No push (no remote configured). The frontend impl is retired post-seal; the orchestrator holds available for the root README + the HITL live-demo QA.
