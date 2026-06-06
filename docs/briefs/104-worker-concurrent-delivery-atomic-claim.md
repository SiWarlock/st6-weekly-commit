# /tdd brief — harden the worker against concurrent at-least-once delivery (atomic claim + reliable-FAILED + stuck-SYNCING reclaim) — robustness follow-up, NOT a demo blocker

## Feature
Replace `SyncMessageListener`'s read-then-write status guard with an **atomic claim** so exactly one thread/pod transitions a record `QUEUED|RETRY_REQUESTED → SYNCING`; guarantee a Graph failure reliably lands the record back at `FAILED` (retryable); and make a stranded `SYNCING` record reclaimable so it can't be permanently stuck. Add a deterministic concurrent-delivery test.

## Use case + traceability
- **Task ID:** worker-concurrency-hardening (live dry-run FINDING, lead-routed). **NOT a demo blocker** — the demo's real blocker is a wrong Graph secret the user is fixing; a FAST successful sync with the correct secret won't hit the visibility-timeout redelivery that triggered this. Land on its own (likely a small slice).
- **LESSONS:** §44 (the SQS consumer — idempotent at-least-once, no `@Transactional` across the network call, the §098 status-guard addendum), §10 (the sync pipeline), §6/§4 (`@Version` optimistic locking on the 5 mutable entities — `OutlookCalendarSyncRecord` is one).
- **Architecture:** §10 (Outlook sync), rule #4 (sync failure never blocks the core lifecycle — preserved; this is worker-downstream), rule #7 (no PII — preserved; the claim is ids-only).

## Observed defect (live, Marco retry dry-run — lead-reported)
A redelivered SQS message hit 2 concurrent worker listener threads (a slow Graph-auth-failure exceeded the queue visibility timeout → SQS redelivered while the first attempt was still in flight). The current flow has a **TOCTOU window** (`SyncMessageListener.java`):
1. **Guard reads status** (L64-76: skip unless `QUEUED|RETRY_REQUESTED`) and `graphEventId` (L77).
2. **…gap…** — both threads can pass the guard before either writes.
3. **Write `SYNCING` + save** (L82-84) and later **write `SYNCED`/`FAILED` + save** (L88-98).

Two threads both pass step 1 → both act → they collide on the `@Version` save → `ObjectOptimisticLockingFailureException`/`StaleObjectStateException` (the lead saw it at `:98`, the FAILED-save). Consequences: (a) the losing thread's exception propagates with the record left in a bad/`SYNCING` state; (b) a `SYNCING` record is then **no-op-skipped** by the §098 guard on every future redelivery → **stranded, un-retryable without a manual reset**; (c) on a *successful* race both could call Graph → **duplicate calendar events**.

