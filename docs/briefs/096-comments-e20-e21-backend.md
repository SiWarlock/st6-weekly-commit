# /tdd brief — Comments backend (E20 list + E21 create)

## Feature
Build the comments REST surface (`GET /api/comments` list + `POST /api/comments` create) — controller + service + DTOs — wiring the **already-built-but-dead** `Comment` entity / `comment` table / `CommentRepository` / `authorizeCommentTargetAccess` authorizer / `CommentTargetType` enum / `COMMENT` affordance into a reachable production path that serves the exact contract the frontend (9.11b, built against MSW) already calls.

## Use case + traceability
- **Task ID:** comments-E20/E21 (drift-fix arc finding #1; orch will inline a Phase-11/§11 task checkbox at `/orchestrate-end`)
- **Architecture sections it implements:** `ARCHITECTURE.md` **§11** (comments & collaboration — flat one-level), **Appendix B.9** (`CommentDto` / `CreateCommentRequest`), **§5/Appendix B.2 E20+E21** (endpoints), **§6** (rule #3 IDOR authz), **REQ-F-014** (manager comments only on `LOCKED`+ direct-report targets), **§15/§16** (audit safe-metadata + RAW user text / React-escape).
- **Related context:** this is the FIRST production caller of `DomainAuthorizationService.authorizeCommentTargetAccess` (dead since it was authored — `AuthorizationIdorMatrixTest` is the only caller). The whole arc is reachability: built surface, no wiring. The frontend contract is **canonical** — match `apps/wc-web/src/features/comment/commentsApi.ts` + `apps/wc-web/src/shared/lib/dtos.ts` field-for-field (shapes pinned below). Mirror the established read-DTO + authz-chokepoint + input-validation patterns: **LESSONS §21** (thin controller→service→record-DTO + entity-never-crosses leak test), **§22/§24** (nested/paginated read mapper), **§25** (per-resource read via authorizer chokepoint — `verify(repo, never())` on a denied authorize), **§26** (server-side input validation: `TextNormalizer` + `@CodePointSize` + uniform `400 VALIDATION_ERROR`), **§39** (the B.20 `PageEnvelope<T>` custom record), **§17/§18** (central authz, IDOR-safe `404` + `REQUIRES_NEW` denial audit, escaped-JSON metadata).

## Acceptance criteria (what "done" means)
- [ ] `GET /api/comments?targetType=&targetId=&page=&size=` returns a **B.20 `PageEnvelope<CommentDto>`** for the target, **ordered `createdAt ASC, id ASC`** (server-fixed, deterministic).
- [ ] `POST /api/comments` with `CreateCommentRequest {targetType,targetId,body}` creates a **flat** comment (`parentCommentId=null`, `depth=0`), returns `CommentDto` (**201**).
- [ ] `CommentDto` mirrors **B.9 + the frontend `dtos.ts` shape EXACTLY**: `{id, targetType, targetId, authorEmployeeId, authorDisplayName, parentCommentId, depth, body, createdAt}` — `authorDisplayName` resolved from `Employee` (NOT on the entity; **batch-load, no N+1**), and **never an entity across the boundary** (`.doesNotExist()` audit-quartet leak test, §21).
- [ ] **`createdAt` is populated** (the entity's `@CreatedDate` is currently a no-op — `@EnableJpaAuditing` is NOT active in this codebase; E15 confirms "createdAt is unpopulated"). The frontend `CommentDto.createdAt` is **required + drives chronological ordering** → the service **sets `createdAt` explicitly via an injected `Clock`** at create time (mirrors the codebase's explicit-timestamp pattern, e.g. `lockedAt`). createdAt must be non-null on the response.
- [ ] **Rule #3 IDOR (§6):** `authorizeCommentTargetAccess(principal, targetType, targetId)` is the **FIRST** service statement on both paths (chokepoint); cross-owner/unseeable/nonexistent target → **codeless `404`** + (on genuine denial) a `REQUIRES_NEW` `AUTHORIZATION_DENIED` audit; genuinely-missing → `404` no-audit (§25).
- [ ] **REQ-F-014 manager gate:** a manager-of-owner may comment on a direct-report target **only when the owning plan is `LOCKED`+** — a manager on a direct-report **DRAFT** target is rejected (see Step-2.5 Q1 for code/posture). The owning IC may comment in any state on their own target.
- [ ] **Rule #2 untouched:** commenting mutates **no** plan/commitment baseline field (assert a commitment/plan baseline is byte-unchanged after a comment).
- [ ] `body` validated (`@NotBlank`, ≤ cap — Step-2.5 Q6), normalized once via `TextNormalizer`, stored **RAW** (React-escapes, §16), **never logged/audited** (§15). Blank/oversize → `400 VALIDATION_ERROR` + `fieldErrors[]`.
- [ ] Endpoint is **request-reachable** through the active `SecurityConfig` chain (RequestMappingHandlerMapping carries both mappings) — Step 7.5.
- [ ] All unit + integration tests in `apps/wc-api/api/src/test/.../comment/` pass; `./gradlew check` green from `apps/wc-api/`.
- [ ] Cross-doc: the 2 new DTOs flagged at Step 9 (orch writes the `apps/wc-api/CLAUDE.md` cross-doc rows; **B.9 + AllowedAction.COMMENT already exist** → no `ARCHITECTURE.md` Appendix edit).

## Files expected to touch
**New:**
- `api/src/main/java/com/st6/wc/comment/CommentController.java` — thin `@RestController`: `GET /api/comments` (+ `Pageable`/query params) + `POST /api/comments` → `CommentService`. No logic (forbidden-pattern #4).
- `api/src/main/java/com/st6/wc/comment/CommentService.java` — `@Service`: list (authorize → LOCKED+ gate → paginated finder → batch-resolve `authorDisplayName` → map) + create (authorize → LOCKED+ gate → set `createdAt` via `Clock` → persist → map → audit). Injects `Clock` (org-time seam).
- `api/src/main/java/com/st6/wc/comment/dto/CommentDto.java` — `record` mirroring B.9.
- `api/src/main/java/com/st6/wc/comment/dto/CreateCommentRequest.java` — validated `record` (compact-ctor normalize, §26).
- `api/src/main/java/com/st6/wc/comment/CommentMapper.java` — entity→DTO (`authorDisplayName` injected by the service; keep the mapper repo-free, §35).
- Tests under `api/src/test/java/com/st6/wc/comment/`.

**Modified:**
- `shared/src/main/java/com/st6/wc/comment/repo/CommentRepository.java` — add `Page<Comment> findByTargetTypeAndTargetId(CommentTargetType, UUID, Pageable)` (server constructs the fixed `createdAt ASC, id ASC` Sort). The `idx_comment_target (target_type,target_id,path)` already backs it.
- **(Step-2.5 Q4)** possibly relocate `PageEnvelope<T>` from `api/manager/` to a neutral package (`com.st6.wc.web`) — its 3rd consumer; touches E13/E15 imports. Default = relocate; impl's call.

> **No `DomainAuthorizationService` change** — `authorizeCommentTargetAccess` already exists; this slice just gives it its first prod caller. The REQ-F-014 LOCKED+ gate lives in `CommentService` (a state precondition, not an authz-identity check) unless Step-2.5 Q1 decides otherwise.
> If implementation needs files beyond this list, **flag at Step 2.5** before going GREEN.

## RED test outline (Step 2)
Tests in `api/src/test/java/com/st6/wc/comment/` (controller/integration + service unit):

1. **`list_returnsPagedCommentsForTarget_orderedByCreatedAtThenId`** — seed 3 comments on a target; GET returns a B.20 envelope, content ordered `createdAt ASC, id ASC`. _Why:_ §11/F.5 chronological + §39 envelope.
2. **`list_resolvesAuthorDisplayName_batchLoaded_noNPlusOne`** — `authorDisplayName` populated from `Employee`; assert a single batch lookup (`findByIdIn`), not per-row. _Why:_ B.9 + §35/§37 load-once.
3. **`create_persistsFlatComment_returns201`** — POST → 201, `depth=0`, `parentCommentId=null`, `createdAt` **non-null**, body echoed. _Why:_ §11 flat + the createdAt criterion.
4. **`create_setsCreatedAtViaClock`** — with a fixed injected `Clock`, `createdAt` equals the clock instant (deterministic). _Why:_ @CreatedDate is a no-op here; pins explicit population.
5. **`create_validatesBody_blankAndOversize_400`** — blank → `400 VALIDATION_ERROR`+`fieldErrors`; > cap code points → `400`; stored RAW (control-char handling per §26). _Why:_ §26/§16.
6. **`commentDto_noEntityLeak`** — audit-quartet / `path` / entity internals absent from the JSON (`.doesNotExist()`). _Why:_ forbidden-pattern #3, §21.
7. **`list_unseeableTarget_404_codeless_noBodyLeak`** — cross-owner/nonexistent `targetId` → codeless `404`, body equal to the canonical not-found (strip server fields, §25). _Why:_ rule #3.
8. **`create_unauthorizedTarget_404_auditedDenial`** — create on an unauthorized target → `404` + exactly one `AUTHORIZATION_DENIED` audit (safe ids, REQUIRES_NEW). _Why:_ rule #3 + §17.
9. **`create_authorizerIsChokepoint_repoNeverHitOnDenial`** — `verify(commentRepository, never()).save(...)` when authorize denies. _Why:_ §25 chokepoint.
10. **`create_managerOnDirectReportDraft_rejected_REQ_F_014`** — seed an active manager→report relationship; manager comments on the report's **DRAFT** plan/commitment → rejected per Q1 posture (default `409`, no audit). _Why:_ §11/REQ-F-014.
11. **`create_managerOnDirectReportLocked_succeeds`** — same relationship, target plan **LOCKED** → 201. _Why:_ the gate admits LOCKED+.
12. **`create_icOwnTarget_succeeds` / `list_icOwnTarget_succeeds`** — owning IC, any state → ok. _Why:_ self-access.
13. **`comment_doesNotTouchBaseline`** — a locked commitment's baseline fields are byte-identical after a comment. _Why:_ rule #2 non-interference.
14. **(if Q7=yes) `create_writesSafeAudit_noBody`** — create audit carries ids only, **no body text** (§15 SENTINEL).
15. **`commentEndpoints_requestReachable`** — both mappings present in `RequestMappingHandlerMapping` (reachability, §22 pattern).

## Cross-doc invariant impact (implementer flags at Step 9; orchestrator writes the docs)
- **Model field changes:** 2 NEW boundary DTOs — `CommentDto` (B.9 response) + `CreateCommentRequest` (B.9/E21 request). **B.9 already exists** in `ARCHITECTURE.md`; **`AllowedAction.COMMENT` already in the vocab** (B.1/F.4) → **no `ARCHITECTURE.md` Appendix edit**.
- **Orchestrator doc rows to write hot (Step 9 routing):** 2 new rows in the `apps/wc-api/CLAUDE.md` cross-doc invariants table (`CommentDto`, `CreateCommentRequest` → B.9). The new repo finder + the `Comment` entity already mirror Appendix A. If Q4 relocates `PageEnvelope`, note it (no contract change).

> **Implementer never edits `apps/wc-api/CLAUDE.md`, `ARCHITECTURE.md`, `MVP_TASKS.md`, or `LESSONS.md`** — flag categorized at Step 9; orchestrator writes hot.

## Things to flag at Step 2.5
1. **REQ-F-014 manager-`LOCKED`+ gate — mechanism + rejection code.** The existing `authorizeCommentTargetAccess` only checks ownership (admits a manager-of-owner regardless of plan state) — it does NOT enforce the LOCKED+ precondition. **Check what 5.6 built** (manager draft-visibility, REQ-F-009/010 — the tracker notes a "no-audit-on-DRAFT-`409`" posture) and reuse it. My default vote: **enforce in `CommentService` after the authz chokepoint; manager-on-direct-report-DRAFT → `409` (no audit), aligning with 5.6's posture** (the manager relationship is legitimate, so `409` not `404`). Confirm whether 5.6 left a reusable helper. _(If you find the manager genuinely can't resolve the DRAFT target at all through the authorizer, say so — the gate may be partly satisfied already.)_
2. **List ordering + `createdAt` population.** Default: service **sets `createdAt` via injected `Clock`** on create (do NOT wire global `@EnableJpaAuditing` here — that's the parked standalone slice); finder/sort **server-fixed `createdAt ASC, id ASC`** (ignore the client `sort` param for determinism; the frontend sends `createdAt,asc` which matches). Agree?
3. **`authorDisplayName` resolution.** Default: batch-load authors via `EmployeeRepository.findByIdIn(authorIds)` in the service, pass names into the mapper (mapper stays repo-free, §35). Agree?
4. **`PageEnvelope<T>` placement.** It lives in `api/manager/` (6.5a, reused by E15) — comments would be its 3rd consumer. Default: **relocate to a neutral `com.st6.wc.web`** (avoids a `comment→manager` package dependency; rule-of-three) and update E13/E15 imports. Acceptable churn, or prefer import-in-place to keep the slice atomic? Your call.
5. **Emit the `COMMENT` affordance on E3/E4 plan/commitment reads?** Default: **NO** — the frontend comments panel is its own surface (`commentsApi`); check whether it reads `allowedActions.includes('COMMENT')` — if not, emitting it adds a §31 tested-but-unconsumed branch. Skip unless the frontend consumes it.
6. **`body` cap + normalization.** Default: **≤4000 code points**, `@NotBlank`, normalized via `TextNormalizer.normalizeMultiLine` (newlines allowed — comments are multiline). Confirm the cap (B.9 says "validated REQ-S-005" without a number; 4000 matches `managerNote`/`icResponse`).
7. **Audit on successful create?** Default: **yes** — one safe-metadata `audit_event` (action e.g. `COMMENT_CREATED`, ids only: commentId/targetType/targetId/authorId, **NO body**, §15). Confirm the §15 audit-action taxonomy has/needs a comment action; if it's not in §15, flag it (orch decides taxonomy vs skip-audit-on-create).

## Dependencies + sequencing
- **Depends on:** nothing new — `Comment` entity + `comment` table + `CommentRepository` + `CommentTargetType` + `authorizeCommentTargetAccess` + `commentTargetOwner` + `AllowedAction.COMMENT` + `PageEnvelope` + `TextNormalizer` + the RFC-7807 advice all already shipped.
- **Blocks:** the frontend comments UI (9.11b, built against MSW) going real on the deployed backend; first slice of the deploy-blocking spine (096→097→098).

## Estimated commit count
**1 — do NOT bundle.** This is one cohesive feature (read+create share the controller/service/DTO/authorizer) AND a **rule-#3 IDOR safety surface** → its own commit, ad-hoc **security-reviewer** pass after GREEN (lead-authorized: security-reviewer ad-hoc on the IDOR-touching comment slice). E20+E21 bundle *within* this one slice (one logical unit); nothing else rides with it.

## Lessons-logged candidates anticipated
- **Convention candidate** — "wiring a dead authorizer to its first prod caller" + the explicit-`createdAt`-via-`Clock` workaround for the un-wired `@CreatedDate` (the ordering contract a read DTO needs when JPA auditing is off).
- **Architecture-doc note candidate** — the REQ-F-014 manager-`LOCKED`+ comment gate posture (409-no-audit, 5.6 alignment) if it lands as a reusable pattern.
- **Future TODO — operational** — global `@EnableJpaAuditing` is still the parked standalone slice; this slice works around it locally (don't let createdAt-via-Clock mask the broader gap).

## How to invoke
1. **Read this brief end-to-end** (you're an already-oriented session — no `/session-start`).
2. Pre-flight: confirm cwd `apps/wc-api/`; skim `commentsApi.ts` + `dtos.ts` (CommentDto/CreateCommentRequest/PageEnvelope) + the `Comment` entity + `authorizeCommentTargetAccess` + how 5.6 gated manager-draft-visibility.
3. **Run `/tdd comments-backend-e20-e21`.**
4. Step 0 (Restate) → confirm against the Feature line. Step 1 → confirm the file list.
5. **Step 2.5** — send me the test designs + your answers to the 7 questions (take defaults or push back). Wait for my `APPROVED.`/`TWEAK:`/`ADD:` before GREEN.
6. Step 9 — categorized summary + ship/no-ship + draft commit message; flag the 2 cross-doc DTO rows.
