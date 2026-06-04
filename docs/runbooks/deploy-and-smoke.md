# Runbook (c) — Deploy + populate secrets + smoke

> **Runbook set:** (a) `auth0-tenant-setup.md` → (b) `fresh-aws-account-to-deploy-ready.md` → **(c) this file**. Precondition: (a) the Auth0 tenant exists (API + SPA + 7 users + post-login Action), and (b) the infra is applied + the GitHub `production` Environment + variables are set (deploy-ready). This runbook **populates the secret values + runs the pipeline + smokes** the deployed stack.
>
> **What's automated vs. you:** the pipeline (`.github/workflows/deploy.yml`) does build → push → `terraform apply` (idempotent) → migrate → rebuild-projections → roll → publish-SPA → smoke, all under OIDC + the reviewer gate. **You** do exactly two manual things: **populate ~2 secret values** (Step 1) and **trigger + approve** the run (Step 2).

## Step 1 — Populate the secret VALUES (post-apply, before the roll)
The `terraform apply` (runbook b) created the secret **containers**; the `db` secret is **TF-auto-populated** from RDS and `demo` is a **prod no-op**. You fill **`auth0`** (Wave 1) and, for Wave 2, **`graph`** — using the no-leak helper (never echoes a value; writes a new secret version; values via env-or-prompt, never a CLI arg/file). Run from the repo root with your AWS creds + region configured:

```bash
# Wave 1 — Auth0 (required for real OAuth):
export AUTH0_ISSUER_URI="https://<tenant>.<region>.auth0.com/"   # trailing slash (the token 'iss')
export ROOT_DOMAIN="<ROOT_DOMAIN>"                                # audience derives: https://api.wc.$ROOT_DOMAIN
infra/scripts/populate-secrets.sh --only auth0
#   → "ok  wc/aws/auth0 — VersionId=…"  (only the NAME + VersionId is printed; never a value)

# Wave 2 — MS Graph (only when wiring live Outlook sync):
export GRAPH_TENANT_ID="<directory-tenant-id>" GRAPH_CLIENT_ID="<application-client-id>"
infra/scripts/populate-secrets.sh --only graph                   # prompts for GRAPH_CLIENT_SECRET (no echo)
```
- `--dry-run` previews the plan (keys only, values `***`, zero AWS calls); `--help` documents every input + provenance.
- **Timing:** populate **before** triggering the pipeline so the first pod roll mounts the real values. If the pods are already running when you (re)populate, **roll them** to remount: `kubectl rollout restart deployment/wc-api deployment/wc-worker -n wc` (the CSI volume is read at pod start). The api otherwise boots with the placeholder issuer-uri and JWT validation fails until the real value is mounted.

## Step 2 — Trigger the deploy pipeline + approve the gate
The workflow triggers on **`workflow_dispatch`** or a **`release-*`** tag push. Every AWS-touching step runs in one `environment: production` job gated by your required reviewer.

```bash
# Option A — manual dispatch:
gh workflow run deploy.yml --repo <owner>/<repo>

# Option B — release tag:
git tag release-$(date +%Y%m%d-%H%M) && git push origin --tags
```
Then **approve** the pending `production` Environment deployment (GitHub UI: the run's "Review deployments" → Approve, or `gh`). Watch the run: `gh run watch --repo <owner>/<repo>`.

## Step 3 — What the pipeline does (the ordered chain)
`gates` (no AWS — Spotless/test/JaCoCo/SpotBugs · ESLint/Prettier/Vitest · Cypress E2E) → **`deploy`** (one reviewer-gated `environment: production` job):
1. OIDC assume the CI deploy role → ECR login.
2. **Build + push** `wc-api` + `wc-sync-worker` images (SHA-tagged; the cronjob/migration/rebuild reuse the api image — no third image).
3. **`terraform apply`** (idempotent — infra exists from runbook b; installs/confirms the cluster add-ons before any kubectl).
4. `update-kubeconfig` → **render** the manifests (allowlisted `envsubst` of the TF outputs into `rendered/`).
5. Apply namespace + ServiceAccounts + SecretProviderClasses → **migration Job** + fail-fast wait (the sole schema owner; seeds V5/V6).
6. **rebuild-projections Job** + fail-fast wait (populates `manager_plan_summary`/`manager_heatmap_cell` from the V6 source — so the command-center/heatmap show data).
7. **Roll** api/worker Deployments + cronjob + Service + Ingress (`rollout status` gates).
8. **Build + publish wc-web** (Vite SPA → S3 sync → CloudFront invalidation).
9. **Deployed smoke** (in-pipeline; Step 4 below).

## Step 4 — Smoke verification
**Automated (in-pipeline, REQ-E-006):** the final step curls, over ACM TLS on the custom domains:
- `https://api.wc.<ROOT_DOMAIN>/actuator/health/readiness` → 200 (the api is up + DB-validated).
- `https://wc.<ROOT_DOMAIN>` → 200 (the SPA serves from CloudFront).

A green run means the deploy succeeded. **Then do the manual functional smoke** (the real demo path):
1. Open `https://wc.<ROOT_DOMAIN>` → redirected to Auth0 login. Sign in as **Dana** (`dana.okafor@st6demo.com`).
2. The SPA lands on the IC workspace; `GET /api/me` resolves to Dana (her seeded identity via the `employee_id` claim).
3. Open the **manager command-center** → it lists Dana's **6 direct reports** with alignment data, and the **heatmap** shows cells (populated by the rebuild Job in Step 3.6 — if the heatmap is empty, the rebuild Job didn't run/seed; see Troubleshooting).
4. Log out → sign in as an IC (e.g. **Priya**) → she sees only her own plan; the manager surfaces are denied (403). Persona-switching = logging in as different seeded users.

## Step 5 — Troubleshooting
| Symptom | Likely cause | Fix |
|---|---|---|
| 401 on every API call | auth0 secret still placeholder, or pods rolled before populate | populate-secrets `--only auth0` → `kubectl rollout restart deployment/wc-api -n wc` |
| api readiness never 200 | DB validate fail / migration didn't run | `kubectl logs job/wc-migration -n wc`; check the `db` secret + RDS reachability |
| command-center/heatmap empty | rebuild-projections Job didn't complete | `kubectl logs job/wc-rebuild-projections -n wc`; confirm V6 seeded (it rides the migration Job) |
| worker pod CrashLoopBackOff | the pre-092 worker-boot issue (JPA autoconfig w/ no datasource) | ensure the 092 worker-boot fix is deployed (Wave-1 completion item); worker is Wave-2 anyway |
| ACM cert stuck pending / TLS errors | Route53 zone not delegated before apply | verify `dig NS <ROOT_DOMAIN>`; re-run apply after NS propagates (runbook b Step 2) |
| SPA login has no Auth0 domain/client (login throws on boot) | the `AUTH0_DOMAIN`/`AUTH0_CLIENT_ID` GitHub vars are unset → the pipeline bakes `VITE_AUTH0_*` as `undefined` | set both Environment vars (runbook b Step 4) + re-run the pipeline so the wc-web build picks them up |
| deploy job denied at AWS | OIDC subject mismatch | the job must run under `environment: production`; the `AWS_DEPLOY_ROLE_ARN`/repo must match the trust subject |

## Re-deploys
Re-running the pipeline is safe + idempotent: `terraform apply` is a no-op when infra is unchanged; the migration + rebuild Jobs are delete-then-apply + idempotent; pod rolls are rolling updates. New app code = push `release-*` or dispatch again.

---
← Back to runbook **(a)** (tenants) / **(b)** (deploy-ready). Topology + local dev: `infra/README.md`.
