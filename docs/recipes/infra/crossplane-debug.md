# Debugging an installation

<!-- tessl-plugin: deployment -->

## Status

**Verified**, 2026-08-28, on this installation's plane, where nothing
is currently failing: step 1 gave a header line and nothing under it,
and steps 2 to 4 were run against the instance's `XCluster` and the
`Cluster` under it. Step 5 issues no command.

## Problem

You need to use Crossplane to find what in an installation is not
ready, or not what you declared.

## Solution

### Prerequisites

- A management plane running in the installation's folder.
- Step 2 — the `crossplane` CLI, which the flake provides.
- The capability each step names. Ours is a Google group; yours may differ.

```bash
# the installation code, e.g. qw01
export QW_CODE=qw01
```

### 1. Find what is not ready

**As the installation's platform viewer.** Ours is
`grp-gcp-<code>-platform-viewer@`, populated rather than joined.

```bash
just crossplane-unready "$QW_CODE-mgmt"
```

Rows are where to start: take the composite nearest the top of the list
into step 2.

A header line with nothing under it means every composite and every
managed resource on the plane reads both synced and ready, and what is
wrong is something that reads correct — a field holding a value nobody
declared, or missing one that was. Step 4 is what answers those, on
whichever object carries the field.

### 2. Trace the composite

```bash
# the composite step 1 named, as kind.group/name, e.g.
export XR="xcluster.platform.repldriven.com/$QW_CODE-n-test"

crossplane resource trace "$XR" -n crossplane-system -c "$QW_CODE-mgmt" \
  -o wide
```

`RESOURCE` is the slot name, which is what the Composition calls the
thing and what a `- name:` in its resources list has to match.
`STATUS` carries the provider's message for anything that is not
`Available`.

A root that is not ready over children that all are is a failure in the
pipeline rather than in any one resource.

### 3. Read the conditions on whatever is not ready

```bash
# the object step 2 named, as kind.group/name, e.g.
export OBJ="cluster.container.gcp.m.upbound.io/$QW_CODE-n-test"

just crossplane-conditions "$OBJ" "$QW_CODE-mgmt"
```

Three types, reporting three different failures. `Synced` is whether
Crossplane could apply what it wants. `Ready` is whether the resource
is usable. `LastAsyncOperation` is what the provider's last call to the
cloud returned, and is the only one of the three that carries a refusal
the cloud made.

### 4. Find who owns a field

```bash
# the object step 2 named, as kind.group/name, e.g.
export OBJ="cluster.container.gcp.m.upbound.io/$QW_CODE-n-test"

just crossplane-owners "$OBJ" "$QW_CODE-mgmt"
```

`composition` is the composition's own manager, `resolver` writes what
it resolved from a `*Ref`, and `provider` writes what it
late-initialised from the cloud. A field in none of the lists is
unowned.

### 5. Change it where its owner is

**Composition-owned.** The change is to
`infra/platform/crossplane-xrds/<kind>-composition.yml`, and it is a
merge. Nothing pins a Composition revision, so every live composite of
that kind takes the edit on its next reconcile. A `kubectl patch` of
such a field reverts.

**Unowned.** A `kubectl patch` holds until something claims the field.

**Provider-owned.** Leave it. A patch added for a field
late-initialisation owns takes the field from the provider, and the
provider stops maintaining it — see
[crossplane-providers](crossplane-providers.md).

Where the field identifies the cloud resource, no edit reaches it under
any owner: the provider refuses the replacement and says so in
`LastAsyncOperation`, so the resource has to be deleted and rebuilt.
Withhold `Delete` first — see
[crossplane-live](crossplane-live.md).

## Failures

**A composite `Ready` over a resource that never observed.** A managed
resource observing against an API nobody enabled fails with a 403
naming that API rather than naming anything about itself — Cloud
Storage is on by default in a new project and Secret Manager is not —
and the failure appears on that resource alone while the composite
above it goes on reporting `Ready`. Step 1 lists managed resources
beside composites for this reason.

**Resources that stopped changing, and no error on any of them.** One
pipeline step failing fails every composed resource rather than its
own, so a go-templating step that will not parse, or a composed kind
whose CRD is not installed on this plane, stops the whole composition
and reports on the composite as `no matches for kind` or a template
error. The resources it would have applied are untouched and say
nothing.

**Every field unowned.** `kubectl` strips `managedFields` out of the
JSON it prints unless `--show-managed-fields` is passed, so the list is
empty rather than absent and every field reads as free to patch.

**A hand patch that reverts within seconds.** The composition declares
that field, so it owns it and rewrites it on the next reconcile. The
same patch against a field the composition never set holds
indefinitely, which is why one `kubectl patch` sticking is no evidence
about the next.

**A field that disappeared when a patch was deleted.** Server-side
apply removes a field when the manager that solely owned it stops
declaring it, so deleting a patch deletes the field rather than
freezing its last value. The provider may write it back under its own
manager on the next reconcile, but the window is real.

**A destination that never existed.** A composite's status is derived
on each reconcile rather than accumulated, so a `ToCompositeFieldPath`
patch that stops firing removes the field it wrote. Anything reading
that field composes without it, and server-side apply then removes it
from whatever the reading resource writes — a cluster endpoint absent
for the length of a move takes the endpoint out of the Secret
registering that cluster, which reads as a destination that was never
built rather than as a value briefly missing.

