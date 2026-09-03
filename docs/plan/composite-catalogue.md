# Plan: a catalogue of composites, and what is left of it

## Context

[ADR-0025](../adr/0025-building-blocks-and-what-cannot-be-one.md)
recorded a direction and a boundary for a catalogue of building blocks
and said nothing was built. Five kinds now are. Four came out of
`XQueenswoodInstance`, which went from 2,985 lines to about 1,520:

- `XPostgres` — a Cloud SQL server, the databases named on it, and the
  one proxy identity every database is reached through.
- `XNetwork` — a VPC, the subnet a cluster sits in, and the proxy-only
  subnet a regional load balancer wants.
- `XPublicEndpoint` — the address an environment answers on, the
  certificates it terminates, and the records pointing its names at
  that address.
- `XCluster` — a GKE cluster, the pool that runs on it, and the
  identity that pool runs as.

The fifth came out of the plane:

- `XPublicZone` — a public zone one installation is authoritative for,
  and whatever records it holds in that zone directly.

All five are in `platform`, beside `XManagementPlane` and
`XManagedUnit`. `platform` means not this product rather than not this
cloud: every one of them composes GCP resources and nothing else.

The mechanics — what a transfer does, what it costs, and the three ways
it went wrong — are in
[crossplane-live](../recipes/infra/crossplane-live.md). This plan is
what is left to do, not how to do it.

## Hollowing out the plane

The plane composes 45 resources in 2,684 lines and repeats two of the
kinds above verbatim. It was left alone deliberately while the instance
was the proving ground; the instance is now proof.

- **`XNetwork`, 113 lines.** The plane's `Network` and `Subnetwork`
  bases are byte-identical to the instance's, which is what settled the
  kind's shape in the first place. `env: c` and `label: mgmt` render
  `vpc-<code>-c-mgmt` and `sb-<code>-c-mgmt-<regionCode>`, exactly what
  it composes today, so this is an adopt with no rename. It also
  composes the proxy subnet the plane has never had, so the plane's
  `network` block gains a `proxyCidr` beside its existing three.
- **`XCluster`, 228 lines.** The plane's cluster and pool already
  withhold `Delete`, so the step the instance needed first is not
  needed here — but that is not what makes this one hard. `XCluster`
  names a cluster `<code>-<env>-<label>` where the plane's carries a
  `gke-` prefix, and names the node identity and the pool differently
  too, so the move renames three live resources rather than adopting
  them. Against a live plane that is a rebuild and a pivot; against one
  built from nothing it is free, and the names come out right the first
  time. See [plane-cluster-naming](plane-cluster-naming.md), which also
  settles the one thing `XCluster` does not cover and the plane must go
  on doing itself.
- **The `ProjectService` loop, 367 lines across ten.** Two of the ten
  do not follow their own names — `cloudresourcemanager` and
  `cloudbilling` — so the plane's list is names beside APIs where the
  instance's is bare names. Both are genuinely needed here: the plane
  is the caller whose project the enablement is counted against, which
  is why the instance needed neither.

That is roughly 700 lines, and none of it needs a new kind.

It does need one thing outside the composition, and nothing links the
two today. `XManagementPlane` currently composes managed resources and
no other composite, which is why `boot-cluster-up` applies one XRD
— its own — onto the boot cluster. Composing `XNetwork` and `XCluster`
from the plane makes those kinds a prerequisite of bootstrapping, so
that recipe has to apply their XRDs and Compositions too, and
`boot-cluster-up` narrows its providers on the same rule. Without
it, `boot-mgmt-apply` fails with `no matches for kind`, which stops
the whole pipeline rather than one resource — and only on the next
bootstrap, which is rare enough that it will not be the person who made
the change. See
[management-plane-install](../recipes/infra/management-plane-install.md).

What the plane must keep is what only it has: the folder and its
bindings, the management project, Crossplane and Argo as `Release`s,
the four identities, and the DNS zone.

## `XPublicZone`

Built, and reshaped by
[ADR-0028](../adr/0028-the-apex-belongs-to-no-installation.md) before
it was ever used. The kind is not what this plan first sketched: it
holds a zone one installation is authoritative for, one per name an
environment answers on, rather than the installation's apex zone.

The argument that split it survives with a different reason under it.
An endpoint rebuilds from its own declaration; a zone does not, because
its nameservers change when it is rebuilt and something outside the
installation names them. So the two still belong to different kinds —
not because a zone must never be replaced, which a delegated one may
be, but because replacing one costs an edit the installation cannot
make itself.

