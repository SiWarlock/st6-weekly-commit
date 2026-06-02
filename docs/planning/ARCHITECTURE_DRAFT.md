# ST6 Weekly Commit Module Architecture Draft

> Status: Rough architecture draft for adversarial finalization.
> Audience: Project owner, technical reviewers, Claude Code `/arch-finalize`, and future implementation sessions.
> Primary implementation constraint: one-week production-shaped assessment build.
> Companion docs: `PRODUCT_BRIEF.md`, `USERS.md`, `STAKEHOLDERS.md`, `USER_FLOWS.md`, `DOMAIN_MODEL.md`, `REQUIREMENTS.md`, `CONSTRAINTS.md`, `EVALUATION_CRITERIA.md`, `ASSUMPTIONS.md`, `OPEN_QUESTIONS.md`, `RESEARCH.md`, `DECISIONS.md`, `DATA_MODEL.md`, `RISKS.md`, `THREAT_MODEL.md`, `DIAGRAM_PLAN.md`, `CLAUDE_CODE_HANDOFF.md`.
> Build contract: Claude Code should treat this as a rough draft, perform a second-pass gap audit, finalize root `ARCHITECTURE.md`, and only then generate `MVP_TASKS.md`.

## §1 - Executive Summary

ST6 Weekly Commit (`WC`) is a strategy-enforced weekly alignment module. ICs create weekly commitments that must map to RCDO Supporting Outcomes; managers use a direct-report command center to see plan status, review accountability, alignment risk, RCDO coverage, reconciliation risk, and disputes before the week drifts.

The architecture is a production-shaped AWS micro-frontend system: React/Vite Module Federation remote served from S3/CloudFront, Spring Boot API and Outlook sync worker on EKS, RDS PostgreSQL 16.4, SNS/SQS/DLQ for non-blocking Outlook sync, and Terraform/GitHub Actions for deploy. The MVP uses hybrid Auth0 and Microsoft Graph adapters so real credentials can be enabled, while seeded persona/demo Graph modes keep assessment delivery reliable.

The most important correctness constraints are server-side Supporting Outcome enforcement, direct-report authorization, locked baseline immutability, sync-record idempotency, and synchronous manager read models.

## §2 - Goals And Non-Goals

Goals:

- Enforce Supporting Outcome links for locked planned commitments.
- Preserve planned-vs-actual baseline truth after lock.
- Provide IC draft/lock/reconcile/carry-forward workflows.
- Provide manager direct-report command center with heatmap, review SLA, disputes, and reconciliation risk.
- Integrate Outlook Graph through non-blocking sync records, SNS/SQS, and a worker.
- Deploy frontend/backend/worker on AWS custom domains.

Non-goals:

- Full 15Five replacement.
- Engagement surveys, High Fives, full reviews, 1-on-1 workspace, HR analytics, compensation, HRIS provisioning.
- RCDO admin UI.
- Pass-up/escalation or leadership rollups.
- True live updates through WebSockets/SSE.
- Unlock/amend workflow after lock.
- Holiday-aware SLA calendar.
- SQS DLQ admin redrive UI.

## §3 - Locked Decisions

The architecture baseline is `DECISIONS.md`.

Load-bearing choices:

- Weekly Commitment is the atomic unit; Weekly Plan is the lifecycle container.
- Plan lifecycle is `DRAFT -> LOCKED -> RECONCILING -> RECONCILED`.
- Review statuses are `NOT_REVIEWED`, `REVIEWED_WITH_DISPUTES`, `REVIEWED`; overdue is derived.
- Direct managers can view drafts but formal review/dispute starts only after lock.
- Planned baseline fields are immutable after lock.
- Manager command center uses `manager_plan_summary` and `manager_heatmap_cell` projections.
- REST JSON APIs use resource endpoints plus explicit command endpoints.
- Domain authorization service owns self/direct-report checks.
- AWS target is EKS/RDS/S3/CloudFront/SNS/SQS/Route 53/ACM.
- Cypress/Cucumber is the primary E2E suite.

## §4 - System Overview

Runtime components:

- `wc-web`: React 18, Vite 5, RTK Query, Flowbite React, Tailwind, Module Federation remote.
- `wc-api`: Spring Boot 3.3 REST API, domain services, authorization, projections, SNS publisher.
- `wc-sync-worker`: Spring Boot worker consuming SQS Outlook sync jobs.
- `wc-e2e`: Cypress/Cucumber BDD suite.
- PostgreSQL: domain state, projections, sync records, audit events.
- AWS: EKS, RDS, ECR, SNS, SQS/DLQ, S3, CloudFront, Route 53, ACM, Secrets Manager, CloudWatch.

End-to-end flow:

1. EKS CronJob generates weekly draft plan shells.
2. IC edits commitments and locks the plan.
3. API validates Supporting Outcomes and freezes planned baseline.
4. API creates review due date, audit event, projections, and Outlook sync record.
5. API publishes lifecycle event to SNS; SQS delivers sync job to worker.
6. Worker calls Graph or demo adapter and updates sync record.
7. Manager reviews direct reports, opens disputes, and uses command-center projections.
8. IC reconciles outcomes, unplanned work, and carry-forward.

## §5 - Domain Model

See `DOMAIN_MODEL.md` and `DATA_MODEL.md`.

Core source entities:

- Employee
- Manager Relationship
- Weekly Plan
- Weekly Commitment
- Rally Cry
- Defining Objective
- Supporting Outcome
- Manager Review
- Alignment Dispute
- Comment
- Outlook Calendar Sync Record
- Audit Event

Projection entities:

- Manager Plan Summary
- Manager Heatmap Cell

Key invariants:

- Employee/week has at most one Weekly Plan.
- Plan cannot lock with zero planned commitments.
- Planned commitments cannot lock without Supporting Outcomes.
- Locked planned fields cannot change.
- Unplanned commitments must link to Supporting Outcomes before reconciliation close.
- Manager formal review requires locked plan.
- A commitment can have at most one unresolved dispute.
- Outlook failure cannot roll back core lifecycle mutations.

## §6 - Physical Data Model

Data model is near-Flyway-level in `DATA_MODEL.md`.

Physical conventions:

- UUID primary keys.
- `VARCHAR` status columns with check constraints.
- JPA entities extend `AbstractAuditingEntity`.
- `audit_event` table records sensitive lifecycle/review/integration/security actions.
- `weekly_commitment` models planned and unplanned commitments in one table.
- `comment` supports full nested materialized-path threads.
- `outlook_calendar_sync_record` is both status record and durable outbox.
- `manager_plan_summary` and `manager_heatmap_cell` are synchronous projections.

Important indexes:

- `weekly_plan(employee_id, week_start_date)` unique.
- manager relationship lookup by manager and direct report.
- commitment lookup by plan, Supporting Outcome, alignment status.
- unresolved dispute partial unique index by commitment.
- sync record unique owner/related/event kind.
- projection uniqueness by manager/report/week/objective grain.

## §7 - REST API Contracts

API style:

- Spring MVC REST JSON.
- Resource endpoints for plan/commitment/review/dispute/comment/sync resources.
- Explicit command endpoints for lifecycle mutations.
- DTOs, not entities, cross the API boundary.
- Spring Data Pageable for manager list views.
- Endpoint handlers should return allowed actions for the current actor so the UI can disable actions without becoming the authorization source.
- Error responses should use a consistent problem-details shape with safe messages, state constraint names, and trace IDs.

Representative endpoints:

