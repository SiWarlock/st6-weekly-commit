# Infrastructure & Deployment

## Executive summary

This layer is the machinery that turns the ST6 Weekly Commit application from source code into a running, internet-reachable system on AWS — and the local Docker Compose stack that lets you run the whole thing on a laptop. It owns the cloud topology (the private network, the Kubernetes cluster, the Postgres database, the message queue, the image registry, the static-site CDN, DNS + TLS, the secret vault, and the log groups), the identities that let each piece talk to the others without long-lived passwords, the Kubernetes manifests that describe the running workloads, and the one GitHub Actions pipeline that builds images, provisions everything, runs the database migration, and rolls the app out. Everything here is declarative: Terraform `*.tf` files describe the AWS resources, YAML manifests describe the Kubernetes objects, and a single `deploy.yml` orchestrates the apply order. The non-negotiable safety posture is that secrets live only in AWS Secrets Manager and reach pods as read-only mounted files via per-workload least-privilege identities — never as environment variables, never in logs, never in the message-queue payloads (which carry only a pointer to a database row). This layer provisions the SNS topic and SQS queue that carry the calendar-sync pipeline, but the *meaning* of those messages and what the workloads *do* at runtime belong to other layers.

## Responsibilities

- **AWS topology (Terraform `infra/terraform/`).** VPC + subnets/NAT (`vpc.tf`), EKS cluster + node group + ALB controller (`eks.tf`), RDS PostgreSQL 16 (`rds.tf`), the SNS topic + SQS queue + DLQ transport (`sns_sqs.tf`), ECR registries (`ecr.tf`), private S3 + CloudFront for the SPA (`s3_cloudfront.tf`), Route 53 + ACM DNS/TLS (`route53_acm.tf`), Secrets Manager containers (`secrets.tf`), and CloudWatch log groups (`cloudwatch.tf`).
- **Identity & secrets path.** The GitHub-OIDC CI deploy role + guardrail permissions boundary (`iam_ci.tf`), the four per-workload IRSA roles plus an external-dns role (`iam_irsa.tf`), the four Secrets Manager secrets (`secrets.tf`), and the per-ServiceAccount `SecretProviderClass` mounts (`secretproviderclass.yaml`). Enforces safety rule #7 (secrets via Secrets Manager + IRSA; never env/logs/queue).
- **Kubernetes workloads.** The `wc-api` Deployment behind an ALB Ingress + ClusterIP Service, and the `wc-worker` Deployment (`deployment-api.yaml`, `deployment-worker.yaml`, `ingress-api.yaml`, `service-api.yaml`), plus the namespace and four ServiceAccounts.
- **CI/CD.** The single `deploy.yml` pipeline: OIDC auth, release-tag / `workflow_dispatch` trigger, build+push to ECR, `terraform apply`, run the migration Job, apply manifests in order, publish the frontend to S3/CloudFront, deployed smoke.
- **Local runtime.** `docker-compose.yml` (real backend + Postgres + one-shot migrate) and the two `Dockerfile`s.
- **Remote-state bootstrap.** A separate sibling root (`terraform-bootstrap/`) that creates the S3 state bucket + DynamoDB lock table the main root's backend assumes already exist.

**NOT this layer (delegated):**
- **What each Job/CronJob *does* at runtime** (the migration Flyway run, projection rebuild, plan-shell generation, perf seed) — the `job-*.yaml` / `cronjob-*.yaml` manifests and their runner semantics belong to [07-scheduled-jobs.md](07-scheduled-jobs.md). This layer only provisions their SAs/IRSA/secret mounts and sequences them in the pipeline.
- **How the application *reads* the mounted secret** (the `spring.config.import=configtree:` binding) — see [04-authorization-identity-audit.md](04-authorization-identity-audit.md) / [01-domain-persistence.md](01-domain-persistence.md).
- **What the SNS/SQS messages *mean* and who publishes/consumes them** — see [06-calendar-sync-messaging.md](06-calendar-sync-messaging.md). This layer only provisions the transport.
- **The app's CORS config, JWT validation, log content hygiene** — Spring-owned (app layers). This layer provisions destinations, not content.

