# /tdd brief — SNS gateway empty-topic-arn boot fix (deploy domino #5b)

## Feature
Fix the aws-profile **Job** boot crash: `AwsSnsLifecycleGateway`'s `@ConditionalOnProperty("app.sns.topic-arn")` fails to resolve `${SNS_TOPIC_ARN}` (no default) in the migration / generate-plan-shells / rebuild-projections Jobs (they run `SPRING_PROFILES_ACTIVE=aws,…` for datasource/configtree but are **never given `SNS_TOPIC_ARN`** — that's API-Deployment-only). Give the property an **empty default** + switch **both** gateway beans to **length-based `@ConditionalOnExpression`** so empty/absent → the no-op stub (clean boot), non-empty → the real gateway.

## Use case + traceability
- **Task ID:** deploy-fix-#5b (live-deploy blocker; lead-routed)
- **Architecture sections:** §10 (the SNS lifecycle publish path / the §43 property-selected real-vs-stub seam), **safety rule #4** (the gateway is the single non-blocking publish seam — selection must not change the swallow semantics).
- **Related:** LESSONS **§43** (the shipped-no-op-seam → real-adapter property-selection pattern — this fix hardens its *condition* against the unresolved-placeholder boot crash) + **§42** (the `application-<profile>.yml` deploy-config + the placeholder-default discipline) + **§45** (the s9 `GraphRealModeSelectionTest`-style `ApplicationContextRunner` condition test — the test shape to mirror). **Root cause (infra-impl diagnosed):** `@ConditionalOnProperty` is evaluated at boot for EVERY context regardless of which beans activate, so an unresolvable `${SNS_TOPIC_ARN}` (no default) in the condition's property crashes any aws-profile Job that lacks the env. The fix is a **backend condition fix, NOT an infra workaround** — setting `SNS_TOPIC_ARN` on the Job manifests would wrongly activate the real SNS gateway in Jobs that lack `sns:Publish` IRSA.

## Acceptance criteria
- [ ] `application-aws.yml:41` → `topic-arn: ${SNS_TOPIC_ARN:}` (empty default — the placeholder ALWAYS resolves).
- [ ] An aws-profile context with **`app.sns.topic-arn` empty/absent boots cleanly** (no `IllegalArgumentException: Could not resolve placeholder 'SNS_TOPIC_ARN'`) and wires the **no-op `LoggingLifecycleSnsGateway`** as the active `LifecycleSnsGateway` bean.
- [ ] An aws-profile context with **`app.sns.topic-arn` set (non-empty)** wires the **real `AwsSnsLifecycleGateway`** (the API Deployment path — unchanged behavior).
- [ ] **Exactly one** `LifecycleSnsGateway` bean exists in every case (mutually-exclusive conditions — no "no bean" crash, no "two beans" conflict).
- [ ] Rule #4 unchanged — the stub is still a no-op, the real still propagates → the single `SnsLifecyclePublisher` swallow is untouched (no behavior change to the publish path; only the activation condition).
- [ ] `./gradlew check` green from `apps/wc-api/`.

## Files expected to touch
**Modified:**
- `api/src/main/resources/application-aws.yml` (line ~41) — `topic-arn: ${SNS_TOPIC_ARN:}`.
- `api/src/main/java/com/st6/wc/sns/AwsSnsLifecycleGateway.java` (line 28) — `@ConditionalOnProperty("app.sns.topic-arn")` → **`@ConditionalOnExpression("'${app.sns.topic-arn:}'.length() > 0")`**.
- `api/src/main/java/com/st6/wc/sns/LoggingLifecycleSnsGateway.java` (line 19) — `@ConditionalOnProperty(name="app.sns.topic-arn", havingValue="false", matchIfMissing=true)` → **`@ConditionalOnExpression("'${app.sns.topic-arn:}'.length() == 0")`** (the load-bearing half — see Step-2.5 Q1).
- `api/src/test/.../sns/LifecycleSnsGatewaySelectionTest.java` — extend (the empty-topic-arn boot case).

