-- V6 — demo fixture plans + lifecycle state matrix (tasks 10.3-10.5; ARCHITECTURE.md Appendix E
-- Part 2 — the binding fixture spec, §3/§9/§10).
--
-- The deterministic SOURCE fixtures for the demo's lifecycle-state matrix: one plan per persona
-- spanning every plan state, their commitments (linked + a deliberately-unlinked DRAFT one), manager
-- reviews, disputes (OPEN + RESOLVED), the two-week carry-forward chain, and the Outlook sync records
-- (FAILED + SYNCED + MANAGER_REVIEW_BLOCK).
--
-- SOURCE rows ONLY. The manager_plan_summary / manager_heatmap_cell projections are NOT hand-seeded
-- here — they populate on deploy via the 6.7 ProjectionRebuildRunner (truncate + recompute from
-- source, §9/§17), run by a rebuild Job after the migration Job. The source fixtures ARE the truth;
-- the rebuild reproduces the projections from them.
--
-- Reviews store what the lifecycle services would have produced (so the derived reads match the
-- matrix): manager_review.status ∈ {NOT_REVIEWED, REVIEWED, REVIEWED_WITH_DISPUTES} — REVIEWED_WITH_
-- DISPUTES is the STORED status the deriver writes at mark-reviewed / open-dispute time
-- (ReviewService / DisputeService), NOT read-derived. OVERDUE is NEVER stored (rule #6): R2 stores
-- NOT_REVIEWED + a past review_due_at so the read-time isReviewOverdue predicate (status=NOT_REVIEWED
-- ∧ now > review_due_at) yields OVERDUE at the demo Clock anchor (2026-06-02 morning CT).
--
-- Lives in db/demo-seed (Opt-1, same as V5) — loaded by the migrate Job profile, never the shared
-- test harness. Deterministic + idempotent (§13): fixed literal UUIDs (per-entity hex prefix
-- continuing V4/V5 a0/b0/c0/d0/e0 → plan f0, commitment 10, review 12, dispute 13, sync 14); fixed
-- timestamps on the week grid (current week 2026-06-01 Mon..06-07, prior week 2026-05-25..05-31, org
-- tz America/Chicago = CDT/UTC-5 → 17:00 CT = 22:00Z). `ON CONFLICT (id) DO NOTHING` → re-run is a
-- no-op. Parent-first insert (plan → commitment → review → dispute → sync); the carry-forward source
-- commitment is inserted before the commitment that references it.

-- 1. Plans (8): R1-R4 LOCKED + R5 RECONCILING + R5 prior RECONCILED + R6 DRAFT + Dana prior RECONCILED
insert into weekly_plan
  (id, employee_id, week_start_date, week_end_date, state,
   generated_at, locked_at, reconciliation_started_at, reconciled_at,
   created_by, created_at, updated_by, updated_at)
values
  -- R1 Priya — LOCKED (current week)
  ('f0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002', '2026-06-01', '2026-06-07', 'LOCKED',
   timestamptz '2026-06-01 13:00:00+00', timestamptz '2026-06-01 14:00:00+00', null, null,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R2 Marco — LOCKED (current week; FAILED sync, overdue review)
  ('f0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000003', '2026-06-01', '2026-06-07', 'LOCKED',
   timestamptz '2026-06-01 13:00:00+00', timestamptz '2026-06-01 14:00:00+00', null, null,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R3 Aisha — LOCKED (current week; one OPEN dispute → review REVIEWED_WITH_DISPUTES)
  ('f0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000004', '2026-06-01', '2026-06-07', 'LOCKED',
   timestamptz '2026-06-01 13:00:00+00', timestamptz '2026-06-01 14:00:00+00', null, null,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R4 Tomas — LOCKED (current week; one RESOLVED dispute → review REVIEWED)
  ('f0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000005', '2026-06-01', '2026-06-07', 'LOCKED',
   timestamptz '2026-06-01 13:00:00+00', timestamptz '2026-06-01 14:00:00+00', null, null,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R5 Grace — RECONCILING (current week; carry-forward target + mixed outcomes)
  ('f0000000-0000-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000006', '2026-06-01', '2026-06-07', 'RECONCILING',
   timestamptz '2026-06-01 13:00:00+00', timestamptz '2026-06-01 14:00:00+00', timestamptz '2026-06-02 13:00:00+00', null,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R5 Grace — RECONCILED (prior week; the carry-forward SOURCE plan)
  ('f0000000-0000-0000-0000-000000000006', 'd0000000-0000-0000-0000-000000000006', '2026-05-25', '2026-05-31', 'RECONCILED',
   timestamptz '2026-05-25 13:00:00+00', timestamptz '2026-05-25 14:00:00+00', timestamptz '2026-05-29 14:00:00+00', timestamptz '2026-05-31 18:00:00+00',
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R6 Sam — DRAFT (current week; one unlinked planned commitment — the can't-lock fixture)
  ('f0000000-0000-0000-0000-000000000007', 'd0000000-0000-0000-0000-000000000007', '2026-06-01', '2026-06-07', 'DRAFT',
   timestamptz '2026-06-01 13:00:00+00', null, null, null,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- Dana — RECONCILED (prior week; her own plan as IC, unmanaged → no review)
  ('f0000000-0000-0000-0000-000000000008', 'd0000000-0000-0000-0000-000000000001', '2026-05-25', '2026-05-31', 'RECONCILED',
   timestamptz '2026-05-25 13:00:00+00', timestamptz '2026-05-25 14:00:00+00', timestamptz '2026-05-29 14:00:00+00', timestamptz '2026-05-31 18:00:00+00',
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00')
on conflict (id) do nothing;

-- 2. Commitments. Parent-first: the carry-forward SOURCE (10…20, R5 prior) is listed BEFORE the
--    commitment that references it (10…0a, R5 current) so the self-FK is satisfied row-by-row.
insert into weekly_commitment
  (id, weekly_plan_id, commitment_kind, title, description, supporting_outcome_id,
   priority, work_type, confidence, alignment_status, reconciliation_outcome, outcome_note,
   carry_forward_source_commitment_id, created_by, created_at, updated_by, updated_at)
values
  -- R1 — 3 planned, all linked
  ('10000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', 'PLANNED', 'Ship the activation funnel A/B test', null, 'c0000000-0000-0000-0000-000000000001',
   'P1', 'STRATEGIC', 'HIGH', 'ALIGNED', null, null, null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('10000000-0000-0000-0000-000000000002', 'f0000000-0000-0000-0000-000000000001', 'PLANNED', 'Cut onboarding drop-off on step 3', null, 'c0000000-0000-0000-0000-000000000006',
   'P2', 'MAINTENANCE', 'MEDIUM', 'ALIGNED', null, null, null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('10000000-0000-0000-0000-000000000003', 'f0000000-0000-0000-0000-000000000001', 'PLANNED', 'Instrument retention cohort dashboard', null, 'c0000000-0000-0000-0000-000000000008',
   'P1', 'STRATEGIC', 'HIGH', 'ALIGNED', null, null, null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R2 — 2 planned, all linked
  ('10000000-0000-0000-0000-000000000004', 'f0000000-0000-0000-0000-000000000002', 'PLANNED', 'Harden the import pipeline retries', null, 'c0000000-0000-0000-0000-000000000003',
   'P1', 'STRATEGIC', 'HIGH', 'ALIGNED', null, null, null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('10000000-0000-0000-0000-000000000005', 'f0000000-0000-0000-0000-000000000002', 'PLANNED', 'Triage the support backlog SLA', null, 'c0000000-0000-0000-0000-000000000004',
   'P2', 'MAINTENANCE', 'MEDIUM', 'ALIGNED', null, null, null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R3 — 2 planned; 10…07 is NEEDS_REVIEW and carries the OPEN MISALIGNED dispute
  ('10000000-0000-0000-0000-000000000006', 'f0000000-0000-0000-0000-000000000003', 'PLANNED', 'Migrate auth to the new identity provider', null, 'c0000000-0000-0000-0000-000000000001',
   'P1', 'STRATEGIC', 'HIGH', 'ALIGNED', null, null, null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('10000000-0000-0000-0000-000000000007', 'f0000000-0000-0000-0000-000000000003', 'PLANNED', 'Rework the billing reconciliation report', null, 'c0000000-0000-0000-0000-000000000009',
   'P2', 'STRATEGIC', 'MEDIUM', 'NEEDS_REVIEW', null, null, null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R4 — 2 planned; 10…09 carried a dispute that was RESOLVED (alignment back to ALIGNED)
  ('10000000-0000-0000-0000-000000000008', 'f0000000-0000-0000-0000-000000000004', 'PLANNED', 'Lift API availability to four nines', null, 'c0000000-0000-0000-0000-000000000006',
   'P1', 'STRATEGIC', 'HIGH', 'ALIGNED', null, null, null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('10000000-0000-0000-0000-000000000009', 'f0000000-0000-0000-0000-000000000004', 'PLANNED', 'Close the SEV-2 security findings', null, 'c0000000-0000-0000-0000-000000000007',
   'P2', 'MAINTENANCE', 'MEDIUM', 'ALIGNED', null, null, null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R6 — DRAFT: 10…0e deliberately UNLINKED (NULL SO), 10…0f linked
  ('10000000-0000-0000-0000-00000000000e', 'f0000000-0000-0000-0000-000000000007', 'PLANNED', 'Explore a weekly-digest email', null, null,
   'P1', 'STRATEGIC', 'HIGH', 'NEEDS_REVIEW', null, null, null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('10000000-0000-0000-0000-00000000000f', 'f0000000-0000-0000-0000-000000000007', 'PLANNED', 'Draft the Q3 reliability roadmap', null, 'c0000000-0000-0000-0000-000000000001',
   'P2', 'MAINTENANCE', 'MEDIUM', 'ALIGNED', null, null, null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- Dana — RECONCILED, all outcomes set
  ('10000000-0000-0000-0000-000000000011', 'f0000000-0000-0000-0000-000000000008', 'PLANNED', 'Run the leadership weekly-commit rollout', null, 'c0000000-0000-0000-0000-000000000001',
   'P1', 'STRATEGIC', 'HIGH', 'ALIGNED', 'COMPLETED', 'Rolled out to all squads.', null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('10000000-0000-0000-0000-000000000012', 'f0000000-0000-0000-0000-000000000008', 'PLANNED', 'Close the quarterly alignment review', null, 'c0000000-0000-0000-0000-000000000006',
   'P2', 'MAINTENANCE', 'MEDIUM', 'ALIGNED', 'COMPLETED', 'All reports reconciled.', null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R5 PRIOR (RECONCILED): the carry-forward SOURCE (10…20, CARRIED_FORWARD, SO-1.2) + one completed
  ('10000000-0000-0000-0000-000000000020', 'f0000000-0000-0000-0000-000000000006', 'PLANNED', 'Draft the activation-onboarding runbook', null, 'c0000000-0000-0000-0000-000000000002',
   'P1', 'STRATEGIC', 'HIGH', 'ALIGNED', 'CARRIED_FORWARD', 'Not finished; carried into the next week.', null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('10000000-0000-0000-0000-000000000021', 'f0000000-0000-0000-0000-000000000006', 'PLANNED', 'Publish the onboarding metrics baseline', null, 'c0000000-0000-0000-0000-000000000001',
   'P2', 'MAINTENANCE', 'MEDIUM', 'ALIGNED', 'COMPLETED', 'Baseline shipped.', null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R5 CURRENT (RECONCILING): C_next (10…0a) carries forward 10…20; + COMPLETED + BLOCKED + UNPLANNED
  ('10000000-0000-0000-0000-00000000000a', 'f0000000-0000-0000-0000-000000000005', 'PLANNED', 'Finish the activation-onboarding runbook', null, 'c0000000-0000-0000-0000-000000000002',
   'P1', 'STRATEGIC', 'HIGH', 'ALIGNED', null, null, '10000000-0000-0000-0000-000000000020', 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('10000000-0000-0000-0000-00000000000b', 'f0000000-0000-0000-0000-000000000005', 'PLANNED', 'Land the support first-response SLA', null, 'c0000000-0000-0000-0000-000000000004',
   'P2', 'MAINTENANCE', 'MEDIUM', 'ALIGNED', 'COMPLETED', 'Hit under 2 hours.', null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('10000000-0000-0000-0000-00000000000c', 'f0000000-0000-0000-0000-000000000005', 'PLANNED', 'Stabilize the release train', null, 'c0000000-0000-0000-0000-000000000007',
   'P1', 'STRATEGIC', 'HIGH', 'ALIGNED', 'BLOCKED', 'Blocked on an upstream infra dependency.', null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('10000000-0000-0000-0000-00000000000d', 'f0000000-0000-0000-0000-000000000005', 'UNPLANNED', 'Hotfix the time-to-first-plan regression', null, 'c0000000-0000-0000-0000-000000000005',
   'P2', 'UNPLANNED', 'MEDIUM', 'ALIGNED', 'PARTIALLY_COMPLETED', 'Mitigated; full fix pending.', null, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00')
on conflict (id) do nothing;

-- 3. Manager reviews (Dana is the manager for R1-R5). STORED status (rule #6: never OVERDUE).
--    R1/R2 NOT_REVIEWED; R3 REVIEWED_WITH_DISPUTES (open dispute); R4/R5 REVIEWED.
insert into manager_review
  (id, weekly_plan_id, manager_employee_id, status, review_due_at, reviewed_at, summary_note,
   created_by, created_at, updated_by, updated_at)
values
  -- R1 — NOT_REVIEWED, due 2026-06-02 17:00 CT → NOT overdue at the 2026-06-02 morning anchor
  ('12000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', 'NOT_REVIEWED', timestamptz '2026-06-02 22:00:00+00', null, null,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R2 — NOT_REVIEWED, due 2026-05-29 17:00 CT (past) → derives OVERDUE at the anchor
  ('12000000-0000-0000-0000-000000000002', 'f0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000001', 'NOT_REVIEWED', timestamptz '2026-05-29 22:00:00+00', null, null,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R3 — REVIEWED_WITH_DISPUTES (the stored status the deriver writes given the open dispute)
  ('12000000-0000-0000-0000-000000000003', 'f0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000001', 'REVIEWED_WITH_DISPUTES', timestamptz '2026-06-02 22:00:00+00', timestamptz '2026-06-01 20:00:00+00', null,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R4 — REVIEWED (its dispute was resolved → 0 unresolved → REVIEWED)
  ('12000000-0000-0000-0000-000000000004', 'f0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000001', 'REVIEWED', timestamptz '2026-06-02 22:00:00+00', timestamptz '2026-06-01 20:00:00+00', null,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R5 — REVIEWED (no disputes)
  ('12000000-0000-0000-0000-000000000005', 'f0000000-0000-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000001', 'REVIEWED', timestamptz '2026-06-02 22:00:00+00', timestamptz '2026-06-01 20:00:00+00', null,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00')
on conflict (id) do nothing;

-- 4. Disputes (opened by Dana the manager). R3 one OPEN MISALIGNED; R4 one full-lifecycle RESOLVED.
insert into alignment_dispute
  (id, commitment_id, manager_employee_id, status, flag_type, manager_note, ic_response, resolved_at,
   created_by, created_at, updated_by, updated_at)
values
  -- R3 — OPEN, MISALIGNED, no IC response yet (on the NEEDS_REVIEW commitment 10…07)
  ('13000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000007', 'd0000000-0000-0000-0000-000000000001', 'OPEN', 'MISALIGNED', 'This looks misaligned with the billing outcome — can you re-link?', null, null,
   'system-seed', timestamptz '2026-06-01 19:00:00+00', 'system-seed', timestamptz '2026-06-01 19:00:00+00'),
  -- R4 — RESOLVED (OPEN → IC_RESPONDED → RESOLVED): ic_response + resolved_at populated
  ('13000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000009', 'd0000000-0000-0000-0000-000000000001', 'RESOLVED', 'NEEDS_REVISION', 'Please tighten the scope to the SEV-2 set.', 'Scoped down and re-linked — thanks.', timestamptz '2026-06-01 21:00:00+00',
   'system-seed', timestamptz '2026-06-01 19:00:00+00', 'system-seed', timestamptz '2026-06-01 21:00:00+00')
on conflict (id) do nothing;

-- 5. Outlook sync records: R2 FAILED IC_PLANNING + R1 SYNCED IC_PLANNING + Dana MANAGER_REVIEW_BLOCK.
insert into outlook_calendar_sync_record
  (id, owner_employee_id, related_type, related_id, event_kind, status,
   graph_event_id, failure_code, safe_message, retry_count, week_start_date,
   created_by, created_at, updated_by, updated_at)
values
  -- R2 — FAILED (REQ-E-004): safe_message only, NO token/secret detail (rule #7), NULL graph_event_id
  ('14000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000003', 'WEEKLY_PLAN', 'f0000000-0000-0000-0000-000000000002', 'IC_PLANNING', 'FAILED',
   null, 'GRAPH_FORBIDDEN', 'Calendar sync failed; you can retry.', 1, '2026-06-01',
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- R1 — SYNCED (success contrast)
  ('14000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000002', 'WEEKLY_PLAN', 'f0000000-0000-0000-0000-000000000001', 'IC_PLANNING', 'SYNCED',
   'demo-graph-event-r1-planning', null, null, 0, '2026-06-01',
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  -- Dana — MANAGER_REVIEW_BLOCK keyed (owner=Dana, week=2026-06-01) via the V2 partial-unique
  ('14000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000001', 'MANAGER_REVIEW_WEEK', 'd0000000-0000-0000-0000-000000000001', 'MANAGER_REVIEW_BLOCK', 'SYNCED',
   'demo-graph-event-dana-review-block', null, null, 0, '2026-06-01',
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00')
on conflict (id) do nothing;
