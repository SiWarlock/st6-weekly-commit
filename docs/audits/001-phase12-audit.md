# Phase 12 — Comprehensive Final Audit (001)

- **Date:** 2026-06-02
- **Scope:** all Phase-12 IaC (both sessions) — `infra/terraform/*.tf`, `infra/k8s/*`, `.github/workflows/deploy.yml`; cross-checked vs `ARCHITECTURE.md` §12–§15 + Appendix C.6/C.7/D/F, `docs/decisions/001` (D1/D2/D3), root `CLAUDE.md` key safety rules, infra `LESSONS.md` §1–§18.
- **Posture:** analyze / validate ONLY (no apply/deploy). Audit, not a fix pass.
- **Methodology:** ULTRACODE/Workflow fan-out — 12 finders (`code-quality-reviewer` + `security-reviewer` + `reachability-auditor` + 8 IaC dimensions + completeness critic), each finding **adversarially verified by an independent default-refuted skeptic**. 33 agents; 21 raw → **11 confirmed / 10 refuted**.
- **Agent-side gates: ALL GREEN (rc=0)** — `terraform fmt -check` · `validate` · `tflint` · `kubeconform -strict` (16 res, 12 valid/4 CRD-skipped) · `actionlint`. **Every confirmed finding passed these gates** — they are apply-time / 2nd-deploy / controller-runtime issues static validation cannot see.
- **Severity tally:** **1 Critical · 1 High · 3 Medium · 6 Low** (11 confirmed). 2 must-fix-before-deploy (C1, H1).

---

## CRITICAL

### C1 — D3 permissions-boundary guardrail gap → CI `terraform apply` denied by its own boundary
- **Files:** `infra/terraform/eks.tf:44–58` (node-group block) vs `infra/terraform/iam_ci.tf:96–100` (`DenyPassRoleOutsideWc`).
- **What:** the D3 boundary scopes `iam:PassRole` to `arn:aws:iam::*:role/wc-*` (NotResource deny, no PassedToService condition). The EKS **managed node-group** IAM role is auto-named `default-eks-node-group-<suffix>` by the vendored eks v21 module — derived from the node-group map key `default`, **NOT** cluster-prefixed (verified `.terraform/modules/eks/.../main.tf:581`). Creating the node group requires the CI role to `PassRole` that role to the EKS/EC2 service → the role name doesn't match `wc-*` → the boundary's own Deny fires → **`AccessDenied`, the first `terraform apply` fails creating the node group.** (The cluster role is fine — `wc-aws-cluster-*`.)
- **Adversarial verdict — REAL (survived):** the skeptic could not refute — the node-role name is module-derived + non-`wc-`, the PassRole deny has no service/condition carve-out, and node-group creation unavoidably PassRoles the node role. Passes all static gates (no apply runs agent-side).
- **Touches:** **Decision 3** (the human-ruled permissions-boundary guardrail) + safety **rule #5** (least-privilege CI) + REQ-S-010 / RISK-016. LESSONS §13 (the boundary attaches to the node role but never caught the role-name↔PassRole-deny interaction).
- **Proposed fix (RECOMMENDED — preserves the `wc-*` scope):** set `iam_role_name = "${local.cluster_name}-node"` (→ `wc-aws-node`, matches `wc-*`) on the node-group block. One line; the `wc-*` PassRole invariant the human ruled stays intact.
- **Alternative:** broaden the boundary `NotResource` to also allow the node-group pattern — weakens the scope; not recommended.
- **Disposition:** **ESCALATED to human** (cat-1/cat-2 — touches the D3 safety design). Fix-slice A (`eks.tf`, its own safety commit) on the ruling.

---

## HIGH

