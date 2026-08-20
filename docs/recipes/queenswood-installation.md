# Queenswood installation

<!-- tessl-plugin: deployment -->

## Problem

You have a control plane that deploys Crossplane applications, and want
to deploy Queenswood.

## Solution

Configure it for your organisation, then commit a manifest. An
installation is one file — everything Queenswood needs from your
organisation is in it or named by it — and changing what exists
afterwards is a merge.

Building the plane that reads that file is
[crossplane-app-deployment](crossplane-app-deployment.md). This recipe
starts once you have one.

### An installation, in full

This is the whole of it. The sections below take it a field at a time.

```yaml
apiVersion: platform.repldriven.com/v1alpha1
kind: XManagementPlane
metadata:
  name: "<metadata-name>" # e.g. qw01
  namespace: crossplane-system
spec:
  code: "<code>" # e.g. qw01
  region: "<region>" # e.g. europe-west2
  regionCode: "<region-code>" # e.g. euw2
  zone: "<zone>" # e.g. europe-west2-a
  access:
    platformViewer: ["group:grp-gcp-<code>-platform-viewer@<your-domain>"]
    platformAdmin: ["group:grp-gcp-<code>-platform-admin@<your-domain>"]
    clusterAdmin: ["group:grp-gcp-<code>-cluster-admin@<your-domain>"]
    secretsAdmin: ["group:grp-gcp-<code>-secrets-admin@<your-domain>"]
  management:
    projectId: "prj-<code>-c-mgmt-xxxxxx"
    adopt: "projects/prj-<code>-c-mgmt-xxxxxx"
    source:
      repoURL: "<source-repo>"
      targetRevision: "main"
      pathPrefix: "."
    manifestRepoURL: "<manifest-repo>"
  createFolder:
    parent: "organizations/<org-id>"
    displayName: "fldr-<code>"
    folderId: "folders/<folder-id>"
```

A first apply carries neither `adopt` nor `folderId`: both name
something that does not exist yet, and a pre-set external name turns a
create into a failed observe. Both are added once the folder and project
do, and what they then do is below.

Angle brackets mark what differs on every installation: your domain,
your GitHub organisation, and the numeric organisation and folder ids —
which are placeholders for a second reason too, since a public document
should carry names rather than account identifiers. `xxxxxx` stands for
the random suffix GCP appends to a project id.

`<source-repo>` is upstream — `https://github.com/repldriven/queenswood`
— unless you run a fork you review or a mirror that vendors this layout,
which is a real choice rather than a placeholder.
`<manifest-repo>` is always yours.

The kind is `XManagementPlane` rather than anything named after this
bank. What the manifest builds is the folder's control plane — a
management project, a cluster, the identities that run it, and
Crossplane and Argo on top — and Queenswood is then one of the things
that plane can install, rather than the reason it exists. The
installation is still the folder, and the manifest is still one file
per folder.

`metadata.name` and `spec.code` carry the same string and are not the
same thing. Nothing in the composition reads `metadata.name`: every
composed name derives from `spec.code`, which the XRD constrains to four
lowercase characters because it is baked into GCP resource names across
the whole organisation. `metadata.name` only has to be unique in one
namespace on one cluster. Nothing enforces that they agree, and the
recipes assume they do — `gcp-plane-apply` waits on
`xmanagementplane/<code>` — so a manifest where they differ
reconciles correctly and is then invisible to the tooling that built it.

`billingAccountId` is absent for the reason given below.

### Two repositories

Queenswood arrives as an API and is described by a manifest. The two sit
apart because they differ in who owns them and who may read them.

**The source**, named by `management.source`, holds the XRD, the
composition and the provider packages — what an `XManagementPlane`
means. `repoURL` names it, `targetRevision` pins the revision the plane
follows, and `pathPrefix` says where in that repository the project sits:
`.` for a fork that keeps this layout, a directory for a mirror that
vendors it. Upstream, a fork you control, or a mirror inside your own
network; the plane only has to reach it. An organisation that reviews
what it runs forks and pins a tag, and upgrading is then a merge like
everything else.

**The manifests**, named by `management.manifestRepoURL`, hold one file
per installation. Private, because a manifest is identifiers — the
organisation, the folder, the billing account, the project ids. Nothing
in it is secret, and nothing in it wants indexing either.

A change to the composition and the manifest change it needs land in
different repositories, so keep a new XRD field optional with a default.
That makes the two-sided change rare rather than co-ordinated.

### The App that reads the private repository

The source repository may be public, and then needs no credential. The
manifests are private, so Argo authenticates as a GitHub App — created
once by an organisation owner, yielding an App ID, an Installation ID
and a private key. See [argocd-github](argocd-github.md) for how one is
made and what goes wrong with it.

All three go to Secret Manager in the management project, into the
container the composite composes for them, where the secrets identity
reads them. They are stored together so the identifiers travel with the
key rather than through a second channel, and nothing else on the
cluster holds a credential at all.

### The manifest

