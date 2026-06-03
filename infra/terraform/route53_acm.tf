# route53_acm.tf — DNS + TLS for the custom domains (§12, REQ-O-008, RISK-010).
#
# Two ACM certs, both DNS-validated: the CloudFront cert MUST be in us-east-1
# (via the aws.us_east_1 aliased provider — RISK-010); the ALB cert is regional.
# Consumers reference the `aws_acm_certificate_validation.*.certificate_arn` (not
# the raw cert arn) so they only go live once DNS validation completes (LESSONS §11).
#
# The hosted zone PRE-EXISTS (HITL-registered/delegated domain) — this slice
# manages records, not the zone.
#
# api.wc.${ROOT_DOMAIN} → ALB alias is NOT here: the ALB is created by the AWS Load
# Balancer Controller at deploy time (post-terraform), so its DNS name can't be a
# pure-terraform record. The ALB CERT (below) is independent of the ALB and IS
# created; the api.wc alias record lands per the escalated Finding (likely
# CI-scripted in 12.11).

data "aws_route53_zone" "root" {
  name = var.ROOT_DOMAIN
}

# CloudFront cert — us-east-1 (RISK-010).
resource "aws_acm_certificate" "cloudfront" {
  provider          = aws.us_east_1
  domain_name       = "wc.${var.ROOT_DOMAIN}"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
  tags = local.common_tags
}

# ALB cert — regional (default provider). DNS-validated independently of the ALB.
resource "aws_acm_certificate" "alb" {
  domain_name       = "api.wc.${var.ROOT_DOMAIN}"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
  tags = local.common_tags
}

# DNS validation records (canonical ACM pattern — for_each over the cert's
# domain_validation_options; keys derive from the known domain_name).
resource "aws_route53_record" "cloudfront_validation" {
  for_each = {
    for dvo in aws_acm_certificate.cloudfront.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      type   = dvo.resource_record_type
      record = dvo.resource_record_value
    }
  }
  zone_id         = data.aws_route53_zone.root.zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_route53_record" "alb_validation" {
  for_each = {
    for dvo in aws_acm_certificate.alb.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      type   = dvo.resource_record_type
      record = dvo.resource_record_value
    }
  }
  zone_id         = data.aws_route53_zone.root.zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "cloudfront" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.cloudfront.arn
  validation_record_fqdns = [for r in aws_route53_record.cloudfront_validation : r.fqdn]
}

resource "aws_acm_certificate_validation" "alb" {
  certificate_arn         = aws_acm_certificate.alb.arn
  validation_record_fqdns = [for r in aws_route53_record.alb_validation : r.fqdn]
}

# wc.${ROOT_DOMAIN} → CloudFront alias. Z2FDTNDATAQYW2 is CloudFront's documented
# fixed hosted-zone id for alias targets.
resource "aws_route53_record" "wc" {
  zone_id = data.aws_route53_zone.root.zone_id
  name    = "wc.${var.ROOT_DOMAIN}"
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.assets.domain_name
    zone_id                = "Z2FDTNDATAQYW2"
    evaluate_target_health = false
  }
}