## Key components

| Component | What it does | Where |
|-----------|--------------|-------|
| VPC module | Public + private subnets across ≥2 AZs, single NAT (thin), ELB subnet-discovery tags | `infra/terraform/vpc.tf:17` |
| EKS module | Cluster + one managed node group, IRSA OIDC provider, public API endpoint | `infra/terraform/eks.tf:8` |
| ALB controller IRSA + Helm release | IRSA role for the AWS Load Balancer Controller + its Helm install | `infra/terraform/eks.tf:77`, `infra/terraform/eks.tf:98` |
| RDS PostgreSQL 16 | Private Postgres 16.x, node-SG-only ingress, encrypted, never public | `infra/terraform/rds.tf:46` |
| RDS master password | `random_password` consumed by RDS + the `db` secret; never an output | `infra/terraform/rds.tf:14` |
| SNS topic `wc-lifecycle` | Lifecycle event fan-out (sync pipeline transport) | `infra/terraform/sns_sqs.tf:14` |
| SQS queue `wc-sync` + DLQ | Standard queue (raw delivery) + DLQ redrive after `maxReceiveCount` | `infra/terraform/sns_sqs.tf:27`, `infra/terraform/sns_sqs.tf:20` |
| ECR repos (exactly 2) | `wc-api` + `wc-sync-worker`, IMMUTABLE tags, scan-on-push, 14d untagged-expiry | `infra/terraform/ecr.tf:13` |
| S3 + CloudFront | Private bucket fronted by CloudFront OAC, SPA 403/404→index.html | `infra/terraform/s3_cloudfront.tf:22`, `infra/terraform/s3_cloudfront.tf:42` |
| Route 53 + ACM | Two DNS-validated certs (CloudFront us-east-1, ALB regional) + alias records | `infra/terraform/route53_acm.tf:22`, `infra/terraform/route53_acm.tf:34` |
| Secrets Manager (4 secrets) | `db` (TF-populated), `auth0`/`graph`/`demo` (placeholder + ignore_changes) | `infra/terraform/secrets.tf:16` |
| CloudWatch log groups (4) | One per workload (api/worker/cronjob/migration) | `infra/terraform/cloudwatch.tf:16` |
| CI deploy role + boundary | GitHub-OIDC `production`-scoped role + guardrail permissions boundary | `infra/terraform/iam_ci.tf:106`, `infra/terraform/iam_ci.tf:47` |
| Per-workload IRSA roles (4 + external-dns) | api/worker/cronjob/migration least-privilege + external-dns route53 | `infra/terraform/iam_irsa.tf:17`, `infra/terraform/iam_irsa.tf:62`, `infra/terraform/iam_irsa.tf:109`, `infra/terraform/iam_irsa.tf:142`, `infra/terraform/iam_irsa.tf:186` |
| Cluster add-ons (Helm) | CSI driver + ASCP + external-dns | `infra/terraform/addons.tf:17`, `infra/terraform/addons.tf:38`, `infra/terraform/addons.tf:61` |
| `wc-api` Deployment | 2 replicas, IRSA SA, CSI secret mount, flyway off, actuator probes | `infra/k8s/deployment-api.yaml:11` |
| `wc-worker` Deployment | 1 replica, IRSA SA, graph+db CSI mount, no Auth0/SNS/CORS | `infra/k8s/deployment-worker.yaml:9` |
| ALB Ingress | internet-facing ALB, HTTPS:443 with ACM cert, TLS1.2 floor, external-dns hostname | `infra/k8s/ingress-api.yaml:11` |
| ClusterIP Service | `app: wc-api` selector, :80 → :8080 | `infra/k8s/service-api.yaml:4` |
| SecretProviderClass (4) | Per-SA CSI mounts mirroring IRSA least-privilege | `infra/k8s/secretproviderclass.yaml:22` |
| `populate-secrets.sh` | HITL helper to write Auth0/Graph values into placeholder secrets, no-leak | `infra/scripts/populate-secrets.sh:127` |
| `deploy.yml` | The single ordered CI/CD pipeline | `.github/workflows/deploy.yml:43` |
| `docker-compose.yml` | Local Postgres + one-shot migrate + real backend | `docker-compose.yml:31` |
| State-bootstrap root | S3 state bucket + DynamoDB lock (separate local-backend root) | `infra/terraform-bootstrap/main.tf:32`, `infra/terraform-bootstrap/main.tf:71` |

