# /tdd brief — `@Scheduled` reaper that re-triggers stale-`SYNCING` reclaim (the crashed-claimer recovery) — 104b, robustness follow-up

## Feature
Add a worker `@Scheduled` reaper that periodically finds **stale-`SYNCING`** records (`lastAttemptAt < now − lease`) and **republishes** them through the existing `SnsLifecyclePublisher` → the listener's 104 lease clause reclaims → reprocesses. This is the **re-trigger** that 104's lease clause needs: 104 made a stranded in-flight row *reclaimable*, but the no-op-ACK deletes the SQS message, so a claimer that crashes mid-Graph has nothing left to re-fire the reclaim. The reaper closes that gap.

## Use case + traceability
- **Task ID:** 104b (the reaper half of the worker-concurrency hardening; lead-approved (b)-as-104b). **NOT a demo blocker, does NOT gate the fed deploy** — rides the fed-deploy bundle **if ready in time**, else a clean follow-up.
- **LESSONS:** **§49** (the design rationale + the re-trigger gap this realizes — read it), §44 (the SQS consumer), §28 (the `SnsLifecyclePublisher` afterCommit outbox — the reuse target).
- **Architecture:** §10 (Outlook sync), rule #4 (non-blocking — the reaper is SYSTEM background, never blocks the core lifecycle), rule #7 (ids-only — republishes the bare `SyncJobPointer`).

