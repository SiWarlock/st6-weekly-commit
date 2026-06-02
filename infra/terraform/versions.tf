# versions.tf — Terraform + provider version constraints and provider wiring.
#
# Provider majors were verified against the live Terraform Registry at authoring
# time (NOT pinned from memory): aws 6.x, kubernetes 3.x, helm 3.x are the current
# majors. Exact selections are locked in .terraform.lock.hcl (committed,
# multi-platform: linux_amd64 for CI + darwin_arm64 for local). The eks module
# (12.2) transitively requires cloudinit/null/time/tls — also locked.
#
# Two AWS providers are declared:
#   - default           → region = var.region (REQ-O-011; default us-east-1, overridable)
#   - alias "us_east_1" → fixed us-east-1 for the CloudFront ACM certificate (§12).
#                         CloudFront requires its ACM cert in us-east-1; the ALB
#                         cert is regional (default provider). This alias has no
#                         consumer until 12.6/12.7 (muted in .tflint.hcl until then).
#
# kubernetes + helm are declared in required_providers so the lockfile pins them
# from slice 1. Their CONFIGURED provider blocks land in the slice that first uses
# each: the `helm` provider is configured below (12.2, for the ALB-controller
# helm_release), keyed off the EKS cluster in eks.tf. The `kubernetes` provider
# stays declared-only until the slice that manages a k8s object via terraform
# (~12.8); if the CI pipeline applies all manifests via kubectl, it may stay
# declared-only permanently.

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
    # random — generates the RDS master password (12.3). MUST be declared
    # explicitly: it is NOT a transitive dep of the eks/vpc/iam modules, and using
    # random_password without a required_providers entry trips tflint
    # terraform_required_providers (infra LESSONS §7).
    random = {
      source  = "hashicorp/random"
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

# helm provider (v3) — installs charts into the EKS cluster (12.2 ALB controller).
# v3 uses the `kubernetes = {...}` attribute form (not a nested block) and
# `set = [{...}]` list-of-objects in helm_release. Auth via `aws eks get-token`
# (the aws CLI must be present at plan/apply — HITL/CI). Keyed off the eks module
# outputs; host/CA are known-after-apply but `validate` does not require them.
provider "helm" {
  kubernetes = {
    host                   = module.eks.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)

    exec = {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", module.eks.cluster_name, "--region", var.region]
    }
  }
}