- `GET /api/me`
- `GET /api/rcdo`
- `GET /api/plans/current`
- `GET /api/plans/{planId}`
- `POST /api/plans/{planId}/commitments`
- `PATCH /api/commitments/{commitmentId}`
- `DELETE /api/commitments/{commitmentId}`
- `POST /api/plans/{planId}/lock`
- `POST /api/plans/{planId}/start-reconciliation`
- `POST /api/plans/{planId}/close-reconciliation`
- `POST /api/plans/{planId}/unplanned-commitments`
- `POST /api/commitments/{commitmentId}/carry-forward`
- `GET /api/manager/command-center?weekStart=&page=&size=`
- `GET /api/manager/heatmap?weekStart=&definingObjectiveId=&supportingOutcomeId=`
- `GET /api/manager/heatmap/{cellId}/drilldown`
- `POST /api/manager/reviews/{reviewId}/mark-reviewed`
- `POST /api/commitments/{commitmentId}/disputes`
- `POST /api/disputes/{disputeId}/respond`
- `POST /api/disputes/{disputeId}/resolve`
- `GET /api/comments?targetType=&targetId=`
- `POST /api/comments`
- `POST /api/outlook-sync/{syncRecordId}/retry`
- `GET /actuator/health/readiness`

DTO fields should include IDs, display labels, state, timestamps, RCDO breadcrumbs, risk badges, and allowed actions for the current actor.

State constraints:

- `POST /lock` requires `DRAFT`, at least one planned commitment, and all planned commitments linked to Supporting Outcomes.
- `PATCH /commitments/{id}` rejects locked planned baseline field changes.
- `POST /start-reconciliation` requires `LOCKED`.
- `POST /close-reconciliation` requires `RECONCILING`, reconciliation outcomes, and Supporting Outcome links for all unplanned commitments.
- Manager review, dispute, and manager comment mutations require locked direct-report plans.
- Manager command-center and heatmap endpoints return only direct reports of the authenticated manager.

## §8 - Authorization Architecture

Authentication:

- Real mode: Spring Security OAuth2 Resource Server validates Auth0 JWT issuer/audience.
- Demo mode: backend accepts demo identity header only when `DEMO_AUTH_ENABLED=true`.
- Claim mapping is configurable.

Authorization:

- Central domain authorization service owns resource checks.
- Controller annotations are coarse authentication/role gates only.
- Service methods check owner/self access and manager direct-report access before repository mutation/read.
- Projection endpoints scope by manager's direct-report relationship before returning rows.

Required denial cases:

- IC reads/mutates another IC plan.
- Manager reads non-direct-report plan.
- Manager opens/resolves dispute for non-direct report.
- IC resolves a dispute.
- IC accesses team heatmap.
- Demo header used when demo mode disabled.

## §9 - Frontend Architecture

`apps/wc-web`:

- React 18, Vite 5, TypeScript strict.
- Flowbite React and Tailwind utilities.
- RTK Query for all API calls.
- No Saga/Thunk, no CSS Modules/styled-components, no SSR framework.
- Module Federation remote exposing one route/module entry and `remoteEntry.js`.
- Shared singleton dependencies: React, React DOM, Redux Toolkit, React Redux where applicable.

Routes/views:

- `/` routes to persona-aware default workspace.
- `/weekly-commit` IC current plan workspace.
- `/weekly-commit/history/:planId` plan detail/history.
- `/manager/command-center` manager dashboard.
- `/manager/heatmap` heatmap/drill-down.

RTK Query:

- API slices/tags for current user, RCDO, plans, commitments, manager summary, heatmap, disputes, comments, sync records.
- Mutations invalidate affected plan, manager summary, heatmap, and sync tags.

Demo persona mode:

- Frontend persona switcher visible only in demo mode.
- Persona switcher sends demo identity header to API.

## §10 - Backend Service Architecture

`apps/wc-api` modules:

- Identity/auth adapter.
- Domain authorization service.
- Weekly plan service.
- Commitment service.
- RCDO read service.
- Manager review service.
- Dispute service.
- Comment service.
- Projection service.
- Outlook sync record service.
- SNS publisher.
- Audit event service.

Service boundaries:

- Lifecycle transitions happen in service methods, not controller patches.
- Service methods validate state, authorization, and immutable fields.
- Projection updates happen synchronously inside affected service transactions.
- Audit events are written for sensitive and lifecycle actions.

Weekly plan generation:

- EKS CronJob invokes generation job/API.
- DB unique constraint on employee/week provides final idempotency guard.

## §11 - Manager Projections

Projection tables:

- `manager_plan_summary`
- `manager_heatmap_cell`

Update triggers:

