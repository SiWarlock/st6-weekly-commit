# Handoff — st6-main backend ORCHESTRATOR cycle, mid-Phase-4 (2026-06-03)

> **Outgoing orch (`16e30b32`) cycled at 66% (~1.4 slices to ACTION)** after persisting across the whole 3.5-keystone → Phase-3-close → 4.2 → impl-cycle → 4.1 → 4.3 arc. Lead-approved proactive cycle at a clean slice boundary (4.3 landed). **The backend implementer (`0e5d37f3`) stays alive** (~43%, healthy) — only the orch cycles. Same fresh-for-the-next-bulk logic the team has applied throughout.

## YOUR IMMEDIATE NEXT ACTIONS (fresh orch)
1. Run `/orchestrate-start` to orient (this handoff + session docs 009/010 + the tracker + LESSONS §22–29).
2. **Run `/orchestrate-end` to SEAL the Phase-4 round — I deferred the shared-`MVP_TASKS.md` routing to you** (completing the full seal myself would have pushed me to HARD-STOP). The backend-area routing (LESSONS + CLAUDE rows) + briefs are ALREADY committed in my cycle commit; **you write only the `MVP_TASKS.md` seal + one CLAUDE cross-doc row** (details in "THE DEFERRED SEAL" below). **Claim the shared-doc window from `st6-main-wc-web-orchestrator` first** (two-way serialize).
3. **Then author + dispatch brief `045`** = **4.4 carry-forward** (the next Phase-4 slice) to the impl (`st6-main-wc-api-implementer`, `0e5d37f3`, idle + waiting) — UNLESS the user prioritized the E5-fix (see OBLIGATIONS).

## STATE
- **HEAD:** `5af8356` (4.3) + my cycle commit (lands handoff 005 + LESSONS §27-ext/§29 + CLAUDE §29-index/B.6-note + briefs 038/039/041). Backend **Phase 4 in progress**: 4.2/4.1/4.3 landed + green; reconciliation lifecycle entry + outcome-recording + unplanned-create done.
- **Phase-4 slices landed this round:** **4.2** (`e19a39a`) start-reconciliation E9 (LOCKED→RECONCILING, IC_RECONCILIATION non-blocking sync, reuses §28); **4.1** (`a35961a`) outcome-recording PATCH E6 (the per-state editable-field allow-list — LESSONS §27-ext; security-reviewer PASS); **4.3** (`5af8356`) unplanned-create E11 (server-forced UNPLANNED, REQ-F-025 byte-identity; security-reviewer PASS).
- **Session docs:** 009 (orch, 3.4b–3.5 Phase-3-complete) + 010 (impl, 3.4b/3.5/4.2). Phase-3 round sealed `38a596d`.
- **Impl:** `0e5d37f3` alive, ~43%, idle — did 4.1+4.3 this session; healthy budget; waiting for brief 045.

