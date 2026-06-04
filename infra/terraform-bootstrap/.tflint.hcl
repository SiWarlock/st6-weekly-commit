# .tflint.hcl — terraform ruleset only (offline-friendly; no AWS-ruleset plugin).
# Copied from the main root (infra/terraform/.tflint.hcl) so lint is consistent
# across both roots. The bootstrap root has NO forward-declared symbols (every
# var/output/resource is consumed), so the `recommended` preset's
# `terraform_unused_declarations` rule stays fully active — no mute needed.

plugin "terraform" {
  enabled = true
  preset  = "recommended"
}
