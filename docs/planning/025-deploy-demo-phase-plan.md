# 025 — Deployed-Backend Demo: Sequenced Phase Plan

> Authored 2026-06-04 by `st6-main-orchestrator` (backend+deploy). Endorsed by the team lead (infra-impl spin + two-wave shape + persist). The two-wave *delivery strategy* is being surfaced to the user for confirmation; **Wave 1 proceeds immediately** regardless (it's the foundation). This doc is the audit-trail artifact for the deploy-demo phase.

## Goal
A **deployed wc-api + RDS Postgres serving the React SPA, with REAL Auth0/OAuth2** (not the `X-Demo-Employee-Id` header), on EKS+RDS, plus a **LIVE Outlook + SQS/SNS sync pipeline** (now required per user decision — not stubbed). Maximize deploy automation; document the irreducible HITL.

## ⚠️ Charter corrections (from a full `infra/` survey — scope is SMALLER than first assumed)
- **deploy.yml ALREADY integrates the worker** (build+push both images at step 2; rolls worker at step 8). The charter's "un-skip the worker" is **already done**.
- **GitHub OIDC provider + CI deploy role + permissions boundary are ALREADY in `iam_ci.tf`.** The bootstrap chicken-and-egg reduces to **just the TF state bucket + DynamoDB lock** (OIDC provider is created in the first human-run `terraform apply`).
- Infra is **~80% built**: 17 TF modules (VPC/EKS/RDS-PG16/ECR×2/SNS-SQS-DLQ/S3-CF/Secrets×4/IRSA×4/Route53-ACM/CloudWatch/add-ons) + 14 k8s manifests (incl. migration Job, worker Deployment, cronjob) + a fully-orchestrated `deploy.yml` (gates→OIDC→build→tf apply→migrate→roll→wc-web→smoke). All schema-valid.

**The REAL blocking gaps:** Dockerfiles (absent), datasource config (absent), V5/V6 seed (V5 in flight), worker *pipeline code* (skeleton only).

## Shape: TWO WAVES
Rule #4 (Outlook sync never blocks the core lifecycle) means the deployed demo with **real OAuth works with the no-op SNS gateway**. So get it **DEPLOYED first**, layer **live Outlook second** — de-risks the demo without losing any required capability.

### WAVE 1 — Deployable backend, real OAuth (sync still no-op)
| # | Slice | Owner | Eff | Brief |
|---|---|---|---|---|
| 1 | **V5** demo seed (personas + relationships, OAuth `external_subject`) — `db/demo-seed/` (Opt-1) | backend | ~0.5 | 084 |
| 2 | **V6** fixture state matrix → `db/demo-seed`; projections populated on deploy via existing 6.7 `ProjectionRebuildRunner` (defer literal `rebuild==seed` test) | backend | ~2 | 086 |
| 3 | **Datasource/JPA deploy config** — `spring.config.import=configtree:/mnt/secrets/db/` (the `db` secret keys ARE `spring.datasource.*`) + `ddl-auto=validate` + Hikari, aws profile | backend | ~1 | TBD |
| 4 | **Dockerfiles** wc-api + worker (multi-stage Gradle→JRE; deploy.yml expects `apps/wc-api/Dockerfile` + `worker/Dockerfile`) | backend | ~1 | TBD |
| 5 | **AWS bootstrap** — TF state bucket + DynamoDB lock + runbook | infra | ~1 | 087 |
| 6 | **Secrets-population helper** (`put-secret-value` for auth0+graph; db is TF-populated) | infra | ~1 | 088 |
| → | **First `terraform apply` + deploy.yml run + smoke** (gated on secret values from Wave-1 runbooks) | infra+HITL | — | — |

### WAVE 2 — Live Outlook + SQS/SNS (touches rules #4 + #7 → ad-hoc security review each)
| # | Slice | Owner | Eff |
|---|---|---|---|
| 7 | **Real SNS gateway** — replace `LoggingLifecycleSnsGateway`; publish `SyncJobPointer` on lifecycle events; afterCommit + swallow (rule #4), pointer-only (rule #7) | backend | ~1 |
| 8 | **Worker SQS consumer** — `@SqsListener` on `wc-sync`, reload by `syncRecordId`, DLQ on fail | backend | ~1.5 |
| 9 | **MS Graph adapter + app-only auth** — Graph calendar create/update + client-credentials token from the `graph` secret | backend | ~2.5 |
| → | worker re-deploy (no new infra — k8s + deploy.yml already carry it) | — | — |

*(Wave 2 needs a ~15min scoping pass before its briefs to confirm the SNS publish seam + SyncRecord wiring.)*

### DOCS (parallel)
Runbook **(a)** setup-everything (expand `auth0-tenant-setup.md` + M365 E5 trial + Entra app reg) ~1 · Runbook **(b)** fresh-AWS→deploy-ready ~0.75 · Runbook **(c)** actual-deployment+smoke ~0.5 · **infra/README** ~0.3 · **apps/wc-api/README** ~0.3 · **ROOT README** (coordinate w/ frontend orch) ~0.3 · **docker-compose** local full-stack — assess (recommend: wc-api + Postgres minimum; LocalStack/Graph-mock optional) ~1.

**Total ≈ 15–16 slices, 2 implementers, ~2 waves.**

## Implementer topology
- **`st6-main-wc-api-implementer`** (cwd `apps/wc-api/`): V5/V6 · datasource (Spring side) · Dockerfiles (app tree) · the entire worker pipeline code (SNS gateway, SQS consumer, Graph adapter).
- **`st6-main-infra-implementer`** (cwd `infra/`): AWS bootstrap · secrets-population helper · deploy.yml verify/fixes · the 3 runbooks · infra/README · the configtree-mount + worker/graph env k8s/TF tweaks.
- **Coordination seams:** Dockerfile paths · the `/mnt/secrets` configtree path (SPC ↔ Spring) · the graph secret keys.

## Automate vs irreducible HITL
**Automated:** state bucket+lock (bootstrap) · OIDC provider+CI role+boundary (TF, first apply) · all AWS infra (`terraform apply`) · image build/push + migrate + render + roll + smoke (deploy.yml/OIDC) · DB secret (TF auto-populates from RDS) · DNS records (external-dns).
**Irreducible HITL (→ runbooks):**
1. AWS account + 1 admin cred for the first apply.
2. Domain + Route53 zone delegation (ACM DNS-validation).
3. GitHub `production` Environment + reviewers + OIDC trust subject (repo admin; partly `gh`-scriptable).
4. Auth0 tenant/API/SPA/7 users/post-login Action.
5. M365 E5 trial + Entra app reg + Graph admin-consent (Calendars.ReadWrite).
6. Put the Auth0/Graph/demo **secret values** into the TF-created containers (templated `put-secret-value` helper → fill-in-the-blanks).

**Net "fresh AWS account → live":** run bootstrap → `terraform apply` → populate ~3 secrets (from runbook-a tenants) → trigger deploy.yml. Everything else automates.

---

## Wave-2 scoping pass — findings (2026-06-04, fresh backend orch)

> The ~15-min seam-confirmation the handoff/plan call for, done BEFORE authoring the Wave-2 briefs. Verified against HEAD `8984d26`. **The seam is clean — Wave-2 is mostly greenfield worker code + one api-gateway swap, no lifecycle-txn surgery.** These findings anchor the s7/s8/s9 briefs (authored after 092 lands).

### s7 — Real SNS gateway (api-side, brief 09x)
- **Seam = `LifecycleSnsGateway.publish(SyncJobPointer)`.** `SnsLifecyclePublisher` (`@Service`, `sns/`) ALREADY owns the whole non-blocking outbox dance: `@Transactional(REQUIRES_NEW)`, loads the record, calls `gateway.publish(pointer)`, flips `PENDING_PUBLISH → QUEUED` + `queuedAt`, **swallows `RuntimeException` (rule #4 — record stays `PENDING_PUBLISH`, retryable), logs ids-only (rule #7)**. The afterCommit hook is registered at `PlanLifecycleService:299–308` (`TransactionSynchronization.afterCommit → syncRecordIds.forEach(snsLifecyclePublisher::publish)`, inline fallback if no active sync). **No lifecycle/publisher change needed.**
- **ONLY change:** replace `LoggingLifecycleSnsGateway` (a bare `@Component`, no conditional) with a real `AwsSnsLifecycleGateway` that JSON-serializes the 4-field `SyncJobPointer` and `sns:Publish`es to `${SNS_TOPIC_ARN}`.
- **Bean selection (Step-2.5 Q):** gate the real gateway (`@ConditionalOnProperty(app.sns.topic-arn)` or `@Profile("aws")`) + make the stub `@ConditionalOnMissingBean(LifecycleSnsGateway.class)` so local/demo/test keep the no-op. Both can't be bare `@Component`s.
- **Deps:** ADD `software.amazon.awssdk:sns` to the **api** module (NONE present today — verify version via the AWS SDK v2 BOM / Context7 at author time).
- **Env:** `SNS_TOPIC_ARN` already wired in `deployment-api.yaml:53`. **GAP — no `APP_ENV`/`app.env` env** → `SnsLifecyclePublisher`'s `@Value("${app.env:local}")` makes the pointer's `env` field `"local"` in the deployed api. **Infra seam:** wire `APP_ENV=aws` (or `app.env`) into `deployment-api.yaml` (+ worker) so the pointer `env` is correct.
- **IRSA:** confirm the api IRSA grants `sns:Publish` on the topic ARN (`iam_irsa.tf`) at author time.
- **Security:** ad-hoc review (rules #4 + #7).

### s8 — Worker SQS consumer (worker-side, greenfield)
- Worker is **skeleton-only** (confirmed — only `WcSyncWorkerApplication` + `WorkerSharedConfig`). `@SqsListener` on the `wc-sync` queue (`SQS_QUEUE_URL` + `SQS_DLQ_URL` already in `deployment-worker.yaml:45–48`); deserialize `SyncJobPointer`, reload `OutlookCalendarSyncRecord` by `syncRecordId` via the `:shared` `OutlookCalendarSyncRecordRepository`, dispatch to the Graph adapter, transition status (`QUEUED → …`), → DLQ on fail.
- **⚠️ This slice RE-ADDS the worker datasource** (removes the 092 JPA-exclude) — the worker must read the SyncRecord + resolve the owner email + plan/commitments to render the calendar event (`SyncRecordService` shows the record carries only `ownerEmployeeId`/`relatedType`/`relatedId`/`eventKind`/`weekStartDate`/`traceId` → the worker loads the rest from the DB). **Pairs with 092** (092 Q4 forward-note: the exclude is removed exactly here).
- **Deps:** ADD `io.awspring.cloud:spring-cloud-aws-starter-sqs` (the `@SqsListener` provider) to the **worker** (verify version at author time).
- **⚠️ INFRA seam (paired infra slice):** the `wc-worker-secrets` SPC + worker IRSA grant **GRAPH-only `GetSecretValue`** today (`deployment-worker.yaml:3`). Wave-2 worker needs **db secret keys added to the worker SPC + `GetSecretValue` on the db secret in the worker IRSA** (read-only). The comment claims the worker IRSA already has `sqs Receive/Delete/GetQueueAttributes` — confirm in `iam_irsa.tf`.
- **Security:** ad-hoc review (rule #7 — pointer-only payload, no body in DLQ; idempotent reprocessing).

### s9 — MS Graph adapter + app-only auth (worker-side, greenfield)
- Client-credentials token from the **graph secret** (`GRAPH_TENANT_ID`/`GRAPH_CLIENT_ID`/`GRAPH_CLIENT_SECRET`). **⚠️ The worker has NO `configtree` import yet** (its `application-aws.yml` after 092 is logging-only) → this slice ADDS `spring.config.import=optional:configtree:/mnt/secrets/` to the worker's aws profile so the graph keys bind (LESSONS §42; the worker SPC already mounts the graph secret).
- **`GRAPH_MODE=demo-success`** (env on both deployments, NO code consumer yet — greenfield): the adapter honors it as a **deterministic success stub** (simulate a successful Graph create/update without a live M365 call — lets the demo run before/without the M365 trial) vs real Graph mode. Confirm the exact `demo-success` contract when authoring.
- Calendar create/update on the owner's calendar (owner email ← `Employee` by `ownerEmployeeId`).
- **Deps:** `com.microsoft.graph:microsoft-graph` + `com.azure:azure-identity` (`ClientSecretCredential`) — verify versions at author time.
- **Security:** ad-hoc review (rule #7 — secrets never logged; rule #4 — Graph failure never blocks, lands `FAILED`/retryable).

### Sequencing
**092 (in flight) → s7 (api SNS, independent of 092) → s8 (worker SQS, depends on 092 + its infra seam) → s9 (Graph, depends on s8 + the worker configtree import).** Each backend slice pairs with an infra seam (APP_ENV env · worker DB secret+IRSA · worker configtree). Ad-hoc security review (general-purpose agent — the `security-reviewer` subagent isn't registered) on each. Brief lane: s7/s8/s9 = next-free after 092 (coordinate numbering with the frontend orch).
