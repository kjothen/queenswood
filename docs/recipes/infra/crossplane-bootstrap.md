# Bootstrapping a management plane

<!-- tessl-plugin: deployment -->

## Status

**Untested as written**, and incomplete: one installation was built
this way, but step 7 — moving the composite onto the cluster it just
created — has no recipe behind it and was not performed. Until it is,
the plane a run of this page leaves behind is the throwaway one, and
the durable cluster reconciles nothing.

## Problem

You need a Crossplane control plane in a folder of your own, because
the control plane your organisation runs cannot apply this kind.

## Solution

### Prerequisites

- A Google Cloud organisation, a billing account, and a parent to
  create in. With none of these, start at
  [cloud-account](cloud-account.md), which is the browser-only half.
- Either a folder id, written `folders/<folder-id>`, or a parent to
  create one under. Step 1 lists the parents you can see.
- Step 5 — write access to the manifests repository, and a merge.
- Google group memberships, by capability:
  - Step 1 — any account in the organisation. What it cannot read it
    says so about, and that blocks nothing.
  - Step 2 — the operating user, in `grp-gcp-billing-admin@`. It binds
    `roles/billing.user` on the billing account, which needs
    `billing.accounts.setIamPolicy`, and creates a project under the
    organisation.
  - Step 3 — `grp-gcp-org-admin@`. It grants six roles at the
    organisation, and nothing below Organization Administrator can.
  - Steps 4 to 9 — `grp-gcp-<code>-platform-admin@`, which step 2
    grants impersonation of the seed identity to.
  - Step 10 — `grp-gcp-org-admin@` again. Removing an organisation
    binding is `organizations.setIamPolicy`, the same as adding one.

Those are break-glass groups and normally empty: join for the act and
leave again, per
[ADR-0023](../../adr/0023-installation-naming-and-access.md).

The steps need no shell of their own. Every recipe below discovers the
organisation, the billing account and the parents, taking an `org=` or
`billing=` argument only where discovery is wrong, and reads the
installation code from `CODE` in the justfile. The folder id is a
manifest value rather than a shell one, set at step 5.

Where a platform team hands you a folder they have run steps 1 to 3 on
your behalf, and you set `spec.createFolder.folderId` rather than
creating one. The steps are otherwise the same and only the supplier
varies.

### 1. Check what you can reach

```bash
just gcp-boot-preflight
```

The organisation, the billing account, your own direct roles, and the
parents you may create under, ending in `nothing blocking` or a
`BLOCKED:` list and a non-zero exit. `spec.createFolder.parent` takes
`organizations/{id}` or `folders/{id}` and checks `folders.create` on
it, so which of the two Prerequisites values you hold decides whether
the folder is created or adopted. Ids, never display names.

Roles reported as `none bound directly` or `not readable by this
account` block nothing: a role held through a group does not appear
there, so an absent line is a prompt to check rather than a finding.

### 2. Create the seed project and the seed identity

```bash
just gcp-boot-seed
```

The seed project, the service account in it, `billing.user` on the
billing account, and the platform-admin group allowed to impersonate
it. No key is created, and it reuses a seed project labelled
`queenswood-tier=seed` where one exists rather than minting a second.

A service account has to live in a project, and the identity that
creates the folder must exist before the folder does — so one project
comes first, named `prj-b-seed-<suffix>` and carrying no installation
code, because one serves the whole organisation. The identity itself
carries a code, since each one creates a particular installation. The
identity ends up holding `projectCreator` and `folderIamAdmin` on the
folder or its parent, `billing.user` on the billing account, and
`orgpolicy.policyAdmin` where the organisation allows it — the last
three of those from step 3.

### 3. Grant the seed identity its organisation roles

```bash
just gcp-boot-seed-grant-org-roles
```

Where you were given a folder, these are held on that folder instead
and granted by whoever owns it.

### 4. Raise a throwaway plane

```bash
just gcp-boot-seed-impersonate
just gcp-boot-cluster-up
```

A local kind cluster running Crossplane and the GCP providers,
authenticating from ADC that impersonates the seed identity. No key
exists, and step 9 ends the impersonation.

It ends by applying `xmanagementplane-xrd.yml` and its Composition from
this repository, so the boot cluster serves the one kind step 5 needs.
Only that kind, and only the providers it composes with:
`XManagementPlane` composes managed resources and no other composite,
where an instance composes four. The management cluster gets the rest
from git once Argo is running there.

Safe against an installation that already has a management plane: the
cluster is local, and Crossplane on it reconciles nothing until a
composite is applied there, which is step 5. What it does leave is a
control plane on your machine holding credentials to the whole folder,
which is what steps 8 and 9 take away.

### 5. Write the manifest, commit it, then apply it

This is the other repository. Step 4 loaded the kind from this one;
what step 5 applies is the installation's own manifest, which lives in
the private manifests repository.

Render the smallest document that builds a plane — the folder, the
management project, and what runs in it. The parent is one step 1
listed, the folder is named `fldr-<code>` unless you pass a name for
one handed to you already named, and the rest defaults from the code:

