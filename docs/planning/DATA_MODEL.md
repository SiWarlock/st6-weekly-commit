# Data Model

> Status: Draft planning artifact
> Phase: 5 / 13 support - Physical data model
> Date: 2026-06-02
> Sources: `DOMAIN_MODEL.md`, `REQUIREMENTS.md`, `DECISIONS.md`

> # ⚠️ SUPERSEDED — this is the stale pre-`/arch-finalize` draft. The BINDING physical contract is **`ARCHITECTURE.md` §4 + Appendix A + the four contract deltas + the 0.3 enum vocabulary**.
>
> Recorded during task **1.2** (2026-06-02) after `V1__core_schema.sql` was encoded against the binding source. **This document drifts from the contract on the following points — trust `ARCHITECTURE.md` §4, NOT this file:**
> 1. **`weekly_commitment.progress_status`** — REMOVED (this draft still lists it; redundant with `reconciliation_outcome` + `outcome_note`).
> 2. **`weekly_commitment.manager_alignment_note`** — ADDED (NEW, manager-owned, post-lock-mutable; absent from this draft).
> 3. **`comment.target_type`** — narrowed to **`{PLAN, COMMITMENT}` only** (this draft lists 4 targets incl. `MANAGER_REVIEW`/`ALIGNMENT_DISPUTE`); `path`/`parent_comment_id` are **nullable** (flat MVP, `depth default 0`), not `path NOT NULL`.
> 4. **`outlook_calendar_sync_record`** — uses **`MANAGER_REVIEW_WEEK`** (not `MANAGER_REVIEW`) and adds **`week_start_date date null`** (NEW); both absent/wrong here.
> 5. **`@Version` (`version bigint`)** optimistic-lock columns on `weekly_plan`, `weekly_commitment`, `manager_review`, `alignment_dispute`, `outlook_calendar_sync_record` (§22 locked decision; this draft lists none).
> 6. **PostgreSQL version** — "16.4" below is superseded by **"latest 16.x minor (≥16.13)"** (OQ-007; 16.4 is no longer creatable on RDS).
>
> The realized DDL is `apps/wc-api/shared/src/main/resources/db/migration/V1__core_schema.sql` (V2 partial-uniques + V3 projections + V4–V6 seed land in later slices). For any conflict, `ARCHITECTURE.md` §4 / Appendix A wins.

## Physical Model Defaults

- Database: Amazon RDS PostgreSQL 16.4.
- Migration tool: Flyway.
- Primary keys: UUID for externally referenced domain records.
- Status fields: `VARCHAR` with check constraints, not native Postgres enums.
- Audit metadata: entities extend `AbstractAuditingEntity`; sensitive actions also write `audit_event`.
- Planned and unplanned work share one `weekly_commitment` table.
- Manager dashboard uses synchronous projection tables: `manager_plan_summary` and `manager_heatmap_cell`.

Classification: locked decision.

## Tables

### `employee`

Purpose: seeded/demo employee identities and production identity mapping target.

Columns:

- `id uuid primary key`
- `external_subject varchar(255) null`
- `email varchar(255) not null unique`
- `display_name varchar(255) not null`
- `role varchar(50) not null check (role in ('IC','MANAGER'))`
- `active boolean not null default true`
- `timezone varchar(100) null`
- auditing columns from `AbstractAuditingEntity`

Indexes:

- unique index on `email`
- index on `external_subject`
- index on `active`

### `manager_relationship`

Purpose: direct-report scoping source for MVP authorization and dashboard reads.

Columns:

- `id uuid primary key`
- `manager_employee_id uuid not null references employee(id)`
- `direct_report_employee_id uuid not null references employee(id)`
- `active boolean not null default true`
- auditing columns

Constraints/indexes:

- unique `(manager_employee_id, direct_report_employee_id)`
- check manager and direct report are not the same employee
- index `(manager_employee_id, active)`
- index `(direct_report_employee_id, active)`

### `rally_cry`

Purpose: read-only seeded top-level strategy reference.

Columns:

- `id uuid primary key`
- `title varchar(255) not null`
- `description text null`
- `active boolean not null default true`
- auditing columns

### `defining_objective`

Purpose: read-only seeded strategy column used by heatmap.

Columns:

- `id uuid primary key`
- `rally_cry_id uuid not null references rally_cry(id)`
- `title varchar(255) not null`
- `description text null`
- `active boolean not null default true`
- auditing columns

Indexes:

- index `(rally_cry_id, active)`

### `supporting_outcome`

Purpose: required strategic target linked to commitments.

Columns:

- `id uuid primary key`
- `defining_objective_id uuid not null references defining_objective(id)`
- `title varchar(255) not null`
- `description text null`
- `active boolean not null default true`
- auditing columns

