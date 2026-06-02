# Domain Model

> Status: Draft planning artifact
> Phase: 5 - Domain model and state machines
> Date: 2026-06-02
> Sources: `PRD.md`, original PRD pasted by user, `PRODUCT_BRIEF.md`, `USERS.md`, `USER_FLOWS.md`, Office Hours reframe, CEO review

## Core Entities

| Entity | Definition | Key Fields | Source Of Truth |
|---|---|---|---|
| Employee | A seeded user who can own weekly plans and/or manage direct reports | id, displayName, email, role, active, timezone optional | WC seed data for MVP; production identity/HR source later |
| Manager Relationship | Direct-report relationship between manager and employee | managerEmployeeId, directReportEmployeeId, active | WC seed data for MVP; production HR/identity source later |
| Weekly Plan | Lifecycle container for one employee's commitments in one Monday-Sunday week | id, employeeId, weekStartDate, weekEndDate, state, generatedAt, lockedAt, reconciliationStartedAt, reconciledAt | WC PostgreSQL |
| Weekly Commitment | Atomic unit of work, alignment, dispute, unplanned tracking, outcome, and carry-forward | id, planId, title, description, supportingOutcomeId, priority, workType, confidence, alignmentStatus, planned/unplanned marker, outcome fields, carryForwardSourceId | WC PostgreSQL |
| Rally Cry | Top-level strategy grouping in RCDO | id, title, description, active | Read-only seeded RCDO for MVP |
| Defining Objective | Middle-level strategy grouping under Rally Cry | id, rallyCryId, title, description, active | Read-only seeded RCDO for MVP |
| Supporting Outcome | Required strategic link target for planned commitments | id, definingObjectiveId, title, description, active | Read-only seeded RCDO for MVP |
| Manager Review | Review record for a locked weekly plan | id, planId, managerEmployeeId, status, reviewDueAt, reviewedAt, note optional | WC PostgreSQL |
| Alignment Dispute | Structured manager challenge on one commitment | id, commitmentId, managerEmployeeId, status, flagType, managerNote, icResponse, resolvedAt | WC PostgreSQL |
| Comment | Nested discussion item attached to a plan, commitment, review, or dispute | id, targetType, targetId, authorEmployeeId, parentCommentId, path, depth, body | WC PostgreSQL |
| Outlook Calendar Sync Record | Persistent sync/retry record and durable outbox for Graph calendar jobs | id, ownerEmployeeId, relatedType, relatedId, eventKind, graphEventId, status, failureCode, safeMessage, lastAttemptAt, queuedAt, processedAt, retryCount | WC PostgreSQL |
| Manager Plan Summary | Denormalized manager command-center row for one manager/report/week | managerEmployeeId, employeeId, planId, weekStartDate, planState, reviewStatus, overdue, risk counts | WC PostgreSQL projection |
| Manager Heatmap Cell | Denormalized direct-report x Defining Objective heatmap cell | managerEmployeeId, employeeId, weekStartDate, definingObjectiveId, commitment counts, risk badges | WC PostgreSQL projection |
| Audit Event | Lightweight audit trail for sensitive lifecycle/review/integration/security actions | id, actorEmployeeId, action, entityType, entityId, timestamp, summary, metadataJson | WC PostgreSQL |

Classification: proposed recommendation.

## Relationships

- Employee has many Weekly Plans.
- Employee may manage many direct reports through Manager Relationship.
- Weekly Plan has many Weekly Commitments.
- Weekly Plan has one Manager Review after lock.
- Weekly Commitment belongs to zero or one Supporting Outcome while draft, and exactly one Supporting Outcome before lock if planned.
- Supporting Outcome belongs to one Defining Objective.
- Defining Objective belongs to one Rally Cry.
- Weekly Commitment may have many Alignment Disputes over time, but can have at most one open or IC-responded unresolved dispute at once.
- Weekly Commitment may point to one carry-forward source commitment.
- Comment may nest under another Comment using `parentCommentId`, `path`, and `depth`.
- Outlook Calendar Sync Record belongs to a related plan/review context and owner employee.
- Outlook Calendar Sync Record is the durable outbox row used by SNS/SQS publishing, worker processing, and manual retry.
- Manager Plan Summary and Manager Heatmap Cell are synchronous read-model projections derived from plans, commitments, reviews, disputes, and RCDO links.
- Audit Event references the actor and affected entity but does not replace `AbstractAuditingEntity` created/updated metadata on normal entities.

Classification: proposed recommendation.

## Seeded RCDO Shape

MVP uses a small realistic business hierarchy:

- 1 Rally Cry
- 3 Defining Objectives under that Rally Cry
- 3 Supporting Outcomes under each Defining Objective
- Execution-SaaS narrative labels, with Defining Objectives around customer adoption, operational excellence, and platform reliability.

Rationale:

- The manager heatmap needs multiple Defining Objectives to demonstrate coverage, overload, and risk.
- Three Supporting Outcomes per Defining Objective gives enough drill-down depth without bloating seed data.
- A single Rally Cry keeps the demo focused on weekly alignment mechanics rather than strategy administration.

Exact copy can be finalized during seed-data authoring, but the theme and shape are locked.

Classification: locked decision, confirmed by user on 2026-06-02.

## Plan State Machine

