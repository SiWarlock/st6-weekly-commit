# Constraints

> Status: Draft planning artifact
> Phase: 7 - Constraints, evaluation, and timebox
> Date: 2026-06-02
> Sources: `PRD.md`, original PRD pasted by user, `PRODUCT_BRIEF.md`, `USERS.md`, `USER_FLOWS.md`, `DOMAIN_MODEL.md`, `REQUIREMENTS.md`

## Timebox And Build Context

- One-week build, accelerated by Codex and Claude Code.
- Architecture must optimize for buildability, testability, stack fidelity, and clear handoff.
- The MVP must feel production-shaped even where seeded/demo adapters stand in for enterprise integrations.

Classification: locked decision.

## Required Deliverables

- Source code.
- Technical documentation.
- Deployed frontend and backend.
- Custom-domain deployment.
- Demo video.
- Test results.
- AI usage log as `AI_USAGE.md`.

Classification: locked requirement from PRD.

## Required Backend Stack

- Java 21.
- Spring Boot 3.3.
- PostgreSQL 16.4.
- Hibernate/JPA with Spring Data.
- Flyway migrations.
- Spring Data Pageable for team views.
- Entities extend `AbstractAuditingEntity`.
- Lombok `@Getter`, `@Setter`, `@Builder`.
- Do not use Lombok `@Data`.
- Auth0 OAuth2 JWT boundary.

Classification: locked requirement from PRD.

## Required Frontend Stack

- TypeScript strict mode.
- React 18.
- Vite 5.
- Vite Module Federation remote shape for production.
- Redux Toolkit with RTK Query for all API calls.
- Flowbite React.
- Tailwind CSS utility classes.
- Vitest.
- Cypress with Cucumber/Gherkin BDD syntax as the primary E2E suite.
- Playwright available for optional/ad hoc QA, not the primary acceptance suite.

Classification: locked requirement from PRD.

## Required Repository And Quality Tooling

- Lightweight Yarn Workspaces + Nx monorepo for the assessment.
- Do not replicate the full PA workspace; prove the WC module shape and build orchestration only.
- JaCoCo minimum 80% backend coverage.
- Vitest unit tests for frontend components.
- Cypress E2E with Cucumber/Gherkin BDD syntax.
- ESLint 9 and Prettier 3.3 for frontend quality gates.
- Spotless and SpotBugs for backend quality gates.
- GitHub Actions runs tests and quality gates before deployment.

Classification: locked requirement from original PRD and user decisions.

## Forbidden / Avoided Technology Choices

- No CSS Modules.
- No styled-components.
- No Redux Saga.
- No Redux Thunk.
- No Next.js.
- No Remix.
- No full WebSocket/SSE live-update layer in MVP.

Classification: locked requirement from PRD.

## PA / Micro-Frontend Boundary Constraints

- WC runs standalone for assessment/demo.
- WC must preserve production-compatible Vite Module Federation remote boundaries.
- WC exposes a single route/module entry point.
- WC declares shared dependencies for Module Federation.
- WC does not hardcode shell navigation.
- WC does not duplicate PA-owned global routing, Auth0/session context, LogRocket/Loki production monitoring, or full PA workspace orchestration.
- WC uses a lightweight assessment-local Yarn Workspaces + Nx shape only.
- Architecture must document the expected PA integration contract.
- Exact PM remote pattern is not present in this workspace; architecture defines a generic Vite Module Federation remote contract and marks exact PM parity as a pre-implementation verification item.

Classification: locked requirement from PRD.

## Identity And Authorization Constraints

- Production authentication boundary is Auth0 OAuth2 JWT.
- Deployed assessment uses hybrid identity:
  - validate real Auth0 JWTs when Auth0 env vars are configured;
  - fall back to seeded persona mode when Auth0 env vars are absent.
- Backend accepts demo identity headers only when `DEMO_AUTH_ENABLED=true`.
- Backend authorization must enforce IC self-access and manager direct-report access.
- Manager dashboard and heatmap are direct-report scoped only.
- MVP has no HR/admin, skip-level, or leadership workflow roles.
- Auth0 claim names are configurable; the architecture defines required identity fields and demo defaults.

Classification: locked decision.

## Data And Domain Constraints

- RCDO is read-only seeded reference data in MVP.
- Seeded RCDO shape: 1 Rally Cry, 3 Defining Objectives, 3 Supporting Outcomes each.
- Seeded RCDO theme: execution SaaS, with objectives such as customer adoption, operational excellence, and platform reliability.
- Demo seed includes one manager with 5-8 direct reports.
- A separate optional synthetic 2,000-record dataset path supports performance tests.
- Weekly periods use a single organization timezone and Monday-Sunday week boundaries.
- Default assessment/demo timezone is `America/Chicago`.
- Manager review SLA uses weekdays only in the organization timezone; holiday calendars are deferred.
- Weekly Plan uniqueness is employee plus week start date.
- Planned commitment baseline fields are immutable after lock.
- No unlock/amend flow in MVP.
- Unplanned commitments must link to Supporting Outcomes before reconciliation closes.
- Entities extend `AbstractAuditingEntity`, and sensitive lifecycle/review/security actions also write a lightweight `audit_event` row.

