#!/usr/bin/env bash
#
# verify-gradle.sh — executable verification gates for the wc-api Gradle multi-module
# build skeleton (task 0.2).
#
# Like 0.1, most of this slice is build wiring, so the "tests" are re-runnable gates that
# assert on `./gradlew` behavior. The one genuinely test-shaped item is the module-boundary
# proof (gate 5), implemented as a Gradle verification task bound to `check`.
#
# Gates:
#   1. gradle_check_green_all_gates  — repo-root `./gradlew check` exits 0 (composite drives
#                                       the backend; Spotless + SpotBugs + JaCoCo all active).
#  1b. per_module_gate_aggregation   — root `check --dry-run` schedules all 9 gate tasks
#                                       (spotless/spotbugs/jacoco x shared/api/worker), so the
#                                       composite aggregate can't silently omit a module.
#   2. jacoco_sub80_fails            — (negative) an uncovered class drops :shared below 80%,
#                                       failing jacocoTestCoverageVerification.
#   3. forbidden_pattern_fails       — (negative) a Spotless violation AND a Lombok @Data
#                                       usage each fail their check gate.
#   4. shared_resolved_by_api_worker — (integration) :api and :worker compileClasspath both
#                                       contain project :shared.
#   5. module_boundary_no_edge       — (the real test) :api/:worker checkModuleBoundaries pass
#                                       on the correct graph, and FAIL when an api<->worker
#                                       edge is introduced.
#
# Exit 0 only when every gate passes. Negative gates create clearly-named throwaway files
# (VerifyTmp*) and/or back up a build file, and restore via an EXIT trap.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WCAPI="$ROOT/apps/wc-api"
cd "$ROOT"

PASS=0
FAIL=0
ok()  { printf '  \033[32mPASS\033[0m %s\n' "$1"; PASS=$((PASS + 1)); }
bad() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; FAIL=$((FAIL + 1)); }

# Throwaway artifacts the negative gates create; the trap guarantees cleanup even on abort.
SHARED_SRC="$WCAPI/shared/src/main/java/com/st6/wc"
TMP_COVGAP="$SHARED_SRC/VerifyTmpCoverageGap.java"
TMP_BADFMT="$SHARED_SRC/VerifyTmpBadFormat.java"
TMP_USESDATA="$SHARED_SRC/VerifyTmpUsesData.java"
API_BUILD="$WCAPI/api/build.gradle"
API_BUILD_BAK="$(mktemp)"
[ -f "$API_BUILD" ] && cp "$API_BUILD" "$API_BUILD_BAK"
cleanup() {
  rm -f "$TMP_COVGAP" "$TMP_BADFMT" "$TMP_USESDATA"
  [ -s "$API_BUILD_BAK" ] && cp "$API_BUILD_BAK" "$API_BUILD"
  rm -f "$API_BUILD_BAK"
}
trap cleanup EXIT INT TERM

# Run the wc-api build directly (it carries its own wrapper for independent builds).
wcapi_gw() { ( cd "$WCAPI" && ./gradlew "$@" ); }

# ---------------------------------------------------------------------------
echo "== Gate 1: gradle_check_green_all_gates =="
if [ -x "$ROOT/gradlew" ] && ./gradlew check --console=plain >/tmp/wc-gradle-check.log 2>&1; then
  ok "repo-root ./gradlew check exited 0 (composite + all three gates)"
else
  bad "repo-root ./gradlew check failed or no root wrapper (see /tmp/wc-gradle-check.log)"
  tail -15 /tmp/wc-gradle-check.log 2>/dev/null | sed 's/^/      /'
fi

# ---------------------------------------------------------------------------
echo "== Gate 1b: per_module_gate_aggregation =="
# Guards the composite "green-but-hollow" mode: REQ §13 needs Spotless + SpotBugs + JaCoCo
# enforced per Gradle module across shared/api/worker. If a module forgot a gate plugin, root
# `check` could still pass while silently skipping it. Assert the resolved task graph (dry-run)
# from the repo root schedules all 9 (gate x module) tasks, however the composite addresses them.
DRY="$(./gradlew check --dry-run --console=plain 2>/tmp/wc-dryrun.log)"
missing=""
for m in shared api worker; do
  for t in spotlessJavaCheck spotbugsMain jacocoTestCoverageVerification; do
    grep -Eq ":${m}:${t}([[:space:]]|\$)" <<<"$DRY" || missing="${missing} ${m}:${t}"
  done
done
if [ -z "${missing}" ]; then
  ok "root check --dry-run schedules all 9 gate tasks (spotless/spotbugs/jacoco x shared/api/worker)"
else
  bad "root check is missing per-module gate tasks:${missing}"
  tail -25 /tmp/wc-dryrun.log 2>/dev/null | sed 's/^/      /'
fi

# ---------------------------------------------------------------------------
echo "== Gate 2: jacoco_sub80_fails (negative) =="
cat > "$TMP_COVGAP" 2>/dev/null <<'JAVA'
package com.st6.wc;

