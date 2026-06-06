# Authorization, Identity & Audit

## Executive summary

This layer is the security core of the backend: it answers three questions on every request — *who is the caller*, *what may they touch*, and *did we leave a tamper-evident trail*. Identity resolution turns a validated Auth0 JWT (or, in demo mode, an `X-Demo-Employee-Id` header) into a typed in-memory `UserPrincipal` carrying the caller's authoritative role and manager status. A single central authorizer — `DomainAuthorizationService` — then gates every resource read and mutation, applying IC self-access and active-direct-report scoping; when a caller is not entitled, the resource's very *existence* is hidden behind a generic `404` (IDOR-safe), and a safe-metadata `audit_event` is written. The audit writer (`AuditService`) durably records sensitive actions with strictly no tokens, secrets, notes, or PII in its metadata. The layer is deliberately split: *identity* resolution (`identity/`) is separate from *authorization* (`auth/`), and both are separate from the *request-path wiring* (`config/`) that places the principal into Spring's `SecurityContext`. It is the home of three of the project's seven named safety rules — #3 (IDOR-safe central authz), #5 (env-gated demo identity, no production backdoor), and #7 (no secret/PII leak into logs or audit).

## Responsibilities

- **Identity resolution** — map a validated JWT or a demo employee id to a `UserPrincipal` with the *authoritative* `Employee.role` and a relationship-driven `isManager` (`identity/PrincipalResolver.java`). It does NOT authorize — it produces the principal that the authorizer consumes.
- **Central domain authorization (rule #3)** — every per-resource access/mutation check; cross-owner/cross-team/missing → IDOR-safe `404`, capability/role denials → `403` + a named code; one safe audit per genuine denial (`auth/DomainAuthorizationService.java`). It does NOT itself render the HTTP response — exception → status mapping is delegated to the API layer's `ProblemDetailsExceptionHandler` (see [02-api-web.md](02-api-web.md)).
- **OAuth2 resource-server + demo wiring** — the per-mode `SecurityFilterChain`, the Auth0 JWT decoder (RS256, issuer + audience), claim mapping, and CORS (`config/`). It does NOT define endpoint-level role rules beyond coarse authentication; resource ownership is delegated to `DomainAuthorizationService`.
- **Demo-identity backdoor control (rule #5)** — accept `X-Demo-Employee-Id` ONLY when `DEMO_AUTH_ENABLED=true`; reject with `403` + audit otherwise (`config/DemoAuthFilter.java`).
- **Central audit writer (rule #7)** — persist `audit_event` rows with safe-only metadata (`audit/AuditService.java`, entity `shared/audit/AuditEvent.java`). It owns the *semantics* of what is safe to write; it does NOT own the `audit_event` table columns (schema lives in [01-domain-persistence.md](01-domain-persistence.md)).
- It does NOT own: lifecycle/state transitions ([03-application-lifecycle.md](03-application-lifecycle.md)), the manager read projections it scopes ([05-manager-projections.md](05-manager-projections.md)), or the RFC-7807 *response body* rendering ([02-api-web.md](02-api-web.md)).

## Key components

| Component | What it does | Where |
|-----------|--------------|-------|
| `DomainAuthorizationService` | The single central authorizer: ownership resolution + IC-self / manager-direct-report scoping; IDOR-safe `404` vs capability `403`; one audit per genuine denial | `apps/wc-api/api/src/main/java/com/st6/wc/auth/DomainAuthorizationService.java:47` |
| `AuthorizationDeniedAuditer` | Writes the one denial `audit_event` in `REQUIRES_NEW` (survives a rolled-back mutation); builds metadata via an escaped JSON node | `apps/wc-api/api/src/main/java/com/st6/wc/auth/AuthorizationDeniedAuditer.java:27` |
| `AuthorizationDeniedException` | Capability denial → `403` + a stable `code` (e.g. `IC_CANNOT_RESOLVE_DISPUTE`) | `apps/wc-api/api/src/main/java/com/st6/wc/auth/AuthorizationDeniedException.java:11` |
| `ResourceNotFoundOrUnauthorizedException` | IDOR-safe denial → `404`, carries no resource detail | `apps/wc-api/api/src/main/java/com/st6/wc/auth/ResourceNotFoundOrUnauthorizedException.java:11` |
| `PrincipalResolver` | Identity → `UserPrincipal`; authoritative `Employee.role`, relationship-driven `isManager`; unknown/inactive → `Optional.empty()` | `apps/wc-api/api/src/main/java/com/st6/wc/identity/PrincipalResolver.java:29` |
| `DomainPrincipal` | Sealed marker permitting exactly `UserPrincipal` + `SystemPrincipal` (exhaustive switch in the authorizer) | `apps/wc-api/api/src/main/java/com/st6/wc/identity/DomainPrincipal.java:10` |
| `UserPrincipal` | Resolved IC/manager principal (`employeeId`, `role`, `isManager`); `getName()` = employeeId | `apps/wc-api/api/src/main/java/com/st6/wc/identity/UserPrincipal.java:22` |
| `SystemPrincipal` | The no-HTTP SYSTEM actor singleton, exempt from self/direct-report checks | `apps/wc-api/api/src/main/java/com/st6/wc/identity/SystemPrincipal.java:11` |
| `SecurityConfig` | One `@ConditionalOnProperty(demo-auth.enabled)` `SecurityFilterChain` per mode; fails fast on a non-boolean gate | `apps/wc-api/api/src/main/java/com/st6/wc/config/SecurityConfig.java:43` |
| `JwtConfig` | Builds the `NimbusJwtDecoder` (RS256, eager OIDC discovery) + the combined issuer/exp/nbf + audience validator; fail-fast on blank config | `apps/wc-api/api/src/main/java/com/st6/wc/config/JwtConfig.java:33` |
| `AudienceValidator` | Explicit `aud`-contains check (Auth0 does not validate audience by default) | `apps/wc-api/api/src/main/java/com/st6/wc/config/AudienceValidator.java:15` |
| `Auth0ClaimMapper` | Validated JWT → `Auth0Identity` via config-bound claim names (F.1); validate-when-present role, no DB access | `apps/wc-api/api/src/main/java/com/st6/wc/config/Auth0ClaimMapper.java:24` |
| `Auth0Identity` | Pure extracted identity record (`externalSubject`, nullable `role` hint, nullable `email`) | `apps/wc-api/api/src/main/java/com/st6/wc/config/Auth0Identity.java:12` |
| `Auth0Properties` | `auth0.audience` + configurable `claims` names (Appendix D.2 / F.1) | `apps/wc-api/api/src/main/java/com/st6/wc/config/Auth0Properties.java:14` |
| `PrincipalJwtAuthenticationConverter` | Real-mode bridge: validated JWT → resolved `UserPrincipal` → `PreAuthenticatedAuthenticationToken` | `apps/wc-api/api/src/main/java/com/st6/wc/config/PrincipalJwtAuthenticationConverter.java:28` |
| `DemoAuthFilter` | Env-gated demo identity filter; the production-backdoor rejector when disabled (rule #5) | `apps/wc-api/api/src/main/java/com/st6/wc/config/DemoAuthFilter.java:40` |
| `CorsConfig` | Exact-origin allow-list, `allowCredentials=false`, mode-independent (demo cannot widen it) | `apps/wc-api/api/src/main/java/com/st6/wc/config/CorsConfig.java:21` |
| `OrgTimeBindingConfig` | Binds `ORG_TIMEZONE` into the shared `OrgTimeConfig` with the D.6 fail-safe (peripheral to this layer) | `apps/wc-api/api/src/main/java/com/st6/wc/config/OrgTimeBindingConfig.java:19` |
| `AuditService` | Central audit writer; safe-metadata-only; `@Transactional`; `created_at` from injectable `Clock` | `apps/wc-api/api/src/main/java/com/st6/wc/audit/AuditService.java:21` |
| `AuditEvent` | Append-only `audit_event` entity; `actorEmployeeId` nullable (SYSTEM); `metadata_json` jsonb safe-only | `apps/wc-api/shared/src/main/java/com/st6/wc/audit/AuditEvent.java:25` |
| `EventKind` / `FlagType` / `RoleType` | Shared enums consumed here: calendar event kind, dispute flag, employee role | `apps/wc-api/shared/src/main/java/com/st6/wc/enums/{EventKind,FlagType,RoleType}.java` |

## Interfaces & contracts

**Identity resolution (`PrincipalResolver`)** — two overloads, both `Optional` (empty = unresolved):

```java
Optional<UserPrincipal> resolve(Auth0Identity identity)   // JWT mode: by externalSubject
Optional<UserPrincipal> resolve(UUID demoEmployeeId)       // demo mode: by primary key
```

Both filter on `Employee::isActive` — an inactive employee resolves to `empty()` (→ 401 IDOR-safe). `isManager` is computed via `relationships.existsByManagerEmployeeIdAndActiveTrue(...)`, never the JWT role claim (`PrincipalResolver.java:55`).

**Authorization (`DomainAuthorizationService`)** — all methods are `void` and throw on denial; the double-read (resolve owner, then re-check) is the accepted trade-off (LESSON §25). Two families:

- *Access* (read; admits a manager-direct-report): `authorizePlanAccess`, `authorizeCommitmentAccess`, `authorizeDisputeAccess`, `authorizeReviewAccess`, `authorizeCommentAccess`, `authorizeCommentTargetAccess`, `authorizeSyncRecordAccess`, `authorizeHeatmapCellAccess` (`DomainAuthorizationService.java:109`–`153`).
- *Mutation/capability* (write; narrower): `authorizePlanMutation`, `authorizeCommitmentMutation` (IC-owner-only → `403 *_OWNER_REQUIRED`); `authorizeDisputeResolution`, `authorizeDisputeCreation`, `authorizeDisputeResponse`, `authorizeManagerAlignmentNote`, `authorizeReviewMutation` (manager-of-owner or IC-owner inverses) (`DomainAuthorizationService.java:158`–`304`). `authorizeTeamHeatmapAccess` is a coarse manager-only capability gate.

Contract for callers (forbidden-pattern #7 / LESSON §32): authorship + mutations MUST use `authorize…Mutation`, never `authorize…Access` (the read-authorizer admits a manager-direct-report, correct for reads, a §6 hole for writes).

**Claim mapping (`Auth0ClaimMapper.map`)** — `Jwt → Auth0Identity`: `externalSubject` = configured employee-id claim, falling back to `sub`; `role` validate-when-present (`{IC,MANAGER}` else `OAuth2AuthenticationException` → 401) / tolerate-absent (null); `email` configured claim or null (`Auth0ClaimMapper.java:33`).

**Audit (`AuditService.record`)** — the one write seam:

```java
void record(String action, String entityType, UUID entityId,
            UUID actorEmployeeId, String summary, String safeMetadataJson)
```

Callers (verified) pass fixed-constant `action`/`entityType` and pre-escaped JSON metadata; `actorEmployeeId` is nullable (SYSTEM). Consumers include this layer (`AuthorizationDeniedAuditer.recordDenial`, `DemoAuthFilter.doFilterInternal`) and the domain-service / job layers (lock, dispute, review, generation, projection rebuild — those audit calls belong to [03-application-lifecycle.md](03-application-lifecycle.md), [05-manager-projections.md](05-manager-projections.md), [07-scheduled-jobs.md](07-scheduled-jobs.md)).

## Data & state

- **`UserPrincipal`** (in-memory, per-request) — `record UserPrincipal(UUID employeeId, RoleType role, boolean isManager)` implementing `AuthenticatedPrincipal` + `DomainPrincipal` (`UserPrincipal.java:22`). `getName()` returns the employee id string — read by the §15 audit actor + logging.
- **`SystemPrincipal.INSTANCE`** — a singleton; the only non-user principal; exempt from self/direct-report checks (`SystemPrincipal.java:14`).
- **`audit_event`** (table, written here, columns owned by [01-domain-persistence.md](01-domain-persistence.md)) — `AuditEvent` maps it: `id`, nullable `actorEmployeeId`, non-null `action`/`entityType`/`summary`/`createdAt`, nullable `entityId`, and `metadata_json` jsonb (safe-only). NO `@Version`, no audit quartet — append-only (`AuditEvent.java:25`).
- **Named-code vocabulary** (constants local to `DomainAuthorizationService`, NOT in `ErrorCodes`): `IC_CANNOT_RESOLVE_DISPUTE`, `IC_CANNOT_OPEN_DISPUTE`, `MANAGER_CANNOT_RESPOND_DISPUTE`, `IC_CANNOT_WRITE_MANAGER_NOTE`, `MANAGER_ROLE_REQUIRED`, `COMMITMENT_OWNER_REQUIRED`, `PLAN_OWNER_REQUIRED` (`DomainAuthorizationService.java:68`–`74`).
- **Denial reasons** (audited metadata, fixed constants): `cross_owner_or_unauthorized`, `ic_cannot_resolve_dispute`, `manager_role_required`, `not_commitment_owner`, … (`DomainAuthorizationService.java:58`–`66`).
- **Audit actions emitted by this layer:** `AUTHORIZATION_DENIED` (`AuthorizationDeniedAuditer.java:29`) and `DEMO_AUTH_REJECTED` (`DemoAuthFilter.java:43`).
- **Enums consumed:** `RoleType {IC, MANAGER}` (`RoleType.java:6`), `FlagType {NEEDS_REVISION, MISALIGNED}` (`FlagType.java:4`), `EventKind {IC_PLANNING, IC_RECONCILIATION, MANAGER_REVIEW_BLOCK}` (`EventKind.java:4`).

## Dependencies

- **Depends on:** the `:shared` domain repositories — `EmployeeRepository`, `ManagerRelationshipRepository` (identity resolution + manager scope), and the per-resource repositories (`WeeklyPlanRepository`, `WeeklyCommitmentRepository`, `AlignmentDisputeRepository`, `CommentRepository`, `ManagerReviewRepository`, `ManagerHeatmapCellRepository`, `OutlookCalendarSyncRecordRepository`) to resolve a resource to its owning employee via flat-FK `findById` chains (`DomainAuthorizationService.java:330`–`354`). It depends on `AuditEventRepository` (via `AuditService`) and Spring Security OAuth2 resource-server. See [01-domain-persistence.md](01-domain-persistence.md) for the entities + repositories.
- **Used by:** every controller and domain service — controllers stay coarse authn/role gates and delegate ownership to this layer; services call `authorize…` as the *first* statement before any repository read/mutation (LESSON §25 chokepoint). The Spring filter chain (this layer's `config/`) places the resolved `UserPrincipal` into the `SecurityContext` for `@AuthenticationPrincipal` injection. The API layer's `ProblemDetailsExceptionHandler` consumes the two exception types to render RFC-7807 ([02-api-web.md](02-api-web.md)).

## How it works (flow)

Real-mode request (Auth0 JWT):

```
HTTP Bearer JWT
   │  (DemoAuthFilter runs DISABLED first → rejects any demo header: 403 + audit)
   ▼
NimbusJwtDecoder (RS256, issuer+exp+nbf+AudienceValidator)   JwtConfig.java:43
   ▼
PrincipalJwtAuthenticationConverter.convert                  PrincipalJwtAuthenticationConverter.java:41
   ├─ Auth0ClaimMapper.map(jwt) → Auth0Identity              Auth0ClaimMapper.java:33
   ├─ PrincipalResolver.resolve(identity) → UserPrincipal    PrincipalResolver.java:41
   │     (empty → OAuth2AuthenticationException → 401 IDOR-safe)
   ▼
PreAuthenticatedAuthenticationToken(UserPrincipal, ROLE_<role>)  → SecurityContext
   ▼
controller (coarse authenticated) → service:
   DomainAuthorizationService.authorize…(principal, id)      DomainAuthorizationService.java:109
```

Inside `authorizeOwnership` (`DomainAuthorizationService.java:308`): resolve the owning employee id via the flat-FK chain (a genuinely *missing* resource throws a **non-audited** `404` at `notFound()`, `:377`). Then, for a `UserPrincipal`, authorized iff `up.employeeId().equals(owner)` OR (`up.isManager()` AND the owner is an active direct report via `isActiveDirectReport`, `:321`). If not authorized → `deny404` which calls `auditer.recordDenial(...)` (one audit) and returns the IDOR-safe `404` (`:315`, `:364`). A `SystemPrincipal` short-circuits as exempt (`:318`).

Capability path (e.g. `authorizeDisputeResolution`, `:201`): access chokepoint first (so an unseeable dispute is `404`), then the capability check — the IC owner attempting to resolve their own dispute → `deny403(..., IC_CANNOT_RESOLVE_DISPUTE)` (audited). The `404`-vs-`403` choice follows the §33 *namespace-legitimacy* tree: a `403` when the caller legitimately uses that API namespace (existence already known), a `404` when they have no legitimate endpoint at all (e.g. an IC against `/api/manager/*` → `authorizeReviewMutation` uses an audited `deny404`, `:300`).

Demo-mode request (`DemoAuthFilter`, `:56`): demo-enabled + a parseable existing/active id → authenticate as that `UserPrincipal`; blank/malformed/unknown/inactive → generic `401` (no audit-spam). Demo-disabled + any `X-Demo-Employee-Id` present → `403` + exactly one `DEMO_AUTH_REJECTED` audit, **without** ever resolving the untrusted value against the DB (`verifyNoInteractions(resolver)` in `DemoAuthFilterTest.java:91`).

Denial audit durability (`AuthorizationDeniedAuditer.recordDenial`, `:38`): runs in `Propagation.REQUIRES_NEW` so the audit row commits independently of (and survives) the surrounding mutation's rollback — proven in `AuthorizationDeniedAuditerTest.denialAuditSurvivesRolledBackMutation`.

## Design decisions & rationale

- **Identity separate from authorization (§6).** `PrincipalResolver` only *resolves*; `DomainAuthorizationService` only *authorizes*. The resolved principal is the single input to authz (`PrincipalResolver.java:14`). This keeps the SYSTEM exemption a sealed-type `instanceof` switch a request-built principal can't satisfy (LESSON §17).
- **Authoritative role from the DB, relationship-driven `isManager` (LESSON §15).** The nullable JWT role claim is a *hint*, discarded; the authoritative role is `Employee.role`, and `isManager` is `true` only with an active managed report — a MANAGER-role employee with no active report is `isManager=false` (`UserPrincipal.java:14`, `PrincipalResolver.java:55`).
- **IDOR-safe `404` over `403` for unauthorized resources (rule #3 / §6).** Cross-owner/cross-team/missing all render the *identical* codeless `404` so existence is never revealed; only capability/role denials (where existence is legitimately known) render `403` + a named code (`ResourceNotFoundOrUnauthorizedException.java:3`, ARCHITECTURE.md §6 / B.0 "Not-found vs denied").
- **One safe audit per *genuine* denial, none on id-probing (anti-spam).** A genuinely missing resource → `404` with no audit (`notFound()`, `:377`); a real denial (exists-but-unauthorized, or a capability denial) → exactly one audit. Verified by `AuthorizationIdorMatrixTest` (7 denials → 8 audits, the 8th being the IC-self heatmap test… actually 7 denials each audited; authorized accesses add none).
- **Denial audit in `REQUIRES_NEW` (LESSON §17/§18).** Default `REQUIRED` would roll the breadcrumb back with the denied mutation; a separate `@Service` opens a real proxy transaction so the record survives (`AuthorizationDeniedAuditer.java:12`).
- **Safe metadata is structural, not caller-trust (rule #7 / LESSON §18).** `recordDenial` builds metadata via a Jackson `objectNode().put(...)` (properly escaped) rather than string concat, and `AuditService` stores only fixed action/summary constants + already-safe JSON — pinned by the `SENTINEL`-no-leak sweep in `AuthorizationIdorMatrixTest.java:184` and `DemoAuthFilterTest.java:110`.
- **One filter chain per mode, fail-fast on a malformed gate (LESSON §19).** `demo-auth.enabled` is matched as exactly `"true"`/`"false"`; an ambiguous value (`yes`/`1`/typo) would match *neither* chain and silently drop the controls, so `SecurityConfig` refuses to start (`SecurityConfig.java:65`). The decoder fails fast on blank issuer/audience too (`JwtConfig.java:61`).
- **Explicit `AudienceValidator` (§6/§16).** Auth0 does not validate `aud` by default; `contains` (not `equals`) because Auth0 tokens routinely carry multiple audiences (`AudienceValidator.java:25`). The decoder rejects `alg=none`, symmetric HS256, wrong issuer, and wrong/missing audience (verified in `JwtDecoderConfigTest`).
- **CORS mode-independent (§16 RISK-008).** The `CorsConfigurationSource` reads *only* `app.cors.allowed-origins`, never `demo-auth.enabled`, so demo mode cannot widen the allow-list; `allowCredentials=false` (bearer transport, no cookies) (`CorsConfig.java:11`).

## Gotchas & sharp edges

- **`authorize…Access` ≠ `authorize…Mutation` (real shipped bug, forbidden-pattern #7 / LESSON §32).** The access-authorizer admits a manager-direct-report. Using it on a *write* lets a manager author on a report's plan. Every write endpoint must use the mutation form and carry a manager-of-owner-`403` test (a seeded active relationship → `403`-not-`404`). The narrow `404`-vs-`403` asymmetry between `authorizeReviewMutation` (`404`) and `authorizeDisputeResolution`/`authorizeDisputeCreation` (`403`) is **intentional** (§33 namespace-legitimacy) — the in-code comment explicitly says "Do not 'fix' this asymmetry" (`DomainAuthorizationService.java:225`).
- **The disabled `DemoAuthFilter` writes a raw `403` status, not an RFC-7807 body.** In real mode the rejection path sets `SC_FORBIDDEN` and returns directly (`DemoAuthFilter.java:76`) — it does not route through the access-denied handler. The body is empty; only the status + the one audit row carry the signal. (The 401 IDOR path is likewise a bare status.)
- **The authorizer does a double read.** It resolves the owner via repository chains and the service then re-reads the resource. This is the accepted trade-off for a `void` central chokepoint (LESSON §25); do not "optimize" it into the service read.
- **`OrgTimeBindingConfig` is in this layer's `config/` package but is not security.** It binds the org timezone fail-safe (D.6) and is unrelated to authz/identity/audit — included here only because it co-locates in `config/`. Its real consumers are the lifecycle SLA + generation job ([03](03-application-lifecycle.md)/[07](07-scheduled-jobs.md)).
- **The audit-action taxonomy is free strings, not an enum.** `AUTHORIZATION_DENIED`, `DEMO_AUTH_REJECTED`, and the domain actions are plain constants (no `VARCHAR`+`CHECK`), deliberately outside the 16-enum `EnumVocabularyTest` pin — like `AllowedAction`/`ErrorCodes`.
- **Drift (low): §6 names only two codes; the code ships seven.** ARCHITECTURE.md §6 (`:165`) cites the named-code set as `IC_CANNOT_RESOLVE_DISPUTE`, `MANAGER_ROLE_REQUIRED` — illustrative, not exhaustive. The shipped authorizer has expanded to seven local codes across Phases 3–5 (`DomainAuthorizationService.java:68`). This is an *expansion*, not a contradiction; the per-code additions are tracked in `apps/wc-api/CLAUDE.md` (`ErrorCodes` row) + Appendix B.21, but §6's prose list was not updated.
- **Drift (low): §6 lists `403/404` for the heatmap-drill-down denial; code returns `404`.** `authorizeHeatmapCellAccess` for a not-own cell returns `404` (`DomainAuthorizationService.java:150`) — consistent with the IDOR-safe rule; §6's "`403`/`404`" phrasing for "manager opens a heatmap drill-down cell not their own" (`:163`) reads ambiguously but the code's `404` is the correct IDOR choice (an unrelated cell's existence is hidden).
- **`AuditEventRepository` is a bare `JpaRepository` with no finders** (`AuditEventRepository.java:8`) — audit is write-only from the app's perspective; there is no read/query API for audit in this layer.

## Connects to

- [01-domain-persistence.md](01-domain-persistence.md) — owns the `audit_event` table columns + the `Employee`/`ManagerRelationship`/resource entities + repositories this layer reads; the flat-FK `findById` owner-resolution chains live against those repos.
- [02-api-web.md](02-api-web.md) — consumes `ResourceNotFoundOrUnauthorizedException` (→ `404`) and `AuthorizationDeniedException` (→ `403` + `code`) and renders them as RFC-7807; also renders the entry-point `401` / access-denied `403`. The `SecurityFilterChain` here is the front door of the API layer.
- [03-application-lifecycle.md](03-application-lifecycle.md) — every lifecycle/dispute/review service calls an `authorize…` method as its first statement and emits domain `audit_event`s through `AuditService` (the writer this layer owns).
- [05-manager-projections.md](05-manager-projections.md) — the manager command-center/heatmap reads are IDOR-scoped by the authenticated manager id and gated by `authorizeTeamHeatmapAccess` / `authorizeHeatmapCellAccess`.
- [07-scheduled-jobs.md](07-scheduled-jobs.md) — the generation CronJob + projection rebuild run under `SystemPrincipal` (exempt from scoping) and still write a SYSTEM-actor `audit_event` per run.
- [06-calendar-sync-messaging.md](06-calendar-sync-messaging.md) — `authorizeSyncRecordAccess` gates the owner's sync-retry; the worker reloads under SYSTEM context.

---
_Generated by `/layer-docs` (initial run) against commit `3b919a9` on 2026-06-04. Claims are anchored to code; `UNVERIFIED` marks anything not confirmed._
