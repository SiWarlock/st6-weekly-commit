# /tdd brief — ship `software.amazon.awssdk:sts` in the api+worker runtime classpath for IRSA (deploy domino, §48 class) — CRITICAL PATH

## Feature
Add `runtimeOnly 'software.amazon.awssdk:sts'` to **both** `apps/wc-api/api/build.gradle` and `apps/wc-api/worker/build.gradle`, and **extend the verify-gradle Gate 7** (production-runtime-drivers) to assert `sts` is in each bootJar. This is the **§48 runtime-classpath-gap class** — identical in shape to deploy-fix #6 (the postgres driver): a production-only dependency that no unit test catches, absent from the bootJar.

## Use case + traceability
- **Task ID:** deploy-fix-sts-irsa (live-deploy BLOCKER; infra-impl FINDING, lead-routed). **ON THE CRITICAL PATH** — the live Outlook sync is non-functional without it; the lead bundles this with the infra `GRAPH_MODE` flip + one `deploy.yml` re-trigger.
- **LESSONS:** §48 (the first-deploy config/packaging gap class — runtime-classpath deps absent from the bootJar), §43/§44 (the SNS gateway / SQS consumer that need AWS creds), §10 (the sync pipeline).
- **Architecture:** §10 (Outlook sync), rule #4 (non-blocking sync — note the failure mode below is the rule-#4 swallow *masking* the missing-creds error).

## Root cause (infra-impl-verified on the live cluster)
AWS SDK v2 **IRSA** auth (`WebIdentityTokenCredentialsProvider` → `AssumeRoleWithWebIdentity`) **requires the `software.amazon.awssdk:sts` module on the classpath**. The Spring Cloud AWS `spring-cloud-aws-starter-sns` (api) and `spring-cloud-aws-starter-sqs` (worker) **do NOT pull `sts` transitively**. It's declared in **neither** build file (confirmed: `api/build.gradle` has the #6 `runtimeOnly` postgres/flyway block but no `sts`; `worker/build.gradle` has `runtimeOnly postgresql` but no `sts`). Live symptom:
- **worker:** logs every ~11s `Unable to load credentials … the 'sts' service module must be on the class path` → it has **NEVER consumed an SQS message**.
- **api:** same failure at `sns:Publish` → the lock-time publish throws → the §28 rule-#4 **single swallow** catches it → the sync record sticks at `PENDING_PUBLISH` (the swallow *masks* the missing-creds root cause — that's why it surfaced as "sync silently does nothing," not a crash).

No unit/integration test catches this (the test classpath uses local creds / no real IRSA; the SDK only needs `sts` when actually assuming the web-identity role on the cluster) — exactly the §48 "production-scope gap invisible to `./gradlew check`" trap.

