# syntax=docker/dockerfile:1.7
# MVX Nexus backend. Builds from source: no Java or Maven needed on your machine.
#   docker compose up -d --build
# (CI that already ran `mvn package` can use Dockerfile.prebuilt instead.)
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
# Dependencies are cached between builds, so only the first build downloads them.
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests dependency:go-offline || true
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

# Runtime image.
# CLIs for the Intelligence toolsets: kubectl, docker/compose (through a read-only proxy), aws.
# Runtimes for MCP stdio servers: node/npx and uv/uvx, plus git.
FROM eclipse-temurin:25-jre-alpine

ARG INSTALL_AWS_CLI=true
# azure-cli is ~1 GB; only build it in when the azure toolset is used.
ARG INSTALL_AZURE_CLI=false

COPY --from=ghcr.io/astral-sh/uv:0.9.5 /uv /uvx /usr/local/bin/

RUN apk add --no-cache curl tzdata ca-certificates git nodejs npm docker-cli docker-cli-compose kubectl \
 && if [ "$INSTALL_AWS_CLI" = "true" ]; then apk add --no-cache aws-cli; fi \
 && if [ "$INSTALL_AZURE_CLI" = "true" ]; then \
      apk add --no-cache python3 py3-pip gcc musl-dev python3-dev libffi-dev openssl-dev cargo make \
      && python3 -m venv /opt/az && /opt/az/bin/pip install --no-cache-dir azure-cli \
      && ln -s /opt/az/bin/az /usr/local/bin/az; \
    fi \
 && addgroup -S holmes && adduser -S -G holmes -h /home/holmes holmes \
 && mkdir -p /data/workspaces /app/config /home/holmes/.kube /home/holmes/.npm /home/holmes/.cache/uv \
 && chown -R holmes:holmes /data /app /home/holmes

WORKDIR /app
COPY --from=build /src/target/mvx-nexus.jar app.jar

USER holmes
ENV TZ=UTC \
    HOME=/home/holmes \
    HOLMES_WORKSPACE_DIR=/data/workspaces \
    HOLMES_CONFIG_FILE=/app/config/holmes.yml \
    NPM_CONFIG_CACHE=/home/holmes/.npm \
    UV_CACHE_DIR=/home/holmes/.cache/uv \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
