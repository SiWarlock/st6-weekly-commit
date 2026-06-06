# ST6 Weekly Commit — System Overview

> **Architecture sentence:** *Every locked weekly commitment maps to exactly one Supporting Outcome; managers see alignment drift in a direct-report command center; calendar sync and manager review are visible but never block the IC weekly lifecycle.*

## What it is

ST6 Weekly Commit is a **strategy-enforced weekly-alignment system** — it replaces the weekly Check-in / Priorities / Objectives slice of 15Five. An individual contributor (IC) builds a weekly plan of commitments; the plan cannot **lock** unless **every** planned commitment links to a *Supporting Outcome* in the company's RCDO strategy hierarchy (Rally Cry → Defining Objective → Supporting Outcome). Managers get a **direct-report command center** and an **alignment heatmap** that surface drift, overdue reviews, and disputes. Locking a plan also tries to drop a matching **Outlook calendar event** — but that sync, and manager review, are *visible-but-non-blocking*: they can never fail the IC's core lifecycle.

## At a glance

- **Stack:** Java 21 · Spring Boot 3.3 (Spring MVC, Spring Data JPA, Spring Security OAuth2 resource server) · PostgreSQL 16 + Flyway · React 18 + Vite 5 (Module-Federation remote) + RTK Query + Tailwind/Flowbite · Terraform (AWS) + EKS + GitHub Actions.
- **Shape:** a backend **service** (one `wc-api` image that runs as the REST API *or* as a batch job by Spring-profile flip) + a separate **`wc-sync-worker`** deployable + a **micro-frontend remote** (`wc-web`) + IaC. A small-services monorepo, not a monolith.
- **Entry points:** HTTPS REST under `/api/*` (7 controllers); an SQS message consumed by the worker; three batch entry points (weekly cron, DB migration job, projection-rebuild job); and the browser UI mounted by a platform host (or run standalone for the demo).
- **Code size:** ~511 indexed files — Java backend (`shared`/`api`/`worker` Gradle modules), TypeScript frontend (~150 files), Terraform/k8s infra, and a Cypress+Cucumber acceptance suite.

## The layers

```
                         ┌──────────────────────────────────────────────┐
   Browser (IC/Manager)  │  09 Frontend (wc-web) — React MF remote       │
        │                │  RTK Query slices = the typed wire contract   │
        │ HTTPS /api/*    └──────────────────────────────────────────────┘
        ▼
 ┌───────────────────────────────────────────────────────────────────────┐
 │  wc-api (Spring Boot)                                                   │
 │                                                                        │
 │  02 API & Web ──▶ 03 Application & Lifecycle ──▶ 01 Domain & Persistence│
 │  (controllers,      (PlanLifecycle, Commitment,    (JPA entities,       │
 │   DTOs, RFC-7807)    Dispute, Review services)      enums, repos,       │
 │        │                   │      │                 Flyway) ──▶ Postgres│
 │        │            04 Authorization, Identity & Audit (every access)   │
 │        │                   │                                            │
 │        └──▶ 05 Manager Command Center & Read Projections (read model)   │
 │                            │ on lock                                    │
 │  07 Scheduled Jobs         ▼                                            │
 │  (cron / migrate /  06 Calendar Sync ──SNS──▶ [SQS] ──▶ wc-sync-worker ─┼─▶ MS Graph
 │   rebuild)          (pointer-only)                      (06)            │
 │  08 Comments (PARTIAL — backend not built)                             │
 └───────────────────────────────────────────────────────────────────────┘
        ▲                                                          ▲
        └──────────────── 10 Infrastructure & Deployment ─────────┘
          (Terraform: VPC/EKS/RDS/SNS-SQS/S3+CF/IRSA/Secrets · k8s · GitHub Actions)
```

