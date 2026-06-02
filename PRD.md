# ST6 Weekly Commit Module

## Product Requirements Document

### Summary

The organization currently uses 15Five for weekly planning, but the weekly planning workflow has no enforced structural connection between individual commitments and organizational strategic goals. Managers lack clear visibility into how team members' weekly work maps to Rally Cries, Defining Objectives, and Supporting Outcomes, making misalignment hard to identify until the week has already drifted.

The ST6 Weekly Commit module (`WC`) is a strategy-enforced weekly alignment system. It replaces the weekly Check-in / Priorities / Objectives-alignment slice of 15Five, not the entire 15Five performance-management suite. The MVP has two equal surfaces:

- **IC Weekly Commit Workspace:** individual contributors create, lock, reconcile, and carry forward weekly commitments.
- **Manager Alignment Command Center:** managers review direct reports' weekly plans, alignment risk, RCDO coverage, reconciliation status, and review accountability.

The core product rule is simple: every planned weekly commitment must map to a specific Supporting Outcome in the RCDO hierarchy.

## Glossary

- **WC:** Weekly Commit module.
- **PA:** Parent/Platform Application, assumed. The existing production host app that loads WC as a Vite Module Federation remote.
- **RCDO:** Rally Cry -> Defining Objective -> Supporting Outcome. This is the strategic hierarchy that weekly commitments must map to.
- **Supporting Outcome:** The required lowest-level strategic target linked to each planned weekly commitment.
- **Chess layer:** Strategic prioritization metadata applied to commitments. This is not a game mechanic or UI metaphor.
- **IC:** Individual contributor.
- **Manager review:** A non-blocking review workflow separate from the IC plan lifecycle.

## Problem And Context

### Business Context

Today, weekly planning happens in 15Five and is disconnected from strategic execution tracking. Employees fill out weekly plans with no enforced link to company strategy, and managers review those plans without a reliable way to know whether the work supports the right priorities.

15Five already supports weekly check-ins, priorities, manager review, and optional objective linking. This product differs by making strategy linkage mandatory and by giving managers a command-center view of alignment, review lag, reconciliation risk, and RCDO coverage.

### Demand Evidence

Demand is prompt-derived. We do not have independent user interviews, incident examples, or manager quotes. The source PRD asserts that weekly planning is disconnected from strategic execution and that managers lack visibility. This PRD therefore makes unknowns explicit rather than pretending the exact internal workflow is known.

### Intended Outcome

WC should help the organization answer, during the week:

- What has each person committed to?
- Which Supporting Outcome does each commitment advance?
- Which Defining Objectives are under-covered, over-covered, or at risk?
- Which commitments look misaligned or need manager review?
- Which plans are locked, reviewed, reconciling, reconciled, or overdue?
- Which work was planned, completed, unplanned, blocked, or carried forward?

## Goals

1. Enforce strategic linkage by requiring every planned weekly commitment to map to a Supporting Outcome.
2. Preserve a locked weekly baseline so planned-vs-actual reconciliation is meaningful.
3. Give managers direct-report visibility into alignment, priorities, review status, and reconciliation risk.
4. Make manager review accountable without blocking IC reconciliation.
5. Integrate Outlook Graph calendar touchpoints without making calendar sync a single point of failure.
6. Run standalone for the assessment while preserving production-compatible Module Federation boundaries.

## Non-Goals

WC does not replace all of 15Five. The following are out of scope for MVP:

- Engagement surveys
- Pulse / sentiment tracking
- High Fives / recognition
- Full Best-Self Reviews
- 360 reviews
- Performance ratings or calibration
- Compensation or talent matrix workflows
- Full 1-on-1 workspace
- Manager coaching / Kona-like features
- HR analytics suite
- HRIS provisioning
- RCDO admin/editing UI
- Multi-level leadership escalation/pass-up
- True live updates via WebSockets/SSE
- Full calendar or Teams replacement

## Users And Permissions

