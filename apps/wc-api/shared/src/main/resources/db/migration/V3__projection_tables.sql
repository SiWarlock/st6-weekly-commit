-- V3 — synchronous projection read-model tables (task 1.4). The 2 §9 manager read models, written
-- transactionally by the ProjectionService (a later phase) alongside the source mutations. These
-- are materialized read models (recomputed wholesale on rebuild) — NOT user-mutated lifecycle
-- entities — so they carry only `updated_at` (no AbstractAuditingEntity quartet, no @Version, like
-- audit_event). plan_state / review_status are denormalized projection mirrors (the source-of-truth
-- CHECKs live on weekly_plan.state / manager_review.status in V1), so no CHECK here.
-- NOTE: is_review_overdue is the §9 read-model column (Appendix A), NOT safety rule #6 — rule #6
-- governs manager_review.status (no OVERDUE, V1) + read-time service derivation, not this column.

-- 1. manager_plan_summary — one row per manager/report/week ------------------
create table manager_plan_summary (
    id uuid primary key,
    manager_employee_id uuid not null references employee (id),
    employee_id uuid not null references employee (id),
    weekly_plan_id uuid not null references weekly_plan (id),
    week_start_date date not null,
    plan_state varchar(32) not null,
    review_status varchar(32),
    review_due_at timestamptz,
    is_review_overdue boolean not null default false,
    planned_count integer not null default 0,
    unplanned_count integer not null default 0,
    misaligned_count integer not null default 0,
    needs_review_count integer not null default 0,
    blocked_count integer not null default 0,
    carry_forward_count integer not null default 0,
    unresolved_dispute_count integer not null default 0,
    updated_at timestamptz not null,
    constraint uq_manager_plan_summary unique (manager_employee_id, employee_id, week_start_date)
);
create index idx_mps_manager_week_state on manager_plan_summary (manager_employee_id, week_start_date, plan_state);
create index idx_mps_manager_review_status on manager_plan_summary (manager_employee_id, review_status);

-- 2. manager_heatmap_cell — manager x report x week x Defining Objective ------
create table manager_heatmap_cell (
    id uuid primary key,
    manager_employee_id uuid not null references employee (id),
    employee_id uuid not null references employee (id),
    week_start_date date not null,
    defining_objective_id uuid not null references defining_objective (id),
    commitment_count integer not null default 0,
    planned_count integer not null default 0,
    unplanned_count integer not null default 0,
    misaligned_count integer not null default 0,
    needs_review_count integer not null default 0,
    blocked_count integer not null default 0,
    carry_forward_count integer not null default 0,
    unresolved_dispute_count integer not null default 0,
    risk_badges text[] not null default '{}',
    updated_at timestamptz not null,
    constraint uq_manager_heatmap_cell unique (manager_employee_id, employee_id, week_start_date, defining_objective_id),
    -- risk_badges elements constrained to the RiskBadge vocabulary (Appendix B.1) via array containment
    constraint ck_risk_badges_vocab check (
        risk_badges <@ array['MISALIGNED', 'NEEDS_REVIEW', 'BLOCKED', 'CARRY_FORWARD', 'UNREVIEWED', 'OVERDUE_REVIEW']::text[]
    )
);
create index idx_mhc_manager_week on manager_heatmap_cell (manager_employee_id, week_start_date);
create index idx_mhc_manager_do on manager_heatmap_cell (manager_employee_id, defining_objective_id);
