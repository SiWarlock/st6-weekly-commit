# Handoff — st6-main backend ORCHESTRATOR cycle, pre-3.5-lock (2026-06-03)

> **Outgoing orch cycled at ~65% (~1.2 slices to ACTION) so a fresh full-budget orch reviews the safety-critical 3.5 LOCK.** The brief (037) was authored by the outgoing orch (full Phase-3 context); the **fresh orch dispatches + reviews it**. Same fresh-for-the-critical-work logic the team applied to the impl.

## YOUR IMMEDIATE NEXT ACTION (fresh orch)
1. Run `/orchestrate-start` to orient (this handoff + session 008 + the tracker).
2. **Dispatch brief `docs/briefs/037-3.5-plan-lock-e8.md`** to the impl (`st6-main-wc-api-implementer`, session `7ae8e1cd`, healthy ~38%, standing by). It is **authored but NOT dispatched** — that's your job.
3. **Review the 3.5 Step-2.5 RIGOROUSLY against the `LOCK-INVARIANT REVIEW CHECKLIST` at the bottom of brief 037** (rules #1/#2/#4 + atomicity + SLA + single-source-`canLock`). This is the project's safety culmination.
4. Impl dispatches the **ad-hoc security-reviewer at Step 7→8** (mandatory for 3.5) — route findings; critical → escalate to the lead immediately.
5. Step 9 → route the cross-doc rows + the lesson + land the round commit.

## STATE
- **HEAD:** the 3.4b round-close commit is the outgoing orch's final act (lands the 3.4b hot-routing + brief 037 + this handoff). Phase-3 backend: **3.1–3.4b all committed + green + security-PASS** (3990513/f66c9cf/157f634/ce39d3e/20a103d/dcad85c). The strategy-enforcement spine is complete **up to the lock**; 3.5 is the lock.
- **Session doc 008** (`008-2026-06-03-backend-phase3-rcdo-plan-commitment.md`) = the full 3.1–3.4a narrative; 3.4b is in the tracker + LESSONS §27.
- **LESSONS §22–27** (`apps/wc-api/LESSONS.md`) capture every Phase-3 pattern — read the index in `apps/wc-api/CLAUDE.md`. The 8+ DTO/vocab cross-doc rows are in that CLAUDE.md table.

## 3.5 REUSE (don't re-create — all in brief 037, verified vs committed HEAD)
`AllowedActionResolver.canLock` (3.3a — the precondition predicate, §15 single-source); the 3.4b freeze gates (`LOCKED_BASELINE_EDIT` rule #2 + `alignmentStatus`→`ILLEGAL_STATE_TRANSITION`, already enforced by `CommitmentService`); `ManagerReviewDto` TYPE exists (3.3a — 3.5 adds the mapper + derived `isOverdue`); `ClockConfig` exists (the "NEW ClockConfig" in the 3.5 task is STALE); `IllegalStateTransitionException`(+constraint)/`ErrorCodes`/`ProblemDetailsExceptionHandler`/`WeeklyPlanDto`/`PlanMapper`/`authorizePlanAccess`.

## YOUR OBLIGATIONS (carried forward)
- **Disputes Option-A (you own the B.6 contract edit).** User-approved (`docs/planning/023` §6): at the Phase-3 **disputes** slice, land `dispute?: AlignmentDisputeDto` in `WeeklyCommitmentDto` B.6 + drop `hasUnresolvedDispute` + the disputes entity/DTO/endpoint, then **ping `st6-main-wc-web-orchestrator` to mirror `dtos.ts`** (unblocks frontend 9.11a). 3.3a already ships the documented B.6-minus-dispute subset (zero rework). Tracked in Carry-forward.
- **Brief lane:** 037 = 3.5 (authored). Next-free = **038**. (Frontend orch is at 031+; coordinate around 038.)
- **`MANAGER_ROLE_REQUIRED` carry-forward** (manager-resource controllers must use the coded+audited denial, not coarse `@PreAuthorize`) — applies when Phase-3 reaches the manager API. `COMMITMENT_OWNER_REQUIRED` (3.4b) set the precedent.

## CROSS-TRACK STATE
- **Frontend orch (`st6-main-wc-web-orchestrator`):** idle, holding for the user's Phase-ST styling direction; Phase-9 functional surface complete (163/163). Its next `/orchestrate-end` round will commit brief 031 + the 9.11b/9.13 hot-routing. **Shared-doc serialize is two-way** — claim the `MVP_TASKS.md`/`ARCHITECTURE.md` window (ping it) before your 3.5 round commit; it git-confirmed zero uncommitted shared-doc edits at the last handoff. 9.11a (disputes) is its ONE slice blocked on your B.6 Option-A edit.
- **Infra track (st6-infra):** Phase 12 COMPLETE; not your concern.

## CONVENTIONS (quick)
- Per-slice sub-agent reviewers OFF by default; `security-reviewer` ad-hoc on rule-touching slices (3.5 = mandatory). Lead is in thin-monitoring mode — ping only for escalation or a context tier.
- Commit cadence: impl commits slice code (explicit `git add`); orch round commit lands docs (lessons/cross-doc/tracker/briefs). Push deferred (no remote).
- Backend gate = `./gradlew check` from `apps/wc-api/` (LESSONS §6).
