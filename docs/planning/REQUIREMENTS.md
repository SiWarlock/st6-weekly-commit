# Requirements

> Status: Draft planning artifact
> Phase: 6 - Requirements extraction
> Date: 2026-06-02
> Sources: `PRD.md`, original PRD pasted by user, `PRODUCT_BRIEF.md`, `USERS.md`, `USER_FLOWS.md`, `DOMAIN_MODEL.md`, Office Hours reframe, CEO review

## Functional Requirements

| ID | Requirement | Source | Priority | Acceptance Signal |
|---|---|---|---|---|
| REQ-F-001 | System generates weekly draft plan shells for active employees by an idempotent EKS Kubernetes CronJob. | User-confirmed | MVP | Rerunning generation for the same employee/week does not create duplicates. |
| REQ-F-002 | Weekly periods use a single organization timezone and Monday-Sunday week boundaries. | User-confirmed | MVP | Plan week start/end labels and uniqueness use organization timezone, default `America/Chicago` for demo. |
| REQ-F-003 | Manager dashboard shows direct reports even when plans are empty generated shells. | Inferred | MVP | Manager can distinguish not-started, draft, locked, reconciling, and reconciled direct-report plans. |
| REQ-F-004 | IC can create, edit, and delete own draft commitments. | PRD explicit | MVP | Draft commitment CRUD succeeds for owner and fails for other users. |
| REQ-F-005 | IC can select one Supporting Outcome for each planned commitment. | PRD explicit | MVP | Commitment stores a Supporting Outcome link from the RCDO hierarchy. |
| REQ-F-006 | IC can assign chess-layer metadata to commitments. | PRD explicit | MVP | Priority, work type, confidence, alignment status, and manager-owned note model are represented. |
| REQ-F-007 | IC can lock a weekly plan only when at least one planned commitment exists and every planned commitment has a Supporting Outcome. | User-confirmed / PRD explicit | MVP | Lock API rejects empty plans and unlinked planned commitments. |
| REQ-F-008 | Locking a plan freezes planned commitment baseline fields. | User-confirmed | MVP | Attempts to mutate title, description, Supporting Outcome, priority, work type, confidence, planned/unplanned marker, or week ownership after lock are rejected. |
| REQ-F-009 | Managers can see direct-report draft commitment details before lock. | User-confirmed | MVP | Manager can inspect draft details for direct reports only. |
| REQ-F-010 | Formal manager review, review SLA, alignment flags, and dispute actions are available only after lock. | User-confirmed | MVP | Manager flag/review mutation against a draft plan is rejected. |
| REQ-F-011 | Manager review status supports `NOT_REVIEWED`, `REVIEWED_WITH_DISPUTES`, and `REVIEWED`. | User-confirmed | MVP | Review status transitions match the domain state machine. |
| REQ-F-012 | `OVERDUE` review is derived from persisted `reviewDueAt`, not stored as source status. | User-confirmed | MVP | API returns overdue signal when now exceeds due date and review status is `NOT_REVIEWED`. |
| REQ-F-013 | `REVIEWED_WITH_DISPUTES` satisfies the manager review SLA while unresolved disputes remain visible as alignment risk. | User-confirmed | MVP | A reviewed-with-disputes plan is not overdue but still appears in dispute/risk filters. |
| REQ-F-014 | Manager can comment on direct-report locked plans/commitments. | PRD explicit | MVP | Comment/note mutation succeeds only for authorized manager. |
| REQ-F-015 | Manager can flag a locked commitment as `Needs Revision` or `Misaligned` only with a required note. | PRD explicit / User-confirmed | MVP | Flag mutation without note is rejected and creates an Alignment Dispute when valid. |
| REQ-F-016 | IC can respond to an alignment dispute by changing the Supporting Outcome or adding rationale. | PRD explicit | MVP | Dispute transitions to IC-responded state with response data. |
| REQ-F-017 | Only the direct manager can explicitly resolve an alignment dispute. | User-confirmed | MVP | IC cannot resolve; authorized manager can resolve. |
| REQ-F-018 | A commitment can have many historical disputes but at most one unresolved dispute at once. | User-confirmed | MVP | Attempt to open second unresolved dispute for same commitment is rejected. |
| REQ-F-019 | Manager command center shows direct-report plan state, review state, overdue signal, dispute state, reconciliation status, and carry-forward risk. | PRD explicit | MVP | Dashboard API returns all listed fields for authorized direct reports. |
| REQ-F-020 | Manager heatmap defaults to direct-report rows and Defining Objective columns. | CEO review / PRD explicit | MVP | Heatmap response groups cells by direct report and Defining Objective. |
| REQ-F-021 | Heatmap cells show commitment counts plus explicit risk badges. | User-confirmed | MVP | Cells include counts and badges for risks such as misaligned, needs-review, blocked, carry-forward, unreviewed, or overdue review. |
| REQ-F-022 | Heatmap supports Supporting Outcome drill-down and linked commitments. | PRD explicit | MVP | Manager can drill into a cell to see outcomes and commitments. |
| REQ-F-023 | Manager dashboard supports filters by person, plan state, review state, Defining Objective, Supporting Outcome, priority, work type, and alignment status. | PRD explicit | MVP | Filter parameters affect dashboard/heatmap query results. |
| REQ-F-024 | IC can enter reconciliation any time after lock. | User-confirmed | MVP | Reconciliation start succeeds for locked plan and fails for draft/reconciled plans. |
| REQ-F-025 | IC can add explicit unplanned commitments after lock. | PRD explicit | MVP | Unplanned commitments are labeled and do not mutate locked planned baseline. |
| REQ-F-026 | Unplanned commitments can be created without Supporting Outcome but must link to Supporting Outcome before reconciliation closes. | User-confirmed | MVP | Reconciliation close rejects unplanned commitments without Supporting Outcomes. |
| REQ-F-027 | IC can mark outcomes as completed, partially completed, blocked, canceled, or carried forward. | PRD explicit | MVP | Reconciliation outcome values are persisted and displayed. |
| REQ-F-028 | Carry-forward targets the next Monday-Sunday week and creates the next-week plan shell if missing. | User-confirmed | MVP | Carry-forward creates linked draft commitment in next week. |
| REQ-F-029 | IC can close reconciliation only when required outcomes and links are complete. | Inferred | MVP | Reconciliation close validates planned/unplanned outcomes and required Supporting Outcome links. |
| REQ-F-030 | ICs do not see team-level heatmap data in MVP. | User-confirmed | MVP | IC role cannot access heatmap/team roll-up endpoints. |
| REQ-F-031 | MVP includes only IC and direct manager human workflow roles. | User-confirmed | MVP | No HR/admin/skip-level/leadership workflow role is exposed. |
| REQ-F-032 | Standalone/deployed demo supports seeded user/persona switching while preserving production Auth0 adapter boundary. | User-confirmed | MVP | Persona switcher changes identity context and backend authorization still enforces ownership/scoping. |

