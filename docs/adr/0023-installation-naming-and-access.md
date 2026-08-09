# 23. Installation naming and access

<!-- tessl-plugin: deployment -->

## Status

Accepted. Extends
[ADR-0022](0022-cloud-foundation-and-environment-lifecycle.md), which
established the folder as an installation but said nothing about what
things are called inside it or who may touch them.

## Context

The first installation was built by answering each question as it
arrived. A permission was missing, so it was granted; a name was needed,
so one was invented. The result works and cannot be explained: nothing
says why the bootstrap identity holds the roles it holds, why one group
is empty and another is not, or what a project should be called.

Two failures show the cost. Rights were withheld from the automation
that owns the folder in order to make the folder undeletable — which
fights the very model that makes the folder safe, and failed anyway,
because GCP grants a folder's creator administrative rights on it. And
names were chosen per resource, so `queenswood-mgmt-42529f` spends 16 of
the 30 characters a project id allows before saying anything specific.

The Google Cloud security foundations guide answers both, and its
answers are consistent with each other. Two lines carry most of it:

> Groups and users should not have any permissions to alter the
> foundation components laid out by the deployment pipeline unless they
> are one of the privileged identities.

> Eliminate or at least severely limit the use of primitive roles in
> your Google Cloud organization because the wide scope of permissions
> inherent in these roles goes against the principles of least
> privilege.

## Decision

### An installation has a code, and the code is in every name

Each installation takes a four-character code — `qw01`, `qw02` — chosen
when it is created and carried in its manifest. Names derive from it, so
nothing needs a lookup to place a resource, and a second installation
cannot collide with the first.

The code is not descriptive on purpose. A project id may be 30
characters, and the guide's own example spends 27, so the budget is
real: a code plus a one-letter environment leaves room for a label and a
uniqueness suffix, where a spelled-out name does not.

### Names follow the guide's scheme

Environment codes are the guide's: `b` bootstrap, `c` common, `d` dev,
`n` nonprod, `p` prod.

- folder — `fldr-qw01`
- project — `prj-qw01-<env>-<label>-<suffix>`, so `prj-qw01-c-mgmt-42529f`
  for the management project and `prj-qw01-d-a1b2c3` for a dev instance
- VPC — `vpc-qw01-c-mgmt`, subnet — `sb-qw01-c-mgmt-euw2`
- service account — `sa-qw01-platform`, `sa-qw01-boot`
- group — `grp-gcp-<label>` at the organisation,
  `grp-gcp-qw01-<label>` for an installation
- bucket — `bkt-qw01-<label>`
- custom role — `rl-<function>`

Two kinds the guide does not cover take the same shape: GKE cluster
`gke-qw01-mgmt`, node pool `np-qw01-mgmt`.

The suffix on a project is six hex characters rather than the guide's
five-digit number, because that is what generates a globally unique id
with the least ceremony. Everything else is as published.

### Kubernetes names mirror GCP names

A managed resource is named for what it manages: `fldr-qw01`,
`prj-qw01-c-mgmt`, `gke-qw01-mgmt`. So `kubectl get managed` and the
Cloud Console read the same, which is what someone debugging needs. It
costs explicit patches where a single format string would otherwise do.

### Inside the zone, automation owns everything

The folder is the boundary. Everything inside it is created and changed
by automation — the bootstrap identity until the management plane
exists, the platform identity afterwards. No human holds a write role
inside the folder, because there is nothing a human should be writing
that the manifest should not.

It follows that automation holds broad rights inside the zone, including
the administrative rights GCP grants a folder's creator. That is not a
leak to be closed. What restrains automation is its own declaration —
`managementPolicies` without `Delete`, `deletionProtection`, liens —
which is reviewable in a pull request, where an IAM binding scattered
across a policy is not.

### Humans are read-only or break-glass

There is no third category. Standing membership grants sight; changing
anything means joining a group that is normally empty, doing the work,
and leaving. Assuming an automation identity is a write capability and
belongs in the second category, not the first.

At the organisation, one set of groups:

- `grp-gcp-organization-admin` — organisation IAM. Empty.
- `grp-gcp-folder-admin` — the boundary itself. Empty. Organization
  Administrator does not carry `resourcemanager.folders.delete`.
- `grp-gcp-billing-admin` — the billing account. Empty, with one direct
  human administrator beside it, because a billing account has no
  recovery path outside its own policy.
- `grp-gcp-security-reviewer` — read-only IAM everywhere. Populated:
  auditing access must never require the power to change it.

Per installation, one set:

- `grp-gcp-qw01-operator` — read inside the folder. Populated.
- `grp-gcp-qw01-automation` — may assume the installation's automation
  identities. Empty.
- `grp-gcp-qw01-cluster-admin` — Kubernetes administration. Empty.
- `grp-gcp-qw01-secret-admin` — secret contents. Empty.

Roles bound to these are predefined and granular. Primitive roles —
Owner, Editor, Viewer — are not used, including for the operator's read
access, which is assembled from predefined viewer roles instead.

### Above the folder we consume, we do not manage

An installation needs an organisation, a billing account and a parent to
create in. It takes those as given and forms no opinion about them.
Domain-wide default grants, other folders, and organisation policy
outside the installation's own folder belong to whoever owns the
organisation, who may not be us.

## Consequences

**The first installation is rebuilt, and two project ids are
abandoned.** `queenswood-mgmt-42529f` and `queenswood-bootstrap-defec2`
cannot be renamed, and a project id is consumed permanently. Two ids is
a cheap price for not having to explain which names are legacy.

**Groups are recreated rather than renamed.** A group's email is its
identity in Workspace, so adopting the scheme means create, rebind,
delete.

**Codes are opaque, and descriptions carry the meaning.** `fldr-qw01`
tells a console browser nothing, so the folder's description says
"Queenswood installation 01" and projects carry labels. The name is for
machines and greppability; the description is for people.

**A per-installation group set multiplies with installations.** Four
groups each. That is the cost of bindings that name their scope, and the
alternative — one `cluster-admin` group bound on every folder — grants
across installations that are supposed to share nothing.

**The operator's read access is more work to assemble.** `roles/viewer`
would have been one binding; predefined viewer roles are several, and
the set grows as the installation gains resource types.

## References

- [Cloud security foundations guide](https://services.google.com/fh/files/misc/google-cloud-security-foundations-guide.pdf)
  — the August 2020 whitepaper this follows. The live URL now serves a
  stub; the intact editions are in the Wayback Machine.
- [ADR-0022](0022-cloud-foundation-and-environment-lifecycle.md) — the
  folder as an installation, and the lifecycle around it.

## Future

Whether instances get their own group sets — `grp-gcp-qw01-p-operator`
against the production instance alone — is left open. It follows the
same shape and should wait until an instance has someone to grant it to.
