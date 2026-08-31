# Changing a live resource

<!-- tessl-plugin: deployment -->

## Status

**Untested**. The move is derived from the four kinds extracted out of
`XQueenswoodInstance` — see
[composite-catalogue](../../plan/composite-catalogue.md) — and its
queries were run against this installation's plane on 2026-08-28.
Nobody has worked down this page, and branch 2b has not been
performed at all.

## Problem

You need to change something about a Crossplane resource that already
exists, and you need to know whether the change applies, is refused, or
destroys.

## Solution

### Prerequisites

- A management plane running in the installation's folder.
- Every branch — write access to this repository, and a merge to
  `main`.
- The capability each branch names. Ours is a Google group; yours may differ.

```bash
# the installation code, e.g. qw01
export QW_CODE=qw01
```

### 1. Determine the type of change

**As the installation's platform viewer.** Ours is
`grp-gcp-<code>-platform-viewer@`, populated rather than joined.

**These apply on their own, and no branch below is involved.**

- **A field the composition owns, that the cloud can update.** Edit the
  Composition and merge. Every live composite of the kind takes it on
  its next reconcile.
- **A field nobody owns.** A `kubectl patch` holds until something
  claims it.
- **A field the provider late-initialised.** Adding a patch takes the
  field from the provider; deleting one removes it before the provider
  writes it back.

Which of the three a field falls into is a question about ownership,
and `just crossplane-owners` answers it —
[crossplane-debug](crossplane-debug.md) is where that is read.

**This one does nothing, and says it worked.**

- **A field the provider writes once, at create.** A write-only field —
  `google_sql_user`'s password is the worked example — cannot be read
  back, so upjet observes no drift and has nothing to reconcile. The
  managed resource reports `Synced: True` for ever, and the value in
  the cloud stays whatever it was created with. Set it out of band, or
  destroy and rebuild the resource.

**These destroy and recreate something.** What each costs is the
resource's `managementPolicies`, so read those before starting rather
than after.

- **Which composite composes it** — step 2a.
- **A field that identifies the resource** — step 2b. Identity is not
  visible anywhere, not in the CRD and not in the object, which is why
  it has a branch rather than a lookup.
- **What a slot is called** — step 2c.
- **Whether the kind exists at all** — step 2d.

### 2a. Move a resource to another composite

1. Read the slot names already in use. The new kind's slots have to be
   names nothing in the `SLOT` column already carries.

   ```bash
   just crossplane-slots "$QW_CODE-mgmt"
   ```

2. Withhold `Delete` from what is moving, in
   `infra/platform/crossplane-xrds/<parent>-composition.yml`, and merge
   that on its own before the change that moves anything.

   ```yaml
   managementPolicies:
     - Observe
     - Create
     - Update
     - LateInitialize
   ```

   > [!WARNING]
   > The `managementPolicies` on the **parent's** copy, at the moment
   > the parent deletes it, is what decides whether the cloud resource
   > survives. A resource still carrying `Delete` is destroyed on the
   > way through rather than orphaned and re-adopted.

3. Check the policy reached the plane: the `POLICIES` column, against
   the slots the previous step edited, reads without `Delete` on every
   one of them. Until it does, the next step destroys what it was meant
   to move.

   ```bash
   just crossplane-slots "$QW_CODE-mgmt"
   ```

4. Move the resources, in one change: delete them from
   `infra/platform/crossplane-xrds/<parent>-composition.yml`, add
   `infra/platform/crossplane-xrds/<child>-xrd.yml` and
   `infra/platform/crossplane-xrds/<child>-composition.yml`, and
   compose the child from the parent, with each resource's full policy
   in the child's `base`, `Delete` included where it belongs. Merge it.
   Two merges in all, not three.

5. Check the transfer finished. The moved resources appear under the
   child composite with `SYNCED` and `READY` both `True`, which is a
   resource that came through: deleted by the parent, recreated by the
   child, and adopted back by external name.

   ```bash
   # the composite the resources moved out of, as kind.group/name, e.g.
   export XR="xqueenswoodinstance.queenswood.repldriven.com/$QW_CODE-n-test"

   crossplane resource trace "$XR" -n crossplane-system -c "$QW_CODE-mgmt" \
     -o wide
   ```

