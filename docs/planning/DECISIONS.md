# Decisions

> Status: Draft planning artifact
> Phase: 11 - Architecture decision discovery / locked decision log
> Date: 2026-06-02
> Sources: `PRD.md`, original PRD pasted by user, planning interview decisions, `RESEARCH.md`

## Locked Decision Summary

| Area | Decision | Status | Rationale | Fallback |
|---|---|---|---|---|
| Product posture | Strategy-Enforced Weekly Alignment System, not full 15Five clone | Locked | Preserves PRD thesis and CEO review scope. | Defer 15Five-suite features. |
| Atomic unit | Weekly Commitment is unit of value; Weekly Plan is lifecycle container | Locked | Supports RCDO links, disputes, heatmap, carry-forward. | None planned. |
| Plan lifecycle | `DRAFT -> LOCKED -> RECONCILING -> RECONCILED` | Locked | Matches PRD and preserves baseline. | No unlock/amend in MVP. |
| Manager review | `NOT_REVIEWED`, `REVIEWED_WITH_DISPUTES`, `REVIEWED`; overdue derived | Locked | Separates review SLA from dispute risk. | Revisit if evaluator demands PRD's explicit persisted `OVERDUE`. |
| Manager visibility | Direct-report only; can view drafts, formal review after lock | Locked | Balances early visibility with submission boundary. | Defer skip-level/leadership. |
| Baseline | Immutable planned commitment fields after lock | Locked | Simpler than snapshot/version tables. | Future audited amendment flow. |
| Plan generation | EKS Kubernetes CronJob generates weekly shells idempotently | Locked | Production-shaped cadence without in-app multi-replica scheduler risk. | Manual admin trigger only if EKS schedule is blocked. |
| Week/SLA time | Monday-Sunday org week, `America/Chicago` demo default, weekdays-only business days | Locked | Deterministic and testable. | Add holiday calendars later. |
| AWS deployment | EKS API/worker/CronJob, RDS Postgres 16.4, S3/CloudFront, SNS/SQS/DLQ, Route 53/ACM | Locked | Original PRD explicitly requires AWS services. | Simplify optional review-block event before cutting AWS services. |
| Frontend deploy | Private S3 origin behind CloudFront OAC at `wc.<root-domain>` | Locked | CDN-optimized SPA and custom-domain requirement. | Generated CloudFront URL only if domain unavailable. |
| API deploy | Spring Boot API on EKS behind `api.wc.<root-domain>` | Locked | Matches AWS/EKS and custom-domain requirements. | ALB generated DNS only if domain unavailable. |
| Database | Amazon RDS PostgreSQL 16.4 | Locked | Managed database avoids running Postgres in EKS. | Verify region availability. |
| Async sync | SNS lifecycle fanout, SQS sync queue, separate EKS worker, DLQ | Locked | Satisfies SQS/SNS and keeps Graph sync non-blocking. | In-process sync only if AWS queue setup blocks demo, but document deviation. |
| Sync consistency | Outlook Calendar Sync Record acts as durable outbox | Locked | Core mutation remains durable and retryable if publish/worker fails. | Full generic transactional outbox later. |
| Graph auth | Real Graph adapter uses app-only tenant/admin consent; demo adapter fallback | Locked | Avoids per-user Outlook connect flow. | Demo adapter if tenant consent unavailable. |
| Auth0 | Spring Security Resource Server when configured; env-gated seeded persona fallback | Locked | Honors Auth0 while keeping demo reliable. | Real Auth0 only if evaluator demands it. |
| Auth claims | Configurable claim-name mapping with stable identity fields | Locked | PA/Auth0 claims unknown. | Lock exact names after PA config appears. |
| Repo shape | Lightweight Yarn Workspaces + Nx monorepo | Locked | Satisfies PRD without replicating full PA workspace. | Document-only if Nx setup becomes build risk. |
| E2E | Cypress/Cucumber primary; Playwright optional/ad hoc | Locked | OG PRD expects Cypress BDD. | Playwright only if Cypress setup blocks, but document variance. |
| CI/CD | GitHub Actions for quality gates and AWS deploy | Locked | Common, direct path to ECR/EKS/S3/CloudFront. | Manual deploy scripts as emergency fallback. |
| IaC | Terraform | Locked | Explicit production-shaped AWS provisioning. | Minimal manifests only if time is critical. |
| Secrets | AWS Secrets Manager | Locked | Avoids hardcoded secrets and supports EKS. | Kubernetes Secrets only for local/dev. |
| Observability | Spring Actuator + CloudWatch; no LogRocket/Loki replication | Locked | PA owns production LogRocket/Loki; assessment still needs health/logs. | Add Loki only if explicitly required. |
| Audit | `AbstractAuditingEntity` plus lightweight `audit_event` table | Locked | Sensitive review/dispute trail needs more than updated columns. | Columns/logs only if table scope must be cut. |
| Seed data | 1 manager, 5-8 reports, execution-SaaS RCDO; optional 2,000-record perf seed | Locked | Human-readable demo plus performance evidence. | Smaller demo seed if needed, keep synthetic perf path. |
| Heatmap data | `manager_heatmap_cell` read model table | Locked | Manager command center needs fast cells and drill-down. | Live aggregate only if projection scope must be cut. |
| Manager roll-up data | `manager_plan_summary` read model table | Locked | Keeps command center fast and consistent with heatmap strategy. | Compute summaries live if projection scope must be cut. |
| Projection updates | Synchronous projection updates plus internal rebuild job/CLI | Locked | Avoids eventual-consistency UX while preserving repair path. | Async projection later if write latency becomes a problem. |
| API style | REST JSON with resources plus lifecycle command endpoints | Locked | Fits Spring MVC, RTK Query, and explicit state transitions. | OpenAPI refinement later. |
| Backend authorization | Central domain authorization service for self/direct-report checks | Locked | Prevents IDOR across plan/review/dispute/heatmap paths. | Controller annotations only for coarse gates. |
| Workspace layout | `apps/wc-web`, `apps/wc-api`, `apps/wc-sync-worker`, `apps/wc-e2e`, `infra/terraform`, `infra/k8s` | Locked | Clear deployables and CI boundaries. | Simpler dirs only if Nx setup blocks. |
| Java build | Gradle multi-module | Locked | Supports API/worker/shared modules and JaCoCo/Spotless/SpotBugs. | Maven multi-module if Gradle is unavailable. |
| Schema detail | Near-Flyway-level physical schema | Locked | Reduces implementation ambiguity for a data-heavy app. | Finalizer can trim exact DDL details. |
| IDs | UUID primary keys | Locked | Avoids enumerable IDs in exposed API paths. | Bigint only for internal/non-exposed tables if needed. |
| Enum storage | `VARCHAR` with check constraints | Locked | Readable and migration-friendly. | Native Postgres enums later if desired. |
| Commitments table | Single `weekly_commitment` table for planned and unplanned work | Locked | Same domain entity; simpler heatmap/projection queries. | Split outcomes later if audit needs grow. |
| Comments | Full nested comment threads using materialized path | Locked | Production-level discussion model without recursive query ambiguity. | One-level threads if UI scope must be cut. |
| Queue payload | Pointer payloads: `syncRecordId`, event kind, tenant/env metadata, trace ID | Locked | Avoids stale/sensitive queue messages. | Hybrid summary only for debugging if safe. |
| AWS region | Default regional workload region is `us-east-1` | Locked | Aligns regional AWS resources and CloudFront ACM needs. | Override through Terraform variable. |
| Root domain | Required `ROOT_DOMAIN` deploy variable | Locked | Keeps custom-domain Terraform concrete without hardcoding a domain. | User supplies before deploy. |
| EKS compute | Small managed node group | Locked | Conventional EKS deployment/debugging path. | Fargate later if ops preference changes. |
| API ingress | AWS Load Balancer Controller / ALB | Locked | Fits EKS custom-domain TLS and host routing. | Service LoadBalancer only if ALB controller blocks. |
| Manager review calendar event | One manager review-block event per manager/week | Locked | Avoids calendar spam and points to command center. | Per-report events later if requested. |
| Container images | Separate API and worker images | Locked | Clear scaling/deployable boundaries. | One image with profiles only if build pipeline scope must shrink. |
| Local dev | Docker Compose for local full stack | Locked | Reproducible local/Cypress environment. | Manual local services only as fallback. |
| Backend DB tests | Testcontainers PostgreSQL | Locked | Tests target real PostgreSQL behavior. | Compose DB only if Testcontainers is unavailable. |
| Threat model | Trust boundaries plus STRIDE | Locked | Captures Auth0/demo, Graph, AWS, DB, and frontend/API boundaries. | Risk-table-only if time is tight. |
| Trim order | Trim Graph polish/review-block automation first | Locked | Preserves lifecycle, AWS deploy, and security. | None. |

