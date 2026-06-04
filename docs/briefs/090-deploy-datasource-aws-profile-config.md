# /tdd brief — Datasource + JPA deploy config (configtree) + the `aws` profile fix (Wave-1 s3)

## Feature
Wire the **deployed `aws` profile** so the api boots against RDS: add `spring.config.import=optional:configtree:/mnt/secrets/` (binds the CSI-mounted `spring.datasource.*` + auth0 + graph keys), `spring.jpa.hibernate.ddl-auto=validate`, and a Hikari pool — **AND fix a profile-name bug**: all k8s workloads run `SPRING_PROFILES_ACTIVE=aws`, but the deploy config lives in `application-prod.yml` (profile `prod`), so it **never loads on the deployed stack**. No `spring.datasource` exists in any profile today (the architecture deferred it to "later phases" — that's now).

## Use case + traceability
- **Task ID:** Wave-1 slice 3 (deploy-demo; `docs/planning/025`). The deployed api **cannot boot** without this (no datasource → no DB; wrong profile → no auth0/CORS). Pairs with the V5/V6 seed (the db it connects to) + the infra secrets (12.13, which populates the auth0 values the same mount reads).
- **Architecture sections:** §12 (Secrets Manager + CSI mount), Appendix D.1/D.2 (the env/secret config contract — **D.2 currently says `file:`, must reconcile to `configtree:`**), §6 (the OAuth resource-server config the same mount feeds), LESSONS §9 (the `@DataJpaTest` PG16+Flyway+`ddl-auto=validate` fidelity harness), §29 (Hikari pool-cap in tests).
- **Related context (verified):**
  - `infra/k8s/deployment-api.yaml:40` → `SPRING_PROFILES_ACTIVE=aws`; `:41` `SPRING_FLYWAY_ENABLED=false`; mounts `wc-api-secrets` SPC at `/mnt/secrets`. The migration Job + cronjob also use `aws,*`.
  - `infra/k8s/secretproviderclass.yaml` (`wc-api-secrets`) → mounts each key as a file named by the key: `/mnt/secrets/spring.datasource.url`, `…username`, `…password`, `…/spring.security.oauth2.resourceserver.jwt.issuer-uri`, `…/auth0.audience`, `…/GRAPH_*`. So `configtree:/mnt/secrets/` binds them all **directly** (the filename IS the property key — Appendix D / the SPC's own comment says exactly this).
  - `secrets.tf` → the `db` secret JSON keys ARE `spring.datasource.url/username/password` (configtree auto-binds the datasource); the `auth0` keys are the full property paths `spring.security.oauth2.resourceserver.jwt.issuer-uri` + `auth0.audience`.
  - `application.yml` (base) → `spring.flyway.enabled=false`, `issuer-uri: ${AUTH0_ISSUER_URI:}`, `auth0.audience: ${AUTH0_AUDIENCE:}`, demo-auth gate. `application-prod.yml` → `auth0.audience: https://api.wc.${ROOT_DOMAIN}`, CORS. **No `spring.datasource` anywhere.**

## ⚠️ Finding to confirm + fix (Step 2.5)
**The deploy config file (`application-prod.yml`) does not load under the deploy profile (`aws`).** Spring loads `application-<profile>.yml`; with `SPRING_PROFILES_ACTIVE=aws` it loads `application-aws.yml` (absent), NOT `application-prod.yml`. **First verify** there's no `spring.profiles.group: {aws: [prod]}` (or `spring.config.activate.on-profile`) in `application.yml` that bridges them. If none (expected), **the deployed api today gets NO `auth0.audience`/CORS** (only the base `${…:}` env fallbacks). **Fix:** rename `application-prod.yml` → `application-aws.yml` (align to the `aws` profile every workload uses) and put the datasource config there. (Alternative — a `spring.profiles.group` mapping — is more indirection; recommend the rename.)

## Acceptance criteria
- [ ] **Profile fix:** `application-prod.yml` → `application-aws.yml` (verified to load under `SPRING_PROFILES_ACTIVE=aws`); its existing `auth0.audience`/CORS content preserved.
- [ ] **Datasource via configtree:** add `spring.config.import=optional:configtree:/mnt/secrets/` to the aws profile — binds `spring.datasource.url/username/password` from the mount; the HikariDataSource auto-configures. `optional:` so local/demo (no mount) still boots.
- [ ] **`spring.jpa.hibernate.ddl-auto=validate`** in the aws profile — the migration Job owns the schema (forbidden-#3); the app validates entities↔schema at boot, fail-fast on drift. (Base/local/demo keep their current ddl-auto, likely `validate` already via the §9 harness — confirm no regression.)
- [ ] **Hikari pool** tuned for prod (e.g. `maximum-pool-size: 10`, sensible `connection-timeout`) in the aws profile — NOT the test cap of 2 (§29 is a test-only concern).
- [ ] **Auth0 reconciliation:** with configtree binding `spring.security.oauth2.resourceserver.jwt.issuer-uri` + `auth0.audience` directly from the mount (higher precedence than the base `${…:}` defaults), confirm the resource-server resolves the real issuer/audience in aws (see Step-2.5 Q2 on whether to drop the now-redundant literals).
- [ ] Tests (deterministic): a profile-property-resolution test (mirror `FlywayProfilePropertyTest`) — aws resolves `spring.config.import`=`…configtree:/mnt/secrets/` + `ddl-auto=validate` + the Hikari size; local/demo do not. + reuse the **§9 `@DataJpaTest` fidelity harness** to prove `ddl-auto=validate` passes against the V1–V3 Flyway schema (entities validate). `./gradlew check` clean.

## Files expected to touch
**Renamed:** `apps/wc-api/api/src/main/resources/application-prod.yml` → `application-aws.yml` (+ any test/code reference to the `prod` profile name — grep).
**Modified:** the renamed `application-aws.yml` (+ datasource/configtree/ddl-auto/Hikari). Possibly `application.yml` (only if a `spring.profiles.group` is the chosen path — NOT recommended).
**Tests:** a new/extended profile-property test; reuse the §9 fidelity harness.
**Flag at Step 2.5** if the worker also needs datasource now (recommend: NO — worker datasource rides Wave-2 when its SQS consumer uses the DB).

## RED test outline (Step 2)
1. **`awsProfile_importsSecretsConfigtree`** — under `aws`, `spring.config.import` resolves to include `configtree:/mnt/secrets/` (+ `ddl-auto=validate`, Hikari size); under base/local/demo it does not.
2. **`awsProfileFileLoadsUnderAwsProfile`** — the renamed `application-aws.yml`'s marker property (e.g. `auth0.audience` literal or a sentinel) resolves under `SPRING_PROFILES_ACTIVE=aws` (pins the profile-name fix — would FAIL today with `application-prod.yml`).
3. **`entitiesValidateAgainstFlywaySchema`** — the §9 `@DataJpaTest` PG16+Flyway+`ddl-auto=validate` boots clean (no entity↔DDL drift) — the datasource config is exercised end-to-end.

## Cross-doc invariant impact (orchestrator writes at Step 9)
- **Architecture:** **Appendix D.2 (line ~928) + C.7 (line ~909) `file:`→`configtree:` reconciliation** — the long-parked cross-team carry-forward (MVP:1555); I write BOTH together (avoid a half-edit). A D.1 note that the deploy profile is `aws` (config in `application-aws.yml`). I also log the profile-name finding.
- **Model field changes:** none.

## Things to flag at Step 2.5
1. **Profile fix mechanism** — rename `application-prod.yml`→`application-aws.yml` (my vote — matches all workloads, simplest) vs a `spring.profiles.group: {aws: [prod]}` in `application.yml` (more indirection). Confirm there's no existing bridge first.
2. **Auth0 literal vs configtree** — the aws profile's `auth0.audience: https://api.wc.${ROOT_DOMAIN}` is now ALSO provided by the mount (`auth0.audience` key, HITL-populated to the Auth0 API identifier). configtree (higher precedence) overrides the literal. My vote: **keep the literal as a sane default + let configtree override** (so the app boots with a correct audience even pre-secret-population), OR drop it to make the mount the single source. Confirm which; same question for `issuer-uri` (no literal today, only `${AUTH0_ISSUER_URI:}` — the mount provides it in aws).
3. **`ddl-auto` in aws** — `validate` (my vote — schema owned by the migration Job; fail-fast on drift) vs `none`. Never `update`/`create` (forbidden — the migration Job is the sole schema owner).
4. **Worker datasource now or Wave-2** — my vote: **Wave-2** (the worker is a skeleton; its db use lands with the SQS consumer). This slice = api only.

## Dependencies + sequencing
- **Depends on:** the SPC mount contract (12.8, done) + the db secret (12.6, done). The actual DB connection is HITL (real RDS at deploy).
- **Blocks:** the deployed api booting → the entire deploy. **⚠️ Sibling prerequisite (NOT this slice):** the **actuator liveness/readiness** endpoints (`deployment-api.yaml` probes `/actuator/health/{liveness,readiness}`) are **inert until the image ships them (task 13.3)** — without them the pod never becomes Ready and the roll fails. I'm tracking 13.3 as a separate Wave-1 prerequisite (flagging to the lead).
- **Pairs with:** 12.13 (secrets-population — fills the auth0 values this mount reads).

## Estimated commit count
**1** — the profile rename + the aws-profile datasource/configtree/Hikari config + its tests. Not safety-critical (config wiring; no request-path logic change — the auth0 resolution path is unchanged, just fed from the mount). No security-reviewer (no secret handling in code — the values are CSI-mounted; rule #7 is the infra mount's concern).

## Lessons-logged candidates anticipated
- **Convention candidate** — the `configtree:/mnt/secrets/` deploy-secret binding (filename==property-key; one import binds db+auth0+graph; `optional:` for local) + the profile-name-must-match-the-active-profile gotcha. Likely a wc-api LESSONS entry.
- **Architecture-doc note** — the D.2 `file:`→`configtree:` reconciliation (I write it).

## How to invoke
> Implementer session oriented (V6 just shipped). Jump straight in.
1. Read this brief + `application.yml`/`application-prod.yml` + `secretproviderclass.yaml` (`wc-api-secrets`) + `deployment-api.yaml` + LESSONS §9/§29.
2. **Verify the profile finding first** (grep for `spring.profiles.group`/`on-profile`); confirm the fix at Step-2.5.
3. `/tdd deploy-datasource-config`; Step-2.5 (the 4 Qs + the finding) via `SendMessage`; pause for my verdict.
4. Step-9 — I write the D.2/C.7 `file:`→`configtree:` reconciliation + the profile-finding log. `git add` only your config + test files (incl. the rename).
