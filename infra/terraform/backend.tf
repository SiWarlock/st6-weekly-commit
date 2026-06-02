# backend.tf — Terraform remote state (ARCHITECTURE.md §13): S3 state bucket +
# DynamoDB state lock.
#
# PARTIAL configuration: bucket / key / region / dynamodb_table are supplied at
# `init` time via -backend-config, NOT hardcoded here. They are environment-
# specific, and the state bucket + lock table are created out-of-band (HITL) with
# versioning + server-side encryption enabled on the bucket. Keeping them out of
# source keeps this root deploy-target-agnostic.
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
