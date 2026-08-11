# Cloud foundation

<!-- tessl-plugin: deployment -->

## Problem

You want to run Queenswood on Google Cloud, and to keep deciding what
exists as you go.

## Solution

Install an API, then commit a manifest. What it lists exists, and what it
stops listing goes away.

### What the API does

`XQueenswoodInstallation` is one Crossplane composite owning the contents
of a folder. Given that folder and an identity with rights on it, it
creates:

- the **management project**, running the platform, and the platform's
  own identity
- **folder org policies**, and the durable bucket and secrets that
  outlive every instance
- **a project per instance**, with its network, cluster, database and
  service accounts, plus a public address and certificate when it
  declares a domain

Instances carry a `state`, so stopping one is an edit. The folder,
management project and instance projects are liened, so nothing the
composite does can delete them.

It ships as a Crossplane Configuration package, not a Helm chart.

### A deployment, and what it produces

```yaml
apiVersion: platform.queenswood.repldriven.com/v1alpha1
kind: XQueenswoodInstallation
metadata:
  name: qw01
  namespace: crossplane-system
spec:
  code: "qw01"
  region: europe-west2
  regionCode: euw2
  zone: europe-west2-a
  access:
    platformViewer: ["group:grp-gcp-qw01-platform-viewer@example.com"]
    platformAdmin: ["group:grp-gcp-qw01-platform-admin@example.com"]
    clusterAdmin: ["group:grp-gcp-qw01-cluster-admin@example.com"]
    secretsAdmin: ["group:grp-gcp-qw01-secrets-admin@example.com"]
  management:
    projectId: prj-qw01-c-mgmt-xxxxxx
  createFolder:
    parent: organizations/<org-id>
    displayName: fldr-qw01
  # Not built. Instances are where this goes -- see "Where this
  # stands" -- and the fields below describe that rather than report it.
  instances:
    - name: test
      state: up
    - name: prod
      state: down
      domain: queenswood.example
      automatedDatabaseBackups: true
```

Render one rather than writing it: `just gcp-plane-manifest` fills in
what it can discover and prints the rest, and the file it produces is
the installation's whole record. No billing account appears in it — the
identity must hold `billing.user` on one before it can link a project,
so that binding already settles which account is used.

```mermaid
flowchart TD
  fldr[folder] --> mgmt[management project<br/>platform, liened]
  fldr --> test[test project<br/>running]
  fldr --> prod[prod project<br/>stopped, data kept]
  mgmt -.creates and reconciles.-> test
  mgmt -.creates and reconciles.-> prod
```

- **management** — the platform, and the secrets and backups outliving
  both instances.
- **test** — running, reached privately, with no address or certificate
  because it declares no `domain`.
- **prod** — exists and holds its data, nothing running, no bill beyond
  storage.

`instances: []` is valid, and the cheapest useful deployment.

### Changing what exists

- `state: up` — running.
- `state: draining` — ordered shutdown. The exports that must precede any
  deletion run, and are waited on.
- `state: down` — node pools at zero, database not activated, data kept.
- Remove the entry — that instance's resources go.

No edit deletes a project. Liens make retiring one a deliberate second
act: lift the lien, then delete.

### The two values you need

- **A folder id**, written `folders/<folder-id>`.
- **An identity** with `projectCreator` and `folderIamAdmin` on that
  folder, `billing.user` on a billing account, and
  `orgpolicy.policyAdmin` on the folder where the organisation allows it.

It lives outside the installation — in the organisation's automation
project, or one small project you create for it.

`spec.createFolder.displayName` labels the installation for people and
nothing else — the folder id is the identifier, and a folder handed to
you carries whatever name its organisation chose. `gcp-plane-apply`
suffixes it like a project id unless you pass one, since display names
must be unique among siblings and a fixed name would refuse the second
installation under a parent. `spec.createFolder.folderId` adopts an
existing folder instead of creating one, which is what makes a rebuilt
control plane take over rather than build a second installation beside
the first.

`spec.management.projectId` is always supplied rather than optional. A
project id is consumed permanently and cannot be undeleted into
usefulness, so carrying it in the manifest is what stops a rebuilt plane
minting a second management project beside the first.

