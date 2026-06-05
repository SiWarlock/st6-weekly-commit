# iam_ci.tf — GitHub Actions OIDC + hardened CI deploy role (§13; safety rule #5,
# REQ-S-010/RISK-016). Implements the human's Decision 1 (full-apply CI, hardened
# defense-in-depth) + Decision 3 (Option A: thread a permissions boundary through
# EVERY TF-created role) — see docs/decisions/001.
#
# The CI role runs the full §13 `terraform apply` + `kubectl` deploy. Its broad
# blast radius is neutralized by defense-in-depth, NOT by service-allowlisting:
#  1. `ci_boundary` permissions boundary (below) is attached to the CI role AND
#     every TF-created role (the 4 IRSA roles, the eks module cluster/node-group
#     roles, the ALB-controller role, and external-dns in 12.8). It is a GUARDRAIL,
#     not an allowlist: Allow `*` (so it's a guaranteed SUPERSET of every bounded
#     role — nothing is capped below its needs, robust to AWS-managed-policy drift)
#     + the escalation DENIES, which are the real control.
#  2. Service-level least-privilege lives in the IDENTITY policies (the CI deploy
#     policy = 18 services; each IRSA = its 1-2 actions; ALB-controller = its
#     module policy).
#  3. OIDC trust is environment-scoped (`environment:production`) — gated by a
#     GitHub Environment with required reviewers (HITL repo prereq); no static keys.

locals {
  # Construct the boundary ARN from account-id so the boundary policy can reference
  # its OWN arn (in the create-role condition + the boundary-edit deny) WITHOUT a
  # self-dependency cycle. Role boundaries use aws_iam_policy.ci_boundary.arn
  # (resource ref) so terraform creates the policy before the roles.
  ci_boundary_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/${local.cluster_name}-ci-boundary"

  # Service-level least-privilege for the CI role's IDENTITY policy — the full §13
  # apply chain (incl. external-dns's route53). NOT the boundary.
  ci_deploy_services = [
    "ec2:*", "eks:*", "rds:*", "ecr:*", "s3:*", "cloudfront:*", "route53:*",
    "secretsmanager:*", "sns:*", "sqs:*", "iam:*", "elasticloadbalancing:*",
    "acm:*", "logs:*", "dynamodb:*", "kms:*", "autoscaling:*", "cloudwatch:*",
  ]
}

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # thumbprint_list omitted — aws provider 6.x auto-handles the well-known GitHub
  # IdP (LESSONS, 12.7b finding).
  tags = local.common_tags
}

# Guardrail permissions boundary — Allow * (superset of every bounded role) + the
# escalation DENIES (the actual control). Attached to the CI role + every
# TF-created role so none can escalate, regardless of its identity policy.
resource "aws_iam_policy" "ci_boundary" {
  name = "${local.cluster_name}-ci-boundary"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "AllowAll"
        Effect   = "Allow"
        Action   = "*"
        Resource = "*"
      },
      {
        # No static-credential backdoors (IAM users / access keys / login profiles).
        Sid      = "DenyStaticCredentialBackdoors"
        Effect   = "Deny"
        Resource = "*"
        Action = [
          "iam:CreateUser", "iam:CreateAccessKey", "iam:CreateLoginProfile",
          "iam:UpdateLoginProfile", "iam:CreateServiceSpecificCredential",
          "iam:PutUserPolicy", "iam:AttachUserPolicy", "iam:CreateSAMLProvider",
        ]
      },
      {
        # Can't remove a boundary from any role/user.
        Sid      = "DenyBoundaryTampering"
        Effect   = "Deny"
        Resource = "*"
        Action   = ["iam:DeleteRolePermissionsBoundary", "iam:DeleteUserPermissionsBoundary"]
      },
      {
        # Can ONLY create/modify roles that themselves carry ci_boundary — closes
        # the escalation-by-creating-an-unbounded-role loop.
        Sid      = "DenyUnboundedRoleWrite"
        Effect   = "Deny"
        Resource = "*"
        Action   = ["iam:CreateRole", "iam:PutRolePolicy", "iam:AttachRolePolicy", "iam:PutRolePermissionsBoundary"]
        Condition = {
          StringNotEquals = { "iam:PermissionsBoundary" = local.ci_boundary_arn }
        }
      },
      {
        # Can't rewrite the boundary policy itself.
        Sid      = "DenyBoundaryPolicyEdit"
        Effect   = "Deny"
        Action   = ["iam:CreatePolicyVersion", "iam:SetDefaultPolicyVersion", "iam:DeletePolicy"]
        Resource = local.ci_boundary_arn
      },
      {
        # PassRole only the project's own wc-* roles (not arbitrary roles).
        Sid         = "DenyPassRoleOutsideWc"
        Effect      = "Deny"
        Action      = "iam:PassRole"
        NotResource = ["arn:aws:iam::*:role/wc-*"]
      },
    ]
  })
  tags = local.common_tags
}

resource "aws_iam_role" "ci_deploy" {
  name                 = "${local.cluster_name}-ci-deploy"
  permissions_boundary = aws_iam_policy.ci_boundary.arn

  # OIDC trust: ONLY the github_repo's `production` GitHub Environment (required
  # reviewers gate each deploy — HITL repo prereq). No static keys, no IAM-user
  # principal.
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = { StringEquals = {
        "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        "token.actions.githubusercontent.com:sub" = "repo:${var.github_repo}:environment:production"
      } }
    }]
  })
  tags = local.common_tags
}

# Identity policy — the broad §13 deploy service set (service-level least-privilege
# lives here; the boundary is the escalation guardrail).
resource "aws_iam_role_policy" "ci_deploy" {
  name = "wc-ci-deploy"
  role = aws_iam_role.ci_deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Sid = "DeployServices", Effect = "Allow", Action = local.ci_deploy_services, Resource = "*" },
      {
        # The eks managed-node-group module reads the EKS-optimized-AMI release version from
        # the AWS-PUBLIC SSM parameter (arn:aws:ssm:<region>::parameter/aws/service/eks/*) at
        # plan time. Scope tightly to those public params (NOT ssm:* on *) so the CI role can
        # plan/apply the node group (deploy-issue #4 — AccessDenied on data.aws_ssm_parameter.ami).
        Sid      = "ReadEksPublicAmiSsmParams"
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParameters"]
        Resource = "arn:aws:ssm:*::parameter/aws/service/eks/*"
      },
    ]
  })
}

# CI EKS access entry (the 12.2-deferred entry) — cluster-admin so CI can apply the
# cluster-wide manifests (namespace/SAs/SPC/deployments/ingress/jobs/external-dns).
resource "aws_eks_access_entry" "ci" {
  cluster_name  = module.eks.cluster_name
  principal_arn = aws_iam_role.ci_deploy.arn
  type          = "STANDARD"
}

resource "aws_eks_access_policy_association" "ci_admin" {
  cluster_name  = module.eks.cluster_name
  principal_arn = aws_iam_role.ci_deploy.arn
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }
}
