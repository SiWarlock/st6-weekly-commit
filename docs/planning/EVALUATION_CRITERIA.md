# Evaluation Criteria

> Status: Draft planning artifact
> Phase: 7 - Constraints, evaluation, and timebox
> Date: 2026-06-02
> Sources: `PRD.md`, original PRD pasted by user, `REQUIREMENTS.md`, `CONSTRAINTS.md`, CEO review, Office Hours reframe

## Primary Evaluation Standard

Engineering/evaluator review standards win when tradeoffs conflict.

The architecture and MVP should be judged first on:

1. Buildability within the one-week timebox.
2. Testability of lifecycle, authorization, and integration failure behavior.
3. Fidelity to the required stack.
4. Clear handoff quality for Claude Code.
5. Visible product proof that weekly commitments enforce strategic alignment.

Classification: locked decision, confirmed by user on 2026-06-02.

## Product Success Signals

| Criterion | Evidence |
|---|---|
| Strategy linkage is enforced | Locked plans cannot contain unlinked planned commitments. |
| IC workflow is complete | IC can draft, lock, reconcile, add unplanned work, and carry forward commitments. |
| Manager value is real | Manager command center shows direct-report status, review SLA, heatmap, disputes, and reconciliation risk. |
| Alignment correction is structured | Manager flags, IC responds, manager resolves. |
| Baseline truth is preserved | Locked planned fields cannot be silently mutated; unplanned work is additive. |
| Outlook is integrated but non-blocking | Graph sync success/failure states are visible and core lifecycle still succeeds on failure. |

Classification: locked requirement.

## Technical Success Signals

| Criterion | Evidence |
|---|---|
| Backend owns invariants | API/service tests reject invalid lifecycle, authorization, baseline, and RCDO-link mutations. |
| Authorization is robust | IDOR tests deny cross-IC and cross-manager access. |
| Domain model is explicit | Entities, relationships, and state machines are present in architecture and schema. |
| Query strategy is credible | Manager dashboard and heatmap use paginated/aggregated backend queries. |
| Micro-frontend boundary is preserved | Standalone app and Module Federation remote contract are both documented. |
| External integration failure is designed | Outlook sync records, safe errors, and manual retry are specified and tested. |
| Deployment is real | Frontend, API, worker, RDS PostgreSQL, SNS/SQS, and custom domains are deployed on AWS with documented environment configuration. |
| Quality gates are real | JaCoCo, Vitest, Cypress/Cucumber, ESLint/Prettier, Spotless, and SpotBugs run in CI. |
| Async integration is production-shaped | SNS/SQS, sync record outbox behavior, worker processing, and DLQ handling are represented. |

Classification: proposed recommendation.

## Demo Success Signals

| Demo Moment | Must Prove |
|---|---|
| IC tries to lock unlinked work | System blocks lock and explains missing Supporting Outcome. |
| IC locks linked plan | Plan becomes locked, baseline freezes, review due date appears. |
| Manager dashboard opens | Direct-report-only data appears with plan states and heatmap risk badges. |
| Manager flags misalignment | Dispute opens with required note and `REVIEWED_WITH_DISPUTES` status can satisfy SLA. |
| IC responds | IC can revise/rationalize but cannot resolve. |
| Manager resolves | Dispute leaves current-risk state. |
| IC reconciles | Outcomes, unplanned work, and carry-forward are persisted. |
| Outlook failure path appears | Warning/retry is visible and core workflow remains complete. |
| Unauthorized manager attempts access | Request is denied. |
| Deployed custom domains work | `wc.<root-domain>` serves the frontend and `api.wc.<root-domain>` serves API health/smoke endpoints. |

Classification: proposed recommendation.

## Disqualifying Or High-Severity Gaps

- A planned commitment can lock without a Supporting Outcome.
- A manager can access a non-direct-report plan, dispute, or heatmap cell by ID manipulation.
- Locked planned commitment fields can be silently changed.
- Outlook failure blocks plan lock, review, reconciliation, or carry-forward.
- Manager command center is only a generic list and lacks heatmap/review/dispute/reconciliation risk.
- Architecture ignores the mandated Java/Spring/PostgreSQL/React/Vite/RTK Query stack.
- Architecture ignores the original PRD's AWS stack: EKS, S3, CloudFront, SQS/SNS.
- Architecture ignores Cypress/Cucumber BDD, JaCoCo 80%, Vitest, ESLint/Prettier, Spotless, or SpotBugs expectations.
- Architecture treats WC as standalone-only and fails to preserve PA/Module Federation boundaries.
- Assessment cannot provide a deployed frontend and backend.
- Deployment has no custom domain path.

Classification: locked evaluation risk.

## Nice-To-Have Evaluation Wins

- Seeded demo data tells a clear story across multiple Defining Objectives.
- Manual Outlook retry shows idempotency by reusing the same sync record.
- Tests are organized around the PRD's lifecycle/security/integration concerns.
- Architecture includes clear anchors for task generation and code review.
- The UI distinguishes overdue review from unresolved dispute risk.
- Real Auth0 and Microsoft Graph adapters can be enabled without changing demo-mode code paths.
- DLQ and sync-record retry behavior can be demonstrated or inspected.

Classification: proposed recommendation.

## Remaining Open Evaluation Questions

- How much real Microsoft Graph integration evidence is expected versus a configured/mocked failure path?
- Are there specific demo-video length or format expectations?
- What exact root domain will be available for custom-domain deployment?

Classification: open question.