```bash
# the parent step 1 listed, e.g.
export PARENT="organizations/<org-id>"
# the installation code, as the justfile sets it, e.g.
export CODE=qw01
# the private manifests repository, wherever it is checked out
export INSTALLATIONS_REPO=../installations

just gcp-boot-mgmt-manifest "$PARENT" \
  > "$INSTALLATIONS_REPO/$CODE/installation.yml"
```

Seven keys under `spec`, and no `recovery` block: the recovery project
and the instances join the manifest afterwards, composed by the plane
rather than by this cluster. The document is
`infra/platform/installation.yml.tmpl` with values substituted, so what
it will say is readable before it is run.

Read it, then commit it. Nothing has been created yet: the file is a
request, and the ids in it are consumed the moment it is applied — so
the commit is what records them before they become permanent, not
something the apply needs. Pushing can wait for step 7.

Then apply the committed file:

```bash
just gcp-boot-mgmt-apply
```

> [!WARNING]
> Only for an installation with no management plane yet. Applying
> patches `management.bootstrap: true` in, which flips the `Release`s
> installing Crossplane and Argo from `Observe` to `Create, Update` —
> so against an installation that already has a plane, the boot plane
> takes over the Crossplane and Argo running on it, and two planes
> reconcile the same composite and the same managed resources.
>
> Push before the pivot, not before this. The apply reads your working
> tree, so pushing now changes nothing here — but from step 7 the plane
> reconciles the installation from the repository, and a manifest that
> never got there leaves it with nothing to reconcile. On a rebuild,
> pushing while the boot plane is still applying is the same two-planes
> hazard arriving through git.

### 6. Read back what it built

```bash
# the installation code, as the justfile sets it, e.g.
export CODE=qw01

kubectl --context kind-boot-mgmt -n crossplane-system \
  get xmanagementplane "$CODE" -o yaml
```

`status` carries the folder id, the management project and the platform
identity. Those are what everything later is named and bound against.

### 7. Pivot onto the cluster it built

Push the manifest first: from here the management cluster reconciles
the installation from the repository rather than from your checkout,
and a manifest that never reached git leaves it with nothing to
reconcile.

Then move the composite onto the management cluster created in step 5.
No recipe does the move yet, and until one exists this step is the
reason the Status says incomplete.

After it, the management cluster reconciles its own project and folder,
and every later change is a merge. The manifest driving them is
[queenswood-installation](queenswood-installation.md).

### 8. Delete the boot cluster

```bash
just gcp-boot-cluster-down
```

The folder and everything in it are orphaned, not deleted: the cluster
holds no cloud state, and what it created is now the management
plane's. The `gcp-creds` Secret — a copy of your ADC — goes with it.

### 9. Revoke the impersonation

> [!WARNING]
> Until this runs, ADC on your machine can create projects and folders
> and grant IAM inside the folder, as the seed identity. It outlives
> the plane, the terminal and the reboot.

```bash
just gcp-boot-seed-impersonate-revoke
```

`just gcp-boot-seed-impersonate-status` then reports no impersonation.
Run it as soon as the throwaway plane is gone — the management cluster
reconciles with its own Workload Identity and needs nothing on your
machine.

### 10. Close the seed identity

```bash
just gcp-boot-seed-close
```

It removes the platform-admin group's `serviceAccountTokenCreator`, so
nobody can become the seed identity at all, and revokes the three roles
it only needed in order to build — `folderCreator`, `folderIamAdmin`
and `projectCreator`. From here the plane creates instance projects and
binds folder IAM as the platform identity, which the composite grants
on the folder.

`orgpolicy.policyAdmin`, `cloudasset.viewer` and `browser` stay:
`just gcp-org-setup` and `just gcp-policy-status` read and write
organisation policy as this identity, and GCP refuses
`orgpolicy.policyAdmin` at folder scope, so it is granted at the
organisation or nowhere.

To bootstrap again — a second installation, or a rebuild — reopen with
`just gcp-boot-seed-open` and `just gcp-boot-seed-grant-org-roles`,
then carry on from step 3. Step 2 would also reopen, since it is
idempotent, but it wants billing admin and project creation for work
that is already done; `-open` needs only the seed project.

## Failures

**A folder that reads `Available` after somebody deleted it.** A
soft-deleted folder still exists to the API for thirty days, so the
provider goes on reporting it healthy for the whole window and
reconciliation repairs nothing. The composite's readiness check on
`lifecycleState` is what turns that into a visible failure.

**A project with a default VPC, SSH and RDP open to the internet.**
`compute.skipDefaultNetworkCreation` was not in force. A composition
cannot undo this: the network is created when the Compute API is
enabled rather than when the project is, so `autoCreateNetwork: false`
is satisfied at a moment when there is nothing to suppress, and
Crossplane declares presence rather than absence, so nothing reconciles
it away either. The constraint prevents; deleting a network that
already exists is a separate act. Ask for that constraint first.

**A chart value set in the composition that never arrives.** The
`Release`s that installed Crossplane and Argo carry `Observe` alone
once a plane is running, so the plane never acts on the release that
installed it. Setting resource requests there and watching nothing
happen is the trap — see
[crossplane-upgrades](crossplane-upgrades.md) and
[argocd-upgrades](argocd-upgrades.md) for the way in.