Deleting a folder is not something reconciliation repairs. A soft-deleted
folder still reads as existing, so the provider reports it Available for
the whole 30-day window; the composite's readiness check on
`lifecycleState` is what turns that into a visible failure.

`spec.createFolder.parent` takes `organizations/{id}` or `folders/{id}`,
and `folders.create` is checked on that parent. So the split below is
whether you were given a parent to create in or one folder to use. Ids
are required, not paths of display names; `just gcp-preflight` lists the
parents you can see.

## If you own the organisation

No Google Cloud at all yet: start with
[cloud-account](cloud-account.md), which is the browser-only half.

1. `just gcp-preflight` — organisation, billing account, your direct
   roles, and candidate parents.
2. `just gcp-boot-identity`, as the operating user — the bootstrap
   project and service account, `billing.user`, and
   `grp-gcp-qw01-platform-admin@` allowed to impersonate it.
3. `just gcp-boot-org-roles`, as a member of `grp-gcp-org-admin@`
   — the organisation roles only an admin can grant.
4. `just gcp-adc-boot`, then `just gcp-plane-up` — a throwaway
   kind cluster running Crossplane and the GCP provider, authenticating
   from ADC that impersonates the bootstrap identity. No key exists, and
   `just gcp-adc-revoke` ends it.
5. Apply the manifest with `spec.createFolder.parent` naming where the
   folder goes, and no `folderId` — that field adopts, and there is
   nothing yet to adopt.
6. Read the folder id, management project and platform identity from
   `status`.
7. `just gcp-plane-pivot` — move the manifest onto the management cluster
   it created, then discard the kind cluster.

After the pivot the management cluster reconciles its own project and
folder. The liens are what make that safe.

## If you are given a folder

One folder to use rather than a parent to create in. If they will grant
`resourcemanager.folders.create` on a parent instead, take that and
follow the path above — it is a smaller ask than organisation rights.

Ask for:

- The folder id.
- An identity with `projectCreator` and `folderIamAdmin` on it, and
  `billing.user` on a billing account.
- `orgpolicy.policyAdmin` on the folder. Failing that, ask that
  `compute.skipDefaultNetworkCreation` and
  `iam.disableServiceAccountKeyCreation` are enforced on it for you —
  without the first every project is born with an unwanted default VPC,
  and without the second the ban on keys is a convention.
- Either that they apply the manifest from their control plane, or a
  Workload Identity Federation binding from that identity to your
  repository.

Then commit the manifest with `spec.createFolder.folderId` set to the
folder you were given, and whoever holds the control plane applies it.
There is no pivot: it already lives somewhere permanent. Break-glass is
their organisation admin, so repair is a request rather than a command.

`spec.createFolder.parent` is required even on this path, where nothing
is being created. Read it off the folder — `gcloud resource-manager
folders describe` reports it — rather than asking for it. The field
being mandatory here is a wart of the schema rather than a permission
you need.

## Both paths

### Four identities

- **The bootstrap identity** creates the management project. Members of
  `grp-gcp-qw01-platform-admin@` impersonate it; where an organisation
  provisions folders, a CI runner assumes it through federation and no
  person can.
- **The platform identity** is created by the manifest and used by the
  management cluster through Workload Identity. Nobody impersonates it.
- **The secrets identity** reads Secret Manager and does nothing else,
  for the operator that turns a stored credential into a cluster
  Secret. Separate from the platform identity, which could do the same
  reading but can also create projects and administer every cluster in
  the folder.
- **The node identity** is what the management cluster's nodes run as,
  holding only what a GKE node needs to report itself. Never the
  default compute service account: that one is shared by everything in
  the project that never chose an identity, and holds whatever the
  organisation's policy on automatic grants leaves it holding. A node
  pool asks for `cloud-platform` scopes, so whatever it holds is
  reachable by every workload on the cluster through the metadata
  server.

None ever has a key: `iam.disableServiceAccountKeyCreation` is
enforced on the folder, so that is structural rather than a habit.

### What lives in git

The manifest, one per installation, in whichever repository the applier
reconciles from. It carries ids and names, nothing secret — secrets go to
Secret Manager in the management project, and `status` is read back
rather than committed.

