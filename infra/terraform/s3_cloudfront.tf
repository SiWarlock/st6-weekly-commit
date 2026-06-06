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

# CORS for the wc-web Module-Federation REMOTE (Deploy 2, cross-origin): the remoteEntry.js +
# federation chunks publish under /remote/* on THIS wc. distribution and are loaded CROSS-ORIGIN by
# the portal host. This policy adds Access-Control-Allow-Origin: https://portal.${ROOT_DOMAIN} to the
# /remote/* behavior ONLY (below) — it does NOT touch the default behavior / SPA root / SPA fallback,
# which stay byte-identical. Reversible: drop this resource + the ordered_cache_behavior to revert.
# Same-origin fallback (if the additive plan ever surprises us): publish the remote into the PORTAL
# bucket /remote/ instead → VITE_WC_REMOTE_URL=https://portal.${ROOT_DOMAIN}/remote/remoteEntry.js, no CORS.
resource "aws_cloudfront_response_headers_policy" "remote_cors" {
  name    = "${local.cluster_name}-remote-cors"
  comment = "CORS ACAO=portal host for the wc-web federation remote (/remote/*) — Deploy 2."

  cors_config {
    access_control_allow_credentials = false

    access_control_allow_headers {
      items = ["*"]
    }
    access_control_allow_methods {
      items = ["GET", "HEAD", "OPTIONS"]
    }
    access_control_allow_origins {
      items = ["https://portal.${var.ROOT_DOMAIN}"]
    }
    origin_override = true
  }
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

  # Deploy-2 cross-origin federation: serve the wc-web REMOTE (remoteEntry.js + chunks) under
  # /remote/* with the CORS policy (ACAO: portal host) + OPTIONS preflight attached. PURELY ADDITIVE
  # — the default_cache_behavior above + the SPA fallback below are unchanged/byte-identical; this
  # only adds the more-specific /remote/* path. Reversible (drop this block + the remote_cors policy).
  ordered_cache_behavior {
    path_pattern               = "/remote/*"
    target_origin_id           = "s3-assets"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_optimized.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.remote_cors.id
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

  # Custom domain wc.${ROOT_DOMAIN} on the us-east-1 ACM cert (12.7a attach,
  # LESSONS §10/§11). The cert is referenced via its _validation resource so the
  # distribution only goes live once DNS validation completes.
  aliases = ["wc.${var.ROOT_DOMAIN}"]

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.cloudfront.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
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
