# An installation's access groups

<!-- tessl-plugin: deployment -->

## Status

**Verified.** One installation's groups were created this way.

## Problem

You are about to build an installation, and its manifest will name
principals that have to exist before it does.

## Solution

### Prerequisites

- An organisation with its own groups, from
  [cloud-groups](cloud-groups.md).
- The super admin, which is what creates groups in the directory.
- The installation's four-character code, chosen now and carried by
  every name it will ever have — see [cloud-naming](cloud-naming.md).

Four groups, coded to the installation and deleted with it. `qw01`
stands in for the code below.

### 1. Create them

In `admin.google.com` under **Directory, then Groups**, exactly as
[cloud-groups](cloud-groups.md) creates the organisation's: Restricted
before Only invited users, and no owner or manager.

- **`grp-gcp-qw01-platform-viewer@`** — Populated. *"Reads qw01 and
  everything inside it, and writes nothing. Populated: this is
  day-to-day operation."*
- **`grp-gcp-qw01-platform-admin@`** — Empty. *"Assumes the identity that
  runs qw01, which administers all of it. Break-glass: join for the
  task, then leave."*
- **`grp-gcp-qw01-cluster-admin@`** — Empty. *"Administers qw01's
  Kubernetes clusters directly. Break-glass: join for the task, then
  leave. Acting on a cluster by hand bypasses whatever reconciles it."*
- **`grp-gcp-qw01-secrets-admin@`** — Empty. *"Reads and manages qw01's
  secrets. Break-glass: join for the task, then leave. Handling secret
  contents is a different job from running the infrastructure that
  holds them."*

### 2. Bind the one that reaches the organisation

```bash
gcloud config unset project
just gcp-groups-bind
```

`grp-gcp-qw01-platform-viewer@` takes Browser at the organisation.
Hierarchy metadata: tooling cannot reach a folder without first
resolving the organisation holding it.

Nothing else here is bound at the organisation. The rest is folder and
project scoped, and reaches these groups through the installation's
manifest — which is [queenswood-bootstrap](queenswood-bootstrap.md),
and needs them to exist by the time it renders one.

## Failures

**An `access` mapping the manifest accepts and IAM refuses.** A group
named there does not exist. IAM rejects the binding rather than the
manifest, so the composite reports the failure and the file looks
correct.

## Rules

**MUST:**

- Create an installation's groups before the manifest that names them
  is rendered.
- Name them for the installation's code, which is chosen here and never
  changed.
- Create each without an owner or a manager, Restricted before Only
  invited users.
- Bind `platform-viewer` at the organisation with
  `just gcp-groups-bind`, which is where Browser has to be.

**MUST NOT:**

- Leave anybody standing in one of the three break-glass groups.
- Bind the other three at the organisation. They are folder and project
  scoped, and the manifest is what grants them.

**MAY:**

- Create no groups at all and install with an empty `access` mapping,
  which is an installation nobody can reach but which reconciles
  correctly.
- Answer a capability with something other than a group — a user, or a
  `principalSet://` from an external provider — since the manifest
  takes whole IAM member strings.

## Discussion

These exist before the installation rather than after it because the
manifest names them, and IAM rejects a binding to a principal that is
not there. The alternative — install with an empty mapping and add
capabilities in a second merge — works and is worth knowing about, but
it leaves an installation nobody can read for as long as it takes
somebody to notice.

Creating them cannot be scripted, for the reason
[cloud-groups](cloud-groups.md) gives: every Cloud Identity write
attributes quota to a project, and at this point the installation has
none. Binding needs no quota project, which is why one half is a recipe
and the other is a browser.

Four rather than one because they separate capabilities that must be
held at different times by different people: reading an installation is
day-to-day, assuming the identity that runs it is not, administering a
cluster by hand bypasses what reconciles it, and handling secret
contents is a different job from running what holds them. See
[ADR-0023](../../adr/0023-installation-naming-and-access.md).

## References

- [cloud-groups](cloud-groups.md) — the organisation's four, which come
  first.
- [queenswood-bootstrap](queenswood-bootstrap.md) — what needs these to
  exist.
- [cloud-naming](cloud-naming.md) — the code they are named for.
- [ADR-0023](../../adr/0023-installation-naming-and-access.md) — the
  capabilities and who holds them.
