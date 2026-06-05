# /tdd brief — aws-profile resolvable-defaults boot fix (deploy domino #7)

## Feature
Restore the **always-resolvable-defaults** invariant in `application-aws.yml`: give the nested `${ROOT_DOMAIN}` a safe default (`:localhost`, mirroring base `application.yml`) on **both** the auth0 audience (line 30) and the CORS allow-list (line 50), so the non-HTTP-serving Jobs (migration / generate-plan-shells / rebuild-projections — which by design don't set `ROOT_DOMAIN`) boot clean. **Systemic:** audit `application-aws.yml` + any `@Value`/`@ConfigurationProperties` bound at boot for EVERY no-default placeholder a Job context would hit, and fix them all in this one slice. Add a regression guard so the class can't recur silently.

## Use case + traceability
- **Task ID:** deploy-fix-#7 (live-deploy blocker; lead-routed). Same CLASS as #5b (`8e593f0`).
- **Architecture sections:** §12/§16 (CORS allow-list), §6 (Auth0 audience validator), Appendix D (the aws-profile deploy config). LESSONS **§43-addendum** (the placeholder-default discipline — extends it from `@ConditionalOnProperty` placeholders to ALL boot-bound placeholders) + **§42** (the deploy-profile YAML config).
- **The crash (migration Job, run 26990694077 — booted PAST #6's pg driver):** `IllegalArgumentException: Could not resolve placeholder 'ROOT_DOMAIN' in value "CORS_ALLOWED_ORIGINS:https://wc.${ROOT_DOMAIN}"`. **Root cause:** the Jobs run `SPRING_PROFILES_ACTIVE=aws` + boot the full context but are **never given `ROOT_DOMAIN`** (only `deployment-api.yaml` + `deployment-worker.yaml` set it — Jobs don't serve HTTP, by design). Base `application.yml` deliberately uses `${ROOT_DOMAIN:localhost}` (resolvable for non-serving/local contexts), but `application-aws.yml` **dropped the default** at line 30 + line 50 → the nested `${ROOT_DOMAIN}` is unresolvable in a Job context → fail-boot. (My 099 Q2 audit noted these weren't in *conditions* but wrongly assumed they were deploy-wide-provided — the Jobs don't set `ROOT_DOMAIN`; that's the gap.)

## Acceptance criteria
- [ ] `application-aws.yml` line 30 → `audience: https://api.wc.${ROOT_DOMAIN:localhost}` (resolvable default).
- [ ] `application-aws.yml` line 50 → `allowed-origins: ${CORS_ALLOWED_ORIGINS:https://wc.${ROOT_DOMAIN:localhost}}` (the nested `${ROOT_DOMAIN}` gets the default).
- [ ] An **aws-profile context with NO env set boots cleanly** (no `Could not resolve placeholder` for ANY placeholder) — the Job-context scenario.
- [ ] **Production unchanged:** the api/worker Deployments inject the real `ROOT_DOMAIN` env → the audience + CORS resolve to the real domain (the `:localhost` default only applies in the non-serving Job contexts).
- [ ] **Systemic — no #7b:** confirmed there is NO other no-default placeholder in `application-aws.yml` OR in any `@Value`/`@ConfigurationProperties` bound at boot that a Job context (no HTTP-serving env, configtree-mounted secrets only) would hit. (My pre-read: `${ROOT_DOMAIN}` ×2 are the only no-default yaml placeholders — SNS got its `:` default at #5b, the rest bind from the configtree mount. Confirm + extend to `@Value`.)
- [ ] A regression guard exists (Step-2.5 Q3).
- [ ] `./gradlew check` green + bootJar unaffected.

## Files expected to touch
**Modified:**
- `api/src/main/resources/application-aws.yml` (lines 30 + 50) — the two `${ROOT_DOMAIN:localhost}` defaults.
- A regression test (Step-2.5 Q3) — likely a new/extended profile-property-resolution test (the §4 `ApplicationContextRunner` + `ConfigDataApplicationContextInitializer` pattern) OR a `verify-gradle.sh` yaml-scan gate.

> If the systemic audit finds an `@Value`/`@ConfigurationProperties` no-default placeholder a Job hits, fix it (a `:default` matching base's intent) + flag. **No infra change** (app-side mirrors the base intent; the alternative — `ROOT_DOMAIN` env on the Job manifests — is the rejected workaround: the Jobs don't serve HTTP so they genuinely don't need it).

## RED test outline (Step 2)
1. **`awsProfile_noEnv_contextResolvesAllPlaceholders`** (THE fix) — load the `aws` profile property sources (the §4 `ApplicationContextRunner` + `ConfigDataApplicationContextInitializer.of("aws")` pattern, or a focused `Binder`/`PropertySourcesPlaceholderConfigurer` test) with **no `ROOT_DOMAIN`/`CORS_ALLOWED_ORIGINS` env** → assert `app.cors.allowed-origins` + `auth0.audience` resolve (to the `localhost` default) WITHOUT a `Could not resolve placeholder` failure. **RED today** (line 50/30 crash). _Why:_ reproduces + pins the Job-context boot.
2. **`awsProfile_withRootDomain_resolvesRealDomain`** — same with `ROOT_DOMAIN=wc.example.com` set → audience/CORS resolve to the real domain (production path unchanged). _Why:_ the default doesn't override the injected env.
3. **(if a yaml-scan gate instead/also) `awsYaml_noBareRootDomainPlaceholder`** — a §42-style marker asserting `application-aws.yml` has no bare `${ROOT_DOMAIN}` without a `:default` (catches a future drop). _Why:_ durable class-guard.

> Use whatever loads the real `application-aws.yml` resource (not hand-rolled property maps) so the test actually exercises the shipped file (§4/§42 precedent).

## Things to flag at Step 2.5
1. **Default value = `:localhost`** (exact mirror of base `application.yml`'s `${ROOT_DOMAIN:localhost}`). The Jobs don't serve HTTP, so the CORS/audience values are unused there → `localhost` is harmless + consistent. Agree, or a different sentinel?
2. **Systemic audit result — report it.** Confirm the 2 `${ROOT_DOMAIN}` (lines 30, 50) are the ONLY no-default placeholders in `application-aws.yml` (my pre-read says yes). Then grep `@Value`/`@ConfigurationProperties` across `:api` (+ `:shared`) for any no-default `${ENV}` bound at boot that a Job context lacks (the configtree-mounted datasource/issuer-uri/audience are fine — they bind from `/mnt/secrets`, not env; the s9 Graph creds are presence-checked, not crash-on-absent). Report what you found (ideally "only the 2 yaml ROOT_DOMAIN").
3. **Regression guard mechanism — your pick:** (a) the profile-property-resolution test (#1 above — catches the whole class at the binding layer, my lean) or (b) a `verify-gradle.sh` gate grepping `application-aws.yml` for bare no-default `${...}` placeholders. Pick whichever is cleaner + actually exercises the shipped yaml. (a) is more robust (catches `@Value` too if you boot a slice); (b) is simplest for the yaml class.

## Cross-doc invariant impact
- **Model changes:** none. **Orchestrator doc routing (Step 9):** a LESSONS note — *the aws (deploy) profile must carry the same resolvable-defaults invariant as base `application.yml` for non-serving Job contexts; a no-default `${ENV}` in a profile YAML (or a boot-bound `@Value`) crashes any Job context that doesn't provide that env (Jobs run the profile but not the HTTP-serving env set).* I'll write it hot (likely a **§43-addendum extension** or a new short lesson) at Step 9.

## Dependencies + sequencing
- **Depends on:** #6 (`1910cbe` — the pg driver, so the Job boots far enough to hit this).
- **Blocks:** the live deploy's migration/cronjob/rebuild Jobs. **Runway-sensitive** — the lead pushes + re-triggers on your hash.

## Estimated commit count
**1.** A 2-line YAML default + a regression test. **No security-reviewer** (config defaults; the prod CORS/audience are unchanged — the real env still wins; `localhost` only in non-serving Jobs where CORS/audience are unused; no widening — base already does this). Impl stays (no cycle — small).

## How to invoke
1. **Read this brief end-to-end.**
2. Pre-flight: read `application-aws.yml` (lines 26-50) + base `application.yml`'s `${ROOT_DOMAIN:localhost}` pattern + the existing profile-property tests (§4).
3. **Run `/tdd aws-profile-rootdomain-default-boot-fix`.**
4. Step 0/1 → **Step 2.5** (report the systemic audit result + the regression-guard pick + the test designs; wait for my header — I'll cross-check the audit completeness).
5. Step 9 — commit-message-first; **report the hash promptly** (the lead pushes + re-triggers). Flag the LESSONS note.
