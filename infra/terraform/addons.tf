# addons.tf — cluster add-ons installed via Terraform helm_release (LESSON §17:
# install-don't-just-consume + ONE consistent mechanism). Convention: PLATFORM
# CONTROLLERS install via helm_release (here + the ALB controller in eks.tf); APP
# WORKLOADS stay kubectl manifests (infra/k8s/*). Chart versions are pinned literals,
# verified against the live Helm repo index at author time (LESSONS §4):
#   secrets-store-csi-driver          1.6.0  (kubernetes-sigs repo index)
#   secrets-store-csi-driver-provider-aws 3.1.1 (aws repo index)
#   external-dns                      1.21.1 (app v0.21.0; kubernetes-sigs repo index)
#
# No aws_iam_role/policy is created here, so no permissions_boundary (D3 applies to
# aws_iam_role only). The external-dns IRSA role stays in iam_irsa.tf (unchanged); the
# ingress-api.yaml hostname annotation stays (external-dns still reads it).

# 1) Secrets Store CSI Driver (kube-system) — the SecretProviderClass CRD + node
#    DaemonSet that 12.6/12.7b/12.8/12.9 ride on. syncSecret.enabled=false keeps secret
#    values OUT of k8s Secrets/etcd (rule #7) — tmpfs file mount at /mnt/secrets only.
resource "helm_release" "secrets_store_csi_driver" {
  name       = "secrets-store-csi-driver"
  repository = "https://kubernetes-sigs.github.io/secrets-store-csi-driver/charts"
  chart      = "secrets-store-csi-driver"
  version    = "1.6.0" # verified vs the repo index at author time (LESSONS §4)
  namespace  = "kube-system"

  values = [yamlencode({
    syncSecret           = { enabled = false } # rule #7 — never sync mounted secrets into k8s Secrets/etcd
    enableSecretRotation = true                # ★Q-rotation — rotated creds refresh without a pod restart
    rotationPollInterval = "2m"
  })]

  depends_on = [module.eks]
}

# 2) AWS provider (ASCP, kube-system) — fetches from Secrets Manager using the
#    REQUESTING POD's IRSA (no new IAM role). The chart bundles the CSI driver as a
#    subchart with install=true BY DEFAULT; we DISABLE it (install=false) because the
#    driver is installed explicitly above — leaving it on would create a DUPLICATE
#    driver DaemonSet.
resource "helm_release" "secrets_store_csi_driver_provider_aws" {
  name       = "secrets-store-csi-driver-provider-aws"
  repository = "https://aws.github.io/secrets-store-csi-driver-provider-aws"
  chart      = "secrets-store-csi-driver-provider-aws"
  version    = "3.1.1" # verified vs the repo index at author time (LESSONS §4)
  namespace  = "kube-system"

  values = [yamlencode({
    "secrets-store-csi-driver" = { install = false } # driver installed separately above (avoid duplicate DaemonSet)
  })]

  depends_on = [helm_release.secrets_store_csi_driver]
}

# 3) external-dns (namespace kube-system) — converted from the 12.8 raw manifest to the
#    official chart (LESSON §17 one-mechanism). Reproduces the 12.8 config: provider aws,
#    upsert-only, txt registry + owner-id wc, ingress source, ROOT_DOMAIN filter. Its SA
#    is annotated with the external-dns IRSA role (iam_irsa.tf) — trust sub updated to
#    system:serviceaccount:kube-system:external-dns to match this placement. external-dns
#    is a CLUSTER ADD-ON, so it lives in kube-system alongside the CSI driver + ASCP + the
#    ALB controller (namespace-consistent; kube-system always exists → no create_namespace
#    / apply-ordering concern). --source=ingress is cluster-wide via the chart ClusterRole,
#    so it still watches the wc ingress.
resource "helm_release" "external_dns" {
  name       = "external-dns"
  repository = "https://kubernetes-sigs.github.io/external-dns/"
  chart      = "external-dns"
  version    = "1.21.1"      # app v0.21.0; verified vs the repo index at author time (LESSONS §4)
  namespace  = "kube-system" # cluster add-on — with the CSI driver/ASCP/ALB controller

  values = [yamlencode({
    provider      = { name = "aws" }
    policy        = "upsert-only" # never deletes records it didn't create
    registry      = "txt"
    txtOwnerId    = "wc"
    sources       = ["ingress"]
    domainFilters = [var.ROOT_DOMAIN]
    serviceAccount = {
      create = true
      name   = "external-dns"
      annotations = {
        "eks.amazonaws.com/role-arn" = aws_iam_role.external_dns.arn
      }
    }
  })]

  depends_on = [module.eks]
}
