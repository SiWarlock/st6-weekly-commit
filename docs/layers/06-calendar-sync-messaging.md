# Calendar Sync & Messaging

## Executive summary

When an IC locks a weekly plan (or starts reconciliation, or the first of a manager's reports locks for the week), the system tries to drop a matching event onto someone's Outlook calendar. This layer is the asynchronous pipeline that does that — and its single most important property is that it **can never break the thing that triggered it**. The core lifecycle write (the lock) commits first; only *after* it commits does the API publish a tiny "pointer" message (just an id plus a few flags — never any calendar text, notes, or PII) to AWS SNS. That message fans out to an SQS queue, where a completely **separate worker deployable** picks it up, reloads the durable sync record from PostgreSQL by id, and calls Microsoft Graph to create the calendar event. Success marks the record `SYNCED`; any failure — a missing credential, a Graph 403, a vanished owner — lands as a `FAILED` (retryable) row and, on the worker side, redrives the SQS message toward a dead-letter queue. The fail-safe (safety rule #4) and the pointer-only payload (rule #7) are the two load-bearing invariants, and the code enforces both structurally: there is exactly one place that swallows publish failures, and the wire payload is a 4-field record with no free-text fields.

## Responsibilities

- **Outbox creation (publish side, in `:api`).** Inside the core lock/reconcile transaction, write one or more `OutlookCalendarSyncRecord` rows in status `PENDING_PUBLISH` (`SyncRecordService`, `api/.../sync/SyncRecordService.java`). This is a durable outbox — the record exists even if SNS is down.
- **Post-commit pointer publish (publish side, in `:api`).** *After* the core txn commits, publish a pointer-only `SyncJobPointer` to SNS in a fresh `REQUIRES_NEW` transaction, flipping the record `PENDING_PUBLISH → QUEUED` on success (`SnsLifecyclePublisher`). On ANY publish failure: catch, log ids only, leave the record `PENDING_PUBLISH`, never rethrow (rule #4).
- **SQS consumption + Graph dispatch (consume side, in `:worker`).** Consume the queue, reload the record by id, drive the calendar create through `GraphCalendarPort`, and mark `SYNCED` or `FAILED` (`SyncMessageListener` + the Graph adapters/gateways).
- **Mode selection.** Property-conditional selection of real-AWS vs. local/demo implementations on both sides (SNS gateway, Graph port), so no test or local boot touches live AWS/Graph.
- **PII containment (rule #7).** The pointer carries only ids/flags; the worker derives a calendar subject from `eventKind + week` only; failures persist fixed non-PII constants; exceptions are cause-less and ids-only.

**What it does NOT own:**
- The lock/start-reconciliation **transaction itself** that *triggers* the publish — owned by `PlanLifecycleService` (the call site); see [03-application-lifecycle.md](03-application-lifecycle.md).
- The **SNS topic, SQS queue, DLQ, IRSA roles, and the worker's k8s Deployment** — owned by infra; see [10-infrastructure-deployment.md](10-infrastructure-deployment.md).
- The `OutlookCalendarSyncRecord` **schema/migration and entity mapping** (the table, its uniques) — defined in [01-domain-persistence.md](01-domain-persistence.md); this layer reads/writes the rows.
- A **user-facing retry endpoint / read endpoint** (`GET /api/outlook-sync`, `POST /api/outlook-sync/{id}/retry`, E22/E23) — **not implemented in code** (see Gotchas + Drift).

## Key components

| Component | What it does | Where |
|-----------|--------------|-------|
| `SyncRecordService` | Writes `PENDING_PUBLISH` outbox rows inside the core txn; `createIcPlanningRecord` / `createIcReconciliationRecord` / idempotent `upsertManagerReviewBlock` | `apps/wc-api/api/src/main/java/com/st6/wc/sync/SyncRecordService.java:31` |
| `SnsLifecyclePublisher` | Post-commit `REQUIRES_NEW` publish; `PENDING_PUBLISH → QUEUED` on success; catch-log-retain-never-rethrow on failure (rule #4 single swallow point) | `apps/wc-api/api/src/main/java/com/st6/wc/sns/SnsLifecyclePublisher.java:47` |
| `LifecycleSnsGateway` | The publish seam (interface) the publisher depends on | `apps/wc-api/api/src/main/java/com/st6/wc/sns/LifecycleSnsGateway.java:13` |
| `AwsSnsLifecycleGateway` | Real AWS impl — publishes the Jackson-serialized pointer to the topic via `SnsOperations`; **propagates** on failure | `apps/wc-api/api/src/main/java/com/st6/wc/sns/AwsSnsLifecycleGateway.java:46` |
| `LoggingLifecycleSnsGateway` | Local/demo/test stub — logs ids only, succeeds | `apps/wc-api/api/src/main/java/com/st6/wc/sns/LoggingLifecycleSnsGateway.java:24` |
| `SyncJobPointer` | The pointer-only wire payload `{syncRecordId, eventKind, env, traceId}` (record; in `:shared`) | `apps/wc-api/shared/src/main/java/com/st6/wc/sns/payload/SyncJobPointer.java:14` |
| `OutlookCalendarSyncRecord` | The durable outbox entity (the row the worker reloads) | `apps/wc-api/shared/src/main/java/com/st6/wc/sync/OutlookCalendarSyncRecord.java:29` |
| `OutlookCalendarSyncRecordRepository` | Spring Data repo + the per-(owner,week,kind) review-block finder | `apps/wc-api/shared/src/main/java/com/st6/wc/sync/repo/OutlookCalendarSyncRecordRepository.java:15` |
| `WcSyncWorkerApplication` | The separate `wc-sync-worker` Spring Boot deployable entry point | `apps/wc-api/worker/src/main/java/com/st6/wc/worker/WcSyncWorkerApplication.java:18` |
| `WorkerSharedConfig` | Wires the `:shared` `ClockConfig` + `@EntityScan` + `@EnableJpaRepositories` into the worker context | `apps/wc-api/worker/src/main/java/com/st6/wc/worker/config/WorkerSharedConfig.java:26` |
| `SyncMessageListener` | `@SqsListener` consumer — reload, idempotent skip, `SYNCING`, port, `SYNCED`/`FAILED`, sanitized rethrow | `apps/wc-api/worker/src/main/java/com/st6/wc/worker/sync/SyncMessageListener.java:57` |
| `GraphCalendarPort` | The Graph publish seam (interface) the listener depends on | `apps/wc-api/worker/src/main/java/com/st6/wc/worker/sync/GraphCalendarPort.java:13` |
| `GraphCalendarAdapter` | Real-mode port — resolves owner email, builds a non-PII spec, calls the gateway, sanitizes errors | `apps/wc-api/worker/src/main/java/com/st6/wc/worker/sync/GraphCalendarAdapter.java:46` |
| `DegradedGraphCalendarPort` | Fail-safe port (real mode + missing creds) — every `createEvent` throws a clean `GraphCalendarException` (records a safe FAILED) | `apps/wc-api/worker/src/main/java/com/st6/wc/worker/sync/DegradedGraphCalendarPort.java:18` |
| `DemoSuccessGraphCalendarPort` | Demo/default stub — returns a deterministic synthetic id, no live Graph call | `apps/wc-api/worker/src/main/java/com/st6/wc/worker/sync/DemoSuccessGraphCalendarPort.java:21` |
| `GraphEventGateway` | The 1-method seam over the actual Graph call (mockable; keeps SDK out of the adapter) | `apps/wc-api/worker/src/main/java/com/st6/wc/worker/sync/GraphEventGateway.java:9` |
| `MsGraphEventGateway` | The lone SDK-touching impl — translates the spec to a Graph `Event` and POSTs it | `apps/wc-api/worker/src/main/java/com/st6/wc/worker/sync/MsGraphEventGateway.java:28` |
| `GraphRealModeConfig` | `@ConditionalOnProperty(app.graph.mode=real)` — builds the real client + adapter, or degrades at startup if any cred is blank | `apps/wc-api/worker/src/main/java/com/st6/wc/worker/sync/GraphRealModeConfig.java:42` |
| `CalendarEventSpec` | Non-PII spec record `{subject, start, end, recordId}` handed to the gateway | `apps/wc-api/worker/src/main/java/com/st6/wc/worker/sync/CalendarEventSpec.java:14` |
| `GraphCalendarException` | Cause-less, ids-only exception thrown by the adapter/degraded port | `apps/wc-api/worker/src/main/java/com/st6/wc/worker/sync/GraphCalendarException.java:15` |
| `SyncProcessingException` | Cause-less, ids-only exception the listener rethrows to trigger SQS redrive → DLQ | `apps/wc-api/worker/src/main/java/com/st6/wc/worker/sync/SyncProcessingException.java:12` |

## Interfaces & contracts

**Publish side — what `:api` exposes to the lifecycle layer**

```java
// SyncRecordService — called INSIDE the core lock/reconcile txn
OutlookCalendarSyncRecord createIcPlanningRecord(WeeklyPlan plan, String traceId);          // IC lock
OutlookCalendarSyncRecord createIcReconciliationRecord(WeeklyPlan plan, String traceId);    // start-reconciliation
Optional<OutlookCalendarSyncRecord> upsertManagerReviewBlock(UUID managerId, LocalDate weekStartDate, String traceId); // idempotent, ≤1 per (owner,week,kind)

// SnsLifecyclePublisher — called from PlanLifecycleService's afterCommit hook
@Transactional(propagation = REQUIRES_NEW) void publish(UUID syncRecordId);
```

**The publish seam (one method):**
```java
interface LifecycleSnsGateway { void publish(SyncJobPointer pointer); }
```

**The wire payload (the rule-#7 + F.2 contract):**
```java
record SyncJobPointer(UUID syncRecordId, EventKind eventKind, String env, String traceId) {}
```
Exact wire JSON (Appendix F.2): `{ "syncRecordId": "...", "eventKind": "IC_PLANNING|IC_RECONCILIATION|MANAGER_REVIEW_BLOCK", "env": "local|aws", "traceId": "..." }`. **Four fields, no calendar bodies/notes/secrets.** Pinned by `SnsLifecycleGatewayTest.publishesPointerToTopic` (`SnsLifecycleGatewayTest.java:37`), which captures the `SnsNotification` and asserts the payload `isEqualTo(pointer)` plus a fixed non-PII subject `"wc-lifecycle-sync"`.

**Consume side — what `:worker` exposes**

```java
// SyncMessageListener — the SQS entry point (the @SqsListener target)
@SqsListener("${app.sqs.queue-url}") void onMessage(SyncJobPointer pointer);

// The Graph seam (one method)
interface GraphCalendarPort { String createEvent(OutlookCalendarSyncRecord record); }      // returns graphEventId
interface GraphEventGateway  { String createEvent(String userEmail, CalendarEventSpec spec); }
```

**Expectations from other layers:** the lifecycle layer must call `SyncRecordService.*` *inside* the transaction and schedule `SnsLifecyclePublisher.publish` via `afterCommit` (it does — `PlanLifecycleService.java:298`). Infra must provision the topic/queue/DLQ and set `app.sns.topic-arn` (api) + `app.sqs.queue-url` (worker) + `GRAPH_*` (worker) so the real beans activate.

## Data & state

**The outbox row** (`OutlookCalendarSyncRecord`, `OutlookCalendarSyncRecord.java:29`) — durable, `@Version`-optimistic-locked via `PersistableUuidEntity` (`PersistableUuidEntity.java:25`). Fields the pipeline reads/writes: `ownerEmployeeId`, `relatedType`, `relatedId`, `eventKind`, `status`, `graphEventId`, `failureCode`, `safeMessage`, `lastAttemptAt`, `queuedAt`, `processedAt`, `retryCount`, `traceId`, `weekStartDate`. (Schema/uniques: [01-domain-persistence.md](01-domain-persistence.md).)

**Enums (`:shared/enums/`):**
- `SyncStatus` (`SyncStatus.java:4`): `PENDING_PUBLISH, QUEUED, SYNCING, SYNCED, FAILED, RETRY_REQUESTED`. **`RETRY_REQUESTED` is defined and vocab-pinned but never written by any production code** (see Drift).
- `EventKind` (`EventKind.java:4`): `IC_PLANNING, IC_RECONCILIATION, MANAGER_REVIEW_BLOCK`.
- `SyncRelatedType` (`SyncRelatedType.java:7`): `WEEKLY_PLAN, MANAGER_REVIEW_WEEK`.

**Status lifecycle as the code actually implements it (per-record):**

```
   (core txn) PENDING_PUBLISH ──publish OK──▶ QUEUED ──worker recv──▶ SYNCING ──Graph OK──▶ SYNCED (+graphEventId,+processedAt)
        │                                                                  │
   publish FAIL: stays PENDING_PUBLISH (retained,                      Graph FAIL: FAILED (+failureCode,+safeMessage,+retryCount++)
   never QUEUED, no throw)                                                  └──▶ rethrow SyncProcessingException → SQS redrive → DLQ
```
`failureCode` is always the fixed constant `"GRAPH_SYNC_FAILED"` and `safeMessage` is always `"Calendar sync failed; it will be retried."` (`SyncMessageListener.java:41-43`) — never the raw Graph error.

**Mode configuration:**
- SNS gateway: `app.sns.topic-arn` present → `AwsSnsLifecycleGateway`; absent → `LoggingLifecycleSnsGateway` (`AwsSnsLifecycleGateway.java:28`, `LoggingLifecycleSnsGateway.java:19`).
- SQS listener: gated entirely on `app.sqs.queue-url` (`SyncMessageListener.java:35`); worker `application-aws.yml` binds it from `${SQS_QUEUE_URL}`.
- Graph port: `app.graph.mode` (default `demo-success`, `worker/application.yml:25`) → `DemoSuccessGraphCalendarPort` or `=real` → `GraphRealModeConfig` (real adapter or degraded).

## Dependencies

- **Depends on:**
  - `:shared` — the `OutlookCalendarSyncRecord` entity + repo + enums + `SyncJobPointer` (which lives in `:shared` precisely because both `:api` and `:worker` bind it), and `ClockConfig` for the injectable `Clock`.
  - `EmployeeRepository` (`:shared`) — the real Graph adapter resolves `ownerEmployeeId → Employee.email` as the calendar *address* (`GraphCalendarAdapter.java:48`). The worker's `WorkerSharedConfig` enables only the `sync.repo` + `employee.repo` repositories (`WorkerSharedConfig.java:29`).
  - AWS Spring Cloud (`spring-cloud-aws-starter-sqs`) + `SnsOperations` (api) for the transport; `microsoft-graph` SDK + `azure-identity` (worker) for the real Graph call.
  - PostgreSQL — the worker reloads authoritative data from the DB (it carries no calendar data over the wire). The worker is JPA-backed with `ddl-auto=validate` and **never** runs Flyway (`WcSyncWorkerApplication.java:14`).
- **Used by:**
  - `PlanLifecycleService` (`:api`) — the only caller of `SyncRecordService` + `SnsLifecyclePublisher`, from `lock` (`PlanLifecycleService.java:144,163`) and `startReconciliation` (`:209`). See [03-application-lifecycle.md](03-application-lifecycle.md).
  - AWS SNS/SQS at runtime — the infra-provisioned topic/queue deliver the message between the two sides. See [10-infrastructure-deployment.md](10-infrastructure-deployment.md).

## How it works (flow)

```
 ┌─────────────────────────┐   afterCommit    ┌──────────────────────┐   SNS→SQS   ┌────────────────────────┐
 │ PlanLifecycleService     │ ───────────────▶ │ SnsLifecyclePublisher │ ─────────▶ │ SyncMessageListener     │
 │ (lock/startReconcile)    │                  │ publish(syncRecordId) │  pointer   │ (worker @SqsListener)   │
 │ • SyncRecordService      │                  │ • PENDING_PUBLISH→     │   only     │ • reload by id          │
 │   writes PENDING_PUBLISH │                  │   QUEUED (or retain)   │            │ • SYNCING→port→SYNCED   │
 │   INSIDE the core txn     │                  └──────────────────────┘            │   / FAILED + rethrow    │
 └─────────────────────────┘                                                        └───────────┬────────────┘
                                                                                                 │ createEvent
                                                                                       ┌─────────▼──────────┐
                                                                                       │ GraphCalendarPort   │
                                                                                       │ (real/demo/degraded)│
                                                                                       └─────────────────────┘
```

1. **Outbox write inside the lock txn.** `PlanLifecycleService.lock` saves the plan `LOCKED`, then calls `syncRecordService.createIcPlanningRecord(plan, traceId)` → a `PENDING_PUBLISH` row (`PlanLifecycleService.java:143`). If the IC has an active manager, it also calls `upsertManagerReviewBlock` (`:163`), which is **idempotent**: the finder `findByOwnerEmployeeIdAndWeekStartDateAndEventKind` returns `Optional.empty()` if a prior report already created the block (`SyncRecordService.java:61`), so only the first report-lock-per-manager-week creates one. All created record ids accumulate into `toPublish`.
2. **Schedule publish for after commit.** `publishAfterCommit(toPublish)` registers a `TransactionSynchronization.afterCommit` hook (`PlanLifecycleService.java:298-306`); if no sync is active (e.g. a unit test), it publishes inline (`:307`). This is the structural guarantee that the publish runs on an *already-committed* lock.
3. **Publish the pointer.** `SnsLifecyclePublisher.publish` runs `REQUIRES_NEW` (`SnsLifecyclePublisher.java:47`), reloads the record (no-op if gone, `:49-52`), builds `new SyncJobPointer(id, eventKind, env, traceId)` and calls `gateway.publish(...)`. On success → `QUEUED` + `queuedAt`, save (`:56-58`). On any `RuntimeException` → catch, `log.warn` ids only, **leave `PENDING_PUBLISH`, do not rethrow** (`:59-65`).
4. **Real publish.** `AwsSnsLifecycleGateway.publish` sends the Jackson-serialized pointer with a fixed subject (`AwsSnsLifecycleGateway.java:51`) and **propagates** any failure (no try/catch) so the publisher remains the single swallow point.
5. **Consume.** `SyncMessageListener.onMessage` reloads by `pointer.syncRecordId()` (`SyncMessageListener.java:58`); unknown id → `log.warn` + return, no throw (don't redrive a ghost, `:59-63`); already-done (`graphEventId != null`) → skip (idempotent at-least-once, `:64-67`).
6. **Sync.** Set `SYNCING` + `lastAttemptAt`, save (`:69-71`); call `graphPort.createEvent(record)`; on success set `SYNCED` + `graphEventId` + `processedAt`, save (`:74-78`).
7. **Real Graph dispatch.** `GraphCalendarAdapter.createEvent` resolves the owner email (missing owner → clean `GraphCalendarException`, `GraphCalendarAdapter.java:51`), builds a non-PII `CalendarEventSpec` whose subject is `eventKind + " — week of " + weekStartDate` (`:70`), and calls `MsGraphEventGateway` which POSTs to `users().byUserId(email).events()` (`MsGraphEventGateway.java:34`). Any gateway error → `log.warn` ids only + clean `GraphCalendarException` (`GraphCalendarAdapter.java:56-61`).
8. **Failure path.** Back in the listener's `catch (RuntimeException e)` (`SyncMessageListener.java:79`): set `FAILED` + fixed `failureCode`/`safeMessage` + `retryCount++`, save, `log.warn` ids only, then `throw new SyncProcessingException(pointer.syncRecordId())` — a cause-less, ids-only rethrow that drives the SQS framework to redrive the message toward the DLQ after `maxReceiveCount`.

## Design decisions & rationale

- **Outbox + post-commit publish = rule #4 (NEVER-TRIM), enforced structurally.** The record is written inside the core txn (durable even if SNS is down), and the publish is deferred to `afterCommit` in a `REQUIRES_NEW` txn. A publish failure therefore *cannot* roll back the committed lock, and the publisher's single `catch … never-rethrow` (`SnsLifecyclePublisher.java:59`) is the one and only swallow point. The real gateway deliberately **propagates** (`AwsSnsLifecycleGateway.java`, doc comment lines 22-25) so a gateway-internal catch can't hide failures from the retention posture. Pinned by `SnsLifecyclePublisherTest.publish_gatewayThrows_recordRetained_noThrow` (`SnsLifecyclePublisherTest.java:71`). §10 / §16 / §3, ARCHITECTURE line 1342.
- **Pointer-only payload = rule #7.** `SyncJobPointer` is a 4-field record with no free-text slot; the worker reloads everything else from the row. The Graph subject is synthesized from `eventKind + week` (no name/email/OKR/commitment text). This is the exact Appendix F.2 wire shape (ARCHITECTURE line 1166-1172), pinned by the captor test.
- **Two-sided PII sanitization for failure messages.** Both `GraphCalendarException` and `SyncProcessingException` are **cause-less by construction** (no `(…, Throwable)` ctor) and ids-only, so the raw Graph error (which may carry attendee emails / calendar bodies / tokens) can never ride a cause-chain or message into a log — including the SQS framework's own redrive logging. Pinned by `GraphCalendarAdapterTest.graphError_isSanitizedToCleanNonPiiException` (`GraphCalendarAdapterTest.java:113`) and `SyncMessageListenerTest.graphFailure_recordsFailedAndThrows` (`SyncMessageListenerTest.java:89`), both asserting `.hasNoCause()` and `hasMessageNotContaining(...)` the PII tokens. (LESSONS §44/§45.)
- **Property-conditional mode selection, not `@ConditionalOnMissingBean`.** Both seams pick real-vs-stub via mutually-exclusive `@ConditionalOnProperty` (SNS on `app.sns.topic-arn`; Graph on `app.graph.mode`) so selection is scan-order-independent and no test/local boot accidentally builds a live client. Exactly-one-bean is pinned by `LifecycleSnsGatewaySelectionTest` and `GraphRealModeSelectionTest`. (LESSONS §43.)
- **Self-authored 1-method gateway isolates the SDK.** `GraphEventGateway`/`MsGraphEventGateway` keeps the Kiota/Graph types out of `GraphCalendarAdapter`, so the adapter is fully unit-testable with a mock and no live Graph call. (LESSONS §45.)
- **Startup degrade over crash or fake-success.** `GraphRealModeConfig` in `real` mode with any blank `GRAPH_*` cred wires `DegradedGraphCalendarPort` (records a safe FAILED) rather than crashing the worker or masking a misconfig as a fake `SYNCED`. Resolved against the binding Appendix D.6 ("records a safe failure"), not ambiguous wording — the degrade outcome is safe-FAILED, NOT demo-success. Pinned by `GraphRealModeSelectionTest.realModeBlankCred_degradesToSafeFailurePort_neverCrashes` (`GraphRealModeSelectionTest.java:60`). (LESSONS §45; §10 / REQ-I-004 / REQ-E-007.)
- **Separate worker deployable.** `wc-sync-worker` is its own Spring Boot app (REQ-O-014) with no `:api ↔ :worker` edge; it reloads the row over JPA (`ddl-auto=validate`, never Flyway). `WorkerSharedConfig` explicitly wires the cross-module `:shared` beans the worker's default scan would miss. (LESSONS §44.)
- **No `@Transactional` across the Graph network call.** Each `syncRecords.save(...)` in the listener is its own transaction, so a slow/failed Graph call never holds a DB transaction open (`SyncMessageListener.java` doc lines 28-29).

## Gotchas & sharp edges

- **DRIFT — the documented manual-retry path (`FAILED → RETRY_REQUESTED → re-publish`) is NOT implemented.** §10 (ARCHITECTURE line 193), endpoints E22/E23 (line 609), and the retry state machine diagram (lines 1340-1363) describe a user-visible `GET /api/outlook-sync` read + `POST /api/outlook-sync/{id}/retry` that flips `FAILED → RETRY_REQUESTED` and re-publishes the same pointer. **No such controller, DTO (`OutlookSyncRecordDto`), or service path exists** (grep over `apps/wc-api/.../main` for `outlook-sync`, `OutlookSyncRecordDto`, `/retry` returns nothing). `RETRY_REQUESTED` appears only in the enum definition (`SyncStatus.java:10`) and the vocab test (`EnumVocabularyTest.java:45`); **no production code ever writes it.** A `FAILED` record is therefore a true terminal in the running system — there is no in-app way to re-publish it. (Consistent with ARCHITECTURE line 241 trim-order, which lists `MANAGER_REVIEW_BLOCK` automation as the first pressure-release and the DLQ admin redrive UI as deferred — but the *per-record* manual retry was specced as in-scope in §10/E23, so this is a genuine spec-vs-code gap.)
- **DRIFT — the worker redelivery guard differs from §10.** §10 (ARCHITECTURE line 193) and the diagram (line 1351) specify the guard as `status ∈ {QUEUED, RETRY_REQUESTED}` and "reuse `graph_event_id` to **update** rather than create." The code instead guards on `graphEventId != null` (`SyncMessageListener.java:64`) and the real gateway **always creates** (`MsGraphEventGateway.java:34` calls `.post(event)`; there is no update branch and `CalendarEventSpec` carries no `graphEventId`). Functionally the `graphEventId != null` check covers the SYNCED-no-op case, but a record left in `SYNCING` (e.g. the worker died mid-flight) is NOT treated as a no-op by status — it would be re-attempted and re-created on redelivery. The §10 update-on-reuse semantics are absent.
- **`demo-failure` Graph mode does not exist in code.** §10 (ARCHITECTURE line 195) lists three Graph adapter modes — `real`, `demo-success`, `demo-failure`. Only `real`, `demo-success` (default), and the credential-degrade fallback are implemented; there is no `demo-failure` port.
- **Deep-link config (`WC_FRONTEND_BASE_URL`, IC/review deep links) is not wired.** §10 (ARCHITECTURE line 195) describes the worker injecting `WC_FRONTEND_BASE_URL` and building calendar deep links into the event body. The implemented `CalendarEventSpec`/`MsGraphEventGateway` set only a subject + start/end (no body, no deep link). This is a faithful rule-#7 simplification but a feature gap vs. §10.
- **`relatedId` for a review block is the manager id, not a plan.** `upsertManagerReviewBlock` sets `relatedType=MANAGER_REVIEW_WEEK` and `relatedId=managerId` (`SyncRecordService.java:70-71`) because the real uniqueness key is the per-`(owner,week,kind)` partial unique, not `relatedId`. Don't read `relatedId` as a plan id for review blocks.
- **The publisher swallows on a *missing* record silently.** `SnsLifecyclePublisher.publish` returns early if `findById` is empty (`SnsLifecyclePublisher.java:49-52`) — no log, no throw. Correct for the never-block posture but means a vanished record produces no signal.
- **Idempotency is at-least-once, not exactly-once.** The listener's only dedup is `graphEventId != null`. Between `SYNCING` and the `SYNCED` save, a redelivery would re-create the event (no transaction spans the Graph call by design). Acceptable per the at-least-once model but worth noting.
- **Real beans only activate with the right properties.** Locally/in tests the SNS publish is a logging no-op, the SQS listener bean isn't created at all (no `app.sqs.queue-url`), and Graph is `demo-success`. End-to-end SNS→SQS→Graph runs only in a deployed `aws` profile with `SNS_TOPIC_ARN` + `SQS_QUEUE_URL` + `GRAPH_*` set.

## Connects to

- **[03-application-lifecycle.md](03-application-lifecycle.md)** — the trigger. `PlanLifecycleService.lock` / `startReconciliation` call `SyncRecordService.*` inside the core txn and schedule `SnsLifecyclePublisher.publish` via `afterCommit` (`PlanLifecycleService.java:144,163,209,298`). This layer owns everything *after* that call site.
- **[01-domain-persistence.md](01-domain-persistence.md)** — the `OutlookCalendarSyncRecord` table/entity, its `@Version` token, the full sync unique `(owner, related_type, related_id, event_kind)`, and the V2 partial unique `(owner, week_start_date) WHERE event_kind=MANAGER_REVIEW_BLOCK` that backs the idempotent review block; plus the `SyncStatus`/`EventKind`/`SyncRelatedType` enum vocabulary.
- **[10-infrastructure-deployment.md](10-infrastructure-deployment.md)** — the SNS topic, SQS queue + DLQ (`maxReceiveCount` redrive), per-workload IRSA (`sns:Publish` for api; `sqs:Receive/Delete/GetAttrs` for worker), the `configtree:` secret mount that binds `app.sns.topic-arn` / `SQS_QUEUE_URL` / `GRAPH_*`, and the worker's k8s Deployment.
- **[04-authorization-identity-audit.md](04-authorization-identity-audit.md)** — the worker runs as the SYSTEM principal with no HTTP surface and no Auth0/CORS config; the lock audit (`PLAN_LOCKED`) is written by the lifecycle layer, not here.

---
_Generated by `/layer-docs` (initial run) against commit `3b919a9` on 2026-06-04. Claims are anchored to code; `UNVERIFIED` marks anything not confirmed._
