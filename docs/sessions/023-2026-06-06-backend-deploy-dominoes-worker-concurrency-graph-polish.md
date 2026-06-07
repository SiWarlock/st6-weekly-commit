# Session 023 — Backend deploy-dominoes + worker-concurrency hardening + Graph-event polish

- **Date:** 2026-06-06
- **Phase:** First-deploy support → live-sync hardening → demo polish. This implementer session ran as the persistent backend deploy-support pair, cycled at the clean 106-seal boundary (ctx ACTION threshold).
- **Role:** **implementer-authored** (`st6-main-wc-api-implementer`, the fresh successor that started by sealing deploy-fix #9). Every slice ran the `/tdd` discipline with the orchestrator (`st6-main-orchestrator`) doing Step-2.5 review + Step-9 routing.
- **Predecessor:** [022](022-2026-06-04-backend-deploy-blocking-drift-fix-arc.md) (096→097→098 drift-fix arc). **Successor:** [026](026-2026-06-06-backend-closeout.md) (demo-seed Job 107a/107b — terminal backend session).
- **HEAD at close:** `672e09d` (106 round-seal). My implementer commits: `a8cd336` (102), `6996318` (103), `c45a7a6` (104), `5822dea` (regression), `1952762` (104b), `0451d9f` (106).

## Why this session existed

The first production deploy was mid-flight; a fresh successor took over to seal the never-run-pipeline boot dominoes and then harden the live Outlook sync. The arc: verify the already-committed boot fixes → kill a flaky CI test → ship the missing IRSA classpath dep → fix a real worker concurrency bug surfaced by the live dry-run → make the recovery safe (idempotency) → polish the synced event content. The live sync proved out in production during the session.

## What was built

### Verified (no new commit)
- **Deploy-fix #9** (`fac73c8`, prior impl): web-gated `SecurityConfig`/`JwtConfig`/`Auth0ClaimMapper` so non-web batch Jobs boot. Found it was already committed (not in the working tree as the lead believed); ran the full gate + a non-cached config-test rerun → GREEN; routed the hash, no duplicate commit.

### 102 — Deterministic worker PII-sanitization assertions (`a8cd336`, brief 102) — test-only
The `:worker` rule-#7/§44 sanitized-rethrow tests asserted `.hasMessageNotContaining("403")` against an ids-only message embedding a random record UUID; `"403"` is pure hex → ~0.5% flake. Replaced the 3× `notContaining` triplet with one **exact-message** assertion at 3 sites (the 2 flaky + the 098 retry site, §48 audit-the-class); strictly stronger + id-agnostic. Deterministic RED (worst-case `00000403-…` id) proved the diagnosis.

### 103 — `awssdk:sts` in the api+worker runtime classpath for IRSA (`6996318`, brief 103) — CRITICAL PATH
AWS SDK v2 IRSA needs `software.amazon.awssdk:sts`; the spring-cloud-aws starters don't pull it transitively → the live worker never consumed SQS + api `sns:Publish` silently stuck at `PENDING_PUBLISH`. Added `runtimeOnly 'software.amazon.awssdk:sts'` to both build files (BOM-resolved 2.25.70) + extended verify-gradle Gate 7. **Bonus (in-slice finding):** Gate 7's `jar_has_lib` was racy-vacuous under `set -o pipefail` (`unzip|grep -q` SIGPIPE → false-negatives); fixed via `PIPESTATUS[1]` so the §48 gate actually pins.

### 104 — Atomic claim for the worker SQS consumer (`c45a7a6`, brief 104) — concurrency
Replaced the listener's read-then-`setStatus(SYNCING)` TOCTOU guard with `claimForSync` (the first `@Modifying` CAS in the codebase): a conditional `UPDATE … WHERE status IN (QUEUED,RETRY_REQUESTED) OR (SYNCING AND lastAttemptAt < leaseExpiry)`. The DB serializes concurrent at-least-once deliveries → exactly one claims, the rest no-op ACK; own short txn commits before the Graph call (§44). Includes the lease-reclaim clause + reconcile-to-SYNCED. Verified the lead's residual crashed-claimer gap → recommended the 104b reaper.

### Single-thread stale-entity regression (`5822dea`) — Deploy-1 gate, test-only
The live `340d7e3b` `StaleObjectStateException` was single-thread stale-entity-reuse (two saves on one detached reference), not concurrency. Testcontainers proof: (a) reproduce the old two-save-on-stale-ref → `ObjectOptimisticLockingFailureException`; (b) real `onMessage` once → FAILED/SYNCED, no stale exception. Cleared the Deploy-1 DB-gate.

### 104b — `@Scheduled` reaper + transactionId idempotency (`1952762`, brief 104b, FOLDED) — safe recovery
A worker `@Scheduled StaleSyncReaper` (aws-gated) re-enqueues stale-SYNCING rows via `SqsTemplate` (NOT the api-only `SnsLifecyclePublisher` — module boundary) → the lease clause reclaims. Idempotency (required so the reaper can't duplicate): MS Graph `transactionId = syncRecordId` + the listener early-persisting `graphEventId` (reassigned to the `saveAndFlush`'d instance). Ad-hoc security-reviewer CLEAN (1 medium — finder-outside-swallow — fixed in-slice with a whole-`reap()` guard).

### 106 — Human Graph event subject + §10 deep-link body (`0451d9f`, brief 106) — demo polish
Closes the ARCH:1012 gap: human subject per eventKind + an HTML deep-link body (IC → `{base}/weekly-commit/history/{planId}`, review-block → `{base}/manager/command-center`). Net-new `app.outlook.frontend-base-url` config (§48-safe nested default). rule-#7-clean by construction; pinned by a sentinel-PII leak test.

## Decisions made
- **102 exact-message over pinned-id** — strictly stronger (whole-message), id-agnostic; keep `UUID.randomUUID()`.
- **103 PIPESTATUS gate-fix folded in** — a vacuous gate can't pin; the fix was required for the AC + the clean RED.
- **104 lease INCLUDE + PT5M, reconcile-to-SYNCED** — the lease alone doesn't recover a crashed claimer; the reaper (104b) is the re-trigger. Skipped the 2-thread race test (DB CAS is the proof) + `max-concurrent-messages:1`.
- **104b SqsTemplate re-enqueue (NOT the publisher)** — `SnsLifecyclePublisher` is api-module, unreachable from the `:shared`-only worker (REQ-O-016). FOLDED idempotency in (reaper never in the repo without it).
- **104b idempotency = transactionId (primary, Context7-verified) + early-persist (defense-in-depth, must reassign).**
- **106 HTML body + the `/history/{planId}` deep-link** (routes verified) + net-new config; worker stays thin (no plan/commitment/SO repos).

## Decisions explicitly NOT made (deferred)
- **No commitment-listing tier in the worker** (106) — the rejected privacy posture; the worker stays thin.
- **No admin DLQ-redrive UI** — out of scope (§10 deferred).
- The **demo-seed job (107)** + the polish-deploy bundle are handed to the successor (handoff doc 019).

## TDD compliance
**Clean.** Every deterministic change had a corresponding test (102 RED-first; 103 gate-driven RED; 104 repo-CAS + listener tests; 5822dea is the test; 104b reaper/idempotency tests; 106 leak + subject/body tests). The build-wiring slices (103) used the gate-as-test discipline. Notable TDD win: 104b's naive early-persist re-introduced the §104 two-save stale bug — **caught by the Testcontainers tests, not the mock**.

## Reachability
All features reachable from real production entry points:
- 103 `awssdk:sts` → the production bootJar classpath (IRSA on the cluster); pinned by verify-gradle Gate 7.
- 104 `claimForSync` → `SyncMessageListener.onMessage` (the `@SqsListener` entry point).
- 104b `StaleSyncReaper.reap` → the `@Scheduled` scheduler (aws-gated; `@EnableScheduling` on `WorkerSharedConfig`). transactionId/early-persist → the listener→adapter→`MsGraphEventGateway` create path.
- 106 subject/body → `GraphCalendarAdapter.buildSpec` (real-mode, on the create path).
- **No tested-but-unwired gaps.** The reaper is *inert until infra grants the worker `sqs:SendMessage`* — an infra IAM dependency (ea04616 grants it), NOT a wiring gap; documented in the reaper + the §49-addendum.

## Open follow-ups (Step-9 categorized list — orchestrator-owned; surfaced for `/orchestrate-end` verification)
- **LESSONS routing (orch writes):** §44-addendum (102), §48-addendum (103), **§49 new** (104 atomic-claim) + 104b carry-forward, §49-addendum (104b reaper/idempotency + the security-medium swallow-the-whole-tick + "real-PG proofs caught what the mock missed"), §45-addendum (106 deep-link body).
- **Infra companions (orch routes to infra; some landed):** SQS visibility timeout + worker `sqs:SendMessage` (104/104b — landed `ea04616`); `WC_FRONTEND_BASE_URL` on the worker Deployment (106 — pairs with the polish deploy; the yaml nested default boots until then).
- **Cross-doc invariant audit: CLEAN** — no Appendix-A/B model, enum, schema, or contract-DTO field changed this session (`claimForSync`/`findByStatusAndLastAttemptAtBefore` are repo methods; `CalendarEventSpec.body` is an internal worker DTO, not Appendix-B; `transactionId` is an SDK `Event` field-set). No doc drift.
- **Next session:** the demo-seed job (107) + the polish-deploy bundle (chevron fix + 106), per handoff doc `docs/team-handoffs/019-…`.
