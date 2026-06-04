# Runbook — Auth0 tenant setup for the deployed demo (HITL)

> **Audience:** the user, standing up the Auth0 tenant for the **deployed-backend demo** (real wc-api + RDS Postgres serving the React SPA, real OAuth2 — NOT the `X-Demo-Employee-Id` demo header). Execute these steps in the Auth0 dashboard + your deploy env once the **V5 seed (brief 084)** has landed (it defines the `external_subject` + email values you map to).
>
> **Why:** the backend OAuth path is already built (Phase 2: OAuth2 resource-server JWT validation + the `Auth0ClaimMapper`→`PrincipalResolver.findByExternalSubject` chain). A validated Auth0 JWT resolves to a **seeded employee** by `Employee.external_subject`, which the token carries as the **`employee_id` claim** (`https://wc.<ROOT_DOMAIN>/employee_id`). This runbook configures Auth0 to emit that claim + creates the matching test users. No backend code changes.

## Prerequisites
- An Auth0 tenant (free tier is fine).
- `ROOT_DOMAIN` chosen (e.g. `example.com`) — used in the audience + the namespaced claim URIs. Must match the backend's `ROOT_DOMAIN` env.
- The **V5-seeded values** (from `V5__seed_personas_and_relationships.sql`, brief 084): each employee's `email` (`@st6demo.com`) + `external_subject` literal (e.g. `st6|dana-okafor`). _(Fill the table in step 4 from the landed V5 migration.)_

## 1 — Create the Auth0 API (the audience)
1. Auth0 Dashboard → **Applications → APIs → Create API**.
2. **Identifier (Audience):** `https://api.wc.<ROOT_DOMAIN>` (must equal the backend's `AUTH0_AUDIENCE`).
3. **Signing algorithm:** RS256 (the backend's `JwtDecoder` pins RS256).
4. Save. (No scopes/permissions needed — authorization is relationship-driven server-side, not scope-driven.)

## 2 — Create the SPA Application (the frontend client)
1. **Applications → Applications → Create Application** → type **Single Page Application**.
2. **Allowed Callback URLs / Logout URLs / Web Origins:** the deployed SPA origin (e.g. `https://wc.<ROOT_DOMAIN>`) — and `http://localhost:5173` if you also run the SPA locally against the deployed API.
3. Note the **Domain** + **Client ID** → the frontend's Auth0 SDK config (`domain`, `clientId`, `audience = https://api.wc.<ROOT_DOMAIN>`). _(Frontend wiring is the web-orch's slice — the SPA login → access token → `Bearer` header; the `getAccessToken` host seam already exists, §22.)_

## 3 — Set the deployed backend env vars
The wc-api real-mode config is env-driven (no code change). In the deploy env (k8s Secrets / env):
| Env var | Value |
|---|---|
| `AUTH0_ISSUER_URI` | `https://<tenant>.auth0.com/` (note the trailing slash; the issuer in the token) |
| `AUTH0_AUDIENCE` | `https://api.wc.<ROOT_DOMAIN>` (from step 1) |
| `ROOT_DOMAIN` | `<ROOT_DOMAIN>` (must match the claim URIs) |
| `CORS_ALLOWED_ORIGINS` | `https://wc.<ROOT_DOMAIN>` (the deployed SPA origin; exact, no wildcard) |
| `DEMO_AUTH_ENABLED` | **unset / `false`** — the deployed demo uses REAL OAuth, NOT the demo backdoor (safety rule #5). |
| `SPRING_PROFILES_ACTIVE` | `prod` (real-mode: real OAuth; flyway off — the Migration Job owns the schema). |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | the RDS Postgres connection (via Secrets). _(The datasource deploy config is a separate small backend slice.)_

## 4 — Create the 7 test users (matching the seed)
For **each** seeded employee, create an Auth0 **Database** user (or social, as you prefer) with the **exact seeded email**, and set `app_metadata.employee_id` = the seeded **`external_subject`** literal:

| Persona | Email (seeded) | `app_metadata.employee_id` = `external_subject` (seeded) | Role |
|---|---|---|---|
| Dana Okafor (manager) | `dana.okafor@st6demo.com` _(confirm from V5)_ | `st6|dana-okafor` _(confirm from V5)_ | MANAGER |
| Report 1 | `<report-01>@st6demo.com` | `st6|report-01` | IC |
| Report 2 | … | … | IC |
| … (reports 3-6) | … | … | IC |

> Set a known password for each (you'll log in as them to switch personas in the demo). Fill the exact emails + `external_subject` values from the landed `V5__seed_personas_and_relationships.sql`.

## 5 — Add the post-login Action (emit the `employee_id` claim)
This is the load-bearing step — it puts the seeded identity into the token so the backend resolves it to the seeded employee.
1. **Actions → Library → Build Custom** (trigger: **Login / Post Login**).
2. Code:
   ```js
   exports.onExecutePostLogin = async (event, api) => {
     const ns = `https://wc.${event.secrets.ROOT_DOMAIN}`;       // or hardcode your ROOT_DOMAIN
     const employeeId = event.user.app_metadata?.employee_id;     // = the seeded external_subject
     if (employeeId) {
       api.accessToken.setCustomClaim(`${ns}/employee_id`, employeeId);
       // optional coarse role hint (authoritative role is the Employee row server-side):
       if (event.user.app_metadata?.role) {
         api.accessToken.setCustomClaim(`${ns}/role`, event.user.app_metadata.role);
       }
     }
   };
   ```
   _(Add `ROOT_DOMAIN` as an Action Secret, or hardcode the namespace.)_
3. **Deploy** the Action → **Actions → Flows → Login** → drag it into the flow → **Apply**.

## 6 — Verify
1. Log in to the SPA as **Dana**. Decode the **access token** (jwt.io) → confirm it carries `https://wc.<ROOT_DOMAIN>/employee_id` = Dana's `external_subject` + `aud` = `https://api.wc.<ROOT_DOMAIN>` + `iss` = your tenant.
2. Hit a backend endpoint (e.g. `GET /api/me`) with that `Bearer` token → it should resolve to Dana (200, her identity). A token WITHOUT the `employee_id` claim (or with an unseeded value) → **401** (IDOR-safe; the resolver finds no Employee).
3. Switch to a report user → the manager command-center denies them (IC has no team surface, 403) and shows their own plan.

## Notes
- **The demo runs wc-api + Postgres only** — no worker, no real SNS/SQS/Graph (the no-op `LoggingLifecycleSnsGateway` + the demo's non-blocking sync, rule #4). No AWS messaging creds needed.
- **`DEMO_AUTH_ENABLED` stays off in the deployed demo** — real OAuth is the path; the demo-header backdoor is dev/local only (safety rule #5).
- This runbook's concrete `email` / `external_subject` values are filled from the landed **V5 (brief 084)** seed — confirm them against `V5__seed_personas_and_relationships.sql` before creating the Auth0 users.