## Integration Requirements

| ID | Requirement | Source | Priority | Acceptance Signal |
|---|---|---|---|---|
| REQ-I-001 | Outlook Graph sync attempts after plan lock, reconciliation start, and manager review-block event creation. | User-confirmed / PRD explicit | MVP | Sync records are created for those triggers and not for generated plan shells by default. |
| REQ-I-002 | Scheduled plan shell generation does not automatically create Outlook events in MVP. | User-confirmed | MVP | Plan generation creates no Graph sync records unless stretch flag is enabled. |
| REQ-I-003 | Outlook events include lifecycle context and deep links to the relevant WC plan or manager command center. | PRD explicit | MVP | Event payload builder includes link/context fields. |
| REQ-I-004 | Outlook sync failure never blocks core lifecycle mutations. | PRD explicit / CEO review | MVP | Lock/reconciliation/review succeeds even when Graph sync fails. |
| REQ-I-005 | Outlook sync failures are visible, logged, and manually retryable from UI. | User-confirmed | MVP | Failed sync state renders warning and retry uses existing sync record. |
| REQ-I-006 | Outlook sync stores Graph event IDs for update/cancel and retry idempotency. | PRD explicit | MVP | Successful sync record contains `graphEventId`; retry does not duplicate events. |
| REQ-I-007 | WC frontend is structured as a Vite Module Federation remote while also running standalone for assessment. | PRD explicit | MVP | Standalone app runs locally and remote exposure/host contract is documented. |
| REQ-I-008 | WC does not duplicate PA shell-owned concerns. | PRD explicit | MVP | Architecture and implementation do not hardcode shell navigation, global routing, LogRocket/Loki, Yarn Workspace, or Nx ownership. |
| REQ-I-009 | Outlook sync jobs use SNS lifecycle fanout and SQS subscription delivery to a separate EKS worker. | User-confirmed / original PRD | MVP | Lock/reconcile/review sync triggers create sync records and enqueue/dispatch worker jobs. |
| REQ-I-010 | Outlook Calendar Sync Record acts as the durable outbox for Graph jobs and manual retry. | User-confirmed | MVP | Manual retry republishes or requeues from an existing sync record without creating duplicates. |
| REQ-I-011 | Outlook sync queue has an SQS DLQ for failed jobs. | User-confirmed | MVP | Queue configuration includes a redrive policy and DLQ; UI retry remains sync-record based. |
| REQ-I-012 | Real Microsoft Graph integration uses app-only tenant/admin consent for calendar writes, with demo adapter fallback. | User-confirmed / research | MVP | Real adapter targets user calendar events when configured; demo adapter can produce deterministic success/failure states. |
| REQ-I-013 | Module Federation uses a generic Vite remote contract until the real PM remote pattern is available. | User-confirmed | MVP | Remote name, `remoteEntry.js`, exposed route/module, and shared React deps are documented. |
| REQ-I-014 | SNS/SQS Outlook sync messages use pointer payloads, not full calendar event payloads. | User-confirmed | MVP | Queue message includes sync record ID, event kind, tenant/env context, and trace ID; worker loads authoritative data from PostgreSQL. |
| REQ-I-015 | Manager review-block Outlook sync creates one event per manager/week. | User-confirmed | MVP | Event update points manager to command center summary rather than creating per-report events. |

