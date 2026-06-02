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