## Acceptance criteria
- [ ] `runtimeOnly 'software.amazon.awssdk:sts'` declared in **`apps/wc-api/api/build.gradle`** AND **`apps/wc-api/worker/build.gradle`** — **BOM-versioned, no explicit version** (the existing `io.awspring.cloud:spring-cloud-aws-dependencies` BOM imports the AWS SDK v2 BOM, which manages `software.amazon.awssdk:*`).
- [ ] **Confirm the BOM actually manages `sts`** — `./gradlew :api:dependencies` / `:worker:dependencies` shows `software.amazon.awssdk:sts` resolving a concrete version. If (unexpectedly) it does NOT, escalate at Step-2.5 — the fallback is importing the `software.amazon.awssdk:bom` explicitly or pinning the version (don't silently hardcode a version without flagging).
- [ ] **Gate 7 extended** (`scripts/verify-gradle.sh`, the `production_runtime_drivers` block ~lines 199-219): add `jar_has_lib "$API_JAR" 'sts-[0-9].*\.jar'` → `api:sts` and `jar_has_lib "$WORKER_JAR" 'sts-[0-9].*\.jar'` → `worker:sts` to the `rtmiss` checks, and update the PASS message to include `sts`. (Both deployables need it: api publishes SNS, worker consumes SQS — both assume the IRSA role.)
- [ ] `./gradlew check` green all 3 modules; `:api:bootJar` + `:worker:bootJar` both carry `sts-*.jar` in `BOOT-INF/lib/` (Gate 7 now asserts this); `scripts/verify-gradle.sh` exits 0.
- [ ] **No production code / no test-source change** — this is build-wiring + a gate extension (no `.java` touched).

## RED → GREEN (gate-driven, the §48 discipline — pin with a gate, not a unit test)
1. **RED:** extend Gate 7 FIRST (add the two `sts` `jar_has_lib` checks) → run `scripts/verify-gradle.sh` (or just rebuild the bootJars + the Gate-7 snippet) → **Gate 7 FAILS** with `bootJar(s) missing production runtime deps: api:sts worker:sts` (proves the gap — `sts` isn't in the bootJars yet).
2. **GREEN:** add the two `runtimeOnly 'software.amazon.awssdk:sts'` lines → rebuild bootJars → Gate 7 **PASS** (`sts-*.jar` now in both `BOOT-INF/lib/`).
3. **Verify:** `./gradlew check` green all 3 modules; `scripts/verify-gradle.sh` exits 0; spot-check `unzip -l` on each bootJar shows `BOOT-INF/lib/sts-<version>.jar`.

## Things to flag at Step 2.5
1. **BOM management confirmation** (the one real risk) — paste the `:api:dependencies` line showing `software.amazon.awssdk:sts -> <version>` resolved via the BOM. If it's unmanaged, flag before pinning.
2. **Gate-7 glob** — `'sts-[0-9].*\.jar'` matches `sts-2.x.y.jar`; confirm no other `BOOT-INF/lib/` artifact starts with `sts-<digit>` (it shouldn't — `sts` is the AWS SDK STS client). Keep the existing `jar_has_lib` regex idiom.
3. **Both modules, not one** — don't add `sts` only to the worker; the api's SNS publish path needs it too (it's why lock-publish silently stuck at PENDING_PUBLISH).

## Cross-doc invariant impact
- **Model changes:** none. **Orchestrator doc routing (Step 9):** a **LESSONS §48-addendum** — *AWS SDK v2 IRSA (`WebIdentityTokenCredentialsProvider`/`AssumeRoleWithWebIdentity`) needs `software.amazon.awssdk:sts` on the runtime classpath; the spring-cloud-aws sns/sqs starters don't pull it transitively → a 3rd §48 prod-runtime-classpath gap (after the pg driver + flyway). The api swallow (rule #4) masks it as "sync does nothing" rather than a crash. Pin with the Gate-7 bootJar-contents assertion.* I'll write it hot at Step 9.

## Dependencies + sequencing
- **Depends on:** nothing (the BOM is already present). **Blocks:** the live Outlook-sync demo beat ("lock → real Outlook event"). The lead bundles the hash with the infra `GRAPH_MODE` flip + one `deploy.yml` re-trigger.

## Estimated commit count
**1.** Two `runtimeOnly` lines + the Gate-7 extension. **No security-reviewer** (build-wiring, no safety/auth surface — it RESTORES intended functionality; rule #4/#7 unchanged). Conventional commit (e.g. `fix(wc-api): ship awssdk:sts in the api+worker runtime classpath for IRSA (deploy #sts)`); note it's the §48 class. **Do NOT push** — the lead bundles + re-triggers. Ping the orch the hash at done-with-slice.

## How to invoke
1. **Read this brief end-to-end.**
2. Pre-flight: confirm `sts` absent from both build files (api ~line 46-48 #6 block; worker ~line 34) + read the Gate-7 block (`scripts/verify-gradle.sh:199-219`).
3. **Run `/tdd awssdk-sts-irsa`** (or treat as a gate-driven build-fix: extend Gate 7 → RED → add runtimeOnly → GREEN → verify).
4. Step 0/1 → **Step 2.5** (the BOM-resolves-`sts` confirmation + the Gate-7 diff; wait for my header).
5. GREEN → Step 9 commit-message-first → report the hash. Flag the §48-addendum.
