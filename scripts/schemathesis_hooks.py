"""Schemathesis hooks for Queenswood API testing.

Registers an auth provider that selects the correct Bearer token
per operation based on its OpenAPI security scheme: `adminAuth`
uses the admin API key, `orgAuth` uses an organization API key.
Both are passed via environment variables.

Using the `@schemathesis.auth()` API (rather than a blind
`before_call` override) lets schemathesis's `ignored_auth` check
properly test that the API rejects unauthenticated / invalid
requests — it can substitute or remove our auth for those probes.
"""
import os

import schemathesis

ADMIN_TOKEN = os.environ.get("ADMIN_TOKEN", "")
ORG_TOKEN = os.environ.get("ORG_TOKEN", "")


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
