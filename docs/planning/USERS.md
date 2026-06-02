# Users And Actors

> Status: Draft planning artifact
> Phase: 2 - Users, actors, and permissions
> Date: 2026-06-02
> Sources: `PRD.md`, `PRODUCT_BRIEF.md`, Office Hours reframe, CEO review

## Primary User: Individual Contributor

- Role: Employee who owns a weekly plan.
- Goal: Create a credible weekly plan, connect each planned commitment to strategy, lock the plan, reconcile outcomes, and carry forward unfinished work without losing the planned baseline.
- Context: Uses WC during weekly planning and week-end reconciliation.
- Pain points: Current weekly planning does not enforce a link to strategic outcomes; manager feedback may arrive too late or be unstructured.
- Workflow: Draft commitments, link Supporting Outcomes, add chess-layer metadata, lock plan, respond to manager disputes, add explicit unplanned work, reconcile outcomes, carry forward unfinished work.
- Success state: A locked and reconciled weekly plan whose commitments are strategically linked and historically auditable.
- Failure state: Plan cannot lock because commitments lack Supporting Outcomes, manager flags misalignment, or reconciliation is incomplete.

Classification: locked decision.

## Primary User: Direct Manager

- Role: Manager with one or more direct reports.
- Goal: Detect alignment drift, review locked plans on time, flag questionable commitments, resolve disputes, and track reconciliation risk.
- Context: Uses the Manager Alignment Command Center for direct-report-only visibility.
- Pain points: Current tooling does not make strategic coverage, review lag, and reconciliation risk visible in one place.
- Workflow: View direct-report roll-up, monitor locked/unlocked/reconciling/reconciled states, review locked plans, comment, flag `Needs Revision` or `Misaligned`, resolve disputes, inspect heatmap and risk filters.
- Success state: Direct reports' plans are reviewed on time, misalignment is handled through structured disputes, and RCDO coverage is visible.
- Failure state: Review becomes overdue, disputes remain unresolved, or manager cannot distinguish planned, unplanned, carried-forward, blocked, and misaligned work.

Classification: locked decision.

## Operator / Platform Actor

- Role: PA host application and production platform owner.
- Goal: Load WC as a Vite Module Federation remote while owning shell-level concerns.
- Context: Production integration, not standalone assessment.
- Owns: Shell navigation, global routing, Auth0/session context, production monitoring via LogRocket and Loki, workspace orchestration via Yarn Workspaces and Nx.
- WC must not own: PA shell navigation, global route orchestration, production monitoring setup, or production workspace topology.

Classification: locked decision from PRD assumption.

## Admin / Data Owner

- Role: Future owner of RCDO hierarchy and employee/manager relationships.
- Goal: Provide authoritative strategy hierarchy and reporting relationships.
- MVP treatment: No RCDO admin UI. RCDO data and employee reporting relationships are seeded locally as read-only reference/demo data.
- Production treatment: Preserve integration boundaries for future PA strategy service, OKR platform, CSV import, or admin import.

Classification: MVP simplification / open production integration question.

## Non-Human Actors

### Auth0 / Identity Provider

- Production role: Issues JWTs that identify the current user and relevant authorization claims.
- MVP/deployed assessment role: Hybrid adapter validates real Auth0 JWTs when configured and falls back to seeded persona mode when Auth0 env vars are absent.
- Demo persona identity is accepted by the backend only when `DEMO_AUTH_ENABLED=true`.
- Claim names are configurable; the application requires stable identity fields such as employee ID and role plus manager-relationship lookup.

Classification: locked decision.

### Outlook Graph API

- Role: Creates or syncs planning, reconciliation, and manager review-block calendar events.
- MVP/deployed assessment role: Hybrid adapter calls real Microsoft Graph when credentials/scopes are configured and otherwise uses a deterministic demo/failure adapter.
- Real Graph authorization model: app-only tenant/admin consent for user-calendar writes.
- Data persisted by WC: sync status, Graph event IDs, failure details safe for display/logging, retry state, and related plan/review context.
- Constraint: Graph failures are non-blocking for the core weekly lifecycle.

Classification: locked decision.

### Outlook Sync Worker

- Role: Separate Spring Boot worker deployed on EKS that consumes SQS Outlook sync jobs, calls the Graph/demo adapter, and updates Outlook Calendar Sync Records.
- Queue path: Core lifecycle mutation writes a sync record, publishes lifecycle event through SNS, and SQS subscription delivers work to the sync worker.
- Retry path: Manual retry republishes or requeues from the existing sync record.
- Constraint: Must avoid duplicate events by using persisted sync records and Graph event IDs where available.
- Failure path: SQS DLQ captures repeatedly failed messages; MVP has no admin redrive UI.

