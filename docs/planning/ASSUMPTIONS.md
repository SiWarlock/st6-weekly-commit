# Assumptions

> Status: Draft planning artifact
> Phase: 9 - Assumptions and open questions
> Date: 2026-06-02
> Sources: `PRD.md`, original PRD pasted by user, planning interview decisions, `CONSTRAINTS.md`, `REQUIREMENTS.md`

## Locked Assumptions

| ID | Assumption | Category | Why It Matters | Validation Path | Fallback |
|---|---|---|---|---|---|
| ASM-001 | Assessment uses a lightweight Yarn Workspaces + Nx monorepo, not a full PA workspace replica. | Repo | Satisfies PRD tooling without overbuilding shell concerns. | Scaffold review and CI config. | Document production PA delta. |
| ASM-002 | WC defines a generic Vite Module Federation remote contract because no PM remote example exists in this workspace. | Micro-frontend | Prevents invented PA/PM details. | Verify against PA/PM repo if made available. | Adjust remote exposure during `/arch-finalize` or implementation. |
| ASM-003 | Deployed AWS target is EKS API, EKS worker, EKS CronJob, RDS PostgreSQL 16.4, S3/CloudFront frontend, SNS/SQS eventing, Route 53/ACM custom domains. | Deployment | Original PRD explicitly requires AWS services. | Terraform plan and deploy smoke. | Keep same service graph, simplify optional review-block event if needed. |
| ASM-004 | Root domain is available but not yet known. | Deployment | Custom domains are required, but the exact DNS zone is not in repo. | User provides root domain before deploy. | Use placeholder in architecture and Terraform variables. |
| ASM-005 | Auth0 claim names are unknown but configurable. | Identity | Avoids hardcoding PA/Auth0 details not in repo. | Configure mapper during deploy. | Demo persona mode remains available with `DEMO_AUTH_ENABLED=true`. |
| ASM-006 | Deployed demo must remain usable without real Auth0 or Graph credentials. | Demo | Protects demo reliability while preserving real adapter boundaries. | Run deployed demo with env vars absent. | Real integrations can be enabled when credentials are available. |
| ASM-007 | Microsoft Graph real path uses app-only tenant/admin consent for calendar writes. | Integration | Avoids adding per-user Outlook connect flow to MVP. | Verify app registration and permissions. | Demo Graph adapter for assessment if tenant setup is unavailable. |
| ASM-008 | Review SLA uses weekdays only in org timezone; holidays are future scope. | Lifecycle | Keeps due-date logic deterministic and testable. | Unit tests around Friday/weekend cases. | Add holiday calendar later. |
| ASM-009 | Demo seed uses one manager with 5-8 direct reports and separate synthetic performance seed for 2,000-record tests. | Data/demo | Keeps demo readable while meeting performance evidence needs. | Seed review and performance test fixture. | Increase synthetic seed only. |
| ASM-010 | `AI_USAGE.md` as Markdown satisfies the AI usage log deliverable. | Deliverable | PRD requires AI usage log but gives no template. | Include generated file in final deliverables. | Convert to evaluator-specific template if provided. |

## Dangerous Assumptions

- Exact PA/PM remote shape is unknown; generic Vite Module Federation contract must be verified later.
- Exact root domain and AWS region are not known; architecture must keep them configurable.
- Real Graph app-only access may require tenant admin consent that is unavailable during assessment; hybrid demo adapter is required.
- AWS/EKS deployment is substantial for a one-week build; CI/CD and IaC scope must stay thin and practical.

## Deferred Assumptions

- Holiday-aware SLA calendars are future scope.
- User-local timezone weeks are future scope.
- Full PA LogRocket/Loki integration is future scope and PA-owned.
- Full PA monorepo replication is not required for assessment.

