-- V5 — demo persona + manager-relationship seed (task 10.2; ARCHITECTURE.md Appendix E Part 2,
-- §4/§6/§16, REQ-E-002).
--
-- The deterministic demo dataset: 1 manager (Dana Okafor) + 6 IC direct reports + six
-- manager_relationship rows (Dana manages each report, active). Dana also owns her own plan as an
-- IC but is herself unmanaged — exercising the "manager who also owns a plan" path (§4).
--
-- ⚠️ OAuth identity mapping (deployed demo, lead-ratified — differs from the original task-10.2
-- spec which left external_subject NULL): the deployed demo uses real Auth0/OAuth2 (not the
-- X-Demo-Employee-Id header), so a validated JWT resolves to a seeded employee via
-- PrincipalResolver.resolve(Auth0Identity) -> EmployeeRepository.findByExternalSubject(...). Each
-- employee therefore carries a deterministic, app-owned external_subject literal (st6|<first>-<last>)
-- which the Auth0 tenant emits as the configured employee_id claim per test user (an Auth0
-- post-login Action maps each user -> app_metadata.employee_id = the seeded external_subject). The
-- literals are distinct across the 7 employees so findByExternalSubject is 1:1 (the resolver assumes
-- <=1 match; seed distinctness is the guarantee for MVP — a unique index is a deferred follow-up).
--
-- Lives in the dedicated classpath:db/demo-seed location (NOT db/migration) so the deploy migration
-- Job seeds the demo personas while the shared integration-test harness — which re-enables Flyway on
-- the default db/migration only — never does (the demo seed is a runtime/demo prerequisite, not a
-- test-data dependency; tests seed their own employees).
--
-- Deterministic + idempotent (§13): fixed UUID literals (continuing V4's per-entity prefix
-- convention a=RC, b=DO, c=SO -> d=employee, e=relationship). Fixed timestamps anchored to the demo
-- anchor date 2026-06-02. `ON CONFLICT (id) DO NOTHING` -> re-running on a populated DB is a no-op.
-- Inserted parent-first (employees -> relationships) to satisfy the V1 FKs.

-- 1. Employees (Dana the manager, then the 6 IC reports — Appendix E Part 2) ------------------
insert into employee
  (id, external_subject, email, display_name, role, active, timezone,
   created_by, created_at, updated_by, updated_at)
values
  ('d0000000-0000-0000-0000-000000000001', 'st6|dana-okafor',  'dana.okafor@dreddy817.onmicrosoft.com',  'Dana Okafor',  'MANAGER', true, 'America/Chicago',
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('d0000000-0000-0000-0000-000000000002', 'st6|priya-raman',  'priya.raman@dreddy817.onmicrosoft.com',  'Priya Raman',  'IC', true, 'America/Chicago',
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('d0000000-0000-0000-0000-000000000003', 'st6|marco-bellini', 'marco.bellini@dreddy817.onmicrosoft.com', 'Marco Bellini', 'IC', true, 'America/Chicago',
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('d0000000-0000-0000-0000-000000000004', 'st6|aisha-khan',   'aisha.khan@dreddy817.onmicrosoft.com',   'Aisha Khan',   'IC', true, 'America/Chicago',
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('d0000000-0000-0000-0000-000000000005', 'st6|tomas-novak',  'tomas.novak@dreddy817.onmicrosoft.com',  'Tomas Novak',  'IC', true, 'America/Chicago',
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('d0000000-0000-0000-0000-000000000006', 'st6|grace-liu',    'grace.liu@dreddy817.onmicrosoft.com',    'Grace Liu',    'IC', true, 'America/Chicago',
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('d0000000-0000-0000-0000-000000000007', 'st6|sam-carter',   'sam.carter@dreddy817.onmicrosoft.com',   'Sam Carter',   'IC', true, 'America/Chicago',
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00')
on conflict (id) do nothing;

-- 2. Manager relationships (Dana -> each report, active) -------------------------------------
--    Each satisfies unique(manager, report) + the V2 partial-unique uq_active_manager_per_report
--    (single active manager per report). No row makes Dana a direct report (she is unmanaged).
insert into manager_relationship
  (id, manager_employee_id, direct_report_employee_id, active,
   created_by, created_at, updated_by, updated_at)
values
  ('e0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002', true,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('e0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000003', true,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('e0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000004', true,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('e0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000005', true,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('e0000000-0000-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000006', true,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00'),
  ('e0000000-0000-0000-0000-000000000006', 'd0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000007', true,
   'system-seed', timestamptz '2026-06-02 00:00:00+00', 'system-seed', timestamptz '2026-06-02 00:00:00+00')
on conflict (id) do nothing;