### Individual Contributor

ICs can:

- Create and edit their own draft weekly plan.
- Add weekly commitments.
- Link planned commitments to Supporting Outcomes.
- Add chess-layer metadata.
- Lock the weekly plan.
- Add explicit unplanned commitments after lock.
- Reconcile planned and unplanned work.
- Carry unfinished work forward.
- Respond to manager alignment disputes.

### Manager

Managers can:

- View direct reports only.
- See team-level plan status, review status, RCDO coverage, alignment risk, and reconciliation risk.
- Review locked plans without blocking IC reconciliation.
- Comment on commitments/plans.
- Mark commitments as `Needs Revision` or `Misaligned`.
- Resolve alignment disputes after IC revision or rationale.
- See review SLA and overdue states.

Managers cannot silently rewrite an IC's Supporting Outcome link in MVP.

### Platform/Admin

For MVP, RCDO data is read-only seeded reference data. There is no user-facing RCDO admin UI. Production architecture should preserve an integration boundary so seeded data can later be replaced by a PA strategy service, OKR platform, admin import, or CSV import.

## Product Scope

### IC Weekly Commit Workspace

The IC workspace is the source of truth for weekly plan data.

Required capabilities:

- View current weekly plan.
- Create, edit, and delete draft commitments.
- Select Supporting Outcome from the RCDO hierarchy.
- Search/browse Rally Cry -> Defining Objective -> Supporting Outcome hierarchy.
- Assign chess-layer metadata:
  - `priority`: P0 / P1 / P2
  - `workType`: Strategic / Maintenance / Blocker / Unplanned
  - `confidence`: High / Medium / Low
  - `alignmentStatus`: Aligned / Needs Review / Misaligned
  - `managerNote`: optional, manager-owned
- Lock weekly plan.
- View manager comments, flags, and alignment disputes.
- Add explicit unplanned commitments after lock.
- Reconcile work at week end.
- Mark outcomes as completed, partially completed, blocked, canceled, or carried forward.
- Create next-week carry-forward commitments linked to the original commitment.

### Manager Alignment Command Center

The command center is the main business-value surface.

Required capabilities:

- Show direct reports only.
- Show each direct report's weekly plan state.
- Show manager review state and overdue status.
- Show reconciliation completion and carry-forward risk.
- Show RCDO coverage heatmap.
- Show commitments needing review, revision, or alignment correction.
- Support manager comments and flags.
- Support alignment dispute workflow.
- Support dashboard filters by person, plan state, review state, Defining Objective, Supporting Outcome, priority, work type, and alignment status.

### RCDO Coverage Heatmap

Default heatmap grain:

- Columns: Defining Objectives.
- Rows: direct reports.
- Cells: count/status/risk summary for commitments mapped under that Defining Objective.
- Drill-down: Supporting Outcomes and linked commitments.

The heatmap should make under-covered outcomes, overloaded objectives, and misaligned work visible without requiring managers to open every plan.

### Manager Review SLA

Manager review is non-blocking but accountable.

- Review is due by end of the next business day after the IC locks a plan.
- Overdue review appears in the manager command center.
- IC reconciliation can proceed even if manager review is overdue.
- Overdue review status remains visible and auditable.

### Alignment Dispute Workflow

Managers need a structured way to challenge alignment without overwriting IC ownership.

Flow:

1. Manager marks a commitment `Needs Revision` or `Misaligned`.
2. Manager adds a required note explaining the concern.
3. IC updates the Supporting Outcome or adds rationale.
4. Manager marks the dispute resolved.

The audit trail should preserve manager flag, IC response, and resolution.

### Outlook Graph Integration

Outlook Graph API integration is a hard requirement. Its MVP purpose is calendar-based weekly planning and review touchpoints.

Required capabilities:

- Create or sync IC weekly planning events.
- Create or sync IC reconciliation events.
- Create or sync manager review-block events.
- Include lifecycle context and deep links to the weekly plan or manager command center.
- Store Graph event IDs for update/cancel.
- Show integration status.
- Retry or mark sync failures without blocking the core weekly lifecycle.

