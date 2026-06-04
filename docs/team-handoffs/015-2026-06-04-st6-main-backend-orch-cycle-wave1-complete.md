# Handoff — st6-main backend ORCHESTRATOR cycle, deploy-demo Wave-1 CODE-COMPLETE (2026-06-04)

> Cycling at the **Wave-1-code-complete boundary** (orch at ~65%, a clean break the lead approved). **Orch-only cycle** — both implementers PERSIST and hold for the fresh orch (they did NOT `/session-end`). Predecessor = handoff 013. HEAD after my round commit. The fresh orch picks up: **092 (worker-boot fix) FIRST**, then runbooks/READMEs (infra) + the **Wave-2 live-sync pipeline** (backend).

## WHAT THIS CYCLE DELIVERED — Wave-1 of the deployed-backend demo
The user wants a **deployed wc-api + RDS + React SPA with REAL Auth0/OAuth2** on EKS, plus (Wave-2) a **LIVE Outlook + SQS/SNS sync pipeline**. Two-wave shape (lead+user CONFIRMED): **Wave-1 = make it deployable (no-op sync)** → first deploy → **Wave-2 = live sync**. Full plan: **`docs/planning/025-deploy-demo-phase-plan.md`**.

**Wave-1 is CODE-COMPLETE. 7 slices landed this round (backend+infra):**
| Slice | Commit | What |
|---|---|---|
| V5 (10.2) | `65cc255` | Demo persona seed (Dana + 6 reports + relationships) in **`db/demo-seed/`** (Opt-1) + OAuth `external_subject=st6\|<first>-<last>` |
| 12.12 (087) | `aef32bf` | `infra/terraform-bootstrap/` cold-start root (TF state bucket + DynamoDB lock) |
| V6 (10.3–10.5) | `101ee1d` | Fixture state matrix (8 plans, source-only; projections via rebuild-on-deploy) |
| 12.13 (088) | `48a9fec` | `infra/scripts/populate-secrets.sh` (no-leak auth0+graph put-secret-value; security-agent PASS) |
| 090 (Wave-1 s3) | `6870a4f` | Datasource via `configtree:/mnt/secrets/` + **the `application-prod.yml`→`application-aws.yml` profile-bug fix** |
| 12.14 (089) | `9004def` | rebuild-projections Job + deploy.yml step + **the generation-cronjob `web-type=none` exit fix** (fold-in) |
| 091 (Wave-1 s4) | `bee7626` | Multi-stage Dockerfiles (wc-api + wc-sync-worker) — the standing build blocker |