## Root cause (from 104 / §49 — the re-trigger gap)
104's `claimForSync` lease clause (`OR (status=SYNCING AND lastAttemptAt < :leaseExpiry)`) makes a stale in-flight row claimable *by a redelivery*. But the listener's no-op path (claimed==0) returns normally → spring-cloud-aws **ACK-deletes** the SQS message. So when a claimer **crashes** mid-Graph and its message was already no-op-ACK-deleted by a racing redelivery, the row sits at `SYNCING` with **no message left** to re-fire the reclaim after the lease expires (E23-retry can't help — it needs `FAILED`). The lease provides *reclaimability*; the reaper provides the *re-trigger*.

## Design (republish via the existing publisher — reuse, don't reimplement)
1. **New repo finder** (bounded): `List<OutlookCalendarSyncRecord> findByStatusAndLastAttemptAtBefore(SyncStatus status, Instant cutoff, Pageable page)` (or a `@Query` with a `LIMIT`) — returns stale-`SYNCING` rows. **Bound it** (a configurable page size, e.g. 100) so a backlog can't republish thousands in one tick.
2. **`@Scheduled` reaper component** (`@Component @ConditionalOnProperty("app.sqs.queue-url")` — active in `aws` only, mirroring the listener's gate; do NOT poll a non-existent queue in local/test). A `reap()` method: `cutoff = clock.instant() − lease`; find stale-`SYNCING` rows; for each, `snsLifecyclePublisher.publish(row.getId())` (the bare `SyncJobPointer`, rule #7) — the listener's lease clause then reclaims + reprocesses. Schedule via `@Scheduled(fixedDelayString = "${app.sqs.reaper-interval:PT1M}")` (config-tunable; default a fraction of the lease so stale rows are caught promptly but not wastefully). Add **`@EnableScheduling`** to the worker app/config (confirm it isn't already on).
3. **Reuse the SAME `lease`** value the listener uses (`app.sqs.sync-claim-lease`, default `PT5M`) — inject it into the reaper so the cutoff matches the claim's lease clause exactly (a single source of truth; if they drift, the reaper could republish a row the claim then refuses, or vice versa).
4. **Idempotent + self-bounding:** republishing the same stale row across two reaper ticks is safe — the listener's `claimForSync` CAS dedupes (the first reclaim sets a recent `lastAttemptAt` → the second republish's reclaim returns 0 → no-op). A reaped row that gets reprocessed lands `SYNCED`/`FAILED` and drops out of the stale-`SYNCING` set. Rule #4: a publish failure is swallowed by the §28 publisher (never blocks); the reaper logs ids-only and continues to the next row.

## Acceptance criteria
- [ ] The reaper republishes ONLY stale-`SYNCING` rows (`lastAttemptAt < now − lease`); a **recent** (active-claimer) `SYNCING`, and any `QUEUED`/`RETRY_REQUESTED`/`SYNCED`/`FAILED`/`PENDING_PUBLISH` row, is NOT republished.
- [ ] Republish goes through the existing `SnsLifecyclePublisher.publish(id)` (bare `SyncJobPointer`, rule #7) — no new publish path, no calendar/PII data.
- [ ] The reaper is `aws`-gated (`@ConditionalOnProperty("app.sqs.queue-url")`), bounded (page-limited), and reuses the listener's `lease` value (single source).
- [ ] **End-to-end crashed-claimer→reclaim→SYNCED** (the lead's explicit ask) is tested deterministically: a stale-`SYNCING` row → `reap()` republishes → the listener (driven directly with the republished pointer) `claimForSync` reclaims via the lease clause → Graph (mock) → `SYNCED`.
- [ ] `./gradlew check` green all 3 modules; tests **deterministic** (call `reap()` directly + fixed `Clock`; NO real `@Scheduled` timing wait, NO thread race — same discipline as 104 / §102-addendum).
- [ ] §44/rule-#4/#7 preserved; no model field change.

## RED → GREEN (deterministic)
1. **Repo finder test** (Testcontainers, the §44 worker harness): seed stale-`SYNCING` (old `lastAttemptAt`) + recent-`SYNCING` + `QUEUED` + `SYNCED` → the finder returns ONLY the stale-`SYNCING` row; the page bound is honored.
2. **Reaper unit test** (mock finder + publisher, fixed Clock): `reap()` → `publisher.publish(id)` called for each stale row, `verifyNoInteractions` for recent/terminal rows; a publish that throws is swallowed and the next row still processed.
3. **End-to-end (the lead's ask), deterministic:** stale-`SYNCING` row in PG → `reap()` republishes → `listener.onMessage(pointer)` → `claimForSync` reclaims (lease clause, already CAS-tested in 104) → mocked `GraphCalendarPort` → `SYNCED` asserted in the DB. Chain the components directly (no real SQS/scheduler).

## Things to flag at Step 2.5
1. **Reaper interval default** (`app.sqs.reaper-interval`) — propose a value (a fraction of the PT5M lease, e.g. `PT1M`) + justify; confirm it's config-tunable + `aws`-gated.
2. **Finder shape + bound** — derived finder vs `@Query`, and the page/limit bound (so a backlog doesn't republish unboundedly in one tick).
3. **`@EnableScheduling` placement** — confirm where it goes on the worker (and that the `@Scheduled` bean stays inactive in local/test via the `@ConditionalOnProperty` gate, so no scheduler fires without a queue).
4. **Single-pod assumption** — for the demo the worker is 1 replica, so one reaper. If ever scaled, two reapers could both republish the same stale row in a tick — but the listener's CAS dedupes the reclaim (only one wins), so it's safe (just a redundant publish). Note it; no leader-election needed for the demo.

## Cross-doc invariant impact
- **Model changes:** none. **New repo finder + a reaper component.** **Orchestrator doc routing (Step 9):** a one-line LESSONS **§49-addendum** — *104b realized the reaper: a `@Scheduled` job republishes stale-`SYNCING` rows through the existing outbox publisher so the lease clause reclaims them — the re-trigger the no-op-ACK-deleted message can't provide.* I'll write it hot at Step 9.

## Dependencies + sequencing
- **Depends on:** 104 (`c45a7a6` — the lease clause + `claimForSync`) + §28 (`SnsLifecyclePublisher`). **Blocks:** nothing. Rides the fed-deploy bundle if ready; else a clean follow-up. Pairs with the held SQS visibility-timeout infra item (the lease > visibility-timeout invariant).

## Estimated commit count
**1.** A repo finder + the `@Scheduled` reaper + `@EnableScheduling` + the reaper-interval yaml + deterministic tests (incl. the e2e). **No security-reviewer** (SYSTEM background, rule #4/#7 preserved). Conventional commit (e.g. `fix(wc-api): @Scheduled reaper re-triggers stale-SYNCING reclaim (104b)`). **Do NOT push.** Ping the orch the hash at done-with-slice.

## How to invoke
1. **Read this brief + LESSONS §49 end-to-end.**
2. Pre-flight: re-read the 104 `claimForSync` lease clause (`OutlookCalendarSyncRecordRepository`), `SnsLifecyclePublisher` (§28), the listener's `aws`-gating, and the §44 worker Testcontainers harness.
3. **Run `/tdd worker-stale-sync-reaper`.**
4. Step 0/1 → **Step 2.5** (the finder + reaper design + interval + the deterministic e2e plan; wait for my header).
5. GREEN → Step 9 commit-message-first → report the hash. Flag the §49-addendum.
