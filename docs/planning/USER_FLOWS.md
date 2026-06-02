# User And System Flows

> Status: Draft planning artifact
> Phase: 4 - User and lifecycle flows
> Date: 2026-06-02
> Sources: `PRD.md`, `PRODUCT_BRIEF.md`, `USERS.md`, `STAKEHOLDERS.md`, Office Hours reframe, CEO review

## Flow: Scheduled Weekly Plan Shell Generation

Actor: EKS Kubernetes CronJob / plan generation service

Trigger: weekly planning period starts.

Preconditions:

- Active seeded employees exist.
- Employee/manager relationships exist.
- The target week can be calculated from the MVP week-boundary policy.
- A unique plan constraint exists for employee plus week.

Steps:

1. Kubernetes CronJob invokes the Spring Boot plan generation job/API.
2. Job determines the current target week.
3. Job loads active employees.
4. For each employee, job attempts to create a draft weekly plan shell if one does not already exist.
5. Existing plans are skipped idempotently.
6. Created plans start empty and count as `Not Started` for manager dashboard purposes until the IC adds at least one commitment.
7. No Outlook events are created automatically in MVP.
8. Job records success/failure telemetry through logs/audit as appropriate.

System Responsibilities:

- Generate weekly plan shells as an organization-driven cadence from EKS.
- Guarantee idempotency so reruns do not create duplicate plans.
- Preserve a future hook for automatic calendar event/reminder creation.
- Keep Graph sync separate from shell generation in MVP.
- Expose CronJob success/failure through CloudWatch-visible logs.

Success State:

- Every active employee has at most one draft plan shell for the generated week.
- Manager dashboards can show all direct reports, including not-started plans.

Failure States:

- Employee roster unavailable or malformed.
- Duplicate plan conflict.
- Partial generation failure.
- Week-boundary calculation error.

Data Touched:

- Employee
- Manager relationship
- Weekly Plan
- Audit/observability event

Security / Lifecycle Constraints:

- Plan generation creates only plan shells, not commitments.
- Generated plan shells remain owned by the employee.
- Managers can view direct-report generated drafts, but formal review actions remain unavailable until lock.

Decision:

MVP uses pre-generated draft plan shells through an idempotent EKS Kubernetes CronJob. Automatic Outlook event/reminder creation is designed as an extension point but not enabled by default in MVP.

Classification: locked decision, confirmed by user on 2026-06-02.

Week Boundary:

MVP uses a single configured organization timezone and Monday-Sunday weekly periods. For assessment/demo, the default organization timezone is `America/Chicago` unless the implementation environment overrides it.

Architecture impact:

- Weekly plan uniqueness is based on employee plus week start date in organization time.
- Scheduler uses the organization timezone to determine the target week.
- Manager review SLA calculations use the organization timezone.
- UI labels should display the week range consistently for all seeded users.
- User-local timezone support is deferred.

Classification: locked decision, confirmed by user on 2026-06-02.

## Stretch Flow: Automatic Calendar Events From Generated Plans

Actor: backend scheduler plus Outlook sync path

Trigger: optional stretch behavior after weekly plan shells are generated.

Preconditions:

- Plan shell generation succeeds.
- Outlook integration is configured and authorized.
- Calendar event policy is enabled.

Steps:

1. Scheduler emits or enqueues plan-generated events.
2. Outlook sync path creates planning/reminder events for users.
3. Sync records store Graph event IDs and status.
4. Failures are visible, logged, and retryable.

MVP Status:

Deferred unless time remains. The architecture should preserve the event hook and sync-record model so this can be added without changing the core plan lifecycle.

Classification: deferred work / designed extension.

## Flow: IC Drafts And Locks Weekly Plan

Actor: IC

Trigger: IC opens the current weekly plan after the weekly plan shell has been generated.

Preconditions:

- A draft weekly plan shell exists for the IC and week.
- IC is authenticated as the owner of the plan.
- Seeded RCDO hierarchy is available.

Steps:

1. IC opens the current week plan.
2. IC adds one or more planned commitments.
3. IC selects exactly one Supporting Outcome for each planned commitment.
4. IC assigns chess-layer metadata.
5. IC saves draft changes.
6. IC locks the plan during the plan week.
7. Backend validates lock requirements.
8. Backend changes plan state from `DRAFT` to `LOCKED`.
9. Backend freezes the planned baseline.
10. Outlook sync path creates or updates the IC planning event when configured.

