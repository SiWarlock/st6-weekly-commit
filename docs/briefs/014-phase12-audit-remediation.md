# /tdd brief — Phase-12 audit remediation (014)

> **Infra brief — NOT red-green TDD.** Author + validate ONLY (NO apply/deploy/kubectl). Human-approved fix-plan for the `docs/audits/001-phase12-audit.md` findings. **Two commits** (A = safety, own commit; B = bundle). Run `/tdd phase12-audit-remediation`.

## Feature
Apply the human-approved remediations for the 8 actionable audit findings (C1, H1, M1, M2, M3, L1, L3; L2 already fixed by the orchestrator). The orchestrator writes the `docs/decisions/001` residual note + marks each finding RESOLVED in `docs/audits/001` + banks the LESSON.

## Traceability
- **Source:** `docs/audits/001-phase12-audit.md` (committed `6ba08ca`). Human ruling relayed by the lead: C1+H1 recommended fixes, M2 = var-default-open + accepted residual.
- **Architecture/decisions:** Decision 3 (`docs/decisions/001`), §12/§13/§16, REQ-S-010/REQ-O-007/REQ-NF-006, RISK-011. LESSONS §6 (verify module input names), §13 (boundary).

═══ FIX-SLICE A — `eks.tf` (SAFETY commit, its OWN commit — do NOT bundle) ═══
- [ ] **C1 (Critical):** on the `eks_managed_node_groups.default` block set **`iam_role_name = "${local.cluster_name}-node"`** so the node role becomes `wc-aws-node` (matches the `wc-*` PassRole allow in the `ci_boundary`). Verify the resulting role name starts with `wc-` (consider `iam_role_use_name_prefix = false` if you want the exact name; a `wc-aws-node-<suffix>` prefix still matches `wc-*` — your call, just confirm it's `wc-`-prefixed). **This closes the create-node-group `PassRole` deny.**
- [ ] **M2 (Medium):** add `variable "eks_public_access_cidrs"` (type `list(string)`, default `["0.0.0.0/0"]`, a one-line description) in `variables.tf`; wire the eks module's **`endpoint_public_access_cidrs = var.eks_public_access_cidrs`** (confirm the exact v21 input name against the module — LESSONS §6). _(The orchestrator records the accepted-residual note in `docs/decisions/001` — not your file.)_
- [ ] **Verify (slice A):** `terraform fmt -check -recursive` / `init -backend=false` / `validate` / `tflint` rc=0. **Confirm C1 closed:** reason through the policy — node role now `wc-*` → `DenyPassRoleOutsideWc` (`NotResource role/wc-*`) no longer matches it → PassRole allowed. (A throwaway `/tmp` policy-sim spike is welcome if you want hard proof.)
- [ ] **Commit A (I finalize at Step 9):** `fix(infra): node-group IAM role wc-* prefix (closes D3 PassRole deny) + scopeable EKS public endpoint (12-audit C1/M2)`.

═══ FIX-SLICE B — bundle (deploy.yml + rds.tf + ingress-api.yaml + comments) ═══
- [ ] **H1 (High):** in `.github/workflows/deploy.yml`, add **`kubectl delete job/wc-migration -n wc --ignore-not-found`** immediately BEFORE the migration `kubectl apply` (line ~205, pairs with the existing `kubectl wait`). Closes the immutable-`spec.template` failure on deploy #2+.
- [ ] **M1 (Medium):** in `rds.tf`, add **`lifecycle { ignore_changes = [engine_version] }`** to `aws_db_instance.wc` (keeps the `rds_minor_version` var for initial provisioning + RISK-011; lets `auto_minor_version_upgrade` proceed without plan drift / CI revert).
- [ ] **M3 (Medium):** in `ingress-api.yaml`, add annotation **`alb.ingress.kubernetes.io/ssl-policy: ELBSecurityPolicy-TLS13-1-2-2021-06`** (TLS 1.2+ floor, matches the CloudFront `TLSv1.2_2021`).
- [ ] **L1 (Low):** in `deploy.yml`, make the migration `kubectl wait` fail-fast — wait on `condition=failed` in parallel with `condition=complete` (e.g. background both + whichever returns first; or a poll loop) so a failed migration errors promptly instead of blocking the full timeout.
- [ ] **L3 (Low):** reword the 3 stale `external-dns.yaml` comments — `infra/k8s/ingress-api.yaml:7`, `infra/k8s/namespace.yaml:3`, `infra/terraform/iam_irsa.tf:175` (external-dns is now a `kube-system` helm_release per 12.2b; no functional change).
- [ ] **Verify (slice B):** `actionlint` rc=0 (deploy.yml); `terraform fmt/validate/tflint` rc=0 (rds.tf); `kubeconform -strict` rc=0 (ingress-api.yaml). Confirm H1 closed (delete-before-apply present before the migration apply).
- [ ] **Commit B (I finalize at Step 9):** `fix(infra): migration Job re-apply + RDS version drift + ALB TLS policy + wait fail-fast + stale comments (12-audit H1/M1/M3/L1/L3)`.

## Things to flag at Step 2.5
The fixes are human-specified, so Step-2.5 is light — but flag:
1. **C1 role-name form** — `iam_role_name` with/without `use_name_prefix`; confirm the final role name is `wc-`-prefixed (the only thing that matters for the PassRole allow).
2. **M2 module input name** — confirm the exact v21 input (`endpoint_public_access_cidrs` vs a `cluster_`-prefixed variant) against the module schema before wiring.
3. **L1 fail-fast mechanism** — your chosen approach (parallel wait vs poll); keep it `actionlint`-clean.
Send me the Step-2.5 confirm (the C1/M2 input-name resolutions + green); I'll approve fast.

## Cross-doc invariant impact
- **Orchestrator writes (Step 9):** the `docs/decisions/001` EKS-public-endpoint accepted-residual note (M2); the RESOLVED marks in `docs/audits/001` (per fix hash); a new LESSON (§19) banking the **permissions-boundary ↔ module-derived-role-name interaction** (a scoped resource-deny on `wc-*` breaks if a vendored module names a role outside the prefix — audit every TF-created role name against the scope). Implementer does NOT touch these.

## Estimated commit count
**2 commits** — A (eks.tf safety, own) + B (bundle). A is safety-critical (the D3 guardrail) → its own commit per the no-bundle-safety rule.

## How to invoke
1. Read `docs/audits/001-phase12-audit.md` C1/H1/M1/M2/M3/L1/L3 entries for full context.
2. `/tdd phase12-audit-remediation` — author + validate, NO apply/deploy.
3. Slice A → Step-2.5 confirm → validate → I route Step-9 → commit A. Then Slice B → validate → Step-9 → commit B.
4. Each Step-9: ship/no-ship + draft commit + confirm the finding is closed (esp. C1 + H1).
