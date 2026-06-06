# Comments & Collaboration

## Executive summary

Comments let an IC and their manager hold a lightweight discussion thread attached to a specific weekly plan or a specific commitment — flat, one-level, no nesting in the MVP. The architecture (§11) positions comments as collaboration that is *visible but never blocks* the core weekly lifecycle. This layer is meant to span three places: a JPA persistence model + repository in the `shared` Gradle module, a backend HTTP surface (list + create endpoints E20/E21) in the `api` module, and an RTK Query slice plus thread/list/form UI in the frontend (`apps/wc-web`). **In reality only two of those three are built.** The persistence model and the frontend exist and are tested; the backend HTTP surface (a `CommentController` / `CommentService`) was never written. The result is a feature that is fully designed and partly built but **non-functional end-to-end against the real API** — the live frontend renders comments only against in-process MSW mocks, and the production `GET/POST /api/comments` paths have no server handler at all.

## Status: PARTIAL (drift)

This feature is **partially built and does not work end-to-end against the real backend.**

- **Built:** the `Comment` JPA entity + `CommentRepository` (in `shared`), the `CommentTargetType` enum, the `comment` table in the V1 migration, and the comment *authorizer* methods (`authorizeCommentAccess` / `authorizeCommentTargetAccess`) on `DomainAuthorizationService`. Plus the entire frontend stack: the `commentsApi` RTK Query slice, `CommentThread` / `CommentList` / `CommentForm` components, the `CommentDto` / `CreateCommentRequest` types, and their tests.
- **Missing:** there is **no `CommentController` and no `CommentService`** anywhere in the `api` module. The E20 (`GET /api/comments`) and E21 (`POST /api/comments`) endpoints the architecture specifies, and the frontend calls, have **no server handler.**
- **Consequence:** against a real backend the frontend's `/api/comments` requests would 404 (no route). The comment authorizer methods that *do* exist are dead code — referenced only by tests, never by a production caller. The feature renders today only because the standalone demo serves `/api/comments` from MSW mocks.

Evidence is recorded in **Gotchas & sharp edges** and in the drift findings.

## Responsibilities