System Responsibilities:

- Enforce ownership authorization.
- Validate that the plan has at least one planned commitment before lock.
- Validate that every planned commitment links to a Supporting Outcome before lock.
- Persist chess-layer metadata.
- Freeze locked baseline fields.
- Emit a lifecycle event or observability signal for `Plan Locked`.
- Trigger non-blocking Outlook sync.

Success State:

- Plan is `LOCKED`.
- Manager review state is `NOT_REVIEWED`.
- Manager review SLA due date is calculated.
- Manager can formally review, comment, and flag alignment.

Failure States:

- No commitments exist.
- One or more planned commitments lack Supporting Outcomes.
- IC attempts to lock another user's plan.
- Plan is already locked, reconciling, or reconciled.
- Outlook sync fails after lock; core lock remains successful and sync status is visible.

Data Touched:

- Weekly Plan
- Weekly Commitment
- Supporting Outcome reference
- Manager Review
- Outlook Calendar Sync Record
- Audit/observability event

Security / Lifecycle Constraints:

- Locking is allowed any time during the plan week once requirements pass.
- Locking an empty plan is not allowed.
- Locking a plan with unlinked planned commitments is not allowed.
- Manager review and dispute actions are unavailable until lock.
- The locked baseline is immutable in MVP.

Decision:

ICs may lock a weekly plan any time during the plan week when at least one planned commitment exists and all planned commitments have Supporting Outcomes.

Classification: locked decision, confirmed by user on 2026-06-02.

## Flow: IC Reconciles Weekly Plan

Actor: IC

Trigger: IC starts reconciliation for a locked plan.

Preconditions:

- Weekly plan state is `LOCKED`.
- IC is authenticated as the owner of the plan.
- Locked planned commitments exist.

Steps:

1. IC opens the locked plan.
2. IC starts reconciliation.
3. Backend changes plan state from `LOCKED` to `RECONCILING`.
4. IC records outcomes for planned commitments.
5. IC adds explicit unplanned commitments for work that happened after lock.
6. IC links unplanned commitments to Supporting Outcomes before reconciliation close.
7. IC records outcomes for unplanned commitments.
8. For unfinished work, IC selects carry-forward where appropriate.
9. Backend resolves the next Monday-Sunday week in the organization timezone.
10. Backend creates the next-week draft plan shell if it does not already exist.
11. Backend creates linked next-week draft commitments for carried-forward work.
12. IC submits reconciliation.
13. Backend validates reconciliation completeness.
14. Backend changes plan state from `RECONCILING` to `RECONCILED`.
15. Outlook sync path creates or updates the IC reconciliation event when configured.

System Responsibilities:

- Allow reconciliation any time after lock.
- Preserve the locked planned baseline.
- Require outcomes for commitments included in reconciliation.
- Require Supporting Outcome links for unplanned commitments before reconciliation close.
- Distinguish planned vs unplanned commitments.
- Link carry-forward commitments back to source commitments.
- Emit lifecycle events for `Plan Entered Reconciliation`, `Commitment Carried Forward`, and `Plan Reconciled`.
- Trigger non-blocking Outlook sync.

Success State:

- Plan is `RECONCILED`.
- Planned-vs-actual data is persisted.
- Carry-forward work is traceable to source commitments.
- Manager command center reflects reconciliation completion and carry-forward risk.

Failure States:

- IC attempts to reconcile a draft or already reconciled plan.
- IC attempts to mutate locked baseline fields.
- Required outcomes are missing.
- One or more unplanned commitments lack Supporting Outcome links at reconciliation close.
- Next-week plan shell creation fails when carry-forward is selected.
- Outlook sync fails after reconciliation; core reconciliation remains successful and sync status is visible.

Data Touched:

- Weekly Plan
- Weekly Commitment
- Commitment outcome fields
- Carry-forward link
- Next-week Weekly Plan
- Outlook Calendar Sync Record
- Audit/observability event

Security / Lifecycle Constraints:

- Reconciliation does not require manager review.
- Reconciliation is allowed any time after lock.
- Date/week-end timing is encouraged through UX and calendar touchpoints, not enforced as a hard MVP gate.
- Unplanned work is additive and labeled.
- Unplanned commitments can be added quickly, but must map to RCDO before reconciliation closes.

