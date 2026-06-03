# Session 004 — Phase 2 Auth0 claim mapper + env-gated demo-auth filter (rule #5)

- **Date:** 2026-06-02
- **Phase:** 2 (Identity, authentication & central authorization)
- **Role:** `st6-main-wc-api-implementer` (backend)
- **Predecessor session:** [003 — Phase 1 JPA entities + repos & Phase 2 Auth0 JWT decoder](003-2026-06-02-phase1-entities-repos-and-phase2-jwt.md) _(same backend arc; the pair cycled at WARN ~73% after 2.3)_
- **Successor session:** _(TBD — fresh full-budget backend pair resumes at 2.4 PrincipalResolver)_
- **Commits this session (2 backend slices):** `869eb69` (2.2 Auth0 claim mapper) · `3c7781e` (2.3 demo-auth filter + AuditService). _(Frontend-track commits `e9ee230`/`8fa030c` interleaved — not this arc.)_

## Why this session existed

Continue the Phase-2 identity spine after the JWT decoder (2.1): map a validated `Jwt` to a stable identity (2.2), and land the safety-critical env-gated demo-identity filter — the production-backdoor control (2.3, rule #5) — plus the minimal central `AuditService` the rejection mandates. The pair was cycled after 2.3 (context budget) so a fresh pair takes 2.4 → 2.5.

## What was built

### Files created
**2.2 — Auth0 claim mapper (`:api`)**
- `config/Auth0Identity.java` — `record(externalSubject, role, email)` (the pure identity).
- `config/Auth0ClaimMapper.java` — `@Component`, maps a validated `Jwt` → `Auth0Identity` via config-bound claim names (F.1), `sub` fallback, validate-when-present / tolerate-absent-or-blank role parsing.
- `config/Auth0ClaimMapperTest.java` — 8 pure-`Jwt` unit tests.

**2.3 — demo-auth filter + audit (`:api`) — SAFETY-CRITICAL (rule #5)**
- `config/DemoAuthFilter.java` — plain `OncePerRequestFilter`; env-gated `X-Demo-Employee-Id` (403+audit when disabled, header-is-principal when enabled, IDOR-safe 401 on bad ids, demo-wins single-source).
- `audit/AuditService.java` — `@Service`, minimal central audit writer (safe-metadata only; `created_at` from the `:shared` `Clock`; `@Transactional`).
- `config/DemoAuthFilterTest.java` (6 unit tests) + `audit/AuditServiceTest.java` (1 Testcontainers test).
- `support/SharedPostgres.java` — the single PG16 Testcontainer shared across the whole `:api` suite.
- `support/AbstractAppBootTest.java` — DB-backed app-boot base (Option A).

### Files modified
- **2.2:** `config/Auth0Properties.java` (added the typed `Claims` sub-record + `claims` component — the 2.1-deferred stub); `application.yml` (`auth0.claims.{employee-id,role,email}` F.1 defaults using `${ROOT_DOMAIN:localhost}`).
- **2.3:** `support/AbstractJpaIntegrationTest.java` (now uses `SharedPostgres` — one container, not per-base); `WcApiApplicationTest.java` + `WcApiDemoBootTest.java` (extend `AbstractAppBootTest` → boot DB-backed; dropped the DataSource/Hibernate excludes, kept the security-autoconfig exclude).

## Decisions made
- **2.2 claim mapper:** split structure (`Auth0Properties.Claims` config + `Auth0ClaimMapper` logic); output `Auth0Identity`; **role validate-when-present / tolerate-absent-or-blank → null** (a non-blank value not in `{IC,MANAGER}` rejects via `OAuth2AuthenticationException`; absent/blank → `null` — the authoritative role is the `Employee` row in 2.4); reject message generic (no role-value echo, rule #7); no real-mode gating (always-present `@Component`).
- **`${ROOT_DOMAIN:localhost}` base default (2.2):** `Auth0Properties` binds in all profiles (via `JwtConfig`'s class-level `@EnableConfigurationProperties`), so an unresolvable `${ROOT_DOMAIN}` in the base claim defaults would break the demo/local boots at binding — the resolvable fallback fixes it; prod's env overrides.
- **2.3 demo filter (rule #5):** plain class (2.6 registers it in the chain — avoids the `@Component`-`Filter` double-registration); `PreAuthenticatedAuthenticationToken(principal=employeeId)` (2.4 enriches); two denial semantics — disabled+header → **403 + one safe audit** (never DB-resolving or echoing the untrusted value), enabled+unknown/blank/malformed → **401 IDOR-safe, no audit**; `entityType="Authentication"`, `action=DEMO_AUTH_REJECTED`.
- **Option A (blast-radius, orchestrator-decided):** `AuditService` is a proper `@Service`; since Phase 2 makes the app DB-dependent in every mode, the app-boot tests now boot DB-backed against the shared Testcontainers PG16 (Flyway + `ddl-auto=validate`), keeping only the security-autoconfig exclude — the DB-less skeleton boot is outgrown.

## Decisions explicitly NOT made
- **`SecurityFilterChain` wiring of `DemoAuthFilter`** — deliberately 2.6 (2.3 proves filter *behavior*; 2.6 proves it's *in the chain*).
- **Clearing `SecurityContext` in the filter** — not the filter's job (the chain's `SecurityContextHolderFilter` clears per-request); clearing here would break the "context holds auth after the filter" tests. → 2.6 confirms the context-holder filter is present.
- **`AuditService` `Propagation.REQUIRES_NEW`** — not needed for 2.3 (the filter has no ambient tx). → 2.5's denial-audits (written during a rolled-back mutation) need it.
- **Richer `AuthenticatedPrincipal` (role/isManager)** — 2.4.

## TDD compliance
- **2.2:** clean test-first (RED on missing `Auth0ClaimMapper`/`Auth0Identity`/`Auth0Properties.Claims`).
- **2.3:** clean test-first (RED on missing `DemoAuthFilter`/`AuditService`). GREEN-phase corrections only: a wrong import package (`PreAuthenticatedAuthenticationToken`) and the `AuditServiceTest` jsonb assertion switched to parse-and-compare (jsonb normalizes whitespace — the 2.1 lesson) — both fixes to test/impl correctness, not test-after-implementation. No violations; no safety-critical TDD skips.
- 2.3 ran the **security-reviewer** (ad-hoc rule-#5 exception): **0 critical / 0 high** — rule #5/#7/§5 controls confirmed sound. (Per the new directive, per-slice reviewers are OFF by default; 2.3 was the explicit exception.)

## Reachability
- **2.2 `Auth0ClaimMapper`:** an always-present `@Component`; request-reachable once **2.4** (`PrincipalResolver` consumes `Auth0Identity`) + **2.6** (chain invokes it on a validated `Jwt`) land — tracked. Not an orphan.
- **2.3 `DemoAuthFilter`:** a plain class proven to *behave* correctly; production-reachable once **2.6** registers it in the `SecurityFilterChain` (tracked — the orchestrator banked the wiring test). `AuditService` is a live `@Service` (the DB-backed boots prove it loads), consumed by the filter (via 2.6) + 2.5. No orphans; no new wiring task beyond the banked 2.6 item.

## Open follow-ups

**Step-9 categorized items (orchestrator hot-routed; surfaced for `/orchestrate-end` verification):**

*2.2*
- Architecture note: `Auth0Properties` binds in all profiles (Appendix C.2/D.2 realized delta); the claim-mapping contract (JWT role = coarse hint; authoritative role/`isManager` relationship-derived in 2.4).
- LESSONS §13: config-driven claim-mapper idiom + the always-present-`@Component`-forces-all-profile-config-binding → needs-a-resolvable-placeholder-default gotcha.
- Cross-doc invariant: NONE (mapping inputs only; no Appendix-A field change).

*2.3*
- Architecture note (Appendix C.2 realized): DB-backed app-boot tests + `SharedPostgres`; `AuditService` central writer; `DEMO_AUTH_REJECTED` + `entityType="Authentication"` seed the §15 audit taxonomy.
- LESSONS §14: the backdoor-control filter pattern (runs-in-all-modes-to-reject; reject-without-DB-query; IDOR-safe convergent 401; plain-class-avoids-double-registration; minimal `AuditService` + SENTINEL-pinned safe-metadata) + the DB-backed-app-boot (Option A) migration.
- Cross-doc invariant: NONE (filter + service; reuses Employee + AuditEvent).
- **Future TODO → next-brief working set (2.6):** (a) test `DemoAuthFilter` is actually wired into the `SecurityFilterChain` in real mode (the other half of the always-in-chain pin); (b) confirm the chain clears `SecurityContext` per-request (standard `SecurityContextHolderFilter` — no thread-local bleed).
- **Future TODO → belongs to a phase (2.5/§15):** `AuditService.record` is `@Transactional(REQUIRED)` → for denial-audits written during a **rolled-back** mutation, use `Propagation.REQUIRES_NEW` (else the audit rolls back with the denied mutation — would violate the rule-#3 denial-audit requirement).

**Cross-doc invariant audit:** no Appendix-A model field changes this session. No discipline violation.

## How to use what was built
- **Claim mapper:** `Auth0ClaimMapper.map(jwt)` → `Auth0Identity`; 2.4's `PrincipalResolver` resolves `externalSubject` → `Employee`. Claim names are config-driven (`auth0.claims.*`).
- **Demo filter:** a plain `DemoAuthFilter(demoEnabled, EmployeeRepository, AuditService)` — 2.6 constructs it as a `@Bean` (passing the canonical `demo-auth.enabled`) and adds it to the `SecurityFilterChain`.
- **AuditService:** `@Autowired` it; `record(action, entityType, entityId, actorEmployeeId, summary, safeMetadataJson)` — pass safe metadata only (never untrusted/secret/PII).
- **Test harness:** full-context app-boot tests extend `AbstractAppBootTest` (DB-backed via `SharedPostgres`); JPA-slice tests extend `AbstractJpaIntegrationTest` (same shared container).