## ADR-001 - AWS Deployment Topology

Status: Locked

Decision:

Use AWS as the deployment target: EKS for Spring Boot API, EKS worker, and CronJob; RDS PostgreSQL 16.4; S3 plus CloudFront for Vite assets; SNS/SQS/DLQ for Outlook sync; Route 53/ACM for custom domains.

Rationale:

The original PRD explicitly names AWS EKS, CloudFront CDN, S3, and SQS/SNS. A simpler PaaS deployment would be faster but would fail stack fidelity.

Fallback:

Keep the same architecture contract and trim optional Graph review-block automation before trimming AWS topology.

## ADR-002 - Hybrid Auth0 And Demo Identity

Status: Locked

Decision:

Use Spring Security OAuth2 Resource Server for real Auth0 JWT validation when configured. Use seeded persona mode only when `DEMO_AUTH_ENABLED=true`, with backend demo identity headers rejected otherwise.

Rationale:

This preserves the production authentication boundary without making real Auth0 setup a demo blocker.

Fallback:

If evaluator requires real Auth0, disable demo mode in deployed assessment and configure issuer/audience/claim mapping.

## ADR-003 - Outlook Sync Architecture

Status: Locked

Decision:

Core lifecycle mutations create Outlook Calendar Sync Records. Backend publishes lifecycle events to SNS; SQS subscription delivers jobs to a separate EKS sync worker. Worker calls real Graph adapter or demo adapter and updates sync records. SQS DLQ captures repeated worker failures.

