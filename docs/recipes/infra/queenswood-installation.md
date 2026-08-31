# Bringing an installation into service

<!-- tessl-plugin: deployment -->

## Status

**Untested as written.** One installation was built this way, but over
many changes rather than in this order, and several steps below are a
hand-edited file where a recipe ought to be. Expect the first run to
find a missing primitive rather than a wrong instruction.

## Problem

You have a plane, and want an installation that can serve instances.

## Solution

### Prerequisites

- A management plane, built by
  [queenswood-bootstrap](queenswood-bootstrap.md), reconciling its own
  manifest.
- Owner of the GitHub organisation holding the private manifests
  repository, for step 1 and nothing else.
- A domain you can prove ownership of, for steps 3 to 5 — see
  [cloud-dns](cloud-dns.md) and
  [cloud-dns-delegation](cloud-dns-delegation.md).
- Write access to the manifests repository, and a merge, for steps 2
  and 4.
- The capability each step names. Ours is a Google group; yours may differ.

```bash
# the installation code, cloud-naming's <code>, e.g.
export QW_CODE=qw01
# the private manifests repository, wherever it is checked out
export QW_INSTALLATIONS_REPO=../installations
```

### 1. Give Argo the credential for the private repository

**As the installation's secrets admin.** Ours is
`grp-gcp-<code>-secrets-admin@` — join for this step, then leave.

The plane reconciles from a repository it cannot yet read, and
[argocd-github](argocd-github.md) is the whole of that: it names the
repository in the manifest, creates a GitHub App, installs it on that
repository, and stores the App ID, Installation ID and private key
together in the one entry the composite made for them.

Come back when the `installation` Application reports `Synced`.

### 2. Render the installation's environment

**As the installation's platform viewer, from here on.** Ours is
`grp-gcp-<code>-platform-viewer@`, populated rather than joined.

```bash
just queenswood-environment-manifest
```

> [!WARNING]
> Re-render as often as you like until the environment is committed: a
> plane reads it from git, so nothing was built and the recovery
> project id it minted means nothing. Once it is committed that file
> may be the only record of an id GCP has consumed, and the recipe
> refuses rather than minting a second.

Commit and merge the new environment manifest in
`QW_INSTALLATIONS_REPO`.

```bash
just crossplane-conditions "xmanagementplane/$QW_CODE"
```

`Ready` back to `True`, with the recovery project composed under it.

### 3. Prepare the domain

[cloud-dns](cloud-dns.md) is the whole of this: it verifies the domain,
adds the automation identity as an owner of the property, inventories
what the registrar serves, and unsigns the domain where it is signed.

Come back when `just dns-carried <domain>` names a verification token.

### 4. Compose the zone

```bash
just queenswood-dns-manifest-snippet <domain>
```

Paste it into `spec` in `<code>/installation.yml`, and merge.

```bash
just queenswood-zone-nameservers
```

Four names, which nothing is delegated to yet. Every instance writes
its records into this one zone.

### 5. Move the delegation

[cloud-dns-delegation](cloud-dns-delegation.md) is the whole of this:
it diffs the two authorities, replaces all four nameservers at the
registrar, and checks the delegation at the registry.

Come back when the verification TXT answers from the new authority.

### 6. Check it can take an instance

```bash
just crossplane-unready
```

A header line with nothing under it. The installation now carries
everything an instance derives from it: the folder, the billing
account, Argo's identity, the recovery project and the zone.

Adding one is [queenswood-instance](queenswood-instance.md).

## Failures

**A plane that is `Ready` and reconciling nothing.** The manifest exists
in a checkout and was never pushed. Applying from a boot plane reads a
working tree and is satisfied by it, so everything up to the handover
passes; afterwards the plane reads the revision its Application names
and finds no file.

**A repository reported unreachable.** Read this as an entry with no
version before reading it as a wrong credential. The composite composes
the container and a person adds the value, and between the two Argo
holds nothing for the repository it reconciles from.

**An instance project that comes up unbilled.** The environment was
added after the instance rather than before. Nothing else declares a
billing account: the field is absent from the managed resource and from
`atProvider`, so a project billed once stays billed and one never
billed stays that way.

**A composed name that moved on its own.** A field the XRD defaults was
left unstated, and the default changed. Where the field is immutable —
a region, a machine type, a subnet range — the change is refused rather
than applied, and the refusal is in `LastAsyncOperation` while `Synced`
goes on reading `True`.

## Rules

**MUST:**

- Change what exists by editing the manifest and merging it, never by
  acting on GCP.
- Apply from merged state only. A `pull_request` trigger gets no cloud
  identity, and a fork's would otherwise run as the platform identity.