Failure policy:

- Outlook sync failures must not prevent plan lock, review, reconciliation, or carry-forward.
- Failures should be logged, surfaced to the affected user, and marked for retry or manual recovery.

Deferred:

- Full 1-on-1 agenda sync
- Broad calendar reading
- Teams integration
- Multi-event recurring calendar management beyond the weekly cadence

## Lifecycle

### Weekly Plan State

The plan lifecycle preserves the baseline required for planned-vs-actual reporting.

```text
DRAFT -> LOCKED -> RECONCILING -> RECONCILED
```

State semantics:

- **DRAFT:** IC can freely create/edit/delete planned commitments.
- **LOCKED:** IC submits weekly plan; planned baseline is frozen.
- **RECONCILING:** IC records actual outcomes against locked baseline and explicit unplanned work.
- **RECONCILED:** Week is closed and becomes historical truth.

Manager review is tracked separately from the plan lifecycle:

```text
NOT_REVIEWED -> REVIEWED
NOT_REVIEWED -> OVERDUE
OVERDUE -> REVIEWED
```

Manager review does not block reconciliation.

### Commitment Outcomes

Commitment reconciliation outcomes:

- Completed
- Partially completed
- Blocked
- Canceled
- Carried forward

Carry-forward is not a terminal state for the original commitment. It is an outcome that creates a linked commitment in the next weekly plan.

### Controlled Post-Lock Exceptions

After lock, users may not silently rewrite the planned baseline.

Allowed post-lock actions:

- Manager comments
- Manager alignment flags/disputes
- Progress/status updates
- Explicit unplanned commitment creation
- Reconciliation fields
- Audit-tracked unlock/amend action if implemented

Unplanned work after lock must be represented as explicit `Unplanned` commitments and must not rewrite the original baseline.

## Data And Domain Requirements

The architecture draft should model at least these domain concepts:

- User / Employee
- Manager relationship
- Weekly Plan
- Weekly Commitment
- RCDO hierarchy:
  - Rally Cry
  - Defining Objective
  - Supporting Outcome
- Manager Review
- Alignment Dispute
- Outlook Calendar Sync Record
- Audit metadata

Required invariants:

- A planned commitment cannot be locked without a Supporting Outcome.
- A manager can only access direct reports in MVP.
- A manager cannot access another manager's direct report by manipulating IDs.
- A locked planned commitment cannot be edited in a way that destroys the baseline.
- Unplanned post-lock work is labeled and distinguishable from the locked plan.
- A carry-forward commitment must link back to its source commitment.
- Outlook sync failure cannot roll back successful core lifecycle changes.

## Integration And Architecture Constraints

### Micro-Frontend

In production, WC is a Vite Module Federation remote loaded by the PA host app. The assessment build should run standalone but preserve production-compatible boundaries.

Requirements:

- Single route/module entry point.
- Shared dependencies declared for Module Federation.
- No hardcoded shell navigation.
- No duplicated PA host responsibilities.
- Document expected PA integration contract.

Assumption: PA owns shell-level navigation, global routing, Auth0/session context, production monitoring via LogRocket + Loki, and monorepo orchestration through Yarn Workspaces + Nx. The assessment does not need to replicate these PA concerns.

The assessment repo should still use a lightweight Yarn Workspaces + Nx monorepo shape to prove WC's module boundaries without recreating the full PA workspace.

### API And Backend

Backend requirements:

- Java 21
- Spring Boot 3.3
- PostgreSQL 16.4
- Hibernate/JPA with Spring Data
- Flyway migrations
- Spring Data Pageable for team views
- Entities extend `AbstractAuditingEntity`
- Lombok `@Getter`, `@Setter`, `@Builder`; do not use `@Data`
- Auth0 OAuth2 JWT boundary

Frontend requirements:

- TypeScript strict mode
- React 18
- Vite 5 with Module Federation
- Redux Toolkit with RTK Query for all API calls
- Flowbite React
- Tailwind CSS utility classes
- Vitest
- Cypress with Cucumber/Gherkin BDD syntax for primary E2E acceptance tests
- Playwright available for optional/ad hoc QA
- No CSS Modules or styled-components
- No Redux Saga or Thunk
- No SSR frameworks such as Next.js or Remix

### AWS Deployment Requirements

AWS is the required cloud platform for assessment delivery.

Required AWS services:

- Amazon EKS for the Spring Boot API.
- Amazon EKS for a separate Outlook sync worker.
- Amazon EKS Kubernetes CronJob for weekly plan shell generation.
- Amazon RDS for PostgreSQL 16.4.
- Amazon S3 for Vite frontend assets.
- Amazon CloudFront CDN for frontend delivery.
- Amazon SNS for lifecycle fanout.
- Amazon SQS for Outlook sync job delivery.
- SQS DLQ for repeatedly failed Outlook sync jobs.
- Amazon ECR for backend/worker container images.
- AWS Secrets Manager for deployed secrets.
- Route 53 and ACM for custom domains.

Deployment expectations:

- Frontend custom domain: `wc.<root-domain>`.
- API custom domain: `api.wc.<root-domain>`.
- Root domain and AWS region are deployment variables.
- CloudFront custom-domain ACM certificate must be in `us-east-1`.
- API/ALB certificate can be regional.
- Terraform is the infrastructure-as-code default.
- GitHub Actions runs quality gates and deploys frontend/backend/worker infrastructure artifacts.

### Code Quality Expectations

- JaCoCo 80% minimum backend coverage.
- Vitest unit tests for frontend components.
- Cypress E2E with Cucumber/Gherkin BDD syntax.
- ESLint 9 and Prettier 3.3 for frontend.
- Spotless and SpotBugs for backend.
- All entities extend `AbstractAuditingEntity`.
- Use Lombok `@Getter`, `@Setter`, and `@Builder`; do not use `@Data`.

## Real-Time Visibility

MVP real-time means:

- Fresh data on page load/refetch.
- RTK Query cache invalidation after mutations.
- Clear loading, empty, error, success, and partial states.

True live updates via WebSockets, SSE, or push notifications are future scope.

## Performance Requirements

- Plan retrieval API response time under 200ms for common paths.
- Lazy-loaded routes for sub-second initial render where feasible.
- Module Federation remote bundle optimized for CDN delivery.
- Manager team views support pagination with Spring Data Pageable.
- Team views should handle up to 2,000 records without loading all data into the browser at once.
- Heatmap aggregation must avoid N+1 queries and should be backed by indexed query paths.

## Security And Privacy Requirements

- Auth0 JWT is the authentication boundary.
- Authorization must enforce IC self-access and manager direct-report access.
- IDs in URLs/API requests must not allow direct object reference attacks.
- Alignment disputes and manager notes are sensitive review data and require audit logging.
- Outlook Graph tokens/secrets must not be hardcoded and must be rotatable.
- Graph failures must be visible but must not expose sensitive token details to users.
- User-entered text fields must handle empty strings, length limits, Unicode, and HTML/script injection attempts.

## Observability Requirements

Even though PA owns production LogRocket + Loki, WC should expose meaningful local/backend observability.

Required signals:

- Plan locked
- Plan review due/overdue/reviewed
- Plan entered reconciliation
- Plan reconciled
- Commitment carried forward
- Alignment dispute opened/resolved
- Outlook event sync succeeded/failed
- Authorization denial for cross-team access

Logs should include enough context to reconstruct a reported issue without exposing sensitive secrets.

Assessment observability should use Spring Actuator health/readiness endpoints plus CloudWatch logs/metrics. PA-owned LogRocket + Loki do not need to be replicated for the assessment.

## Success Metrics