## Interfaces & contracts

**Terraform deploy inputs** (`variables.tf`): two have NO default and must be supplied or the plan fails — `ROOT_DOMAIN` (`variables.tf:3`) and `github_repo` (`variables.tf:162`). `region` defaults `us-east-1` (`variables.tf:13`); `env` ∈ {`local`,`aws`} default `aws` (`variables.tf:36`); `rds_minor_version` must match `^16\.` (`variables.tf:19`); `eks_cluster_version` default `1.33` (`variables.tf:66`); `k8s_namespace` default `wc` pins the IRSA trust `sub` (`variables.tf:154`).

**Terraform outputs → CI envsubst → manifests.** The root exports the values the pipeline injects into the static k8s manifests (`outputs.tf`): `cluster_name`, `rds_endpoint`, `ecr_{api,worker}_repo_url`, `sns_topic_arn`, `sqs_queue_url`, `sqs_dlq_url`, `s3_assets_bucket_name`, `cloudfront_distribution_id`, the four `*_secret_arn`s, `alb_cert_arn`, the four `irsa_*_role_arn`s, and `ci_deploy_role_arn`. **Secret *values* are never an output** — only ARNs (`outputs.tf:84`, `rds.tf:8`). The manifests carry literal `${TOKEN}` placeholders; the pipeline runs an **allowlisted** `envsubst` over exactly 15 tokens (`deploy.yml:185`).

**Secret JSON key contract** (`secrets.tf`): the `db` secret holds `spring.datasource.{url,username,password}` (`secrets.tf:23`); `auth0` holds `spring.security.oauth2.resourceserver.jwt.issuer-uri` + `auth0.audience` (`secrets.tf:37`); `graph` holds `GRAPH_TENANT_ID`/`GRAPH_CLIENT_ID`/`GRAPH_CLIENT_SECRET` (`secrets.tf:53`). Each `SecretProviderClass` `jmesPath` extracts these keys into identically-named mount files (`secretproviderclass.yaml:31`) so Spring binds them via a config tree (consumption is [04](04-authorization-identity-audit.md)/[01](01-domain-persistence.md) territory).

**IRSA trust ↔ ServiceAccount contract.** Each IRSA role's OIDC trust `sub` is `system:serviceaccount:wc:wc-{api,worker,cronjob,migration}` (`iam_irsa.tf:27`,`:72`,`:119`,`:152`); the matching ServiceAccount annotates `eks.amazonaws.com/role-arn` with the role ARN (`serviceaccount-api.yaml:14`). external-dns trusts `system:serviceaccount:kube-system:external-dns` (`iam_irsa.tf:198`).

**Pipeline triggers** (`deploy.yml:27`): `workflow_dispatch` (manual) or a `release-*` git tag push. AWS auth is one OIDC assumption of the `production`-scoped CI role; no static keys (`deploy.yml:101`).

**Local runtime** (`docker-compose.yml`): brings up Postgres 16 → one-shot `migrate` (the `wc-api` image in the `flyway-migrate` profile) → `wc-api` in the `local` profile serving `:8080` with in-process sync (no SNS/SQS/Graph) (`docker-compose.yml:49`,`:63`).

## Data & state

