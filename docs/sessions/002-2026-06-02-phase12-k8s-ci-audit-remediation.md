# Session 002 — Phase 12 close-out (k8s + CI) + comprehensive final audit + remediation

- **Date:** 2026-06-02
- **Phase:** 12 (Deployment & Infrastructure)
- **Role:** st6-infra-implementer (team: st6-infra, branch `infra-track`)
- **Predecessor session:** [001-2026-06-02-phase12-infra-iac.md](001-2026-06-02-phase12-infra-iac.md)
- **Successor session:** _(none yet — Phase 12 complete; next is Phase 13)_
- **Slice commits:** `f8129bb` (12.8) → `02fab19` (12.9) → `1f8cda2` (12.2b) → `4e5c908` (12.11) → audit (`6ba08ca`, orchestrator) → `0e4db1c` (fix A) → `eb60ee3` (fix B)

## Why this session existed

Fresh successor after the predecessor cycled on context at the clean 12.7c boundary (round 1 = 12.1–12.7 + 12.10, HEAD `82c3cb0`). This session took Phase 12 to completion — the k8s deploy surface (12.8), workload manifests (12.9), a round-2 gap-fix that converted all cluster add-ons to one install mechanism (12.2b), and the GitHub Actions OIDC deploy pipeline (12.11) — then ran a **comprehensive ultracode/Workflow final audit** (user directive) and remediated the confirmed findings before close-out.

## What was built

### Files created
- `infra/k8s/namespace.yaml` — the `wc` namespace (12.8).
- `infra/k8s/serviceaccount-{api,worker,cronjob,migration}.yaml` — 4 SAs annotated with their 12.7b IRSA role ARNs (12.8).
- `infra/k8s/secretproviderclass.yaml` — 4 per-SA SecretProviderClasses (CSI+ASCP → `/mnt/secrets`, least-privilege mirror, dotted-key JMESPath, tmpfs-only) (12.8).
- `infra/k8s/service-api.yaml` — ClusterIP, selector `app: wc-api` (12.8).
- `infra/k8s/ingress-api.yaml` — ALB ingress (ACM cert, host + external-dns hostname annotation) (12.8).
- `infra/k8s/external-dns.yaml` — SA+RBAC+Deployment (12.8) — **deleted @12.2b** (became a helm_release).
- `infra/k8s/deployment-api.yaml`, `deployment-worker.yaml`, `cronjob-generation.yaml`, `job-migration.yaml`, `job-perf-seed.yaml` — 5 workload manifests (12.9).
- `infra/terraform/addons.tf` — 3 cluster-add-on `helm_release`s: Secrets Store CSI Driver 1.6.0, ASCP 3.1.1, external-dns 1.21.1 (12.2b).
- `.github/workflows/deploy.yml` — OIDC-federated, `production`-gated, ordered deploy pipeline + curl deployed smoke (12.11).

### Files modified
- `infra/terraform/iam_irsa.tf` — +external-dns IRSA role (12.8); trust sub `wc`→`kube-system` (12.2b); stale-comment rewords (fix B/L3).
- `infra/terraform/outputs.tf` — +`external_dns_role_arn` (12.8).
- `infra/terraform/eks.tf` — node-group `iam_role_name = "${local.cluster_name}-node"` (fix A/C1); +`endpoint_public_access_cidrs = var.eks_public_access_cidrs` (fix A/M2).
- `infra/terraform/variables.tf` — +`var.eks_public_access_cidrs` (fix A/M2).
- `infra/terraform/rds.tf` — +`lifecycle { ignore_changes = [engine_version] }` (fix B/M1).
- `infra/k8s/ingress-api.yaml` — +`ssl-policy: ELBSecurityPolicy-TLS13-1-2-2021-06` (fix B/M3); comment reword (fix B/L3).
- `infra/k8s/namespace.yaml` — comment reword (fix B/L3).
- `.github/workflows/deploy.yml` — migration delete-before-apply (fix B/H1) + fail-fast poll loop (fix B/L1).