Decision:

ICs may enter reconciliation any time after lock. Week-end behavior is encouraged by UI and Outlook touchpoints rather than hard date gating in MVP.

Classification: locked decision, confirmed by user on 2026-06-02.

Carry-Forward Target Decision:

Carry-forward always targets the next Monday-Sunday weekly period in the organization timezone. The backend creates the next-week draft plan shell if it is missing, then creates linked draft commitments from the carried-forward source commitments.

Classification: locked decision, confirmed by user on 2026-06-02.

## Flow: Manager Reviews And Opens Alignment Dispute

Actor: Direct manager

Trigger: A direct report locks a weekly plan.

Preconditions:

- Weekly plan state is `LOCKED` or later.
- Manager is authenticated and authorized for the IC as a direct report.
- Formal review actions are unavailable for draft plans and available after lock.

Steps:

1. Manager opens the command center.
2. Manager reviews direct-report plan status, commitments, chess-layer metadata, and RCDO links.
3. Manager adds comments where needed.
4. Manager marks the plan reviewed or flags one or more commitments as `Needs Revision` or `Misaligned`.
5. For each flag, manager must enter a note explaining the concern.
6. Backend creates an alignment dispute for each flagged commitment.
7. IC sees the manager flag and note.
8. IC responds by changing the Supporting Outcome or adding rationale.
9. Manager reviews the IC response.
10. Manager explicitly resolves the dispute.

System Responsibilities:

- Enforce direct-report authorization.
- Separate draft read visibility from locked-plan review/dispute mutations.
- Require manager notes for alignment flags.
- Preserve manager flag, IC response, and manager resolution in the audit trail.
- Keep dispute state separate from plan lifecycle and manager review status.
- Surface unresolved disputes in both IC workspace and manager command center.

Success State:

- Manager review action is captured.
- Any alignment disputes have an auditable manager flag, IC response, and manager resolution.
- Manager command center reflects current review and dispute status.

Failure States:

- Manager attempts to review a non-direct-report plan.
- Manager attempts to flag a draft plan.
- Manager flags a commitment without a required note.
- IC attempts to resolve a dispute directly.
- Manager attempts to change the IC's Supporting Outcome link without IC revision.

Data Touched:

- Weekly Plan
- Weekly Commitment
- Manager Review
- Alignment Dispute
- Audit/observability event

Security / Lifecycle Constraints:

- Only the direct manager can formally review or resolve disputes.
- IC response does not auto-resolve the dispute.
- Manager must explicitly resolve the dispute.
- Manager cannot silently rewrite an IC's Supporting Outcome link in MVP.

Decision:

When a manager flags a commitment and the IC responds, the dispute remains open until the manager explicitly resolves it.

Classification: locked decision, confirmed by user on 2026-06-02.

Review Completion Decision:

Manager review uses a two-step distinction:

- `REVIEWED_WITH_DISPUTES`: manager has reviewed the locked plan and opened one or more unresolved alignment disputes.
- `REVIEWED`: manager has reviewed the locked plan and there are no unresolved alignment disputes.

This keeps manager review activity distinct from unresolved alignment correction work.

Classification: locked decision, confirmed by user on 2026-06-02.

SLA Semantics:

`REVIEWED_WITH_DISPUTES` satisfies the manager review SLA. The review SLA measures whether the manager examined the locked plan on time. Unresolved disputes are tracked separately as alignment risk.

Classification: locked decision, confirmed by user on 2026-06-02.

## Flow: Outlook Calendar Sync

Actor: WC backend, SNS/SQS, Outlook Sync Worker, Outlook Graph API or demo Graph adapter

Trigger: selected workflow state changes.

Preconditions:

- User identity is known.
- The related plan or manager review context exists.
- Outlook integration is configured for the environment, or the MVP demo Graph adapter fallback is enabled.

Sync Triggers:

- IC locks a weekly plan: create or update IC weekly planning event.
- IC enters reconciliation: create or update IC reconciliation event.
- Manager review-block event is needed: create or update manager calendar event with deep link to the command center.

Explicit Non-Trigger:

- Scheduled weekly plan shell generation does not automatically create Outlook events in MVP.

Steps:

1. Core workflow mutation succeeds.
2. Backend creates or updates an Outlook Calendar Sync Record in the same durable workflow boundary.
3. Backend publishes a lifecycle/sync event through SNS.
4. SQS subscription receives the Outlook sync job.
5. Outlook Sync Worker consumes the SQS job.
6. Worker calls the real Graph adapter when configured, otherwise the deterministic demo/failure adapter.
7. Sync record stores Graph event ID on success.
8. Sync record stores safe failure status/details on failure.
9. User-facing UI shows sync success, warning, retry, or failed state.
10. Manual retry uses the existing sync record to republish/requeue work and avoid duplicate events.
11. SQS DLQ captures repeatedly failed messages for operational visibility.

System Responsibilities:

- Keep Outlook sync downstream of core lifecycle mutations.
- Treat Outlook Calendar Sync Record as the durable outbox for Graph jobs.
- Include lifecycle context and deep links in event payloads.
- Persist Graph event IDs for update/cancel.
- Surface failures without exposing secrets.
- Avoid duplicate events on retry.
- Log success and failure outcomes.

Success State:

- Calendar touchpoint exists or is updated.
- Sync record is `SYNCED`.
- UI can show the deep-linked event state where useful.

Failure States:

- Graph credentials missing.
- Graph API unavailable.
- Token expired or insufficient scope.
- Event creation/update fails.
- Retry fails.
- SQS message reaches DLQ after repeated worker failure.

Data Touched:

- Outlook Calendar Sync Record
- SNS lifecycle event
- SQS sync job
- Weekly Plan
- Manager Review
- Audit/observability event

Security / Lifecycle Constraints:

- Outlook sync failure never rolls back plan lock, review, reconciliation, or carry-forward.
- Graph tokens/secrets are never hardcoded and are never displayed to users.
- Sync records must avoid leaking sensitive token details.
- Graph integration uses app-only tenant/admin consent for real calendar writes.
- Demo adapter is allowed when Graph env vars are absent.

Decision:

MVP automatically attempts Outlook sync after plan lock, reconciliation start, and manager review-block event creation. Scheduled generated plan shells do not create Outlook events by default. The runtime path uses SNS/SQS and a separate EKS worker.

Classification: locked decision, confirmed by user on 2026-06-02.

Recovery Decision:

When Outlook Graph sync fails, MVP exposes a user-visible warning and manual retry action. The backend logs the failure and retries through the existing Outlook Calendar Sync Record to avoid duplicate events. SQS includes a DLQ for repeatedly failed worker jobs, but no admin redrive UI is included in MVP.

Classification: locked decision, confirmed by user on 2026-06-02.

## Flow: Manager Review SLA And Overdue Signal

Actor: WC backend read model and direct manager

Trigger: IC locks a weekly plan.

Preconditions:

- Weekly plan transitions from `DRAFT` to `LOCKED`.
- Manager review state is initialized for the locked plan.
- Organization timezone is configured.

Steps:

1. Backend creates or updates the Manager Review record when the plan locks.
2. Backend calculates `reviewDueAt` as end of the next business day in the organization timezone.
3. Manager command center loads direct-report review records.
4. Backend/API returns review status, `reviewDueAt`, and an `isOverdue` read-model signal.
5. `isOverdue` is true when current time is past `reviewDueAt` and review status is not `REVIEWED` or `REVIEWED_WITH_DISPUTES`.
6. Manager completes review as `REVIEWED` or `REVIEWED_WITH_DISPUTES`.
7. Overdue signal clears because the review SLA has been satisfied.

System Responsibilities:

- Persist the due date at lock time.
- Compute overdue at read time instead of relying on a scheduler to flip rows.
- Keep overdue review separate from unresolved dispute risk.
- Expose overdue state in manager command center filters and badges.

Success State:

- Manager sees which locked plans need review and which are overdue.
- `REVIEWED_WITH_DISPUTES` satisfies review SLA while unresolved disputes remain visible separately.

Failure States:

- Plan has no manager relationship.
- Due date cannot be calculated because organization timezone config is missing.
- Read model computes overdue inconsistently across API/UI.

Data Touched:

- Manager Review
- Weekly Plan
- Employee/manager relationship
- Organization configuration

Security / Lifecycle Constraints:

- Overdue visibility is direct-report scoped.
- No background job is required to mutate review status to `OVERDUE`.
- `OVERDUE` is a derived display/query state, not the source-of-truth status value.

Decision:

