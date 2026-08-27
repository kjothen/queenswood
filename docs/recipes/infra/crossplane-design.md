# Designing a Crossplane kind

<!-- tessl-plugin: deployment -->

## Problem

You need Crossplane to manage a set of cloud resources.

## Solution

Five decisions, in the order you answer them.

### 1. How much one kind covers

Managed resources go straight into a Composition that already exists,
until a second place needs the same arrangement. Extract then, not at
the first call site: what varies is guesswork from one example, and the
invariants are the same either way. Keep the block contiguous until it
moves, so the extraction is a move rather than a rewrite.

What an XRD buys is that one edit reaches every XR of the kind. What it
costs is a CRD on every plane that composes it, and a failure inside
the child reporting on the child rather than on the XR that asked for
it — one more hop in the direction that already misleads.

See [ADR-0025](../../adr/0025-building-blocks-and-what-cannot-be-one.md)
for what cannot be a kind at all.

### 2. What the caller chooses

The invariants are the reason the kind exists: fix them in `base`, and
parameterise only what genuinely differs. Then every field the caller
does supply is one of three things.

- **Required** — `policy.fromFieldPath: Required`, where absence is a
  mistake rather than a meaning. A patch whose source is absent is
  otherwise skipped in silence.
- **Defaulted** — a value in the XRD, for what most callers should not
  think about. Every patch reading a defaulted field wants `Required`
  as well: the default is absent for a window, because Argo applies an
  XRD and a Composition in one sync and the new Composition can be
  selected before the API server serves the regenerated CRD.
- **The switch for a block** — a Required patch whose source is absent
  drops the whole composed resource rather than the one field, so a
  block composes nothing until the field is set. Such a field cannot
  carry an XRD default, which would make it always present.

Two sources into one field is one `CombineFromComposite`; two patches
to one field is the second overwriting the first. Constants belong in
`base`, because a `Format` transform with no verb for its input appends
`%!(EXTRA string=…)` to the value.

A field whose *count* varies with the caller — a binding per member,
none where a capability is not provided — cannot be
`patch-and-transform`, which composes a fixed set and cannot express
"no resource at all". That needs `function-go-templating`.

### 3. What each part is called

The `- name:` in the resources list is the identity, not
`metadata.name`. Crossplane records it as
`crossplane.io/composition-resource-name` and matches on it, so
changing a `metadata.name` patch renames nothing, and changing the
`- name:` is what makes a slot a new resource.

Read the live `crossplane.io/composition-resource-name`s before
choosing one. The matching does not care what kind sits in the slot, so
reusing a name a managed resource already carries makes two XRs claim
the same object.

### 4. What may be deleted

`managementPolicies` per composed resource, and `Delete` withheld from
anything whose loss is not recoverable — a database, a DNS zone, a
bucket holding backups. Without `Delete`, removing the managed resource
orphans the cloud resource rather than destroying it.

Decide this while designing rather than when something is about to be
deleted: an XR deletes what it composed, subject to each resource's own
policy, and the policy that governs a transfer is the one on the copy
being deleted.

### 5. When it is ready

A managed resource reports `Ready` from its own conditions, which may
not reflect the cloud: a GCP folder deleted through the console sits in
`DELETE_REQUESTED` for thirty days and reads as existing throughout.
Add a `readinessCheck` against the field carrying the real state.

End every Composition with `function-auto-ready`, including one that
composes nothing templated yet. `function-patch-and-transform` sets
readiness for the resources it composes and `function-go-templating`
sets it for none, so a pipeline mixing the two reports every templated
resource unready for ever — and the Composition that does not need
`function-auto-ready` today is the one that silently stops computing
readiness the day a template is added to it.

## Failures

**A patch that never fires, and no error anywhere.** A patch whose
source field is absent is skipped silently. That is what makes an
optional field work, and what turns an unapplied XRD into an empty
value nothing complains about.

**Two XRs claiming one object, while nothing looks broken.** A slot
name reused from a live managed resource: the parent matches the live
object to the new slot rather than releasing it, both XRs then claim
it, and Kubernetes refuses two owner references with `controller: true`.
Every apply the new kind attempts fails, the old owner never lets go,
and the resource keeps running under its original owner while the
transfer never completes.

**A first reconcile that fails on a value the resource cannot be
created with.** A Composition has no way to withhold a field: a patch
always writes something, a map transform must name every key it may
see, and `patch-and-transform` cannot express "not this field, this
time". So a value that is a *transition* rather than a *state* — Cloud
SQL's `activationPolicy: NEVER`, which stops a database and cannot
create one stopped — fails rather than being skipped. Reading observed
state in a templating step does not rescue it, because the resource
would still have to be created legal and moved afterwards. Say so in
the XRD, where whoever writes the manifest is looking.