## Data Requirements

| ID | Requirement | Source | Priority | Acceptance Signal |
|---|---|---|---|---|
| REQ-D-001 | PostgreSQL persists Employees, Manager Relationships, Weekly Plans, Weekly Commitments, RCDO hierarchy, Manager Reviews, Alignment Disputes, Outlook Sync Records, and audit metadata/events. | PRD explicit / Inferred | MVP | Flyway schema covers all core entities. |
| REQ-D-002 | Weekly Plan uniqueness is employee plus week start date. | Inferred | MVP | Database constraint prevents duplicate plan shells. |
| REQ-D-003 | RCDO is read-only seeded reference data for MVP. | CEO review / PRD explicit | MVP | No API/UI mutates Rally Cry, Defining Objective, or Supporting Outcome data. |
| REQ-D-004 | Seeded RCDO shape is 1 Rally Cry, 3 Defining Objectives, and 3 Supporting Outcomes per Defining Objective. | User-confirmed | MVP | Seed migration/demo data creates the specified hierarchy. |
| REQ-D-005 | Seed data includes one manager with 5-8 direct reports plus plans, commitments, reviews, disputes, overdue states, carry-forward, and Graph failure states for demo. | User-confirmed / Inferred | MVP | Demo fixtures exercise IC and manager workflows. |
| REQ-D-006 | Carry-forward commitments link back to source commitments. | PRD explicit | MVP | Database relation or field stores source commitment ID. |
| REQ-D-007 | Audit data preserves sensitive lifecycle/review/dispute actions through `AbstractAuditingEntity` plus a lightweight `audit_event` table. | User-confirmed / PRD explicit / Inferred | MVP | Plan lock, review, dispute open/respond/resolve, reconciliation, carry-forward, Graph failure, and authorization denial create audit/loggable events. |
| REQ-D-008 | Synthetic performance seed path can generate up to 2,000 team-view records separately from human-readable demo seed. | User-confirmed | MVP | Performance tests can run against a larger generated dataset without bloating normal demo seed. |
| REQ-D-009 | Physical schema uses UUID primary keys for externally referenced domain records. | User-confirmed | MVP | Flyway schema uses UUID IDs for plans, commitments, reviews, disputes, sync records, comments, and projections. |
| REQ-D-010 | Lifecycle/status fields use `VARCHAR` values with check constraints. | User-confirmed | MVP | Flyway schema constrains plan/review/dispute/outcome/sync statuses. |
| REQ-D-011 | Planned and unplanned commitments share one `weekly_commitment` table. | User-confirmed | MVP | `commitment_kind` or equivalent distinguishes planned from unplanned while preserving one domain entity. |
| REQ-D-012 | Manager command center uses synchronous read-model tables. | User-confirmed | MVP | `manager_plan_summary` and `manager_heatmap_cell` update with affected domain writes. |
| REQ-D-013 | Projection rebuild is available as an internal job/CLI. | User-confirmed | MVP | Maintainer can rebuild manager summaries/heatmap cells without a user-facing admin UI. |
| REQ-D-014 | Comments support full nested threads using materialized path. | User-confirmed | MVP | Comment records include parent ID, path, depth, and target scope for plan/commitment discussion. |

