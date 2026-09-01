# Up and running, end to end

<!-- tessl-plugin: deployment -->

## Status

**Untested as written**, and ahead of the recipes. Every step below has
been run and never in one sequence by one person. Step 1's seed
identity is still the first three of
[queenswood-bootstrap](queenswood-bootstrap.md)'s eleven steps, and
what that recipe calls step 5 onwards is step 4 here. Until they are
separated, read steps 1 and 4 out of the one recipe.

## Problem

You want a Queenswood instance serving traffic, starting either from no
Google Cloud at all or from a folder an organisation hands you.

## Solution

### Prerequisites

Two paths, and which one you are on decides where you start.

**From nothing**, being your own platform team. Start at step 1, and
you need:

- A domain, with access to edit its records at the registrar.
- A payment method, for a billing account.
- Somewhere to keep a private git repository, for the manifests.

Everything else is created on the way.

**From an established organisation.** Step 1 is its to do, however it
does it, so start at step 2. You need the domain and the repository
above, and from whoever runs the organisation:

- An IAM member string per capability the contract names — read
  [queenswood-secure-foundation](queenswood-secure-foundation.md) for
  what to ask for.
- Either a parent to create a folder under, or the id of a folder they
  hand you, written `folders/<folder-id>`.
- A four-character installation code, chosen before step 2 and never
  changed, since every name derives from it — see
  [cloud-naming](../practices/cloud-naming.md).

Whether they hand you a folder or a place to make one is a field in
step 2, not a different path: `folder.folderId` adopts, and
`folder.parent` with `folder.displayName` composes.

Each step is a recipe of its own. This page is the order they go in and
what each leaves for the next, and none of it is repeated here.

### 1. The organisation

[gcp-secure-foundation](gcp-secure-foundation.md), then steps 2, 3 and
11 of [queenswood-bootstrap](queenswood-bootstrap.md). Cloud Identity,
the domain verified against it, the organisation, a billing account,
the capabilities nobody holds by default, and the seed identity that
creates folders and projects on behalf of all of it.

Done once for an organisation rather than once per installation: the
seed project is reused where one exists, and its organisation-scoped
rights are opened for a bootstrap and closed after it.

Leaves: an organisation, a billing account, a super admin nobody signs
in as, and an identity that can create a folder.

### 2. The contract

[queenswood-secure-foundation](queenswood-secure-foundation.md) for the
principals, then `just queenswood-environment-manifest` for the file
that names them. Between them they settle what an organisation provides
and what we build: who holds which capability, which folder this
installation is, what pays for it, and where its manifests live.

The principals first and the file second, because IAM rejects a binding
to a principal that does not exist — and before step 3 rather than
before step 4, because the boundary reads two of the four keys.

Leaves: `environment.yml` committed, naming principals that exist.

### 3. The boundary

[boundary-install](boundary-install.md). The folder that the
installation is, and the capabilities bound inside it, declared by a
manifest carrying the code and built from the contract's `folder`
block: composed where the folder is ours, adopted where an organisation
hands one over.

Composed or adopted it leaves the same object, which is what makes this
a seam rather than a branch inside a later step.

Leaves: `subsidiary.yml` committed, and — once step 4 raises something
able to apply it — a folder with `platformViewer` and `clusterAdmin`
bound on it.

### 4. The management plane

[queenswood-bootstrap](queenswood-bootstrap.md), less the seed identity
that belongs to step 1 and the folder that belongs to step 3. A
throwaway control plane raises the management project and the cluster
inside the boundary, the composite pivots onto the cluster it built,
and the throwaway one is discarded.

Bringing it into service is part of this and not a step after it:
[queenswood-installation](queenswood-installation.md) still carries
that half — the Argo credential, the recovery project, the zone and its
delegation — and reads as a sixth step it is not. The credential in
particular belongs to the boot, since without it the plane reconciles
nothing while reporting healthy, which is an incomplete boot rather
than a later stage.

Leaves: a management plane reconciling the installation from git, and
an installation an instance can derive everything from.

### 5. An instance

[queenswood-instance](queenswood-instance.md). A unit: the project,
network, cluster, database and names one environment answers on, and
then the bank on top of it.

Leaves: a console answering at `https://console.<domain>`.

## Rules

**MUST:**

- Do these in order. Each leaves what the next reads, and every
  ordering hazard is one recipe assuming what an earlier one built.
- Choose the installation's code before step 2 and never change it.
  Every name derives from it, and nothing after step 2 chooses one.
- Answer every capability before the contract names it, since IAM
  rejects a binding to a principal that does not exist.
- Commit the contract before step 3. The boundary reads `access` and
  `folder` from it, and composes neither the bindings nor the folder
  without them.
- Prepare the domain before the zone and delegate it after — see
  [gcp-dns](gcp-dns.md) and
  [gcp-dns-delegation](gcp-dns-delegation.md), which step 4 sequences
  rather than either pointing at the other.

**MAY:**

- Start at step 2 where the organisation is established. That is the
  same page read from further down, never a step done differently —
  and a folder handed over is a field in the contract rather than a
  path of its own.
- Stop after step 4, which is an installation with no instance on it —
  valid, and what a platform team hands over.
- Run step 1 once for an organisation and steps 2 to 5 once per
  installation, where you run the organisation at all.

## Discussion

The order is not arbitrary and the dependencies run one way: a
directory before an organisation, principals before anything binds
them, a folder before a project inside it, a plane before anything it
reconciles, an installation before an instance derives from it.

Step 1 is a browser and nothing in it has an API — Cloud Identity, a
directory, a billing account — with the seed identity the one part that
is a shell. Everything from step 3 is a file in a repository. That is
the seam worth knowing about, because it is where the work stops being
performed and starts being recorded.

Step 3 is the other seam, and the more useful one. A folder is what an
installation is, so handing one over is the whole handover, and
`XSubsidiary` is the same object whether we composed it or adopted what
somebody gave us. That is why the three paths are three starting points
on one page rather than one page with a branch in it: the earlier
version of this recipe told you to read step 3 and do parts of it
differently, which is a step nobody can follow twice the same way.

What every path converges on is capabilities rather than groups. Every
recipe from step 3 asks for `platformViewer` rather than for
`grp-gcp-<code>-platform-viewer@`, which is only our worked answer.
An established organisation answers the same capabilities its own way,
and the boundary, the plane and the instance cannot tell which path
produced them.

## References

- [gcp-secure-foundation](gcp-secure-foundation.md) — step 1, and the
  seed identity in [queenswood-bootstrap](queenswood-bootstrap.md).
- [queenswood-secure-foundation](queenswood-secure-foundation.md) —
  step 2, and what to ask an organisation for.
- [boundary-install](boundary-install.md) — step 3.
- [queenswood-bootstrap](queenswood-bootstrap.md) — step 4, and step
  1's seed identity, which it does not yet separate.
- [queenswood-installation](queenswood-installation.md) — the rest of
  step 4, still written as though it followed one.
- [queenswood-instance](queenswood-instance.md) — step 5.
- [ADR-0022](../../adr/0022-cloud-foundation-and-environment-lifecycle.md)
  — the folder as the boundary an installation occupies.
- [ADR-0023](../../adr/0023-installation-naming-and-access.md) — the
  code, and who holds which capability.
- [ADR-0027](../../adr/0027-the-folder-is-a-subsidiary.md) — the folder
  as its own kind, and the handover in either direction.
