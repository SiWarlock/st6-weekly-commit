# LESSONS.md — ST6 Weekly Commit Module (infrastructure)

> Full prose for every lesson logged during work in `infra/`. The compact index lives in `infra/CLAUDE.md` "Lessons logged" table.
>
> **Lesson numbers are stable IDs.** New lessons get the next sequential number. Numbers may be referenced from code comments, commit messages, and cross-references between lessons. **Don't reorder; don't reuse a deleted number's slot.**
>
> **Lessons start at §1.** Each code area has its own lesson sequence — lessons don't carry across code areas.

---

## Lesson format

```markdown
## <a id="N"></a>N. <Short topic> — <one-line rule>

**Date:** YYYY-MM-DD.
**Source slice:** <slice-id or commit hash>.

<2-5 paragraphs explaining: what was discovered, why it matters, how to
apply the rule, what edge cases are still open. Cite file:line references
where applicable.>

**Rule:** <one-sentence summary, same as the heading subtitle>.
```

---

<a id="1"></a>
## 1. Forward-declared Terraform symbols vs `terraform_unused_declarations` — mute the one rule, never weaken the gate

**Date:** 2026-06-02.
**Source slice:** 12.1 (Terraform root skeleton).

A foundation-first Terraform slice legitimately declares variables, an output-aggregation file, and provider aliases that *no resource consumes yet* — their consumers land in later slices (e.g. `ROOT_DOMAIN` → 12.6/12.7, `rds_minor_version` → 12.3, the `aws.us_east_1` provider alias → 12.6's CloudFront cert). tflint's `recommended` preset enables `terraform_unused_declarations`, which flags every such forward-declaration and returns rc=2 — failing the lint gate for code that is correct-by-design.

The wrong fix is to broaden the escape hatch: `tflint --minimum-failure-severity=error` makes *all* warnings non-blocking and silently guts the entire gate, so a genuinely-unused declaration introduced later slips through unnoticed. The right fix is surgical: disable **only** `terraform_unused_declarations` in `.tflint.hcl`, with an inline comment naming each forward-declared symbol it is muting **and** a "RE-ENABLE at <slice>" marker (here: 12.7, once all three gain consumers). Every other recommended rule stays blocking, and the mute is self-documenting + time-boxed.

Pair the mute with a Carry-forward entry so the re-enable actually happens — a muted rule with no re-enable trigger quietly becomes permanent.

**Rule:** Never leave a forward-declared var/output/provider-alias naked under `terraform_unused_declarations`; mute that ONE rule with an inline reason + a dated re-enable marker, and never globally weaken the lint gate.

<a id="2"></a>
## 2. Terraform verification recipe under the author+VALIDATE scope (no AWS creds)

**Date:** 2026-06-02.
**Source slice:** 12.1 (Terraform root skeleton).

This track authors + validates IaC but does not hold AWS credentials — `terraform apply`, real `terraform init` against the S3 backend, and any `terraform plan` (which makes provider/data-source API calls) are Human-In-The-Loop. The agent-side verification recipe that runs without creds and proves a slice is internally sound:

```bash
terraform -chdir=infra/terraform fmt -check -recursive   # canonical formatting
terraform -chdir=infra/terraform init -backend=false     # provider constraints resolve; no S3 backend touched
terraform -chdir=infra/terraform validate                # internally consistent; no undeclared-resource refs
tflint                                                    # lint (terraform ruleset) rc=0
```

Negative/guardrail behavior that only surfaces at `plan` time (variable `validation{}` blocks firing, required-variable errors) is plan-time → HITL; it can be *pre-verified* in a throwaway local-state spike (cleaned up, repo tree untouched) and documented, but the canonical agent-side gate is the four commands above. Acceptance items phrased "integration: `terraform plan` shows…" are HITL-deferred — tick them with a parenthetical, don't claim them green agent-side.

**Rule:** Agent-side Terraform gate = `fmt -check` + `init -backend=false` + `validate` + `tflint` rc=0; real `init`/`plan`/`apply` and any plan-time assertion are HITL-deferred and marked as such.

<a id="3"></a>
## 3. `outputs.tf` is append-only per slice — never reference a not-yet-created resource

**Date:** 2026-06-02.
**Source slice:** 12.1 (Terraform root skeleton).

Appendix C.6 pins a single `outputs.tf` for the Terraform root, but its eventual exports (RDS endpoint, ECR URLs, SNS ARN, SQS URLs, S3 bucket, CloudFront id, hosted-zone id) reference resources created across slices 12.3–12.10. Declaring all of them in the foundation slice makes `terraform validate` fail for the *entire root* — a reference to an undeclared resource is a hard error, not a warning, so it blocks every subsequent slice's verification too.

The pattern: `outputs.tf` ships in the foundation slice as a structured **comment block enumerating the planned exports**, with **zero live `output` blocks**. Each later slice that creates a resource appends its own `output` in the same change. `validate` stays green at every slice boundary, and the comment block documents the contract so the file's end-state is legible from slice 1.

**Rule:** `outputs.tf` is append-only per slice — enumerate planned exports as comments, add a live `output` only in the slice that creates its backing resource; never reference a resource a later slice creates.

<a id="4"></a>
## 4. Community Terraform modules have no lockfile — the `version` constraint IS the pin

**Date:** 2026-06-02.
**Source slice:** 12.2 (VPC + EKS + ALB controller).

`.terraform.lock.hcl` pins **providers** (exact version + hashes), but it does **not** pin **module** versions — a registry module is pinned solely by the `version` argument in its `module {}` block. A bare or loose constraint means a later `terraform init` can silently pull a newer module version with different behavior.

Pin the **major** with `~> N` (e.g. `terraform-aws-modules/eks/aws` at `~> 21.0`): major-pinning blocks breaking changes, while minor/patch within a major are backward-compatible by semver. Verify the **current** module version against the live registry at author time (don't pin from memory), and adopt the current major on greenfield. Exact-pinning (`= 21.23.0`) is available if strict reproducibility is ever required, but `~> major` is the ecosystem norm and the default here.

**Rule:** Community modules have no lockfile — the `version = "~> N"` constraint is the pin; verify the current major against the live registry at author time and pin the major.

<a id="5"></a>
## 5. Configure a provider in the slice that first uses it — declared-only is lint-clean

**Date:** 2026-06-02.
**Source slice:** 12.2 (VPC + EKS + ALB controller); extends 12.1 Q5.

Declare every provider you'll eventually need in `required_providers` up front (so the lockfile is stable), but add the **configured `provider "X" {}` block only in the slice that first consumes that provider**. A configured-but-unused provider is noise; worse, configuring a provider that depends on a not-yet-created resource (e.g. the `helm`/`kubernetes` providers keyed off EKS cluster outputs) forces awkward ordering.

In 12.2 the `helm` provider is configured (keyed off `module.eks` host/CA + an `exec` `aws eks get-token`) because the ALB-controller `helm_release` is its first consumer; the `kubernetes` provider stays **declared-only** because nothing in Terraform manages a k8s object yet. With EKS API auth-mode + all k8s manifests applied by `kubectl` in CI (not Terraform `kubernetes_manifest`), the `kubernetes` provider may stay declared-only **permanently** — and that's fine: `terraform_unused_declarations` does **not** flag `required_providers` entries, so declared-only is lint-clean.

**Rule:** Declare providers up front; configure each in the first slice that consumes it. Declared-but-unconfigured is lint-clean and correct — don't configure a provider before something uses it.

<a id="6"></a>
## 6. Author community modules against the CURRENT-major schema, not memory

**Date:** 2026-06-02.
**Source slice:** 12.2 (VPC + EKS + ALB controller).

Major versions of popular modules rename inputs and restructure submodules — authoring from memory (or from a tutorial written against an older major) produces config that fails `init`/`validate` with confusing errors. Two live traps caught at 12.2 by checking the registry + running a throwaway spike before authoring the real files:

- `terraform-aws-modules/eks` **v21** renamed cluster inputs: `cluster_name`→`name`, `cluster_version`→`kubernetes_version`, plus `addons`/`endpoint_public_access` replacing older names.
- `terraform-aws-modules/iam` **v6** renamed the IRSA submodule `iam-role-for-service-accounts-eks`→`iam-role-for-service-accounts` — the old `-eks` path 404s at `init`.

The author-time registry/Context7 check + a disposable spike (cleaned up, repo tree untouched) catch these before they reach a commit, and let the Step-2.5 write-up cite empirical results instead of guesses.

**Rule:** Before authoring against a community module major, verify its current input/submodule schema against the live registry/Context7 and de-risk in a throwaway spike — module majors rename things; memory drifts.

<a id="7"></a>
## 7. DB master password — `random_password` → secret slice; and the `random` provider must be declared

**Date:** 2026-06-02.
**Source slice:** 12.3 (RDS PostgreSQL 16, a safety-rule-#7 slice).

For a datastore whose credentials are contractually sourced from a Secrets Manager secret (Appendix D.2 `spring.datasource.*` ← the `db` secret), the password-handling pattern that keeps the binding contract intact (Pattern 2): generate `random_password.db`, pass it as the RDS `password`, and let the **secret slice (12.6)** populate the `db` secret from `aws_db_instance.<x>.address/port` + `random_password.db.result`. The provisioning slice (12.3) creates **no** secret (it doesn't exist yet — LESSONS §3) and exposes **only** the endpoint (`host:port`, no credentials). The password lives **only** in encrypted, access-controlled TF state — **never an `output`, never a log line** (safety rule #7 / RISK-016). `publicly_accessible=false` is hardcoded (not a var, so it can't be flipped on); DB ingress is `referenced_security_group_id = <node SG>` only, never a CIDR.

The alternative (Pattern 1, `manage_master_user_password=true`) is more secure (AWS-managed + rotated, no password in state) but reshapes the D.2 contract (the app reads the AWS-managed secret) → it requires an atomic D.2 edit + a load-bearing-decision escalation. Both `validate`; choose Pattern 2 unless there's a feasibility/security reason to deviate.

Provider gotcha caught here: **`random_password` requires the `random` provider to be explicitly declared in `required_providers`.** It is **not** pulled transitively by the eks/vpc/iam modules (those bring cloudinit/null/time/tls, not random). Using `random_password` without declaring `random` trips tflint's `terraform_required_providers` rule (rc=2). Declare `random = { source = "hashicorp/random", version = "~> 3.0" }` and re-lock.

**Rule:** DB master password = `random_password` consumed by RDS + populated into the `db` secret by the secret slice — never an output/log; and explicitly declare the `random` provider (it is NOT a transitive module dep, and `random_password` without it fails tflint).

<a id="8"></a>
## 8. ECR repository posture — IMMUTABLE + scan-on-push + untagged-expiry, exactly api+worker

**Date:** 2026-06-02.
**Source slice:** 12.4 (ECR repositories).

Image repos are SHA-tagged (image tag = commit SHA, §13). The standard posture: `image_tag_mutability = "IMMUTABLE"` (a SHA tag can never be overwritten — prevents accidental tag reuse), `image_scanning_configuration { scan_on_push = true }` (cheap vuln-scan win), and a minimal `aws_ecr_lifecycle_policy` expiring untagged images after ~14 days (bounds storage). Provision **exactly the two repos the architecture pins** — `wc-api` and `wc-sync-worker` — via a 2-element `for_each = toset([...])` so a third repo is structurally impossible; the CronJob + migration Job **reuse the `wc-api` image** via Spring profiles/args (§8/§12), never a third image.

**Rule:** ECR repos = IMMUTABLE + scan_on_push + untagged-expiry lifecycle, created via a fixed `for_each` set of exactly the api + worker repos — CronJob/migration reuse `wc-api`, never a third image.

<a id="9"></a>
## 9. Secret-value split — TF populates derived secrets; real third-party creds are HITL-populated placeholders

**Date:** 2026-06-02.
**Source slice:** 12.6 (Secrets Manager, a safety-rule-#7 slice).

Not all Secrets Manager secrets are populated the same way:

- **Derived secrets (e.g. `db`)** — the value is composed from other TF resources (RDS endpoint + `random_password.db.result`), so TF writes an `aws_secretsmanager_secret_version`. The value renders into encrypted state only; the ARN is exported, the **value is never an `output`** (rule #7).
- **Real third-party credentials (e.g. `auth0`, `graph`, `demo`)** — these are NOT knowable to Terraform (Auth0/Graph client secrets). TF creates the secret **container** + a **placeholder version** (`REPLACE_VIA_HITL`) with `lifecycle { ignore_changes = [secret_string] }`, and a HITL operator populates the real value out-of-band. `ignore_changes` stops TF from reverting the human-set value on the next apply. Real creds thus never touch TF state or `*.tfvars`.

Shape the placeholders with the **contract keys** (Appendix D.2/D.3: db→`spring.datasource.{url,username,password}`, auth0→`jwt.issuer-uri`+`auth0.audience`, graph→tenant/client/secret) so the secret structure is self-documenting and a pre-population CSI mount with jmesPath still resolves. The authoritative secret-key ↔ SecretProviderClass-jmesPath alignment is finalized in the k8s-manifest slice.

**Rule:** TF populates derived secrets (value in encrypted state, never an output); real third-party creds are a container + placeholder-version + `ignore_changes=[secret_string]`, HITL-populated out-of-band — never in TF state/vars.

<a id="10"></a>
## 10. CloudFront ships on the default cert in its own slice; the custom-domain ACM cert + alias attach in the Route53/cert slice

**Date:** 2026-06-02.
**Source slice:** 12.6 (CloudFront) → 12.7 (Route53/ACM).

A CloudFront distribution's custom domain needs a us-east-1 ACM cert, and that cert is **DNS-validated against the Route53 hosted zone**. When the zone + cert live in a later slice than the distribution, the distribution slice **cannot** reference the not-yet-created cert (LESSONS §3). Resolution: the distribution slice ships with `viewer_certificate { cloudfront_default_certificate = true }` and **no `aliases`** (fully functional over `*.cloudfront.net`); the Route53/cert slice **edits the distribution in place** to swap in `acm_certificate_arn` + `aliases`. A parameterized-var approach does NOT work — the cert ARN is a resource attribute, and a variable can't reference a resource, so it would force a manual two-apply `tfvars` dance. Direct edit in the cert slice is the clean pattern (and it's the slice that owns cert/DNS wiring).

**Rule:** When a CloudFront distribution precedes its Route53/ACM slice, ship it on the default cert with no aliases; the cert slice edits the distribution in place to attach the us-east-1 ACM cert + alias (a var can't reference the cert resource).

<a id="11"></a>
## 11. ACM certs — CloudFront in us-east-1 (aliased provider), ALB regional, both DNS-validated; consumers reference `_validation.certificate_arn`

**Date:** 2026-06-02.
**Source slice:** 12.7a (Route53 + ACM).

CloudFront requires its viewer cert in **us-east-1** regardless of the deploy region (RISK-010) — create it with `provider = aws.us_east_1` (the aliased provider declared in the root). The ALB cert is **regional** (default provider). Both use `validation_method = "DNS"` with the canonical pattern: `aws_route53_record` via `for_each` over the cert's `domain_validation_options`, plus an `aws_acm_certificate_validation` resource (the us-east-1 cert's validation resource ALSO needs `provider = aws.us_east_1`). Set `create_before_destroy` on the certs.

Consumers (the CloudFront `viewer_certificate`, the ALB ingress annotation) must reference the **`aws_acm_certificate_validation.<x>.certificate_arn`** — NOT the raw `aws_acm_certificate.<x>.arn` — so the consumer waits for DNS validation to complete before using the cert. The hosted zone is a **data source** (the domain is HITL-registered; we manage records, not the zone). The CloudFront alias A-record points at the distribution's `domain_name` with the fixed CloudFront hosted-zone id `Z2FDTNDATAQYW2`.

**Rule:** CloudFront cert → us-east-1 aliased provider; ALB cert → regional; both DNS-validated; consumers reference `_validation.certificate_arn` (waits for validation); the Route53 zone is a data source.

<a id="12"></a>
## 12. Per-workload IRSA least-privilege — each SA gets exactly its actions on exact ARNs; no `*`

**Date:** 2026-06-02.
**Source slice:** 12.7b (per-workload IRSA — a safety-rule-#5/#7 slice).

Each EKS workload gets its OWN IRSA role scoped to **exactly** the AWS actions it needs, on **exact resource ARNs** — never a shared role, never `Resource:"*"` on secret reads. For WC (§12): api → `sns:Publish` (the lifecycle topic ARN) + `GetSecretValue` on [db, auth0, graph]; worker → `sqs:{ReceiveMessage,DeleteMessage,GetQueueAttributes}` on [queue, DLQ] + `GetSecretValue` on [db, graph] (db added Wave-2 s8 `adfa639` for the worker's SyncRecord reload — still ARN-scoped, NOT auth0/demo); cronjob + migration → `GetSecretValue` on [db ONLY]. The `demo` secret is granted to NO workload (it's not consumed in the deployed/auth0-mode env). **No `secretsmanager:GetSecretValue` on `*` anywhere** — the strongest single least-privilege check, greppable.

The trust policy federates the **cluster** OIDC provider (`module.eks.oidc_provider_arn`) with a `sub = system:serviceaccount:<ns>:<sa>` condition + `aud = sts.amazonaws.com`. The `<ns>`/`<sa>` strings form a **contract with the k8s manifest slice** (the ServiceAccount must use that exact namespace + name + the role-ARN annotation) — pin it explicitly. Inline `aws_iam_role_policy` (not a canned module) keeps the exact scoping auditable in one place. Audit the policy JSON before approving — least-privilege is the safety review, not a formality.

(Distinct concern: a CI/deploy role that runs `terraform apply` can't be this tight — if apply is HITL, prefer a **plan-only** CI role [ECR push + state + read/describe] to keep "least-privilege" true; see the §13 CI-role posture decision.)

**Rule:** Each workload gets its own IRSA role with exactly its §12 actions on exact ARNs; no `GetSecretValue` on `*`; OIDC `sub`+`aud` trust on the cluster issuer; the namespace+SA-name is a pinned contract with the k8s slice; audit the policy JSON as the safety review.

<a id="13"></a>
## 13. Hardened CI deploy role — a permissions boundary is an escalation GUARDRAIL (Allow * + Denies), not a service-allowlist

**Date:** 2026-06-02.
**Source slice:** 12.7c (CI deploy identity — a safety-rule-#5 slice); Decision 3 in `docs/decisions/001`.

A CI role that runs `terraform apply` + `kubectl apply` inherently needs broad service access — so "least-privilege" for it is delivered in two separate places:
- **Service-level least-privilege → the IDENTITY policy** (the CI role's deploy policy = the exact ~18 services it deploys; each workload IRSA = its 1–2 actions; a module role = its module policy).
- **Escalation-prevention → a permissions BOUNDARY** carrying `Allow: "*"` + a fixed set of **Deny** statements. The Allow-`*` makes the boundary a guaranteed **superset** of every role it's attached to (so it never caps a role below its needs — critical for module-created roles like the AWS Load Balancer Controller, which needs `wafv2`/`shield`/`cognito-idp`, and the EKS cluster/node roles which use **unauditable AWS-managed policies** that can drift). The escalation control is the **Denies**, independent of Allow breadth.

The Deny set (put them ON THE BOUNDARY so they cap the role AND every role bounded by it, regardless of identity policy): no static-credential backdoors (`iam:CreateUser`/`CreateAccessKey`/`CreateLoginProfile`/`PutUserPolicy`/…); no boundary tampering (`iam:Delete{Role,User}PermissionsBoundary`); **no unbounded-role creation** (`Deny iam:CreateRole`/`PutRolePolicy`/`AttachRolePolicy`/**`PutRolePermissionsBoundary`** when `iam:PermissionsBoundary != <this boundary>`) — and **thread the boundary onto EVERY Terraform-created role** so this deny doesn't block the apply; no boundary-policy edits (`Deny iam:CreatePolicyVersion`/`SetDefaultPolicyVersion`/`DeletePolicy` on the boundary's own ARN); scoped `PassRole` (Deny on non-`wc-*`). The OIDC trust is environment-scoped (`repo:<owner>/<repo>:environment:production`) with a GitHub Environment + required reviewers (HITL) — no long-lived keys.

Two gotchas: (1) the boundary's self-referencing Denies must reference a **constructed** ARN string (`arn:aws:iam::${account_id}:policy/<name>`), NOT `aws_iam_policy.x.arn`, or Terraform sees a self-dependency cycle — and the constructed ARN MUST exactly match the real policy ARN (HITL apply verifies). (2) The eks managed-node-group boundary input is the **per-node-group `iam_role_permissions_boundary`**, not `node_iam_role_permissions_boundary` (which is EKS-Auto-Mode-only). The irreducible residual — the boundary's broad service *ceiling* (`* − denies`) — is accepted and backstopped by the reviewer gate.

**Rule:** For an apply-role, put service-least-privilege in the identity policy and escalation-prevention in a permissions boundary (`Allow *` + the Deny set); thread that boundary onto every TF-created role; self-reference the boundary via a constructed ARN; env-scope the OIDC trust with reviewer approval; no static keys.

<a id="14"></a>
## 14. TF-output → k8s-manifest injection via `${PLACEHOLDER}` tokens + allowlisted CI `envsubst`

**Date:** 2026-06-02.
**Source slice:** 12.8 (k8s deploy surface + external-dns).

Several k8s manifest values are only known *after* `terraform apply` — the four IRSA role ARNs (SA annotations), the external-dns role ARN, the ALB ACM cert ARN, the three secret ARNs (SecretProviderClass `objectName`s), and `ROOT_DOMAIN`. Manifests are static YAML committed to the repo, so the value can't be a Terraform reference. The pattern: each unknown carries a `${TOKEN}` placeholder (e.g. `${IRSA_API_ROLE_ARN}`, `${ALB_CERT_ARN}`, `${ROOT_DOMAIN}`), and the 12.11 CI pipeline substitutes the tokens from `terraform output` values via `envsubst` immediately before `kubectl apply`. `kubeconform -strict` validates the manifests with the placeholders treated as plain strings, so the structure is provable agent-side without the real values.

**Use an *allowlisted* `envsubst`** — `envsubst '$ALB_CERT_ARN $DB_SECRET_ARN …' < f.yaml` (the explicit single-quoted variable list) so ONLY the intended token set is expanded. Bare `envsubst` expands *every* `$NAME` in the file, which would clobber any future manifest content that legitimately contains a `$` (shell-style args, regex, JSONPath). Keep the token set tight and enumerated, each mapping 1:1 to a named TF output — and re-derive it by grepping the live manifests, don't carry a hardcoded count (the set shifts as manifests/installs change: external-dns's role ARN left the envsubst set when it became a Terraform-managed `helm_release` in 12.2b, so the 12.11 pipeline's allowlist is 15 tokens, not the 16 a stale carry-forward implied). Scrub stray `${...}` strings out of comments so the live token set equals the real one.

**Rule:** Bind post-apply TF values into static k8s manifests with `${TOKEN}` placeholders + an **allowlisted** CI `envsubst` (enumerated variable list) before `kubectl apply`; `kubeconform` validates the placeholder form agent-side.

<a id="15"></a>
## 15. Per-SA SecretProviderClass mirrors IRSA least-privilege — and two correctness gotchas

**Date:** 2026-06-02.
**Source slice:** 12.8 (k8s deploy surface + external-dns).

The Secrets Store CSI Driver + AWS provider (ASCP) mounts secrets per ServiceAccount. Author **one SecretProviderClass per SA** whose `objects` list exposes ONLY the secrets that SA's IRSA role can read — a defense-in-depth mirror of the 12.7b least-privilege split (api → db/auth0/graph; worker → db + graph [db added Wave-2 s8]; cron/migration → db only). The IRSA role is the hard control (the pod literally can't `GetSecretValue` outside it); the per-SA SPC is the soft mirror that keeps the mount surface honest and the manifest auditable against the IAM policy.

Two correctness gotchas that bite silently:
1. **Dotted JMESPath keys MUST be double-quoted.** When extracting a secret-JSON key that contains dots (e.g. `spring.datasource.url`), the `jmesPath[].path` must be `'"spring.datasource.url"'` — unquoted, JMESPath parses the dots as nested field access (`spring` → `datasource` → `url`) and the extraction returns null. Keys without dots (e.g. `GRAPH_TENANT_ID`) stay unquoted.
2. **No `secretObjects:` block.** Mount the extracted values as files under `/mnt/secrets` (tmpfs) only; do NOT add a `secretObjects:` stanza, which would sync the values into a Kubernetes `Secret` (etcd-persisted) — a needless second copy and a rule-#7 (secrets-never-leak) violation. Spring reads the file-per-key mount via `spring.config.import=configtree:/mnt/secrets/` (config-tree = file-per-property; NOT `file:`, which expects a single parseable config document — see the Appendix D.2 reconciliation carry-forward).

**Rule:** One SecretProviderClass per SA exposing only that SA's IRSA-permitted secrets; double-quote dotted JMESPath keys; mount to tmpfs files with NO `secretObjects:` etcd sync.

<a id="16"></a>
## 16. k8s workload manifest conventions — per-pod CSI secret volume + the single-Flyway-owner / no-third-image audits

**Date:** 2026-06-02.
**Source slice:** 12.9 (Deployments + generation CronJob + migration/perf-seed Jobs).

Workload manifests (Deployments/Jobs/CronJob) consume the 12.8 secret surface and must honor two §12 invariants that are easy to violate silently. Bank the shape + the audit recipe:

**CSI secret volume, one per pod.** Each pod declares a `volumes[]` entry with `csi.driver: secrets-store.csi.k8s.io`, `readOnly: true`, and `volumeAttributes.secretProviderClass: <that workload's SPC>`, mounted `readOnly` at `/mnt/secrets`. The DB/Auth0/Graph **secret values are files** under that mount (read by Spring via `configtree:` — see §15), **never** plain `env:`. Only non-secret config (region, ARNs, `ROOT_DOMAIN`, profiles, flags) is `env:`, injected via `${PLACEHOLDER}` tokens (§14). The volume references the per-SA SPC by name, so the SA→SPC→IRSA least-privilege chain stays 1:1 across all four workloads. (The CSI Driver + AWS provider/ASCP that back this must be installed cluster-side via `helm_release` — a separate platform addon, peer to the ALB controller; a manifest that mounts the volume does not install the driver.)

**Two forbidden-pattern audits, grep-checkable before GREEN.** (1) **Single Flyway owner:** exactly ONE manifest carries `spring.flyway.enabled=true` (relaxed-binding `SPRING_FLYWAY_ENABLED=true`) — the migration Job; api/worker/cronjob/perf-seed are `false`. (2) **No third image:** the CronJob, migration Job, and perf-seed Job all reuse the **wc-api** image (`${ECR_API_IMAGE}`); only the worker uses `${ECR_WORKER_IMAGE}` — exactly two image tokens across all manifests. Both are one-line greps; run them at the verify step. The migration-before-deploy *ordering* is NOT a manifest property — the CI pipeline enforces it (`kubectl wait --for=condition=complete job/...`); the perf-seed Job is opt-in (excluded from the deploy chain, marked by an annotation/banner).

**Rule:** k8s workloads mount secrets as a per-pod CSI SecretProviderClass volume (files at `/mnt/secrets`, never env); grep-audit the single-Flyway-owner + no-third-image invariants before GREEN; ordering + opt-in exclusion live in the pipeline, not the manifest.

<a id="17"></a>
## 17. A manifest that USES a cluster add-on never INSTALLS it — and install all add-ons by one consistent mechanism

**Date:** 2026-06-02.
**Source slice:** 12.9 Step-2.5 Finding → 12.2b (cluster add-ons install + consistency).

Authoring the manifests that *consume* a cluster add-on is not the same as *installing* the add-on, and it's easy to ship the consumer while silently omitting the provider. Here: 12.6 (secrets) + 12.7b (IRSA) + 12.8 (SecretProviderClasses) + 12.9 (pod CSI volume mounts) all rode on the **Secrets Store CSI Driver + AWS provider (ASCP)** — but nothing installed the driver. It validates clean (the CSI volume is a core PodSpec field; the `SecretProviderClass` is referenced by name as a string), so `kubeconform`/`validate` are all green — yet at deploy the CRD wouldn't exist and the volumes wouldn't mount. **The audit that catches this:** for every cluster feature a manifest depends on (a CSI driver, an ingress class / controller, a DNS annotation, a webhook, an admission policy), confirm a corresponding *install* resource exists (a `helm_release` or applied manifest). A reachability pass over manifests must include their controllers, not just the workloads.

**Install all cluster add-ons by ONE mechanism.** The cluster grew three add-ons — ALB Load Balancer Controller, external-dns, Secrets Store CSI Driver + ASCP — and they had drifted into two install styles (`helm_release` vs a hand-written manifest). Because the ALB controller and the CSI driver are effectively helm-only (they ship CRDs/webhooks/DaemonSets that are impractical to hand-maintain), consistency converges on **all cluster add-ons via Terraform `helm_release`** (version-pinned, installed during `terraform apply`, before the app `kubectl apply`). external-dns — which has an official chart — was converted from its raw manifest to a `helm_release` to match. Draw the line at **platform controllers (Terraform `helm_release`) vs application workloads (kubectl manifests)**: SAs, SecretProviderClasses, Deployments, Jobs, Service, Ingress stay manifests; the controllers they depend on are `helm_release`s.

**Rule:** Every cluster-feature a manifest depends on needs its own install resource — audit controllers, not just workloads; install all cluster add-ons by one consistent mechanism (here: Terraform `helm_release` for platform controllers, kubectl manifests for app workloads).

<a id="18"></a>
## 18. GitHub Actions deploy pipeline — OIDC-only, env-scoped trust forces a single reviewer-gated AWS job, ordered graph

**Date:** 2026-06-02.
**Source slice:** 12.11 (GitHub Actions OIDC deploy pipeline).

The §13 deploy pipeline shape that satisfies the hardened-CI decisions (D1) — and the non-obvious constraint that shapes the whole job layout:

**env-scoped OIDC forces the job layout.** The CI role trust is `StringEquals` on `sub = repo:<repo>:environment:production` (12.7c), so **every job that calls `aws-actions/configure-aws-credentials` MUST run under `environment: production`** — otherwise its OIDC `sub` is `…:ref:refs/…` and `AssumeRoleWithWebIdentity` is **denied at the real run** (a latent failure `actionlint` cannot see). Putting AWS work in a separate non-environment job (e.g. a `build-images` job that pushes to ECR *before* the gated deploy) breaks. **Consolidate ALL AWS-touching steps — build+push, `terraform apply`, migrate, roll, S3 sync, deployed smoke — into ONE `environment: production` job** (one OIDC assumption + one reviewer approval); keep `gates` (no AWS) as a separate pre-job that runs on every trigger. (Two AWS jobs both referencing the environment would also double the reviewer prompt.)

**The rest of the shape:** OIDC-only, no static keys (`permissions.id-token: write`; `role-to-assume` = the env-scoped role; zero `aws-access-key-id`/`secrets.AWS*` inputs — REQ-S-010). Ordered graph: `gates → deploy` via `needs`; within the deploy job, sequential steps enforce the §12/§13 order so an out-of-order path is unreachable — `terraform apply` (installs the cluster add-ons, §17) **before** any `kubectl`; the migration Job + `kubectl wait --for=condition=complete` **before** any Deployment/CronJob roll (§12). Value injection via the allowlisted `envsubst` (§14), token set grepped from the live manifests. Non-secret config via GitHub `vars.*`; AWS via OIDC. Author+validate only (`actionlint`); the workflow orchestrates the app area's build/test/Docker targets (forward-deps) and runs HITL.

**Rule:** Deploy pipeline = OIDC-only (no static keys) + ALL AWS work in ONE `environment: production` job (the env-scoped trust requires it — a separate AWS job is denied at the real run) + `gates` separate + an ordered `needs`/sequential-step graph that makes apply-before-kubectl and migrate-before-roll unreachable to violate + allowlisted-`envsubst` injection.

<a id="19"></a>
## 19. A scoped resource-Deny on a NAME PREFIX breaks if a vendored module names a role outside the prefix

**Date:** 2026-06-02.
**Source slice:** Phase-12 final-audit finding C1 → remediation (brief 014).

D3 scoped the CI role's `iam:PassRole` to `role/wc-*` (a `NotResource` Deny). The intent: every Terraform-created role is `wc-*` + bounded, so PassRole-to-`wc-*` is the complete allowed set. But the EKS **managed node-group** IAM role is auto-named `default-eks-node-group-<suffix>` by the vendored eks v21 module — derived from the node-group **map key** (`default`), NOT the cluster name. So it falls **outside** `wc-*`, and creating the node group (which `PassRole`s the node role to the service) is denied by the CI role's **own** boundary → the first `terraform apply` fails with `AccessDenied`. The cluster role was fine (named from the cluster name → `wc-aws-cluster-*`); the asymmetry is that the node role derives from a different source.

This is the dangerous class: **a scoped deny on a name prefix is only as correct as your control over EVERY name in scope** — and vendored modules name resources from their own inputs (keys, defaults), not your prefix convention. Static validation (`validate`/`tflint`/no-plan) cannot see it; it only fires at apply, when the role is actually `PassRole`'d. The comprehensive pre-deploy audit caught it precisely because it reasoned about apply-time behavior, not just static structure.

Two defenses: **(1)** when a boundary/policy scopes by resource-name prefix, **audit every TF-created role name — including module-created ones — against the prefix**; don't assume the convention holds. **(2)** Force the name: set the module's `iam_role_name` input (here `iam_role_name = "${local.cluster_name}-node"` → `wc-aws-node`) so the role joins the prefix, rather than widening the scope. The C1 fix used (2) — it preserves the tight `wc-*` scope the human ruled.

**Rule:** A scoped resource-Deny/Allow on a name prefix is only correct if EVERY name in scope — including vendored-module-created roles named from keys/defaults, not your convention — matches the prefix; audit them all, and force module role names into the prefix (`iam_role_name`) rather than widening the scope.

## <a id="20"></a>20. Bootstrap the remote-state backend with a SEPARATE sibling TF root on a LOCAL backend — it can't store its state in the bucket it's creating

**Date:** 2026-06-04.
**Source slice:** 12.12 (brief 087 — the deployed-demo cold-start root).

The main `infra/terraform` root uses an S3 backend (`backend.tf`, partial config). But the S3 state bucket + DynamoDB lock that backend needs must exist **before** the first `terraform init` — a chicken-and-egg: you can't provision them *with* the main root (its state has nowhere to live yet).

- **Solution = a SEPARATE sibling root `infra/terraform-bootstrap/` with a LOCAL backend** (omit the `backend` block entirely → Terraform defaults to local). It provisions exactly the two backend resources: the versioned + encrypted (AES256 SSE) + public-access-blocked S3 state bucket, and the DynamoDB lock table (`hash_key = "LockID"` type `S`, `PAY_PER_REQUEST` — the S3-backend lock *contract*, the key MUST be `LockID`). Run once by a human with admin creds, before the main root's first `init`.
- **Deterministic naming, no random provider:** `wc-${env}-tfstate-${account_id}` (account id via `data.aws_caller_identity.current`) — re-apply finds the same name (idempotent); nothing stored in state for a suffix.
- **The state store is the crown jewels → fail-closed:** `prevent_destroy = true` + `force_destroy = false` on both the bucket + table. A stray `terraform destroy` then fails closed; a real teardown requires commenting `prevent_destroy` out first (a documented one-line caveat — runbook b).
- **The bootstrap's OWN state** is local + **gitignored** (`*.tfstate*` — the sibling dir is NOT covered by the main root's dir-scoped `.gitignore`, so it needs its own). OPTIONAL: `terraform init -migrate-state` moves it INTO the bucket it just created — but under a **DISTINCT key** (`wc/bootstrap.tfstate`), NEVER the main root's `wc/terraform.tfstate` (collision).
- **Verify** (infra path): `fmt -check` + `init -backend=false` + `validate` + `tflint` rc=0 on the mini-root, **plus** re-run the main-root gate to prove the `backend.tf` comment pointer is inert (no regression). Real `init`/`apply` is HITL (runbook b, LESSONS §2). Outputs are non-secret resource names only (rule #7) — `state_bucket_name`/`lock_table_name` + a paste-ready `backend_config_hint`.

**Rule:** bootstrap the remote-state backend with a SEPARATE sibling TF root on a LOCAL backend (it cannot store state in the bucket it creates); provision exactly the encrypted/versioned/public-blocked S3 bucket + the `LockID` DynamoDB lock; deterministic `account_id`-suffixed bucket name; `prevent_destroy` on both; gitignore the local state; optional `-migrate-state` under a DISTINCT key. (origin: 12.12)

## <a id="21"></a>21. A no-leak secret-population helper: env → `jq -n env.X` → `--secret-string file:///dev/stdin` (no argv, no disk, no log, no history)

**Date:** 2026-06-04.
**Source slice:** 12.13 (brief 088 — the Auth0/Graph secret-population helper).

A HITL helper that writes credential VALUES into Secrets Manager (`aws secretsmanager put-secret-value`) must satisfy rule #7 — no secret value reaches process argv (visible in `ps` / `/proc/<pid>/cmdline`), disk (temp files, the repo), a log/stdout, or the operator's shell history. The leak-proof mechanism (security-agent PASS):

- **Build the secret JSON with `jq -n` reading from the ENVIRONMENT** — `env.MY_VALUE`, **NOT `--arg`** (`--arg my "$v"` puts the value in jq's argv → leaked). Pass it as a one-shot command-prefix assignment scoped to the single jq process: `MY_VALUE="$v" jq -n '{"key": env.MY_VALUE}'`.
- **Pipe the JSON to `aws … put-secret-value --secret-string file:///dev/stdin`** — stdin; NEVER an inline `$VAR` (which hits argv), NEVER a temp file. (`mktemp` + `chmod 600` + `trap`-shred is the documented portable FALLBACK if `/dev/stdin` isn't viable — but the stdin pipe leaves nothing on disk.)
- **`read -s` only for the TRUE secret** (e.g. a client secret); public OAuth/tenant config (issuer-uri, audience, ids) can use visible `read` so the operator confirms the paste — the uniform no-leak JSON wrap protects everything regardless.
- **Input is env-or-interactive-`read`, NEVER a CLI arg** (lands in shell history) and NEVER a values file (an on-disk vector).
- **`set -euo pipefail`, NO `set -x`/`-v`** (which echo values); the only output is secret-ids, key NAMES, and the `VersionId`/`ARN` success signal — never a value. A **`--dry-run`** redacts to `***` and makes ZERO mutating AWS calls (guard the put behind the dry-run check). jq program strings stay single-quoted literals referencing only `env.X` (operator values flow through the env, not string-interpolation) → no JSON-break / command injection.

**Rule:** populate secrets via `env → jq -n env.X → aws put-secret-value --secret-string file:///dev/stdin`; `read -s` the true secret; env-or-prompt input (never a CLI arg / values file); `set -euo pipefail` with no `-x`; output only names + VersionId/ARN; a redacted zero-call `--dry-run`. (origin: 12.13)

## <a id="22"></a>22. An `--app.job` runner-Job terminates ONLY via `--spring.main.web-application-type=none`; without it the Job hangs forever

**Date:** 2026-06-04.
**Source slice:** 12.14 (brief 089 — the rebuild-projections Job; caught the generation-cronjob gap).

The one-shot `--app.job` runners (`ProjectionRebuildRunner`, `PlanShellGenerationRunner`) deliberately do NOT call `SpringApplication.exit` or close the context — termination relies ENTIRELY on launching with **`--spring.main.web-application-type=none`** (no web server → `main` returns → the JVM exits). A k8s Job/CronJob that launches such a runner **without** the arg starts a web server and **never reaches `Complete`** — it hangs to `backoffLimit`/timeout. (The migration Job avoids this because its `flyway-migrate` profile sets web-type=none; the runner-jobs must pass it as an arg since no profile does.)

- Always `args: ["--app.job=<name>", "--spring.main.web-application-type=none"]` on a runner Job, + **`activeDeadlineSeconds`** as the operational backstop.
- **Audit:** the generation-cronjob shipped WITHOUT the arg (no `application-generation.yml` set web-type=none) → its Jobs would hang forever. A manifest review of EVERY `--app.job` Job for the exit arg is the durable guard (caught + folded the fix at 12.14).

**Rule:** every k8s `--app.job` runner-Job must launch with `--spring.main.web-application-type=none` (+ `activeDeadlineSeconds`); without it the runner starts a web server + the Job never completes. Audit all runner-Jobs for the arg. (origin: 12.14)

