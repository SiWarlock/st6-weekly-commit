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

---

## <a id="13"></a>13. Config-driven Auth0 claim mapper + the always-present-`@Component`-forces-all-profile-config-binding gotcha

**Date:** 2026-06-02.
**Source slice:** 2.2 (auth0-claim-mapper).

The Auth0 claim mapper (Jwt → identity fields) established the config-driven mapping idiom + surfaced a profile-binding trap that pairs with §12:

- **Config-driven claim mapper.** Bind claim *names* (not values) via `@ConfigurationProperties` (a nested `Auth0Properties.Claims` record: `employeeId`/`role`/`email` claim-name strings) with the Appendix F.1 namespaced defaults — **nothing hardcoded** (REQ-S-009), so claim names are deployment-configurable. The mapper (`Auth0ClaimMapper`, a pure `@Component`) reads the configured names off a validated `Jwt`: `externalSubject` = the employee-id claim, **falling back to the standard `sub`** when absent/blank; `role` = the role claim parsed to the `RoleType` enum; `email` = the email claim. Output is a typed `Auth0Identity(externalSubject, role, email)` record. **No DB access** — Employee-row resolution is the next layer's job (2.4 `PrincipalResolver`); the JWT `role` is only a coarse team/heatmap hint (§6/F.1), the authoritative role + `isManager` are relationship-derived in 2.4.
- **Role parsing — validate-when-present / tolerate-absent-or-blank.** A **present, non-blank** role not in `{IC,MANAGER}` is rejected (no silent default, REQ-S-007) via `OAuth2AuthenticationException(new OAuth2Error("invalid_token", <generic msg>, null))` — which the resource server's `BearerTokenAuthenticationEntryPoint` auto-renders `401` (no custom mapping needed), and the **generic message must NOT echo the bad role value** (rule #7). An **absent OR blank** role → `null` (the mapper invents nothing; the authoritative role is the Employee row). Treat blank as absent — symmetric with the employee-id blank→`sub` fallback.
- **Test as a pure unit** with `Jwt.withTokenValue("t").claim(name, value).build()` fixtures — no decoder, no network. Prove no-hardcoding by mapping with overridden claim names; prove the `sub` fallback (absent + blank); prove present-invalid→throw and absent/blank→null; prove the mapper takes only `Auth0Properties` (no repo) for the no-DB-access contract.
- **⚠ Gotcha — an always-present `@Component` that injects a `@ConfigurationProperties` bean forces that config to bind in EVERY profile.** Because `Auth0ClaimMapper` is an ungated `@Component` constructor-injected with `Auth0Properties`, `Auth0Properties` must be a bound bean in every profile (incl. demo/local/CI) — so any `${...}`-interpolated default in the **base** `application.yml` needs a **resolvable placeholder default** or binding fails and the DB-less/IdP-less skeleton boots break. Here the F.1 claim defaults use `${ROOT_DOMAIN:localhost}` (e.g. `https://wc.${ROOT_DOMAIN:localhost}/employee_id`); prod's `ROOT_DOMAIN` env overrides, and the `:localhost` fallback claim-name is never used to validate a real token in those non-JWT modes — it just needs to *resolve* so binding succeeds. (Pairs with §12's autoconfig-exclude-≠-component-scan lesson — both are "what loads/binds in which profile" traps. Whenever you make a config bean always-present, audit its base-yaml placeholders for resolvable defaults.)

**Rule:** Bind claim *names* via `@ConfigurationProperties` (nothing hardcoded) + `sub` fallback + validate-when-present/tolerate-absent-or-blank role parsing (reject non-blank-invalid as a generic `OAuth2AuthenticationException`→401, null on absent/blank — no silent default, no value leak); and remember an always-present `@Component` injecting a `@ConfigurationProperties` bean forces all-profile binding, so every `${...}` default in base yaml needs a resolvable fallback (`${ROOT_DOMAIN:localhost}`) or skeleton boots break.

---

## <a id="14"></a>14. Env-gated backdoor-control filter (rule #5) + minimal `AuditService` (rule #7) + the DB-backed-app-boot migration (Option A)

**Date:** 2026-06-02.
**Source slice:** 2.3 (demo-auth-filter) — SAFETY-CRITICAL, rule #5.

The `DemoAuthFilter` (env-gated demo identity, the production-backdoor control) established the backdoor-control filter pattern + the central audit-write seed + forced the DB-backed-boot migration. Security-reviewer clean (0 critical / 0 high).

