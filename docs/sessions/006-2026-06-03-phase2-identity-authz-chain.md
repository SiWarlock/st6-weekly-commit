# Session 006 — Phase 2 identity / authz / security-chain / endpoint spine (2.4–2.7) — **Phase 2 complete**

- **Date:** 2026-06-03
- **Area:** backend (`apps/wc-api/`)
- **Phase:** Phase 2 (Identity, AuthN/AuthZ, Security chain) — slices 2.4, 2.5, 2.6, 2.7 (closes Phase 2)
- **Predecessor:** [`004-2026-06-02-phase2-claim-mapper-and-demo-auth-filter.md`](004-2026-06-02-phase2-claim-mapper-and-demo-auth-filter.md) (2.2/2.3)
- **Successor:** _(fresh backend impl — Phase 3 lock enforcement / safety rule #1; cycled at the Phase-2→3 boundary after 2.7)_
- **Commits:** `46a44e1` (2.4) · `6bebea4` (2.5) · `57af246` (2.6) · `7d79fd8` (2.7)

## Why this session existed

Build the Phase-2 identity→authorization→request-path→first-endpoint spine on top of 2.1–2.3: resolve any identity to a principal (2.4), centrally authorize every resource access IDOR-safely (2.5, rule #3), wire it into the HTTP request path (2.6, rule #5/#3 operational), and close Phase 2 with CORS + the first real endpoint/DTO (2.7). After 2.7 the full **authn→authz→DTO request path is live end-to-end** and the §2 Phase-2 acceptance criteria are met.

## What was built

### 2.4 — PrincipalResolver + principals (`46a44e1`)
- **NEW (`api/`):** `identity/PrincipalResolver` (`Auth0Identity`|demo-id → `Optional<UserPrincipal>`; role=authoritative `Employee.role`, isManager=relationship-driven), `identity/UserPrincipal` (record `implements AuthenticatedPrincipal`; **renamed from `AuthenticatedPrincipal`** — SpotBugs `NM_SAME_SIMPLE_NAME_AS_INTERFACE`), `identity/SystemPrincipal` (singleton no-HTTP boundary); tests `PrincipalResolverTest` + `IdentityFinderTest`.
- **MODIFIED (`shared/`):** `EmployeeRepository` (+`findByExternalSubject`), `ManagerRelationshipRepository` (+`existsByManagerEmployeeIdAndActiveTrue`).

### 2.5 — central DomainAuthorizationService (`6bebea4`, SAFETY-CRITICAL rule #3)
- **NEW (`api/auth/`+`identity/`):** `auth/DomainAuthorizationService` (single authorizer; IC-self/manager-active-direct-report/SYSTEM-exempt; cross-owner→404 IDOR-safe, capability→403+code; one safe denial audit per genuine denial; missing→404-no-audit), `auth/AuthorizationDeniedAuditer` (`@Transactional(REQUIRES_NEW)` — survives rolled-back mutation), `auth/ResourceNotFoundOrUnauthorizedException` (404) + `auth/AuthorizationDeniedException` (403+code), `identity/DomainPrincipal` (`sealed permits UserPrincipal, SystemPrincipal`); tests `DomainAuthorizationServiceTest` + `AuthorizationIdorMatrixTest` (Testcontainers §17 IDOR matrix, SENTINEL no-leak) + `AuthorizationDeniedAuditerTest` (`@SpringBootTest` REQUIRES_NEW survives-rollback). Security-reviewed (3 invariants PASS, 0 crit).

### 2.6 — SecurityConfig per-mode chain + RFC-7807 (`57af246`, safety-touching rule #5/#3)
- **NEW (`api/config/`+`web/`):** `config/SecurityConfig` (`proxyBeanMethods=false @EnableWebSecurity @EnableMethodSecurity` + two `@ConditionalOnProperty(demo-auth.enabled)` chains + fail-fast on a non-boolean gate), `config/PrincipalJwtAuthenticationConverter` (Jwt→`UserPrincipal`, empty→401), `web/ProblemDetailFactory` (static util) + `web/ProblemDetailsExceptionHandler` (advice) + `web/ProblemDetailsAuthenticationEntryPoint` (401) + `web/ProblemDetailsAccessDeniedHandler` (403); 6 test classes (incl. `SecurityChainRealModeTest` MockWebServer issuer/JWKS, `CustomJwtDecoderWinsTest`, `SecurityConfigGateTest`).
- **MODIFIED:** `config/DemoAuthFilter` (resolve via `PrincipalResolver`→`UserPrincipal`+`ROLE_<role>`), `identity/PrincipalResolver` (`.filter(Employee::isActive)` — Q-B). Security-reviewed (3 invariants PASS); the HIGH (malformed-gate) fixed in-slice.

### 2.7 — CORS + GET /api/me (`7d79fd8`, Phase-2 closer)
- **NEW (`api/me/`+`config/`):** `me/dto/MeDto` (the B.3 record — first DTO across the boundary), `me/MeService` (`UserPrincipal`+`Employee` → `MeDto`; persona=email), `me/MeController` (`GET /api/me`, authenticated-only, no authorizer call — self), `config/CorsConfig` (exact-origin `CorsConfigurationSource`, no wildcard, `allowCredentials=false`, mode-independent); tests `MeServiceTest` + `CorsConfigTest` + `MeEndpointTest` (`@SpringBootTest`+MockMvc, incl. entity-leak assertion + preflight + REQ-F-032).
- **MODIFIED:** `config/SecurityConfig` (+`http.cors()` both chains), `application.yml`+`application-prod.yml` (`app.cors.allowed-origins`).

## Decisions made

- **2.4** `AuthenticatedPrincipal`→`UserPrincipal` rename (SpotBugs shadow gate; lead-blessed). **2.5** lead-adjudicated 403/404 mapping + REQUIRES_NEW + missing→404-no-audit + `DomainPrincipal` marker; build-and-throw deny helpers (JaCoCo artifact); escaped-`ObjectNode` audit metadata. **Q-B** inactive principal enforced at 2.6 resolution (`filter(active)`, zero extra DB hit). **2.6** SpotBugs idioms (`ProblemDetailFactory`→static util for EI2; `proxyBeanMethods=false`+`final` for CT_CONSTRUCTOR_THROW; manual `ProblemDetail` flatten for the filter-write path); HIGH gate fail-fast. **2.7** `persona=email` both modes (lead-confirmed); `MeDto` record mirrors B.3 verbatim; `/me` authenticated-only (no no-op self-authz); CORS via `http.cors()` mode-independent (no demo widen).

## Decisions explicitly NOT made (deferred)

- **JPA-auditing populator** (`@EnableJpaAuditing`+`AuditorAware<String>`) — UNBLOCKED by 2.6; its own small slice (orchestrator retargets).
- **authn-layer 401 audit breadcrumb** — log-only for MVP (revisitable).
- **`MANAGER_ROLE_REQUIRED` routing guard** — the team-heatmap controller (9.9/9.10) must route through `DomainAuthorizationService` (coded+audited 403), NOT a coarse `@PreAuthorize`. Carry-forward.
- **[MEDIUM] filter-path `ObjectMapper` write-robustness** — theoretical for the fixed small bodies; not hardened.

## TDD compliance

**Clean — no violations.** All four slices ran strict RED → Step-2.5 (orchestrator/lead review) → GREEN, test-first. 100% line+branch coverage on every new type across all four. Mid-GREEN refactors (rename, deny-helper return-and-throw, the SpotBugs fixes, the HIGH fail-fast) stayed inside the GREEN/refactor loop with the suite green. **Process note:** 2.7's RED tests were written→approved, then discarded + re-created verbatim from the approved design during a crossed cycle-timing whipsaw (lead "cycle now" crossed "2.7 runs"); they were re-created BEFORE GREEN from the approved Step-2.5 design — no test-after-implementation. The session-doc commit `a5ee924` (a premature 006 covering only 2.4–2.6) was correctly reverted when the "2.7 RUNS" lock landed.

## Cross-doc invariant audit

**One cross-doc change this session: `MeDto` (2.7) — the FIRST backend DTO across the boundary = Appendix B.3.** Flagged at Step 9 `Cross-doc invariant change`; the orchestrator adds the `MeDto`↔B.3 row to the `apps/wc-api/CLAUDE.md` cross-doc table at `/orchestrate-end`. **No `ARCHITECTURE.md` edit needed** — B.3 already exists + the frontend already mirrors it; the backend record matches B.3 verbatim (`employeeId/email/displayName/role/persona/isManager/timezone?`). 2.4/2.5/2.6 added no Appendix-A field changes. LESSONS §15–§20 + CLAUDE index rows were hot-routed (committed `da781bb`).

## Reachability

- **2.7 `GET /api/me` is the first real `/api/**` endpoint** — a live `@RestController` on the 2.6 chain (proven by `MeEndpointTest` driving real requests + the preflight). `CorsConfig` wired into both chains via `http.cors()`. **The full authn→authz→DTO request path is live end-to-end.**
- **2.6 chain IS the production request path** → resolved the declared-dep status of 2.1–2.5 (decoder, claim mapper, demo filter, resolver, authorizer all fire).
- **Still declared-dep (tracked):** Phase 3+ controllers call `DomainAuthorizationService`; `SystemPrincipal` consumed at Phase 8 cron / Phase 10 worker.

## Open follow-ups

**Step-9 categorized items (surfaced for `/orchestrate-end` verification — orchestrator hot-routed):**
- Convention (LESSONS §15–§20 committed `da781bb` + new for 2.6/2.7): relationship-driven resolution; NM-shadow gate gotcha; central-authorizer idiom; build-and-throw deny helpers; escaped-JSON audit metadata; SS6 per-mode chain idiom + the 3 SpotBugs gotchas (EI2→static-util, CT→proxyBeanMethods=false+final, ProblemDetail props-nesting); first-controller+DTO pattern (record-not-entity + `.doesNotExist()` leak test); `CorsConfigurationSource`-via-`http.cors()`-both-chains-mode-independent.
- Arch notes (queued, orchestrator `/orchestrate-end`): §6 realized (chain makes 2.1–2.5 reachable; inactive→401; per-mode chain); the 403/404 mapping + `AUTHORIZATION_DENIED` + the two 403 codes (pin `MANAGER_ROLE_REQUIRED`/`IC_CANNOT_RESOLVE_DISPUTE` into §5/B.21); **Phase 2 COMPLETE** (full authn→authz→DTO path; persona=email; CORS exact-origin §12/§16).
- Cross-doc: **`MeDto`↔B.3** (orchestrator adds the `apps/wc-api/CLAUDE.md` row).
- Future TODO — phase/operational: JPA-auditing populator (own slice, unblocked); `MANAGER_ROLE_REQUIRED` routing guard (9.9/9.10); SystemPrincipal consumers (Phase 8/10); [MEDIUM] filter-write robustness; authn-401-audit log-only (revisitable).

## How to use what was built

Every later-phase controller: authenticate via the chain (`@AuthenticationPrincipal UserPrincipal` in both modes), then call `domainAuthorizationService.authorize…(principal, id)` **before** any repo read/mutation (the rule-#3 chokepoint), and return a **DTO record** (never an entity, forbidden-pattern #3 — assert no entity-field leak). Throw `ResourceNotFoundOrUnauthorizedException`(404)/`AuthorizationDeniedException(code)`(403); the `ProblemDetailsExceptionHandler` renders RFC-7807. `GET /api/me` is the reference thin-controller→service→DTO pattern. Manager *resource* surfaces must route through the authorizer (the `MANAGER_ROLE_REQUIRED` guard), not a coarse `@PreAuthorize`.
