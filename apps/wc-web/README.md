# `wc-web` — Weekly Commit micro-frontend

A React 18 + Vite 5 (TypeScript-strict) remote that renders the IC weekly-planning
workspace and the manager command center. It runs **two ways**:

- **Standalone** (`src/standalone/main.tsx`) — for local dev / demo: owns its own
  router, Redux store, theme, and demo-identity chrome.
- **Module-Federation remote** (`src/remote/WeeklyCommitApp.tsx`, exposed as
  `./WeeklyCommitApp`) — embedded inside a host shell that provides the router,
  store, and auth accessor.

This README covers **running it locally**, **deploying the standalone SPA to AWS**
(with real Auth0 OAuth), and the **host-integration contract** for the remote mode
(REQ-I-007, REQ-I-013; `ARCHITECTURE.md §7`, §22 / OQ-004):

- [Running locally](#running-locally) — the Vite + MSW dev loop, the quality gate, the standalone build.
- [Deploying to AWS](#deploying-to-aws-standalone-spa--real-oauth) — `build:standalone` → S3/CloudFront + the Auth0 OAuth login flow.
- [Host integration](#host-integration) — the Module-Federation remote contract (below).

---

## Running locally

The standalone app (`src/standalone/main.tsx`) renders the full UI with **no backend**
via an MSW mock layer (ST.7a / 9.15) — the fastest way to see and QA every surface.

```bash
yarn install                                          # from the repo root (Yarn workspaces)
cp apps/wc-web/.env.example apps/wc-web/.env.local    # ⚠️ required — see note below
yarn nx dev wc-web                                    # Vite dev server (default http://localhost:5173)
```

- **`.env.local` is required.** Vite reads `VITE_*` only from a dotenv file, not from an
  inline `VITE_AUTH_MODE=demo yarn dev` — without it `prepareHeaders` throws
  `Unsupported VITE_AUTH_MODE` and every query hangs on the loading skeleton (LESSONS §26).
  The committed `.env.example` defaults to `VITE_AUTH_MODE=demo`.
- **Demo mode = MSW + personas.** With `VITE_AUTH_MODE=demo` (and `VITE_API_BASE_URL`
  **unset** — the mock worker only intercepts same-origin requests), the app boots the
  mock worker and the in-app **PersonaSwitcher** (a manager + IC personas). Switch personas
  to exercise the manager command center, the IC weekly plan, and the live disputes loop.
- **Run against a real local API** instead of mocks: set `VITE_USE_MOCKS=false` +
  `VITE_API_BASE_URL=http://localhost:8080` in `.env.local` (and run wc-api locally).

### Quality gate

```bash
yarn nx lint wc-web && yarn nx typecheck wc-web && yarn nx test wc-web   # 310 Vitest, all green
yarn prettier --check .
```

### The standalone build (preview the deployable artifact)

```bash
cd apps/wc-web && yarn build:standalone   # VITE_BUILD_TARGET=standalone → dist/ (index.html + hashed assets)
yarn preview                              # serve dist/ over a static server + open the SPA
```

`build:standalone` turns the Module-Federation plugin **OFF** and builds a static SPA from
`index.html` (LESSONS §28). Plain `yarn build` (= `build:remote`) is **unchanged** — it emits
the production `remoteEntry.js`. The two are separate artifacts from one `vite.config.ts`.

---

## Deploying to AWS (standalone SPA + real OAuth)

The deployed demo serves the **standalone SPA** as static files from **S3 + CloudFront**,
authenticating against the real wc-api via **Auth0 OAuth** — never the demo header (safety
rule #5; `DEMO_AUTH_ENABLED` stays off in the deployed env).

### 1. Build the SPA (env is baked at build time)

Vite inlines `import.meta.env` at build → **one build per backend URL + Auth0 tenant**. Set
these (in `.env.local` or the build environment) — the full reference + verified values live
in [`.env.example`](.env.example), not duplicated here:

| Var                    | Value                                                                        |
| ---------------------- | ---------------------------------------------------------------------------- |
| `VITE_AUTH_MODE`       | `auth0`                                                                      |
| `VITE_USE_MOCKS`       | `false`                                                                      |
| `VITE_API_BASE_URL`    | `https://api.wc.<ROOT_DOMAIN>` (required — fail-fast if unset)               |
| `VITE_AUTH0_DOMAIN`    | the issuer tenant domain (the SAME tenant as the backend `AUTH0_ISSUER_URI`) |
| `VITE_AUTH0_CLIENT_ID` | the Auth0 SPA application's Client ID                                        |
| `VITE_AUTH0_AUDIENCE`  | `https://api.wc.<ROOT_DOMAIN>` (== backend `auth0.audience`)                 |

```bash
cd apps/wc-web && yarn build:standalone    # → dist/  (index.html + hashed assets)
```

### 2. Serve from S3 + CloudFront

Upload `dist/` to the S3 bucket fronted by CloudFront (Terraform: `infra/terraform/s3_cloudfront.tf`,
task 12.6). **SPA-fallback is already configured there** — CloudFront returns `/index.html` (200)
for `403`/`404`, with `default_root_object = index.html` — so a hard refresh or deep link on a
client route (e.g. `/callback`, `/manager/command-center`) resolves to the SPA, not a CloudFront 404. No extra infra is needed for client-side routing.

### 3. The OAuth login flow (9.17)

`VITE_AUTH_MODE=auth0` activates the standalone-only **Auth0 PKCE SPA login**
(`src/standalone/Auth0IdentityProvider.tsx`):

1. Unauthenticated → a branded **login screen** → `loginWithRedirect()` → Auth0 Universal Login.
2. Auth0 redirects to **`<origin>/callback`** → the SDK exchanges the code → `onRedirectCallback`
   navigates into the app (the `/callback` route renders only while the exchange runs).
3. `getAccessTokenSilently()` feeds the token into the existing accessor seam → every request
   carries `Authorization: Bearer <jwt>` → wc-api validates it and resolves the seeded employee
   via the `…/employee_id` claim.
4. The app-bar shows **"signed in as X"** + Log out (the PersonaSwitcher is retired in auth0 mode).

**Personas are real Auth0 test users**, one per seeded employee — log in as the manager (Dana) or
an IC; cross-role flows (e.g. disputes) run across two browser windows. The federated remote build
carries **none** of this — the Auth0 SDK is standalone-only (REQ-I-008, pinned by `boundary.test.ts`).

### 4. Stand up the Auth0 tenant (HITL — one-time)

Configuring the Auth0 tenant (API/audience, the SPA app + Allowed Callback `<origin>/callback`,
the 7 test users, the post-login Action that emits the `…/employee_id` claim) and the backend
OAuth env (`AUTH0_ISSUER_URI` / `AUTH0_AUDIENCE` / `DEMO_AUTH_ENABLED=false`) is a one-time manual
step — follow **[`docs/runbooks/auth0-tenant-setup.md`](../../docs/runbooks/auth0-tenant-setup.md)**.

> The EKS / RDS / S3 / CloudFront provisioning itself is infra (Phase 12) + HITL — see `infra/`
> and the deploy runbooks.

---

## Host integration

> **Status — provisional (REQ-I-013 / OQ-004).** The remote uses a **generic Vite
> Module-Federation pattern** (`@originjs/vite-plugin-federation`) until the real
> PA (host platform) remote pattern is verified. The shapes below are realized in
> `vite.config.ts`, `src/remote/WeeklyCommitApp.tsx`, and `src/app/authAccessor.ts`;
> if the verified PA pattern differs, this contract is the single place to reconcile.

### Remote identity (from `vite.config.ts`)

| Field          | Value                                                           |
| -------------- | --------------------------------------------------------------- |
| Remote name    | `wc_web`                                                        |
| Entry filename | `remoteEntry.js`                                                |
| Exposed module | `./WeeklyCommitApp` → `src/remote/WeeklyCommitApp.tsx`          |
| Exposed shape  | **default export** — a React component `WeeklyCommitApp(props)` |

The remote is **self-contained for state**: `WeeklyCommitApp` wraps its own
`<Provider store={store}>` internally (the store + `baseApi` are remote-internal),
so the host does **not** supply a store and there is **no `./store` expose**. An
earlier `./store` expose whose chunk did a top-level `await
importShared('@reduxjs/toolkit')` deadlocked the cross-build shared-scope init and
hung the host forever on "Connecting…"; self-providing removes that await. A worked
host lives in `apps/wc-host/`.

The exposed module **consumes** a host-provided router: it renders the lazy
`<AppRoutes/>` **inside the host's router context** and never creates a
`BrowserRouter` of its own. It owns no shell chrome (no nav, no `PersonaSwitcher`,
no `ThemeToggle`).

### The host MUST provide, before mount (load-bearing — OQ-004)

1. **A router — a `<BrowserRouter>` (or equivalent history router) wrapping the
   remote.** The remote renders `<AppRoutes/>`'s `<Routes>`/`useNavigate` inside the
   host's router context and never creates its own. (The Redux store is NOT a host
   responsibility — the remote self-provides it; see "Remote identity" above.)

2. **An auth accessor — `getAccessToken(): Promise<string>`** — passed as the
   `getAccessToken` prop to `WeeklyCommitApp`. The remote registers it into the
   shared accessor seam (`setAccessTokenProvider`, `src/app/authAccessor.ts`) so
   `prepareHeaders` attaches the host's bearer token. Required in **`auth0`** mode;
   if it is absent in auth0 mode the remote renders a non-fatal `role="alert"`
   notice (it never crashes the host). Not required in `demo` mode.

3. **`VITE_API_BASE_URL`** — the wc-api base URL (host-overridable build env).
   Resolved by `resolveApiBaseUrl` (`src/app/baseApi.ts`): a **production build
   with it unset fails fast at import** rather than silently using a relative `/`.

Minimal host mount (illustrative — generic pattern, pending PA verification):

```tsx
import WeeklyCommitApp from 'wc_web/WeeklyCommitApp';
import { BrowserRouter } from 'react-router-dom';

// The host owns the router; the remote consumes it AND self-provides its own Redux
// store (no host store import). getAccessToken is required only in auth0 mode.
// (Worked host: apps/wc-host/.)
<BrowserRouter>
  <WeeklyCommitApp getAccessToken={host.getAccessToken} />
</BrowserRouter>;
```

### Shared singletons (federation `shared`, from `vite.config.ts`)

| Package            | `requiredVersion` |
| ------------------ | ----------------- |
| `react`            | `^18.3.1`         |
| `react-dom`        | `^18.3.1`         |
| `@reduxjs/toolkit` | `^2.3.0`          |
| `react-redux`      | `^9.1.2`          |
| `react-router-dom` | `^6.28.0`         |

ALL React-coupled singletons are shared so the remote uses the host's **single React
instance**: `react`/`react-dom` (else "invalid hook call"); `@reduxjs/toolkit` +
`react-redux` (a bundled react-redux runs its hooks — `useSyncExternalStore` →
`React.useRef` — against a second React → "Cannot read properties of null (reading
'useRef')", a blank-page dual-React crash); `react-router-dom` (the remote renders
`<AppRoutes/>` inside the host's `<BrowserRouter>`, one Router context).

**The store is still self-provided** (no `./store` expose) even though redux is
shared — those are independent. Sharing redux does NOT reintroduce the deadlock: the
deadlock was a separate `import('wc_web/store')` running BEFORE the WeeklyCommitApp
ensure and racing the shared-scope init; with the store folded into the single
WeeklyCommitApp ensure, redux's `importShared` resolves in the canonical flow (like
`react`). So: **redux shared (singleton React) + store self-provided** — both true.

`@originjs/vite-plugin-federation` **dedups** each shared dependency into one
version-matched shared chunk — it is **not** webpack-style `singleton` enforcement
(that key isn't in its typed API). The **single React instance** guarantee is
therefore a **host + remote shared-scope agreement** (this contract), not something
the plugin enforces alone. The host must place these five packages in the shared
scope at compatible versions.

### Auth boundary — `VITE_AUTH_MODE` XOR (REQ-I-008)

Exactly **one** identity path is active per build (`prepareHeaders`, `src/app/baseApi.ts`):

| `VITE_AUTH_MODE` | Header attached                        | Source                                        |
| ---------------- | -------------------------------------- | --------------------------------------------- |
| `auth0`          | `Authorization: Bearer <jwt>` **only** | host `getAccessToken()` via the accessor seam |
| `demo`           | the demo persona header **only**       | a **standalone-only** injected applier        |

The two are never combined; an unrecognized mode fails loud (`Unsupported
VITE_AUTH_MODE`). The **demo header-name literal lives only under `src/standalone/`**
and is tree-shaken out of the exposed remote build, so the shared, remote-reachable
`baseApi`/`authAccessor` carry no demo branch (REQ-I-008; pinned by the REQ-I-008
boundary test in `src/remote/boundary.test.ts`).

### What the remote does NOT own (REQ-I-008)

The remote does **not** duplicate PA-shell-owned concerns:

- No `BrowserRouter` / global routing ownership (consumes the host router).
- No shell navigation / app chrome.
- No demo-identity / persona switching (standalone-only; tree-shaken from the remote).
- No LogRocket / Loki / observability ownership.
- No Nx-workspace / shell-config ownership.

### Standalone vs remote — responsibility split

| Concern     | Standalone (`src/standalone/StandaloneShell.tsx`) | Remote (`src/remote/WeeklyCommitApp.tsx`)    |
| ----------- | ------------------------------------------------- | -------------------------------------------- |
| Router      | owns `BrowserRouter`                              | **consumes** the host's                      |
| Redux store | owns `Provider` + `store`                         | **self-provides** its own `Provider`+`store` |
| Theme       | owns `ThemeProvider` + `ThemeToggle`              | host-owned                                   |
| Identity    | `DemoIdentityProvider` + `PersonaSwitcher` (demo) | host injects `getAccessToken`                |
| Mounts      | `<WeeklyCommitApp/>`                              | exposes itself                               |

### Environment variables (host-overridable)

See `.env.example`. The two build-time vars the host overrides:

- `VITE_AUTH_MODE` — `demo` (standalone) **xor** `auth0` (hosted).
- `VITE_API_BASE_URL` — wc-api base URL; required in production builds.

---

This contract is anti-drift–guarded by `src/remote/host-integration.test.ts`
(it pins the documented federation name / entry / exposed module / shared
singletons against `vite.config.ts`, and the `getAccessToken` signature against
`src/app/authAccessor.ts`).