**Backdoor-control filter pattern (rule #5 / #7 / §5):**
- **The filter must run in ALL modes to *reject* the disabled header — never be gated out.** If it were only registered in demo mode, a demo header in real/prod would be silently ignored (no rejection, no audit) and the backdoor control would never fire. So it's always in the chain and **branches on the canonical `demo-auth.enabled` at request time**.
- **On the disabled path, reject the untrusted header WITHOUT a DB query** — read only its *presence*, never run the untrusted backdoor value through a repository (avoids enumeration/injection via the backdoor). Pinned with `verifyNoInteractions(employees)`.
- **Two distinct denial semantics:** (a) demo **disabled** + header present → **`403` + exactly one safe-metadata `audit_event`** (an active backdoor-attempt rejection, RISK-008); (b) demo **enabled** + unknown/blank/malformed id → **`401`, IDOR-safe, NO audit** (a normal failed auth — convergent generic response, no existence disclosure, no audit-spam). Don't conflate them.
- **Plain class, NOT `@Component`** — register it as a `@Bean` in the `SecurityConfig`/chain. A `@Component` servlet `Filter` is **double-registered** (auto-registered by the servlet container AND added to the `SecurityFilterChain` → runs twice). The filter **SETS** the `SecurityContext` but does **not** clear it — clearing is the chain's `SecurityContextHolderFilter` job (the wiring slice must confirm that filter is present, else thread-local bleed).

**Minimal central `AuditService` (rule #7 safe-metadata):** `record(action, entityType, entityId, actorEmployeeId, summary, metadataJson)` persisting one `AuditEvent` with **safe-only** content — nullable actor/entity-id, a generic summary, and a **constant** `metadata_json`; **NEVER echo untrusted input** (the disabled-mode header value). Pin it with a **SENTINEL** test: an injection-style value asserted absent from *every* recorded audit field. `created_at` from the injected `:shared` `Clock` (no JPA auditing populator yet). First audit event seeds the taxonomy: `action="DEMO_AUTH_REJECTED"`, `entityType="Authentication"` (a security-event type, not a domain entity that wasn't touched). **Forward (§15/2.5):** denial audits written *during a rolled-back mutation* must use `Propagation.REQUIRES_NEW` — default `REQUIRED` joins the caller's tx, so the audit would roll back with the denied mutation and lose the denial record.

**DB-backed-app-boot migration (Option A — pairs with §9/§12/§13 "what loads/binds in which profile"):** from Phase 2 the app is **DB-dependent in every mode** (demo resolves the demo id + writes audits; real resolves `external_subject` + writes denial audits). So when an always-component-scanned DB-dependent `@Service` lands (`AuditService` injects `AuditEventRepository`), the DB-less skeleton boots can't survive via `autoconfigure.exclude` (it's a component-scanned `@Service`, not an autoconfig). The **DB-less skeleton boot is outgrown** — migrate the app-boot tests to a **shared** Testcontainers PG16 (one container reused across JPA + boot tests via a `SharedPostgres` holder) + Flyway + `ddl-auto=validate`, keeping only the security-autoconfig exclude. Durable vs a growing `@MockBean`/exclude list, and the boots become realistic.

**Rule:** A backdoor-control filter runs in ALL modes to *reject* the disabled header (never gated out) and rejects the untrusted value WITHOUT a DB query; disabled+header → 403+one-safe-audit, enabled+unknown → convergent IDOR-safe 401 (no audit); register it as a plain-class `@Bean` (not `@Component`, to avoid double-registration); the central `AuditService` writes safe-metadata-only (SENTINEL-pinned, never echo untrusted input; denial audits need `REQUIRES_NEW`); and once an always-scanned DB-dependent `@Service` lands, migrate the DB-less skeleton boots to a shared Testcontainers PG (Option A), don't accrete mocks.

---

## <a id="15"></a>15. Relationship-driven principal resolution (identity → `UserPrincipal`, separate from authorization)

**Date:** 2026-06-02.
**Source slice:** 2.4 (principal-resolver).

`PrincipalResolver` maps an authenticated identity — **either** a validated-JWT `Auth0Identity` (2.2) **or** a demo `employeeId` (2.3) — to a resolved `UserPrincipal`, and is the single input the central `DomainAuthorizationService` (2.5) reads. The load-bearing discipline is to **separate identity resolution (identity → principal) from domain authorization (principal → resource access)** and to make the manager signal **relationship-driven, not role-driven**:

- **Authoritative `role` = the `Employee` DB row, never the JWT hint.** `Auth0Identity.role` is a *nullable coarse hint* (§6/F.1); once the resolver finds the `Employee` (by `externalSubject` in JWT mode, by `id` in demo mode), `principal.role = employee.role`. The JWT role is discarded — pinned by a test where the hint says `IC` but the row says `MANAGER` → principal is `MANAGER`. (Resolves 2.2's deferral: "the authoritative role is the Employee row in 2.4.")
- **`isManager` is relationship-driven** — `existsByManagerEmployeeIdAndActiveTrue(employee.id)` (the *inverse* of the existing report→manager finder `findByDirectReportEmployeeIdAndActiveTrue`), **never** the role claim. A `MANAGER`-role employee with **no active managed report** is `isManager=false` for relationship-gated reads. This is the load-bearing foundation for the rule-#3 IDOR authorizer (2.5) — manager scope flows from active relationships, not from a self-asserted token role.
- **Identity → no `Employee` → IDOR-safe `Optional.empty()`.** A valid identity that maps to no row resolves to `empty` with **no existence leak and no further DB probe on denial** (pin with `verifyNoInteractions(relationships)` — don't run the `isManager` lookup once the employee lookup misses). The resolver stays pure/unit-testable returning `Optional<UserPrincipal>`; the **2.6** filter chain translates `empty → 401`. This **pins the previously-unpinned authn-time status to 401** (an authenticated token that maps to no app user is *unauthenticated*, IDOR-safe).
- **`UserPrincipal implements org.springframework.security.core.AuthenticatedPrincipal`** with `getName()` = `employeeId.toString()` — so once 2.6 stuffs it into the `Authentication`, `AbstractAuthenticationToken.getName()` delegates to it and `authentication.getName()` resolves to the employee id (read by the §15 audit actor + request logging). The no-HTTP **`SystemPrincipal`** (singleton `INSTANCE`) is a *distinct* boundary type that deliberately does NOT implement `AuthenticatedPrincipal` — the SYSTEM actor for the CronJob/worker (Phase 8/10), exempt from self/direct-report checks.
- **Finders + tests:** back JWT-mode lookup with a derived `findByExternalSubject` (not unique-constrained → returns `Optional`) and `isManager` with the inverse `existsBy…ActiveTrue`; prove both under `@DataJpaTest` on PG16 (the active-scope branch — active=true vs inactive-only vs none — is load-bearing for rule #3). Resolver logic itself unit-tests cleanly with mocked repos. A `@Component` resolver is component-scanned (boots) but **not request-reachable until 2.6 wires it into the `SecurityFilterChain`** — a declared dependency, not an orphan.

**Rule:** Resolve identity → `UserPrincipal` with the **authoritative `Employee.role`** (never the nullable JWT role hint) and **relationship-driven `isManager`** (`existsBy` active manager relationship, never the role claim — MANAGER-role-but-no-active-report ⇒ `isManager=false`); identity-maps-to-no-`Employee` ⇒ IDOR-safe `Optional.empty()` (→ 401 at the 2.6 chain, no existence leak, no further DB probe via `verifyNoInteractions`); keep resolution separate from authorization (2.5); `implements` Spring's `AuthenticatedPrincipal` so `authentication.getName()` = employeeId; keep `SystemPrincipal` a distinct no-HTTP boundary singleton.

---

## <a id="16"></a>16. A domain type that `implements` a framework interface must not shadow its simple name (SpotBugs gate)

**Date:** 2026-06-02.
**Source slice:** 2.4 (principal-resolver) — surfaced post-Step-2.5, reversed an approval.

Naming a domain type the **same simple name** as a framework interface it `implements` fails the `./gradlew check` gate. We approved `record AuthenticatedPrincipal implements org.springframework.security.core.AuthenticatedPrincipal` (FQN import to dodge the name clash at the *language* level — it compiles), but SpotBugs at **effort=MAX / Confidence=MEDIUM** (the §13 gate) flagged **`NM_SAME_SIMPLE_NAME_AS_INTERFACE`** → `:api:spotbugsMain FAILED`, exit 1.

- **The shadow itself is the finding, not the import.** FQN-`implements` does NOT placate the rule — SpotBugs flags that your type's simple name equals an implemented interface's simple name regardless of how you reference the interface. There is no language-level trick that satisfies the gate.
- **Fix by renaming the domain type distinctly** — `UserPrincipal` (not `AuthenticatedPrincipal`) for the type implementing `o.s.s.core.AuthenticatedPrincipal`. Bonus: it reads better and pairs with its sibling (`UserPrincipal` ↔ `SystemPrincipal` = HTTP user actor ↔ no-HTTP system actor).
- **Do NOT suppress it in `exclude.xml`.** That concedes a SpotBugs suppression on *production* code AND leaves two same-named types in the tree — the exact readability smell the rule legitimately flags (best-practice-over-pragmatic: remove the smell, don't hide it).
- **Watch list:** the temptation recurs for any well-known framework interface name — `Clock`, `Filter`, `UserDetails`, `AuthenticatedPrincipal`, `Authentication`. When you implement one, give your type a distinct domain name up front.

**Rule:** A domain type implementing a framework interface must not shadow the interface's simple name — `NM_SAME_SIMPLE_NAME_AS_INTERFACE` (SpotBugs MAX/MEDIUM) fails the gate *even with FQN-`implements`*; rename the domain type distinctly (`UserPrincipal`, not `AuthenticatedPrincipal`), never suppress it on production code.

---

## <a id="17"></a>17. Central domain-authorization service (rule #3, IDOR-safe) idiom

**Date:** 2026-06-02.
**Source slice:** 2.5 (domain-authorization-service) — SAFETY-CRITICAL, rule #3. Security-reviewed clean (0 critical / 0 high).

A single `DomainAuthorizationService` (`@Service`) owns **every** resource check before any repository read/mutation — controllers/services carry only coarse authn/role gates (§6). It takes a `DomainPrincipal` (a `sealed interface permits UserPrincipal, SystemPrincipal` — the single authorizer input, introduced here as the 2.4-deferred marker) and enforces IC-self / manager-active-direct-report scope.

- **IDOR-safe status split (§5/§11/§16):** cross-owner / cross-team / **genuinely-missing** all render **`404`** (`ResourceNotFoundOrUnauthorizedException`) — existence is never revealed; **capability/role** denials render **`403`** (`AuthorizationDeniedException` carrying a named `code` — `IC_CANNOT_RESOLVE_DISPUTE`, `MANAGER_ROLE_REQUIRED`). The rule: revealing the resource *exists* would leak ⇒ 404; the actor legitimately *sees* the resource but can't perform the action (or the surface is categorically role-gated) ⇒ 403.
- **Manager scope = strictly the active `manager_relationship`** (reuse `findByDirectReportEmployeeIdAndActiveTrue`; an `active=false` row removes scope — ≤1 active by the V2 partial unique). A manager who **owns** a resource is authorized on it **as IC** (§4 relationship-driven — self short-circuit, no relationship lookup).
- **One safe-metadata denial `audit_event` per GENUINE denial, in `REQUIRES_NEW`.** A separate `AuthorizationDeniedAuditer` `@Service` with `@Transactional(Propagation.REQUIRES_NEW)` writes the audit (across a real proxy boundary, audit written *before* the throw) so it **survives a rolled-back mutation** (consumes the 2.3 carry-forward §14). **A genuinely-missing resource → 404 with NO audit** (anti-spam / anti-DoS on random-id probing — consistent with 2.3's unknown-demo-id→401-no-audit; both paths return identical 404 so no existence leak). §6's denial cases are all *exists-but-wrong-actor* (→ they audit).
- **Owner-resolution = flat-FK `findById` chains** (commitment→plan→owner; dispute→commitment→plan→owner; comment(target)→plan/commitment→owner; review→plan→owner; sync→ownerEmployeeId; heatmapCell→managerEmployeeId) — **zero new finders** beyond the existing active-direct-report one.
- **SYSTEM exemption is unreachable from a request:** `SystemPrincipal` (singleton) is exempt from self/direct-report checks via an `instanceof` guard on the sealed marker, and is never produced from an HTTP request (only `UserPrincipal` is) — so the exemption can't be abused.
- **Test surface:** unit (mocked repos, `verify`/`verifyNoMoreInteractions` on the auditer per denial), the Testcontainers §17 **IDOR matrix** (seed real entities with SENTINEL text → drive every denial row → assert each 403/404, exactly one audit per genuine denial, and **SENTINEL absence across every audit column**), and a real-proxy `@SpringBootTest` proving `REQUIRES_NEW` survives a `rollbackOnly` outer txn.

**Rule:** One central `@Service` authorizes every resource access (controllers coarse-only); cross-owner/cross-team/missing → IDOR-safe `404`, capability/role → `403` + named code; manager scope = active `manager_relationship` (owner short-circuits as IC); each genuine denial writes one safe-metadata `audit_event` in `REQUIRES_NEW` (survives rollback), genuinely-missing → 404 no-audit; resolve owners via flat-FK `findById` chains; gate the SYSTEM exemption behind a sealed-marker `instanceof` that a request-built principal can't satisfy.

---

## <a id="18"></a>18. Two safety/coverage build gotchas: build-and-throw deny helpers + escaped-JSON audit metadata

**Date:** 2026-06-02.
**Source slice:** 2.5 (domain-authorization-service). Both surfaced during GREEN / the ad-hoc security-reviewer.

- **Build-and-throw, don't throw-from-helper (JaCoCo coverage artifact).** A deny helper that *itself* throws (`private void deny(...) { audit(); throw new X(); }`) leaves an always-throwing line JaCoCo can mark as a partial/uncovered branch artifact — and it cost a real diagnostic cycle. Instead have the helper **build + return** the exception and **`throw` at the call site** (`throw deny(...)`). The audit side-effect happens in the helper; the `throw` is at the caller where coverage is clean. Keeps 100% line+branch honest without contortions.
- **Audit metadata JSON via an escaped node, never raw string-concat (rule #7).** Building `metadata_json` by concatenating untrusted-or-semi-trusted values into a JSON string is safe *today* only by caller discipline — a latent rule-#7 leak the moment a caller passes a value with a quote/brace. The security-reviewer flagged it; the fix is to build the object with an escaped JSON node (Jackson `ObjectNode` / `JsonNodeFactory`) so escaping is structural, not manual. **Don't let a safety invariant rest on caller discipline.** (Pairs with §14's SENTINEL-pinned safe-metadata `AuditService` — §14 says *what* stays out; this says *how* to build the JSON so it can't leak.)

**Rule:** Have deny/error helpers **build-and-return** the exception (throw at the call site) to avoid the JaCoCo always-throwing-helper coverage artifact; and build any audit/log JSON via an **escaped JSON node**, never string-concat, so rule-#7 safety is structural, not caller-dependent.

---

## <a id="19"></a>19. SS6 per-mode `SecurityFilterChain` wiring — the integration slice that makes the auth spine fire

**Date:** 2026-06-03.
**Source slice:** 2.6 (securityconfig-filter-chain) — safety-touching (rule #5 + #3 operational halves). Security-reviewed (rule #5/#3/#7 PASS; one HIGH fixed in-slice).

`SecurityConfig` (`@EnableWebSecurity @EnableMethodSecurity`) is the slice that makes the whole Phase-2 identity spine (decoder, claim mapper, demo filter, principal resolver, authorizer) actually fire in the request path — they're component-scanned-but-unreachable until a chain invokes them.

- **One `SecurityFilterChain` bean per mode, `@ConditionalOnProperty(demo-auth.enabled)`** (real = `havingValue=false, matchIfMissing=true`; demo = `true`) — mirrors `JwtConfig`'s gate (LESSONS §12). **demo** chain: `DemoAuthFilter` authenticates `X-Demo-Employee-Id` (no JWT path). **real** chain: OAuth2 resource server validates the Auth0 JWT, and `DemoAuthFilter` runs **disabled as the production-backdoor rejector** (rule #5 operational half — proven *in the chain*, not just at the unit level). Modes are **mutually exclusive** (only one chain bean exists at runtime) → bearer-XOR-demo holds structurally.
- **Resolve every identity to a `UserPrincipal` in BOTH modes** so the `SecurityContext` carries a uniform principal type for `DomainAuthorizationService` + the coarse gate. Real mode: a `Converter<Jwt,AbstractAuthenticationToken>` (`Auth0ClaimMapper.map` → `PrincipalResolver.resolve(Auth0Identity)` → `PreAuthenticatedAuthenticationToken(userPrincipal, jwt, ROLE_<role>)`); empty resolve (no employee / **inactive** / unknown subject) → throw `OAuth2AuthenticationException` → 401. Demo mode: swap `DemoAuthFilter`'s `EmployeeRepository` dep → `PrincipalResolver` so its principal is a `UserPrincipal` too (disabled-mode presence-only backdoor rejection unchanged). Coarse role gate = `@PreAuthorize("hasRole('MANAGER')")` with `ROLE_<role>` authority from the **authoritative** `UserPrincipal.role`.
- **THE RFC-7807 render gotcha — 3 render points, not 1.** Chain-level **authn** failures (`AuthenticationEntryPoint`) and **access-denied** failures (`AccessDeniedHandler`) are thrown by FILTERS and **never reach `@RestControllerAdvice`**. So you need all three feeding one shared body builder: a `ProblemDetailsAuthenticationEntryPoint` (401), a `ProblemDetailsAccessDeniedHandler` (coarse 403), AND the `@RestControllerAdvice` (the authorizer's `…NotFoundOrUnauthorized`→404 / `AuthorizationDenied`→403+code, thrown from within request handling, + a safe 500 fallback). A single advice silently misses every chain-level denial.
- **Prove the custom `JwtDecoder` wins** over `OAuth2ResourceServerAutoConfiguration`'s `@ConditionalOnMissingBean` decoder by registering the autoconfig in an `ApplicationContextRunner` and asserting OUR bean (with the audience validator) is the one wired.
- **⚠ HIGH (fixed in-slice) — fail-fast on a non-boolean security-mode gate.** A malformed `demo-auth.enabled` (`yes`/`1`/typo) matches **neither** `@ConditionalOnProperty` chain → Boot's *default* chain silently takes over (fails closed, but drops the rule-#5 rejector + Auth0 validation + RFC-7807 with **no signal**). Fix: the config **fails fast at startup** on a non-boolean gate (refuse to start with an ambiguous security mode) — secure-by-default made *loud*. Whenever a security posture is keyed on a `@ConditionalOnProperty` boolean, validate it's actually boolean at startup.
- **Reachability:** the `@EnableWebSecurity` chain provides its own beans independently of an excluded `SecurityAutoConfiguration` — so existing DB-less/skeleton boot tests that `spring.autoconfigure.exclude` it keep passing unchanged once a real chain exists (no harness churn).

**Rule:** Wire one `@ConditionalOnProperty(demo-auth.enabled)` `SecurityFilterChain` per mode (mutually exclusive → bearer-XOR-demo structural); resolve every identity to a `UserPrincipal` in both modes (JWT-converter + demo-filter-via-`PrincipalResolver`, empty→401); render RFC-7807 at **three** points (entry-point 401 + access-denied 403 + `@RestControllerAdvice` 404/403+code/500 — chain-filter exceptions never hit the advice); prove the custom decoder wins over autoconfig; and **fail-fast at startup on a non-boolean security-mode gate** (a malformed value silently falls through to Boot's default chain).

---

## <a id="20"></a>20. Three SpotBugs/Jackson gotchas wiring the RFC-7807 problem+json path

**Date:** 2026-06-03.
**Source slice:** 2.6 (securityconfig-filter-chain). Each cost a real diagnostic cycle at GREEN.

- **`EI_EXPOSE_REP2` on a `@Component` storing a concrete-mutable dependency.** A render component that stores an injected `ObjectMapper` (concrete, mutable) trips EI2 (same family as §10). Make the body builder a **pure static utility** that stores only interfaces/immutables (or constructs its own writer), rather than holding a mutable collaborator field.
- **`CT_CONSTRUCTOR_THROW` on a validating constructor.** A `@Configuration` whose constructor validates + throws (e.g. the fail-fast non-boolean-gate check) trips `CT_CONSTRUCTOR_THROW` (partially-constructed-object finalizer-attack guard). Resolve with **`@Configuration(proxyBeanMethods = false)` + a `final` class** (no subclass → no finalizer attack surface), mirroring `JwtConfig`'s shape.
- **A plain `ObjectMapper` nests `ProblemDetail`'s custom properties under `properties`.** Spring MVC's normal serialization path uses a mixin that **flattens** `ProblemDetail.getProperties()` to top level; a **filter-level** write (entry-point / access-denied handler, outside MVC) using a plain `ObjectMapper` does NOT get that mixin, so `safeMessage`/`code`/`traceId` come out nested under `"properties": {...}`. Flatten them manually (build the body map yourself, or register the mixin) on the filter write path so the demo + real chains emit an identical flat RFC-7807 shape.

**Rule:** On the RFC-7807 path — make the body builder a pure static util (no stored mutable `ObjectMapper` → EI2); give a validating `@Configuration` `proxyBeanMethods=false`+`final` (→ CT_CONSTRUCTOR_THROW); and flatten `ProblemDetail`'s custom props yourself on any **filter-level** (non-MVC) write, since the flattening mixin isn't applied there.

---

## <a id="21"></a>21. First controller + DTO across the boundary (record-not-entity) + CORS via `http.cors()`

**Date:** 2026-06-03.
**Source slice:** 2.7 (cors-and-me-endpoint) — Phase-2 closer.

`GET /api/me` established the reference **controller → service → DTO** pattern + the CORS security boundary that every Phase-3+ endpoint follows:

- **Thin `@RestController` → `@Service` → DTO record.** The controller reads the resolved `@AuthenticationPrincipal UserPrincipal`; a small `@Service` reloads the `Employee` (one `findById`) for display fields (the principal carries only id/role/isManager) and maps to a **DTO `record`**. **Entities NEVER cross the boundary** (forbidden-pattern #3) — pin it with a leak test asserting entity-only fields are absent: `jsonPath("$.active"/"$.externalSubject"/"$.createdAt"/"$.version").doesNotExist()`.
- **`/me`-style self endpoints are authenticated-only — NO `DomainAuthorizationService` call** (the caller's own identity is self by definition; don't add a no-op self-authz check). Other resource endpoints DO call the authorizer before any repo access (the rule-#3 chokepoint).
- **`MeDto` mirrors Appendix B.3 verbatim** (`employeeId/email/displayName/role/persona/isManager/timezone?`) — the **first DTO contract** = a cross-doc invariant (the executable mirror of B.3). `persona=email` in both modes (simplest stable per-identity key; a persona switch → a different `MeDto`, satisfying REQ-F-032).
- **CORS = a `CorsConfigurationSource` bean wired via `http.cors()` into BOTH mode chains** — exact-origin allow-list (**no wildcard**), `allowCredentials=false` (bearer transport), the configured methods/headers, preflight `OPTIONS` bypasses auth (Spring Security short-circuits preflight before the authz filter). **MODE-INDEPENDENT:** the bean reads only `app.cors.allowed-origins`, never `demo-auth.enabled` → demo mode **cannot widen** the allow-list (RISK-008 corollary, §12/§16). A disallowed origin is never reflected.

**Rule:** First controller = thin `@RestController` → `@Service` → **DTO `record`** (entity never crosses the boundary — assert via a `.doesNotExist()` leak test); `/me`-style self endpoints are authenticated-only (no authorizer call), all other resource endpoints call the authorizer first; wire CORS as a `CorsConfigurationSource` via `http.cors()` into every mode chain — exact-origin + `allowCredentials=false` + **mode-independent** (read only `app.cors.allowed-origins` so demo can't widen).

## <a id="22"></a>22. Nested read-DTO pattern (RCDO tree) — flat id-ordered finders + in-memory parent-id assembly + seed-UUID ordering

**Date:** 2026-06-03.
**Source slice:** 3.1 (rcdo-read-service) — first Phase-3 slice.

`GET /api/rcdo` established the **nested read-DTO** pattern that every multi-level read endpoint (RCDO browse, plan+commitments, manager drilldown) follows — built on the 2.7 controller→service→DTO pattern:

- **Three flat `findAll…` finders + an in-memory parent-id mapper, NOT JPA fetch-joins.** The mapper groups children by their flat-`UUID` parent FK (`rallyCryId`/`definingObjectiveId`), preserves input order, and maps `active` **verbatim — never filters** (the field is the contract; inactive nodes serialize `active=false`). For a small bounded catalog this avoids N+1/fetch-join complexity entirely. A child whose parent id matches no parent is dropped (never attached to a phantom) — pinned by a defensive mapper test even though FKs make it impossible in the DB.
- **Object-wrapper, never a bare array.** `RcdoTreeDto = { rallyCries: [...] }` (B.4 / §5 envelope) — empty DB → `{"rallyCries":[]}` (never `null`, never bare `[]`). Pin the empty case at the **mapper unit level**, not the integration test, once a seed migration makes the DB non-empty for every Flyway-running test.
- **Org-wide reference reads are authenticated-only — NO `DomainAuthorizationService` call** (like `/me`, §21): RCDO is org-wide read data, not a per-user resource; IC and Manager both 200, unauth → 401 via the 2.6 chain. Don't add a no-op authz check.
- **id-asc over deliberately-sequential seed UUIDs gives logical wire order with ZERO schema change.** No `sort_order` column exists; ordering by `title` scrambles the logical strategy order (DO-1/2/3, SO-1.x). Instead, the `V4__seed_rcdo.sql` seed assigns fixed UUID literals in logical sequence (RC `…0001`; DO `…0001/0002/0003`; SO `…0001`…`…0009`) and the finders are `findAllByOrderByIdAsc()` → `ORDER BY id` == logical order. Pin the order as a contract in the endpoint test (`definingObjectives[0].title` == the DO-1 string, `…supportingOutcomes[0].title` == the SO-1.1 string). A first-class `display_order` column is the textbook-correct model but is an Appendix-A schema change — deferred for permanently read-only hand-seeded reference data (REQ-D-003, no admin UI ever).
- **A read-only domain proves "no mutation endpoint exists" STRUCTURALLY.** Iterate `RequestMappingHandlerMapping.getHandlerMethods()` and assert no `POST/PUT/PATCH/DELETE` is mapped under `/api/rcdo*` (REQ-D-003 drift-guard) — a reflection test, not a per-verb 405 check.
- **An internal lookup seam may return the entity.** `RcdoReadService.findSupportingOutcome(UUID)` returns the `SupportingOutcome` **entity** (carries `definingObjectiveId` for the 3.3 breadcrumb), reused by 3.4/3.5 commitment→SO linking — forbidden-pattern #3 (no entities across the boundary) does NOT apply to a service-to-service seam; unknown id → `ResourceNotFoundOrUnauthorizedException` (404 via 2.6).
- **Three mechanical gotchas this slice surfaced:** (a) immutable record DTOs holding collections need `List.copyOf()` **compact constructors** to stay SpotBugs-clean (`EI_EXPOSE_REP`); (b) `@Autowired RequestMappingHandlerMapping` is ambiguous (actuator registers `controllerEndpointHandlerMapping`) → qualify with `@Qualifier("requestMappingHandlerMapping")`; (c) re-invoking a `@Nullable`-returning getter after a null-check trips SpotBugs `NP_NULL_ON_SOME_PATH` → read once into a local.

**Rule:** Nested reads = N flat id-ordered finders + an order-preserving in-memory parent-id mapper (no fetch-joins), `active` mapped verbatim, object-wrapper-not-bare-array; org-wide reference reads are authenticated-only (no authorizer call); get logical wire order from id-asc over sequential seed UUIDs (no schema column), pinned by a title-order assertion; prove read-only domains carry no mutation verb via a `RequestMappingHandlerMapping` reflection test; an internal lookup seam may return the entity (not a boundary leak); guard record-DTO collections with `List.copyOf()`, qualify `requestMappingHandlerMapping`, and read `@Nullable` getters once.

## <a id="23"></a>23. One-shot `--app.job` runner pattern (SYSTEM batch job) + testable gating

**Date:** 2026-06-03.
**Source slice:** 3.2 (plan-shell-generation) — first one-shot batch job.

The weekly-shell generation job established the **one-shot `--app.job=<name>` runner** pattern that every future batch job (perf-seed, rebuild) reuses — same `wc-api` image, no third app:

- **2-class split — logic component + thin runner.** A `@Component` `PlanShellGenerator` holds the `@Transactional generate()` logic; a thin `PlanShellGenerationRunner` (`@Component implements ApplicationRunner`, `@ConditionalOnProperty(name="app.job", havingValue="generate-plan-shells")`) just delegates `run()` → `generate()`. **Why split:** an `ApplicationRunner` **auto-fires at context startup**, so you can't put it in a `@SpringBootTest` with the activation property set without it running mid-test. The split lets you test `generate()` directly against PG **and** prove the gating via `ApplicationContextRunner` (which evaluates `@ConditionalOnProperty` but does **not** auto-invoke runners → `run()` delegation is testable without a JVM exit). Inertness in the web image is pinned by `@Autowired(required=false) <Runner>` being null on a normal boot.
- **One-shot termination via `--spring.main.web-application-type=none`, NOT a stored context + `SpringApplication.exit`.** Holding the `ConfigurableApplicationContext` to call `exit(ctx)` trips SpotBugs `EI_EXPOSE_REP2` (no prod suppressions, §16); `web-application-type=none` terminates naturally (runner completes → `main()` returns → JVM exits, Hikari threads are daemon), consistent with the migration Job. Pass it as a CronJob **launch arg** (infra-owned) alongside `--app.job`; `activeDeadlineSeconds` is the k8s backstop. A hard `System.exit`-in-`main()` guard is an optional belt-and-suspenders only if natural termination ever proves unclean.
- **SYSTEM-actor audit reuses the existing `record(...)`.** Server-initiated work writes one summary `audit_event` per run with **null `actor_employee_id`** (SYSTEM, §6) — no dedicated SYSTEM method needed (the existing `AuditService.record` already takes a nullable actor; YAGNI). Safe metadata only (`{week_start_date, shells_created_count}` via escaped `ObjectNode`, §14/§18 — never employee PII, rule #7). One row **per run even when 0 rows mutated** (operational trail).
- **Idempotency = existence pre-filter + unique backstop, NOT exception-driven control flow.** Load the set of `employee_id`s that already have a row for the target key (`findByWeekStartDate` → ids), insert only the missing ones in one `@Transactional`; the `unique(employee_id, week_start_date)` constraint is the backstop (a truly concurrent double-run rolls back — acceptable for a single scheduled CronJob, noted-not-handled). No try/catch on the constraint.
- **`jsonb` round-trips through Postgres CANONICAL form** (space after `:`), so audit-metadata assertions must be **structural** (`objectMapper.readTree(...)` equality), never exact-compact-string — reinforces §8 (`jsonb`→`String` mappings compare structurally).
- **Org-tz fail-safe is reused, never re-implemented** — the job consumes the static `OrgTimeBindingConfig.resolveZone` (0.4) + `OrgTimeConfig.weekStartDate/weekEndDate`; `weekEndDate(date)` = `weekStartDate(date).plusDays(6)` is the Sunday pair. Inject the shipped `ClockConfig` `Clock` for deterministic week resolution in tests.

**Rule:** One-shot batch jobs = a `@Component` logic class + a thin `@ConditionalOnProperty("app.job")` `ApplicationRunner` that delegates (split for testability — `ApplicationContextRunner` proves gating without auto-firing); terminate via `--spring.main.web-application-type=none` launch arg (never a stored context + `SpringApplication.exit` → `EI_EXPOSE_REP2`); audit one SYSTEM null-actor row per run (reuse `record(...)`, safe metadata, even on 0 mutations); idempotency = existence pre-filter + unique backstop (no exception control-flow); assert `jsonb` metadata structurally (`readTree`); reuse the org-tz fail-safe + inject `Clock`.

## <a id="24"></a>24. Plan-DTO-with-nested-commitments + server-authoritative `allowedActions[]` (affordance↔enforcement single-source)

**Date:** 2026-06-03.
**Source slice:** 3.3a (plan-dto-and-current-read) — the `WeeklyPlanDto` contract + E3 self-read.

`GET /api/plans/current` established the nested-mutable-DTO + affordance pattern every plan/commitment-returning endpoint (E3/E4/E8–E11) reuses:

- **`PlanMapper` assembles the nested DTO; the breadcrumb stays in the RCDO service.** The mapper maps `WeeklyPlan`→`WeeklyPlanDto` with a nested `WeeklyCommitmentDto[]`, and for each linked commitment resolves the RC→DO→SO `RcdoBreadcrumbDto` via **`RcdoReadService.resolveBreadcrumb(soId)`** — RCDO knowledge lives in the RCDO service, not duplicated in the plan mapper; null breadcrumb when unlinked (resolver not called).
- **`allowedActions[]` is server-authoritative (§15) and computed from the SAME predicate the lifecycle service enforces.** `AllowedActionResolver.canLock(actor, plan, commitments)` (DRAFT ∧ owning IC ∧ ≥1 PLANNED ∧ all PLANNED linked) is the single source — **3.5's `PlanLifecycleService` reuses it for enforcement**, so the UI affordance and the server's 409 never disagree (no affordance↔enforcement drift). The UI gates only on `allowedActions[]`, never re-deriving eligibility client-side.
- **"No affordance without enforcement."** Emit only affordances whose enforcement exists or is imminent: 3.3a emits **only `LOCK`** (the one applicable to a DRAFT plan, enforced next at 3.5); `START_RECONCILIATION`/`ADD_UNPLANNED`/`CARRY_FORWARD`/`OPEN_DISPUTE`/`COMMENT` are emitted by their **enforcing** slices (Phase 4 / disputes / comments). Commitment `allowedActions=[]` until then. Eagerly emitting an un-enforced affordance = a UI control that 409s.
- **`AllowedAction` is computed DTO vocab, NOT a `shared/enums/` member** — it lives in a neutral api pkg (`com.st6.wc.action`, cross-cutting across plan/commitment/review DTOs); putting it in `enums/` would break `EnumVocabularyTest`'s exact-16 pin (it's never persisted, no `VARCHAR`+`CHECK` column).
- **Self-scoped reads are authenticated-only — no authorizer call** (like `/me`, §21/§22): `GET /plans/current` returns only the caller's own plan (resolved by `employeeId`=caller, no `{id}` IDOR surface) → no `DomainAuthorizationService` call. The per-resource `{id}` read (E4) is the separate IDOR slice (3.3b) that DOES call the authorizer.
- **A named-coded 404 (`PLAN_NOT_FOUND`) is fine on a self-scoped read; the codeless IDOR 404 is for per-resource reads.** Absent own-current-shell → `404 PLAN_NOT_FOUND` (named, no create-on-GET — generation owns shell creation) leaks nothing (it's the caller's own data); the per-resource IDOR 404 (`ResourceNotFoundOrUnauthorized`, **no code**) must stay codeless to avoid existence-revealing.
- **A response DTO may intentionally LAG a pending-but-approved contract change** as a documented transitional subset — `WeeklyCommitmentDto` omits the dispute field (Option-A drops `hasUnresolvedDispute`/adds `dispute?: AlignmentDisputeDto`) rather than ship a throwaway stub; the field is added fresh at the enforcing (disputes) slice. Document the omission in the cross-doc row so it reads as intent, not drift.

**Rule:** Nested plan/commitment DTOs = a mapper that assembles children + resolves the RCDO breadcrumb via the RCDO service (not duplicated); `allowedActions[]` is server-authoritative (§15), computed from the SAME predicate the lifecycle service enforces, under "no affordance without enforcement" (emit only enforced actions); `AllowedAction` is neutral-pkg computed vocab (never `enums/`); self-scoped reads skip the authorizer (per-resource `{id}` reads don't); a named-coded 404 is fine self-scoped but the IDOR 404 stays codeless; a DTO may carry a documented transitional subset for a pending-approved contract change.

## <a id="25"></a>25. Per-resource read via the authorizer chokepoint (rule #3 consumer) + existence-hiding test

**Date:** 2026-06-03.
**Source slice:** 3.3b (plan-by-id-idor) — the first per-resource `DomainAuthorizationService` consumer (E4 `GET /api/plans/{id}`). **Ad-hoc security-reviewer: PASS, 0 findings.**

Every per-resource `{id}` endpoint (E4/E6/E7, disputes, comments, sync, heatmap-drilldown) consumes the central authorizer this way — the read-path counterpart to the 2.5 authorizer build:

- **`authorize…Access(principal, id)` is the FIRST statement in the service method** — before any resource load reaches a response path (the rule-#3 chokepoint, §6). The controller stays thin (`@AuthenticationPrincipal` + id → service). **Pin the chokepoint with a unit test:** mock the authorizer to throw, then `verify(repo, never()).findById(id)` — proves no resource is read into a response before authorization.
- **The codeless IDOR 404 is IDENTICAL for genuinely-missing + existing-but-unauthorized** — both throw the same `ResourceNotFoundOrUnauthorizedException` (no `code`) → identical body via the 2.6 advice. The ONLY difference is server-internal: a genuine denial (resource exists, principal unauthorized) writes one `REQUIRES_NEW` safe-metadata denial audit; a genuinely-missing id writes **none**. The caller cannot distinguish "doesn't exist" from "not yours" (existence-hiding). This distinction lives in the 2.5 authorizer (`planOwner` `orElseThrow(notFound)` = no audit; `authorizeOwnership` cross-owner = `deny404` audit+throw) — the consumer just calls it; the consumer's matrix proves it E2E.
- **Per-resource codeless 404 ≠ self-scoped named 404.** A self-scoped read (E3 `/plans/current`, your own absent plan) may return a **named/coded** `PLAN_NOT_FOUND` (leaks nothing — it's your own data). A per-resource `{id}` read must return the **codeless** IDOR 404 (a code would reveal existence). Route the by-id path only through `ResourceNotFoundOrUnauthorizedException` — incl. the defensive post-authorize re-fetch's empty branch (the concurrent-delete TOCTOU case stays codeless).
- **Existence-hiding body-equality test strips request-correlation fields, NOT literal bytes.** Assert the cross-owner and missing-id 404 bodies are identical **after dropping `traceId` (random per response) AND `instance` (= the request URI, which embeds the differing `{id}`)** — the server-*determined* fields (`status`/`title`/`safeMessage`/absent-`code`) must match. Literal byte-equality can't hold (those two fields legitimately vary).
- **The double-read is acceptable.** The authorizer's internal owner-lookup `findById` + the service's post-authorize re-fetch read the resource twice. Leave it — the authorizer's **void** contract (authorize-or-throw, no resource returned) keeps the authz surface uniform across resource types; one extra bounded `findById` is cheaper than coupling `authorize…Access` to each resource's return type.
- **200 paths write zero audits** — authorized access is not audited (only denials are, §6).

**Rule:** Per-resource `{id}` reads call `authorize…Access(principal, id)` as the first service statement (chokepoint, pinned by `verify(repo, never()).findById` on a denied authorize); missing + unauthorized both return the identical **codeless** IDOR 404 (audit only on genuine denial, never on missing); a per-resource 404 is codeless while a self-scoped 404 may be named; the existence-hiding body-equality test strips `traceId` + `instance`; the authorizer stays void (the double-read is the right tradeoff); authorized reads write no audit.

## <a id="26"></a>26. Server-side input validation pattern (Appendix E Part 1) — normalize-once + code-point caps + safe error rendering

**Date:** 2026-06-03.
**Source slice:** 3.4a (commitment-create-validation) — E5 + the validation suite. **Ad-hoc security-reviewer: PASS, 0 findings.**

The commitment-create endpoint established the §16 server-side validation pattern every text-bearing write (E6 patch, comments, notes, dispute text) reuses:

- **Normalize ONCE in the request record's compact constructor** (the DTO boundary, Appendix E cross-cutting rule 5). A pure `TextNormalizer` (`strip()` + collapse internal whitespace runs to a single space + NFC for single-line fields; preserve newlines for multi-line; blank/whitespace-only → `null`) runs in the `record`'s compact constructor, so the components are already normalized before `@Valid` and before persistence — read paths never re-normalize.
- **Cap in CODE POINTS, not UTF-16 units** — a plain `@Size(max=255)` counts `char`s (UTF-16), so a 255-code-point title of astral chars (surrogate pairs = 510 units) is wrongly rejected. Use a custom `@CodePointSize` constraint delegating to `String.codePointCount`; it validates the **normalized** value. Pin it with a 255-astral-char test (accepted) + a 256-cp test (400).
- **Control chars per Appendix E** — title **rejects** any remaining C0/C1 control after whitespace-collapse (a `@NoControlChars` constraint → 400); description **strips** C0 controls except `\n`/`\t`. (Contract/data-quality, not security — §16's raw-store + render-escape already covers XSS; honor it anyway because the validation contract is the slice's whole point.)
- **Unknown-enum / malformed-JSON surfaces as `HttpMessageNotReadableException`, NOT `MethodArgumentNotValidException`** — Jackson fails to deserialize the bad value *before* `@Valid` runs. Handle it → `400 VALIDATION_ERROR` (never 500), and **extract ONLY the safe field NAME from the `InvalidFormatException` path, NEVER `getValue()`** (§15 — reflecting the untrusted value back is a leak). `MethodArgumentNotValidException` handles the genuine `@Valid` constraint failures (`fieldErrors[]`). **Do NOT add a `ConstraintViolationException` handler until a `@Validated` method-param slice exists** — no reachable trigger = untested dead handler (the no-handler-without-a-trigger discipline, §19/§21).
- **Store user text RAW** — no server-side HTML stripping (the system never renders user text as HTML; React default-escapes — §16, first XSS trim point); sensitive text (notes, dispute/comment bodies) is **never** logged or put in `metadata_json` (§15). The canonical XSS probe `<img src=x onerror=alert(1)>` + emoji/RTL `🚩مرحبا` are stored byte-for-byte.
- **Server forces server-owned fields** — `commitmentKind=PLANNED` is set server-side (not a request field), so a client can't override it; `workType=UNPLANNED` on the planned-only endpoint → 400.
- **Create-via-parent-plan-authz** — a create has no child resource to authorize yet, so authorize the **parent** (`authorizePlanAccess(principal, planId)`, the §25 chokepoint) before any write; non-DRAFT parent → `409 ILLEGAL_STATE_TRANSITION` (a create-precondition, NOT `LOCKED_BASELINE_EDIT` — that's for editing an existing baseline field, Appendix E rule 2). No projection upsert on DRAFT commitments (ProjectionService is 3.5; §9).
- **`ErrorCodes` is free-string §5 named-code vocab** (not an `enums/` member); a shared `CommitmentMapper` (extracted from `PlanMapper`) is the one place the B.6 shape + breadcrumb is built. `spring-boot-starter-validation` (Hibernate Validator) is required for the custom-constraint path.

**Rule:** Validate user input server-side by normalizing once in the request record's compact constructor (`TextNormalizer`: strip+collapse+NFC, blank→null) + a custom `@CodePointSize` (code points, not UTF-16) + `@NoControlChars` (title rejects / description strips per Appendix E) → uniform `400 VALIDATION_ERROR`/`fieldErrors`; route unknown-enum/malformed-JSON through a `HttpMessageNotReadableException` handler that extracts ONLY the safe field name (never the value, §15), never adding a no-trigger `ConstraintViolationException` handler; store user text raw (React escapes, §16) and never log sensitive text; force server-owned fields server-side; authorize the parent plan before a child create (non-DRAFT → `409 ILLEGAL_STATE_TRANSITION`).

## <a id="27"></a>27. PATCH 3-way presence + per-resource mutation authz + the SpotBugs mutator-name trap

**Date:** 2026-06-03.
**Source slice:** 3.4b (commitment-patch-delete-gates) — E6/E7 + the rule-#2 baseline-immutability gate. **Ad-hoc security-reviewer: PASS (0 crit/0 high).**

The commitment edit/delete slice surfaced three load-bearing gotchas + the per-resource-mutation authz pattern:

- **PATCH needs a presence-flag POJO, NOT an `Optional<>` record.** A partial-update DTO must distinguish three states per field: **absent** (untouched), **present-null** (clear/unlink a nullable field), **present-value** (set). An `Optional<>`-typed `record` CANNOT model this — Jackson collapses *both* absent and JSON-null into `Optional.empty()` (so you lose absent-vs-clear), AND Hibernate Validator's `OptionalValueExtractor` feeds the empty Optional's `null` into a container-element `@NotBlank`, spuriously rejecting an *absent* field (this fails GREEN immediately). The fix is a **presence-flag POJO**: each Jackson setter records that the JSON key was present; the service then validates/normalizes only the provided fields (reusing the §26 `TextNormalizer` + constraints). **The "flag set iff JSON key present" invariant is load-bearing for the rule-#2 gate** — the gate keys on "did the patch *provide* a frozen field"; a code path that sets a field without its provided-flag would silently bypass the freeze. Pin present-null-clears with its own test (the payoff of the 3-way mechanism).
- **Per-resource MUTATION authz = a dedicated `authorize…Mutation` in the central authorizer** (not an owner-check scattered in the service). `authorize…Access` authorizes IC-owner **and** manager-direct-report (both can *read*); a mutation is **IC-owner-only**. Add `DomainAuthorizationService.authorizeCommitmentMutation` = access-chokepoint (codeless `404`+audit if no access) **then** an owner-check → **`403 COMMITMENT_OWNER_REQUIRED` + denial audit** for a manager-direct-report (existence already known to them via read → `403` capability, NOT `404`). Mirrors `authorizeDisputeResolution` (access-then-capability); keeps §17 one-central-authorizer intact. `COMMITMENT_OWNER_REQUIRED` is an authorizer-local constant (like `MANAGER_ROLE_REQUIRED`) — add it to the B.21 code list.
- **Two distinct post-lock gates, two codes.** A frozen-baseline-field edit (`title`/`description`/`supportingOutcomeId`/`priority`/`workType`/`confidence`/`commitmentKind`) on a locked plan → `409 LOCKED_BASELINE_EDIT` (rule #2). A post-lock `alignmentStatus` edit → `409 ILLEGAL_STATE_TRANSITION` with `constraint=alignment_status_read_only_post_lock` (Appendix E rule 2 — `alignmentStatus` is read-only post-lock but NOT a baseline field). **Baseline-first precedence** when a single patch carries both. Give `IllegalStateTransitionException` an **optional `constraint`** field rendered as the `constraint` body property (no-arg ctor unchanged for delete/create).
- **SpotBugs 4.8.x `MutableClasses` mutator-name heuristic.** A class with a public method whose name starts with `add/append/clear/delete/insert/pop/push/put/remove/replace/set` is deemed *mutable* → a spurious `EI_EXPOSE_REP2` on whatever injects it (e.g. the controller holding the service). **No suppression on prod (§16)** → rename the method (`delete`→**`discard`**; `update` is NOT in the list, so it stays) — the REST verb stays `DELETE`. (Same family as §10/§16/§20 SpotBugs-without-suppression discipline.)
- **State-gate the allow-list per plan state, don't widen `!= DRAFT`** (security-reviewer forward-guard): the `!= DRAFT` gate today collapses LOCKED/RECONCILING/RECONCILED into one bucket — fine for 3.4b (reconciliation fields are absent from the DTO, so worst case is a no-op-200), but the Phase-4 reconciliation PATCH must **re-derive the editable-field allow-list per state**, never widen the single gate.
- **Realized at 4.1 (outcome-recording PATCH).** The forward-guard is implemented as an explicit **per-plan-state editable-field allow-list** in `CommitmentService.update` (ordered gates, NOT a widened `!= DRAFT`): `DRAFT` → baseline + `alignmentStatus` editable, outcome fields → `409 ILLEGAL_STATE_TRANSITION`; `LOCKED` → baseline → `409 LOCKED_BASELINE_EDIT`, `alignmentStatus`/outcome → `409 ILLEGAL_STATE_TRANSITION` (not yet reconciling); `RECONCILING` → baseline → `409 LOCKED_BASELINE_EDIT`, `alignmentStatus` → `409 ILLEGAL_STATE_TRANSITION`, `{reconciliationOutcome, outcomeNote}` → applied; `RECONCILED` → all → `409`. **Baseline-first precedence holds across the new field combination** — a patch carrying BOTH a baseline field AND an outcome rejects baseline-first and does NOT apply the outcome (pinned by a dedicated precedence test — the exact rule-#2 bypass to prevent). A direct `reconciliationOutcome=CARRIED_FORWARD` is rejected `400 VALIDATION_ERROR` (set only via E12/carry-forward). The outcome write recomputes the §9 projection + audits in one `@Version` txn; the 3.5 count derivations stay unchanged (whether `reconciliation_outcome=BLOCKED` feeds `blocked_count` is a pending §9 doc-pin). (4.5 will extend the `RECONCILING` set to allow `supportingOutcomeId` for **UNPLANNED** commitments only — planned baseline stays frozen.) **Security-reviewer PASS.**
- **Extended at 4.5 (close-reconciliation) to a per-(state × kind) matrix.** The `RECONCILING` set now opens `supportingOutcomeId` for **UNPLANNED commitments only** (the pre-close SO-link, REQ-F-026) while a PLANNED `supportingOutcomeId` stays frozen (`409 LOCKED_BASELINE_EDIT`) in every non-DRAFT state. Implementation: **split `supportingOutcomeId` out of the always-frozen `touchesBaseline` set** + a SEPARATE SO gate — non-DRAFT + `supportingOutcomeId` provided → `LOCKED_BASELINE_EDIT` UNLESS `(state==RECONCILING ∧ commitment.kind==UNPLANNED)` → apply (validated via `RcdoReadService`, unknown → `400`). The allow-list is now keyed on BOTH plan state AND commitment kind; the security-reviewer's **6-cell `(state × kind)` trace** confirmed it opens EXACTLY that one cell and preserves every prior freeze (3.4b's each-baseline-field loop + 4.1's RECONCILING-baseline-frozen). **Never widen one gate — add a narrower cell.** The link-only PATCH audits `OUTCOME_RECORDED` (the reconciliation-mutation umbrella) carrying the touched field NAMES in safe metadata (§15, names-not-values). **Ad-hoc security-reviewer PASS (0 findings).**

**Rule:** PATCH partial-update DTO = a presence-flag POJO (NOT an `Optional<>` record — Jackson + `OptionalValueExtractor` collapse absent/null; the provided-flag is load-bearing for state gates); per-resource mutations use a dedicated `authorize…Mutation` (access-chokepoint + owner-check → `403 …_OWNER_REQUIRED`+audit, mirroring `authorizeDisputeResolution`); the two post-lock gates are distinct codes (`LOCKED_BASELINE_EDIT` baseline-first vs `ILLEGAL_STATE_TRANSITION`+`constraint` for `alignmentStatus`); dodge the SpotBugs mutator-name `EI2` heuristic by renaming (`delete`→`discard`), never suppressing; re-derive editable-field allow-lists per plan state **AND commitment kind** (the 4.5 (state × kind) matrix — a narrower cell, never a widened gate) rather than widening one condition.

---

## <a id="28"></a>28. Plan-LOCK transaction pattern — single-source `canLock` accept-gate + fail-closed diagnosis, afterCommit→REQUIRES_NEW non-blocking publish, optimistic-lock→409, and §9 recompute-from-source

**Date:** 2026-06-03.
**Source slice:** 3.5 (plan-lock-e8) — E8 `POST /api/plans/{id}/lock`, the Phase-3 safety culmination (rules #1/#2/#4). **Ad-hoc security-reviewer: PASS (0 findings across rules #1/#2/#3/#4/#6/#7).**

The lock transition is the project's keystone — ONE `@Version`-guarded transaction that moves `DRAFT→LOCKED` and fans out six side-effects, with a strictly non-blocking calendar publish after commit. The reusable patterns:

- **Precondition = the affordance predicate as a single accept-gate, diagnosed only on rejection, fail-closed (§15).** `PlanLifecycleService` calls the REAL `AllowedActionResolver.canLock(actor, plan, commitments)` (the 3.3a affordance predicate) as the single ACCEPT gate — never a second copy of the accept logic (affordance↔enforcement single-source). Only when `canLock` returns **false** does a separate diagnosis pick the specific 409: non-`DRAFT`→`ILLEGAL_STATE_TRANSITION`, zero planned→`EMPTY_PLAN_LOCK`, any unlinked planned→`UNLINKED_PLANNED_COMMITMENT` with `fieldErrors[]` naming each unlinked `commitments[N].supportingOutcomeId` + `constraint=planned_commitment_requires_supporting_outcome_at_lock` (B.21). The diagnosis is **fail-closed**: if `canLock` is false but no branch matches, it still throws a generic `409` (never falls through to a silent lock or NPE) — robust to `canLock` growing a new false-condition. `PlanLifecycleServiceTest` uses the REAL resolver (single-source proof) PLUS one stubbed-resolver test forcing `canLock=false` on an otherwise-lockable plan → asserts a 409 + `planRepository.save` never called (the fail-closed pin).

- **Non-blocking calendar publish = `afterCommit` synchronization → a `REQUIRES_NEW` publisher that swallows-never-rethrows (rule #4).** The core txn writes the sync record `PENDING_PUBLISH`; `TransactionSynchronizationManager.registerSynchronization(afterCommit → publisher.publish(recordId))` fires the SNS publish **only after the core commit**, so a publish failure cannot roll back the lock. The publisher is `@Transactional(REQUIRES_NEW)` (a fresh txn — the original already committed), calls the gateway, on success → `QUEUED`+`queuedAt`, on **any** exception → catch+log, **leave `PENDING_PUBLISH`** (retained/retryable), never rethrow. A guard publishes immediately when no synchronization is active (unit context). Testable **synchronously** (the afterCommit runs in-thread before the controller returns): the `@SpringBootTest` endpoint test with a throwing `@MockBean` gateway asserts `200 LOCKED` + record `PENDING_PUBLISH`; the success path asserts `QUEUED`. The pointer payload `SyncJobPointer{syncRecordId,eventKind,env,traceId}` is pointer-only (rule #7, F.2). The API-side `FAILED` transition is NOT part of the lock path (`FAILED` is worker/Graph-side, §10) — "retained" means it stays `PENDING_PUBLISH`.

- **A concurrent command conflict is a 409, not a 500.** Map `OptimisticLockingFailureException`/`ObjectOptimisticLockingFailureException` → `409 ILLEGAL_STATE_TRANSITION` in `ProblemDetailsExceptionHandler` (§5 "all command endpoints guarded by optimistic concurrency … 409 incl. optimistic-lock"), rendered without leaking the stack. Pinning a "concurrent double-lock→409" criterion does NOT need a flaky threaded race — **decompose it**: the `@Version` generates the `OptimisticLockingFailureException` (proven at the repo layer, §11) + the service propagates it (doesn't swallow) + the handler maps it without leak. Three deterministic links compose to the contract.

- **§9 projection at lock = recompute-from-source insert; skipped entirely when there is no active manager.** `ProjectionService.recompute(manager, employee, week, …)` derives `manager_plan_summary` + the per-DO `manager_heatmap_cell` rows from the current commitment set: `plannedCount`, `misalignedCount` (`alignment_status=MISALIGNED`), `needsReviewCount`, `blockedCount` (`work_type=BLOCKER`), `risk_badges` from the §9/§4 vocabulary, grouped by the commitment's SO→DO grain (resolved via the RCDO service). At lock it's an INSERT (DRAFT plans aren't projected); `is_review_overdue=false` (NOT_REVIEWED + future `reviewDueAt`); `unresolvedDisputeCount=0` (no disputes yet); stale-cell deletion on recompute is deferred to the reconciliation/dispute slices. **No active manager** (e.g. a manager locking their own plan) → skip ALL manager-scoped side-effects (review + projections + review-block — `manager_review.manager_employee_id` is NOT NULL, so a review can't exist without a manager); the lock + `PLAN_LOCKED` audit + `IC_PLANNING` record still happen.

- **`PlanMapper` maps the review for `LOCKED+` plans** (looks up by `weeklyPlanId` + `ReviewMapper`, null while DRAFT) so E3/E4/E8 all surface it; the 3.3a/3.3b DRAFT read tests stay green. **IC-owner-only lock authz** = `authorizePlanMutation` (access-chokepoint→codeless 404; owner-check→`403 PLAN_OWNER_REQUIRED`+audit for a manager-direct-report who can read but not lock) — the plan-level parallel of 3.4b's `authorizeCommitmentMutation` (§17 central authorizer). Note: the `ReconciliationOutcome` enum DOES include `CARRIED_FORWARD` (enum + V1 CHECK + B.1 + §3/§4/A all agree); at lock, carry-forward is derived from `carryForwardSourceCommitmentId != null` (the only signal available pre-reconciliation).

**Rule:** Lock = one `@Version` txn; the precondition reuses the `canLock` affordance predicate as a single accept-gate + a fail-closed diagnosis for the granular 409s (§15 single-source); the calendar publish is `afterCommit`→`REQUIRES_NEW`, swallow-never-rethrow, leaving `PENDING_PUBLISH` on failure (rule #4 non-blocking); a concurrent conflict maps to 409 (decomposed proof, not a threaded race); §9 projections recompute-from-source as inserts at lock, skipped entirely when there's no active manager.

---

## <a id="29"></a>29. `@SpringBootTest` context-cache holds a live Hikari pool per distinct context — cap `maximum-pool-size` in the shared test base or the suite exhausts PG `max_connections` as it grows

**Date:** 2026-06-03.
**Source slice:** 4.1 (outcome-recording-patch) — a test-infra fix surfaced when a new `@MockBean` context tipped the suite past PostgreSQL's `max_connections`.

Spring's `@SpringBootTest` context cache keeps **each distinct application context alive for the whole JVM test run**, and each cached context holds its own live Hikari connection pool. As the integration suite grows — and especially because **every distinct `@MockBean` set forks a new context-cache key** — the SUM of the cached pools climbs toward PostgreSQL's `max_connections` (default 100 on the Testcontainers PG16). At 4.1, adding a `@MockBean AuditService` atomicity test created a new cached context whose pool tipped the suite over the limit → **16 cascading `FATAL: sorry, too many clients already`** context-load failures in *unrelated* endpoint tests (PlanLock / StartReconciliation), NOT in the new test — a confusing non-local failure that looks like a regression in already-green slices.

**Fix:** cap `spring.datasource.hikari.maximum-pool-size=2` in the shared test-support base (`support/AbstractAppBootTest`). MockMvc integration tests are single-threaded, so 2 connections per context is ample; the cap bounds the total (cached-context-count × pool-size) deterministically regardless of how many contexts accumulate. `./gradlew check` green. Keep it in the **shared base** (it governs every `@SpringBootTest`), not per-test.

**Forward:** any slice that introduces a distinct `@SpringBootTest` context (especially via `@MockBean`, which forks the cache key) inherits the cap — don't raise it without re-checking `cached-context-count × pool-size` vs `max_connections`. If one test ever genuinely needs pool concurrency, scope a higher pool to that single context, never the shared base. (Pairs with the §9/§14 "what loads in which context" family of test-infra traps.)

**Rule:** Cap `spring.datasource.hikari.maximum-pool-size` (e.g. 2) in the shared `@SpringBootTest` base — each distinct (especially `@MockBean`) context holds its own live pool in Spring's context cache, and the sum exhausts PG `max_connections` as the suite grows, surfacing as non-local `too many clients` context-load failures in unrelated tests.

---

## <a id="30"></a>30. Carry-forward (E12) transaction pattern — carried-IN projection reading, create-if-absent next-week shell, idempotency via existence-prefilter + source `@Version`

**Date:** 2026-06-03.
**Source slice:** 4.4 (carry-forward-e12) — E12 `POST /api/commitments/{id}/carry-forward`, the most complex Phase-4 transition (a cross-week write in the locked-baseline neighborhood). **Ad-hoc security-reviewer: PASS (0 critical/high; 2 medium routed as Carry-forward hardening).**

Carry-forward is the **only** path that sets `reconciliation_outcome=CARRIED_FORWARD` (the E6 outcome-PATCH rejects a direct `CARRIED_FORWARD` → `400`, 4.1). It runs in a new `CarryForwardService` (not folded into `CommitmentService` — Appendix C.2), commitment-keyed, owner-only. The reusable patterns:

- **Carried-IN projection reading (the §9 `carry_forward_count` source-field pin).** `carry_forward_count` counts a week's commitments with `carryForwardSourceCommitmentId != null` — i.e. **successors carried IN** from a prior week (the existing 3.5 `ProjectionService.isCarryForward` predicate), confirmed by the Appendix-E R5 fixture (the CARRY_FORWARD badge sits on the current `RECONCILING` week, which holds the successor `C_next`). It does NOT count the source's `reconciliation_outcome=CARRIED_FORWARD` (carried OUT). Consequence: at carry-forward time the **source-week** projection's `carry_forward_count` does NOT change (no projection count keys on `reconciliation_outcome`); the count materializes when the **successor's** week is later locked (the successor lives in a DRAFT shell, unprojected until lock). So carry-forward recomputes the source plan in-txn for §9 lockstep but changes no count there, and needs **no `ProjectionService` change**. This corrected the 4.4 task-spec wording "source week `carry_forward_count`++" (it does not). [Pairs with the open `blocked_count`-source pin — both pinned at the manager-projection / reconciliation slice.]

- **Create-if-absent next-week shell (extends §23).** Resolve the next Mon–Sun week via `sourcePlan.getWeekStartDate().plusDays(7)` (the stored `week_start_date` is already a Monday `LocalDate`, so `plusDays(7)` is calendar-exact across year/DST — no zone conversion needed); `weekEndDate = +6`. `WeeklyPlanRepository.findByEmployeeIdAndWeekStartDate(ic, nextMonday)` → reuse the existing shell, else build a DRAFT shell mirroring `PlanShellGenerator` (`new WeeklyPlan` → random id, employeeId, weekStart/weekEnd, `state=DRAFT`), reusing on the V1 `unique(employee_id, week_start_date)` conflict (a concurrent cross-source shell create loses the INSERT → rolls back → IC retries → reuse). This is the §23 existence-prefilter + unique-backstop pattern extended to a cross-week create.

- **Idempotency = existence pre-filter on the self-link + source `@Version` serialization (no migration).** `WeeklyCommitmentRepository.findByCarryForwardSourceCommitmentId(sourceId)` FIRST: present → return the existing successor (no source re-touch, no shell create, no audit, no recompute — a pure idempotent read). Concurrent same-source double-carry is serialized by the **source `@Version`**: both txns set the source's outcome (a save → version bump); the loser's stale `@Version` → `OptimisticLockingFailureException` → `409`, rolling back its successor INSERT → at most one successor. An optional partial-unique `(carry_forward_source_commitment_id) WHERE NOT NULL` is the §23 second leg, deferred for MVP (the source `@Version` + the V1 `unique(employee,week)` cover the races today; routed as Carry-forward hardening). Don't add a flaky cross-source concurrency test — pin the same-source race via the stale-`@Version`-propagates unit test + shell reuse via the reuse test (the §23 "concurrent double-run rolls back, noted-not-handled" precedent).

- **Successor shape + single-outcome overwrite.** Carry-forward OVERWRITES any prior completion outcome on the source → `CARRIED_FORWARD` (the explicit carry action; §3 single-outcome rule — `CARRIED_FORWARD` is mutually exclusive with completion outcomes). The successor starts unlinked-DRAFT-equivalent: copies the source's content/chess fields (`title`, `description`, `priority`, `confidence`), `commitmentKind=PLANNED`, `supportingOutcomeId=null` (the IC re-links during next-week planning — R5), `alignmentStatus=NEEDS_REVIEW`, `reconciliationOutcome=null`, `carryForwardSourceCommitmentId=source.id`. **`workType` = the source's if it is a planned type, else `STRATEGIC`** — an UNPLANNED source cannot legally copy `workType=UNPLANNED` onto a PLANNED successor (the E5 planned≠UNPLANNED invariant). REQ-E-005: the cross-week write touches ONLY the source's `reconciliation_outcome` — the locked prior-week baseline is byte-identical (proven by the R5-style two-week chain integration test).

- **Authz = `authorizeCommitmentMutation` (commitment-keyed chokepoint, first statement).** E12 is `/api/commitments/{id}`-keyed → the per-commitment chokepoint (`403 COMMITMENT_OWNER_REQUIRED` for a manager-direct-report; IDOR `404` for a stranger), NOT `authorizePlanMutation` (which takes a `planId` and would force loading the resource before authorizing — breaks chokepoint-first). Mirrors 4.1's outcome-PATCH + 3.4b's discard. IC audit `COMMITMENT_CARRIED_FORWARD` (free-string, safe metadata, no §15 edit); **no Outlook sync record** (§10 has no carry-forward trigger — pinned by a zero-sync-records assertion distinguishing E12 from E9 start-reconciliation).

**Rule:** Carry-forward (E12) = a commitment-keyed, owner-only (`authorizeCommitmentMutation`) cross-week write: overwrite the source outcome → `CARRIED_FORWARD` (sole writer), create a self-linked successor in the next Mon–Sun DRAFT plan (create-if-absent shell, §23 pattern), idempotent per source via existence-prefilter on the self-link + source-`@Version` serialization (no migration); `carry_forward_count` is the carried-IN (successor-link) reading so the source-week count is unchanged and no `ProjectionService` change is needed (it materializes at next-week lock); the successor copies content + a planned `workType` (UNPLANNED→`STRATEGIC`); the locked prior-week baseline stays byte-identical (REQ-E-005); no Outlook sync record (§10).

---

## <a id="31"></a>31. Affordance-subset pattern — a per-commitment affordance may NARROW enforcement for UX (subset single-source), via a context-aware mapper overload

**Date:** 2026-06-03.
**Source slice:** 4.4b (carry-forward-affordance) — emit `CARRY_FORWARD` in `WeeklyCommitmentDto.allowedActions` on the plan read. Read-path slice (no security-reviewer).

A server-authoritative `allowedActions` affordance (§24) is usually computed from the SAME predicate the lifecycle service enforces (3.5: `PlanLifecycleService` calls `canLock` AS its accept-gate → affordance == enforcement, literal identity). But an affordance MAY be a deliberate UX-narrowed **subset** of enforcement — and when it is, the single-source invariant is weaker, by design:

- **The invariant is the subset direction: `affordance-eligible ⟹ enforcement-accepts` ("no affordance without enforcement").** Carry-forward (E12) accepts any owned commitment in a `RECONCILING` plan regardless of outcome — an already-carried re-invoke is **idempotent-accepted** (returns the existing successor, 200; REQ-D-006). The `CARRY_FORWARD` affordance ADDS a hide-once-carried narrowing (`reconciliationOutcome != CARRIED_FORWARD`) — re-carry is a pointless idempotent no-op, so the UI doesn't surface it. So `canCarryForward ⊊ canEnforce`. Pin the subset direction with a parameterized **eligibility⟹preconditions sweep** (over state × outcome × ownership × kind) asserting every affordance-eligible case satisfies the enforcement gate — NOT literal predicate identity.
- **Do NOT make the service reuse the narrowed affordance predicate.** Reusing `canCarryForward` as E12's gate would reject an already-carried re-invoke with a 409, breaking the shipped+tested idempotency (REQ-D-006). The service keeps its own (broader) gate; the resolver owns the (narrower) affordance. They share the *direction* (subset), not the *predicate*.
- **The reverse direction intentionally does NOT hold.** Enforcement accepts cases the affordance hides (the idempotent re-carry). Showing FEWER affordances than strictly enforced is always safe — it never offers an action the server would reject; it only declines to surface a pointless one.

**Mechanism — a context-aware mapper overload (keeps existing callers empty):** `WeeklyCommitmentDto.allowedActions` is empty by default (since 3.3a). Add `AllowedActionResolver.commitmentActions(actorEmployeeId, plan, commitment) → List<AllowedAction>` (resolver-owned, not inlined — single-source + unit-testable; extensible as OPEN_DISPUTE/COMMENT land). The mapper grows a context-aware overload `toDto(commitment, plan, actor)` that fills `allowedActions` via the resolver; the **no-arg `toDto(commitment)` stays empty** (the E5/E6/E7/E11/E12 single-commitment write responses keep it — the UI re-reads the plan via RTK cache invalidation). Thread the overload only where the plan + actor are in scope AND the affordance is consumed: the **plan read** (`PlanMapper` for E3/E4) is the must-have. A successor in a fresh DRAFT plan never qualifies, so the E12 response needs no threading (it would be a no-op). (`commitment.mapper` → `plan` pkg is an intra-`:api` dep — no module-boundary issue; `PlanMapper` already bridges both, no cycle.)

**Rule:** A per-commitment affordance may deliberately NARROW the enforcement predicate for UX (a subset, not literal identity like §24/`canLock`); the invariant to pin is the **subset direction** (`affordance-eligible ⟹ enforcement-accepts`, via an eligibility⟹preconditions sweep), and the service keeps its own broader gate (never reuse the narrowed predicate — it would break idempotency/other accepted paths). Realize it via a context-aware `toDto(commitment, plan, actor)` overload (no-arg stays empty) threaded through `PlanMapper` on the read path.

---

## <a id="32"></a>32. Authorship/mutation endpoints use `authorize…Mutation` (owner-only), NEVER `authorize…Access` (the read-authorizer admits managers) — a §6 hole

**Date:** 2026-06-03.
**Source slice:** the E5 authz fix (brief 055) — an escalated Finding (handoff 005), lead-greenlit, fixed at the Phase-4→5 boundary. **Ad-hoc security-reviewer: CLEAN PASS (0 findings).**

The central authorizer (§17) exposes two families with **different scope**, and confusing them is a real §6 authorization hole:

- **`authorize…Access(principal, id)`** — admits the **IC-owner OR an active manager-of-owner** (a manager may *read* a direct report's resource). Correct for READ endpoints (E4 `GET /plans/{id}`, E14 heatmap, etc.).
- **`authorize…Mutation(principal, id)`** — access-check **first** (cross-team/missing → IDOR-safe `404`, identical to `…Access`), **then** an owner-check → a manager-of-owner who passed access gets **`403 …_OWNER_REQUIRED` + a denial audit**; only the owning IC (or SYSTEM) is authorized. Correct for every WRITE — authorship (create) + mutation (update/delete/lifecycle).

**The bug:** 3.4a's E5 `POST /api/plans/{id}/commitments` (create) used `authorizePlanAccess` — so a manager-direct-report could **author** a PLANNED commitment on a report's DRAFT plan (violating §6 IC-only-authorship, rule-#3-adjacent). It shipped because **no test asserted the manager-create case** (the absence of a manager-403 test is exactly why the hole went unnoticed). Fix = the one-line swap `authorizePlanAccess` → `authorizePlanMutation` on the create path (E11/E6/E7/E8/E9/E10/E12 were already on a mutation authorizer; `PlanService` E4 *read* correctly keeps `…Access`).

**The regression-pin recipe:** seed the manager with an **active relationship** to the owner (so they genuinely pass the access-check) → assert the write returns `403 …_OWNER_REQUIRED` (a capability denial), NOT `404` (an access denial). A no-relationship manager would get `404` and wouldn't prove the hole — the active relationship is load-bearing. Assert exactly one safe `AUTHORIZATION_DENIED` audit (rule #3). Reproduce RED first (the manager gets `201`/`200` on the buggy code), then swap → GREEN. Pairs with §17 (central authorizer) + §27 (the `authorize…Mutation` access-then-capability pattern).

**Rule:** Authorship + mutation endpoints authorize with `authorize…Mutation` (owner-only → `403 …_OWNER_REQUIRED`+audit for a manager-of-owner; IDOR `404` for cross/missing), NEVER `authorize…Access` (which admits managers — a §6 authorship hole; correct only for reads). Every new write endpoint needs a manager-of-owner-403 test (seed an active relationship → 403-not-404) — its absence is how the E5 hole shipped. (Forbidden-pattern #7.)

---

## <a id="33"></a>33. Manager-capability mutation authz + the 404-vs-403 namespace-legitimacy decision tree

**Date:** 2026-06-03.
**Source slice:** 5.2 (mark-reviewed E16) — the first **manager-side** write. **Ad-hoc security-reviewer: CLEAN PASS (0 findings).** Drains the `MANAGER_ROLE_REQUIRED` Carry-forward (origin 2.6 — its first real consumer).

`authorize…Mutation` (§32) is owner-only where "owner" = the IC. The **manager-side** inverse — `authorizeReviewMutation` (and, coming, dispute-resolve / managerAlignmentNote) — is **manager-of-owner-only**: the capability holder is the active direct manager, and the IC-owner is *denied* the mutation. Same shape (access-check first, then the capability check), capability inverted.

**The 404-vs-403 decision for a genuine capability denial turns on _namespace-legitimacy_, not artifact-ownership:** when the denied principal passed the access-check (the resource exists + they can see it) but lacks the capability —
- **`403` + a capability code** when the principal **legitimately uses the resource's API namespace** (so existence is not hidden from them): a manager-of-owner mutating a plan/commitment → `403 …_OWNER_REQUIRED` (they read it via E4); the **IC** resolving a dispute → `403 IC_CANNOT_RESOLVE` (the IC legitimately uses `/api/disputes/*` to *respond*, E18).
- **`404` IDOR (codeless)** when the principal has **no legitimate endpoint in that namespace** (the whole namespace is existence-hidden from them): the **IC** calling `POST /api/manager/reviews/{id}/mark-reviewed` → `404` — the IC has no `/api/manager/*` endpoint and views their review via `/api/plans` (E3/E4), so `/api/manager` is existence-hidden from them. (Don't "fix" this asymmetry to match the dispute case — it's deliberate; document the rationale in the authorizer javadoc.)

**Both flavors AUDIT** — a genuine capability denial writes one safe-metadata `AUTHORIZATION_DENIED` event whether it surfaces as `403` or `404` (the `404`-dressed denial is still genuine — use the *audited* deny path with a distinct reason, e.g. `not_direct_manager`, NOT a bare not-found throw). **Only a truly-missing resource is the no-audit `404`** (§25). Implement the `404`-with-reason via a `deny404(…, reason)` overload paralleling `deny403` (§18 build-and-throw).

**`MANAGER_ROLE_REQUIRED` is for the COARSE gate only** — "you are not a manager at all" (the team-heatmap/command-center entry, `authorizeTeamHeatmap`). A *per-resource* manager-capability denial is NOT `MANAGER_ROLE_REQUIRED`; it's the namespace-legitimacy `404`/`403` above. (This is what closed the `MANAGER_ROLE_REQUIRED` Carry-forward correctly — via the coded+audited central authorizer, never a coarse `@PreAuthorize`, which would give a codeless 403 + no denial audit.)

**Rule:** Manager-side mutations use a manager-capability `authorize…Mutation` (active-direct-manager-only; the IC-owner is denied). A genuine capability denial is `403`+code when the principal legitimately uses the resource's namespace, else `404` IDOR — but **both audit** (only truly-missing skips); reserve `MANAGER_ROLE_REQUIRED` for the coarse not-a-manager-at-all gate. Extends §27/§32 to the manager side.

## <a id="34"></a>34. Gated exception to a safety invariant + the IC-owner-capability authz variant (dispute respond, E18)

**Date:** 2026-06-03.
**Source slice:** 5.4 (respond-dispute E18) — the IC's response to a manager's alignment dispute. **Ad-hoc security-reviewer: CLEAN PASS (0 findings).**

**The exception.** E18 respond lets the owning IC revise the disputed commitment's `supportingOutcomeId` — a deliberate carve-out of **rule #2 (locked-baseline immutability)**, so the IC can re-align exactly the field the manager flagged. A locked PLANNED commitment's `supportingOutcomeId` is otherwise frozen (`LOCKED_BASELINE_EDIT`); this is the only post-lock SO-write path besides the 4.5 `(RECONCILING, UNPLANNED)` allow-list cell.

**A gated exception to a safety invariant is SAFE iff it is escape-proof** — the five properties the security-reviewer verified, reusable as a checklist for any future invariant carve-out:
1. **Exactly one writable field.** The request DTO (`RespondDisputeRequest`) carries `supportingOutcomeId` and nothing else baseline — a full setter inventory confirms no title/description/priority/workType/confidence/alignmentStatus path. Pinned by a `doesNotTouchOtherBaselineFields` test at **both** the service and endpoint layers.
2. **Double-gated.** The authz chokepoint runs first (owning-IC-only), then a state guard (`dispute OPEN`). Neither alone admits the write.
3. **Target loaded by an owning id from the trusted entity, never from the request.** The commitment is loaded by `dispute.getCommitmentId()` — so a dispute on commitment-X can never revise commitment-Y's SO (no cross-resource vector). Had the request carried the commitment id, the exception would leak across the aggregate boundary.
4. **Audited with safe metadata.** `DISPUTE_RESPONDED` records `supportingOutcomeRevised` + the new SO id (RCDO reference ids, §15-safe) — the baseline mutation has a trail — while the `icResponse` free text stays out (§15/§18).
5. **The carve-out lives in the special path, not a widened general gate.** The E6 PATCH `LOCKED_BASELINE_EDIT` allow-list stays dispute-unaware/unchanged (`CommitmentService` untouched). Never widen the general baseline gate to admit the special case — keep the exception local to the one path that owns it.

**The authz is the §33 tree in its IC-owner-capability direction** (the inverse of manager-capability): respond is owning-IC-only, so the only non-owner who passes the access-check is the active direct manager — who lacks the *respond* capability and gets `403 MANAGER_CANNOT_RESPOND_DISPUTE` (they legitimately use `/api/disputes` via open E17 / resolve E19, so existence isn't hidden), while an unrelated/non-owning actor gets `404` IDOR. The exact mirror of resolve's `IC_CANNOT_RESOLVE_DISPUTE` (§33). Both denials audit; chokepoint-first (`verify(disputes, never()).save` on a denied authorize). `@Version` on both the dispute and commitment saves (concurrent edit → OLE → 409).

**Rule:** A new path may carve an exception to a safety invariant only when it is escape-proof — exactly one writable field, double-gated (authz + state), target loaded by an owning id from the trusted entity (never the request), audited (safe ids only), and the carve-out kept local to that path (never widen the general gate). Authz follows the §33 namespace-legitimacy tree in whichever capability direction the endpoint owns (here IC-owner-capability → manager `403`, unrelated `404`). Extends §32/§33.

## <a id="35"></a>35. Per-viewer affordances on a shared read — compute the relationship-context once at the mapper root

**Date:** 2026-06-03.
**Source slice:** 5.5b (dispute affordances — read-path). No security-reviewer (mirror-enforcement).

A read DTO whose `allowedActions[]` depend on **who is viewing** (owner-IC vs active-direct-manager) — not just the row state — needs the viewer's relationship determined ONCE at the aggregate-mapper root and threaded down as a boolean, never re-derived per child.

- **Compute once, thread the boolean.** `PlanMapper` resolves `viewerIsDirectManager` with a single `findByDirectReportEmployeeIdAndActiveTrue(plan.ownerId)` lookup per read (pinned `verify(times(1))`), then passes the boolean (+ the owner id) through `CommitmentMapper` → the per-dispute `DisputeMapper` + `AllowedActionResolver.commitmentActions`. Do NOT inject the relationship repo into the per-child resolver/mapper (it would N-query) — keep the resolver **repo-free**; the caller passes the booleans (mirrors how it already passes `actorEmployeeId`).
- **Reuse an already-resolved child** for derived conditions. `OPEN_DISPUTE`'s "no unresolved dispute" reuses the 5.3b nested-dispute resolution (`hasUnresolvedDispute = resolvedDispute != null`), not a second query.
- **Affordance predicates MIRROR — don't share — the void-throw authorizers.** `authorize…Mutation`/`authorizeDisputeResolution`/`authorizeDisputeResponse` are void-and-throw, so they can't be reused as predicates; the affordance is the parallel boolean form (`RESPOND_DISPUTE = actor==owner ∧ OPEN` mirrors E18; `RESOLVE_DISPUTE = manager ∧ {OPEN,IC_RESPONDED}` mirrors E19; `OPEN_DISPUTE = manager ∧ state≠DRAFT ∧ !hasUnresolvedDispute` mirrors E17). The §24 invariant that holds is the subset direction (affordance-true ⟹ enforcement-accepts), pinned by an eligibility⟹preconditions sweep (§31).
- **Trace EVERY caller of the shared mapper** before adding per-viewer logic — a per-viewer affordance on a shared mapper silently leaks to every caller otherwise. Confirm where the manager affordances must NOT appear: the lock/start/close lifecycle responses route through the same `PlanMapper` but are owner-only (`viewerIsDirectManager=false`); the E15 heatmap drilldown does NOT use `PlanMapper` (no leak).

This extends 4.4b (owner-only `commitmentActions`) to the manager direction. **Phase-6 MARK_REVIEWED + the projection reuse the `viewerIsDirectManager` threading** established here.

**Rule:** Per-viewer `allowedActions` on a shared read DTO = resolve the viewer-relationship ONCE at the aggregate-mapper root, thread booleans (not repos) to the per-child affordance computations, mirror the void-throw authorizers as parallel predicates (subset-pinned, §24/§31), and trace every caller of the shared mapper to confirm no affordance leaks where the viewer isn't entitled. Extends §24/§31/4.4b.

## <a id="36"></a>36. Field-level authz on a shared mutation endpoint

**Date:** 2026-06-03.
**Source slice:** 5.7 (managerAlignmentNote on E6 PATCH). **Ad-hoc security-reviewer: CLEAN PASS (0 findings).**

When one field of a shared mutation endpoint is owned by a **different actor** than the rest — E6 PATCH's `managerAlignmentNote` is **manager-of-owner-only**, while every other E6 field is **IC-owner-only** — the endpoint's authz becomes **field-dependent**. Branch it cleanly:

- **Single-actor-per-patch + a request-shape mixing-`400`.** If the manager-field is provided AND any IC field is also provided → `400 VALIDATION_ERROR`. Place this check as **request-shape validation, BEFORE any resource load / authz** (like `@Valid`): it's actor- and resource-independent (a mixed patch → `400` for ANY id, revealing nothing), so it's IDOR-safe and forecloses privilege-confusion. A single patch is one actor's.
- **Route to the actor-appropriate chokepoint.** A manager-field-only patch → the manager-capability authz (`authorizeManagerAlignmentNote`, §33 commitment-field manager-capability — IC-owner → `403 IC_CANNOT_WRITE_MANAGER_NOTE`, unrelated/non-direct → IDOR `404`, both audited); everything else → the existing IC-owner chokepoint (`authorizeCommitmentMutation`), **unchanged**.
- **The other actor's path stays byte-for-byte unchanged.** Prove it (a no-regression test on the IC path). The manager-field's sole production setter call-site is the manager branch — the IC path is *structurally unable* to set it (the security-reviewer verified the call-graph), so there's no privilege-escalation vector even if the field somehow appeared on the IC path.
- **Pin the non-leak + scope:** `doesNotTouchBaseline` (the manager-field write touches ONLY that field), the audit-has-no-note-body (§15), the chokepoint (`verify(repo, never()).save` on a denied authorize).

This is distinct from the §27 per-(state×kind) allow-list (which gates the **same** actor's fields by plan state) — here the gate is by **actor/capability**, on the same endpoint. The authz reuses the §33 commitment-field manager-capability shape (`authorizeDisputeCreation` is the sibling); a 3rd such commitment-keyed-`403` authorizer would warrant extracting a generic `authorizeCommitmentManagerCapability(principal, commitmentId, code)` (review-mutation is excluded — it uses the `404` namespace tree, §33).

**Rule:** Field-level authz on a shared mutation endpoint = a request-shape single-actor-per-patch `400` (before any load, IDOR-safe), then route the actor-distinct field to its own capability chokepoint (§33), leaving the other actor's path byte-for-byte unchanged (no-regression-pinned) with the field structurally unsettable from it. Extends §27/§33.

## <a id="37"></a>37. Projection counts that depend on a sibling table — load-once-derive-in-memory + correct a count's SOURCE against the binding seed

**Date:** 2026-06-03.
**Source slice:** 6.2 (§9 ProjectionService derivation completion). Read-model only — no security-reviewer.

A synchronous projection count whose source spans a **sibling table** (the §9 `misaligned_count` union + `unresolved_dispute_count` both read `alignment_dispute`, keyed on the plan's commitments) loads that table **once** over the grain's id set and derives every dependent count + badge in-memory — never a per-row lookup:

- **One query over the id set.** Inject the sibling repo into the projection service and fetch the relevant rows for the whole grain in a single finder (`findByCommitmentIdInAndStatusIn(ids, {OPEN,IC_RESPONDED})` returns the rows, so the same result set yields both the misaligned-union set (`flagType==MISALIGNED`) and the unresolved count). **Guard the empty id set** — an empty SQL `IN` is invalid, so short-circuit (no query) when the grain has no rows. No N+1.
- **Load internally, keep caller signatures stable.** Loading the sibling rows *inside* `recompute(...)` (not threaded in by each caller) left all 5 existing call sites — incl. the safety-critical lock txn — byte-for-byte unchanged; the constructor gained one arg (only the unit test constructs the service). Lowest blast radius.
- **Dedup at the per-commitment grain** so a commitment that satisfies the union two ways (alignment-MISALIGNED *and* a MISALIGNED dispute) counts once; the same union feeds the count *and* the risk-badge so they can never disagree.

**The source-predicate trap (the load-bearing half):** correct a count's **source predicate against the binding seed (`rebuild==seed`, §9/§17)** — not against the field that's merely *similarly named*. `blocked_count` had shipped (3.5) deriving from `work_type=BLOCKER` — a planning **category** — but the binding R5 Grace fixture shows a `BLOCKED` badge from a `reconciliation_outcome=BLOCKED` commitment (a reconciliation **risk-outcome**, sibling of `carry_forward_count=CARRIED_FORWARD`, ARCH §1127/§1135/§1387). The `work_type` derivation would NOT reproduce R5 ⇒ fails `rebuild==seed`, and false-positives on a COMPLETED blocker-category task. The seed is the spec: when a count's source is ambiguous, the fixture the rebuild job must reproduce **decides it** — pin against that, not the plausible-looking column.

**Rule:** A projection count over a sibling table loads once over the grain's id set (empty-`IN`-guarded) and derives all dependents + badges in-memory from that one result (dedup per grain row; the union feeds count *and* badge); and when a count's source predicate is ambiguous, pin it against the binding seed the rebuild job must reproduce (`rebuild==seed`, §17) — not a similarly-named field. A wrong source is a latent bug even when "green."

**Addendum (6.3b):** a synchronous recompute must also **DELETE grain-rows the new source no longer produces** — it is recompute-**then-prune**, not upsert-only. `ProjectionService.upsertHeatmapCells` was upsert-only; a dispute-respond rule-#2 SO revision remaps a commitment to a new Defining Objective, orphaning the old cell. Load the existing grain-rows, compute the new set, delete those whose key ∉ the new set (the summary grain is stable — only cells go stale). Pin with a **remap test** (a commitment moves DO-X→DO-Y ⇒ no DO-X cell). Otherwise incremental ≠ rebuild (§17/RISK-003 drift).

## <a id="38"></a>38. A new synchronous FK-writing side-effect on an existing service method ripples into every driver test's teardown

**Date:** 2026-06-03.
**Source slice:** 6.3b (wiring the projection refresh into the dispute/review services). Cost a full-suite debug pass.

When you wire a **new synchronous side-effect that persists FK-referencing rows** into an **already-tested** service method (6.3b made `DisputeService.open/respond/resolve` + `ReviewService.markReviewed` write `manager_plan_summary`/`manager_heatmap_cell`, which FK to `employee`/`weekly_plan`/`defining_objective`), **every existing test that drives that method now leaks those child rows** — and its `@AfterEach` teardown doing `plans.deleteAll()` / `employees.deleteAll()` **FK-fails** (the new child rows still reference the parent), aborting cleanup and **leaking rows that cascade into UNRELATED test classes as opaque `ConstraintViolationException`s**. The symptom is non-local: 6.3b surfaced **64 cross-class failures** (`MeEndpointTest`, `ReviewStatusDeriverTest`, `JpaEntityMappingTest`…) with nothing to do with the change — the real cause is the now-side-effecting op's drivers not clearing the new tables.

**Fix + discipline:** when a service method becomes side-effecting on a new (esp. FK-child) table, **trace ALL its drivers** (every test that calls it, directly or via its endpoint) and add the new child-table deletes to their teardown in **FK-safe order** (children before parents: `summaries`/`heatmapCells` before `plans`/`employees`). 6.3b added those deletes to the 4 dispute/review endpoint tests' `@AfterEach`.

**Rule:** A new synchronous FK-writing side-effect on an existing service method is a **test-teardown ripple**, not just a production change — trace every driver and clear the new child rows first in their cleanup, or the leak surfaces as opaque cross-class FK violations far from the change. Distinct from §29 (context-cache pool exhaustion); this is teardown-FK-order.

**Addendum (6.4) — the intra-table self-FK sibling.** The same teardown-FK class also bites a table with a **self-referential FK**: `weekly_commitment.carry_forward_source_commitment_id` references `weekly_commitment(id)`. A per-row `commitments.deleteAll()` deletes one statement at a time, and if a carry-forward **source** is deleted before its **successor**, the `NO ACTION` self-FK fails at that statement-end → the same abort-and-cascade-into-unrelated-tests. **Use `deleteAllInBatch()`** — a single `DELETE FROM` → the self-FK is checked once, after all rows are gone → order-independent and deterministic. Latent since Phase 4; surfaced at 6.4 when a new test class shifted execution order (same flaky class as 6.3b). **Rule:** clear a self-referential-FK table with `deleteAllInBatch()`, never per-row `deleteAll()`.

## <a id="39"></a>39. A paginated REST response whose contract carries a `sort` field needs a CUSTOM envelope record — stock Spring `Page` serialization (and `VIA_DTO`) omits it

**Date:** 2026-06-04.
**Source slice:** 6.5a (E13 command center — the first paginated endpoint).

The B.20 contract pins the envelope as `{ content: T[], page: { number, size, totalElements, totalPages }, sort: [{ property, direction }] }`, and the frontend parses exactly that. Spring Boot 3.x **deprecated direct `Page<T>` serialization** (unstable JSON + a startup warning); the sanctioned replacement `@EnableSpringDataWebSupport(pageSerializationMode=VIA_DTO)` emits a `PagedModel` of `{ content, page:{...} }` — **with NO `sort` field**. Neither stock path produces the contracted shape.

- **Realize a custom `PageEnvelope<T>` record** built from the `Page<T>` (`getNumber/getSize/getTotalElements/getTotalPages` + the `Sort` orders → `[{property,direction}]`); `List.copyOf` the content in the compact constructor (EI-safety, §22). Deterministic + version-agnostic; reused by every paginated surface (E13, E15 drilldown, comments).
- **Request side stays stock:** accept Spring's `Pageable` controller param + set `spring.data.web.pageable.default-page-size`/`max-page-size` (25/100) — the first paginated endpoint sets these app-wide.
- **Sort by a joined non-association column** (e.g. `employeeDisplayName`, resolved by a cross-join over a flat-UUID FK with no JPA association): a `JpaSpecificationExecutor`/derived finder **cannot** sort by it (no association path) — use a **Criteria query** (it controls the join + `ORDER BY` + the page+count pair, N+1-free). Keep it an **api-layer `@Repository` query component** when its tests live in `api` (a Criteria impl in `shared` exercised only by `api` tests tanks `shared`'s per-module JaCoCo).

**Rule:** when a pagination contract carries `sort` (or any field stock serialization drops), build a custom envelope record over `Page<T>` — don't rely on `VIA_DTO`. Sort-by-a-joined-non-association-column ⇒ a Criteria query, co-located with its tests.
