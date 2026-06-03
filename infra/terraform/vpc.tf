# vpc.tf — VPC network foundation (terraform-aws-modules/vpc, RISK-009 thin path).
#
# Public + private subnets across ≥2 AZs with NAT egress. The AWS Load Balancer
# Controller discovers subnets by tag: `kubernetes.io/role/elb` (public, internet-
# facing ALB) and `kubernetes.io/role/internal-elb` (private), plus the
# `kubernetes.io/cluster/<name>` ownership tag.

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  # First var.az_count AZs in the region (≥2 enforced by the variable validation).
  azs = slice(data.aws_availability_zones.available.names, 0, var.az_count)
}

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 6.0" # latest 6.6.1 verified at author time; no module lockfile — constraint IS the pin (LESSONS §4)

  name = local.cluster_name
  cidr = var.vpc_cidr

  azs = local.azs
  # Private /20 (ample pod IPs for the VPC-CNI), public /24 (ALB only).
  private_subnets = [for i in range(var.az_count) : cidrsubnet(var.vpc_cidr, 4, i)]
  public_subnets  = [for i in range(var.az_count) : cidrsubnet(var.vpc_cidr, 8, i + 48)]

  enable_nat_gateway = true
  single_nat_gateway = true # thin/cost per RISK-009 — one NAT, not per-AZ (accepted single-AZ-NAT egress risk)

  # AWS Load Balancer Controller subnet discovery (REQ-O-012).
  public_subnet_tags = {
    "kubernetes.io/role/elb"                      = "1"
    "kubernetes.io/cluster/${local.cluster_name}" = "shared"
  }
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb"             = "1"
    "kubernetes.io/cluster/${local.cluster_name}" = "shared"
  }

  tags = local.common_tags
}
