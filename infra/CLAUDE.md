# ST6 Weekly Commit Module `infra/` — Build Guide

> **You're in `infra/`.** This file plus root `CLAUDE.md` both load. The root file covers global project conventions + shared comm rules (track-prefix, escalation taxonomy, messaging budget); this file owns code-area conventions for infrastructure.

## Launch protocol

| Working on... | cwd | Loads |
|---|---|---|
| Planning / docs / commits | repo root (`ST6/`) | root `CLAUDE.md` only |
| infrastructure code | `infra/` | this `CLAUDE.md` + root |

<!-- For a multi-area project, add a row per additional code area. -->

If you find yourself fighting the wrong conventions, check your cwd.

## Session start/end protocol

**At session start:**
1. Read `MVP_TASKS.md` (repo root) → "Currently in progress" section.
2. Confirm with the user what feature this session is targeting.
3. Read the relevant section of `ARCHITECTURE.md` from the lookup table below.

**At session end** (only when the user explicitly says we're done):

1. **Implementer runs `/session-end`.** Implementer writes ONLY:
   - `infra/` code files (the slice's implementation)
   - test files (the slice's tests)
   - dependency manifest / lockfile (deps the slice adds)
   - `docs/sessions/<NNN>-<date>-<topic>.md` (session doc, created at `/session-end` Step 5)

   **Implementer must NOT touch (all orchestrator territory):**
   - `MVP_TASKS.md`
   - `infra/LESSONS.md`
   - `infra/CLAUDE.md` (entire file — both the Cross-doc invariants table AND the Lessons logged index)
   - `ARCHITECTURE.md`
   - `docs/orchestrator-briefing.md` / `docs/tdd-brief-template.md` / `docs/briefs/` / `docs/runbooks/`
   - other top-level deliverable / design docs
   - `.gitignore` and root-level dotfiles (unless adding a new artifact to ignore, flagged at Step 9)

   At the slice's Step 10 commit, **explicit `git add <path>` for each slice file**; **never `git add -A`** or `git add .`; **never stage an orchestrator-territory file**. If the slice surfaces a change to any orchestrator-territory file (new model needing a cross-doc table row, a lesson candidate, an architecture note), the implementer **flags it at Step 9** per the routing matrix in `docs/orchestrator-briefing.md`. The orchestrator writes the change hot during the same session — working-tree state stays aligned within the round even though commits stagger.

2. **Orchestrator runs `/orchestrate-end`** for round close-out + Carry-forward triage + round terminal commit + push.

## Lookup table — where to find canonical info

Don't paste these sections into the prompt. Grep the file:section, read only what you need. `/check-arch <topic>` dispatches off this table.

| Topic | File (relative to repo root) | Section |
|---|---|---|
| AWS deployment topology (EKS/RDS/ECR/SNS-SQS/S3-CloudFront/Route53-ACM/Secrets/IRSA) | `ARCHITECTURE.md` | §12 |
| CI/CD pipeline + Terraform remote state + local runtime | `ARCHITECTURE.md` | §13 |
| `infra/terraform` file layout | `ARCHITECTURE.md` | Appendix C.6 |
| `infra/k8s` manifest layout | `ARCHITECTURE.md` | Appendix C.7 |
| Config & environment contract (env vars, secret sources) | `ARCHITECTURE.md` | Appendix D |
| Terraform root module / providers + backend / root vars | `infra/terraform/` | `{versions,backend,main,variables,outputs}.tf` |
| VPC / EKS / node-group / ALB controller | `infra/terraform/` | `{vpc,eks}.tf` |
| RDS PostgreSQL (private, node-SG-only) | `infra/terraform/` | `rds.tf` |
| ECR repos / SNS-SQS transport | `infra/terraform/` | `{ecr,sns_sqs}.tf` |
| Lessons logged (full prose) | `infra/LESSONS.md` | by lesson # |

<!-- Starts near-empty. Add a row whenever a topic is looked up twice. -->

**Code intelligence & docs (when available):** prefer a code-intelligence MCP (e.g. CodeGraph) for code navigation / callers / traces over `grep`+read loops, and a docs MCP (e.g. Context7) for up-to-date library/API docs — see root `CLAUDE.md` "Code intelligence & docs." No-op if not installed.

## Stack

<!-- ▼ EXAMPLE BLOCK [id=area-stack]: stack quick-reference for implementer sessions. Canonical stack lives in root CLAUDE.md + ARCHITECTURE.md; this is the cheat sheet. ▼ -->

- **Runtime:** Terraform + kubectl/Helm (no app runtime)
- **IaC / build:** Terraform (provider lockfile) · Terraform AWS provider · EKS k8s manifests · GitHub Actions (OIDC)
- **Verify:** terraform fmt + tflint / terraform validate / terraform plan (no red-green TDD)
- **Deploy order (pipeline-enforced):** migration Job → api/worker Deployments → CronJob

<!-- ▲ END EXAMPLE BLOCK [id=area-stack] ▲ -->

## Standard commands

```bash
# Install deps (run once; re-run when the manifest changes)
terraform -chdir=infra/terraform init

# Run the dev server (if applicable)
terraform -chdir=infra/terraform plan

# Tests
terraform -chdir=infra/terraform validate

# Quality
terraform fmt -check -recursive && tflint
terraform fmt -check -recursive
terraform validate

# Preflight (use before saying "done" with a feature)
terraform fmt -check -recursive && tflint && terraform validate && terraform -chdir=infra/terraform validate
```

## TDD protocol

**Infrastructure (Terraform/k8s) is NOT red-green TDD.** There is no failing-test-first loop here — declarative IaC is verified, not test-driven. A slice is verified via `terraform validate` + `terraform plan` + policy/lint (`terraform fmt -check -recursive && tflint`, kubeconform for manifests) and confirmed by the deployed smoke suite after apply. See the TDD posture in root `CLAUDE.md` (`tdd-scope`) — deterministic backend/frontend logic is test-first; infra rides the validate/plan/policy/smoke path instead.

**Commit per slice when practical.** Never bundle a safety-critical slice with anything else.

## Forbidden patterns

<!-- ▼ EXAMPLE BLOCK [id=forbidden-patterns]: forbidden patterns — 3-5 narrow, enforceable, domain-specific rules. Shape: "Don't <pattern X> because <reason / past incident>; use <alternative Y>." Test-pin them where possible. Starts small; accretes as lessons surface. ▼ -->

Do not:

1. **Hardcode secrets** — DB/Auth0/Graph secrets come from AWS Secrets Manager via the CSI driver + per-workload IRSA (least privilege).
2. **Use long-lived AWS keys in CI** — GitHub Actions authenticates via OIDC federation to a least-privilege deploy role.
3. **Enable Flyway anywhere but the migration Job** — `spring.flyway.enabled=true` ONLY on the pre-deploy migration Job; `false` on api/worker/cron (single schema owner, no race).
4. **Provision the CloudFront ACM cert outside `us-east-1`** — CloudFront requires its cert in `us-east-1` (the ALB cert is regional).
5. **Run a CronJob/migration as a third image** — the CronJob + migration Job reuse the `wc-api` image via Spring profiles/args.

<!-- ▲ END EXAMPLE BLOCK [id=forbidden-patterns] ▲ -->

## Cross-doc invariants — schema/docs mirroring

Several typed models in this codebase are **contracts** mirrored in `ARCHITECTURE.md` and indexed in the table below. The architecture doc is the canonical contract; the model is the executable enforcement. Drift produces silent disagreement.

**Authoring discipline (orchestrator owns this table).** When the implementer adds, removes, or renames a field on one of these models, the implementer **flags it at Step 9 categorized as `Cross-doc invariant change`** per the routing matrix in `docs/orchestrator-briefing.md`. The implementer does NOT edit `infra/CLAUDE.md` or `ARCHITECTURE.md` directly — the orchestrator writes the table row + the architecture edit hot during the same session. Working-tree state aligns within the round; commits stagger (implementer's slice commit lands code+tests; orchestrator's round commit lands the doc rows).

| Model | `ARCHITECTURE.md` section | Notes |
|---|---|---|
| <model> | §X | <field summary> |

<!-- Starts empty (or with the first model if one exists). Populated as contract models land. -->

## Module organization

<!-- ▼ EXAMPLE BLOCK [id=module-layout]: module layout + layer dependency rule. Replace with the project's real directory tree and import-direction DAG. ▼ -->

```
infra/
  terraform/   main.tf variables.tf outputs.tf backend.tf vpc.tf eks.tf rds.tf ecr.tf sns_sqs.tf s3_cloudfront.tf route53_acm.tf secrets.tf iam_irsa.tf cloudwatch.tf
  k8s/         deployment-api.yaml deployment-worker.yaml cronjob-generation.yaml job-migration.yaml job-perf-seed.yaml service-api.yaml ingress-api.yaml serviceaccount-*.yaml secretproviderclass.yaml
```

No layer DAG (declarative IaC). Discipline instead: one Terraform root module; modules composed via `main.tf`; the migration Job is the sole schema owner; apply order = migration Job → api/worker Deployments → CronJob (enforced in the GitHub Actions pipeline, not by Terraform).

<!-- ▲ END EXAMPLE BLOCK [id=module-layout] ▲ -->

## Subagents

See `.claude/agents/README.md` for the canonical inventory + integration points.

<!-- ▼ EXAMPLE BLOCK [id=area-subagent-candidates]: area-specific subagent candidates — list candidates that would earn their keep specifically in this area (e.g. an ABI/types syncer for a frontend area, a Pyth/feed verifier for a contracts area). Build only on real friction. ▼ -->

Candidates: a **terraform-plan policy checker** (diffs plan against the §12 contract — IRSA least-privilege, CloudFront-cert-in-us-east-1, flyway-only-on-migration-Job); a **k8s manifest validator** (kubeconform).

<!-- ▲ END EXAMPLE BLOCK [id=area-subagent-candidates] ▲ -->

## Lessons logged from prior sessions

The full prose for each lesson lives in `infra/LESSONS.md`. This index is the compact orientation surface.

**Lesson numbers are stable IDs** — once assigned, they don't change. New lessons get the next sequential number. `/session-end` proposes additions when it detects them; the user approves before the entry is written and a row is added here.

Lessons start at §1.

| # | Date | Topic | Rule (one-liner) |
|--:|---|---|---|
| 1 | 2026-06-02 | [tflint forward-declarations](LESSONS.md#1) | Mute only `terraform_unused_declarations` for forward-declared vars/outputs/aliases with an inline reason + dated re-enable marker; never globally weaken the lint gate. |
| 2 | 2026-06-02 | [Terraform verify recipe](LESSONS.md#2) | Agent-side gate = `fmt -check` + `init -backend=false` + `validate` + `tflint` rc=0; real `init`/`plan`/`apply` + plan-time assertions are HITL-deferred. |
| 3 | 2026-06-02 | [outputs.tf append-only](LESSONS.md#3) | `outputs.tf` enumerates planned exports as comments; add a live `output` only in the slice that creates its backing resource — never reference a not-yet-created resource. |
| 4 | 2026-06-02 | [module version = the pin](LESSONS.md#4) | Community modules have no lockfile; the `version = "~> N"` constraint IS the pin — verify the current major against the live registry at author time and pin the major. |
| 5 | 2026-06-02 | [configure provider in first-using slice](LESSONS.md#5) | Declare providers up front; add the configured `provider {}` block only in the slice that first consumes it — declared-only is lint-clean (`unused_declarations` ignores `required_providers`). |
| 6 | 2026-06-02 | [author vs current-major schema](LESSONS.md#6) | Verify a community module's current input/submodule schema against the registry/Context7 + spike before authoring — module majors rename inputs (eks v21 `cluster_*`→`name`/`kubernetes_version`) and submodules (iam v6 `…-eks` drop). |
| 7 | 2026-06-02 | [DB password via random_password](LESSONS.md#7) | DB master password = `random_password` consumed by RDS + populated into the `db` secret by the secret slice; never an output/log (rule #7). Declare the `random` provider explicitly — not a transitive module dep, and `random_password` without it fails tflint. |
| 8 | 2026-06-02 | [ECR repo posture](LESSONS.md#8) | ECR = IMMUTABLE + scan_on_push + untagged-expiry, via a fixed `for_each` of exactly api+worker repos — CronJob/migration reuse `wc-api`, never a third image. |
| 9 | 2026-06-02 | [secret-value split](LESSONS.md#9) | TF populates derived secrets (db — value in encrypted state, never an output); real 3rd-party creds (auth0/graph/demo) = container + placeholder + `ignore_changes`, HITL-populated, never in state/vars (rule #7). |
| 10 | 2026-06-02 | [CloudFront default-cert-then-attach](LESSONS.md#10) | CloudFront ships on the default cert + no aliases in its slice; the Route53/cert slice edits the distribution to attach the us-east-1 ACM cert + alias (a var can't reference the cert resource). |
| 11 | 2026-06-02 | [ACM cert pattern](LESSONS.md#11) | CloudFront cert → us-east-1 aliased provider (RISK-010); ALB cert → regional; both DNS-validated; consumers reference `_validation.certificate_arn` (waits for validation); Route53 zone is a data source. |
| 12 | 2026-06-02 | [per-workload IRSA least-privilege](LESSONS.md#12) | Each workload gets its own IRSA role with exactly its §12 actions on exact ARNs; no `GetSecretValue` on `*`; OIDC sub+aud trust on the cluster issuer; ns+SA-name is a pinned contract with the k8s slice; audit the policy JSON. |
| 13 | 2026-06-02 | [hardened CI role / boundary-as-guardrail](LESSONS.md#13) | CI apply-role: service-LP in the identity policy, escalation-prevention in a permissions boundary (`Allow *` + Deny set) threaded onto EVERY TF-created role; self-ref the boundary via a constructed ARN (cycle); env-scoped OIDC + reviewer gate; no static keys. node-group boundary = per-node-group `iam_role_permissions_boundary`. |
| 14 | 2026-06-02 | [TF-output→manifest envsubst injection](LESSONS.md#14) | Bind post-apply TF values into static k8s manifests via `${TOKEN}` placeholders + an **allowlisted** CI `envsubst` (enumerated var list, not bare) before `kubectl apply`; `kubeconform` validates the placeholder form agent-side. |
| 15 | 2026-06-02 | [per-SA SecretProviderClass least-privilege](LESSONS.md#15) | One SPC per SA exposing only that SA's IRSA-permitted secrets (worker→graph, cron/migration→db); double-quote dotted JMESPath keys (`'"spring.datasource.url"'`); mount to tmpfs files with NO `secretObjects:` etcd sync (rule #7); consume via `configtree:` not `file:`. |
| 16 | 2026-06-02 | [k8s workload manifest conventions](LESSONS.md#16) | Workloads mount secrets as a per-pod CSI SecretProviderClass volume (files at `/mnt/secrets`, never env); grep-audit single-Flyway-owner + no-third-image before GREEN; migration ordering + perf-seed opt-in live in the pipeline, not the manifest; the CSI driver itself installs via `helm_release` (platform addon). |
| 17 | 2026-06-02 | [install add-ons, don't just consume them](LESSONS.md#17) | A manifest that USES a cluster add-on (CSI driver, ingress class, DNS annotation) doesn't INSTALL it — audit controllers, not just workloads, for a matching install resource; install all cluster add-ons by ONE mechanism (Terraform `helm_release` for platform controllers, kubectl manifests for app workloads). |
| 18 | 2026-06-02 | [OIDC deploy pipeline shape](LESSONS.md#18) | Env-scoped OIDC trust (`sub=…:environment:production`) forces ALL `configure-aws-credentials` work into ONE `environment: production` job (a separate AWS job is denied at the real run; `actionlint` can't see it) + `gates` separate; OIDC-only/no static keys; ordered `needs`/step graph makes apply-before-kubectl + migrate-before-roll unreachable; allowlisted-`envsubst` injection. |
| 19 | 2026-06-02 | [scoped name-prefix deny vs module-named roles](LESSONS.md#19) | A boundary/policy that scopes by a role-name prefix (`role/wc-*`) breaks when a vendored module names a role outside it (eks node-group role = `default-eks-node-group-*` from the map key) → the CI role's own PassRole deny fails `apply`; audit EVERY TF-created role name (incl. module-created) against the prefix, and force module names into it via `iam_role_name` rather than widening the scope. Static gates can't see it — only apply. |
| 20 | 2026-06-04 | [bootstrap remote-state with a separate LOCAL-backend root](LESSONS.md#20) | The remote-state S3 bucket + DynamoDB lock must exist BEFORE the main root's first `init` (chicken-and-egg) → bootstrap them with a SEPARATE sibling `infra/terraform-bootstrap/` root on a LOCAL backend (it can't store state in the bucket it creates). 2 resources: encrypted/versioned/public-blocked S3 bucket + `LockID`/`S`/PAY_PER_REQUEST DynamoDB lock; deterministic `wc-${env}-tfstate-${account_id}` name (no random provider); `prevent_destroy`+`force_destroy=false` (state = crown jewels, fail-closed); gitignore the local state (sibling dir uncovered); optional `-migrate-state` under a DISTINCT `wc/bootstrap.tfstate` key. Verify on the mini-root + re-run main-root gate (comment inert). (origin: 12.12) |
| 21 | 2026-06-04 | [no-leak put-secret-value helper](LESSONS.md#21) | A HITL secret-population helper satisfies rule #7 via env→`jq -n env.X` (NOT `--arg` → keeps the value out of argv)→`aws put-secret-value --secret-string file:///dev/stdin` (stdin, no temp file, no inline `$VAR`); `read -s` the TRUE secret only (public OAuth/tenant config can use visible read); env-or-prompt input (never a CLI arg / values file); `set -euo pipefail` with no `-x`; output only names + VersionId/ARN (never a value); `--dry-run` redacts to `***` + makes zero AWS calls. (origin: 12.13) |
| 22 | 2026-06-04 | [runner-Job web-application-type=none termination contract](LESSONS.md#22) | A one-shot `--app.job` runner (`ProjectionRebuildRunner`/`PlanShellGenerationRunner`) does NOT self-exit — termination relies ENTIRELY on `--spring.main.web-application-type=none` (no web server → `main` returns → JVM exits). A k8s Job/CronJob launching such a runner WITHOUT the arg starts a web server + never reaches `Complete` (hangs to backoffLimit). Always pass it + `activeDeadlineSeconds`; audit every `--app.job` Job for the arg (caught the generation-cronjob gap + folded the fix at 12.14). (origin: 12.14) |
| 23 | 2026-06-06 | [Deploy-2 same-origin PATH federation (Option B) — additive `/portal/*` behavior + rewrite Function; fail-safe-to-remote build trap; user-gated prod writes](LESSONS.md#23) | When a new CloudFront distribution is blocked (account-verification gate) or cross-origin CORS is unwanted, serve a federation host **same-origin** via an additive `/portal/*` cache behavior on the EXISTING distribution + a **viewer-request rewrite Function** for per-path SPA fallback (distribution-wide custom errors can't do per-path → `/portal/callback` breaks Auth0). Never "remove the standalone build var" to repoint the main site — wc-web is fail-safe-to-remote (wc-web LESSON 28) so a no-`VITE_BUILD_TARGET` build ships the REMOTE to root; SKIP the build step instead + audit every wc-web build step for explicit `VITE_BUILD_TARGET=standalone`. Keep prod `s3 sync`/invalidation user-executed (the classifier gate is correct). (origin: 025 / Deploy 2) |

<!-- Starts empty. Each row links to its `LESSONS.md` anchor. -->

<!-- Slash commands: see root CLAUDE.md "Slash commands available." Implementer pair: /session-start + /session-end. -->
