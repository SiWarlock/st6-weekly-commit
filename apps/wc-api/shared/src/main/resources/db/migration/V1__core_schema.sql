-- V1 — core schema (task 1.2). The physical encoding of the 12 Appendix-A core models (§4).
-- Conventions (§4): UUID PKs; VARCHAR + CHECK status columns mirroring the 0.3 enum vocabulary
-- EXACTLY (REQ-D-010, not native enums); AbstractAuditingEntity columns on the 11 domain tables;
-- optimistic-lock `version` (JPA @Version, bigint to match PersistableUuidEntity.version : Long) on
-- the 5 mutable lifecycle tables. Partial unique indexes (V2/1.3) + projection tables (V3/1.4) +
-- seed (V4-V6) are out of scope. Authored against §4 + the 4 contract deltas + the 0.3 enums;
-- docs/planning/DATA_MODEL.md is the stale pre-delta draft (superseded — see orchestrator routing).

-- 1. employee ---------------------------------------------------------------
create table employee (
    id uuid primary key,
    external_subject varchar(255),
    email varchar(255) not null unique,
    display_name varchar(255) not null,
    role varchar(50) not null check (role in ('IC', 'MANAGER')),
    active boolean not null default true,
    timezone varchar(100),
    created_by varchar(255),
    created_at timestamptz,
    updated_by varchar(255),
    updated_at timestamptz
);
create index idx_employee_external_subject on employee (external_subject);
create index idx_employee_active on employee (active);

-- 2. manager_relationship ---------------------------------------------------
create table manager_relationship (
    id uuid primary key,
    manager_employee_id uuid not null references employee (id),
    direct_report_employee_id uuid not null references employee (id),
    active boolean not null default true,
    created_by varchar(255),
    created_at timestamptz,
    updated_by varchar(255),
    updated_at timestamptz,
    constraint uq_manager_relationship unique (manager_employee_id, direct_report_employee_id),
    constraint ck_manager_not_self check (manager_employee_id <> direct_report_employee_id)
);
create index idx_mgr_rel_manager_active on manager_relationship (manager_employee_id, active);
create index idx_mgr_rel_report_active on manager_relationship (direct_report_employee_id, active);

-- 3. rally_cry --------------------------------------------------------------
create table rally_cry (
    id uuid primary key,
    title varchar(255) not null,
    description text,
    active boolean not null default true,
    created_by varchar(255),
    created_at timestamptz,
    updated_by varchar(255),
    updated_at timestamptz
);

-- 4. defining_objective -----------------------------------------------------
create table defining_objective (
    id uuid primary key,
    rally_cry_id uuid not null references rally_cry (id),
    title varchar(255) not null,
    description text,
    active boolean not null default true,
    created_by varchar(255),
    created_at timestamptz,
    updated_by varchar(255),
    updated_at timestamptz
);
create index idx_do_rally_active on defining_objective (rally_cry_id, active);

-- 5. supporting_outcome -----------------------------------------------------
create table supporting_outcome (
    id uuid primary key,
    defining_objective_id uuid not null references defining_objective (id),
    title varchar(255) not null,
    description text,
    active boolean not null default true,
    created_by varchar(255),
    created_at timestamptz,
    updated_by varchar(255),
    updated_at timestamptz
);
create index idx_so_do_active on supporting_outcome (defining_objective_id, active);

-- 6. weekly_plan (mutable lifecycle — @Version) -----------------------------
create table weekly_plan (
    id uuid primary key,
    employee_id uuid not null references employee (id),
    week_start_date date not null,
    week_end_date date not null,
    state varchar(32) not null check (state in ('DRAFT', 'LOCKED', 'RECONCILING', 'RECONCILED')),
    generated_at timestamptz,
    locked_at timestamptz,
    reconciliation_started_at timestamptz,
    reconciled_at timestamptz,
    version bigint not null default 0,
    created_by varchar(255),
    created_at timestamptz,
    updated_by varchar(255),
    updated_at timestamptz,
    constraint uq_weekly_plan_employee_week unique (employee_id, week_start_date)
);
create index idx_plan_employee_state on weekly_plan (employee_id, state);
create index idx_plan_week_state on weekly_plan (week_start_date, state);

-- 7. weekly_commitment (mutable lifecycle — @Version) -----------------------
-- DELTA 1: manager_alignment_note present. DELTA 2: progress_status ABSENT.
create table weekly_commitment (
    id uuid primary key,
    weekly_plan_id uuid not null references weekly_plan (id),
    commitment_kind varchar(32) not null check (commitment_kind in ('PLANNED', 'UNPLANNED')),
    title varchar(255) not null,
    description text,
    supporting_outcome_id uuid references supporting_outcome (id),
    priority varchar(16) not null check (priority in ('P0', 'P1', 'P2')),
    work_type varchar(32) not null check (work_type in ('STRATEGIC', 'MAINTENANCE', 'BLOCKER', 'UNPLANNED')),
    confidence varchar(32) not null check (confidence in ('HIGH', 'MEDIUM', 'LOW')),
    alignment_status varchar(32) not null check (alignment_status in ('ALIGNED', 'NEEDS_REVIEW', 'MISALIGNED')),
    manager_alignment_note text,
    reconciliation_outcome varchar(32) check (reconciliation_outcome in ('COMPLETED', 'PARTIALLY_COMPLETED', 'BLOCKED', 'CANCELED', 'CARRIED_FORWARD')),
    outcome_note text,
    carry_forward_source_commitment_id uuid references weekly_commitment (id),
    version bigint not null default 0,
    created_by varchar(255),
    created_at timestamptz,
    updated_by varchar(255),
    updated_at timestamptz
);
create index idx_commitment_plan_kind on weekly_commitment (weekly_plan_id, commitment_kind);
create index idx_commitment_so on weekly_commitment (supporting_outcome_id);
create index idx_commitment_carry_source on weekly_commitment (carry_forward_source_commitment_id);
create index idx_commitment_alignment on weekly_commitment (alignment_status);
create index idx_commitment_priority on weekly_commitment (priority);
create index idx_commitment_work_type on weekly_commitment (work_type);

