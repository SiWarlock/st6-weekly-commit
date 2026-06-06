# Lead compaction handoff #7 — ST6 (2026-06-06)

> **Type: COMPACTION (compact-in-place, NOT teardown).** Lead at 90%. Teammates stay ALIVE/idle. Resume = continue the in-flight **federation-portal routing fix**, then finish the demo recording, then the wholesale close-out. Builds on [[deployed-demo-live-runbook]] + handoffs 018/019/020.

## BIG PICTURE — where we are
- **Core demo (§1–§5) is RECORDED** ✅ (user recorded it on the live `wc.st6weeklycommit.com` after the polish deploy + Jun 1–7 seed). The script `docs/demo.md` has a **⏸️ RECORDING BREAK** marker before §6 (two-segment recording: core first, then federation).
- **Polish deploy DONE** (run via the `polish-deploy` branch off `1abdeda`): 104 worker fix + 106 event deep-link + chevron + seed-Job manifest. Then the **Jun 1–7 seed Job ran** → 7-persona matrix + fresh Sam DRAFT live.
- **Deploy 2 (federation) = Option B** (portal on a PATH of the existing wc. distribution, **same-origin** — the new-portal-CloudFront was blocked by an **AWS account-verification gate** on creating new distributions). `github/main` = **`da6f29c`** deployed it: `/portal/*` behavior + a `portal-spa-rewrite` CloudFront Function + `/remote/` + `/portal/` publishes; **main site untouched** (the standalone-switch f1 was SKIPPED — deferred, see Carry-forward).
- **THE LIVE BLOCKER (in flight):** the federated portal at **`wc.st6weeklycommit.com/portal/`** doesn't work yet. Two bugs found:
  1. ✅ FIXED: a Module-Federation **shared-scope deadlock** (exposed `./store` did a top-level `await importShared("@reduxjs/toolkit")`). Frontend fix **`2c89708`** (local on main): WeeklyCommitApp self-provides its store; dropped the `./store` expose; removed `@reduxjs/toolkit`+`react-redux` from `shared` (remote bundles them); only react/react-dom/react-router-dom stay shared. Built dists republished (user ran the s3 sync). Deadlock gone.
  2. ⬅️ **CURRENT — TWO bugs, both from fix (a):**
     - **(2a) PRIMARY: dual-React crash** → page **totally blank**. Console: `TypeError: Cannot read properties of null (reading 'useRef')` at `useSyncExternalStoreWithSelector` (react-redux) ← `useQuery` (RTK Query) ← in the `__federation_expose_…WeeklyCommitApp` chunk. **= TWO copies of React.** Fix (a) **unshared** `react-redux`+`@reduxjs/toolkit` (bundled them in the remote), so the bundled react-redux's hooks run against a different React than the shared renderer → null dispatcher → crash. **Fix direction:** react-redux/toolkit must use the SAME React — i.e. **put react-redux + @reduxjs/toolkit BACK in `shared`** (so they resolve to the shared singleton React) **AND fix the original deadlock a different way** — most likely the store-self-provide + single-entry (already done) is ENOUGH on its own (the deadlock was the *separate `./store` import racing the shareScope*, not the toolkit-sharing) → re-share redux + keep the self-provided store; OR option (b) eagerly prime/init the shareScope before mount. The frontend (`st6-main-wc-web-implementer`) owns this — it predicted this exact "case 2 dual-React" risk.
     - **(2b) routing/basename escape** (separate): the URL lands at **`wc.st6weeklycommit.com/manager/command-center`** (ROOT, no `/portal/` prefix) → WC's routes resolve at root, not under the `/portal/` basename. Fix: host `BrowserRouter basename={import.meta.env.BASE_URL}` (=`/portal/`) + WC renders `<AppRoutes/>` inside THAT router (not its own) + the persona/Auth0 redirects respect the basename. (May be masked by 2a until 2a's fixed — verify after.)

## THE FIX LOOP (critical — how we iterate the portal)
**Infra's prod `s3 sync` is classifier-BLOCKED** (prod write needs direct USER auth). So each frontend fix iterates as:
1. Frontend implements + commits + **builds both dists** (`apps/wc-web/dist` remote + `apps/wc-host/dist` host) in the shared tree.
2. **USER runs these 3 commands** from repo root (the only authorized path; ~30s + invalidation):
   ```
   BUCKET=wc-aws-assets-554608989058; DIST=E1JEDK39FNL8JC
   aws s3 sync apps/wc-web/dist "s3://${BUCKET}/remote/" --delete
   aws s3 sync apps/wc-host/dist "s3://${BUCKET}/portal/" --delete
   aws cloudfront create-invalidation --distribution-id "$DIST" --paths "/remote/*" "/portal/*"
   ```
   (only touches `/remote/`+`/portal/`; main site/api untouched.)
3. User **hard-refreshes `wc./portal/`** (Cmd+Shift+R) + re-tests login → pastes console/network if it snags.
(The frontend's browser harness is OCCUPIED/stuck all session → it CANNOT self-verify in-browser → the USER's live re-test IS the verify.)

