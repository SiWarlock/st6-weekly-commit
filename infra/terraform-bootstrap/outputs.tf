# outputs.tf — non-secret resource names (rule #7-safe; LESSONS §3 — outputs only
# reference created resources). The `backend_config_hint` is a paste-ready
# `-backend-config` flag string for the main root's `terraform init` (runbook b).

output "state_bucket_name" {
  description = "Name of the S3 bucket holding the main root's remote state. Pass as the main root's -backend-config=\"bucket=…\"."
  value       = aws_s3_bucket.tfstate.id
}

output "lock_table_name" {
  description = "Name of the DynamoDB state-lock table. Pass as the main root's -backend-config=\"dynamodb_table=…\"."
  value       = aws_dynamodb_table.tflock.name
}

output "backend_config_hint" {
  description = "Paste-ready backend-config flags for the main root's `terraform -chdir=infra/terraform init` (runbook b)."
  value = join(" ", [
    "-backend-config=\"bucket=${aws_s3_bucket.tfstate.id}\"",
    "-backend-config=\"key=wc/terraform.tfstate\"",
    "-backend-config=\"region=${var.region}\"",
    "-backend-config=\"dynamodb_table=${aws_dynamodb_table.tflock.name}\"",
    "-backend-config=\"encrypt=true\"",
  ])
}
