# Handoff — st6-main backend ORCHESTRATOR cycle, Phase-5-COMPLETE → Phase-6-entry (2026-06-03)

> **Outgoing orch (`4570249c`) cycled at 70% WARN** after driving the **entire Phase-5 tail to completion** (5.3b/5.4/5.5/5.5b/5.6/5.7) + the Phase-5-close round seal. **Lead-approved proactive cycle at the clean Phase-5 boundary** — Phase 6 is heavy (projection tables + command-center/heatmap/drilldown), so a fresh full-budget orch for the whole phase is the architecturally-correct checkpoint. **The backend implementer (`0095db66`) PERSISTS** (healthy, lower-context — it carries the Phase-6 work). Orch-only cycle. HEAD = `7b17764`.

## YOUR IMMEDIATE NEXT ACTIONS (fresh orch)
1. Register (the standard team-registry `jq` one-liner) → run **`/orchestrate-start`** (NOT /session-start) to orient (this handoff + session docs **013** + **015** + the tracker Phase-6 section + LESSONS §22–§36).
2. **Phase 5 is COMPLETE — nothing to finish there.** Your first real work is **authoring the first Phase-6 brief** (see NEXT). Confirm direction with the lead before dispatch (the user is napping; the lead logs).
3. **Shared-doc CAUTION:** the **frontend orch was just unblocked** (I freed the MVP_TASKS window at my seal) and is reconciling its disputes-round + cycling its impl — **git-verify the shared tree (MVP_TASKS/ARCHITECTURE) is clean + serialize the window with `st6-main-wc-web-orchestrator` before any shared-doc commit.**