Indexes:

- index `(defining_objective_id, active)`

### `weekly_plan`

Purpose: lifecycle container for one employee/week.

Columns:

- `id uuid primary key`
- `employee_id uuid not null references employee(id)`
- `week_start_date date not null`
- `week_end_date date not null`
- `state varchar(32) not null check (state in ('DRAFT','LOCKED','RECONCILING','RECONCILED'))`
- `generated_at timestamptz null`
- `locked_at timestamptz null`
- `reconciliation_started_at timestamptz null`
- `reconciled_at timestamptz null`
- auditing columns

Constraints/indexes:

- unique `(employee_id, week_start_date)`
- index `(week_start_date, state)`
- index `(employee_id, state)`

### `weekly_commitment`

Purpose: atomic work/alignment/reconciliation unit.

Columns:

- `id uuid primary key`
- `weekly_plan_id uuid not null references weekly_plan(id)`
- `commitment_kind varchar(32) not null check (commitment_kind in ('PLANNED','UNPLANNED'))`
- `title varchar(255) not null`
- `description text null`
- `supporting_outcome_id uuid null references supporting_outcome(id)`
- `priority varchar(16) not null check (priority in ('P0','P1','P2'))`
- `work_type varchar(32) not null check (work_type in ('STRATEGIC','MAINTENANCE','BLOCKER','UNPLANNED'))`
- `confidence varchar(32) not null check (confidence in ('HIGH','MEDIUM','LOW'))`
- `alignment_status varchar(32) not null check (alignment_status in ('ALIGNED','NEEDS_REVIEW','MISALIGNED'))`
- `progress_status varchar(32) null`
- `reconciliation_outcome varchar(32) null check (reconciliation_outcome in ('COMPLETED','PARTIALLY_COMPLETED','BLOCKED','CANCELED','CARRIED_FORWARD'))`
- `outcome_note text null`
- `carry_forward_source_commitment_id uuid null references weekly_commitment(id)`
- auditing columns

Constraints/indexes:

- check unplanned commitments use `work_type = 'UNPLANNED'`
- planned commitments require `supporting_outcome_id` before lock through service validation
- unplanned commitments require `supporting_outcome_id` before reconciliation close through service validation
- index `(weekly_plan_id, commitment_kind)`
- index `(supporting_outcome_id)`
- index `(carry_forward_source_commitment_id)`
- index `(alignment_status)`

Baseline rule:

- Planned baseline fields are immutable after parent plan lock: title, description, supporting outcome, priority, work type, confidence, commitment kind, plan/week ownership.

### `manager_review`

Purpose: manager review state and SLA record for a locked plan.

Columns:

- `id uuid primary key`
- `weekly_plan_id uuid not null unique references weekly_plan(id)`
- `manager_employee_id uuid not null references employee(id)`
- `status varchar(32) not null check (status in ('NOT_REVIEWED','REVIEWED_WITH_DISPUTES','REVIEWED'))`
- `review_due_at timestamptz not null`
- `reviewed_at timestamptz null`
- `summary_note text null`
- auditing columns

Indexes:

- index `(manager_employee_id, status, review_due_at)`
- index `(review_due_at)`

Derived field:

- `isOverdue = now > review_due_at and status = 'NOT_REVIEWED'`

### `alignment_dispute`

Purpose: structured manager challenge on a commitment's alignment.

Columns:

- `id uuid primary key`
- `commitment_id uuid not null references weekly_commitment(id)`
- `manager_employee_id uuid not null references employee(id)`
- `status varchar(32) not null check (status in ('OPEN','IC_RESPONDED','RESOLVED'))`
- `flag_type varchar(32) not null check (flag_type in ('NEEDS_REVISION','MISALIGNED'))`
- `manager_note text not null`
- `ic_response text null`
- `resolved_at timestamptz null`
- auditing columns

Constraints/indexes:

- partial unique index allowing at most one unresolved dispute per commitment where status in `OPEN`,`IC_RESPONDED`
- index `(commitment_id, status)`
- index `(manager_employee_id, status)`

### `comment`

Purpose: full nested plan/commitment/review/dispute discussion.

Columns:

- `id uuid primary key`
- `target_type varchar(32) not null check (target_type in ('PLAN','COMMITMENT','MANAGER_REVIEW','ALIGNMENT_DISPUTE'))`
- `target_id uuid not null`
- `author_employee_id uuid not null references employee(id)`
- `parent_comment_id uuid null references comment(id)`
- `path varchar(2048) not null`
- `depth integer not null default 0`
- `body text not null`
- auditing columns

Indexes:

- index `(target_type, target_id, path)`
- index `(parent_comment_id)`
- index `(author_employee_id)`