## Security Requirements

| ID | Requirement | Source | Priority | Acceptance Signal |
|---|---|---|---|---|
| REQ-S-001 | Backend enforces IC self-access for plan and commitment mutations. | PRD explicit | MVP | Tests prove IC cannot read/mutate another IC plan by ID. |
| REQ-S-002 | Backend enforces manager direct-report scoping for plan, review, dispute, dashboard, and heatmap access. | PRD explicit / CEO review | MVP | Unauthorized manager receives denial for non-direct-report resources. |
| REQ-S-003 | Authorization is enforced server-side and never only by frontend route hiding. | Inferred | MVP | API integration tests cover denied requests. |
| REQ-S-004 | Graph tokens/secrets are not hardcoded, are rotatable, and are never displayed to users. | PRD explicit | MVP | Config uses environment variables and UI shows safe failure messages only. |
| REQ-S-005 | User-entered text handles empty strings, length limits, Unicode, and HTML/script injection attempts. | PRD explicit | MVP | Validation and escaping tests cover commitment titles/notes/comments/rationale. |
| REQ-S-006 | Sensitive manager notes and disputes are audit logged. | PRD explicit | MVP | Dispute and manager note mutations emit audit/log events. |
| REQ-S-007 | Auth0 JWT is the production authentication boundary, represented through a hybrid adapter-compatible seeded persona strategy. | User-confirmed / PRD explicit | MVP | Code validates real JWTs when configured and separates identity resolution from domain authorization. |
| REQ-S-008 | Backend accepts demo persona identity headers only when demo auth is explicitly enabled. | User-confirmed | MVP | Requests with demo identity header are rejected when `DEMO_AUTH_ENABLED` is false. |
| REQ-S-009 | Auth0 claim names are configurable while required identity fields are stable. | User-confirmed | MVP | Employee ID, role, and relationship lookup inputs are mapped through configuration rather than hardcoded claim names. |
| REQ-S-010 | Deployed secrets use AWS Secrets Manager. | User-confirmed | MVP | Auth0, Graph, database, and demo-mode secrets are injected from Secrets Manager, not committed or baked into images. |
| REQ-S-011 | Backend resource authorization is centralized in a domain authorization service. | User-confirmed | MVP | Service-layer checks enforce self/direct-report access for reads and mutations; controller annotations are coarse gates only. |

## Non-Functional Requirements