- **Persist flat comments** keyed to a `{targetType, targetId}` pair (a plan or a commitment) — owned by the `shared` module (`Comment` entity + `CommentRepository` + the `comment` table). The schema is *nestable* (carries `parent_comment_id`, `path`, `depth`) but the MVP is flat: `depth` defaults `0`, `parent_comment_id` stays null.
- **Authorize comment access** — owned by `DomainAuthorizationService` (the §6 central authorizer), which exposes `authorizeCommentAccess(commentId)` and `authorizeCommentTargetAccess(targetType, targetId)` resolving the target to its owning plan and applying the IC-self / manager-direct-report rule. **NOT** wired to any endpoint (see drift).
- **Expose the comment thread in the UI** — owned by the frontend `comment` feature: a collapsible `CommentThread` (gated on the server's `COMMENT` allowed-action), a paginated `CommentList`, and a `CommentForm`, driven by the `commentsApi` RTK Query slice.
- **What it does NOT own / delegates:**
  - The **HTTP request/response surface** (controllers, services, error mapping). The architecture assigns this to the `api` module — **but it was never built here.** See [API & Web Layer](02-api-web.md) for the pattern other endpoints follow.
  - The **DTO-across-the-boundary contract** mirror lives in the frontend `dtos.ts` and the architecture Appendix B.9; the backend `CommentDto` record that should mirror it **does not exist** (there is no `api/comment/dto/` package).
  - **Dispute IC-response text** — comments are explicitly *not* the dispute response channel (§11: the IC response uses `alignment_dispute.ic_response`). See [Application & Lifecycle Services](03-application-lifecycle.md).
  - **Identity / auth filter mechanics** — see [Authorization, Identity & Audit](04-authorization-identity-audit.md).

## Key components

| Component | What it does | Where |
|-----------|--------------|-------|
| `Comment` (JPA entity) | Flat-in-MVP, nestable-schema comment row; maps the `comment` table; `targetType`+`targetId`+`authorEmployeeId`+`body`, nullable `parentCommentId`/`path`, `depth` default 0 | `apps/wc-api/shared/src/main/java/com/st6/wc/comment/Comment.java:25` |
| `CommentRepository` | Bare `JpaRepository<Comment, UUID>` — **no finders** (the javadoc says "finder queries land in 1.6"; they never did) | `apps/wc-api/shared/src/main/java/com/st6/wc/comment/repo/CommentRepository.java:8` |
| `CommentTargetType` (enum) | The narrowed 2-value vocab `{PLAN, COMMITMENT}` (Appendix B.1; §11 flat MVP) | `apps/wc-api/shared/src/main/java/com/st6/wc/enums/CommentTargetType.java:4` |
| `comment` table (V1 migration) | `target_type varchar(32) CHECK in (PLAN,COMMITMENT)`, `target_id`, `author_employee_id→employee`, nullable `parent_comment_id→comment`, `path`, `depth default 0`, `body text` + audit quartet; index `(target_type, target_id, path)` | `apps/wc-api/shared/src/main/resources/db/migration/V1__core_schema.sql:174` |
| `DomainAuthorizationService.authorizeCommentAccess` | Loads the comment, resolves its target's owning plan, applies ownership rule — **never called by a production endpoint** | `apps/wc-api/api/src/main/java/com/st6/wc/auth/DomainAuthorizationService.java:125` |
| `DomainAuthorizationService.authorizeCommentTargetAccess` | The create-comment-path target IDOR check (`targetType`+`targetId` → owning plan) — **never called by a production endpoint** | `apps/wc-api/api/src/main/java/com/st6/wc/auth/DomainAuthorizationService.java:135` |
| `commentsApi` (RTK Query slice) | `getComments` (E20, paginated B.20 envelope) + `createComment` (E21); per-target cache tag; success-only invalidation | `apps/wc-web/src/features/comment/commentsApi.ts:44` |
| `CommentThread` (component) | The `COMMENT`-gated, collapsible affordance; mounts `CommentList`+`CommentForm` lazily on open | `apps/wc-web/src/features/comment/CommentThread.tsx:21` |
| `CommentList` (component) | Reads the paginated thread; renders loading/empty/error/success; body React-escaped | `apps/wc-web/src/features/comment/CommentList.tsx:22` |
| `CommentForm` (component) | Posts a flat comment (E21); clears on success, retains body on error | `apps/wc-web/src/features/comment/CommentForm.tsx:18` |
| `CommentDto` / `CreateCommentRequest` (TS types) | The frontend boundary contract mirroring Appendix B.9 | `apps/wc-web/src/shared/lib/dtos.ts:226`, `:239` |
| MSW `GET`/`POST /api/comments` handlers | The standalone demo's mock backend for comments (static read fixture + echo create) | `apps/wc-web/src/standalone/mocks/handlers.ts:126`, `:271` |
| `commentsForTarget` (fixture) | Static demo read data — only `PLAN`+`PLAN_IC_2` returns rows, everything else empty | `apps/wc-web/src/standalone/mocks/fixtures.ts:822` |

**Note on the missing rows:** there is deliberately no `CommentController`, no `CommentService`, and no `api/comment/dto/CommentDto` row above — because **no such files exist** (verified by `find`/`grep`, see Gotchas).

## Interfaces & contracts

### What the architecture specifies (§11, Appendix B.2 E20/E21, Appendix B.9)

Two endpoints, both gated `IC (own) / Manager (direct-report)`, manager targets require `LOCKED`+ (REQ-F-014), per `ARCHITECTURE.md:400-401`:

```
E20  GET  /api/comments?targetType=&targetId=&page=&size=   → PageEnvelope<CommentDto>   (B.20)
E21  POST /api/comments        body: { targetType, targetId, body }  → CommentDto
```

`CommentDto` (Appendix B.9 / `ARCHITECTURE.md:566`):

```
{ id, targetType, targetId, authorEmployeeId, authorDisplayName,
  parentCommentId(null in MVP), depth(0 in MVP), body, createdAt }
```

The authorizer is supposed to resolve `targetId` → owning plan and reject unseeable/nonexistent targets with `404` (target-id IDOR; `ARCHITECTURE.md:578`).

### What is actually exposed in code

- **Backend HTTP surface: NONE.** No controller maps `/api/comments`. The only backend code that exists is the entity, repository, enum, table, and two *unreferenced* authorizer methods.
- **`shared` persistence interface (real):** `CommentRepository extends JpaRepository<Comment, UUID>` — only the inherited `save`/`findById`/etc.; no `findByTargetTypeAndTargetId` finder exists (`CommentRepository.java:8`).
- **Authorizer interface (real but dead):** `void authorizeCommentAccess(DomainPrincipal, UUID commentId)` and `void authorizeCommentTargetAccess(DomainPrincipal, CommentTargetType, UUID targetId)` (`DomainAuthorizationService.java:125`, `:135`). Inputs → throws on denial (`404`/`403`); no production caller.
- **Frontend interface (real):** the `commentsApi` slice issues the exact E20/E21 calls the architecture specifies:
  - `useGetCommentsQuery({targetType, targetId, page?, size?, sort?})` → `GET /api/comments?...` (`commentsApi.ts:46-55`), default `page=0`, `size=25`, `sort=['createdAt,asc']`.
  - `useCreateCommentMutation({targetType, targetId, body})` → `POST /api/comments` (`commentsApi.ts:61-66`), success-only invalidation of the per-target tag (`error ? [] : [commentTag(...)]`, `:67-68`).
  - Errors parsed via `parseProblemDetail` (RFC-7807), surfacing only `safeMessage` (`:59`, `:69`).

So the **frontend contract and the architecture agree**; the **backend implementation of that contract is absent.**

## Data & state

- **Table `comment`** (`V1__core_schema.sql:174`): primary state. Columns mirror the `Comment` entity exactly — `target_type varchar(32) CHECK in ('PLAN','COMMITMENT')`, `target_id uuid`, `author_employee_id uuid references employee(id)`, nullable `parent_comment_id uuid references comment(id)` (self-FK for future nesting), `path varchar(2048)`, `depth integer not null default 0`, `body text not null`, plus the audit quartet (`created_by/at`, `updated_by/at`). Index `idx_comment_target on (target_type, target_id, path)` (`V1__core_schema.sql:188`) — built to support the not-yet-existent target-thread finder.
- **`Comment` entity** (`Comment.java:25`): extends `AbstractAuditingEntity` with an inline `@Id` (the "audited, not versioned" base shape per backend LESSONS §7) — **no `@Version`**, so comments are not optimistically locked. `targetType` is `@Enumerated(STRING)`. `parentCommentId`/`path` nullable; `depth` a primitive `int` (defaults 0).
- **`CommentTargetType` enum** (`CommentTargetType.java:4`): exactly `{PLAN, COMMITMENT}`. This is one of the three drift-trap negatives pinned by `EnumVocabularyTest` per the backend cross-doc invariants table (the enum must mirror the DB `CHECK` exactly).
- **Frontend cache state:** RTK Query keyed by `commentTag(targetType, targetId) = { type:'comments', id:`${targetType}:${targetId}` }` (`commentsApi.ts:33-35`) so a post invalidates only its own thread. The `'comments'` tag is registered in `app/tags.ts:14`.
- **Demo/mock state:** the standalone MSW layer serves comments **statically** — `commentsForTarget` (`fixtures.ts:822`) returns the `COMMENTS_IC_2` fixture only for `PLAN`/`PLAN_IC_2`, an empty envelope otherwise; the POST handler (`handlers.ts:271`) **echoes** the posted body back as a fabricated `comment-new-<targetId>` row and does **not** persist into the mutable `db.ts` (a `grep -i comment` over `db.ts` returns nothing — comments were never added to the mutable demo DB, unlike plans/commitments/disputes). So even in the demo, a created comment does not appear in a subsequent list read.

## Dependencies

- **Depends on:**
  - `shared` base classes (`AbstractAuditingEntity`) and the `employee` table (FK `author_employee_id`) — see [Domain Model & Persistence](01-domain-persistence.md).
  - `WeeklyPlan`/`WeeklyCommitment` ownership resolution inside `DomainAuthorizationService` (a comment's target resolves to an owning plan; commitment→plan) — see [Authorization, Identity & Audit](04-authorization-identity-audit.md).
  - The `AllowedAction` vocabulary (`COMMENT`) and `PageEnvelope<T>` (B.20) on the frontend — see [Frontend (wc-web)](09-frontend.md).
- **Used by:**
  - Frontend: `CommentThread` is mounted at the plan level by `WeeklyPlanView.tsx:123` and per-commitment by `CommitmentList.tsx:188` — both gated on the server's per-resource `allowedActions[]` containing `COMMENT`.
  - Backend: **nothing in production uses the comment authorizer or repository for comments.** The repository is constructor-injected into `DomainAuthorizationService` (`DomainAuthorizationService.java:79`, `:90`) solely to back the two unreferenced authorizer methods.

## How it works (flow)

The *intended* end-to-end flow (per §11) and the *actual* flow diverge. Both are shown.

**Intended (architecture):**
```
CommentThread (COMMENT-gated)
   └─ open ─> CommentList ──GET /api/comments──> [CommentController] ─> [CommentService]
                                                       │ authorizeCommentTargetAccess
                                                       └─> CommentRepository.findByTarget(...) ─> PageEnvelope<CommentDto>
   └─ CommentForm ──POST /api/comments──> [CommentController.create] ─> [CommentService.create]
                                                       │ authorizeCommentTargetAccess (404 on unseeable target)
                                                       └─> CommentRepository.save(...) ─> CommentDto
```
The two bracketed `[...]` boxes **do not exist in code.**

**Actual (frontend → mock only):**
```
CommentThread.tsx:28  can('COMMENT', allowedActions) gate (else renders null)
   └─ open ─> CommentList.tsx:24  useGetCommentsQuery(...)
                 └─ commentsApi.ts:54  GET /api/comments?targetType=&targetId=&page=&size=&sort=
                       ├─ [real backend]  → 404 (no route)               ← the drift
                       └─ [standalone MSW] handlers.ts:126 → commentsForTarget(...) static envelope
   └─ CommentForm.tsx:32  useCreateCommentMutation(...).unwrap()
                 └─ commentsApi.ts:63  POST /api/comments
                       ├─ [real backend]  → 404 (no route)               ← the drift
                       └─ [standalone MSW] handlers.ts:271 → echoes a fabricated CommentDto (not persisted)
```

Walkthrough of the real (built) frontend path:
1. `CommentThread` renders nothing unless `can('COMMENT', allowedActions)` is true (`CommentThread.tsx:28`) — the only gate, server-authoritative, never re-derived client-side (§6/§11). The `COMMENT` value exists in the backend `AllowedAction` enum (`AllowedAction.java:25`) but **no backend code path ever emits it** — `AllowedActionResolver` only references `COMMENT` in javadoc ("added by the slices that enforce them" / "`COMMENT` joins [later]", `AllowedActionResolver.java:28,84`), and no resolver predicate adds it to any response's `allowedActions[]`. So in the live remote the thread is permanently *dormant* — never permitted by a real backend.
2. On expand, `CommentList` subscribes via `useGetCommentsQuery` (`CommentList.tsx:24`) and renders the four §7 view-states; the `body` is rendered as escaped text (`{c.body}` at `CommentList.tsx:73`) — never `dangerouslySetInnerHTML` (REQ-S-005).
3. `CommentForm` posts on submit (`CommentForm.tsx:32`); empty-body is a UX-only short-circuit (`:27`), with the server stated as authoritative; on success the input clears and the thread refetches via tag invalidation (no optimistic insert, §7).

## Design decisions & rationale

- **Flat in MVP, nestable schema retained (§11, Appendix A).** The `comment` table and `Comment` entity carry `parent_comment_id`, `path`, and `depth` columns so threading can be enabled later without a migration, but the MVP keeps `depth=0`/`parent=null` and renders flat siblings. `CommentList` deliberately ignores `parentCommentId`/`depth` for layout (`CommentList.tsx:18-20` javadoc). Trade-off: a small amount of dead schema surface in exchange for forward-compatibility.
- **Target-id IDOR via target→plan resolution (§11/§6).** Rather than a per-comment ACL, the authorizer resolves a comment's `{targetType,targetId}` to its owning plan and reuses the same self/direct-report rule as the target (`DomainAuthorizationService.java:127-131`). Unseeable/nonexistent targets return `404` (never reveal existence). This is the correct design — but it is **inert**, because nothing calls it.
- **Comments are not the dispute-response channel (§11).** The IC's dispute reply uses `alignment_dispute.ic_response`, not a comment; E19 dispute resolution explicitly tells managers to use a comment (E20/E21) for *resolution context* (`ARCHITECTURE.md:564`). This keeps the dispute state machine clean.
- **Frontend leads the backend (frontend LESSONS §19, "dormant-until-emitted").** `CommentThread` gates purely on the server `COMMENT` allowed-action and is fully built + unit-tested with the action mocked present, so it activates with zero frontend change the moment the backend ships the emission. This is a deliberate project pattern — the frontend was allowed to land its half while the backend half waits. The catch in *this* feature is that the backend half **never landed at all**: no controller, no service, and the `COMMENT` allowed-action — though defined in the `AllowedAction` enum (`AllowedAction.java:25`) — is **never emitted** by `AllowedActionResolver` (only named in its javadoc as future work, `AllowedActionResolver.java:28,84`). The "dormant" control has no backend that will ever wake it.
- **Per-target cache tag, success-only invalidation (frontend LESSONS §10).** A post invalidates only its own thread (`commentsApi.ts:67-68`), and never on error — standard for the project's RTK Query mutations.

## Gotchas & sharp edges

### DRIFT (HIGH): no backend HTTP surface — the feature is non-functional end-to-end

The architecture (§11, Appendix B.2 E20/E21, B.9) specifies a real list/create endpoint pair. **No backend handler exists.** Verified directly:

```
$ find apps/wc-api/api/src/main -type f -iname '*comment*'        → (no files)
$ grep -rn -E 'class +Comment(Controller|Service)' apps/wc-api/api/src   → (no matches; exit 1)
$ grep -rl -i comment apps/wc-api/api/src/main
    application.yml
    auth/DomainAuthorizationService.java     ← the only real hit (the authorizer)
    plan/AllowedActionResolver.java          ← incidental ("commitment"/COMMENT action)
    commitment/dto/WeeklyCommitmentDto.java  ← incidental ("commitment")
    commitment/TextNormalizer.java           ← incidental
    manager/dto/PageEnvelope.java            ← incidental
    action/AllowedAction.java                ← the COMMENT action constant
```

Every `api/src/main` hit is either the authorizer or an incidental `commitment`/`COMMENT`-action match — **there is no controller or service.** Against a real backend, the frontend's `GET`/`POST /api/comments` calls would resolve to no route (404). The persistence model, the table, and the authorizer all exist; the wiring that would make them reachable does not.

### DRIFT (consequence): the comment authorizer is dead code

`authorizeCommentAccess` and `authorizeCommentTargetAccess` (`DomainAuthorizationService.java:125`, `:135`) are referenced **only** by tests (`AuthorizationIdorMatrixTest`, `DomainAuthorizationServiceTest`) — never by a production caller (`grep -rn 'authorizeComment' apps/wc-api/api/src/main` returns only the two definitions). The `CommentRepository` is injected into the authorizer (`:79`, `:90`) purely to support these unreachable methods. The seam is correct and tested; it is simply never plugged in.

### The frontend "works" only against mocks

The live remote build talks to a real backend; in that mode comments are broken (no endpoint). The thread renders today **only** in the standalone demo, where MSW intercepts `/api/comments` (`handlers.ts:126`, `:271`). Even there it is shallow: reads are a static fixture (`commentsForTarget`, only `PLAN_IC_2` has rows — `fixtures.ts:822-833`) and the create handler **echoes without persisting** (it does not write to `db.ts`, which has zero comment state — `grep -i comment db.ts` is empty). So a comment posted in the demo will not appear on the next list fetch. This is a demo affordance, not a working feature.

### Other sharp edges

- **No `@Version` on `Comment`** (`Comment.java`) — comments are append-only by design; no optimistic-lock conflict path exists, but also nothing prevents lost updates if editing were ever added.
- **No repository finder** — `CommentRepository` has no `findByTargetTypeAndTargetId` (or paged variant). A backend implementer would need to add the finder the `idx_comment_target` index already anticipates; the index exists but is currently unused.
- **`createdAt` rendered raw** — `CommentList.tsx:65,69` puts the raw ISO `createdAt` string straight into the `<time>` element; no localization/formatting. Cosmetic, but worth noting.
- **Stored-XSS surface is correctly defended** — `body` is React-escaped (`CommentList.tsx:71-73`), matching REQ-S-005 / forbidden-pattern #4; user text would be stored raw server-side per the project convention (React escapes on render).

## Connects to

- [01 — Domain Model & Persistence](01-domain-persistence.md): the `Comment` entity, `comment` table, and `CommentTargetType` enum live with the rest of the JPA/DDL layer in `shared`.
- [02 — API & Web Layer](02-api-web.md): where E20/E21 *should* live (thin `@RestController` → `@Service` → DTO record, RFC-7807 error mapping) — the missing half of this feature; follow the dispute/commitment controllers as the template.
- [04 — Authorization, Identity & Audit](04-authorization-identity-audit.md): the comment authorizer methods (`authorizeCommentAccess` / `authorizeCommentTargetAccess`) and the target→owning-plan IDOR resolution that the absent endpoints would call.
- [03 — Application & Lifecycle Services](03-application-lifecycle.md): the dispute lifecycle, which §11 keeps separate from comments (IC response uses `alignment_dispute.ic_response`, not a comment).
- [09 — Frontend (wc-web)](09-frontend.md): the `commentsApi` slice, the `CommentThread`/`List`/`Form` components, the `CommentDto` contract, the standalone MSW mock handlers, and the `COMMENT` allowed-action gate that mounts the thread.

## Open questions (UNVERIFIED)

- Whether a `CommentController`/`CommentService` was ever planned for a later phase (the `CommentRepository` javadoc says "finder queries land in 1.6", but no 1.6 comment finder exists). Not confirmed from `MVP_TASKS.md` in this pass — flagged as the build gap, not a resolved scheduling fact.

---
_Generated by `/layer-docs` (initial run) against commit `3b919a9` on 2026-06-04. Claims are anchored to code; `UNVERIFIED` marks anything not confirmed._
