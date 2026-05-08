# syntax=docker/dockerfile:1.7

# bank-app: Svelte/Vite frontend.
#  Stage 1 — build the static bundle with Node. The `npm run build`
#  script first compiles the cherry-cljs sources (`api.cljs`,
#  `time.cljs`) to ESM via the cherry-cljs devDependency, then runs
#  `vite build`.
#  Stage 2 — serve `dist/` via nginx, proxying `/v1` to the bank-api
#  service. The upstream URL is parameterised through the
#  `API_UPSTREAM` env var so the same image works locally
#  (host.docker.internal), in kind (Kubernetes service DNS), and in
#  any other deployment.

# `--platform=$BUILDPLATFORM` pins the Node build to the host arch
# regardless of the requested target. The output (`dist/`) is static
# HTML/CSS/JS — arch-independent — so we can copy it into either
# linux/amd64 or linux/arm64 runtime images. Without this, multi-arch
# bake would run `npm ci` under QEMU for the non-native arch, where
# node's network stack is slow enough to hit ETIMEDOUT on registry
# fetches.
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /app
COPY bases/bank-app/package.json bases/bank-app/package-lock.json ./
RUN npm ci
COPY bases/bank-app/ ./
RUN npm run build

# Runtime stage uses the requested target arch — nginx is multi-arch
# on Docker Hub, so this just works.
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY infra/docker/bank-app/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/ /usr/share/nginx/html/

# nginx-unprivileged auto-runs envsubst on /etc/nginx/templates/*.template
# at startup. Restrict the substitution to API_UPSTREAM so nginx's own
# variables (`$host`, `$remote_addr`, `$proxy_add_x_forwarded_for`,
# `$scheme`) survive untouched in the rendered config.
ENV NGINX_ENVSUBST_FILTER="^API_UPSTREAM$"
ENV API_UPSTREAM="http://bank-api:8080"

EXPOSE 8080