Rationale:

This keeps Graph failures non-blocking, visible, retryable, and AWS-aligned.

Fallback:

If queue setup becomes a delivery blocker, process sync from the durable sync record in-process and document the variance from PRD AWS eventing.

## ADR-004 - Module Federation Contract

Status: Locked with verification item

Decision:

Define a generic Vite Module Federation remote contract: remote name, `remoteEntry.js`, one exposed route/module entry point, and shared React/React DOM/Redux dependencies. Exact PM remote parity remains a verification item.

Rationale:

No PM remote implementation exists in the workspace, so inventing exact host conventions would be false precision.

Fallback:

Adjust remote contract after PA/PM example is provided.

## ADR-005 - Testing And Quality Gates

Status: Locked

Decision:

Use JaCoCo 80%, Spotless, SpotBugs, Vitest, ESLint 9, Prettier 3.3, and Cypress/Cucumber as required CI gates. Playwright is optional/ad hoc QA.

Rationale:

The original PRD explicitly calls out these quality expectations and Cypress BDD syntax.

Fallback:

If time is tight, reduce scenario count but keep the toolchain and critical path coverage.

## ADR-006 - Manager Dashboard Read Models

Status: Locked

Decision:

Use two synchronous projections for the Manager Alignment Command Center:

- `manager_plan_summary` for direct-report plan/review/reconciliation summary rows.
- `manager_heatmap_cell` for direct-report x Defining Objective heatmap cells with counts and risk badges.

