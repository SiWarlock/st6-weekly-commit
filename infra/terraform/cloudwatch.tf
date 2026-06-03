# cloudwatch.tf — CloudWatch log DESTINATIONS for the four workloads (§15, REQ-O-009).
#
# One log group per workload (api/worker/cronjob/migration) via for_each over a
# fixed set (DRY; same pattern as the ECR repos — LESSONS §8), each with a finite
# retention. This task STOPS at the destinations: the pod→CloudWatch forwarding
# agent (Fluent Bit DaemonSet / Container Insights) is NOT in Appendix C.7 and is
# deferred (Phase 13 trims) — container logs do not reach these groups without it.
#
# §15 log hygiene (IDs+state only, no notes/secrets/PII) is an APP-layer contract
# (Phase 13 structured logging) — this slice provisions destinations, not content.

locals {
  cloudwatch_workloads = toset(["api", "worker", "cronjob", "migration"])
}

resource "aws_cloudwatch_log_group" "workload" {
  for_each = local.cloudwatch_workloads

  name              = "/aws/eks/${local.cluster_name}/${each.value}"
  retention_in_days = var.cloudwatch_log_retention_days

  tags = local.common_tags
}