## The fix (3 parts — atomic claim is the core)
1. **Atomic claim (CAS on status) — replaces the read-then-`setStatus(SYNCING)`-then-`save`.** Add a `@Modifying @Query` repo method that conditionally transitions in ONE statement and returns the affected-row count:
   ```
   UPDATE OutlookCalendarSyncRecord r
   SET r.status = SYNCING, r.lastAttemptAt = :now, r.version = r.version + 1
   WHERE r.id = :id AND r.status IN (QUEUED, RETRY_REQUESTED)
   ```
   `int claimed = claimForSync(id, now)`. **`claimed == 0` → no-op ACK** (another thread/pod already claimed it, or it's already terminal). **`claimed == 1` → this thread owns it** → reload the fresh `SYNCING` instance (`findById`, since `@Modifying` bypasses the persistence context) → keep the `graphEventId != null` idempotency re-check → call Graph. Only the sole claimer reaches Graph → no duplicate events, no optimistic-lock race on the transition. (`@Modifying` needs its own short `@Transactional` — the claim txn; it commits BEFORE the Graph call, preserving the §44 "no txn across the network call" rule. Manually bumping `version` keeps optimistic-lock consistency since the bulk update skips the auto-increment.)
2. **Reliable failure → FAILED.** The sole claimer owns the `SYNCING` record, so the `catch` path's `SYNCING → FAILED` (+ retryCount++, fixed non-PII code/message — §44 unchanged) save no longer races another worker → the record reliably lands `FAILED` (retryable). Keep the sanitized cause-less rethrow → DLQ.
3. **Stuck-SYNCING reclaim (the lead's actual symptom) — DECISION at Step-2.5.** Even with the claim, a claimer that CRASHES after claiming but before a terminal transition strands the record at `SYNCING` (the §098 guard then skips it forever). **Recommended:** make the claim **lease-based** — also reclaim a *stale* `SYNCING` whose `lastAttemptAt` is older than a lease window (an active claimer's `lastAttemptAt` is recent → not reclaimed):
   ```
   WHERE r.id = :id AND (
     r.status IN (QUEUED, RETRY_REQUESTED)
     OR (r.status = SYNCING AND r.lastAttemptAt < :leaseExpiry)
   )
   ```
   `leaseExpiry = now − leaseDuration` (a config property; set > max expected Graph call time, aligned with the SQS visibility timeout). This is what actually un-stucks the lead's observed record. **Flag the lease-duration value + whether to include this part in THIS slice or as a fast-follow** (it adds a tunable + couples to the visibility timeout). I lean **include it** — the stranded-SYNCING was the reported symptom; the atomic claim alone doesn't recover it.

## Acceptance criteria
- [ ] Two concurrent deliveries of the same record result in **exactly one** `SYNCING` claim + **at most one** `graphPort.createEvent` call; the other is a no-op ACK. **No `ObjectOptimisticLockingFailureException` escapes** `onMessage`.
- [ ] A Graph failure on the claimer lands the record at `FAILED` (retryable, retryCount incremented, fixed non-PII `failureCode`/`safeMessage` — §44/rule-#7 byte-for-byte unchanged) + the sanitized cause-less `SyncProcessingException` rethrow → DLQ.
- [ ] (If part 3 included) a `SYNCING` record older than the lease is reclaimable on redelivery → not permanently stranded; a *recent* `SYNCING` (active claimer) is NOT reclaimed.
- [ ] The `graphEventId != null` idempotency skip is preserved (a record already created in Graph is never double-created).
- [ ] `./gradlew check` green all 3 modules; the new test is **DETERMINISTIC** (see below — we just fixed a flaky test; do NOT introduce a thread-race-timing flake).

## RED → GREEN (deterministic — no thread-race flake)
Prefer **deterministic simulations of the interleaving** over a real two-thread race:
1. **Claim-CAS repo test** (Testcontainers PG, the §44 worker harness): seed a `RETRY_REQUESTED` record → `claimForSync` returns **1** + the row is `SYNCING`; a **second** `claimForSync` on the now-`SYNCING` record returns **0** (the losing thread's no-op). (Plus, if part 3: a `SYNCING` record with an OLD `lastAttemptAt` → reclaim returns 1; a RECENT one → 0.)
2. **Listener no-op test:** a record already `SYNCING` (claim returns 0) → `onMessage` makes **no** `graphPort` call and does **not** throw (verifyNoInteractions(graphPort)).
3. **Listener failure test:** the claimer's Graph call throws → record `FAILED` + retryCount++ + sanitized rethrow (the existing §44 tests stay green; adapt to the claim flow).
- A true concurrent test (a `CountDownLatch` releasing two threads into `onMessage`) MAY be added as a belt-and-suspenders integration check, but the CAS/no-op determinism must be pinned by (1)+(2) — don't let a timing-racy test be the only proof.

## Things to flag at Step 2.5
1. **Part-3 scope + lease duration** — include the stale-`SYNCING` reclaim in this slice (my lean: yes) or fast-follow? What lease duration, and is it a config property aligned with the SQS visibility timeout?
2. **`@Modifying` + `@Version` mechanics** — confirm the manual `version = version + 1` in the bulk update + the reload-after-claim gives a consistent instance for the subsequent `SYNCED`/`FAILED` save (no second optimistic-lock surprise).
3. **Listener concurrency (optional in-slice config hardening)** — set `spring.cloud.aws.sqs.listener.max-concurrent-messages: 1` in `application-aws.yml`? It serializes WITHIN a pod (defense-in-depth + fewer redundant Graph calls) but does NOT replace the atomic claim (cross-pod redelivery still races; and it caps throughput). Your call — the claim is the real fix; concurrency=1 is optional.
4. **Companion INFRA item (NOT this slice)** — the SQS queue **visibility timeout** (Terraform) should exceed the max expected Graph call time so a slow attempt isn't redelivered mid-flight. That's infra territory — I'll route it to the infra impl separately (flag if you want it bundled or noted).

## Cross-doc invariant impact
- **Model changes:** none (no new field; `status`/`lastAttemptAt`/`version` already exist). **New repo method** `claimForSync` (a `@Modifying` query — not a contract DTO). **Orchestrator doc routing (Step 9):** a LESSONS **§44-addendum** — *an at-least-once SQS consumer must CLAIM the record atomically (a conditional `UPDATE … WHERE status IN (…)` CAS), not read-then-write status — the §098 read-then-`setStatus(SYNCING)` guard has a TOCTOU window that collides on `@Version` under concurrent redelivery + risks duplicate side-effects; a lease-reclaim of stale in-flight rows prevents permanent stranding.* I'll write it hot at Step 9.

## Dependencies + sequencing
- **Depends on:** nothing. **Blocks:** nothing (NOT the demo path). The lead folds the hash into the next deploy (the federation deploy, when the frontend host lands).

## Estimated commit count
**1.** A repo claim method + the listener refactor + deterministic tests (+ optionally the lease + the concurrency yaml). **No security-reviewer** — robustness/idempotency, no authz/lock-enforcement/PII change (rule #4/#7 preserved; confirm in the write-up). Conventional commit (e.g. `fix(wc-api): atomic claim for the worker SQS consumer — concurrent-delivery race + stuck-SYNCING reclaim`). **Do NOT push.** Ping the orch the hash at done-with-slice.

## How to invoke
1. **Read this brief end-to-end.**
2. Pre-flight: re-read `SyncMessageListener.onMessage` (the L64-76 guard → L82-84 SYNCING save → L88-98 terminal saves), `OutlookCalendarSyncRecordRepository`, the `@Version`/`SyncStatus` on the entity, and the §44 worker Testcontainers harness.
3. **Run `/tdd worker-concurrent-delivery-atomic-claim`.**
4. Step 0/1 → **Step 2.5** (the claim-query design + the part-3/lease decision + the deterministic test plan; wait for my header).
5. GREEN → Step 9 commit-message-first → report the hash. Flag the §44-addendum.
