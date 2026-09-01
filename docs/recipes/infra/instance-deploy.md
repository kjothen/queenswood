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

- A running Queenswood installation — see
  [management-plane-install](management-plane-install.md).
- The installation's recovery project, named in its `EnvironmentConfig`
  as `recoveryProjectId`.
- `argoServiceAccount` in that same `EnvironmentConfig`, correct.
- The domain verified and delegated, once for the installation — see
  [gcp-dns](gcp-dns.md).
- Write access to the private manifests repository, and a merge.
- Headroom on the plane.
- The capability each step names. Ours is a Google group; yours may differ.

### 1. Render the instance unit

**As the installation's platform viewer.** Ours is
`grp-gcp-<code>-platform-viewer@`, populated rather than joined.

Exports follow [cloud-naming](../practices/cloud-naming.md)'s `<code>`,
`<env>` and `<label>` convention, which every composed name is built
from —
they are stated once here and carried through every step below.
Everything else the render needs is read from the installation's
own manifest and from the running management plane.

```bash
# the installation code, e.g.
export QW_CODE=qw01
# the instance's env and label, e.g.
export QW_ENV=n QW_LABEL=dev
# the instance's private manifests repository, e.g.
export QW_INSTALLATIONS_REPO=../installations
```

```bash
just queenswood-instance-manifest
# or, for a spec that states what it would otherwise default:
QW_SPEC=full just queenswood-instance-manifest
```

> [!WARNING]
> Re-render as often as you like until the unit is committed: a plane
> reads it from git, so nothing was built and the minted project id means
> nothing. Once it is committed the file may be the only record of a
> project id GCP has consumed, and the recipe refuses rather than
> minting a second.

### 2. Read what it wrote

In the `QW_INSTALLATIONS_REPO`, check the domain, which is the one thing
the render cannot know is wrong: it must differ from every other instance's,
or both compose a record for the same name.

If you rendered with `QW_SPEC=full`, now is the opportunity to
change settings to a better fit for your instance.

### 3. Merge the instance, and wait for it

> [!WARNING]
> Commit and merge `<label>.unit.yml` and
> `units/<label>/instance.yml` ONLY, and NONE of the other files yet.

The merge is what starts the build.

```bash
just crossplane-conditions "xqueenswoodinstance/$QW_CODE-$QW_ENV-$QW_LABEL"
```

`Synced` reaches `True` within a minute or two and stays there.
`Ready` is `False` for around twenty minutes, then `True`. A `Synced`
that never arrives is the composite refusing to render at all;
`LastAsyncOperation` carries anything the cloud refused.

```bash
just crossplane-unready
```

Everything on the plane still building, this instance's resources
among them. A header line with nothing under it is the finished
answer.

### 4. Create the OAuth client

**As the installation's platform admin.** Ours is
`grp-gcp-<code>-platform-admin@` — join for this step, then leave.

In the new project, in the console, as
[google-sign-in](google-sign-in.md) has it: the consent screen first,
then a Web application client. No API creates one with a chosen
redirect URI. The redirect URI is
`https://keycloak.<domain>/realms/<realm>/broker/<alias>/endpoint`,
alias included, and the client is this environment's alone.

### 5. Write the secrets

**As the instance's own secrets admin**, not the installation's. Ours
is `grp-gcp-<code>-<env>-secrets-admin@` — join for this step, then
leave.

```bash
just queenswood-instance-google-secret
just queenswood-instance-keycloak-admin
just queenswood-recovery-backup-key
```

Each names the entry it wrote and the version it added.

### 6. Merge the Applications

**As the installation's platform viewer again.**

Put the client id from step 4 into the unit's `values.yml` as
`keycloak.googleClientId`, then commit and merge every remaining file
in the unit.

This is what installs the bank.

### 7. Check it serves

```bash
just argo-apps-status
```

Every Application for the instance `Synced` and `Healthy`, and the
console answering at `https://console.<domain>`.

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

- Render the unit with `just queenswood-instance-manifest`, which mints the
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
- Merge the composite and the Applications separately, the composite
  first. Keycloak honours a bootstrap admin only while the master realm
  is absent, and nothing automatic holds that gap open — a folder with
  no Applications in it does.
- Write the Keycloak bootstrap admin with
  `just queenswood-instance-keycloak-admin` before the bank first starts, and
  name it in the unit's values as `keycloak.bootstrapAdmin.secretName`.
- Write the other versions with `just queenswood-instance-google-secret`
  and `just queenswood-recovery-backup-key`, and let each strip the
  trailing newline.
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
- Render a unit over one that has been committed. The project id is
  minted per call, and a committed unit may already be built, leaving
  the file as the only record of the one GCP consumed.
- Add a second version to the FDB backup key. A later key strands every
  backup written under the first.

**MAY:**

- Render minimally and lean on the XRD's defaults, which is what
  `QW_DEFAULTS=true` does. State them with `QW_DEFAULTS=false` for
  anything long-lived: the blocks it writes out are immutable or nearly
  so, and a default that moves under a live instance is refused rather
  than applied.
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

**What the composite builds, and what waits behind it.** The project,
the network, the cluster, the identities, the database, the endpoint
and the three empty Secret Manager entries — then it registers the
cluster with Argo, which is what makes the unit's Applications able to
target it by name. The merge is what starts it, since the plane reads
the revision its Application names rather than a working tree, and
nothing on the plane reacts to a file that is only local.

**Why it is two merges.** Keycloak honours a bootstrap admin only
while the master realm is absent, and the OAuth client cannot be
created before the project holding it exists — so a secret has to be
written after the project is built and before the bank first starts.
Nothing automatic can hold that gap open. Sync waves order one sync;
they cannot wait for somebody to visit a console.

Leaving the unit's Applications out of the first merge is what holds it
open instead. The folder the unit points at contains only the
composite until step 6, so there is nothing to install and no clock
running, and the second merge starts the bank with every secret already
in place. Doing it in one merge would be a race against the build:
the composite reporting `Ready` is what releases the Applications, and
an entry with no version does not fail an `ExternalSecret` — it syncs
green and empty, which is how an instance arrives green everywhere and
unable to sign anybody in. The unit's Application carries
`prune: false`, so adding files to a folder it already syncs takes
nothing away.

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

Two of those are supplied by absence as much as by presence. Without
`recoveryProjectId` the instance composes neither a backups bucket nor
a backup key entry, and `just queenswood-recovery-backup-key` refuses.
The domain needs no act here at all: a Search Console Domain property
covers every subdomain, so an instance under one is neither verified
nor delegated again.

No step needs a cluster admin either. That capability is what `kubectl`
against the new cluster takes, with
`just gcp-instance-cluster-ctx <env> <label>` to reach it, and that is
debugging rather than any part of standing an instance up.

## References

- [management-plane-install](management-plane-install.md) — building the plane
  this runs on.
- [management-plane-install](management-plane-install.md) — the manifest
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
