# sns_sqs.tf — async lifecycle transport (§10/§12): SNS topic → standard SQS sync
# queue (raw delivery) + DLQ redrive. The separate wc-sync-worker consumes the
# queue (REQ-I-009); failed messages redrive to the DLQ after maxReceiveCount
# (REQ-I-011). The pointer-payload-only rule (Appendix F.2 — payloads carry only
# {syncRecordId,eventKind,env,traceId}, no secrets/PII) is enforced at the APP
# layer, NOT in this topology.
#
# Resource names are intentionally BARE (wc-lifecycle / wc-sync / wc-sync-dlq) to
# match the Appendix D.2/D.3 contract illustrations — the app reads the ARN/URLs
# from env (SNS_TOPIC_ARN/SQS_QUEUE_URL/SQS_DLQ_URL), so the binding contract is
# the env-var KEY, not the name. (Deliberate divergence from the cluster_name=
# wc-${env} pattern used for env-scoped infra like the VPC/EKS/RDS.)

resource "aws_sns_topic" "lifecycle" {
  name              = "wc-lifecycle"
  kms_master_key_id = "alias/aws/sns" # SSE with the AWS-managed SNS key (no KMS mgmt)
  tags              = local.common_tags
}

resource "aws_sqs_queue" "sync_dlq" {
  name                      = "wc-sync-dlq"
  sqs_managed_sse_enabled   = true    # SSE-SQS at rest
  message_retention_seconds = 1209600 # 14d (max) — DLQ ops visibility
  tags                      = local.common_tags
}

resource "aws_sqs_queue" "sync" {
  name                    = "wc-sync"
  sqs_managed_sse_enabled = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.sync_dlq.arn
    maxReceiveCount     = var.sqs_max_receive_count
  })

  tags = local.common_tags
}

resource "aws_sns_topic_subscription" "sync" {
  topic_arn            = aws_sns_topic.lifecycle.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.sync.arn
  raw_message_delivery = true
}

# Allow ONLY this SNS topic to publish to the queue — scoped to the SNS service
# principal + the topic ARN via condition. No `*` principal.
resource "aws_sqs_queue_policy" "sync" {
  queue_url = aws_sqs_queue.sync.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowSNSTopicPublish"
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.sync.arn
      Condition = {
        ArnEquals = { "aws:SourceArn" = aws_sns_topic.lifecycle.arn }
      }
    }]
  })
}
