# `infra/` — ST6 Weekly Commit Module infrastructure

Infrastructure-as-code for the deployed-backend demo: a **deployed wc-api + RDS Postgres serving the React SPA with real Auth0/OAuth2** on EKS, plus the SNS/SQS + MS Graph sync transport (live in Wave 2). All declarative — Terraform + Kubernetes manifests + a GitHub Actions OIDC pipeline. No long-lived AWS keys anywhere.

> **Canonical contract:** `ARCHITECTURE.md` **§12** (AWS topology) + **§13** (CI/CD + local runtime) + **Appendix C.6/C.7** (file layouts) + **Appendix D** (config/secret contract). This README is the operational map; the architecture doc is binding.

## Layout
```
infra/
├── terraform-bootstrap/     # cold-start root: TF state bucket + DynamoDB lock (run once per AWS account)
├── terraform/               # the main root: VPC/EKS/RDS/ECR/SNS-SQS/S3-CF/Route53-ACM/Secrets/IRSA/CI-role/add-ons
├── k8s/                     # app workload manifests (Deployments/Jobs/CronJob/SAs/SPCs/Service/Ingress)
├── scripts/populate-secrets.sh   # HITL helper: write Auth0/Graph secret VALUES post-apply (no-leak)
├── CLAUDE.md  └ LESSONS.md  # area conventions + banked engineering lessons (§1–§22)
└── README.md               # this file
```
The deploy pipeline lives at `.github/workflows/deploy.yml` (repo root). The runbooks live at `docs/runbooks/`.

## Runbooks (the HITL path — read these to actually deploy)
- **(a)** [`docs/runbooks/auth0-tenant-setup.md`](../docs/runbooks/auth0-tenant-setup.md) — external tenants: Auth0 (API/SPA/7 users/Action) + M365/Entra Graph (Wave 2).
- **(b)** [`docs/runbooks/fresh-aws-account-to-deploy-ready.md`](../docs/runbooks/fresh-aws-account-to-deploy-ready.md) — bootstrap → Route53 delegation → first admin-cred `apply` (creates OIDC+CI role) → GitHub `production` Environment.
- **(c)** [`docs/runbooks/deploy-and-smoke.md`](../docs/runbooks/deploy-and-smoke.md) — populate secrets → run the pipeline → smoke.

---

## Part 1 — AWS topology (the deployed architecture, §12)

```
                       Route53 (ROOT_DOMAIN zone, delegated)  +  ACM (DNS-validated)
                                   │                                    │
              wc.<ROOT_DOMAIN> ────┤                  api.wc.<ROOT_DOMAIN> ──┐
                                   ▼ (A→alias)                              ▼ (external-dns A→alias)
                            CloudFront (OAC, SPA          ALB (AWS Load Balancer Controller, regional ACM cert)
                            fallback → index.html)                          │
                                   │                                        ▼
                            private S3 (Vite assets)            ┌──────────  EKS (managed node group, VPC private subnets)  ──────────┐
                                                                │  wc-api Deployment   wc-sync-worker Deployment   wc-generation CronJob │
                                                                │  wc-migration Job    wc-rebuild-projections Job  (one-shot, on deploy) │
                                                                │        │  each pod: CSI mount /mnt/secrets (per-SA SecretProviderClass) │
                                                                └────────┼─────────────────────────────────────────────────────────────┘
                                                                         │  IRSA (per-workload least privilege)
                          ┌──────────────────────┬───────────────────────┼─────────────────────┬──────────────────────┐
                          ▼                       ▼                       ▼                     ▼                      ▼
                    RDS PostgreSQL 16        Secrets Manager          SNS topic →          ECR (wc-api,            CloudWatch logs
                    (private, node-SG-only)  (db/auth0/graph/demo)    SQS queue + DLQ      wc-sync-worker)        (per workload)
```

