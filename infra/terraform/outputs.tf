# outputs.tf — single aggregation point for root-module outputs (Appendix C.6).
#
# APPEND-ONLY, per slice: each resource slice adds ONLY the output(s) whose
# backing resource it creates. NEVER declare an `output` that references a
# resource a later slice creates — an output referencing an undeclared resource
# breaks `terraform validate` for the ENTIRE root, blocking every subsequent
# slice (infra LESSONS §3).
#
# Pending exports (go live with their backing resource):
#   rds_endpoint                → 12.3  rds.tf
#   ecr_api_repo_url            → 12.4  ecr.tf
#   ecr_worker_repo_url         → 12.4  ecr.tf
#   sns_topic_arn               → 12.5  sns_sqs.tf
#   sqs_queue_url               → 12.5  sns_sqs.tf
#   sqs_dlq_url                 → 12.5  sns_sqs.tf
#   s3_assets_bucket_name       → 12.6  s3_cloudfront.tf
#   cloudfront_distribution_id  → 12.6  s3_cloudfront.tf
#   hosted_zone_id              → 12.7  route53_acm.tf

# --- 12.2: network + compute (VPC / EKS) — LIVE ------------------------------

output "vpc_id" {
  description = "ID of the VPC hosting the EKS cluster, RDS, and ALBs."
  value       = module.vpc.vpc_id
}

output "private_subnet_ids" {
  description = "Private subnet IDs (EKS nodes, RDS, internal ALBs)."
  value       = module.vpc.private_subnets
}

output "public_subnet_ids" {
  description = "Public subnet IDs (internet-facing ALB)."
  value       = module.vpc.public_subnets
}

output "cluster_name" {
  description = "EKS cluster name (consumed by CI `aws eks update-kubeconfig`, 12.11)."
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "EKS API server endpoint."
  value       = module.eks.cluster_endpoint
}

output "cluster_oidc_provider_arn" {
  description = "IRSA OIDC provider ARN (consumed by the per-workload IRSA roles, 12.7)."
  value       = module.eks.oidc_provider_arn
}

output "cluster_oidc_issuer_url" {
  description = "EKS OIDC issuer URL (IRSA trust)."
  value       = module.eks.cluster_oidc_issuer_url
}