One file, and the fields it carries. `management.*` says which project
and where its configuration comes from, which a plane needs because it
composes a folder and a project and `createFolder.*` is the other half
of that pair. Everything describing the cluster it builds is flat,
because a plane has one cluster and nothing else with a size — where an
instance groups, since `diskSize` there could be a node's or a
database's.

- **`code`** — the installation's short name, which every resource name
  derives from. See [cloud-naming](cloud-naming.md).
- **`region`, `regionCode`, `zone`, `machineType`** — stated
  rather than left to the XRD's defaults. A name carries the region
  abbreviation, and moving the region, the zone or the machine type
  rebuilds a subnet, a cluster or a node pool, so a default that
  changed underneath a live installation would move it silently. State
  the machine type the pool is already running: it is immutable, so a
  value that merely differs replaces the pool, and the pool being
  replaced is the one running the Crossplane doing the replacing.

  The general rule is stronger than the silent-move argument, and
  applies to any field an XRD defaults and a composition patches from.
  A default is absent until the regenerated CRD arrives, which is a
  window a composition can reconcile inside. See
  [crossplane](crossplane.md).
- **`management.projectId`** — always supplied. A project id is consumed
  permanently and cannot be undeleted into usefulness, so carrying it in
  the file is what stops a rebuilt plane minting a second management
  project beside the first.
- **`createFolder.parent`** — where a folder would be created.
  **`createFolder.folderId`** adopts an existing one instead, which is
  what you set when a folder was handed to you, and what makes a rebuilt
  plane take over rather than build a second installation.
- **`billingAccountId`** — supplied when the management project is
  created rather than committed, because the account is a property of
  the identity, which holds `billing.user` on exactly one. Nothing then
  declares it: the field is absent from the managed resource and absent
  from `atProvider` too, so a project billed once stays billed with
  reconciliation neither owning the field nor fighting a change to it.
  Instances get theirs from the installation's shared facts instead.
- **`access`** — below.

### An instance manifest

One file per instance, beside the plane's. Every field below is stated
at what the XRD already defaults to, so this is both a worked example
and the answer to "what can I change".

```yaml
apiVersion: queenswood.repldriven.com/v1alpha1
kind: XQueenswoodInstance
metadata:
  name: qw01-n-test
  namespace: crossplane-system
spec:
  code: "qw01"
  env: "n"
  label: "test"
  projectId: "prj-qw01-n-test-xxxxxx"
  state: "up"
  region: "europe-west2"
  regionCode: "euw2"
  zone: "europe-west2-a"
  access:
    # Granted the pod-log-reader role in this project. Empty binds
    # nothing, and reading a log then means joining clusterAdmin.
    platformViewer: ["group:grp-gcp-<code>-platform-viewer@<your-domain>"]
  network:
    podsCidr: "10.20.0.0/16"
    proxyCidr: "10.40.0.0/24"
    psaPrefixLength: 16
    servicesCidr: "10.30.0.0/20"
    subnetCidr: "10.10.0.0/16"
  cluster:
    diskSize: 50
    diskType: "pd-standard"
    machineType: "e2-standard-2"
    nodeCount: 3
    releaseChannel: "REGULAR"
  keycloak:
    database:
      availabilityType: "ZONAL"
      backupEnabled: true
      diskSize: 10
      diskType: "PD_HDD"
      edition: "ENTERPRISE"
      pointInTimeRecovery: false
      tier: "db-custom-1-3840"
      version: "POSTGRES_18"
```

`code`, `env`, `label` and `projectId` are the only required fields;
everything else defaults. State them anyway, for the reason
[crossplane](crossplane.md) gives — a defaulted field is absent for as
long as a regenerated CRD takes to arrive, and a manifest that states
its values does not care.

`adopt`, `displayName` and `billingAccountId` are omitted rather than
defaulted. The first takes over an existing project, the second names
it for humans, and the third overrides the installation's billing
account for this instance alone.

**Grouped where a name would otherwise be ambiguous.** `diskSize` on an
instance could mean a node's or the database's, so both group; the
plane has one cluster and nothing else with a size, so its
`machineType` stays flat. `keycloak.database` rather than `database`
because FoundationDB is the bank's own store and will want a section of
its own — neither should get to be "the database".

**What is not here is deliberate.** The composition keeps
`ipv4Enabled: false`, `sslMode: ENCRYPTED_ONLY`,
`cloudsql.iam_authentication`, `GKE_METADATA`, `removeDefaultNodePool`
and the deletion protections as literals. Those are not choices an
environment makes differently; they are the properties the ADRs argue
for, and a field that can be set to the wrong value is a way to lose
them in a file reviewed as configuration rather than as architecture.

### The installation's shared facts


A second file in the same directory, holding what is true of the whole
installation rather than of one composite:

```yaml
apiVersion: apiextensions.crossplane.io/v1beta1
kind: EnvironmentConfig
metadata:
  name: <code>
  labels:
    installation: <code>
data:
  billingAccountId: "<billing-account-id>"
  argoServiceAccount: "sa-<code>-c-argo@prj-<code>-c-mgmt-xxxxxx.iam.gserviceaccount.com"
```

