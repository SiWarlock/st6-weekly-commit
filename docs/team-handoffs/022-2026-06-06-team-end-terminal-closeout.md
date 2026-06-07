# Team-end terminal close-out — ST6 (2026-06-06)

> **Type: TERMINAL `/team-end` (project wrapped + submitted).** NOT a pause/compaction. Demo recorded, submission committed to `main`, live site styled + Jun 8–14-review-ready. All teammates shut down + registry ghosts cleaned. This doc is the final wrap; the "Resume for the Jun 8–14 review" section is the only thing a future session needs. Builds on handoffs 017–021 + [[deployed-demo-live-runbook]].

## What this session accomplished — the federation-portal arc
Post-deploy demo enhancement: stand up a Module-Federation **host** ("Acme Portal") that embeds the `wc-web` Weekly Commit module as a remote, deployed LIVE, to prove federation. Drove it from a blank/crashing portal to a fully-styled, real-data embed through a 4-bug cascade:

1. **MF shared-scope deadlock** (`2c89708`) — a separate `import('wc_web/store')` did a top-level `await importShared(...)` that deadlocked cross-build init (host hung on "Connecting…"). Fix: remote self-provides its store, single `./WeeklyCommitApp` expose, drop the `./store` expose.
2. **Dual-React crash** (`2f817b8`) — fix-1 had unshared `react-redux`/`@reduxjs/toolkit` → the remote bundled a 2nd React → `Cannot read 'useRef'` (null dispatcher). Fix: re-share both (5 shared singletons total), keep the self-provided store.
3. **Access-token timing** (`b8dc412`) — a §29 regression ACROSS the federation boundary: the host's `getAccessToken` was registered in a mount `useEffect`, but the remote's child RTK-Query first-query effect runs before parent effects → "No access-token provider configured." Fix: synchronous `useState` lazy-init registration in the exposed module (banked **wc-web §31**).
4. **Federated CSS unstyled** (`1b3854a` + test `9091f31`) — @originjs auto-injection drops `assetsDir` under an absolute `--base` (404). Fix: `?url` base-resolved import + manual `<link>` on mount; expose CSS array stays empty (banked **wc-web §32**).

Deploy path: **Deploy-2 Option B** (`da6f29c`) — same-origin portal on a **path** (`/portal/*`) of the existing `wc.` CloudFront distribution (the new-distribution route was blocked by an AWS account-verification gate), with a `portal-spa-rewrite` viewer-request Function + additive `/remote/*` behavior; main site untouched. Fix loop: frontend builds dists → **user runs the prod `s3 sync` + invalidation** (infra's prod write is classifier-blocked) → re-test.

**Live state (curl-verified):** `wc.st6weeklycommit.com/portal/` serves the host (`index-CX3NEaJf.js`) → remote `WeeklyCommitApp-C8CjoUNQ.js` + `theme-DFCn5eCK.css` (200) → real data (Aisha Khan's locked Jun 1–7 plan) inside the Acme Portal frame, fully styled. The earlier (pre-demo) Outlook live-sync proof stands ([[deployed-demo-live-runbook]]).

## Final git state (submission)
- Local `main` HEAD carries the full session (federation fixes + Deploy-2 + this close-out batch). Submission remote = `origin` (gitlab); deploy remote = `github`.
- **The user pushes** (`git push origin main` + optional `git push github main`) — pushes are user-controlled. As of this doc, the close-out batch is committed locally; the user does the final push to finalize the submission.
- `docs/learn-site/` is gitignored (per the user's "layer docs only" call); `docs/demo.md` stays gitignored (local script).

## Lessons banked this session
- **wc-api §50** — week-parameterized demo-seed Job (reset-then-seed two-week footprint; direct-insert not lifecycle-services; `recomputeForPlan` projections; Clock-anchored OVERDUE).
- **wc-web §31/§32/§33** — federation accessor-sync across the boundary / @originjs federated CSS `?url`+`<link>` / @originjs host↔remote cascade (1 expose, 5 shared singletons, remote self-provides store).
- **infra §23** — Deploy-2 Option-B same-origin path federation (additive behavior + rewrite Function; fail-safe-to-remote build trap x-ref wc-web §28; user-gated prod writes).
- **ARCHITECTURE.md** — §7 realized federation surface + **OQ-004 ✅ REALIZED**.

## Carry-forwards (post-demo, all user-gated — recorded in MVP_TASKS.md "Carry-forward")
1. **Jun 8–14 demo seed run** (`SEED_WEEK=2026-06-08`) before the external live review.
2. **AWS CloudFront account-verification** → enables the real cross-origin `portal.` distribution (cert ISSUED, partials dormant ~$0).
3. **Standalone main-site switch** (browser-verified) — serve the standalone artifact at the `wc.` root.
4. **KMS-pin + apex→`wc` redirect** (infra hardening).

## Team roster (all SHUT DOWN + ghosts cleaned at close-out)
- `st6-main-orchestrator` (`763d2090`), `st6-main-wc-api-implementer` (`aa6054b5`), `st6-main-infra-implementer` (`45891d66`), `st6-main-wc-web-implementer` (`4dc78bd1`), dormant `st6-main-wc-web-orchestrator` (`85537232`) — registry + heartbeat files removed.

## Resume for the Jun 8–14 review (only if needed)
Re-stand the team via `/team-start st6-main` ONLY if live changes are needed. Before the review: **run the Jun 8–14 seed** (`SEED_WEEK=2026-06-08` via the seed Job, [[deployed-demo-live-runbook]]) so the matrix is fresh for that week. Live debug constraints (secretsmanager/kubectl-exec/prod-DB blocked; `kubectl logs` + public curl OK; prod s3/CloudFront writes user-run) per [[live-debug-prod-read-constraints]]. Configs in [[deploy-config-values]].
