# Stakeholders

> Status: Draft planning artifact
> Phase: 3 - Stakeholders and reviewers
> Date: 2026-06-02
> Sources: `PRD.md`, `PRODUCT_BRIEF.md`, `USERS.md`, Office Hours reframe, CEO review

## Stakeholder Matrix

| Stakeholder | Cares About | Would Reject If | Evidence Needed | Architecture Must Address |
|---|---|---|---|---|
| Product Owner | Strategy-enforced weekly alignment, coherent MVP scope, no full 15Five clone | Commitments can lock without Supporting Outcomes, manager command center is thin, lifecycle is vague | Requirements traceability, flows, acceptance criteria, deferred scope | RCDO linkage, IC workspace, manager command center, lifecycle, exclusions |
| Engineering Reviewer | Buildable one-week architecture, clear boundaries, testable invariants | Architecture is generic, lacks APIs/data model/state rules, or ignores mandated stack | Domain model, API contracts, data model, state machines, implementation boundaries | Backend/frontend modules, persistence, state transitions, test strategy |
| Security Reviewer | Auth0 boundary, direct-report scoping, sensitive notes/disputes, Graph secrets | IDOR is possible, manager can access non-direct reports, secrets are hardcoded, audit trail is missing | Threat model, authorization rules, security tests, audit/event model | Trust boundaries, resource authorization, Graph token handling, audit logging |
| Manager User Representative | Fast team visibility, review accountability, actionable alignment correction | Dashboard is only a list, heatmap is missing, overdue/dispute states are not visible | Manager flows, dashboard requirements, heatmap aggregation, review SLA behavior | Command center, filters, review SLA, disputes, reconciliation risk |
| IC User Representative | Lightweight planning, clear RCDO selection, fair reconciliation, visible manager feedback | Workflow feels like HR surveillance, lock/reconciliation is confusing, unplanned work is punitive | IC flows, lifecycle semantics, UX requirements, unplanned/carry-forward rules | Draft/lock/reconcile flow, own-plan visibility, comments/disputes, no peer heatmap |
| PA / Platform Owner | Module Federation compatibility and no duplicated shell concerns | WC hardcodes shell navigation, assumes standalone-only identity, duplicates PA monitoring/workspace | Integration contract, shared dependency strategy, Auth0 adapter boundary | Remote entry shape, host contract, standalone adapter, production boundaries |
| Demo / Assessment Evaluator | Production-shaped MVP, reliable demo, visible hard requirements, test evidence | Outlook fails silently, seeded data is unrealistic, feature set is too broad or too shallow | Demo path, seed strategy, test plan, Graph fallback, success/failure states | Local/demo architecture, seed data, acceptance tests, non-blocking Graph sync |

Classification: proposed recommendation.

## Stakeholder Assumptions

- The assessment evaluator is likely both a product and engineering reviewer.
- There is no specified HR/admin stakeholder for MVP workflow decisions.
- CISO/security review is inferred because Auth0, direct-report scoping, sensitive manager notes, and Graph secrets create meaningful security risk.
- PA/platform owner is inferred from the Module Federation and PA boundary requirements.

Classification: proposed recommendation / open question mix.

## Reviewer Questions Still Open

- Who is the highest-priority reviewer for architecture tradeoffs: product evaluator, engineering evaluator, security reviewer, PA/platform owner, or demo audience?
- Does the assessment require a live deployed app, or is local/demo plus video acceptable?
- Are there company-specific security/compliance expectations beyond the PRD?

Classification: open question.

## Reviewer Priority

When architecture tradeoffs conflict, engineering/evaluator review standards win.

Priority order for MVP tradeoffs:

1. Buildability, testability, stack fidelity, and clear handoff to Claude Code.
2. Product/demo clarity.
3. Security/platform rigor, while still meeting baseline Auth0/RBAC/Graph/Module Federation requirements.

Rationale:

- This is a one-week assessment, so the architecture must be executable without hidden chat context.
- The mandated stack, lifecycle rules, authorization model, and test plan need to be concrete enough for implementation.
- A strong demo matters, but not at the cost of unverifiable core invariants.

Classification: locked decision, confirmed by user on 2026-06-02.

