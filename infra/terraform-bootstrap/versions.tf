# versions.tf — Terraform + provider constraints for the bootstrap mini-root.
#
# This is a STANDALONE sibling root to infra/terraform/ whose only job is to
# create the two resources the main root's S3 backend assumes already exist: the
# versioned/encrypted state bucket + the DynamoDB state-lock table. It therefore
# uses a LOCAL backend (no `backend` block → terraform defaults to local
# *.tfstate) — it cannot store its state in the bucket it is creating on first
# apply (the bootstrap chicken-and-egg). The local state is gitignored; an
# optional `terraform init -migrate-state` can later move it into that bucket
# under a DISTINCT key (key=wc/bootstrap.tfstate, never the main root's
# wc/terraform.tfstate) — see runbook (b).
#
# Provider major (aws ~> 6.0) mirrors the main root (infra/terraform/versions.tf)
# and is locked in this dir's OWN committed .terraform.lock.hcl (multi-platform:
# linux_amd64 + darwin_arm64). Only the aws provider is needed here — no
# eks/helm/random.

terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = local.common_tags
  }
}
