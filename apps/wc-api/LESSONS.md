# LESSONS.md — ST6 Weekly Commit Module (backend)

> Full prose for every lesson logged during work in `apps/wc-api/`. The compact index lives in `apps/wc-api/CLAUDE.md` "Lessons logged" table.
>
> **Lesson numbers are stable IDs.** New lessons get the next sequential number. Numbers may be referenced from code comments, commit messages, and cross-references between lessons. **Don't reorder; don't reuse a deleted number's slot.**
>
> **Lessons start at §1.** Each code area has its own lesson sequence — lessons don't carry across code areas.

---

## Lesson format

```markdown
## <a id="N"></a>N. <Short topic> — <one-line rule>

**Date:** YYYY-MM-DD.
**Source slice:** <slice-id or commit hash>.

<2-5 paragraphs explaining: what was discovered, why it matters, how to
apply the rule, what edge cases are still open. Cite file:line references
where applicable.>

**Rule:** <one-sentence summary, same as the heading subtitle>.
```

---

## <a id="1"></a>1. Gradle gate-wiring recipe — bind Spotless + SpotBugs + JaCoCo (≥80% line+branch) into `check` per module, prove the module boundary with a task, and resolve the toolchain via foojay + `JAVA_HOME` (never a committed machine path)

**Date:** 2026-06-02.
**Source slice:** 0.2 (gradle-multimodule).

The `apps/wc-api` multi-module build (task 0.2) established the reusable backend gate recipe that every later backend slice and the 0.8 CI workflow inherit:

