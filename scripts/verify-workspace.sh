#!/usr/bin/env bash
#
# verify-workspace.sh — executable verification gates for the JS monorepo root (task 0.1).
#
# This slice is build/workspace wiring, not red-green domain logic, so the "tests"
# are re-runnable verification gates that assert on `yarn`/`nx` output and fail loudly.
# Re-used by the Phase-0.8 GitHub Actions CI workflow.
#
# Gates:
#   1. workspace_install_clean       — `yarn install` exits 0 and produces a lockfile.
#   2. nx_lists_js_projects          — `nx show projects` lists wc-web AND wc-e2e.
#   3. install_idempotent            — re-install is immutable; yarn.lock does not drift.
#   4. broken_workspace_fails_fast   — (negative) a malformed workspace member fails install.
#   5. workspace_membership_resolves — (integration) workspace graph resolves to exactly the
#                                       expected JS members with zero unresolved refs.
#
# Exit 0 only when every gate passes; non-zero (with a summary) otherwise.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export COREPACK_ENABLE_DOWNLOAD_PROMPT=0

PASS=0
FAIL=0
ok()  { printf '  \033[32mPASS\033[0m %s\n' "$1"; PASS=$((PASS + 1)); }
bad() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; FAIL=$((FAIL + 1)); }

# Launcher: prefer a real `yarn` executable on PATH; else fall back to the Corepack shim
# (this dev box and CI both expose Yarn via Corepack + the pinned packageManager field).
# Resolve with `type -P` (matches executables only — NOT this same-named function, which
# `command -v` would self-detect) and bind the function once per case so `nx()` and the
# gates can call `yarn ...`/`nx ...` uniformly with no recursion.
if type -P yarn >/dev/null 2>&1; then
  yarn() { command yarn "$@"; }
else
  yarn() { corepack yarn "$@"; }
fi
nx() { yarn nx "$@"; }

# ---------------------------------------------------------------------------
echo "== Gate 1: workspace_install_clean =="
if yarn install >/tmp/wc-install.log 2>&1 && [ -f "$ROOT/yarn.lock" ]; then
  ok "yarn install exited 0 and yarn.lock is present"
else
  bad "yarn install failed or yarn.lock missing (see /tmp/wc-install.log)"
  tail -8 /tmp/wc-install.log 2>/dev/null | sed 's/^/      /'
fi

# ---------------------------------------------------------------------------
echo "== Gate 2: nx_lists_js_projects =="
PROJECTS="$(nx show projects 2>/tmp/wc-nx.log)"; rc=$?
if [ "$rc" -eq 0 ] && grep -qx 'wc-web' <<<"$PROJECTS" && grep -qx 'wc-e2e' <<<"$PROJECTS"; then
  ok "nx show projects lists wc-web and wc-e2e"
else
  bad "nx show projects did not list wc-web + wc-e2e (rc=$rc; got: $(echo "$PROJECTS" | tr '\n' ' '))"
  tail -8 /tmp/wc-nx.log 2>/dev/null | sed 's/^/      /'
fi

# ---------------------------------------------------------------------------
echo "== Gate 3: install_idempotent =="
if [ -f "$ROOT/yarn.lock" ]; then
  H1="$(shasum "$ROOT/yarn.lock" | awk '{print $1}')"
  if yarn install --immutable >/tmp/wc-immut.log 2>&1; then
    H2="$(shasum "$ROOT/yarn.lock" | awk '{print $1}')"
    if [ "$H1" = "$H2" ]; then
      ok "re-install --immutable succeeded; yarn.lock unchanged"
    else
      bad "yarn.lock hash drifted across installs ($H1 -> $H2)"
    fi
  else
    bad "yarn install --immutable failed (lockfile would change; see /tmp/wc-immut.log)"
    tail -8 /tmp/wc-immut.log 2>/dev/null | sed 's/^/      /'
  fi
else
  bad "no yarn.lock to check idempotency against (gate 1 must pass first)"
fi

# ---------------------------------------------------------------------------
echo "== Gate 4: broken_workspace_fails_fast (negative) =="
# Reframed from the brief's original premise. EMPIRICAL FINDING (flagged at Step 2.5):
# Yarn Berry does NOT fail on a *missing* workspace dir — it is silently skipped. What it
# DOES reject is a *malformed* workspace member, so we assert that real misconfiguration
# surfaces fast. Self-contained in a temp dir; never touches the repo root.
TMP="$(mktemp -d)"
mkdir -p "$TMP/packages/broken"
cat > "$TMP/package.json" <<'JSON'
{ "name": "neg-root", "private": true, "packageManager": "yarn@4.5.3", "workspaces": ["packages/*"] }
JSON
printf 'nodeLinker: node-modules\nenableTelemetry: false\n' > "$TMP/.yarnrc.yml"
printf '{ "name": "broken", this is not valid json }' > "$TMP/packages/broken/package.json"
if ( cd "$TMP" && yarn install ) >/tmp/wc-neg.log 2>&1; then
  bad "expected install to FAIL on a malformed workspace member, but it succeeded"
else
  ok "malformed workspace member fails install fast (non-zero exit)"
fi
rm -rf "$TMP"

# ---------------------------------------------------------------------------
echo "== Gate 5: workspace_membership_resolves (integration) =="
LIST_JSON="$(yarn workspaces list --json 2>/tmp/wc-list.log)"; rc=$?
# Expect exactly the root plus the two JS workspace members; apps/wc-api (Gradle, no
# package.json) must NOT appear, and there must be no unresolved references.
if [ "$rc" -eq 0 ] \
  && grep -q '"name":"wc-web"' <<<"$LIST_JSON" \
  && grep -q '"name":"wc-e2e"' <<<"$LIST_JSON" \
  && ! grep -q '"name":"wc-api"' <<<"$LIST_JSON"; then
  ok "workspace graph resolves to exactly the expected JS members (wc-web, wc-e2e; no wc-api)"
else
  bad "workspace membership unexpected (rc=$rc; got: $(echo "$LIST_JSON" | tr '\n' ' '))"
  tail -8 /tmp/wc-list.log 2>/dev/null | sed 's/^/      /'
fi

# ---------------------------------------------------------------------------
echo
echo "== Summary: ${PASS} passed, ${FAIL} failed =="
[ "$FAIL" -eq 0 ]
