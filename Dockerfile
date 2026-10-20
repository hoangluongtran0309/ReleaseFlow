# syntax=docker/dockerfile:1@sha256:ecfaec9ed6d810b56388c508f4121597bfbba70d41a6dfeee4d8cad5f295fc32
#
# Single-process ReleaseFlow image used by docker-compose.demo.yml and CI.
# Orchestration, TLS termination, and secret management remain deployment
# responsibilities. The health check asks the management port, which reports the
# database too; that port is private and must never be published.

# ---- Build stage ------------------------------------------------------------
FROM eclipse-temurin:21-jdk@sha256:23cc5734ff46e776f51ce66eb9ee08444fd47caabec0a95b7eb6e94be2d3696d AS build
WORKDIR /app

# Resolve Maven dependencies before copying sources so source-only changes
# reuse this layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B --no-transfer-progress dependency:go-offline

# frontend-maven-plugin runs the Tailwind build during `package`. The licence and
# notice are copied into the JAR's META-INF, so the build needs them here too.
COPY package.json package-lock.json postcss.config.cjs LICENSE NOTICE ./
COPY src/ src/
# The Release workflow passes the version from its tag; any other build keeps the POM's
# default. The JAR records it in its manifest.
ARG RELEASEFLOW_VERSION=0.0.0-SNAPSHOT
RUN ./mvnw -B --no-transfer-progress clean package -DskipTests -Prelease \
    -Drevision="${RELEASEFLOW_VERSION}"

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre-ubi10-minimal@sha256:11fefe6ccb04bfee5f342b99c7a06c6140f26b0656d04a2cbc61dec7f9a36778 AS runtime
ARG RELEASEFLOW_VERSION=0.0.0-SNAPSHOT
# The commit the image was built from. Without it, the base image's own revision label
# would be inherited and name a commit of somebody else's repository.
ARG RELEASEFLOW_REVISION=unknown
# The source label is what links the GHCR package to its repository.
LABEL org.opencontainers.image.title="ReleaseFlow" \
      org.opencontainers.image.version="${RELEASEFLOW_VERSION}" \
      org.opencontainers.image.revision="${RELEASEFLOW_REVISION}" \
      org.opencontainers.image.source="https://github.com/hoangluongtran0309/ReleaseFlow" \
      org.opencontainers.image.licenses="Apache-2.0"
WORKDIR /app

COPY --from=build --chown=65534:65534 /app/target/releaseflow.jar app.jar
USER 65534:65534

# 8080 serves the product. 8081 is the management port: declared so an operator can
# see it, and deliberately never published by the Compose stack.
EXPOSE 8080 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["curl", "--fail", "--silent", "--show-error", "--output", "/dev/null", "http://127.0.0.1:8081/actuator/health"]
ENTRYPOINT ["java", "-jar", "app.jar"]
