# Plan: name the plane's cluster the way an instance's is named

## Context

[cloud-naming](../recipes/practices/cloud-naming.md) says a cluster is
`<code>-<env>-<label>` with no kind prefix, "since GKE prefixes `gke-`
itself". An instance follows it. The management plane does not: it
composes `gke-<code>-c-mgmt`, from before the rule and before
`XCluster` and `XNetwork` existed.

The cost is visible on the nodes, which GKE names from the cluster:

```
plane     gke-gke-qw01-c-mgmt-np-qw01-c-mgmt-xxxxxxxx-xxxx
instance  gke-qw01-n-test-np-qw01-n-test-primar-xxxxxxxx-xxxx
```

The plane doubles the prefix, and spends four characters of the
node-name budget on it. The instance's own pool name is already
truncated — `np-qw01-n-test-primar` — so the budget is not notional.

The second cost is structural. `XManagementPlane` composes its
`Network`, `Subnetwork`, `Cluster`, `NodePool` and node identity
directly, where an instance composes `XNetwork` and `XCluster`. So the
plane rebuilds a subnet's name in its own `Cluster` patch:

```
CombineFromComposite over spec.code and spec.regionCode
  -> "sb-%s-c-mgmt-%s" -> spec.forProvider.subnetworkRef.name
```

A combine cannot read the EnvironmentConfig, so this is the one reason
`XManagementPlane.spec.regionCode` still has to be set, and the one
reason `installation.yml` still states a region field after the region
moved into the installation's environment.

## Why this waits for a rebuild

Every part of it is a rename of a live resource, and a rename is a
delete and a create:

- `gke-qw01-c-mgmt` is the cluster Crossplane and Argo run on. Renaming
  it rebuilds the plane and needs the composite pivoted onto the new
  cluster — step 5 of
  [management-plane-install](../recipes/infra/management-plane-install.md),
  performed again.
- Moving `Network` and `Subnetwork` from `XManagementPlane` into a
  composed `XNetwork` is the transfer in
  [crossplane-live](../recipes/infra/crossplane-live.md): withhold
  `Delete` in its own merge that reaches the plane first, then move,
  and expect a window.

Against a plane being built from nothing, none of that applies. The
names are chosen once, the composites are composed from the start, and
there is no transfer.

## What to change

**`XManagementPlane` composes `XNetwork`** rather than a `Network` and
a `Subnetwork`. With `env: c` and `label: mgmt`, `XNetwork` already
produces `vpc-<code>-c-mgmt` and `sb-<code>-c-mgmt-<regionCode>` —
the names the plane composes today, so this part is a no-op even
against a live plane.

It also composes a proxy subnet, which the plane has never had. Give
the plane's `network` block a `proxyCidr` beside its existing three,
defaulted in the same range.

**`XManagementPlane` composes `XCluster`** rather than a `Cluster`, a
`NodePool` and a node identity. This is what renames things:

```
                        today                     after
cluster                 gke-<code>-c-mgmt         <code>-c-mgmt
kubeconfig secret       gke-<code>-c-mgmt-kubeconfig  <code>-c-mgmt-kubeconfig
node identity           see the composition       sa-<code>-c-mgmt-nodes
```

Check the node pool's name against `XCluster`'s before assuming it
matches: the plane's is `np-<code>-c-mgmt` and an instance's is
`np-<code>-<env>-<label>-primary`.

**`MGMT_CTX`** is `<code>-mgmt` and the context is renamed from
whatever `plane-ctx` fetches, which builds `gke-<code>-c-mgmt` by hand.
That string moves with the cluster.

**`regionCode` stops being the plane's.** `XNetwork` takes it as a
plain patch, so `XManagementPlane` patches it from the environment the
way it patches `region` and `zone`, and `XCluster` takes it the same
way. Then:

- the plane's own `spec.regionCode` is unused, and can be removed from
  the XRD once no manifest sets it;
- `installation.yml` states no region field at all, which is what
  [contract-install](../recipes/infra/contract-install.md) already says
  the environment is for.

## What not to do

A `subnetworkSelector` with `matchLabels` will resolve the subnet
without naming it, and is tempting because it retires `regionCode`
without the rename. It is not worth it. To be unique the label has to
carry `<code>-<env>-<label>`, so the derivation moves from a name to a
label rather than disappearing — and a Kubernetes name is unique
because the API server enforces it, where a label is unique because
somebody was careful. For the field that decides which subnet a cluster
attaches to, that is a guarantee traded for a convention.

The plane's `regionCode` is not a wart in the composition. It is the
shadow of the cluster not having been migrated, and it goes when that
does.

## Ordering

Against a rebuild, in the order the recipes already run:

1. Make the changes above and merge them before the plane is built, so
   `crossplane-xrds` carries them when the boot plane applies the
   composite.
2. Build the plane from
   [management-plane-install](../recipes/infra/management-plane-install.md).
   The names are right from the first create.
3. Render an instance and confirm nothing states a region field.
4. Remove `regionCode` from `XManagementPlane`'s XRD, once no manifest
   sets it — a field cannot leave the schema while a manifest still
   carries it, or Argo diffs for ever.

Against a live plane, if it is ever done that way instead: 1, then the
`XNetwork` move as two merges with `Delete` withheld first, then the
cluster rename as a plane rebuild and pivot, then 4.

## What this does not settle

Whether `XCluster` fits the plane at all. It composes a node identity
and a `roles/container.defaultNodeServiceAccount` binding, which the
plane also composes, and the plane's cluster carries Workload Identity
config, release channel and authorized networks that an instance's may
not. Read both compositions before assuming the plane is an instance
with a different label — that assumption is what this plan is worth
checking against.

## References

- [cloud-naming](../recipes/practices/cloud-naming.md) — no kind prefix
  on a cluster, and why.
- [crossplane-live](../recipes/infra/crossplane-live.md) — moving a
  resource between composites, and what a rename does.
- [crossplane-design](../recipes/infra/crossplane-design.md) — a
  composed resource is identified by its composition name.
- [management-plane-install](../recipes/infra/management-plane-install.md)
  — building the plane, and the pivot a rename would repeat.
- [ADR-0024](../adr/0024-instances-are-their-own-composites.md) — what
  the plane composes, and what Argo installs.