**A second installation refused under the same parent.** Display names
must be unique among siblings, so a `spec.createFolder.displayName`
fixed in a shared template refuses the second one. The folder id is the
identifier; the display name labels the installation for people and
nothing else.

**A `folders.create` denial on an identity that plainly has the role.**
`roles/orgpolicy.policyAdmin` and its relatives are organisation-only,
and the refusal is a 400 declining the scope rather than a permission
the caller lacks — see [gcp-iam](gcp-iam.md).

## Rules

**MUST:**

- Build the plane before expecting a merge to install anything — a
  control plane running another toolchain cannot apply this kind.
  `just gcp-boot-cluster-up` raises the throwaway one and loads the
  kind onto it.
- Read what you can reach with `just gcp-boot-preflight` before choosing
  between creating a folder and adopting one.
- Grant the seed identity its rights on the folder or the parent
  with `just gcp-boot-seed` and
  `just gcp-boot-seed-grant-org-roles`, never a key.
- Impersonate with `just gcp-boot-seed-impersonate` rather than holding
  a credential, and revoke it once the throwaway plane is gone with
  `just gcp-boot-seed-impersonate-revoke`. It outlives the plane
  otherwise — the terminal and the reboot too.
- Render the manifest with `just gcp-boot-mgmt-manifest` and commit it
  before applying it with `just gcp-boot-mgmt-apply`, and push it
  before any plane takes over reading it from git.
- Ask for `compute.skipDefaultNetworkCreation` before the first project
  is created. `just gcp-org-setup` enforces the constraints and
  `just gcp-policy-status` reports what is in force.
- Pivot the composite off the throwaway plane before discarding it
  with `just gcp-boot-cluster-down`.
- Close the seed identity with `just gcp-boot-seed-close` once the
  bootstrap is done, and reopen it with `just gcp-boot-seed-open` for
  the next one. Its organisation grants, and the impersonation that
  reaches them, otherwise stand for ever — and the plane needs
  neither.

**MUST NOT:**

- Grant a person `serviceAccountTokenCreator` on the platform identity,
  or create a key for any of the four identities.
- Assume you can create a folder. Ids are required, and one may be
  handed to you instead.
- Delete a project as a side effect of an edit.
- Fix a default VPC in a composition. It cannot be undone there.

**MAY:**

- Stand the installation up with no instance at all. An instance is its
  own composite, applied afterwards and reconciled by the plane.
- Ask a platform team for the folder and the identity, which shortens
  this path without changing it.
- Load another XRD and composition onto the plane, and deploy something
  else the same way.

## Discussion

We build the plane once and install everything else by merging, because
the privilege that installs software in a GitOps organisation has to be
of the right kind: a control plane reconciling Terraform against AWS
cannot apply an `XManagementPlane` at all, since the manifest names a
kind its API server has never heard of. The question is not whether you
deploy declaratively but whether you hold a Crossplane control plane
that can be taught this kind, and where you do not, this builds one.

What it leaves behind is not Queenswood-specific. A plane in a folder,
running Crossplane and Argo against a repository, reconciles whatever
composite it is given, so loading another XRD and composition deploys
something else the same way. An organisation builds this once and holds
a general capability afterwards.

**The four identities, and why they are four.** The **bootstrap
identity** creates the management project; members of the platform-admin
group impersonate it, and where an organisation provisions folders a CI
runner assumes it through federation and no person can. The **platform
identity** is created by the manifest and used by the management cluster
through Workload Identity, and nobody impersonates it. The **secrets
identity** reads Secret Manager for one cluster and does nothing else,
kept separate from the platform identity — which could do the same
reading but can also create projects and administer every cluster in the
folder — and there is one per cluster, so an instance's operator cannot
read another instance's secrets. The **node identity** is what one
cluster's nodes run as, holding only what a GKE node needs to report
itself, and never the default compute service account: a node pool asks
for `cloud-platform` scopes, so whatever it holds is reachable by every
workload on the cluster through the metadata server. None holds a key,
because `iam.disableServiceAccountKeyCreation` is enforced at the
organisation by default — inherited rather than established here, so it
holds for a folder handed to you as well, and worth confirming rather
than assuming.

**Why organisation policy is not composed.** `orgpolicy.policyAdmin` is
granted at the organisation and nowhere else, so the management plane
cannot hold it without being able to weaken any constraint anywhere in
that organisation. Where you were given a folder these are the
organisation's acts and not yours — ask for them.

## References

- [cloud-account](cloud-account.md) — the browser-only half, before any
  of this.
- [queenswood-installation](queenswood-installation.md) — the manifest
  this plane then reads.
- [ADR-0022](../../adr/0022-cloud-foundation-and-environment-lifecycle.md) —
  the folder as the installation, and what protects a foundation.
- [ADR-0024](../../adr/0024-instances-are-their-own-composites.md) —
  what the plane composes, and what Argo installs.
- [gcp-iam](gcp-iam.md) — Workload Identity's two halves, and the
  scopes a role may be granted at.
