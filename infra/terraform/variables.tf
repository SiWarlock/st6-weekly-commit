# variables.tf — root deploy inputs.

variable "ROOT_DOMAIN" {
  description = <<-EOT
    Apex domain under which the app is served: wc.$ROOT_DOMAIN (frontend, via
    CloudFront) and api.wc.$ROOT_DOMAIN (API, via ALB). Required, NO default
    (OQ-001) — an unset value must fail the plan, never silently default.
    Uppercased to mirror the Appendix D.2 env-var name carried into k8s.
  EOT
  type        = string
}

variable "region" {
  description = "Default AWS region for regional resources (REQ-O-011). Overridable; the CloudFront ACM cert is pinned to us-east-1 via the aws.us_east_1 provider alias regardless of this value."
  type        = string
  default     = "us-east-1"
}

variable "rds_minor_version" {
  description = <<-EOT
    PostgreSQL engine version for RDS — the latest available 16.x minor (>= 16.13;
    supersedes the PRD literal "16.4" per OQ-007/§4). The true latest is region-
    dependent and resolved out-of-band via `aws rds describe-db-engine-versions`
    (HITL); kept as a single variable so a region-availability mismatch is changed
    in one place (RISK-011).
  EOT
  type        = string
  default     = "16.13"

  validation {
    condition     = can(regex("^16\\.", var.rds_minor_version))
    error_message = "rds_minor_version must be a PostgreSQL 16.x minor (e.g. \"16.13\")."
  }
}

variable "env" {
  description = "Deploy target environment: \"local\" (Docker Compose) or \"aws\" (EKS)."
  type        = string
  default     = "aws"

  validation {
    condition     = contains(["local", "aws"], var.env)
    error_message = "env must be one of: \"local\", \"aws\"."
  }
}
