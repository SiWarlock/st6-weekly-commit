# syntax=docker/dockerfile:1
# wc-sync-worker image — multi-stage build over the apps/wc-api Gradle composite. Build context =
# repo root (per .github/workflows/deploy.yml: `docker build -f apps/wc-api/worker/Dockerfile … .`).
#
# The worker is a separate Spring Boot deployable (:worker, web+actuator only — the servlet server
# exists solely for the k8s probes). Env (SPRING_PROFILES_ACTIVE / the CSI graph-secret mount) is set
# by deployment-worker.yaml — never baked in (rule #7).

# --- build stage: produce :worker:bootJar from the self-contained composite -------------------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY apps/wc-api/ ./
# Build only the worker fat jar (the CI gate runs the suite). Select the non-plain bootJar.
RUN ./gradlew :worker:bootJar --no-daemon \
    && cp "$(ls worker/build/libs/*.jar | grep -v -- '-plain' | head -n1)" /build/app.jar

# --- runtime stage: slim JRE 21, non-root -----------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --chown=app:app --from=build /build/app.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
