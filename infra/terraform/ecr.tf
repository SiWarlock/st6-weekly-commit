# ecr.tf — container image registries (§12, REQ-O-014).
#
# Exactly TWO repositories: wc-api and wc-sync-worker. The generation CronJob and
# the Flyway migration Job reuse the wc-api image (Spring profiles/args) — there is
# NEVER a third repo. for_each over a 2-element set makes a third structurally
# impossible. Images are tagged by commit SHA (§13); IMMUTABLE tags prevent a SHA
# tag from being overwritten.

locals {
  ecr_repos = toset(["wc-api", "wc-sync-worker"])
}

resource "aws_ecr_repository" "this" {
  for_each = local.ecr_repos

  name                 = each.value
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = local.common_tags
}

# Minimal hygiene: expire untagged images after 14 days (prevents unbounded
# storage from overwritten/orphaned layers). Tagged (SHA) images are retained.
resource "aws_ecr_lifecycle_policy" "this" {
  for_each   = aws_ecr_repository.this
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Expire untagged images older than 14 days"
      selection = {
        tagStatus   = "untagged"
        countType   = "sinceImagePushed"
        countUnit   = "days"
        countNumber = 14
      }
      action = { type = "expire" }
    }]
  })
}
