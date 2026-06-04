# Runbook (a) — Setup Everything: external tenants (Auth0 + M365/Graph)

*This runbook covers **BOTH** external-tenant setups: **Auth0** (Wave 1, real OAuth — Part 1) and **Microsoft 365 / Entra Graph** (Wave 2, live Outlook sync — Part 2). The filename stays `auth0-tenant-setup.md` to preserve inbound references; the scope is broader than the name.*

> **Runbook set:** **(a) this file — external-tenant setup** → **(b)** `fresh-aws-account-to-deploy-ready.md` (AWS bootstrap → deploy-ready) → **(c)** `deploy-and-smoke.md` (run the pipeline + populate secrets + smoke). Start here: these external tenants produce the ~3 secret VALUES the deploy needs.
>
> **Audience:** the operator standing up the **deployed-backend demo** (real wc-api + RDS Postgres serving the React SPA, **real Auth0/OAuth2** — NOT the `X-Demo-Employee-Id` header). Two waves: **Wave 1** = deployable backend + real OAuth (Auth0 only — **Part 1**). **Wave 2** = live Outlook/Graph sync (the M365/Entra creds — **Part 2**; produce them now if convenient, but they're only consumed in Wave 2).
>
> **Why no backend code changes:** the OAuth path is already built (Phase 2: OAuth2 resource-server JWT validation + the `Auth0ClaimMapper` → `PrincipalResolver.findByExternalSubject` chain). A validated Auth0 JWT resolves to a **seeded employee** by `Employee.external_subject`, carried in the token's **`employee_id` claim** (`https://wc.<ROOT_DOMAIN>/employee_id`). This runbook configures Auth0 to emit that claim + creates the matching test users. The V5/V6 demo seed (already landed) defines the exact identity literals used below.

## Prerequisites
- An **Auth0 tenant** (free tier is fine).
- **`ROOT_DOMAIN`** chosen (e.g. `example.com`) — used in the audience + the namespaced claim URIs + the SPA origin. **Must match** the backend's `ROOT_DOMAIN` (set as a Terraform/k8s env at deploy; see runbook (b)).
- For **Part 2 (Wave 2)** only: a **Microsoft 365 E5** tenant (1-month trial is fine) with global-admin rights to register an app + grant admin consent.
- The deployed secret containers already exist after `terraform apply` (runbook (b)); the **values** below are written post-apply via `infra/scripts/populate-secrets.sh` (runbook (c)).

---

# Part 1 — Auth0 (Wave 1, required for the real-OAuth demo)

## 1.1 — Create the Auth0 API (the audience)
1. Auth0 Dashboard → **Applications → APIs → Create API**.
2. **Identifier (Audience):** `https://api.wc.<ROOT_DOMAIN>` — **must equal** the backend's `auth0.audience` (the custom `AudienceValidator` checks it; Auth0 does not validate `aud` by default).
3. **Signing algorithm:** **RS256** (the backend's `JwtDecoder` pins RS256).
4. Save. No scopes/permissions needed — authorization is relationship-driven server-side (§6), not scope-driven.

## 1.2 — Create the SPA Application (the frontend client)
1. **Applications → Applications → Create Application** → type **Single Page Application**.
2. **Allowed Callback URLs:** `https://wc.<ROOT_DOMAIN>/callback` (+ `http://localhost:5173/callback` if you also run the SPA locally against the deployed API).
3. **Allowed Logout URLs / Allowed Web Origins:** `https://wc.<ROOT_DOMAIN>` (+ `http://localhost:5173` for local).
4. Note the **Domain** (`<tenant>.<region>.auth0.com`) + **Client ID** → the frontend's Auth0 SDK config (`domain`, `clientId`, `audience = https://api.wc.<ROOT_DOMAIN>`). The SPA requests the access token for that audience via `getAccessTokenSilently` and sends it as the `Bearer` header. _(Frontend wiring is the web-orch's slice; the `getAccessToken` host seam already exists, §22.)_
   - **→ Where these go:** the **Domain** + **Client ID** you just noted become the GitHub Environment vars **`AUTH0_DOMAIN`** + **`AUTH0_CLIENT_ID`** (runbook (b) Step 4). The deploy pipeline bakes them into the SPA build as **`VITE_AUTH0_DOMAIN`** / **`VITE_AUTH0_CLIENT_ID`** / **`VITE_AUTH0_AUDIENCE`** (the audience is derived from `ROOT_DOMAIN` = `https://api.wc.<ROOT_DOMAIN>`). The SPA's `auth0Config.ts` **fail-fasts** without all three, so an unset var = a deployed login that won't boot. Full SPA build/deploy reference: **`apps/wc-web/README.md` → "Deploying to AWS"** + `apps/wc-web/.env.example`.

## 1.3 — Create the 7 test users (matching the V5 seed)
For **each** seeded employee, create an Auth0 **Database** user with the **exact seeded email**, and set **`app_metadata.employee_id`** = the seeded **`external_subject`** literal. These values are the landed **`V5__seed_personas_and_relationships.sql`** — use them verbatim:

| Persona | Email | `app_metadata.employee_id` (= `external_subject`) | Role |
|---|---|---|---|
| **Dana Okafor** (manager) | `dana.okafor@st6demo.com` | `st6\|dana-okafor` | MANAGER |
| Priya Raman | `priya.raman@st6demo.com` | `st6\|priya-raman` | IC |
| Marco Bellini | `marco.bellini@st6demo.com` | `st6\|marco-bellini` | IC |
| Aisha Khan | `aisha.khan@st6demo.com` | `st6\|aisha-khan` | IC |
| Tomas Novak | `tomas.novak@st6demo.com` | `st6\|tomas-novak` | IC |
| Grace Liu | `grace.liu@st6demo.com` | `st6\|grace-liu` | IC |
| Sam Carter | `sam.carter@st6demo.com` | `st6\|sam-carter` | IC |

> The `|` is a literal pipe character in the `external_subject` (e.g. `st6|dana-okafor`). Set a **known password** for each (you log in as them to switch personas in the demo). Dana is the manager (she also owns her own IC plan + manages the 6 reports); the other 6 are her direct reports. Set `app_metadata` under the user's **Details → Metadata → app_metadata** (JSON), e.g. `{ "employee_id": "st6|dana-okafor", "role": "MANAGER" }`.

## 1.4 — Add the post-login Action (emit the `employee_id` claim)
**The load-bearing step** — it puts the seeded identity into the token so the backend resolves it to the seeded employee.
1. **Actions → Library → Build Custom** (trigger: **Login / Post Login**).
2. Code (the namespace **must** be `https://wc.<ROOT_DOMAIN>` so the claim key matches the backend's configured `auth0.claims.employee-id`):
   ```js
   exports.onExecutePostLogin = async (event, api) => {
     const ns = `https://wc.${event.secrets.ROOT_DOMAIN}`;        // or hardcode your ROOT_DOMAIN
     const employeeId = event.user.app_metadata?.employee_id;      // = the seeded external_subject
     if (employeeId) {
       api.accessToken.setCustomClaim(`${ns}/employee_id`, employeeId);
       // optional coarse role hint (the authoritative role is the Employee row server-side):
       if (event.user.app_metadata?.role) {
         api.accessToken.setCustomClaim(`${ns}/role`, event.user.app_metadata.role);
       }
     }
   };
   ```
   _(Add `ROOT_DOMAIN` as an Action **Secret**, or hardcode the namespace.)_
3. **Deploy** the Action → **Actions → Flows → Login** → drag it into the flow → **Apply**.

## 1.5 — Where the Auth0 values go (the deployed backend reads them from the CSI mount, NOT env)
The deployed wc-api reads `issuer-uri` + `auth0.audience` from the **`wc/<env>/auth0`** Secrets Manager secret, mounted as files via the CSI driver and bound by `spring.config.import=optional:configtree:/mnt/secrets/` (LESSONS §42 — the mount-file name == the Spring property key). So you do **not** set them as k8s env; you put them in the secret post-apply with the helper (runbook (c)):

```bash
# after `terraform apply` (runbook b), from the repo root:
AUTH0_ISSUER_URI="https://<tenant>.<region>.auth0.com/"   # NOTE the trailing slash
ROOT_DOMAIN="<ROOT_DOMAIN>"                                 # audience defaults to https://api.wc.$ROOT_DOMAIN
export AUTH0_ISSUER_URI ROOT_DOMAIN
infra/scripts/populate-secrets.sh --only auth0             # writes the auth0 secret (audience derived)
```
- The `auth0` secret keys are `spring.security.oauth2.resourceserver.jwt.issuer-uri` + `auth0.audience` (the helper sets both; it warns if the issuer-uri lacks the trailing `/` — Auth0 issuers carry it, and the token's `iss` must match exactly).
- The other relevant config is **non-secret deploy env** (set via Terraform/manifests, not this helper): `ROOT_DOMAIN` (envsubst token), `SPRING_PROFILES_ACTIVE=aws`, `DEMO_AUTH_ENABLED` **unset/false** (real OAuth — NOT the demo backdoor, safety rule #5), `CORS_ALLOWED_ORIGINS` defaults to `https://wc.${ROOT_DOMAIN}` (override only for multi-origin). The datasource binds from the TF-populated `db` secret via the same configtree mount.

## 1.6 — Verify (Auth0)
1. Log in to the SPA as **Dana**. Decode the **access token** (jwt.io) → confirm it carries `https://wc.<ROOT_DOMAIN>/employee_id` = `st6|dana-okafor`, `aud` = `https://api.wc.<ROOT_DOMAIN>`, `iss` = `https://<tenant>.<region>.auth0.com/`.
2. Hit a backend endpoint (`GET /api/me`) with that `Bearer` token → resolves to Dana (200, her identity). A token **without** the `employee_id` claim (or an unseeded value) → **401** (IDOR-safe; the resolver finds no Employee).
3. Switch to a report (e.g. Priya) → the manager command-center denies her (IC has no team surface, 403) and shows her own plan; logging in as Dana shows the 6-report command center + heatmap.

---

# Part 2 — Microsoft 365 E5 + Entra app registration (Wave 2: live Outlook/Graph)

> **Wave 2 only.** The deployed demo runs end-to-end on real OAuth with the **no-op SNS sync gateway** (rule #4 — sync never blocks the lifecycle), so Wave 1 needs none of Part 2. Do Part 2 when you wire the live Outlook calendar sync (the worker's MS Graph adapter, app-only/client-credentials). It produces the **`GRAPH_TENANT_ID` / `GRAPH_CLIENT_ID` / `GRAPH_CLIENT_SECRET`** values for the `wc/<env>/graph` secret.

## 2.1 — Sign up for a Microsoft 365 E5 tenant (1-month trial)
1. Get an **M365 E5** trial (e.g. the Microsoft 365 Developer Program, which provisions a sandbox E5 tenant, or a paid E5 1-month trial). This gives you a tenant with Exchange Online mailboxes (the calendars the sync writes to).
2. Note the **tenant primary domain** (`<something>.onmicrosoft.com`) and sign in to the **Microsoft Entra admin center** (`entra.microsoft.com`) as a **global admin**.
3. _(Optional, for a richer demo)_ create/seed a few user mailboxes whose calendars the demo will write commitments to. The seeded `@st6demo.com` personas are app identities for OAuth; mapping them to real M365 mailboxes is a Wave-2 demo-data decision (out of scope here).

## 2.2 — Register the Entra application (app-only / client-credentials)
1. Entra admin center → **Identity → Applications → App registrations → New registration**.
2. **Name:** e.g. `wc-graph-sync`. **Supported account types:** *Accounts in this organizational directory only* (single-tenant). **Redirect URI:** leave blank — this is a **daemon / app-only** app (client-credentials flow; no interactive sign-in, no redirect).
3. Register. From the app's **Overview**, copy:
   - **Application (client) ID** → `GRAPH_CLIENT_ID`
   - **Directory (tenant) ID** → `GRAPH_TENANT_ID`

## 2.3 — Grant the Graph **application** permission + admin consent
1. App → **API permissions → Add a permission → Microsoft Graph → Application permissions** (NOT delegated — app-only has no signed-in user).
2. Add **`Calendars.ReadWrite`** (create/update calendar events as the application).
3. Click **Grant admin consent for <tenant>** → the permission's status must show **Granted** (a green check). Without admin consent, the client-credentials token is issued but Graph calls return `403`.

## 2.4 — Create a client secret
1. App → **Certificates & secrets → Client secrets → New client secret**. Set a description + expiry (≤ your demo window).
2. **Copy the secret VALUE immediately** (it's shown once) → `GRAPH_CLIENT_SECRET`. _(This is the one true secret of the three — handle it like a password.)_

> **Token shape (for reference; the worker's Graph adapter does this):** app-only access token via client-credentials against `https://login.microsoftonline.com/<GRAPH_TENANT_ID>/oauth2/v2.0/token` with `scope=https://graph.microsoft.com/.default`, `client_id=<GRAPH_CLIENT_ID>`, `client_secret=<GRAPH_CLIENT_SECRET>`, `grant_type=client_credentials`. The returned bearer token authorizes `POST/PATCH /users/<id>/events`.

## 2.5 — Where the Graph values go (the `graph` secret, mounted by the worker)
The worker reads `GRAPH_*` from the **`wc/<env>/graph`** secret, mounted **graph-only** via the `wc-worker-secrets` SecretProviderClass (the worker's IRSA can read only the graph secret — least privilege). Populate it post-apply with the same helper (runbook (c)):

```bash
GRAPH_TENANT_ID="<directory-tenant-id>"
GRAPH_CLIENT_ID="<application-client-id>"
export GRAPH_TENANT_ID GRAPH_CLIENT_ID            # GRAPH_CLIENT_SECRET is prompted silently (read -s)
infra/scripts/populate-secrets.sh --only graph    # prompts for the client secret with no echo
```
- The `graph` secret keys are exactly `GRAPH_TENANT_ID` / `GRAPH_CLIENT_ID` / `GRAPH_CLIENT_SECRET` (the helper matches the `secretproviderclass.yaml` jmesPath keys — a mismatch would break the mount).
- _(Wave-2 hook:_ both deployments carry a `GRAPH_MODE` env — a stub-vs-live switch for the Graph adapter. Confirm its exact semantics in the worker code when you wire Wave 2; in Wave 1 the no-op gateway means Graph is never called.)_

---

## Notes / safety
- **`DEMO_AUTH_ENABLED` stays off in the deployed demo** — real OAuth is the path; the `X-Demo-Employee-Id` backdoor is dev/local only (safety rule #5).
- **No PII / secrets in logs** — the `populate-secrets.sh` helper never echoes a value (rule #7); the access token's claims are the only identity surface.
- **The Wave-1 demo runs wc-api + Postgres only** — no worker, no real SNS/SQS/Graph (the no-op `LoggingLifecycleSnsGateway`); no Graph creds needed until Wave 2.
- **Secret rotation:** re-running `populate-secrets.sh` writes a new secret version (idempotent); rolling the pods picks up the new mount.

## Next
→ Runbook **(b)** `fresh-aws-account-to-deploy-ready.md` — bootstrap the AWS account + state + OIDC + domain so `terraform apply` can run.
→ Runbook **(c)** `deploy-and-smoke.md` — run the deploy pipeline, populate these secrets, and smoke the deployed stack.
