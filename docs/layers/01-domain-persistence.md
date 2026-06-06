# Domain Model & Persistence

## Executive summary

This is the foundation layer of the ST6 Weekly Commit backend: the `shared` Gradle module that defines *what the data looks like* and *how it is stored*. It holds the JPA entity classes (the in-memory shape of every business object), the Java enums that pin the legal values for status-style columns, the Spring Data repository interfaces (the lookup methods every other layer uses to read and write rows), and the Flyway SQL migrations that physically create the PostgreSQL 16 tables, constraints, indexes, and seed data. Because every other backend module (`api` and `worker`) depends on `shared`, this layer is the single source of truth for the domain's structure — change a field here and the whole system feels it. It deliberately owns *structure only*: it declares the tables and the shapes but contains almost no behavior, no lifecycle rules, and no authorization — those live in the application/service layers built on top of it. The headline asset is the RCDO strategy hierarchy (RallyCry → DefiningObjective → SupportingOutcome) plus the weekly-lifecycle aggregates (WeeklyPlan, WeeklyCommitment) and the database-level guarantees (FK to a Supporting Outcome, partial unique indexes) that make the system's safety rules provably consistent at the storage layer.

## Responsibilities

- **Owns the entity shapes.** 14 JPA `@Entity` classes mapping the 12 V1 core tables + 2 V3 projection tables (`apps/wc-api/shared/src/main/java/com/st6/wc/**`), each a plain Lombok `@Getter/@Setter` POJO over flat `UUID` foreign keys — never `@ManyToOne` associations (LESSONS §7).
- **Owns the enum vocabulary.** 16 enums in `enums/` are the executable mirror of the `VARCHAR`+`CHECK` status columns (`apps/wc-api/shared/src/main/java/com/st6/wc/enums/`), pinned by `EnumVocabularyTest`.
- **Owns the physical schema.** Flyway migrations V1–V4 in `db/migration` create tables, constraints, partial unique indexes, and reference seed; V5–V6 in `db/demo-seed` carry demo personas/fixtures (a separate Flyway location).
- **Owns the persistence-mechanics base classes.** `PersistableUuidEntity`, `AbstractAuditingEntity` (`@MappedSuperclass` bases), `ClockConfig` (injectable `Clock`), and `OrgTimeConfig` (org-timezone + Mon–Sun week resolver).
- **Owns the repository seam.** 14 Spring Data `JpaRepository` interfaces + their derived-name finders.
- **Does NOT own behavior.** No lifecycle transitions, no authorization, no projection recomputation, no audit *writing*, no DTO mapping, no calendar sync. Those are delegated: lifecycle/authorization/projection-write logic lives in `:api` services (see [03-application-lifecycle.md](03-application-lifecycle.md), [04-authorization-identity-audit.md](04-authorization-identity-audit.md), [05-manager-projections.md](05-manager-projections.md)). This layer only declares the structures those layers operate on.
- **Does NOT bind config values.** `OrgTimeConfig`/`ClockConfig` declare the seam; the actual `app.org.timezone` binding + fail-safe lives in `:api`'s `OrgTimeBindingConfig` (`apps/wc-api/api/src/main/java/com/st6/wc/config/OrgTimeBindingConfig.java:24`), outside this layer.

## Key components

