# /tdd brief — Dockerfiles: wc-api + wc-sync-worker images (Wave-1 s4)

> **Build-artifact slice — NOT red-green TDD** (a Dockerfile has no unit test). Verified via: `docker build` succeeds for both, the image runs (`java -jar` boots the Spring context), `hadolint` (if available) clean, and the deploy.yml image-build step references the exact paths. The acceptance criteria + a local `docker build`/`run` smoke are the gate.

## Feature
The two container images the deploy pipeline + k8s manifests already reference but that **do not exist yet** — the standing build blocker: **`apps/wc-api/Dockerfile`** (the `wc-api` image, `:api:bootJar`) + **`apps/wc-api/worker/Dockerfile`** (the `wc-sync-worker` image, `:worker:bootJar`). Multi-stage (build → slim JRE runtime), JDK/JRE 21, non-root.

## Use case + traceability
- **Task ID:** Wave-1 slice 4 (deploy-demo; `docs/planning/025`). The LAST backend Wave-1 slice — it packages everything (datasource config from 090, actuator probes, the Flyway-migrate/generation/rebuild profiles all ride the same `wc-api` image). The deployed demo cannot build without these.
- **Architecture sections:** §12 (the §12 image contract — `wc-api` image reused by the api Deployment + migration Job + generation CronJob + the rebuild Job; `wc-sync-worker` for the worker), §13 (the CI build/push step). Appendix C.2/C.3 (the Gradle multi-module layout).
- **Related context (verified):**
  - `infra/k8s/deployment-api.yaml` / `job-migration.yaml` / `cronjob-generation.yaml` / `job-rebuild-projections.yaml` (12.14) ALL use `${ECR_API_IMAGE}` — **one `wc-api` image, profile/arg-switched** (forbidden-pattern #5: no third image). `deployment-worker.yaml` uses `${ECR_WORKER_IMAGE}`.
  - `.github/workflows/deploy.yml` — the build+push step does `docker build -f apps/wc-api/Dockerfile` + `-f apps/wc-api/worker/Dockerfile`, tags `${IMAGE_TAG}` (commit SHA), pushes to the `wc-api` + `wc-sync-worker` ECR repos. **Confirm the exact build context + `-f` paths the workflow passes** (Step-2.5).
  - Gradle multi-module: `apps/wc-api/settings.gradle` includes `shared`/`api`/`worker`; `:api:bootJar` + `:worker:bootJar` produce the Spring Boot fat jars; `:api` depends on `:shared`, `:worker` depends on `:shared` (no api↔worker edge). JDK 21 (toolchain via foojay).
  - The apps expose port 8080 (http + actuator probes). `SPRING_PROFILES_ACTIVE`/`SPRING_FLYWAY_ENABLED`/etc. are set by the k8s manifests (NOT baked into the image).

## Acceptance criteria
- [ ] NEW `apps/wc-api/Dockerfile` — multi-stage: **build stage** runs the Gradle build to produce `:api:bootJar`; **runtime stage** = a slim JRE 21 base, copies the jar, runs as a **non-root** user, `EXPOSE 8080`, `ENTRYPOINT ["java","-jar","/app/app.jar"]` (or the exploded-layers form). No profile/secret baked in (the manifests set env). `.dockerignore` to keep the context lean (exclude `build/`, `.gradle/`, `.git`).
- [ ] NEW `apps/wc-api/worker/Dockerfile` — same shape for `:worker:bootJar` → the `wc-sync-worker` image.
- [ ] **`wc-api` image is the single api-side image** (the migration Job + generation CronJob + 12.14 rebuild Job all reuse it via profile/arg — forbidden-#5; the Dockerfile must NOT be api-Deployment-specific).
- [ ] Both images **build clean** (`docker build`) and **boot** (`java -jar` starts the Spring context — locally it'll fail at the DB/secret step without a mount, which is expected; the image is valid if the boot reaches config-loading). JRE 21 (matches the toolchain). Reasonable size (slim JRE, not full JDK, in the runtime stage).
- [ ] `.github/workflows/deploy.yml` build step references resolve to these exact paths (no workflow change expected — just confirm the `-f` paths + context match; if a mismatch, **flag at Step 2.5**, don't silently rename).

## Verification (replaces RED — build-artifact path)
1. `docker build -f apps/wc-api/Dockerfile -t wc-api:local <context>` → succeeds; final image is JRE-based + non-root.
2. `docker build -f apps/wc-api/worker/Dockerfile -t wc-sync-worker:local <context>` → succeeds.
3. `docker run --rm wc-api:local` → the Spring context starts (reaches config/datasource loading; a DB/secret failure without a mount is expected + fine — proves the jar + entrypoint are valid). Same for the worker.
4. `hadolint` on both (if installed) → no high-severity findings (pinned base tags, non-root, no `apt` cache left, etc.).
5. Confirm the deploy.yml `docker build` invocation matches the file paths + context.

## Cross-doc invariant impact
- **Model field changes:** none.
- **Orchestrator doc rows (Step 9):** tick the MVP backend Wave-1 Dockerfiles entry; an Appendix C.2/C.3 note that the Dockerfiles live at `apps/wc-api/Dockerfile` + `apps/wc-api/worker/Dockerfile` (the §12 image paths) if not already pinned. Possible wc-api LESSONS entry (the multi-stage multi-module Dockerfile pattern — building one module's bootJar from the composite build).

## Things to flag at Step 2.5
1. **Build-in-Dockerfile (multi-stage) vs CI-builds-jar + COPY.** (a) Self-contained multi-stage Dockerfile (Gradle build inside) — reproducible, no CI coupling, but slower + re-downloads deps unless cached. (b) The deploy.yml `gates` job ALREADY runs the Gradle build/test — so CI could `bootJar` once + the Dockerfile just `COPY`s the jar (faster, smaller, reuses the gate build). My default vote: **(a) self-contained multi-stage** for portability (a `docker build` works standalone, not only in CI) — but if deploy.yml's structure makes (b) clean (the jar is already built in the gates job + available as an artifact), (b) is acceptable. Confirm against deploy.yml's actual build step.
2. **Base images.** Build stage: a JDK-21 image with the Gradle wrapper (e.g. `eclipse-temurin:21-jdk` + the committed `./gradlew`) vs a `gradle:8.x-jdk21` image. Runtime: `eclipse-temurin:21-jre` (simple) vs a distroless `gcr.io/distroless/java21` (smaller/hardened, but no shell). My default: **temurin:21-jdk build + temurin:21-jre runtime** (familiar, shell present for debugging); flag if you prefer distroless. **Pin by digest or at least a specific tag** (not `:latest`).
3. **Layered jars** (`bootJar` layered + `java -Djarmode=layertools extract` → COPY layers for better caching) vs a single fat-jar COPY. My default: **single fat-jar COPY** (simplest; layered is an optimization — flag if worth it).
4. **Build context.** Repo root vs `apps/wc-api/` (the Gradle composite root). The build needs the whole `apps/wc-api/` (settings + all 3 modules). Confirm what deploy.yml passes as the context + align the Dockerfile's relative paths.

## Dependencies + sequencing
- **Depends on:** 090 (the datasource config the image carries) + the actuator config (already wired — verified). Nothing else.
- **Blocks:** the deploy.yml image build → push → the entire deploy. **This is the last backend Wave-1 slice** — after it, Wave-1 is code-complete + the first `terraform apply` + deploy.yml run can proceed (gated on the HITL secret population + AWS account).

## Estimated commit count
**1** — two Dockerfiles + a `.dockerignore`. Not safety-critical (build packaging; no request-path code). No security-reviewer, though note: non-root user + pinned base + no secrets-in-image are the security hygiene to honor (rule #7 — no secrets baked into a layer).

## Lessons-logged candidates anticipated
- **Convention candidate** — the multi-stage Dockerfile over a Gradle composite multi-module build (building ONE module's `bootJar` from the composite; the single `wc-api` image serving api+migration+cron+rebuild via profile/arg). Flag if reusable.

## How to invoke
> Implementer session oriented (090 just shipped). Jump straight in.
1. Read this brief + `apps/wc-api/settings.gradle` + the `api`/`worker` `build.gradle` (the `bootJar` tasks) + the deploy.yml build step (the `-f` paths + context).
2. Author both Dockerfiles + `.dockerignore`; run the Verification path (1–4). Step-2.5 (the 4 Qs) via `SendMessage`; pause for my verdict.
3. Step-9 — categorized summary + draft commit message. `git add` only the Dockerfiles + `.dockerignore`.
