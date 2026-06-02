# outputs.tf — single aggregation point for root-module outputs (Appendix C.6).
#
# APPEND-ONLY, per slice: each resource slice adds ONLY the output(s) whose
# backing resource it creates. NEVER declare an `output` that references a
# resource a later slice creates — an output referencing an undeclared resource
# breaks `terraform validate` for the ENTIRE root, blocking every subsequent
# slice (infra LESSONS §3).
#

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

# --- 12.3: RDS — LIVE --------------------------------------------------------

output "rds_endpoint" {
  description = "RDS connection endpoint (host:port). NO credentials — the master password is never an output (RISK-016); the db secret (12.6) holds it."
  value       = aws_db_instance.wc.endpoint
}

# --- 12.4: ECR — LIVE --------------------------------------------------------

output "ecr_api_repo_url" {
  description = "wc-api ECR repository URL (CI push target, 12.11)."
  value       = aws_ecr_repository.this["wc-api"].repository_url
}

output "ecr_worker_repo_url" {
  description = "wc-sync-worker ECR repository URL (CI push target, 12.11)."
  value       = aws_ecr_repository.this["wc-sync-worker"].repository_url
}

# --- 12.5: SNS/SQS — LIVE (names match Appendix D.2/D.3 env keys) ------------

output "sns_topic_arn" {
  description = "Lifecycle SNS topic ARN → app env SNS_TOPIC_ARN (Appendix D.2)."
  value       = aws_sns_topic.lifecycle.arn
}

output "sqs_queue_url" {
  description = "Sync SQS queue URL → app env SQS_QUEUE_URL (Appendix D.3)."
  value       = aws_sqs_queue.sync.url
}

output "sqs_dlq_url" {
  description = "Sync DLQ URL → app env SQS_DLQ_URL (Appendix D.3)."
  value       = aws_sqs_queue.sync_dlq.url
}

# --- 12.6: S3/CloudFront + Secrets Manager — LIVE ----------------------------
# Secret ARNs only — NEVER the secret values (rule #7 / RISK-016). Consumed by
# the 12.7 IRSA policies + the 12.8 SecretProviderClass.

output "s3_assets_bucket_name" {
  description = "Private S3 bucket for the Vite SPA assets (CI sync target, 12.11)."
  value       = aws_s3_bucket.assets.bucket
}

output "cloudfront_distribution_id" {
  description = "CloudFront distribution id (CI invalidation target, 12.11; 12.7 attaches the cert + wc. alias)."
  value       = aws_cloudfront_distribution.assets.id
}

output "db_secret_arn" {
  description = "ARN of the db secret (TF-populated coords/password). Value never output."
  value       = aws_secretsmanager_secret.db.arn
}

output "auth0_secret_arn" {
  description = "ARN of the auth0 secret (placeholder; HITL-populated). Value never output."
  value       = aws_secretsmanager_secret.auth0.arn
}

output "graph_secret_arn" {
  description = "ARN of the graph secret (placeholder; HITL-populated). Value never output."
  value       = aws_secretsmanager_secret.graph.arn
}

output "demo_secret_arn" {
  description = "ARN of the demo secret (placeholder; HITL-populated). Value never output."
  value       = aws_secretsmanager_secret.demo.arn
}

# --- 12.7a: Route53 / ACM — LIVE ---------------------------------------------

output "hosted_zone_id" {
  description = "Route53 hosted zone id for ROOT_DOMAIN (record management)."
  value       = data.aws_route53_zone.root.zone_id
}

output "alb_cert_arn" {
  description = "Regional ACM cert ARN for api.wc.<ROOT_DOMAIN> -> the 12.8 ALB ingress (alb.ingress.kubernetes.io/certificate-arn)."
  value       = aws_acm_certificate_validation.alb.certificate_arn
}

# --- 12.7b: per-workload IRSA role ARNs — LIVE -------------------------------
# Consumed by the 12.8 ServiceAccount annotations (eks.amazonaws.com/role-arn).

output "irsa_api_role_arn" {
  description = "IRSA role ARN for the wc-api ServiceAccount."
  value       = aws_iam_role.irsa_api.arn
}

output "irsa_worker_role_arn" {
  description = "IRSA role ARN for the wc-worker ServiceAccount."
  value       = aws_iam_role.irsa_worker.arn
}

output "irsa_cronjob_role_arn" {
  description = "IRSA role ARN for the wc-cronjob ServiceAccount."
  value       = aws_iam_role.irsa_cronjob.arn
}

output "irsa_migration_role_arn" {
  description = "IRSA role ARN for the wc-migration ServiceAccount."
  value       = aws_iam_role.irsa_migration.arn
}

# --- 12.10: CloudWatch log groups — LIVE (keyed by workload) -----------------

output "cloudwatch_log_group_names" {
  description = "Per-workload CloudWatch log group names ({api,worker,cronjob,migration} -> name)."
  value       = { for k, g in aws_cloudwatch_log_group.workload : k => g.name }
}

output "cloudwatch_log_group_arns" {
  description = "Per-workload CloudWatch log group ARNs ({api,worker,cronjob,migration} -> arn)."
  value       = { for k, g in aws_cloudwatch_log_group.workload : k => g.arn }
}