Rules:

- Materialized path stores ordered ancestry for full nested retrieval.
- Authorization follows the target resource's visibility rules.

### `outlook_calendar_sync_record`

Purpose: durable outbox and status record for Graph calendar sync.

Columns:

- `id uuid primary key`
- `owner_employee_id uuid not null references employee(id)`
- `related_type varchar(32) not null check (related_type in ('WEEKLY_PLAN','MANAGER_REVIEW'))`
- `related_id uuid not null`
- `event_kind varchar(32) not null check (event_kind in ('IC_PLANNING','IC_RECONCILIATION','MANAGER_REVIEW_BLOCK'))`
- `status varchar(32) not null check (status in ('PENDING_PUBLISH','QUEUED','SYNCING','SYNCED','FAILED','RETRY_REQUESTED'))`
- `graph_event_id varchar(255) null`
- `failure_code varchar(100) null`
- `safe_message text null`
- `last_attempt_at timestamptz null`
- `queued_at timestamptz null`
- `processed_at timestamptz null`
- `retry_count integer not null default 0`
- `trace_id varchar(100) null`
- auditing columns

Constraints/indexes:

- unique `(owner_employee_id, related_type, related_id, event_kind)`
- index `(status, queued_at)`
- index `(owner_employee_id, event_kind)`

Queue payload:

- SNS/SQS message contains `syncRecordId`, `eventKind`, tenant/env metadata, and trace ID only. Worker loads authoritative payload data from PostgreSQL.

### `manager_plan_summary`

Purpose: direct-report roll-up row for manager command center.

Columns:

- `id uuid primary key`
- `manager_employee_id uuid not null references employee(id)`
- `employee_id uuid not null references employee(id)`
- `weekly_plan_id uuid not null references weekly_plan(id)`
- `week_start_date date not null`
- `plan_state varchar(32) not null`
- `review_status varchar(32) null`
- `review_due_at timestamptz null`
- `is_review_overdue boolean not null default false`
- `planned_count integer not null default 0`
- `unplanned_count integer not null default 0`
- `misaligned_count integer not null default 0`
- `needs_review_count integer not null default 0`
- `blocked_count integer not null default 0`
- `carry_forward_count integer not null default 0`
- `unresolved_dispute_count integer not null default 0`
- `updated_at timestamptz not null`

Constraints/indexes:

- unique `(manager_employee_id, employee_id, week_start_date)`
- index `(manager_employee_id, week_start_date, plan_state)`
- index `(manager_employee_id, review_status)`

### `manager_heatmap_cell`

Purpose: manager heatmap cell for direct report x Defining Objective.

Columns:

- `id uuid primary key`
- `manager_employee_id uuid not null references employee(id)`
- `employee_id uuid not null references employee(id)`
- `week_start_date date not null`
- `defining_objective_id uuid not null references defining_objective(id)`
- `commitment_count integer not null default 0`
- `planned_count integer not null default 0`
- `unplanned_count integer not null default 0`
- `misaligned_count integer not null default 0`
- `needs_review_count integer not null default 0`
- `blocked_count integer not null default 0`
- `carry_forward_count integer not null default 0`
- `unresolved_dispute_count integer not null default 0`
- `risk_badges text[] not null default '{}'`
- `updated_at timestamptz not null`

Constraints/indexes:

- unique `(manager_employee_id, employee_id, week_start_date, defining_objective_id)`
- index `(manager_employee_id, week_start_date)`
- index `(manager_employee_id, defining_objective_id)`

### `audit_event`

Purpose: durable trail for sensitive lifecycle, review, integration, and authorization events.

Columns:

- `id uuid primary key`
- `actor_employee_id uuid null references employee(id)`
- `action varchar(100) not null`
- `entity_type varchar(64) not null`
- `entity_id uuid null`
- `summary text not null`
- `metadata_json jsonb null`
- `created_at timestamptz not null`

Indexes:

- index `(entity_type, entity_id)`
- index `(actor_employee_id, created_at)`
- index `(action, created_at)`

## Projection Update Rules

- Projection updates occur synchronously with affected domain writes.
- Writes that affect summary/heatmap include commitment create/update/delete, plan lock, reconciliation start/close, manager review, dispute open/respond/resolve, and carry-forward creation.
- An internal rebuild job/CLI can truncate/recompute `manager_plan_summary` and `manager_heatmap_cell` from source tables.
- There is no user-facing admin projection rebuild UI in MVP.

## Data Model Open Items

- Exact RCDO seed copy can be finalized during seed migration authoring.
- Exact Auth0 claim-name defaults remain configurable.
- Exact `ROOT_DOMAIN` and AWS region override are deploy-time variables.

