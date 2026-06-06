# `wc-host` — Module Federation HOST shell ("Acme Portal")

A minimal but real Vite + React 18 + TypeScript **host** app that proves `wc-web`
is a Module Federation **remote**: it loads the `wc_web` remote (`remoteEntry.js`)
and mounts the exposed `./WeeklyCommitApp` inside a parent "Acme Portal" chrome
(topbar + left nav + content panel), gated behind a real Auth0 login against the
**same tenant** as the deployed app.

```
┌──────────────────────────── Acme Portal (host) ─────────────────────────────┐
│ ▲ Acme Portal      Employee Workspace        plugin: wc_web (remote)  [user] │
├───────────────┬──────────────────────────────────────────────────────────── │
│ Workspace     │  Weekly Commit   [Embedded micro-frontend]                   │
│  Dashboard    │ ┌──────────────────────────────────────────────────────────┐│
│  Directory    │ │   ← the wc_web REMOTE renders here (remoteEntry.js)       ││
│ ▎Weekly Commit│ │     consumes the host's <BrowserRouter> + getAccessToken; ││
│  Reports      │ │     self-provides its Redux store; calls the LIVE API.    ││
│  Settings     │ └──────────────────────────────────────────────────────────┘│
└───────────────┴───────────────────────────────────────────────────────────┘
```

## Deployment topology (Option B — same-origin)

The portal is served from a **path on the existing wc. CloudFront**, same-origin
with the remote — a new `portal.*` distribution was blocked by an AWS account
verification gate, and same-origin removes all cross-origin CORS:

| Artifact        | URL                                                           | Build base                                      |
| --------------- | ------------------------------------------------------------- | ----------------------------------------------- |
| Standalone demo | `https://wc.st6weeklycommit.com/`                             | `/` (build:standalone)                          |
| MF remote       | `https://wc.st6weeklycommit.com/remote/assets/remoteEntry.js` | `--base=https://wc.st6weeklycommit.com/remote/` |
| **Portal host** | `https://wc.st6weeklycommit.com/portal/`                      | `--base=/portal/`                               |

Same-origin ⇒ **no CORS needed**: the host loads the remote chunks from the same
wc. origin, and the embedded remote's API calls carry `Origin: https://wc.st6weeklycommit.com`
which wc-api already allows (Deploy 1).

## What the host provides to the remote (the §7 host-integration contract)

The host provides exactly **two** things; the remote owns everything else:

1. **A router** — the host's `<BrowserRouter basename="/portal">` (in `src/App.tsx`).
   The remote renders `<AppRoutes/>`'s `<Routes>`/`useNavigate` inside it.
2. **`getAccessToken(): Promise<string>`** — passed as the `getAccessToken` prop;
   the remote registers it into its auth seam so `prepareHeaders` sends
   `Authorization: Bearer <jwt>` (auth0 mode). Built from `@auth0/auth0-react`'s
   `getAccessTokenSilently({ authorizationParams: { audience }})`.

The **Redux store is NOT a host responsibility** — the remote self-provides its own
`<Provider store={store}>` internally. (We tried exposing `./store` and importing it
from the host; its chunk's top-level `await importShared('@reduxjs/toolkit')`
deadlocked the cross-build shared-scope init and hung the host on "Connecting…". The
remote owning its store removes that await.)

## Shared scope (must match the remote EXACTLY — single instance)

Only the singletons that **cross the boundary** are shared:

| Package            | requiredVersion | why shared                                               |
| ------------------ | --------------- | -------------------------------------------------------- |
| `react`            | `^18.3.1`       | one React instance (else "invalid hook call")            |
| `react-dom`        | `^18.3.1`       | one renderer                                             |
| `react-router-dom` | `^6.28.0`       | the host's `<BrowserRouter>` context the remote consumes |

`@reduxjs/toolkit` + `react-redux` are **NOT** shared — the remote self-provides its
store, so they're remote-internal (bundled into the remote). Version drift on the
shared three is the classic federation footgun.

## Run it locally (end-to-end)

From the repo root (Yarn workspaces; `apps/wc-host` is a workspace member):