> If you need files beyond this, flag at Step 2.5. **No `:shared`/worker change** (the worker doesn't publish). If implementation reveals the base `application.yml` also carries a no-default `app.sns.topic-arn`, flag it (the default belongs where the deploy binds it).

## RED test outline (Step 2)
Mirror the §45 `ApplicationContextRunner` selection test (no live SNS):

1. **`emptyTopicArn_bootsClean_selectsStub`** (THE fix) — `withPropertyValues("app.sns.topic-arn=")` → context **boots without failure** (`assertThat(ctx).hasNotFailed()`), the active `LifecycleSnsGateway` is `LoggingLifecycleSnsGateway`, NO `AwsSnsLifecycleGateway`. _Why:_ reproduces the deploy crash scenario (Job with no `SNS_TOPIC_ARN` → empty default) + pins the clean boot.
2. **`setTopicArn_selectsRealGateway`** — `withPropertyValues("app.sns.topic-arn=arn:aws:sns:…")` → active bean is `AwsSnsLifecycleGateway`, NO stub. _Why:_ the API path unchanged.
3. **`absentTopicArn_selectsStub`** — no `app.sns.topic-arn` property at all → empty-default resolves → stub + boots. _Why:_ defense (the property genuinely absent, e.g. a non-aws profile).
4. **`exactlyOneGatewayBean`** — in both empty + set cases, exactly one `LifecycleSnsGateway` bean. _Why:_ mutual exclusion (no-bean / two-bean guard).
5. **(if cheap) `awsProfileYaml_hasEmptyDefaultOnTopicArn`** — a marker test (mirror the §42 profile-filename marker) pinning `application-aws.yml`'s `${SNS_TOPIC_ARN:}` default so a future edit dropping the `:` is caught.

> Keep/adjust whatever the existing `LifecycleSnsGatewaySelectionTest` already asserts — the present-vs-absent selection may need its "absent" expectation updated (absent now → empty-default → **stub**, and crucially **no crash**).

## Things to flag at Step 2.5
1. **Both beans MUST switch to the length-expression (the subtle part).** With the empty default, `app.sns.topic-arn` is **always present (as `""`)**, so the stub's `matchIfMissing=true` **no longer fires** (missing≠present-empty) AND `havingValue="false"` doesn't match `""` → the stub would silently STOP activating → "no `LifecycleSnsGateway` bean" boot crash. So the stub's condition must become `@ConditionalOnExpression("'${app.sns.topic-arn:}'.length() == 0")` (the exact inverse of the real). Confirm you're changing BOTH (not just the real bean per the lead's "~2 line" shorthand). _(Profile-split is an acceptable alternative if you judge it cleaner — but the length-expression pair is the most localized + keeps the §43 property-selection intent.)_
2. **Audit for other condition-referenced no-default placeholders (prevent the next domino).** Grep all `@ConditionalOnProperty`/`@ConditionalOnExpression` in `:api` (+ `:shared`) for properties bound to a no-default `${ENV}` in `application-aws.yml` that the Jobs don't provide. My read: **`app.sns.topic-arn` is the only one** (the other `application-aws.yml` `${ROOT_DOMAIN}` placeholders are in config-properties — auth0 audience / CORS — which are deploy-wide-provided + lazily bound, not always-evaluated conditions). Confirm; if you find another condition-referenced Job-absent placeholder, **flag it** (same empty-default treatment) so the deploy doesn't hit a #5c.
3. **Update tooling-comments** — the two gateways' javadocs reference the old `@ConditionalOnProperty` selection rationale; update them to the length-expression (keep the "order-independent, NOT `@ConditionalOnMissingBean`" note — it still holds).

## Cross-doc invariant impact
- **Model field changes:** none. **Orchestrator doc rows:** likely a LESSONS **§43-addendum** (the placeholder-default + length-expression hardening of the property-selection — an unresolvable `${ENV}` in a `@ConditionalOnProperty` crashes EVERY context that loads the profile, even ones where neither bean is "wanted") + a §42 nod. I'll decide hot at Step 9.

## Dependencies + sequencing
- **Depends on:** the Wave-2 s7 gateways (`82fd2ab`) + `application-aws.yml` (090/092).
- **Blocks:** the live deploy (the migration/cronjob/rebuild Jobs can't boot until this lands). **Runway-sensitive** — the lead pushes this WITH the infra #5 fix (`cdee04f`) + re-triggers the deploy ONCE.

## Estimated commit count
**1.** A ~3-line condition/config fix + the selection test. **Impl stays (no cycle** — lead-confirmed; ~71% has headroom for this). **No security-reviewer** — config/condition change, no PII/authz/payload surface; rule #4 semantics unchanged (the stub stays a no-op, the real stays propagating). Light: confirm at Step-9 that the selection is mutually exclusive + the publish path byte-unchanged.

## How to invoke
1. **Read this brief end-to-end.**
2. Pre-flight: read both gateway beans' current annotations + `application-aws.yml:40-41` + the existing `LifecycleSnsGatewaySelectionTest`.
3. **Run `/tdd sns-gateway-empty-topic-arn-boot-fix`.**
4. Step 0/1 → **Step 2.5** (send the test designs + your answers to the 3 questions — esp. Q1 the both-beans confirmation + Q2 the audit result; wait for my header).
5. Step 9 — commit-message-first; the lead pushes this with infra #5 + re-triggers. **Report the hash promptly** (runway-sensitive).
