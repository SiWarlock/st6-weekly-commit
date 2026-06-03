# main.tf — root module composition point.
#
# Provider/terraform wiring lives in versions.tf; the remote-state backend in
# backend.tf; inputs in variables.tf; aggregated exports in outputs.tf. This file
# holds root-level locals and is where cross-cutting composition lands.
#
# Resource files appended per slice: vpc.tf, eks.tf (12.2) · rds.tf (12.3) ·
# ecr.tf (12.4) · sns_sqs.tf (12.5) · s3_cloudfront.tf, secrets.tf (12.6) ·
# route53_acm.tf, iam_irsa.tf (12.7) · cloudwatch.tf (12.10).

locals {
  # Cluster / resource base name, derived from the deploy target (e.g. "wc-aws").
  # Shared by vpc.tf (ELB subnet-discovery tags) and eks.tf (cluster name).
  cluster_name = "wc-${var.env}"

  # Governance tags stamped on every taggable resource via the aws provider
  # default_tags (cost allocation + ownership). `Env` is the deploy target.
  common_tags = {
    Project   = "wc"
    ManagedBy = "terraform"
    Env       = var.env
  }
}
