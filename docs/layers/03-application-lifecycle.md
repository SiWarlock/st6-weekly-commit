# Application & Lifecycle Services

## Executive summary

This layer is the "brain" of the weekly-commitment workflow: a set of Spring `@Service` classes in the `api` module that own every state change a plan, commitment, dispute, or manager review can undergo. It exists so that the business rules live in exactly one place — inside transactional service methods, never in controllers (a forbidden pattern) — where validation, authorization checks, the state transition, the audit trail, and downstream side-effects all happen atomically. It is the heart of three of the project's load-bearing safety invariants: a plan cannot lock unless every planned commitment links a Supporting Outcome (#1), a locked plan's planned baseline is immutable (#2), and a commitment can carry at most one unresolved dispute (#6). Each service authorizes first (delegating the actual check to `DomainAuthorizationService`, documented in [04](04-authorization-identity-audit.md)), then guards the source state, then mutates in one `@Version`-guarded transaction, then refreshes manager read-projections ([05](05-manager-projections.md)) and schedules calendar-sync publishes ([06](06-calendar-sync-messaging.md)). The web/DTO shaping that wraps these methods is owned by [02](02-api-web.md).

## Responsibilities

- **Accountable for:** the `DRAFT → LOCKED → RECONCILING → RECONCILED` plan state machine and its forward-only guards (`PlanLifecycleService`); commitment create/patch/delete/unplanned-create and reconciliation-outcome recording (`CommitmentService`); the carry-forward cross-week write (`CarryForwardService`); user-text normalization (`TextNormalizer`); the alignment-dispute `OPEN → IC_RESPONDED → RESOLVED` machine plus the single-unresolved invariant (`DisputeService`); the manager-review mark-reviewed write, the review SLA clock, and the read-time `OVERDUE`/status derivations (`ReviewService`, `ReviewSlaService`, `ReviewStatusDeriver`); and the server-authoritative `allowedActions[]` affordance computation (`AllowedActionResolver`).
- **Enforces safety invariants #1, #2, #6** server-side as the single source of truth (the UI affordances mirror but never replace these gates).
- **NOT accountable for:** the authorization decision itself — every service *calls* `DomainAuthorizationService` (a void-throw chokepoint) but the rule logic lives in [04](04-authorization-identity-audit.md). HTTP/DTO request-and-response shaping, RFC-7807 error rendering, and bean validation live in [02](02-api-web.md). The manager read-model tables + `RiskBadge` derivation live in [05](05-manager-projections.md) (this layer only *triggers* a refresh via `ProjectionRefresher.recomputeForPlan`). The actual SNS publish + worker consumption live in [06](06-calendar-sync-messaging.md) (this layer only *creates the sync record + schedules the after-commit publish*).

## Key components

| Component | What it does | Where |
|-----------|--------------|-------|
| `PlanLifecycleService.lock` | `DRAFT→LOCKED`; enforces #1 (≥1 planned + all linked) + creates review/sync records | `apps/wc-api/api/src/main/java/com/st6/wc/plan/PlanLifecycleService.java:105` |
| `PlanLifecycleService.startReconciliation` | forward-only `LOCKED→RECONCILING` | `apps/wc-api/api/src/main/java/com/st6/wc/plan/PlanLifecycleService.java:193` |
| `PlanLifecycleService.closeReconciliation` | forward-only `RECONCILING→RECONCILED`; completeness gate (422) | `apps/wc-api/api/src/main/java/com/st6/wc/plan/PlanLifecycleService.java:246` |
| `AllowedActionResolver.canLock` | the #1 lock predicate, shared by affordance + enforcement | `apps/wc-api/api/src/main/java/com/st6/wc/plan/AllowedActionResolver.java:173` |
| `AllowedActionResolver.planActions` / `commitmentActions` | computes `allowedActions[]` per plan/commitment state (F.4) | `apps/wc-api/api/src/main/java/com/st6/wc/plan/AllowedActionResolver.java:40` / `:87` |
| `PlanService.getCurrentPlan` / `getPlanById` | IC read of own/by-id plan (self-scoped vs authorizer-gated) | `apps/wc-api/api/src/main/java/com/st6/wc/plan/PlanService.java:59` / `:76` |
| `CommitmentService.create` | E5 planned create on a `DRAFT` plan | `apps/wc-api/api/src/main/java/com/st6/wc/commitment/CommitmentService.java:70` |
| `CommitmentService.createUnplanned` | E11 unplanned create on `LOCKED`/`RECONCILING` | `apps/wc-api/api/src/main/java/com/st6/wc/commitment/CommitmentService.java:121` |
| `CommitmentService.update` | E6 PATCH; #2 baseline-immutability + per-state editable allow-list | `apps/wc-api/api/src/main/java/com/st6/wc/commitment/CommitmentService.java:191` |
| `CommitmentService.discard` | E7 delete (named `discard` to dodge SpotBugs) on `DRAFT` | `apps/wc-api/api/src/main/java/com/st6/wc/commitment/CommitmentService.java:387` |
| `CarryForwardService.carryForward` | E12; sole writer of `CARRIED_FORWARD`; idempotent successor seed | `apps/wc-api/api/src/main/java/com/st6/wc/commitment/CarryForwardService.java:73` |
| `TextNormalizer` | NFC + strip/collapse user text; code-point counting; control-char detection | `apps/wc-api/api/src/main/java/com/st6/wc/commitment/TextNormalizer.java:20` |
| `DisputeService.open` | E17; #6 single-unresolved (pre-check + DB backstop) | `apps/wc-api/api/src/main/java/com/st6/wc/dispute/DisputeService.java:89` |
| `DisputeService.respond` | E18; `OPEN→IC_RESPONDED`; rule-#2 SO-revision exception | `apps/wc-api/api/src/main/java/com/st6/wc/dispute/DisputeService.java:150` |
| `DisputeService.resolve` | E19; `→RESOLVED`; re-derives review (deferred to [04]/[05] for authz/projection) | `apps/wc-api/api/src/main/java/com/st6/wc/dispute/DisputeService.java:224` |
| `ReviewService.markReviewed` | E16; server-derives status; stamps `reviewedAt` | `apps/wc-api/api/src/main/java/com/st6/wc/review/ReviewService.java:62` |
| `ReviewSlaService.reviewDueAt` | F.3 SLA clock: 17:00 org-tz next business day after lock | `apps/wc-api/api/src/main/java/com/st6/wc/review/ReviewSlaService.java:31` |
| `ReviewStatusDeriver` | server-side `REVIEWED` vs `REVIEWED_WITH_DISPUTES` + unresolved count | `apps/wc-api/api/src/main/java/com/st6/wc/review/ReviewStatusDeriver.java:20` |
| `AllowedAction` (enum) | the computed affordance vocabulary (DTO-layer, not a persisted enum) | `apps/wc-api/api/src/main/java/com/st6/wc/action/AllowedAction.java:15` |
| `PlanNotFoundException` | named `404 PLAN_NOT_FOUND` for the self-scoped current-week read only | `apps/wc-api/api/src/main/java/com/st6/wc/plan/PlanNotFoundException.java:10` |

## Interfaces & contracts

The four lifecycle/write services expose `(UserPrincipal actor, UUID id, …Request req)` methods that return a DTO and throw typed exceptions the [02](02-api-web.md) advice renders as RFC-7807 bodies:

```java
// PlanLifecycleService
WeeklyPlanDto lock(UserPrincipal actor, UUID planId)                 // DRAFT→LOCKED
WeeklyPlanDto startReconciliation(UserPrincipal actor, UUID planId)  // LOCKED→RECONCILING
WeeklyPlanDto closeReconciliation(UserPrincipal actor, UUID planId)  // RECONCILING→RECONCILED

// CommitmentService
WeeklyCommitmentDto create(actor, planId, CreateCommitmentRequest)            // E5, DRAFT
WeeklyCommitmentDto createUnplanned(actor, planId, CreateUnplannedCommitmentRequest) // E11
WeeklyCommitmentDto update(actor, commitmentId, PatchCommitmentRequest)       // E6
void discard(actor, commitmentId)                                            // E7, DRAFT

// CarryForwardService
WeeklyCommitmentDto carryForward(actor, commitmentId)                        // E12, RECONCILING

// DisputeService
AlignmentDisputeDto open(actor, commitmentId, OpenDisputeRequest)            // E17
AlignmentDisputeDto respond(actor, disputeId, RespondDisputeRequest)         // E18
AlignmentDisputeDto resolve(actor, disputeId)                                // E19

// ReviewService
ManagerReviewDto markReviewed(actor, reviewId, MarkReviewedRequest)          // E16
```

**Typed-exception → HTTP contract** (all rendered by [02](02-api-web.md)):

| Exception | Code / status | Raised by |
|-----------|---------------|-----------|
| `EmptyPlanLockException` | `409 EMPTY_PLAN_LOCK` (`EmptyPlanLockException.java:8`) | lock, no planned commitments |
| `UnlinkedPlannedCommitmentException` | `409 UNLINKED_PLANNED_COMMITMENT` + per-commitment `fieldErrors` (`UnlinkedPlannedCommitmentException.java:12`) | lock, ≥1 planned unlinked (#1) |
| `LockedBaselineEditException` | `409 LOCKED_BASELINE_EDIT` (`LockedBaselineEditException.java:12`) | E6 patch of a frozen baseline field (#2) |
| `IllegalStateTransitionException` | `409 ILLEGAL_STATE_TRANSITION` (+ optional `constraint`) (`IllegalStateTransitionException.java:12`) | wrong source state; post-lock `alignmentStatus` edit |
| `UnplannedMissingLinkAtCloseException` | `422 UNPLANNED_MISSING_LINK_AT_CLOSE` + `fieldErrors` (`UnplannedMissingLinkAtCloseException.java:16`) | close with incomplete outcomes/links |
| `SecondOpenDisputeException` | `409 SECOND_OPEN_DISPUTE` (`SecondOpenDisputeException.java:10`) | second unresolved dispute (#6) |
| `ValidationException.field(...)` | `400 VALIDATION_ERROR` + `fieldErrors` | unknown SO, illegal value, IC/manager field mix |
| `ResourceNotFoundOrUnauthorizedException` | codeless `404` (IDOR-safe) | denied/missing resource (thrown by [04](04-authorization-identity-audit.md)) |
| `PlanNotFoundException` | named `404 PLAN_NOT_FOUND` (`PlanNotFoundException.java:10`) | self-scoped current-week read only |

**`AllowedActionResolver`** is a pure `@Component` (no repositories — verified by constructor: `AllowedActionResolver.java:31` has no fields). It is consumed two ways: `PlanLifecycleService.lock` reuses `canLock` as the lock precondition (`PlanLifecycleService.java:115`), and the mappers (`PlanMapper`/`CommitmentMapper`, in [02](02-api-web.md)) call `planActions`/`commitmentActions` to populate the response `allowedActions[]`. The boolean predicates (`canCarryForward`, `canOpenDispute`, `canMarkReviewed`, …) take pre-resolved booleans (`viewerIsDirectManager`, `hasUnresolvedDispute`) so the resolver stays repo-free (`AllowedActionResolver.java:87`).

## Data & state

- **State lives in the JPA entities** (owned by [01](01-domain-persistence.md)): `WeeklyPlan.state` (`PlanState ∈ {DRAFT,LOCKED,RECONCILING,RECONCILED}`, `PlanState.java:5`) plus the four lifecycle timestamps `lockedAt`/`reconciliationStartedAt`/`reconciledAt` (`WeeklyPlan.java:42-44`); `WeeklyCommitment.commitmentKind`/`reconciliationOutcome`/`supportingOutcomeId`/`carryForwardSourceCommitmentId`; `AlignmentDispute.status` (`DisputeStatus ∈ {OPEN,IC_RESPONDED,RESOLVED}`); `ManagerReview.status` (`ReviewStatus ∈ {NOT_REVIEWED,REVIEWED_WITH_DISPUTES,REVIEWED}`) + `reviewDueAt`/`reviewedAt`.
- **`@Version` optimistic locking** on `weekly_plan`, `weekly_commitment`, `manager_review`, `alignment_dispute` is the concurrency guard: every lifecycle save (e.g. `PlanLifecycleService.java:139`, `:204`, `:275`; `CarryForwardService.java:96`) can throw `ObjectOptimisticLockingFailureException` on a concurrent double-action, which propagates (never swallowed) and is mapped to `409` by [02](02-api-web.md).
- **`OVERDUE` is never stored** — there is deliberately no `ReviewStatus.OVERDUE` constant. It is derived at read time in `ReviewMapper` as `status == NOT_REVIEWED && now > reviewDueAt` over an injectable `Clock` (`ReviewMapper.java:33`); `ReviewStatusDeriver` only ever returns `REVIEWED` or `REVIEWED_WITH_DISPUTES` (`ReviewStatusDeriver.java:50`).
- **The unresolved-dispute bucket** is the constant `{OPEN, IC_RESPONDED}`, defined identically in `DisputeService.java:85` and `ReviewStatusDeriver.java:31`; a `RESOLVED` dispute is historical and does not count.
- **`AllowedAction`** is a derived response-field enum (`AllowedAction.java:15`), deliberately NOT in `shared/enums/` (no `VARCHAR`+`CHECK` column).

## Dependencies

- **Depends on:**
  - `DomainAuthorizationService` ([04](04-authorization-identity-audit.md)) — every mutating method calls an `authorize…Mutation`/`authorizeDisputeCreation`/`authorizeReviewMutation` chokepoint *as its first statement* (e.g. `PlanLifecycleService.java:107`, `CommitmentService.java:71`, `DisputeService.java:91`).
  - JPA repositories (`WeeklyPlanRepository`, `WeeklyCommitmentRepository`, `AlignmentDisputeRepository`, `ManagerReviewRepository`, etc.) in [01](01-domain-persistence.md).
  - `ProjectionRefresher.recomputeForPlan` ([05](05-manager-projections.md)) — called in-transaction after writes that change manager-visible counts/state (`CommitmentService.java:154`, `DisputeService.java:125/196/246`, `ReviewService.java:84`, `PlanLifecycleService.java:161/211/278`, `CarryForwardService.java:119`).
  - `SyncRecordService` + `SnsLifecyclePublisher` ([06](06-calendar-sync-messaging.md)) — lock/start create sync records and schedule an after-commit publish (`PlanLifecycleService.java:143-165`, `:298-310`).
  - `RcdoReadService` (RCDO read seam, [01](01-domain-persistence.md)) — validates an optional Supporting-Outcome id (unknown → `400`).
  - `AuditService` ([04](04-authorization-identity-audit.md)) — note-body-free audit rows for every mutation.
  - `Clock` + `OrgTimeConfig` — injectable time for SLA/overdue determinism.
- **Used by:** the four controllers — `PlanController` (`PlanController.java:54-80`), `CommitmentController`, `DisputeController`, `ReviewController` — which are thin pass-throughs documented in [02](02-api-web.md). The mappers `PlanMapper` and `CommitmentMapper` consume `AllowedActionResolver` to build `allowedActions[]`.

## How it works (flow)

**Plan lock — the safety culmination (`PlanLifecycleService.lock`, `:105`):**

```
authorize (owner-only, chokepoint) ── deny → 404/403 + audit, nothing loaded
        │  PlanLifecycleService.java:107
        ▼
load plan + commitments ── canLock false? ─→ diagnose: non-DRAFT→409 ISE
        │  :110/:115                          empty→EMPTY_PLAN_LOCK
        │                                     unlinked→UNLINKED_… + fieldErrors
        │                                     else fail-closed→409 ISE
        ▼
set LOCKED + lockedAt, save (@Version) ──→ create IC_PLANNING sync record
        │  :137-145                            (+ if manager: NOT_REVIEWED review
        ▼                                       w/ weekday SLA, projection refresh,
audit PLAN_LOCKED ──→ schedule after-commit SNS publish ──→ map to DTO
   :168              :171 (rule #4 non-blocking)            :178
```

1. `lock` authorizes IC-owner-only first; a denial throws before any load or mutation (pinned by `PlanLifecycleServiceTest.lock_deniedAuthorizer_neverLoadsOrMutates`, `:183`).
2. It reuses the **same** `AllowedActionResolver.canLock` predicate the affordance emits (`:115`), then — only on rejection — re-diagnoses the specific code: non-`DRAFT` → `IllegalStateTransitionException` (`:117`), no planned → `EmptyPlanLockException` (`:124`), any unlinked planned → `UnlinkedPlannedCommitmentException` with each unlinked commitment id in `fieldErrors` (`:131`), else a fail-closed generic `409` (`:133`). All four rejections leave the plan untouched (`verify(plans, never()).save(...)` across the test class).
3. On success it sets `LOCKED`+`lockedAt`, saves under `@Version`, creates the `IC_PLANNING` sync record (always), and — only when the IC has an active manager — creates a `NOT_REVIEWED` `ManagerReview` with a weekday-only SLA due date, refreshes the projection, and upserts the per-manager/week review block (`:147-166`). The no-manager branch skips all three (`PlanLifecycleServiceTest.lock_noManager_…`, `:232`).
4. The SNS pointer publish runs in an `afterCommit` `TransactionSynchronization` (`:298`) so a publish failure can never roll back the committed lock (safety #4; deferred detail in [06](06-calendar-sync-messaging.md)).

**Reconcile + carry-forward (IC reconcile, ARCHITECTURE Sequence (4)):** `startReconciliation` flips `LOCKED→RECONCILING` (`:193`); during `RECONCILING` the IC records outcomes via `CommitmentService.update` (`applyOutcome`, `:479`) and carries items forward via `CarryForwardService.carryForward` (`:73`) — which is the *only* path that sets `reconciliationOutcome=CARRIED_FORWARD`, is idempotent per source (existing successor returned untouched, `:91`), and seeds a self-linked PLANNED successor in next Monday's DRAFT plan (creating the shell if absent, `:99-117`). `closeReconciliation` (`:246`) then runs the completeness gate — every commitment needs an outcome, every UNPLANNED also needs a SO link — and throws `422 UNPLANNED_MISSING_LINK_AT_CLOSE` with per-field keys otherwise (`:255-271`); `CARRIED_FORWARD` is a non-null outcome so it satisfies the gate.

**Dispute single-unresolved (#6, `DisputeService.open`, `:89`):** authorize manager-of-owner → guard non-`DRAFT` → **service pre-check** `findByCommitmentIdAndStatusIn(commitmentId, {OPEN,IC_RESPONDED})` (`:104`) → persist with `saveAndFlush` whose **DB backstop** catch maps the partial-unique violation to `SecondOpenDisputeException` (`:115-122`) and rethrows any other constraint unmasked (`:121`). Both the pre-check and the race-backstop are pinned in `OpenDisputeServiceTest` (`:181`, `:194`, `:209`).

**Review derivation:** at lock, `ReviewSlaService.reviewDueAt` computes 17:00 org-tz on the next business day, skipping weekends (`ReviewSlaService.java:31-37`; Friday→Monday pinned in `ReviewSlaServiceTest.fridayLock_skipsWeekend_dueMonday1700`). At `markReviewed`, `ReviewStatusDeriver.unresolvedDisputeCount` drives `statusFor(...)` → `REVIEWED_WITH_DISPUTES` if any unresolved else `REVIEWED` (`ReviewService.java:76-78`). The mapper then derives `isOverdue` at read time (`ReviewMapper.java:33`).

## Design decisions & rationale

- **Transitions live in services, never controllers** (forbidden-pattern #4) so validation + the state change + audit + projection refresh + sync-record creation are one transaction. The plan machine is strictly **forward-only** — every backward/self transition is a `409` (ARCHITECTURE §3, `ARCHITECTURE.md:99`); there is no unlock/amend in MVP.
- **Single-source affordance↔enforcement (#1):** `lock` reuses `AllowedActionResolver.canLock` rather than re-implementing the predicate, so the button the UI offers and the gate the server enforces can never drift (ARCHITECTURE §15; `PlanLifecycleServiceTest` constructs the *real* resolver, `:68`). `canLock` returns true iff owner ∧ `DRAFT` ∧ ≥1 planned ∧ every planned has a `supportingOutcomeId` (`AllowedActionResolver.java:173-181`).
- **Affordance is UI-only, never authorization** (`AllowedAction.java:5`): the resolver's predicates *mirror* the void-throw authorizers as parallel booleans, and several (`canCarryForward`, `canMarkReviewed`) are a deliberately *narrowed subset* of enforcement — proven by the eligibility⟹preconditions sweeps in `AllowedActionResolverTest` (`:241`, `:328`). The services keep their own broader gate (e.g. `CarryForwardService` idempotent-accepts an already-carried re-invoke that the affordance hides).
- **`OVERDUE` derived not stored** (§3, `ARCHITECTURE.md:112`): avoids a stored state that drifts from wall-clock; the injectable `Clock` makes it test-deterministic. `REVIEWED_WITH_DISPUTES` satisfies the SLA (never overdue).
- **Rule-#2 has exactly two tightly-gated exceptions to baseline immutability**, both intentional (§3, `ARCHITECTURE.md:114`): (a) an UNPLANNED commitment's SO link is editable during `RECONCILING` only (the pre-close link — `CommitmentService.java:216-227`); (b) the IC's dispute *respond* may revise the disputed commitment's `supportingOutcomeId` — that field only, loaded by the dispute's own `commitmentId` (no cross-resource vector), audited (`DisputeService.java:164-177`).
- **`discard` instead of `delete`** for E7 (`CommitmentService.java:387`) to dodge a SpotBugs `MutableClasses` false-positive on the injecting controller — a build-gate accommodation, documented inline (`:380`).

## Gotchas & sharp edges

- **`closeReconciliation` raises `422`, the others raise `409`.** The completeness gate is `UNPLANNED_MISSING_LINK_AT_CLOSE` (HTTP 422, `UnplannedMissingLinkAtCloseException.java:16`), distinct from the lock-time 409s — an easy place to assume uniform status.
- **`canLock` is over-decomposed by design.** `lock` calls `canLock` and *then*, on a false result, re-derives why with three branches plus a fail-closed final `throw` (`PlanLifecycleService.java:115-133`). The fail-closed branch is intentionally unreachable on today's predicate but exists so that adding a new `canLock` reason can never silently let a lock through — pinned by `PlanLifecycleServiceTest.lock_canLockFalseUnknownReason_failsClosed409` (`:268`).
- **Opening/resolving a dispute must NOT mark a `NOT_REVIEWED` review reviewed.** `reDeriveReviewStatus` filters to non-`NOT_REVIEWED` reviews before re-deriving (`DisputeService.java:265-273`); the deriver only ever returns the two reviewed statuses. Pinned by `OpenDisputeServiceTest.open_onNotReviewedPlan_reviewStaysNotReviewed` (`:250`).
- **Empty `IN` guard.** `ReviewStatusDeriver.unresolvedDisputeCount` short-circuits to `0` on an empty commitment set because an empty SQL `IN` is invalid (`ReviewStatusDeriver.java:43`).
- **`PatchCommitmentRequest` is a presence-flag POJO, not a record** — `update` reads `…Provided()` flags to distinguish absent vs present-null vs value (`CommitmentService.java:196`, `:220-234`); this is load-bearing for the per-state allow-list and the manager-vs-IC field routing (`:196-202`). Detail of the DTO shape is in [02](02-api-web.md).
- **`carryForward` recomputes the *source* plan's projection, not the successor's** (`CarryForwardService.java:119`); the carried-IN count materializes only at next-week lock — a deliberate `§9` lockstep choice, not an omission.
- **DRIFT (low):** the architecture's `allowedActions` table lists `START_RECONCILIATION` precondition as plan `LOCKED` and `ADD_UNPLANNED` as `LOCKED` only (`ARCHITECTURE.md:366`, `:368`), but the code's `AllowedActionResolver.canAddUnplanned` grants `ADD_UNPLANNED` in **both** `LOCKED` and `RECONCILING` (`AllowedActionResolver.java:74-77`), matching `CommitmentService.createUnplanned`'s enforcement which accepts both states (`CommitmentService.java:128`). The code (affordance↔enforcement aligned at both states) is internally consistent and is the safer reading; the §F.4 table row appears narrower than the implemented behavior. Evidence: `AllowedActionResolverTest.addUnplanned_present_whenLockedOrReconcilingOwner` (`:130`) asserts both states.
- **`AllowedAction` carries values not yet emitted by any resolver path** (`RESPOND_DISPUTE`, `RESOLVE_DISPUTE`, `COMMENT`, `RETRY_SYNC` — `AllowedAction.java:23-26`). These are vocabulary placeholders; the resolver only emits actions whose enforcement exists ("no affordance without enforcement"), so their absence from `planActions`/`commitmentActions` output is intentional.

## Connects to

- [01-domain-persistence.md](01-domain-persistence.md) — the JPA entities (`WeeklyPlan`, `WeeklyCommitment`, `AlignmentDispute`, `ManagerReview`), their `@Version` columns, the partial-unique index `uq_one_unresolved_dispute_per_commitment` backing #6, and the repositories every service loads through.
- [02-api-web.md](02-api-web.md) — the controllers that call these methods, the request/response DTOs (`PatchCommitmentRequest` presence flags, `WeeklyPlanDto.allowedActions[]`), and the RFC-7807 advice that renders every typed exception above.
- [04-authorization-identity-audit.md](04-authorization-identity-audit.md) — `DomainAuthorizationService` (the `authorize…Mutation` chokepoints called first in every method) and `AuditService` (the note-body-free audit rows).
- [05-manager-projections.md](05-manager-projections.md) — `ProjectionRefresher.recomputeForPlan`, called in-transaction after lock/start/close/unplanned-create/outcome/dispute/review writes.
- [06-calendar-sync-messaging.md](06-calendar-sync-messaging.md) — `SyncRecordService` + `SnsLifecyclePublisher`; the sync records created at lock/start and the strictly-non-blocking after-commit publish (#4).

---
_Generated by `/layer-docs` (initial run) against commit `3b919a9` on 2026-06-04. Claims are anchored to code; `UNVERIFIED` marks anything not confirmed._
