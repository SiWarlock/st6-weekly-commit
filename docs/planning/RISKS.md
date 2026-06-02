# Risks

> Status: Draft planning artifact
> Phase: 14 - Security, risk, and failure modes
> Date: 2026-06-02
> Sources: `REQUIREMENTS.md`, `CONSTRAINTS.md`, `DECISIONS.md`, `DATA_MODEL.md`, `RESEARCH.md`

## Risk Register

| ID | Risk | Category | Severity | Likelihood | Mitigation | Fallback / Trim | Must Appear In Architecture |
|---|---|---|---|---|---|---|---|
| RISK-001 | Manager accesses non-direct-report plan, dispute, comment, or heatmap data by ID manipulation. | Security | Critical | Medium | Domain authorization service, service-layer checks, IDOR integration tests. | None; must fix. | Yes |
| RISK-002 | Locked baseline can be silently mutated. | Data/lifecycle | Critical | Medium | Immutable field validation after lock, unit/integration tests, audit events. | No unlock/amend MVP. | Yes |
| RISK-003 | Projection table drift makes manager dashboard inaccurate. | Data | High | Medium | Synchronous projection updates, internal rebuild job/CLI, projection tests. | Compute affected summary live if projection fails. | Yes |
| RISK-004 | Outlook Graph failure blocks core lifecycle. | Integration | High | Medium | Sync record outbox, SNS/SQS worker path, non-blocking mutations, failure UI. | Demo Graph adapter; trim review-block polish first. | Yes |
| RISK-005 | Outlook sync duplicates events on retry. | Integration | High | Medium | Unique sync record per owner/related/event kind, store Graph event ID, pointer payloads. | Manual cleanup plus audit; fix idempotency. | Yes |
| RISK-006 | App-only Graph permission is unavailable because tenant admin consent is not granted. | Integration/demo | High | Medium | Hybrid Graph adapter with deterministic success/failure fallback. | Demo adapter for assessment. | Yes |
| RISK-007 | Auth0 setup blocks deployed demo. | Demo/security | High | Medium | Hybrid Auth0 adapter and env-gated persona mode. | Demo auth mode with documented real Auth0 config. | Yes |
| RISK-008 | Demo persona header becomes production backdoor. | Security | Critical | Low | Only accept demo header when `DEMO_AUTH_ENABLED=true`; deployed prod docs disable it. | None; must fix. | Yes |
| RISK-009 | AWS/EKS/Terraform scope exceeds one-week build. | Scope/deployment | High | High | Thin Terraform, GitHub Actions, managed node group, trim Graph polish first. | Manual deploy scripts only as emergency deviation. | Yes |
| RISK-010 | CloudFront SPA routing or custom-domain cert blocks frontend deploy. | Deployment | Medium | Medium | S3 private bucket, OAC, ACM in `us-east-1`, Route 53 alias, fallback docs. | Generated CloudFront URL only if domain unavailable, document deviation. | Yes |
| RISK-011 | RDS PostgreSQL 16.4 unavailable in selected AWS region. | Deployment | Medium | Low | Default `us-east-1`, region availability check before Terraform apply. | Use closest supported 16.x only if evaluator accepts deviation. | Yes |
| RISK-012 | Full nested comments expand UI/API scope. | Scope | Medium | Medium | Materialized path schema, keep UI behavior minimal, no moderation/notifications. | One-level/flat UI rendering while preserving schema. | Yes |
| RISK-013 | Cypress/Cucumber BDD becomes brittle and slows CI. | Testing | Medium | Medium | Keep scenarios focused on core acceptance flows; use local Compose services. | Reduce scenario count, keep required suite. | Yes |
| RISK-014 | Manager heatmap read model hides wrong risk badges. | Product/data | High | Medium | Projection tests with seeded cases; drill-down explains source commitments. | Rebuild projections; add live drill-down verification. | Yes |
| RISK-015 | User-entered text creates XSS or unsafe rendering. | Security | High | Medium | Backend length validation, frontend escaping, tests with HTML/script content. | Block rich markdown in MVP. | Yes |
| RISK-016 | Sensitive Graph/Auth0 secrets leak in logs or UI. | Security | Critical | Low | AWS Secrets Manager, safe failure messages, no token logging. | None; must fix. | Yes |
| RISK-017 | `audit_event` table omits critical actions. | Audit | Medium | Medium | Explicit audit action list in architecture; tests assert key events. | Add events before final submission. | Yes |
| RISK-018 | PM remote contract differs from generic Vite remote. | Integration | Medium | Medium | Mark exact PM parity as verification item; generic remote contract documented. | Adjust once PA/PM example is available. | Yes |

## Trim Order

If the one-week build hits a wall, trim in this order:

1. Graph polish and automatic manager review-block event details.
2. UI polish and nonessential filters.
3. Projection rebuild niceties, while keeping synchronous projections.
4. Full nested comment UI depth, while preserving data model.

Do not trim:

- Required Supporting Outcome enforcement.
- Direct-report authorization.
- Locked baseline immutability.
- Manager command center core heatmap/review/dispute visibility.
- AWS deployment fidelity unless explicitly approved.

## Risk Validation

- Unit tests: lifecycle transitions, baseline immutability, SLA, dispute rules, projection aggregation.
- Integration tests: IDOR denial, RCDO links, projection rows, sync record retry, Graph failure.
- E2E/BDD: IC full loop, manager dispute loop, Outlook warning path, unauthorized manager denial.
- Deployment smoke: custom domains, API health, frontend load, persona mode, basic dashboard read.

