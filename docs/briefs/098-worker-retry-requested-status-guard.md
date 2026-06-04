# /tdd brief — Worker RETRY_REQUESTED handling (§10 redelivery status guard)

## Feature
Add the §10 worker redelivery guard to the `:worker` `SyncMessageListener`: re-attempt the Graph calendar write **only** when the reloaded sync record's status ∈ `{QUEUED, RETRY_REQUESTED}`; treat `SYNCED` / `SYNCING` / `FAILED` / `PENDING_PUBLISH` as a **no-op** (ack + skip). This makes the worker spec-faithful to §10, hardens idempotency under SQS at-least-once redelivery, and completes the 097 retry end-to-end (a republished `RETRY_REQUESTED`/`QUEUED` record is re-attempted).

## Use case + traceability
- **Task ID:** sync-098 (drift-fix arc finding #2, `:worker` half; 097 was the `:api` half)
- **Architecture sections it implements:** **§10** (the sync state machine — "the worker loads the row and treats `SYNCED`/active-`SYNCING` as a no-op; it only (re)attempts Graph when status ∈ `{QUEUED, RETRY_REQUESTED}`"), **safety rule #4** (the worker is downstream — never blocks/rolls back the core lifecycle), **safety rule #7** (pointer-only payload; PII-free DLQ logs — the §44 cause-less rethrow MUST stay intact).
- **Related context:** the **last deploy-blocking** drift-fix slice. The s8 worker (LESSONS **§44**) currently skips only on `graphEventId != null`; it does NOT gate on status, so a redelivered `SYNCING` (in-flight duplicate) or `FAILED` record would be re-attempted. 097 (`a9ca31b`) makes the api retry flip `FAILED→RETRY_REQUESTED` + republish (→ the worker sees `QUEUED`, or `RETRY_REQUESTED` in a publish-partial edge). This slice teaches the worker which states to (re)attempt. Reuse the §44 patterns verbatim (cause-less sanitized rethrow, idempotent at-least-once, no `@Transactional` across the network call).

## Acceptance criteria (what "done" means)
- [ ] The worker re-attempts Graph **only** when the reloaded record's `status ∈ {QUEUED, RETRY_REQUESTED}`.
- [ ] `SYNCED`, active-`SYNCING`, `FAILED`, `PENDING_PUBLISH` → **no-op** (the message is acked/deleted, NO Graph call, status unchanged, no rethrow → no DLQ redrive for a legitimately-skipped state).
- [ ] A `RETRY_REQUESTED` record is processed → `SYNCING` → Graph attempt → `SYNCED` (the retry e2e completes).
- [ ] A `QUEUED` record still processes (the normal lock-path flow — **regression guard**).
- [ ] **§44 preserved:** the failure path on a (re)attempt still records `FAILED` + fixed non-PII `failureCode`/`safeMessage` + the **cause-less sanitized** `SyncProcessingException` → PII-free DLQ logging; the existing idempotency + no-txn-across-network properties hold.
- [ ] All tests in the `:worker` test class pass; `./gradlew check` green from `apps/wc-api/` (all 3 modules).

## Files expected to touch
**Modified:**
- `worker/src/main/java/com/st6/wc/worker/sync/SyncMessageListener.java` — add the status guard as the first check after the record reload (before the `SYNCING` transition).

**Tests:**
- the existing `:worker` listener test (e.g. `worker/src/test/.../SyncMessageListenerTest.java`) — add the new state cases.

> If implementation needs files beyond this, **flag at Step 2.5**. The `SnsLifecyclePublisher:62` log-wording cleanup (the 097 cosmetic LOW) is `:api` (cross-module) → **NOT in this `:worker` slice**; it stays in Carry-forward (fix when `api/sns` is next touched). Don't pull it in here.

## RED test outline (Step 2)
Tests in the `:worker` listener test (mock the `GraphCalendarPort`/gateway + the repo, or the existing harness):

1. **`retryRequested_reattempts_toSynced`** — a `RETRY_REQUESTED` record (graphEventId null) → `SYNCING` → Graph attempt → `SYNCED` + `graphEventId`/`processedAt` set. _Why:_ completes the 097 retry e2e.
2. **`queued_reattempts_toSynced`** — a `QUEUED` record → processes (the normal lock-path flow). _Why:_ regression guard — the existing path must not break.
3. **`synced_noOp`** — a `SYNCED` record (graphEventId set) → **no Graph call**, status unchanged, message acked. _Why:_ §10 no-op + at-least-once idempotency.
4. **`syncing_noOp`** — a `SYNCING` record (an in-flight duplicate redelivery) → **no Graph call**, no double-attempt. _Why:_ the NEW idempotency hardening (the gap the graphEventId-only guard left).
5. **`failed_noOp`** — a `FAILED` record (not yet retried) → **no Graph call** (the worker doesn't process `FAILED` until a retry sets `RETRY_REQUESTED`). _Why:_ §10 — `FAILED` is not a worker-trigger state.
6. **`pendingPublish_noOp`** — a `PENDING_PUBLISH` record (shouldn't be queued) → no-op. _Why:_ defensive; an unexpected state is skipped, not attempted.
7. **`retryRequested_graphFailure_recordsFailed_causelessRethrow`** — the (re)attempt fails → `FAILED` + fixed non-PII `failureCode`/`safeMessage` + a **cause-less** `SyncProcessingException` (`.hasNoCause()`, no PII) → DLQ. _Why:_ §44 preserved on the retry path.

## Cross-doc invariant impact (implementer flags at Step 9; orchestrator writes the docs)
- **Model field changes:** none (no DTO/enum/schema change — pure worker control-flow).
- **Orchestrator doc rows to write hot:** likely none beyond a §10 realized-note + a LESSONS addendum to §44 (the status-guard completes the §44 consumer). I'll decide at Step 9.

## Things to flag at Step 2.5
1. **Status-guard placement + the `graphEventId` guard interaction.** Default: add `if (status != QUEUED && status != RETRY_REQUESTED) { log(ids-only) + return; }` as the **first** check after reload, before `SYNCING`. **Keep** the existing `graphEventId != null` skip as a secondary defense (a `SYNCED` record has both, so it's caught by the status guard too — defense-in-depth). Agree, or fold the graphEventId check into the status guard?
2. **No-op = ack the message (delete), not redrive.** A no-op `return` from `@SqsListener` completes normally → the message is deleted (acked), NOT sent to the DLQ. That's correct (a legitimately-skipped state shouldn't redrive). Confirm — only a genuine processing FAILURE rethrows (→ DLQ), never a no-op skip.
3. **The §10 "reuse `graph_event_id` to UPDATE rather than create" clause.** For MVP this edge doesn't arise (a `QUEUED`/`RETRY_REQUESTED` record always has `graphEventId == null` — retry only fires on `FAILED`, which never created an event), so the **create** path covers every real case; a `QUEUED`-with-graphEventId record (shouldn't happen) is caught by the secondary graphEventId guard → no-op. Default: **do NOT implement event-update** (out of scope; note it). Agree?
4. **Observability log on skip.** Default: a single ids-only (`syncRecordId` + `status`) debug/info line on a no-op skip, for traceability. No PII (rule #7).

## Dependencies + sequencing
- **Depends on:** 097 landed (`a9ca31b` — the api retry sets `RETRY_REQUESTED` + republishes); the s8 `SyncMessageListener` + `GraphCalendarPort` + the §44 cause-less rethrow all already shipped.
- **Blocks:** nothing downstream in the arc — this is the **last deploy-blocking slice**. After 098 commits, the deploy gate's code prerequisites (096+097+098) are met; the **re-seed** micro-slice + the user's infra apply remain before the first `deploy.yml`.

## Estimated commit count
**1.** A small, focused `:worker` control-flow guard. **Cross-module note:** this is `:worker` only — do NOT bundle the `:api` `SnsLifecyclePublisher` log cleanup. Ad-hoc **security-reviewer — light pass** warranted (the rule-#4/#7-adjacent worker sync path; confirm the §44 PII-free cause-less rethrow stays intact + the no-op skip doesn't accidentally swallow a genuine failure).

## Lessons-logged candidates anticipated
- **LESSONS §44 addendum** — the status-guard completes the §44 consumer (the worker now gates on the §10 state set, not just `graphEventId`); the no-op-acks-vs-failure-rethrows distinction.
- **Architecture-doc note** — §10 worker-redelivery guard realized (the worker-trigger state set `{QUEUED, RETRY_REQUESTED}`).

## How to invoke
1. **Read this brief end-to-end** (already-oriented session — no `/session-start`).
2. Pre-flight: read the current `worker/.../SyncMessageListener.java` (the s8/§44 structure) + the §10 state-machine paragraph + `SyncStatus`.
3. **Run `/tdd worker-retry-requested-status-guard`.**
4. Step 0 (Restate) + Step 1 (files). **Step 2.5** — send test designs + answers to the 4 questions; wait for `APPROVED.`/`TWEAK:`/`ADD:` before GREEN.
5. Step 9 — categorized summary + ship/no-ship + draft commit message.