OAuth backend was already built (Phase 2). The seed→OAuth-identity→datasource→image spine is complete. **`docs/briefs/086–091`** are the design audit trail (085 is the frontend orch's).

## 🔴 THE FRESH ORCH'S FIRST SLICE — 092 (worker-boot fix), a Wave-1 deploy-completion item
**Finding (091 boot-smoke):** the deployed `wc-sync-worker` will **crashloop in `aws`** — it pulls JPA transitively via `:shared` → `DataSourceAutoConfiguration` activates → "Failed to determine a suitable driver class", but `wc-worker-secrets` mounts **graph-only** (no `spring.datasource.*`). The worker's tests pass only via a **test-only** `spring.autoconfigure.exclude` of DataSource/JPA, absent in prod → its `rollout status` fails the first deploy's worker roll.
**Fix (small):** exclude `DataSourceAutoConfiguration`/`HibernateJpaAutoConfiguration` in the worker's `aws` config (web+actuator-only until Wave-2 wires its DB) **+ the worker `application-prod.yml`→`application-aws.yml` rename** (the 090 carry-forward — same profile-name bug as the api had, cosmetic-only on the worker today but fold it here). The first deploy is **HITL-gated**, so 092 isn't urgent, but it's the natural first slice (worker = Wave-2 domain).

## HITL-gated first deploy — what the USER must do (the irreducible HITL; runbooks document it)
Wave-1 code is done; the first `terraform apply` + `deploy.yml` run needs (these CANNOT be automated):
1. **AWS account** + 1 admin cred for the first apply (creates the OIDC provider + everything).
2. **Domain + Route53 hosted zone delegation** (ACM DNS-validation needs the zone).
3. **GitHub `production` Environment** + required reviewers + the OIDC trust subject (`owner/repo`) — partly `gh`-scriptable.
4. **Auth0 tenant** — API (audience `https://api.wc.<ROOT_DOMAIN>`), SPA app (callback `https://wc.<ROOT_DOMAIN>/callback`), 7 test users + their `app_metadata.employee_id` = the seeded `st6\|<first>-<last>` (Dana→`st6\|dana-okafor`, etc.), the post-login Action emitting the `https://wc.<ROOT_DOMAIN>/employee_id` claim.
5. **M365 E5 trial + Entra app reg** (Graph creds, for Wave-2's live Outlook) — admin-consent `Calendars.ReadWrite`.
6. **Populate ~3 secrets** post-apply via `infra/scripts/populate-secrets.sh` (auth0 now; graph at Wave-2; db is TF-populated; demo is prod no-op).
**Net:** run bootstrap (`terraform-bootstrap` apply) → `terraform apply` → populate secrets → trigger deploy.yml. Everything else automates.

## WAVE-2 scope (the fresh orch's main backend work — ~5 slices; do a ~15-min scoping pass first)
LIVE Outlook + SQS/SNS, now REQUIRED (user decision). Touches **rule #4** (sync never blocks the lifecycle txn) + **rule #7** (pointer-only payloads, secrets via Secrets Manager) → **ad-hoc security review on each** (general-purpose agent — the `security-reviewer` subagent isn't registered).
- **Real SNS gateway** — replace the no-op `LoggingLifecycleSnsGateway`; publish the `SyncJobPointer` (F.2: `{syncRecordId,eventKind,env,traceId}` only) on lifecycle events; afterCommit + swallow (rule #4).
- **Worker SQS consumer** — `@SqsListener` on the `wc-sync` queue (env `SQS_QUEUE_URL`), reload by `syncRecordId`, → DLQ on fail. (The worker also needs its datasource here — pairs with 092.)
- **MS Graph adapter + app-only auth** — Graph calendar create/update + client-credentials token from the `graph` secret (`GRAPH_*`). Note `GRAPH_MODE=demo-success` env exists on both deployments (a stub-mode hook — confirm its meaning in the worker code).
- Worker re-deploy (no new infra — k8s + deploy.yml already carry the worker).
**Scoping pass needed:** confirm the SNS publish seam + the `SyncRecord`/`OutlookCalendarSyncRecord` wiring before authoring the Wave-2 briefs.

## INFRA impl queue (it persists, holding) — runbooks + READMEs + docker-compose
The infra impl committed 087/088/089 and holds. Its remaining Wave-1/docs queue:
- **deploy.yml verify** (largely done — deploy.yml is complete; a final pass).
- **3 runbooks:** (a) setup-everything = EXPAND the drafted `docs/runbooks/auth0-tenant-setup.md` (+ M365 E5 trial + Entra app reg); (b) fresh-AWS→deploy-ready (bootstrap→init→apply→secrets; incl. the `prevent_destroy` teardown caveat + the distinct `wc/bootstrap.tfstate` migrate-state key); (c) actual-deployment (run pipeline + smoke + the `populate-secrets.sh` step). The existing **MVP:~1553** HITL-runbook carry-forward feeds (b)/(c).
- **infra/README** (local + AWS topology).
- Assess a local full-stack **docker-compose** (wc-api + Postgres minimum; LocalStack/Graph-mock optional).

## SEAMS the fresh orch coordinates (infra impl ↔ backend impl)
- **Dockerfile paths:** `apps/wc-api/Dockerfile` + `apps/wc-api/worker/Dockerfile` (done; deploy.yml references them).
- **`/mnt/secrets` configtree:** the SPC mounts each key as a file named by the property key; Spring reads `configtree:/mnt/secrets/` (wc-api LESSONS §42). The worker mounts graph-only.
- **graph secret keys:** `GRAPH_TENANT_ID`/`GRAPH_CLIENT_ID`/`GRAPH_CLIENT_SECRET` (Wave-2 Graph auth).

## VERIFIED OAuth contract (for the runbook + Wave-2 reference)
- Claim: `https://wc.<ROOT_DOMAIN>/employee_id` (Auth0ClaimMapper → external_subject, sub-fallback). Audience: `https://api.wc.<ROOT_DOMAIN>` (the SPA must request it via `getAccessTokenSilently`). SPA origin/callback: `https://wc.<ROOT_DOMAIN>` + `/callback`. The 7 `app_metadata.employee_id` literals = the V5 `st6\|<first>-<last>` values.

## CARRY-FORWARDS (for the fresh orch + next /orchestrate-end triage)
- **092 worker-boot fix** (above) — fresh orch's first slice. _(origin: 091)_
- **Worker `application-prod.yml`→`application-aws.yml`** — fold into 092 (same bug as 090's api fix). _(origin: 090)_
- **`unique(external_subject) WHERE NOT NULL`** partial-unique — defense-in-depth, deferred (MVP relies on seed-distinctness). _(origin: V5/10.2)_
- **§9 command-center `summary` field** + the **3 B.11 follow-ups** (`resolvedDisputeCount`/`unlinkedCount`/`reviewedAt`, origin ST.8b) — bundle as one B.11/§9 command-center-row shape-change round (coordinate the `dtos.ts` mirror with the frontend orch). _(parked from Phase 6)_
- **`rebuild==seed` literal test** — DISCHARGED-by-reframe (projections populate via the rebuild Job; there are no hand-seeded literals to equal). _(origin: 6.7, resolved this round)_
- **GitHub Actions SHA-pin + RDS hardening + tflint-AWS-ruleset** — Phase-13 §20 trims (infra carry-forwards, MVP Phase-12 section).

## CONVENTIONS / state
- **Brief lane: next-free 092.** (085 frontend / 086–091 backend+infra this round.)
- **Shared-doc serialize:** I round-committed my ARCH + MVP_TASKS regions (Appendix-E/§12/D.2 + Phase-10/Phase-12). The **frontend orch's ARCH/MVP edits are round-seal-deferred** (9.16/9.17/§7-MFE regions) — it diff-and-keeps my regions at its seal. The fresh orch re-syncs the brief lane + the shared-doc window two-way with `st6-main-wc-web-orchestrator`.
- **Reviewer policy:** per-slice reviewers OFF; ad-hoc security review (general-purpose agent) on the Wave-2 rule-#4/#7 slices. Backend gate `./gradlew check` from `apps/wc-api/`. Co-Author trailer `Claude Opus 4.8 (1M context)`. Push deferred (no remote).
- **LESSONS banked this round:** wc-api §41 (db/demo-seed location) +addendum (seed-what-services-produce) + §42 (configtree/profile-gotcha); infra §20 (bootstrap-root) + §21 (no-leak put-secret-value) + §22 (runner-job web-type=none).
- **13.3 actuator = SATISFIED-BY-PRIOR** (verified: actuator dep + probes.enabled + `permitAll(/actuator/health/**)` already wired; a stale manifest comment misled — NOT a missing slice).

## FIRST ACTIONS (fresh backend orch)
1. Register (team-registry jq one-liner) → `/orchestrate-start` (this handoff + `docs/planning/025` + brief 086–091 + the OAuth contract).
2. **Dispatch 092 (worker-boot fix)** to the held backend impl FIRST. Re-sync the brief lane (next-free 092) + the shared-doc window with the frontend orch.
3. Do the **Wave-2 ~15-min scoping pass** (SNS publish seam + SyncRecord wiring) → author the Wave-2 briefs (SNS gateway → SQS consumer → Graph adapter) for the backend impl.
4. Keep coordinating the infra impl (runbooks + infra/README + docker-compose) + the seams. Per-slice ping the lead (`team-lead`) after each Step-10.
