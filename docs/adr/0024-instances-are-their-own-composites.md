# 24. Instances are their own composites

<!-- tessl-plugin: deployment -->

## Status

Accepted. Extends
[ADR-0022](0022-cloud-foundation-and-environment-lifecycle.md), which
decided that a folder is an installation, that one management plane
serves every environment in it, and that an environment composite
carries `state: up | draining | down`. This says where that composite
lives and what the plane's relationship to it is.

Supersedes the `spec.instances` sketch in
[cloud-just-migration](../plan/cloud-just-migration.md), which had the
instances declared as a field on the plane's own composite.

## Context

The plane's API was renamed from `XQueenswoodInstallation` to
`XManagementPlane`, because what it builds is a folder, a management
project, a cluster, the identities that run it, and Crossplane and Argo
on top — and nothing bank-shaped at all. That rename made a question
unavoidable that the old name had hidden: if the plane's kind no longer
names this bank, where does this bank get declared?

The plan's answer was `spec.instances` on the plane's XRD. That does not
sit with the rename, and it does not sit with ADR-0022 either, which
already says "an environment composite carries `state`" — a separate
composite, stated in passing and never reconciled with the plan.

Underneath the naming question is a real one, and it is the one worth
answering first: **what is a management plane for, if the things it
deploys are declared elsewhere?** Left unanswered, the plane reads as a
container for permanent resources, which understates it badly enough to
make the whole split look arbitrary.

There is also evidence now. Re-adopting `qw01` under the new kind meant
deleting the plane's composite and recreating it: 47 managed resources
removed from Kubernetes and re-adopted from GCP. It went cleanly,
because nothing the composite owns carries `Delete`. But every resource
that composite owned passed through that operation, and what an
operation touches is decided by where the composite boundary is drawn.

## Decision

### The plane is a runtime, not a container

Three things it is, and the first is the one that answers the question:

- **It is the only thing that reconciles anything.** The providers, the
  credentials they use, the Argo that applies manifests, and the
  controllers that turn a declaration into a GCP call all live on it.
  Every composite in the installation is reconciled by this plane —
  instances included. That is true in every design considered here.
- **It is the durable home.** The folder, the platform identity, the
  Secret Manager entries, the backup bucket: everything that must
  outlive any particular environment sits in the management project,
  protected by `managementPolicies` and liens under ADR-0022.
- **It is where the API lives.** An XRD and a Composition loaded onto
  the plane are what make a kind declarable at all. Adding a capability
  to an installation is loading another pair, not rebuilding anything.

So the plane manages the instances. What it does not do is *compose*
them, and that distinction is the whole of this decision.

### An instance is its own composite

One XR per environment, of its own kind, reconciled by the plane
alongside the plane's own composite. Not a field on the plane's XR.

Four reasons, in the order they matter:

- **A composite is a unit of replacement, so it is a blast radius.**
  Replacing the plane's composite — a kind rename, a composition
  rewrite, a rebuild in a cheaper region — deletes and recreates every
  resource it composes. That is not hypothetical: it is what re-adopting
  `qw01` did to 47 resources. With instances inside it, the same
  operation would put every environment's project, cluster and database
  through delete-and-adopt. Withholding `Delete` makes that survivable;
  it does not make it something to do with production data in scope.
- **The lifecycles are different, and one of them is a field.** ADR-0022
  makes `state: up | draining | down` a per-environment property. A
  plane has no such state — it is either there or the installation is
  not running.
- **A list is a bad identity.** Crossplane matches composed resources by
  their composition resource name, so a list of instances needs those
  names generated per item. Remove an item from the middle and the ones
  after it renumber, and the resources they name are deleted and
  rebuilt. The access step already hashes its member rather than
  indexing it for exactly this reason, and there the cost of getting it
  wrong is a re-created IAM binding. Here it would be a live
  environment's project.
- **The plane's kind stays generic.** A second application on the same
  plane is another XRD and another manifest, not a wider plane. That is
  what the rename was for, and putting this bank's environments on the
  plane's own schema would undo it in one field.

### The kind is `XQueenswoodInstance`, in the product's own group

`XQueenswoodInstance` in `queenswood.repldriven.com`, against
`XManagementPlane` in `platform.repldriven.com`.

**Instance**, because ADR-0022 and the cloud-naming rule already use
that word — one project per instance, discriminated by the
env letter (`d`, `n`, `p`). Not *environment*, which is the word
`QUEENSWOOD_ENV` owns and which is being retired: reusing it would make
every sentence ambiguous between the model being replaced and the one
replacing it. Not *installation*, which is the folder.

**The group says who owns the kind.** Anything in
`platform.repldriven.com` should be installable whatever runs on top;
`queenswood.repldriven.com` is where this bank's kinds live. The
previous generation's `platform.queenswood.repldriven.com` —
`XPlatform`, `XQueenswoodApex`, `XQueenswoodCertificate` — is retired
with this: all three are loaded on the plane with no composites of
their kinds, and what is useful in them (the apex address and DNS
record, the managed certificate) becomes composed resources of the
instance rather than three kinds and the references between them.

### The project belongs to the instance and survives `down`

The instance composes its own project, with the protected tier's
policies from ADR-0022 — `managementPolicies` without `Delete`, and a
lien — so that `down` is a stopped environment rather than a deleted
one: node pools at zero, CloudSQL `activationPolicy: NEVER`, whatever
is purely rebuildable absent, and the project, its data, its DNS zone
and its identities still standing.