/** Throwaway: an uncovered class to prove the JaCoCo >=80% gate fires. Deleted by the gate. */
final class VerifyTmpCoverageGap {
  String uncovered() {
    return "no test covers this";
  }

  String branchUncovered(boolean flag) {
    if (flag) {
      return "a";
    }
    return "b";
  }
}
JAVA
if [ ! -f "$TMP_COVGAP" ]; then
  bad "gate_2 setup failed: could not create $TMP_COVGAP (build skeleton not present yet?)"
elif wcapi_gw :shared:jacocoTestCoverageVerification --console=plain >/tmp/wc-jacoco.log 2>&1; then
  bad "expected :shared coverage verification to FAIL below 80%, but it passed"
else
  ok "uncovered class drops :shared below 80% and fails jacocoTestCoverageVerification"
fi
rm -f "$TMP_COVGAP"

# ---------------------------------------------------------------------------
echo "== Gate 3: forbidden_pattern_fails (negative) =="
# 3a — Spotless formatting violation fails spotlessCheck.
printf 'package com.st6.wc;class VerifyTmpBadFormat{String x(){return    "bad";}}\n' > "$TMP_BADFMT" 2>/dev/null
if [ ! -f "$TMP_BADFMT" ]; then
  bad "gate_3a setup failed: could not create $TMP_BADFMT (build skeleton not present yet?)"
elif wcapi_gw :shared:spotlessJavaCheck --console=plain >/tmp/wc-spotless.log 2>&1; then
  bad "expected Spotless to FAIL on a formatting violation, but it passed"
else
  ok "Spotless violation fails spotlessJavaCheck"
fi
rm -f "$TMP_BADFMT"
# 3b — Lombok @Data usage fails the forbidLombokData gate (root CLAUDE.md forbidden pattern).
cat > "$TMP_USESDATA" 2>/dev/null <<'JAVA'
package com.st6.wc;

import lombok.Data;

@Data
class VerifyTmpUsesData {
  private String field;
}
JAVA
if [ ! -f "$TMP_USESDATA" ]; then
  bad "gate_3b setup failed: could not create $TMP_USESDATA (build skeleton not present yet?)"
elif wcapi_gw :shared:forbidLombokData --console=plain >/tmp/wc-data.log 2>&1; then
  bad "expected forbidLombokData to FAIL on @Data usage, but it passed"
else
  ok "Lombok @Data usage fails forbidLombokData"
fi
rm -f "$TMP_USESDATA"

# ---------------------------------------------------------------------------
echo "== Gate 4: shared_resolved_by_api_worker (integration) =="
API_DEPS="$(wcapi_gw -q :api:dependencies --configuration compileClasspath --console=plain 2>/tmp/wc-apideps.log)"
WORKER_DEPS="$(wcapi_gw -q :worker:dependencies --configuration compileClasspath --console=plain 2>/tmp/wc-workerdeps.log)"
if grep -q 'project :shared' <<<"$API_DEPS" && grep -q 'project :shared' <<<"$WORKER_DEPS"; then
  ok ":api and :worker compileClasspath both contain project :shared"
else
  bad ":api/:worker do not both resolve project :shared"
  echo "$API_DEPS" | grep -i shared | sed 's/^/      api: /'
  echo "$WORKER_DEPS" | grep -i shared | sed 's/^/      worker: /'
fi

# ---------------------------------------------------------------------------
echo "== Gate 5: module_boundary_no_edge (the real test) =="
# 5a — positive: the boundary task passes on the correct (edge-free) graph.
if wcapi_gw :api:checkModuleBoundaries :worker:checkModuleBoundaries --console=plain >/tmp/wc-boundary-pos.log 2>&1; then
  ok "checkModuleBoundaries passes on the edge-free graph (shared<-api, shared<-worker)"
else
  bad "checkModuleBoundaries failed on the correct graph (see /tmp/wc-boundary-pos.log)"
  tail -10 /tmp/wc-boundary-pos.log 2>/dev/null | sed 's/^/      /'
fi
# 5b — negative: introduce an api->worker edge; the boundary task must fail.
if [ -f "$API_BUILD" ]; then
  printf '\n// VerifyTmp edge — removed by verify-gradle.sh trap\ndependencies { implementation project(":worker") }\n' >> "$API_BUILD"
  if wcapi_gw :api:checkModuleBoundaries --console=plain >/tmp/wc-boundary-neg.log 2>&1; then
    bad "expected checkModuleBoundaries to FAIL on an api->worker edge, but it passed"
  else
    ok "an api->worker edge fails checkModuleBoundaries (REQ-O-016 enforced)"
  fi
  cp "$API_BUILD_BAK" "$API_BUILD"
else
  bad "apps/wc-api/api/build.gradle missing — cannot run the boundary negative test"
fi

# ---------------------------------------------------------------------------
echo
echo "== Summary: ${PASS} passed, ${FAIL} failed =="
[ "$FAIL" -eq 0 ]
