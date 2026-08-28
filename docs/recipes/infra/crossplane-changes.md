# Changing a kind that already exists

<!-- tessl-plugin: deployment -->

## Status

**Untested**, derived from the four kinds extracted out of
`XQueenswoodInstance` — see
[composite-catalogue](../../plan/composite-catalogue.md) — and from
what those extractions cost. The queries in steps 1, 3 and 5 were run
on 2026-08-27 against this installation's plane; nobody has performed a
move by working down this page.

## Problem

You need to move composed resources into a kind of their own, or
withdraw a kind, without destroying what those resources manage.

## Solution

### Prerequisites

- A management plane running in the installation's folder.
- Steps 2 and 4 — write access to this repository, and a merge to
  `main`.
- Google group memberships, by capability:
  - Steps 1, 3 and 5 — `platformViewer`, e.g.
    `grp-gcp-<code>-platform-viewer@`.

```bash
# the installation code, e.g. qw01
export CODE=qw01
```

### 1. Read the slot names already in use

```bash
just crossplane-slots "$CODE-mgmt"
```

The `SLOT` column. The new kind's slots have to be names nothing in it
already carries.

### 2. Withhold `Delete` from what is moving

> [!WARNING]
> The `managementPolicies` on the **parent's** copy, at the moment the
> parent deletes it, is what decides whether the cloud resource
> survives. A resource still carrying `Delete` is destroyed on the way
> through rather than orphaned and re-adopted.

In `infra/platform/crossplane-xrds/<parent>-composition.yml`, drop
`Delete` from every resource that is moving:

```yaml
managementPolicies:
  - Observe
  - Create
  - Update
  - LateInitialize
```

Merge that on its own, before the change that moves anything.

### 3. Check the policy reached the plane

```bash
just crossplane-slots "$CODE-mgmt"
```

The `POLICIES` column, against the slots step 2 edited: every one of
them reads without `Delete`. Until it does, step 4 destroys what it was
meant to move.

### 4. Move the resources

In one change: delete the resources from
`infra/platform/crossplane-xrds/<parent>-composition.yml`, add
`infra/platform/crossplane-xrds/<child>-xrd.yml` and
`infra/platform/crossplane-xrds/<child>-composition.yml`, and compose
the child from the parent, with each resource's full policy in the
child's `base`, `Delete` included where it belongs.

Merge it. Two merges in all, not three.

### 5. Check the transfer finished

```bash
# the composite the resources moved out of, as kind.group/name, e.g.
export XR="xqueenswoodinstance.queenswood.repldriven.com/$CODE-n-test"

crossplane resource trace "$XR" -n crossplane-system -c "$CODE-mgmt" \
  -o wide
```

The moved resources appear under the child composite with `SYNCED` and
`READY` both `True`, which is a resource that came through: it was
deleted by the parent, recreated by the child, and adopted the cloud
resource back by external name.

### Withdrawing a kind

Count the live composites of the kind first, against the cluster:

```bash
# the kind being withdrawn, plural and lower-case, e.g.
export KIND=xpublicendpoints

kubectl --context "$CODE-mgmt" get "$KIND" -A
```

`No resources found` is the only answer that makes the next step a
removal rather than a deletion: the XRD owns the CRD it establishes, so
withdrawing it takes every composite of the kind, and each of those
deletes what it composes.

Then delete both
`infra/platform/crossplane-xrds/<kind>-xrd.yml` and
`infra/platform/crossplane-xrds/<kind>-composition.yml` in one change
and merge. The `crossplane-xrds` Application prunes, so the merge is
the removal: Argo prunes both objects, and Crossplane garbage-collects
the CRD beneath the XRD and the revisions beneath the Composition.

### Renaming a slot

The same procedure, one level down. A composed composite sits in a slot
like anything else, so renaming its slot deletes that composite, which
deletes what it composes. Withhold `Delete` from the resources
**inside** the child first, in a merge of its own, then rename, then
restore the policies.

## Failures

