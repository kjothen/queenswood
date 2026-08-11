# Crossplane providers
<!-- tessl-plugin: deployment -->

## Problem

Upjet providers wrap a Terraform provider. Terraform's notions of
identity and immutability leak through, and the CRD schema is not the
Terraform documentation.

## Solution

Read the schema from the cluster, and expect a change to a resource's
identity to be refused rather than performed.

### Identity is not configuration

A field Terraform marks ForceNew cannot be updated. Upjet refuses:

```
refuse to update the external resource because the following update
requires replacing it: cannot change the value of the argument "…"
```

It reports in `LastAsyncOperation`, so a `get` showing `SYNCED False`
may be describing something else entirely. Delete the managed resource
and let the composition rebuild it.

Identity includes more than it looks: a GKE node pool's
`serviceAccount`, an IAM binding's `member`, a resource's name. Anything
that identifies the thing is a delete, not an update.

### External names

The external name is the cloud identifier, and defaults to
`metadata.name`. Where the two must differ — a GCP custom role id takes
underscores where a Kubernetes name takes hyphens — set it explicitly.

Some resources have no field for their id at all: a Secret Manager
secret's id *is* its external name.

For a `Project`, the external name is empty immediately after create, so
the first build only completes when the generated id is fed back as
`adopt`.

### Late initialisation

The provider owns fields it late-initialises. A composition that stops
setting a field it previously owned drops it on the apply that
relinquishes it — the provider then writes it back under its own field
manager. Three managers are normal: the composition, the reference
resolver, the provider.

### Groups

Prefer the namespaced `.m.` group — `cloudplatform.gcp.m.upbound.io`,
`helm.m.crossplane.io`. Both groups are usually installed; the
NAMESPACED column tells them apart, not the version. Do not copy an API
group from an older file.

### Schemas

`kubectl explain <kind>.spec.forProvider` against the installed CRD.
Field shapes differ from the Terraform documentation and between
provider versions — a v1 list becomes a v2 map, a documented argument
turns out not to exist.

## Rules

**MUST:**

- Read the CRD before writing a composed resource, not the provider's
  documentation.
- Delete the managed resource to change anything that identifies it.
- Use the `.m.` API group.
- Set the external name explicitly where it must differ from the
  Kubernetes name, or where something else spells it.
- Feed a generated id back as an adopt value where the external name is
  empty after create, or the resource never completes.

**MUST NOT:**

- Expect a ForceNew change to replace a resource. It is refused.
- Diagnose from `Synced` alone. The refusal is in
  `LastAsyncOperation`.
- Re-add a patch for a field late-initialisation now owns.

## References

- [crossplane](crossplane.md) — the engine underneath.
- [gcp-iam](gcp-iam.md) — what the provider's identity needs.
