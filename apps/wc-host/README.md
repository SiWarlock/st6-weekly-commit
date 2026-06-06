# `wc-host` — Module Federation HOST shell ("Acme Portal")

A minimal but real Vite + React 18 + TypeScript **host** app that proves `wc-web`
is a Module Federation **remote**: it loads the `wc_web` remote (`remoteEntry.js`)
and mounts the exposed `./WeeklyCommitApp` inside a parent "Acme Portal" chrome
(topbar + left nav + content panel), gated behind a real Auth0 login against the
**same tenant** as the deployed app.

```
┌──────────────────────────────────────── Acme Portal (host, :5273) ──────────┐
│ ▲ Acme Portal      Employee Workspace        plugin: wc_web (remote)  [user] │
├───────────────┬──────────────────────────────────────────────────────────── │
│ Workspace     │  Weekly Commit   [Embedded micro-frontend]                   │
│  Dashboard    │ ┌──────────────────────────────────────────────────────────┐│
│  Directory    │ │                                                          ││
│ ▎Weekly Commit│ │   ← the wc_web REMOTE renders here (remoteEntry.js)       ││
│  Reports      │ │     consumes the host's <BrowserRouter> + Redux store +   ││
│ Admin         │ │     getAccessToken; calls the LIVE API.                   ││
│  Billing      │ │                                                          ││
│  Settings     │ └──────────────────────────────────────────────────────────┘│
└───────────────┴───────────────────────────────────────────────────────────┘
```

## What the host provides to the remote (the §7 host-integration contract)

The remote (`apps/wc-web/src/remote/WeeklyCommitApp.tsx`) **consumes**, never owns:

1. **A Redux `<Provider>` store** — and it must be the **same** store instance the
   remote's components dispatch against (its hooks bind to the remote container's
   `baseApi` singleton). A host-built store from a separately-imported `baseApi`
   would be a _second_ RTK Query instance → queries never resolve. So the host
   imports the store **from the remote**: `import('wc_web/store')` (see "wc-web
   changes" below). Wrapped as `<Provider store={store}>` in
   `src/portal/WeeklyCommitMount.tsx`.
2. **A router** — the host's `<BrowserRouter>` (in `src/App.tsx`). The remote
   renders `<AppRoutes/>`'s `<Routes>`/`useNavigate` inside it.
3. **`getAccessToken(): Promise<string>`** — passed as the `getAccessToken` prop;
   the remote registers it into its auth seam so `prepareHeaders` sends
   `Authorization: Bearer <jwt>` (auth0 mode). Built from `@auth0/auth0-react`'s
   `getAccessTokenSilently({ authorizationParams: { audience }})`.

## Two changes this required in `apps/wc-web/vite.config.ts`

The existing federation surface exposed only `./WeeklyCommitApp` and shared 4
singletons — not enough for a real host. Both changes are additive and test-safe
(all 310 wc-web Vitest pass; `boundary.test.ts` + `host-integration.test.ts` green):

1. **Exposed `./store`** — so the host gets the remote's _exact_ configured store
   (req #1 above). `store.ts` is demo-free (`baseApi` only), so no REQ-I-008 surface.
2. **Shared `react-router-dom@^6.28.0`** — React Router's context is
   module-identity-based; host + remote must resolve to **one** `react-router-dom`
   instance or the remote's route hooks read a different context than the host's
   `<BrowserRouter>` provides ("useRoutes() may be used only in the context of a
   `<Router>`").

> The wc-web host-integration README (`apps/wc-web/README.md`) documents the
> federation surface; it should list the `./store` expose + the `react-router-dom`
> shared singleton too (anti-drift). Flagged for the orchestrator/lead.

## Shared scope (must match the remote EXACTLY — single instance)

| Package            | requiredVersion |
| ------------------ | --------------- |
| `react`            | `^18.3.1`       |
| `react-dom`        | `^18.3.1`       |
| `@reduxjs/toolkit` | `^2.3.0`        |
| `react-redux`      | `^9.1.2`        |
| `react-router-dom` | `^6.28.0`       |

Version drift here is the classic federation footgun: two React copies →
"invalid hook call".

## Run it locally (end-to-end)

From the repo root (Yarn workspaces; `apps/wc-host` is a workspace member):

```bash
yarn install                         # once — wires wc-host into the workspace

# 1) Build the REMOTE with the live-API + auth0 env baked in (apps/wc-web):
cd apps/wc-web
VITE_AUTH_MODE=auth0 VITE_API_BASE_URL=https://api.wc.st6weeklycommit.com \
  yarn build:remote
#   → dist/assets/remoteEntry.js
#   → dist/assets/__federation_expose_WeeklyCommitApp-*.js
#   → dist/assets/__federation_expose_Store-*.js
#   → dist/assets/__federation_shared_*.js  (react, react-dom, react-redux,
#                                            @reduxjs/toolkit, react-router-dom)
# NOTE: VITE_AUTH_MODE + VITE_API_BASE_URL bake into the REMOTE here (not the host).

# 2) Serve the remote dist with CORS (vite preview echoes the request Origin):
yarn vite preview --port 5274 --strictPort        # http://localhost:5274/assets/remoteEntry.js

# 3) Build + serve the HOST (apps/wc-host). .env.local supplies the Auth0 vars +
#    VITE_WC_REMOTE_URL (read by vite.config via loadEnv):
cd ../wc-host
cp .env.example .env.local           # then confirm VITE_WC_REMOTE_URL=http://localhost:5274/assets/remoteEntry.js
yarn vite build
yarn vite preview --port 5273 --strictPort        # http://localhost:5273

# dev mode also works: `yarn dev` (consumes the built remote on :5274).
```

Open **http://localhost:5273** → Acme Portal → "Sign in with Auth0" → the remote
mounts in the panel.

> Ports 5173/5174 (the "canonical" Vite ports) were occupied on this machine, so
> the verified run used **5273 (host) / 5274 (remote)**. Any free pair works —
> just keep `VITE_WC_REMOTE_URL` and the Auth0 callback origin in sync with the
> host port.

## What the USER / infra must configure for it to fully work

### Auth0 (SPA client `psB6r4UN0AgRLXLxmp974lieHgOvN2sI`, tenant `dev-pw0um8pz8cgr31eo.us.auth0.com`)

Add the **host origin** to the SPA application's allow-lists (the deployed SPA
already has `https://wc.st6weeklycommit.com`):

| Setting               | Local dev                        | Proposed deployed host                        |
| --------------------- | -------------------------------- | --------------------------------------------- |
| Allowed Callback URLs | `http://localhost:5273/callback` | `https://portal.st6weeklycommit.com/callback` |
| Allowed Web Origins   | `http://localhost:5273`          | `https://portal.st6weeklycommit.com`          |
| Allowed Logout URLs   | `http://localhost:5273`          | `https://portal.st6weeklycommit.com`          |

### CORS — two distinct requirements

1. **Remote chunks** (`remoteEntry.js` + assets) must be served with
   `Access-Control-Allow-Origin: <host origin>` (the host fetches them
   cross-origin). `vite preview` does this locally; the deployed remote's
   S3/CloudFront must add it for the host origin.
2. **The wc-api backend** must allow the **host origin** in its CORS allow-list,
   because the embedded remote calls `https://api.wc.st6weeklycommit.com`
   cross-origin. **As of now wc-api returns 403 on a preflight from
   `http://localhost:5273`** (only `https://wc.st6weeklycommit.com` is allowed).
   So the live-API calls are blocked from a localhost host until either:
   - `http://localhost:5273` is added to wc-api's CORS allow-list (backend
     redeploy), **or**
   - the host is deployed to an allowed origin (e.g. `portal.st6weeklycommit.com`,
     added to wc-api CORS), **or**
   - wc-api is run locally with a permissive CORS origin.

## Files

```
apps/wc-host/
  vite.config.ts            # federation host: remotes{wc_web}, shared scope, esnext
  index.html
  src/
    main.tsx                # createRoot → <App/>
    App.tsx                 # <BrowserRouter> → Auth0Provider (navigate-aware callback)
    auth/authConfig.ts      # resolve VITE_AUTH0_* (fail-fast)
    portal/
      Portal.tsx            # Acme Portal chrome + auth gate (login → mount)
      WeeklyCommitMount.tsx # import('wc_web/store') → <Provider> → lazy remote
      portal.css            # host chrome styling (host-only; remote owns its own)
    types/remotes.d.ts      # types for wc_web/WeeklyCommitApp + wc_web/store
  .env.example / .env.local # VITE_WC_REMOTE_URL + VITE_AUTH0_* (live tenant)
```
