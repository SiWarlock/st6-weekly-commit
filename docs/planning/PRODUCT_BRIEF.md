# Product Brief

> Status: Draft planning artifact
> Phase: 0 - PRD intake and planning-mode selection
> Date: 2026-06-02
> Sources: `PRD.md`, `docs/planning/OFFICE_HOURS_PRD_REFRAME.md`, `.gstack/projects/ST6/ceo-plans/2026-06-02-weekly-commit-module.md`, `docs/15five-research.md`

## Product In One Sentence

The ST6 Weekly Commit module is a strategy-enforced weekly alignment system where ICs create weekly commitments that must map to RCDO Supporting Outcomes, and direct managers monitor review status, alignment risk, reconciliation progress, and strategic coverage before weekly drift becomes retrospective surprise.

Classification: locked decision.

## Product Is

- An IC Weekly Commit Workspace for drafting, locking, reconciling, and carrying forward weekly commitments.
- A Manager Alignment Command Center for direct-report visibility into plan lifecycle, manager review status, RCDO coverage, alignment disputes, and reconciliation risk.
- A replacement for the weekly Check-in / Priorities / Objectives-alignment slice of 15Five.
- A production-shaped MVP that runs standalone for assessment while preserving production-compatible Vite Module Federation boundaries for PA integration.
- A read-only consumer of seeded RCDO reference data for MVP.
- An Outlook Graph-integrated workflow for planning, reconciliation, and manager review-block calendar touchpoints.

Classification: locked decision.

## Product Is Not

- A full 15Five replacement.
- A performance-management, HR analytics, compensation, coaching, engagement-survey, High Fives, or full 1-on-1 product.
- An RCDO administration system.
- A multi-level leadership escalation/pass-up system.
- A true live-collaboration product using WebSockets, SSE, or push updates in MVP.
- A full calendar, Teams, HRIS, or PA shell replacement.

Classification: locked decision.

## Primary Problem

Weekly planning currently exists without a required structural connection to strategic execution. ICs can submit weekly plans whose work does not reliably map to Rally Cry, Defining Objective, and Supporting Outcome data, while managers lack a fast way to see alignment drift, unreviewed plans, reconciliation risk, and overloaded or under-covered objectives during the week.

Classification: locked decision, with demand evidence caveat.

## Demand Evidence Caveat

Demand is prompt-derived. The architecture should not pretend user interviews, incident data, or manager quotes exist. Unknown internal workflow details must remain tagged as assumptions or open questions until validated.

Classification: locked decision.

## Primary Users

- Individual contributors create the weekly source-of-truth data.
- Direct managers consume the team roll-up and run the review/dispute loop.

The MVP is IC-first for data creation but manager-equal for business value.

Classification: locked decision.

## Core Workflow

1. IC opens the current weekly plan.
2. IC creates draft commitments.
3. IC links each planned commitment to one Supporting Outcome in the RCDO hierarchy.
4. IC adds chess-layer metadata: priority, work type, confidence, alignment status, and manager note where manager-owned.
5. IC locks the plan; the planned baseline becomes immutable except for explicit post-lock exception paths.
6. Outlook sync creates or updates the weekly planning touchpoint; failures are visible but non-blocking.
7. Manager reviews locked direct-report plans, sees overdue review signals, comments, and flags alignment concerns.
8. If flagged, IC revises the Supporting Outcome or adds rationale; manager resolves the dispute.
9. IC enters reconciliation, records outcomes, and adds explicit unplanned work rather than rewriting the baseline.
10. IC reconciles the plan; carry-forward creates a linked next-week commitment.
11. Outlook sync creates or updates reconciliation and manager review-block events, with deep links back into WC.

Classification: locked decision.

## Explicit PRD Requirements

