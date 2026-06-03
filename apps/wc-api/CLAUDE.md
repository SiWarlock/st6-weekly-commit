# ST6 Weekly Commit Module `apps/wc-api/` — Build Guide

> **You're in `apps/wc-api/`.** This file plus root `CLAUDE.md` both load. The root file covers global project conventions + shared comm rules (track-prefix, escalation taxonomy, messaging budget); this file owns code-area conventions for backend.

## Launch protocol

| Working on... | cwd | Loads |
|---|---|---|
| Planning / docs / commits | repo root (`ST6/`) | root `CLAUDE.md` only |
| backend code | `apps/wc-api/` | this `CLAUDE.md` + root |

<!-- For a multi-area project, add a row per additional code area. -->

If you find yourself fighting the wrong conventions, check your cwd.

## Session start/end protocol

**At session start:**
1. Read `MVP_TASKS.md` (repo root) → "Currently in progress" section.
2. Confirm with the user what feature this session is targeting.
3. Read the relevant section of `ARCHITECTURE.md` from the lookup table below.

**At session end** (only when the user explicitly says we're done):

1. **Implementer runs `/session-end`.** Implementer writes ONLY:
   - `apps/wc-api/` code files (the slice's implementation)
   - test files (the slice's tests)
   - dependency manifest / lockfile (deps the slice adds)
   - `docs/sessions/<NNN>-<date>-<topic>.md` (session doc, created at `/session-end` Step 5)

   **Implementer must NOT touch (all orchestrator territory):**
   - `MVP_TASKS.md`
   - `apps/wc-api/LESSONS.md`
   - `apps/wc-api/CLAUDE.md` (entire file — both the Cross-doc invariants table AND the Lessons logged index)
   - `ARCHITECTURE.md`
   - `docs/orchestrator-briefing.md` / `docs/tdd-brief-template.md` / `docs/briefs/` / `docs/runbooks/`
   - other top-level deliverable / design docs
   - `.gitignore` and root-level dotfiles (unless adding a new artifact to ignore, flagged at Step 9)

   At the slice's Step 10 commit, **explicit `git add <path>` for each slice file**; **never `git add -A`** or `git add .`; **never stage an orchestrator-territory file**. If the slice surfaces a change to any orchestrator-territory file (new model needing a cross-doc table row, a lesson candidate, an architecture note), the implementer **flags it at Step 9** per the routing matrix in `docs/orchestrator-briefing.md`. The orchestrator writes the change hot during the same session — working-tree state stays aligned within the round even though commits stagger.

2. **Orchestrator runs `/orchestrate-end`** for round close-out + Carry-forward triage + round terminal commit + push.

## Lookup table — where to find canonical info

Don't paste these sections into the prompt. Grep the file:section, read only what you need. `/check-arch <topic>` dispatches off this table.

| Topic | File (relative to repo root) | Section |
|---|---|---|
| Plan lifecycle (`DRAFT→LOCKED→RECONCILING→RECONCILED`) | `ARCHITECTURE.md` | §3 |
| Lessons logged (full prose) | `apps/wc-api/LESSONS.md` | by lesson # |

<!-- Starts near-empty. Add a row whenever a topic is looked up twice. -->

**Code intelligence & docs (when available):** prefer a code-intelligence MCP (e.g. CodeGraph) for code navigation / callers / traces over `grep`+read loops, and a docs MCP (e.g. Context7) for up-to-date library/API docs — see root `CLAUDE.md` "Code intelligence & docs." No-op if not installed.

## Stack

<!-- ▼ EXAMPLE BLOCK [id=area-stack]: stack quick-reference for implementer sessions. Canonical stack lives in root CLAUDE.md + ARCHITECTURE.md; this is the cheat sheet. ▼ -->

- **Runtime / build:** Java 21 · Gradle multi-module (shared / api / worker)
- **Framework:** Spring Boot 3.3 (Spring MVC · Spring Data JPA + Hibernate · Spring Security OAuth2 resource server)
- **Migrations / DB:** Flyway · PostgreSQL 16 (RDS, latest 16.x) — Flyway enabled ONLY on the migration Job
- **Validation:** Jakarta Bean Validation (Hibernate Validator) on every request DTO
- **Lint / types / tests:** Spotless + SpotBugs / javac (statically typed) + SpotBugs / JUnit 5 + Testcontainers · JaCoCo ≥80%/module

<!-- ▲ END EXAMPLE BLOCK [id=area-stack] ▲ -->

## Standard commands

> **Run backend Gradle commands from `apps/wc-api/`** (or via the repo-root composite). The repo-root build only exposes the **aggregate** `check`/`build`; per-task targets (`compileJava`/`spotbugsMain`/`spotlessCheck`/`test`) live in the `apps/wc-api` build. See LESSONS §6.

```bash
# (from apps/wc-api/)
# Run the dev server (if applicable)
./gradlew :api:bootRun

# Tests
./gradlew test

# Quality (per-task)
./gradlew spotbugsMain
./gradlew spotlessCheck
./gradlew compileJava

# Preflight / the real gate (use before saying "done" — this is also the §13 CI gate):
# runs Spotless + SpotBugs + JaCoCo ≥80%/module verification + forbidLombokData
# + checkModuleBoundaries + all tests, per module. NOT `build -x test` (jacoco
# verification is bound to check and needs tests to run).
./gradlew check
```

## TDD protocol

**Write the failing test first.** Applies to deterministic code — see the TDD posture in root `CLAUDE.md` for what is test-first vs. exempt.

**Commit per slice when practical.** Never bundle a safety-critical slice with anything else.

## Forbidden patterns

<!-- ▼ EXAMPLE BLOCK [id=forbidden-patterns]: forbidden patterns — 3-5 narrow, enforceable, domain-specific rules. Shape: "Don't <pattern X> because <reason / past incident>; use <alternative Y>." Test-pin them where possible. Starts small; accretes as lessons surface. ▼ -->

Do not:

1. **Write code without a failing test first** (deterministic backend logic). Even one-line methods.
2. **Use Lombok `@Data`** — use `@Getter`/`@Setter`/`@Builder`; `@Data`'s generated `equals`/`hashCode` on JPA entities breaks Hibernate identity.
3. **Return JPA entities across the API boundary** — map to DTOs (per Appendix B); entities leaking causes lazy-init + over-exposure bugs.
4. **Put lifecycle transitions in controllers** — transitions live in service methods (`PlanLifecycleService`, etc.) inside one transaction with validation + projection updates.
5. **Store `OVERDUE` as a review status** — it is derived at read time via an injectable `Clock`.
6. **Let an Outlook/SNS failure roll back a core mutation** — the sync record is a downstream outbox; failures are caught, never rethrown into the core txn.

<!-- ▲ END EXAMPLE BLOCK [id=forbidden-patterns] ▲ -->

## Cross-doc invariants — schema/docs mirroring

Several typed models in this codebase are **contracts** mirrored in `ARCHITECTURE.md` and indexed in the table below. The architecture doc is the canonical contract; the model is the executable enforcement. Drift produces silent disagreement.

**Authoring discipline (orchestrator owns this table).** When the implementer adds, removes, or renames a field on one of these models, the implementer **flags it at Step 9 categorized as `Cross-doc invariant change`** per the routing matrix in `docs/orchestrator-briefing.md`. The implementer does NOT edit `apps/wc-api/CLAUDE.md` or `ARCHITECTURE.md` directly — the orchestrator writes the table row + the architecture edit hot during the same session. Working-tree state aligns within the round; commits stagger (implementer's slice commit lands code+tests; orchestrator's round commit lands the doc rows).

| Model | `ARCHITECTURE.md` section | Notes |
|---|---|---|
| Enum vocabulary (16 enums in `shared/enums/`) | Appendix A / Appendix B.1 | Constant sets mirror Appendix B.1 **exactly** (REQ-D-010 — the executable mirror of the `VARCHAR`+`CHECK` columns). Pinned by `EnumVocabularyTest` (parameterized value-set + the 3 drift-trap negatives: no `ReviewStatus.OVERDUE`, `CommentTargetType={PLAN,COMMITMENT}`, `SyncRelatedType=MANAGER_REVIEW_WEEK`). `RoleType` is in `enums/`; `AllowedAction` is computed DTO vocab (Phase 3), not an `enums/` member. Adding/removing/renaming a constant requires a paired B.1 edit. (origin: 0.3) |
| Core schema (`V1__core_schema.sql`, 12 tables) | §4 / Appendix A | Physical encoding of the 12 Appendix-A core models. `VARCHAR`+`CHECK` status columns mirror the enum vocabulary exactly — pinned by the `enum↔CHECK` test (`V1CoreSchemaMigrationTest`, parses `pg_get_constraintdef` over all 15 status columns). Carries the 4 deltas (manager_alignment_note present, progress_status absent, comment flat `{PLAN,COMMITMENT}`, sync week_start_date + MANAGER_REVIEW_WEEK), `version bigint` `@Version` on the 5 mutable tables, `unique(employee_id,week_start_date)`. **Binding source = §4/Appendix A; `docs/planning/DATA_MODEL.md` is superseded (stale).** A column/constraint change pairs a §4 + Appendix A edit. (origin: 1.2) |
| Partial unique indexes (`V2__partial_unique_indexes.sql`) | §4 / §6 / §3 / §10 / Appendix A | The 3 partial uniques backing the single-row invariants (safety-relevant): `uq_active_manager_per_report` (single active manager, §6), `uq_one_unresolved_dispute_per_commitment` (one unresolved dispute, safety rule #6), `uq_one_review_block_per_manager_week` (one review-block/manager/week, §10). Each tested firing (23505) + non-firing/partial-scope on PG16. Distinct from the V1 full sync unique. **Repo-layer proof (1.6):** each fires through `saveAndFlush` as a Spring `DataIntegrityViolationException`; the unresolved-dispute index is proven across **OPEN + IC_RESPONDED** (the rule-#6 cross-status uniqueness bucket), not a same-status duplicate. A constraint-clause change pairs a §4 + Appendix A edit. (origin: 1.3) |
| Manager projections (`V3__projection_tables.sql`) | §9 / Appendix A | The 2 synchronous read-model tables — `manager_plan_summary` (unique(manager,employee,week)) + `manager_heatmap_cell` (unique(manager,employee,week,DO)), each with the 7 count columns + `updated_at` (read models: **no audit quartet, no `@Version`** — recomputed wholesale by the ProjectionService). `manager_heatmap_cell.risk_badges text[]` is constrained to the 6-badge `RiskBadge` vocabulary via a `<@` containment CHECK, pinned to `RiskBadge.values()`. `is_review_overdue` is the §9 projection column (NOT safety rule #6). A field change pairs a §9 + Appendix A edit. (origin: 1.4) |
| WeeklyPlan | §3 / Appendix A | Lifecycle state + locked baseline; mirror field changes into Appendix A. |
| JPA entities (14, `shared/<domain>/*.java`) | Appendix A (all 14 models) / §3 / §4 / §9 | The Spring Data JPA entities are the **executable mirror of all 14 Appendix-A models** — field names, nullability, enum bindings (`@Enumerated(STRING)`), the 4 deltas (`managerAlignmentNote` present, no `progressStatus`, `weekStartDate` present, comment `{PLAN,COMMITMENT}`), `@Version` on the 5 mutable lifecycle entities — mapped onto the V1–V3 columns. Three base-class shapes + flat-`UUID` FKs (LESSONS §7); `risk_badges text[]`/`metadata_json jsonb` via Hibernate-6 `@JdbcTypeCode` (§8). A field add/rename/remove pairs an Appendix A + `§`-section edit. **No drift at 1.5** (entities == V1–V3 DDL == Appendix A; both reviewers confirmed). `@Version` optimistic locking proven behaviorally at the repo layer (increment 0→1 + `ObjectOptimisticLockingFailureException` on stale update) in 1.6. (origin: 1.5) |
| `MeDto` (E1 response DTO, `api/me/dto/`) | Appendix B.3 / §5 (E1) / §6 | The **first DTO across the API boundary** — the executable mirror of B.3 (`employeeId/email/displayName/role/persona/isManager/timezone?`); a `record`, **never an entity** (forbidden-pattern #3, pinned by `MeEndpointTest`'s entity-field `.doesNotExist()` leak test). Fields bind to `Employee`/`ManagerRelationship` (Appendix A): `role`=authoritative `Employee.role`, `isManager`=relationship-driven (2.4), `persona`=email (both modes). B.3 already exists in `ARCHITECTURE.md` + the frontend mirrors it — **no Appendix edit needed**; a future field add/rename pairs a B.3 edit. Sets the DTO-mirrors-Appendix-B pattern for every Phase-3+ response. (origin: 2.7) |

<!-- Starts empty (or with the first model if one exists). Populated as contract models land. -->

## Module organization

<!-- ▼ EXAMPLE BLOCK [id=module-layout]: module layout + layer dependency rule. Replace with the project's real directory tree and import-direction DAG. ▼ -->

```
apps/wc-api/
  settings.gradle               # include 'shared','api','worker'
  shared/  src/main/java/com/st6/wc/   # JPA entities, enums, DTOs, repos (depended on by api + worker)
  api/     src/main/java/com/st6/wc/   # controllers, services, config (security/cors/jwt/flyway), jobs (generation, migration), auth, sns, web
  worker/  src/main/java/com/st6/wc/worker/   # SQS listener, graph adapters, sync-record service
```

Dependency direction (top depends on bottom, never reverse):

```
api  →  shared
worker → shared          (no api ↔ worker edge)
controllers → services → repositories → entities   (controllers never call repositories directly)
```

Cross-cutting layers can be imported from anywhere. Enforce the rule mechanically with a test where possible — the test *is* the spec for the rule.

<!-- ▲ END EXAMPLE BLOCK [id=module-layout] ▲ -->

## Subagents

See `.claude/agents/README.md` for the canonical inventory + integration points.

<!-- ▼ EXAMPLE BLOCK [id=area-subagent-candidates]: area-specific subagent candidates — list candidates that would earn their keep specifically in this area (e.g. an ABI/types syncer for a frontend area, a Pyth/feed verifier for a contracts area). Build only on real friction. ▼ -->

Candidates (build only on real friction): an **IDOR/authorization property-test writer** (generates the per-denial-case matrix); an **invariant test writer** for lifecycle/baseline-immutability; a **DTO↔entity mapper checker**.

<!-- ▲ END EXAMPLE BLOCK [id=area-subagent-candidates] ▲ -->

## Lessons logged from prior sessions

The full prose for each lesson lives in `apps/wc-api/LESSONS.md`. This index is the compact orientation surface.

**Lesson numbers are stable IDs** — once assigned, they don't change. New lessons get the next sequential number. `/session-end` proposes additions when it detects them; the user approves before the entry is written and a row is added here.

Lessons start at §1.

| # | Date | Topic | Rule (one-liner) |
|--:|---|---|---|
| 1 | 2026-06-02 | [Gradle gate-wiring recipe](LESSONS.md#1) | Bind Spotless+SpotBugs+JaCoCo (≥80% line+branch) into `check` per module, assert the aggregation, prove the module boundary with a task, resolve the JDK 21 toolchain via foojay+`JAVA_HOME` (never a committed machine path). |
| 2 | 2026-06-02 | [SpotBugs `Confidence` in Groovy](LESSONS.md#2) | For Kotlin enums with per-constant bodies referenced from Groovy (e.g. SpotBugs `Confidence`), use `Type.valueOf('NAME')`, not `Type.NAME`. |
| 3 | 2026-06-02 | [Enum ↔ contract pinning](LESSONS.md#3) | Pin enum-to-contract with a parameterized value-set test over all enums + `valueOf` drift-trap negatives; Phase 1 extends it to the DB `CHECK` list. |
| 4 | 2026-06-02 | [Spring Boot app-skeleton pattern](LESSONS.md#4) | Enable probe groups in base config; cover via `@SpringBootTest` + exclude `*Application` from JaCoCo (keep bundle non-empty with a real `@Configuration`); static/reusable env fail-safes; per-profile property tests via `ApplicationContextRunner` + `ConfigDataApplicationContextInitializer` (`o.s.boot.test.context`). |
| 5 | 2026-06-02 | [Flyway + Testcontainers PG16 harness](LESSONS.md#5) | Pin `testcontainers-bom:1.21.4` over the SB 3.3.5 BOM (Docker Engine 29 compat); migrate via the Flyway API on a shared TC PG16 container; pin enum↔CHECK by parsing `pg_get_constraintdef` vs `enum.values()`; assert violations by SQLSTATE. |
| 6 | 2026-06-02 | [Backend gate = `./gradlew check` from `apps/wc-api/`](LESSONS.md#6) | The §13 gate is `./gradlew check` run from `apps/wc-api/` (composite root exposes only aggregate check/build) — not the generic per-task `/preflight` list, not `build -x test` (jacoco verification needs tests). |
| 7 | 2026-06-02 | [JPA entity mapping conventions](LESSONS.md#7) | Pick the entity base by row shape (`PersistableUuidEntity` mutable+versioned / `AbstractAuditingEntity`+inline `@Id` audited / inline `@Id`+own-timestamp minimal); map every FK as a flat `UUID` (never `@ManyToOne`); entities mirror DDL type/nullability while `varchar` length caps stay in DTO validation. |
| 8 | 2026-06-02 | [Hibernate-6 non-scalar mappings](LESSONS.md#8) | `text[]`→`List<enum>` via `@JdbcTypeCode(SqlTypes.ARRAY)`+`@Enumerated(STRING)`+`columnDefinition="text[]"`; `jsonb`→`String` via `@JdbcTypeCode(SqlTypes.JSON)` (needs `hibernate-core` on the entity module's compile path — use `starter-data-jpa`, not bare `spring-data-jpa`); prove each with a round-trip test, compare jsonb structurally. |
| 9 | 2026-06-02 | [`@DataJpaTest` fidelity harness](LESSONS.md#9) | Prove entity↔DDL fidelity with `@DataJpaTest`+`@DynamicPropertySource` singleton PG16+Flyway+`ddl-auto=validate`; add explicit `@EntityScan`/`@EnableJpaRepositories` for cross-module sliced tests; a JPA starter on a shared lib activates DataSource autoconfig in every downstream module → `spring.autoconfigure.exclude` it on DB-less skeleton boots. |
| 10 | 2026-06-02 | [SpotBugs EI/EI2 on Lombok mutable collections](LESSONS.md#10) | For mutable-collection fields on Lombok entities, hand-write defensive-copy getters/setters — `EI_EXPOSE_REP`/`EI2` are NOT covered by the `@lombok.Generated` skip; field-access JPA makes the copies harmless to persistence. |
| 11 | 2026-06-02 | [Spring Data repo-layer finders + `@DataJpaTest` proofs](LESSONS.md#11) | Back partial-unique lookups with derived-name finders returning `Optional` (test the empty branch); prove repo-layer invariants under `@DataJpaTest` with `saveAndFlush` (coexistence-first/violation-last), assert Spring `ObjectOptimisticLockingFailureException` for `@Version` conflicts, and fire a set-valued partial unique across its status set (OPEN+IC_RESPONDED), not a same-value duplicate. |
| 12 | 2026-06-02 | [SS6 OAuth2 resource-server JWT decoder](LESSONS.md#12) | `withIssuerLocation`(EAGER OIDC discovery)+`jwsAlgorithm(RS256)`+`DelegatingOAuth2TokenValidator`(default-with-issuer + custom `.contains()`-audience validator), fail-fast on blank config; test via `withPublicKey` decoder wired to the SAME production validator + Nimbus tokens + MockWebServer discovery stub; a Security starter needs BOTH `autoconfigure.exclude` AND a separate gate for component-scanned `@Configuration`; fail-secure `@ConditionalOnProperty(demo-auth.enabled, havingValue=false, matchIfMissing=true)`. |
| 13 | 2026-06-02 | [Config-driven claim mapper + all-profile-binding gotcha](LESSONS.md#13) | Bind claim *names* via `@ConfigurationProperties` (nothing hardcoded) + `sub` fallback + validate-when-present/tolerate-absent-or-blank role parsing (reject non-blank-invalid as a generic `OAuth2AuthenticationException`→401, null on absent/blank — no silent default, no value leak); an always-present `@Component` injecting a `@ConfigurationProperties` bean forces all-profile binding, so every `${...}` default in base yaml needs a resolvable fallback (`${ROOT_DOMAIN:localhost}`) or skeleton boots break. |
| 14 | 2026-06-02 | [Backdoor-control filter (rule #5) + AuditService + DB-backed boots](LESSONS.md#14) | An env-gated backdoor filter runs in ALL modes to *reject* the disabled header (never gated out), rejects the untrusted value WITHOUT a DB query (`verifyNoInteractions`); disabled+header → 403+one-safe-audit, enabled+unknown → convergent IDOR-safe 401 (no audit); register it as a plain-class `@Bean` not `@Component` (double-registration); central `AuditService` is safe-metadata-only (SENTINEL-pinned, never echo untrusted input; denial audits need `REQUIRES_NEW`); once an always-scanned DB-dependent `@Service` lands, migrate the DB-less boots to a shared Testcontainers PG (Option A), don't accrete mocks. |
| 15 | 2026-06-02 | [Relationship-driven principal resolution](LESSONS.md#15) | Resolve identity→`UserPrincipal` with the authoritative `Employee.role` (never the nullable JWT role hint) + relationship-driven `isManager` (`existsByManagerEmployeeIdAndActiveTrue`, never the role claim — MANAGER-role-but-no-active-report ⇒ `isManager=false`); identity→no-`Employee` ⇒ IDOR-safe `Optional.empty()`(→401 at the 2.6 chain, no existence leak / no further DB probe via `verifyNoInteractions`); keep resolution separate from authorization (2.5); `implements` Spring `AuthenticatedPrincipal` so `authentication.getName()`=employeeId; `SystemPrincipal` = distinct no-HTTP boundary singleton. |
| 16 | 2026-06-02 | [Framework-interface name shadow fails SpotBugs](LESSONS.md#16) | A domain type implementing a framework interface must not shadow its simple name — `NM_SAME_SIMPLE_NAME_AS_INTERFACE` (SpotBugs MAX/MEDIUM) fails `check` *even via FQN-`implements`* (the shadow is the finding, not the import); rename distinctly (`UserPrincipal`, not `AuthenticatedPrincipal`), never suppress on production code. |
| 17 | 2026-06-02 | [Central domain-authorization service (rule #3, IDOR-safe)](LESSONS.md#17) | One central `@Service` authorizes every resource access (controllers coarse-only); cross-owner/cross-team/missing → IDOR-safe `404`, capability/role → `403`+named code; manager scope = active `manager_relationship` (owner short-circuits as IC); each genuine denial writes one safe-metadata `audit_event` in `REQUIRES_NEW` (survives rollback), genuinely-missing → 404 no-audit; resolve owners via flat-FK `findById` chains; gate the SYSTEM exemption behind a sealed-marker `instanceof` a request-built principal can't satisfy. |
| 18 | 2026-06-02 | [Build-and-throw deny helpers + escaped-JSON audit metadata](LESSONS.md#18) | Have deny/error helpers **build-and-return** the exception (`throw` at the call site) to dodge the JaCoCo always-throwing-helper coverage artifact; build any audit/log JSON via an **escaped JSON node** (Jackson `ObjectNode`), never string-concat, so rule-#7 safety is structural not caller-dependent (pairs w/ §14). |
| 19 | 2026-06-03 | [SS6 per-mode SecurityFilterChain wiring](LESSONS.md#19) | One `@ConditionalOnProperty(demo-auth.enabled)` chain per mode (mutually exclusive → bearer-XOR-demo structural; DemoAuthFilter = real-mode backdoor rejector); resolve every identity to a `UserPrincipal` both modes (JWT-converter + demo-filter-via-`PrincipalResolver`, empty→401); render RFC-7807 at **3 points** (entry-point 401 + access-denied 403 + `@RestControllerAdvice` 404/403+code/500 — chain-filter exceptions never hit the advice); prove custom decoder wins over autoconfig; **fail-fast on a non-boolean security gate** (malformed value silently falls to Boot's default chain). |
| 20 | 2026-06-03 | [Three SpotBugs/Jackson gotchas on the RFC-7807 path](LESSONS.md#20) | Body builder = pure static util (no stored mutable `ObjectMapper` → EI2); validating `@Configuration` needs `proxyBeanMethods=false`+`final` (→ CT_CONSTRUCTOR_THROW); flatten `ProblemDetail`'s custom props yourself on any **filter-level** (non-MVC) write — the flattening mixin isn't applied off the MVC path (else `safeMessage`/`code`/`traceId` nest under `properties`). |
| 21 | 2026-06-03 | [First controller + DTO (record-not-entity) + CORS via `http.cors()`](LESSONS.md#21) | First endpoint = thin `@RestController`→`@Service`→**DTO `record`** (entity never crosses the boundary — assert via a `.doesNotExist()` leak test); `/me`-style self endpoints authenticated-only (no authorizer call), all other resource endpoints call the authorizer first; wire CORS as a `CorsConfigurationSource` via `http.cors()` into every mode chain — exact-origin + `allowCredentials=false` + **mode-independent** (read only `app.cors.allowed-origins` so demo can't widen). |

<!-- Starts empty. Each row links to its `LESSONS.md` anchor. -->

<!-- Slash commands: see root CLAUDE.md "Slash commands available." Implementer pair: /session-start + /session-end. -->
