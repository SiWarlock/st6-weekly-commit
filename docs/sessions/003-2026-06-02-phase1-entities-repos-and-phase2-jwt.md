# Session 003 — Phase 1 JPA entities + repos & Phase 2 Auth0 JWT decoder

- **Date:** 2026-06-02
- **Phase:** 1 (Physical schema / JPA) → 2 (Identity, authentication & central authorization)
- **Role:** `st6-main-wc-api-implementer` (backend)
- **Predecessor session:** [001 — Phase 0 backend + Phase 1 schema trio](001-2026-06-02-phase0-backend-and-phase1-schema.md) _(the backend arc; session 002 is the parallel `st6-main-wc-web-*` frontend track, not this arc)_
- **Successor session:** _(TBD — team paused after 2.1; next backend slice is 2.2 claim mapper)_
- **Commits this session (3 backend slices):** `1d43a01` (1.5 JPA entities + bare repos) · `8da6446` (1.6 repo finders + repo-layer invariant proofs) · `922211e` (2.1 Auth0 JWT decoder). _(Frontend-track commits `4efd1e6`/`1e4f5eb`/`1479ea6`/`cceaed7`/`9f17c3c` interleaved — not this arc.)_

## Why this session existed

Close the Phase-1 persistence layer (the JPA entities + repositories that mirror the landed Flyway V1–V3 schema, plus the repo-layer proofs of the locked invariants) and open the safety-critical Phase-2 auth spine with the Auth0 JWT validation foundation — all before any domain endpoint (§6 ordering).

## What was built

### Files created
**1.5 — JPA entities + bare repos (`:shared`)**
- 14 entities under `com/st6/wc/<domain>/`: `employee/Employee`, `relationship/ManagerRelationship`, `rcdo/{RallyCry,DefiningObjective,SupportingOutcome}`, `plan/WeeklyPlan`, `commitment/WeeklyCommitment`, `review/ManagerReview`, `dispute/AlignmentDispute`, `comment/Comment`, `sync/OutlookCalendarSyncRecord`, `audit/AuditEvent`, `projection/{ManagerPlanSummary,ManagerHeatmapCell}` — three base-class shapes (5 mutable extend `PersistableUuidEntity`; 6 audited-non-versioned extend `AbstractAuditingEntity`+inline `@Id`; 3 minimal inline `@Id`+own timestamp).
- 14 bare `<domain>/repo/<Entity>Repository.java` (`JpaRepository<E,UUID>`, no finders).
- `api/.../support/AbstractJpaIntegrationTest.java` — `@DataJpaTest` + singleton Testcontainers PG16 + `@DynamicPropertySource` + Flyway V1–V3 + `ddl-auto=validate` harness.
- `api/.../persistence/JpaEntityMappingTest.java` — 8 round-trip/mapping/fidelity tests.

**1.6 — repo finders + constraint proofs**
- `api/.../persistence/RepositoryConstraintTest.java` — 10 tests (repo-layer `DataIntegrityViolationException` for the unique + 3 partial-uniques; `@Version` increment + `ObjectOptimisticLockingFailureException`; the 3 finders).

**2.1 — Auth0 JWT decoder (`:api`)**
- `config/JwtConfig.java` — real-mode-gated `JwtDecoder` bean (eager `withIssuerLocation` OIDC discovery, RS256-pinned) + the package-static `jwtValidator(issuer,audience)` factory + fail-fast `requireRealModeConfig`.
- `config/AudienceValidator.java` — `OAuth2TokenValidator<Jwt>` enforcing `aud` *contains* the configured audience.
- `config/Auth0Properties.java` — `@ConfigurationProperties(prefix="auth0")` record (`audience`; `claims` deferred to 2.2).
- `config/JwtDecoderConfigTest.java` — 12 tests (generated RSA key + Nimbus-signed tokens + MockWebServer OIDC-discovery stub; no live Auth0).

