# Up and running, end to end

<!-- tessl-plugin: deployment -->

## Status

**Untested as written.** Every recipe below has been run, and never in
one sequence by one person. Expect the first run to find an ordering
this page states wrongly rather than a recipe it omits.

## Problem

You have nothing, and want a Queenswood instance serving traffic.

## Solution

### Prerequisites

- A domain, with access to edit its DNS at the registrar.
- A payment method, for a billing account.
- Somewhere to keep a private git repository, for the manifests.

Nothing else. Everything below is created on the way.

Each step is a recipe of its own. This page is the order they go in and
what each leaves behind for the next; none of it is repeated here.

### 1. The organisation

[cloud-account](cloud-account.md). Cloud Identity, the domain verified
against it, the organisation, an operating user and a billing account.

Leaves: an organisation, and a super admin nobody uses day to day.

### 2. The organisation's groups

[cloud-groups](cloud-groups.md). Four groups in the directory, bound at
the organisation, so that no person holds a standing right in it.

Leaves: break-glass groups, empty, and one populated reviewer.

### 3. The installation's groups

[queenswood-groups](queenswood-groups.md). Four more, coded to the
installation, which the manifest in step 4 names.

Leaves: principals that exist, so IAM accepts the bindings step 4
declares.

### 4. The plane

[queenswood-bootstrap](queenswood-bootstrap.md). A throwaway control
plane raises the folder, the management project and the cluster, then
the composite pivots onto the cluster it built and the throwaway one is
discarded.

Leaves: a plane reconciling the installation's manifest from git.

### 5. The installation

[queenswood-installation](queenswood-installation.md). The credential
that lets the plane read its manifests, the environment every composite
resolves from, and the public zone — which is
[cloud-dns](cloud-dns.md) and
[cloud-dns-delegation](cloud-dns-delegation.md) either side of it.

Leaves: an installation an instance can derive everything from.

### 6. An instance

[queenswood-instance](queenswood-instance.md). A unit: the project,
network, cluster, database and names one environment answers on, and
then the bank on top of it.

Leaves: a console answering at `https://console.<domain>`.

## Rules

**MUST:**

- Do these in order. Each leaves what the next reads, and every
  ordering hazard below is one recipe assuming what an earlier one
  built.
- Choose the installation's code at step 3 and never change it. Every
  name derives from it.
- Create groups before the manifest that names them, since IAM rejects
  a binding to a principal that does not exist.

**MAY:**

- Stop after step 5, which is an installation with no instance on it —
  valid, and what a platform team hands over.
- Run steps 1 and 2 once for an organisation and steps 3 to 6 once per
  installation.

## Discussion

The order is not arbitrary and the dependencies run one way: a
directory before an organisation, groups before the manifest that names
them, a plane before anything it reconciles, an installation before an
instance derives from it. Where two recipes interleave — the domain,
which is prepared before the zone and delegated after it — the
installation recipe sequences them rather than either pointing at the
other.

Steps 1 to 3 are a browser, and nothing there has an API: Cloud
Identity, a directory, a billing account. Steps 4 to 6 are a shell and
a merge. That is the seam worth knowing about, because it is also where
the work stops being repeatable and starts being recorded — everything
from step 4 is a file in a repository, and everything before it is an
act somebody performed.

## References

- [cloud-account](cloud-account.md) — step 1.
- [cloud-groups](cloud-groups.md) — step 2.
- [queenswood-groups](queenswood-groups.md) — step 3.
- [queenswood-bootstrap](queenswood-bootstrap.md) — step 4.
- [queenswood-installation](queenswood-installation.md) — step 5.
- [queenswood-instance](queenswood-instance.md) — step 6.
- [ADR-0022](../../adr/0022-cloud-foundation-and-environment-lifecycle.md)
  — the folder as an installation.
- [ADR-0023](../../adr/0023-installation-naming-and-access.md) — the
  code, and who holds which capability.
