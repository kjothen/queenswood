# syntax=docker/dockerfile:1.7

# Multi-stage build for any Queenswood service uberjar.
# Pass PROJECT_NAME (e.g. bank-api-service, bank-cash-account-service)
# at build time:
#   docker buildx build \
#     --build-arg PROJECT_NAME=bank-api-service \
#     -t ghcr.io/repldriven/bank-api-service:0.0.0 \
#     -f infra/docker/service/Dockerfile \
#     --platform linux/amd64,linux/arm64 \
#     .

ARG PROJECT_NAME

# `--platform=$BUILDPLATFORM` pins the JVM build to the host arch.
# Java bytecode is platform-independent, so the resulting uberjar runs
# on either linux/amd64 or linux/arm64 runtime images without
# rebuilding. Avoids ~3× QEMU-emulation slowdown for the cross-arch
# build under multi-platform bake. The runtime stage stays on
# `$TARGETPLATFORM` because the JRE and libfdb_c.so are arch-specific.
FROM --platform=$BUILDPLATFORM clojure:temurin-21-tools-deps-bookworm AS build
ARG PROJECT_NAME
ARG BUILDARCH
WORKDIR /workspace

# Code-gen tooling for the prep step. Both versions match the local
# nix flake so generated output is identical to dev:
#  - protoc 25.8 — arch-aware Linux build from the upstream release
#  - protoc-gen-clojure 2.1.2 — protojure's plugin, distributed as a
#    `:;exec java -jar ...` self-executing jar, so it runs on any
#    arch via the temurin JDK already in this image
# `BUILDARCH` (not TARGETARCH) because this stage runs on the build
# host; protoc's binary needs to match the host, not the eventual
# image's target arch.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && PROTOC_ARCH="$(case ${BUILDARCH:-amd64} in \
                     amd64|x86_64)  echo x86_64 ;; \
                     arm64|aarch64) echo aarch_64 ;; \
                     *) echo "unknown arch: ${BUILDARCH}" >&2; exit 1 ;; \
                   esac)" \
 && curl -fsSL \
      "https://github.com/protocolbuffers/protobuf/releases/download/v25.8/protoc-25.8-linux-${PROTOC_ARCH}.zip" \
      -o /tmp/protoc.zip \
 && unzip -q /tmp/protoc.zip -d /usr/local \
 && rm /tmp/protoc.zip \
 && curl -fsSL \
      "https://github.com/protojure/protoc-plugin/releases/download/v2.1.2/protoc-gen-clojure" \
      -o /usr/local/bin/protoc-gen-clojure \
 && chmod +x /usr/local/bin/protoc-gen-clojure

COPY . .
# Cache mounts for the Maven local repo and tools.deps git-libs cache.
# `sharing=locked` serialises mount access across concurrent bake
# targets — required because Maven Resolver isn't safe for parallel
# downloads to the same local repo (concurrent writers race on the
# `*.part` files mid-download). With locked, the first build downloads
# everything; subsequent builds hit the populated cache and finish in
# seconds. Cache contents are *not* baked into the image layer, which
# keeps the runtime stage clean.
# Code-gen step (Lancaster Avro schemas, protobuf classes) must run
# from the workspace root — the `:+bank` alias is defined in the
# top-level deps.edn, not in any project deps.edn. Mirrors the
# local `just force-prep` recipe.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    --mount=type=cache,target=/root/.gitlibs,sharing=locked \
    clojure -X:deps prep :aliases '[:+bank :dev]' :force true
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    --mount=type=cache,target=/root/.gitlibs,sharing=locked \
    cd "projects/${PROJECT_NAME}" \
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
