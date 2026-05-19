// bank-app's API surface. Replaces the old cherry-cljs-compiled
// `api.cljs`. All admin-scoped requests use the Keycloak ops user
// JWT (held in memory by keycloak-js, attached via `fresh_token()`).
// Org-scoped requests can additionally use a per-org service-account
// JWT minted by /oauth/token; `set_org` mints one from credentials
// stored at org-creation time. When no per-org credentials are
// stashed, requests fall back to the ops user JWT — which carries
// the `:admin` role and is accepted by every route.
import { fresh_token } from "./auth.mjs";

const ORG_CREDENTIALS_KEY = "queenswood-org-credentials";

// In-memory bearer for org-scoped requests. Null means "fall back
// to the ops user JWT". Set by `set_org`.
let org_token = null;

function load_org_credentials() {
  const raw = localStorage.getItem(ORG_CREDENTIALS_KEY);
  return raw ? JSON.parse(raw) : {};
}

function save_org_credentials(org_id, client_id, client_secret, status) {
  const store = load_org_credentials();
  store[org_id] = {
    "client-id": client_id,
    "client-secret": client_secret,
    status,
  };
  localStorage.setItem(ORG_CREDENTIALS_KEY, JSON.stringify(store));
}

export function clear_org_credentials() {
  localStorage.removeItem(ORG_CREDENTIALS_KEY);
  org_token = null;
}

async function exchange_token(client_id, client_secret, status) {
  const scope =
    status === "live" ? "queenswood-api-live" : "queenswood-api-test";
  const params =
    "grant_type=client_credentials" +
    `&client_id=${encodeURIComponent(client_id)}` +
    `&client_secret=${encodeURIComponent(client_secret)}` +
    `&scope=${scope}`;
  const res = await fetch("/oauth/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: params,
  });
  const body = await res.json();
  return body.access_token;
}

export async function set_org(org_id) {
  const creds = load_org_credentials()[org_id];
  if (!creds) {
    // No stored credentials — fall back to the ops user JWT. The
    // ops user has the `:admin` realm role which the bank-api
    // translates into `:admin :org`, so org-scoped routes accept
    // it as the actor.
    org_token = null;
    return null;
  }
  const token = await exchange_token(
    creds["client-id"],
    creds["client-secret"],
    creds.status,
  );
  org_token = token ?? null;
  return token;
}

async function bearer_admin() {
  return await fresh_token();
}

async function bearer_org() {
  return org_token ?? (await fresh_token());
}

async function parse_response(res) {
  const body = res.status === 204 ? null : await res.json().catch(() => null);
  return { "http-status": res.status, body };
}

async function admin_request(path, opts = {}) {
  const token = await bearer_admin();
  const res = await fetch(path, {
    ...opts,
    headers: {
      ...(opts.headers ?? {}),
      Authorization: `Bearer ${token}`,
    },
  });
  return parse_response(res);
}

async function org_request(path, opts = {}) {
  const token = await bearer_org();
  const res = await fetch(path, {
    ...opts,
    headers: {
      ...(opts.headers ?? {}),
      Authorization: `Bearer ${token}`,
    },
  });
  return parse_response(res);
}

function uuid() {
  return crypto.randomUUID();
}

function json_body(obj) {
  return {
    "Content-Type": "application/json",
    ...obj,
  };
}

// ─── Organizations + tiers + policies (admin-scoped) ───

export async function create_organization(name, status, tier, currencies) {
  const res = await admin_request("/v1/organizations", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name, status, tier, currencies }),
  });
  const s = res["http-status"];
  if (s >= 200 && s < 300) {
    const body = res.body ?? {};
    const org_id = body["organization-id"];
    const client_id = body["client-id"];
    const client_secret = body["client-secret"];
    if (client_id && client_secret) {
      save_org_credentials(org_id, client_id, client_secret, status);
    }
  }
  return res;
}

export function list_organizations() {
  return admin_request("/v1/organizations");
}

export function list_tiers() {
  return admin_request("/v1/tiers");
}

export function list_policies() {
  return admin_request("/v1/policies");
}

export function get_policy(policy_id) {
  return admin_request(`/v1/policies/${policy_id}`);
}

// ─── Simulate (admin-scoped, per-org) ───

export function simulate_inbound_transfer(org_id, account_id, amount, currency) {
  return admin_request(
    `/v1/simulate/organizations/${org_id}/inbound-transfer`,
    {
      method: "POST",
      headers: json_body({ "Idempotency-Key": uuid() }),
      body: JSON.stringify({
        "account-id": account_id,
        amount,
        currency,
      }),
    },
  );
}

export function simulate_accrue(org_id, as_of_date) {
  return admin_request(`/v1/simulate/organizations/${org_id}/accrue`, {
    method: "POST",
    headers: json_body({ "Idempotency-Key": uuid() }),
    body: JSON.stringify({ "as-of-date": as_of_date }),
  });
}

export function simulate_capitalize(org_id, as_of_date) {
  return admin_request(`/v1/simulate/organizations/${org_id}/capitalize`, {
    method: "POST",
    headers: json_body({ "Idempotency-Key": uuid() }),
    body: JSON.stringify({ "as-of-date": as_of_date }),
  });
}

// ─── Parties (org-scoped) ───

