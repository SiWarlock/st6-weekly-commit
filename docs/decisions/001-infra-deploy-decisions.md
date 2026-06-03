# Infra Phase 12 — open decisions for the human (st6-infra track)

> Written by `st6-infra-orchestrator` as a durable, lead-readable surface (message bodies reach the lead only as summaries). Two decisions are blocking the remaining Phase-12 slices. The lead surfaces these to the human via `AskUserQuestion`; the orchestrator implements the chosen options.
>
> **Status:** both RESOLVED by the human 2026-06-03 (relayed by the lead) — see "Lead ruling" at the bottom. Original framing preserved below for the audit trail.

---

## Decision 1 — CI deploy-role posture (blocks 12.7c) · SAFETY (rule #5 least-privilege)

### The safety question / tension
§13 specifies a "**least-privilege** CI deploy role" — GitHub Actions OIDC-federated, no long-lived keys (REQ-S-010 / RISK-016). But the role's scope depends on **what the CI workflow does**:
- If the CI workflow runs the full §13 chain (`terraform apply` → `kubectl` deploy → migration Job → deployed smoke), the role must be **broad** — `iam:* + Resource:*` across ~16 services. The highest risk is **`iam:*`**: a privilege-escalation surface (the role can create/modify IAM), gated *only* by the OIDC trust. That contradicts "least-privilege."
- **The human has already set real `terraform apply` / deploy as HITL for this assessment.** So the CI workflow likely shouldn't apply at all — which collapses the role to genuinely least-privilege.

### Options (blast radius vs §13 pipeline fidelity)
- **Option 1 — plan-only CI + HITL apply (RECOMMENDED; least-privilege-truest; aligns with the HITL-apply decision).**
  CI workflow = gates (lint/test) → build + push images to ECR → `terraform plan` → STOP. The `apply` → deploy → migration → smoke chain ships as a `docs/runbooks/` HITL procedure (the complete deploy design is still delivered — run by the human with their creds, not auto-applied). CI role becomes NARROW: ECR push + S3/DynamoDB state R/W + read/describe (for plan refresh). **No `iam:*` write, no create/modify.** The blast-radius problem disappears. Trade: 12.11's auto-run workflow is gates+build+plan, not the literal §13 apply→smoke chain (a documented architecture note; the chain lives in a runbook).
- **Option 2 — full-apply CI + IAM permissions boundary.**
  CI workflow runs the full §13 chain; the broad role is capped by an IAM permissions boundary (limits what the role + any IAM it creates can do). Faithful to §13's literal pipeline; mitigates the `iam:*` escalation; still a broad blast radius. Moderate extra Terraform.
- **Option 3 — full-apply CI + GitHub Environment + required reviewers.**
  Like Option 2, but the OIDC subject is environment-scoped (`repo:<owner>/<repo>:environment:production`) with a GitHub Environment requiring manual reviewer approval before each deploy (a human gate on every run). Tightest *access* control; doesn't reduce policy breadth. Combinable with Option 2.

### Recommendation
**Option 1.** It's the only option where the role isn't broad at all, and it matches the apply-is-HITL decision already made. The deliverable loses nothing — the full apply/deploy/smoke design ships as a runbook (arguably clearer than workflow YAML). Either way the role stays OIDC-federated, no static keys, repo+branch-scoped subject (needs a new `var.github_repo` = `owner/repo`, required, no default).

---

## Decision 2 — `api.wc.${ROOT_DOMAIN}` → ALB Route53 alias (blocks the api.wc record in 12.11) · FINDING (spec/reality contradiction)

### The contradiction
Task 12.7 specifies Terraform creates BOTH Route53 alias records — `wc.` → CloudFront (done in 12.7a) **and `api.wc.` → ALB**. But the **ALB is created by the AWS Load Balancer Controller from the 12.8 Ingress at deploy time — after `terraform apply`** (§13 order: apply → deploy → controller reconciles the Ingress into an ALB). So at Terraform-time the ALB's DNS name doesn't exist; the `api.wc → ALB` alias **cannot** be a pure-Terraform record. (The ALB *cert* — regional ACM for api.wc — IS already created + DNS-validated independently in 12.7a; only the alias record hits this.)

