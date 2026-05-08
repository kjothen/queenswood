"""Schemathesis hooks for Queenswood API testing.

Auth hook
---------
Selects the correct Bearer token per operation based on its OpenAPI
security scheme: `adminAuth` uses the admin API key, `orgAuth` uses an
organization API key.  Both are passed via environment variables.

Using the `@schemathesis.auth()` API (rather than a blind
`before_call` override) lets schemathesis's `ignored_auth` check
properly test that the API rejects unauthenticated / invalid
requests — it can substitute or remove our auth for those probes.

Parameter injection hook
------------------------
Schemathesis generates random values for path parameters and request
body fields, most of which don't correspond to real resources and
cause 404s before the API logic is exercised.  The `before_call` hook
replaces generated values with IDs from real resources created by the
`just schemathesis` fixture setup, passed in via environment variables.

Path parameter overrides apply universally.  Body overrides are
operation-specific: only fields that need a real ID are replaced; all
other generated fields (amounts, dates, etc.) are left as-is so
schemathesis still exercises the full value space.
"""
import os
import re

import schemathesis

ADMIN_TOKEN = os.environ.get("ADMIN_TOKEN", "")
ORG_TOKEN   = os.environ.get("ORG_TOKEN", "")

ORG_ID              = os.environ.get("ORG_ID", "")
PARTY_ID            = os.environ.get("PARTY_ID", "")
PRODUCT_ID          = os.environ.get("PRODUCT_ID", "")
VERSION_ID          = os.environ.get("VERSION_ID", "")
ACCOUNT_ID          = os.environ.get("ACCOUNT_ID", "")
ACCOUNT_ID_2        = os.environ.get("ACCOUNT_ID_2", "")
INTERNAL_PAYMENT_ID = os.environ.get("INTERNAL_PAYMENT_ID", "")
OUTBOUND_PAYMENT_ID = os.environ.get("OUTBOUND_PAYMENT_ID", "")
BALANCE_TYPE        = os.environ.get("BALANCE_TYPE", "")
BALANCE_CURRENCY    = os.environ.get("BALANCE_CURRENCY", "")
BALANCE_STATUS      = os.environ.get("BALANCE_STATUS", "")
CHECK_ID            = os.environ.get("CHECK_ID", "")
POLICY_ID           = os.environ.get("POLICY_ID", "")


@schemathesis.auth(refresh_interval=None)
class SecurityAuth:
    def get(self, case, context):
        definition = context.operation.definition.raw
        security = definition.get("security", [])
        schemes = {k for requirement in security for k in requirement}
        if "adminAuth" in schemes:
            return ADMIN_TOKEN
        if "orgAuth" in schemes:
            return ORG_TOKEN
        return None

    def set(self, case, data, context):
        if data is None:
            return
        if case.headers is None:
            case.headers = {}
        case.headers["Authorization"] = f"Bearer {data}"


def _override(params, mapping):
    """Replace values in a path-parameter dict using only non-empty entries."""
    for key, value in mapping.items():
        if key in params and value:
            params[key] = value


# Project ID shape: `<3-letter-prefix>.<26 Crockford base32 chars>`,
# e.g. `acc.01kr3kvscfhgbhfnj4v2eas2vk`. Used to decide whether a
# generated value is "valid-shape" (and thus should be redirected to
# a real fixture record) or "malformed" (and should be left alone so
# schemathesis's negative-coverage tests can verify rejection).
_ID_RE = re.compile(r'^[a-z]{3}\.[0-9a-hjkmnp-tv-z]{26}$')


def _normalise_id(target, key, real_id):
    """Replace `target[key]` with `real_id` only when the existing
    value is a syntactically-valid ID (so the override rescues us
    from a downstream 404 / not-found). Leave malformed values alone
    so schemathesis's negative-coverage tests still verify malli
    rejection."""
    val = target.get(key)
    if real_id and isinstance(val, str) and _ID_RE.match(val):
        target[key] = real_id


_VALID_CURRENCIES = {"EUR", "GBP", "USD"}


def _normalise_currency(body):
    """The test fixture's account/balance is GBP-only, so submissions
    in other valid currencies (EUR/USD) would fail with downstream
    domain errors (no matching balance). Normalise valid enum values
    to GBP, but leave clearly-invalid values alone so schemathesis's
    negative-coverage tests still verify malli rejection."""
    cur = body.get("currency")
    if isinstance(cur, str) and cur in _VALID_CURRENCIES:
        body["currency"] = "GBP"


@schemathesis.hook("before_call")
def before_call(context, case, session):
    path   = context.operation.path
    method = context.operation.method.lower()

    # --- Path parameter injection ---
    if case.path_parameters:
        # ID-shaped path params: only redirect when value looks like a
        # real ID, leave malformed values alone for negative coverage.
        for key, real_id in (("account-id",  ACCOUNT_ID),
                             ("org-id",      ORG_ID),
                             ("product-id",  PRODUCT_ID),
                             ("version-id",  VERSION_ID),
                             ("party-id",    PARTY_ID),
                             ("check-id",    CHECK_ID),
                             ("policy-id",   POLICY_ID)):
            _normalise_id(case.path_parameters, key, real_id)
        # Enum-style path params (route-specific values, no negative
        # coverage at this layer in practice).
        _override(case.path_parameters, {
            "balance-type":   BALANCE_TYPE,
            "currency":       BALANCE_CURRENCY,
            "balance-status": BALANCE_STATUS,
        })
        # Payment routes: pick the right payment-id by URL segment
        if "payment-id" in case.path_parameters:
            if "/outbound/" in path:
                val = OUTBOUND_PAYMENT_ID or INTERNAL_PAYMENT_ID
            else:
                val = INTERNAL_PAYMENT_ID or OUTBOUND_PAYMENT_ID
            _normalise_id(case.path_parameters, "payment-id", val)

    # --- Request body injection ---
    if not (case.body and isinstance(case.body, dict)):
        return

    # POST /v1/cash-accounts — needs a real party-id and product-id
    if path == "/v1/cash-accounts" and method == "post":
        _normalise_id(case.body, "party-id",   PARTY_ID)
        _normalise_id(case.body, "product-id", PRODUCT_ID)
        _normalise_currency(case.body)

    # POST /v1/payments/internal — needs two real account IDs
    elif path == "/v1/payments/internal" and method == "post":
        _normalise_id(case.body, "debtor-account-id",   ACCOUNT_ID)
        _normalise_id(case.body, "creditor-account-id", ACCOUNT_ID_2)
        _normalise_currency(case.body)

    # POST /v1/payments/outbound — needs a funded debtor account
    elif path == "/v1/payments/outbound" and method == "post":
        _normalise_id(case.body, "debtor-account-id", ACCOUNT_ID)
        _normalise_currency(case.body)

    # POST /v1/simulate/.../inbound-transfer — needs a real account
    elif path.endswith("/inbound-transfer") and method == "post":
        _normalise_id(case.body, "account-id", ACCOUNT_ID)
        _normalise_currency(case.body)
