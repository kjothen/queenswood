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
- `roles/oauthconfig.editor` on that project. The console names the
  half it is missing — `oauthconfig.verification.get` — and
  `roles/oauthconfig.viewer` carries that one and stops at the next:
  creating the client needs `clientauthconfig.clients.create` and
  `.createSecret`, which only the editor role has.
- The domain Keycloak is published on, and the realm name.
- The identity provider's alias as the realm defines it — `google`
  below, and the same string the console SPA sends as its
  `keycloakIdpHint`.

The right is granted through the installation's `platformAdmin`
capability, so it reaches a person through group membership rather than
by being clicked on. It sits there rather than with `platformViewer`
for a reason worth keeping: creating a client mints a credential that
speaks for this installation to Google, and a capability called viewer
should not.

No automation holds it, and none can. Google exposes no API for a web
client with a chosen redirect URI, so there is no identity to give the
job to — which is what makes this a standing right for a person rather
than something to grant an automation and take away.

### 1. Configure the consent screen

The client cannot be created until the project has one, and it asks for
two email addresses that are not the same kind of thing. The **user
support** address is shown to users as the contact for questions about
their consent, so it should be a group rather than a person, and Google
accepts only an address you own. The **contact** addresses are Google
notifying you — deprecations, policy changes, verification status — and
take any address. Neither wants a personal account: these are how an
installation finds out an API it depends on is going away, and an
account that leaves takes them with it.

Name the app for what a user is consenting to, and distinguish the
environment. Each instance is its own project with its own consent
screen, so without it there are eventually two apps called the same
thing and nothing on the screen to tell them apart.

**Internal or external is the consequential choice.** Internal admits
only accounts in the organisation, which makes an environment a
different sign-in path from the one being shipped. External admits any
Google account, which is what a product whose users bring their own
identity needs.

Verification is the usual reason people reach for internal, and for
these scopes it does not apply: Keycloak's Google provider requests
`openid profile email` unless the realm sets `defaultScope`, and
verification is required for sensitive and restricted scopes. An
external app requesting only these can be published without joining the
queue.

What external does cost is testing mode, which it starts in:

- Only accounts on the test-user list may sign in, up to a hundred.
- **Refresh tokens expire after seven days.** A session that should
  persist drops back to sign-in about weekly, which reads as a bug in
  the application rather than as a property of the consent screen, and
  is the single most confusing consequence of leaving an environment in
  testing mode.

Publishing to production ends both. Do it once the test-user list stops
being the point.

### 2. Create the OAuth client

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

### 3. One client per environment

Not one shared across them. Revoking or rotating the client a
development environment uses must not touch the one real customers
sign in through, and a client still in Google's Testing mode is a
different risk object from a published one — different consent
behaviour, different token lifetimes, a different audience.

The cost of one each is a console visit. The cost of sharing is
discovering the difference during an incident.

### 4. Put the pair where the realm can reach it

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

### 5. Check

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

- Configure the consent screen before the client. The client cannot be
  created without one.
- Create the OAuth client as a Web application, by hand, in the
  console. No API creates one with a chosen redirect URI.
- Match the redirect URI to
  `https://keycloak.<domain>/realms/<realm>/broker/<alias>/endpoint`
  exactly, alias included.
- Create one client per environment.
- Grant `roles/oauthconfig.editor` through `platformAdmin`, not
  `platformViewer`. Creating a client mints a credential.
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

- Choose external where users bring their own identity, and read the
  verification warning against the scopes actually requested rather
  than as written: `openid profile email` needs none.
- Publish out of testing mode once the test-user list stops being the
  point, or refresh tokens keep expiring after seven days and it reads
  as an application fault.
- Name the app for the environment as well as the product. Each
  instance has its own consent screen.
- Use a group for the user support address, and never a personal
  account for either address.

## References

- [cloud-account](cloud-account.md) — the other console work with no
  API behind it.
- [recovery-procedures](recovery-procedures.md) — why a realm keeps
  what it was first imported with, and what that costs on a restore.
- [deployment](deployment.md) — the console client's own redirect
  list, which is reconciled rather than manual.
