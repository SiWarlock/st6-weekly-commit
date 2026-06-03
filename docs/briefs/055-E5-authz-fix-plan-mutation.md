# /tdd brief — E5 authz fix: `POST /plans/{id}/commitments` create must be owner-only (§6 / rule #3)

> **SAFETY-CRITICAL standalone slice (Phase-4→5 boundary).** The shipped 3.4a **E5 commitment-create uses `authorizePlanAccess`** (which admits IC-owner **AND** manager-direct-report) → a manager can author a PLANNED commitment on a direct report's DRAFT plan, violating **§6 IC-only-authorship** (rule-#3-adjacent). Every other authorship/mutation is already owner-only (E6/E7 `authorizeCommitmentMutation`; E8/E9/E10 lock/start/close + **E11 unplanned-create** `authorizePlanMutation`). E5 is the lone gap. **Lead-greenlit** (the user-escalated Finding from handoff 005). **Own commit + ad-hoc `security-reviewer`** — never bundled. For the standing impl (`b7849b0e`).

## Feature
`POST /api/plans/{id}/commitments` (E5 create) becomes **owner-only**: only the plan's owning IC may author a commitment; a manager-direct-report who can *read* the plan gets `403 PLAN_OWNER_REQUIRED` + a denial audit; a cross-team/missing plan stays IDOR-safe `404`. Swaps the create authorizer from `authorizePlanAccess` → `authorizePlanMutation`.

## Use case + traceability
- **Task ID:** E5-authz-fix (a 3.4a correctness fix; sequenced at the Phase-4→5 boundary)
- **Architecture sections:** `ARCHITECTURE.md §6` (central authorization — IC self-authorship; manager scope is read/review, NOT authorship), **safety rule #3** (IDOR-safe central authz), §5/B.21 (`PLAN_OWNER_REQUIRED` — already realized at 3.5). REQ: §6 IC-only-authorship.
- **Related:** the Finding in `docs/team-handoffs/005-...`; LESSONS §17 (central authorizer), §27 (`authorize…Mutation` access-then-capability pattern), §28 (`authorizePlanMutation` realized at lock). Session doc 012 (Phase-4 context).

## ROOT CAUSE (verified against HEAD — the fix is one line + tests)
- **`CommitmentService.java:79`** — `create` (E5) calls `authz.authorizePlanAccess(actor, planId)`. `authorizePlanAccess` admits IC-owner OR active-manager-of-owner (correct for **reads** like E4); for an **authorship mutation** it must be `authorizePlanMutation` (owner-only → `403 PLAN_OWNER_REQUIRED` for a manager-direct-report; IDOR `404` for cross/missing).
- **NOT a gap:** E11 `createUnplanned` (line 131) already uses `authorizePlanMutation` (owner-only ✓); E6 `update` / E7 `discard` use `authorizeCommitmentMutation` ✓. **E5 is the only authorship path on `authorizePlanAccess`.** `PlanService.java:77` uses `authorizePlanAccess` for the **E4 read** — correct, leave it.