### 2b. Change an identity field

**As the installation's cluster admin.** Ours is
`grp-gcp-<code>-cluster-admin@` — join for this branch, then leave.

Some fields are the resource's identity, and upjet declines to replace
a resource rather than performing the replacement. The value moves only
if the cloud resource is destroyed and built again.

> [!WARNING]
> This destroys the cloud resource. Whatever it holds goes with it, and
> a resource withholding `Delete` withholds it because that loss is not
> recoverable.

1. Confirm it is identity rather than a passing failure: the managed
   resource reads `SYNCED` `False`, and `LastAsyncOperation` carries
   `refuse to update the external resource because the following update
   requires replacing it`.

   ```bash
   # the managed resource refusing the change, as kind.group/name, e.g.
   export OBJ="cluster.container.gcp.m.upbound.io/$QW_CODE-n-test"

   just crossplane-conditions "$OBJ" "$QW_CODE-mgmt"
   ```

2. Merge the new value into the Composition. Nothing applies it, and
   nothing will until the resource is gone.

3. Read what deleting the managed resource will do, in `POLICIES`.
   Carrying `Delete`, the cloud resource is destroyed and the
   composition builds a new one at the merged value. Without it, the
   cloud resource is orphaned, the composition builds a managed
   resource that adopts that same one by external name, and the field
   is still what it was — so the change needs `Delete` granted for the
   duration, or the cloud resource destroyed by hand.

   ```bash
   just crossplane-slots "$QW_CODE-mgmt"
   ```

4. Delete the managed resource and let the composition rebuild it.

5. Check it came back at the new value, in `just crossplane-conditions`
   and in the field itself read from the cloud.

Where the field is one a caller supplies, the fix is upstream: an
identity field belongs in `base` rather than in a patch, and a caller
who must vary one wants a second resource. See
[crossplane-design](crossplane-design.md).

### 2c. Rename a slot

Step 2a, one level down. A composed composite sits in a slot like
anything else, so renaming its slot deletes that composite, which
deletes what it composes. Withhold `Delete` from the resources
**inside** the child first, in a merge of its own, then rename, then
restore the policies.

### 2d. Withdraw a kind

1. Count the live composites of the kind, against the cluster.
   `No resources found` is the only answer that makes the next step a
   removal rather than a deletion: the XRD owns the CRD it establishes,
   so withdrawing it takes every composite of the kind, and each of
   those deletes what it composes.

   ```bash
   # the kind being withdrawn, plural and lower-case, e.g.
   export KIND=xpublicendpoints

   kubectl --context "$QW_CODE-mgmt" get "$KIND" -A
   ```

2. Delete both `infra/platform/crossplane-xrds/<kind>-xrd.yml` and
   `infra/platform/crossplane-xrds/<kind>-composition.yml` in one
   change and merge. The `crossplane-xrds` Application prunes, so the
   merge is the removal: Argo prunes both objects, and Crossplane
   garbage-collects the CRD beneath the XRD and the revisions beneath
   the Composition.

## Failures

