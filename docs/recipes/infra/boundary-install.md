# Declaring the boundary an installation occupies

<!-- tessl-plugin: deployment -->

## Status

**Untested as written.** The composite has been applied, and never from
this recipe: `qw01`'s folder was created by an earlier shape of the
bootstrap and adopted afterwards.

## Problem

You want the folder an installation lives in, and the capabilities
bound inside it, declared in the manifests repository — whether you are
creating that folder or were handed one.

## Solution

### Prerequisites

- The installation's code, chosen and never changed, from
  [cloud-naming](../practices/cloud-naming.md).
- Its contract committed:
  [queenswood-secure-foundation](queenswood-secure-foundation.md) for
  the principals, and `environment.yml` beside it naming them.
- In that file, either a `folder.parent` and `folder.displayName`, or a
  `folder.folderId`.
- Write access to the manifests repository, and a merge.

```bash
# the installation code, e.g.
export QW_CODE=qw01
# the private manifests repository, wherever it is checked out
export QW_INSTALLATIONS_REPO=../installations
```

### 1. Read the folder block from the contract

```bash
grep -A3 '^  folder:' "$QW_INSTALLATIONS_REPO/$QW_CODE/environment.yml"
```

Either a `folderId`, or a `parent` and a `displayName`. Neither means
the contract is unfinished: go back to
[queenswood-secure-foundation](queenswood-secure-foundation.md).

### 2. Render the manifest

```bash
just queenswood-subsidiary-manifest \
  > "$QW_INSTALLATIONS_REPO/$QW_CODE/subsidiary.yml"
```

One key under `spec`, the code, at the top of the installation's
directory rather than inside it.

### 3. Read it, then commit it

```bash
cat "$QW_INSTALLATIONS_REPO/$QW_CODE/subsidiary.yml"
```

### 4. Apply it

On a first installation, nothing: `just gcp-boot-cluster-up` and `just
gcp-boot-mgmt-apply` do it, in
[queenswood-bootstrap](queenswood-bootstrap.md).

Against a management plane already running, merge. Argo applies it from
the installation's directory.

### 5. Read back what it built

```bash
kubectl --context "$QW_CODE-mgmt" -n crossplane-system \
  get xsubsidiary "$QW_CODE" -o yaml
```

`status.folderId` carries the folder, composed or adopted.

## Failures

**`Unsynced resources: folder`, while the folder reports `Synced`.**
The apply is being rejected rather than the folder being wrong. Read
the composite's events for the reason — a `Folder` carrying `Create` or
`Update` in `managementPolicies` and no `displayName` is invalid, and
the provider says so on every apply while the composite reports only
that something is unsynced.

**Two folders under the parent.** `folderId` was absent when the
contract still named a `parent` and a `displayName`, so the composite
composed rather than adopted. GCP permits two folders with one display
name under a parent, so nothing refuses it. Count them before trusting
a composite that reports healthy.

## Rules

**MUST:**

- Commit the contract before this. The boundary reads `access` and
  `folder` from it and composes neither the bindings nor the folder
  without them.
- Put the manifest at the top of the installation's directory, never
  inside a subdirectory of it.
- Prove an adoption by counting folders under the parent rather than by
  reading the composite, which reports healthy either way.

**MUST NOT:**

- Name a principal in the contract's `access` that does not exist. IAM
  rejects the binding, not the file.
- State `parent` and `displayName` beside a `folderId` and expect them
  to apply. They are ignored, and removing the `folderId` line later
  arms a second folder.

**MAY:**

- Re-render this manifest at any time. It carries nothing generated.
- Declare a boundary with an empty `access` mapping, which reconciles
  correctly and which nobody can reach.

## Discussion

The folder is what an installation is, so this is the whole handover:
one kind, two suppliers, and the same object left behind either way.
That is why it is a step rather than a branch inside the bootstrap,
where it used to be — a page that says "steps 1 to 3 are theirs, and
read this one differently" is a page nobody follows twice the same way.

The manifest carries the code alone, which looks thin until you notice
what it is for. It is the XR: applying it is what instantiates the
kind, and everything the kind needs is stated once in the contract
where the plane and every instance read it too. A folder handed over
changes one field in one file rather than a manifest and a procedure.
Nothing in it is generated, so it may be re-rendered freely — which the
contract beside it may not, since that one carries the folder id.

It sits at the top of the installation's directory because the
Application that syncs it does not recurse: a manifest filed in a
subdirectory is never applied at all, and nothing reports that.

The two modes are exclusive by precedence rather than by refusal. Where
`folderId` is set the other two are ignored, so stating all three is
inert rather than an error — the better failure, since the contract is
read by three composites and rejecting it would stop the plane and
every instance rather than this alone.

An adopted folder is observed and never written. `folderId` withholds
`Create` and `Update` from the composed `Folder`, so the provider
cannot rename it or move it whatever the spec says — which matters more
than it reads, since an empty `displayName` renames a folder and an
empty `parent` moves it. See
[ADR-0027](../../adr/0027-the-folder-is-a-subsidiary.md).

## References

- [queenswood-secure-foundation](queenswood-secure-foundation.md) — the
  contract, and the principals it names.
- [queenswood-bootstrap](queenswood-bootstrap.md) — what applies this
  on a first installation, and the plane built inside it afterwards.
- [queenswood-up-and-running](queenswood-up-and-running.md) — where
  this sits in the order.
- [ADR-0027](../../adr/0027-the-folder-is-a-subsidiary.md) — the folder
  as its own kind, and the handover in either direction.
- [ADR-0023](../../adr/0023-installation-naming-and-access.md) — the
  capabilities bound here, and who holds them.