The label is what selects it: a composition matches `installation`
against its own `spec.code`, because the name has to follow the code and
a reference takes a literal. The resource is cluster-scoped, so nothing
composes it — a namespaced composite may not compose a cluster-scoped
kind — and Argo applies it from this directory like everything else.

What belongs here is a fact identical across every instance that carries
no naming or ordering consequence. A billing account qualifies, and so
does Argo's own address: an instance grants Argo access to its project,
which needs the account's full email — the code is on the instance and
the management project is not, and a combine cannot mix a composite
field with an environment one.
**Region does not**: it is baked into resource names, so an
installation-wide default would silently want to rebuild every
instance's subnet and cluster when edited, which is the hazard the
manifest's own `region` field avoids by being stated. **A folder id
does not** for parenting either — a composition resolves the folder by
reference, which orders the two resources and cannot go stale, where a
copied number does neither.

Resolution is `Required`, so an installation with no config is a
composite that says so rather than a project that comes up unbilled and
looks healthy. That makes the order matter in one direction: this file
exists before a composition reads it, and adding it early is free
because a config nothing reads does nothing.

### Who holds which capability

`access` maps a capability to the principals that hold it, and carries
whole IAM member strings because a principal need not be a group. An
organisation with access groups already maps them straight in and mints
nothing:

- **`platformViewer`** — day-to-day reading, across the folder and the
  management project.
- **`platformAdmin`** — impersonating the identity that builds a plane.
- **`clusterAdmin`** — administering the clusters.
- **`secretsAdmin`** — the management project's secrets.

The last three are break-glass: joined for a task and left. Only the
viewer capability is expected to carry anybody day to day, and it is the
one capability also bound at the organisation, with `roles/browser`,
because tooling cannot reach a folder without resolving the organisation
above it. Everything else `access` grants is folder- or project-scoped
and composed from this mapping.

A capability left out binds nothing, so an empty mapping installs. That
matters where the principals do not exist yet: minting groups needs a
quota project, and until the management project exists there is none. So
install first, mint the groups against the new project, and add them in
a second merge.

### Changing what exists

By editing the manifest and merging it. A merge is the privileged
action, so merged state applies and a `pull_request` trigger gets no
cloud identity; otherwise a fork's pull request runs as the platform
identity.

No edit deletes a project. Retiring one is a deliberate second act — lift
its lien, then delete — and where no lien is on yet, what stands between
a manifest edit and a deleted project is `managementPolicies` without
`Delete`.

`status` is read back rather than committed. It carries the folder id,
the management project and the platform identity, which is where to read
them rather than from any document.

### Two states that pass every check

Both leave the composite Ready and every managed resource green.

**A manifest that was never pushed.** Applying from a boot plane reads
the file from a checkout, and that is all a boot plane needs. A plane
that has taken over reads it from GitHub, so a file that exists locally
and was never pushed satisfies everything up to the handover and then
reconciles from nothing.

**A secret with no version.** The composite composes the container the
App's credentials go in, and a person adds the version. Between the two,
Argo holds no credential for the repository it reconciles from. That
secret is the link between the plane and its manifests, and it is the
one piece the composite deliberately cannot fill.

## Rules

**MUST:**

- Change what exists by editing the manifest, not by acting on GCP.
- Apply from merged state only. A `pull_request` trigger gets no cloud
  identity.
- Push the manifest before a plane takes over reading it from git.
- Supply `management.projectId` always, and `createFolder.folderId`
  wherever the folder already exists.
- Give `metadata.name` and `spec.code` the same string.
- Keep the manifests repository private, and read `status` back rather
  than committing it.

**MUST NOT:**

- Commit anything secret beside the manifest.
- Name a principal in `access` that does not exist. IAM rejects the
  binding, not the manifest.
- Create a key for any identity the installation composes.
- Leave a new XRD field required, when the manifest that sets it lives
  in another repository.

**MAY:**

- Point `management.source` at upstream, a fork, or a mirror that
  vendors this layout, and pin `targetRevision` to a tag.
- Install with an empty `access` mapping, and add capabilities later.
- Create more than one installation. One manifest per folder.

## References

- [crossplane-app-deployment](crossplane-app-deployment.md) — building
  the plane that reads the manifest.
- [argocd-github](argocd-github.md) — the App that reaches a private
  repository, and how it is rotated.
- [cloud-account](cloud-account.md) — the organisation, access groups and
  billing account, none of which has an API.
- [cloud-naming](cloud-naming.md) — the installation code, and what every
  name derives from it.
- [ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
  — the folder as an installation, and why foundations are not deleted.
- [ADR-0023](../adr/0023-installation-naming-and-access.md) — the access
  capabilities and who holds them.
- `infra/platform/crossplane-xrds/xmanagementplane-xrd.yml` — the
  fields above, as a schema.
- `justfiles/gcp.just` — `gcp-plane-manifest` mints a first manifest,
  `gcp-plane-apply` applies a committed one, `gcp-github-app-secret`
  stores the App's three values, and `gcp-secret-version` puts any
  other one into the entry its composite made.
