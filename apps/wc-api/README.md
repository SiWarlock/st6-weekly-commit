# wc-api — ST6 Weekly Commit backend

The strategy-enforcement backend for the ST6 Weekly Commit Module: ICs draft weekly commitments that must each link to a Supporting Outcome (RCDO hierarchy), a plan moves `DRAFT→LOCKED→RECONCILING→RECONCILED`, and managers get a direct-report Alignment Command Center. Outlook calendar sync is non-blocking (it never blocks the IC lifecycle).

**Stack:** Java 21 · Spring Boot 3.3 (MVC · Spring Data JPA + Hibernate · Spring Security OAuth2 resource server) · Flyway · PostgreSQL 16 · Gradle multi-module · JUnit 5 + Testcontainers.

> **Binding contract:** [`ARCHITECTURE.md`](../../ARCHITECTURE.md) (§3 lifecycle · §4 schema · §5 endpoints/errors · §6 authz · §9 manager projections · §10 sync · Appendix A models / B DTOs).
> **Area conventions + forbidden patterns:** [`CLAUDE.md`](CLAUDE.md). **Banked engineering lessons:** [`LESSONS.md`](LESSONS.md).

---

## Module layout

```
apps/wc-api/                  Gradle composite (settings.gradle: shared, api, worker)
├── shared/   JPA entities · 16 enums · DTOs · repos · SyncJobPointer    (depended on by api + worker)
├── api/      controllers → services → repositories; security/jwt/cors config;
│             jobs (plan-shell generation, projection rebuild); auth; sns; web (RFC-7807)
└── worker/   wc-sync-worker — the SQS→Graph calendar-sync consumer (separate deployable)
```

Dependency direction (top depends on bottom, never reverse): `api → shared`, `worker → shared` (no `api ↔ worker` edge); `controllers → services → repositories → entities`. Enforced by a module-boundary check in the build.

The **migration Job** and the **generation CronJob** reuse the **`wc-api` image** via Spring profiles/args — never a third image.

---

## Local development

**Prerequisites:** JDK 21 (the Gradle toolchain resolves it via foojay; never a committed machine path — LESSONS §1), Docker (for Testcontainers + the local full stack).

**Run all commands from `apps/wc-api/`** (the repo-root composite exposes only the aggregate `check`/`build`; per-task targets live here — LESSONS §6).

### The quality gate (run before "done")

```bash
./gradlew check        # Spotless + SpotBugs + JaCoCo (≥80% line+branch/module) + module-boundary + all tests
```

This is the CI gate (§13). Not `build -x test` (JaCoCo verification is bound to `check` and needs tests to run).

### Run the full stack locally (recommended)

The one-command local stack — **Postgres + the migration Job (seeds the V5/V6 demo personas + fixtures) + wc-api on the `local` profile** — is provided via docker-compose; see [`infra/README.md`](../../infra/README.md) for the canonical `docker compose up` invocation and the up-smoke. It uses the **same image and the same migrate→seed path as production**, so the strategy-enforcement spine + manager surfaces run against real Postgres with the seeded demo data.

In `local` (and `demo`), **identity is the demo path** — `demo-auth.enabled=true` gates off the real Auth0 `JwtDecoder`, and you pass a persona header instead of a bearer token:

```bash
curl -s localhost:8080/actuator/health/readiness
curl -s -H 'X-Demo-Employee-Id: st6|dana-okafor' localhost:8080/api/me     # Dana (manager) + 6 reports
```