## THE DEFERRED SEAL (what YOU write at `/orchestrate-end` — claim the window first)
**`MVP_TASKS.md` (shared — serialize with the frontend orch):**
- **Tick the checkboxes** for **4.2** (the `### 4.2` block), **4.1** (`### 4.1`), **4.3** (`### 4.3`) — all landed + green + (4.1/4.3) security-reviewed. (3.5's boxes + the Phase-3 acceptance criteria were ticked at the Phase-3 seal `38a596d`.)
- **Update "Currently in progress"** (backend para): replace the stale "next = brief 038 → impl 7ae8e1cd" — now 4.2/4.1/4.3 LANDED (`e19a39a`/`a35961a`/`5af8356`); next = **4.4** (brief 045); impl is `0e5d37f3` (the 7ae8e1cd→0e5d37f3 impl cycle happened mid-round); orch cycled (me→you).
- **Add a Log entry** — the Phase-4 round (4.2/4.1/4.3); decisions: the §28-reuse transitions, the per-state allow-list (4.1), server-forcing-via-DTO-omission (4.3); the E5 Finding escalated; the impl + orch cycles.
- **Add a Carry-forward item — the §9 `blocked_count`-SOURCE pin** (deferred from 4.1): does `reconciliation_outcome=BLOCKED` feed `blocked_count` (union with `work_type=BLOCKER`), or a separate outcome-count column? §9 pins only `misaligned_count`; the `blockedCount` cell is empty; 3.5 chose `work_type=BLOCKER`; 4.1 kept it stable (no `ProjectionService` change). **Pairs with the existing 3.5 `carry_forward_count`-source pin** — pin BOTH at the manager-projection/reconciliation slice. Don't guess in code.

**`apps/wc-api/CLAUDE.md` (backend-area, no window):**
- **Add a cross-doc row for `CreateUnplannedCommitmentRequest`↔B.6 (E11 request)** — the 4.3 DTO (validated record; no `workType`/`commitmentKind` — server-forced UNPLANNED; mirrors the `CreateCommitmentRequest`/E5 row minus those). I deferred this one row; the §29-index + the PatchCommitmentRequest-B.6 note are already committed.

**Already committed in my cycle commit (don't redo):** LESSONS §27-extension (per-state allow-list realized at 4.1) + §29 (the `@SpringBootTest`/`@MockBean` context-cache Hikari-pool cap — 4.1's test-infra catch); CLAUDE §29-index row + the PatchCommitmentRequest-B.6 note; briefs 038 (4.2) / 039 (4.1) / 041 (4.3); this handoff.

**Audit actions (no §15 edit needed):** `OUTCOME_RECORDED` (4.1) + `UNPLANNED_COMMITMENT_CREATED` (4.3) are valid audit actions; §15's emitted-signals list (illustrative) doesn't need a per-commitment line (the reconciliation-lifecycle signals cover it). Consistent call across both.

## PHASE-4 REMAINING (your work)
- **4.4 carry-forward (E12, brief 045)** — the most complex Phase-4 slice: source commitment `reconciliation_outcome=CARRIED_FORWARD` (the ONLY path that sets it — 4.1 rejects a direct PATCH-CARRIED_FORWARD `400`); create a successor in the next Mon–Sun plan (`carry_forward_source_commitment_id` self-link); **idempotent per source** (re-invoke returns the existing successor); create the next-week DRAFT shell only if absent (reuse on unique conflict); RECONCILING-only; **dual-week projection** upsert; no sync record. Reuse `OrgTimeConfig` week resolver, `authorizePlanMutation` (owner-only), `ProjectionService.recompute`. **Consider the ad-hoc security-reviewer** (touches carry-forward state + dual-week projection).
- **4.5 close-reconciliation (E10)** — RECONCILING→RECONCILED; completeness validation (every planned has an outcome AND every unplanned has outcome+SO) → `422 UNPLANNED_MISSING_LINK_AT_CLOSE`+fieldErrors; **extend the 4.1 per-state allow-list** so RECONCILING allows `supportingOutcomeId` for UNPLANNED commitments only (planned baseline stays frozen — LESSONS §27-ext flags this). Symmetric transition to 4.2 (reuse the §28 shape; consider extracting a shared `transition(...)` helper if the lock/start/close duplication is now real).
- **4.6 baseline-immutability proof (REQ-E-005)** — the integration + Cypress proof that a full reconciliation pass (start→unplanned→outcomes→carry-forward→close) leaves the locked planned baseline byte-identical. The realistic lock→start→close chain + the IC_PLANNING/IC_RECONCILIATION sync coexistence land here.

## YOUR OBLIGATIONS (carried forward)
- **⚠ E5 authz Finding — ESCALATED to the user, PENDING approval (route the fix when the lead relays).** The shipped 3.4a **E5 `POST /plans/{id}/commitments` (create) uses `authorizePlanAccess`** (IC-owner OR manager-direct-report) → a manager can author a PLANNED commitment on a report's DRAFT plan (violates §6 IC-only-authorship; rule-#3-adjacent). Every other authorship/mutation is owner-only (E6/E8/E9/E11). **Fix (small, obvious):** E5 `create` → `authorizePlanMutation` + a manager-403 test (mirrors E11). I recommended its **own slice** (with security-reviewer), NOT folded. **The lead is relaying to the user** — when approved, route it (front-load if prioritized). Brief number from 045+.
- **Disputes Option-A (you own the B.6 contract edit).** User-approved (`docs/planning/023` §6): at the Phase-3/5 **disputes** slice, land `dispute?: AlignmentDisputeDto` in `WeeklyCommitmentDto` B.6 + drop `hasUnresolvedDispute` + the disputes entity/DTO/endpoint, then **ping `st6-main-wc-web-orchestrator`** (by role name — it cycled to a fresh session) to mirror `dtos.ts` (unblocks frontend 9.11a). HELD behind Phase 4 (lead's default).
- **Deferred §9 doc-pins (Carry-forward):** the `blocked_count`-source (4.1) + `carry_forward_count`-source (3.5) — both pin at the manager-projection/reconciliation slice.
- **`MANAGER_ROLE_REQUIRED` carry-forward** — manager-resource controllers must use the coded+audited denial (not coarse `@PreAuthorize`); applies at the manager-API surface.

## CONVENTIONS (quick)
- **Brief lane:** 037–044 consumed (037 3.5, 038 4.2, 039 4.1, 040 ST.3, 041 4.3, 042 ST.4, 043 ST.5a, 044 ST.5b). **Backend-orch next-free = 045.** Coordinate new numbers with `st6-main-wc-web-orchestrator` (by role name; it reserves ST.x numbers + pings — flag only on collision).
- **Shared-doc serialize (two-way) with the frontend orch:** claim the `MVP_TASKS.md`/`ARCHITECTURE.md` window before committing them; the frontend track is render-only on ARCHITECTURE (touches only MVP_TASKS frontend regions). git-confirm the shared tree is clean before/at your seal.
- **Reviewer policy (user):** per-slice reviewers OFF by default; ad-hoc `security-reviewer` on rule-touching slices (4.1 per-state gate ✓, 4.3 authz ✓ — both PASS). 4.4/4.5/E5-fix: your judgment (4.5's close gate + the E5 authz fix likely warrant it).
- **Backend gate** = `./gradlew check` from `apps/wc-api/` (LESSONS §6). Commit cadence: impl commits slice code; orch round commit lands docs. Push deferred (no remote). Co-Author trailer: `Claude Opus 4.8 (1M context)`.
- **The §28 lock-transaction pattern + §27 per-state allow-list + §29 Hikari-cap** are the load-bearing Phase-4 reuse surfaces — read them.