## Decisions made (implementer-level; load-bearing ones were orchestrator/human-ruled)
- **12.8** — included external-dns ClusterRole/ClusterRoleBinding (brief said "Deployment+SA"; non-functional without RBAC; orch-confirmed). Allowlisted `${...}`+envsubst injection. Per-SA SPC mirrors IRSA least-privilege; no `secretObjects:` (tmpfs-only, rule #7); dotted JMESPath keys double-quoted.
- **12.9** — single `${ECR_API_IMAGE}`/`${ECR_WORKER_IMAGE}` repo:tag tokens; secrets-as-files via CSI (never env); actuator probes declared ahead of 13.3; perf-seed reuses the db-only `wc-migration` SA.
- **12.2b** — ASCP's bundled CSI-driver subchart disabled (`install=false`) to avoid a duplicate DaemonSet; external-dns moved `wc`→`kube-system` (orch TWEAK — root-fixes the namespace-ordering issue, makes all 4 add-ons namespace-consistent); chart versions verified vs the official kubernetes-sigs/aws `index.yaml`.
- **12.11** — all AWS work consolidated into one `environment: production` job (orch TWEAK — the env-scoped OIDC trust requires every `configure-aws-credentials` job to carry the production environment); curl-based deployed smoke (no `apps/**` edit).
- **Audit** — ultracode/Workflow fan-out (12 finders: 3 named reviewers + 8 IaC dimensions + completeness critic), each finding adversarially verified by an independent skeptic (default-refuted) before reporting.
- **Remediation** — poll-loop fail-fast for L1 (cleaner + shellcheck-safe than racing backgrounded `kubectl wait`s); C1 closed via `wc-aws-node-*` role rename (proven with a `/tmp` policy-match spike + control).

## Decisions explicitly NOT made (deferred)
- **EKS public-endpoint CIDR tightening** — `eks_public_access_cidrs` defaults open (`0.0.0.0/0`); tightening to operator/CI egress is an operator/Phase-13 action (accepted residual recorded by orch in decisions/001).
- **SHA-pinning third-party GitHub Actions** — currently major tags (`@v4`/`@v3`/`@v2`) → Phase-13 hardening.
- **RDS hardening** (deletion_protection, final snapshot, Multi-AZ) — Phase-13 trims (documented).
- **Cypress deployed-smoke subset** (`apps/wc-e2e/smoke/`) — st6-main territory.
- **Log-forwarding agent** (Fluent Bit / Container Insights) — Phase-13 trims (12.10 provisioned log-group destinations only).

## TDD compliance
**Infra is NOT red-green TDD** (root `CLAUDE.md` tdd-scope + `infra/CLAUDE.md` TDD protocol) — declarative IaC is verified, not test-driven. Every slice substituted "author → `kubeconform`/`terraform validate`/`tflint`/`actionlint` + structural assertions" for RED→GREEN, with the Step-2.5 design-review gate honored each time (12.2b and 12.11 each took a `TWEAK:` before `APPROVED.`). The comprehensive final audit (adversarial multi-agent) served as the deep verification pass. **No TDD violations**; no safety-critical shortcut.

## Reachability (Step 7.5)
- **12.8 manifests + 12.9 workloads** → applied by the 12.11 pipeline (`kubectl apply` in §12 order: migration→wait→roll; perf-seed excluded). SAs/SPCs consumed by pods; `service-api` selector `app: wc-api` matched by `deployment-api` pod label.
- **12.2b helm_releases** → reachable from `terraform apply` (root-module resources); close the LESSON §17 install gap (CSI driver/ASCP/external-dns that 12.6–12.9 consume).
- **12.11 `deploy.yml`** → the production deploy entry point (`workflow_dispatch` + `push` tag `release-*`); consumes all prior infra.
- **Fixes A/B** → all on the above live paths (C1/M2 in the eks module graph; H1/L1 in the pipeline; M1 in rds; M3 in ingress).
- **Forward-deps (HITL / st6-main, intentionally not wired here):** app images + Dockerfiles (incl. the assumed `apps/wc-api/worker/Dockerfile`), gate/build/web targets, actuator `/actuator/health/*` endpoints (13.3), GitHub `production` Environment + reviewers + `vars.*`, the S3 state bucket, the Route53 hosted zone.

## Open follow-ups (Step-9 categorized — orchestrator routes/verifies; do not self-route)
**Convention candidates (→ orchestrator banks LESSONS + index):**
- §14 envsubst-allowlist injection — **corrected to 15 tokens** (EXTERNAL_DNS_ROLE_ARN left the set when 12.2b removed external-dns.yaml).
- §15 per-SA SPC mirrors IRSA least-privilege; §16 workload-manifest conventions (CSI-volume + single-flyway/no-third-image audits); §17 install-add-ons-don't-just-consume; §18 OIDC reviewer-gated single-AWS-job pipeline; §19 permissions-boundary ↔ module-derived-role-name interaction (the C1 root cause).

**Architecture-doc notes (→ orchestrator writes):** Appendix C.6 +=`addons.tf`; C.7 −=`external-dns.yaml`; §13 prose reorder to apply→migrate (audit L2); decisions/001 Decision 4 + the M2 EKS-public-endpoint accepted-residual; audit RESOLVED marks.

**Cross-team carry-forwards (st6-main, Phase 13):** gate/build/Docker targets + worker Dockerfile path; Cypress deployed-smoke subset; actuator endpoints (13.3); the `spring.config.import` `file:`→`configtree:` reconciliation (Appendix D.2).

**Phase-13 (→ phase tasks):** SHA-pin actions; RDS hardening; log-forwarding agent; EKS public-endpoint CIDR tightening; HITL deploy runbook (13.6 — incl. the `vars.*`/Environment/state-bucket/hosted-zone prereqs); tflint AWS ruleset plugin.

## How to use what was built
The HITL deploy: set the GitHub `production` Environment + reviewers + repo `vars.*` (`AWS_DEPLOY_ROLE_ARN`, `AWS_REGION`, `ROOT_DOMAIN`, `TF_STATE_BUCKET`, `TF_STATE_LOCK_TABLE`), then trigger `deploy.yml` (workflow_dispatch or a `release-*` tag). The workflow runs gates → build/push images → `terraform apply` (installs the add-ons) → envsubst+`kubectl apply` (migration→wait→roll) → S3 sync + CloudFront invalidation → curl smoke. Everything in this repo is author+validate only; the real apply/deploy is HITL.
