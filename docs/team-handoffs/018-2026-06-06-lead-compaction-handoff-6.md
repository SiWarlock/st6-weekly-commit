# Lead compaction handoff #6 — ST6 (2026-06-06)

> **Type: COMPACTION handoff (compact-in-place, NOT teardown).** Lead hit **74% WARN** climbing, mid post-deploy demo-enhancement phase. Per [[lead-context-limit-handoff]]: write this, user compacts the lead in place, **teammates stay ALIVE (idle)**. Lead's 6th compaction (prior 001/006/012/016/017). The first deploy is DONE + green; we're now adding demo enhancements + then a wholesale close-out. **Resume = continue the 3 workstreams below, then the close-out.**

## Where we are (the big picture)
- **First full deploy is LIVE + GREEN end-to-end** (app https://wc.st6weeklycommit.com, API https://api.wc.st6weeklycommit.com). Last clean green run carried `e75dd51` (smoke-DNS harden) + `a8cd336` (flaky-fix). Then a **sync-fix redeploy** landed green on `6996318` (awssdk:sts IRSA + worker GRAPH_MODE=real + the §48 hollow-Gate-7 repair). Config in [[deploy-config-values]] + [[deployed-demo-live-runbook]].
- **Demo script REWRITTEN** for the deployed app → `docs/demo.md` (gitignored), comprehensive cut: architecture + infrastructure + the 4 lifecycles (plan/review/dispute/sync, explained + shown) + a Module Federation segment (§4 explain, §6 live host demo). Persona/state matrix from V5+V6 seed.
- **User then asked to ADD** (deferring the close-out): (1) show the sync working in the demo, (2) a Module-Federation HOST app proving wc-web plugs into a parent — **decided: FULLY deploy host+remote**, (3) ensure the script covers arch/infra/lifecycles/federation (done). **Then** the wholesale close-out.

## Deploy mechanics (unchanged)
`git push github main` → `gh workflow run deploy.yml --repo SiWarlock/st6-weekly-commit --ref main` → `gates` (~10min) → **USER approves the `production` gate** → `deploy` job. Watch: background bash polling `gh run view <RID> --json status` for `waiting`(gate)/`completed`. Smoke is now DNS-hardened (`--resolve`).

## THREE active workstreams (resume these)

### 1. Sync demo — BLOCKED on the user's Graph-secret fix (in progress NOW)
- Pipeline is VERIFIED working end-to-end (sts/IRSA ✅, SNS→SQS→worker ✅, GRAPH_MODE=real ✅, worker reaches real Graph ✅). **The ONLY failure: the Graph client secret in `wc/aws/graph` is the secret's ID, not its VALUE** → `AADSTS7000215`. 
- **✅ USER FIXED IT (2026-06-06):** created a new Azure client secret, repopulated `wc/aws/graph` with the **Value** (via `populate-secrets.sh --only graph`), and `kubectl rollout restart deployment/wc-worker`. So the secret blocker is RESOLVED — the next step is the re-test.
- **RESUME ACTION (do this first):** the secret + worker-restart are DONE → have the user **LOCK SAM's plan** (link the unlinked "Explore a weekly-digest email" commitment first → Lock) = a FRESH IC_PLANNING sync (avoids the stuck record + needs no DB reset). Cue `st6-main-infra-implementer` to tail `kubectl logs -n wc deploy/wc-worker -f` + SNS/SQS metrics → expect QUEUED→consume→Graph createEvent SUCCESS→SYNCED + a real event on **sam.carter@dreddy817.onmicrosoft.com**'s calendar (Outlook = outlook.office.com as that mailbox). NOTE: locking Sam consumes the §5a demo-DRAFT fixture — fine if the user records §5a right after; no easy reseed (V6 is ON-CONFLICT-DO-NOTHING).
- **Persona prereq:** each persona you log in as needs `{employee_id, role}` in Auth0 **app_metadata** (NOT user_metadata — that was the big bug; see [[deployed-demo-live-runbook]]). User did Dana; the other 6 need moving (values `st6|<first>-<last>`).
- **Known leftover:** Marco's sync record `14000000-…001` is stuck SYNCING (concurrency race below) — left as-is (not used for the re-test).

### 2. Federation host app — BUILT (local), DEPLOY pending
- `st6-main-wc-web-implementer` (frontend impl, online) built **apps/wc-host** (Vite+React "Acme Portal" shell that mounts the wc_web remote per the host contract: host owns BrowserRouter + Auth0 + imports `wc_web/store` for the single baseApi + passes `getAccessToken`). Committed **`14cdb39` (local, NOT pushed)**. Includes 2 wc-web vite.config changes (expose `./store` + share react-router-dom) — additive, **310/310 green, standalone build verified unaffected** (deployed site safe).
- **RESUME ACTION (the federation deploy, route to `st6-main-infra-implementer` + the frontend impl):**
  - Build cmds/dist (from the impl's report): REMOTE = `VITE_AUTH_MODE=auth0 VITE_API_BASE_URL=https://api.wc.st6weeklycommit.com yarn build:remote` → `apps/wc-web/dist/assets/remoteEntry.js` (+ federation chunks); HOST = `VITE_WC_REMOTE_URL=<deployed remoteEntry URL> yarn build` in apps/wc-host → `apps/wc-host/dist/`.
  - Infra: deploy host to **portal.st6weeklycommit.com** (S3 + CloudFront + **ACM SAN** [⚠ cert re-validation — isolate from any "clean green" run] + Route53); publish the **remote build** to a CORS-accessible HTTPS origin that sends `Access-Control-Allow-Origin: https://portal.st6weeklycommit.com` on remoteEntry.js + chunks; tell the frontend impl that final remote URL → it rebuilds the host with `VITE_WC_REMOTE_URL`.
  - **wc-api CORS:** add `https://portal.st6weeklycommit.com` to the api CORS allow-list (deployment-api.yaml `CORS_ALLOWED_ORIGINS` → comma-sep `https://wc.${ROOT_DOMAIN},https://portal.${ROOT_DOMAIN}`) → api redeploy. (Embedded remote's live-API calls 403 without this — confirmed.)
  - **USER task:** add the host origin to the Auth0 SPA client (psB6r4UN…): Allowed Callback `https://portal.st6weeklycommit.com/callback`, Web Origins + Logout `https://portal.st6weeklycommit.com`.
  - Runtime QA on the deployed portal (user's browser: login → WC mounts in the portal → live data, no invalid-hook). Then finalize demo §6.

### 3. Worker concurrency bug — routed to backend orch (follow-up, NOT blocking)
- Worker: concurrent/at-least-once delivery → TOCTOU in the §098 status guard → `OptimisticLockingFailure` → record stuck SYNCING (no DLQ). Routed to `st6-main-orchestrator` to scope+fix (concurrency=1 / visibility-timeout / atomic status-claim / failure→FAILED-not-stuck + a concurrency test). Bundle into the next deploy. Awaiting its design+hash.

## Local git state (nothing auto-pushed)
- github main = `6996318` (deployed). Local main ahead: `9c04cd7` (orch round-seal docs, brief 103/§48-addendum) + `14cdb39` (frontend host + wc-web ./store/react-router-dom). Working tree: docs/demo.md (gitignored), apps/wc-host, docs/layers, learn-site, handoffs, etc. **Next push (the federation deploy) bundles 9c04cd7 + 14cdb39 + the infra fed-deploy commits + any concurrency-fix.**

## Team roster (all ALIVE/idle through compaction; address lead as `team-lead`)
- `st6-main-orchestrator` (b66b675d, ~31%) — backend orch; has the concurrency-bug follow-up.
- `st6-main-wc-api-implementer` (c1ba3c12, ~25%) — backend impl.
- `st6-main-infra-implementer` (b31b8da3, ~47%) — infra/deploy expert; watches the sync re-test + does the federation deploy. Prod-DB writes + `kubectl exec` + `secretsmanager get-value` are classifier-blocked for it ([[live-debug-prod-read-constraints]]).
- `st6-main-wc-web-implementer` (NEW, ~20%) — frontend; built apps/wc-host; standing by for the host rebuild with the final remote URL.
- `st6-main-wc-web-orchestrator` (85537232) — **DORMANT GHOST** (~35h stale); never engaged this phase. **Clean its registry+heartbeat during the close-out** ([[cycle-cleanup-stale-registry]]).
- Spawned teammates launch in $HOME → manual-command pattern ([[spawned-teammates-launch-in-home]]).

## PENDING (after the 3 workstreams): the wholesale CLOSE-OUT (user-requested, deferred)
Run /orchestrate-end (orch) + /session-end (impls, via manual pattern) + /team-end (lead) + **shutdown ALL spawns** + clean all registry/heartbeat ghosts. Carry-forwards to capture: apex→wc 301 redirect (separate terraform deploy, cert-SAN re-validation isolated); KMS-pin (`kms_key_administrators=[admin,CI]`); the worker concurrency fix; persona app_metadata move (other 6); the demo recording (by 2026-06-07, real clock).

## RESUME PROMPT (user sends after compacting)
> Resume the ST6 team lead from `docs/team-handoffs/018-2026-06-06-lead-compaction-handoff-6.md`. First deploy is LIVE+green; we're mid post-deploy demo-enhancement. 3 workstreams: (1) sync demo — I'm fixing the Graph client secret (was the ID not the Value); when done, have me lock Sam to re-test → infra watches → real Outlook event; (2) federation host app — built (14cdb39 local), needs the full deploy to portal.st6weeklycommit.com + wc-api CORS + my Auth0 portal origins; (3) worker concurrency bug — backend follow-up. THEN the wholesale close-out (shutdown all spawns + /team-end). Config in the deploy-config-values + deployed-demo-live-runbook memories.
