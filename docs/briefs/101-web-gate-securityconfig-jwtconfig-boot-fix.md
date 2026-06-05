# /tdd brief — web-gate SecurityConfig + JwtConfig for non-serving batch jobs (deploy domino #9) — SAFETY-ADJACENT

## Feature
Gate `SecurityConfig` AND `JwtConfig` (`apps/wc-api/api/.../config/`) on `@ConditionalOnWebApplication(type = SERVLET)` at the class level, so the **serving API** (servlet-web → always TRUE) keeps its full security chain + Auth0 fail-fast intact, while the **non-serving batch jobs** (migration / rebuild-projections / cronjob — they run the api image with `web=none` via infra #8) **exclude** both → no eager `jwtDecoder`, no `SecurityFilterChain` beans → boot clean → Flyway runs. Composes with infra #8 (`b744612`, already on main — KEEP it).

## Use case + traceability
- **Task ID:** deploy-fix-#9 (live-deploy blocker; lead-routed). **SAFETY-ADJACENT** — touches the security configuration (rule #3/#5-operational territory).
- **Architecture sections:** §6 (the security chain + Auth0 JWT resource server), §16 (rule #5 demo-auth env-gating). LESSONS **§12/§19** (the SS6 per-mode chain + the JWT decoder fail-fast), **§4** (the `web=none` one-shot batch-job pattern), **§48** (the deploy-config gap class).
- **Root cause (lead-verified, confirmed against the code):** `SecurityConfig` is `@Configuration @EnableWebSecurity @EnableMethodSecurity`. **`@EnableWebSecurity` is an explicit user annotation** that imports the servlet security infra (`HttpSecurityConfiguration`) and forces the `@Bean SecurityFilterChain` methods to build **independent of `spring.main.web-application-type`** — so infra #8's `web=none` alone did NOT stop it. In prod (`demo-auth.enabled` unset → `matchIfMissing=true`), `realModeChain` builds → wires `oauth2ResourceServer` → needs the `JwtDecoder` → `JwtConfig.jwtDecoder` (an **eager singleton**, same condition) → `requireRealModeConfig(issuer-uri)` → `IllegalStateException` (the batch jobs have no issuer-uri). Boot's OWN `SecurityAutoConfiguration`/`OAuth2ResourceServerAutoConfiguration` are `@ConditionalOnWebApplication(SERVLET)` (would skip in `web=none`) — the explicit `@EnableWebSecurity` bypasses that. **Scope:** only the api IMAGE (the 3 batch jobs run it; migration crashes now, rebuild+cronjob would next). The **worker is unaffected** (separate image, `com.st6.wc.worker` scan, depends on `:shared` not `:api`, no spring-security on its classpath; it's a web app for actuator health only, no security chain).

## Acceptance criteria
- [ ] `SecurityConfig` + `JwtConfig` each carry **class-level `@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)`**.
- [ ] **Serving path unchanged:** a servlet-web context (the api Deployment; the existing MOCK/servlet security tests) loads BOTH → `realModeChain`/`demoModeChain` + `jwtDecoder` build → full security + the Auth0 issuer/audience fail-fast (§12/§19) intact. The existing `SecurityChainDemoModeTest` / `SecurityChainRealModeTest` / `SecurityConfigGateTest` stay GREEN.
- [ ] **Batch path fixed:** a `web=none` context (aws profile, `demo-auth.enabled` unset, NO `issuer-uri`/`audience`) **loads clean** — no `JwtDecoder` bean, no `SecurityFilterChain` bean, NO `IllegalStateException` (the batch jobs no longer require Auth0). RED today.
- [ ] **JwtConfig MUST be gated too** (not just SecurityConfig) — `jwtDecoder` is an eager singleton that instantiates at startup even when no chain references it; gating only SecurityConfig would still crash on `jwtDecoder`.
- [ ] **No safety regression** — verified by the ad-hoc security-reviewer (below): the conditional cannot exclude security on ANY serving path; no batch path relies on `SecurityConfig`/`JwtConfig`/`@EnableMethodSecurity`.
- [ ] `./gradlew check` green all 3 modules + bootJar unaffected.

## Files expected to touch
**Modified:**
- `api/src/main/java/com/st6/wc/config/SecurityConfig.java` — `+@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)` (class level) + import.
- `api/src/main/java/com/st6/wc/config/JwtConfig.java` — same.

**New/extended test:**
- a batch-scenario context-loads test (the `web=none` boot) — see RED outline.

> No `:shared`/worker change (worker has no SecurityConfig). No infra change (#8 `web=none` `b744612` is already on main — this layers on top; both are needed: batch non-web AND security web-gated).

## RED test outline (Step 2)
1. **`batchContext_webNone_noAuth0_loadsClean`** (THE fix) — boot the api context with **`webEnvironment = NONE`** (or an `ApplicationContextRunner` with `web-application-type=none`), profile `aws`, `demo-auth.enabled` unset, NO `issuer-uri`/`audience` → assert the context **loads** (`hasNotFailed()` / no `IllegalStateException`), and there is **NO `JwtDecoder` bean and NO `SecurityFilterChain` bean**. **RED today** (jwtDecoder's eager `requireRealModeConfig` throws). _Why:_ pins the batch-job boot (the migration/rebuild/cronjob scenario). Reuse the `SharedPostgres` Testcontainers harness if a full `@SpringBootTest` (the datasource still loads under `web=none`); an `ApplicationContextRunner` over just `{SecurityConfig, JwtConfig}` is the lighter alternative (Step-2.5 Q2).
2. **`servletContext_realMode_loadsSecurityChainAndDecoder`** (regression) — a servlet/MOCK web context, real mode (demo-auth unset), issuer+audience set → `realModeChain` + `jwtDecoder` present (security intact on the serving path). _Why:_ proves the conditional is TRUE on the serving path — no security hole. (The existing `SecurityChainRealModeTest` may already cover this — extend/confirm rather than duplicate.)
3. **`servletContext_missingIssuer_stillFailsFast`** (safety regression) — a servlet web context, real mode, BLANK issuer → `jwtDecoder` STILL throws the `IllegalStateException` (the §12/§19 fail-fast is preserved on the serving path; the web-gate must NOT weaken the serving-path Auth0 requirement). _Why:_ confirms the fix only excludes security in non-web contexts, never relaxes it on the serving path.

> Confirm the 3 existing `config/` security tests stay green (they run web/MOCK → both configs load → unchanged).

## Things to flag at Step 2.5
1. **Gate BOTH on `@ConditionalOnWebApplication(SERVLET)`** (the lead's design — confirmed by the code: `jwtDecoder` is an independent eager singleton). Agree, or did you find a reason one is sufficient? (It isn't — flag if you disagree with evidence.)
2. **The batch-scenario test mechanism** — `@SpringBootTest(webEnvironment=NONE)` on the real app (reusing `SharedPostgres`, since the datasource still loads under `web=none`) is the most faithful; an `ApplicationContextRunner` over `{SecurityConfig, JwtConfig}` + `web-application-type=none` is lighter but less end-to-end. Pick + justify; it must prove the batch context loads WITHOUT Auth0 config AND that the security beans are absent.
3. **`@ConditionalOnWebApplication` over `@Profile`** — the lead recommends the web-app conditional (correct semantic: web-security ⟺ web-app; robust; doesn't couple to profile names — the profile-file `web-type` setting already silently failed at #8). Agree (don't use a `@Profile("!flyway-migrate")` exclude).
4. **Safety surface for the security-reviewer (flag what you verified):** the api serving context is unambiguously servlet-web (`spring-boot-starter-web` + embedded Tomcat) → the conditional is ALWAYS TRUE there; the batch jobs (web=none, SYSTEM context, no HTTP) have no endpoints to secure + don't traverse the HTTP authz path; nothing in the batch runners (`PlanShellGenerationRunner`/`ProjectionRebuildRunner`/the migration) relies on `SecurityConfig`/`JwtConfig`/`@EnableMethodSecurity` (the central `DomainAuthorizationService`, rule #3, is a separate `@Service` — NOT gated — and is invoked only on the HTTP request path, not by SYSTEM batch jobs). Report your read so the reviewer can confirm.

## Cross-doc invariant impact
- **Model changes:** none. **Orchestrator doc routing (Step 9):** a LESSONS note / **§12 or §19 addendum** — *`@EnableWebSecurity` forces the security chains to build regardless of `web-application-type`, so a one-shot batch job sharing the serving image must web-gate `SecurityConfig` AND the eager `JwtConfig` (`@ConditionalOnWebApplication(SERVLET)`), not rely on `web=none` alone.* I'll write it hot at Step 9.

## Dependencies + sequencing
- **Depends on:** infra #8 (`b744612`, `web=none` on the batch jobs — already on main; KEEP). #6/#7 (the Job boots far enough to reach the security init).
- **Blocks:** the live deploy's migration Job (→ Flyway V1–V6 + the 7-persona seed → rebuild → roll → publish → smoke). **Runway-sensitive** but **DO NOT fast-path the safety review.**

## Estimated commit count
**1.** A 2-class annotation + the batch-scenario test. **MANDATORY ad-hoc `security-reviewer`** after GREEN (lead-directed — this is rule #3/#5-operational territory; security-reviewer is allowed ad-hoc on safety slices per our policy). The reviewer confirms: no serving path can lose security (the api is always servlet-web), no batch path relies on the gated configs, the §12/§19 serving-path fail-fast is preserved. **No cycle** (small), but the security review is non-negotiable.

## How to invoke
1. **Read this brief end-to-end.**
2. Pre-flight: read `SecurityConfig.java` (the `@EnableWebSecurity` + the 2 chains) + `JwtConfig.java` (the eager `jwtDecoder` + `requireRealModeConfig`) + the 3 existing `config/` security tests.
3. **Run `/tdd web-gate-securityconfig-jwtconfig-boot-fix`.**
4. Step 0/1 → **Step 2.5** (the design + the test mechanism + your safety read for Q4; wait for my header).
5. GREEN → I run the **ad-hoc security-reviewer** → Step 9 commit-message-first → **report the hash** (the lead pushes + re-triggers). Flag the LESSONS note.
