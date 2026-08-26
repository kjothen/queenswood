# Plan: a catalogue of composites, and what is left of it

## Context

[ADR-0025](../adr/0025-building-blocks-and-what-cannot-be-one.md)
recorded a direction and a boundary for a catalogue of building blocks
and said nothing was built. Four kinds now are, all extracted from
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

All four are in `platform`, beside `XManagementPlane` and
`XManagedUnit`. `platform` means not this product rather than not this
cloud: every one of them composes GCP resources and nothing else.

The mechanics — what a transfer does, what it costs, and the three ways
it went wrong — are in
[crossplane](../recipes/infra/crossplane.md). This plan is what is left
to do, not how to do it.

## Hollowing out the plane

The plane composes 45 resources in 2,684 lines and repeats two of the
kinds above verbatim. It was left alone deliberately while the instance
was the proving ground; the instance is now proof.

- **`XNetwork`, 113 lines.** The plane's `Network` and `Subnetwork`
  bases are byte-identical to the instance's, which is what settled the
  kind's shape in the first place. `env: c` and `label: mgmt` render
  `vpc-<code>-c-mgmt` and `sb-<code>-c-mgmt-<region>`, exactly what it
  composes today, so this is an adopt with no rename.
- **`XCluster`, 228 lines.** The plane's cluster and pool already
  withhold `Delete`, so the step the instance needed first is not
  needed here.
- **The `ProjectService` loop, 367 lines across ten.** Two of the ten
  do not follow their own names — `cloudresourcemanager` and
  `cloudbilling` — so the plane's list is names beside APIs where the
  instance's is bare names. Both are genuinely needed here: the plane
  is the caller whose project the enablement is counted against, which
  is why the instance needed neither.

That is roughly 700 lines, and none of it needs a new kind.

What the plane must keep is what only it has: the folder and its
bindings, the management project, Crossplane and Argo as `Release`s,
the four identities, and the DNS zone.

## `XPublicZone`

The other half of the split
[ADR-0024](../adr/0024-instances-are-their-own-composites.md) implies
and #557 only did one side of. A public zone is the installation's and
a public endpoint is an environment's, and their lifecycles are
opposite: an endpoint rebuilds from its own declaration, while a
recreated zone gets new nameservers the registrar does not follow and
draws from a finite per-domain pool.

The estate already shows the line. Zone-level records — the apex and
`_dmarc` TXT — carry `Delete` and belong to the plane; an environment's
A and validation records belong to its endpoint and do not.

The endpoint names the zone rather than owning it, and should go on
doing so after this exists: two composites cannot read each other's
status, and a zone that enumerated its tenants would make adding an
environment a change to the plane.

## `XEgress`

Nothing composes a Cloud NAT today, so this is a capability rather than
a refactor. It sits beside `XPublicEndpoint` rather than beside the
zone — instance-scoped, disposable, and about how traffic leaves one
environment rather than about what domain an installation owns.
`cloud-naming` already reserves `nat-<code>-<env>-<region>`.

It is what a payment provider that allowlists a source IP needs, which
nothing can be given while traffic leaves via whichever node runs the
adapter.

## `XDeployTarget`

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

Named for what the cluster becomes rather than for what consumes it.
The Secret's shape is Argo's, but the concept is somewhere the platform
may deploy to, and a kind that named the tool would read as though the
tool were a cluster -- which it is not, and the cluster already has a
kind. The implementation being Argo-shaped is the composition's
business, the same way `XPostgres` is not called `XCloudSQL`.

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
  [crossplane](../recipes/infra/crossplane.md) applied one level down —
  withhold `Delete` from the pool inside `XCluster` first, rename, then
  restore. Worth doing for the name alone, but not casually.
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

Within the plane, the `ProjectService` loop first: it is the only one
of the three that transfers no ownership at all, because a loop can
reproduce the composed names it replaces exactly.
