# Manager Command Center & Read Projections

## Executive summary

This layer is the **manager-facing read model** of the Weekly Commit module: when a manager opens the command center, the alignment heatmap, or drills into a single cell, the rows and counts they see come from here. Rather than re-aggregate raw commitments on every page load, the system keeps two pre-computed **projection tables** — `manager_plan_summary` (one row per manager/report/week) and `manager_heatmap_cell` (one row per manager/report/week/Defining-Objective) — that are recomputed synchronously inside the same database transaction whenever an IC or manager mutates the underlying lifecycle (lock, reconciliation, carry-forward, dispute, review). A separate from-source **rebuild job** can truncate and recompute both tables for every plan, producing a result byte-identical to the incremental path. Two safety invariants live here: every query is hard-scoped to `manager_employee_id = the authenticated manager` (rule #3 — a manager can never reach another's rows), and "OVERDUE"/risk are **derived at read/recompute time from a `Clock`**, never stored as a status (rule #6). This layer owns the projection schema, the recompute/rebuild/refresh logic, the risk-badge derivation, and the three read services (command center, heatmap, drilldown); it does **not** own the HTTP controller, the review/SLA lifecycle, or the rebuild job's runner.

## Responsibilities

- **Owns the two synchronous read-model tables** — `manager_plan_summary` and `manager_heatmap_cell` (schema `V3__projection_tables.sql:11`/`:35`), their JPA entities (`ManagerPlanSummary`, `ManagerHeatmapCell` in `shared`), and the plain upsert repositories.
- **Owns count derivation from source** — `ProjectionService.recompute` (`api/.../projection/ProjectionService.java:70`) derives all seven count columns + the `is_review_overdue` boolean from a plan's commitments, disputes, and review.
- **Owns the single in-transaction refresh entrypoint** — `ProjectionRefresher.recomputeForPlan` (`api/.../projection/ProjectionRefresher.java:46`), the one block the five (now nine call-site) lifecycle mutations and the rebuild job all funnel through.
- **Owns the from-source rebuild** — `ProjectionRebuilder.rebuild` (`api/.../projection/ProjectionRebuilder.java:58`) truncates and recomputes both tables for every plan.
- **Owns risk-badge derivation** — `RiskBadgeDeriver.derive` (`api/.../projection/RiskBadgeDeriver.java:30`), each badge driven by the cell's own stored count (RISK-014 structural).
- **Owns the three manager read services** — command center (`ManagerQueryService`), heatmap (`ManagerHeatmapService`), drilldown (`ManagerDrilldownService`) — including the coarse manager-role gate, IDOR scoping, default sort, page clamp, and filter translation.
- **Owns the read DTOs + the Criteria queries** — `ManagerCommandCenterRowDto`/`HeatmapCellDto`/`HeatmapResponseDto`/`HeatmapDrilldownDto`/`DrilldownOutcomeGroup` (B.11/B.12), `PageEnvelope` (B.20), `ReviewStateFilter`, `CommandCenterFilters`, and the two `@Repository` Criteria queries (`ManagerCommandCenterQuery`, `ManagerHeatmapQuery`).

It does **NOT** own:
- The **HTTP surface** (`ManagerController` `@GetMapping`s) → [02-api-web.md](02-api-web.md).
- The **review status / SLA / read-time review `isOverdue`** logic (`ReviewMapper`, `ReviewStatusDeriver`, `ReviewSlaService`) → [03-application-lifecycle.md](03-application-lifecycle.md). This layer only *consumes* the review row to mirror its status/due-date and compute the projection's own `is_review_overdue`.
- The **rebuild job's activation runner** (`ProjectionRebuildRunner`, `--app.job=rebuild-projections`) → [07-scheduled-jobs.md](07-scheduled-jobs.md).
- The lifecycle services (`PlanLifecycleService`, `DisputeService`, etc.) that *call* `recomputeForPlan` → [03-application-lifecycle.md](03-application-lifecycle.md).
- The `DomainAuthorizationService` chokepoints it invokes → [04-authorization-identity-audit.md](04-authorization-identity-audit.md).

## Key components

| Component | What it does | Where |
|-----------|--------------|-------|
| `ProjectionService` | Recomputes both projection tables from a plan's commitments/disputes/review; derives all 7 counts + `is_review_overdue` over an injectable `Clock`; upserts summary + per-DO cells; deletes stale cells | `api/src/main/java/com/st6/wc/projection/ProjectionService.java:70` |
| `ProjectionRefresher` | Single source-loading refresh entrypoint: resolve manager from plan owner → §28 skip if no manager/no review → load commitments+review → delegate to `recompute` | `api/src/main/java/com/st6/wc/projection/ProjectionRefresher.java:46` |
| `ProjectionRebuilder` | From-source rebuild: truncate both tables, recompute every plan via the same `recomputeForPlan`, one SYSTEM audit row | `api/src/main/java/com/st6/wc/projection/ProjectionRebuilder.java:58` |
| `RiskBadgeDeriver` | Pure static derivation of a cell's `RiskBadge` list from its already-set counts + review booleans (RISK-014 structural) | `api/src/main/java/com/st6/wc/projection/RiskBadgeDeriver.java:30` |
| `ManagerQueryService` | E13 command-center read: coarse role gate, IDOR scoping, default sort, page clamp (100), `reviewState`→`overdue` translation, B.20 envelope | `api/src/main/java/com/st6/wc/manager/ManagerQueryService.java:49` |
| `ManagerHeatmapService` | E14 heatmap read: coarse role gate, IDOR scoping, SO→parent-DO resolution; NOT paginated | `api/src/main/java/com/st6/wc/manager/ManagerHeatmapService.java:40` |
| `ManagerDrilldownService` | E15 own-cell drilldown: own-cell authz chokepoint first, resolve cell → that-week plan → SO-grouped commitments (paginated) | `api/src/main/java/com/st6/wc/manager/ManagerDrilldownService.java:72` |
| `ManagerCommandCenterQuery` | api-layer Criteria query: cross-join `employee` for display name, AND-combine filters, 4 cross-table EXISTS, page + count (N+1-free) | `api/src/main/java/com/st6/wc/manager/query/ManagerCommandCenterQuery.java:65` |
| `ManagerHeatmapQuery` | api-layer Criteria query: cross-join `employee`+`defining_objective` for names/title, IDOR-scoped, single statement | `api/src/main/java/com/st6/wc/manager/query/ManagerHeatmapQuery.java:45` |
| `ManagerProjectionMapper` | Maps projection entity (+ joined name/title) → B.11/B.12 record; the only place the entity becomes a boundary DTO | `api/src/main/java/com/st6/wc/manager/mapper/ManagerProjectionMapper.java:18` |
| `ManagerPlanSummary` (entity) | JPA entity for `manager_plan_summary`; no audit quartet, no `@Version`, single `updatedAt` | `shared/src/main/java/com/st6/wc/projection/ManagerPlanSummary.java:30` |
| `ManagerHeatmapCell` (entity) | JPA entity for `manager_heatmap_cell`; `risk_badges text[]`→`List<RiskBadge>`, defensive-copy accessors | `shared/src/main/java/com/st6/wc/projection/ManagerHeatmapCell.java:33` |
| `ManagerPlanSummaryRepository` | Plain upsert repo + `(manager,employee,week)` finder | `shared/src/main/java/com/st6/wc/projection/repo/ManagerPlanSummaryRepository.java:16` |
| `ManagerHeatmapCellRepository` | Plain upsert repo + `(manager,employee,week)` cell finder | `shared/src/main/java/com/st6/wc/projection/repo/ManagerHeatmapCellRepository.java:14` |
| `PageEnvelope<T>` | B.20 custom paginated envelope over `Page<T>` (stock serialization omits `sort`) | `api/src/main/java/com/st6/wc/manager/dto/PageEnvelope.java:13` |
| `CommandCenterFilters` | Internal 8-field filter carrier (4 summary-level + 4 cross-table) | `api/src/main/java/com/st6/wc/manager/query/CommandCenterFilters.java:20` |
| `ReviewStateFilter` | E13 `reviewState` query vocab (`NOT_REVIEWED|REVIEWED_WITH_DISPUTES|REVIEWED|OVERDUE`); computed vocab, not an `enums/` member | `api/src/main/java/com/st6/wc/manager/dto/ReviewStateFilter.java:11` |
| `V3__projection_tables.sql` | DDL for both tables, unique constraints, indexes, `risk_badges` vocab CHECK | `shared/src/main/resources/db/migration/V3__projection_tables.sql:11` |

## Interfaces & contracts

**Write side (recompute / refresh / rebuild):**

```java
// ProjectionService.java:70 — the count-derivation core
void recompute(WeeklyPlan plan, UUID managerId, List<WeeklyCommitment> commitments, ManagerReview review)

// ProjectionRefresher.java:46 — the single in-txn entrypoint the lifecycle calls
void recomputeForPlan(WeeklyPlan plan)   // no-op if no active manager OR no review row (§28)

// ProjectionRebuilder.java:58 — the from-source rebuild
@Transactional int rebuild()   // truncate both tables + recompute all plans; returns plan count

// RiskBadgeDeriver.java:30 — pure, static
static List<RiskBadge> derive(ManagerHeatmapCell cell, boolean unreviewed, boolean overdue)
```

`recomputeForPlan` is the contract every lifecycle write depends on. Verified callers (all in [03-application-lifecycle.md](03-application-lifecycle.md) services, called inside the mutation's `@Version` transaction):
- `PlanLifecycleService.java:161` (lock — right after the review row is saved), `:211`, `:278` (start/close reconciliation)
- `DisputeService.java:125`, `:196`, `:246` (open / respond / resolve)
- `CarryForwardService.java:119`
- `CommitmentService.java:154`, `:252` (commitment outcome / unplanned create)
- `ReviewService.java:84` (mark-reviewed)

That is **nine** synchronous call sites (the CLAUDE.md cross-doc note calls them "9 synchronous triggers"); they funnel through one entrypoint.

**Read side (services → DTOs):**

```java
// ManagerQueryService.java:49 — E13
PageEnvelope<ManagerCommandCenterRowDto> commandCenter(
    UserPrincipal principal, LocalDate weekStart, UUID employeeId, PlanState planState,
    ReviewStateFilter reviewState, UUID definingObjectiveId, Priority priority,
    WorkType workType, AlignmentStatus alignmentStatus, Pageable pageable)

// ManagerHeatmapService.java:40 — E14 (NOT paginated)
HeatmapResponseDto heatmap(UserPrincipal principal, LocalDate weekStart,
    UUID definingObjectiveId, UUID supportingOutcomeId)

// ManagerDrilldownService.java:72 — E15 (own-cell)
HeatmapDrilldownDto drilldown(UserPrincipal principal, UUID cellId, Pageable pageable)
```

**What this layer expects from others:**
- `DomainAuthorizationService.authorizeTeamHeatmapAccess(principal)` (coarse manager-role gate, `DomainAuthorizationService.java:158`) and `authorizeHeatmapCellAccess(principal, cellId)` (own-cell, `:146`) — [04-authorization-identity-audit.md](04-authorization-identity-audit.md).
- `RcdoReadService.findSupportingOutcome(...)` to resolve a commitment's SO → parent DO during recompute (`ProjectionService.java:158`).
- `AlignmentDisputeRepository.findByCommitmentIdInAndStatusIn(...)` to load unresolved disputes once per recompute (`ProjectionService.java:84`).
- A `WeeklyPlan` + `ManagerReview` + `List<WeeklyCommitment>` as recompute inputs (supplied by `ProjectionRefresher`).

## Data & state

**Table `manager_plan_summary`** (`V3__projection_tables.sql:11`) — one row per `(manager_employee_id, employee_id, week_start_date)` (unique `uq_manager_plan_summary`, `:29`). Columns: `weekly_plan_id`, `plan_state varchar(32)`, `review_status varchar(32)` (nullable), `review_due_at`, `is_review_overdue boolean default false`, the 7 counts (`planned/unplanned/misaligned/needs_review/blocked/carry_forward/unresolved_dispute`), `updated_at`. **No CHECK** on `plan_state`/`review_status` — they are denormalized mirrors of the source-of-truth CHECKs on `weekly_plan.state`/`manager_review.status` (`:6`). Indexes: `idx_mps_manager_week_state`, `idx_mps_manager_review_status` (`:31`/`:32`).

**Table `manager_heatmap_cell`** (`V3__projection_tables.sql:35`) — one row per `(manager, employee, week, defining_objective_id)` (unique `uq_manager_heatmap_cell`, `:51`). Carries `commitment_count` + the same 7 counts + `risk_badges text[]` + `updated_at`. The `risk_badges` array is constrained by `ck_risk_badges_vocab` (`:53`) — a `<@` containment CHECK pinned to the six `RiskBadge` values. Indexes: `idx_mhc_manager_week`, `idx_mhc_manager_do` (`:57`/`:58`).

**Key invariant in the DDL itself (`:7`):** `is_review_overdue` is the §9 read-model column, explicitly **NOT** safety rule #6 — rule #6 governs `manager_review.status` (which has no `OVERDUE`) plus read-time derivation. The projection column is *also* derived (recomputed from the `Clock`), never a stored status.

**Enums consumed:** `PlanState`, `ReviewStatus` (entity-mirrored `@Enumerated(STRING)`), `RiskBadge` (six values, `shared/.../enums/RiskBadge.java:4`), plus the read-only `ReviewStateFilter` vocab (`ReviewStateFilter.java:11`) which is *not* a persisted enum.

**No `@Version`, no audit quartet** on either entity (`ManagerPlanSummary.java:30`, `ManagerHeatmapCell.java:33`) — these are materialized read models recomputed wholesale, so optimistic-lock and audit tracking would be meaningless. The only timestamp is `updatedAt`, set from the `Clock` on every upsert.

## Dependencies

- **Depends on:**
  - `RcdoReadService` (resolve SO → parent DO for cell grain) — `ProjectionService.java:158`.
  - `AlignmentDisputeRepository` (load unresolved disputes once) — `ProjectionService.java:84`.
  - `ManagerRelationshipRepository`, `ManagerReviewRepository`, `WeeklyCommitmentRepository` (refresher source loads) — `ProjectionRefresher.java:48`/`:57`/`:60`.
  - `DomainAuthorizationService` (coarse role + own-cell chokepoints) — read services, [04](04-authorization-identity-audit.md).
  - `AuditService` (one SYSTEM `PROJECTIONS_REBUILT` row per rebuild) — `ProjectionRebuilder.java:87`.
  - `SupportingOutcomeRepository`, `WeeklyPlanRepository`, `WeeklyCommitmentRepository`, `CommitmentMapper` (drilldown) — `ManagerDrilldownService.java:50`–`:55`.
  - Spring Data `Page`/`Pageable`/`Sort`, JPA Criteria API.
- **Used by:**
  - **Lifecycle services** (write side) call `recomputeForPlan` inside their transactions — [03-application-lifecycle.md](03-application-lifecycle.md).
  - **`ProjectionRebuildRunner`** (job activation) calls `rebuild()` — [07-scheduled-jobs.md](07-scheduled-jobs.md).
  - **`ManagerController`** (HTTP) calls the three read services — [02-api-web.md](02-api-web.md).
  - The **frontend** mirrors B.11/B.12/B.20 in `dtos.ts` — [09-frontend.md](09-frontend.md).

## How it works (flow)

**Write path (incremental refresh) — happens inside a lifecycle transaction:**

```
  lifecycle mutation              ProjectionRefresher            ProjectionService
  (lock / dispute / etc.)   →     .recomputeForPlan(plan)   →    .recompute(plan,mgr,
  PlanLifecycleService.java:161   resolve mgr from owner         commitments,review)
                                  §28 skip if no mgr/review      derive 7 counts + overdue
                                                                 upsert summary + per-DO cells
                                                                 RiskBadgeDeriver.derive(cell)
                                                                 delete stale cells
```

1. A lifecycle service finishes its core mutation and calls `recomputeForPlan(plan)` (e.g. `PlanLifecycleService.java:161`).
2. `ProjectionRefresher.java:48` resolves the manager from the **plan owner's** active relationship. If none, **return** (§28 skip — `:52`).
3. It loads the review row by plan id; if absent, **no-op** (`:57`). Otherwise it loads all commitments id-ascending and calls `recompute` (`:60`).
4. `ProjectionService.recompute` (`:70`) computes `overdue = review NOT_REVIEWED AND clock.instant() > reviewDueAt` (`:72`), loads the plan's unresolved disputes **once** over the commitment ids (`:84`, empty set → no query), and derives the unresolved-dispute and MISALIGNED-flag commitment-id sets in memory.
5. `upsertSummary` (`:111`) find-or-creates the one summary row and sets all 7 counts: `misaligned` = `alignment_status=MISALIGNED` ∪ open-MISALIGNED-dispute (deduped, `:222`); `needs_review` = `NEEDS_REVIEW` (`:228`); `blocked` = `reconciliation_outcome=BLOCKED` (`:239`); `carry_forward` = non-null `carryForwardSourceCommitmentId` (`:243`); `unresolved_dispute` = commitments with an open dispute (`:217`).
6. `upsertHeatmapCells` (`:143`) groups **linked** commitments by their SO's parent DO (`:152`), upserts one cell per DO with the same counts, then calls `RiskBadgeDeriver.derive(cell, unreviewed, overdue)` (`:189`) to set `risk_badges` from the cell's own counts.
7. **Stale-cell deletion** (`:199`): any pre-existing cell for a DO no longer touched by any commitment is deleted (RISK-003 drift — e.g. a dispute-respond SO revision remaps a commitment to a different DO).

**Rebuild path (from source):** `ProjectionRebuilder.rebuild` (`:58`) — one `@Transactional` run: `deleteAllInBatch` both tables (`:61`), then `recomputeForPlan` for every plan via `plans.findAll()` (`:64`), then one SYSTEM `PROJECTIONS_REBUILT` audit (`:87`). Because it reuses the same per-plan entrypoint, **rebuild == incremental** by construction, and drift/orphan cells are corrected.

**Read path (command center, E13):**

```
  ManagerController       ManagerQueryService.java:49        ManagerCommandCenterQuery.java:65
  GET /command-center  →  authorizeTeamHeatmapAccess (60) →  cross-join employee for displayName
  (02-api-web)            translate reviewState→overdue       AND filters + 4 cross-table EXISTS
                          clamp size to 100 / default sort    page query + count query (N+1-free)
                          query.findCommandCenter(            → PageEnvelope.of(page)
                            principal.employeeId(), ...)
```

1. `ManagerQueryService.commandCenter` (`:49`) calls `authz.authorizeTeamHeatmapAccess(principal)` first (`:60`) — non-manager → `403 MANAGER_ROLE_REQUIRED`.
2. It translates `reviewState`: `OVERDUE` → `overdue=true` (no stored status), the three real statuses → `ReviewStatus` (`:91`).
3. It clamps page size to `MAX_PAGE_SIZE=100` (`:71`) and applies `DEFAULT_SORT` (week desc, name asc) when the client sends none (`:72`).
4. It calls `query.findCommandCenter(principal.employeeId(), ...)` (`:77`) — the IDOR scope is `principal.employeeId()`, **never a request value**.
5. `ManagerCommandCenterQuery` (`:65`) builds the Criteria query: cross-join `employee` for `displayName` (`:77`), `manager_employee_id = managerEmployeeId` as the first WHERE (`:113`), AND the optional filters, plus the 4 cross-table EXISTS subqueries (`:139`). It runs a page query and a separate count query (N+1-free, fixed two statements), mapping rows via `ManagerProjectionMapper.toRowDto` (`:88`).

**Read path (heatmap, E14):** `ManagerHeatmapService.heatmap` (`:40`) gates on `authorizeTeamHeatmapAccess`, optionally resolves `supportingOutcomeId` → its parent DO (cell grain is DO; disagree/unknown → empty `:54`), then `heatmapQuery.findCells(principal.employeeId(), ...)` (`:62`). `ManagerHeatmapQuery` (`:45`) cross-joins `employee`+`defining_objective` for names/title, IDOR-scoped (`:57`), one statement, not paginated.

**Read path (drilldown, E15):** `ManagerDrilldownService.drilldown` (`:72`) calls `authz.authorizeHeatmapCellAccess(principal, cellId)` as the **first** statement (`:73`) — own-cell chokepoint (IDOR `404` + audit / missing `404` no audit). It loads the authorized cell (`:75`), resolves the report's that-week plan via `findByEmployeeIdAndWeekStartDate` from the **cell's** `(employeeId, weekStartDate)` — never the request (`:82`), groups commitments by SO under the cell's DO with B.20 pagination (`priority ASC, createdAt ASC, id ASC`, `:47`), and omits empty SO groups (`:90`).

## Design decisions & rationale

- **Synchronous projection over async/materialized view (§9).** The two tables are recomputed *inside the same transaction* as the source mutation, so a reader never observes a stale projection relative to a committed write. The cost is one extra recompute per mutation; the benefit is read-time queries become simple indexed scans with no live aggregation. ARCHITECTURE §9 (manager projections / read models).
- **One refresh entrypoint reused by both paths (§8/§9).** `ProjectionRefresher.recomputeForPlan` was extracted (task 6.3a) from five byte-identical mutation sites, then reused by the rebuild job (task 6.7). This guarantees `rebuild == incremental` — the rebuild can't drift from the live path because it runs the same code (`ProjectionRebuilder.java:66`).
- **Risk badges derive from the cell's own stored counts (RISK-014 structural).** `RiskBadgeDeriver.derive` reads `cell.getMisalignedCount()` etc. (`:33`), not a re-derivation from source. A badge therefore cannot disagree with the count it represents — the consistency is structural, not a discipline a future edit could break.
- **Derived OVERDUE, never stored (rule #6 / §9).** The projection's `is_review_overdue` is computed over an injectable `Clock` at recompute time (`ProjectionService.java:72`) and the `OVERDUE_REVIEW` badge fires off that same boolean (`RiskBadgeDeriver.java:48`). There is no `ReviewStatus.OVERDUE`; the `ReviewStateFilter.OVERDUE` query value maps to `is_review_overdue=true`, not a stored status (`ManagerQueryService.java:91`). The *read-time* review-DTO `isOverdue` (the same rule expressed in `ReviewMapper`) lives in [03-application-lifecycle.md](03-application-lifecycle.md); this layer derives the projection-column form.
- **IDOR by query-scoping, not post-filter (rule #3).** The manager identity is `principal.employeeId()` and is injected as the **first** WHERE predicate in both Criteria queries (`ManagerCommandCenterQuery.java:113`, `ManagerHeatmapQuery.java:57`). A request value never reaches the scope; the four command-center cross-table filters only *narrow* (`crossTablePredicates`, `:139`). Drilldown enforces own-cell at the authz chokepoint before any load (`ManagerDrilldownService.java:73`).
- **Custom `PageEnvelope` over stock `Page` (B.20, LESSONS §39).** Boot 3.x deprecated direct `Page` serialization and `PagedModel` omits the `sort` array B.20 requires, so a custom record (`PageEnvelope.java:13`) carries `{content, page, sort}`.
- **api-layer Criteria queries, not `shared` repo fragments (LESSONS §39).** Sorting by a joined non-association column (`employeeDisplayName` over a flat-UUID FK) is impossible with `JpaSpecificationExecutor`, so the dynamic reads are api-layer `@Repository` Criteria queries; the `shared` repos stay plain upsert repos (`ManagerPlanSummaryRepository.java:16`).

## Gotchas & sharp edges

- **`is_review_overdue` is NOT the same thing as a `ReviewStatus`.** The DDL comment (`V3__projection_tables.sql:7`) and the entity Javadoc (`ManagerPlanSummary.java:24`) both call this out: it is a §9 read-model column, derived, recomputed wholesale — confusing it with safety rule #6's status would be a category error. Both are "derived OVERDUE," but rule #6 forbids a *stored status*, which this column is not.
- **`blocked_count` source was a latent bug.** It was shipped as `work_type=BLOCKER` (a planning category) and corrected (task 6.2) to `reconciliation_outcome=BLOCKED` (`ProjectionService.java:239`) — the sibling of `carry_forward_count`. The seed (`rebuild==seed`, R5 Grace) decided the ambiguous source. At lock no commitment has a reconciliation outcome, so `blocked_count` is 0 at lock.
- **Stale heatmap cells are deleted, not just upserted.** Because a dispute-respond SO revision can remap a commitment to a different DO, the upsert-only path would leave an orphan cell; the explicit delete loop (`ProjectionService.java:199`) prunes any DO no longer touched. Summary rows are never stale (grain is stable). Forgetting this is RISK-003 drift.
- **Empty SQL `IN` guard.** Disputes are loaded only when the commitment-id set is non-empty (`ProjectionService.java:82`) — an empty `IN` is invalid SQL.
- **Heatmap cell grain is DO, not SO.** A `supportingOutcomeId` filter on E14 resolves to its parent DO; an unknown SO or a DO that disagrees with a supplied `definingObjectiveId` yields **no cells** (`ManagerHeatmapService.java:54`), keeping counts DO-honest.
- **Drilldown reads the canonical `weekly_plan`, not the summary projection.** The report's plan is resolved from the authorized cell's `(employeeId, weekStartDate)` via `findByEmployeeIdAndWeekStartDate` (`ManagerDrilldownService.java:82`) — deliberately decoupled from the projection so the work breakdown is authoritative.
- **Drilldown commitments are context-free.** They use `CommitmentMapper.toDto(c)` → empty `allowedActions` (`ManagerDrilldownService.java:94`); dispute affordances are E3/E4-only (§35). A manager drilling in gets no action affordances here.
- **Page-size clamp is a backstop.** `MAX_PAGE_SIZE=100` in the service (`ManagerQueryService.java:37`, `ManagerDrilldownService.java:45`) duplicates the `spring.data.web.pageable` resolver config — both must agree.
- **No DRIFT found between ARCHITECTURE.md and the code for this layer.** The CLAUDE.md cross-doc invariant row for `V3__projection_tables.sql` (§9 / Appendix A) matches the DDL, entities, count derivation, badge derivation, refresh centralization, and rebuild as described. The B.11/B.12/B.20 DTO mirrors match the records (B.12's deliberate `weekStart` vs `weekStartDate` asymmetry is intentional and documented — `HeatmapCellDto.java:13` — do not harmonize).

## Connects to

- **[02-api-web.md](02-api-web.md)** — `ManagerController` (`ManagerController.java:49`/`:75`/`:88`) is the HTTP surface that calls this layer's three read services; it does nothing but bind params and delegate (deferred there).
- **[03-application-lifecycle.md](03-application-lifecycle.md)** — the nine lifecycle write sites (`PlanLifecycleService`, `DisputeService`, `CarryForwardService`, `CommitmentService`, `ReviewService`) call `recomputeForPlan` inside their transactions; the *review status / SLA / read-time review `isOverdue`* logic (`ReviewMapper`, `ReviewStatusDeriver`) lives there. **Cross-link for #6:** the read-time `isOverdue` on the review DTO is the review-package twin of this layer's projection `is_review_overdue` — both derive over a `Clock`, neither is stored.
- **[04-authorization-identity-audit.md](04-authorization-identity-audit.md)** — `DomainAuthorizationService.authorizeTeamHeatmapAccess` and `authorizeHeatmapCellAccess` (the rule-#3 chokepoints); `AuditService` writes the `PROJECTIONS_REBUILT` row.
- **[07-scheduled-jobs.md](07-scheduled-jobs.md)** — `ProjectionRebuildRunner` (`--app.job=rebuild-projections`) activates `ProjectionRebuilder.rebuild()`; the runner is the job entry point (deferred there).
- **[01-domain-persistence.md](01-domain-persistence.md)** — the source entities (`WeeklyPlan`, `WeeklyCommitment`, `AlignmentDispute`, `ManagerReview`, `Employee`, `DefiningObjective`, `SupportingOutcome`) this layer reads to derive counts; the `RiskBadge` enum vocabulary.
- **[09-frontend.md](09-frontend.md)** — `dtos.ts` mirrors B.11/B.12/B.20 field-for-field (the frontend command center / heatmap consume these).

---
_Generated by `/layer-docs` (initial run) against commit `3b919a9` on 2026-06-04. Claims are anchored to code; `UNVERIFIED` marks anything not confirmed._
