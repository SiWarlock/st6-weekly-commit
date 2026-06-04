# main.tf — the bootstrap resources: the Terraform remote-state S3 bucket + the
# DynamoDB state-lock table (§13 remote-state contract; Appendix C.6).
#
# SAFETY (rule #7): this root creates an EMPTY encrypted bucket + a lock table —
# it handles NO secrets. Its outputs are non-secret resource names only
# (LESSONS §3). The bucket is hardened: versioned (state history / recovery),
# AES256-encrypted at rest, ALL public access blocked, and force_destroy=false +
# prevent_destroy — the state store is the crown jewels, so an accidental destroy
# (which would lose all state history) is made fail-closed. Teardown must comment
# the prevent_destroy out first (documented in runbook b).

data "aws_caller_identity" "current" {}

locals {
  # Governance tags — identical shape to the main root (infra/terraform/main.tf)
  # so both roots' resources tag consistently.
  common_tags = {
    Project   = "wc"
    ManagedBy = "terraform"
    Env       = var.env
  }

  # Globally-unique + deterministic names. The account_id suffix makes the bucket
  # name reproducible (re-apply is idempotent — same account → same name), with no
  # random provider and nothing extra stored in state.
  state_bucket_name = "wc-${var.env}-tfstate-${data.aws_caller_identity.current.account_id}"
  lock_table_name   = "wc-${var.env}-tflock"
}

# --- S3 remote-state bucket --------------------------------------------------

resource "aws_s3_bucket" "tfstate" {
  bucket        = local.state_bucket_name
  force_destroy = false
  tags          = local.common_tags

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket                  = aws_s3_bucket.tfstate.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# --- DynamoDB state-lock table -----------------------------------------------
# The Terraform S3 backend's lock contract REQUIRES the hash key to be exactly
# "LockID" (type S). PAY_PER_REQUEST = no provisioned-capacity cost for the
# low-volume lock traffic.

resource "aws_dynamodb_table" "tflock" {
  name         = local.lock_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  tags = local.common_tags

  lifecycle {
    prevent_destroy = true
  }
}