### Files modified
- **1.5:** `shared/build.gradle` (+`spring-boot-starter-data-jpa` — Hibernate-core for the `@JdbcTypeCode` array/json mappings); `api/build.gradle` (+`spring-boot-starter-data-jpa` + Testcontainers/Flyway/pg test deps); `WcApiApplicationTest`+`WcApiDemoBootTest`+`worker/.../WcSyncWorkerApplicationTest` (test-local `spring.autoconfigure.exclude` of DataSource/JPA — the `:shared` starter propagates to `:api`+`:worker`).
- **1.6:** `relationship/repo/ManagerRelationshipRepository`, `dispute/repo/AlignmentDisputeRepository`, `sync/repo/OutlookCalendarSyncRecordRepository` (each +1 derived `Optional` finder).
- **2.1:** `api/build.gradle` (+`oauth2-resource-server` starter, +`spring-security-test`, +`mockwebserver` test); `application.yml` (issuer-uri/`auth0.audience`/`demo-auth.enabled`); `application-prod.yml` (audience); `application-local.yml`+`application-demo.yml` (`demo-auth.enabled: true`); `WcApiApplicationTest`+`WcApiDemoBootTest` (extended the exclude with the security autoconfigs).

## Decisions made
- **JPA entity shapes (1.5):** three base-class shapes, no refactor of the landed 0.3 bases; flat `UUID` FK fields, **no `@ManyToOne`** (services compose via repos; entities never cross the API boundary). `text[]`→`List<RiskBadge>` via `@JdbcTypeCode(SqlTypes.ARRAY)`+`@Enumerated(STRING)`; `metadata_json jsonb`→`String` via `@JdbcTypeCode(SqlTypes.JSON)`. JPA auditing populator deferred to Phase 2 (resolves the 1.2 audit-NOT-NULL carry-forward as "deferred").
- **SpotBugs EI/EI2 on the `riskBadges` Lombok accessor (1.5):** hand-wrote defensive-copy accessors (safe — entity uses field access; Hibernate bypasses them). The `@lombok.Generated` skip does NOT cover EI/EI2 for mutable-collection fields.
- **Finder style (1.6):** Spring Data derived names returning `Optional` (partial uniques guarantee ≤1). `@DataJpaTest` constraint-proof pattern: `saveAndFlush` (plain `save` defers under test rollback); coexistence-asserted-first / violation-last; single-tx `em.detach` two-snapshot for the optimistic-lock conflict; assert the Spring `ObjectOptimisticLockingFailureException`. Unresolved-dispute proof uses OPEN+IC_RESPONDED (the cross-status bucket), not a same-status duplicate.
- **Auth0 decoder (2.1):** canonical mode gate `demo-auth.enabled` (explicit `${DEMO_AUTH_ENABLED:false}` placeholder — relaxed binding alone targets the dotted form), shared by 2.3/2.6. base/prod = real mode (secure-by-default, fail-closed); local/demo = demo mode (gates the decoder off). **Decision A:** keep eager `withIssuerLocation` (D.2 discovery-default + a startup fail-fast on issuer misconfig) and cover the bean construction with a MockWebServer OIDC-discovery stub. Dual blast-radius mitigation: demo-mode gating + security-autoconfig exclude on the skeleton boots.

