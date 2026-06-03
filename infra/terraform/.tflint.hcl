# .tflint.hcl — terraform ruleset only (no AWS-ruleset plugin → offline-friendly;
# the AWS plugin needs a `tflint --init` network fetch and is deferred to the
# slice that first benefits from deep AWS-resource linting).
#
# The `terraform_unused_declarations` mute (Phase-12 foundation forward-decls:
# ROOT_DOMAIN, rds_minor_version, aws.us_east_1) was REMOVED at 12.7a — all three
# now have consumers (ROOT_DOMAIN → certs/alias, rds_minor_version → 12.3,
# aws.us_east_1 → CloudFront cert). The rule is active; the gate is fully strict.

plugin "terraform" {
  enabled = true
  preset  = "recommended"
}
