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
    "template-id": data["template-id"],
    currency: data.currency,
  };
  if (data["interest-rate-bps"]) {
    body["interest-rate-bps"] = data["interest-rate-bps"];
  }
  if (data["effective-from"]) {
    body["effective-from"] = data["effective-from"];
  }
  if (data["effective-to"]) {
    body["effective-to"] = data["effective-to"];
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

// ─── Ledger accounts (org-scoped, read-only) ───
//
// The bank's chart of accounts (GL accounts). The list endpoint returns
// the accounts without balances; each account's constituent balances —
// keyed by (balance-type, balance-status) with credit/debit minor units —
// come from a separate per-account endpoint. The Ledger page fetches the
// list, then the balances for each account, to render the tree-table.

export function list_ledger_accounts() {
  return request("/v1/ledger-accounts");
}

export function list_ledger_account_balances(account_id) {
  return request(`/v1/ledger-accounts/${account_id}/balances`);
}

// ─── Policies (org-scoped, read-only) ───
//
// The policies effective for the caller's own bank — the always-on
// platform tier plus any bound to the bank. `/v1/policies` proper is
// admin-only (the ops console); this `/me` variant is scoped to the
// tenant via the bank-id on their token. The wire shape is the nested
// protojure policy; policy-adapter.mjs flattens it for the matrix.

export function list_my_policies() {
  return request("/v1/me/policies");
}

// The resolved effective decision for my bank: capabilities/limits
// collapsed (deny-wins, most-restrictive) to one survivor per scope,
// each carrying its origin policy. policy-adapter.mjs flattens it.
export function list_my_effective_policies() {
  return request("/v1/me/effective-policies");
}

// ─── Jobs (scheduler, org-scoped, read-only) ───
//
// The bank's scheduled jobs — preset task pipelines run on a cadence
// (daily interest accrual + capitalisation today). The list endpoint
// returns the jobs with their schedule (periodicity, run-time-minutes,
// enabled) and last/next-run timestamps, but not the run outcome; the
// status an operator watches — succeeded/failed/running — comes from a
// job's runs (newest-first), so the Jobs page fetches the latest run
// per job to drive its badge. Read-only: force-start and schedule
// edits aren't surfaced yet.

export function list_jobs() {
  return request("/v1/jobs");
}

export function list_job_runs(job_id) {
  return request(`/v1/jobs/${job_id}/runs`);
}

// Force-start a job now. Runs the pipeline synchronously server-side and
// returns the completed run; idempotent (the daily-limit policy guards
// double-accrual).
export function force_start_job(job_id) {
  return mutate(`/v1/jobs/${job_id}/runs`, { method: "POST" });
}

// Edit a job's schedule — any of periodicity / run-time-minutes /
// enabled (omitted fields keep their current value). Toggling enabled is
// the pause/resume control.
export function update_job_schedule(job_id, body) {
  return mutate(`/v1/jobs/${job_id}/schedule`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
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