Projection rows update synchronously in the same service transaction as affected commitment, review, dispute, and reconciliation mutations. Provide an internal rebuild job/CLI for repair; do not expose a user-facing admin rebuild UI.

Rationale:

The user selected a production-level read-model approach instead of live aggregation. Synchronous updates avoid an eventual-consistency story in the MVP, while the rebuild job gives maintainers a way to recover from projection drift.

Fallback:

If projection implementation becomes too heavy, keep `manager_heatmap_cell` and compute `manager_plan_summary` live.

## ADR-007 - REST API And Authorization

Status: Locked

Decision:

Use Spring MVC REST JSON APIs with resource endpoints plus explicit lifecycle command endpoints, such as `/lock`, `/start-reconciliation`, `/resolve-dispute`, and `/retry-sync`. API contracts should include endpoint path, method, key DTO fields, auth scope, and lifecycle/state constraints.

Use a central domain authorization service for IC self-access and manager direct-report checks. Controller annotations provide coarse role/authentication gates only.

Rationale:

REST JSON best fits Spring Boot, RTK Query, Spring Data Pageable, and the one-week build. Command endpoints make lifecycle transitions auditable and harder to confuse with generic field patches.

Fallback:

If endpoint surface grows too large, compress view-specific reads but keep explicit command mutations.

## ADR-008 - Physical Data Model

Status: Locked

Decision:

Produce a near-Flyway-level `DATA_MODEL.md` with UUID primary keys, `VARCHAR` status fields with check constraints, explicit foreign keys, uniqueness constraints, projection tables, and important indexes.

Use one `weekly_commitment` table for both planned and unplanned commitments, differentiated by `commitment_kind` and work type. Use nested `comment_thread` / `comment` structures with materialized path fields for full nested comment trees.

Rationale:

This app's correctness depends on persistence invariants: locked baseline, direct-report scoping, one-open-dispute rule, carry-forward links, and projection consistency. A conceptual model would leave too many decisions to the implementer.

Fallback:

Finalizer may simplify exact DDL if it proves too detailed, but should preserve constraints and indexes.

## ADR-009 - Workspace, Build, And Local Test Shape

Status: Locked

Decision:

Use a lightweight Yarn Workspaces + Nx layout:

- `apps/wc-web`
- `apps/wc-api`
- `apps/wc-sync-worker`
- `apps/wc-e2e`
- `infra/terraform`
- `infra/k8s`

Use Gradle multi-module for Java modules shared by the API and worker. Use separate API and worker container images. Use Docker Compose for local full-stack development/Cypress runs and Testcontainers PostgreSQL for backend integration tests.

Rationale:

This honors the OG PRD's Yarn/Nx and quality expectations without recreating the full PA monorepo.

Fallback:

If Nx/Gradle setup becomes a delivery blocker, keep the same directories and simplify orchestration scripts while documenting the deviation.

## ADR-010 - AWS Runtime Details

Status: Locked

Decision:

Use `us-east-1` as the default AWS region, a required `ROOT_DOMAIN` deployment variable, EKS managed node group compute, AWS Load Balancer Controller/ALB for `api.wc.${ROOT_DOMAIN}`, S3/CloudFront OAC for `wc.${ROOT_DOMAIN}`, separate API/worker images, and pointer-only SNS/SQS messages containing `syncRecordId`, event kind, tenant/env metadata, and trace ID.

Rationale:

The decisions keep the infrastructure concrete enough for Terraform and task generation while avoiding hardcoded customer-specific DNS values or sensitive queue payloads.

Fallback:

AWS region can be overridden through Terraform variables; root domain must be provided before deploy.