### H1 — Migration Job re-applied by fixed name → 2nd+ deploy fails on immutable `spec.template`
- **Files:** `.github/workflows/deploy.yml:205` (`kubectl apply` migration) + `infra/k8s/job-migration.yaml:11–13`.
- **What:** `kubectl apply` on the fixed-name `wc-migration` Job with a new `${ECR_API_IMAGE}` SHA each release hits `Job…spec.template: field is immutable` → aborts at the migration gate on **every deploy after the first**. No `ttlSecondsAfterFinished`/delete/replace anywhere (verified). The trigger is `release-*` tags, so deploy #2 is the normal case.
- **Adversarial verdict — REAL (survived):** k8s Job `spec.template` is immutable; a changed image = a changed template = apply error. First deploy works; the skeptic confirmed deploy #2 is the standard path. Flyway is idempotent → no data risk; failure is loud + pre-roll (ordering safety intact).
- **Touches:** §12 (migration-Job-first ordering) + §13 (pipeline) + REQ-O-007.
- **Proposed fix:** `kubectl delete job/wc-migration -n wc --ignore-not-found` immediately before the apply (pairs with the existing `kubectl wait`); or `spec.ttlSecondsAfterFinished` on the manifest (delete-before-apply is more robust against back-to-back deploys).
- **Disposition:** **ESCALATED to human** (must-fix; the lead asked Crit+High be surfaced for the human to weigh in on the fix approach). Fix-slice B.

---

## MEDIUM

### M1 — RDS `engine_version="16.13"` + `auto_minor_version_upgrade=true`, no `ignore_changes` → perpetual plan drift + CI revert risk
- **Files:** `infra/terraform/rds.tf:50–51`, `infra/terraform/variables.tf:28/31`.
- **What:** after AWS auto-upgrades the minor version, every `plan` shows drift back to `16.13`; CI applies `-auto-approve` → a future run attempts the in-place revert/downgrade.
- **Adversarial verdict — REAL (survived):** auto-minor-upgrade + a pinned exact minor + no `ignore_changes` = guaranteed drift; CI auto-approve makes it an unattended revert attempt. First apply succeeds → not deploy-blocking.
- **Touches:** RISK-011 (RDS minor as a single var — the fix preserves this), §4/§12.
- **Proposed fix:** `lifecycle { ignore_changes = [engine_version] }` (keeps the `rds_minor_version` var for initial provisioning + RISK-011; lets auto-upgrade proceed untouched). Alt: pin major `"16"` + loosen the `^16\.` validation.
- **Disposition:** **fix-slice B** (orchestrator-authorized correctness fix; lead to confirm).