| ID | Requirement | Source | Priority | Acceptance Signal |
|---|---|---|---|---|
| REQ-NF-001 | Common plan retrieval API responses should target under 200ms. | PRD explicit | MVP | Performance test or documented benchmark covers common retrieval path. |
| REQ-NF-002 | Manager team views must paginate and avoid loading all records into the browser. | PRD explicit | MVP | API accepts Pageable parameters and frontend uses paginated data. |
| REQ-NF-003 | Heatmap aggregation must avoid N+1 query paths and client-side full aggregation. | PRD explicit / Inferred | MVP | Aggregation query is backend-owned and indexed. |
| REQ-NF-004 | Frontend must use RTK Query for API calls and cache invalidation/refetch for MVP freshness. | PRD explicit | MVP | No Saga/Thunk; mutations invalidate relevant query tags. |
| REQ-NF-005 | Frontend routes should be lazy-loaded where feasible for sub-second initial render. | PRD explicit | MVP | Major screens load through route/module splitting where practical. |
| REQ-NF-006 | Frontend remote bundle should deploy to S3/CloudFront and be optimized for CDN delivery. | Original PRD / PRD explicit | MVP | Vite build assets are cacheable through CloudFront with SPA fallback routing. |
| REQ-NF-007 | Backend and worker deploy as containerized Spring Boot workloads on EKS. | Original PRD / User-confirmed | MVP | GitHub Actions builds images and deploys API/worker workloads to EKS. |

## UX Requirements

| ID | Requirement | Source | Priority | Acceptance Signal |
|---|---|---|---|---|
| REQ-UX-001 | IC workspace makes RCDO selection clear enough that every planned commitment can link to one Supporting Outcome. | Inferred | MVP | User can browse/search Rally Cry -> Defining Objective -> Supporting Outcome. |
| REQ-UX-002 | IC workspace clearly distinguishes draft, locked, reconciling, reconciled, unplanned, and carried-forward work. | PRD explicit | MVP | UI labels and controls reflect lifecycle state. |
| REQ-UX-003 | Manager command center exposes actionable review, dispute, overdue, heatmap, and reconciliation risk signals without opening every plan. | PRD explicit | MVP | Dashboard shows roll-up and filters. |
| REQ-UX-004 | Outlook sync warning path is visible and recoverable. | User-confirmed | MVP | UI displays warning plus retry action for failed sync. |
| REQ-UX-005 | ICs see only own plan data, own manager comments/disputes, and RCDO context. | User-confirmed | MVP | IC UI has no team heatmap entry point. |

## Operational Requirements

| ID | Requirement | Source | Priority | Acceptance Signal |
|---|---|---|---|---|
| REQ-O-001 | Backend emits/logs key lifecycle signals. | PRD explicit | MVP | Logs include plan locked, review due/reviewed, reconciliation started/reconciled, carry-forward, dispute open/resolved, Graph success/failure, and authorization denial. |
| REQ-O-002 | Local/demo environment supports seeded data and persona switching. | User-confirmed | MVP | Demo can exercise IC and manager flows without real Auth0 setup. |
| REQ-O-003 | Business-day review due date uses organization timezone. | User-confirmed | MVP | Due date calculation uses configured org timezone. |
| REQ-O-004 | Holiday-aware SLA is deferred unless needed. | Inferred | Deferred | MVP documents weekday-only business-day calculation fallback. |
| REQ-O-005 | Deployment uses AWS EKS, RDS PostgreSQL 16.4, S3, CloudFront, SNS, SQS, Route 53, ACM, ECR, and Secrets Manager. | Original PRD / User-confirmed | MVP | Terraform and deployment docs describe/provision the AWS service graph. |
| REQ-O-006 | Infrastructure is managed with Terraform. | User-confirmed | MVP | Terraform configuration covers required AWS infrastructure. |
| REQ-O-007 | CI/CD uses GitHub Actions. | User-confirmed | MVP | Pipeline runs tests/quality gates, builds images/assets, deploys EKS workloads, and syncs S3/CloudFront assets. |
| REQ-O-008 | Deployed frontend and API use custom domains. | User-confirmed | MVP | `wc.${ROOT_DOMAIN}` serves frontend and `api.wc.${ROOT_DOMAIN}` serves backend API. |
| REQ-O-009 | Observability uses Spring Actuator plus CloudWatch logs/metrics. | User-confirmed | MVP | API, worker, and CronJob expose health/readiness and write CloudWatch-visible logs. |
| REQ-O-010 | AI usage log is delivered as Markdown. | User-confirmed / original PRD | MVP | `AI_USAGE.md` records tool/model usage, AI-assisted task summaries, human review notes, and generated artifacts. |
| REQ-O-011 | AWS default region is `us-east-1`, overridable through Terraform variables. | User-confirmed | MVP | Terraform defaults regional resources to `us-east-1`; CloudFront ACM certificate is also in `us-east-1`. |
| REQ-O-012 | API ingress uses AWS Load Balancer Controller / ALB. | User-confirmed | MVP | EKS ingress provisions ALB and uses ACM for `api.wc.${ROOT_DOMAIN}`. |
| REQ-O-013 | EKS uses a small managed node group for API, worker, and CronJob workloads. | User-confirmed | MVP | Terraform provisions managed node group compute. |
| REQ-O-014 | API and sync worker build as separate container images. | User-confirmed | MVP | CI pushes separate ECR images and deploys separate EKS workloads. |
| REQ-O-015 | Local development uses Docker Compose for full-stack services. | User-confirmed | MVP | Compose starts PostgreSQL and local service wiring for frontend/API/worker/Cypress runs. |
| REQ-O-016 | Java backend uses Gradle multi-module. | User-confirmed | MVP | Gradle modules separate shared code, API app, and sync worker app. |

