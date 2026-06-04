# variables.tf — bootstrap inputs. Defaults MIRROR the main root
# (infra/terraform/variables.tf) so the derived names line up: the bucket this
# root creates is `wc-${var.env}-tfstate-<account_id>`, which is exactly what the
# main root's `terraform init -backend-config="bucket=…"` must point at.

variable "env" {
  description = "Deploy target environment (mirrors the main root). Drives the state-bucket + lock-table name prefix so they match the main root's backend-config."
  type        = string
  default     = "aws"

  validation {
    condition     = contains(["local", "aws"], var.env)
    error_message = "env must be one of: \"local\", \"aws\"."
  }
}

variable "region" {
  description = "AWS region for the state bucket + lock table. Mirrors the main root's default; must equal the region passed to the main root's backend-config."
  type        = string
  default     = "us-east-1"
}