### Impact
The 12.11 deployed smoke asserts `https://api.wc.${ROOT_DOMAIN}/actuator/health/readiness` (REQ-E-006) — so the record must exist by deploy-end.

### Options
- **Option A — CI/HITL-scripted post-deploy record (RECOMMENDED).** After the Ingress creates the ALB, read its DNS name (`kubectl get ingress` / `aws elbv2`) and upsert the `api.wc` Route53 alias via `aws route53 change-resource-record-sets`. No new component; a step in the deploy procedure. Under Decision-1 Option 1 (plan-only CI), this becomes a **HITL-runbook step**.
- **Option B — external-dns controller.** The Ingress carries a hostname annotation + an external-dns Deployment auto-creates the Route53 record. The standard k8s pattern, but **adds a component NOT in Appendix C.7** (an architecture change — needs an atomic C.7 edit + a new manifest).
- **Option C — HITL manual record.** Operator creates the alias by hand after the ALB exists. Simplest, but a manual step.

### Recommendation
**Option A** (which, under Decision-1 Option 1, is a documented HITL-runbook step alongside the apply/deploy chain). Keeps Appendix C.7 unchanged.

---

## What the orchestrator does on each ruling
- **Decision 1 → Option 1:** author 12.7c with a narrow plan-only CI role (ECR push + state + read/describe) + `github_repo` var; author the apply/deploy/smoke chain + the api.wc upsert as a `docs/runbooks/` procedure; 12.11's workflow = gates+build+plan; add an architecture note reconciling §13. *(Options 2/3: broader role + permissions-boundary / GH-Environment; 12.11 runs the full chain.)*
- **Decision 2 → Option A:** the api.wc upsert is a runbook/CI step (per Decision 1); annotate the 12.7a tracker entry as resolved. *(Option B: atomic Appendix C.7 edit + an external-dns manifest in 12.8.)*

Both decisions are independent; the human can rule on them separately.

---

## Lead ruling / Human decision — 2026-06-02 (RESOLVED)

