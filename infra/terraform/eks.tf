# eks.tf — EKS cluster + one thin managed node group + IRSA OIDC provider +
# AWS Load Balancer Controller (terraform-aws-modules/eks v21, RISK-009 thin path).
#
# v21 schema note: the module renamed core inputs vs v20 (`name`,
# `kubernetes_version`, `addons`, `endpoint_public_access`) — authored against the
# verified v21 schema, not memory.

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 21.0" # latest 21.23.0 verified at author time (LESSONS §4)

  name               = local.cluster_name
  kubernetes_version = var.eks_cluster_version

  # Hardened CI (12.7c / Decision 3 Option A): the module-created CLUSTER role
  # carries ci_boundary so CI's terraform apply can create it (the deploy role
  # denies creating unbounded roles).
  iam_role_permissions_boundary = aws_iam_policy.ci_boundary.arn

  # Public API endpoint so the HITL operator + CI can reach it; cluster creator is
  # granted admin via an access entry (API auth mode — v21 default). The CI-deploy-
  # role access entry is added in 12.7 alongside the role itself (deferred here to
  # avoid referencing a not-yet-created resource — infra LESSONS §3).
  endpoint_public_access                   = true
  enable_cluster_creator_admin_permissions = true

  # IRSA: create the cluster OIDC provider (the per-workload IRSA roles in 12.7 and
  # the ALB-controller role below trust it). Default-true in v21 — set explicitly
  # since the whole IRSA chain depends on oidc_provider_arn being real at apply.
  enable_irsa = true

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  addons = {
    coredns                = {}
    eks-pod-identity-agent = {}
    kube-proxy             = {}
    vpc-cni                = { before_compute = true }
  }

  # Exactly ONE managed node group, thin (no autoscaler — RISK-009), sized for
  # api + worker + CronJob + migration Job (REQ-O-013).
  eks_managed_node_groups = {
    default = {
      ami_type       = "AL2023_x86_64_STANDARD" # v21 default for k8s ≥ 1.30
      instance_types = var.node_instance_types

      min_size     = var.node_min_size
      max_size     = var.node_max_size
      desired_size = var.node_desired_size

      # Node-group role carries ci_boundary (12.7c). NOTE: the per-node-group
      # input, NOT the module's node_iam_role_permissions_boundary (that one is
      # for EKS Auto Mode, which we don't use).
      iam_role_permissions_boundary = aws_iam_policy.ci_boundary.arn
    }
  }

  tags = local.common_tags
}

# IRSA role for the AWS Load Balancer Controller. The iam v6 submodule was renamed
# `iam-role-for-service-accounts-eks` → `iam-role-for-service-accounts`; it bundles
# the controller's IAM policy via `attach_load_balancer_controller_policy`.
module "alb_controller_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts"
  version = "~> 6.0" # latest 6.6.1 verified at author time

  name                                   = "${local.cluster_name}-alb-controller"
  permissions_boundary                   = aws_iam_policy.ci_boundary.arn # 12.7c — guardrail boundary (superset; doesn't cap)
  attach_load_balancer_controller_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:aws-load-balancer-controller"]
    }
  }

  tags = local.common_tags
}

# Install the AWS Load Balancer Controller via Helm (REQ-O-012). The chart creates
# its own ServiceAccount annotated with the IRSA role ARN. helm provider v3 uses
# `set = [{...}]` list-of-objects (configured in versions.tf, keyed off the cluster).
resource "helm_release" "aws_load_balancer_controller" {
  name       = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = var.alb_controller_chart_version
  namespace  = "kube-system"

  set = [
    {
      name  = "clusterName"
      value = module.eks.cluster_name
    },
    {
      name  = "serviceAccount.create"
      value = "true"
    },
    {
      name  = "serviceAccount.name"
      value = "aws-load-balancer-controller"
    },
    {
      name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
      value = module.alb_controller_irsa.arn
    },
    {
      name  = "region"
      value = var.region
    },
    {
      name  = "vpcId"
      value = module.vpc.vpc_id
    },
  ]

  depends_on = [module.eks]
}