## Acceptance criteria (what "done" means)
- [ ] `POST /api/plans/{id}/commitments` (E5) authorizes via **`authorizePlanMutation`** (the first service statement — chokepoint-first, mirroring E11/E6/E7).
- [ ] **A manager-direct-report** creating a commitment on a report's DRAFT plan → **`403 PLAN_OWNER_REQUIRED`** + a denial `audit_event` (the bug, now closed).
- [ ] **The owning IC** creating on their own DRAFT plan → still succeeds (happy path unchanged).
- [ ] **A cross-team / missing plan** → IDOR-safe `404` (no existence leak) — unchanged.
- [ ] The stale javadoc (`CommitmentService` line ~118, in `createUnplanned`'s doc) that contrasts "unlike E5 which uses `authorizePlanAccess` (admits managers)" is corrected — E5 is now owner-only too.
- [ ] `./gradlew check` green; ad-hoc **security-reviewer PASS**; the existing E5 owner-happy + cross-owner-404 tests stay green (`CommitmentCreateEndpointTest`, `CommitmentServiceTest`).

## Files expected to touch
**Modified:** `commitment/CommitmentService.java` (line 79 `authorizePlanAccess`→`authorizePlanMutation`; fix the line-118 javadoc); `commitment/CommitmentServiceTest.java` (update the unit-test authz mock/verify: `authorizePlanMutation` is now the create chokepoint — verify it's called; the denied-authorize → `verify(plans, never()).save(...)` pin); `commitment/CommitmentCreateEndpointTest.java` (+ the manager-403 test; confirm no existing test asserts the OLD buggy manager-can-create behavior — if one does, INVERT it).
**No new files.** **No production change beyond the one-line swap + the javadoc.**

## RED test outline (Step 2)
1. **`create_managerDirectReport_403PlanOwnerRequired`** (NEW — the regression pin for the Finding) — a manager with an active relationship to the plan's owning IC POSTs a commitment to the report's DRAFT plan → `403`, `$.code=PLAN_OWNER_REQUIRED`, a denial audit row written, `weekly_commitment` row count unchanged. This test **fails RED on the current code** (manager currently gets 201 — the bug) and passes after the swap.
2. **`create_owningIc_succeeds`** (exists / confirm) — owner still gets `201` + the commitment created (happy path regression guard).
3. **`create_crossTeamOrMissingPlan_404`** (exists / confirm) — IDOR-safe `404` unchanged.
4. **Unit (`CommitmentServiceTest`):** the create authz chokepoint is `authorizePlanMutation` (verify called first; on a denied authorize, `verify(plans, never()).findById/save` — the chokepoint pin, LESSONS §25/§27).

## Cross-doc invariant impact
- **None new.** `PLAN_OWNER_REQUIRED` already in §5/B.21 (realized at 3.5). No Appendix-A/DTO/schema change. I'll note the E5-now-owner-only correction in the Log + (if the `CreateCommitmentRequest`↔B.6 or an authz row warrants it) a one-line cross-doc touch at Step 9 — flag if you think a row needs it; my default is Log-only (it's an authz-call fix, not a contract change).

## Things to flag at Step 2.5
1. **[SAFETY] Confirm `authorizePlanMutation` is the exact right authorizer** — owner-only, `403 PLAN_OWNER_REQUIRED` for a manager-direct-report, IDOR `404` for cross/missing, denial audit on the 403. (It's the same one E11/lock/start/close use — the §6/rule-#3 chokepoint.) Confirm the swap is the whole fix (no other create path admits managers).
2. **Existing-test inversion check.** Does any current E5 test assert a manager/non-owner-with-access CAN create (the buggy behavior)? If yes, INVERT it to expect `403` (don't leave a test pinning the bug). Report what you found.
3. **Audit on the new 403.** `authorizePlanMutation` already writes the denial audit (like E11). Confirm the manager-403 test asserts the audit row (safe metadata, §15) — consistent with the other mutation-denial audits.

## Dependencies + sequencing
- **Depends on:** 3.5 (`authorizePlanMutation` + `PLAN_OWNER_REQUIRED` exist); 3.4a (the E5 create being fixed).
- **Blocks:** nothing functionally, but it's a **safety correctness fix** — closes the §6 authorship hole before Phase 5. Lands at the Phase-4→5 boundary.

## Estimated commit count
**1 — standalone safety fix (do NOT bundle).** A surgical authz correctness fix; gets its own commit + ad-hoc **security-reviewer** (rule #3 / §6). Tiny diff, high safety value.

## Lessons-logged candidates anticipated
- **Possibly a convention note** — "authorship mutations use `authorize…Mutation` (owner-only), NEVER `authorize…Access` (which admits managers for reads)" — but this is already implicit in §17/§27. Flag at Step 9 if the Finding's root cause (a read-authorizer reused for a create) is worth an explicit forbidden-pattern note (e.g. a CommitmentService/CLAUDE forbidden-pattern: "never authorize a create/mutation with `authorize…Access`"). My lean: a short forbidden-pattern addition is worth it (it's a repeat-risk class — the next create endpoint could make the same mistake).

## How to invoke
1. Read this brief — esp. the ROOT CAUSE (one-line swap) + Step-2.5 Q2 (existing-test inversion).
2. Run `/tdd E5-authz-fix-plan-mutation`.
3. Step 2 RED: `create_managerDirectReport_403PlanOwnerRequired` should fail on current code (manager gets 201) — that's the bug reproduced. Then swap → GREEN.
4. Pause at Step 2.5; send the test design + answers + your existing-test-inversion finding. I reply `APPROVED.`/`TWEAK:`/`ADD:`.
5. Step 7→8: run the ad-hoc **security-reviewer**; surface in Step 9.