**Key facts:**
- **Two public hostnames:** `wc.<ROOT_DOMAIN>` (the SPA, CloudFront over a private S3 origin via OAC, SPA-fallback to `index.html` for deep links like `/callback`) and `api.wc.<ROOT_DOMAIN>` (the API, ALB provisioned by the AWS Load Balancer Controller). The `wc.` record is a Terraform Route53 alias; the `api.wc.` record is created by **external-dns** at deploy time (the ALB DNS name isn't known to Terraform).
- **ACM:** the CloudFront cert is in **`us-east-1`** (CloudFront requirement); the ALB cert is **regional**. Both DNS-validated against the (HITL-delegated) Route53 zone — the zone is a **data source**, not Terraform-managed.
- **EKS workloads (5):** `wc-api` (Deployment, behind ALB) · `wc-sync-worker` (Deployment, Wave-2 SQS consumer) · `wc-generation` (CronJob, weekly plan shells) · `wc-migration` (Job, **sole Flyway schema owner**, runs first) · `wc-rebuild-projections` (Job, populates the manager read-models from seed on deploy). The CronJob + both Jobs **reuse the `wc-api` image** (no third image) via Spring `--app.job=` args / profiles.
- **Secrets:** AWS Secrets Manager (`db`/`auth0`/`graph`/`demo`), mounted per-pod via the **Secrets Store CSI Driver + ASCP** as files under `/mnt/secrets`, bound by Spring `spring.config.import=optional:configtree:/mnt/secrets/` (one import binds db+auth0+graph; LESSONS §42). **Never** synced into a k8s Secret (rule #7). `db` is TF-populated from RDS; `auth0`/`graph` are HITL-populated via `populate-secrets.sh`.
- **IRSA least privilege (per workload):** api → `sns:Publish` + `GetSecretValue`(db/auth0/graph); worker → `sqs:*`(queue+DLQ) + `GetSecretValue`(graph only); cronjob/migration/rebuild → `GetSecretValue`(db only). No `GetSecretValue` on `*`.
- **No static keys:** the GitHub Actions deploy role is assumed via **OIDC**, env-scoped to `repo:<owner>/<repo>:environment:production` (a hardened permissions-boundary caps escalation). RDS is private (node-SG ingress only, never a CIDR).

### Terraform root module map (`terraform/`)
| File | Provisions |
|---|---|
| `versions.tf` / `main.tf` / `variables.tf` / `outputs.tf` / `backend.tf` | providers (aws ~>6, helm, kubernetes, random) · root locals/tags · inputs (`ROOT_DOMAIN`, `github_repo`, `region`, …) · exports · S3 remote-state backend (partial) |
| `vpc.tf` / `eks.tf` | VPC (private/public subnets, multi-AZ) · EKS cluster + managed node group · AWS Load Balancer Controller |
| `addons.tf` | cluster add-ons via `helm_release`: ALB controller, Secrets Store CSI Driver + ASCP, external-dns (installed during `apply`, before any kubectl — §17) |
| `rds.tf` | RDS PostgreSQL 16.x (private, node-SG-only, `random_password`) |
| `ecr.tf` | 2 ECR repos (wc-api, wc-sync-worker) — IMMUTABLE + scan-on-push |
| `sns_sqs.tf` | SNS lifecycle topic → SQS sync queue + DLQ (Wave-2 transport) |
| `s3_cloudfront.tf` | private S3 (SPA assets) + CloudFront (OAC, SPA fallback) + Secrets Manager (db/auth0/graph/demo) |
| `route53_acm.tf` | ACM certs (CloudFront us-east-1 + ALB regional, DNS-validated) + the `wc.` alias record (zone = data source) |
| `iam_irsa.tf` | the 4 per-workload IRSA roles + external-dns role (least privilege) |
| `iam_ci.tf` | GitHub OIDC provider + the hardened CI deploy role + permissions boundary + EKS access entry |
| `cloudwatch.tf` | per-workload CloudWatch log groups |

### Kubernetes manifests (`k8s/`)
`namespace` · `serviceaccount-{api,worker,cronjob,migration}` (IRSA-annotated) · `secretproviderclass` (one SPC per SA, least-privilege mount) · `deployment-{api,worker}` · `cronjob-generation` · `job-migration` · `job-rebuild-projections` · `job-perf-seed` (opt-in, **off** the deploy chain) · `service-api` · `ingress-api` (ALB). Post-`apply` TF values (role ARNs, secret ARNs, cert ARN, image URLs, `ROOT_DOMAIN`) are injected via an **allowlisted `envsubst`** of `${TOKEN}` placeholders in the pipeline before `kubectl apply` (LESSONS §14).

### Deploy pipeline (`.github/workflows/deploy.yml`, §13)
`gates` (no AWS: Spotless/test/JaCoCo/SpotBugs · ESLint/Prettier/Vitest · Cypress E2E) → **`deploy`** (one reviewer-gated `environment: production` job, OIDC): build+push images → `terraform apply` (installs add-ons) → render manifests → migration Job (+wait) → **rebuild-projections Job (+wait)** → roll api/worker/cron + svc/ingress → build+publish wc-web (S3+CloudFront) → deployed smoke. The ordered `needs`/step graph makes apply-before-kubectl + migrate-before-roll unreachable to violate.

### Cold-start state backend (`terraform-bootstrap/`)
A standalone sibling root (local backend) that creates the S3 state bucket + DynamoDB lock the main root assumes — resolving the chicken-and-egg so a fresh account can `terraform init terraform/`. Run **once** per account (runbook b). `prevent_destroy` on both (teardown requires commenting it out first); outputs a paste-ready `backend_config_hint`.

---

## Part 2 — Local dev (how infra is exercised locally)

Infrastructure is **declarative — it is verified, not run, locally** (there is no local EKS). Terraform `apply`/`init`-against-S3 + the real pipeline are **HITL** (need AWS creds). The agent/CI-side gate that proves a change is internally sound **without AWS creds** (LESSONS §2):

```bash
# Terraform (main root + bootstrap):
terraform -chdir=infra/terraform fmt -check -recursive
terraform -chdir=infra/terraform init -backend=false      # providers/modules resolve; no S3 touched
terraform -chdir=infra/terraform validate
tflint --chdir=infra/terraform
#   …and the same four for infra/terraform-bootstrap

# Kubernetes manifests (placeholders treated as plain strings):
kubeconform -strict -ignore-missing-schemas infra/k8s/*.yaml   # SPC CRD instances are skipped

# The deploy workflow:
actionlint .github/workflows/deploy.yml

# The secrets helper (no AWS calls):
shellcheck infra/scripts/populate-secrets.sh
infra/scripts/populate-secrets.sh --dry-run                    # redacted plan, zero AWS calls
```

**Running the app stack locally** (the application, not the infra) — **one command**:
```bash
docker compose up --build      # postgres + migrate (seeds V5/V6) + wc-api (local profile) → :8080
```
`docker-compose.yml` (repo root) brings up the **real backend + Postgres with the seeded demo data** — the same image + the same migrate→seed path as prod, in the `local` profile (demo-header personas; worker sync **in-process**, no live SNS/SQS — §13). The **frontend runs separately** (Vite HMR): `cd apps/wc-web && VITE_AUTH_MODE=demo VITE_USE_MOCKS=false VITE_API_BASE_URL=http://localhost:8080 yarn dev`. Ports (F.7): **Vite 5173 · API 8080 · Postgres 5432**. Up-smoke: `curl localhost:8080/actuator/health/readiness` + `curl -H 'X-Demo-Employee-Id: d0000000-0000-0000-0000-000000000001' localhost:8080/api/me` (→ Dana). Backend integration tests use **Testcontainers PostgreSQL** (no H2). _(LocalStack/Graph-mock intentionally omitted — the `local` profile's in-process sync makes them unnecessary for the "works locally" story; they'd only matter for a local Wave-2 live-sync rehearsal.)_

---

## Part 3 — Deploy (the 4-line summary; full steps in the runbooks)
1. **Tenants** (runbook a): Auth0 API + SPA + 7 users + post-login Action (and M365/Entra for Wave-2 Graph).
2. **Deploy-ready** (runbook b): `terraform-bootstrap` apply → delegate the Route53 zone → first admin-cred `terraform apply` (creates OIDC + CI role) → GitHub `production` Environment + vars.
3. **Deploy** (runbook c): `populate-secrets.sh --only auth0` → `gh workflow run deploy.yml` → approve the gate.
4. **Smoke** (runbook c): the pipeline curls api-readiness + SPA-root over ACM TLS; then log in as **Dana** → the command-center shows her 6 reports + a populated heatmap.

**Net irreducible HITL:** an AWS account + 1 admin cred (first apply) · a delegated domain · the Auth0 (+ M365) tenant · ~2 secret values. Everything else — infra, OIDC, image build/push, migrate, rebuild, roll, DNS records, the db secret — automates.

## Safety invariants (infra-relevant — see root `CLAUDE.md`)
- **No long-lived AWS keys in CI** (OIDC only; env-scoped trust + reviewer gate).
- **Secrets never leak** (rule #7): pointer-only queue payloads; secret VALUES never in logs/state/outputs/argv; CSI tmpfs mount, no etcd sync; `populate-secrets.sh` is no-leak.
- **Single Flyway owner** (forbidden-#3): only the migration Job migrates; api/worker/cron/rebuild are `flyway.enabled=false`.
- **No third image** (forbidden-#5): the CronJob + migration + rebuild Jobs reuse the `wc-api` image.
- **IRSA least privilege** (rule #3-adjacent): each workload gets exactly its actions on exact ARNs.

## Cross-links
- Backend: [`apps/wc-api/README.md`](../apps/wc-api/README.md) · Frontend: [`apps/wc-web/README.md`](../apps/wc-web/README.md) (incl. the SPA Auth0 build-env).
- Area conventions + lessons: [`infra/CLAUDE.md`](CLAUDE.md) · [`infra/LESSONS.md`](LESSONS.md).