**A cloud resource destroyed by a change that was meant to move it.**
The transfer is a delete and a create rather than a handover: the
parent stops declaring the resource and garbage-collects its copy
before the child creates its own. The policy that governs is the one on
the parent's copy at that moment, so a child declaring `Observe,
Create, Update, LateInitialize` protects nothing that the parent still
held with `Delete`.

**A resource rebuilt at the old value.** An identity field changed on a
resource whose `managementPolicies` withhold `Delete`. Deleting the
managed resource orphaned the cloud resource rather than destroying it,
the composition built a replacement, and that replacement adopted the
same cloud resource by external name — so the field is what it always
was, and the refusal returns on the next reconcile. Grant `Delete` for
the duration, or destroy the cloud resource by hand.

**A merged value that reached every Secret and not the cloud.** The
field is write-only, so `Synced: True` means "no diff detected" rather
than "the value is what you think it is" — and nothing has told the
cloud. A password is the case that bites: an open session survives a
change, so the workload keeps running and fails at whatever moment it
next restarts. Compare against the cloud rather than against the
manifest, and set it explicitly.

**A rename that deleted a cluster.** Renaming a composite's slot has a
blast radius of its grandchildren. The composite in the renamed slot is
deleted and rebuilt, and each resource it composed goes or stays on its
own policy — so a node pool carrying `Delete` goes with it while the
cluster survives, or does not, on what its own policy says.

**A new kind whose every apply fails, over a resource that is running
fine.** A slot name reused from one a live managed resource already
carries, which is what 2a's first step avoids — see
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

- Determine what kind of change it is before making it. Ownership —
  `just crossplane-owners` — decides what happens to a field, and
  identity is not visible anywhere, so it is decided when the kind is
  designed rather than looked up here.
- Read the live slot names — `just crossplane-slots` — before naming a
  composed resource in the new kind. Reusing one a live managed
  resource carries makes two composites claim it, and every apply then
  fails.
- Withhold `Delete` before moving a resource to another composite, in a
  change of its own that reaches the plane first, and check it reached
  the plane in the `POLICIES` column of `just crossplane-slots`. The
  transfer deletes the parent's copy, so the parent's policy is the one
  that governs.
- Read the managed resource's `LastAsyncOperation` with
  `just crossplane-conditions` before treating a change as applied. A
  refusal reports there, and the composite above goes on reading
  `Synced`.
- Prove a write-only field against the cloud, never against `Synced`.
  The provider cannot read one back, so it reports no drift whether the
  value took or not.
- Destroy and rebuild the cloud resource to change an identity field,
  granting `Delete` for the duration where the policy withholds it.
  Nothing else moves the value.
- Count the live instances of a kind before removing its XRD. The CRD
  and every composite of it go with the XRD.
- Delete a Composition alongside the XRD it names. Nothing links them
  but a `compositeTypeRef`.
- Read whether the Application carrying a file prunes before treating a
  deletion from the repository as a removal from the plane.
- Merge a change before expecting it on the plane. Argo reads the
  revision an Application names, never a working tree.

**MUST NOT:**

- Combine the policy change and the move into one merge. The plane
  applies what it reads, and it reads them in order.
- Expect a merged value to reach a field that identifies its resource.
  upjet refuses the replacement rather than performing it.
- Rename a composite's slot without applying the same two-step to the
  resources inside it.
- Delete a composite to tidy up. It deletes what it composes, subject
  to each resource's `managementPolicies`.

## Discussion

We ask what a change costs before making it, because a Composition
edit has three quite different outcomes and only one of them is the
one people expect: it applies, it is refused, or it destroys and
rebuilds. Which of the three a given field takes is not visible in the
manifest, and the second and third are indistinguishable from the
first until something is already gone.

**The orphan-and-readopt loop is reasoned rather than observed.** It
follows from two things this page documents separately — that a
resource withholding `Delete` is orphaned rather than destroyed, and
that a rebuilt managed resource adopts a cloud resource by external
name — but nobody here has watched an identity change go round it.
Treat it as the expected behaviour rather than as a recorded one.

**Why the parent's policy is the one that counts.** A move has no
handover in it: what looks like a transfer is a delete on one side and a
create on the other, joined only by the cloud resource's external name.
The parent stops declaring the resource, so Crossplane garbage-collects
the managed object it owns, and that deletion is governed by the policy
on that object. The child then creates its own managed object, observes
the cloud resource by external name, and adopts it. The child's policy
has had no opportunity to matter yet. Which is what makes the order
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

- [ADR-0023](../../adr/0023-installation-naming-and-access.md) — what a
  capability is, and how an organisation answers one.
- [crossplane-design](crossplane-design.md) — how much one kind covers,
  and what may be deleted.
- [crossplane-debug](crossplane-debug.md) — what in an installation
  is not ready, or not what you declared.
- [crossplane-providers](crossplane-providers.md) — why upjet refuses a
  replacement, and what else counts as identity.
- [argocd-apps](argocd-apps.md) — what prunes, and why a merged change
  reaches a plane when a working tree does not.
- [composite-catalogue](../../plan/composite-catalogue.md) — the
  extractions this was derived from, and what is left to do.
