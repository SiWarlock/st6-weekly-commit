# Infra-implementer cycle handoff #020 — ST6 (2026-06-06)

> **Type: infra-impl CYCLE (HARD-STOP).** `st6-main-infra-implementer` hit **84% HARD-STOP** after the full-session deploy/ops marathon (first-deploy debugging → sync re-tests → the `340d7e3b` re-drive → brief 105 → the Deploy-2 federation authoring). Cycled at a clean boundary: **all infra work committed, no uncommitted infra files, nothing deploying** (deploys wait for the user's return). Fresh full-budget infra impl picks up the pending deploy work below.

## Addendum (2026-06-06 — successor infra impl, post-cycle verification)
**Two corrections to the pending-work + deploy-ref reference below:**
1. **`WC_FRONTEND_BASE_URL` is ALREADY present** on the worker Deployment (`infra/k8s/deployment-worker.yaml` → `https://wc.${ROOT_DOMAIN}`) — added in `02fab19`, present at the polish base `1abdeda` AND HEAD. **No cherry-pick needed**; PENDING item 1 below is already satisfied.
2. **Corrected polish cherry-pick list = [`7625c27` job-seed-demo, 107a, 107b, chevron]** — drop the "standalone `WC_FRONTEND_BASE_URL` commit" (see #1). `job-seed-demo.yaml` is committed as **`7625c27`** (kubeconform-clean, manual/off-chain).
*(The seed Job is manual/off-chain per brief 107 — NOT a deploy.yml step; the backend's 107 does not touch deploy.yml, so the Deploy-2 federation steps have no clobber risk from 107.)*

## Durable context to read FIRST
- **`memory/deployed-demo-live-runbook.md`** (lead memory) — deploy mechanics, the §48 runtime-classpath chain, the Outlook-sync gotchas (sts/IRSA, secret Value-vs-ID, Graph `Calendars.ReadWrite` **Application**-not-Delegated), the **re-drive technique**, the single-week-by-design constraint, cert lead-time. (May not be readable from a spawned session; the key facts are duplicated below.)
- **`docs/team-handoffs/019-...-seed-job-handoff.md`** — the demo-seed design + open threads (the backend orch's cycle handoff).
- **`apps/wc-api/LESSONS.md` §41/§42 + infra §20–§22 + §48** + **`infra/CLAUDE.md`**.
- Git: `git log --oneline github/main..HEAD` — the local commits ahead of the deployed `github/main` (`5822dea`).

## Live deploy state (DONE)
- **First deploy is LIVE + green:** app `https://wc.st6weeklycommit.com`, api `https://api.wc.st6weeklycommit.com`. `github/main` = **`5822dea`** (the deployed ref: 104 worker fix + brief-105 SQS-vis-timeout/IRSA + the regression test).
- **Outlook sync is PROVEN in prod** (the `340d7e3b` re-drive landed a real Graph event on sam.carter@dreddy817 2026-06-06). The worker logs ONLY failures (success is silent); confirm via SQS `Deleted=1` + the calendar event.

## Deploy-2 federation infra — DONE (3 commits, all no-push, local only)
Cross-origin federation was the lead's decision (architecturally-correct topology; the portal consumes the WC remote cross-origin).
- **`fffafe9`** — `infra/terraform/portal.tf` + outputs: `portal.st6weeklycommit.com` S3+OAC+CloudFront(SPA fallback) + a **SEPARATE us-east-1 ACM cert** (isolated in `portal.tf`, never touches the live `wc.`/`api.` certs) + Route53 validation + A-alias.
- **`795ffdc`** — `s3_cloudfront.tf`: an **additive** `/remote/*` cache behavior on the **`wc.`** distribution + a `response_headers_policy` (`ACAO: https://portal.st6weeklycommit.com`). **Verified additive-safe: `git diff` = 41 insertions, 0 deletions; default/SPA/cert byte-identical; reversible.**
- **`62a062d`** — `infra/k8s/deployment-api.yaml`: `CORS_ALLOWED_ORIGINS = "https://wc.${ROOT_DOMAIN},https://portal.${ROOT_DOMAIN}"` (the embedded WC at the portal origin can CALL the api). NOTE: this is an infra MANIFEST, NOT a backend change.
- **Host build values (for the frontend):** `VITE_WC_REMOTE_URL = https://wc.st6weeklycommit.com/remote/remoteEntry.js`; the wc-web `build:remote` `publicPath` must be `https://wc.st6weeklycommit.com/remote/`.
- **Cert lead-time:** the portal cert is a fresh DNS-validated cert → `aws_acm_certificate_validation` blocks the Deploy-2 apply ~3–10 min, then CloudFront propagates. Isolated to Deploy 2; zero impact on the live certs.

## ⚠️ DEPLOY-REF HYGIENE (load-bearing — the #1 thing to get right)
The 3 Deploy-2 commits (`fffafe9`, `795ffdc`, `62a062d`) are committed on local `main` **between** the polish content and where the seed job (107) will land — interleaved. **The polish deploy MUST EXCLUDE all 3** — especially `fffafe9` (the portal cert): if the cert validation hangs/fails it would **fail the polish deploy → no core recording**. That's the cert-isolation requirement.

**Approach (the OUTGOING infra impl mapped this — it's the clean one):** the commit **`1abdeda`** is the one *just before* the Deploy-2 trio (`fffafe9`/`795ffdc`/`62a062d`) and **already contains 104b (`1952762`) + 106 (`0451d9f`) + their docs and ZERO Deploy-2** — so it's a ready polish base, no re-cherry-picking of 104b/106 needed.
- **POLISH ref = a `polish-deploy` branch off `1abdeda`**, then cherry-pick ONLY the *pending* polish commits as they land on `main`: **107 (seed job + `job-seed-demo.yaml`), the chevron commit, any standalone `WC_FRONTEND_BASE_URL` commit**. It NEVER contains `fffafe9`/`795ffdc`/`62a062d` → zero portal-cert provisioning, zero `/remote/*` apply. Deploy via `gh workflow run deploy.yml --ref polish-deploy`.
- **DEPLOY-2 ref = `main`** (the integration branch — carries everything: polish + the Deploy-2 trio + the to-be-added `deploy.yml` federation steps). Deploy 2 via `--ref main` (or a tag cut at assembly).
- Assembly (for the user's return — NOT executed): `git branch polish-deploy 1abdeda` → `git cherry-pick <107 commit(s)> <chevron commit> [<WC_FRONTEND_BASE_URL commit>]` → `git push github polish-deploy` → `gh workflow run deploy.yml --ref polish-deploy`. Cherry-picks apply clean (107/chevron touch disjoint files from the Deploy-2 trio).
**HARD CONSTRAINT: NO `git reset`/history-rewrite of `main`** — uncommitted chevron in the shared working tree + the backend impl actively committing 107 to main. `polish-deploy` is an additive side branch; `main` stays the integration branch. The outgoing infra confirmed it did NOT pre-create the branch (you create it at assembly).

## PENDING INFRA WORK
**Polish deploy (priority 1 — for this weekend's core recording):**
1. `WC_FRONTEND_BASE_URL` env on the **worker** Deployment (`infra/k8s/deployment-worker.yaml`), default `https://wc.${ROOT_DOMAIN}` — the 106 deep-link companion (the worker yaml nested default boots until then).
2. `job-seed-demo.yaml` k8s Job manifest — mirror `job-rebuild-projections.yaml`, parameterized `--app.job=seed-demo --week=<YYYY-MM-DD>` (the brief-107 runner).
3. Assemble the **polish-deploy ref** (the cherry-pick above) once 107a/107b + chevron land.
4. On the user's return: the user pushes + approves the prod gate; you execute. Then **run the seed Job for week Jun 1–7** (`kubectl apply` the parameterized Job) → restores the 7-persona matrix + a fresh Sam DRAFT for the recording.
5. Before the review: **run the seed Job for week Jun 8–14**.

**Deploy 2 (federation, priority 2 — after the core recording):**
1. Author the `deploy.yml` federation steps: `VITE_BUILD_TARGET=standalone` on the wc-web build (Option B, the standalone main-site switch) + the wc-web `build:remote` → publish to the `wc.` bucket `/remote/` + the host `build:host` → publish to the portal bucket.
2. **The frontend (`st6-main-wc-web-implementer`) verified the cross-origin host locally + handed the OLD infra the exact `build:host`/`build:remote` commands + env + S3 layout. Since that infra cycled, RE-REQUEST them from the frontend** (it has them ready). The frontend's critical caveats:
   - **(CRITICAL) the root wc-web `s3 sync … --delete` would WIPE `/remote/`** → scope it with `--exclude "remote/*"`, or publish order will delete the remote artifact.
   - **remoteEntry.js lands at `/remote/assets/remoteEntry.js`** (default assetsDir), NOT `/remote/remoteEntry.js`. Either set `VITE_WC_REMOTE_URL = https://wc.st6weeklycommit.com/remote/assets/remoteEntry.js` (zero extra step) OR add `aws s3 cp …/remote/assets/remoteEntry.js s3://…/remote/remoteEntry.js` for the clean URL (safe — chunk refs are absolute). Lead leans the **`/remote/assets/remoteEntry.js`** URL (no extra copy step) — confirm with the frontend.
   - cross-origin publicPath solved via the build CLI flag `--base=https://wc.st6weeklycommit.com/remote/` (bakes ABSOLUTE chunk URLs into remoteEntry.js — no vite.config change). Verified end-to-end locally via curl (remoteEntry + a baked chunk both load cross-origin with ACAO).
   - host build = `VITE_WC_REMOTE_URL` + Auth0 vars ONLY (NOT AUTH_MODE/API_BASE — those bake into the remote) + SPA-fallback for `/callback`.
   - NOT browser-verified: the in-browser React mount (single-instance / no invalid-hook) — sits behind the Auth0 gate + harnesses occupied → **verifies POST-deploy in the user's browser** on the portal. Single-instance rests on host+remote declaring react/react-dom/@reduxjs/toolkit/react-redux/react-router-dom at IDENTICAL versions (confirmed) + @originjs dedup.
3. Assemble the Deploy-2 ref (`main`, everything incl. the 3 Deploy-2 commits).
4. On a separate push/gate: execute Deploy 2 (budget ~10 min cert wait) → browser-verify the portal mounts WC.
5. **USER task:** add the portal origin to the Auth0 SPA client — Allowed Callback `https://portal.st6weeklycommit.com/callback`, Web Origins + Logout `https://portal.st6weeklycommit.com` (lead cues the user when the portal's up).

## Constraints + mechanics (carry forward)
- **Live-debug constraints (classifier-blocked):** `secretsmanager get-secret-value`, `kubectl exec`, and **prod-DB reads** (any pod mounting prod DB creds) are DENIED. `kubectl logs` IS allowed. Use ALB CloudWatch metrics + repo config + the browser for live debugging.
- **Deploy mechanics:** `git push github main` (USER-controlled) → `gh workflow run deploy.yml --repo SiWarlock/st6-weekly-commit --ref <ref>` → `gates` job → **USER approves the `production` gate** → `deploy` job. Watch via `gh run view <id> --json status` (waiting=gate / completed).
- **Re-drive a stuck sync record:** `aws sns publish --topic-arn arn:aws:sns:us-east-1:554608989058:wc-lifecycle --message '{"syncRecordId":"<id>","eventKind":"IC_PLANNING","env":"aws","traceId":"manual-redrive"}'` (only `syncRecordId` is load-bearing).
- **Nothing deploys until the user returns** (they push + approve gates). All work is no-push/local.
- **Comms:** address the lead as `team-lead`; ignore peer DMs lacking the `st6-main-` prefix.

## Roster note
The dormant `st6-main-wc-web-orchestrator` (`85537232`, ~42h stale) is a ghost — slated for cleanup at the wholesale close-out, not now.