export function create_party(data) {
  const body = {
    type: "person",
    "display-name": data["display-name"],
    "given-name": data["given-name"],
    "family-name": data["family-name"],
    "date-of-birth": data["date-of-birth"],
    nationality: data.nationality,
  };
  if (data["middle-names"]) body["middle-names"] = data["middle-names"];
  if (data["national-identifier"])
    body["national-identifier"] = data["national-identifier"];
  return org_request("/v1/parties", {
    method: "POST",
    headers: json_body({ "Idempotency-Key": uuid() }),
    body: JSON.stringify(body),
  });
}

export function list_parties(query_string) {
  const url = query_string ? `/v1/parties?${query_string}` : "/v1/parties";
  return org_request(url);
}

// ─── Payee checks (org-scoped) ───

export function list_payee_checks(query_string) {
  const url = query_string
    ? `/v1/payee-checks?${query_string}`
    : "/v1/payee-checks";
  return org_request(url);
}

export function check_payee(creditor_name, sort_code, account_number, account_type) {
  return org_request("/v1/payee-checks", {
    method: "POST",
    headers: json_body({}),
    body: JSON.stringify({
      "creditor-name": creditor_name,
      account: {
        "sort-code": sort_code,
        "account-number": account_number,
      },
      "account-type": account_type,
    }),
  });
}

// ─── Cash accounts (org-scoped) ───

export function open_cash_account(data) {
  return org_request("/v1/cash-accounts", {
    method: "POST",
    headers: json_body({ "Idempotency-Key": uuid() }),
    body: JSON.stringify({
      "party-id": data["party-id"],
      name: data.name,
      currency: data.currency,
      "product-id": data["product-id"],
    }),
  });
}

export function close_cash_account(account_id) {
  return org_request(`/v1/cash-accounts/${account_id}/close`, {
    method: "POST",
    headers: json_body({ "Idempotency-Key": uuid() }),
  });
}

const EMBED_PARAMS = "embed[balances]=true&embed[transactions]=true";

export function list_cash_accounts(query_string) {
  const url = query_string
    ? `/v1/cash-accounts?${query_string}&${EMBED_PARAMS}`
    : `/v1/cash-accounts?${EMBED_PARAMS}`;
  return org_request(url);
}

export function get_cash_account(account_id) {
  return org_request(`/v1/cash-accounts/${account_id}?${EMBED_PARAMS}`);
}

export function list_balances(account_id) {
  return org_request(`/v1/cash-accounts/${account_id}/balances`);
}

export function list_transactions(account_id) {
  return org_request(`/v1/cash-accounts/${account_id}/transactions`);
}

// ─── Cash account products (org-scoped) ───

function product_request_body(data) {
  const body = {
    name: data.name,
    "product-type": data["product-type"],
    "balance-sheet-side": data["balance-sheet-side"],
  };
  if (data["allowed-currencies"]?.length)
    body["allowed-currencies"] = data["allowed-currencies"];
  if (data["balance-products"]?.length)
    body["balance-products"] = data["balance-products"];
  if (data["allowed-payment-address-schemes"]?.length)
    body["allowed-payment-address-schemes"] =
      data["allowed-payment-address-schemes"];
  if (data["interest-rate-bps"])
    body["interest-rate-bps"] = data["interest-rate-bps"];
  return body;
}

export function create_cash_account_product(data) {
  return org_request("/v1/cash-account-products", {
    method: "POST",
    headers: json_body({}),
    body: JSON.stringify(product_request_body(data)),
  });
}

export function list_cash_account_products() {
  return org_request("/v1/cash-account-products");
}

export function publish_cash_account_product(product_id, version_id) {
  return org_request(
    `/v1/cash-account-products/${product_id}/versions/${version_id}/publish`,
    {
      method: "POST",
      headers: json_body({}),
    },
  );
}

export function open_cash_account_product_draft(product_id, data) {
  return org_request(`/v1/cash-account-products/${product_id}/versions`, {
    method: "POST",
    headers: json_body({}),
    body: JSON.stringify(product_request_body(data)),
  });
}

export function update_cash_account_product_draft(product_id, version_id, data) {
  return org_request(
    `/v1/cash-account-products/${product_id}/versions/${version_id}`,
    {
      method: "PUT",
      headers: json_body({}),
      body: JSON.stringify(product_request_body(data)),
    },
  );
}

export function discard_cash_account_product_draft(product_id, version_id) {
  return org_request(
    `/v1/cash-account-products/${product_id}/versions/${version_id}`,
    { method: "DELETE" },
  );
}

// ─── Payments (org-scoped) ───

export function submit_internal_payment(
  debtor_account_id,
  creditor_account_id,
  currency,
  amount,
  reference,
) {
  const body = {
    "debtor-account-id": debtor_account_id,
    "creditor-account-id": creditor_account_id,
    currency,
    amount,
  };
  if (reference) body.reference = reference;
  return org_request("/v1/payments/internal", {
    method: "POST",
    headers: json_body({ "Idempotency-Key": uuid() }),
    body: JSON.stringify(body),
  });
}

export function submit_outbound_payment(
  debtor_account_id,
  creditor_bban,
  creditor_name,
  currency,
  amount,
  scheme,
  reference,
) {
  const body = {
    "debtor-account-id": debtor_account_id,
    "creditor-bban": creditor_bban,
    "creditor-name": creditor_name,
    currency,
    amount,
    scheme,
  };
  if (reference) body.reference = reference;
  return org_request("/v1/payments/outbound", {
    method: "POST",
    headers: json_body({ "Idempotency-Key": uuid() }),
    body: JSON.stringify(body),
  });
}

// ─── /v1/me (the operator's own user record) ───

export function get_me() {
  return admin_request("/v1/me");
}
