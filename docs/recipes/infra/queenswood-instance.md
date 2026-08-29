# Adding an instance to an installation

<!-- tessl-plugin: deployment -->

## Status

**Untested as written.** One instance exists and it accreted across a
dozen changes rather than being created from nothing, so every step
below has been performed and the sequence has not. Expect the first run
to find an ordering this page states wrongly rather than a step it
omits.

## Problem

You want to add a Queenswood instance to an installation.

## Solution

### Prerequisites

- An installation with a management plane reconciling it, built by
  [queenswood-bootstrap](queenswood-bootstrap.md), and its manifest
  merged.
- The installation's recovery project, named in its `EnvironmentConfig`
  as `recoveryProjectId`. Absent, the instance composes neither a
  backups bucket nor a backup key entry, and `just gcp-fdb-backup-key`
  refuses.
- `argoServiceAccount` in that same `EnvironmentConfig`, correct. It is
  deliberately not a `Required` patch, so a wrong one composes green.
- The domain verified and delegated, once for the installation — see
  [cloud-dns](cloud-dns.md). A Search Console Domain property covers
  every subdomain, so an instance under it needs no registrar act and
  no verification act.
- Write access to the private manifests repository, and a merge.
- Headroom on the plane. Every instance adds composites, managed
  resources and Applications to it, `machineType` is immutable, and the
  pool being replaced is the one Crossplane runs on.
- Google group memberships, by capability:
  - Steps 1 to 3 — write access to the manifests repository, and
    `grp-gcp-<code>-platform-viewer@` to read what the plane does with
    it.
  - Step 4 — `grp-gcp-<code>-platform-admin@`, which the instance's own
    `access` mapping grants `roles/oauthconfig.editor` on the new
    project.
  - Step 5 — `grp-gcp-<code>-secrets-admin@`, the instance's own rather
    than the installation's: the installation's binds on the management
    project and writes nothing here.
  - Step 7 — `grp-gcp-<code>-cluster-admin@`, for `kubectl` against the
    new cluster.

Those are break-glass groups and normally empty: join for the act and
leave again, per
[ADR-0023](../../adr/0023-installation-naming-and-access.md).

### 1. Render the unit

```bash
# the installation code, as the justfile sets it, e.g.
export CODE=qw01
# the private manifests repository, wherever it is checked out
export INSTALLATIONS_REPO=../installations

# the environment letter and the label, as arguments
just gcp-instance-manifest n dev
```

Six files: `<code>/<label>.unit.yml`, and `instance.yml`,
`external-secrets.yml`, `config.yml`, `queenswood.yml` and `values.yml`
under `<code>/units/<label>/`. It reports the project id it minted, the
domain, the zone it will write records into, and the recovery project.

The environment letter and the label are the arguments; everything else
is read from the installation's own manifest and from the plane.

> [!WARNING]
> Re-render as often as you like until the unit is applied: nothing
> exists yet and the id means nothing. Afterwards the file is the only
> record of a project id GCP has consumed, so the recipe refuses where
> the unit is already there rather than minting a second.

### 2. Read what it wrote

The declaration sits at the top of `<code>/` and the rest in the
folder. That is not cosmetic: the installation's Application is not
recursive, so a declaration filed inside the folder is never applied at
all.

Check the domain, which must differ from every other instance's, and
every field carrying the minted project id — `projectId` in
`instance.yml` and `config.yml`, the `iam.gke.io/gcp-service-account`
annotation in `external-secrets.yml`, and the service accounts and
connection name in `values.yml`. All are rendered from one value and
have to stay that way.

Change what the defaults got wrong — the machine type, the database
tier, the network ranges if this installation will ever peer — then
commit.

### 3. Merge, and watch the composite build

```bash
just crossplane-unready
```

The composite builds the project, network, cluster, identities,
database, endpoint and the three empty Secret Manager entries, then
registers the cluster with Argo. Twenty minutes is normal. Nothing of
the bank installs until it reports ready: `instance.yml` applies at
wave 0, so the Applications behind it wait.

Do steps 4 and 5 while it builds.

### 4. Create the OAuth client

In the new project, in the console, as
[google-sign-in](google-sign-in.md) has it: the consent screen first,
then a Web application client. No API creates one with a chosen
redirect URI. The redirect URI is
`https://keycloak.<domain>/realms/<realm>/broker/<alias>/endpoint`,
alias included, and the client is this environment's alone.

### 5. Write the three secret versions

```bash
just gcp-secret-version "sec-$CODE-n-dev-google-oauth"
just gcp-keycloak-admin-secret n dev
just gcp-fdb-backup-key n dev
```

The first two entries are in the instance's project and the third is in
the installation's recovery project. Each reports the version it added.

> [!WARNING]
> Before the bank first starts, not after. Keycloak honours a bootstrap
> admin only while the master realm is absent, and a version added
> later reaches the running realm not at all.

### 6. Name what reads them, and merge

In the unit's `values.yml`: `keycloak.googleClientId` from step 4, and
`keycloak.bootstrapAdmin.secretName`, without which the entry step 5
wrote is inert and Keycloak comes up on the operator's own generated
password. The client id is carried in the import Job's name, so a
changed id produces a Job that runs.

### 7. Check it serves

```bash
just argo-apps-status
just gcp-instance-cluster-ctx n dev
```

Every Application for the instance `Synced` and `Healthy`, then sign in
at `https://console.<domain>` and complete the Google flow.

## Failures

**A container that appears not to exist.** The write was attempted from
the installation's `secretsAdmin`, which binds on the management
project. A capability bound on one project writes nothing in another,
and the denial is reported as absence. The instance's own `access`
mapping is what grants it, so it has to be merged and reconciled first.

