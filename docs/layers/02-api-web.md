# API & Web Layer

## Executive summary

This layer is the HTTP front door of the backend `api` module: it is where browser requests turn into Java calls and where Java results (or failures) turn back into JSON. It owns seven thin REST controllers (one per domain — me, rcdo, plan, commitment, dispute, manager, review), the request/response data-transfer objects (DTOs) that travel over the wire, the mappers that build those DTOs from internal JPA entities, the server-side input validation that rejects bad input before it reaches business logic, and a single uniform error model (RFC-7807 `application/problem+json`) that renders every failure with a stable machine-readable `code`. Two iron rules live here: controllers never contain business logic — they delegate immediately to a service — and a JPA entity never crosses the API boundary; everything the client sees is a purpose-built DTO `record`. This is also the wire-level home of three safety invariants: the `409` conflict codes that block an illegal lock or a locked-baseline edit, and the `403`/`404` rendering of authorization denials. The layer depends inward on the lifecycle/authorization/projection services; everything outside (the frontend, integration tests) depends on the exact shapes it publishes.

## Responsibilities

- **HTTP routing + binding (the 7 controllers).** Map an HTTP method + path to a Java method, bind path variables / query params / the JSON body, run `@Valid` Jakarta Bean Validation, then delegate to exactly one service. Controllers are deliberately thin — they hold no lifecycle transitions, no authorization decisions, no queries. (`apps/wc-api/api/.../plan/PlanController.java:22`, and the six siblings.)
- **Request DTOs + input validation.** Define the validated `record`/POJO shape of every write request, normalize user text once at the boundary, and enforce field caps in Unicode code points. (`apps/wc-api/api/.../commitment/dto/CreateCommitmentRequest.java:25`.)
- **Response DTOs + mappers.** Define the exact wire shape of every read/write response and assemble it from entities — enforcing the "DTOs cross the boundary, never JPA entities" rule. (`apps/wc-api/api/.../plan/mapper/PlanMapper.java:48`.)
- **The RFC-7807 error model.** Translate every exception thrown during controller invocation into a uniform `application/problem+json` body carrying a `safeMessage`, a `traceId`, and an optional named `code`. (`apps/wc-api/api/.../web/ProblemDetailsExceptionHandler.java:43`.)
- **`allowedActions[]` affordance vocabulary.** Stamp server-authoritative UI-affordance hints onto response DTOs (F.4) — a display hint, never the authorization source.

**NOT this layer's job (delegated):** lifecycle transitions (lock / reconciliation) and write-side state rules → [03-application-lifecycle.md](03-application-lifecycle.md); the manager read-model Criteria queries that back E13/E14/E15 → [05-manager-projections.md](05-manager-projections.md); the security filter chain, JWT decode, demo-identity gate, and the `DomainAuthorizationService` IDOR checks → [04-authorization-identity-audit.md](04-authorization-identity-audit.md); sync-record endpoints E22/E23 → [06-calendar-sync-messaging.md](06-calendar-sync-messaging.md); comment endpoints E20/E21 → [08-comments-collaboration.md](08-comments-collaboration.md). For all of these the controllers in this layer are only the thin entry seam.

## Key components

### The 7 controllers

