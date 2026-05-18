# syntax=docker/dockerfile:1.7

# bank-console: Svelte/Vite frontend for the human sign-in flow.
#  Stage 1 — build the static bundle with Node. Pure ESM/Svelte —
#  no cherry-cljs step (unlike bank-app), since the console only
#  talks to bank-api via fetch + keycloak-js.
#  Stage 2 — serve dist/ via nginx, proxying /v1, /oauth, and
#  /.well-known to bank-api so the SPA stays same-origin (no CORS).
#  nginx also synthesises /env.js from container env at startup
#  so the SPA can resolve its Keycloak URL without a rebuild per env.

# `--platform=$BUILDPLATFORM` pins the Node build to the host arch
# regardless of the requested target. Same rationale as bank-app:
# static output is arch-independent, and we avoid running npm under
# QEMU for non-native arches.
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /app
COPY bases/bank-console/package.json bases/bank-console/package-lock.json* ./
RUN npm install
COPY bases/bank-console/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY infra/docker/bank-console/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/ /usr/share/nginx/html/

# nginx-unprivileged runs envsubst on /etc/nginx/templates/*.template
# at startup. We need API_UPSTREAM (bank-api proxy target) and the
# three KEYCLOAK_* values that get rendered into /env.js.
ENV NGINX_ENVSUBST_FILTER="^(API_UPSTREAM|KEYCLOAK_URL|KEYCLOAK_REALM|KEYCLOAK_CLIENT_ID|KEYCLOAK_IDP_HINT)$"
ENV API_UPSTREAM="http://bank-api:8080"
ENV KEYCLOAK_URL=""
ENV KEYCLOAK_REALM="queenswood"
ENV KEYCLOAK_CLIENT_ID="queenswood-console"
ENV KEYCLOAK_IDP_HINT=""

EXPOSE 8080
