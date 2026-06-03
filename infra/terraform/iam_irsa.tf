# iam_irsa.tf — per-workload IRSA least-privilege roles (§12, §16; safety rules
# #5 + #7). Each role federates the cluster OIDC provider and is scoped to EXACTLY
# the AWS actions §12 allows — no `secretsmanager:GetSecretValue` on `*`, no broad
# grants. The 12.8 ServiceAccounts (namespace `wc`, names wc-{api,worker,cronjob,
# migration}) annotate eks.amazonaws.com/role-arn with these ARNs; the trust `sub`
# MUST match those SA identities (the 12.7b↔12.8 contract).
#
# (The GitHub OIDC provider + CI deploy role + CI EKS access entry are 12.7c —
# split out pending the human's ruling on the CI-role posture.)

locals {
  # OIDC issuer host (no scheme) — the trust condition keys are prefixed with it.
  oidc_issuer = replace(module.eks.cluster_oidc_issuer_url, "https://", "")
}

# ---- api: sns:Publish (lifecycle topic) + GetSecretValue (db, auth0, graph) ----
resource "aws_iam_role" "irsa_api" {
  name                 = "${local.cluster_name}-irsa-api"
  permissions_boundary = aws_iam_policy.ci_boundary.arn # 12.7c — every TF-created role is bounded
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = module.eks.oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = { StringEquals = {
        "${local.oidc_issuer}:sub" = "system:serviceaccount:${var.k8s_namespace}:wc-api"
        "${local.oidc_issuer}:aud" = "sts.amazonaws.com"
      } }
    }]
  })
  tags = local.common_tags
}

resource "aws_iam_role_policy" "irsa_api" {
  name = "wc-api-least-priv"
  role = aws_iam_role.irsa_api.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "PublishLifecycle"
        Effect   = "Allow"
        Action   = "sns:Publish"
        Resource = aws_sns_topic.lifecycle.arn
      },
      {
        Sid    = "ReadApiSecrets"
        Effect = "Allow"
        Action = "secretsmanager:GetSecretValue"
        Resource = [
          aws_secretsmanager_secret.db.arn,
          aws_secretsmanager_secret.auth0.arn,
          aws_secretsmanager_secret.graph.arn,
        ] # NOT demo
      },
    ]
  })
}

# ---- worker: sqs Receive/Delete/GetQueueAttributes (queue+DLQ) + graph secret ----
resource "aws_iam_role" "irsa_worker" {
  name                 = "${local.cluster_name}-irsa-worker"
  permissions_boundary = aws_iam_policy.ci_boundary.arn # 12.7c — every TF-created role is bounded
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = module.eks.oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = { StringEquals = {
        "${local.oidc_issuer}:sub" = "system:serviceaccount:${var.k8s_namespace}:wc-worker"
        "${local.oidc_issuer}:aud" = "sts.amazonaws.com"
      } }
    }]
  })
  tags = local.common_tags
}

resource "aws_iam_role_policy" "irsa_worker" {
  name = "wc-worker-least-priv"
  role = aws_iam_role.irsa_worker.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ConsumeSync"
        Effect = "Allow"
        Action = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
        Resource = [
          aws_sqs_queue.sync.arn,
          aws_sqs_queue.sync_dlq.arn,
        ]
      },
      {
        Sid      = "ReadGraphSecret"
        Effect   = "Allow"
        Action   = "secretsmanager:GetSecretValue"
        Resource = aws_secretsmanager_secret.graph.arn # graph ONLY (no db/auth0/demo)
      },
    ]
  })
}

# ---- cronjob: GetSecretValue (db ONLY) ----
resource "aws_iam_role" "irsa_cronjob" {
  name                 = "${local.cluster_name}-irsa-cronjob"
  permissions_boundary = aws_iam_policy.ci_boundary.arn # 12.7c — every TF-created role is bounded
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = module.eks.oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = { StringEquals = {
        "${local.oidc_issuer}:sub" = "system:serviceaccount:${var.k8s_namespace}:wc-cronjob"
        "${local.oidc_issuer}:aud" = "sts.amazonaws.com"
      } }
    }]
  })
  tags = local.common_tags
}

resource "aws_iam_role_policy" "irsa_cronjob" {
  name = "wc-cronjob-least-priv"
  role = aws_iam_role.irsa_cronjob.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "ReadDbSecret"
      Effect   = "Allow"
      Action   = "secretsmanager:GetSecretValue"
      Resource = aws_secretsmanager_secret.db.arn # db ONLY
    }]
  })
}

# ---- migration: GetSecretValue (db ONLY) ----
resource "aws_iam_role" "irsa_migration" {
  name                 = "${local.cluster_name}-irsa-migration"
  permissions_boundary = aws_iam_policy.ci_boundary.arn # 12.7c — every TF-created role is bounded
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = module.eks.oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = { StringEquals = {
        "${local.oidc_issuer}:sub" = "system:serviceaccount:${var.k8s_namespace}:wc-migration"
        "${local.oidc_issuer}:aud" = "sts.amazonaws.com"
      } }
    }]
  })
  tags = local.common_tags
}

resource "aws_iam_role_policy" "irsa_migration" {
  name = "wc-migration-least-priv"
  role = aws_iam_role.irsa_migration.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "ReadDbSecret"
      Effect   = "Allow"
      Action   = "secretsmanager:GetSecretValue"
      Resource = aws_secretsmanager_secret.db.arn # db ONLY
    }]
  })
}
