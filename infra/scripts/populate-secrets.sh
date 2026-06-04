#!/usr/bin/env bash
#
# populate-secrets.sh — write the operator-supplied Auth0 + MS Graph credential
# VALUES into the Terraform-created AWS Secrets Manager placeholder containers
# (wc/<env>/auth0, wc/<env>/graph). Run ONCE after `terraform apply`, before the
# pods deploy. The `db` secret is TF-populated from RDS (skipped); `demo` is a
# prod no-op (skipped). Values come from runbook (a); run-timing is runbook (c).
# (ARCHITECTURE §12 / Appendix D.2,D.3; safety rule #7.)
#
# pragma: secrets — NO value is ever echoed, logged, written to disk, or placed in
# argv. Only secret NAMES, KEY names, and the returned VersionId/ARN are printed.
# Secret JSON is built by `jq -n` reading values from the ENVIRONMENT (env.X — NOT
# --arg, which would expose them in jq's argv/`ps`) and piped to the AWS CLI via
# `--secret-string file:///dev/stdin` (stdin — never an inline arg, never a file on
# disk). Do NOT add `set -x`. Do NOT echo/printf any *_V / *secret value variable.
#
# Portable fallback (if `file:///dev/stdin` is ever unavailable): write the JSON to
# a `mktemp` file, `chmod 600`, pass via `--secret-string file://<tmp>`, and
# `trap 'shred -u "$tmp" 2>/dev/null || rm -f "$tmp"' EXIT`. The stdin pipe below
# is preferred — it keeps the secret off disk entirely.

set -euo pipefail

# --- defaults ----------------------------------------------------------------
ENV_NAME="${ENV:-aws}"          # secret-name prefix wc/<env>/… ; mirrors TF var.env + bootstrap
REGION=""                       # empty → AWS CLI default resolution (AWS_REGION / profile)
ROOT_DOMAIN="${ROOT_DOMAIN:-}"  # used only to derive the auth0 audience default
ONLY=""                         # "", "auth0", or "graph"
DRY_RUN="false"
SELF="$(basename "$0")"

# --- logging (names only — never values) -------------------------------------
log()  { printf '%s\n' "$*"; }
warn() { printf 'WARN: %s\n' "$*" >&2; }
die()  { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<EOF
$SELF — populate the Auth0 + MS Graph secret VALUES into the Terraform-created
Secrets Manager containers (post-'terraform apply', pre-pod-deploy HITL step).

USAGE
  $SELF [--only auth0|graph] [--env <env>] [--region <region>] [--dry-run] [--help]

WHAT IT TOUCHES
  wc/<env>/auth0   keys: spring.security.oauth2.resourceserver.jwt.issuer-uri
                         auth0.audience
  wc/<env>/graph   keys: GRAPH_TENANT_ID, GRAPH_CLIENT_ID, GRAPH_CLIENT_SECRET
  wc/<env>/db      SKIPPED — Terraform-populated from RDS (do not touch).
  wc/<env>/demo    SKIPPED — prod no-op (demo-auth disabled, safety rule #5).

INPUTS (environment variable if set, else interactive prompt)
  AUTH0_ISSUER_URI     Auth0 tenant issuer URL, e.g. https://TENANT.auth0.com/
                       (note the TRAILING SLASH — the token 'iss' must match exactly).
  AUTH0_AUDIENCE       API identifier; defaults to https://api.wc.\$ROOT_DOMAIN.
  ROOT_DOMAIN          apex domain, used only to derive the AUTH0_AUDIENCE default.
  GRAPH_TENANT_ID      Entra tenant id         (public).
  GRAPH_CLIENT_ID      Entra app client id     (public).
  GRAPH_CLIENT_SECRET  Entra app client secret (SECRET — prompted with NO echo).
  Provenance: Auth0 values from runbook (a) steps 1-3; Graph values from the Entra
  app registration (runbook a, Wave 2).

TWO-WAVE NOTE
  Wave 1 (real-OAuth deployed demo) needs only auth0: run '$SELF --only auth0'.
  Graph is for the Wave-2 worker — populate it once the Entra app reg exists.
  With no --only, a secret whose inputs are absent is skipped with a notice.

SAFETY (rule #7)
  No secret value is echoed, logged, written to disk, or placed in a command
  argument. Values are piped to the AWS CLI over stdin; only the returned
  VersionId/ARN is printed as the success signal. '--dry-run' prints the plan
  with all values redacted (***) and makes ZERO AWS calls.

REQUIRES (non-dry-run): aws CLI + jq on PATH; AWS creds with
  secretsmanager:PutSecretValue on wc/<env>/{auth0,graph}; region via
  AWS_REGION / AWS_PROFILE or --region.
EOF
}

# --- arg parse ---------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --only)     ONLY="${2:-}"; shift 2 ;;
    --only=*)   ONLY="${1#*=}"; shift ;;
    --env)      ENV_NAME="${2:-}"; shift 2 ;;
    --env=*)    ENV_NAME="${1#*=}"; shift ;;
    --region)   REGION="${2:-}"; shift 2 ;;
    --region=*) REGION="${1#*=}"; shift ;;
    --dry-run)  DRY_RUN="true"; shift ;;
    --help|-h)  usage; exit 0 ;;
    *)          die "unknown argument: $1 (try --help)" ;;
  esac