Classification: proposed recommendation.

### Weekly Plan Generation CronJob

- Role: Kubernetes CronJob deployed on EKS that invokes the plan generation job/API for the current Monday-Sunday week.
- Constraint: Database uniqueness on employee plus week start date remains the final idempotency guard.

Classification: locked decision.

## Standalone Identity Strategy

The standalone/deployed assessment demo uses seeded users plus a local persona switcher, while preserving a production Auth0 adapter boundary.

- The frontend can switch among seeded IC and manager personas for demo.
- The backend authorization model still evaluates employee identity, manager direct-report relationships, role, and resource ownership.
- Production Auth0 JWT parsing is represented as an adapter boundary rather than replaced by hardcoded user assumptions.
- When Auth0 env vars are configured, the backend validates real JWTs.
- When Auth0 env vars are absent and `DEMO_AUTH_ENABLED=true`, the backend accepts the seeded persona identity header.
- When demo mode is disabled, the seeded persona header is rejected.
- IDOR and manager scoping tests must use the same seeded identity model.

Rationale:

- Real Auth0 setup is high demo risk for a one-week build.
- Hardcoding one user would make manager authorization and cross-team denial hard to prove.
- The architecture can still specify the production claim contract as an open integration detail.

Classification: locked decision, confirmed by user on 2026-06-02.

## Permission Matrix

| Actor | Can Do | Cannot Do | Risk |
|---|---|---|---|
| IC | Manage own draft plan and commitments, lock own plan, add unplanned work after lock, reconcile own work, respond to disputes | Access another IC's private plan by ID, edit locked planned baseline, resolve manager dispute | IDOR, baseline mutation |
| Direct Manager | View scoped direct-report draft and locked plan details, review locked plans, comment, flag alignment, resolve disputes, inspect heatmap/roll-up | Access non-direct-report plans, silently rewrite IC Supporting Outcome links, perform formal review before lock | Cross-team data leakage, untracked manager override |
| PA Host | Provide shell/session/routing context in production | Own WC domain state or duplicate WC lifecycle logic | Boundary confusion |
| RCDO Data Owner | Provide future authoritative strategy hierarchy | Edit RCDO through MVP UI | Scope creep |
| Outlook Graph API | Create/update/cancel calendar touchpoints when authorized | Block WC lifecycle on failure, expose secrets to user | Integration fragility, secret leakage |
| Outlook Sync Worker | Consume SQS sync jobs, call Graph/demo adapter, update sync records | Duplicate events, mutate core plan state after failure | Idempotency failure |
| Weekly Plan Generation CronJob | Generate idempotent weekly plan shells | Create duplicate plans or mutate commitments | Scheduler/idempotency failure |

Classification: proposed recommendation.

## Draft Visibility

Managers can see full draft commitment details for direct reports before lock, but cannot perform formal review, start the review SLA, flag `Needs Revision`, flag `Misaligned`, or open an alignment dispute until the plan is locked.

Rationale:

- This gives managers early planning visibility without treating an unfinished draft as submitted work.
- The architecture must separate read visibility from review/dispute mutation permissions.
- Dashboard filters may include draft plans, but review-status fields only become actionable after lock.

Classification: locked decision, confirmed by user on 2026-06-02.

## MVP Reviewer Roles

MVP includes only two human workflow roles: IC and direct manager.

HR/admin reviewers, skip-level managers, leadership viewers, and cross-team auditors are deferred. Platform/admin remains an integration/data-owner concept, not a user-facing workflow role in MVP.

Rationale:

- CEO review explicitly deferred escalation/pass-up until reporting-chain hierarchy and leadership visibility rules are known.
- Direct-manager-only scoping is simpler to authorize, test, and explain.
- Broader visibility would require source-of-truth hierarchy rules that are not specified in the PRD.

Classification: locked decision, confirmed by user on 2026-06-02.

## IC Visibility Into Team Alignment

ICs do not see the team-level RCDO coverage heatmap in MVP.

ICs see their own weekly plan, their own commitments, manager comments/disputes on their own commitments, and the RCDO hierarchy context needed to choose Supporting Outcomes. The manager command center remains the team visibility surface.

Rationale:

- Keeps authorization straightforward for the one-week MVP.
- Avoids peer-plan privacy and social-comparison questions.
- Preserves the manager dashboard as the primary team alignment surface.

Classification: locked decision, confirmed by user on 2026-06-02.

## User Questions Still Open

- What exact Auth0 claim-name defaults should the configurable mapper use?
- What root domain will be available for deployed `wc.<root-domain>` and `api.wc.<root-domain>`?

Classification: open question.
