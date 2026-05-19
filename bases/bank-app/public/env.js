// Placeholder so Vite dev returns 200 for this path. In production
// nginx synthesises this file from container env (see
// infra/docker/bank-app/nginx.conf.template); locally the SPA
// falls back to VITE_KEYCLOAK_* env vars read from `.env.local`.
window.__env = {};