## STATE
- **HEAD:** `7b17764` (session-doc renumber) on `9cd2c8a` (Phase-5-close seal). **Phase 5 (5.1–5.7) COMPLETE.** Backend Phases 1–5 done.
- **Phase-5 slices landed this arc (since the prior handoff 008):** **5.3b** (`1991a91`) B.6 dispute nest → frontend 9.11a unblock · **5.4** (`95559ed`) IC-respond E18 (rule-#2 gated SO-revision; LESSONS §34) · **5.5** (`2d210d0`) resolve E19 (closes the dispute loop) · **5.5b** (`003394a`) dispute affordances on E3/E4 (the `viewerIsDirectManager` threading; LESSONS §35) → frontend controls activated · **5.6** (`19dd610`) draft-visibility acceptance proof (REQ-F-009/010; satisfied-by-prior) · **5.7** (`f31710c`) managerAlignmentNote E6 (field-level authz; LESSONS §36). All `./gradlew check` green; 5.4/5.5/5.7 security-reviewer CLEAN PASS.
- **Two round seals this arc:** `79172c1` (after 5.4, the impl cycle) + `9cd2c8a` (Phase-5 close) + `7b17764` (session-doc renumber).
- **Impl:** `0095db66` (fresh post-5.4, healthy, lower-context) — did 5.5/5.5b/5.6/5.7. **NOT cycled.** It carries Phase 6.
- **Session docs:** **013** (4.6/E5/5.2/5.3/5.3b/5.4, orch-authored) + **015** (5.5/5.5b/5.6/5.7, orch-authored Phase-5-close continuity; renumbered from 014 after a parallel-track collision).
- **LESSONS banked this arc:** **§34** (gated exception to a safety invariant + IC-owner-capability authz), **§35** (per-viewer affordances — compute `viewerIsDirectManager` once at the `PlanMapper` root, thread booleans), **§36** (field-level authz on a shared mutation endpoint). Read §35 — **Phase-6 MARK_REVIEWED reuses the `viewerIsDirectManager` threading.**

## NEXT — Phase 6 (manager command-center / projection-completeness) — author the first brief
The §9 projection TABLES exist (V3: `manager_plan_summary` + `manager_heatmap_cell`); 3.5 ships the recompute-from-source as an INSERT at lock. **Phase 6 makes the manager command-center reads REAL.** The **frontend already built these UIs** (9.9 command-center, 9.10 heatmap/drilldown) against MSW + the contract — so Phase 6 backs them with real data (coordinate the contract with the frontend orch). Surfaces:
- **E13** `GET /api/manager/command-center?weekStart=&page=&size=&sort=&{filters}` — direct-report roll-up (plan state, review state, overdue, dispute state, reconciliation/carry-forward risk; REQ-F-003/019/023). **The §9 command-center `summary` field** (team-level roll-up) is a **frontend Carry-forward dependency** (the at-a-glance SLA strip — server-pagination makes a client roll-up inaccurate).
- **E14** `GET /api/manager/heatmap?weekStart=&definingObjectiveId=&supportingOutcomeId=` — direct-report rows × Defining-Objective columns (REQ-F-020/021).
- **E15** `GET /api/manager/heatmap/{cellId}/drilldown` (manager-own-cell; REQ-F-022). NOTE: per 5.5b, the dispute affordances are emitted on E3/E4 ONLY, **not** E15 — confirm the drilldown's affordance posture.
- **Projection completeness** (the recurring Carry-forward): recompute-from-source `unresolvedDisputeCount` + the §9 `misaligned_count` = (`alignment_status=MISALIGNED` OR open-dispute `flag_type=MISALIGNED`) union + **stale-cell deletion** (3.5 is insert-only); the **MARK_REVIEWED read-affordance** + real `unresolvedDisputeCount` on the plan read (E3/E4) — **reuse the 5.5b `viewerIsDirectManager` threading** in `PlanMapper` (LESSONS §35).
- **Manager-scope authz** on the command-center/heatmap reads (`authorizeTeamHeatmapAccess` coarse gate exists; per-cell `authorizeHeatmapCellAccess` exists) — read-path, but the manager-scoping is rule-#3-adjacent → your judgment on the security-reviewer.

## CARRY-FORWARD — Phase-6 inputs (in MVP_TASKS, triaged at my seal)
- **Projection completeness** (extend `ProjectionService` beyond the 3.5 lock-INSERT: `unresolvedDisputeCount` + `misaligned_count` union + stale-cell deletion). _(origin 3.5)_
- **MARK_REVIEWED read-affordance + real `unresolvedDisputeCount` on the plan read** (thread manager-context into `PlanMapper` — reuse 5.5b's threading). _(origin 5.2; re-seq to Phase 6)_
- **§9 `blocked_count` source-field pin** (`work_type=BLOCKER` only vs union `reconciliation_outcome=BLOCKED`) — pin at the projection slice. _(origin 4.1)_
- **`ReconciliationProjectionRefresher` extraction** — the rule-of-three reconciliation-projection-refresh DRY (5 call sites); a dedicated refactor before/at Phase 6. _(origin 4.5)_
- **`authorizeCommitmentManagerCapability` extraction** — the 3rd commitment-keyed-403 manager-capability authorizer (after `authorizeDisputeCreation` + `authorizeManagerAlignmentNote`); note-if-recurs, NOT yet rule-of-three (§33 excludes review-mutation's 404 tree). _(origin 5.7)_
- **(Frontend dep) the §9 command-center `summary` field** for the at-a-glance SLA strip — the frontend deferred its SLA strip pending this. _(frontend Carry-forward)_
- Low-pri: the partial-unique carry-forward backstop; the 5.3 message-substring-classifier note-if-recurs.

## CONVENTIONS (quick)
- **Brief lane:** backend **through 066 consumed** (5.7); frontend **through 065** (9.14); **backend next-free = 067.** **⚠ Coordinate numbers with `st6-main-wc-web-orchestrator`** — we've had collisions (060 brief, 014 session doc), always resolved by yielding. **Qualify + re-pick the next-free after any cross-track merge** (parallel-track numbering).
- **Shared-doc serialize (two-way)** with the frontend orch — claim the `MVP_TASKS.md`/`ARCHITECTURE.md` window before committing; the frontend uses explicit `git add` (frontend regions) + you do too (backend/orch territory). **git-verify the shared tree is clean before every seal.**
- **Reviewer policy (user):** per-slice reviewers OFF by default; ad-hoc `security-reviewer` on rule-touching slices. Phase-6 reads are mostly read-path; the manager-scope authz is your judgment.
- **Backend gate** = `./gradlew check` from `apps/wc-api/`. Commit cadence: impl commits slice code (explicit `git add`, never `-A`); orch round commit lands docs. Push deferred (no remote). Co-Author trailer: `Claude Opus 4.8 (1M context)`.
- **The §33/§35/§36 authz + affordance-threading patterns are the load-bearing Phase-6 reuse surfaces** — read them before authoring the command-center/projection briefs.
- **Per-slice flow:** Step-2.5 review (magic-words `APPROVED.`/`TWEAK:`/`ADD:`), Step-9 commit-message-first routing, per-slice `/context-check <team>` + lead ping after Step-10. The impl persists across slices (already oriented).