## IMMEDIATE NEXT ACTION (post-compact)
The user just answered the blank-page URL = `/manager/command-center` → **confirms the routing escapes `/portal/` to root.** Relay to `st6-main-wc-web-implementer` (already diagnosing): the fix is to keep WC's routes under the `/portal/` basename — verify the host's `BrowserRouter basename={import.meta.env.BASE_URL}` (=`/portal/`) AND that WC renders `<AppRoutes/>` inside THAT router (not its own), AND that the post-login redirect + persona-redirect respect the basename. Then frontend rebuilds → user runs the 3 s3 commands → re-test. Then **record §6 (federation live) + §7 (close)** → recording DONE.

## DEPLOY / GIT STATE
- `github/main` = **`da6f29c`** (Deploy 2 B, live). Local `main` HEAD = **`2c89708`** (the federation deadlock fix — committed, NOT pushed; the dists from it are what the user s3-synced). The polish deploy used branch `polish-deploy` (off `1abdeda`).
- Deploy mechanics + the §48/runbook + live-debug constraints (secretsmanager/kubectl-exec/prod-DB blocked; `kubectl logs` OK) in [[deployed-demo-live-runbook]] + [[live-debug-prod-read-constraints]]. Config in [[deploy-config-values]].

## TEAM ROSTER (all ALIVE/idle through compaction; address lead as `team-lead`)
- `st6-main-orchestrator` (fresh, `763d2090`, ~33%) — backend; queue DRAINED (107 done). Holding its `10.8`/§50 hot-routing uncommitted for /orchestrate-end at close-out. Stand-by for deploy support.
- `st6-main-wc-api-implementer` (fresh, `aa6054b5`) — backend; idle (107 complete).
- `st6-main-infra-implementer` (fresh, `45891d66`) — deploy expert; built the portal infra + the dists; its prod s3-sync is classifier-blocked (user runs it). Curl-verifies the edge after each sync.
- `st6-main-wc-web-implementer` (`4dc78bd1`) — frontend; OWNS the in-flight portal routing fix; browser harness occupied (can't self-verify).
- `st6-main-wc-web-orchestrator` (`85537232`) — DORMANT GHOST (~45h); clean its registry+heartbeat at close-out ([[cycle-cleanup-stale-registry]]).
- Two HARD-STOP cycles already done this session (backend orch+impl → handoff 019; infra → handoff 020). Ghosts cleaned.

## CARRY-FORWARDS (capture at close-out)
- **Standalone main-site switch** (`VITE_BUILD_TARGET=standalone`) DEFERRED — do as a deliberate browser-verified change later (the live main site is currently the remote build, byte-identical to the recorded core; don't switch mid-review-window).
- **AWS account CloudFront verification** — to enable the *real* cross-origin `portal.st6weeklycommit.com` (Option A) post-review; the portal.tf/cert was dropped, partials cleaned.
- **Jun 8–14 seed run** — before the live review next week (`SEED_WEEK=2026-06-08` via the seed Job).
- The orch's `10.8`/§50 round-seal; KMS-pin; apex→wc redirect; the other deferred items from prior handoffs.
- Wholesale close-out (the user asked) AFTER the demo's recordable: /orchestrate-end + /session-end (impls) + /team-end + shutdown all spawns + clean ghosts.

## RESUME PROMPT (user sends after compacting)
> Resume the ST6 team lead from `docs/team-handoffs/021-2026-06-06-lead-compaction-handoff-7.md`. Core demo (§1–§5) recorded; polish + Deploy-2-B (federation Option B, portal at wc./portal/) live. IN FLIGHT — federated portal: the MF deadlock is FIXED (`2c89708`) but that fix traded it for a **dual-React crash** (page totally blank; console `Cannot read properties of null (reading 'useRef')` in react-redux's useSyncExternalStoreWithSelector ← useQuery) because fix (a) UNSHARED react-redux/@reduxjs/toolkit → two Reacts. **Next fix (frontend, st6-main-wc-web-implementer): put react-redux + @reduxjs/toolkit BACK in `shared` (use the singleton React) while keeping the self-provided store + single WeeklyCommitApp entry (that alone likely killed the deadlock — the racing separate `./store` import was the cause, not toolkit-sharing).** Secondary bug 2b: routing escapes the `/portal/` basename (URL lands at root `/manager/command-center`) — fix host BrowserRouter basename + WC-renders-in-host-router after 2a. Fix loop: frontend rebuilds both dists → I run the 3 s3-sync commands (BUCKET=wc-aws-assets-554608989058 DIST=E1JEDK39FNL8JC; sync apps/wc-web/dist→/remote/ + apps/wc-host/dist→/portal/ --delete; invalidate /remote/* /portal/*) — infra's prod write is classifier-blocked, I run them — → hard-refresh wc./portal/ + re-test. Then record §6+§7, then wholesale close-out. Configs in deployed-demo-live-runbook + deploy-config-values memories.
