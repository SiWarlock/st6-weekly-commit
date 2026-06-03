# `wc-web` — Weekly Commit micro-frontend

A React 18 + Vite 5 (TypeScript-strict) remote that renders the IC weekly-planning
workspace and the manager command center. It runs **two ways**:

- **Standalone** (`src/standalone/main.tsx`) — for local dev / demo: owns its own
  router, Redux store, theme, and demo-identity chrome.
- **Module-Federation remote** (`src/remote/WeeklyCommitApp.tsx`, exposed as
  `./WeeklyCommitApp`) — embedded inside a host shell that provides the router,
  store, and auth accessor.

This document is the **host integration contract** for the remote mode
(REQ-I-007, REQ-I-013; `ARCHITECTURE.md §7`, §22 / OQ-004).

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

The exposed module **consumes** a host-provided router: it renders the lazy
`<AppRoutes/>` **inside the host's router context** and never creates a
`BrowserRouter` of its own. It owns no shell chrome (no nav, no `PersonaSwitcher`,
no `ThemeToggle`).

### The host MUST provide, before mount (load-bearing — OQ-004)

1. **A Redux `<Provider>` (store) wrapping the remote — required _before_ mount.**
   The route tree gates eagerly: `AppRoutes → useIsManager → useCurrentUser →`
   the `me` RTK Query slice, which dispatches against the store on first render.
   Without a host-provided store the gating query cannot run. The remote does
   **not** create its own store in remote mode (the standalone shell supplies one
   only for standalone). The store must register `baseApi` (reducer + middleware).

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
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';

// hostStore must register wc-web's baseApi; the host owns the router, the
// remote consumes it. getAccessToken is required only in auth0 mode.
<Provider store={hostStore}>
  <BrowserRouter>
    <WeeklyCommitApp getAccessToken={host.getAccessToken} />
  </BrowserRouter>
</Provider>;
```

### Shared singletons (federation `shared`, from `vite.config.ts`)

| Package            | `requiredVersion` |
| ------------------ | ----------------- |
| `react`            | `^18.3.1`         |
| `react-dom`        | `^18.3.1`         |
| `@reduxjs/toolkit` | `^2.3.0`          |
| `react-redux`      | `^9.1.2`          |

`@originjs/vite-plugin-federation` **dedups** each shared dependency into one
version-matched shared chunk — it is **not** webpack-style `singleton`
enforcement (that key isn't in its typed API). The **single React instance + single
store** guarantee is therefore a **host + remote shared-scope agreement** (this
contract), not something the plugin enforces alone. The host must place these four
packages in the shared scope at compatible versions.

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

| Concern     | Standalone (`src/standalone/StandaloneShell.tsx`) | Remote (`src/remote/WeeklyCommitApp.tsx`) |
| ----------- | ------------------------------------------------- | ----------------------------------------- |
| Router      | owns `BrowserRouter`                              | **consumes** the host's                   |
| Redux store | owns `Provider` + `store`                         | **consumes** the host's                   |
| Theme       | owns `ThemeProvider` + `ThemeToggle`              | host-owned                                |
| Identity    | `DemoIdentityProvider` + `PersonaSwitcher` (demo) | host injects `getAccessToken`             |
| Mounts      | `<WeeklyCommitApp/>`                              | exposes itself                            |

### Environment variables (host-overridable)

See `.env.example`. The two build-time vars the host overrides:

- `VITE_AUTH_MODE` — `demo` (standalone) **xor** `auth0` (hosted).
- `VITE_API_BASE_URL` — wc-api base URL; required in production builds.

---

This contract is anti-drift–guarded by `src/remote/host-integration.test.ts`
(it pins the documented federation name / entry / exposed module / shared
singletons against `vite.config.ts`, and the `getAccessToken` signature against
`src/app/authAccessor.ts`).
