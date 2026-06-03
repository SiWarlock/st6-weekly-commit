-- V2 — partial unique indexes (task 1.3). The DB-level enforcement that makes the single-row
-- review/dispute/review-block invariants provably consistent (§4 verbatim). Layered on V1; the
-- sync FULL unique (owner, related_type, related_id, event_kind) already lives in V1 — V2 adds
-- only the partial (WHERE-scoped) uniques. Each backs a key invariant; the partial scope is what
-- lets a superseded row (inactive relationship / RESOLVED dispute / non-review-block sync) coexist.

-- 1. Single active manager per direct report (§6 — makes the single-row direct-report model
--    consistent; an inactive relationship does not block reassignment).
create unique index uq_active_manager_per_report
    on manager_relationship (direct_report_employee_id)
    where active = true;

-- 2. At most one unresolved dispute per commitment (safety rule #6 — a RESOLVED dispute frees the
--    commitment for a new one).
create unique index uq_one_unresolved_dispute_per_commitment
    on alignment_dispute (commitment_id)
    where status in ('OPEN', 'IC_RESPONDED');

-- 3. One review-block calendar event per manager/week (§10 locked decision — owner = the manager,
--    keyed on (owner, week) regardless of related_id, giving the "one event per manager/week" grain
--    DB-level idempotency). Non-review-block sync records are unconstrained by this index.
create unique index uq_one_review_block_per_manager_week
    on outlook_calendar_sync_record (owner_employee_id, week_start_date)
    where event_kind = 'MANAGER_REVIEW_BLOCK';
