# Up and running, end to end

<!-- tessl-plugin: deployment -->

## Status

**Untested as written.** Every recipe below has been run, and never in
one sequence by one person. Expect the first run to find an ordering
this page states wrongly rather than a recipe it omits.

## Problem

You want a Queenswood instance serving traffic, starting either from no
Google Cloud at all or from an established organisation.

## Solution

### Prerequisites

Two paths, and which one you are on decides where you start.

**From nothing**, being your own platform team. Start at step 1, and
you need:

- A domain, with access to edit its records at the registrar.
- A payment method, for a billing account.
- Somewhere to keep a private git repository, for the manifests.

Everything else is created on the way.

**From an organisation that manages its own groups.** Steps 1 and 2 are
its to do, however it does them, so start at step 3. You need the same
domain and repository as above, and from whoever runs the organisation:

- A folder, or a parent to create one under.
- An IAM member string per capability the manifest names — read
  [queenswood-secure-foundation](queenswood-secure-foundation.md) for
  what to ask them for.
- A four-character installation code, chosen before step 3 and never
  changed, since every name derives from it — see
  [cloud-naming](../practices/cloud-naming.md).

Each step is a recipe of its own. This page is the order they go in and
what each leaves behind for the next; none of it is repeated here.

### 1. The organisation

[gcp-secure-foundation](gcp-secure-foundation.md). Cloud Identity, the
domain verified against it, the organisation, a billing account, and
capabilities nobody holds by default. Not yours on the second path: a
directory you joined is not yours to add groups to.

Leaves: an organisation, a billing account, and a super admin nobody
signs in as.

### 2. The installation's capabilities

[queenswood-secure-foundation](queenswood-secure-foundation.md). The
same separation applied to the part of the organisation this
installation occupies: read-only or break-glass, nobody holding a
standing write right, and the people who operate it in the one
capability that is populated. On the second path this is what to ask the
organisation for rather than something you do.

Leaves: principals that exist, so IAM accepts the bindings step 4
declares, and somebody who can read what they build.

### 3. The installation's plane

[queenswood-bootstrap](queenswood-bootstrap.md). The folder that is the
installation, and the management plane inside it: a throwaway control
plane raises the folder, the management project and the cluster, then
the composite pivots onto the cluster it built and the throwaway one is
discarded.

Leaves: a management plane reconciling the installation from git.

### 4. The installation in service

[queenswood-installation](queenswood-installation.md). Bringing the
management plane into service, so that an instance has something to
derive from.

Leaves: an installation an instance can derive everything from.

### 5. An instance

[instance-deploy](instance-deploy.md). A unit: the project,
network, cluster, database and names one environment answers on, and
then the bank on top of it.

Leaves: a console answering at `https://console.<domain>`.

## Rules

**MUST:**

- Do these in order. Each leaves what the next reads, and every ordering
  hazard below is one recipe assuming what an earlier one built.
- Choose the installation's code before step 3 and never change it.
  Every name derives from it. Step 2 is where it is chosen on the first
  path; on the second, nothing else chooses one.
- Answer every one of an installation's capabilities before the manifest
  that names them, since IAM rejects a binding to a principal that does
  not exist.

**MAY:**

- Start at step 3 where you belong to an organisation that manages its
  own groups. Steps 1 and 2 produce capabilities and nothing else, and
  such an organisation produces them its own way.
- Stop after step 4, which is an installation with no instance on it —
  valid, and what a platform team hands over.
- Run step 1 once for an organisation and steps 2 to 5 once per
  installation, where you are running the organisation at all.

## Discussion

The order is not arbitrary and the dependencies run one way: a directory
before an organisation, capabilities before the manifest that names
them, a plane before anything it reconciles, an installation before an
instance derives from it. Where two recipes interleave — the domain,
which is prepared before the zone and delegated after it — the
installation recipe sequences them rather than either pointing at the
other.

Steps 1 and 2 are a browser, and nothing there has an API: Cloud
Identity, a directory, a billing account. Steps 3 to 5 are a shell and a
merge. That is the seam worth knowing about, because it is also where
the work stops being repeatable and starts being recorded — everything
from step 3 is a file in a repository, and everything before it is an
act somebody performed.

It is also where the two paths converge. The first two steps produce
capabilities and nothing else, and every recipe from step 3 on asks for
a capability rather than for a group: `platformViewer`, e.g.
`grp-gcp-<code>-platform-viewer@`. Ours is the worked answer for
somebody who is their own platform team. An established organisation
answers the same capabilities its own way, and the plane, the
installation and the instance cannot tell which path produced them.

## References

- [gcp-secure-foundation](gcp-secure-foundation.md) — step 1.
- [queenswood-secure-foundation](queenswood-secure-foundation.md) —
  step 2.
- [queenswood-bootstrap](queenswood-bootstrap.md) — step 3.
- [queenswood-installation](queenswood-installation.md) — step 4.
- [instance-deploy](instance-deploy.md) — step 5.
- [ADR-0022](../../adr/0022-cloud-foundation-and-environment-lifecycle.md)
  — the folder as an installation.
- [ADR-0023](../../adr/0023-installation-naming-and-access.md) — the
  code, and who holds which capability.
