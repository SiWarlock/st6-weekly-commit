# Runbook (b) — Fresh AWS account → deploy-ready

> **Runbook set:** (a) `auth0-tenant-setup.md` (external tenants) → **(b) this file** → (c) `deploy-and-smoke.md`. This runbook takes a **bare AWS account + a domain + a GitHub repo** to the point where the deploy pipeline can run. It ends "deploy-ready"; runbook (c) runs the actual deploy.
>
> **The one load-bearing idea:** the **first `terraform apply` is run by a human with admin credentials** — it creates the GitHub OIDC provider + the hardened CI deploy role (among everything else). *After* that, the GitHub Actions pipeline (runbook c) authenticates via **OIDC** + that CI role for all subsequent applies/deploys — **no long-lived AWS keys ever live in CI** (REQ-S-010). So this runbook is the irreducible admin-cred HITL; everything past it automates.

## Prerequisites
- An **AWS account** + an **admin credential** for the first apply (a short-lived admin session is fine; it's used once).
- A **domain you control** (registered in Route53 or anywhere — you'll delegate NS to a Route53 zone in this account).
- A **GitHub account** you admin (you create the deploy repo in **Step 0**; `origin` stays your GitLab submission remote — GitHub is an *added* deploy remote, not a switch).
- Tools: **Terraform ≥ 1.9**, **AWS CLI** (configured with the admin cred), **kubectl**, **gh** (GitHub CLI), **jq**, **git**.
- _(Parallelizable: runbook (a) Auth0/M365 tenant setup can be done any time before runbook (c).)_

---

## Step 0 — GitHub repository + remote (do first; the deploy lives on GitHub)
The deploy pipeline (GitHub Actions + OIDC) runs from a **GitHub** repo. Per the project remote posture (root `CLAUDE.md`): **`origin` = GitLab** is the submission/code remote (already configured + pushed by the user) — you **ADD** a `github` remote for the deploy; you do **NOT** switch `origin`, and you do **NOT** mirror.

1. The deploy repo (already created by the user): **`SiWarlock/st6-weekly-commit`**.
2. Add it as a **second** remote (keep `origin` = GitLab) + publish the code:
   ```bash
   git remote add github git@github.com:SiWarlock/st6-weekly-commit.git   # KEEP origin=gitlab; this is the 2nd remote
   git push github main                                                   # origin/GitLab is untouched
   ```
3. **`SiWarlock/st6-weekly-commit` is the single value that wires the entire deploy** — set it once and it threads through:
   - → the Terraform **`github_repo`** var (Step 3 `TF_VAR_github_repo`),
   - → the CI role's **OIDC trust subject** `repo:SiWarlock/st6-weekly-commit:environment:production` — **already parameterized** on that var in `iam_ci.tf` (you only set the var; **no Terraform code edit**),
   - → the GitHub **`production` Environment** + its **variables** (Step 4, incl. the `AUTH0_DOMAIN`/`AUTH0_CLIENT_ID` SPA-build vars).
4. **Deploy trigger (deliberate):** the pipeline runs **only** on a **`release-*` tag push** or a **`workflow_dispatch`** — so to deploy you push to `github` then tag/dispatch (runbook c). **GitLab stays the submission remote with no auto-deploy**, and pushing to `github main` alone does **not** deploy (only a release tag / a manual dispatch does).

---

## Step 1 — AWS bootstrap (Terraform remote-state backend)
Creates the S3 state bucket + DynamoDB lock table the main root's backend assumes (resolves the chicken-and-egg). Uses its OWN **local** backend (it can't store state in the bucket it's creating).

```bash
cd infra/terraform-bootstrap
terraform init                      # local backend; no AWS state touched yet
terraform apply                     # creates wc-<env>-tfstate-<account_id> + wc-<env>-tflock
terraform output                    # capture these:
#   state_bucket_name   = "wc-aws-tfstate-<account_id>"
#   lock_table_name     = "wc-aws-tflock"
#   backend_config_hint = "-backend-config=... (paste-ready for Step 3's init)"
```
- **`env` defaults to `aws`** (mirrors the main root). Override with `-var env=<x>` / `-var region=<x>` if you change the main root's.
- **Safety:** the bucket is versioned + AES256-encrypted + public-access-blocked, with **`prevent_destroy = true`** on both the bucket + the lock table (the state store is the crown jewels). **Teardown caveat:** a full `terraform destroy` of this root requires **commenting out the two `prevent_destroy` lines first** (one-line, reversible) — see Teardown below.
- **Optional (advanced):** to keep even the bootstrap state remote, after the apply run `terraform init -migrate-state -backend-config=...` pointing at the bucket it just created, **using a DISTINCT key** `key=wc/bootstrap.tfstate` (NEVER the main root's `wc/terraform.tfstate` — they must not collide). Default is to leave the bootstrap state local (gitignored); it rarely changes.

## Step 2 — Domain + Route53 hosted zone delegation  ⚠️ DO THIS BEFORE Step 3
The main root reads the hosted zone as a **Terraform data source** (`data.aws_route53_zone.root` keyed on `ROOT_DOMAIN`) and DNS-validates the ACM certs against it. **The zone must exist and be delegated before the apply**, or the apply fails (no zone) / hangs (cert validation can't resolve).

```bash
# Create a PUBLIC hosted zone for your apex domain in THIS account:
aws route53 create-hosted-zone --name <ROOT_DOMAIN> --caller-reference "wc-$(date +%s)"
# Read the 4 NS records for the zone:
aws route53 get-hosted-zone --id <zone-id> --query 'DelegationSet.NameServers'
```
- **Delegate:** set those 4 NS records at your **domain registrar** (or the parent zone). If the domain is registered *in* Route53, point it at this zone. Wait for NS propagation (minutes to a few hours) — verify with `dig NS <ROOT_DOMAIN>` returning the AWS nameservers.
- The apply creates the `wc.<ROOT_DOMAIN>` → CloudFront record; the `api.wc.<ROOT_DOMAIN>` → ALB record is created by **external-dns** at deploy time (runbook c). You only provide the delegated zone.

## Step 3 — First `terraform apply` (admin creds) — creates EVERYTHING incl. OIDC + CI role
```bash
cd infra/terraform
# init against the bootstrap-created backend (paste the Step-1 backend_config_hint):
terraform init \
  -backend-config="bucket=<state_bucket_name>" \
  -backend-config="key=wc/terraform.tfstate" \
  -backend-config="region=<region>" \
  -backend-config="dynamodb_table=<lock_table_name>" \
  -backend-config="encrypt=true"

# the two no-default vars (deploy-specific):
export TF_VAR_ROOT_DOMAIN="<ROOT_DOMAIN>"
export TF_VAR_github_repo="SiWarlock/st6-weekly-commit"     # the OIDC trust subject
# export TF_VAR_region="<region>"               # optional; defaults to us-east-1

terraform plan -out tfplan
terraform apply tfplan          # ~20–30 min (EKS control plane + node group dominate)
```
This single apply creates: the VPC/EKS cluster + node group, RDS PG16, ECR repos, SNS/SQS+DLQ, S3+CloudFront, the 4 Secrets Manager **containers** (db auto-populated; auth0/graph/demo are empty placeholders), per-workload IRSA roles, the ACM certs (DNS-validated against the Step-2 zone — the apply *waits* for validation), the cluster add-ons (ALB controller, Secrets Store CSI Driver+ASCP, external-dns via `helm_release`), **the GitHub OIDC provider + the hardened CI deploy role**, and CloudWatch log groups.

Capture the outputs you need for Step 4:
```bash
terraform output -raw ci_deploy_role_arn      # → GitHub var AWS_DEPLOY_ROLE_ARN
terraform output -raw cluster_name            # FYI (the pipeline derives it itself)
```
- The admin principal that runs this apply gets **EKS cluster-admin** (so the in-apply `helm_release` add-on installs succeed). The CI deploy role is granted its own EKS access entry by the apply (so the pipeline's `kubectl` works under OIDC).
- **No secrets yet:** auth0/graph hold `REPLACE_VIA_HITL` placeholders (`ignore_changes` — the apply won't clobber a later real value). They're populated in runbook (c).

## Step 4 — GitHub `production` Environment + required reviewers + variables
The CI role's OIDC trust is **`repo:SiWarlock/st6-weekly-commit:environment:production`** — so the deploy job must run under a GitHub Environment named **exactly `production`**, gated by required reviewers (the per-deploy HITL approval).

```bash
# Create the production Environment (idempotent):
gh api -X PUT "repos/SiWarlock/st6-weekly-commit/environments/production"

# Add a required reviewer (replace <reviewer-user-id>; or set in the UI: Settings → Environments → production → Required reviewers):
gh api -X PUT "repos/SiWarlock/st6-weekly-commit/environments/production" \
  -f 'reviewers[][type=User]' -F 'reviewers[][id]=<reviewer-user-id>'

# Set the 5 deploy variables (Environment-scoped or repo-scoped):
gh variable set AWS_REGION          --env production --repo SiWarlock/st6-weekly-commit --body "<region>"
gh variable set ROOT_DOMAIN         --env production --repo SiWarlock/st6-weekly-commit --body "<ROOT_DOMAIN>"
gh variable set AWS_DEPLOY_ROLE_ARN --env production --repo SiWarlock/st6-weekly-commit --body "<ci_deploy_role_arn>"
gh variable set TF_STATE_BUCKET     --env production --repo SiWarlock/st6-weekly-commit --body "<state_bucket_name>"
gh variable set TF_STATE_LOCK_TABLE --env production --repo SiWarlock/st6-weekly-commit --body "<lock_table_name>"
```

| GitHub variable | Value | Source |
|---|---|---|
| `AWS_REGION` | the deploy region | your choice (matches the TF `region`) |
| `ROOT_DOMAIN` | apex domain | Step 2 |
| `AWS_DEPLOY_ROLE_ARN` | the CI deploy role ARN | Step 3 `ci_deploy_role_arn` output |
| `TF_STATE_BUCKET` | the state bucket name | Step 1 `state_bucket_name` output |
| `TF_STATE_LOCK_TABLE` | the lock table name | Step 1 `lock_table_name` output |
| `AUTH0_DOMAIN` | the Auth0 SPA app's tenant Domain | runbook (a) §1.2 ("note the Domain") |
| `AUTH0_CLIENT_ID` | the Auth0 SPA app's Client ID | runbook (a) §1.2 ("note the Client ID") |

```bash
# the 2 SPA-build vars (the pipeline bakes VITE_AUTH0_DOMAIN/CLIENT_ID/AUDIENCE into the SPA):
gh variable set AUTH0_DOMAIN    --env production --repo SiWarlock/st6-weekly-commit --body "<tenant>.<region>.auth0.com"
gh variable set AUTH0_CLIENT_ID --env production --repo SiWarlock/st6-weekly-commit --body "<spa-client-id>"
```
> `AUTH0_DOMAIN` + `AUTH0_CLIENT_ID` feed the wc-web build (`VITE_AUTH0_*`); the SPA's `auth0Config.ts` fail-fasts without them, so the deployed login won't boot if they're unset. `VITE_AUTH0_AUDIENCE` is derived from `ROOT_DOMAIN` (no separate var).

- **Required reviewers** are the manual gate: every `deploy.yml` run pauses for an approval before any AWS-touching step. No static AWS keys are ever set as GitHub secrets (OIDC only).

## Deploy-ready ✅
Infra exists, OIDC trust is live, the 7 variables are set, the `production` Environment gates the deploy. → Proceed to runbook **(c) `deploy-and-smoke.md`** to populate the secret values + run the pipeline + smoke.

---

## Teardown (reverse order)
1. `cd infra/terraform && terraform destroy` (tears down EKS/RDS/etc.; the OIDC provider + CI role go too).
2. **Comment out the two `prevent_destroy = true` lines** in `infra/terraform-bootstrap/main.tf` (the bucket + the lock table), then `cd infra/terraform-bootstrap && terraform destroy`. (The bucket must be emptied of state versions first if non-empty.)
3. Remove the Route53 zone + the GitHub Environment/variables if no longer needed.