- Every planned weekly commitment must map to a Supporting Outcome before lock.
- Weekly plan lifecycle is `DRAFT -> LOCKED -> RECONCILING -> RECONCILED`.
- Manager review lifecycle is separate and non-blocking: `NOT_REVIEWED -> REVIEWED`, `NOT_REVIEWED -> OVERDUE`, `OVERDUE -> REVIEWED`.
- Locked planned baseline must be preserved for planned-vs-actual reconciliation.
- Unplanned post-lock work must become explicit `Unplanned` commitments and must not rewrite the locked baseline.
- Carry-forward is an outcome that creates a linked next-week commitment.
- Manager dashboard scope is direct reports only.
- Managers can comment, flag `Needs Revision` or `Misaligned`, and resolve disputes.
- Managers cannot silently rewrite IC Supporting Outcome links in MVP.
- RCDO coverage heatmap defaults to Defining Objective columns and direct-report rows, with Supporting Outcome drill-down.
- Manager review is due by end of the next business day after plan lock.
- Outlook Graph integration must create or sync IC planning, IC reconciliation, and manager review-block events.
- Outlook events must include lifecycle context and deep links.
- Outlook failures must not block lock, review, reconciliation, or carry-forward.
- WC must run standalone for assessment while preserving production-compatible Vite Module Federation remote boundaries.
- Backend stack: Java 21, Spring Boot 3.3, PostgreSQL 16.4, Hibernate/JPA, Flyway, Spring Data Pageable, Auth0 OAuth2 JWT.
- Frontend stack: TypeScript strict mode, React 18, Vite 5, Module Federation, Redux Toolkit with RTK Query, Flowbite React, Tailwind CSS.
- Forbidden frontend choices include CSS Modules, styled-components, Saga/Thunk, Next.js, and Remix.

Classification: locked decision.

## Implied Requirements

- The backend must own lifecycle invariants, not rely on client-side validation alone.
- Authorization must be enforced on every plan, commitment, review, dispute, and heatmap path.
- The data model needs immutable baseline fields or versioned snapshot semantics for locked planned commitments.
- Heatmap and manager dashboard APIs need aggregation-first query paths to avoid client-side full loads and N+1 queries.
- Calendar sync needs idempotency keys or stored Graph event IDs so retries do not create duplicate events.
- Graph integration must have a hybrid real/demo adapter fallback for missing or invalid credentials.
- Manager review SLA uses weekdays only in the organization timezone; holiday calendars are deferred.
- Audit metadata must preserve sensitive review/dispute actions through `AbstractAuditingEntity` and `audit_event`.
- Seed data must include one manager with 5-8 direct reports, weekly plans, RCDO hierarchy, disputes, overdue states, carry-forward, and Graph failure states for demo.
- A separate synthetic seed path should support 2,000-record manager-view performance tests.
- Frontend route/data boundaries must be compatible with both standalone mode and PA-hosted remote mode.
- AWS deployment boundaries must include EKS, RDS PostgreSQL 16.4, S3/CloudFront, SNS/SQS, Route 53/ACM, ECR, Secrets Manager, and CloudWatch.

Classification: proposed recommendation.

## External Dependencies

- PA host application for production shell concerns.
- Auth0 for JWT authentication boundary.
- Microsoft Outlook Graph API for calendar touchpoints.
- AWS SNS/SQS for Outlook sync job fanout and delivery.
- AWS EKS for API, sync worker, and weekly generation CronJob.
- AWS RDS PostgreSQL 16.4 for persistent state.
- AWS S3 and CloudFront for Vite frontend delivery.
- AWS Route 53 and ACM for custom domains.
- AWS Secrets Manager for deployed secrets.
- RCDO source of truth in production, currently unspecified.
- Vite Module Federation host/remote pattern. No PM remote example is present in this workspace, so architecture uses a generic Vite remote contract and marks exact PM parity as a verification item.
- PA-owned observability and workspace infrastructure in production, including LogRocket, Loki, Yarn Workspaces, and Nx.

Classification: mixed; PRD-mandated dependencies are locked, production RCDO and exact PM remote details are open questions.

## Ambiguous Terms And Unknowns

- Exact PM remote pattern WC should follow.
- Exact Auth0 claim-name defaults for employee identity, roles, team, and manager relationship lookup.
- Production RCDO source and ownership model.
- Exact root domain and AWS region for deployment.

Classification: open question.

## Initial Technical Risks

- Direct-report authorization defects could create IDOR exposure for sensitive plans, notes, and disputes.
- State machine shortcuts could allow locked baseline mutation and break reconciliation truth.
- Outlook Graph retries could duplicate events if sync records are not modeled carefully.
- Heatmap aggregation could miss the 200ms target if implemented as per-user loops or client-side aggregation.
- Module Federation boundaries could be overfit to standalone demo and become hard to host in PA.
- Business-day SLA logic could become overbuilt or wrong without a clear time zone/holiday rule.

Classification: risk.

## Initial Product Risks

- The manager command center could become a passive report instead of an actionable review surface.
- The IC workflow could become too heavy if RCDO selection and metadata entry are not ergonomic.
- The product could drift into a shallow 15Five clone if exclusions are not enforced.
- Prompt-derived demand means demo success depends on making the strategic alignment value obvious without invented customer evidence.

Classification: risk.

## Initial Demo And Evaluation Risks

