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

A `string` `Format` transform is `fmt.Sprintf`. Given an input it does
not interpolate, it appends `%!(EXTRA string=…)` to the result. A
constant belongs in `base`.

Two sources into one field is one `CombineFromComposite`. Two patches
to the same field is the second overwriting the first.

### Ownership

The composition owns every field it patches, under server-side apply.
Deleting a patch deletes the field. A `kubectl patch` of a field the
composition sets reverts within seconds; a field it has never set
sticks. That decides which lever works: `managementPolicies` is set in
`base` and cannot be patched by hand, `deletionPolicy` usually can.

`managementPolicies` without `Delete` orphans the cloud resource when
the managed resource is deleted.

### Scope

A namespaced composite composes namespaced resources only. A
cluster-scoped kind fails the whole pipeline with `cannot apply cluster
scoped composed resource … for a namespaced composite resource`, and
may survive one reconcile before it does, because create and apply are
different paths.

### Failure

One pipeline step failing fails every composed resource, not its own.
A go-templating step that will not parse — an action nested inside
another action's operand, for instance — stops the composition, and the
error appears on the composite while the symptom is resources that
stopped changing.

A composed kind whose CRD is not installed on the applying plane fails
the same way: `no matches for kind`.

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
- Withhold `Delete` from `managementPolicies` for anything whose loss
  is not recoverable. Deleting the managed resource then orphans the
  cloud resource rather than destroying it.

**MUST NOT:**

- Patch a field the composition sets and expect it to hold.
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
