# Session 017 — Backend: Phase 6 — §9 trigger set complete (6.3b) + RiskBadgeDeriver (6.4) + E13 command center (6.5a)

- **Date:** 2026-06-04
- **Phase:** Phase 6 (manager projections, command center & heatmap) — mid-phase.
- **Role:** implementer `st6-main-wc-api-implementer` (`a72d2a78`, the fresh impl spawned at the 6.3b cycle) — **session doc authored by `st6-main-orchestrator` (`98bd8384`)**, which reviewed every Step-2.5 + Step-9 this round. **The impl's `/session-end` was SKIPPED by lead decision** (72% WARN after a deep 3-slice session incl. the 15-file E13 — too close to the confabulation edge to risk a full session-end write); the orchestrator captures continuity in its place, then the impl is cycled.
- **Predecessor:** [016](016-2026-06-03-backend-phase6-projection-derivation.md) (Phase 6 entry — 6.2 derivation + 6.3a extraction).
- **Successor:** _(Phase 6 6.5a-2 — brief 073: the E13 cross-table EXISTS filters — fresh impl.)_

## Why this session existed

The fresh impl (`a72d2a78`, spawned at the 6.3a→6.3b cycle) drove the §9 projection spine to completion and opened the manager read surfaces: the 4 remaining synchronous triggers (6.3b), the risk-badge extraction + RISK-014 coverage (6.4), and the first manager read endpoint E13 (6.5a — the one the frontend's 9.9 command-center awaited). After the heavy 6.5a slice the impl hit 72% WARN at a clean boundary → lead-approved impl-only cycle before 6.5a-2.

## What was built

### Slice 6.3b — dispute/review projection triggers + stale-cell deletion + RISK-003 (brief 069) — `efdf0f5`
Wired the 4 remaining §9 triggers — `DisputeService.open`/`respond`/`resolve` + `ReviewService.markReviewed` — through `ProjectionRefresher.recomputeForPlan(plan)` inside their existing `@Transactional` (**all 9 §9 triggers now refresh synchronously**). Added **stale heatmap-cell deletion** to `ProjectionService` (the recompute is now **recompute-then-prune**: it DELETEs cells whose Defining Objective the new source no longer produces — `respond`'s rule-#2 SO-revision remaps the DO; pinned by a remap integration test). Proven: per-trigger refresh, **RISK-003** transactional rollback (a projection-upsert failure rolls back the whole mutation — no dispute/projection/audit rows), the §17 full `lock→dispute→resolve→mark-reviewed` sequence (§3-correct review status + counts at each step), and **REQ-F-013** (a `REVIEWED_WITH_DISPUTES` review past `reviewDueAt` stays not-overdue — the added past-due pin). `respond`'s 5.4 "NO projection change" docstring corrected. Read-model only; no new authz. **LESSON §38** banked (a new synchronous FK-writing side-effect on an existing service method ripples into every driver test's `@AfterEach` — 64 opaque cross-class failures until the projection-table deletes were added FK-safe-order).

### Slice 6.4 — RiskBadgeDeriver + RISK-014 coverage (brief 070) — `3a49985`
Extracted the inline badge derivation into a stateless `RiskBadgeDeriver` (no logic change; `ProjectionService` delegates) — the single badge source for the incremental path + the 6.7 rebuild. **RISK-014 made structural:** each count-driven badge derives from the cell's own stored count (`misalignedCount>0`, etc.), so a badge can't disagree with its count; `UNREVIEWED`/`OVERDUE_REVIEW` from the caller's review booleans. Pinned the Appendix-E coverage the inline form lacked: **R2** (`NOT_REVIEWED` past-due ⇒ `OVERDUE_REVIEW` co-emits with `UNREVIEWED` — the previously-untested overdue=true branch), **R3** (`MISALIGNED`+`NEEDS_REVIEW`), **R5** (`BLOCKED`+`CARRY_FORWARD`), empty-`{}`, within-SLA, enum-only-in-order. **Fixed a latent carry-forward test-cleanup bug in-slice** (per-row `commitments.deleteAll()` is order-fragile on the `carry_forward_source_commitment_id` self-FK → `deleteAllInBatch()`; **LESSON §38-addendum**).

### Slice 6.5a — manager command center E13 + B.20 envelope (brief 071) — `5268784` — **security-reviewer CLEAN PASS**
`GET /api/manager/command-center` (E13) — the first manager READ surface. Direct-report-scoped + **IDOR-safe by query-scoping** (`manager_employee_id = principal.employeeId()`, **never a request value** — another manager's rows are unreachable, not just hidden) + coarse `authorizeTeamHeatmapAccess`. Returns the **B.20** paginated envelope (`PageEnvelope<T>` — a custom record, since stock Spring `Page`/`VIA_DTO` serialization omits the contract's `sort` field; **LESSON §39**) of **B.11** `ManagerCommandCenterRowDto` (a record, leak-tested). The 3 **summary-level** filters (`employeeId`/`planState`/`reviewState` incl. derived `OVERDUE`), the F.5 default sort (`weekStartDate DESC, employeeDisplayName ASC`), size clamp (25/max 100), **N+1-free** (one Criteria query cross-joining `employee` for the display-name + joined-column sort over a flat-UUID FK, + a count query — proven by a Hibernate-statistics `prepareStatementCount==2` test). **Design deviation (orch-ratified):** the dynamic query landed as an **api-layer `@Repository` component** (`ManagerCommandCenterQuery`) over the shared projection — keeps JaCoCo coverage where the api tests are + the `shared` repo a plain upsert repo. Added `MissingServletRequestParameterException`/`MethodArgumentTypeMismatchException` → 400 `VALIDATION_ERROR` handlers (additive). The **web-orch verified zero-drift** vs `dtos.ts`/`managerApi` → the real endpoint supersedes the frontend's 9.9 MSW stub with zero frontend change. The 4 cross-table filters are **6.5a-2** (split, not declared here). Ad-hoc **security-reviewer: CLEAN PASS, 0 findings** (IDOR scoping, chokepoint-first authz, injection-safe Criteria, no leak, denial audit).

## Decisions made

- **E13 cross-table filters SPLIT to 6.5a-2** (impl-recommended, orch-approved) — keeps the IDOR-critical scoping the focused security-review target; the 4 EXISTS-subquery filters (DO via heatmap; priority/workType/alignmentStatus via `weekly_commitment` over the §4 indexes) are a clean separable chunk. Re-sequencing within Phase 6, **not a scope cut** → no escalation; recorded in Carry-forward (`last-consumer-slice: 073`). The 4 params are undeclared in 6.5a's controller (Spring ignores undeclared → no 400) until 073.
- **api-layer Criteria query** over a shared Spring Data custom fragment — the shared fragment tanked `shared`'s per-module JaCoCo (only api tests exercise it); the api component keeps coverage where the tests are + a cleaner module boundary (the dynamic read is an api concern). Contract/behavior identical.
- **Custom `PageEnvelope<T>` record** for B.20 — stock `Page` serialization (deprecated in Boot 3.x) + `VIA_DTO`'s `PagedModel` both omit the contract's `sort` field (LESSON §39).
- **`respond` refreshes the projection unconditionally** — it's a §9-listed trigger; the no-SO-revision case is a cheap identical-row no-op (but the SO-revision case is exactly why it must refresh — the DO remap + stale-cell deletion).

## Decisions explicitly NOT made (deferred)

- **6.5a-2 (brief 073)** — the 4 E13 cross-table EXISTS filters. NEXT slice.
- **6.5b (brief 074)** — E14 heatmap (`HeatmapResponseDto`/`HeatmapCellDto`) + E15 drilldown (`HeatmapDrilldownDto`/`DrilldownOutcomeGroup`, own-cell `authorizeHeatmapCellAccess`). Coordinate B.11/B.12 with the FRESH web-orch.
- **6.6 (075)** — IC team-heatmap denial + the manager-scope IDOR matrix (incl. the positive denial-audit assertion the 6.5a reviewer noted). **6.7 (076)** — the rebuild job (`rebuild==incremental==seed`; depends on the HELD V5/V6 demo seed for the full Appendix-E reproduction).
- **MARK_REVIEWED read-affordance + real `unresolvedDisputeCount` on the plan-read (E3/E4)** — the projection side is done; the plan-read half (thread manager-context into `PlanMapper`, reuse §35) is a separate Phase-6 plan-read slice.

## TDD compliance

**Clean — no violations.** Each slice ran RED→Step-2.5→GREEN; the orchestrator reviewed every Step-2.5 + Step-9. 6.3b/6.4 are read-model (no security-reviewer per policy); **6.5a got the ad-hoc security-reviewer (CLEAN PASS, 0 findings)**. `./gradlew check` green on each. Two honest impl disclosures this round (the 6.3a-style test-ripple at 6.3b; the api-layer-query deviation at 6.5a) — both surfaced at Step-2.5/Step-9 and ratified.

## Reachability (Step 7.5)

6.3b: all 4 triggers on existing HTTP paths (E17/E18/E19/E16) → `recomputeForPlan`. 6.4: `RiskBadgeDeriver` on the projection path (all 9 triggers + the 6.7 rebuild). 6.5a: `GET /api/manager/command-center` → `ManagerController` → `ManagerQueryService` → `ManagerCommandCenterQuery` → DB (endpoint tests exercise the real HTTP path; N+1-free proven). No tested-but-unwired gaps.

## Security review

6.5a got the ad-hoc security-reviewer — **CLEAN PASS, 0 findings.** Verified: no IDOR vector (`managerEmployeeId` from `principal.employeeId()` only, the binding first WHERE predicate, a forged param ignored, filters only narrow); chokepoint-first authz; injection-safe bound Criteria; record-DTO no-leak; the 400 handlers leak only safe field names; denial audit via `deny403`→`recordDenial`. One optional LOW (the IC-denial audit row isn't positively asserted in this slice's test — structurally guaranteed + folds into 6.6's IDOR matrix). 6.3b/6.4 read-model — no reviewer.

## Open follow-ups

### Step-9 items (routed hot — orchestrator-written, in the round commit)
- **ARCHITECTURE §9** (stale-cell recompute-then-prune). **LESSONS §37** (+6.3b/6.4 addenda) · **§38** (synchronous-FK-side-effect test-teardown) · **§39** (B.20 custom-envelope). **CLAUDE** cross-doc row (the manager command-center read DTOs) + the §38/§39 index rows. **MVP_TASKS** 6.3/6.4/6.5 status + the Log entry + the Carry-forward triage (projection-completeness fully resolved; the 6.5a-2 split entry).

### Carried obligations (live — Phase 6)
- 6.5a-2 (073) cross-table filters; 6.5b (074) E14/E15; 6.6 (075) IDOR matrix + the denial-audit assertion; 6.7 (076) rebuild job (V5/V6 seed dep). The MARK_REVIEWED plan-read half (§35 threading).

### Wiring tasks
None outstanding — all features wired (the 4 new triggers + the E13 endpoint reachable from production HTTP paths).

## How to use what was built

The §9 manager-projection spine is now **complete, correct-from-source, centralized, and drift-free**: every one of the 9 lifecycle mutations refreshes the read-models synchronously (recompute-then-prune, no orphan cells, transactional rollback proven), the counts + badges are correct (dispute-union misaligned, reconciliation-outcome blocked, structural RISK-014 badges), and the **first manager read endpoint (E13 command center) is live + IDOR-safe + N+1-free**, feeding the frontend's 9.9 UI with zero frontend change. **Phase 6 continues at 6.5a-2 (brief 073)** with a fresh implementer: the 4 E13 cross-table EXISTS filters, then E14/E15 (the heatmap + drilldown reads), the IC-denial IDOR matrix, and the rebuild job.