### M2 — EKS public API endpoint open to `0.0.0.0/0` (unstated module default)
- **Files:** `infra/terraform/eks.tf:24` (`endpoint_public_access=true`, no `endpoint_public_access_cidrs`).
- **What:** the public Kubernetes API endpoint accepts any source IP. Auth is still gated (IAM/OIDC access entries) + the private path is on by default → defense-in-depth, not an open admin port — but the wide-open CIDR is implicit, not a stated decision.
- **Adversarial verdict — REAL but nuanced (survived as a posture-gap, downgraded from "open port"):** the skeptic confirmed auth-gating + private path, so it's an exposure-surface/posture issue, not an unauthenticated hole.
- **Touches:** §16 (security posture); no safety-rule violation.
- **Proposed fix:** add a `eks_public_access_cidrs` var (default `0.0.0.0/0`) so the operator can scope it, AND record an explicit **accepted residual** in `docs/decisions/001` (same pattern as D3 residual #1). Alt: scope to a known operator/CI egress (impractical with GitHub-hosted dynamic-IP runners).
- **Disposition:** **fix-slice A** (`eks.tf`) — var + residual note. Lead to confirm the posture (scope vs accept).

### M3 — ALB ingress has no `ssl-policy` → ALB controller defaults to `ELBSecurityPolicy-2016-08` (TLS 1.0/1.1)
- **Files:** `infra/k8s/ingress-api.yaml:17–23`.
- **What:** without `alb.ingress.kubernetes.io/ssl-policy`, the controller defaults to a policy negotiating TLS 1.0/1.1 — inconsistent with the project's own CloudFront `TLSv1.2_2021` floor (`s3_cloudfront.tf:87`).
- **Adversarial verdict — REAL (survived):** the controller default is documented `ELBSecurityPolicy-2016-08`; no annotation overrides it.
- **Touches:** §16 (TLS posture), REQ-NF-006.
- **Proposed fix:** add `alb.ingress.kubernetes.io/ssl-policy: ELBSecurityPolicy-TLS13-1-2-2021-06`.
- **Disposition:** **fix-slice B** (security hardening; lead to confirm).

---

## LOW

### L1 — Migration `kubectl wait --for=condition=complete` has no fail-fast
- **File:** `.github/workflows/deploy.yml:206`. A failed Flyway migration blocks the full 600s timeout then errors opaquely (ordering safety intact). **Fix:** parallel-wait on `condition=failed` / poll both. **Disposition:** fix-slice B (nice-to-have).

### L2 — `ARCHITECTURE.md` §13 prose ordered "migration Job → terraform apply" (inverted) — ✅ ALREADY FIXED
- **File:** `ARCHITECTURE.md:207`. Contradicted binding Decision 1 (`decisions/001`) + the correct `deploy.yml`. Deploy.yml was correct; only the prose was stale. **Disposition:** **DONE** (orchestrator territory) — reordered to apply→migrate with a Decision-1 order note; rides the `/orchestrate-end` round commit.

### L3 — 3 stale comments referencing the deleted `external-dns.yaml`
- **Files:** `infra/k8s/ingress-api.yaml:7` ("inert until external-dns.yaml deploys"); `infra/k8s/namespace.yaml:3` ("the four ServiceAccounts + external-dns live here" — external-dns is now kube-system); `infra/terraform/iam_irsa.tf:175` ("k8s Deployment, 12.8" — contradicts line 192's "kube-system add-on"). Zero functional impact (live HCL/annotations correct). **Fix:** reword. **Disposition:** fix-slice B (doc-hygiene, implementer's files).

---

## REFUTED (10 — screened out by adversarial verification)

| # | Claim | Why refuted |
|---|---|---|
| R1 | perf-seed Job re-apply | off the deploy chain (opt-in, excluded) |
| R2 | no HTTP→HTTPS redirect | HTTPS-only is intentional (sole client = the SPA over https; no :80 listener is correct for an API) |
| R3 | RDS `deletion_protection=false`/`skip_final_snapshot` | documented Phase-13 trim (`MVP_TASKS.md:1342`) |
| R4 | CI `iam:*` blast radius | accepted D3 residual #1 (boundary-mitigated, reviewer-gated) |
| R5–R6 | `iam_irsa.tf` stale comment ×2 | self-corrected by adjacent line 192 |
| R7 | `DenyUnboundedRoleWrite` absent-key edge | correct-by-design |
| R8 | deploy.yml "§17" reference | correct — it's infra LESSONS §17, not ARCH §17 |
| R9 | HITL runbook missing | scheduled Phase-13 task 13.6 |
| R10 | `MVP_TASKS` 12.8 entry stale | round-2 gap-fix convention; superseded by the 12.2b entry |

---

## Disposition summary

| ID | Sev | Disposition |
|---|---|---|
| **C1** | Critical | **ESCALATED → human** (D3 design); fix-slice A (`eks.tf` node-role rename) |
| **H1** | High | **ESCALATED → human** (must-fix; approach sign-off); fix-slice B (`deploy.yml` delete-before-apply) |
| M1 | Medium | fix-slice B (`rds.tf` `ignore_changes`) |
| M2 | Medium | fix-slice A (`eks.tf` scoping var + decisions/001 residual) |
| M3 | Medium | fix-slice B (`ingress-api.yaml` ssl-policy) |
| L1 | Low | fix-slice B (`deploy.yml` wait fail-fast) |
| L2 | Low | ✅ DONE (orchestrator — ARCH §13 reorder) |
| L3 | Low | fix-slice B (comment cleanup) |

**Close-out HELD** pending the human's C1 + H1 rulings and a go on the fix-plan. No fixes applied yet (except L2 doc, orchestrator territory). Machine-readable fan-out result: see the implementer's workflow task output.
