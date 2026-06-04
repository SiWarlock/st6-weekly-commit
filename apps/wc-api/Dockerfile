# syntax=docker/dockerfile:1
# wc-api image — multi-stage build over the apps/wc-api Gradle composite. Build context = repo root
# (per .github/workflows/deploy.yml: `docker build -f apps/wc-api/Dockerfile … .`).
#
# This is the SINGLE api-side image: the api Deployment + the migration Job + the generation CronJob
# + the rebuild Job all reuse it, profile/arg-switched (forbidden-pattern #5 — no third image). So the
# image is NOT api-Deployment-specific: the entrypoint is a bare `java -jar`; the k8s manifests supply
# SPRING_PROFILES_ACTIVE / SPRING_FLYWAY_ENABLED / --app.job / the CSI secret mount. Nothing
# environment- or secret-specific is baked into any layer (rule #7).

# --- build stage: produce :api:bootJar from the self-contained composite ----------------------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
# The apps/wc-api tree is a self-contained Gradle composite (its own ./gradlew + settings.gradle +
# shared/api/worker). .dockerignore trims build/, .gradle/, and the non-backend dirs from the context.
COPY apps/wc-api/ ./
# Build only the api fat jar (no tests — the CI gate job already runs the full suite). Spring Boot
# emits both <module>.jar (bootJar, executable) and <module>-plain.jar — select the non-plain one.
RUN ./gradlew :api:bootJar --no-daemon \
    && cp "$(ls api/build/libs/*.jar | grep -v -- '-plain' | head -n1)" /build/app.jar

# --- runtime stage: slim JRE 21, non-root -----------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --chown=app:app --from=build /build/app.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
