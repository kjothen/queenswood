// Thin fetch wrapper that attaches the Keycloak access token. All
// console traffic terminates at bank-api (`/v1/*`); cross-origin is
// avoided because the same nginx pod that serves this SPA also
// proxies `/v1/*` to bank-api, and in Vite dev the proxy in
// vite.config.js does the same locally.
import { fresh_token } from "./auth.mjs";

async function request(path, opts = {}) {
  const token = await fresh_token();
  const headers = {
    "Content-Type": "application/json",
    ...(opts.headers ?? {}),
    Authorization: `Bearer ${token}`,
  };
  const res = await fetch(path, { ...opts, headers });
  const body =
    res.status === 204 ? null : await res.json().catch(() => null);
  return { status: res.status, body };
}

export function get_me() {
  return request("/v1/me");
}

export function onboard(organization_name) {
  return request("/v1/onboarding/me", {
    method: "POST",
    body: JSON.stringify({ "organization-name": organization_name }),
  });
}
