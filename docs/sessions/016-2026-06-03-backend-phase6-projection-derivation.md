# Session 016 — Backend: Phase 6 opens — §9 projection derivation completion + ProjectionRefresher extraction (6.2 / 6.3a)

- **Date:** 2026-06-03
- **Phase:** Phase 6 (manager projections, command center & heatmap) — entry.
- **Role:** implementer `st6-main-wc-api-implementer` (`0095db66`) — **session doc authored by `st6-main-orchestrator` (`98bd8384`)**, which reviewed every Step-2.5 + Step-9 this round. **The implementer's `/session-end` was SKIPPED by lead decision** (the impl was at 74% after a deep 6-slice session — a full session-end audit+write risked crossing ~80% into the confabulation edge); the orchestrator (healthy, reviewed every slice) writes this continuity record in its place, then the impl is cycled.
- **Predecessor:** [015](015-2026-06-03-backend-phase5-complete.md) (Phase 5 COMPLETE — 5.5/5.5b/5.6/5.7).
- **Successor:** [017](017-2026-06-04-backend-phase6-triggers-badges-command-center.md) (Phase 6 — §9 trigger set complete 6.3b + RiskBadgeDeriver 6.4 + E13 command center 6.5a; impl cycle).

## Why this session existed

The persisted backend impl (`0095db66`, carried over from the Phase-5 close) opened Phase 6 with the two **projection-derivation** slices that make the §9 read-models correct-from-source and centralized — the foundation the manager command-center/heatmap reads (E13/E14/E15, brief 070) and the trigger wiring (6.3b) build on. Both slices are read-model work (no safety invariant); behavior-correcting only where the spec demanded (the `blocked_count` latent-bug fix). After 6.3a the impl hit WARN 74% at a clean boundary → lead-approved impl-only cycle before the heavier 6.3b.

## What was built

### Slice 6.2 — §9 ProjectionService derivation completion (brief 067) — `7e86239`
Completed `ProjectionService.recompute` to derive **all** §9 dispute-driven counts from source. `misalignedCount` (summary + each heatmap cell) = the **deduped union** `alignment_status=MISALIGNED` **OR** an `OPEN`/`IC_RESPONDED` dispute with `flag_type=MISALIGNED` (a commitment satisfying both counts once); the `MISALIGNED` risk-badge consumes the **same** union. Real **`unresolvedDisputeCount`** (was hardcoded `0`) = commitments with an `OPEN`/`IC_RESPONDED` dispute. Disputes loaded **once** over the plan's commitment ids via a new `AlignmentDisputeRepository.findByCommitmentIdInAndStatusIn` (List/rows — one query serves both the union set + the count; empty-id-set guarded → no invalid `IN`); the repo is injected into `ProjectionService` so all 5 `recompute(...)` call sites stay unchanged. **Behavior-correcting:** `blockedCount` source `work_type=BLOCKER` → **`reconciliation_outcome=BLOCKED`** — a **latent bug** (BLOCKER is a planning *category*, not a risk outcome): the binding R5 Grace seed shows a `BLOCKED` badge from a `reconciliation_outcome=BLOCKED` commitment, and `rebuild==seed` (§9/§17) requires reproducing it; the shipped derivation would fail that + false-positive on a COMPLETED blocker-category task. Delta: at lock `blockedCount` is now `0` (no reconciliation outcome yet). `carryForwardCount` unchanged (successor-link reading, §30). Pinned by `recompute_blockedCount_fromReconciliationOutcome` (the R5 §17 regression proof) + the dispute-union/dedup/`unresolvedDisputeCount`/NEEDS_REVISION-excluded/empty-guard unit tests + the cross-status finder `@DataJpaTest`. **Lead-ruled decision** (user napping → delegated): `blocked_count=reconciliation_outcome=BLOCKED`, spec-determined.