- Push the manifest before a plane takes over reading it from git.
- Give Argo the credential for the manifests repository before
  expecting any later merge to reach the plane at all.
- Supply `management.projectId` always, and `createFolder.folderId`
  wherever the folder already exists.
- Give `metadata.name` and `spec.code` the same string. Nothing
  enforces it, and the tooling assumes it.
- State `region`, `regionCode`, `zone` and anything else immutable
  rather than leaving it to a default that may move.
- Render the environment with `just queenswood-environment-manifest`,
  and merge it before the first instance is composed.
- Keep the manifests repository private, and read `status` back rather
  than committing it.

**MUST NOT:**

- Commit anything secret beside the manifest.
- Name a principal in `access` that does not exist.
- Create a key for any identity the installation composes.
- Leave a new XRD field required, when the manifest that sets it lives
  in another repository.
- Delete and recreate the public zone. The nameservers change with it
  and the registrar does not follow.
- Retype a verification token into the manifest.
  `just queenswood-dns-manifest-snippet` renders the block from what the domain
  answers, and a token that only exists in the file proves nothing.

**MAY:**

- Point `management.source` at upstream, a fork, or a mirror that
  vendors this layout, and pin `targetRevision` to a tag.
- Install with an empty `access` mapping and add capabilities later,
  which is an installation nobody can reach but which reconciles
  correctly.
- Adopt an existing recovery project by passing its id, where one was
  made outside this path.
- Create more than one installation. One manifest per folder.

## Discussion

An installation is a folder, and its manifest is one file. What
bootstrap leaves behind is a plane that can apply that file; what this
page adds is everything an instance later derives from it. Each step is
a merge, because a merge is the privileged act — the only one that runs
as the platform identity.

**Why the credential comes first.** Every step after it is a merge, and
a merge only means something to a plane that can read the repository.
Until the App's values are in Secret Manager, the plane reconciles from
nothing while reporting healthy, which is the first of the two states
that pass every check.

**Why the two repositories are separate.** The source holds the XRD and
the composition — what an `XManagementPlane` means — and may be public,
forked or mirrored, with `targetRevision` pinned by anyone who reviews
what they run. The manifests hold one file per installation and are
private, because a manifest is identifiers: the organisation, the
folder, the billing account, the project ids. Nothing in it is secret
and nothing in it wants indexing either. The cost is that a composition
change and the manifest change it needs land in different repositories,
which is why a new XRD field is added optional and defaulted rather
than required.

**Why the environment is a second file.** An `EnvironmentConfig` is
cluster-scoped, so nothing composes it — a namespaced composite may not
compose a cluster-scoped kind — and Argo applies it from the same
directory like everything else. It holds what is true of the whole
installation and carries no naming or ordering consequence: the billing
account qualifies, and so does Argo's own address, since an instance
grants Argo access to its project and needs the full email, which
combines a composite field with an environment one. A region does not
qualify, because it is baked into names, and an installation-wide
default would silently want to rebuild every instance's subnet and
cluster when edited.

**Why the order is what it is.** The credential first, or nothing
lands. The environment before any instance, because resolution is
`Required` and a missing config is a composite that says so rather than
a project that comes up unbilled. The domain prepared before the zone
is composed and delegated after it, because a delegation to a zone that
does not answer is an outage.

**What has no primitive yet.** Step 4 is hand-edited YAML in the
private repository, where step 2 has a recipe. The
renderer that produces a first manifest mints ids and writes the whole
file, so it cannot be used to add a block to a manifest that already
exists — which is what step 4 does. Until something can, the guard
against a second render losing what was hand-added is that the file is
committed and the renderer refuses.

## References

- [queenswood-bootstrap](queenswood-bootstrap.md) — building the plane
  that reads the manifest.
- [queenswood-instance](queenswood-instance.md) — adding an instance to
  what this leaves.
- [argocd-github](argocd-github.md) — the App that reaches a private
  repository, and how it is rotated.
- [cloud-dns](cloud-dns.md) — proving the domain, and moving a
  registrar once.
- [gcp-secure-foundation](gcp-secure-foundation.md) — the organisation,
  the access
  groups and the billing account, none of which has an API.
- [cloud-naming](cloud-naming.md) — the code, and what every name
  derives from it.
- [ADR-0022](../../adr/0022-cloud-foundation-and-environment-lifecycle.md)
  — the folder as an installation, and why foundations are not deleted.
- [ADR-0023](../../adr/0023-installation-naming-and-access.md) — the
  capabilities and who holds them.
- `infra/platform/crossplane-xrds/xmanagementplane-xrd.yml` — every
  field named above, as a schema.
