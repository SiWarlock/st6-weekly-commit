# backend.tf — Terraform remote state (ARCHITECTURE.md §13): S3 state bucket +
# DynamoDB state lock.
#
# PARTIAL configuration: bucket / key / region / dynamodb_table are supplied at
# `init` time via -backend-config, NOT hardcoded here. They are environment-
# specific, and the state bucket + lock table are created by the
# `infra/terraform-bootstrap/` mini-root (run once per fresh AWS account — see
# runbook b) with versioning + AES256 SSE + public-access-block on the bucket;
# `terraform -chdir=infra/terraform-bootstrap output backend_config_hint` prints
# the exact flags to paste below. Keeping them out of source keeps this root
# deploy-target-agnostic.
#
# HITL init (NOT run agent-side — requires AWS credentials + a pre-created bucket):
#   terraform -chdir=infra/terraform init \
#     -backend-config="bucket=<tf-state-bucket>" \
#     -backend-config="key=wc/terraform.tfstate" \
#     -backend-config="region=<region>" \
#     -backend-config="dynamodb_table=<tf-lock-table>" \
#     -backend-config="encrypt=true"
#
# Agent-side verification uses `terraform init -backend=false`, which skips this
# backend entirely (module + provider schema load without touching AWS).

terraform {
  backend "s3" {}
}