| Component | What it does | Where |
|-----------|--------------|-------|
| `PersistableUuidEntity` | `@MappedSuperclass` base: UUID `@Id` + `@Version` optimistic-lock token, on top of the audit quartet | `apps/wc-api/shared/src/main/java/com/st6/wc/common/PersistableUuidEntity.java:19` |
| `AbstractAuditingEntity` | `@MappedSuperclass` base: the four audit columns `created_by/created_at/updated_by/updated_at` | `apps/wc-api/shared/src/main/java/com/st6/wc/common/AbstractAuditingEntity.java:18` |
| `ClockConfig` | Spring `@Configuration` exposing an injectable `Clock` bean (system default zone) for derived/time-based state | `apps/wc-api/shared/src/main/java/com/st6/wc/config/ClockConfig.java:14` |
| `OrgTimeConfig` | Pure org-time helper: default zone `America/Chicago`, Mon–Sun `weekStartDate`/`weekEndDate` resolvers | `apps/wc-api/shared/src/main/java/com/st6/wc/common/OrgTimeConfig.java:14` |
| `RallyCry` | Top of the read-only RCDO hierarchy; audited, not versioned | `apps/wc-api/shared/src/main/java/com/st6/wc/rcdo/RallyCry.java:20` |
| `DefiningObjective` | 2nd RCDO tier; flat `rallyCryId` FK | `apps/wc-api/shared/src/main/java/com/st6/wc/rcdo/DefiningObjective.java:21` |
| `SupportingOutcome` | RCDO leaf; the mandatory link target for every locked planned commitment (safety rule #1) | `apps/wc-api/shared/src/main/java/com/st6/wc/rcdo/SupportingOutcome.java:22` |
| `Employee` | IC or manager identity; `role` is the authoritative `RoleType`; audited, not versioned | `apps/wc-api/shared/src/main/java/com/st6/wc/employee/Employee.java:25` |
| `ManagerRelationship` | Manager → direct-report edge; the single-active-manager invariant is the V2 partial unique | `apps/wc-api/shared/src/main/java/com/st6/wc/relationship/ManagerRelationship.java:21` |
| `WeeklyPlan` | Mutable lifecycle aggregate root; `state` `PlanState`; `@Version` | `apps/wc-api/shared/src/main/java/com/st6/wc/plan/WeeklyPlan.java:26` |
| `WeeklyCommitment` | Mutable lifecycle entity; carries the 4 contract deltas; nullable `supportingOutcomeId`; `@Version` | `apps/wc-api/shared/src/main/java/com/st6/wc/commitment/WeeklyCommitment.java:30` |
| `AlignmentDispute` | Mutable lifecycle entity; one-unresolved-dispute invariant is the V2 partial unique; `@Version` | `apps/wc-api/shared/src/main/java/com/st6/wc/dispute/AlignmentDispute.java:26` |
| `ManagerReview` | Mutable lifecycle entity; stored `status` has NO `OVERDUE` (rule #6 — derived); `@Version` | `apps/wc-api/shared/src/main/java/com/st6/wc/review/ManagerReview.java:24` |
| `AuditEvent` | Append-only audit row; own non-null `createdAt` (not an `AbstractAuditingEntity`); `metadata_json jsonb` | `apps/wc-api/shared/src/main/java/com/st6/wc/audit/AuditEvent.java:25` |
| 16 enums (`enums/`) | Java mirror of every `VARCHAR`+`CHECK` column vocabulary (REQ-D-010) | `apps/wc-api/shared/src/main/java/com/st6/wc/enums/` |
| 14 repositories (`*/repo/`) | Spring Data `JpaRepository` interfaces + derived-name finders | e.g. `apps/wc-api/shared/src/main/java/com/st6/wc/plan/repo/WeeklyPlanRepository.java` |
| `V1__core_schema.sql` | 12 core tables, `VARCHAR`+`CHECK` status columns, `version bigint` on 5 mutable tables | `apps/wc-api/shared/src/main/resources/db/migration/V1__core_schema.sql:1` |
| `V2__partial_unique_indexes.sql` | 3 partial unique indexes backing single-row safety invariants | `apps/wc-api/shared/src/main/resources/db/migration/V2__partial_unique_indexes.sql:1` |
| `V4__seed_rcdo.sql` | Deterministic RCDO reference seed: 1 RallyCry / 3 DOs / 9 SOs | `apps/wc-api/shared/src/main/resources/db/migration/V4__seed_rcdo.sql:1` |

## Interfaces & contracts

**What this layer exposes to `:api` and `:worker`:**

- **Entity classes** — the typed in-memory rows. Other layers construct, mutate (via Lombok setters), and persist them. Entities never cross the API boundary; mapping to DTOs is the `:api` layer's job (forbidden-pattern #3).
- **Repository interfaces** — the persistence seam. Each extends `JpaRepository<T, UUID>`, inheriting `save`/`saveAndFlush`/`findById`/`deleteAll`/etc., plus a handful of derived-name finders:
  - `EmployeeRepository.findByExternalSubject(String)` / `findByActiveTrue()` (`apps/wc-api/shared/src/main/java/com/st6/wc/employee/repo/EmployeeRepository.java`)
  - `ManagerRelationshipRepository.findByDirectReportEmployeeIdAndActiveTrue(UUID)` / `existsByManagerEmployeeIdAndActiveTrue(UUID)` (`apps/wc-api/shared/src/main/java/com/st6/wc/relationship/repo/ManagerRelationshipRepository.java`)
  - `WeeklyPlanRepository.findByWeekStartDate(LocalDate)` / `findByEmployeeIdAndWeekStartDate(UUID, LocalDate)` (`apps/wc-api/shared/src/main/java/com/st6/wc/plan/repo/WeeklyPlanRepository.java`)
  - `WeeklyCommitmentRepository.findByWeeklyPlanIdOrderByIdAsc(UUID)` / `findByCarryForwardSourceCommitmentId(UUID)` / `findByWeeklyPlanIdAndSupportingOutcomeId(UUID, UUID, Pageable)` (`apps/wc-api/shared/src/main/java/com/st6/wc/commitment/repo/WeeklyCommitmentRepository.java`)
  - `AlignmentDisputeRepository.findByCommitmentIdAndStatusIn(...)` / `countByCommitmentIdInAndStatusIn(...)` / `findByCommitmentIdInAndStatusIn(...)` (`apps/wc-api/shared/src/main/java/com/st6/wc/dispute/repo/AlignmentDisputeRepository.java`)
  - `ManagerReviewRepository.findByWeeklyPlanId(UUID)` (`apps/wc-api/shared/src/main/java/com/st6/wc/review/repo/ManagerReviewRepository.java`)
  - `OutlookCalendarSyncRecordRepository.findByOwnerEmployeeIdAndWeekStartDateAndEventKind(...)` (`apps/wc-api/shared/src/main/java/com/st6/wc/sync/repo/OutlookCalendarSyncRecordRepository.java:18`)
  - RCDO repos expose `findAllByOrderByIdAsc()` (id-asc is the logical strategy order — see "How it works"); `SupportingOutcomeRepository.findByDefiningObjectiveIdOrderByIdAsc(UUID)`.
  - Projection repos expose `findByManagerEmployeeIdAndEmployeeIdAndWeekStartDate(...)` (summary returns `Optional`, heatmap returns `List`).
  - `AuditEventRepository` / `CommentRepository` are bare `JpaRepository` with no custom finders.
- **Base-class accessors** — inherited `getId()`/`setId()`, `getVersion()`/`setVersion()`, and the four audit getters/setters (`BaseEntityShapeTest:54` exercises the inheritance chain).
- **`OrgTimeConfig` methods** — `zoneId()`, `weekStartDate(LocalDate)`, `weekStartDate(Instant)`, `weekEndDate(LocalDate)`. Pure and deterministic; the Monday resolver is `previousOrSame(MONDAY)`, the Sunday is `weekStartDate(date).plusDays(6)` (`OrgTimeConfig.java:36`,`:51`).
- **`ClockConfig` bean** — `Clock clock()` so time-based derivation (OVERDUE, SLA, cadence) reads "now" from a bean tests can replace with a fixed `Clock`.

**What it expects from others:** the actual `app.org.timezone` value (bound in `:api`), Flyway enabled only on the migration Job, and that callers map entities to DTOs before crossing the API boundary.

## Data & state

**The three base-class shapes (LESSONS §7).** Row shape picks the base:
1. **`PersistableUuidEntity`** (UUID `@Id` + `@Version` + audit quartet) — the 5 mutable lifecycle entities: `WeeklyPlan`, `WeeklyCommitment`, `AlignmentDispute`, `ManagerReview`, plus `OutlookCalendarSyncRecord` (the durable outbox; deferred to [06](06-calendar-sync-messaging.md)).
2. **`AbstractAuditingEntity` + inline `@Id`** (audited, no `@Version`) — `Employee`, `ManagerRelationship`, `RallyCry`, `DefiningObjective`, `SupportingOutcome`, `Comment`.
3. **Inline `@Id` + own timestamp** (no base class, no audit quartet, no `@Version`) — `AuditEvent` (single non-null `createdAt`, `AuditEvent.java:48`) and the two projection entities `ManagerPlanSummary`/`ManagerHeatmapCell` (single non-null `updatedAt`, recomputed wholesale).

**Persistence mechanics:**
- **IDs are app-assigned UUIDs** — `@Id` with no `@GeneratedValue`; `updatable = false, nullable = false` (`PersistableUuidEntity.java:21`). The service layer assigns the UUID before persist.
- **Optimistic locking** — `@Version private Long version` maps the `version bigint not null default 0` column (`PersistableUuidEntity.java:25`; `V1__core_schema.sql:93` etc.). A stale update surfaces as Spring's `ObjectOptimisticLockingFailureException` (proven behaviorally at the repo layer, LESSONS §11).
- **Auditing** — `AbstractAuditingEntity` declares `created_by/created_at/updated_by/updated_at` (`:20`–`:30`); the *base only declares the shape* — who/when population is wired elsewhere (the class javadoc says "Population (who/when) is wired in Phase 1").
- **Org-time** — `OrgTimeConfig` (default `America/Chicago`, `OrgTimeConfig.java:17`) resolves the Mon-anchored `week_start_date`/`week_end_date` for the weekly cadence; `ClockConfig` supplies the injectable `Clock` for time-derived state.

**The RCDO hierarchy** (read-only reference data, seeded by V4):
```
RallyCry  (rally_cry)                  ─ 1 row
   └─ DefiningObjective (defining_objective, rallyCryId FK)   ─ 3 rows
         └─ SupportingOutcome (supporting_outcome, definingObjectiveId FK) ─ 9 rows
```
Each tier is `id / title / description? / active` over a flat parent-id FK (`RallyCry.java`, `DefiningObjective.java`, `SupportingOutcome.java`).

**The 16 enums** (`enums/`, pinned by `EnumVocabularyTest:23`):

| Enum | Constants |
|---|---|
| `PlanState` | DRAFT, LOCKED, RECONCILING, RECONCILED |
| `RoleType` | IC, MANAGER |
| `CommitmentKind` | PLANNED, UNPLANNED |
| `Priority` | P0, P1, P2 |
| `WorkType` | STRATEGIC, MAINTENANCE, BLOCKER, UNPLANNED |
| `Confidence` | HIGH, MEDIUM, LOW |
| `AlignmentStatus` | ALIGNED, NEEDS_REVIEW, MISALIGNED |
| `ReviewStatus` | NOT_REVIEWED, REVIEWED_WITH_DISPUTES, REVIEWED *(no OVERDUE — rule #6)* |
| `DisputeStatus` | OPEN, IC_RESPONDED, RESOLVED |
| `ReconciliationOutcome` | COMPLETED, PARTIALLY_COMPLETED, BLOCKED, CANCELED, CARRIED_FORWARD |
| `FlagType` | NEEDS_REVISION, MISALIGNED |
| `CommentTargetType` | PLAN, COMMITMENT *(deferred to [08](08-comments-collaboration.md))* |
| `SyncRelatedType` | WEEKLY_PLAN, MANAGER_REVIEW_WEEK *(deferred to [06](06-calendar-sync-messaging.md))* |
| `EventKind` | IC_PLANNING, IC_RECONCILIATION, MANAGER_REVIEW_BLOCK |
| `SyncStatus` | PENDING_PUBLISH, QUEUED, SYNCING, SYNCED, FAILED, RETRY_REQUESTED *(deferred to [06](06-calendar-sync-messaging.md))* |
| `RiskBadge` | MISALIGNED, NEEDS_REVIEW, BLOCKED, CARRY_FORWARD, UNREVIEWED, OVERDUE_REVIEW *(used by projections — [05](05-manager-projections.md))* |

The **"status/enum columns are `VARCHAR` + `CHECK`, mirrored by Java enums" convention**: the DB stores statuses as `varchar(n) not null check (col in ('A','B',...))` (e.g. `V1__core_schema.sql:88` for `weekly_plan.state`), and Java binds them with `@Enumerated(EnumType.STRING)` (e.g. `WeeklyPlan.java:37`). The two are kept in lockstep two ways: `EnumVocabularyTest` pins each enum's constant set against Appendix B.1, and `V1CoreSchemaMigrationTest` parses `pg_get_constraintdef` over all status columns against `enum.values()` (LESSONS §3/§5).

**The `audit_event` table SHAPE** (you document the columns here; *write/usage semantics are deferred to [04](04-authorization-identity-audit.md)*):

| Column | Type / nullability | Notes |
|---|---|---|
| `id` | `uuid primary key` | app-assigned |
| `actor_employee_id` | `uuid` nullable, FK `employee(id)` | nullable = SYSTEM actor (`AuditEvent.java:31`) |
| `action` | `varchar(100) not null` | free-string action taxonomy (not a `CHECK` enum) |
| `entity_type` | `varchar(64) not null` | |
| `entity_id` | `uuid` nullable | |
| `summary` | `text not null` | |
| `metadata_json` | `jsonb` | **safe-only metadata — NO secrets/PII (safety rule #7)**; mapped via Hibernate `SqlTypes.JSON` to a `String` field (`AuditEvent.java:44`, LESSONS §8) |
| `created_at` | `timestamptz not null` | the row's own timestamp (no audit quartet) |

Indexes: `idx_audit_entity (entity_type, entity_id)`, `idx_audit_actor_created (actor_employee_id, created_at)`, `idx_audit_action_created (action, created_at)` (`V1__core_schema.sql:232`).

## Dependencies

- **Depends on:** Spring Data JPA + Hibernate 6 (`@Entity`, `@MappedSuperclass`, `JpaRepository`, `@JdbcTypeCode`), Jakarta Persistence annotations, Lombok (`@Getter/@Setter` only — never `@Data`, which breaks Hibernate identity), Flyway (runs the SQL), and PostgreSQL 16 (the `jsonb`/`text[]`/partial-index features the schema relies on). It is the *bottom* of the module graph — `shared` depends on no other project module.
- **Used by:** everything. `:api` (controllers → services → these repositories → these entities; see [02](02-api-web.md), [03](03-application-lifecycle.md), [04](04-authorization-identity-audit.md), [05](05-manager-projections.md)) and `:worker` (the SQS consumer reloads sync rows by id; see [06](06-calendar-sync-messaging.md), [07](07-scheduled-jobs.md)). The dependency direction is strictly `api → shared` and `worker → shared`, never reverse, with no `api ↔ worker` edge (enforced mechanically by the module-boundary gate, `apps/wc-api/CLAUDE.md` "Module organization").

## How it works (flow)

This layer has no runtime "flow" of its own — it is consumed. The two paths that *originate* here are migration application and entity↔DDL mapping.

**1. Schema creation (Flyway, on the migration Job).**
```
db/migration/V1 ──> 12 core tables + VARCHAR+CHECK + version + FKs + indexes
db/migration/V2 ──> 3 partial unique indexes (layered on V1)
db/migration/V3 ──> 2 projection tables (manager_plan_summary, manager_heatmap_cell)
db/migration/V4 ──> RCDO reference seed (ON CONFLICT DO NOTHING)
db/demo-seed/V5 ──> demo personas + relationships    (separate location)
db/demo-seed/V6 ──> demo fixture plans + lifecycle state
```
Flyway orders by version across both locations, so V5/V6 still run after V4 (`V4__seed_rcdo.sql:13`, LESSONS §41). The shared integration-test harness loads `db/migration` only, so demo rows never pollute clean-baseline assertions; the deploy migrate Job loads both locations (LESSONS §41).

**2. RCDO read ordering trick.** V4 assigns UUID literals *sequentially in logical order* — RC `a…0001`; DOs `b…0001/0002/0003`; SOs `c…0001..0009` (`V4__seed_rcdo.sql:7`). The RCDO repos' `findAllByOrderByIdAsc()` therefore reproduce the logical strategy order on the wire with no schema "order" column (LESSONS §22). V4 inserts parent-first (RC → DO → SO) to satisfy the V1 FKs and is idempotent via `ON CONFLICT (id) DO NOTHING`.

**3. Entity ↔ DDL mapping.** Each `@Entity` maps a V1/V3 table; FKs are flat `UUID` fields (`WeeklyCommitment.weeklyPlanId`, not a `@ManyToOne WeeklyPlan`). Non-scalar columns use Hibernate-6 type codes: `audit_event.metadata_json jsonb` → `String` via `@JdbcTypeCode(SqlTypes.JSON)` (`AuditEvent.java:44`); `manager_heatmap_cell.risk_badges text[]` → `List<RiskBadge>` via `@JdbcTypeCode(SqlTypes.ARRAY)` + `@Enumerated(STRING)` (deferred to [05](05-manager-projections.md), `ManagerHeatmapCell.java:75`). The entity↔DDL fidelity is proven under `@DataJpaTest` + a real PG16 + `ddl-auto=validate` (LESSONS §9).

**4. Safety-rule storage anchors.**
- *Rule #1 (required Supporting Outcome):* `weekly_commitment.supporting_outcome_id uuid references supporting_outcome(id)` is **nullable** (nullable-until-lock; `V1__core_schema.sql:111`, `WeeklyCommitment.java:44`). The FK guarantees referential integrity; the *lock precondition* (every planned commitment must be linked) is enforced in the service layer, not by a DB NOT NULL — see [03](03-application-lifecycle.md).
- *Rule #6 (one open dispute; OVERDUE derived):* the V2 partial unique `uq_one_unresolved_dispute_per_commitment ON alignment_dispute(commitment_id) WHERE status in ('OPEN','IC_RESPONDED')` (`V2__partial_unique_indexes.sql:15`) makes the single-row invariant DB-provable; a `RESOLVED` dispute drops out of the partial scope so a new one can open. There is **no `OVERDUE` column anywhere** — `manager_review.status` omits it (`V1__core_schema.sql:139`), `ReviewStatus` omits it (`EnumVocabularyTest:69`), and OVERDUE is computed at read time. *(The projection table's `is_review_overdue` column is a different, §9 read-model concept — not rule #6; see [05](05-manager-projections.md).)*

## Design decisions & rationale

- **Flat-UUID FKs over JPA associations (LESSONS §7).** Every FK is a bare `UUID` field, never `@ManyToOne`/`@OneToMany`. This avoids lazy-init surprises and over-fetching, keeps entities cheap to map to DTOs, and matches the "DTOs cross the boundary, never entities" rule. The cost is manual joins in the read layer (e.g. the manager command-center Criteria query cross-joins `employee` by UUID — see [05](05-manager-projections.md)).
- **Three base-class shapes, chosen by row semantics (LESSONS §7).** Mutable lifecycle rows get `@Version` (optimistic locking matters for concurrent lock/reconcile); reference + relationship rows are audited but unversioned; append-only/recomputed rows (`audit_event`, projections) carry only their own timestamp. This keeps the audit quartet and version token off rows where they would be dead weight or semantically wrong (you don't optimistically-lock an append-only audit row).
- **`VARCHAR`+`CHECK` over native PG enums (ARCHITECTURE §4 / Appendix B.1 / REQ-D-010).** Statuses are `varchar` columns with a `CHECK (col in (...))` constraint, mirrored by Java enums bound `@Enumerated(STRING)`. Native PG enums are painful to evolve and migrate; `VARCHAR`+`CHECK` plus an enum mirror gives the same safety with easier migrations, and the mirror is made *provable* by the dual tests (`EnumVocabularyTest` for the Java set, `V1CoreSchemaMigrationTest` parsing `pg_get_constraintdef` for the DB set). These enums are a cross-doc contract: a constant change requires a paired Appendix A/B.1 edit (`apps/wc-api/CLAUDE.md` cross-doc-invariants table).
- **Partial unique indexes for single-row invariants (ARCHITECTURE §4/§6/§3/§10).** Rather than enforce "one active manager per report" / "one open dispute per commitment" / "one review block per manager-week" in application code alone, V2 pushes them into `WHERE`-scoped unique indexes so the DB itself rejects a second row with SQLSTATE `23505`. The partial scope is the whole point: a superseded row (inactive relationship, RESOLVED dispute, non-review-block sync) coexists legally (`V2__partial_unique_indexes.sql:5`, proven both firing and non-firing in `V2PartialUniqueIndexTest:71`–`110`).
- **Deterministic seed via fixed UUID literals (ARCHITECTURE Appendix E Part 2 / §13).** V4–V6 use fixed UUID hex prefixes per entity type (`a`=RC, `b`=DO, `c`=SO, `d`=employee, `e`=relationship, `f`=plan…) and fixed timestamps anchored to `2026-06-02`, all `ON CONFLICT DO NOTHING`. This makes the demo reproducible and the seed idempotent, and lets the RCDO read derive its wire order from id-asc.
- **Injectable `Clock` (`ClockConfig`).** Derived state (OVERDUE, SLA due dates, weekly cadence) must read "now" from a bean tests can pin to a fixed instant — otherwise rule #6's read-time OVERDUE derivation would be untestable. `ClockConfig` lives in `:shared` because both `:api` and `:worker` consume it (`ClockConfig.java:8`).

## Gotchas & sharp edges

- **DRIFT — the assigned brief says "18 enums"; the code has 16.** There are exactly **16** enum files in `shared/enums/` (`ls` confirmed: `AlignmentStatus, CommentTargetType, CommitmentKind, Confidence, DisputeStatus, EventKind, FlagType, PlanState, Priority, ReconciliationOutcome, ReviewStatus, RiskBadge, RoleType, SyncRelatedType, SyncStatus, WorkType`), and both `EnumVocabularyTest` (`:23`–`:55`) and `ARCHITECTURE.md:359` independently assert **16**. The `apps/wc-api/CLAUDE.md` cross-doc table (`:123`) also says "16 enums." `AllowedAction`, `ErrorCodes`, and `ReviewStateFilter` are *computed DTO-layer vocabularies*, NOT `enums/` members — deliberately excluded to keep the 16-enum pin intact. Treat the brief's "18" as a brief error.
- **`supporting_outcome_id` is nullable in the schema — rule #1 is NOT a DB NOT NULL.** The FK guarantees the *target exists*; the "every planned commitment must link" precondition is a service-layer lock gate (`409 UNLINKED_PLANNED_COMMITMENT`), not a column constraint. Do not assume the database alone enforces rule #1 — see [03](03-application-lifecycle.md).
- **No `OVERDUE` anywhere in stored state (rule #6).** Searching for an `OVERDUE` review status will turn up nothing on purpose: `ReviewStatus` omits it, `manager_review.status`'s CHECK omits it, and `EnumVocabularyTest:69` actively asserts `ReviewStatus.valueOf("OVERDUE")` throws. The lookalike `manager_plan_summary.is_review_overdue` is a *projection* column (a different §9 concept) — see [05](05-manager-projections.md).
- **`metadata_json` must never carry secrets/PII (rule #7).** This layer declares the `jsonb` column shape; the *guarantee* that nothing sensitive lands there is enforced where audits are written (escaped JSON node, SENTINEL-pinned tests) in [04](04-authorization-identity-audit.md). The column note is the only safety surface this layer owns.
- **`@Data` is forbidden on entities (LESSONS §7, `forbidLombokData` gate).** Lombok `@Data`'s generated `equals`/`hashCode` break Hibernate identity. `BaseEntityShapeTest:44` and a build-task gate both assert no `@Data` on the base classes; entities use `@Getter/@Setter` only.
- **`ManagerHeatmapCell` hand-writes defensive-copy accessors for `risk_badges` (LESSONS §10).** Lombok's `@Getter/@Setter` would trip SpotBugs EI/EI2 on the mutable `List<RiskBadge>`, so the getter/setter are hand-written to copy (`ManagerHeatmapCell.java:89`). Safe because field-access JPA reads the field directly. (Detail belongs to [05](05-manager-projections.md), flagged here only because it lives in `shared`.)
- **`OrgTimeConfig` declares the seam but does NOT bind the timezone.** The `app.org.timezone` value + the "blank/invalid → fall back to `America/Chicago` + WARN, never UTC, never throw" fail-safe live in `:api`'s `OrgTimeBindingConfig` (`apps/wc-api/api/src/main/java/com/st6/wc/config/OrgTimeBindingConfig.java:24`), outside this layer. `OrgTimeConfig` itself is a plain final class with a no-arg default-zone constructor — it is not a Spring bean here.
- **`manager_review.weekly_plan_id` is `unique` (one review per plan).** Beyond the FK, V1 makes it `not null unique` (`V1__core_schema.sql:137`) — a structural one-review-per-plan guarantee that is easy to miss.
- **`weekly_plan` has a full `unique(employee_id, week_start_date)`** (`V1__core_schema.sql:98`) — one plan per employee per week, distinct from the V2 partials.

## Connects to

- **[02-api-web.md](02-api-web.md)** — controllers call services that call this layer's repositories; entities are mapped to DTOs at that boundary (entities never cross it).
- **[03-application-lifecycle.md](03-application-lifecycle.md)** — the `DRAFT→LOCKED→RECONCILING→RECONCILED` transitions on `WeeklyPlan`/`WeeklyCommitment`, the rule-#1 lock precondition (the nullable `supporting_outcome_id` FK is enforced-at-lock there), locked-baseline immutability (rule #2), and read-time OVERDUE derivation off the injectable `Clock`.
- **[04-authorization-identity-audit.md](04-authorization-identity-audit.md)** — *writes/usage semantics* of the `audit_event` table (this layer owns only the column shape); identity resolution via `EmployeeRepository.findByExternalSubject`; the manager-scope reads via `ManagerRelationshipRepository`.
- **[05-manager-projections.md](05-manager-projections.md)** — the V3 projection tables + `ManagerHeatmapCell`/`ManagerPlanSummary` entities + `RiskBadge` + the `is_review_overdue` projection column + the `risk_badges text[]` mapping.
- **[06-calendar-sync-messaging.md](06-calendar-sync-messaging.md)** — `OutlookCalendarSyncRecord` + `SyncStatus`/`SyncRelatedType`/`EventKind` + the `SyncJobPointer` pointer payload + the V2 review-block partial unique.
- **[07-scheduled-jobs.md](07-scheduled-jobs.md)** — batch jobs (plan-shell generation, projection rebuild) consuming these repositories + `OrgTimeConfig`/`ClockConfig`.
- **[08-comments-collaboration.md](08-comments-collaboration.md)** — the `Comment` entity + `CommentTargetType {PLAN, COMMITMENT}` (declared in `shared`, behavior elsewhere).
- **[10-infrastructure-deployment.md](10-infrastructure-deployment.md)** — Flyway runs on the migration Job only; the two-location (`db/migration` + `db/demo-seed`) load is a deploy concern (LESSONS §41).

---
_Generated by `/layer-docs` (initial run) against commit `3b919a9` on 2026-06-04. Claims are anchored to code; `UNVERIFIED` marks anything not confirmed._
