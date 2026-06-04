# Session 019 — Backend: Phase 6 COMPLETE — E13 cross-table filters (6.5a-2) + E14/E15 heatmap+drilldown (6.5b) + manager-read IDOR matrix (6.6) + ProjectionRebuildRunner (6.7) + MARK_REVIEWED plan-read affordance (6.8)

- **Date:** 2026-06-04
- **Phase:** Phase 6 (manager projections, command center & heatmap) — **✅ COMPLETE**.
- **Role:** implementer `st6-main-wc-api-implementer` (the fresh impl spawned at the full-team reset) — **session doc authored by `st6-main-orchestrator`**, which reviewed every Step-2.5 + Step-9 this round and ran the ad-hoc security passes. **The impl's `/session-end` was SKIPPED** by lead decision (74% WARN after the 5-slice run — a full session-end write would cross 75% mid-way, the confabulation edge; the 016/017 precedent). The impl was then **RETIRED** (lead shutdown; backend pauses, queue empty); the orchestrator captures continuity here from git + its own slice-by-slice knowledge.
- **Predecessor:** [017](017-2026-06-04-backend-phase6-triggers-badges-command-center.md) (Phase 6 — 6.3b/6.4/6.5a). _(018 is the frontend styling-fidelity round.)_
- **Successor:** _(backend PAUSED pending user prioritization — §9 command-center `summary` field / V5–V6 Phase-10 demo seed / the web-orch's incoming B.11 follow-ups.)_

## Why this session existed

The fresh impl drove the entire Phase-6 manager-**READ** surface to completion, on top of the §9 projection spine (6.2/6.3a/6.3b/6.4) that landed the prior arc. Five slices: the E13 cross-table filters (6.5a-2), the E14 heatmap + E15 drilldown (6.5b), the §6/§17 IDOR-matrix completion (6.6), the from-source rebuild job (6.7), and the MARK_REVIEWED plan-read affordance (6.8) — closing **every** open Phase-6 manager-read carry-forward. After the run the impl hit 74% WARN at a clean boundary (backend queue empty) → lead-approved close-out.

## What was built

### Slice 6.5a-2 — E13 command-center cross-table filters (brief 073) — `2ba8283` — **security-reviewer CLEAN PASS**
The 4 remaining E13 filters (REQ-F-023) as **EXISTS subqueries** in a dedicated `crossTablePredicates(cb, query, s, filters)` helper (reused by the page + count queries) on the existing `ManagerCommandCenterQuery`: `definingObjectiveId` (EXISTS over `manager_heatmap_cell` at the full manager/report/week/DO grain) + `priority`/`workType`/`alignmentStatus` (each EXISTS over `weekly_commitment` correlated on `s.weeklyPlanId`, over the §4 indexes). Declared the 4 `@RequestParam` on `ManagerController`; `CommandCenterFilters` 4→8 fields (internal carrier). **IDOR scope untouched** (the `manager_employee_id` predicate stays the first unconditional WHERE; filters only NARROW), **N+1-free preserved** (a fixed 2 statements with all 7 filters). The §9/REQ-F-023 E13 filter set is now COMPLETE. Ad-hoc security-reviewer **CLEAN PASS** (no IDOR widening; the `attribute` arg is a compile-time literal — no injection).

### Slice 6.5b — E14 heatmap + E15 drilldown (brief 074) — `cce700e` — **security-reviewer CLEAN PASS, B.12 zero-drift**
**E14** `GET /api/manager/heatmap` → `HeatmapResponseDto {weekStart, cells: HeatmapCellDto[]}` (NOT paginated, bounded by reports×DOs); coarse `authorizeTeamHeatmapAccess` + IDOR-scoped Criteria join (`ManagerHeatmapQuery`) over `employee`+`defining_objective` for the names/titles, N+1-free (one statement); `supportingOutcomeId` resolves to its **parent DO** + narrows (count-honest; unknown/disagreeing → empty). **E15** `GET /api/manager/heatmap/{cellId}/drilldown` → `HeatmapDrilldownDto` with per-SO `DrilldownOutcomeGroup[]`, each a **B.20 `PageEnvelope<WeeklyCommitmentDto>`** (`priority ASC, createdAt ASC, id ASC`); **own-cell** `authorizeHeatmapCellAccess` chokepoint-first → IDOR `404`+audit (missing → `404` no-audit, §25); the plan resolved from the **authorized cell's** `(employeeId, weekStartDate)` via `WeeklyPlanRepository.findByEmployeeIdAndWeekStartDate` (decoupled from the summary projection); commitments use the **context-free `CommitmentMapper.toDto`** → empty `allowedActions` (dispute affordances E3/E4-only, 5.5b/§35); empty SO groups omitted (revisitable per REQ-F-022). 4 new DTOs + `ManagerHeatmapService` + `ManagerDrilldownService` + `toHeatmapCellDto`. The `id` tiebreaker was an orch ADD at Step-2.5 (deterministic pagination while `createdAt` is unpopulated — pinned by `drilldown_samePriorityCommitments_paginateDeterministically`). **Deliberate B.12 asymmetry preserved:** response `weekStart` vs cell `weekStartDate`. Single combined commit (the shared controller makes a 2-way split non-buildable). Ad-hoc security-reviewer **CLEAN PASS**; web-orch verified **zero-drift** vs `dtos.ts`/`managerApi`.

### Slice 6.6 — §6/§17 manager-read IDOR matrix completion (brief 075) — `827538f` — **security CLEAN PASS, test-only**
Endpoint-level IDOR coverage for the Phase-6 reads (the service-level `AuthorizationIdorMatrixTest` already pinned the §6 set at the authz layer; this closed the endpoint + positive-audit gaps): the **IC-team-heatmap denial** (REQ-F-030 — IC→E13/E14 `403 MANAGER_ROLE_REQUIRED`/`Heatmap`; IC→E15 `404`/`HeatmapCell` via the own-cell authorizer's existence-hiding) + **positive `AUTHORIZATION_DENIED` audit-row assertions** (action + entityType + actor + `hasSize(1)`) on every manager-read denial; the E15 not-own-cell `count()>0` **upgraded** to the specific row (the §38-6.5b addendum). Test-only — **no production change** (every Phase-6 denial already audits; missing→no-audit §25 preserved); the gap analysis found **no audit-gap** → no escalation. Coverage-hardening, not RED→GREEN (teeth = the assertion specificity). Ad-hoc security/completeness review **CLEAN PASS** (the 403/404-vs-§6 mapping + the assertion teeth confirmed).

### Slice 6.7 — ProjectionRebuildRunner (brief 076) — `ebb4d46` — no security review (SYSTEM job)
The internal `--app.job=rebuild-projections` runner (REQ-D-013): `ProjectionRebuilder.rebuild()` (one `@Transactional`) **truncates** both projection tables (`deleteAllInBatch`) then iterates `plans.findAll()` → `ProjectionRefresher.recomputeForPlan` (the SAME entrypoint the 9 synchronous triggers use, 6.3a) — so **`rebuild==incremental`** by construction, with drift/orphan cells corrected; `ProjectionRebuildRunner` is a thin `@ConditionalOnProperty(app.job)` `ApplicationRunner` mirroring `PlanShellGenerationRunner` (§8/§23). Proven by **corrupt-then-rebuild-then-assert-corrected** (mutate a count / delete a cell / inject an orphan → rebuild → back-to-source, orphan pruned — the meaningful proof vs a tautological build-rebuild-equal), idempotency, the §28 unmanaged/unreviewed skip, one SYSTEM null-actor `PROJECTIONS_REBUILT` audit/run; gating via `ApplicationContextRunner`. The defensive `em.flush()/clear()` was **dropped** (no read-model entity loads before the truncate → no staleness; a `@PersistenceContext` field broke the no-JPA gating-test seam). **LESSON §40** banked (the rebuild-job pattern + the corrupt-then-rebuild test teeth). `rebuild==seed` (Appendix-E R1–R6) deferred to the HELD V5/V6 seed (Phase 10).

### Slice 6.8 — MARK_REVIEWED plan-read affordance + real `unresolvedDisputeCount` (brief 081) — `fea91b2` — no security review (read-affordance)
Closed the **last open Phase-6 manager-read carry-forward** (`origin: 5.2`). On E3/E4: emit `MARK_REVIEWED` on `ManagerReviewDto.allowedActions` iff `viewerIsDirectManager` (the §35 boolean already resolved at `PlanMapper`) AND `status == NOT_REVIEWED` — a **§31 UX-narrowed subset** of E16's enforcement (`ReviewService.markReviewed` authorizes manager-of-owner + guards `!= DRAFT`, with **no status guard** — so the affordance is a deliberate subset that hides the button once reviewed, not a status-mirror). The predicate is a repo-free `AllowedActionResolver.canMarkReviewed(viewerIsDirectManager, PlanState, ReviewStatus)` that **mirrors — never calls —** `authorizeReviewMutation` (§24/§31). Replaced the hardcoded `unresolvedDisputeCount=0` with the count **derived from the already-loaded `commitmentDtos`** (non-null nested `dispute` = OPEN/IC_RESPONDED, 5.3b) → no extra query, no N+1 (§35), with §9-projection parity asserted. **§35 leak guard:** the IC owner (and any non-direct-manager) sees empty `allowedActions`; the 4 `PlanMapper` callers traced (the 3 lifecycle responses pass the owning IC → `viewerIsDirectManager=false` → no leak); the E16 `ReviewService` caller of `ReviewMapper.toDto` updated to `canMarkReviewed=false` (a just-marked review isn't NOT_REVIEWED).

## Decisions made

- **E13 cross-table filters as a `crossTablePredicates` helper** (not extending `summaryPredicates`) — it needs the `CriteriaQuery`/`Subquery` handle; reused by page+count (6.5a-2).
- **E14 `supportingOutcomeId` → parent-DO resolution** (Option A) — the cell grain is DO; a commitment-EXISTS narrowing would leave the DO-level counts misleading. Count-honest (6.5b).
- **E15 sort appends `id ASC`** (orch ADD) — deterministic pagination while `createdAt` is unpopulated (the JPA-auditing populator is a standing carry-forward); a strict F.5 refinement, not a deviation (6.5b).
- **E15 empty SO groups OMITTED** — a revisitable reading of REQ-F-022 ("see outcomes and commitments" = the report's actual SO-grouped work); flip to include-uncovered-SOs trivially if the human wants coverage-gap visibility (6.5b).
- **6.5b single combined commit** — the shared `ManagerController` carries both endpoints; a 2-way split leaves commit-1 non-compiling (breaks bisectability) and the security review covers the whole tree.
- **6.6 in-place test strengthening** (no new consolidated `ManagerReadIdorMatrixTest`) — keeps denial coverage co-located; the service-level matrix owns the cross-cutting SENTINEL sweep.
- **6.7 single `@Transactional` rebuild** (atomic swap — no half-rebuilt read) reusing `recomputeForPlan`; `PROJECTIONS_REBUILT` free-string job action (§15, parallel to `PLAN_SHELLS_GENERATED`, no enum member); the `em.flush()/clear()` drop.
- **6.8 `NOT_REVIEWED`-only affordance narrowing** (§31 subset); `unresolvedDisputeCount` derived from the loaded nested disputes (not the projection — decoupled, no N+1).

## Decisions explicitly NOT made (deferred / parked)

- **`rebuild==seed` (Appendix-E R1–R6)** — pends the **HELD V5/V6 demo seed** (tasks 3.1b/10.2/10.3; migrations are only V1–V4). Phase 10. In Carry-forward. The lead is surfacing the Phase-10-seed timing to the user.
- **§9 command-center `summary` field** (the manager SLA strip's server source) — DEFERRED backend follow-up; the frontend computes a client-side demo strip (D-1). User-prioritized on return.
- **MARK_REVIEWED widen to `REVIEWED_WITH_DISPUTES`** (a "re-confirm after disputes clear" affordance) — NOT built; only wanted if the dispute-resolve auto-progression (REVIEWED_WITH_DISPUTES→REVIEWED, §17) is NOT in place. Revisitable.
- **The web-orch's 3 incoming B.11 follow-ups** (`resolvedDisputeCount` / `unlinkedCount` / `reviewedAt`, origin ST.8b) — arrive in Carry-forward at the web-orch's seal; the next backend round picks them up.

## TDD compliance

**Clean — no violations.** Each slice ran RED→Step-2.5→GREEN; the orchestrator reviewed every Step-2.5 + Step-9. 6.6 was honest coverage-hardening over already-audited behavior (no RED→GREEN — flagged + affirmed as the correct posture; teeth from assertion specificity). 6.7 sent design-first at Step-2.5 (a heavy integration slice). `./gradlew check` green on each. Ad-hoc security-reviewer CLEAN PASS on 6.5a-2/6.5b/6.6; 6.7 (SYSTEM job) + 6.8 (read-affordance) needed none per the reviewer policy.

## Reachability (Step 7.5)

All features reachable from production HTTP paths: 6.5a-2 (the 4 new `@RequestParam` on `GET /api/manager/command-center`), 6.5b (`GET /api/manager/heatmap` + `/heatmap/{cellId}/drilldown`), 6.6 (the denial paths on E13/E14/E15, endpoint-tested), 6.8 (`MARK_REVIEWED` on the E3/E4 plan read). 6.7 is the one-shot `--app.job=rebuild-projections` runner (gated `ApplicationRunner`, `web-application-type=none` exit). No tested-but-unwired gaps.

## Security review

6.5a-2 / 6.5b / 6.6 each got an ad-hoc security/completeness review — **all CLEAN PASS, 0 findings** (IDOR scope from the principal only, never a request value; own-cell existence-hiding `404`; injection-safe Criteria; no leak; the IDOR tests have genuine teeth). 6.7 (SYSTEM batch job) + 6.8 (read-affordance on the already-authorized E3/E4) are not rule-#3 surfaces — no reviewer; the §35 affordance-leak test is 6.8's safety pin.

## Open follow-ups

### Step-9 items (routed hot this arc — orchestrator-written, in the round commits)
- `apps/wc-api/CLAUDE.md`: the B.12 heatmap read-DTO row; the §6/§17-coverage note; the §9-complete note on the projection row; the 6.5a-2 command-center-filter row; the B.7 caveat-resolved note (6.8). **LESSONS** §38-addendum (denial-endpoint audit-teardown) + §40 (rebuild-job pattern). **ARCHITECTURE** F.5 (E15 `id` tiebreaker). **MVP_TASKS** the 6.5–6.7 ticks + 2 Log entries + the Carry-forward triage (6ec8c8a + the 081 seal).

### Carried obligations (live — backend paused)
- `rebuild==seed` (V5/V6 seed, Phase 10); the §9-summary field; the web-orch's 3 B.11 follow-ups; the JPA-auditing populator (the unblocked standalone — once `createdAt` is populated, the E15 `id` tiebreaker becomes a true secondary, and the commitment-order switch from `OrderByIdAsc` → `createdAt`-asc lands).

### Wiring tasks
None outstanding — all features wired + reachable.

## How to use what was built

**Phase 6 is COMPLETE and the §9 manager-projection arc is end-to-end:** the 9 synchronous incremental triggers + a from-source rebuild that corrects drift, both through the one `ProjectionRefresher.recomputeForPlan` derivation; the manager READ surface — E13 command center (paginated, all 7 REQ-F-023 filters, IDOR-safe), E14 heatmap (bounded, DO-grained), E15 drilldown (own-cell, per-SO paginated) — is fully built, **IDOR-complete** (the §6/§17 matrix + positive denial audits), **N+1-free**, and **zero-drift** with the frontend (`dtos.ts`/`managerApi`); and the manager **plan-read** (E3/E4) now emits the `MARK_REVIEWED` affordance + a real `unresolvedDisputeCount`. **Backend pauses here** — the next slice (the §9-summary field, the V5/V6 Phase-10 seed, or the web-orch's B.11 follow-ups) awaits the user's prioritization; the orchestrator persists to hold the carry-forwards and spin a fresh implementer when work is picked.