## Testing Requirements

| ID | Requirement | Source | Priority | Acceptance Signal |
|---|---|---|---|---|
| REQ-T-001 | Unit tests cover plan lifecycle transitions. | PRD explicit | MVP | Invalid transitions fail. |
| REQ-T-002 | Unit tests cover required Supporting Outcome validation for lock and reconciliation close. | PRD explicit / User-confirmed | MVP | Planned/unplanned validation cases pass. |
| REQ-T-003 | Unit tests cover locked baseline edit restrictions. | User-confirmed | MVP | Mutating immutable fields after lock fails. |
| REQ-T-004 | Unit tests cover manager review SLA/overdue derivation. | PRD explicit / User-confirmed | MVP | `reviewDueAt` and overdue signal cases pass. |
| REQ-T-005 | Unit tests cover dispute state changes and one-open-dispute rule. | User-confirmed | MVP | Second unresolved dispute is rejected. |
| REQ-T-006 | Integration tests cover commitment CRUD with RCDO links. | PRD explicit | MVP | CRUD path persists/returns links. |
| REQ-T-007 | Integration tests cover manager direct-report authorization and IDOR denial. | PRD explicit | MVP | Unauthorized manager cannot access another team. |
| REQ-T-008 | Integration tests cover heatmap aggregation. | PRD explicit | MVP | Counts/risk badges match seeded data. |
| REQ-T-009 | Integration tests cover Outlook sync success/failure/manual retry. | PRD explicit / User-confirmed | MVP | Failed sync does not block core workflow and retry is idempotent. |
| REQ-T-010 | E2E tests cover IC draft/lock/reconcile/carry-forward. | PRD explicit | MVP | Browser flow completes successfully. |
| REQ-T-011 | E2E tests cover manager review/flag/IC response/manager resolve. | PRD explicit | MVP | Full dispute loop completes successfully. |
| REQ-T-012 | Performance tests or benchmarks cover common plan retrieval and manager pagination. | PRD explicit | MVP | Evidence included in test results. |
| REQ-T-013 | Backend test/quality gates enforce JaCoCo 80% minimum coverage, Spotless, and SpotBugs. | Original PRD | MVP | CI fails below coverage threshold or on Spotless/SpotBugs violations. |
| REQ-T-014 | Frontend test/quality gates enforce Vitest component/unit coverage, ESLint 9, and Prettier 3.3. | Original PRD | MVP | CI fails on lint/format/unit-test failures. |
| REQ-T-015 | Cypress E2E uses Cucumber/Gherkin BDD syntax as the primary acceptance suite. | User-confirmed / original PRD | MVP | `.feature` scenarios and TypeScript step definitions cover core IC/manager flows. |
| REQ-T-016 | Full Cypress/Cucumber suite runs against local CI/test services; deployed smoke suite runs against deployed custom domains. | User-confirmed | MVP | CI produces local E2E evidence and post-deploy smoke evidence. |
| REQ-T-017 | Backend database integration tests use Testcontainers PostgreSQL. | User-confirmed | MVP | Java integration tests start PostgreSQL containers instead of H2. |

