# outputs.tf — single aggregation point for root-module outputs (Appendix C.6).
#
# APPEND-ONLY, per slice: each resource slice adds ONLY the output(s) whose
# backing resource it creates. NEVER declare an `output` that references a
# resource a later slice creates — an output referencing an undeclared resource
# breaks `terraform validate` for the ENTIRE root, blocking every subsequent
# slice. At 12.1 there are zero resources, so there are zero live outputs.
#
# Planned exports (each goes live with its backing resource):
#   rds_endpoint                → 12.3  rds.tf
#   ecr_api_repo_url            → 12.4  ecr.tf
#   ecr_worker_repo_url         → 12.4  ecr.tf
#   sns_topic_arn               → 12.5  sns_sqs.tf
#   sqs_queue_url               → 12.5  sns_sqs.tf
#   sqs_dlq_url                 → 12.5  sns_sqs.tf
#   s3_assets_bucket_name       → 12.6  s3_cloudfront.tf
#   cloudfront_distribution_id  → 12.6  s3_cloudfront.tf
#   hosted_zone_id              → 12.7  route53_acm.tf
#
# (No live `output` blocks at 12.1.)