## Decisions explicitly NOT made
- **`Auth0Properties.claims` sub-record** — deferred to 2.2 (don't speculate the claim-name shape; 2.2 owns it).
- **Lazy `withJwkSetUri` decoder** — rejected for 2.1 (D.2 default is issuer-discovery; lazy is the documented fallback if startup/IdP decoupling is ever needed).
- **Shared fixture-builder extraction** across `JpaEntityMappingTest`/`RepositoryConstraintTest` — declined (rule-of-three; only 2 consumers; would churn a committed test).
- **The `SecurityFilterChain` wiring** — deliberately 2.6, not 2.1.

## TDD compliance
- **1.5, 1.6:** clean test-first — RED confirmed failing for the right reason (missing entities/repos/finders) before any implementation.
- **2.1:** the 12 security tests were test-first (RED on missing `JwtConfig`/`AudienceValidator`). Minor note: `real_mode_builds_decoder_bean` was added during the GREEN/coverage phase to cover the `@Bean` construction glue (`withIssuerLocation`/`setJwtValidator`/return) the other 11 tests didn't exercise — a coverage backfill over framework-glue lines, **not** new business logic; the security-critical validator behavior was fully test-first. No safety-critical TDD violation.
- All slices passed `./gradlew check` (the §13 gate, LESSONS §6) before commit; both 1.5 and 2.1 ran the code-quality + security reviewers (0 critical / 0 high).

## Reachability
- **1.5 entities + repos:** auto-scanned by `WcApiApplication`; proven instantiable as Spring Data beans in-test. Consumers = 1.6 + Phase 2/3 services (`PlanLifecycleService`, `DomainAuthorizationService`, `ProjectionService`). Tracked downstream; not an orphan.
- **1.6 finders:** invoked via repo beans in-test. Consumers = Phase 2 (`DomainAuthorizationService` active-manager finder + deny-branch), Phase 3/5 (dispute/lifecycle + `@Version` conflict). Tracked; not an orphan.
- **2.1 `JwtDecoder`:** request-reachable once **2.6** wires it into the `SecurityFilterChain` (`oauth2ResourceServer().jwt()`). 2.6 is a tracked Phase-2 slice. The decoder/validator unit is fully proven here; the 401-on-protected-endpoint behavior is 2.6 by design. Not an orphan.
- No tested-but-unwired gaps requiring a NEW wiring task — all consumers are already tracked phases.

## Open follow-ups

**Step-9 categorized items (orchestrator hot-routed during the session; surfaced here for `/orchestrate-end` verification):**

*1.5*
- Cross-doc table row: JPA entity layer = executable mirror of all 14 Appendix-A models → `apps/wc-api/CLAUDE.md` (orch, done hot).
- Architecture note: Appendix C.2 realized-tree (`:shared`+`:api` gain data-jpa starter; the 3-test autoconfigure-exclude) → `ARCHITECTURE.md` (orch, at `/orchestrate-end`).
- LESSONS §7–§10 candidates: 3-shape base-class + flat-UUID-FK idiom; Hibernate-6 `text[]`/`jsonb` recipes; `@DataJpaTest`+`ddl-auto=validate` fidelity harness; EI/EI2-on-Lombok-mutable-collections; entities-don't-cap-varchar posture → `LESSONS.md` (orch).
- Future TODO (Phase 2): JPA auditing populator (`AuditorAware`) + audit-NOT-NULL decision → resolves the 1.2 carry-forward as "deferred to Phase 2."
- Future TODO (1.6, now discharged): behavioral `@Version` assertion → landed in 1.6's `version_increments_on_update`.

*1.6*
- Architecture confirmation: V2 partial-unique + `@Version` rows now have repo-layer proof (no field edits).
- LESSONS §11: derived-`Optional`-finder idiom; the `@DataJpaTest` constraint/`@Version` test pattern; the OPEN+IC_RESPONDED rule-#6 framing → `LESSONS.md` (orch, done hot).
- Future TODO (declined): shared fixture-builder extraction (orchestrator declined to carry — rule-of-three).

*2.1*
- Architecture note: §6/D.2 eager-discovery (`withIssuerLocation`) = fail-fast; lazy `withJwkSetUri` = documented fallback. + Appendix C.2 (oauth2 starter + test deps + skeleton-boot exclude). + profile-semantics (local/demo=demo, base/prod=real/secure-default) → `ARCHITECTURE.md` (orch, at `/orchestrate-end`).
- LESSONS §12: SS6 resource-server decoder recipe (eager-`withIssuerLocation` corrected); autoconfig-exclude ≠ component-scan suppression (dual mitigation, extends §9); fail-secure gating idiom → `LESSONS.md` (orch).
- **Future TODO — next-brief working set (2.6):** prove the custom `JwtDecoder` wins over Boot's auto-configured `issuer-uri` decoder (`@ConditionalOnMissingBean` no-collision) — register `OAuth2ResourceServerAutoConfiguration` in the runner; natural home is 2.6 where the resource server is wired.
- **Future TODO — belongs to a phase:** (a) authn accept/deny audit breadcrumb → Phase-2/§15 audit slice; (b) **infra:** `DEMO_AUTH_ENABLED` must be absent/pinned-false in deployed real-mode manifests (k8s/Secrets) → infra area / Phase 12.

**Cross-doc invariant audit:** no Appendix-A model field changes this session (1.5 entities mirror the V1–V3 DDL exactly — no drift, confirmed by both reviewers; 1.6/2.1 add no model fields). No discipline violation.

## How to use what was built
- **Entities/repos:** `@Autowired` the `<Entity>Repository` beans; entities are flat-FK (look up related rows by id via repos, never object-graph navigation). The 3 finders (`findByDirectReportEmployeeIdAndActiveTrue`, `findByCommitmentIdAndStatusIn`, `findByOwnerEmployeeIdAndWeekStartDateAndEventKind`) return `Optional`.
- **JWT decoder:** active only in real mode (`demo-auth.enabled=false`); 2.6 wires it via `oauth2ResourceServer().jwt()`. Tests that need a JPA/security-free context boot must exclude the relevant autoconfigs (see the skeleton boot tests).
