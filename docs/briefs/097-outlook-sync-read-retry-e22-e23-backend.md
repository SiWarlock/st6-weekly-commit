# /tdd brief — Outlook sync read + retry (E22 list + E23 manual-retry)

## Feature
Build the user-facing Outlook-sync REST surface — `GET /api/outlook-sync?planId=` (list a plan's sync records) + `POST /api/outlook-sync/{syncRecordId}/retry` (manual retry of a `FAILED` record) — wiring the **already-built-but-dead** `RETRY_REQUESTED` status / `authorizeSyncRecordAccess` authorizer / `RETRY_SYNC` affordance + **reusing the §28/s7 `SnsLifecyclePublisher` afterCommit republish verbatim**, serving the exact contract the frontend (9.12, built against MSW) already calls.

## Use case + traceability
- **Task ID:** sync-E22/E23 (drift-fix arc finding #2, api half; orch inlines the task checkbox at `/orchestrate-end`)
- **Architecture sections it implements:** **§5/Appendix B.2 E22+E23** (endpoints), **§10 + Appendix D.6** (sync state machine — `FAILED → RETRY_REQUESTED`, API re-publishes the same pointer → `QUEUED`, single publish path), **§4** (`outlook_calendar_sync_record`), **§6** (rule #3 IDOR authz), **safety rule #4** (sync never blocks/rolls back the core lifecycle), **safety rule #7** (pointer-only republish), **Appendix F.2** (`SyncJobPointer`).
- **Related context:** the SECOND deploy-blocking drift-fix slice (after 096 comments). `RETRY_REQUESTED` exists in `SyncStatus` but **no flow sets it** (enum + vocab-test only); `authorizeSyncRecordAccess` exists with **zero prod callers** (another dead authorizer → first prod caller here); `RETRY_SYNC` is already in the `AllowedAction` enum + frontend `dtos.ts`. **The V6 demo seed already plants a FAILED record for R2 Marco** (`failure_code='GRAPH_FORBIDDEN'`, `safe_message='Calendar sync failed; you can retry.'`, `retry_count=1`, `graph_event_id` NULL) **explicitly to demo this retry** (ARCHITECTURE Appendix E / §15). Patterns to reuse: **LESSONS §28** (afterCommit `REQUIRES_NEW` publish, rule #4 swallow-never-rethrow), **§43** (the real SNS gateway behind the seam), **§25** (per-resource authorizer chokepoint), **§21** (record-DTO, entity-never-crosses), **§31/§35** (affordance subset / per-viewer affordance), **§46** (reachability drift-fix — wiring a dead authorizer). **097 = api side only;** the worker `RETRY_REQUESTED` handling is **098** (separate `:worker` slice) — 097 is independently functional because the existing worker re-attempts any record with `graphEventId == null` (a retried FAILED record), and 098 then hardens it with the §10 status guard.

## Acceptance criteria (what "done" means)
- [ ] `GET /api/outlook-sync?planId=` returns an **`OutlookSyncRecordDto[]`** (a **bare array, NOT paginated** — matches the frontend) of the plan's sync records (`relatedType=WEEKLY_PLAN, relatedId=planId` — i.e. its `IC_PLANNING` + `IC_RECONCILIATION` records).
- [ ] `OutlookSyncRecordDto` mirrors the frontend `dtos.ts` shape **EXACTLY**: `{id, ownerEmployeeId, relatedType, relatedId, eventKind, weekStartDate?, status, graphEventId?, failureCode?, safeMessage?, retryCount, traceId?, allowedActions[], version}` — never the entity (audit/`queuedAt`/`processedAt`/`lastAttemptAt` leak-tested out, §21).
- [ ] **`RETRY_SYNC` affordance** emitted on a record's `allowedActions[]` **iff `status == FAILED`** (affordance↔enforcement single-source, §31 — the same precondition E23 enforces) AND the viewer is authorized to retry it.
- [ ] `POST /api/outlook-sync/{syncRecordId}/retry` (no body): only when **`status == FAILED`** → transitions `FAILED → RETRY_REQUESTED` in the core txn, then an **afterCommit** non-blocking republish of the **same pointer** (reuse `SnsLifecyclePublisher.publish` → `QUEUED` on success / stays `RETRY_REQUESTED` on failure). Returns the updated `OutlookSyncRecordDto` with **`status = RETRY_REQUESTED`** (the core-txn state — matches the frontend mock; the afterCommit publish advances the DB to `QUEUED` asynchronously).
- [ ] **Precondition:** retry on a non-`FAILED` record → **`409 SYNC_NOT_RETRYABLE`** (NEW code).
- [ ] **Rule #4 (non-blocking):** an SNS republish failure NEVER throws to the caller and NEVER rolls back the `RETRY_REQUESTED` write (the existing `SnsLifecyclePublisher` catch leaves the record retained — here `RETRY_REQUESTED`, still a worker-trigger state + visible/retryable). Assert the publish-failure path leaves `RETRY_REQUESTED` + returns 200 + no exception.
- [ ] **Rule #7:** the republish payload is the bare 4-field `SyncJobPointer` (`{syncRecordId,eventKind,env,traceId}`) — reused verbatim (no new payload, no PII).
- [ ] **Rule #3 IDOR:** E22 authorizes the plan first (chokepoint); E23 calls **`authorizeSyncRecordAccess`** first (**first prod caller** — admits IC-owner + active-direct-manager per the E23 catalog scope); cross-owner/unseeable/missing → codeless `404` (+ denial audit on genuine denial).
- [ ] Both endpoints **request-reachable** through the active `SecurityConfig` chain (RMHM) — Step 7.5.
- [ ] All tests in `apps/wc-api/api/src/test/.../sync/` pass; `./gradlew check` green from `apps/wc-api/`.
- [ ] Cross-doc flagged at Step 9: the new `OutlookSyncRecordDto` + the `SYNC_NOT_RETRYABLE` code (orch writes the rows).

## Files expected to touch
**New:**
- `api/src/main/java/com/st6/wc/sync/SyncController.java` — thin `@RestController`: `GET /api/outlook-sync?planId=` + `POST /api/outlook-sync/{syncRecordId}/retry`.
- `api/src/main/java/com/st6/wc/sync/dto/OutlookSyncRecordDto.java` — `record` mirroring `dtos.ts`.
- `api/src/main/java/com/st6/wc/sync/SyncRecordMapper.java` — entity→DTO + the `RETRY_SYNC` affordance (status==FAILED).
- read + retry service methods — **either** extend the existing `api/.../sync/SyncRecordService` (currently the SYSTEM-side `PENDING_PUBLISH` creator) **or** add `SyncReadService` (E22) + `SyncRetryService` (E23, holds the afterCommit republish). See Step-2.5 Q for placement.
- Tests under `api/src/test/java/com/st6/wc/sync/`.

**Modified:**
- `shared/src/main/java/com/st6/wc/sync/repo/OutlookCalendarSyncRecordRepository.java` — add `List<OutlookCalendarSyncRecord> findByRelatedTypeAndRelatedId(SyncRelatedType, UUID)` (E22; ordered — see Q2).
- `api/src/main/java/com/st6/wc/action/AllowedActionResolver.java` (or wherever affordances resolve) — add `canRetrySync(status)` = `status == FAILED`.
- `api/src/main/java/com/st6/wc/web/ErrorCodes.java` — add `SYNC_NOT_RETRYABLE`.

> **Reuse, don't rebuild:** `SnsLifecyclePublisher.publish(UUID)` already does the rule-#4 afterCommit republish (sets `QUEUED` on success, swallows + retains on failure). The retry service registers the **same** `TransactionSynchronization.afterCommit → publisher.publish(id)` hook the **lock path** (`PlanLifecycleService`, 3.5/§28) registers — mirror it. **No `DomainAuthorizationService` change** (`authorizeSyncRecordAccess` exists). If you need files beyond this list, **flag at Step 2.5**.

## RED test outline (Step 2)
Tests in `api/src/test/java/com/st6/wc/sync/` (controller/integration + service unit):

1. **`list_returnsPlanSyncRecords_asBareArray`** — seed 2 records for a plan (IC_PLANNING + IC_RECONCILIATION); GET returns a JSON array (not a B.20 envelope) with all fields mapped. _Why:_ E22 contract.
2. **`list_emitsRetrySyncAffordance_onlyOnFailed`** — a FAILED record carries `allowedActions:["RETRY_SYNC"]`; a SYNCED/QUEUED record carries `[]`. _Why:_ §31 affordance↔enforcement.
3. **`syncRecordDto_noEntityLeak`** — `queuedAt`/`processedAt`/`lastAttemptAt`/audit internals absent (`.doesNotExist()`). _Why:_ §21.
4. **`list_crossOwnerPlan_404`** — a plan the principal can't access → codeless 404 (chokepoint). _Why:_ rule #3.
5. **`retry_failedRecord_transitionsToRetryRequested_andRepublishes`** — POST on a FAILED record → 200, response `status=RETRY_REQUESTED`; assert the afterCommit republish invoked the gateway with the bare `SyncJobPointer` (ArgumentCaptor: 4 fields, no PII). _Why:_ §10 + rule #7.
6. **`retry_responseShowsRetryRequested_notQueued`** — the returned DTO is `RETRY_REQUESTED` (the publisher's `QUEUED` flip is afterCommit/async) — matches the frontend mock. _Why:_ contract precision.
7. **`retry_nonFailedRecord_409_syncNotRetryable`** — retry on SYNCED/QUEUED/RETRY_REQUESTED → `409 SYNC_NOT_RETRYABLE`, no state change, no republish. _Why:_ E23 precondition.
8. **`retry_publishFailure_leavesRetryRequested_noThrow_200`** — gateway throws → caller still gets 200, record stays `RETRY_REQUESTED`, no exception propagates. _Why:_ **rule #4 (THE safety pin)**.
9. **`retry_authorizerChokepoint_firstStatement`** — `authorizeSyncRecordAccess` denies → `verify(repo, never()).save` + no republish. _Why:_ §25.
10. **`retry_managerOfOwner_allowed`** — an active direct manager of the record's owner retries → 200 (E23 admits manager-of-owner, unlike IC-owner-only mutations). _Why:_ E23 catalog scope.
11. **`retry_stranger_404`** — unrelated principal → codeless 404 + denial audit. _Why:_ rule #3.
12. **`syncEndpoints_requestReachable`** — both mappings in RMHM. _Why:_ §22/reachability (use `@Qualifier("requestMappingHandlerMapping")` — actuator adds a 2nd bean, LESSONS §46).

## Cross-doc invariant impact (implementer flags at Step 9; orchestrator writes the docs)
- **Model field changes:** NEW boundary DTO `OutlookSyncRecordDto` (mirrors `dtos.ts` + §4/§10 entity). **Check whether Appendix B has a sync-record DTO row** — if absent, I'll add one (flag it); if the contract is only in `dtos.ts` + §4/§10, I write the `apps/wc-api/CLAUDE.md` cross-doc row + a B-appendix row.
- **NEW error code `SYNC_NOT_RETRYABLE`** → `ErrorCodes` (impl) + the §5/B.21 row (orchestrator writes hot).
- **`RETRY_SYNC` affordance** already in `AllowedAction` + B.1/F.4 → no Appendix edit.
- **Orchestrator doc rows to write hot:** the `OutlookSyncRecordDto` cross-doc row + the `SYNC_NOT_RETRYABLE` B.21 row.

## Things to flag at Step 2.5
1. **E22 read scope/authz.** Catalog says E22 = "IC (own)"; E23 = "IC(owns)/Manager(manages owner)". The frontend only calls E22 from the IC's own plan view. My default: authorize the plan via **`authorizePlanAccess(planId)`** (resolves plan→owner, IDOR-safe 404; admits the owning IC + manager-of-owner read — consistent with every other read in §6; sync status is non-sensitive). Tighten to owner-only only if you read the catalog "IC own" as a hard constraint — your call; flag your read.
2. **E22 finder + ordering.** Default: `findByRelatedTypeAndRelatedId(WEEKLY_PLAN, planId)` (the plan's IC records; MANAGER_REVIEW_WEEK records key on managerId so they're naturally excluded). Order? The frontend renders a list — default a deterministic order (e.g. `eventKind` then `id`, or `lastAttemptAt`/`queuedAt` if reliable). Pick a server-fixed deterministic sort + say which.
3. **`SYNC_NOT_RETRYABLE` (409) for a non-FAILED retry.** Default code name + 409. Agree? (I add it to B.21.)
4. **Retry response state = `RETRY_REQUESTED`.** The service maps+returns the DTO from the core-txn state (`RETRY_REQUESTED`) BEFORE the afterCommit publish flips the DB to `QUEUED` — matching the frontend mock. Confirm you're returning `RETRY_REQUESTED`, not re-reading post-publish.
5. **Service placement.** Extend the existing `SyncRecordService` (the SYSTEM creator) with `listForPlan` + `retry`, OR new `SyncReadService`/`SyncRetryService`? Default: a new **`SyncRetryService`** for E23 (it owns the afterCommit register + the rule-#4 surface — keeps the safety surface focused for the security review) + the E22 read in `SyncRecordService` or a `SyncReadService`. Your call; keep the rule-#4 republish in its own service method.
6. **`retryCount` on retry.** Default: the retry endpoint does **NOT** increment `retryCount` (the worker bumps it on the next failed attempt, §4); the endpoint only flips status + republishes. Agree?
7. **`OutlookSyncRecordDto` placement + Appendix.** `api/.../sync/dto/`. Mirror `dtos.ts` verbatim (14 fields). Flag whether Appendix B needs a new DTO row (I author it).

## Dependencies + sequencing
- **Depends on:** 096 landed (`c62fcce`); the §28/s7 `SnsLifecyclePublisher` + `LifecycleSnsGateway` + `SyncJobPointer` + `authorizeSyncRecordAccess` + `SyncStatus.RETRY_REQUESTED` + `AllowedAction.RETRY_SYNC` all already shipped.
- **Blocks:** **098** (worker `RETRY_REQUESTED` handling — the worker side of the retry); the frontend 9.12 retry UI going real; the V6 Marco FAILED-record demo.

## Estimated commit count
**1 — do NOT bundle.** One cohesive feature (read + retry share the controller/DTO/mapper) AND a **rule-#4 + #7 safety surface** (the afterCommit republish) → its own commit, ad-hoc **security-reviewer** pass after GREEN (rule #4 non-blocking + rule #7 pointer-only). E22+E23 bundle *within* this slice; the `:worker` handling is the separate 098.

## Lessons-logged candidates anticipated
- **Convention candidate** — reusing the lock's afterCommit `SnsLifecyclePublisher` verbatim for a SECOND trigger (retry) — the publisher is trigger-agnostic (sets QUEUED on any successful publish), so the retry is "set the intent state + register the same afterCommit hook." Extends §28/§43/§46.
- **Architecture-doc note** — the retry response surfaces `RETRY_REQUESTED` (intent) while the publish→`QUEUED` is async (§10 sequence made concrete).
- **Future TODO — operational** — the §10 "worker redelivery guard" (only re-attempt status ∈ {QUEUED, RETRY_REQUESTED}) is **098**; don't add it here.

## How to invoke
1. **Read this brief end-to-end** (already-oriented session — no `/session-start`).
2. Pre-flight: skim `apps/wc-web/src/features/sync/syncApi.ts` + `dtos.ts` (`OutlookSyncRecordDto`/`SyncStatus`) + `SnsLifecyclePublisher` + how `PlanLifecycleService` (3.5) registers the afterCommit publish + `authorizeSyncRecordAccess`.
3. **Run `/tdd outlook-sync-read-retry-e22-e23`.**
4. Step 0 (Restate) + Step 1 (files). **Step 2.5** — send test designs + answers to the 7 questions; wait for `APPROVED.`/`TWEAK:`/`ADD:` before GREEN.
5. Step 9 — categorized summary + ship/no-ship + draft commit message; flag the `OutlookSyncRecordDto` + `SYNC_NOT_RETRYABLE` cross-doc rows.