MVP stores `reviewDueAt` and computes overdue status at read time when the review is past due and not completed. No scheduler is required to flip review rows into an explicit `OVERDUE` state.

Classification: locked decision, confirmed by user on 2026-06-02.

## Flow: Manager Uses RCDO Coverage Heatmap

Actor: Direct manager

Trigger: Manager opens the command center or changes dashboard filters.

Preconditions:

- Manager is authenticated.
- Direct-report relationships exist.
- Weekly plans and commitments exist for the selected week.
- RCDO hierarchy is available.

Steps:

1. Manager opens the command center.
2. Backend authorizes the manager and resolves direct reports.
3. Backend aggregates commitments by direct report and Defining Objective.
4. API returns heatmap cells with counts and explicit risk badges.
5. Manager scans cells for under-covered objectives, overloaded objectives, misaligned work, needs-review work, blocked work, carry-forward risk, unreviewed plans, or overdue review.
6. Manager drills into a cell.
7. UI shows Supporting Outcome breakdown and linked commitments for that direct report/objective combination.
8. Manager filters by person, plan state, review state, Defining Objective, Supporting Outcome, priority, work type, and alignment status.

System Responsibilities:

- Enforce direct-report scoping before aggregation.
- Aggregate on indexed backend query paths.
- Avoid N+1 query patterns and client-side full loads.
- Return drill-down data scoped to the authorized manager.
- Keep heatmap risk signals explicit rather than hiding them in a calculated score.

Success State:

- Manager can identify alignment concentration, gaps, and risk without opening every plan.
- Drill-down explains why a cell is risky.

Failure States:

- Manager attempts to query non-direct-report data.
- RCDO hierarchy is missing or stale.
- Heatmap query loads too much data or misses the 200ms common-path target.
- Risk badges conflict with underlying commitment state.

Data Touched:

- Employee/manager relationship
- Weekly Plan
- Weekly Commitment
- Manager Review
- Alignment Dispute
- RCDO hierarchy

Security / Lifecycle Constraints:

- Heatmap is manager-only in MVP.
- ICs do not see team-level heatmap data.
- Cells default to Defining Objective grain with Supporting Outcome drill-down.
- Risk badges must derive from commitment/review/dispute/reconciliation state.

Decision:

Manager heatmap cells show commitment counts plus explicit risk badges such as misaligned, needs-review, blocked, carry-forward, unreviewed, or overdue review. The MVP does not use an opaque calculated health score.

Classification: locked decision, confirmed by user on 2026-06-02.

## Phase 4 Flow Coverage Matrix

| MVP Requirement | Covered By Flow | Status |
|---|---|---|
| Generate current weekly planning surface | Scheduled Weekly Plan Shell Generation | Covered |
| IC creates draft commitments | IC Drafts And Locks Weekly Plan | Covered |
| Required Supporting Outcome link before lock | IC Drafts And Locks Weekly Plan | Covered |
| Chess-layer metadata | IC Drafts And Locks Weekly Plan; Manager Uses RCDO Coverage Heatmap | Covered |
| Locked baseline | IC Drafts And Locks Weekly Plan; IC Reconciles Weekly Plan | Covered |
| Manager direct-report visibility | Manager Reviews And Opens Alignment Dispute; Manager Uses RCDO Coverage Heatmap | Covered |
| Manager review after lock | Manager Reviews And Opens Alignment Dispute | Covered |
| Review SLA/overdue signal | Manager Review SLA And Overdue Signal | Covered |
| Alignment dispute workflow | Manager Reviews And Opens Alignment Dispute | Covered |
| RCDO heatmap and drill-down | Manager Uses RCDO Coverage Heatmap | Covered |
| Unplanned post-lock work | IC Reconciles Weekly Plan | Covered |
| Reconciliation outcomes | IC Reconciles Weekly Plan | Covered |
| Carry-forward | IC Reconciles Weekly Plan | Covered |
| Outlook planning/reconciliation/review events | Outlook Calendar Sync | Covered |
| Outlook failure recovery | Outlook Calendar Sync | Covered |
| Unauthorized manager denial | Manager Reviews And Opens Alignment Dispute; Manager Uses RCDO Coverage Heatmap | Covered |

Phase 4 stop condition:

Every MVP requirement in the PRD maps to at least one user or system flow. No additional MVP flow was requested before moving to Phase 5.

Classification: locked planning checkpoint, confirmed by user on 2026-06-02.
