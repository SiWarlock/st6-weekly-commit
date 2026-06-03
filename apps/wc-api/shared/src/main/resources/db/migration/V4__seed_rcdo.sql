-- V4 — RCDO strategy hierarchy seed (task 3.1; ARCHITECTURE.md Appendix E Part 2, REQ-D-004).
--
-- Read-only reference data: 1 Rally Cry / 3 Defining Objectives / 9 Supporting Outcomes, the
-- execution-SaaS narrative copied verbatim from Appendix E Part 2. No admin UI ever mutates these
-- (REQ-D-003) — they are the link targets every locked planned commitment must reference (rule #1).
--
-- Deterministic + idempotent (§13): fixed UUID literals assigned SEQUENTIALLY in logical order
-- (RC ...0001; DO-1/2/3 ...0001/0002/0003; SO-1.1..SO-3.3 ...0001..0009) so the 3.1 read's
-- `ORDER BY id` reproduces the logical strategy order on the wire with zero schema change. Fixed
-- timestamps anchored to the demo anchor date 2026-06-02. `ON CONFLICT (id) DO NOTHING` → re-running
-- on a populated DB is a no-op. Inserted parent-first (RC → DO → SO) to satisfy the V1 FKs.
--
-- The persona/employee/relationship seed (V5) and fixture plans/state (V6) are separate migrations
-- (a later seed slice) — this file is RCDO only.

-- 1. Rally Cry --------------------------------------------------------------
insert into rally_cry (id, title, description, active, created_by, created_at, updated_by, updated_at)
values
  ('a0000000-0000-0000-0000-000000000001',
   'Become the system of record every execution-driven team trusts by end of FY26.',
   null, true, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00')
on conflict (id) do nothing;

-- 2. Defining Objectives (logical order: DO-1, DO-2, DO-3) -------------------
insert into defining_objective (id, rally_cry_id, title, description, active, created_by, created_at, updated_by, updated_at)
values
  ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001',
   'Win customer adoption & expansion',
   null, true, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001',
   'Operational excellence in delivery',
   null, true, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000001',
   'Platform reliability & trust',
   null, true, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00')
on conflict (id) do nothing;

-- 3. Supporting Outcomes (SO-1.1..SO-1.3, SO-2.1..SO-2.3, SO-3.1..SO-3.3) ----
insert into supporting_outcome (id, defining_objective_id, title, description, active, created_by, created_at, updated_by, updated_at)
values
  ('c0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001',
   'Lift activated-team weekly-active rate to 70%',
   null, true, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('c0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001',
   'Cut new-workspace time-to-first-locked-plan under 10 minutes',
   null, true, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('c0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000001',
   'Reach 120% net revenue retention on strategic accounts',
   null, true, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('c0000000-0000-0000-0000-000000000004', 'b0000000-0000-0000-0000-000000000002',
   'Ship the weekly release train with zero rollback for 8 consecutive weeks',
   null, true, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('c0000000-0000-0000-0000-000000000005', 'b0000000-0000-0000-0000-000000000002',
   'Drive median support first-response under 2 business hours',
   null, true, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('c0000000-0000-0000-0000-000000000006', 'b0000000-0000-0000-0000-000000000002',
   'Bring every squad to a green weekly-commit reconciliation rate above 90%',
   null, true, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('c0000000-0000-0000-0000-000000000007', 'b0000000-0000-0000-0000-000000000003',
   'Sustain 99.9% API availability across all regions',
   null, true, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('c0000000-0000-0000-0000-000000000008', 'b0000000-0000-0000-0000-000000000003',
   'Keep p95 command-center latency under 200ms at 2,000-record scale',
   null, true, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('c0000000-0000-0000-0000-000000000009', 'b0000000-0000-0000-0000-000000000003',
   'Close 100% of SEV-1/2 security findings within SLA',
   null, true, 'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00')
on conflict (id) do nothing;
