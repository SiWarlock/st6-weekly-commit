# Lead compaction handoff — ST6 (2026-06-03)

> **Type: COMPACTION handoff, NOT a teardown.** The team-lead session hit ~70% context; per dreddy's directive ([[lead-context-limit-handoff]] in memory) the lead pauses everyone idle, writes this doc, hands a resume prompt, and the user compacts the lead **in place** (same session continues). Teammates stay ALIVE/idle through the compaction. Resume from this doc + the durable files (git, MVP_TASKS.md, ARCHITECTURE.md) — those are the source of truth; this is a pointer.

## Team composition (3 parallel teams; the user bridges them)
- **`st6-main`** (this lead): backend pair (`st6-main-orchestrator` + `st6-main-wc-api-implementer`) + frontend pair (`st6-main-wc-web-orchestrator` + `st6-main-wc-web-implementer`). All 4 fresh-ish (cycled recently). Both pairs being **paused idle** for this handoff (finish current slice + commit + idle, stay alive).
- **`st6-infra`** (separate team, the user's lead): Phase 12 IaC. **DONE + merge-prep done** (it merged main→infra-track, resolved conflicts, validated). On branch `infra-track` (worktree `…/ST6-infra`).
- **`st6-test`** (separate team, the user's lead): Phase 11 BDD/acceptance in `apps/wc-e2e`, branch `test-track`. Running. (It found + drove the `422`→`409` ARCHITECTURE safety fix.)

## Current state (re-verify on resume via `git log` + MVP_TASKS "Currently in progress")
- **HEAD ~`ef4eb0a`** (frontend brief-lane fix). Recent safety fix: `cc0bb6b` (ARCHITECTURE `422`→`409` for `UNLINKED_PLANNED_COMMITMENT`, rule #1).
- **Backend:** Phase 2 (auth/authz). Done: 2.1 JWT decoder, 2.2 claim mapper, 2.3 demo-auth filter (rule #5), 2.4 PrincipalResolver, **2.5 `DomainAuthorizationService` (IDOR, rule #3) — finishing now** (lead-adjudicated its 3 safety Qs → all ACCEPT; see `docs/planning/022-2.5-authz-safety-adjudication.md`; ad-hoc security-reviewer running). Next: **2.6 → 2.7 → Phase 3** (lock enforcement — must use `409 UNLINKED_PLANNED_COMMITMENT`, NOT 422).
- **Frontend:** Phase 9. Done: 0.6/ST.1/ST.2 + 9.1–9.7. **9.8 in progress.** Next: **9.9+**. Plan: `docs/planning/frontend-styling-proposal.md` (signed off: Fork1=A, Fork2=2, Fork3=indigo).

## RESUME ACTIONS (in order)
1. **Confirm both pairs are paused/idle** (acks: "paused — 2.5 at <hash>" / "paused — 9.8 state"). Re-read `git log -5` + MVP_TASKS "Currently in progress" for exact state.
2. **Clean main FIRST:** the light pause may have left the backend orch's `2.4`/`2.5` hot-routing uncommitted (the `MANAGER_ROLE_REQUIRED` §5/B.21 pin, the §6 "inactive-owner not auto-denied" design note, LESSONS, MVP_TASKS ticks). Have the backend orch commit it (`git status` to confirm; plain `git add` its sections — main's quiet) so the working tree is clean. THEN:
3. **Land the infra merge while main is still quiet** (before re-dispatching pairs): `infra-track`→`main` (merge-prep is done on infra-track). `infra/` is conflict-free; resolve any residual `MVP_TASKS`/`ARCHITECTURE` Phase-12-section conflicts; then `./gradlew check` + JS build green. Coordinate with the user (they drive the `st6-infra` lead).
3. **Re-dispatch the pairs:** backend orch → next slice (~`2.6`); frontend orch → next (~`9.9`). (Just message the orchs to resume; they re-derive from MVP_TASKS.)
4. **Phase 10 (seed) decision = HELD** (user's call). Rec: defer to the backend track (apps/wc-api contention + lifecycle-state seed needs Phase 3+); only static org/RCDO/employee seed is cleanly parallel now.
5. Resume normal thin-lead monitoring + per-slice context self-checks on done-with-slice events.

## Standing process facts (most are in MEMORY.md — loaded each session)
- **Per-slice reviewers DISABLED** ([[per-slice-reviewers-disabled]]); `security-reviewer` ad-hoc ONLY on safety slices (e.g. it ran on 2.5).
- **`git add -p` is BLOCKED in-env** → shared-doc (`MVP_TASKS`/`ARCHITECTURE`) commits between the two `st6-main` orchs must **serialize** (ls-first, ping-before-staging, one commits whole-file then the other).
- **Cycle cleanup** ([[cycle-cleanup-stale-registry]]): after shutting down + respawning a teammate, `rm` its old `~/.claude/team-registry/<sid>.json` + `~/.claude/heartbeats/<sid>.json` or `/context-check` shows a false ACTION ghost.
- **Lead delivery quirk** ([[team-lead-verify-via-durable-files]]): the lead gets message *summaries*, not bodies — verify via git/team-history/heartbeats, and for substantive teammate content (safety adjudications, proposals) have them WRITE to a `docs/planning/` doc and read it.
- **Brief numbers:** carry the next-free number into successor-orch spawn prompts; orchs `ls docs/briefs/` + coordinate lanes (backend 022=2.5; frontend 023=9.8; next-free ~024).
- **Cycle trigger:** WARN 70% / ACTION 75%; cycle the at-threshold *pair* (orch+impl together) at a clean boundary, clean ghosts, carry brief-allocation into spawns.

## RESUME PROMPT (user sends this after compacting the lead)
> Resume the ST6 team lead from `docs/team-handoffs/001-2026-06-03-lead-compaction-handoff.md`. Both `st6-main` pairs were paused idle for the compaction. Do the resume actions in order: confirm pairs idle, land `infra-track`→main while main's quiet, then re-dispatch backend (~2.6) + frontend (~9.9) and go back to thin-lead monitoring. Phase 10 is held. The st6-infra + st6-test teams are mine to drive; coordinate the infra merge with me.
