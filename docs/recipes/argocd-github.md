# Argo CD against a private GitHub repository

<!-- tessl-plugin: deployment -->

## Problem

Argo reconciles from a repository that is private, and GitHub offers no
credential-free way to read one.

## Solution

A GitHub App, created once by an organisation owner, whose credentials
live in a secret store and reach the cluster as a Secret.

An App rather than a deploy key: it belongs to the organisation instead
of to a person, appears in the organisation's installed applications
with an audit trail, is revoked by uninstalling it, and reaches GitHub
over HTTPS where a deploy key needs SSH egress. A personal access token
is worse than either, since it carries whatever else its owner can
reach and dies when they leave.

### Creating it

In the UI, because GitHub has no API that creates an App:

1. Organisation settings, then Developer settings, then GitHub Apps,
   then New GitHub App.
2. Name it for what it reads. App names are globally unique on GitHub,
   so it carries the organisation as well — `<org>-<what>-reader`.
3. Homepage URL is required and unused. The repository's URL will do.
4. Untick Webhook Active. Nothing calls back.
5. Repository permissions: Contents, read-only, and nothing else.
   Metadata read-only is mandatory and selects itself.
6. Only on this account.
7. Create it, and record the App ID from the page that follows.
8. Generate a private key. A `.pem` downloads, and it is shown once.
9. Install App, then Only select repositories, then the repository Argo
   reads.
10. The installation's settings URL ends in the Installation ID:
    `.../settings/installations/<id>`.

Three values come out. The App ID and the Installation ID are
identifiers; the private key is the only secret. Store all three
together, so the identifiers travel with the key rather than through a
second channel.

### What Argo does with them

Argo mints an installation token from the key, valid an hour, and
refreshes it itself. So the stored key is not what reaches GitHub on
each request, and its rotation is not disruptive: an App may hold two
keys at once, so add the new one, update what is stored, then delete the
old.

Argo reads the three values from a Secret in its own namespace, labelled
so it is treated as repository credentials rather than as an
Application's own configuration. Nothing places that Secret by hand: it
comes from the secret store through an operator, which is what keeps the
key out of both git and anybody's shell history.

### Where it goes wrong quietly

**A container with no version.** Where the secret's container is
declared by one thing and filled by another, both can succeed
separately: the container exists, the operator syncs it, and what
reaches the cluster is empty. Argo then reports the repository as
unreachable rather than the credential as missing.

**A repository the App was never installed on.** An App can exist,
authenticate, and hold no access to the repository in question, because
installation is a separate act from creation. The failure names
authorisation rather than installation.

**A key rotated in one place.** Deleting the old key before the store
holds the new one leaves Argo unable to mint a token until its next
refresh, which is up to an hour after everything looks correct.

## Rules

**MUST:**

- Use a GitHub App, not a deploy key or a personal access token.
- Grant Contents read-only, and install the App only on the
  repositories Argo reads.
- Keep the private key in a secret store, and let an operator place the
  Secret.
- Add a new key before deleting the one it replaces.

**MUST NOT:**

- Commit the `.pem`, or pass it on a command line.
- Give the App webhook access, or any write permission.

**MAY:**

- Use one App for several repositories in the same organisation, where
  the same reader should reach all of them.

## References

- [argocd](argocd.md) — Applications, sync waves, and what a parent may
  hold.
- [queenswood-installation](queenswood-installation.md) — the private
  repository this reads for Queenswood, and where its three values are
  stored.
- [external-secrets](external-secrets.md) — how the key gets into that
  store, and out of it onto a cluster.
