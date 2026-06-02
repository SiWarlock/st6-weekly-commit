# s3_cloudfront.tf — SPA delivery (§12, REQ-NF-006, RISK-010).
#
# PRIVATE S3 bucket (block-public-access ALL true; no public ACL, no website
# config) fronted by CloudFront via Origin Access Control (OAC, NOT legacy OAI).
# The bucket policy grants read ONLY to the CloudFront service principal scoped to
# this distribution's ARN — never `*`, never public. SPA fallback maps both 403
# and 404 to /index.html (200) so client-side routes resolve.
#
# Custom domain (wc.${ROOT_DOMAIN}) + the us-east-1 ACM cert attach in 12.7 — the
# cert's DNS validation needs the 12.7 Route53 zone, so 12.6 ships on the default
# CloudFront cert with NO aliases (LESSONS §3: don't reference a not-yet-created
# resource). 12.7 edits the viewer_certificate + aliases below.

data "aws_caller_identity" "current" {}

# Managed cache policy (CachingOptimized) — modern cache_policy_id, avoids the
# deprecated forwarded_values block.
data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

resource "aws_s3_bucket" "assets" {
  bucket = "wc-${var.env}-assets-${data.aws_caller_identity.current.account_id}" # account suffix → globally unique
  tags   = local.common_tags
}

resource "aws_s3_bucket_public_access_block" "assets" {
  bucket                  = aws_s3_bucket.assets.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "assets" {
  name                              = "${local.cluster_name}-assets-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "assets" {
  enabled             = true
  default_root_object = "index.html"
  comment             = "WC SPA (${var.env})"

  origin {
    domain_name              = aws_s3_bucket.assets.bucket_regional_domain_name
    origin_id                = "s3-assets"
    origin_access_control_id = aws_cloudfront_origin_access_control.assets.id
  }

  default_cache_behavior {
    target_origin_id       = "s3-assets"
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

  # 12.7 swaps this to acm_certificate_arn (us-east-1, sni-only, TLS1.2_2021) and
  # adds aliases = ["wc.${var.ROOT_DOMAIN}"].
  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = local.common_tags
}

resource "aws_s3_bucket_policy" "assets" {
  bucket = aws_s3_bucket.assets.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowCloudFrontOACRead"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.assets.arn}/*"
      Condition = {
        StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.assets.arn }
      }
    }]
  })
}
