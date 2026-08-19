# Google sign-in
<!-- tessl-plugin: deployment -->

## Problem

The realm ships an identity provider for Google with a placeholder
client id and secret, because a real pair belongs to an installation
rather than to a chart. Supplying the real one is manual twice over:
the client itself exists only in a console, and the realm that must
carry it cannot be re-imported.

Neither half is hard. Both are easy to get subtly wrong in ways that
surface after a user has already left the site.

## Solution

### Before you start

- The instance's project, and access to its console.
- The domain Keycloak is published on, and the realm name.
- The identity provider's alias as the realm defines it — `google`
  below, and the same string the console SPA sends as its
  `keycloakIdpHint`.

### 1. Create the OAuth client

There is no API for this. `gcloud iap oauth-clients` creates an
IAP-scoped client, sets its own redirect URI, and stopped functioning
in any case. A Web application client with a redirect URI of your
choosing is a console action.

Create it as a **Web application** under the instance project's
credentials page. The redirect URI is the part worth checking twice:

```
https://keycloak.<domain>/realms/<realm>/broker/<alias>/endpoint
```

`broker/<alias>/endpoint` is Keycloak's shape and the alias is the
realm's, not the provider's name in general. A wrong one is not
refused at setup: Google accepts the client happily and returns
`redirect_uri_mismatch` at the moment a user has left your site for
theirs, which is the worst place to discover a typo.

An external consent screen puts the client in Google's verification
queue; an internal one is limited to the organisation. Which is right
depends on who signs in, and it is worth settling before the client is
created rather than after.

### 2. One client per environment

Not one shared across them. Revoking or rotating the client a
development environment uses must not touch the one real customers
sign in through, and a client still in Google's Testing mode is a
different risk object from a published one — different consent
behaviour, different token lifetimes, a different audience.

The cost of one each is a console visit. The cost of sharing is
discovering the difference during an incident.

### 3. Put the pair where the realm can reach it

The realm was imported with a placeholder and cannot be re-imported —
the operator creates realms and never overwrites them, so a chart
change never reaches a realm that exists. Both values therefore go in
over the Admin API, against the running realm:

```
PUT /admin/realms/<realm>/identity-provider/instances/<alias>
```

Send the client id directly. **Do not send the secret.** Send a vault
expression — `${vault.<alias>-client-secret}` — and place the secret
in the vault Keycloak mounts, so what is stored on the identity
provider is a reference rather than a credential. Keycloak resolves it
from the mounted file at token exchange.

Keycloak reads that mount at startup, so a secret placed after the pod
started is not seen until it restarts.

### 4. Check

Sign in. A failure is legible if you know which half produced it:

- **`redirect_uri_mismatch`, from Google** — the redirect URI on the
  client does not match what Keycloak sent. Compare it against the
  form above, character for character, including the scheme.
- **`401 invalid_client`, from Google** — the id or the secret is
  wrong, or the secret never resolved and Keycloak sent the literal
  vault expression. On a restored or rebuilt environment this is
  indistinguishable from the restore itself having failed, which is
  what makes it worth ruling out first.
- **`Invalid parameter: redirect_uri`, from Keycloak** — nothing to do
  with Google. That is the console client's own redirect list, which
  the realm import reconciles; see
  [deployment](deployment.md).

## Rules

**MUST:**

- Create the OAuth client as a Web application, by hand, in the
  console. No API creates one with a chosen redirect URI.
- Match the redirect URI to
  `https://keycloak.<domain>/realms/<realm>/broker/<alias>/endpoint`
  exactly, alias included.
- Create one client per environment.
- Put the id and the secret in over the Admin API. A realm that exists
  keeps the placeholder it was imported with, whatever the chart says.
- Send a vault expression as the secret, never the secret itself.
- Restart Keycloak after placing a secret in the vault mount.

**MUST NOT:**

- Share one OAuth client across environments.
- Expect a chart change or a re-import to reach the placeholder.
- Read `401 invalid_client` on a rebuilt environment as evidence the
  rebuild failed. It is the likelier cause and the wrong conclusion.

**SHOULD:**

- Settle internal against external consent before creating the client,
  since external means a verification queue.

## References

- [cloud-account](cloud-account.md) — the other console work with no
  API behind it.
- [recovery-procedures](recovery-procedures.md) — why a realm keeps
  what it was first imported with, and what that costs on a restore.
- [deployment](deployment.md) — the console client's own redirect
  list, which is reconciled rather than manual.
