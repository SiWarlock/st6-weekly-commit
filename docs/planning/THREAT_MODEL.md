# Threat Model

> Status: Draft planning artifact
> Phase: 14 - Trust boundaries plus STRIDE
> Date: 2026-06-02
> Sources: `RISKS.md`, `REQUIREMENTS.md`, `DECISIONS.md`, `DATA_MODEL.md`

## Trust Boundaries

| Boundary | What Crosses | Controls | Validation | Main Risks | MVP Mitigation |
|---|---|---|---|---|---|
| Browser -> WC API | JWT/demo header, REST requests, user-entered text | User browser, React app, Spring API | Auth0 JWT validation or env-gated demo auth; DTO validation; server authz | Spoofing, IDOR, XSS, tampering | Spring Security, domain auth service, validation, escaping, tests |
| Demo persona -> Backend identity | `X-Demo-Employee-Id` style identity header | Frontend persona switcher, backend auth adapter | `DEMO_AUTH_ENABLED=true` required | Production backdoor | Reject demo headers unless demo mode enabled |
| Auth0 -> WC API | JWT claims, issuer, audience, roles | Auth0 tenant, Spring Security | issuer/audience validation, configurable claims | Spoofed token, wrong audience, wrong claim mapping | OAuth2 Resource Server, configurable mapper, tests |
| WC API -> PostgreSQL/RDS | Domain writes, projections, audit events | Spring services, JPA, Flyway | Transactions, constraints, service invariants | Baseline mutation, projection drift, missing audit | DB constraints, service validation, audit_event, projection rebuild |
| WC API -> SNS/SQS | Sync record pointer messages | API publisher, AWS services | Sync record exists; message contains pointer only | Stale/sensitive payloads, lost publish | Sync record outbox, pointer payloads, DLQ |
| SQS -> Sync Worker | Sync job message | AWS SQS, Spring worker | load sync record from DB, state transitions | Duplicate processing, replay, failure loops | Graph event IDs, sync state machine, DLQ |
| Sync Worker -> Microsoft Graph | Calendar event payload, app-only token | Worker, Microsoft Graph tenant | app credentials/scopes, safe error handling | Token leakage, event spam, unauthorized calendars | Secrets Manager, app-only consent, safe messages, demo adapter |
| EKS workloads -> AWS Secrets Manager | DB/Auth0/Graph secrets | EKS IAM, Secrets Manager | IAM roles, secret injection | Secret leakage, overbroad IAM | Secrets Manager, least-privilege IAM, no hardcoded secrets |
| CloudFront/S3 -> Browser | Static Vite assets | CloudFront, private S3 bucket | OAC, TLS cert, cache controls | Asset tampering, broken SPA routing | S3 private bucket, OAC, ACM, SPA fallback |
| GitHub Actions -> AWS | Build artifacts, images, deploy credentials | GitHub, AWS IAM/ECR/EKS/S3 | CI secrets/IAM, quality gates | Supply-chain/deploy mistakes | GitHub Actions gates, ECR images, Terraform plan/apply discipline |

## STRIDE Analysis

### Spoofing

- Threat: attacker spoofs an IC/manager through demo identity header.
- Mitigation: demo header only accepted when `DEMO_AUTH_ENABLED=true`; real deployments validate Auth0 JWT issuer/audience.

- Threat: manager role spoofed through configurable Auth0 claims.
- Mitigation: claim mapper is explicit and tested; direct-report relationship is verified server-side rather than trusting role alone.

### Tampering

- Threat: user mutates locked planned commitment fields.
- Mitigation: service validation rejects immutable-field changes after lock; tests cover mutation attempts.

- Threat: SQS message body is modified or stale.
- Mitigation: queue payload references `syncRecordId`; worker loads current authoritative state from DB.

### Repudiation

- Threat: manager denies opening/resolving a dispute.
- Mitigation: `audit_event` records dispute open/respond/resolve with actor, entity, timestamp, and safe metadata.

- Threat: user disputes a lock/reconcile event.
- Mitigation: plan lifecycle timestamps plus audit events.

### Information Disclosure

- Threat: manager accesses another manager's team data.
- Mitigation: domain authorization service enforces direct-report scoping on every read/mutation.

- Threat: Graph/Auth0 secrets leak through logs/UI.
- Mitigation: Secrets Manager, safe error messages, no token logging, failure-code sanitization.

- Threat: IC sees team heatmap or peer commitments.
- Mitigation: IC role has no heatmap/team endpoints; API denies access server-side.

### Denial Of Service

- Threat: heatmap/dashboard queries overload API or DB.
- Mitigation: synchronous read-model tables, indexes, pagination, synthetic performance tests.

- Threat: Outlook sync failures loop indefinitely.
- Mitigation: sync state machine, retry count, SQS DLQ, manual retry from sync record.

### Elevation Of Privilege

- Threat: IC resolves manager dispute or accesses manager-only dashboard.
- Mitigation: service-level authorization checks and role/resource tests.

- Threat: demo auth remains enabled in production.
- Mitigation: architecture calls out deploy config; smoke checks should verify expected auth mode.

## Security Tests

- IC cannot read/mutate another IC plan by ID.
- Manager cannot read/mutate non-direct-report plans, reviews, disputes, comments, or heatmap cells.
- Demo identity header rejected when demo mode is disabled.
- JWT with wrong issuer/audience rejected when Auth0 mode is enabled.
- Locked baseline mutation rejected.
- Script/HTML content is escaped or safely rendered.
- Graph failure response does not expose token/secrets.
- Sync retry does not create duplicate events.

