# GCP IAM for automation
<!-- tessl-plugin: deployment -->

## Problem

An automation identity works until the thing that created it goes away,
because most of what it could do was never granted to it.

## Solution

Grant explicitly, at the narrowest scope that works, and audit before
discarding whatever built the project.

### Rights held by accident

Whoever creates a project becomes its owner. A bootstrap identity
therefore holds every permission on what it built, none of it declared
— so a composition works under it and fails under the identity that
inherits it.

Enumerate before handing over: for each resource, would the inheriting
identity alone have the rights to create it? Found this way rather than
by failing: `iam.serviceAccounts.actAs` on a node identity,
`iam.serviceAccountAdmin`, `resourcemanager.projects.setIamPolicy`,
`serviceusage.services.enable`.

Two are circular. The binding that repairs a missing
`projects.setIamPolicy` is itself a project IAM binding, so an identity
that lacks it cannot grant it back.

### Workload Identity is two halves

A GCP binding of `roles/iam.workloadIdentityUser` for
`serviceAccount:<project>.svc.id.goog[<namespace>/<ksa>]`, **and** the
annotation `iam.gke.io/gcp-service-account` on that Kubernetes service
account. Without the second, a token minted for it is exchangeable for
nothing.

For Crossplane's providers the service account name must be pinned by a
`DeploymentRuntimeConfig`; left generated, the binding matches nothing.

### Node identities

Never the default compute service account. It is shared by everything
in the project that chose none, and holds whatever the organisation's
policy on automatic grants leaves it holding — nothing where
`iam.automaticIamGrantsForDefaultServiceAccounts` is enforced, Editor
where it is not. A node pool requests `cloud-platform` scopes, so
whatever it holds reaches every workload through the metadata server.

Grant a dedicated account `roles/container.defaultNodeServiceAccount`,
which is narrower than the four-role list older guidance gives and
carries `autoscaling.sites.writeMetrics`, which none of them do.

Attaching a service account to anything requires
`iam.serviceAccounts.actAs` on it.

### Roles and scopes

A role has resource types it may be granted on, and that set is not the
set of resources the feature acts on. `roles/orgpolicy.policyAdmin` is
organisation-only, though the policy it sets is applied per project.
The refusal is a 400 declining the scope, not a permission the caller
lacks, so no upstream grant fixes it.

`roles/container.viewer` does not carry `container.pods.getLogs`, and
the only predefined role that does also grants exec and every write. A
project custom role with the one permission is the answer where an
organisation role is not ours to define. Custom role ids take letters,
numbers, underscores and periods — never hyphens.

### Credentials

`gcloud auth login` does not refresh application-default credentials.
Impersonation then fails minutes later, inside whatever is using it,
as `invalid_rapt` or a denied `getAccessToken`.

Console recommender insights are worth checking rather than dismissing,
and are generated on a schedule — they lag a fix by up to a day.

## Rules

**MUST:**

- Give every node pool its own service account with
  `roles/container.defaultNodeServiceAccount`.
- Grant both halves of Workload Identity, and pin the Kubernetes
  service account name.
- Audit an inheriting identity against every resource it must manage,
  before the identity that created them is discarded.
- Prefer a project custom role over a predefined role that grants
  writes you do not want, and name it with underscores: a custom role
  id takes no hyphens.
- Grant `iam.serviceAccounts.actAs` on any service account something
  must attach to a resource.

**MUST NOT:**

- Rely on the default compute service account being powerless. That is
  an org policy enforced elsewhere.
- Assume a role can be granted at the scope its feature acts on.
- Assume `gcloud auth login` refreshed ADC.

## References

- [cloud-foundation](cloud-foundation.md) — the identities an
  installation has, and why each is separate.
- [crossplane-providers](crossplane-providers.md) — how a provider
  authenticates as one.