`X-Demo-Employee-Id` is accepted **only** when `DEMO_AUTH_ENABLED=true` (safety rule #5 — no production backdoor). The 7 seeded personas are `st6|<first>-<last>` (Dana → `st6|dana-okafor`, + Priya/Marco/Aisha/Tomas/Grace/Sam).

### Run just the api against your own Postgres

```bash
SPRING_PROFILES_ACTIVE=local \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/wc \
SPRING_DATASOURCE_USERNAME=wc SPRING_DATASOURCE_PASSWORD=wc \
./gradlew :api:bootRun
```

Run the migrations first (the api never migrates — `flyway.enabled=false`): launch the `wc-api` image with `--spring.profiles.active=flyway-migrate` (the sole Flyway-enabled profile — V1–V6 incl. the `db/demo-seed` personas, LESSONS §41), or use the compose `migrate` service.

---

## Spring profiles

| Profile | Used by | Notes |
|---|---|---|
| `local` | local dev | `demo-auth.enabled=true` (demo-header identity); worker consume runs **in-process** (no SNS/SQS); flyway off. |
| `demo` | standalone demo | deterministic demo adapters; demo-header identity. |
| `aws` | **every deployed k8s workload** | real Auth0 + datasource + SNS/SQS; secrets bind via `spring.config.import=optional:configtree:/mnt/secrets/` (one import binds datasource + auth0 + graph — LESSONS §42); `ddl-auto=validate`. |
| `flyway-migrate` | the migration Job only | the **sole** Flyway owner (forbidden-pattern #3); loads `db/migration` + `db/demo-seed`; `web-application-type=none` (one-shot). |

> ⚠️ A profile-named yaml only loads under that active profile. The deployed config lives in `application-aws.yml` (NOT `application-prod.yml`, which is dead under `SPRING_PROFILES_ACTIVE=aws` — LESSONS §42). The worker carries the same rule (092).

---

## AWS / deployment

The api + worker deploy to EKS (RDS PostgreSQL · S3/CloudFront for the SPA · SNS/SQS for sync · Secrets Manager + IRSA). The pipeline is `migration Job → api/worker Deployments → generation CronJob` (GitHub Actions OIDC). Infra topology + the Terraform/k8s layout: [`infra/README.md`](../../infra/README.md) + [`ARCHITECTURE.md`](../../ARCHITECTURE.md) §12/§13.

**First-deploy + tenant setup runbooks** (`docs/runbooks/`):
- [`auth0-tenant-setup.md`](../../docs/runbooks/auth0-tenant-setup.md) — Auth0 (API/SPA/7 users/post-login Action) + M365 E5 + Entra/Graph app registration.
- [`fresh-aws-account-to-deploy-ready.md`](../../docs/runbooks/fresh-aws-account-to-deploy-ready.md) — bootstrap → OIDC → state → domain → GitHub `production` Environment.
- [`deploy-and-smoke.md`](../../docs/runbooks/deploy-and-smoke.md) — run the pipeline + populate secrets + smoke.

**Sync pipeline status:** the lifecycle outbox (`SyncJobPointer`, pointer-only — rule #7) + the non-blocking afterCommit publish (rule #4) are built. **Wave-1** ships the deployable backend with a no-op SNS gateway + a DB-less worker; **Wave-2** wires the real SNS gateway → SQS consumer → MS Graph calendar adapter (the worker regains its datasource then). See `docs/planning/025`.

---

## Testing

- **Unit:** JUnit 5 + AssertJ + Mockito.
- **Integration / persistence:** Testcontainers PostgreSQL 16 (a shared singleton container; `ddl-auto=validate` proves entity↔schema fidelity — LESSONS §9). The demo seed is verified in an isolated container, never the shared clean-baseline harness (LESSONS §41).
- **Coverage:** JaCoCo ≥80% line+branch per module, bound into `check`.

---

## Conventions (see [`CLAUDE.md`](CLAUDE.md) for the full set)

- Lombok `@Getter`/`@Setter`/`@Builder` — **never `@Data`** (breaks Hibernate identity).
- **DTOs cross the API boundary, never JPA entities** (Appendix B).
- Lifecycle transitions live in **service methods** inside one transaction (never controllers).
- `OVERDUE` is **derived at read time** via an injectable `Clock` — never stored.
- An Outlook/SNS failure is **caught, never rethrown** into the core txn (rule #4).
- RFC-7807 errors with the named `code`s in §5/B.21; server-side Jakarta Bean Validation on every request DTO.

---

**Other areas:** [`apps/wc-web/README.md`](../wc-web/README.md) (React SPA / MFE remote) · [`infra/README.md`](../../infra/README.md) (Terraform + k8s + CI/CD) · the repo-root `README.md` (top-level entry point).
