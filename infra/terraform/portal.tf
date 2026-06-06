# portal.tf — Deploy-2 federation HOST hosting: portal.${ROOT_DOMAIN}.
#
# A Module-Federation HOST shell that loads the wc-web remote. Authored AHEAD of Deploy 2 and
# isolated in its own file so it is NOT entangled with the polish / clean-green deploys:
#  - The portal ACM cert is a FRESH DNS-validated cert kept HERE (never edits the wc./api. certs in
#    route53_acm.tf), so a portal cert (re)validation can't disturb the live certs (the cert-
#    revalidation-off-clean-green concern raised earlier).
#  - Everything mirrors the wc. SPA hosting (s3_cloudfront.tf) + cert/DNS pattern (route53_acm.tf):
#    private S3 + OAC + CloudFront + SPA fallback + us-east-1 ACM + Route53 alias.
#
# Reuses the module-global data sources declared in s3_cloudfront.tf / route53_acm.tf:
#   data.aws_caller_identity.current, data.aws_cloudfront_cache_policy.caching_optimized,
#   data.aws_route53_zone.root.

resource "aws_s3_bucket" "portal" {
  bucket = "wc-${var.env}-portal-${data.aws_caller_identity.current.account_id}" # account suffix → globally unique
  tags   = local.common_tags
}

resource "aws_s3_bucket_public_access_block" "portal" {
  bucket                  = aws_s3_bucket.portal.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "portal" {
  name                              = "${local.cluster_name}-portal-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# Portal CloudFront cert — us-east-1 (RISK-010), SEPARATE from the wc. cert (LESSONS §11). Isolated
# here so a portal cert (re)validation never touches the live wc./api. certs.
resource "aws_acm_certificate" "portal" {
  provider          = aws.us_east_1
  domain_name       = "portal.${var.ROOT_DOMAIN}"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
  tags = local.common_tags
}

# DNS validation records (canonical ACM pattern — for_each over domain_validation_options).
resource "aws_route53_record" "portal_validation" {
  for_each = {
    for dvo in aws_acm_certificate.portal.domain_validation_options : dvo.domain_name => {
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

resource "aws_acm_certificate_validation" "portal" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.portal.arn
  validation_record_fqdns = [for r in aws_route53_record.portal_validation : r.fqdn]
}

resource "aws_cloudfront_distribution" "portal" {
  enabled             = true
  default_root_object = "index.html"
  comment             = "WC federation host portal (${var.env})"

  origin {
    domain_name              = aws_s3_bucket.portal.bucket_regional_domain_name
    origin_id                = "s3-portal"
    origin_access_control_id = aws_cloudfront_origin_access_control.portal.id
  }

  default_cache_behavior {
    target_origin_id       = "s3-portal"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
  }

  # SPA fallback — both 403 (OAC denies LIST on missing key) and 404 → index.html.
  custom_error_response {
    error_code         = 403
    response_code      = 200
    response_page_path = "/index.html"
  }
  custom_error_response {
    error_code         = 404
    response_code      = 200
    response_page_path = "/index.html"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  aliases = ["portal.${var.ROOT_DOMAIN}"]

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.portal.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = local.common_tags
}

resource "aws_s3_bucket_policy" "portal" {
  bucket = aws_s3_bucket.portal.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowCloudFrontOACRead"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.portal.arn}/*"
      Condition = {
        StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.portal.arn }
      }
    }]
  })
}

# portal.${ROOT_DOMAIN} → portal CloudFront alias. Z2FDTNDATAQYW2 is CloudFront's fixed alias zone id.
resource "aws_route53_record" "portal" {
  zone_id = data.aws_route53_zone.root.zone_id
  name    = "portal.${var.ROOT_DOMAIN}"
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.portal.domain_name
    zone_id                = "Z2FDTNDATAQYW2"
    evaluate_target_health = false
  }
}
