# syntax=docker/dockerfile:1.7

# bank-app: Svelte/Vite operator console.
#  Stage 1 — build the static bundle with Node (`npm run build` →
#  `vite build`). Auth lives in `auth.mjs` and talks to Keycloak
#  via keycloak-js.
#  Stage 2 — serve `dist/` via nginx, proxying /v1, /oauth, and
#  /.well-known to bank-api so the SPA stays same-origin (no CORS).
#  nginx also synthesises `/env.js` from container env at startup
#  so a single image works against Vite dev, kind, and GKE without
#  rebuilds.

# `--platform=$BUILDPLATFORM` pins the Node build to the host arch
# regardless of the requested target. Same rationale as bank-console.
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /app
COPY bases/bank-app/package.json bases/bank-app/package-lock.json* ./
RUN npm install
COPY bases/bank-app/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY infra/docker/bank-app/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/ /usr/share/nginx/html/

# nginx-unprivileged runs envsubst on /etc/nginx/templates/*.template
# at startup. We need API_UPSTREAM (bank-api proxy target) and the
# four KEYCLOAK_* values rendered into /env.js.
ENV NGINX_ENVSUBST_FILTER="^(API_UPSTREAM|KEYCLOAK_URL|KEYCLOAK_REALM|KEYCLOAK_CLIENT_ID|KEYCLOAK_IDP_HINT)$"
ENV API_UPSTREAM="http://bank-api:8080"
ENV KEYCLOAK_URL=""
ENV KEYCLOAK_REALM="queenswood-ops"
ENV KEYCLOAK_CLIENT_ID="queenswood-app"
ENV KEYCLOAK_IDP_HINT=""

EXPOSE 8080
