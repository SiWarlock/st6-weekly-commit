# Research

> Status: Draft planning artifact
> Phase: 10 - Research plan and execution
> Date: 2026-06-02
> Source policy: official/vendor docs preferred.

## Research Questions

| ID | Question | Why It Matters | Decision It Informs | Status |
|---|---|---|---|---|
| RQ-001 | What Graph calendar endpoints/permissions support backend-created Outlook events? | Outlook Graph is PRD-required and must not block core workflow. | Graph adapter/auth model. | Researched |
| RQ-002 | How should Spring Boot validate Auth0 JWTs? | Auth0 OAuth2 JWT is a hard boundary. | Auth adapter and configurable claims. | Researched |
| RQ-003 | What Vite Module Federation remote contract is credible without PM repo access? | WC must run standalone and as PA remote. | Frontend architecture. | Researched |
| RQ-004 | Can AWS support PostgreSQL 16.4 on RDS? | PRD pins PostgreSQL 16.4. | Database deployment. | Researched |
| RQ-005 | How should SQS/SNS failure handling work? | Outlook sync is async and retryable. | Queue/DLQ architecture. | Researched |
| RQ-006 | How should S3/CloudFront custom-domain SPA hosting work? | Frontend deployment and custom domain are required. | AWS deployment architecture. | Researched |
| RQ-007 | How should EKS workloads receive secrets and expose health? | EKS deployment needs production-shaped security/ops. | Secrets and observability. | Researched |
| RQ-008 | How should Cypress/Cucumber be wired? | OG PRD requires Cypress E2E with Cucumber/Gherkin. | Test architecture. | Researched |

## Findings

### R-001 - Microsoft Graph Calendar Events

Question: Which Graph calendar path should WC target?

Findings:

- Microsoft Graph v1.0 is the stable production endpoint pattern.
- Creating user-calendar events requires calendar write permissions; `Calendars.ReadWrite` is relevant for delegated and application access on user calendars.
- WC should store returned Graph event IDs for future update/cancel and retry idempotency.
- App-only authorization with tenant/admin consent is a better MVP fit than per-user delegated Outlook connect because it avoids adding a new user OAuth workflow.

Sources:

- [Microsoft Graph API overview](https://learn.microsoft.com/en-us/graph/api/overview)
- [Create event API](https://learn.microsoft.com/en-us/graph/api/calendar-post-events?view=graph-rest-1.0)
- [Microsoft Graph permissions reference](https://learn.microsoft.com/en-us/graph/permissions-reference)

Impact:

- Architecture uses a real Graph adapter with app-only tenant consent when configured.
- Architecture keeps a deterministic demo/failure adapter for assessment when Graph credentials are absent.
- Outlook Calendar Sync Record stores Graph event ID, status, safe failure message, and retry state.

Remaining Risk:

- Tenant admin consent may not be available during assessment.
- Application access may need mailbox scoping policy in a real tenant.

### R-002 - Spring Security / Auth0 JWT

Question: How should Spring Boot validate Auth0 JWTs?

Findings:

- Spring Security OAuth2 Resource Server validates JWTs using issuer URI and can validate audience.
- JWK Set URI can be configured directly when needed.
- Claim mapping should be application-owned so PA/Auth0 custom claim names are not hardcoded.

Sources:

- [Spring Security JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)

Impact:

- Architecture uses Spring Security Resource Server for real Auth0 validation.
- Demo persona mode is an environment-gated alternate identity provider, not a replacement for server-side authorization.

Remaining Risk:

- Exact Auth0 claim names are unknown until PA/Auth0 config is available.

### R-003 - Vite Module Federation

Question: What remote contract should WC define?

Findings:

- Vite Module Federation remotes define a remote name, `remoteEntry.js`, exposed modules, and shared dependencies.
- React and React DOM should be shared/singleton-versioned to avoid duplicate React runtime issues.
- No PM remote example exists in this workspace.

Sources:

- [Module Federation Vite documentation](https://github.com/module-federation/vite)

Impact:

- Architecture defines a generic `wc` remote exposing a single route/module entry.
- Exact PM remote parity remains a verification item, not an invented decision.

Remaining Risk:

- PA host may use a convention not visible in this repo.

### R-004 - RDS PostgreSQL 16.4

Question: Can AWS RDS support the PRD-pinned database version?

Findings:

- Amazon RDS PostgreSQL release notes list PostgreSQL 16.4 variants.
- Newer 16.x versions exist, but the PRD pin should remain the architecture target unless region availability blocks it.

Sources:

- [Amazon RDS PostgreSQL versions](https://docs.aws.amazon.com/AmazonRDS/latest/PostgreSQLReleaseNotes/postgresql-versions.html)

Impact:

- Architecture targets Amazon RDS for PostgreSQL 16.4.

Remaining Risk:

- Verify region-specific availability during Terraform/deploy.

### R-005 - SNS/SQS And DLQ

Question: How should async sync failure handling work?

Findings:

- SQS DLQs are configured through redrive policy and `maxReceiveCount`.
- AWS recommends DLQ retention longer than source queue retention.
- SNS subscriptions can use SQS DLQs for failed deliveries; an SQS queue DLQ is appropriate for worker processing failures.

Sources:

- [Amazon SQS dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html)
- [Amazon SNS dead-letter queues](https://docs.aws.amazon.com/sns/latest/dg/sns-dead-letter-queues.html)

Impact:

- Architecture uses SNS lifecycle fanout, SQS sync queue, and SQS DLQ.
- Manual UI retry still originates from the Outlook Calendar Sync Record, not from a DLQ admin UI.

Remaining Risk:

- Message schema must remain small and reference sync record IDs rather than duplicating sensitive details.

### R-006 - S3 / CloudFront SPA Hosting And Custom Domains

Question: How should the deployed frontend be hosted?

Findings:

- CloudFront can serve private S3 origins with Origin Access Control.
- CloudFront alternate domain names require a trusted certificate, and CloudFront requires ACM certificates in `us-east-1`.
- Route 53 alias records can route custom domains to CloudFront distributions.
- SPA fallback routing should send unknown app routes to `index.html` while preserving API routing separately.

Sources:

- [Restrict access to an Amazon S3 origin](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html)
- [CloudFront alternate domain names and HTTPS requirements](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cnames-and-https-requirements.html)
- [Route traffic to a CloudFront distribution](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/routing-to-cloudfront-distribution.html)

Impact:

- Architecture uses S3 private bucket + CloudFront OAC for `wc.<root-domain>`.
- API is separate at `api.wc.<root-domain>`.

Remaining Risk:

- Root domain and hosted zone must be supplied before deployment.

### R-007 - EKS Secrets And Runtime Observability

Question: How should EKS workloads receive secrets and expose health?

Findings:

- AWS supports injecting Secrets Manager values into EKS pods through the AWS Secrets and Configuration Provider for the Secrets Store CSI Driver.
- EKS workloads can expose Kubernetes readiness/liveness probes; Spring Actuator provides health/readiness endpoints for Spring Boot services.
- CloudWatch is the AWS-default log/metric destination for EKS assessment visibility.

Sources:

- [Use AWS Secrets Manager secrets in Amazon EKS](https://docs.aws.amazon.com/secretsmanager/latest/userguide/integrating_ascp_irsa.html)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html)

Impact:

- Architecture uses AWS Secrets Manager, EKS workload injection, Spring Actuator, and CloudWatch.

Remaining Risk:

- Exact EKS secret injection mechanism can be finalized in implementation, but Secrets Manager remains the source of truth.

### R-008 - Cypress / Cucumber BDD

Question: How should the E2E suite satisfy the OG PRD?

Findings:

- `@badeball/cypress-cucumber-preprocessor` supports `.feature` files and TypeScript step definitions.
- The preprocessor can be configured through `cypress.config.ts` with esbuild.

Sources:

- [Badeball Cypress Cucumber Preprocessor](https://github.com/badeball/cypress-cucumber-preprocessor)

Impact:

- Architecture uses Cypress/Cucumber as the primary E2E acceptance suite.
- Playwright remains optional/ad hoc QA.

Remaining Risk:

- Keep Gherkin scenarios focused on core IC/manager workflows to avoid test brittleness.

