# Lead compaction handoff #5 — ST6 (2026-06-05)

> **Type: COMPACTION handoff (compact-in-place, NOT teardown).** Lead hit **77% [ACTION]** mid-deploy (canonical `/context-check`, surfaced by the orch). Per [[lead-context-limit-handoff]]: write this, user compacts the lead in place, **teammates stay ALIVE (paused idle)**. Lead's 5th compaction (prior: 001, 006, 012, 016). **We are mid-way through the FIRST live deploy — iteratively debugging the never-before-run pipeline. Resume = continue the deploy.**

## Config values → [[deploy-config-values]] memory (auto-loads)
Domain `st6weeklycommit.com`, AWS `554608989058`/us-east-1/`wc-deploy-admin`, Auth0 `dev-pw0um8pz8cgr31eo.us.auth0.com`, M365 `dreddy817.onmicrosoft.com` + Graph app, GitHub deploy repo `SiWarlock/st6-weekly-commit`, EKS `wc-aws`. GitHub Environment `production` + 8 vars set (incl. `ADMIN_PRINCIPAL_ARN`). Secrets auth0/graph/db all populated. **All external setup (Auth0 + M365 + 7 mailboxes) DONE.**

## State (re-verify via git + gh)
- The whole deployed-demo is built + sealed; the **drift-fix round (096 c62fcce, 097 a9ca31b, 098 639044b, re-seed 87daf80) is committed + round-sealed a1ec5ff**.
- **The first deploy is iterating through "first-time-run" failures.** Deploy = push `main` to `github` + `gh workflow run deploy.yml --repo SiWarlock/st6-weekly-commit --ref main` → `gates` (all GREEN now, ~10min) → **`production` reviewer gate (USER clicks Approve)** → `deploy` job (terraform apply → migrate+seed → rebuild-projections → roll → publish SPA → smoke).

## Deploy-issue chain (all FIXED unless noted) — the iterative pattern
1. **e2e gate** `b08f466` — `nx e2e wc-e2e` was a nonexistent target → swapped to `wc-e2e:typecheck`.
2. **prettier gate** `46e7205`+`a5d7433` — root `prettier --check .` broken under Berry → `yarn workspace wc-web format:check` + prettier-wrote 24 files.
3. **corepack** `412f199` — CI runner had yarn 1.x; added `corepack enable` (both jobs) for the pinned yarn 4.5.3.
4. **CI-role ssm + EKS access-entry churn** `8f8bf60` — granted `ssm:GetParameter` on `…/eks/*` + `enable_cluster_creator_admin_permissions=false` + stable `admin` entry (state-mv'd) + `ADMIN_PRINCIPAL_ARN` var. (Human-admin→CI-role apply handoff.)
5. **CSIDriver tokenRequests** `cdee04f` — Secrets Store CSI driver lacked `spec.tokenRequests` → db secret never mounted (FailedMount). Added `tokenRequests=[{audience=sts.amazonaws.com}]` in addons.tf.
5b. **SNS_TOPIC_ARN no-default boot crash** `8e593f0` — `AwsSnsLifecycleGateway @ConditionalOnProperty(app.sns.topic-arn)` + no default → migration/cronjob/rebuild Jobs (no SNS_TOPIC_ARN) fail Spring init. Empty-default + length-expression gate.
6 + 6b. **Postgres driver + flyway-database-postgresql test-scoped** — `org.postgresql:postgresql` AND (Flyway 10) `org.flywaydb:flyway-database-postgresql` were `testImplementation`-only → absent from the bootJars → migration `Failed to load driver class org.postgresql.Driver`. Fix = `runtimeOnly` both on api + worker. **BEING COMMITTED by the backend impl now** (the commit AFTER `8e593f0` — get the hash via `git log --oneline -3`). This is the LAST KNOWN blocker; run 26989514222 failed here.

## RESUME ACTIONS (in order)
1. **Confirm the #6/#6b commit landed** (`git log` — latest after `8e593f0`, a `fix(wc-api)`/build commit adding the runtimeOnly postgres + flyway-database-postgresql). If not yet committed, the orch/impl are finishing it (paused-idle) — re-engage the orch.
2. **Push + re-trigger:** `git push github main` → `gh workflow run deploy.yml --repo SiWarlock/st6-weekly-commit --ref main`. Get the run URL.
3. **Watch to the approval gate** (background Bash polling `gh run view <RID> --json status` until `status==waiting` or `completed`; on `waiting` → **ping the USER to re-approve** at the run URL → Review deployments → `production` → Approve and deploy).
4. **Watch the deploy job to completion** (poll until `status==completed`; on `failure` → `gh run view <RID> --log-failed | tail -60`, diagnose the next domino, route it: **infra/CI/terraform/k8s → `st6-main-infra-implementer`** (deploy expert, standing by), **backend app/build → `st6-main-orchestrator`** → impl; then re-push + re-trigger). The migration runs Flyway + seeds the 7 `@dreddy817` personas. After migration: rebuild-projections → roll → publish SPA → smoke.
5. **On SUCCESS (run conclusion=success):** the app is LIVE. Smoke: `https://wc.st6weeklycommit.com` → Auth0 login as Dana (`dana.okafor@st6demo.com`) → IC workspace + manager command-center (6 reports) + heatmap; create a commitment → it syncs to the real Outlook calendar (`dana.okafor@dreddy817.onmicrosoft.com`). Then the live-demo QA (frontend orch + a fresh frontend impl, real-browser).

## Team state (all ALIVE, paused idle through the compaction)
- **`st6-main-orchestrator`** (backend orch, ~66%) — persists; deploy-support (routes/scopes backend deploy-fixes to the impl). Holds 099-perf-seed + the **KMS-pin carry-forward** (post-deploy hardening: `kms_key_administrators=[admin,CI]`) + its LESSONS/MVP deploy-fix doc notes for a later commit.
- **`st6-main-wc-api-implementer`** (~74%, idle) — backend deploy-fix slices. If it nears HARD-STOP, cycle it (lead owns registry lifecycle — [[cycle-cleanup-stale-registry]]).
- **`st6-main-infra-implementer`** (deploy expert, idle) — the CI/terraform/k8s deploy-fixer; has cluster-admin (admin access entry) for live `kubectl` diagnosis.
- **`st6-main-wc-web-orchestrator`** (idle) — for the post-deploy live-demo QA.
- Comms: teammates address the lead as `team-lead` ([[lead-handle-is-team-lead]]). Spawned teammates launch in $HOME ([[spawned-teammates-launch-in-home]]).

## STANDING FACTS
- **Deploy is HITL** — lead pushes to github + triggers; the **USER clicks the `production` Approve gate** each run (per-run reviewer). Nothing AWS-touching runs until that click.
- Local gates dry-run can't catch CI-env/infra-handoff/first-real-run issues — that's why the chain above only surfaced on live runs. Each run gets materially further; we're at the migration step (deepest), ~all the pipeline traversed.
- Next-free handoff = 018.

## RESUME PROMPT (user sends after compacting)
> Resume the ST6 team lead from `docs/team-handoffs/017-2026-06-05-lead-compaction-handoff-5.md`. We're mid first-deploy, iteratively fixing never-run-pipeline issues (chain #1–#6/#6b, all fixed). Resume: confirm the #6/#6b postgres-driver commit landed (`git log`), push `main` to github + re-trigger `deploy.yml`, watch to the approval gate, ping me to re-approve, then watch the deploy job — route any next domino (infra→infra-impl, backend→orch) + re-push/re-trigger, or on success smoke the live app at `https://wc.st6weeklycommit.com`. Config in the deploy-config-values memory.
