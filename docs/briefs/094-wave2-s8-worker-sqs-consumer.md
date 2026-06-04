# /tdd brief — Wave-2 s8: worker SQS consumer (+ Graph port seam + worker datasource)

## Feature
The `wc-sync-worker` consumes the `wc-sync` SQS queue: an `@SqsListener` deserializes the `SyncJobPointer`, reloads the `OutlookCalendarSyncRecord` by `syncRecordId`, transitions it `QUEUED → SYNCING → SYNCED/FAILED`, and dispatches the calendar op to a **`GraphCalendarPort` seam** (a no-op/demo-success stub in this slice; the real MS Graph adapter is s9). Re-adds the worker datasource (removes the 092 JPA exclude — the worker now reads the DB). Failures throw → SQS redrive → DLQ; processing is idempotent (at-least-once safe) and rule-#7-safe (no PII/secret/calendar-body in `failureCode`/`safeMessage`/logs).

## Use case + traceability
- **Task ID:** Wave-2 s8 (plan `docs/planning/025` → WAVE 2 row 8 + the Wave-2 scoping addendum).
- **Architecture sections it implements:** §10 (sync transport / worker pipeline), §16/§15 (rule #7 — no secrets/PII in payloads, logs, failure messages), Appendix D.3 (worker config), Appendix F.2 (`SyncJobPointer`).
- **Safety rules touched:** **rule #7** (pointer-only; `failureCode`/`safeMessage` must be non-PII, never the raw Graph error body) → **ad-hoc security review** (general-purpose agent). Rule #4 is the api-side publish guarantee (s7) — the worker is downstream of the committed lock, so its job is "don't crash the listener / don't leak"; a bad message lands `FAILED` + DLQ, never a crash loop.
- **Related context:**
  - **LESSONS §43** (just banked, Wave-2 s7) — directly reused: the **`GraphCalendarPort` seam + stub** mirrors the SNS gateway (build the seam + no-op now, swap the real adapter in s9; the port impl PROPAGATES, the consumer decides FAILED-vs-throw); inject the framework `*Operations` interface (`SqsTemplate`/listener config); and **the AWS starter needs a `spring.cloud.aws.region.static` fallback in the worker's base `application.yml`** so a clean CI runner boots (the SQS starter auto-configures `SqsClient` in every profile).
  - **LESSONS §9 / brief 092** — 092 added `@SpringBootApplication(exclude = {DataSourceAutoConfiguration, HibernateJpaAutoConfiguration})` to `WcSyncWorkerApplication` because the worker was DB-less. **s8 REVERSES that** (the worker now reloads `OutlookCalendarSyncRecord` → needs JPA + a datasource). The 092 `WorkerAwsProfileBootTest` bean-absence assertions INVERT (the worker now HAS a `DataSource`/`EntityManagerFactory`).
  - **`OutlookCalendarSyncRecord`** (`shared/.../sync/`) + `OutlookCalendarSyncRecordRepository` (`shared`) — already carry every field/finder the worker needs (`graphEventId`, `failureCode`, `safeMessage`, `lastAttemptAt`, `processedAt`, `retryCount`, `status`). **No schema/entity change.**
  - **`SyncJobPointer`** (`shared/.../sns/payload/`) — the 4-field wire payload the api publishes (s7); the worker deserializes it + reloads by `syncRecordId`.
  - **`infra/k8s/deployment-worker.yaml`** — sets `SQS_QUEUE_URL` + `SQS_DLQ_URL` + `GRAPH_MODE=demo-success` env (the stub hook); mounts `wc-worker-secrets` (graph-only today → the paired infra seam adds db).
  - **`SnsLifecyclePublisher` / s7** — the api side that produces `QUEUED` records; this is its consumer counterpart.

## Acceptance criteria (what "done" means)
- [ ] An `@SqsListener` on the `wc-sync` queue (URL from `${SQS_QUEUE_URL}`) deserializes the `SyncJobPointer` and reloads the `OutlookCalendarSyncRecord` by `syncRecordId`.
- [ ] **Idempotent (at-least-once safe):** a redelivered message whose record is already `SYNCED` (or has a non-null `graphEventId`) is a no-op skip — no double calendar op.
- [ ] **Status transitions:** `QUEUED → SYNCING` (+ `lastAttemptAt`) before the Graph call; on success `→ SYNCED` (+ `graphEventId` + `processedAt`); on failure `→ FAILED` (+ a non-PII `failureCode` + a non-PII `safeMessage` + `retryCount++`).
- [ ] **Failure → DLQ via throw:** after recording `FAILED`, the listener throws so SQS's redrive policy moves the message to the DLQ after `maxReceiveCount` (the redrive policy is infra — confirm it exists; see Q3). The listener never crashes the consumer process; a poison message ends up in the DLQ, not a crash loop.
- [ ] **`GraphCalendarPort` seam:** a `GraphCalendarPort` interface (`create/update` keyed off the record) + a stub impl honoring `GRAPH_MODE=demo-success` (returns a synthetic `graphEventId`, simulating success without a live Graph call). The real adapter is s9 (swapped behind the port, §43).
- [ ] **Rule #7:** no calendar body / secret / token / PII / employee email in the payload, the DB `failureCode`/`safeMessage`, or any log line. The `safeMessage` is a fixed/templated non-PII string (never the raw Graph/exception body).
- [ ] **Worker datasource re-added:** the 092 JPA exclude removed from `WcSyncWorkerApplication`; the worker's `application-aws.yml` gains the `configtree:/mnt/secrets/` import (db secrets) — paired with the infra seam (worker SPC + IRSA db access). `ddl-auto=validate` (the worker validates, never migrates — flyway stays off).
- [ ] **§43 region fallback:** `spring.cloud.aws.region.static: ${AWS_REGION:us-east-1}` in the **worker's** base `application.yml` (the SQS starter needs a boot-resolvable region for clean CI).
- [ ] The 092 `WorkerAwsProfileBootTest` bean-absence assertions inverted (worker now HAS a `DataSource`/`EntityManagerFactory`); the worker boots under `aws` with a datasource.
- [ ] `./gradlew check` clean from `apps/wc-api/`; ad-hoc rule-#7 security review PASS.

## Files expected to touch
**New (worker):**
- `worker/.../sync/SyncMessageListener.java` (or similar) — the `@SqsListener`; deserialize → reload → idempotency-skip → SYNCING → port call → SYNCED/FAILED → throw-on-failure.
- `worker/.../sync/GraphCalendarPort.java` — the seam interface.
- `worker/.../sync/DemoSuccessGraphCalendarPort.java` (or `GRAPH_MODE`-gated stub) — the no-op/demo-success impl (s9 adds the real one behind the same port).
- Tests: a listener test (reload + the status transitions + idempotency-skip + failure→FAILED+throw + rule-#7 safe message) + a port-selection/stub test.

**Modified (worker):**
- `worker/.../WcSyncWorkerApplication.java` — **remove** the 092 `exclude = {DataSourceAutoConfiguration, HibernateJpaAutoConfiguration}` (the worker now uses JPA).
- `worker/build.gradle` — add the spring-cloud-aws BOM platform + `io.awspring.cloud:spring-cloud-aws-starter-sqs` (reuse `libs.versions.toml` `springCloudAws` 3.2.1 from s7).
- `worker/src/main/resources/application.yml` — `spring.cloud.aws.region.static: ${AWS_REGION:us-east-1}` (§43) + `ddl-auto=validate`.
- `worker/src/main/resources/application-aws.yml` — `spring.config.import=optional:configtree:/mnt/secrets/` (db secrets, LESSONS §42).
- `worker/.../WorkerAwsProfileBootTest.java` — invert the bean-absence assertions (worker now HAS DataSource/EntityManagerFactory); may need a test datasource (Testcontainers PG, like the api persistence tests — §9/§29 harness).
- Possibly `worker/.../WcSyncWorkerApplicationTest.java` / `WorkerFlywayPropertyTest.java` — adjust if the datasource re-add affects them.

If implementation needs files beyond this list, **flag at Step 2.5** before going GREEN.

## RED test outline (Step 2)
1. **`consumesPointer_reloadsAndSyncs`** — given a `QUEUED` record + the demo-success stub, the listener reloads by `syncRecordId`, transitions `SYNCING → SYNCED`, sets `graphEventId` + `processedAt`.
2. **`alreadySynced_isIdempotentNoOp`** — a redelivered pointer for an already-`SYNCED` record (or non-null `graphEventId`) → no second port call, no state change.
3. **`graphFailure_recordsFailedAndThrows`** — the port throws → the record goes `FAILED` (+ non-PII `failureCode`/`safeMessage` + `retryCount++`) AND the listener rethrows (so SQS redrive → DLQ). Rule-#7 teeth: assert `safeMessage`/`failureCode` contain no PII/secret/raw-exception-body.
4. **`worker_awsProfile_hasDataSource` (inverted 092 pin)** — under `@ActiveProfiles("aws")` (with a test datasource) the worker context loads WITH a `DataSource` + `EntityManagerFactory` (the 092 exclude is gone).
5. **Port selection/stub** — `GRAPH_MODE=demo-success` activates the stub returning a synthetic `graphEventId`.

## Cross-doc invariant impact (implementer flags at Step 9; orchestrator writes the docs)
- **Model field changes:** **NONE** — `OutlookCalendarSyncRecord` (Appendix A) + `SyncJobPointer` (F.2) unchanged; the worker only reads/transitions existing fields.
- **Orchestrator doc rows to write hot (Step 9):** likely none. **Candidate:** an ARCHITECTURE §10 note that the worker consumer + Graph port seam are live (fold with the s7 §10 note at round-seal). A LESSONS candidate if the SQS-consumer/idempotency pattern surfaces something beyond §43.

## Things to flag at Step 2.5
1. **Worker datasource re-add — remove the 092 annotation exclude, or narrow it?** Default: **remove it entirely** — the worker now genuinely uses JPA (the SyncRecord repo). The aws profile gets the datasource via the configtree mount + `ddl-auto=validate`; the 092 bean-absence test inverts. (The worker still never migrates — flyway stays off.)
2. **`GraphCalendarPort` seam shape + the stub.** Default: a small port interface (`create`/`update` taking the loaded record, returning a `graphEventId`); a `GRAPH_MODE`-gated stub (`demo-success` → synthetic id) so s8 is end-to-end testable without live Graph; s9 swaps the real adapter behind the SAME port (§43 — property/profile-select like the SNS gateway). Confirm the `GRAPH_MODE=demo-success` contract (stub returns success).
3. **DLQ — throw-to-redrive (infra policy) vs. explicit DLQ send.** Default: **throw** after recording `FAILED` → SQS's redrive policy (on the `wc-sync` queue → `wc-sync-dlq`, infra) moves the message after `maxReceiveCount`. Simplest; infra owns the policy. **Confirm the redrive policy exists** (the paired infra seam checks `sns_sqs.tf`). If absent → infra adds it (flag).
4. **Idempotency key — `status==SYNCED` vs. `graphEventId != null`.** Default: skip if **`graphEventId != null`** (the strongest "already created in Graph" signal; covers the SYNCED + the rare SYNCED-but-status-not-yet-persisted edge). Confirm.
5. **Rule-#7 failure message.** Default: `failureCode` = a small enum/constant (e.g. `GRAPH_ERROR`/`TRANSIENT`); `safeMessage` = a fixed/templated non-PII string; NEVER the raw Graph/exception `getMessage()` (which could carry calendar/PII). Pin with a test asserting no PII/secret in the persisted fields.

## Dependencies + sequencing
- **Depends on:** 092 (the exclude this reverses), s7 (`82fd2ab` — the api publishes `QUEUED` records the worker consumes), the `:shared` `OutlookCalendarSyncRecord` + repo (shipped).
- **Blocks:** s9 (the real MS Graph adapter swaps in behind the `GraphCalendarPort`).
- **Paired infra seam (dispatch to the infra impl):** add db secret keys to the `wc-worker-secrets` SPC + `GetSecretValue` on the db secret to the worker IRSA (read-only); confirm the worker IRSA's `sqs Receive/Delete/GetQueueAttributes` is present; confirm the `wc-sync → wc-sync-dlq` redrive policy in `sns_sqs.tf`.

## Estimated commit count
**1 — and do NOT bundle with s9.** Rule-#7-touching → its own commit + ad-hoc security review. The consumer + port-seam + stub + datasource-re-add are one cohesive worker-pipeline unit; the real Graph adapter is the separate s9 (behind the port).

## Lessons-logged candidates anticipated
- **Convention candidate** — the SQS-consumer counterpart to §43: idempotent at-least-once processing (skip on `graphEventId`), throw-to-redrive for DLQ, rule-#7-safe failure persistence, and the §43 region-fallback applied to the worker's SQS starter.
- **Architecture-doc note** — §10 worker consumer + Graph port seam live.
- **Future TODO** — the worker datasource re-add completes the 092 Wave-2 forward-note.

## How to invoke
1. Read this brief end-to-end (esp. Q1 datasource re-add + Q3 DLQ + Q5 rule-#7 failure message).
2. `/tdd wave2-worker-sqs-consumer`.
3. Step 0 (Restate) → confirm against the Feature line.
4. Step 1 → confirm files (note the 092-exclude removal + the inverted bean-absence test).
5. Step 2.5 → the 5 answers (or defaults).
6. Step 9 → categorized summary + draft commit; I run the ad-hoc rule-#7 security review at Step-7→8.