- **Per-module gates bound into `check`.** Spotless (`google-java-format` + `removeUnusedImports` + `importOrder`), SpotBugs (effort `MAX`), and JaCoCo with `jacocoTestCoverageVerification` at **≥80% line + branch** are applied to `:shared`, `:api`, and `:worker`, and each verification task is wired so a plain `./gradlew check` runs all of them. **Verify the aggregation, not just the exit code:** a composite/aggregate `check` can silently omit a module's gates while still exiting 0 — pin it with a dry-run assertion that all `:<module>:<gate-task>` (9 = 3 gates × 3 modules) are scheduled (`./gradlew check --dry-run --console=plain`, suffix-match the task paths so the assertion survives the composite's `:wc-api:` prefix).
- **Mechanical forbidden-pattern gate.** `forbidLombokData` is a tiny Gradle task (bound into `check`) that fails if `@Data` / `import lombok.Data` appears in `src/main/java` — mechanically enforcing the no-`@Data` rule (root + area `CLAUDE.md` forbidden pattern #2). **The comment false-positive predicted here was hit in 0.3:** the first real `@Getter/@Setter` base class documented "never `@Data`" in its javadoc and tripped the raw grep. Fix applied: **strip block + line comments before matching** (a `@Data` in code/imports is still caught; the `@Data`-negative gate still passes). Lesson for any grep-based source gate: scan code, not comments.
- **Module-boundary proof (REQ-O-016).** `checkModuleBoundaries` inspects each module's declared `ProjectDependency`s and fails on a forbidden `api↔worker` edge — proving `shared←api`, `shared←worker` structurally rather than by convention. Cheaper than ArchUnit for project-level rules (ArchUnit is for package-level rules in a later phase).
- **Toolchain resolution is portable, never machine-pinned in a committed file.** Apply the `org.gradle.toolchains.foojay-resolver-convention` settings plugin + rely on `JAVA_HOME` (JDK 21). Do **NOT** commit `org.gradle.java.installations.paths=/opt/homebrew/...` — it's machine-specific and breaks CI/other devs; machine pins belong in an uncommitted `~/.gradle/gradle.properties`. CI provisions JDK 21 via `actions/setup-java`. See `docs/runbooks/jdk21-toolchain-setup.md`.
- **Repo-root composite.** Root `settings.gradle` does `includeBuild('apps/wc-api')`; root `build.gradle` aggregates `check`/`build` onto the included build (whose root `check` is wired to depend on each module's `check`). Wrappers are duplicated at the repo root *and* `apps/wc-api` (same Gradle 8.10.2, final release + `distributionSha256Sum`) so `apps/wc-api` stays independently buildable per the area `CLAUDE.md` "Standard commands."
- **Confirmed:** `google-java-format` runs cleanly on **JDK 21** with no `--add-exports` workaround needed (the earlier risk that it might require a palantir-java-format fallback is closed).

**Rule:** Bind Spotless + SpotBugs + JaCoCo (≥80% line+branch) into `check` per module, assert the aggregation (all gate tasks scheduled), prove the module boundary with a Gradle task, and resolve the JDK 21 toolchain via foojay + `JAVA_HOME` — never a committed machine path.

---

## <a id="2"></a>2. SpotBugs `Confidence` in a Groovy build script — use `Confidence.valueOf('MEDIUM')`, not `Confidence.MEDIUM`

**Date:** 2026-06-02.
**Source slice:** 0.2 (gradle-multimodule).

Configuring the SpotBugs Gradle extension in a Groovy `build.gradle`, `reportLevel = Confidence.MEDIUM` failed at configuration time with "using an instance of type java.lang.Class". Cause: SpotBugs' `Confidence` is a Kotlin enum with **per-constant bodies**, which compiles each constant to a nested subclass (`Confidence$MEDIUM`). Groovy's member resolution picks up the **nested class** for `Confidence.MEDIUM` rather than the enum field, so the assignment gets a `Class` instead of an enum instance.

Fix: reference the constant via the factory method — `reportLevel = Confidence.valueOf('MEDIUM')`. (The SpotBugs `Effort` type is a plain enum without per-constant bodies, so `Effort.MAX` works directly — only `Confidence` is affected.) Cost one GREEN iteration in 0.2; flagging so later SpotBugs config tweaks don't repeat it.

**Rule:** For Kotlin enums with per-constant bodies referenced from Groovy (e.g. SpotBugs `Confidence`), use `Type.valueOf('NAME')`, not `Type.NAME`.

---

## <a id="3"></a>3. Enum ↔ contract pinning — assert each enum's exact value set with a parameterized test + `valueOf` drift-trap negatives

**Date:** 2026-06-02.
**Source slice:** 0.3 (shared-enums-common).

The 16 `shared/enums/` enums are the executable mirror of the Appendix A / Appendix B.1 wire vocabulary and (Phase 1) the DB `VARCHAR`+`CHECK` columns (REQ-D-010). To pin that contract cheaply and exhaustively, `EnumVocabularyTest` uses a **parameterized test** (`@MethodSource` supplying `(enumClass, expectedNameSet)` for all 16) asserting `values()` collected to a name-set equals the expected set — count + spelling, in one place. Adding a 17th enum = one `@MethodSource` row.

Pair it with **targeted `valueOf` negatives** for the high-risk drift points — `assertThrows(IllegalArgumentException, () -> ReviewStatus.valueOf("OVERDUE"))`, same for `SyncRelatedType.valueOf("MANAGER_REVIEW")` — which lock the three traps that bite hardest (`ReviewStatus` has no stored `OVERDUE` (it's derived, §3); `CommentTargetType` is exactly `{PLAN,COMMITMENT}` (§11 flat comments); `SyncRelatedType` uses `MANAGER_REVIEW_WEEK` (§10 per-manager/week key)). A positive `valueOf` of the correct name guards against accidental rename.

**Phase 1 reuse:** when entities + Flyway land, extend the same pattern to assert each enum's value set equals the DB `CHECK`-constraint allowed list (read the constraint, compare to `values()`), so the Java enum and the SQL `CHECK` can't silently diverge.

**Rule:** Pin an enum-to-contract invariant with a parameterized value-set test over all enums plus `valueOf` drift-trap negatives for the high-risk constants — not ad-hoc per-enum assertions.

---

## <a id="4"></a>4. Spring Boot app-skeleton pattern — probe groups, context-load coverage, `*Application` JaCoCo exclude, a reusable env fail-safe, and boot-free property-resolution tests

**Date:** 2026-06-02.
**Source slice:** 0.4+0.5 (spring-boot-apps).

Standing up `WcApiApplication` + `WcSyncWorkerApplication` established the reusable Spring Boot skeleton pattern (Phase 1+ apps, the D.4 CronJob, and the D.5 Migration Job reuse it):

- **k8s probe endpoints:** set `management.endpoint.health.probes.enabled=true` (+ `management.endpoints.web.exposure.include: health`) in base config so `/actuator/health/{liveness,readiness}` exist in **all** profiles (not only when a k8s environment is auto-detected). A non-web app that still needs HTTP probes (the worker) carries `spring-boot-starter-web` purely for the management server — no controllers.
- **Coverage of a bootable app:** a `@SpringBootTest` context-load test covers the `@SpringBootApplication`/`@Configuration` classes, but the `static main()` is bootstrap-only and stays uncovered — so **exclude `**/*Application.class` from JaCoCo** rather than chase its coverage. Keep the module bundle non-empty with a *real* covered class: if a module would otherwise only contain its excluded `*Application`, give it a genuine small `@Configuration` (e.g. the worker's `WorkerSharedConfig` `@Import(ClockConfig)`) — covered by the context-load test — instead of a throwaway marker or weakening the gate.
- **Cross-package bean wiring:** a bean in a sibling package outside an app's component-scan root (here `com.st6.wc.config.ClockConfig` vs the worker at `com.st6.wc.worker`) needs an explicit `@Import` (on a dedicated `@Configuration`), not reliance on the default scan.
- **Reusable env fail-safe:** make environment-driven fail-safes (e.g. `ORG_TIMEZONE` ⇒ valid `ZoneId` else `America/Chicago` + WARN, never UTC/crash, D.6) a **static, context-free method** (`OrgTimeBindingConfig.resolveZone`) so it's unit-testable in isolation and reusable across the api + the CronJob + the Migration Job with no duplication. Drive it from `${ENV:}` blank-passthrough config so the resolver is the single source of truth.
- **Boot-free profile assertions:** to assert a property resolves differently per profile (e.g. `spring.flyway.enabled` false except `flyway-migrate`) without a full app boot, use `ApplicationContextRunner` + `ConfigDataApplicationContextInitializer`. **Gotcha:** `ConfigDataApplicationContextInitializer` lives in `org.springframework.boot.test.context` (the spring-boot-test jar), NOT `org.springframework.boot.context.config` — cost one GREEN iteration.

**Rule:** Spring Boot skeletons — enable probe groups in base config, cover via `@SpringBootTest` context-load + exclude `*Application` from JaCoCo (keep the bundle non-empty with a real config class), make env fail-safes static/reusable, and assert per-profile properties with `ApplicationContextRunner` + `ConfigDataApplicationContextInitializer` (from `o.s.boot.test.context`).

---

## <a id="5"></a>5. Flyway + Testcontainers PG16 migration harness — pin `testcontainers-bom:1.21.4` over the Spring Boot 3.3.5 BOM, migrate via the Flyway API, and pin enum↔CHECK by parsing `pg_get_constraintdef`

**Date:** 2026-06-02.
**Source slice:** 1.2 (flyway-v1-core-schema).

The reusable harness for testing Flyway migrations against real PostgreSQL 16 (never H2, §17) — reused by 1.3/1.4, every later migration, and 0.8 CI:

- **⚠ Pin `org.testcontainers:testcontainers-bom:1.21.4` (testImplementation `platform`) — overriding the Spring Boot 3.3.5 BOM's 1.19.8.** Testcontainers 1.19.8 ships an old docker-java that **400s on the Docker Engine 29 API** (`/info` → BadRequest) — the `docker` CLI works (it negotiates the API version) but docker-java does not. 1.21.4 (docker-java 3.4.2) fixes it; forward-compatible and harmless on older CI Docker. **Any Testcontainers test needs this override until the Spring Boot BOM catches up.**
- **Migrate via the Flyway API, not Spring autoconfig:** `Flyway.configure().dataSource(container.getJdbcUrl(), user, pw).locations("classpath:db/migration").load().migrate()` in `@BeforeAll`, one shared `@Container` per test class — no Spring Boot app needed (migrations live in `:shared`, a plain library). Deps (testImpl, BOM-managed): `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql`, `org.testcontainers:postgresql` + `:junit-jupiter`. Pin the PG image (`postgres:16.13`, ≥16.13 per §4).
- **Pin enum↔CHECK at the DB layer** (extends §3 to SQL): a parameterized test reads each status column's `CHECK` via `pg_get_constraintdef`, extracts the allowed literals, and asserts they equal the corresponding `enum.values()` — over **all** status columns. This is the executable guard that the Java enum and the SQL `CHECK` can't silently diverge (REQ-D-010). Assert structural violations by **SQLSTATE** (`23514` check, `23505` unique, `23503` FK) — precise and driver-agnostic.
- **SpotBugs:** a JDBC test harness that builds SQL from test-controlled UUIDs/literals trips `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE`; suppress it **narrowly** (the migration test package only, in `config/spotbugs/exclude.xml`) — never broaden the exclusion to production code.
- **Testing *partial* unique indexes (1.3):** assert **both** the firing case (a dup inside the `WHERE` predicate → `23505`) AND the **non-firing / partial-scope** case (a row outside the predicate coexists freely) — the partial scope IS the point, so a test that only checks firing is half a test. When a partial unique overlaps a broader full unique on the same table, **isolate the partial** by varying a column the full unique keys on but the partial doesn't (e.g. the V2 review-block partial on `(owner, week_start_date)` was tested with a *distinct* `related_id` so the V1 full `(owner, related_type, related_id, event_kind)` unique couldn't fire first and mask it).
- **`text[]` enumerated-vocabulary columns (1.4):** constrain an array column to an enum vocabulary at the DB layer with a containment CHECK — `CHECK (col <@ ARRAY['A','B',...]::text[])` — and **pin it to `Enum.values()`** with the same `pg_get_constraintdef`-parsing approach as the scalar enum↔CHECK test. Test the array round-trip, the `'{}'` default, and an out-of-vocab element (→ `23514`). (e.g. `manager_heatmap_cell.risk_badges` ↔ `RiskBadge`.)
- **Version-scope every migration test (1.4):** a migration test that migrates the whole `db/migration` dir asserts against the *cumulative* schema, so a later migration silently breaks a prior test's assertions (V3 broke V1's "projection tables absent" check). Pin each test to **its own version**: `Flyway.configure()...target(MigrationVersion.fromVersion("N")).load().migrate()` — each test then validates exactly its version's state and is immune to later migrations (incl. the V4–V6 seed migrations). Apply retroactively when a new migration lands.

**Rule:** For Flyway-migration tests, pin `testcontainers-bom:1.21.4` (Docker Engine 29 compat), migrate via the Flyway API on a shared Testcontainers PG16 container, and pin enum↔CHECK by parsing `pg_get_constraintdef` against `enum.values()`.

---

## <a id="6"></a>6. Backend gate is `./gradlew check` from `apps/wc-api/` — not the generic `/preflight` step list, not `build -x test`

**Date:** 2026-06-02.
**Source slice:** 1.2 / session-001 close-out.

The generic `/preflight` skill's backend steps don't fit this project's Gradle shape, surfaced at the session-001 close-out:

- **Run from `apps/wc-api/`.** The repo-root composite build (`includeBuild('apps/wc-api')`, rootProject `st6-wc`) only exposes the **aggregate** `check`/`build` tasks — the per-task targets (`compileJava`, `spotbugsMain`, `spotlessCheck`, `test`, `jacocoTestCoverageVerification`) live inside the `apps/wc-api` build, not at the composite root. A bare `./gradlew spotbugsMain` from the repo root fails ("task not found").
- **`./gradlew check` IS the gate** (matches §13 CI): it runs, per module, Spotless (google-java-format) + SpotBugs (MAX) + JaCoCo ≥80% line+branch verification + `forbidLombokData` + `checkModuleBoundaries` + all tests. That single task is the authoritative "is it green" gate.
- **`build -x test` is NOT a valid gate here.** `jacocoTestCoverageVerification` is bound into `check` and cannot verify coverage without the tests having run, so skipping tests defeats the gate. (Also: a JaCoCo `classDirectories` reconfiguration must base on `output.classesDirs` + `dependsOn classes`, else `build -x test` fails on an undeclared class-producer dependency — fixed in `1a34f3b`.)

**Rule:** The backend quality gate is `./gradlew check` run from `apps/wc-api/` (the §13 CI gate) — never the generic per-task `/preflight` list and never `build -x test`.

---

## <a id="7"></a>7. JPA entity mapping conventions — three base-class shapes, flat-`UUID` FKs (never `@ManyToOne`), and entities mirror type/nullability but NOT `varchar` length caps

**Date:** 2026-06-02.
**Source slice:** 1.5 (jpa-entities-appendix-a).

The 14 Appendix-A entities established the project's entity-mapping idiom. The DDL has **three distinct row shapes**, so there is no single base class for everything — match the shape:

- **(a) 5 mutable lifecycle entities** (`WeeklyPlan`, `WeeklyCommitment`, `ManagerReview`, `AlignmentDispute`, `OutlookCalendarSyncRecord`) — id + `@Version` + audit quartet → **`extends PersistableUuidEntity`** (the 0.3 base, which is `PersistableUuidEntity extends AbstractAuditingEntity`).
- **(b) 6 audited-but-non-versioned** (`Employee`, `ManagerRelationship`, `RallyCry`, `DefiningObjective`, `SupportingOutcome`, `Comment`) — audit quartet, **no `version` column** → **`extends AbstractAuditingEntity` + declare an inline `@Id UUID id`**. They must NOT extend `PersistableUuidEntity` or Hibernate maps a `version` column that doesn't exist on their table → `ddl-auto=validate` failure.
- **(c) 3 minimal** (`AuditEvent`, `ManagerPlanSummary`, `ManagerHeatmapCell`) — own single timestamp only (`created_at` for audit append-only; `updated_at` for the recomputed projections), **no audit quartet, no version** → **inline `@Id UUID id` + own timestamp field, no base class.**

Do **not** refactor the landed 0.3 base classes to "DRY up" the inline `@Id` — the duplication is a 3-line idiom and the bases are a contract surface (Appendix C.2).

- **Flat `UUID` foreign keys, never `@ManyToOne`/`@OneToMany`.** Every FK is a plain `UUID` field (`employeeId`, `weeklyPlanId`, `supportingOutcomeId`, the `carryForwardSourceCommitmentId` self-ref, …). Rationale: §8 services compose via repositories; entities never cross the API boundary (DTOs do, Appendix B / forbidden-pattern #3); flat entities avoid Hibernate lazy-init/N+1 footguns. DB-level FK integrity is already enforced by V1.
- **Entities mirror the DDL column's *type and nullability*, but NOT its `varchar(N)` length cap.** Length caps are a request-DTO Jakarta-Bean-Validation concern (Appendix E Part 1), not an entity concern — an over-long string is rejected at the API boundary, not by the entity. So a `varchar(255)` column maps to a plain `String` field with no `@Size`/`@Column(length=…)` ceiling enforced here. Keeps the entity layer a pure structural mirror and the validation layer the single source of input bounds.

**Rule:** Pick the entity base by row shape — `PersistableUuidEntity` (mutable+versioned) vs `AbstractAuditingEntity`+inline `@Id` (audited, no version) vs inline `@Id`+own-timestamp (minimal); map every FK as a flat `UUID` (never `@ManyToOne`); and let entities mirror DDL type/nullability while `varchar` length caps stay in DTO validation.

---

## <a id="8"></a>8. Hibernate-6 non-scalar column mappings — `text[]`→`List<enum>` via `@JdbcTypeCode(SqlTypes.ARRAY)`, `jsonb`→`String` via `@JdbcTypeCode(SqlTypes.JSON)`

**Date:** 2026-06-02.
**Source slice:** 1.5 (jpa-entities-appendix-a).

Two PostgreSQL columns need non-scalar mappings that Spring Data alone can't provide — they require **`hibernate-core`** annotations (`org.hibernate.annotations.JdbcTypeCode` + `org.hibernate.type.SqlTypes`), which means the entity module (`:shared`) needs a Hibernate provider on its **compile** classpath, not just `jakarta.persistence-api`. Use `spring-boot-starter-data-jpa` (BOM-aligned `hibernate-core` + `spring-data-jpa`) rather than bare `spring-data-jpa` (which omits `hibernate-core`).

- **`risk_badges text[]` → `List<RiskBadge>`:** `@JdbcTypeCode(SqlTypes.ARRAY) @Enumerated(EnumType.STRING) @Column(name = "risk_badges", columnDefinition = "text[]") private List<RiskBadge> riskBadges;`. The DB `<@` containment CHECK (V3) stays the vocabulary guard; the mapping is type-safety + round-trip.
- **`metadata_json jsonb` → `String`:** `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "metadata_json") private String metadataJson;`. Keep it an opaque `String` — the safe-only-content rule (safety rule #7) is enforced by `AuditService` at write time, not the entity.
- **`@Enumerated(STRING)` over CHECK-less projection mirrors** (`manager_plan_summary.plan_state`/`review_status` are denormalized VARCHARs with no CHECK) is still correct + `validate`-clean — `ddl-auto=validate` checks column type, not the presence of a CHECK; the projection only ever holds values the source-of-truth column already constrained.
- **Confirm the exact incantation against the live Hibernate 6.x docs** (Context7) when adding a new array/json mapping — the annotation package + `SqlTypes` constant names have moved across Hibernate 5→6.

**Verification:** prove each non-scalar mapping with an explicit **round-trip** test (store a non-null value → flush+clear → reload → assert), not just `ddl-auto=validate` (which only proves column-type compatibility, not store/retrieve). For `jsonb`, assert with a **single-key payload or parse-and-compare** — jsonb does not preserve key order/whitespace, so raw multi-key string equality flakes.

**Rule:** Map `text[]`→`List<enum>` with `@JdbcTypeCode(SqlTypes.ARRAY)`+`@Enumerated(STRING)`+`columnDefinition="text[]"` and `jsonb`→`String` with `@JdbcTypeCode(SqlTypes.JSON)` (needs `hibernate-core` on the entity module's compile path); prove each with a round-trip test, and compare jsonb structurally not by raw string.

---

## <a id="9"></a>9. `@DataJpaTest` entity↔DDL fidelity harness — singleton PG16 via `@DynamicPropertySource` + Flyway + `ddl-auto=validate`; sliced tests need explicit `@EntityScan`; a JPA starter on a shared lib activates DataSource autoconfig everywhere downstream

**Date:** 2026-06-02.
**Source slice:** 1.5 (jpa-entities-appendix-a).

The reusable harness that proves JPA entities match the Flyway-migrated schema (reused by 1.6 + every later persistence slice):

- **`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + a singleton `PostgreSQLContainer` wired via `@DynamicPropertySource`** (not `@ServiceConnection` — avoids the extra `spring-boot-testcontainers` module and stays consistent with the landed 1.2–1.4 raw-Testcontainers migration tests). Run Flyway V1–V3, then set **`spring.jpa.hibernate.ddl-auto=validate`** so Hibernate's own schema validation is the strongest entity↔DDL fidelity proof (every entity's column existence + type is checked at context boot). The 1.21.4 Testcontainers BOM override (LESSONS §5) still governs.
- **A *sliced* `@DataJpaTest` needs explicit `@EntityScan(...)` + `@EnableJpaRepositories("com.st6.wc")` on the base** when entities live in a *different module* (`:shared`) than the test (`:api`). A full `@SpringBootTest` inherits the scan root from the located `@SpringBootApplication` (here `WcApiApplication` at `com.st6.wc`), so the "no `@EntityScan` needed" property holds **only** for the full context — the test slice does not auto-discover cross-module entities without the explicit annotations.
- **⚠ Adding `spring-boot-starter-data-jpa` to a shared library activates `DataSourceAutoConfiguration` in EVERY downstream module's full-context `@SpringBootTest`.** The `:shared` starter propagates transitively to `:api` AND `:worker`, so the **3** DB-less skeleton boot tests (`WcApiApplicationTest`, `WcApiDemoBootTest`, `WcSyncWorkerApplicationTest`) start failing context-load (no DataSource, H2 banned). Fix: a **test-local `spring.autoconfigure.exclude`** of `DataSourceAutoConfiguration` + `HibernateJpaAutoConfiguration` on exactly those probe/clock/profile tests (they test no persistence). Flyway/JpaRepositories/transaction-manager autoconfig are `@ConditionalOnBean(DataSource)`, so excluding the DataSource cascade-disables them. Keep the exclude **test-local** — never in any main `application*.yml`.

**Rule:** Prove entity↔DDL fidelity with a `@DataJpaTest` + `@DynamicPropertySource` singleton PG16 + Flyway + `ddl-auto=validate` harness; add explicit `@EntityScan`/`@EnableJpaRepositories` for cross-module sliced tests; and when a JPA starter lands on a shared lib, `spring.autoconfigure.exclude` DataSource+JPA autoconfig on every downstream DB-less skeleton boot test.

---

## <a id="10"></a>10. SpotBugs EI_EXPOSE_REP/EI_EXPOSE_REP2 fires on Lombok `@Getter`/`@Setter` for mutable-collection entity fields — the `@lombok.Generated` skip does NOT cover EI/EI2

**Date:** 2026-06-02.
**Source slice:** 1.5 (jpa-entities-appendix-a).

`config/spotbugs/exclude.xml` assumes Lombok-generated members are auto-skipped (they carry `@lombok.Generated`), but that skip does **not** apply to the `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` detectors — SpotBugs (effort MAX) still flags a Lombok `@Getter` returning, or `@Setter` storing, a **mutable collection** field by reference (it sees the generated accessor as exposing internal representation). Hit on `ManagerHeatmapCell.riskBadges` (`List<RiskBadge>`).

Fix applied in-slice: **hand-write defensive-copy accessors** for the mutable-collection fields (`return new ArrayList<>(riskBadges)` / `this.riskBadges = new ArrayList<>(value)`) instead of letting Lombok generate them. This is **safe for JPA** because Hibernate uses **field access** (not the property accessors) for persistence, so the defensive copies don't interfere with dirty-checking or load. Scalar/immutable fields keep their Lombok accessors. The `exclude.xml` comment claiming Lombok members are universally auto-skipped is now known-incomplete for mutable types — do not broaden the exclusion to silence EI/EI2; fix the accessor.

**Rule:** For mutable-collection fields on Lombok entities, hand-write defensive-copy getters/setters (SpotBugs `EI_EXPOSE_REP`/`EI2` are not covered by the `@lombok.Generated` skip); field-access JPA makes the copies harmless to persistence.

---

## <a id="11"></a>11. Spring Data repository layer — derived-`Optional` finders for partial-unique lookups, and the `@DataJpaTest` constraint / `@Version` test patterns

**Date:** 2026-06-02.
**Source slice:** 1.6 (repo-finders-and-constraint-proofs).

The repository layer over the 1.5 entities established two reusable patterns — the finder idiom and the repo-layer invariant-proof test recipe (which differs subtly but importantly from the raw-SQL migration tests of §5):

- **Derived-name finders returning `Optional` for partial-unique-backed single lookups.** Where a partial unique guarantees ≤1 matching row, the finder returns `Optional<E>` and uses Spring Data's method-name DSL — no `@Query` needed: `ManagerRelationshipRepository.findByDirectReportEmployeeIdAndActiveTrue(UUID)` (single active manager, §6), `AlignmentDisputeRepository.findByCommitmentIdAndStatusIn(UUID, Collection<DisputeStatus>)` (callers pass `{OPEN, IC_RESPONDED}`), `OutlookCalendarSyncRecordRepository.findByOwnerEmployeeIdAndWeekStartDateAndEventKind(UUID, LocalDate, EventKind)`. The `Optional`-empty branch is real (e.g. `DomainAuthorizationService` denies when there is no active manager) — test it, not just the present path.

- **`@DataJpaTest` repo-layer constraint + `@Version` proof recipe** (reuses the §9 `AbstractJpaIntegrationTest` harness; complements, does not replace, the §5 raw-SQL migration tests — this layer proves the constraint surfaces as the **Spring exception** Phase 2+ services catch):
  - **Use `saveAndFlush`, never plain `save`.** Under the `@DataJpaTest` rollback-only transaction, a plain `save` defers the INSERT/UPDATE to a commit that never happens, so the DB constraint never fires. `saveAndFlush` forces the SQL now.
  - **Assert coexistence FIRST, the violation LAST.** A `DataIntegrityViolationException` on `saveAndFlush` marks the transaction rollback-only, so **no further DB op can run in the same test method** (the next query/save throws a different exception). Persist the coexisting rows + assert `count()` first, then trigger the violating `saveAndFlush` as the final `assertThatThrownBy`.
  - **Optimistic-lock conflict in a single transaction:** persist+flush (v0) → `em.clear()` → load `stale` + `em.detach(stale)` → load `fresh` → **mutate a field on `fresh`** (an unmodified managed flush is a no-op — no version bump) → `saveAndFlush(fresh)` bumps the DB to v1 → `saveAndFlush(stale)` (still v0) merges against v1 → conflict. **Assert the Spring translation `org.springframework.orm.ObjectOptimisticLockingFailureException`**, not Hibernate's `StaleObjectStateException` (services catch the Spring type → the §5/409 conflict). Proven deterministic in 1.6; a `TransactionTemplate` REQUIRES_NEW two-transaction variant is the fallback if a single-tx merge ever flakes (unique-UUID committed rows are harmless in the singleton container).
  - **Prove a set-valued partial unique across the SET, not a same-value duplicate.** The unresolved-dispute index is `(commitment_id) WHERE status IN ('OPEN','IC_RESPONDED')` — safety rule #6. Test the firing case with **OPEN + IC_RESPONDED** (the two distinct unresolved statuses), not OPEN + OPEN: a same-status duplicate would still pass if someone accidentally narrowed the predicate to `status='OPEN'`, silently breaking the rule; the cross-status case pins the actual invariant (the predicate treats both statuses as one uniqueness bucket) and catches that regression.

**Rule:** Back partial-unique lookups with derived-name finders returning `Optional` (test the empty branch); prove repo-layer invariants under `@DataJpaTest` with `saveAndFlush` (coexistence-first / violation-last), assert the Spring `ObjectOptimisticLockingFailureException` for `@Version` conflicts, and fire a set-valued partial unique across its status set (OPEN+IC_RESPONDED), not a same-value duplicate.

---

## <a id="12"></a>12. Spring Security 6 OAuth2 resource-server JWT decoder — recipe, EAGER-vs-lazy discovery, autoconfig-exclude ≠ component-scan suppression, and fail-secure mode gating

**Date:** 2026-06-02.
**Source slice:** 2.1 (auth0-jwt-decoder).

The Auth0 resource-server JWT validation foundation (§6) established the reusable Spring Security 6 (Boot 3.3) decoder pattern + three non-obvious gotchas that Phase-2 (2.2/2.3/2.6) and any future resource-server work inherit:

- **Decoder recipe.** `NimbusJwtDecoder.withIssuerLocation(issuer).jwsAlgorithm(SignatureAlgorithm.RS256).build()` then `decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), new AudienceValidator(audience)))`. Pinning `jwsAlgorithm(RS256)` rejects `alg=none` + symmetric (HS*). A **custom `AudienceValidator implements OAuth2TokenValidator<Jwt>`** is mandatory because **Auth0 does not validate `aud` by default** — `aud` must `.contains()` the configured audience (not `.equals()`, since Auth0 tokens carry multiple audiences). Issuer **and** audience are required in real mode; validate with `Assert.hasText(...)` at bean construction so a blank config **fails fast at startup** with a clear message (no silent insecure default). `SignatureAlgorithm` is `org.springframework.security.oauth2.jose.jws.SignatureAlgorithm`.

- **⚠ `withIssuerLocation` is EAGER, `withJwkSetUri` is LAZY.** `withIssuerLocation(issuer).build()` performs **eager OIDC discovery at bean-build** (fetches `{issuer}/.well-known/openid-configuration`), so bean creation couples to issuer reachability — a *desirable* fail-fast on issuer misconfig, and faithful to D.2's "defaults from issuer-uri discovery; override only if pinning." `withJwkSetUri(uri)` defers the JWKS fetch to first decode (lazy, decouples startup from the IdP) — the documented fallback if startup/IdP decoupling is ever needed. (Corrects an earlier mistaken "withIssuerLocation is lazy" assumption — only the JWKS leg under `withJwkSetUri` is lazy.)

- **Deterministic test harness (no live Auth0/JWKS).** Test the `AudienceValidator` as a pure unit. Build the test decoder with `NimbusJwtDecoder.withPublicKey(testRsaPublicKey).signatureAlgorithm(RS256)` wired with the **same** production `JwtConfig.jwtValidator(issuer, audience)` factory — so the test exercises the real validation chain (issuer + audience + exp/nbf), swapping only the network key source. Sign tokens with Nimbus (`com.nimbusds`, transitive via oauth2-jose): RS256 valid, HS256/`alg=none` rejected. For the **eager** `withIssuerLocation` build happy-path, stub the OIDC discovery endpoint with **MockWebServer** (`testImplementation 'com.squareup.okhttp3:mockwebserver'`, BOM-managed) — the stubbed `issuer` field must exactly match the configured issuer. Fail-fast-on-missing-config via `ApplicationContextRunner` (LESSONS §4).

- **autoconfig-exclude ≠ component-scan suppression (extends §9).** Adding `spring-boot-starter-oauth2-resource-server` (any Spring Security starter) activates the default security chain that secures *every* endpoint incl. actuator probes → breaks DB-less skeleton boot tests. `spring.autoconfigure.exclude` (`SecurityAutoConfiguration`, `OAuth2ResourceServerAutoConfiguration`, `UserDetailsServiceAutoConfiguration`, `ManagementWebSecurityAutoConfiguration`) kills the **autoconfig** — but a **component-scanned `@Configuration`** (like `JwtConfig`) is NOT an autoconfig and is **NOT** suppressed by the exclude; under a profile where its `@Conditional` is satisfied it stays active and its fail-fast still fires. The **complete** fix is dual: exclude the security autoconfig on the skeleton boots **and** gate the component-scanned config off (here via demo-mode). One mitigation alone is insufficient.

- **Fail-secure mode gating + canonical property.** Gate the real decoder with `@ConditionalOnProperty(name = "demo-auth.enabled", havingValue = "false", matchIfMissing = true)` — the **real/secure** decoder stays ON when the demo flag is false, absent, or *misspelled* (fail-secure: you can't accidentally disable real auth via a typo'd demo flag). Bind the canonical env var with an **explicit `${DEMO_AUTH_ENABLED:false}` placeholder** in `application.yml` (resolves by exact env name) — relaxed binding alone would target the dotted `demo.auth.enabled`, missing the kebab `demo-auth.enabled` property. **`demo-auth.enabled` is the single mode source shared by 2.1/2.3/2.6**; base/prod default = `false` (real mode, secure-by-default, fails closed), `local`/`demo` profiles set `true`.

**Rule:** SS6 resource-server JWT — `withIssuerLocation`(EAGER discovery)+`jwsAlgorithm(RS256)`+`DelegatingOAuth2TokenValidator`(default-with-issuer + a custom `.contains()`-audience validator), fail-fast on blank issuer/audience; test via a `withPublicKey` decoder wired to the *same* production validator + Nimbus tokens + a MockWebServer discovery stub; and remember a Security starter needs BOTH `autoconfigure.exclude` (autoconfig) AND a separate gate (component-scanned config), with fail-secure `@ConditionalOnProperty(havingValue=false, matchIfMissing=true)` on the canonical `demo-auth.enabled`.