done

case "$ONLY" in
  ""|auth0|graph) : ;;
  *) die "--only must be 'auth0' or 'graph' (got '$ONLY')" ;;
esac

# Can we prompt? (a usable controlling terminal)
INTERACTIVE="false"
if [[ -r /dev/tty && -w /dev/tty ]]; then INTERACTIVE="true"; fi

# Optional --region as a shellcheck-clean conditional arg array.
region_args=()
if [[ -n "$REGION" ]]; then region_args=(--region "$REGION"); fi

# --- input helper ------------------------------------------------------------
# prompt_var VARNAME "Label" [secret]
#   Reads from the controlling terminal into the named caller variable (dynamic
#   scope). 'secret' mode uses `read -s` (no echo). Returns 1 if non-interactive
#   (caller then treats the value as absent → skip-with-notice).
prompt_var() {
  local __name="$1" __label="$2" __mode="${3:-visible}" __val=""
  if [[ "$INTERACTIVE" != "true" ]]; then return 1; fi
  if [[ "$__mode" == "secret" ]]; then
    read -r -s -p "  ${__label}: " __val </dev/tty
    printf '\n' >/dev/tty
  else
    read -r -p "  ${__label}: " __val </dev/tty
  fi
  printf -v "$__name" '%s' "$__val"
}

# --- AWS interaction (names + VersionId/ARN only) ----------------------------
# put_secret <short>  — reads the secret JSON on stdin, writes a new version.
put_secret() {
  local short="$1" secret_id resp version_id arn
  secret_id="wc/${ENV_NAME}/${short}"
  if ! resp="$(aws secretsmanager put-secret-value \
        --secret-id "$secret_id" \
        --secret-string file:///dev/stdin \
        ${region_args[@]+"${region_args[@]}"} \
        --output json)"; then
    warn "put-secret-value failed for ${secret_id} (check AWS creds + that the container exists)."
    return 1
  fi
  version_id="$(printf '%s' "$resp" | jq -r '.VersionId')"
  arn="$(printf '%s' "$resp" | jq -r '.ARN')"
  log "  ok  ${secret_id} — VersionId=${version_id}"
  log "      ARN=${arn}"
}

# exists <short> — true if the TF-created container is present (metadata only).
exists() {
  aws secretsmanager describe-secret \
    --secret-id "wc/${ENV_NAME}/$1" \
    ${region_args[@]+"${region_args[@]}"} >/dev/null 2>&1
}

# plan_line <secret_id> <key>...  — dry-run plan with values redacted.
plan_line() {
  local secret_id="$1" k
  shift
  log "  [dry-run] would set ${secret_id}:"
  for k in "$@"; do log "              ${k} = ***"; done
}