The endpoint goes on naming the zone rather than owning it: two
composites cannot read each other's status, and a zone that enumerated
its tenants would make adding an environment a change to the plane.

What the plane keeps is nothing. The apex belongs to no installation
and is declared outside every control plane, so `XManagementPlane`'s
`dns` step and its XRD field go, and the estate's one zone moves out of
the installation holding it. That move is
[apex-dns-migration](apex-dns-migration.md), which is where the
ordering lives.

## `XEgress`

Nothing composes a Cloud NAT today, so this is a capability rather than
a refactor. It sits beside `XPublicEndpoint` rather than beside the
zone — instance-scoped, disposable, and about how traffic leaves one
environment rather than about what domain an installation owns.
`cloud-naming` already reserves `nat-<code>-<env>-<region>`.

It is what a payment provider that allowlists a source IP needs, which
nothing can be given while traffic leaves via whichever node runs the
adapter.

## `XArgoDestination`

Two resources in the instance composite are not this product's either:
a `ProjectIAMMember` granting the deployer identity `container.admin`
on the project, and an `Object` writing the Secret that registers the
cluster as a destination. Any cluster Argo deploys to needs exactly
that pair, and nothing about either is Queenswood.

They are a pair in the sense
[ADR-0025](../adr/0025-building-blocks-and-what-cannot-be-one.md)
means: half of it is a silent failure. A registration with no binding
syncs and then fails on every apply; a binding with no registration is
a cluster Argo cannot see.

Named in Argo's own vocabulary. An Application has a `destination`,
and what this composes is the thing a destination resolves to, so the
word is borrowed rather than invented -- the same reason
`XPublicEndpoint` reads as it does.

Not `XArgoCluster`, which parses as a cluster belonging to Argo: Argo
is not a cluster and the cluster already has a kind. Not a
tool-agnostic name either. `XPostgres` is the concrete engine where
`XCloudSQL` would be the generic wrapper, so naming the specific thing
is what this repository already does -- and every resource here is
Argo-shaped, so a name pretending otherwise would claim a portability
the composition does not have.

It should take the endpoint and the CA as spec fields rather than
composing the cluster itself. A cluster kind has no business knowing
how anything deploys to it — that is the line
[ADR-0024](../adr/0024-instances-are-their-own-composites.md) draws
between what a composite builds and what Argo installs. That leaves the
values arriving through the composing instance's status, which is the
coupling that broke this cluster's registration once already; naming it
in a spec does not remove the round trip, but it makes it a declared
dependency rather than an incidental one.

One call site today, so the shape would be read off a single example —
the caveat `XPostgres` carried, and the reason its first version
hardcoded a database name.

The custom role beside them is unrelated: it is a viewer capability
granted through the access step rather than anything to do with
deployment.

## Smaller things

- **Rename `instance-gke` back to `instance-cluster`**, for consistency
  with every other slot. Not free: renaming the slot deletes the
  composite in it, which deletes the pool, which carries `Delete`. It
  wants the two-step from
  [crossplane-live](../recipes/infra/crossplane-live.md) applied one
  level down — withhold `Delete` from the pool inside `XCluster` first,
  rename, then restore. Worth doing for the name alone, but not
  casually.
- **Stop enabling `serviceusage` on an instance project.** The same
  argument that removed `cloudresourcemanager`: enabling any API on a
  project is a Service Usage call against a project that has nothing
  enabled yet, so the call cannot require what it is about to turn on.
  Left alone when the other went, because one change should remove one
  thing.
- **The `allowedServices` constraint**, which bounds what may be
  enabled at all rather than reconciling what was. Recorded in
  [cloud-just-migration](cloud-just-migration.md) under what is
  outstanding.

## Ordering

The plane before the new kinds. Hollowing it out uses kinds that exist
and are proven on a live instance; `XPublicZone` and `XEgress` both add
kinds, and one of them touches DNS, where a mistake is measured in
registrar propagation rather than in reconciles.

The estate's zone leaves the plane before any of this, for its own
reasons rather than the plane's — see
[apex-dns-migration](apex-dns-migration.md). Once it has, a plane
rebuild has no zone to preserve, which is one fewer thing
[plane-cluster-naming](plane-cluster-naming.md) has to sequence around.

Within the plane, the `ProjectService` loop first: it is the only one
of the three that transfers no ownership at all, because a loop can
reproduce the composed names it replaces exactly.