**A rendered diff that shows nothing wrong.** A render proves what a
composition produces, not what applying it does to what already
exists. It cannot see a slot name colliding with a live object, the
composite's own status, or what a deletion cascades to.

It cannot see the CRD's own validation either, which is what makes a
change to `managementPolicies` worth reading the schema for. A provider
marks a field required when the resource is managed rather than when it
is written — `!('*' in managementPolicies || 'Create' in
managementPolicies || 'Update' in managementPolicies) ||
has(forProvider.displayName)` on `Folder` — so dropping a field and
withholding only `Update` renders perfectly and is rejected by the API
server on every apply. Read the rule off the installed CRD with `just
crossplane-explain` before composing a resource that omits a field its
provider might require.

## Rules

**MUST:**

- Start from what is not ready — `just crossplane-unready` — rather
  than from the resource somebody named.
- Read `Synced`, `Ready` and `LastAsyncOperation` before concluding
  anything, with `just crossplane-conditions`. They report different
  failures.
- Read the composite's own conditions before any composed resource's.
  One pipeline step failing stops them all and reports there.
- Check which field manager owns a field — `just crossplane-owners`,
  which passes `--show-managed-fields` — before assuming a hand patch
  will hold or that a field will survive its patch being removed.
- Change a composition-owned field in the Composition and merge it.
  Nothing pins a revision, so every live composite takes the edit on
  its next reconcile, and a `kubectl patch` of such a field reverts.
- Enable an API before composing a kind that needs it. Cloud Storage is
  on by default in a new project and Secret Manager is not.
- Install a provider for every kind the composite composes, on every
  plane that composes it.

**MUST NOT:**

- Treat a failure as belonging to the resource it names. One pipeline
  step failing — a template that will not parse, a kind with no CRD —
  stops every composed resource, and reports on the composite.
- Read `Ready` on a composite as evidence about every resource under
  it.
- Patch a field the composition sets and expect it to hold.
- Delete a patch for a field you want kept. The composition owns what
  it patches, so the field goes with the patch.
- Treat a rendered diff as proof of what applying it will do to what
  already exists, or as proof that it applies at all. A render does not
  run the CRD's validation rules.

## Discussion

We read a composite from the top down — its own conditions before any
of its resources' — because the two halves of a composition fail in
opposite directions, and we settle every question about a field by
asking who owns it rather than by trying an edit and watching.

**Where a failure reports.** A Composition is a pipeline of functions,
and the pipeline is one program: a step that fails fails everything the
composition would have applied. So an unparseable template or a kind
with no CRD on the applying plane surfaces as an error on the composite
and as resources that quietly stopped changing. The other direction is
just as misleading: a failure inside one composed resource stays on
that resource, and the composite goes on reporting `Ready` because
everything else in it is.

**Three managers, and one rule.** Kubernetes records, for every field,
which field manager set it, and server-side apply enforces one rule
from that record: a manager that stops declaring a field it solely owns
removes that field. Three managers write to a composed resource — the
composition, which declares everything it sets in `base` or patches;
the provider, which writes status and whatever it late-initialises from
the cloud; and the reference resolver, which writes what it resolves
from a `*Ref`. Four consequences follow, and they are the whole of step
4:

- Every field the composition sets, it owns. Adding a patch is not
  free: it takes the field from whoever had it.
- Deleting a patch deletes the field, rather than leaving it as it was.
- A field the composition never set is free — the provider can
  late-initialise it, the resolver can resolve it, and a hand patch of
  it holds.
- Two owners are stable. Where the composition and the provider both
  declare a field, one relinquishing does not remove it.

The instance's GKE `Cluster` shows all four at once. The
`forProvider.folderId` field is never set by the composition and is
resolved from `folderIdRef`, so it survives every apply, while
`forProvider.billingAccount` is set at creation and late-initialised
afterwards — so while both managers declare it, dropping either changes
nothing.
`managementPolicies` is set in `base` and therefore composition-owned,
so a `kubectl patch` of it reverts and the only way to change it is to
change the composition — which is why orphaning a resource before it is
deleted is a merge rather than something done at the cluster. The v1
API's `deletionPolicy` would have been the hand lever for that, and the
`.m.` kinds do not carry the field at all.

**Why a status field can vanish.** A composite's status is derived on
each reconcile, not accumulated, so a patch that stops firing takes its
field with it rather than leaving the last value standing. Where a
consumer cannot tolerate that gap, the patch reading the field wants
`policy.fromFieldPath: Required`, so an absent value drops the consumer
rather than composing it half-built — weighed against what dropping it
costs, since a dropped composed resource is a deleted one.

## References

- [crossplane-design](crossplane-design.md) — how much one kind covers,
  what the caller supplies, and what may be deleted.
- [crossplane-live](crossplane-live.md) — whether
  a change applies, is refused, or destroys.
- [crossplane-providers](crossplane-providers.md) — what
  Terraform-backed providers add to this.
- [argocd-health](argocd-health.md) — why an Application over a
  composite that failed to compose reads `Healthy`.
