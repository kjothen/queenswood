# syntax=docker/dockerfile:1.7

# bank-app: Svelte/Vite operator console.
#  Stage 1 — build the static bundle with Node from the yarn 4
#  workspace at the repo root. bank-app is one workspace member;
#  the shared design system (`@queenswood/bank-ui`) is another,
#  hoisted by yarn so its tokens.css and components are reachable
#  via `@queenswood/bank-ui`.
#  Stage 2 — serve `dist/` via nginx, proxying /v1, /oauth, and
#  /.well-known to bank-api so the SPA stays same-origin (no CORS).
#  nginx also synthesises `/env.js` from container env at startup
#  so a single image works against Vite dev, kind, and GKE without
#  rebuilds.

# `--platform=$BUILDPLATFORM` pins the Node build to the host arch
# regardless of the requested target. Same rationale as bank-console.
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /repo

# Workspace manifests first for Docker layer caching. All three
# workspaces must be present even if this image only builds bank-app
# — yarn validates the whole graph at install time.
COPY package.json yarn.lock .yarnrc.yml ./
COPY bases/bank-console/package.json bases/bank-console/
COPY bases/bank-app/package.json bases/bank-app/
COPY components/bank-ui/package.json components/bank-ui/
RUN corepack enable && corepack yarn install --immutable

# Brick source.
COPY bases/bank-app/ bases/bank-app/
COPY components/bank-ui/ components/bank-ui/
RUN corepack yarn workspace bank-app build

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY infra/docker/bank-app/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /repo/bases/bank-app/dist/ /usr/share/nginx/html/

# nginx-unprivileged runs envsubst on /etc/nginx/templates/*.template
# at startup. API_UPSTREAM is the bank-api proxy target,
# KEYCLOAK_UPSTREAM is the in-cluster Keycloak Service that the
# `/keycloak/*` location reverse-proxies to so the SPA stays same-
# origin, and the four KEYCLOAK_* values get rendered into /env.js.
ENV NGINX_ENVSUBST_FILTER="^(API_UPSTREAM|KEYCLOAK_UPSTREAM|KEYCLOAK_URL|KEYCLOAK_REALM|KEYCLOAK_CLIENT_ID|KEYCLOAK_IDP_HINT)$"
ENV API_UPSTREAM="http://bank-api:8080"
ENV KEYCLOAK_UPSTREAM="http://queenswood-keycloak-keycloak-service:8080"
ENV KEYCLOAK_URL=""
ENV KEYCLOAK_REALM="queenswood-ops"
ENV KEYCLOAK_CLIENT_ID="queenswood-app"
ENV KEYCLOAK_IDP_HINT=""

EXPOSE 8080
