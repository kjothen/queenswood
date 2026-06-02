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

// Mutations get an `Idempotency-Key` so a retried POST/PUT/DELETE
// doesn't double-apply on the server side. Same convention bank-app
// uses; bank-api keys against this header to dedupe.
function mutate(path, opts = {}) {
  return request(path, {
    ...opts,
    headers: {
      "Idempotency-Key": crypto.randomUUID(),
      ...(opts.headers ?? {}),
    },
  });
}

export function get_me() {
  return request("/v1/me");
}

export function onboard(bank_name) {
  return mutate("/v1/onboarding/me", {
    method: "POST",
    body: JSON.stringify({ "bank-name": bank_name }),
  });
}

// ─── Cash-account products (org-scoped) ───
//
// Ported from bank-app/src/lib/api.mjs. bank-app uses an org-selector
// (operator acting on behalf of any org) and so wraps each call in
// `org_request` to mint a per-org service-account JWT. The console
// user has a single membership; their own user JWT already carries
// the right org context, so we just use `request()` and let bank-api
// resolve the org from the token's claims.

function product_request_body(data) {
  // Mirrors bank-app's `product_request_body` — the bank-api accepts
  // the same shape regardless of caller. `interest-rate-bps` is
  // optional (omitted for products with no interest).
  const body = {
    name: data.name,
    "product-type": data["product-type"],
    currency: data.currency,
  };
  if (data["interest-rate-bps"]) {
    body["interest-rate-bps"] = data["interest-rate-bps"];
  }
  return body;
}

export function list_cash_account_product_templates() {
  return request("/v1/cash-account-product-templates");
}

export function list_cash_account_products() {
  return request("/v1/cash-account-products");
}

export function create_cash_account_product(data) {
  return mutate("/v1/cash-account-products", {
    method: "POST",
    body: JSON.stringify(product_request_body(data)),
  });
}

export function open_cash_account_product_draft(product_id, data) {
  return mutate(`/v1/cash-account-products/${product_id}/versions`, {
    method: "POST",
    body: JSON.stringify(product_request_body(data)),
  });
}

export function update_cash_account_product_draft(product_id, version_id, data) {
  return mutate(
    `/v1/cash-account-products/${product_id}/versions/${version_id}`,
    {
      method: "PUT",
      body: JSON.stringify(product_request_body(data)),
    },
  );
}

export function discard_cash_account_product_draft(product_id, version_id) {
  return mutate(
    `/v1/cash-account-products/${product_id}/versions/${version_id}`,
    { method: "DELETE" },
  );
}

export function publish_cash_account_product(product_id, version_id) {
  return mutate(
    `/v1/cash-account-products/${product_id}/versions/${version_id}/publish`,
    { method: "POST" },
  );
}

// ─── Parties (org-scoped) ───
//
// bank-api `Party` shape carries summary fields only (party-id, type,
// display-name, status, created-at, updated-at). The richer record
// the Legal Persons drawer wants — given/family names, dob, address,
// national identifier — is what `create_party` accepts but not what
// `list_parties` returns. The drawer falls back to "—" for the
// per-field detail until the read endpoint surfaces it.

export function list_parties() {
  return request("/v1/parties");
}

export function get_party(party_id) {
  return request(`/v1/parties/${party_id}`);
}

export function create_party(data) {
  return mutate("/v1/parties", {
    method: "POST",
    body: JSON.stringify(data),
  });
}