**A resource composed with a hole in it.** The window where an XRD
default has not been applied yet. What it costs depends on the target:
a field the provider's CRD requires fails the apply, which retries and
heals, while an optional one composes a resource the provider may then
fail to observe — a state no later reconcile recovers, because the
resource exists and is wrong.

**Every templated resource unready for ever, naming one that is
`Available`.** A pipeline mixing `function-patch-and-transform` and
`function-go-templating` without `function-auto-ready` at the end.

**A whole pipeline failing on one composed resource.** A cluster-scoped
kind composed from a namespaced XR fails everything with `cannot apply
cluster scoped composed resource … for a namespaced composite resource`,
and may survive one reconcile before it does, because create and apply
are different paths.

## Rules

**MUST:**

- Extract a kind at the second call site, fixing the invariants and
  parameterising the rest.
- Read the live `crossplane.io/composition-resource-name`s before
  naming a composed resource.
- Change a resource's `- name:` to rebuild it under a new
  `metadata.name`. Deleting the object alone rebuilds the old one.
- Set `policy.fromFieldPath: Required` where a missing source is a
  mistake rather than a meaning, and on every patch reading a field the
  XRD defaults.
- Put constants in `base`.
- Use `function-go-templating` where the number of composed resources
  varies with the caller.
- Withhold `Delete` from `managementPolicies` for anything whose loss
  is not recoverable.
- End every Composition with `function-auto-ready`.
- Add a `readinessCheck` against the field carrying the real state,
  where a managed resource's own conditions do not reflect the cloud.
- Make every Composition edit safe for an XR that already exists: add
  fields rather than repurpose them, default what a manifest does not
  yet set, and never make a field required in the change that
  introduces it.

**MUST NOT:**

- Add a version to an XRD. Where a change is that large it is a
  different kind, named for what it is and adopted deliberately.
- Set `compositionUpdatePolicy: Manual` on an XR. Pinning divides an
  estate into the XRs that took an edit and the ones that did not,
  which is the problem versioning an XRD would have caused.
- Give an XRD default to a field that switches a block of resources on.
  A default makes it always present, so the block always composes.
- Expect a Composition to withhold a field.
- Compose a cluster-scoped kind from a namespaced XR.

## Discussion

We design a kind by deciding what it covers, what its caller may
choose, what its parts are called, what may be destroyed and when it is
finished — and we make each of those decisions once, because nothing
here revisits them.

**The four things, in Crossplane's words.** An **XRD** defines the
schema and creates the API endpoint. A **Composition** configures how
an XR creates other resources. A **composite resource (XR)** is the
result — "a set of Kubernetes resources as a single Kubernetes object".
The **composed resources** are what the Composition creates, and the
**managed resources (MRs)** among them map one-to-one onto a remote
API.

**Why every edit is immediate.** An XR's `compositionUpdatePolicy`
defaults to `Automatic`, so it uses the latest `CompositionRevision`
and takes an edit on its next reconcile. It could pin — `Manual` with a
`compositionRevisionRef` — and nothing here does, deliberately. A
pinned estate divides into the XRs that took the change and the ones
that did not, so a policy tightened centrally becomes a policy that has
to be chased, which is exactly what a shared module in a tool with
version pinning costs.

**Which is why the kind stays at `v1alpha1`.** A version on an XRD is
not a compatibility knob, because nothing pins one. A second version
promises an upgrade path that does not exist: existing XRs would have
to be migrated either way, and the version number is the only thing
suggesting otherwise. Where a change is large enough to want a version,
what it wants is a different kind with a name of its own, that an
installation moves to when it chooses.

**So every edit is designed for an XR that already exists.** The
manifests live in another repository and reach a plane on their own
schedule, so a field made required in the change that introduces it
breaks every XR whose manifest has not caught up. Add rather than
repurpose, default what is not yet set, and let the requirement follow
once the manifests do.

## References

- [crossplane](crossplane.md) — operating a kind that exists.
- [crossplane-providers](crossplane-providers.md) — what
  Terraform-backed providers add to this.
- [ADR-0025](../../adr/0025-building-blocks-and-what-cannot-be-one.md) —
  when repetition wants a kind, and what cannot be one.
- [argocd-apps](argocd-apps.md) — how a Composition reaches a plane.
