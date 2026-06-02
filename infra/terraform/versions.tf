# versions.tf — Terraform + provider version constraints and AWS provider wiring.
#
# Provider majors were verified against the live Terraform Registry at authoring
# time (NOT pinned from memory): aws 6.x, kubernetes 3.x, helm 3.x are the current
# majors. Exact selections are locked in .terraform.lock.hcl (committed,
# multi-platform: linux_amd64 for CI + darwin_arm64 for local).
#
# Two AWS providers are declared:
#   - default           → region = var.region (REQ-O-011; default us-east-1, overridable)
#   - alias "us_east_1" → fixed us-east-1 for the CloudFront ACM certificate (§12).
#                         CloudFront requires its ACM cert in us-east-1; the ALB
#                         cert is regional (default provider). This alias has no
#                         consumer until 12.6/12.7 (muted in .tflint.hcl until then).
#
# kubernetes + helm are declared here so the lockfile pins them from slice 1, but
# their configured `provider {}` blocks are deferred to the slice that stands up
# the EKS cluster (12.2 / 12.8) — they cannot authenticate before the cluster
# exists. (helm 3.x changed its release schema vs 2.x: author 12.2's helm_release
# against the 3.x docs.)

terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 3.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 3.0"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = local.common_tags
  }
}

# Aliased provider fixed to us-east-1 for the CloudFront ACM certificate (§12).
# No consumer until 12.6 (CloudFront distribution) / 12.7 (ACM cert).
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = local.common_tags
  }
}