### Slice 6.3a — ProjectionRefresher extraction (brief 068) — `0e5d725`
Extracted the "resolve manager from the plan owner → §28 no-manager/no-review skip → load commitments+review → `ProjectionService.recompute`" block — copied **byte-identical** across 5 mutation sites — into `ProjectionRefresher.recomputeForPlan(WeeklyPlan plan)`, the §9 **source-loading entrypoint** the 6.7 rebuild job will reuse. Routed all 5 sites (`PlanLifecycleService` lock/start/close + `CarryForwardService` + `CommitmentService`) through it; deleted the two private `recomputeProjection` methods. **Behavior-preserving** — the endpoint/integration regression net (lock 3.5 / start-close 4.2/4.5 / carry-forward 4.4 / outcome 4.1 / 6.2 derivation) stays byte-for-byte green = the proof; the 8 service unit tests took **mechanical** ctor/verify rewiring (the no-manager/no-review skip coverage relocated to `ProjectionRefresherTest`). Lock keeps its own managerId/review/`MANAGER_REVIEW_BLOCK` logic + the `@Version` txn (rules #1/#2/#4 untouched). Resolves the 4.5-origin carry-forward.

## Decisions made

- **`blocked_count = reconciliation_outcome=BLOCKED`** (lead-ruled, user-delegated) — spec-determined by the R5 Grace seed + `rebuild==seed` (§9/§17), NOT `work_type=BLOCKER` (a planning category) nor the union. The shipped `work_type=BLOCKER` was a **latent bug**, corrected at 6.2 + regression-pinned. §9 + the CLAUDE projection row pinned.
- **§9 command-center `summary` field (the manager SLA strip) DEFERRED** (lead-ruled) — YAGNI: the backend half of a deferred frontend feature. E13 (070) builds to the current `Page<ManagerCommandCenterRowDto>` (B.11/B.20) contract; the summary + the frontend strip get built together iff greenlit on the user's return.
- **6.3 split into 6.3a (extraction, behavior-preserving) + 6.3b (069, new-trigger wiring)** — keeps the behavior-preserving lock-site refactor out of the same commit as new-trigger feature wiring (bisectability + the no-bundle-safety posture).
- **Dispute load lives inside `ProjectionService`/`ProjectionRefresher`** (repo injected) so all 5 existing recompute callers — incl. the safety-critical lock txn — stay byte-for-byte unchanged.

## Decisions explicitly NOT made (deferred to 6.3b / later Phase 6)

- **Stale-cell deletion** — the recompute path is still upsert-only (like 3.5); `respond`'s rule-#2 SO-revision remaps a commitment's Defining Objective → the old `manager_heatmap_cell` goes stale. Add deletion in 6.3b (069) + prove under 6.7 `rebuild==incremental`.
- **The 4 dispute/mark-reviewed trigger wirings + the RISK-003 transactional-rollback test + the §17 integration** — brief 069 (6.3b). (3 of the 4 trigger methods already load the plan in-scope; `respond` needs an added plan load + a docstring correction — its 5.4 "NO projection change" is superseded.)
- **The full Appendix-E R1–R6 seed-reproduction integration** (`rebuild==seed`) — depends on the HELD V5/V6 demo seed → 6.7 / seed slice. (6.2 landed the unit/edge/error + the R5 BLOCKED-badge proof.)
- **`MARK_REVIEWED` read-affordance + real `unresolvedDisputeCount` on the plan-read (E3/E4)** — the projection side is done (6.2); the plan-read half (thread manager-context into `PlanMapper`, reuse §35) folds into 070/6.5 or a dedicated slice.
- **`authorizeCommitmentManagerCapability` extraction** — still 2 instances (note-if-recurs at the 3rd).

## TDD compliance

**Clean — no violations.** 6.2 ran RED→Step-2.5→GREEN (new unit + repo tests; the existing BLOCKED assertion re-sourced as intended); 6.3a was a behavior-preserving extraction (existing endpoint/integration tests the regression net + a new `ProjectionRefresherTest`; the 8 service unit tests mechanically rewired — disclosed at Step 2.5, accepted). The orchestrator reviewed every Step-2.5 + Step-9. `./gradlew check` green on each. No security-reviewer (read-model derivation + internal refactor — no safety invariant, per policy).

## Reachability (Step 7.5)

6.2's corrected derivation flows through all 5 wired `recompute` sites (the dispute repo autowired; signatures unchanged) → the §9 read-models E13/E14 will consume. 6.3a's `ProjectionRefresher` is reachable from all 5 mutation sites (Spring DI; the endpoint tests exercise the real refresher) + is the 6.7 rebuild entrypoint. The 4 dispute/mark-reviewed triggers are NOT yet wired (6.3b/069) — where the dispute-driven counts actually change at runtime.

## Open follow-ups

### Step-9 items (routed hot — orchestrator-written, in the round commit)
- **ARCHITECTURE §9:** `blocked_count = reconciliation_outcome=BLOCKED` pin. **CLAUDE projection row:** the 6.2 derivation (union/real-count/blocked correction) + the 6.3a `ProjectionRefresher` centralization. **LESSONS §37** (sibling-table projection counts: load-once-derive-in-memory + pin the source against the binding seed) + index row. **MVP_TASKS:** 6.1/6.2/6.3a status annotations + the Log entry + the Carry-forward triage (3 resolved / 2 narrowed).

### Carried obligations (live — Phase 6)
- 6.3b (069): the 4 trigger wirings + stale-cell deletion + RISK-003 rollback + §17 integration. Then 6.4 (RiskBadgeDeriver) / 6.5 (E13/E14/E15 + manager pkg; security-reviewer ad-hoc) / 6.6 (IC-denial IDOR matrix; security-reviewer ad-hoc) / 6.7 (rebuild). The plan-read MARK_REVIEWED affordance (§35 threading). The full Appendix-E `rebuild==seed` integration (V5/V6 seed dep).

### Wiring tasks
None outstanding from this round — both slices fully wired (6.3a is the centralized refresh seam; the 4 new triggers are explicitly 6.3b's scope).

## How to use what was built

The §9 manager read-models now derive every count correctly from source (the dispute-union `misaligned_count`, real `unresolved_dispute_count`, the corrected reconciliation-outcome `blocked_count`) and the in-txn refresh is centralized in `ProjectionRefresher.recomputeForPlan(plan)` — the single seam the remaining Phase-6 triggers (6.3b) plug into and the rebuild job (6.7) reuses. **Phase 6 continues at 6.3b (brief 069)** with a fresh implementer: wire the 4 dispute/mark-reviewed triggers + stale-cell deletion, prove transactional rollback (RISK-003), then build the manager-facing reads (E13/E14/E15) that make the frontend's command-center/heatmap UIs real.