A merge is the privileged action, so merged state applies and a
`pull_request` trigger gets no cloud identity. Otherwise a fork's pull
request runs as the platform identity.

### The GitHub App

Argo reads the installation manifest from a private repository, and
GitHub offers no credential-free way to do that. A GitHub App is the
form to use rather than a deploy key: it belongs to the organisation
instead of to a person, appears in the organisation's installed
applications with an audit trail, is revoked by uninstalling it, and
reaches GitHub over HTTPS where a deploy key needs SSH egress.

Created once, by an organisation owner, in the UI — GitHub has no API
that creates an App:

1. Organisation settings, then Developer settings, then GitHub Apps,
   then New GitHub App.
2. Name it for what it reads. App names are globally unique on GitHub,
   so it carries the organisation as well —
   `queenswood-installations-reader`.
3. Homepage URL is required and unused. The repository's URL will do.
4. Untick Webhook Active. Nothing calls back.
5. Repository permissions: Contents, read-only, and nothing else.
   Metadata read-only is mandatory and selects itself.
6. Only on this account.
7. Create it, and record the App ID from the page that follows.
8. Generate a private key. A `.pem` downloads, and it is shown once.
9. Install App, then Only select repositories, then the installations
   repository.
10. The installation's settings URL ends in the Installation ID:
    `.../settings/installations/<id>`.

Three values come out. The App ID and the Installation ID are
identifiers; the private key is the only secret. All three go to Secret
Manager in the management project, where the secrets identity reads
them.

What Argo actually authenticates with is an installation token minted
from that key, valid an hour, refreshed by Argo itself. Rotating the key
is not disruptive, because an App may hold two at once: add the new one,
update the stored secret, delete the old.

### Where this stands

The composite creates the folder, the management project and its APIs,
the network and the zonal GKE cluster the platform will run on, and the
platform's own identity with a Workload Identity binding to it. What
that identity is allowed to do, and moving the manifest onto the cluster
it just built, are the next steps. Instances, the durable
tier and `state: draining` are described above because that is where
this goes, per
[ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md);
`justfiles/cloud.just` and the `queenswood-gcp` chart are what run an
instance today.

New GCP recipes go in `justfiles/gcp.just`, and anything still needed
from `cloud.just` moves across rather than being called into. That way
`cloud.just` only ever shrinks, instead of becoming a file of two
generations.

## Rules

**MUST:**

- Change what exists by editing the manifest, not by acting on GCP.
- Apply from merged state only. A `pull_request` trigger gets no cloud
  identity.
- Bind groups where humans hold access, and principals directly where
  automation does.
- Pivot the manifest off a throwaway control plane before discarding it.

**MUST NOT:**

- Grant a person `serviceAccountTokenCreator` on either identity, or
  create a key for either.
- Assume you can create a folder. Only one of the two paths needs it.
- Make retention or liens a spec field, or delete a project as a side
  effect of an edit.
- Commit anything secret beside the manifest.

**MAY:**

- Deploy with `instances: []` and patch instances in later.
- Run an instance with no `domain`, reached privately.
- Create more than one installation. One manifest per folder.

## References

- [ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
  — the folder as an installation, declared `state`, ordered draining,
  and why foundations are liened.
- [ADR-0016](../adr/0016-crossplane-over-terraform.md) — why
  infrastructure is declared rather than scripted.
- [cloud-account](cloud-account.md) — the organisation, access groups and
  billing account.
- [cloud-deployment](cloud-deployment.md) — the tier model and up/down
  runbook for an instance.
- [infrastructure](../tdd/infrastructure.md) — the bootstrap chain, sync
  waves and existing compositions.
- `justfiles/gcp.just` — `gcp-preflight`, `gcp-groups-bind`,
  `gcp-boot-*`, `gcp-platform-*`, `gcp-adc-*`, `gcp-plane-up` /
  `-manifest` / `-apply` / `-status` / `-down`. `gcp-plane-pivot` is
  not written yet. Group membership is not among them: it is Admin
  console work, for the reason the directory work above is.
- `infra/platform/crossplane-xrds/xqueenswoodinstallation-*.yml` — the
  XRD and Composition. Folder only, so far.
