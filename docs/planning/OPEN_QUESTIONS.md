# Open Questions

> Status: Draft planning artifact
> Phase: 9 - Assumptions and open questions
> Date: 2026-06-02

## Open Questions

| ID | Question | Why It Matters | Current Best Guess | Must Answer By | Fallback | Status |
|---|---|---|---|---|---|---|
| OQ-001 | What root domain should be used for `wc.${ROOT_DOMAIN}` and `api.wc.${ROOT_DOMAIN}`? | Terraform, Route 53, ACM, CloudFront, and API ingress need a DNS zone. | `ROOT_DOMAIN` is a required deployment variable. | Before deployment task. | Use Terraform variables and document placeholder. | Open |
| OQ-002 | Should any AWS regional workload override the default `us-east-1` region? | Affects Terraform and service availability. | Use `us-east-1` by default. | Before Terraform apply. | Keep region configurable. | Open |
| OQ-003 | What exact Auth0 claim-name defaults should the configurable mapper use? | Needed for production Auth0 config. | Use demo defaults and configurable mapper. | Before real Auth0 enablement. | Demo auth mode for assessment. | Open |
| OQ-004 | Is the real PA/PM remote pattern available for verification? | Avoids mismatched Module Federation contract. | Not present in this workspace. | Before final architecture or implementation if repo appears. | Generic Vite remote contract. | Open |
| OQ-005 | Will a real Microsoft 365 tenant/app registration be available? | Determines whether demo shows real Graph calls or demo adapter. | Not guaranteed. | Before deployment/demo recording. | Hybrid demo/failure adapter. | Open |
| OQ-006 | Are there evaluator-specific demo-video or AI usage log templates? | Affects final deliverable formatting. | No template known. | Before final submission. | Markdown `AI_USAGE.md`; concise demo video. | Open |

## Resolved Questions

| Question | Resolution |
|---|---|
| Is deployment required? | Yes, deployed frontend and backend are required. |
| Which cloud platform? | AWS with EKS, CloudFront, S3, SQS/SNS, RDS PostgreSQL, Route 53/ACM for custom domains. |
| Should custom domain be required? | Yes: `wc.<root-domain>` for frontend and `api.wc.<root-domain>` for API. |
| Should the repo use Nx/Yarn? | Yes, lightweight Yarn Workspaces + Nx monorepo. |
| Which E2E tool is primary? | Cypress with Cucumber/Gherkin BDD. Playwright is optional/ad hoc. |
| How should Graph/Auth0 work for demo? | Hybrid real adapter when configured, seeded/demo fallback when env vars are absent. |
| Where does PostgreSQL run? | Amazon RDS PostgreSQL 16.4. |
| How are Outlook sync jobs processed? | SNS lifecycle fanout, SQS subscription, separate EKS worker, SQS DLQ. |
| How are secrets managed? | AWS Secrets Manager. |
| How are weekly plan shells generated? | EKS Kubernetes CronJob plus DB uniqueness. |
| What AWS region is default? | `us-east-1`, overridable by Terraform variable. |
| How are heatmap/dashboard reads backed? | Synchronous `manager_plan_summary` and `manager_heatmap_cell` read models with internal rebuild job/CLI. |
| What API style is used? | REST JSON resources plus explicit command endpoints. |
| How is authorization enforced? | Central service-layer domain authorization for self/direct-report checks. |
| What is the workspace layout? | `apps/wc-web`, `apps/wc-api`, `apps/wc-sync-worker`, `apps/wc-e2e`, `infra/terraform`, `infra/k8s`. |
| What Java build shape is used? | Gradle multi-module. |
| What local/test DB strategy is used? | Docker Compose for local full-stack; Testcontainers PostgreSQL for backend integration tests. |
