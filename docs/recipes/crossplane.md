# Crossplane

<!-- tessl-plugin: deployment -->

## Problem

A composition is a program whose failures report somewhere other than
where they happen, and whose resources are identified by something
other than their name.

## Solution

Know what identifies a composed resource, what a patch does when its
source is missing, and which condition carries which error.

### What identifies a composed resource

The `- name:` in the resources list, not `metadata.name`. Crossplane
records it as `crossplane.io/composition-resource-name` and matches on
it. The composite's `resourceRefs` then holds the object name it built,
and that is what gets recreated — so changing a `metadata.name` patch
renames nothing, and deleting the object rebuilds it under the old
name. Changing the `- name:` is what makes it a new resource.

### Patches

A patch whose source field is absent is skipped, silently. That is what
makes an optional manifest field work, and what turns an unapplied XRD
into an empty value nothing complains about. Where absence is a
mistake rather than a meaning, set `policy.fromFieldPath: Required`.

A field the XRD defaults is absent too, for a window. Argo applies an
XRD and a Composition in one sync, and the new composition can be
selected before the API server serves the regenerated CRD, so the
default has not been applied yet and a patch reading it is skipped.
What that costs depends on the target rather than on the source. A
field the provider's CRD requires makes the apply fail, which retries a
moment later and heals. An optional one composes a resource with a hole
in it, and the provider may then fail to observe what it created — a
state no later reconcile recovers, because the resource already exists
and is wrong. So `Required` belongs on every patch whose source the
XRD defaults, and stating the value in the manifest closes the window
for one composite without closing it for the next.

Some values are only legal on a resource that already exists, and a
composition has no way to withhold one. A patch always writes
something: a map transform must name every key it may see, and
`patch-and-transform` cannot express "not this field, this time". So a
field whose value is a *transition* rather than a *state* — Cloud SQL's
`activationPolicy: NEVER`, which stops a database and cannot create one
stopped — makes the first reconcile fail rather than the field simply
not applying. Reading observed state in a templating step does not
rescue it either, because the resource would still have to be created
in the legal state and moved afterwards, which is the same behaviour
with a better error. Say so in the XRD instead, where whoever writes
the manifest is looking.

A `string` `Format` transform is `fmt.Sprintf`. Given an input it does
not interpolate, it appends `%!(EXTRA string=…)` to the result. A
constant belongs in `base`.

Two sources into one field is one `CombineFromComposite`. Two patches
to the same field is the second overwriting the first.

### Fixed and varying sets

`patch-and-transform` composes a fixed set of resources. It cannot
express "no resource at all" for an absent input, so anything whose
*count* varies with the manifest — a binding per member, none where a
capability is not provided — needs `function-go-templating`.

### Ownership

Kubernetes records, for every field, which *field manager* set it —
visible in `metadata.managedFields`. Server-side apply then enforces
one rule: **a manager that stops declaring a field it solely owns
removes that field.** Everything below follows from it.

Three managers write to a composed resource:

- the **composition**, which declares every field it sets in `base` or
  patches;
- the **provider**, which writes status and any field it
  late-initialises from the cloud;
- the **reference resolver**, which writes fields it resolves from a
  `*Ref`.

So:

- **Every field the composition sets, it owns.** Adding a patch is not
  free: it takes the field from whoever had it.
- **Deleting a patch deletes the field.** Not "leaves it as it was" —
  the sole owner relinquished it, so it is removed. On the next
  reconcile the provider may write it back under its own manager, but
  the window is real.
- **A field the composition never set is free.** The provider can
  late-initialise it, the resolver can resolve it, and a `kubectl
  patch` of it holds.
- **Two owners are stable.** If the composition and the provider both
  declare a field, one relinquishing does not remove it.

Which is why a hand patch sometimes sticks and sometimes reverts within
seconds. Check `managedFields` before assuming either.

Worked examples, all from one composition:

- `forProvider.folderId` — never set by the composition, resolved from
  `folderIdRef`. Survives every apply, because the composition has no
  claim on it.