```bash
yarn install                         # once — wires wc-host into the workspace

# 1) Build the REMOTE (apps/wc-web) with the live-API + auth0 env baked in:
cd apps/wc-web
VITE_AUTH_MODE=auth0 VITE_API_BASE_URL=https://api.wc.st6weeklycommit.com \
VITE_AUTH0_DOMAIN=<tenant> VITE_AUTH0_CLIENT_ID=<spa-id> VITE_AUTH0_AUDIENCE=https://api.wc.st6weeklycommit.com \
  yarn vite build --base=https://wc.st6weeklycommit.com/remote/
#   → dist/assets/remoteEntry.js + __federation_expose_WeeklyCommitApp-*.js
#   → __federation_shared_{react,react-dom,react-router-dom}-*.js  (NO redux — bundled)
# remoteEntry bakes ABSOLUTE chunk URLs at /remote/assets/* so it resolves wherever served.

# 2) Serve the remote dist (vite preview echoes Origin + serves JS):
yarn vite preview --port 5274 --strictPort        # http://localhost:5274/assets/remoteEntry.js

# 3) Build + serve the HOST (apps/wc-host):
cd ../wc-host
cp .env.example .env.local           # VITE_AUTH0_* + VITE_WC_REMOTE_URL
VITE_WC_REMOTE_URL=http://localhost:5274/assets/remoteEntry.js yarn vite build   # local: base '/'
yarn vite preview --port 5273 --strictPort        # http://localhost:5273

# dev mode: `yarn dev` (consumes the built remote).
```

Open the host → "Sign in with Auth0" → the remote mounts in the panel.

> Local dev uses base `/` (host at the server root). The **deployed** host uses
> `--base=/portal/` (served under `wc.st6weeklycommit.com/portal/`); the router
> basename + the Auth0 `redirect_uri` (`/portal/callback`) derive from
> `import.meta.env.BASE_URL`, so the same source builds both.

## What the USER / infra must configure

### Auth0 (SPA client `psB6r4UN0AgRLXLxmp974lieHgOvN2sI`, tenant `dev-pw0um8pz8cgr31eo.us.auth0.com`)

Add the portal's callback + logout to the SPA app (the Web Origin
`https://wc.st6weeklycommit.com` is already allowed from Deploy 1):

| Setting               | Deployed portal                                  |
| --------------------- | ------------------------------------------------ |
| Allowed Callback URLs | `https://wc.st6weeklycommit.com/portal/callback` |
| Allowed Logout URLs   | `https://wc.st6weeklycommit.com/portal/`         |

### Infra (CloudFront on the wc. distribution)

- **`/portal/*` SPA fallback** — a CloudFront Function rewrites extension-less
  `/portal/*` (incl. `/portal/callback`) → `/portal/index.html` (the host), so deep
  links + the OAuth callback don't fall through to the root standalone demo.
- **`--delete` guard** — the root standalone `s3 sync … --delete` must
  `--exclude "remote/*" --exclude "portal/*"` so it doesn't wipe the remote/host.
- No CORS / response-headers policy needed for `/remote/*` (same-origin).

## Files

```
apps/wc-host/
  vite.config.ts            # federation host: remotes{wc_web}, shared{react,react-dom,react-router-dom}, esnext
  index.html
  src/
    main.tsx                # createRoot → <App/>
    App.tsx                 # <BrowserRouter basename=BASE_URL> → Auth0Provider (navigate-aware callback)
    auth/authConfig.ts      # resolve VITE_AUTH0_* (fail-fast); redirectUri = <origin><BASE_URL>callback
    portal/
      Portal.tsx            # Acme Portal chrome + auth gate (login → mount)
      WeeklyCommitMount.tsx # lazy <WeeklyCommitApp getAccessToken/> in <Suspense> (remote self-provides its store)
      portal.css            # host chrome styling (host-only; remote owns its own)
    types/remotes.d.ts      # type for wc_web/WeeklyCommitApp (no ./store — remote owns the store)
  .env.example / .env.local # VITE_WC_REMOTE_URL + VITE_AUTH0_* (live tenant)
```