**A cloud resource destroyed by a change that was meant to move it.**
The transfer is a delete and a create rather than a handover: the
parent stops declaring the resource and garbage-collects its copy
before the child creates its own. The policy that governs is the one on
the parent's copy at that moment, so a child declaring `Observe,
Create, Update, LateInitialize` protects nothing that the parent still
held with `Delete`.

**A rename that deleted a cluster.** Renaming a composite's slot has a
blast radius of its grandchildren. The composite in the renamed slot is
deleted and rebuilt, and each resource it composed goes or stays on its
own policy — so a node pool carrying `Delete` goes with it while the
cluster survives, or does not, on what its own policy says.

**A new kind whose every apply fails, over a resource that is running
fine.** A slot name reused from one a live managed resource already
carries, which step 1 is what avoids — see
[crossplane-design](crossplane-design.md) for what the two claims do to
each other.

**A repository edit that did nothing.** Whether deleting a file removes
anything is a property of the Application carrying it, not of the file.
`crossplane-xrds` prunes; the Application carrying the installation
manifest deliberately does not, so a deletion there leaves the plane
serving what the repository no longer describes.

**A plane serving a kind whose XRD is gone.** Nothing links a
Composition to its XRD but a `compositeTypeRef`, so a Composition
outlives the XRD it names. Delete the two together, or the plane keeps
a Composition for a kind it no longer serves.

**A consumer that loses a value for the length of the move.** A
composite's status is derived rather than accumulated, so a field
published by a resource that is mid-transfer is absent while it is
gone, and server-side apply removes it from whatever was reading it.
See [crossplane-debug](crossplane-debug.md).

## Rules

**MUST:**

- Withhold `Delete` before moving a resource to another composite, in a
  change of its own that reaches the plane first. The transfer deletes
  the parent's copy, so the parent's policy is the one that governs.
- Read the live slot names before naming a composed resource in the new
  kind. Reusing one a live managed resource carries makes two
  composites claim it, and every apply then fails.
- Count the live instances of a kind before removing its XRD. The CRD
  and every composite of it go with the XRD.
- Delete a Composition alongside the XRD it names. Nothing links them
  but a `compositeTypeRef`.
- Read whether the Application carrying a file prunes before treating a
  deletion from the repository as a removal from the plane.
- Merge a change before expecting it on the plane. Argo reads the
  revision an Application names, never a working tree.

**MUST NOT:**

- Delete a composite to tidy up. It deletes what it composes, subject
  to each resource's `managementPolicies`.
- Rename a composite's slot without applying the same two-step to the
  resources inside it.
- Combine the policy change and the move into one merge. The plane
  applies what it reads, and it reads them in order.

## Discussion

We move resources between kinds by preparing what governs their
deletion first and moving them second, because Crossplane has no
handover: what looks like a transfer is a delete on one side and a
create on the other, joined only by the cloud resource's external name.

**Why the parent's policy is the one that counts.** The parent stops
declaring the resource, so Crossplane garbage-collects the managed
object it owns, and that deletion is governed by the policy on that
object. The child then creates its own managed object, observes the
cloud resource by external name, and adopts it. The child's policy has
had no opportunity to matter yet. Which is what makes the order
load-bearing rather than tidy: `Delete` withheld in the same merge as
the move is a policy that arrives with the deletion it was meant to
prevent, or after it.

**Why two merges rather than three.** The third would be restoring
`Delete` to the parent, and there is no parent left to restore it to.
The child declares the resource's real policy in its `base` from the
moment it exists, so the only window is between the two merges, and in
that window the resources are exactly where they were with one
capability withheld.

**What a withdrawal actually removes.** Three objects and a kind: the
XRD, the CRD it established, the Composition naming it, and every
composite anybody created. Only the first is in the repository, which
is why the count in the cluster is the step that decides whether this
is safe — nothing in the tree records that somebody applied a composite
of the kind by hand, and a private manifests repository is not
somewhere this repository can look.

## References

- [crossplane-design](crossplane-design.md) — how much one kind covers,
  and what may be deleted.
- [crossplane-debug](crossplane-debug.md) — what in an installation
  is not ready, or not what you declared.
- [argocd-apps](argocd-apps.md) — what prunes, and why a merged change
  reaches a plane when a working tree does not.
- [composite-catalogue](../../plan/composite-catalogue.md) — the
  extractions this was derived from, and what is left to do.