| Controller | Endpoints (E#) | Delegates to | Where |
|-----------|----------------|--------------|-------|
| `MeController` | E1 `GET /api/me` | `MeService` | `apps/wc-api/api/.../me/MeController.java:17` |
| `RcdoController` | E2 `GET /api/rcdo` | `RcdoReadService` | `apps/wc-api/api/.../rcdo/RcdoController.java:17` |
| `PlanController` | E3 `GET /api/plans/current`, E4 `GET /api/plans/{id}`, E8 `lock`, E9 `start-reconciliation`, E10 `close-reconciliation` | `PlanService`, `PlanLifecycleService` | `apps/wc-api/api/.../plan/PlanController.java:22` |
| `CommitmentController` | E5 create, E11 unplanned-create, E12 carry-forward, E6 `PATCH`, E7 `DELETE` | `CommitmentService`, `CarryForwardService` | `apps/wc-api/api/.../commitment/CommitmentController.java:37` |
| `DisputeController` | E17 open, E18 respond, E19 resolve | `DisputeService` | `apps/wc-api/api/.../dispute/DisputeController.java:24` |
| `ManagerController` | E13 command-center, E14 heatmap, E15 drilldown | `ManagerQueryService`, `ManagerHeatmapService`, `ManagerDrilldownService` | `apps/wc-api/api/.../manager/ManagerController.java:34` |
| `ReviewController` | E16 mark-reviewed | `ReviewService` | `apps/wc-api/api/.../review/ReviewController.java:22` |

(E20–E23 — comments + sync — exist in the catalog but their controllers live in the comment/sync packages, deferred to layers 08 and 06.)

### The `web/` error-model package

| Component | What it does | Where |
|-----------|--------------|-------|
| `ProblemDetailsExceptionHandler` | `@RestControllerAdvice` mapping controller-invocation exceptions → RFC-7807 (400/403/404/409/422/500) | `apps/wc-api/api/.../web/ProblemDetailsExceptionHandler.java:43` |
| `ProblemDetailFactory` | Stateless builder for the problem+json body (`safeMessage`+`traceId`+`code`); `write()` flattens for the filter-level handlers | `apps/wc-api/api/.../web/ProblemDetailFactory.java:22` |
| `ProblemDetailsAuthenticationEntryPoint` | Renders chain-level 401 (no/invalid credentials) — never echoes the exception detail | `apps/wc-api/api/.../web/ProblemDetailsAuthenticationEntryPoint.java:18` |
| `ProblemDetailsAccessDeniedHandler` | Renders chain-level 403 (coarse filter denial) | `apps/wc-api/api/.../web/ProblemDetailsAccessDeniedHandler.java:18` |
| `ErrorCodes` | The named-code string constants (`VALIDATION_ERROR`, `ILLEGAL_STATE_TRANSITION`, `LOCKED_BASELINE_EDIT`, `EMPTY_PLAN_LOCK`, `UNLINKED_PLANNED_COMMITMENT`, `UNPLANNED_MISSING_LINK_AT_CLOSE`, `SECOND_OPEN_DISPUTE`) | `apps/wc-api/api/.../web/ErrorCodes.java:11` |
| `ValidationException` | Service-layer validation failure carrying `fieldErrors` → 400 | `apps/wc-api/api/.../web/ValidationException.java:12` |
| `IllegalStateTransitionException` | Illegal source-state action (+ optional `constraint`) → 409 | `apps/wc-api/api/.../web/IllegalStateTransitionException.java:12` |
| `LockedBaselineEditException` | Frozen post-lock baseline edit (rule #2) → 409 `LOCKED_BASELINE_EDIT` | `apps/wc-api/api/.../web/LockedBaselineEditException.java:12` |
| `EmptyPlanLockException` | Lock with no planned commitments (rule #1) → 409 `EMPTY_PLAN_LOCK` | `apps/wc-api/api/.../web/EmptyPlanLockException.java:8` |
| `UnlinkedPlannedCommitmentException` | Lock with an unlinked planned commitment (rule #1) → 409 + `fieldErrors` | `apps/wc-api/api/.../web/UnlinkedPlannedCommitmentException.java:12` |
| `UnplannedMissingLinkAtCloseException` | Close-reconciliation incompleteness → 422 + per-commitment `fieldErrors` | `apps/wc-api/api/.../web/UnplannedMissingLinkAtCloseException.java:16` |
| `SecondOpenDisputeException` | Second unresolved dispute (rule #6) → 409 `SECOND_OPEN_DISPUTE` | `apps/wc-api/api/.../web/SecondOpenDisputeException.java:10` |
| `CodePointSize` / `CodePointSizeValidator` | Bean-Validation constraint capping length in Unicode code points (not UTF-16) | `apps/wc-api/api/.../web/CodePointSize.java:29`, `CodePointSizeValidator.java:12` |
| `NoControlChars` / `NoControlCharsValidator` | Rejects C0/C1 control chars on single-line fields (title) | `apps/wc-api/api/.../web/NoControlChars.java:29`, `NoControlCharsValidator.java:11` |

### The `action/` package

| Component | What it does | Where |
|-----------|--------------|-------|
| `AllowedAction` | The 11-value computed affordance enum (`LOCK`…`RETRY_SYNC`) — a DTO-layer vocab, NOT a persisted `shared/enums/` member | `apps/wc-api/api/.../action/AllowedAction.java:15` |
| `AllowedActionResolver` | Computes the per-actor / per-state `allowedActions[]` from the same predicates the lifecycle services enforce | `apps/wc-api/api/.../plan/AllowedActionResolver.java:31` |

## Interfaces & contracts

### Endpoint catalog owned here (Appendix B.2 E1–E19, minus comments/sync)

```
E1   GET   /api/me                                    → MeDto                (self; no authz call)
E2   GET   /api/rcdo                                  → RcdoTreeDto          (org-wide; no authz call)
E3   GET   /api/plans/current                         → WeeklyPlanDto        (self-scoped)
E4   GET   /api/plans/{id}                            → WeeklyPlanDto        (authz chokepoint → 404 IDOR)
E5   POST  /api/plans/{id}/commitments                → 201 WeeklyCommitmentDto   (@Valid CreateCommitmentRequest)
E6   PATCH /api/commitments/{id}                      → 200 WeeklyCommitmentDto   (PatchCommitmentRequest, no @Valid)
E7   DELETE /api/commitments/{id}                     → 204
E8   POST  /api/plans/{id}/lock                       → WeeklyPlanDto        (no body; rule #1 gates)
E9   POST  /api/plans/{id}/start-reconciliation       → WeeklyPlanDto        (no body)
E10  POST  /api/plans/{id}/close-reconciliation       → WeeklyPlanDto        (no body; 422 on incomplete)
E11  POST  /api/plans/{id}/unplanned-commitments      → 201 WeeklyCommitmentDto   (@Valid CreateUnplannedCommitmentRequest)
E12  POST  /api/commitments/{id}/carry-forward        → 201 WeeklyCommitmentDto   (no body; idempotent)
E13  GET   /api/manager/command-center?weekStart=…    → PageEnvelope<ManagerCommandCenterRowDto>
E14  GET   /api/manager/heatmap?weekStart=…           → HeatmapResponseDto   (not paginated)
E15  GET   /api/manager/heatmap/{cellId}/drilldown    → HeatmapDrilldownDto  (own cell)
E16  POST  /api/manager/reviews/{reviewId}/mark-reviewed → ManagerReviewDto  (@Valid optional MarkReviewedRequest)
E17  POST  /api/commitments/{id}/disputes             → 201 AlignmentDisputeDto   (@Valid OpenDisputeRequest)
E18  POST  /api/disputes/{id}/respond                 → 200 AlignmentDisputeDto   (@Valid RespondDisputeRequest)
E19  POST  /api/disputes/{id}/resolve                 → 200 AlignmentDisputeDto   (body-less in MVP)
```

The mapping is verified against Appendix B.2 (`ARCHITECTURE.md:381`) and matches one-for-one; the controller javadoc on each method cites its E-number (e.g. `PlanController.java:54` annotates E8 lock).

### What controllers expect from callers

Every resource method takes `@AuthenticationPrincipal UserPrincipal principal` — the caller already resolved by the security chain (layer 04). `@PathVariable` ids are `UUID` (a malformed id fails binding → 400, never 500, via `handleTypeMismatch`, `ProblemDetailsExceptionHandler.java:103`). The two self/org-wide reads (`/api/me`, `/api/rcdo`) and the self-scoped `/api/plans/current` carry only the coarse authn gate and make **no** `DomainAuthorizationService` call (`PlanController.java:33`, `RcdoController.java:25`, `MeController.java:26`); every other resource endpoint relies on the service authorizing first.

### Request DTO contracts

| DTO | Endpoint | Shape / validation | Where |
|-----|----------|--------------------|-------|
| `CreateCommitmentRequest` (record) | E5 | `title @NotBlank @NoControlChars @CodePointSize(255)`, `description @CodePointSize(4000)`, `supportingOutcomeId?`, `priority/workType/confidence @NotNull`, `alignmentStatus?`; normalizes in compact ctor | `apps/wc-api/api/.../commitment/dto/CreateCommitmentRequest.java:25` |
| `CreateUnplannedCommitmentRequest` (record) | E11 | inverse of E5 — **no `workType`/`commitmentKind`** (server-forced to `UNPLANNED`) | `apps/wc-api/api/.../commitment/dto/CreateUnplannedCommitmentRequest.java:27` |
| `PatchCommitmentRequest` (mutable POJO) | E6 | presence-flag POJO (not a record) for 3-way PATCH semantics; carries no bean constraints — validation lives in the service | `apps/wc-api/api/.../commitment/dto/PatchCommitmentRequest.java:30` |
| `OpenDisputeRequest` (record) | E17 | `flagType @NotNull`, `managerNote @NotBlank @CodePointSize(4000)` | `apps/wc-api/api/.../dispute/dto/OpenDisputeRequest.java:18` |
| `RespondDisputeRequest` (record) | E18 | `icResponse? @CodePointSize(4000)`, `supportingOutcomeId?`; at-least-one-required is service-checked | `apps/wc-api/api/.../dispute/dto/RespondDisputeRequest.java:17` |
| `MarkReviewedRequest` (record) | E16 | optional body; `summaryNote? @CodePointSize(4000)` | `apps/wc-api/api/.../review/dto/MarkReviewedRequest.java:13` |

### Response DTO contracts (all `record`s, never entities)

`MeDto` (B.3, `me/dto/MeDto.java:14`); `RcdoTreeDto`→`RallyCryNode`→`DefiningObjectiveNode`→`SupportingOutcomeNode` (B.4, `rcdo/dto/RcdoTreeDto.java:13`); `WeeklyPlanDto` (B.5, `plan/dto/WeeklyPlanDto.java:20`) nesting `WeeklyCommitmentDto[]` + `ManagerReviewDto?` + `allowedActions[]`; `WeeklyCommitmentDto` (B.6, `commitment/dto/WeeklyCommitmentDto.java:27`) nesting `RcdoBreadcrumbDto?` + `AlignmentDisputeDto?` + `allowedActions[]`; `ManagerReviewDto` (B.7, `review/dto/ManagerReviewDto.java:19`) with derived `isOverdue`; `AlignmentDisputeDto` (B.8, `dispute/dto/AlignmentDisputeDto.java:18`); the manager read DTOs `ManagerCommandCenterRowDto`/`PageEnvelope<T>`/`HeatmapResponseDto`/`HeatmapCellDto`/`HeatmapDrilldownDto`/`DrilldownOutcomeGroup` (B.11/B.12/B.20, `manager/dto/`).

## Data & state

This layer is **stateless** — it holds no persistent state of its own. It transforms two state representations:

- **Inbound:** raw JSON → a request DTO. User text is normalized exactly **once** in the request `record`'s compact constructor (e.g. `CreateCommitmentRequest.java:34` calls `TextNormalizer.normalizeSingleLine`/`normalizeMultiLine`) so Bean Validation runs on the stored form. `PatchCommitmentRequest` is the lone exception — a mutable presence-flag POJO that defers normalization to the service.
- **Outbound:** JPA entity → response DTO via a `@Component` mapper. `PlanMapper.toWeeklyPlanDto` (`plan/mapper/PlanMapper.java:48`) reads `WeeklyPlan` + its `WeeklyCommitment` list, delegates each commitment to `CommitmentMapper` (`commitment/mapper/CommitmentMapper.java:63`), resolves the RC→DO→SO breadcrumb and the nested unresolved dispute, computes counts, and stamps `allowedActions`.

**Enums on the wire** (B.1) are the shared `com.st6.wc.enums.*` types (`PlanState`, `Priority`, `WorkType`, `Confidence`, `AlignmentStatus`, `ReconciliationOutcome`, `ReviewStatus`, `DisputeStatus`, `FlagType`, `RiskBadge`, `CommitmentKind`, `RoleType`). They are owned by the domain layer ([01-domain-persistence.md](01-domain-persistence.md)); this layer only references them in DTO components. The wire envelope is `application/json` for success and `application/problem+json` for errors (`ProblemDetailFactory.java:51`).

**Three non-persisted vocabularies live here** (deliberately NOT in `shared/enums/`, so they stay out of the 16-enum `EnumVocabularyTest` pin): `AllowedAction` (`action/AllowedAction.java:15`), `ErrorCodes` string constants (`web/ErrorCodes.java:11`), and `ReviewStateFilter` (the E13 query filter vocab, `manager/dto/ReviewStateFilter.java:11`).

### `allowedActions[]` (Appendix F.4) — the affordance vocabulary

The 11 values and where each surfaces, per `AllowedActionResolver`:

| Action | Surfaces on | Granted when (code) |
|--------|-------------|---------------------|
| `LOCK` | WeeklyPlanDto | owner ∧ DRAFT ∧ ≥1 planned ∧ all planned linked (`AllowedActionResolver.java:173` `canLock`) |
| `START_RECONCILIATION` | WeeklyPlanDto | owner ∧ LOCKED (`:162`) |
| `CLOSE_RECONCILIATION` | WeeklyPlanDto | owner ∧ RECONCILING (`:64`) |
| `ADD_UNPLANNED` | WeeklyPlanDto | owner ∧ (LOCKED ∨ RECONCILING) (`:74`) |
| `CARRY_FORWARD` | WeeklyCommitmentDto | owner ∧ RECONCILING ∧ not already carried (`:150`) |
| `MARK_REVIEWED` | ManagerReviewDto | direct manager ∧ plan≠DRAFT ∧ NOT_REVIEWED (`:128`) |
| `OPEN_DISPUTE` | WeeklyCommitmentDto | direct manager ∧ plan≠DRAFT ∧ no unresolved dispute (`:112`) |
| `RESPOND_DISPUTE` | AlignmentDisputeDto | owning IC ∧ dispute OPEN (`DisputeMapper.java:70`) |
| `RESOLVE_DISPUTE` | AlignmentDisputeDto | direct manager ∧ OPEN\|IC_RESPONDED (`DisputeMapper.java:73`) |
| `COMMENT` | plan/commitment | **not emitted yet** — enforcing slice (comments, layer 08) not wired |
| `RETRY_SYNC` | OutlookSyncRecordDto | not emitted here — sync layer 06 |

`allowedActions` is a **UI affordance only — never the authorization source** (`AllowedAction.java` javadoc): the server re-validates eligibility on every mutation. Each non-empty value is computed from the *same predicate* the enforcing service uses ("no affordance without enforcement"), so the button and the gate never drift.

## Dependencies

- **Depends on:**
  - **Application & lifecycle services** ([03](03-application-lifecycle.md)) — `PlanService`, `PlanLifecycleService`, `CommitmentService`, `CarryForwardService`, `DisputeService`, `ReviewService`, `MeService`, `RcdoReadService`. Controllers do nothing but delegate to these (`CommitmentController.java:42` constructor-injects two of them).
  - **Manager read projections** ([05](05-manager-projections.md)) — `ManagerQueryService`/`ManagerHeatmapService`/`ManagerDrilldownService` back E13/E14/E15.
  - **Authorization / identity** ([04](04-authorization-identity-audit.md)) — the `UserPrincipal` arriving via `@AuthenticationPrincipal`; the chain-level entry-point/access-denied handlers in `web/` are wired into the SecurityFilterChain there. The `DomainAuthorizationException` types it throws are *rendered* here but *thrown* there.
  - **Domain entities + enums** ([01](01-domain-persistence.md)) — the JPA entities the mappers read (`WeeklyPlan`, `WeeklyCommitment`, `AlignmentDispute`, `ManagerReview`) and the wire enums.
  - `TextNormalizer` (`com.st6.wc.commitment.TextNormalizer`, lives in the commitment service package) — the single normalize/code-point/control-char helper reused by both the request DTOs and the validators.
- **Used by:**
  - The **frontend** (`apps/wc-web`, [09-frontend.md](09-frontend.md)) — `dtos.ts` mirrors these DTO shapes field-for-field; the RTK Query types are the API contract.
  - The **E2E suite** (`apps/wc-e2e`) and the api-module endpoint tests (`*EndpointTest.java`) — which assert status codes, `code` values, and `.doesNotExist()` entity-leak checks against these shapes.

## How it works (flow)

A write request (E5 create commitment) walks the layer like this:

```
 HTTP POST /api/plans/{id}/commitments  (Bearer JWT, JSON body)
        │
        ▼
 [SecurityFilterChain — layer 04]  authn → UserPrincipal in SecurityContext
        │  (401 here → ProblemDetailsAuthenticationEntryPoint, problem+json)
        ▼
 CommitmentController.create(principal, planId, @Valid request)   ← this layer
        │  @Valid runs Bean Validation on the *normalized* DTO
        │  (constraint fail → MethodArgumentNotValidException → 400 VALIDATION_ERROR + fieldErrors)
        ▼
 CommitmentService.create(...)   [layer 03]
        │  authorizes owner-only FIRST (chokepoint), then state rules
        │  (cross-owner/missing → 404 IDOR; manager-of-owner → 403 OWNER_REQUIRED)
        ▼
 CommitmentMapper.toDto(entity)   ← this layer (entity → DTO record)
        │
        ▼
 201 Created  WeeklyCommitmentDto (JSON)
```

Anchors for the path: `CommitmentController.java:48` (mapping + `@ResponseStatus(CREATED)`), DTO normalization at `CreateCommitmentRequest.java:34`, the validator at `CodePointSizeValidator.java:22`, and DTO assembly at `CommitmentMapper.java:98`.

**The error path is centralized.** Any exception thrown *during controller invocation* lands in `ProblemDetailsExceptionHandler` (`web/ProblemDetailsExceptionHandler.java:43`), which maps each known exception type to a status + `safeMessage` + `code` via `ProblemDetailFactory.of` (`web/ProblemDetailFactory.java:31`). The unknown-fallback is a generic 500 that never leaks the exception detail (`:191`). Critically — the javadoc and Lesson §19 note this — **chain-filter authn/authz exceptions never reach the advice**; those are rendered separately by `ProblemDetailsAuthenticationEntryPoint` (401) and `ProblemDetailsAccessDeniedHandler` (403), which call `ProblemDetailFactory.write` to flatten the body identically off the MVC path.

**A read (E4 plan-by-id)** flows: `PlanController.byId` (`PlanController.java:42`) → `PlanService.getPlanById` (authorizes first) → `PlanMapper.toWeeklyPlanDto` which resolves `viewerIsDirectManager` once (`PlanMapper.java:59`), threads it through each commitment + dispute affordance, and stamps plan-level `allowedActions`.

## Design decisions & rationale

- **Thin controllers, fat services** (ARCHITECTURE §5; forbidden-pattern #4). Every controller method is one delegating call. Lifecycle transitions must run inside one service transaction with validation + projection updates, so they cannot live in a controller. `PlanController.lock` is a no-body POST that just calls `planLifecycleService.lock` (`PlanController.java:54`).
- **DTOs cross the boundary, never entities** (§5 binding contract `ARCHITECTURE.md:324`; forbidden-pattern #3). Returning a JPA entity would cause lazy-init failures and over-expose internal/audit columns. Every response is a `record` and the endpoint tests assert entity-only fields `.doesNotExist()`. This is enforced *structurally* by making the mappers the only entity→wire path.
- **Normalize once at the boundary** (§16 / Appendix E; Lesson §26). User text is stripped/collapsed/NFC-normalized in the request record's compact constructor, so validation and persistence both see the canonical form and read paths never re-normalize. Caps are in **code points** (`CodePointSize`, not Jakarta `@Size`) so an astral-plane title isn't miscounted (`CodePointSize.java:18`).
- **PATCH needs a presence-flag POJO, not an `Optional` record** (Lesson §27). Jackson collapses an absent `Optional` parameter into `Optional.empty()` — indistinguishable from an explicit JSON `null` — which breaks "absent vs clear-nullable" and makes Hibernate Validator spuriously reject absent `@NotBlank` fields. Hence `PatchCommitmentRequest` is a mutable bean whose setters record a `*Provided` flag (`PatchCommitmentRequest.java:43`), and it carries **no** `@Valid` annotation on the controller (`CommitmentController.java:75`).
- **One uniform error model** (§5 / B.21; Lesson §19/§20). A single RFC-7807 shape `{type,title,status,detail,safeMessage,code,constraint?,fieldErrors?,traceId}` means clients branch on one stable `code`. `safeMessage` is always generic (never the exception detail) so internal state can't leak (rule #7). `ProblemDetailFactory` is a pure static util (no stored mutable `ObjectMapper`) to dodge SpotBugs EI2 (`ProblemDetailFactory.java:24`, Lesson §20).
- **Affordance ↔ enforcement single-source** (§15; Lesson §24/§31/§35). `allowedActions` is computed from the same predicate the service enforces (e.g. `canLock` is reused by `PlanLifecycleService`), under "no affordance without enforcement" — an action is emitted only by the slice that enforces it, so the UI never offers a button the server would reject.

## Gotchas & sharp edges

- **The advice does NOT see filter-level failures.** A 401 (bad/absent token) or a coarse chain 403 is rendered by the entry-point / access-denied handlers, NOT `ProblemDetailsExceptionHandler`. If you add a new error type, decide which path it travels — a service-thrown exception goes through the advice; a filter-thrown one needs `ProblemDetailFactory.write`. (Lesson §19/§20.)
- **`OptimisticLockingFailureException` is rendered as `409 ILLEGAL_STATE_TRANSITION`, not a dedicated code** (`ProblemDetailsExceptionHandler.java:181`). A concurrent double-lock surfaces the same `code` as a stale-state action. Clients can't distinguish "you were too late" from "wrong state" by `code` alone.
- **Two distinct post-lock 409s.** Editing a frozen baseline field → `LOCKED_BASELINE_EDIT` (rule #2). Editing `alignmentStatus` post-lock → `ILLEGAL_STATE_TRANSITION` + `constraint=alignment_status_read_only_post_lock` — because `alignmentStatus` is *not* part of the immutable planned baseline (B.1 cross-cutting rule 2, `ARCHITECTURE.md:1085`; `LockedBaselineEditException.java:8` javadoc spells out the distinction).
- **Close-reconciliation incompleteness is `422`, lock-time unlinked is `409`.** Both carry `fieldErrors`, but `UnplannedMissingLinkAtCloseException` → 422 (`ProblemDetailsExceptionHandler.java:165`) while `UnlinkedPlannedCommitmentException` → 409 (`:151`). The `fieldErrors` key is `commitments[<commitmentId>].<field>` keyed by commitment **id**, not array index (`UnplannedMissingLinkAtCloseException.java:9`).
- **E19 (resolve) is body-less in MVP** but the controller has no `@RequestBody` at all (`DisputeController.java:65`) — any provided body is silently ignored. The catalog's `resolutionNote` is unimplemented.
- **`unknown enum → 400, never 500`** relies on `HttpMessageNotReadableException` → `handleUnreadable` extracting only the safe field *name* from the Jackson path, never the offending value (`ProblemDetailsExceptionHandler.java:82`, §15). Same for `MethodArgumentTypeMismatchException` (bad query enum/UUID/date).
- **`code` is a free string, not an enum.** `ErrorCodes` is `static final String` constants (`ErrorCodes.java:11`); several authorization codes (`MANAGER_ROLE_REQUIRED`, `PLAN_OWNER_REQUIRED`, `COMMITMENT_OWNER_REQUIRED`, `IC_CANNOT_RESOLVE_DISPUTE`, `IC_CANNOT_OPEN_DISPUTE`, `MANAGER_CANNOT_RESPOND_DISPUTE`, `IC_CANNOT_WRITE_MANAGER_NOTE`) are **authorizer-local constants in `DomainAuthorizationService`** (layer 04), NOT in `ErrorCodes`. So the full B.21 code vocabulary is split across two packages — don't grep only `web/ErrorCodes.java` expecting all of them.

### Drift between ARCHITECTURE.md and the code

- **Rule #5 (demo-disabled `403`) is NOT rendered in this layer.** The brief asks to surface it here, but the actual rendering is the `DemoAuthFilter` (in `config/`, layer 04) — it does not call this layer's `ProblemDetailFactory` (no `ProblemDetailFactory` reference in `DemoAuthFilter.java`). This layer's `web/` handlers render 401/403/404/409/422/500 for the MVC + chain paths, but the env-gated demo backdoor rejection is owned by layer 04. Documented as low-severity drift vs. the brief, not vs. ARCHITECTURE.md.
- **`fieldErrors` is a `Map<String,String>` in code, an array-of-objects in B.21.** The handler emits `body.setProperty("fieldErrors", Map<field,message>)` (`ProblemDetailsExceptionHandler.java:159`/`:206`), i.e. a JSON object `{ "commitments[..].supportingOutcomeId": "..." }`. The B.21 example (`ARCHITECTURE.md:694`) shows `fieldErrors` as an **array** of `{field,code,message}` objects. The shapes differ (object-map vs object-array, and the code lacks the per-error `code` sub-field). Medium-severity wire-shape drift — frontend mirrors must match the *code's* map shape, not the doc's array.
- **`MARK_REVIEWED` granted-when narrows the doc.** B.1 (`ARCHITECTURE.md:370`) says "actor = direct manager, plan `LOCKED`+"; the code additionally requires `reviewStatus == NOT_REVIEWED` (`AllowedActionResolver.java:128`) so the affordance hides once reviewed. This is the documented §31 UX-narrowing subset (affordance ⟹ enforcement holds), not a contract violation — low severity, intentional.

## Connects to

- **[01-domain-persistence.md](01-domain-persistence.md)** — supplies the JPA entities the mappers read and the `com.st6.wc.enums.*` wire enums; the "entity never crosses the boundary" rule is the seam between the two layers.
- **[03-application-lifecycle.md](03-application-lifecycle.md)** — every non-read controller method delegates to a lifecycle/write service; this layer renders the `409`/`422` exceptions those services throw (lock gates, baseline immutability, dispute uniqueness).
- **[04-authorization-identity-audit.md](04-authorization-identity-audit.md)** — supplies the `UserPrincipal` and the `DomainAuthorizationService` that authorizes inside the services; throws `AuthorizationDeniedException`/`ResourceNotFoundOrUnauthorizedException` that *this* layer's advice renders as `403`/`404`; owns the chain-level 401/demo-403 handlers wired from `web/`.
- **[05-manager-projections.md](05-manager-projections.md)** — `ManagerController` (E13/E14/E15) delegates to the read-projection query services; the `PageEnvelope`/`ManagerCommandCenterRowDto`/heatmap DTOs are defined here, populated there.
- **[06-calendar-sync-messaging.md](06-calendar-sync-messaging.md)** — E22/E23 sync-record endpoints + their DTO live there; the `RETRY_SYNC` affordance value is reserved here.
- **[08-comments-collaboration.md](08-comments-collaboration.md)** — E20/E21 comment endpoints + the `COMMENT` affordance (declared but not yet emitted) live there.
- **[09-frontend.md](09-frontend.md)** — consumes every DTO shape; `dtos.ts` mirrors these records field-for-field and branches on the RFC-7807 `code`.

---
_Generated by `/layer-docs` (initial run) against commit `3b919a9` on 2026-06-04. Claims are anchored to code; `UNVERIFIED` marks anything not confirmed._
