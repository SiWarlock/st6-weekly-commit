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

# --- 12.2: network + compute (VPC / EKS) ------------------------------------

variable "vpc_cidr" {
  description = "CIDR block for the VPC. Subnets are carved from this (private /20, public /24 per AZ)."
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Number of Availability Zones to span (≥2 for multi-AZ subnets + ALB)."
  type        = number
  default     = 2

  validation {
    condition     = var.az_count >= 2
    error_message = "az_count must be >= 2 (multi-AZ public + private subnets)."
  }
}

variable "eks_cluster_version" {
  description = "EKS/Kubernetes control-plane + node-group minor version (e.g. \"1.33\"). Pinned via variable; the true list of supported minors is resolved out-of-band (HITL)."
  type        = string
  default     = "1.33"

  validation {
    condition     = can(regex("^1\\.[0-9]{2}$", var.eks_cluster_version))
    error_message = "eks_cluster_version must be a 1.NN Kubernetes minor (e.g. \"1.33\")."
  }
}

variable "node_instance_types" {
  description = "Instance types for the single managed node group (sized for api + worker + CronJob + migration Job, REQ-O-013)."
  type        = list(string)
  default     = ["t3.large"]
}

variable "node_min_size" {
  description = "Managed node group minimum size (thin, no autoscaler — RISK-009)."
  type        = number
  default     = 1
}

variable "node_desired_size" {
  description = "Managed node group desired size."
  type        = number
  default     = 2

  validation {
    condition     = var.node_desired_size >= 1
    error_message = "node_desired_size must be >= 1 (a zero-node group cannot run the workloads)."
  }
}

variable "node_max_size" {
  description = "Managed node group maximum size (thin headroom — RISK-009)."
  type        = number
  default     = 3
}

variable "alb_controller_chart_version" {
  description = "Pinned aws-load-balancer-controller Helm chart version (eks-charts repo). Verified against the live chart index at author time."
  type        = string
  default     = "3.3.0"
}

# --- 12.3: RDS PostgreSQL (thin/cost; hardening deferred to Phase 13 trims) --

variable "rds_instance_class" {
  description = "RDS instance class. db.t4g.micro (ARM/Graviton burstable) — thin/cost (RISK-009)."
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_allocated_storage" {
  description = "RDS allocated storage in GB (gp3)."
  type        = number
  default     = 20
}

variable "rds_multi_az" {
  description = "Multi-AZ RDS deployment. Default single-AZ (thin/cost, mirrors the single-NAT posture); Multi-AZ HA is a Phase 13 hardening item."
  type        = bool
  default     = false
}

variable "rds_backup_retention_period" {
  description = "Automated-backup retention in days."
  type        = number
  default     = 7
}