- **Terraform remote state.** S3 backend + DynamoDB lock, partial config supplied at `init` time (`backend.tf:25`). The state bucket holds the encrypted RDS master password (state is the crown jewels). It is created by the **bootstrap root**: a versioned/encrypted/public-blocked S3 bucket named `wc-${env}-tfstate-${account_id}` + a `LockID`/`S`/`PAY_PER_REQUEST` DynamoDB table, both `prevent_destroy` + `force_destroy=false` (`terraform-bootstrap/main.tf:32`,`:71`). The bootstrap root uses a **local** backend (chicken-and-egg: it can't store state in the bucket it creates) (`terraform-bootstrap/versions.tf:1`).
- **Network CIDRs.** VPC `10.0.0.0/16`; private subnets `/20` (pod IPs), public `/24` (ALB only) (`vpc.tf:26`).
- **Secrets Manager containers** (`secrets.tf`): `wc/${env}/{db,auth0,graph,demo}`. `db` is TF-populated from RDS coords + `random_password`; the others ship a `REPLACE_VIA_HITL` placeholder version with `lifecycle { ignore_changes = [secret_string] }` so the HITL helper can write real creds without TF reverting (`secrets.tf:41`).
- **SQS redrive.** `wc-sync` redrives to `wc-sync-dlq` after `var.sqs_max_receive_count` (default 5) (`sns_sqs.tf:31`, `variables.tf:146`); DLQ retention 14d (`sns_sqs.tf:23`).
- **Workload identity matrix** (the IRSA + SPC least-privilege grid):

| SA | SNS | SQS | Secrets (GetSecretValue) |
|----|-----|-----|--------------------------|
| `wc-api` | `Publish` lifecycle | — | db, auth0, graph (`iam_irsa.tf:35`) |
| `wc-worker` | — | Receive/Delete/GetQueueAttributes on queue+DLQ | db, graph (`iam_irsa.tf:80`) |
| `wc-cronjob` | — | — | db only (`iam_irsa.tf:127`) |
| `wc-migration` | — | — | db only (`iam_irsa.tf:160`) |

  The `SecretProviderClass` mounts mirror this exactly (api→db/auth0/graph, worker→db/graph, cronjob→db, migration→db) (`secretproviderclass.yaml`).

## Dependencies

- **Depends on:**
  - **AWS** — every resource is an AWS API call; the cluster, DB, queue, registry, CDN, DNS, and vault are all AWS-managed.
  - **The app images** ([01](01-domain-persistence.md)/[02](02-api-web.md)/[06](06-calendar-sync-messaging.md)) — `deploy.yml` builds `apps/wc-api/Dockerfile` and `apps/wc-api/worker/Dockerfile` (`deploy.yml:127`); both are real (`apps/wc-api/Dockerfile`, `apps/wc-api/worker/Dockerfile`).
  - **The migration / scheduled jobs** ([07](07-scheduled-jobs.md)) — the pipeline runs the migration Job and rebuild Job as gates before rolling Deployments; the manifests for those Jobs/CronJob reuse the `wc-api` image.
  - **The bootstrap root** — must run once before the main root's first `init`.
  - **HITL prerequisites** — the Route 53 hosted zone PRE-EXISTS (data source, not managed — `route53_acm.tf:17`); the GitHub `production` Environment with required reviewers; the `populate-secrets.sh` HITL step for Auth0/Graph values.
- **Used by:**
  - **The app workloads** — they assume their IRSA role to reach AWS and mount their secret via the CSI volume.
  - **The frontend** ([09-frontend.md](09-frontend.md)) — its built Vite assets are synced to the S3 bucket and served via CloudFront; the pipeline injects `VITE_*` build-time env (`deploy.yml:286`).
  - **The calendar-sync pipeline** ([06](06-calendar-sync-messaging.md)) — the api publishes to the SNS topic; the worker consumes the SQS queue. This layer provisions both endpoints + the IRSA actions.

## How it works (flow)

The whole system materializes through `deploy.yml` in a strictly ordered chain (job `needs` + in-job step order make an out-of-order path unreachable):

```
 gates (no AWS)                    deploy (environment: production, OIDC)
 ┌──────────────┐   needs   ┌────────────────────────────────────────────────┐
 │ Spotless/test│──────────▶│ OIDC assume CI role → build+push api+worker IMG │
 │ JaCoCo/lint  │           │ → terraform apply (provisions ALL + add-ons)    │
 │ local E2E    │           │ → update-kubeconfig → envsubst(15 tokens)       │
 └──────────────┘           │ → apply ns/SAs/SPCs                             │
                            │ → migration Job + WAIT (sole schema owner)      │
                            │ → rebuild-projections Job + WAIT                 │
                            │ → roll api/worker/cron + svc/ingress             │
                            │ → build wc-web → S3 sync → CF invalidate         │
                            │ → deployed smoke (curl readiness + SPA)          │
                            └────────────────────────────────────────────────┘
```

1. **Gates** (`deploy.yml:47`) run with no AWS creds on every trigger: backend Gradle gates, frontend lint/test, local Cypress E2E vs Compose.
2. **Deploy** runs only after gates pass, inside the GitHub `production` Environment (reviewer-gated) so the env-scoped OIDC trust matches. It assumes the CI role via a short-lived OIDC token (`deploy.yml:101`).
3. **Build + push** the two SHA-tagged images to ECR (`deploy.yml:122`). The CronJob + migration Job + rebuild Job reuse the `wc-api` image — never a third.
4. **`terraform apply`** (`deploy.yml:135`): `init` against the S3/DynamoDB backend → `plan` → `apply -auto-approve`. Critically, apply installs the cluster add-ons (CSI driver + ASCP + external-dns via `helm_release`) **before** any `kubectl`, so the `SecretProviderClass` CRD + driver exist when pods mount (`addons.tf`, lesson §17).
5. **`update-kubeconfig`** via the CI EKS access entry (cluster-admin) (`deploy.yml:151`, `iam_ci.tf:141`).
6. **Render manifests** (`deploy.yml:160`): read TF outputs, `export` them, and run an **allowlisted** `envsubst` over exactly the 15 tokens — substituting ARNs/URLs/image refs into the static manifests' `${TOKEN}` placeholders.
7. **Apply base + migration** (`deploy.yml:196`): namespace → SAs → SPCs, then `delete`-then-`apply` the migration Job (its `spec.template` is immutable across re-deploys) and poll BOTH terminal conditions, failing fast on `Failed` (the *what-it-does* is [07](07-scheduled-jobs.md)).
8. **Rebuild projections** (`deploy.yml:247`) gated the same way, before any Deployment serves traffic.
9. **Roll** Deployments + CronJob + Service + Ingress, then `rollout status` (`deploy.yml:273`). The ALB controller provisions the internet-facing ALB from the Ingress annotations; external-dns upserts the `api.wc.${ROOT_DOMAIN}` alias record.
10. **Frontend** (`deploy.yml:286`): build the SPA with `VITE_*` env, `aws s3 sync --delete` to the bucket, CloudFront invalidation.
11. **Deployed smoke** (`deploy.yml:308`): `curl` the API readiness probe + the SPA root over ACM TLS on the custom domains.

The secret path at runtime: a pod's IRSA role lets the ASCP fetch the named Secrets Manager secret; the CSI driver mounts each JSON key as a tmpfs file under `/mnt/secrets` (no k8s Secret / etcd sync — `addons.tf:25`, `secretproviderclass.yaml:14`); Spring reads them as a config tree (binding is [04](04-authorization-identity-audit.md)/[01](01-domain-persistence.md)).

## Design decisions & rationale

- **Apply-before-migrate, migrate-before-roll** (ARCHITECTURE.md §13 Decision 1). `terraform apply` provisions the cluster + RDS + the add-ons the Job rides on, so it must precede the migration Job; the migration Job is the **sole** Flyway schema owner and must complete before any Deployment/CronJob roll. The ordered step graph makes an out-of-order path structurally unreachable (`deploy.yml:194`–`:283`).
- **Hardened full-apply CI with a guardrail boundary** (ARCHITECTURE.md §13; `docs/decisions/001`). Rather than service-allowlisting the CI role, its broad blast radius is neutralized by a permissions boundary threaded onto the CI role AND **every** TF-created role: `Allow *` (a guaranteed superset, robust to AWS-managed-policy drift) + escalation **Denies** (no static-credential backdoors, no boundary tampering, can only create roles that themselves carry the boundary, PassRole only `wc-*` roles) (`iam_ci.tf:47`). Service-level least-privilege lives in the *identity* policies; the boundary is the escalation control.
- **Per-workload IRSA least-privilege** (ARCHITECTURE.md §12; safety rules #5/#7). Each workload gets exactly its 1–2 actions on exact ARNs — no `GetSecretValue` on `*` — and the `SecretProviderClass` mirrors the same grant (defense-in-depth) (`iam_irsa.tf`, `secretproviderclass.yaml`).
- **Secrets as mounted files, never env** (safety rule #7). `syncSecret.enabled=false` keeps values out of k8s Secrets/etcd (`addons.tf:25`); pods read tmpfs files (`deployment-api.yaml:79`). The HITL helper pipes values over stdin and prints only names + VersionId/ARN (`populate-secrets.sh:127`). `db` is TF-populated (value only in encrypted state, never an output); third-party creds use placeholder + `ignore_changes` so they never touch TF state/vars (`secrets.tf`).
- **CloudFront cert in us-east-1; ALB cert regional** (ARCHITECTURE.md §12; RISK-010). CloudFront *requires* its ACM cert in us-east-1, provisioned via the `aws.us_east_1` aliased provider; the ALB cert is regional on the default provider (`route53_acm.tf:22`,`:34`, `versions.tf:61`).
- **Exactly two ECR repos** (forbidden-pattern #5). `for_each` over a fixed 2-element set makes a third image structurally impossible; CronJob/migration reuse `wc-api` (`ecr.tf:9`).
- **Pointer-only queue payloads provisioned, not enforced here** (safety rule #7). The SNS→SQS topology is provisioned with SSE + a scoped queue policy; the rule that payloads carry only `{syncRecordId,eventKind,env,traceId}` is enforced at the app layer (`sns_sqs.tf:4`) — see [06](06-calendar-sync-messaging.md).
- **Thin/cost MVP posture** (RISK-009). Single NAT gateway (`vpc.tf:30`), single-AZ RDS default (`variables.tf:132`), `db.t4g.micro` (`variables.tf:120`), no cluster autoscaler, `skip_final_snapshot`/`deletion_protection=false` for clean teardown (`rds.tf:71`). HA hardening is a Phase 13 item.
- **Separate bootstrap root for remote state** (lesson §20). The state bucket + lock must exist before the main root's first `init`, so a sibling local-backend root creates them, fail-closed (`prevent_destroy`) (`terraform-bootstrap/main.tf`).

## Gotchas & sharp edges

- **`X-Demo-Employee-Id` / `DEMO_AUTH_ENABLED=false` in prod** (safety rule #5). The api Deployment hardcodes `DEMO_AUTH_ENABLED: "false"` (`deployment-api.yaml:47`) and the `demo` secret is a prod no-op the populate helper skips (`populate-secrets.sh:50`). The api IRSA role deliberately does **not** grant the `demo` secret (`iam_irsa.tf:55`).
- **CloudWatch destinations exist but nothing forwards to them.** `cloudwatch.tf` provisions four log groups but the pod→CloudWatch forwarding agent (Fluent Bit / Container Insights) is **NOT** provisioned — container logs do not reach these groups without it (deferred to Phase 13) (`cloudwatch.tf:5`). This is honest in-code; do not assume logs flow.
- **EKS public API endpoint is open by default.** `eks_public_access_cidrs` defaults `["0.0.0.0/0"]` (`variables.tf:112`, `eks.tf:24`) — an accepted residual for the demo (rationale in `docs/decisions/001`); tighten in real deploys.
- **Node-group role name is forced under `wc-*`** (lesson §19; `eks.tf:62`). The vendored eks module would otherwise name it `default-eks-node-group-*`, outside the boundary's `PassRole` `wc-*` scope, failing `apply`. Static gates can't catch this — only a real apply.
- **RDS engine drift is intentionally ignored.** `auto_minor_version_upgrade=true` + `ignore_changes = [engine_version]` means `engine_version` pins only the initial provision; AWS bumps the minor in a maintenance window and `plan` won't try to revert it (`rds.tf:78`).
- **Migration Job re-apply needs delete-first.** A Job's `spec.template` is immutable, so re-applying the fixed-name Job with a new image SHA fails "field is immutable" — the pipeline `kubectl delete`s it first (`deploy.yml:209`).
- **`api.wc` DNS is not pure-Terraform.** The ALB is created by the ALB controller at deploy time (post-apply), so its alias record can't be a Terraform record; **external-dns** (a kube-system Helm add-on) watches the Ingress hostname annotation and upserts it (`route53_acm.tf:11`, `ingress-api.yaml:27`, `addons.tf:61`). The ALB *cert* is independent and IS Terraform-managed.
- **The `kubernetes` Terraform provider is declared but unused.** All manifests are applied via `kubectl` in CI, not Terraform; the provider may stay declared-only permanently (`versions.tf:18`).

**Drift between ARCHITECTURE.md and code (documented, not smoothed):**

- **`worker` IRSA gained the `db` secret (post-doc evolution, now reconciled).** ARCHITECTURE.md §12 originally described worker → graph only, but was updated to "db + graph" (`ARCHITECTURE.md:207` cites commit `adfa639`); the code grants both (`iam_irsa.tf:99`) and the worker SPC mounts both (`secretproviderclass.yaml:73`). **No live drift** — doc and code agree; noted because the LOW-severity history is visible in comments. Severity: low.
- **`application-aws.yml` vs `application-prod.yml`.** ARCHITECTURE.md §12 explicitly calls out that the deploy profile is `aws` and an `application-prod.yml` is **dead** under an `aws` profile; the manifests set `SPRING_PROFILES_ACTIVE: "aws"` (`deployment-api.yaml:40`), consistent with the doc. The app-side config file is [01](01-domain-persistence.md)/[04](04-authorization-identity-audit.md) territory (lesson §42 records a real bug here); flagged so a reader doesn't expect a `prod` profile. Severity: low.

## Connects to

- [06-calendar-sync-messaging.md](06-calendar-sync-messaging.md) — **handoff: the SNS topic + SQS queue + DLQ**. This layer provisions `wc-lifecycle` (`sns_sqs.tf:14`), `wc-sync`/`wc-sync-dlq` (`sns_sqs.tf:20`,`:27`), exports `sns_topic_arn`/`sqs_queue_url`/`sqs_dlq_url` (`outputs.tf:68`–`:81`), and grants the api `sns:Publish` + the worker SQS Receive/Delete (`iam_irsa.tf`). The publish/consume logic and pointer-payload semantics live there.
- [07-scheduled-jobs.md](07-scheduled-jobs.md) — **handoff: the migration Job, rebuild Job, generation CronJob, perf-seed Job**. This layer provisions their SAs (`serviceaccount-{cronjob,migration}.yaml`), IRSA (`iam_irsa.tf:109`,`:142`), CSI mounts, and sequences them in `deploy.yml`; what they execute belongs there.
- [04-authorization-identity-audit.md](04-authorization-identity-audit.md) / [01-domain-persistence.md](01-domain-persistence.md) — **handoff: secret consumption + datasource**. The `db`/`auth0` secret keys and the `configtree:` mount are bound by the app's config there; this layer provisions the secret containers + the CSI `SecretProviderClass`.
- [09-frontend.md](09-frontend.md) — **handoff: S3/CloudFront hosting + Vite build env**. The SPA assets are synced to the bucket this layer provisions and served via CloudFront; the pipeline injects `VITE_*` (`deploy.yml:286`).
- [02-api-web.md](02-api-web.md) — **handoff: the `wc-api` image + ALB Ingress + CORS pass-through**. The api Deployment/Service/Ingress front the app's HTTP surface; CORS is Spring-owned and passes through the ALB/CloudFront.

---
_Generated by `/layer-docs` (initial run) against commit `3b919a9` on 2026-06-04. Claims are anchored to code; `UNVERIFIED` marks anything not confirmed._
