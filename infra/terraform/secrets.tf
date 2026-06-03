# secrets.tf — four Secrets Manager secrets (§12): db, auth0, graph, demo.
# Mounted into pods via the Secrets Store CSI Driver + ASCP (12.8); consumed by
# Spring via spring.config.import=optional:file:/mnt/secrets/... (Appendix D).
#
# SAFETY (rule #7 / RISK-016): secret VALUES never leave encrypted state — only
# the ARNs are exported (for 12.7 IRSA + 12.8 SecretProviderClass).
#  - db: TF-populated from the 12.3 RDS coords + random_password (Pattern 2,
#    LESSONS §7). The value (incl. the password) is a resource reference, rendered
#    only into encrypted state; never a literal in source, never an output.
#  - auth0/graph/demo: container + a PLACEHOLDER version with ignore_changes, so
#    HITL populates real third-party creds out-of-band without TF reverting them —
#    real creds never touch TF state/vars. Placeholders carry the contract key
#    names (Appendix D.2/D.3) so a pre-HITL CSI mount resolves. The authoritative
#    key↔jmesPath alignment lands with the 12.8 SecretProviderClass.

resource "aws_secretsmanager_secret" "db" {
  name = "wc/${var.env}/db"
  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    "spring.datasource.url"      = "jdbc:postgresql://${aws_db_instance.wc.address}:${aws_db_instance.wc.port}/wc"
    "spring.datasource.username" = "wc_app"
    "spring.datasource.password" = random_password.db.result
  })
}

resource "aws_secretsmanager_secret" "auth0" {
  name = "wc/${var.env}/auth0"
  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "auth0" {
  secret_id = aws_secretsmanager_secret.auth0.id
  secret_string = jsonencode({
    "spring.security.oauth2.resourceserver.jwt.issuer-uri" = "REPLACE_VIA_HITL"
    "auth0.audience"                                       = "REPLACE_VIA_HITL"
  })
  lifecycle {
    ignore_changes = [secret_string]
  }
}

resource "aws_secretsmanager_secret" "graph" {
  name = "wc/${var.env}/graph"
  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "graph" {
  secret_id = aws_secretsmanager_secret.graph.id
  secret_string = jsonencode({
    GRAPH_TENANT_ID     = "REPLACE_VIA_HITL"
    GRAPH_CLIENT_ID     = "REPLACE_VIA_HITL"
    GRAPH_CLIENT_SECRET = "REPLACE_VIA_HITL"
  })
  lifecycle {
    ignore_changes = [secret_string]
  }
}

resource "aws_secretsmanager_secret" "demo" {
  name = "wc/${var.env}/demo"
  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "demo" {
  secret_id     = aws_secretsmanager_secret.demo.id
  secret_string = jsonencode({ placeholder = "REPLACE_VIA_HITL" })
  lifecycle {
    ignore_changes = [secret_string]
  }
}