| # | Layer | Responsibility (one line) | Doc | Arch § |
|---|---|---|---|---|
| 01 | Domain Model & Persistence | The `shared` module: JPA entities, enums, repos, Flyway schema — the single source of truth for structure | [01-domain-persistence.md](01-domain-persistence.md) | §3, §4 |
| 02 | API & Web Layer | 7 thin REST controllers, request/response DTOs + mappers, validation, RFC-7807 error model | [02-api-web.md](02-api-web.md) | §5, App. B |
| 03 | Application & Lifecycle Services | The transactional services that own every state change — and enforce safety rules #1/#2/#6 | [03-application-lifecycle.md](03-application-lifecycle.md) | §3, §8 |
| 04 | Authorization, Identity & Audit | Who the caller is, what they may touch (IDOR-safe #3), demo gating (#5), tamper-evident audit (#7) | [04-authorization-identity-audit.md](04-authorization-identity-audit.md) | §6, §15, §16 |
| 05 | Manager Command Center & Read Projections | Pre-computed read models for the command center, heatmap, drilldown; OVERDUE derived not stored (#6) | [05-manager-projections.md](05-manager-projections.md) | §9 |
| 06 | Calendar Sync & Messaging | The lock→SNS→SQS→worker→MS Graph pipeline; never blocks the lifecycle (#4); pointer-only payloads (#7) | [06-calendar-sync-messaging.md](06-calendar-sync-messaging.md) | §10, §16 |
| 07 | Scheduled Jobs & Batch Entry Points | The non-HTTP runs of the `wc-api` image: weekly cron, migration job, projection rebuild | [07-scheduled-jobs.md](07-scheduled-jobs.md) | §D.4–D.6, §13 |
| 08 | Comments & Collaboration **(PARTIAL)** | Flat per-plan/commitment threads — *specified + frontend-built, backend HTTP surface never written* | [08-comments-collaboration.md](08-comments-collaboration.md) | §11 |
| 09 | Frontend (wc-web) | The React Module-Federation remote + standalone demo shell + the `wc-e2e` acceptance suite | [09-frontend.md](09-frontend.md) | §7, C.4–C.5 |
| 10 | Infrastructure & Deployment | Terraform AWS topology, k8s manifests, IRSA/Secrets, the one GitHub Actions deploy pipeline | [10-infrastructure-deployment.md](10-infrastructure-deployment.md) | §12, §13 |

## How it fits together

Follow one IC plan from draft to a manager's screen:

1. **The IC opens the UI** (09). The React remote authenticates (Auth0 in prod, a demo persona locally), and every screen is rendered from typed **RTK Query** slices whose TypeScript DTOs mirror the backend's wire contract. The UI never re-derives business rules — it renders only what the server says is allowed (`allowedActions[]`).
2. **The IC adds commitments and links each to a Supporting Outcome.** Each request hits a thin controller (02), which validates the DTO and delegates immediately to a service (03). Before any mutation, the service calls the **central authorizer** (04), which confirms the caller owns the plan — an unauthorized access returns a generic `404` (never reveals existence) plus an audit row.
3. **The IC locks the plan** (`PlanLifecycleService.lock`, 03). This is the system's load-bearing gate: lock is rejected `409` unless there is ≥1 planned commitment **and every one links a Supporting Outcome** (safety rule #1). After lock, the planned baseline is immutable (#2). The mutation, the audit event, the **read-projection refresh** (05), and the **sync publish** (06) all happen in/around one transaction.
4. **Calendar sync fans out** (06). *Only after* the lifecycle transaction commits, the API publishes a tiny **pointer** message — `{syncRecordId, eventKind, env, traceId}`, never any calendar text or PII (#7) — to SNS. It reaches SQS, where the separate **`wc-sync-worker`** reloads the durable sync record by id and calls **MS Graph** to create the event. Any failure lands as a `FAILED` (retryable) row and **never throws back into the lifecycle** (#4).
5. **The manager opens the command center** (05). Rather than re-aggregate raw commitments, the manager's rows and heatmap cells come from **pre-computed projection tables** (`manager_plan_summary`, `manager_heatmap_cell`) that were refreshed in step 3. Every query is hard-scoped to the authenticated manager's own reports (#3), and `OVERDUE`/risk are computed at read time from a `Clock` — never stored (#6).
6. **Out of band**, a weekly **cron** (07) creates next week's empty `DRAFT` plan shells, and one-shot **migration**/**rebuild** jobs run from the same image under different Spring profiles. All of it runs on **EKS**, provisioned by Terraform with per-workload **IRSA** identities and **Secrets Manager**-mounted credentials (10).

## Cross-cutting concerns

- **Authorization & identity (04)** thread through *every* request: a JWT (or demo header) becomes a typed `UserPrincipal`, and `DomainAuthorizationService` is the single chokepoint for self/direct-report scoping. IDOR-safe denials return `404`.
- **Audit (04 + 01)** — sensitive actions and every authorization denial write an `audit_event` with strictly **no tokens/secrets/notes/PII** in `metadata_json` (#7).
- **Error model (02)** — one uniform RFC-7807 `application/problem+json` shape with a stable machine-readable `code` for every failure.
- **The typed contract** — the backend DTOs (App. B), the Java enums (`shared`), and the frontend `dtos.ts` are a cross-document invariant: a field change requires edits in all three plus an ARCHITECTURE § in the same change.
- **Profiles** — the same `wc-api` image is api / generation-cron / flyway-migrate / rebuild depending on `SPRING_PROFILES_ACTIVE` + `--app.job`; the deploy profile is `aws`.
- **Testing** — JUnit 5 + Testcontainers (backend), Vitest (frontend), and a Cypress+Cucumber acceptance suite (`wc-e2e`) whose 7 `.feature` files map 1:1 to REQ IDs.

## Key decisions & trade-offs

- **Lock is enforced in the service layer, not by a DB constraint.** `weekly_commitment.supporting_outcome_id` is a *nullable* FK (links are added before lock); the "every planned commitment must link" rule is a service-layer precondition (`409 UNLINKED_PLANNED_COMMITMENT`), not a `NOT NULL`. Deliberate — but a reader shouldn't assume the DB enforces it.
- **Read models are pre-computed, refreshed in-transaction.** Manager views read from projection tables kept fresh on every lifecycle write, with a from-source rebuild job as the byte-identical fallback. Trades write-time work + a rebuild path for fast, scope-safe manager reads.
- **Sync is asynchronous and fail-open by construction.** The lifecycle commits first; the publish is the only place that swallows failures; the wire payload is a 4-field record with no free-text. This is how #4 and #7 are *structurally* guaranteed rather than merely intended.
- **The frontend is allowed to lead the backend** ("dormant-until-emitted"): UI affordances gate on server `allowedActions[]` and ship fully built+tested while the backend half lands later. This pattern is healthy — *except* where the backend half never lands at all (see Comments, below).

### Drift between the architecture and the code (flagged, not resolved)

The deep read surfaced these gaps between `ARCHITECTURE.md` and the running code. The three **HIGH** items are genuine missing functionality; consult each layer doc for evidence.

| Sev | Drift | Where | Doc |
|-----|-------|-------|-----|
| **HIGH** | **Comments backend never built** — §11/B.9/E20/E21 specified and the frontend is fully built, but there is **no `CommentController`/`CommentService`**; the authorizer methods are dead code and the `COMMENT` allowed-action is never emitted. Works only against MSW mocks. | 08 | [08](08-comments-collaboration.md) |
| **HIGH** | **Outlook manual-retry + read endpoints unimplemented** — §10 + `GET /api/outlook-sync` (E22) + `POST /api/outlook-sync/{id}/retry` (E23) + the retry state machine are specified, but no controller/DTO/retry path exists. `FAILED` is terminal; `RETRY_REQUESTED` is enum-only. | 06 | [06](06-calendar-sync-messaging.md) |
| **HIGH** | **Perf-seed job runner missing** — `job-perf-seed.yaml` passes `--app.job=generate-perf-seed` but no runner matches it, and it omits `web-application-type=none`, so a launched container would hang. Opt-in/excluded from deploys, so normal deploys are unaffected. | 07 | [07](07-scheduled-jobs.md) |
| MED | **ProblemDetail `fieldErrors` shape** — B.21 shows an array of `{field,code,message}`; the code emits a `Map<String,String>` (object keyed by field, no per-error `code`). | 02 | [02](02-api-web.md) |
| MED | **Worker redelivery guard / Graph create-vs-update** — §10 specifies a status-based guard + `graphEventId` reuse to UPDATE; the code guards on `graphEventId != null` and always CREATEs (no update branch). | 06 | [06](06-calendar-sync-messaging.md) |
| MED | **CloudWatch log forwarding absent** — log groups are provisioned but no Fluent Bit / Container Insights forwarder exists, so container logs don't reach CloudWatch (deferred to Phase 13). | 10 | [10](10-infrastructure-deployment.md) |
| MED | **`wc-e2e` selectors mismatch** — `selectors.ts` `data-cy` keys don't match the realized `wc-web` attributes; the suite is authored-not-green (deferred to CI/Phase 12). | 09 | [09](09-frontend.md) |
| LOW | Several minor doc/code phrasings: rule-#1 not a DB `NOT NULL` (by design); §6 names 2 authz codes vs 7 in code; `403/404`→`404`; `ADD_UNPLANNED` `LOCKED` vs `LOCKED+RECONCILING`; `AllowedAction` vocab placeholders; no demo-failure Graph port; calendar deep-link not wired; migration job lacks `activeDeadlineSeconds`; "five routes" loose count; worker IRSA `db+graph` (reconciled); `aws` not `prod` profile. | various | each layer's *Gotchas* |

> _Note: a "drift" the deep read flagged about **enum count (16 vs 18)** was an error in the generation brief, **not** the code — `ARCHITECTURE.md` and `apps/wc-api/CLAUDE.md` both correctly say 16. The code is right._

## Map

Read in order (data → services → cross-cutting → integration → batch → UI → infra):

1. [01 — Domain Model & Persistence](01-domain-persistence.md)
2. [02 — API & Web Layer](02-api-web.md)
3. [03 — Application & Lifecycle Services](03-application-lifecycle.md)
4. [04 — Authorization, Identity & Audit](04-authorization-identity-audit.md)
5. [05 — Manager Command Center & Read Projections](05-manager-projections.md)
6. [06 — Calendar Sync & Messaging](06-calendar-sync-messaging.md)
7. [07 — Scheduled Jobs & Batch Entry Points](07-scheduled-jobs.md)
8. [08 — Comments & Collaboration](08-comments-collaboration.md) *(partial — drift)*
9. [09 — Frontend (wc-web)](09-frontend.md)
10. [10 — Infrastructure & Deployment](10-infrastructure-deployment.md)

---
_Generated by `/layer-docs` (initial run) against commit `3b919a9` on 2026-06-04. Anchored to code; drift between `ARCHITECTURE.md` and the code is flagged in each layer's *Gotchas* section, never silently reconciled._
