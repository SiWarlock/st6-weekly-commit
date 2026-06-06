# Session 022 — Backend deploy-blocking drift-fix arc COMPLETE (096 → 097 → 098 → re-seed)

- **Date:** 2026-06-04
- **Phase:** Pre-deploy drift-fix — wiring 3 confirmed-HIGH "built-but-dead-surface" findings before a single full Wave-1+2 deploy. **✅ COMPLETE.**
- **Role:** **orchestrator-authored** (`st6-main-orchestrator`, fresh cycle). The backend implementer (`st6-main-wc-api-implementer`) drove every `/tdd` slice but did NOT run `/session-end` this round — it **persists** for deploy-support (lead directive). This doc is the orchestrator's round-of-record, captured from the slice-by-slice Step-2.5/Step-9 reviews + git.
- **Predecessor:** [021](021-2026-06-04-backend-wave2-live-sync.md) (Wave-2 live-sync). Continuity: lead compaction handoff 4 (`3b919a9`).
- **Successor:** [023](023-2026-06-06-backend-deploy-dominoes-worker-concurrency-graph-polish.md) (deploy dominoes #9 + 102/103 + worker concurrency 104/104b + Graph-event polish 106).
- **HEAD:** `87daf80`. **The 096+097+098+re-seed deploy gate is MET** — only the user's infra apply remains before the first `deploy.yml`.

## Why this round existed

The user **paused the deploy** to fix 3 lead-verified confirmed-HIGH drift findings — a single bug class: **built-but-dead surface** (code shipped in prior rounds with zero production callers / no wiring). Reachability was the heart of the arc: dead authorizers, an enum-only `RETRY_REQUESTED`, dead `COMMENT`/`RETRY_SYNC` affordances (already in both the backend enum AND `dtos.ts`), and a perf-seed manifest with no matching runner. The frontend (9.11b comments, 9.12 retry) had been built against MSW; the backend had to serve the contracts it already calls.

## What was built (4 commits — all `./gradlew check` green; 096/097 full security-reviewed CLEAN 0/0/0, 098 light pass clean)

### 096 — Comments REST surface, E20 list + E21 create (`c62fcce`, brief 096) — rule #3 IDOR
`CommentController`/`CommentService`/`CommentMapper`/`CommentDto`/`CreateCommentRequest` + a `findByTargetTypeAndTargetId` finder. **First prod caller of `authorizeCommentTargetAccess`** (dead since authored). Rule-#3 chokepoint-first (codeless 404 + REQUIRES_NEW denial audit); **REQ-F-014 manager-`LOCKED`+ gate uniform on read+write** (gate AFTER the chokepoint so a stranger gets 404 not the 409 leak; trusted-entity plan-state, PLAN + COMMITMENT→plan discriminators). `createdAt` stamped via injected `Clock` (the `@CreatedDate` is a no-op — no global `@EnableJpaAuditing`); B.20 paged. Frontend 9.11b unblocked. LESSONS **§46**.

### 097 — Outlook sync read + retry, E22 list + E23 retry (`a9ca31b`, brief 097) — rule #4/#7
`SyncController`/`SyncReadService`/`SyncRetryService`/`SyncRecordMapper`/`OutlookSyncRecordDto` + the new owner-only `authorizeSyncListAccess` (E22) + first prod caller of `authorizeSyncRecordAccess` (E23) + `SyncNotRetryableException`. **E22 owner-only / E23 manager-inclusive** — the deliberate catalog asymmetry, frontend-confirmed (E22 consumed only in the IC's `WeeklyPlanView`; no manager sync surface). E23 flips `FAILED→RETRY_REQUESTED` in the core txn + **reuses the §28 `SnsLifecyclePublisher` afterCommit republish verbatim** (trigger-agnostic: QUEUED on success, swallow+retain on failure — rule #4 never throws to the caller); response shows `RETRY_REQUESTED` (core-txn snapshot, async→QUEUED); non-FAILED → `409 SYNC_NOT_RETRYABLE`. Bare 4-field `SyncJobPointer` reused (rule #7). Frontend 9.12 + V6 Marco demo unblocked. LESSONS **§47**.

### 098 — Worker §10 redelivery status guard (`639044b`, brief 098) — `:worker`, rule #4/#7-adjacent
`SyncMessageListener` (re)attempts Graph ONLY when `status ∈ {QUEUED, RETRY_REQUESTED}`; SYNCED/SYNCING/FAILED/PENDING_PUBLISH = **no-op** (a plain `return` acks/deletes the message — a legitimately-skipped state never redrives). Completes the 097 retry e2e + hardens at-least-once idempotency (closes the in-flight-SYNCING-duplicate gap the `graphEventId`-only guard left). **§44 byte-for-byte preserved** (cause-less PII-free DLQ rethrow). LESSONS **§44-addendum** (incl. the JaCoCo orphaned-branch insight). Pure worker control-flow — no cross-doc invariant change.

### re-seed — V5 persona emails → the M365 tenant (`87daf80`, non-TDD data slice)
The 7 V5 persona `Employee.email` values `@st6demo.com` → `@dreddy817.onmicrosoft.com` (the user's M365 E3 tenant — the worker Graph adapter writes to `Employee.email` as the calendar mailbox). Local parts + `external_subject` unchanged. **Auth-inert** (identity resolves via `findByExternalSubject`/`findById` — no `findByEmail`; the Auth0 login emails stay `@st6demo.com`, cosmetic). **Completeness verified against the migrate Job:** V5 is the sole DB-`Employee.email` seed source (V6 + V1–V4 set no persona emails). The impl grep-flagged non-V5 refs BEFORE committing → orchestrator-routed: ① ARCH Appendix E mirrored hot; ② the `apps/wc-e2e` fixture → the 11.9 test-track carry-forward; ③ Auth0/mocks/docs correctly stay `@st6demo.com`.

## Decisions made
- **E22 owner-only (097).** The catalog scopes E22 to "IC (own)" (omitting "/ Manager", which it adds for E4/E23); the frontend confirms E22 is IC-view-only (no manager sync surface). So a new central `authorizeSyncListAccess` (owner-IC + SYSTEM only, codeless 404 IDOR) — NOT the manager-admitting `authorizePlanAccess`, NOT the 403 `authorizePlanMutation`. The E22-owner-only / E23-manager-inclusive asymmetry is the deliberate contract, faithfully implemented + leak-tested both directions.
- **`SYNC_NOT_RETRYABLE` (097)** — a new B.21 409 code for a retry on a non-`FAILED` record (specific named code over the generic `ILLEGAL_STATE_TRANSITION`; mirrors `SecondOpenDisputeException` + its handler).
- **Re-seed mechanism = edit V5 in place (lead-approved).** V5 is never persistently applied (deploy HITL-pending), so the clean end-state is V5 seeding the correct emails — not a new `Vn UPDATE` migration. A hard pre-first-deploy item (edit-in-place couples to Flyway's checksum once V5 applies).
- **PageEnvelope relocate deferred (096).** The §39 envelope imported in-place from `api/manager/dto/` (relocate-to-neutral is a Carry-forward hygiene refactor — sequenced OUT of the IDOR slice, not bundled into a safety commit).
- **COMMENT_CREATED audit (096)** added to the §15 emitted-signals taxonomy (the taxonomy is designed to grow; ids-only, no body).

## Decisions explicitly NOT made (deferred)
- **099 perf-seed HELD (lead).** Off the deploy critical path (zero demo value, never gates the deploy) → post-deploy or defer entirely. NOT dispatched (the impl stays available for deploy-fixes). NOT a scope cut by the orchestrator — the lead owns this call.
- The impl **was NOT cycled** despite WARN 71% (lead deferred — 099 is the work that needs a fresh impl + it's held; a 71% impl can still take a small deploy-fix; cycle when real backend work lands).

## TDD compliance
Clean. Each deterministic slice ran RED→Step-2.5→GREEN; the orchestrator reviewed every Step-2.5 (one `ADD:` at 096 = gate E20 list too; one `TWEAK:` at 097 = E22 owner-only) + every Step-9 + ran the security pass (096/097 full ad-hoc `security-reviewer` subagent CLEAN 0/0/0; 098 light orchestrator read-pass). The re-seed is non-TDD (data + test-data edit). `./gradlew check` green on each.

## Reachability (Step 7.5)
All reachable — the entire arc IS reachability. 096: first prod caller of `authorizeCommentTargetAccess`, both `/api/comments` mappings in RMHM. 097: first prod caller of `authorizeSyncRecordAccess` + the new `authorizeSyncListAccess`, both `/api/outlook-sync` mappings in RMHM. 098: the guard is on the existing `@SqsListener` prod path; completes the retry e2e. No tested-but-unwired gaps.

## Security review
096 (rule #3 IDOR) + 097 (rule #4/#7 + the new owner-only authorizer) each got a full ad-hoc `security-reviewer` subagent pass — **CLEAN, 0 critical/high/medium** (096: 1 low convention; 097: 0/0/0/0). 098 got a light orchestrator read-pass (rule-#4/#7-adjacent worker control-flow) — clean (§44 byte-for-byte preserved; the no-op skip returns before the try/catch). The re-seed had no safety surface (auth-inert, verified by code).

## Open follow-ups
- **Next backend work:** 099 perf-seed (HELD — post-deploy or defer; off-path). The orchestrator + impl **stand by for deploy-support** (the lead pings if the gates dry-run / live deploy surfaces a code issue).
- **Carry-forward (this tracker):** two convention-candidate LOWs (the `AuditService.record` string-metadata → escaped-node §18; the `SnsLifecyclePublisher:62` log-wording); the PageEnvelope relocate-to-neutral; the e2e persona-email reconciliation (folded into the 11.9 test-track item).
- **Deploy:** the lead pushes the clean tree to github + triggers `deploy.yml` once this round commit lands. Deploy config: domain `st6weeklycommit.com`, M365 tenant `dreddy817.onmicrosoft.com`, repo `SiWarlock/st6-weekly-commit`.
