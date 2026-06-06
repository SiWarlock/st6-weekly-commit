# infra brief — SQS `wc-sync` explicit visibility timeout + worker IRSA `sqs:SendMessage` (worker-fix companion, Deploy 1) — INFRA TRACK

> **For st6-main-infra-implementer.** Infra is verified via `terraform fmt`/`validate`/`tflint`/`plan` + the deployed smoke — NOT red-green TDD (per root CLAUDE.md TDD posture). Two small, independent Terraform changes that pair with the backend worker-concurrency fix (104 / 104b).

## Feature
Two `wc-sync` SQS changes that complete the worker sync-reliability fix:
1. **Set the `wc-sync` queue visibility timeout EXPLICITLY to 90s** (range 60–120s) — replaces the implicit AWS 30s default.
2. **Add `sqs:SendMessage` to the worker IRSA role's `wc-sync` policy** — the worker is currently a pure consumer; the 104b reaper needs to re-enqueue.

## Why (live evidence — Deploy 1 demo-critical)
The first live Sam sync re-test (Graph creds now fixed) hit the worker concurrency bug: syncRecord `340d7e3b` reached `SYNCING`, `createEvent` was invoked, then an `ObjectOptimisticLockingFailureException` on the status-save stranded it. Timeline: `createEvent` at 04:42:56, **redelivery at 04:43:24 — ~28s apart, right at the 30s default visibility timeout.** The slow Graph call exceeded the 30s visibility timeout → SQS redelivered the message mid-flight → the concurrent-delivery race. Backend `104` (`c45a7a6`, the atomic claim) fixes the race even at 30s, but **raising the visibility timeout removes the mid-flight redelivery itself** (fewer redundant Graph calls, cleaner). **This is part of the demo-critical Deploy 1** (104 + visibility-timeout) for a clean in-app `SYNCED`.

The worker `sqs:SendMessage` grant is needed by the **104b reaper** (a `@Scheduled` job that re-enqueues stale-`SYNCING` pointers to `wc-sync` via `SqsTemplate` → re-fires the `@SqsListener` → reclaims). The worker can't reach the api-side SNS publisher (no api↔worker edge, REQ-O-016/Gate 5), so it produces directly to its own queue.

## Change 1 — `wc-sync` visibility timeout (Deploy-1-critical)
**File:** `infra/terraform/sns_sqs.tf` (the `aws_sqs_queue "wc-sync"` resource, ~line 28).
- Add `visibility_timeout_seconds = 90` to the `wc-sync` queue (the main queue, NOT the DLQ).
- **Constraints (load-bearing):** must be **> the max Graph createEvent round-trip** (observed ~tens of seconds; 90s gives margin) AND **< the backend claim lease** (`app.sqs.sync-claim-lease = PT5M = 300s`, the worker's reaper/claim lease). 90s sits cleanly in `30s_default < graph_time < 90s < 300s_lease`. Do NOT exceed ~120s (keep it well under the 300s lease so an active-but-slow claimer is never lease-reclaimed mid-flight).
- If the spring-cloud-aws listener does its own visibility extension, this is the base timeout — 90s is still the right floor.

## Change 2 — worker IRSA `sqs:SendMessage` (104b-coupled; harmless to land early)
**File:** `infra/terraform/iam_irsa.tf` (the worker IRSA SQS statement, ~line 89 — currently `Action = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]`).
- Add `"sqs:SendMessage"` scoped to the `wc-sync` queue ARN only (do NOT widen).
- Inert until the 104b reaper ships (no current producer), so safe to land in Deploy 1 ahead of 104b. Without it the reaper's `send` is `AccessDenied` → swallowed → a benign no-op.

**As-implemented (`ea04616`, supersedes the initial `901831b`):** the lead tightened this to a **dedicated `ProduceSyncReaper` IAM statement with `sqs:SendMessage` on `wc-sync` ONLY** (excludes the `wc-sync-dlq` ARN the reaper never sends to); the existing `ConsumeSync` statement (`ReceiveMessage`/`DeleteMessage`/`GetQueueAttributes` on `[wc-sync, wc-sync-dlq]`) is byte-identical/untouched. Least-privilege; `visibility-timeout=90` unchanged.

## Verify (infra path — no red-green)
- `terraform fmt -check` + `terraform validate` + `tflint` clean.
- `terraform plan` shows EXACTLY: (1) the `wc-sync` queue `visibility_timeout_seconds` 0/default→90 in-place update, (2) the worker IRSA policy adding `sqs:SendMessage` on the `wc-sync` ARN. **No other diffs** (no queue replacement, no scope widening, no DLQ change, raw-delivery untouched — `raw_message_delivery=true` stays).
- Post-apply smoke: confirm the `wc-sync` queue attribute `VisibilityTimeout=90` (console/CLI) and the worker SA's effective policy includes `sqs:SendMessage`.

## Sequencing
- **Deploy 1 (demo-critical):** Change 1 (visibility timeout) is required for the clean live `SYNCED`; Change 2 (SendMessage) rides along (pre-positioned for 104b). Both are low-risk, no cert/DNS — push with the backend `104` worker fix.
- Can be applied **now, in parallel** with the impl's 104b build (the lead released infra). No dependency on 104b code landing.
- **Do NOT** touch the federation host / portal cert-SAN here — that's the separate Deploy 2 (cert re-validation risk isolated).

## Commit
Conventional (e.g. `fix(infra): explicit wc-sync SQS visibility timeout (90s) + worker IRSA sqs:SendMessage for the 104b reaper`). Note the 104/104b coupling + the `30s < graph < 90s < 300s lease` invariant. **Do NOT push** — the lead bundles + triggers Deploy 1. Ping the lead (and cc the backend orch) the commit hash.

## Cross-track notes
- The `90s < 300s lease` invariant couples to the backend `app.sqs.sync-claim-lease` (PT5M) — if either changes, reconcile both. Flagged in wc-api LESSONS §49 (the lease > visibility-timeout invariant).
- Raw message delivery is already on (`sns_sqs.tf:43`) — the 104b `SqsTemplate` re-enqueue relies on it; do not disable it.