- Plan generated/locked/reconciling/reconciled.
- Commitment create/update/delete.
- Dispute open/respond/resolve.
- Manager review status changes.
- Carry-forward creation.

Projection repair:

- Internal job/CLI can rebuild projection tables from source tables.
- No admin UI in MVP.

Heatmap semantics:

- Rows: direct reports.
- Columns: Defining Objectives.
- Cells: counts plus explicit risk badges.
- Drill-down: Supporting Outcomes and commitments.

## §12 - Outlook Sync Architecture

Sync triggers:

- IC plan lock -> IC planning event.
- IC starts reconciliation -> IC reconciliation event.
- Manager review-block needed -> one manager review-block event per manager/week.

Sync record states:

- `PENDING_PUBLISH`
- `QUEUED`
- `SYNCING`
- `SYNCED`
- `FAILED`
- `RETRY_REQUESTED`

Flow:

1. API core mutation succeeds.
2. API writes/updates `outlook_calendar_sync_record`.
3. API publishes pointer event to SNS.
4. SQS subscription delivers job.
5. Worker loads sync record and related data from DB.
6. Worker calls real Graph adapter or demo/failure adapter.
7. Worker stores Graph event ID or safe failure state.
8. UI displays sync status and manual retry.

Queue payload:

- `syncRecordId`
- `eventKind`
- tenant/env metadata
- trace ID

Do not put full calendar payloads or secrets in SNS/SQS messages.

Graph adapter modes:

- Real mode uses Microsoft Graph app-only credentials with tenant/admin consent.
- Demo success mode records deterministic successful sync results without external credentials.
- Demo failure mode records deterministic safe failures for warning/retry demos.

Idempotency:

- Sync records are unique by owner, related object, and event kind.
- Worker loads authoritative payload data from PostgreSQL before calling Graph.
- Existing `graphEventId` is used to update the calendar event rather than create duplicates where possible.
- Manual retry changes sync state from `FAILED` to `RETRY_REQUESTED` and republishes/requeues from the same sync record.

## §13 - Comments And Collaboration

Comment model:

- Full nested threads.
- Materialized path with parent comment and depth.
- Comments target plan, commitment, review, or dispute.
- Authorization inherits target visibility.

MVP UI should render nested comments clearly but avoid moderation, notifications, mentions, or rich markdown scope unless time allows.

## §14 - AWS Deployment Architecture

Default region:

- `us-east-1`.
- Override by Terraform variable if needed.

Domains:

- `ROOT_DOMAIN` is required.
- Frontend: `wc.${ROOT_DOMAIN}`.
- API: `api.wc.${ROOT_DOMAIN}`.

Infrastructure:

- Terraform provisions EKS managed node group, RDS PostgreSQL 16.4, ECR, SNS, SQS/DLQ, S3 private bucket, CloudFront OAC, Route 53 records, ACM certificates, Secrets Manager, IAM.
- CloudFront certificate must be in `us-east-1`.
- API ingress uses AWS Load Balancer Controller / ALB.
- API and worker are separate container images.
- Worker and CronJob use Secrets Manager-injected config.

CI/CD:

- GitHub Actions runs quality gates.
- Build/push API and worker images to ECR.
- Deploy EKS manifests.
- Build Vite frontend and sync to S3.
- Invalidate CloudFront as needed.
- Run deployed smoke tests.

## §15 - Local Development And Test Runtime

Local:

- Docker Compose for PostgreSQL and full-stack local services.
- Demo Auth0 and Graph adapters enabled by local env.
- Frontend/API/worker can run locally against Compose database.

Backend integration:

- Testcontainers PostgreSQL for Java integration tests.
- No H2 fallback for persistence behavior.

E2E:

- Cypress/Cucumber BDD suite against local full-stack services in CI.
- Smaller smoke suite against deployed custom domains.

## §16 - Testing And Quality Gates

Backend:

- Unit tests for lifecycle, SLA, baseline immutability, disputes, sync state.
- Integration tests for JPA/Flyway, authorization, projections, sync records.
- JaCoCo minimum 80%.
- Spotless and SpotBugs.

Frontend:

- Vitest component/unit tests.
- ESLint 9 and Prettier 3.3.
- RTK Query mutation/cache invalidation tests where practical.

E2E:

- Cypress/Cucumber feature files:
  - IC draft/lock/reconcile/carry-forward.
  - Manager review/dispute/resolve.
  - Heatmap drill-down.
  - Outlook warning/retry path.
  - Unauthorized manager denial.

Performance:

- Plan retrieval under 200ms common path.
- Manager pagination.
- Heatmap/projection query checks using synthetic 2,000-record dataset.

## §17 - Observability And Audit

Observability:

- Spring Actuator health/readiness for API and worker.
- Kubernetes probes use Actuator endpoints.
- CloudWatch logs/metrics for API, worker, CronJob.
- No LogRocket/Loki replication; PA owns those in production.

Audit:

- `audit_event` records plan lock, reconciliation start/close, carry-forward, review, dispute open/respond/resolve, sync success/failure, and authorization denial.
- Audit metadata should avoid secrets and sensitive token details.

## §18 - Security And Threat Model

See `THREAT_MODEL.md`.

Critical controls:

- Auth0 issuer/audience validation in real mode.
- Env-gated demo persona mode.
- Service-layer domain authorization.
- Safe Graph failure messages.
- Secrets Manager for deployed secrets.
- Pointer-only queue messages.
- Server-side validation for HTML/script text and length limits.

## §19 - Alternatives Considered

- Live heatmap aggregation: rejected in favor of read-model table for production-level dashboard speed.
- Materialized view: rejected because refresh semantics add complexity.
- Spring `@Scheduled`: rejected in favor of EKS CronJob to avoid multi-replica scheduler issues.
- Per-user Graph delegated auth: rejected to avoid Outlook connect flow.
- In-process Graph sync: rejected because original PRD requires SQS/SNS and non-blocking integration.
- Generated CloudFront URL only: rejected after user chose custom domain.
- One-level comments: rejected after user chose full nested comments.

## §20 - MVP Boundaries And Deferred Work

Deferred:

- Full 15Five suite features.
- Pass-up/escalation.
- RCDO admin.
- Holiday calendars.
- User-local weeks.
- Unlock/amend flow.
- True live updates.
- Per-report manager calendar events.
- SQS DLQ admin redrive UI.
- Full PA LogRocket/Loki replication.

Trim first:

- Graph polish and review-block event automation.
- Nonessential UI filters/polish.
- Projection rebuild niceties.
- Deep nested-comment UI rendering, while preserving schema.

## §21 - Repo Scaffold

Expected layout:

```text
apps/
  wc-web/
  wc-api/
  wc-sync-worker/
  wc-e2e/
infra/
  terraform/
  k8s/
docs/
  planning/
AI_USAGE.md
```

Build systems:

- Yarn Workspaces + Nx at repo level.
- Gradle multi-module for Java.
- Terraform for AWS.
- GitHub Actions for CI/CD.

## §22 - Spec Anchor Index

| Anchor | Topic |
|---|---|
| §1 | Executive summary |
| §2 | Goals/non-goals |
| §3 | Locked decisions |
| §4 | System overview |
| §5 | Domain model |
| §6 | Physical data model |
| §7 | REST API |
| §8 | Authorization |
| §9 | Frontend |
| §10 | Backend services |
| §11 | Manager projections |
| §12 | Outlook sync |
| §13 | Comments |
| §14 | AWS deployment |
| §15 | Local/test runtime |
| §16 | Testing/quality gates |
| §17 | Observability/audit |
| §18 | Security/threat model |
| §19 | Alternatives |
| §20 | Deferred work |
| §21 | Repo scaffold |

## §23 - Claude Code Review Instructions

Claude Code should:

1. Read every `docs/planning/*` artifact and `PRD.md`.
2. Perform a gap audit before implementation.
3. Finalize root `ARCHITECTURE.md` using the project template.
4. Preserve locked decisions unless it finds a critical contradiction.
5. Ask the human before changing load-bearing decisions.
6. Generate `MVP_TASKS.md` only after final architecture is accepted.