```text
DRAFT -> LOCKED -> RECONCILING -> RECONCILED
```

Rules:

- Generated plan shells start in `DRAFT`.
- `DRAFT` allows draft commitment edits.
- `LOCKED` freezes planned baseline fields.
- `RECONCILING` allows outcome entry and explicit unplanned commitments.
- `RECONCILED` closes the week as historical truth.
- No unlock/amend flow exists in MVP.

Classification: locked decision.

## Manager Review State Machine

Source statuses:

```text
NOT_REVIEWED -> REVIEWED_WITH_DISPUTES
NOT_REVIEWED -> REVIEWED
REVIEWED_WITH_DISPUTES -> REVIEWED
```

Derived display state:

```text
OVERDUE when now > reviewDueAt and status is NOT_REVIEWED
```

Rules:

- Manager review record is created or initialized when a plan locks.
- Source review statuses are exactly `NOT_REVIEWED`, `REVIEWED_WITH_DISPUTES`, and `REVIEWED` for MVP.
- `REVIEWED_WITH_DISPUTES` satisfies the manager review SLA.
- Unresolved disputes remain separate alignment risk.
- `OVERDUE` is computed from `reviewDueAt` and not persisted as the source status.

Classification: locked decision, confirmed by user on 2026-06-02.

## Alignment Dispute State Machine

```text
OPEN -> IC_RESPONDED -> RESOLVED
```

Rules:

- Manager opens a dispute by flagging `Needs Revision` or `Misaligned` with a required note.
- IC may revise the Supporting Outcome or add rationale.
- IC response does not resolve the dispute.
- Direct manager must explicitly resolve the dispute.
- MVP allows no formal dispute escalation/pass-up.

Classification: locked decision.

## Commitment Outcome States

During reconciliation, commitments can be marked:

- Completed
- Partially completed
- Blocked
- Canceled
- Carried forward

Rules:

- Carry-forward is an outcome on the source commitment and creates a linked draft commitment in the next weekly plan.
- Unplanned work after lock is represented by explicit `Unplanned` commitments.
- Unplanned commitments do not require a Supporting Outcome at creation time.
- Unplanned commitments must link to a Supporting Outcome before reconciliation can close, unless the architecture later defines an explicit unable-to-align exception.

Classification: locked decision.

## Business Rules And Invariants

- Employee plus week start date must be unique for Weekly Plan.
- Planned commitments cannot be locked without Supporting Outcomes.
- A plan cannot lock with zero planned commitments.
- Manager formal review actions require a locked plan.
- Managers can see direct-report draft details but cannot formally review drafts.
- Managers can only access direct-report plans and dashboard aggregates.
- ICs can only mutate their own plans.
- ICs do not see team-level heatmap data in MVP.
- Locked planned commitment baseline fields cannot be changed in MVP.
- Unplanned work after lock must be labeled as unplanned.
- Unplanned commitments must link to Supporting Outcomes before reconciliation close.
- Carry-forward commitment must link back to its source commitment.
- A commitment can have at most one unresolved alignment dispute at a time.
- Outlook sync failure cannot roll back core lifecycle changes.
- Graph retry must reuse an existing sync record to avoid duplicate events.
- Repeated Outlook worker failures can route messages to SQS DLQ, but user-facing retry remains sync-record based.
- Sensitive lifecycle, manager review, dispute, Graph, and authorization-denial events write Audit Event rows.
- Domain records use UUID primary keys.
- Lifecycle/status fields are stored as `VARCHAR` with check constraints.
- Planned and unplanned commitments share one `weekly_commitment` table.
- Manager command-center projections update synchronously with domain writes.
- Projection repair is available through an internal job/CLI, not a user-facing admin UI.
- Nested comments use a materialized-path model.

Classification: locked decision / proposed recommendation mix.

## Locked Baseline Representation

MVP represents the locked baseline by making planned commitment fields immutable after plan lock.

Immutable after lock for planned commitments:

- title
- description
- Supporting Outcome link
- priority
- work type, except explicit post-lock commitments must use `Unplanned`
- confidence
- planned/unplanned marker
- source plan/week ownership

Mutable after lock:

- progress/status fields used during reconciliation
- reconciliation outcome
- outcome notes
- carry-forward link created during reconciliation
- manager-owned alignment status and manager note
- dispute-related fields owned by Alignment Dispute records

Rationale:

- The MVP has no unlock/amend flow.
- Backend validation can preserve baseline truth without a separate snapshot table.
- A future audited amendment/versioning model can be added if production requires it.

Classification: locked decision, confirmed by user on 2026-06-02.

## Glossary

- RCDO: Rally Cry -> Defining Objective -> Supporting Outcome.
- Supporting Outcome: Required lowest-level strategic link for planned weekly commitments.
- Chess layer: strategic metadata on commitments: priority, work type, confidence, alignment status, manager note.
- Plan shell: generated empty draft Weekly Plan for an employee/week.
- Locked baseline: planned commitment state preserved at plan lock for planned-vs-actual reconciliation.
- Unplanned commitment: explicit post-lock work item that does not rewrite the locked baseline.

Classification: locked decision.

## Ambiguous Terms / Open Domain Questions

- What exact root domain and AWS region should deployment use?
- What exact Auth0 claim-name defaults should the configurable mapper use?

Classification: open question.
