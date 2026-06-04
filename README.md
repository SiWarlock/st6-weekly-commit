# ST6 Weekly Commit Module

A strategy-enforced weekly-alignment system that replaces the weekly Check-in / Priorities / Objectives slice of 15Five. ICs create weekly commitments that must each map to a **Supporting Outcome** in the RCDO strategy hierarchy (Rally Cry → Defining Objective → Supporting Outcome); managers get a direct-report **Alignment Command Center**.

> **Architecture posture:** *Every locked weekly commitment maps to exactly one Supporting Outcome; managers see alignment drift in a direct-report command center; calendar sync and manager review are visible but never block the IC weekly lifecycle.*

It's a production-shaped AWS micro-frontend: a React SPA (Module Federation remote, also runs standalone), a Spring Boot REST API + a separate Outlook-sync worker on EKS, RDS PostgreSQL, and non-blocking SNS→SQS Outlook Graph calendar sync — deployed behind CloudFront with real Auth0 OAuth.

---

## Repo layout

| Path | Area | README |
|---|---|---|
| `apps/wc-web/` | **Frontend** — React 18 / Vite 5 / TS-strict / RTK Query / Flowbite + Tailwind. A Module Federation remote that also runs standalone (local demo + the deployed SPA). | [`apps/wc-web/README.md`](apps/wc-web/README.md) |
| `apps/wc-api/` | **Backend** — Java 21 / Spring Boot 3.3 / Gradle multi-module (shared + api + worker). Spring MVC + JPA + Flyway; OAuth2 resource server. | [`apps/wc-api/README.md`](apps/wc-api/README.md) |
| `apps/wc-e2e/` | **Acceptance suite** — Cypress + Cucumber/Gherkin (rides the frontend toolchain). | — |
| `infra/` | **Infrastructure** — Terraform (AWS) + EKS/k8s manifests + GitHub Actions (OIDC). | [`infra/README.md`](infra/README.md) |
| `docs/` | Architecture contract, the task tracker, runbooks, briefs, sessions, design system. | see [Docs map](#docs-map) |

---

## Quick start — run it locally

**Fastest path (frontend, no backend):** the standalone SPA renders the full UI against an in-memory MSW mock layer with switchable demo personas.

```bash
yarn install                                          # repo root (Yarn workspaces)
cp apps/wc-web/.env.example apps/wc-web/.env.local    # required — defaults to VITE_AUTH_MODE=demo
yarn nx dev wc-web                                    # http://localhost:5173
```

Switch personas (a manager + ICs) to exercise the IC weekly plan, the manager command center + heatmap, and the live disputes loop. Full detail — including running the SPA against a real local API — in [`apps/wc-web/README.md` → Running locally](apps/wc-web/README.md#running-locally).

**Full local stack (real backend):** run the Spring Boot API + a local Postgres, then point the SPA at it (`VITE_USE_MOCKS=false` + `VITE_API_BASE_URL`). See [`apps/wc-api/README.md`](apps/wc-api/README.md) for the backend dev loop (Gradle, Flyway, Testcontainers) and the JDK toolchain note in [`docs/runbooks/jdk21-toolchain-setup.md`](docs/runbooks/jdk21-toolchain-setup.md).

---

## Architecture

The binding design contract is [`ARCHITECTURE.md`](ARCHITECTURE.md) (§1–§22 + Appendix A–F) — the data model, the plan lifecycle (`DRAFT → LOCKED → RECONCILING → RECONCILED`), the authorization model, the projection/derivation rules, and the seven key safety invariants. The phase plan + build state live in [`MVP_TASKS.md`](MVP_TASKS.md).

**AWS topology:** the React SPA is a static build served from S3 via CloudFront; the API + Outlook-sync worker run on EKS; RDS PostgreSQL 16 is the database; SNS → SQS → DLQ carries non-blocking calendar-sync events; a pre-deploy Flyway Job owns the schema + the demo seed. Real authentication is **Auth0 OAuth** (the SPA logs in via PKCE; the API validates the JWT and resolves the seeded employee). See `ARCHITECTURE.md §7` (frontend/MFE), §10 (sync), §12–§16 (infra/security).

## Tech stack

| Layer | Frontend (`apps/wc-web`) | Backend (`apps/wc-api`) | Infra (`infra`) |
|---|---|---|---|
| Runtime / build | Node 20 · Yarn + Nx · Vite 5 | Java 21 · Gradle multi-module | Terraform (AWS) |
| Framework | React 18 · RTK Query · Flowbite + Tailwind | Spring Boot 3.3 · Spring MVC · JPA/Hibernate · Spring Security (OAuth2) | EKS / k8s manifests |
| Data / migrations | — (RTK Query is the API contract) | PostgreSQL 16 · Flyway | RDS · S3/CloudFront · SNS/SQS |
| Tests / verify | Vitest · Cypress/Cucumber (`apps/wc-e2e`) | JUnit 5 · Testcontainers · JaCoCo | `terraform validate`/`plan` · GitHub Actions (OIDC) |

---

## Deploying to AWS

The deploy is a **HITL** flow driven by three runbooks (in order), plus the per-area deploy notes:

1. **[`docs/runbooks/auth0-tenant-setup.md`](docs/runbooks/auth0-tenant-setup.md)** — stand up the external tenants: **Auth0** (the API/audience, the SPA app, the 7 test users, the post-login Action that emits the `employee_id` claim) and, for live Outlook sync, the **M365/Entra Graph** app. Produces the secret values the deploy consumes.
2. **[`docs/runbooks/fresh-aws-account-to-deploy-ready.md`](docs/runbooks/fresh-aws-account-to-deploy-ready.md)** — bootstrap a fresh AWS account to deploy-ready (state backend, OIDC, the domain) so `terraform apply` can run.
3. **[`docs/runbooks/deploy-and-smoke.md`](docs/runbooks/deploy-and-smoke.md)** — run the pipeline, populate the secrets, and smoke the deployed stack.

Area specifics: the **frontend** build/serve (the standalone SPA → S3/CloudFront + the Auth0 OAuth flow) is in [`apps/wc-web/README.md` → Deploying to AWS](apps/wc-web/README.md#deploying-to-aws-standalone-spa--real-oauth); the **infra** (Terraform + EKS/k8s + CI) is in [`infra/README.md`](infra/README.md); the **backend** deploy config (profiles, the CSI-mounted secrets, the migration Job) is in [`apps/wc-api/README.md`](apps/wc-api/README.md).

---

## Docs map

| Doc | What |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | The binding design contract (§1–§22 + Appendix A–F). |
| [`MVP_TASKS.md`](MVP_TASKS.md) | Phase plan + build state + the round Log. |
| `AI_USAGE.md` | AI-assistance log (deliverable — authored in Phase 13). |
| `docs/runbooks/` | Operational procedures (the deploy set a/b/c + the JDK toolchain note). |
| `docs/briefs/` · `docs/sessions/` | The per-slice `/tdd` design briefs + the chronological session docs. |
| `docs/design/cadence-design-system/` | The Cadence design system (the frontend's binding visual source of truth). |
| `CLAUDE.md` | Global project conventions + the agent-team coordination rules. |

---

*Built across three code areas (frontend / backend / infra) under a TDD-disciplined agent-team workflow. The architecture is treated as a binding contract — see `ARCHITECTURE.md` and each area's `CLAUDE.md` + `LESSONS.md`.*