## Demo / Evaluation Requirements

| ID | Requirement | Source | Priority | Acceptance Signal |
|---|---|---|---|---|
| REQ-E-001 | Demo proves 100% Supporting Outcome linkage for locked planned commitments. | PRD explicit | MVP | Attempt to lock unlinked planned commitment fails. |
| REQ-E-002 | Demo proves manager direct-report-only command center. | PRD explicit | MVP | Persona switcher can show allowed and denied manager views. |
| REQ-E-003 | Demo proves manager heatmap with counts, risk badges, and drill-down. | CEO review / User-confirmed | MVP | Seeded manager view shows varied cells and drill-down. |
| REQ-E-004 | Demo proves non-blocking Outlook failure path. | PRD explicit | MVP | Simulated or real Graph failure shows warning and workflow remains successful. |
| REQ-E-005 | Demo proves unplanned work and carry-forward do not rewrite the locked baseline. | PRD explicit / User-confirmed | MVP | Reconciliation view shows planned baseline, unplanned work, and linked carry-forward. |
| REQ-E-006 | Demo proves AWS deployment through custom frontend/API domains. | User-confirmed / original PRD | MVP | Demo video or evidence shows deployed `wc.<root-domain>` and `api.wc.<root-domain>` paths. |
| REQ-E-007 | Demo proves hybrid Auth0/Graph fallback behavior. | User-confirmed | MVP | Demo remains usable without real credentials and documents how real adapters activate when configured. |

## Deferred Requirements

| ID | Requirement | Source | Priority | Acceptance Signal |
|---|---|---|---|---|
| REQ-X-001 | Pass-up/escalation to skip-level or leadership. | CEO review | Deferred | Not implemented; documented as future scope. |
| REQ-X-002 | RCDO admin/editing UI. | CEO review / PRD explicit | Deferred | No mutation UI/API for RCDO in MVP. |
| REQ-X-003 | Full 15Five replacement features: surveys, High Fives, reviews, 1-on-1 workspace, HR analytics, coaching, compensation, HRIS provisioning. | PRD explicit | Deferred | Excluded from scope and architecture. |
| REQ-X-004 | True live updates through WebSockets/SSE/push. | PRD explicit | Deferred | MVP uses RTK Query refetch/cache invalidation. |
| REQ-X-005 | Automatic calendar events from generated plan shells. | User-confirmed | Stretch/deferred | Architecture preserves hook; not enabled by default. |
| REQ-X-006 | Automatic background retry for Outlook failures. | User-confirmed | Deferred | Manual retry only in MVP. |
| REQ-X-007 | Audited unlock/amend workflow after lock. | User-confirmed | Deferred | No unlock/amend in MVP. |
| REQ-X-008 | User-local timezone weekly periods. | User-confirmed | Deferred | Single org timezone in MVP. |
| REQ-X-009 | HR/admin, skip-level, or leadership workflow roles. | User-confirmed | Deferred | Only IC and direct manager in MVP. |
| REQ-X-010 | Holiday-aware business-day SLA. | User-confirmed | Deferred | MVP uses weekday-only calculation in organization timezone. |
| REQ-X-011 | SQS DLQ admin redrive UI. | User-confirmed | Deferred | DLQ exists for ops visibility, but manual UI retry uses sync records. |

## Requirements Notes

- Engineering/evaluator review standards take priority when tradeoffs conflict.
- Requirements should be implemented through backend-enforced invariants and tests, not only UI constraints.
- The architecture should preserve production integration boundaries even when the MVP uses seeded data or demo adapters.
- Any requirement depending on current Microsoft Graph/Auth0 API details should be verified in the research phase before final architecture decisions are locked.
- No current MVP requirements are downgraded to stretch as of 2026-06-02.
- If the one-week build needs a pressure release, automatic Outlook manager review-block event creation is the first candidate to simplify while preserving visible/manual Graph recovery behavior.

Classification: locked planning checkpoint, confirmed by user on 2026-06-02.