- Strategic alignment visibility: percent of planned commitments linked to Supporting Outcomes. Target: 100% for locked plans.
- Weekly planning completion rate.
- Manager review turnaround time.
- Overdue manager review count.
- Reconciliation completion rate.
- Reconciliation accuracy: planned vs. completed/blocked/canceled/carried-forward.
- Carry-forward rate by person/team/outcome.
- Unplanned work rate after lock.
- Time-to-plan reduction vs. 15Five.

## Acceptance Criteria

### IC Workflow

- IC can create a weekly plan in draft.
- IC cannot lock a planned commitment without a Supporting Outcome.
- IC can lock a weekly plan.
- Locked planned commitments preserve baseline data.
- IC can add explicit unplanned work after lock.
- IC can reconcile completed, partially completed, blocked, canceled, and carried-forward work.
- Carry-forward creates a next-week linked commitment.

### Manager Workflow

- Manager sees only direct reports.
- Manager command center shows plan states, review states, overdue review status, reconciliation status, and carry-forward risk.
- Manager heatmap defaults to Defining Objective and drills into Supporting Outcomes.
- Manager can comment on plans/commitments.
- Manager can flag a commitment as Needs Revision or Misaligned with a note.
- IC can revise alignment or add rationale.
- Manager can resolve the dispute.

### Outlook Workflow

- IC planning and reconciliation events can be created/synced.
- Manager review-block event can be created/synced.
- Events include lifecycle context and deep links.
- Graph sync failure does not block core workflow.
- Graph sync failure is visible, logged, and recoverable.
- Outlook sync jobs use sync records, SNS/SQS delivery, and a separate worker.
- Real Graph integration can be enabled when credentials are configured; deterministic demo/failure fallback is allowed when credentials are absent.

### Micro-Frontend Workflow

- WC runs standalone for assessment/demo.
- WC is structured so it can be exposed as a Vite Module Federation remote.
- WC does not hardcode shell navigation or duplicate PA-owned monitoring/workspace setup.

## Test Plan

### Unit Tests

- Weekly plan lifecycle transitions.
- Required Supporting Outcome validation.
- Locked baseline edit restrictions.
- Unplanned post-lock commit policy.
- Carry-forward link creation.
- Manager review SLA/overdue calculation.
- Alignment dispute state changes.
- Outlook sync failure classification.

### Integration Tests

- Commit CRUD with RCDO links.
- RCDO hierarchy read APIs.
- Manager direct-report authorization.
- Unauthorized manager cross-team access denial.
- Heatmap aggregation.
- Reconciliation and carry-forward persistence.
- Outlook sync success and failure handling.

### E2E / BDD Tests

- IC drafts, locks, reconciles, and carries forward a weekly plan.
- Manager reviews, flags misalignment, IC revises, manager resolves.
- Manager sees RCDO heatmap and overdue review signals.
- IC adds unplanned work after lock and reconciles it.
- Outlook warning path appears when calendar sync fails.
- Manager cannot access another manager's direct report.

### Performance Tests

- Plan retrieval meets 200ms target on common path.
- Manager team view paginates.
- Heatmap aggregation avoids loading all records client-side.
- Synthetic seed path supports up to 2,000 records for manager-view performance checks.

### Quality Gate Tests

- Backend CI enforces JaCoCo 80%, Spotless, and SpotBugs.
- Frontend CI enforces Vitest, ESLint 9, and Prettier 3.3.
- Cypress/Cucumber BDD suite runs against local CI/test services.
- Deployed smoke suite runs against the deployed custom-domain environment.

## Required Deliverables

- Source code.
- Technical documentation.
- Deployed frontend and backend on AWS custom domains.
- Demo video.
- Test results.
- AI usage log, delivered as Markdown if no evaluator template is provided.

## Open Questions For Architecture Draft

- What exact PM remote pattern should WC follow?
- Which Auth0 claim-name defaults should the configurable mapper use?
- What root domain and AWS region should be used for deployment?
