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
| <subsystem A> | `ARCHITECTURE.md` | §X |
| <subsystem B> | `ARCHITECTURE.md` | §Y |
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
| | | | |

<!-- Starts empty. Each row links to its `LESSONS.md` anchor. -->

<!-- Slash commands: see root CLAUDE.md "Slash commands available." Implementer pair: /session-start + /session-end. -->