- Outlook Graph may be hard to configure live, so the demo needs visible success and failure states.
- RCDO seed data must be realistic enough for heatmap/drill-down behavior to make sense.
- Reviewer may test unauthorized access by manipulating IDs.
- Reviewer may look for proof that the locked baseline cannot be silently edited.
- The one-week timebox rewards a narrow, production-shaped slice over broad feature coverage.

Classification: risk.

## Recommended Planning Mode

Expanded mode is selected.

Rationale:

- The product has enterprise/team-heavy surfaces: ICs, managers, PA host, Auth0, Outlook Graph, and sensitive review data.
- The architecture needs explicit users, stakeholders, user flows, domain model, data model, threat model, risks, and decisions.
- The MVP is timeboxed, but the failure modes are mostly boundary and state-machine failures, not UI polish failures.
- Claude Code will need a build-ready handoff with stable anchors and little hidden chat context.

Classification: locked decision, confirmed by user on 2026-06-02.

## Phase 1 - Product Mechanics

### Core Object Of Value

The Weekly Commitment is the atomic unit of work, alignment, dispute, carry-forward, unplanned-work tracking, and heatmap aggregation.

The Weekly Plan is the lifecycle container. It groups a user's commitments for a week, owns the plan state, preserves the locked baseline, and provides the reconciliation boundary.

Classification: locked decision, confirmed by user on 2026-06-02.

### State-Changing Actions

- IC creates, edits, and deletes draft commitments.
- IC links each planned commitment to a Supporting Outcome.
- IC assigns chess-layer metadata.
- IC locks the weekly plan.
- Outlook sync creates or updates planning events after core lifecycle mutations.
- Manager reviews a locked plan.
- Manager comments on a plan or commitment.
- Manager flags a commitment as `Needs Revision` or `Misaligned`.
- IC revises alignment or adds rationale.
- Manager resolves an alignment dispute.
- IC adds explicit unplanned commitments after lock.
- IC enters reconciliation.
- IC records commitment outcomes.
- IC carries unfinished work forward into next week's plan.
- Outlook sync creates or updates reconciliation and manager review-block events.

Classification: proposed recommendation.

### Lifecycle

Plan lifecycle:

```text
DRAFT -> LOCKED -> RECONCILING -> RECONCILED
```

Manager review lifecycle:

```text
NOT_REVIEWED -> REVIEWED
NOT_REVIEWED -> OVERDUE
OVERDUE -> REVIEWED
```

Commitment outcome lifecycle during reconciliation:

```text
Pending outcome -> Completed | Partially completed | Blocked | Canceled | Carried forward
```

Carry-forward is an outcome on the source commitment and a creation event for a linked next-week commitment.

Classification: locked decision from PRD and CEO review.

### Units And Records

- Unit of work: Weekly Commitment.
- Unit of plan lifecycle: Weekly Plan.
- Unit of strategic linkage: Supporting Outcome.
- Unit of manager accountability: Manager Review.
- Unit of alignment correction: Alignment Dispute.
- Unit of calendar integration: Outlook Calendar Sync Record.
- Unit of auditability: audit metadata and/or domain audit event.

Classification: proposed recommendation.

### Hidden Mechanics

- Locking a plan must freeze the planned baseline.
- Unplanned work after lock is additive, not a rewrite of planned work.
- Heatmap counts and risk indicators derive from commitments, not manually maintained dashboard rows.
- Outlook sync is downstream of core state changes and must be retryable.
- Manager review status is separate from plan state and cannot block IC reconciliation.

Classification: locked decision / proposed recommendation mix.

### Manager Review Timing

Formal manager review starts only after an IC locks the weekly plan. Managers may see draft status in the command center, but review status, review SLA timing, `Needs Revision`, `Misaligned`, and dispute actions apply only to locked plans.

Rationale:

- Lock is the IC's submission/accountability moment.
- Reviewing drafts risks challenging work the IC has not submitted yet.
- The review SLA needs a single, auditable start event.
- The locked baseline remains the boundary between planning and review.

Classification: locked decision, confirmed by user on 2026-06-02.

### Post-Lock Amendments

MVP does not include an explicit unlock/amend flow.

Locked planned commitments remain immutable for baseline-preservation purposes. Corrections after lock happen through explicit unplanned commitments, reconciliation outcomes, cancellation, manager comments, dispute/rationale, or carry-forward. A formal audit-tracked amendment workflow is deferred.

Rationale:

- The MVP's planned-vs-actual signal depends on a trustworthy locked baseline.
- A general amendment flow would require versioning, approvals, and clearer audit semantics.
- The PRD already defines safer post-lock paths for unplanned work, disputes, and reconciliation.

Classification: locked decision, confirmed by user on 2026-06-02.
