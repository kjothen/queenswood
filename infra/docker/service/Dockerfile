# syntax=docker/dockerfile:1.7

# Multi-stage build for any Queenswood service uberjar.
# Pass PROJECT_NAME (e.g. bank-api-service, bank-cash-account-service)
# at build time:
#   docker buildx build \
#     --build-arg PROJECT_NAME=bank-api-service \
#     -t ghcr.io/kjothen/bank-api-service:0.0.0 \
#     -f infra/docker/service/Dockerfile \
#     --platform linux/amd64,linux/arm64 \
#     .

ARG PROJECT_NAME

FROM clojure:temurin-21-tools-deps-bookworm AS build
ARG PROJECT_NAME
WORKDIR /workspace
COPY . .
RUN cd "projects/${PROJECT_NAME}" \
 && clojure -X:deps prep :aliases '[:+bank :dev]' :force true \
 || true
RUN cd "projects/${PROJECT_NAME}" \
 && clojure -X:build uber :snapshot true \
 && mv target/*.jar target/app.jar

FROM eclipse-temurin:21-jre-noble AS runtime
ARG PROJECT_NAME
ARG TARGETARCH
ARG FDB_VERSION=7.3.75

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates dnsutils \
 && rm -rf /var/lib/apt/lists/*

# libfdb_c is required by the FDB Java client at runtime; arch-aware
# download from the upstream release.
RUN ARCH="$(case ${TARGETARCH:-amd64} in \
              amd64|x86_64) echo x86_64 ;; \
              arm64|aarch64) echo aarch64 ;; \
              *) echo "unknown arch: ${TARGETARCH}" >&2; exit 1 ;; \
            esac)" \
 && curl -fsSL \
      "https://github.com/apple/foundationdb/releases/download/${FDB_VERSION}/libfdb_c.${ARCH}.so" \
      -o /usr/lib/libfdb_c.so \
 && ln -sf /usr/lib/libfdb_c.so /usr/lib/libfdb_c_${FDB_VERSION}.so

ENV FDB_CLUSTER_FILE=/etc/fdb/fdb.cluster
ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError"

WORKDIR /app
COPY --from=build /workspace/projects/${PROJECT_NAME}/target/app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar", "-c", "classpath:application.yml", "-p", "default"]