Classification: locked decision.

## Integration Constraints

- Outlook Graph API integration is required.
- Deployed assessment uses hybrid Graph integration:
  - real Microsoft Graph adapter when credentials/scopes are configured;
  - deterministic demo/failure adapter when Graph env vars are absent.
- Real Graph path uses app-only tenant/admin consent for user-calendar writes, not per-user Outlook connect flows.
- MVP sync triggers: plan lock, reconciliation start, manager review-block event creation.
- Generated plan shells do not create Outlook events by default.
- Graph failures do not block core workflow.
- Graph failure state must be visible, logged, and manually retryable.
- Graph tokens/secrets must not be hardcoded or exposed to users.
- Outlook Calendar Sync Record doubles as the durable outbox for Graph jobs.
- Core lifecycle mutations create sync records; SNS/SQS publishing and manual retry work from those records.
- SNS publishes lifecycle fanout events.
- SQS subscriptions carry Outlook sync jobs to the worker.
- SQS dead-letter queue is included for Outlook sync jobs; no admin redrive UI in MVP.

Classification: locked decision.

## AWS Deployment Constraints

- AWS is the required cloud platform.
- Spring Boot API deploys to Amazon EKS.
- Outlook sync worker deploys as a separate Spring Boot workload on Amazon EKS.
- Weekly plan shell generation runs as a Kubernetes CronJob on EKS.
- PostgreSQL 16.4 runs on Amazon RDS for PostgreSQL.
- Vite frontend assets deploy to S3 and are served through CloudFront CDN.
- Frontend bucket should be private behind CloudFront Origin Access Control.
- Frontend custom domain: `wc.${ROOT_DOMAIN}`.
- API custom domain: `api.wc.${ROOT_DOMAIN}`.
- `ROOT_DOMAIN` is a required deployment variable.
- Default regional AWS workload region is `us-east-1`.
- CloudFront custom-domain ACM certificate must be in `us-east-1`.
- API/ALB certificate can be regional.
- EKS uses a small managed node group.
- API ingress uses AWS Load Balancer Controller / ALB.
- API and sync worker build as separate container images.
- Terraform is the infrastructure-as-code default.
- AWS Secrets Manager is the source for deployed secrets, injected into EKS workloads.
- Spring Actuator health/readiness plus CloudWatch logs/metrics are the assessment observability baseline.
- GitHub Actions builds, tests, pushes backend/worker images to ECR, deploys EKS workloads, and syncs Vite assets to S3/CloudFront.

Classification: locked decision from original PRD and user decisions.

## Performance Constraints

- Common plan retrieval API response target: under 200ms.
- Manager team views must paginate with Spring Data Pageable.
- Team views should handle up to 2,000 records without loading all data into the browser.
- Heatmap aggregation must avoid N+1 query paths and client-side full aggregation.
- Frontend should lazy-load routes where feasible.
- Module Federation remote bundle should be optimized for CDN delivery in production.

Classification: locked requirement from PRD.

## Security And Privacy Constraints

- IDs in URLs/API requests must not enable direct object reference attacks.
- Manager notes and alignment disputes are sensitive review data.
- Sensitive lifecycle/review/dispute events require audit logging or equivalent audit metadata.
- User-entered text must handle empty strings, length limits, Unicode, and HTML/script injection attempts.
- Graph failures must not expose token or secret details.

Classification: locked requirement from PRD.

## Demo Constraints

- Demo must prove the strategy-enforced alignment thesis.
- Seed data must show varied plan states, review states, risk badges, disputes, carry-forward, unplanned work, and Graph failure recovery.
- Demo identity must support both IC and manager personas.
- Real Auth0 and real Graph credentials are not assumed for local/demo.
- Assessment delivery requires deployed frontend and backend, not only a local runnable app.
- Assessment delivery requires custom-domain deployment.
- If build pressure appears, automatic manager review-block Graph event creation is the first pressure-release candidate.

Classification: locked decision / proposed recommendation mix.

## Explicitly Deferred Scope

- Full 15Five replacement features.
- RCDO admin/editing UI.
- Pass-up/escalation and broader leadership rollups.
- True live updates via WebSockets/SSE/push.
- Full calendar or Teams replacement.
- Automatic Outlook events from generated plan shells.
- Automatic background retry for Outlook failures.
- User-local weekly timezones.
- Holiday-aware business-day SLA.
- Admin redrive UI for SQS DLQ.
- Per-report manager review calendar events; MVP uses one manager review-block event per manager/week.
- Audited unlock/amend flow.

Classification: locked decision.

## Remaining Open Constraints

- Exact root domain value for `wc.${ROOT_DOMAIN}` and `api.wc.${ROOT_DOMAIN}`.
- Exact Auth0 claim-name defaults for the configurable mapper.
- Exact existing PM remote pattern to verify against, if the PA/PM repo becomes available.

Classification: open question.