-- 8. manager_review (mutable lifecycle — @Version) --------------------------
-- status: no stored OVERDUE (derived at read time, §3).
create table manager_review (
    id uuid primary key,
    weekly_plan_id uuid not null unique references weekly_plan (id),
    manager_employee_id uuid not null references employee (id),
    status varchar(32) not null check (status in ('NOT_REVIEWED', 'REVIEWED_WITH_DISPUTES', 'REVIEWED')),
    review_due_at timestamptz not null,
    reviewed_at timestamptz,
    summary_note text,
    version bigint not null default 0,
    created_by varchar(255),
    created_at timestamptz,
    updated_by varchar(255),
    updated_at timestamptz
);
create index idx_review_mgr_status_due on manager_review (manager_employee_id, status, review_due_at);
create index idx_review_due on manager_review (review_due_at);

-- 9. alignment_dispute (mutable lifecycle — @Version) -----------------------
-- Partial unique (one unresolved dispute / commitment) is V2/1.3.
create table alignment_dispute (
    id uuid primary key,
    commitment_id uuid not null references weekly_commitment (id),
    manager_employee_id uuid not null references employee (id),
    status varchar(32) not null check (status in ('OPEN', 'IC_RESPONDED', 'RESOLVED')),
    flag_type varchar(32) not null check (flag_type in ('NEEDS_REVISION', 'MISALIGNED')),
    manager_note text not null,
    ic_response text,
    resolved_at timestamptz,
    version bigint not null default 0,
    created_by varchar(255),
    created_at timestamptz,
    updated_by varchar(255),
    updated_at timestamptz
);
create index idx_dispute_commitment_status on alignment_dispute (commitment_id, status);
create index idx_dispute_mgr_status on alignment_dispute (manager_employee_id, status);

-- 10. comment ---------------------------------------------------------------
-- DELTA 3: target_type {PLAN,COMMITMENT} only; flat-but-nestable (parent/path nullable, depth 0).
create table comment (
    id uuid primary key,
    target_type varchar(32) not null check (target_type in ('PLAN', 'COMMITMENT')),
    target_id uuid not null,
    author_employee_id uuid not null references employee (id),
    parent_comment_id uuid references comment (id),
    path varchar(2048),
    depth integer not null default 0,
    body text not null,
    created_by varchar(255),
    created_at timestamptz,
    updated_by varchar(255),
    updated_at timestamptz
);
create index idx_comment_target on comment (target_type, target_id, path);
create index idx_comment_parent on comment (parent_comment_id);
create index idx_comment_author on comment (author_employee_id);

-- 11. outlook_calendar_sync_record (durable outbox — @Version) --------------
-- DELTA 4: week_start_date present; related_type uses MANAGER_REVIEW_WEEK (not MANAGER_REVIEW).
-- Q3: full unique in V1; the per-manager/week partial unique is V2/1.3.
create table outlook_calendar_sync_record (
    id uuid primary key,
    owner_employee_id uuid not null references employee (id),
    related_type varchar(32) not null check (related_type in ('WEEKLY_PLAN', 'MANAGER_REVIEW_WEEK')),
    related_id uuid not null,
    event_kind varchar(32) not null check (event_kind in ('IC_PLANNING', 'IC_RECONCILIATION', 'MANAGER_REVIEW_BLOCK')),
    status varchar(32) not null check (status in ('PENDING_PUBLISH', 'QUEUED', 'SYNCING', 'SYNCED', 'FAILED', 'RETRY_REQUESTED')),
    graph_event_id varchar(255),
    failure_code varchar(100),
    safe_message text,
    last_attempt_at timestamptz,
    queued_at timestamptz,
    processed_at timestamptz,
    retry_count integer not null default 0,
    trace_id varchar(100),
    week_start_date date,
    version bigint not null default 0,
    created_by varchar(255),
    created_at timestamptz,
    updated_by varchar(255),
    updated_at timestamptz,
    constraint uq_sync_owner_related_kind unique (owner_employee_id, related_type, related_id, event_kind)
);
create index idx_sync_status_queued on outlook_calendar_sync_record (status, queued_at);
create index idx_sync_owner_kind on outlook_calendar_sync_record (owner_employee_id, event_kind);

-- 12. audit_event (append-only — own created_at, NOT an AbstractAuditingEntity) -
create table audit_event (
    id uuid primary key,
    actor_employee_id uuid references employee (id),
    action varchar(100) not null,
    entity_type varchar(64) not null,
    entity_id uuid,
    summary text not null,
    metadata_json jsonb,
    created_at timestamptz not null
);
create index idx_audit_entity on audit_event (entity_type, entity_id);
create index idx_audit_actor_created on audit_event (actor_employee_id, created_at);
create index idx_audit_action_created on audit_event (action, created_at);