**The project is durable, so its data may be too.** This paragraph
first said the opposite — that an instance's project holds nothing
whose loss is unrecoverable, and that the FDB backup bucket therefore
belongs in the management project. That followed from `down` meaning
destroy, which is the generation this ADR replaces. `down` is now a
declared state, demonstrated in both directions: the project, network,
identities and cluster survive it and only the nodes go. A project that
survives every `down` is not a disposable place to keep data, and
pushing every environment's buckets and disks into the management
project makes the one project that runs the control plane into a store
for everything else.

So an instance's operational state — its database, its persistent
disks, the buckets its workloads write — lives in the instance's own
project, with the workload that owns it. This is also what the security
foundations guide does: a workload's data belongs to the workload's
project, and what it separates out is keys and secrets, into a
per-environment `prj-<env>-kms` and `prj-<env>-secrets`.

What still leaves the project leaves on blast radius rather than on
disposability, and it is a shorter list than before. Cloud SQL needs
nothing: IAM database authentication makes the workload's service
account the database user, so no password is created to keep anywhere.
The FDB backup encryption key is the real case — symmetric key material
that is not a GCP credential, generated once, and whose loss turns every
backup into noise — and whoever may operate an instance should not be
able to destroy it. Where that key lives is deferred until it exists,
deliberately, because one key is a thin basis for choosing a project
layout and the guide's answer is waiting when there is a second thing to
put beside it.

A project id is consumed permanently, so a retired instance's project is
retired deliberately — by lifting its lien — rather than by reconciling
it away. That matters more now that the project holds data: retiring one
is the act that loses it.

### The instance finds the folder by name, not by reference to the plane

The instance's project needs a folder. It names the composed `Folder`
managed resource directly — `fldr-<code>`, derived from its own
`spec.code` — rather than referring to the plane's composite or
carrying the numeric id in its manifest.

That reference resolves against a managed resource, not a composite, so
Crossplane orders the two itself and neither composite knows about the
other. Deleting an instance never touches the plane; replacing the plane
leaves the instances reconciling against the same folder.

It makes the *name* load-bearing, which is what
[cloud-naming](../recipes/cloud-naming.md) already requires: every name
derives from the four-character code, so the reference is derivable
rather than configured.

### Workloads arrive by Argo, not by the composite

The composite builds what an instance *is* — the project, the network,
the cluster, the identities, the database, the names it answers on. It
does not install what runs there. That arrives through Argo, from an
Application the plane holds, reading the chart from one repository and
the values from another.

The line is between a cloud API and a cluster. A composite creates the
identity a controller runs as; Argo installs the controller. Crossplane
could in principle do both — `provider-helm` exists — but doing so would
mean a second delivery path, its own provider configuration and its own
credential into every instance cluster, beside one that already works
and is already how every service reaches the instance.

It also keeps the failure modes apart. A composite that stops
reconciling leaves the workloads running; an Application that fails to
sync leaves the infrastructure standing. Neither takes the other down,
and each reports in its own place.

### One manifest per composite, flat in the installation's directory

```
qw01/installation.yml   # XManagementPlane
qw01/dev.yml            # XQueenswoodInstance, state: up
qw01/prod.yml           # XQueenswoodInstance, state: down
```

All in the private manifests repository, in the installation's own
directory, which the plane's `installation` Application already syncs
whole. Adding an instance is adding a file, and `prune: false` on that
Application means adding one can never remove anything.

Flat rather than nested, because that Application's directory source
does not recurse: a `qw01/instances/` subdirectory needs
`directory.recurse: true` on the Application before anything in it is
read at all, which is a silent no-op rather than an error.

## Consequences

**A plane rebuild stops reaching production data.** This is the property
bought, and it is worth stating as the outcome rather than the reason:
after this, deleting and recreating the plane's composite touches the
folder, the management project, the cluster that runs Crossplane and the
identities — and nothing an environment holds.

**An installation is more than one file.** The manifests repository
carries a plane manifest and one per instance, and a change spanning
both is two commits or one commit touching two files. The alternative
was one file whose every edit reconciles a control plane and a
production database together.

**Names are load-bearing across composites.** An instance resolving
`fldr-<code>` depends on the plane having composed a folder under that
name. Renaming a composed resource in the plane's composition is
already a rebuild of that resource; after this it can also strand every
instance in the installation. The naming rule is the thing that makes
this safe, so it stops being a convention and becomes an interface.

**Retired instances accumulate projects.** `down` leaves a project
standing, deliberately, and a folder collects the retired ones.
Reclaiming a project id is impossible and reusing one is the point, but
an installation running for years needs someone to retire them
deliberately rather than expecting reconciliation to.

**Three XRDs are deleted with nothing to migrate.** `XPlatform`,
`XQueenswoodApex` and `XQueenswoodCertificate` have no composites of
their kinds on the plane, so removing them is a file deletion. What they
knew about apex addresses and managed certificates is re-expressed
inside the instance composition rather than ported.

**`state: draining` is still unproven.** ADR-0022 makes the export Jobs
the teardown gates and says to treat the design as unproven until a
cycle runs unattended, with Keycloak's restore (#349) the outstanding
precondition. Nothing here changes that: an instance composite can ship
with `up` and `down` before `draining` is trustworthy, and should.

## Future

Whether an instance composes the bank's workloads directly as `Release`
resources, or composes an Argo `Application` per instance and lets Argo
own the workload tree, is left open. The previous generation did the
former through `queenswood-platform`; the latter puts the workload
manifests under the same review as everything else and keeps the
composition to infrastructure. Where the chart is published, and which
identity reads it, follows from that choice rather than preceding it.

Whether prod eventually gets a plane of its own is answered in
ADR-0022 — a second instance of the same configuration, not a
re-architecture — and this decision does not change the answer, since
an instance manifest applied to a second plane is the same file.