**An instance that composes green while Argo has no rights in it.**
`argoServiceAccount` in the installation's `EnvironmentConfig` is
missing or wrong. It is not a `Required` patch — deliberately, since a
merge there before the account exists would fail every instance rather
than this one binding — so nothing reports it and the Applications fail
against the new cluster alone.

**A Secret that syncs green and is empty.** The composite composed the
container and the operator synced it, both correctly, and no version
was ever written. It surfaces at whatever reads it rather than where it
happened.

**`401 invalid_client` on an instance that just came up.** Read this as
the secret before reading it as the build: a trailing newline on the
version, a vault expression that resolved to nothing, or a value stored
after the pod that reads it had started. Deleting the external-secrets
pod and restarting Keycloak is faster than waiting out the refresh
interval.

**A Cloud SQL create refused with `invalidOperation`.** The manifest
carried `state: down`. An already-stopped database cannot be created;
an instance is built up and stopped afterwards.

**The plane's own pods restarting while the instance builds.** The
instance's composites and Applications landed on a plane with no
headroom. The symptom is liveness kills rather than memory pressure,
because pods with no requests read as uncommitted to the scheduler.

## Rules

**MUST:**

- Render the unit with `just gcp-instance-manifest`, which mints the
  project id once and writes it into every file that carries it. Where
  one is written by hand instead, they have to agree: a wrong id in the
  external-secrets annotation is a service account nothing is bound to,
  not an error.
- Give the instance its own `access` mapping, and let it reconcile
  before writing any secret version.
- Put the unit declaration at the top of the installation's directory,
  never inside the unit's folder.
- State `ingress.domain` distinct from every other instance's, with
  `zone.name` and `zone.project` naming the installation's zone.
- Create the OAuth client in the console, in the instance's own
  project, one per environment.
- Write the Keycloak bootstrap admin with
  `just gcp-keycloak-admin-secret` before the bank first starts, and
  name it in the unit's values as `keycloak.bootstrapAdmin.secretName`.
- Write the other two versions with `just gcp-secret-version` and
  `just gcp-fdb-backup-key`, and let each strip the trailing newline.
- Read the build back with `just crossplane-unready` and the workloads
  with `just argo-apps-status`, and reach the cluster with
  `just gcp-instance-cluster-ctx`.

**MUST NOT:**

- Create an instance with `state: down`. Cloud SQL refuses to create an
  already-stopped instance, and the refusal is a 400.
- Share an `ingress.domain` between two instances. Both compose a
  record for the same name, and each reconciles it to its own address.
- Reuse or rename a project id. Neither is possible, and the second
  rebuilds the resource.
- Render a unit over one that has been applied. The project id is
  minted per call, and after an apply the file is the only record of
  the one GCP consumed.
- Add a second version to the FDB backup key. A later key strands every
  backup written under the first.

**MAY:**

- Leave `network` unstated. The ranges are identical in every instance
  and safe while the VPCs are isolated, which they are until one peers.
- Take the instance down once it is up, which is a one-word change.
- Stand an instance up with no `ingress` at all, which answers on no
  name and composes no certificate.

## Discussion

An instance is a unit: one declaration at the top of the installation's
directory and a folder of manifests beneath it. The declaration is what
the plane reads, the folder is what the declaration's own Application
reads, and the split is what keeps a unit's sync waves out of the
installation's.

**Why the unit is in two places.** The plane's `installation`
Application syncs the installation's directory with no `directory`
block, which makes it non-recursive on purpose: it applies the
installation's own manifests and one declaration per unit, all of which
sit at the top, while a unit's contents are installed by the
Application that unit composes. An `include` cannot narrow it instead —
Argo compiles those globs with no separator, so `*` crosses `/` and any
pattern admitting the top level admits the whole tree, bringing the
unit's Applications back into the installation's sync carrying their
waves.

**Why the secrets are written while the composite builds.** The
instance XR applies at wave 0 and its API group carries a health check,
so Argo holds every Application behind it until the composite reports
ready. That gate is what makes the window safe rather than lucky: the
Secret Manager containers are composed early and the cluster takes
twenty minutes, so there is time to write the versions before anything
reads them. Writing them afterwards is the common way to arrive at an
instance that is green everywhere and cannot sign anybody in.

**Why `down` is not a starting state.** Down is a declared state and
reconciling toward it is ordinary, but it describes a database that
exists and is stopped. Cloud SQL will not create one already stopped,
so the first reconcile of a new instance has to build what later
reconciles may stop.

**What the installation supplies, and what the instance states.** The
folder, the platform identity, the recovery project and the public zone
belong to the installation, and the instance reaches them by naming
them rather than by referring to the plane's composite — the zone
explicitly, because two composites have to spell one name and only one
of them makes it. What the instance owns is its project and everything
in it, which is why `down` stops an environment rather than emptying
one.

## References

- [queenswood-bootstrap](queenswood-bootstrap.md) — building the plane
  this runs on.
- [queenswood-installation](queenswood-installation.md) — the manifest
  the plane reads, and changing it by merge.
- [google-sign-in](google-sign-in.md) — the console acts and the Admin
  API call behind step 4.
- [external-secrets](external-secrets.md) — the declared container and
  the written version, and what each entry holds.
- [argocd-apps](argocd-apps.md) — what a parent Application may hold,
  and reading a sync that is not applying.
- [crossplane-debug](crossplane-debug.md) — starting from what is not
  ready.
- [ADR-0023](../../adr/0023-installation-naming-and-access.md) — the
  code in every name, and who holds which capability.
- [ADR-0024](../../adr/0024-instances-are-their-own-composites.md) —
  why an instance is its own composite.
