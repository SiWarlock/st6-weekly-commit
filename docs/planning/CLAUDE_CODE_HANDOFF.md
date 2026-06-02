# Claude Code Handoff

> Status: Handoff artifact for `/arch-finalize`
> Date: 2026-06-02
> Project: ST6 Weekly Commit Module
> Current stage: `arch-draft` rough architecture complete / ready for adversarial finalization

## Mission For The Next Stage

Run `/arch-finalize` as the second architecture brain. Do not implement application code yet.

Your job is to read the PRD and every planning artifact, run a second-pass gap audit, challenge the rough draft, ask the human before changing load-bearing decisions, then produce the binding root `ARCHITECTURE.md`. Only after the finalized architecture is accepted should downstream task generation produce `MVP_TASKS.md`.

## Required Inputs To Read

Read these before drafting anything:

- `PRD.md`
- `docs/planning/PRODUCT_BRIEF.md`
- `docs/planning/USERS.md`
- `docs/planning/STAKEHOLDERS.md`
- `docs/planning/USER_FLOWS.md`
- `docs/planning/DOMAIN_MODEL.md`
- `docs/planning/REQUIREMENTS.md`
- `docs/planning/CONSTRAINTS.md`
- `docs/planning/EVALUATION_CRITERIA.md`
- `docs/planning/ASSUMPTIONS.md`
- `docs/planning/OPEN_QUESTIONS.md`
- `docs/planning/RESEARCH.md`
- `docs/planning/DECISIONS.md`
- `docs/planning/DATA_MODEL.md`
- `docs/planning/RISKS.md`
- `docs/planning/THREAT_MODEL.md`
- `docs/planning/ARCHITECTURE_DRAFT.md`
- `docs/planning/DIAGRAM_PLAN.md`
- `docs/planning/CLAUDE_CODE_HANDOFF.md`

Also review supporting context if useful:

- `docs/planning/OG_PRD.md`
- `docs/planning/OFFICE_HOURS_PRD_REFRAME.md`
- `docs/planning/15five-research.md`
- `docs/ceo-plans/2026-06-02-weekly-commit-module.md`, if present

## What Is Locked

Do not weaken these decisions without explicit human confirmation:

- WC is a Strategy-Enforced Weekly Alignment System, not a full 15Five replacement.
- RCDO means Rally Cry -> Defining Objective -> Supporting Outcome.
- Every locked planned commitment must map to a Supporting Outcome.
- Chess layer means priority, work type, confidence, alignment status, and manager note.
- Plan lifecycle is `DRAFT -> LOCKED -> RECONCILING -> RECONCILED`.
- Manager review status is separate from IC plan lifecycle.
- Manager dashboard scope is direct reports only.
- Managers can view drafts, but formal review/dispute starts only after lock.
- Planned baseline fields are immutable after lock; no unlock/amend flow in MVP.
- Unplanned post-lock commitments do not rewrite locked baseline.
- Carry-forward seeds the next Monday-Sunday week.
- Alignment disputes require manager note, IC response/revision/rationale, and explicit manager resolution.
- RCDO is read-only seeded reference data in MVP.
- Pass-up/escalation, leadership rollups, and RCDO admin are deferred.
- AWS topology is required: EKS, RDS PostgreSQL 16.4, S3/CloudFront, SNS/SQS/DLQ, Route 53/ACM, ECR, Secrets Manager.
- Frontend custom domain is `wc.${ROOT_DOMAIN}` and API custom domain is `api.wc.${ROOT_DOMAIN}`.
- Deployment uses Terraform and GitHub Actions.
- API and Outlook sync worker are separate Spring Boot workloads/images.
- Weekly shell generation runs as an EKS Kubernetes CronJob.
- Auth0 is the production auth boundary; demo identity mode is env-gated.
- Outlook Graph failures never block core workflow.
- Outlook Calendar Sync Record doubles as durable outbox for sync/retry.
- Queue messages are pointer payloads, not full calendar payloads.
- Manager command center uses synchronous read-model tables.
- REST JSON API uses explicit lifecycle command endpoints.
- Central service-layer domain authorization enforces IC self-access and manager direct-report scoping.
- Primary E2E tool is Cypress with Cucumber/Gherkin BDD; Playwright is optional/ad hoc.