> Relayed by `st6-infra-team-lead` from the human. Recorded here in the infra worktree as the canonical durable record (the lead's own edit landed in a sibling location). Both decisions resolved; queue resumed.

### Decision 1 → **Full-apply CI, hardened defense-in-depth** (Option 3 + Option 2 combined)
The CI workflow performs the full §13 deploy, but the broad role is capped + gated so the `iam:*` escalation surface is neutralized:
- **IAM permissions boundary** on the CI deploy role (caps the role + any IAM roles it creates).
- **GitHub Environment `production` + required reviewers** — a human approval gate per deploy.
- **OIDC subject environment-scoped**: `repo:<owner>/<repo>:environment:production`; no static keys.
- New **required var `github_repo`** (`owner/repo`, no default).
- **12.11 = the full §13 chain**, authored complete + `actionlint`-clean. Real run stays HITL-triggered (human supplies the AWS account + approves the Environment gate) — still author+validate-only on our side.

### Decision 2 → **Option B, external-dns** (declarative / self-healing)
- **external-dns Deployment** manifest in 12.8 + an Ingress hostname annotation for `api.wc.${ROOT_DOMAIN}`.
- **Scoped IRSA** for external-dns: `route53:ChangeResourceRecordSets` + `route53:ListHostedZones` + `route53:ListResourceRecordSets` on **ONLY the project hosted zone** (least-privilege).
- **Atomic Appendix C.7 edit** documenting the added external-dns component (orchestrator writes, per the Step-9 matrix).
- **Supersedes** the "api.wc deferred / post-deploy script" carry-forward from 12.7a — external-dns owns that record now.

### Orchestrator follow-through
- 12.7c (CI role) authored per D1: broad deploy policy + permissions boundary + boundary attachment + GitHub OIDC provider (env-scoped subject) + CI EKS access entry + `github_repo` var. → `iam_ci.tf`.
- 12.8 absorbs external-dns per D2: external-dns IRSA role (scoped to the hosted zone) + external-dns Deployment/SA manifests + the Ingress `api.wc` hostname annotation; + the atomic Appendix C.7 edit.
- 12.11 = full §13 pipeline (gates → build/push → tf apply → migration Job → deploy → S3 sync + CF invalidation → deployed smoke), `actionlint`-clean; the `production` Environment gate + env-scoped OIDC. Real run HITL.
- Posture unchanged: author + validate only; no apply/deploy on our side.

---

## Decision 3 — permissions-boundary blast radius (refines D1; blocks 12.7c) · SAFETY (rule #5) · OPEN

> Surfaced implementing D1 at 12.7c Step-2.5 (the lead pre-authorized re-escalation for new safety/arch questions). 12.7c is HELD at Step-2.5 (not committed) pending this ruling. Date: 2026-06-02.

### The finding
D1 chose a permissions boundary on the CI role **"+ any IAM it creates."** The faithful implementation is a deploy-policy `Deny` on `iam:CreateRole`/`PutRolePolicy`/`AttachRolePolicy` when `iam:PermissionsBoundary != ci_boundary` (so every role CI creates is itself bounded — closes the escalation loop). The JSON audits clean. **But that deny blocks the CI role from creating ANY unbounded role during `terraform apply` — including the community-module-created roles** we already committed: the `terraform-aws-modules/eks` **cluster role + managed-node-group role** and the **ALB-controller IRSA role** (12.2), plus the 4 workload IRSA roles (12.7b) and the external-dns role (12.8). On the first `terraform apply`, CI creates all of these → the deny fires → **the §13 apply fails** unless `ci_boundary` is threaded through *every* terraform-created role.

### Options
- **Option A — thread `ci_boundary` through ALL roles (full D1 fidelity; most secure).** Set `permissions_boundary`/`iam_role_permissions_boundary` on: the 4 workload IRSA roles (edit `iam_irsa.tf`), the eks module cluster + node-group roles + the ALB-controller iam-submodule role (edit `eks.tf` — the modules expose boundary inputs), the external-dns role (12.8). Feasible; **effective least-privilege unchanged** (a boundary only caps max — the roles' narrow inline policies are unaffected). **Invasive:** touches committed `eks.tf` (12.2) + `iam_irsa.tf` (12.7b) + 12.8. Fully honors "every IAM it creates is bounded."
- **Option B — drop `DenyUnboundedRoleWrite`; keep the CI role's own boundary + backdoor/tampering denies + env-scoped OIDC + the GitHub-Environment reviewer gate (simpler).** The config applies cleanly. The residual escalation path (CI creates an unbounded admin role + assumes it) is mitigated by: the **production Environment reviewer gate (a human approves every deploy)** + the backdoor denies (no user/access-key creation) + env-scoped OIDC. Weaker *technical* control than A, but leans on the reviewer gate D1 already chose. Note: the CI role's own boundary does NOT cap roles it creates — only `DenyUnboundedRoleWrite` did that — so dropping it genuinely reopens the create-then-assume path (reviewer-gated).
- **Option C — bootstrap/steady-state split.** A human runs the FIRST cluster-bootstrap apply (creating the module roles) with elevated creds outside the CI role; CI (with `DenyUnboundedRoleWrite`) handles steady-state. Operationally awkward; not recommended.

### Recommendation + which is least-privilege-truest
**Least-privilege-truest = Option A, PLUS the self-protection denies from residual-point #2** (thread `ci_boundary` everywhere + extend the deny to `iam:PutRolePermissionsBoundary`-with-wrong-boundary + Deny edits to the boundary policy ARN). That closes both the create-then-assume loop AND the self-weakening loop; the only residual is the inherent broad-account-service-access (#1), which no TF-apply role escapes. This is consistent with the hardened defense-in-depth path you chose in D1 — but it's the most invasive (touches committed `eks.tf`/`iam_irsa.tf` + the boundary self-protection denes) .

**Option B** (drop `DenyUnboundedRoleWrite`) is the pragmatic simplification: the config applies cleanly and the production-Environment **reviewer gate** (a human approves every deploy) becomes the load-bearing control for residuals #1–#3. Defensible for a single-operator assessment the user controls; weaker *technical* control.

My lean: **A-with-self-protection-denies if you want the boundary to actually mean something** (a boundary that the role can trivially replace is security theater — so if we keep `DenyUnboundedRoleWrite`, we MUST also close #2, else Option A gives false assurance). **B if you'd rather not thread boundaries through the community modules** and trust the reviewer gate. What I'd AVOID: the implementer's drafted middle-ground (keep `DenyUnboundedRoleWrite` but don't close #2 and don't thread the modules) — it's both invasive-incomplete AND self-weakenable. Your risk call.

### Residual blast radius — even WITH the permissions boundary (the precise concern)
A boundary caps *effective* permissions but does not, by itself, make the role harmless. With the implementer's drafted hardening (boundary capping to ~18 deploy services + `DenyUnboundedRoleWrite` + `DenyStaticCredentialBackdoors` + `DenyBoundaryTampering`), the residual blast radius is:
1. **Broad account-wide service access (inherent).** The boundary caps to the deploy *services* (ec2/eks/rds/s3/iam/…) but on `Resource:*` — so the role can read/modify/delete **any** resource in those services account-wide, not just WC's. No TF-apply role avoids this; it's the irreducible deploy-role residual.
2. **★Self-weakening gap (a real hole in the drafted boundary).** `DenyBoundaryTampering` denies `iam:Delete{Role,User}PermissionsBoundary` — but **NOT `iam:PutRolePermissionsBoundary`** (replace) nor edits to the `ci_boundary` policy itself (`iam:CreatePolicyVersion`/`SetDefaultPolicyVersion`/`DeletePolicy` on the boundary ARN). Since the boundary *allows* `iam:*`, the role could **replace its own (or a created role's) boundary with a weaker one, or rewrite the `ci_boundary` policy** → escape the cap entirely. **So the boundary is NOT self-protecting as drafted.** To close it, Option A must ALSO: extend the `DenyUnbounded…` condition to cover `iam:PutRolePermissionsBoundary` (allow only when the new boundary = `ci_boundary`), and Deny `iam:CreatePolicyVersion`/`SetDefaultPolicyVersion`/`DeletePolicy` on the `ci_boundary` policy ARN.
3. **Unrestricted `iam:PassRole`** — minor in this account's role set (no powerful pre-existing role to pass), but ideally scoped to `wc-*` roles.

The **production-Environment reviewer gate** (a human approves every deploy) is the backstop that covers ALL of the above regardless of option.

### Effect on 12.7c
- **Option A:** 12.7c commits the CI role + boundary + denies; ALSO threads the boundary into `iam_irsa.tf` (this slice) + I instruct `eks.tf` edits + 12.8 external-dns boundary. (Larger 12.7c + a 12.2 touch.)
- **Option B:** 12.7c commits the CI role + boundary + backdoor/tampering denies + env-scoped OIDC, **minus** `DenyUnboundedRoleWrite`. No threading; no module/IRSA edits. Smallest 12.7c.
- The 12.7c JSON is otherwise audited-clean either way; only `DenyUnboundedRoleWrite` + the threading scope changes.

### Human ruling on Decision 3 — 2026-06-02 (RESOLVED) → **Option A, full (airtight)**
Relayed by the lead. Pause ALSO lifted — full queue resumes, no idle-hold. The boundary must actually mean something (a role-rewritable boundary is theater), so close every loop:
1. **Thread `ci_boundary` through EVERY Terraform-created role:** the 4 workload IRSA roles (`iam_irsa.tf` — re-touches 12.7b); the EKS module **cluster + node-group** roles + the **ALB-controller** role (`eks.tf` — re-touches 12.2, via the modules' boundary inputs); the **external-dns** role (12.8).
2. **Keep `DenyUnboundedRoleWrite`** (deny `iam:CreateRole`/`PutRolePolicy`/`AttachRolePolicy` when the new boundary ≠ `ci_boundary`).
3. **Close the self-weakening gap (residual #2):** extend the deny to **`iam:PutRolePermissionsBoundary`** (allow only when the new boundary = `ci_boundary`), AND Deny **`iam:CreatePolicyVersion`/`SetDefaultPolicyVersion`/`DeletePolicy`** on the `ci_boundary` policy ARN.
4. **Scope `iam:PassRole` to `wc-*` roles** (residual #3 — cheap; do it since we're going full best-practice).
5. **Residual #1 (broad account-wide access across the ~18 deploy services) is ACCEPTED** — irreducible for any apply-role; backstopped by the production-Environment reviewer gate; documented as the accepted residual.

**Effect:** 12.7c grows (CI role + boundary + full denies + threads `iam_irsa.tf`) + re-touches committed `eks.tf` (12.2); the 12.8 external-dns role carries the boundary too. Re-touching committed slices is approved.
**Then:** resume the queue normally — 12.8 → 12.9 → 12.11. Per-slice context pings resume. Escalate only a NEW cat-1/cat-4 question.

### D3 implementation refinement — 2026-06-02 (orchestrator-settled; human FYI/objection-window)
Implementing Option A's "thread `ci_boundary` everywhere" surfaced that an **enumerated boundary Allow would BREAK roles** (spike-verified): the **ALB-controller** role's module policy needs `cognito-idp`/`shield`/`waf-regional`/`wafv2` (outside the 18-service list — the controller calls `wafv2:GetWebACLForResource` + `shield:GetSubscriptionState` even for plain ALBs during ingress reconciliation), and the eks **cluster/node** roles use **AWS-managed policies whose contents aren't locally auditable** and can drift. A boundary that caps a role below its needs breaks it.

**Resolution (settled): `ci_boundary` is an escalation-guardrail, not a service-allowlist** — `Allow: "*"` on `"*"` + ALL the escalation **Deny** statements (DenyStaticCredentialBackdoors + DenyBoundaryTampering + DenyUnboundedRoleWrite [incl. `iam:PutRolePermissionsBoundary`] + DenyBoundaryPolicyEdit + scoped PassRole). The escalation-prevention the human chose is delivered ENTIRELY by the Denies — independent of Allow breadth. Service-level least-privilege stays where it belongs: the **identity** policies (CI deploy = 18 services; each workload IRSA = its 1–2 actions; ALB-controller = its module policy). Effective perms = intersection(boundary, identity) = each role's identity policy minus the denied escalation actions — **unchanged + uncapped**, and robust against AWS-managed-policy drift. This is the standard AWS permissions-boundary delegation pattern.

**Why orchestrator-settled (not re-escalated):** this is FORCED by D3's own "thread the boundary everywhere" + correctness — (a) enumerated cannot thread without breaking roles, so (b) the guardrail boundary is the only correct realization of the ruled intent, NOT a new decision. The escalation-prevention posture the human approved is 100% preserved. Flagged to the human as an FYI with an objection window (the visible change is the boundary `Allow=*`, which — given the Denies + identity policies — is the mechanism, not a weakening).

---

## Lead ruling (relaying the human's decision — 2026-06-03)

### Decision 1 → **Full-apply CI, hardened defense-in-depth** (Option 3 + Option 2's permissions boundary)
The human is fine with CI performing the deploy, and explicitly wants the architecturally-correct posture. So the broad role is taken **but capped + gated** so the `iam:*` escalation surface that triggered this escalation is neutralized:
- **IAM permissions boundary** on the CI deploy role — the role (and any IAM it creates) cannot exceed an explicit ceiling. This is the direct mitigation for the `iam:*` privilege-escalation risk.
- **GitHub Environment** (`production`) with **required reviewers** — a human approval gate before each deploy run.
- **OIDC subject environment-scoped** (`repo:<owner>/<repo>:environment:production`); no long-lived/static keys (REQ-S-010 / RISK-016).
- New required var **`github_repo`** (`owner/repo`, no default).
- **12.11 runs the full §13 chain** (gates → build/push → `terraform apply` → migration Job → deploy api/worker+cron → S3 sync + CloudFront invalidation → deployed smoke). The real run is still HITL-triggered for this assessment (human supplies AWS account + approves the Environment gate); the workflow is authored complete + `actionlint`-clean.

### Decision 2 → **Option B: external-dns** (declarative, self-healing — the best-practice answer)
- New **external-dns Deployment manifest** (in 12.8) + Ingress hostname annotation for `api.wc.${ROOT_DOMAIN}`.
- **Scoped IRSA role** for external-dns: `route53:ChangeResourceRecordSets` + the read actions it needs (`ListHostedZones`/`ListResourceRecordSets`) on **only the project's hosted zone** — least-privilege, consistent with the rule-#7 discipline held all phase.
- **Atomic `Appendix C.7` edit** documenting the added component (architecture-doc note per the Step-9 routing matrix — orchestrator writes).
- **Supersedes** the "api.wc deferred / post-deploy script" carry-forward from 12.7a — external-dns now owns that record declaratively; no scripted/runbook DNS step needed.

**Unblocks:** 12.7c (broad role + permissions boundary + env-scoped OIDC + `github_repo` var), 12.8 (k8s SAs/ingress + external-dns manifest + its IRSA), 12.9, 12.11. Orchestrator resumes the queue.

---

## Decision 4 — EKS public API endpoint exposure → ACCEPTED RESIDUAL (var-scopeable) — 2026-06-02

> Surfaced by the Phase-12 comprehensive final audit (`docs/audits/001-phase12-audit.md`, finding **M2**); human-approved remediation relayed by the lead.

### The posture question
The EKS cluster has `endpoint_public_access = true` with no CIDR restriction → the public Kubernetes API endpoint defaults (via the module) to `0.0.0.0/0`. Access is still gated by IAM/OIDC access entries and the **private** endpoint path is also enabled (defense-in-depth) — so this is an exposure-surface posture, **not** an unauthenticated open admin port. But the wide-open CIDR was an unstated module default.

### Ruling (human-approved) — keep public, make it scopeable, accept the open default as a documented residual
- **Added `variable "eks_public_access_cidrs"`** (`list(string)`, default `["0.0.0.0/0"]`) in `variables.tf`, wired to the eks module's `endpoint_public_access_cidrs` (12-audit fix-slice A). The operator CAN now scope the public endpoint to a known egress (e.g. a self-hosted-runner / office CIDR) by overriding the var.
- **Accepted residual (default `0.0.0.0/0`):** for the assessment, CI runs from GitHub-hosted runners (dynamic IPs), so fully scoping the public endpoint would force a self-hosted runner or a published-GitHub-IP set. The open default is accepted — backstopped by IAM/OIDC auth-gating + the private path — and is now **explicit** (this note), the same way **D3 residual #1** (the boundary's broad service ceiling) was accepted. A reviewer expecting a scoped endpoint sees the trim was intentional + the knob exists.
- **Phase-13 hardening candidate:** scope `eks_public_access_cidrs` to a real egress (or go private-only with a bastion/VPN) — surface in the §20 hardening set alongside Multi-AZ / role-separation / SHA-pinned actions.

**Audit cross-ref:** see `docs/audits/001-phase12-audit.md` M2.