# --- secret writers ----------------------------------------------------------
write_auth0() {
  local issuer="${AUTH0_ISSUER_URI:-}" audience="${AUTH0_AUDIENCE:-}"
  if [[ -z "$audience" && -n "$ROOT_DOMAIN" ]]; then audience="https://api.wc.${ROOT_DOMAIN}"; fi

  if [[ "$DRY_RUN" == "true" ]]; then
    plan_line "wc/${ENV_NAME}/auth0" \
      "spring.security.oauth2.resourceserver.jwt.issuer-uri" "auth0.audience"
    return 0
  fi

  if [[ -z "$issuer" ]]; then
    prompt_var issuer "Auth0 issuer-uri (e.g. https://TENANT.auth0.com/)" || true
  fi
  if [[ -z "$audience" ]]; then
    if [[ -z "$ROOT_DOMAIN" ]]; then
      prompt_var ROOT_DOMAIN "ROOT_DOMAIN (for the api.wc.<domain> audience)" || true
    fi
    if [[ -n "$ROOT_DOMAIN" ]]; then audience="https://api.wc.${ROOT_DOMAIN}"; fi
    if [[ -z "$audience" ]]; then
      prompt_var audience "Auth0 audience (https://api.wc.<domain>)" || true
    fi
  fi

  if [[ -z "$issuer" || -z "$audience" ]]; then
    warn "auth0: missing issuer-uri and/or audience — SKIPPED (set AUTH0_ISSUER_URI / AUTH0_AUDIENCE or run interactively)."
    return 0
  fi

  case "$issuer" in
    */) : ;;
    *) warn "auth0 issuer-uri has no trailing '/' — Auth0 issuers end in '/' and the token 'iss' must match the resource-server's expected issuer exactly. Continuing as entered." ;;
  esac

  if ! exists auth0; then
    warn "wc/${ENV_NAME}/auth0 container not found — run 'terraform apply' first. SKIPPED."
    return 0
  fi

  AUTH0_ISSUER_V="$issuer" AUTH0_AUDIENCE_V="$audience" jq -n \
    '{"spring.security.oauth2.resourceserver.jwt.issuer-uri": env.AUTH0_ISSUER_V,
      "auth0.audience": env.AUTH0_AUDIENCE_V}' \
    | put_secret auth0
}

write_graph() {
  local tenant="${GRAPH_TENANT_ID:-}" client="${GRAPH_CLIENT_ID:-}" secret="${GRAPH_CLIENT_SECRET:-}"

  if [[ "$DRY_RUN" == "true" ]]; then
    plan_line "wc/${ENV_NAME}/graph" GRAPH_TENANT_ID GRAPH_CLIENT_ID GRAPH_CLIENT_SECRET
    return 0
  fi

  if [[ -z "$tenant" ]]; then prompt_var tenant "Graph GRAPH_TENANT_ID" || true; fi
  if [[ -z "$client" ]]; then prompt_var client "Graph GRAPH_CLIENT_ID" || true; fi
  if [[ -z "$secret" ]]; then prompt_var secret "Graph GRAPH_CLIENT_SECRET (hidden)" secret || true; fi

  if [[ -z "$tenant" || -z "$client" || -z "$secret" ]]; then
    warn "graph: missing tenant/client/secret — SKIPPED (Wave-2 input; set GRAPH_* or run interactively once the Entra app reg exists)."
    return 0
  fi

  if ! exists graph; then
    warn "wc/${ENV_NAME}/graph container not found — run 'terraform apply' first. SKIPPED."
    return 0
  fi

  GRAPH_TENANT_V="$tenant" GRAPH_CLIENT_V="$client" GRAPH_SECRET_V="$secret" jq -n \
    '{GRAPH_TENANT_ID: env.GRAPH_TENANT_V,
      GRAPH_CLIENT_ID: env.GRAPH_CLIENT_V,
      GRAPH_CLIENT_SECRET: env.GRAPH_SECRET_V}' \
    | put_secret graph
}

# --- main --------------------------------------------------------------------
main() {
  local mode="apply"
  if [[ "$DRY_RUN" == "true" ]]; then mode="dry-run"; fi

  if [[ "$DRY_RUN" != "true" ]]; then
    command -v aws >/dev/null 2>&1 || die "aws CLI not found on PATH (required for a real run; use --dry-run to preview)."
    command -v jq  >/dev/null 2>&1 || die "jq not found on PATH (required to build/parse JSON safely)."
  fi

  log "populate-secrets: env=${ENV_NAME} mode=${mode}${REGION:+ region=${REGION}}"

  if [[ -z "$ONLY" || "$ONLY" == "auth0" ]]; then write_auth0; fi
  if [[ -z "$ONLY" || "$ONLY" == "graph" ]]; then write_graph; fi

  if [[ "$DRY_RUN" == "true" ]]; then
    log "dry-run complete — no AWS calls made."
  else
    log "done — only NAMES + VersionId/ARN were printed; no secret value was emitted."
  fi
}

main