- `forProvider.billingAccount` — set by the composition at creation,
  late-initialised by the provider afterwards. Dropping the patch moves
  it to the provider; while both declare it, dropping one changes
  nothing.
- `managementPolicies` — set in `base`, so composition-owned. A
  `kubectl patch` reverts on the next reconcile, and the only way to
  change it is to change the composition.
- `deletionPolicy` — never set by this composition. A `kubectl patch`
  sticks, which makes it the usable lever when a managed resource must
  be deleted without taking the cloud resource with it.

### Scope

A namespaced composite composes namespaced resources only. A
cluster-scoped kind fails the whole pipeline with `cannot apply cluster
scoped composed resource … for a namespaced composite resource`, and
may survive one reconcile before it does, because create and apply are
different paths.

### Readiness

A managed resource reports `Ready` from its own conditions, which may
not reflect the cloud. A GCP folder deleted through the console sits in
`DELETE_REQUESTED` for thirty days and still reads as existing, so the
provider reported it `Available` throughout — no event, no log line.
Add a `readinessCheck` against the field that carries the real state,
and alert on composites rather than on managed resources.

### Failure

One pipeline step failing fails every composed resource, not its own.
A go-templating step that will not parse — an action nested inside
another action's operand, for instance — stops the composition, and the
error appears on the composite while the symptom is resources that
stopped changing.

A composed kind whose CRD is not installed on the applying plane fails
the same way: `no matches for kind`.

### Removal

An XRD owns the CRD it establishes, so deleting the XRD withdraws the
kind and takes every composite of it with it — and a composite deletes
what it composes, subject to each resource's `managementPolicies`. So
the question before removing an XRD is how many instances of its kind
exist, and the answer has to be read from the cluster rather than
assumed from the fact that nothing in the repository creates one.

Where the Application carrying the XRD prunes, deleting the file is the
removal: Argo prunes the XRD, and Crossplane garbage-collects the CRD
beneath it and the revisions beneath its Composition. Where it does
not, the file leaves the repository and the plane goes on serving the
kind, so the removal is a delete against the cluster and the repository
edit alone reads as a change that did nothing. Which of the two it is
is a property of the Application, not of the XRD, so it is worth
reading before assuming either.

A Composition outlives the XRD it names, since nothing links them but a
`compositeTypeRef`. Delete it alongside, or the plane keeps a
Composition for a kind it no longer serves.

## Rules

**MUST:**

- Change a resource's `- name:` to rebuild it under a new
  `metadata.name`. Deleting the object alone rebuilds the old one.
- Set `policy.fromFieldPath: Required` where a missing source is a
  mistake rather than a meaning.
- Put constants in `base`. A `Format` transform with no verb for its
  input corrupts the value.
- Install a provider for every kind the composite composes, on every
  plane that composes it.
- Read `Synced`, `Ready` and `LastAsyncOperation` before concluding
  anything. They report different failures.
- Check `metadata.managedFields` before assuming a hand patch will
  hold, or that a field will survive a patch being removed.
- Count the live instances of a kind before removing its XRD. The CRD
  and every composite of it go with the XRD.
- Delete a Composition alongside the XRD it names. Nothing links them
  but a `compositeTypeRef`.
- Withhold `Delete` from `managementPolicies` for anything whose loss
  is not recoverable. Deleting the managed resource then orphans the
  cloud resource rather than destroying it.

**MUST NOT:**

- Patch a field the composition sets and expect it to hold.
- Expect a composition to withhold a field. A patch always writes
  something, so a value only legal on a resource that already exists
  fails the first reconcile rather than being skipped.
- Compose a cluster-scoped kind from a namespaced composite.
- Delete a composite to tidy up. It deletes what it composes, subject
  to each resource's `managementPolicies`.
- Delete a patch for a field you want kept. The composition owns what
  it patches, so the field goes with the patch.
- Treat a failure as belonging to the resource it names. One pipeline
  step failing — a template that will not parse, a kind with no CRD —
  stops every composed resource, and reports on the composite.

## References

- [crossplane-providers](crossplane-providers.md) — what Terraform-backed
  providers add to this.
- [argocd](argocd.md) — delivering compositions.