## Open Questions To Preserve

These are still open, but none block architecture finalization:

| ID | Question | Finalizer Action |
|---|---|---|
| OQ-001 | Exact `ROOT_DOMAIN` value. | Keep as Terraform/deploy variable unless user supplies it. |
| OQ-002 | Whether to override default `us-east-1`. | Keep `us-east-1` default and variable override. |
| OQ-003 | Exact Auth0 claim-name defaults. | Keep configurable mapping and demo defaults. |
| OQ-004 | Real PA/PM remote pattern availability. | Mark as pre-implementation verification if unavailable. |
| OQ-005 | Real Microsoft 365 tenant/app registration availability. | Keep hybrid real/demo Graph adapter. |
| OQ-006 | Evaluator-specific demo-video or AI usage templates. | Keep generic `AI_USAGE.md` unless template appears. |

## Required Second-Pass Gap Audit

Before writing the final root architecture, audit at least these dimensions:

1. Requirement coverage: every MVP requirement in `REQUIREMENTS.md` maps to an architecture section.
2. User-flow coverage: every primary flow in `USER_FLOWS.md` has state, API, data, auth, and test coverage.
3. Lifecycle correctness: illegal transitions, baseline immutability, review status, overdue derivation, disputes, carry-forward.
4. Authorization: IC self-access, manager direct-report scoping, comment/dispute/review/heatmap IDOR cases.
5. Data model: uniqueness constraints, indexes, projection update rules, sync idempotency, audit events.
6. API contracts: endpoint paths, commands, DTO boundaries, error shapes, pageable manager views.
7. Frontend architecture: RTK Query only, Flowbite/Tailwind, Module Federation, standalone mode, no shell coupling.
8. Integration architecture: Graph adapter modes, SNS/SQS/DLQ, outbox/sync states, retry behavior, deep links.
9. AWS deployment: EKS workloads, RDS, S3/CloudFront, ALB, Route 53/ACM, Secrets Manager, CloudWatch.
10. CI/CD and local dev: Nx/Yarn, Gradle, Docker Compose, Testcontainers, GitHub Actions.
11. Performance: 200ms plan retrieval target, manager pagination, heatmap projection strategy, 2,000-record seed path.
12. Security and threat model: STRIDE coverage, demo-auth risk, safe Graph errors, XSS/input validation, secrets handling.
13. Scope pressure: trim order preserves the strategy-enforced alignment thesis and PRD-required infrastructure.

## Expected Final Outputs

The `/arch-finalize` stage should produce:

- Root `ARCHITECTURE.md` using the project's architecture template if one exists.
- A concise list of edits or corrections made from the rough draft.
- A short remaining-open-questions section.
- No application code.

After that, `/tasks-gen` or equivalent should produce:

- Root `MVP_TASKS.md`
- Implementation tasks sequenced by domain/data/API/frontend/integration/deploy/test
- Acceptance criteria linked back to requirement IDs and architecture sections

## Handoff Notes

- The original PRD's infrastructure stack is binding. Do not collapse this into a generic local-only app.
- Preserve the assessment-local distinction: WC runs standalone but remains structured as a Vite Module Federation remote.
- Do not implement PA-owned global shell concerns, LogRocket/Loki, or the full PA workspace.
- If AWS scope becomes too large, trim Graph polish before trimming AWS fidelity.
- If Graph or Auth0 real credentials are unavailable, the architecture still expects real adapters plus deterministic demo/failure adapters.
- Be careful not to turn manager review into a blocker for IC reconciliation. Missed manager review becomes visible/overdue, not a workflow stop.
- Be careful not to store `OVERDUE` as manager review source status unless the finalizer deliberately changes the decision. It is currently derived.
- Keep one open unresolved dispute per commitment while allowing historical disputes.
- Keep seeded RCDO read-only; no RCDO admin UI in MVP.
