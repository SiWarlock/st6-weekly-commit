# .tflint.hcl — terraform ruleset only (no AWS-ruleset plugin → offline-friendly;
# the AWS plugin needs a `tflint --init` network fetch and is deferred to the
# slice that first benefits from deep AWS-resource linting, ~12.6/12.7).

plugin "terraform" {
  enabled = true
  preset  = "recommended"
}

# MUTED during the Phase 12 foundation build-up. The root forward-declares its
# public variable interface and the CloudFront-cert provider alias before their
# consumers exist:
#   - var.ROOT_DOMAIN        → consumer lands 12.6/12.7 (CloudFront/Route53/ACM)
#   - var.rds_minor_version  → consumer lands 12.3 (rds.tf)
#   - provider aws.us_east_1 → consumer lands 12.6 (CloudFront ACM cert)
# Muting this ONE rule (rather than weakening the gate globally via
# --minimum-failure-severity) keeps every other recommended rule blocking.
# RE-ENABLE at 12.7, once all three forward-declarations have consumers.
rule "terraform_unused_declarations" {
  enabled = false
}
