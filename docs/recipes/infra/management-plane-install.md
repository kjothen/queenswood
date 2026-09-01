# Bootstrapping a management plane

<!-- tessl-plugin: deployment -->

## Status

**Untested as written**, and incomplete: one installation was built
this way, but step 5 — moving the composite onto the cluster it just
created — has no recipe behind it and was not performed. Until it is,
the plane a run of this page leaves behind is the throwaway one, and
the durable cluster reconciles nothing.

## Problem

You want a management plane inside the boundary an installation
occupies, reconciling that installation from git.

## Solution

### Prerequisites

- The seed identity, from [gcp-bootstrap](gcp-bootstrap.md), and an
  organisation and billing account behind it.
- The boundary this plane is built inside, from
  [boundary-install](boundary-install.md).
- The installation's contract, from
  [contract-install](contract-install.md).
- Steps 2 and 5 — write access to the manifests repository, and a
  merge.
- The capability each step names. Ours is a Google group; yours may differ.

```bash
# the installation code, e.g.
export QW_CODE=qw01
# the private manifests repository, wherever it is checked out
export QW_INSTALLATIONS_REPO=../installations
```

### 1. Raise a throwaway plane

**As the installation's platform admin.** Ours is
`grp-gcp-<code>-platform-admin@` — join for steps 1 to 7, then
leave.

```bash
just gcp-boot-seed-impersonate
just gcp-boot-cluster-up
```

A local kind cluster running Crossplane and the GCP providers,
authenticating from ADC that impersonates the seed identity. No key
exists, and step 7 ends the impersonation.

It ends by applying `xmanagementplane-xrd.yml` and its Composition from
this repository, so the boot cluster serves the one kind step 3
applies.

### 2. Render the manifest into the installations repository

The installation's own manifest goes in the private manifests
repository, not this one. The parent is one `gcp-boot-preflight`
listed, the folder
is named `fldr-<code>` unless you pass a name for one handed to you
already named, and the rest defaults from the code:

```bash
# the parent gcp-boot-preflight listed, e.g.
export QW_PARENT="organizations/<org-id>"

just queenswood-installation-manifest \
  > "$QW_INSTALLATIONS_REPO/$QW_CODE/installation.yml"
```

Seven keys under `spec`, and no `recovery` block. Step 6 reads the file
back from the same path.

Read it, then commit it. Pushing can wait for step 5.

> [!WARNING]
> Only where that file does not exist yet. The management project id is
> minted per call, so a second render replaces the recorded id with one
> no project answers to.

### 3. Apply the committed manifest

```bash
just gcp-boot-mgmt-apply
```

It reads the file step 2 wrote, merges in the billing account and
`management.bootstrap: true`, prints the whole document, and asks
before applying. It ends by waiting on the composite and reporting the
folder.

> [!WARNING]
> Only for an installation with no management plane yet. `bootstrap`
> flips the `Release`s installing Crossplane and Argo from `Observe` to
> `Create, Update`, so against an installation that already has a plane
> the boot cluster takes over the Crossplane and Argo running on it,
> and two planes reconcile the same composite.

### 4. Read back what it built

```bash
kubectl --context kind-boot-mgmt -n crossplane-system \
  get xmanagementplane "$QW_CODE" -o yaml
```

`status` carries the folder id, the management project and the platform
identity. Those are what everything later is named and bound against.

### 5. Pivot onto the cluster it built

Push the manifest first, then move the composite onto the management
cluster created in step 3. No recipe does the move yet.

After it, the management cluster reconciles its own project and folder,
and every later change is a merge. The manifest driving them is
[management-plane-install](management-plane-install.md).

### 6. Delete the boot cluster

```bash
just gcp-boot-cluster-down
```

The folder and everything in it are orphaned, not deleted: the cluster
holds no cloud state, and what it created is now the management
plane's. The `gcp-creds` Secret — a copy of your ADC — goes with it.

### 7. Revoke the impersonation

**As the installation's platform admin.** Ours is
`grp-gcp-<code>-platform-admin@` — join for steps 1 to 7, then
leave.

> [!WARNING]
> Until this runs, ADC on your machine can create projects and folders
> and grant IAM inside the folder, as the seed identity. It outlives
> the plane, the terminal and the reboot.

```bash
just gcp-boot-seed-impersonate-revoke
```

`just gcp-boot-seed-impersonate-status` then reports no impersonation.
Run it as soon as the throwaway plane is gone.

### 8. Give Argo the credential for the private repository

**As the installation's secrets admin.** Ours is
`grp-gcp-<code>-secrets-admin@` — join for this step, then leave.

[argocd-github](argocd-github.md) is the whole of this: it names the
repository in the manifest, creates a GitHub App, installs it on that
repository, and stores the App ID, Installation ID and private key in
the entry the composite made for them.

Come back when the `installation` Application reports `Synced`.

### 9. Compose the zone

Prepare the domain first with [gcp-dns](gcp-dns.md), and come back when
`just dns-carried <domain>` names a verification token.

```bash
just queenswood-dns-manifest-snippet <domain>
```

Paste it into `spec` in `<code>/installation.yml`, and merge.

```bash
just queenswood-zone-nameservers
```

Four names, which nothing is delegated to yet. Move the delegation with
[gcp-dns-delegation](gcp-dns-delegation.md).

### 10. Check it can take an instance

```bash
just crossplane-unready
```

A header line with nothing under it. The installation now carries
everything an instance derives from it: the folder, the billing
account, Argo's identity, the recovery project and the zone.

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
must be unique among siblings, so a display name fixed in a shared
template refuses the second one. The folder id is the
identifier; the display name labels the installation for people and
nothing else.

**A manifest naming a management project that was never built.** Step 5
was run twice, and the second render overwrote the recorded id with a
freshly minted one — so the file names a project nothing created, while
the project that was created goes unrecorded. The folder guard does not
cover this: it refuses only where a folder named `fldr-<code>` already
exists under the parent.

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
- Join a break-glass group for the step that names it and leave again.
  Each step says which capability it takes.
- Impersonate with `just gcp-boot-seed-impersonate` rather than holding
  a credential, and revoke it once the throwaway plane is gone with
  `just gcp-boot-seed-impersonate-revoke`. It outlives the plane
  otherwise — the terminal and the reboot too.
- Render the manifest with `just queenswood-installation-manifest` and commit it
  before applying it with `just gcp-boot-mgmt-apply`, and push it
  before any plane takes over reading it from git.
- Ask for `compute.skipDefaultNetworkCreation` before the first project
  is created. `just gcp-org-setup` enforces the constraints and
  `just gcp-policy-status` reports what is in force.
- Pivot the composite off the throwaway plane before discarding it
  with `just gcp-boot-cluster-down`.
- Close the seed identity once this is done — see
  [gcp-bootstrap](gcp-bootstrap.md). Its organisation grants, and the
  impersonation that reaches them, otherwise stand for ever, and the
  plane needs neither.

**MUST NOT:**

- Grant a person `serviceAccountTokenCreator` on the platform identity,
  or create a key for any of the four identities.
- Assume you can create a folder. Ids are required, and one may be
  handed to you instead.
- Delete a project as a side effect of an edit.
- Fix a default VPC in a composition. It cannot be undone there.
- Render a manifest over one that already exists. The management
  project id is minted per call, so the second render replaces the
  recorded id with one no project answers to, and the redirect
  truncates before the renderer runs.

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

**Why a seed project comes before anything else.** A service account
has to live in a project, and the identity that creates the folder must
exist before the folder does, so one project comes first — named
`prj-b-seed-<suffix>` and carrying no installation code, because one
serves the whole organisation. The identity in it carries a code, since
each one creates a particular installation, and ends up holding
`projectCreator` and `folderIamAdmin` on the folder or its parent,
`billing.user` on the billing account, and `orgpolicy.policyAdmin`
where the organisation allows it — the last three from
[gcp-bootstrap](gcp-bootstrap.md).

**What preflight can and cannot see.** A subsidiary's `parent` takes
`organizations/{id}` or `folders/{id}`, and `gcp-boot-preflight` checks
`folders.create` on it, so which of the two you hold is what decides
whether step 3 creates a folder or adopts one. What it cannot see is a
role held through a group: those never appear in a policy read, which
is why an absent line there is a prompt to check rather than a finding.

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

**What the boot cluster serves, and what it cannot disturb.** One kind,
and only the providers that kind composes with: `XManagementPlane`
composes managed resources and no other composite, where an instance
composes four. The management cluster gets the rest from git once Argo
is running there. Against an installation that already has a management
plane the cluster itself is harmless — it is local, and Crossplane on
it reconciles nothing until a composite is applied there, which is step
6. What it does leave is a control plane on your machine holding
credentials to the whole folder, which is what steps 6 and 7 take
away.

**Why the manifest is committed before it is applied.** Nothing has
been created when step 2 finishes: the file is a request, and the
folder name and the management project id in it are consumed the moment
step 3 applies them. The commit records them while they are still only
proposed, which is what the file is for — a project id is consumed
permanently and cannot be undeleted into usefulness, so one that was
minted, applied and then lost is not recoverable from anything the
cluster holds. The document is `infra/platform/templates/installation.yml.tmpl`
with values substituted, so what it will say is readable before it is
run.

That id is minted rather than derived, on every call, which is what
makes step 2 a first-run act: a second render over the same file
replaces the recorded id with one no project answers to, and the
redirect truncates before the renderer runs. The folder is guarded —
a name already in use under the parent is refused, with the adopt
command printed — but that catches a rebuild which reached GCP and
cannot catch the project id at all. Until a renderer exists that reads
an existing manifest and preserves what it already holds, the file is
the only record and overwriting it is how an installation is lost.

**Where the exports go.** Every recipe below discovers the
organisation, the billing account and the parents for itself, taking an
`org=` or `billing=` argument only where discovery is wrong, and the
folder id is a manifest value rather than a shell one. What is left is
two. `QW_CODE` and `QW_INSTALLATIONS_REPO` are `env_var_or_default` in
the justfile, so the two exports in `### Prerequisites` set both the
path the render is redirected to and the path `gcp-boot-mgmt-apply`
reads back. Writing the path into the redirect literally instead leaves
the render in one place and the apply reading another.

**Two values that belong to the act rather than the installation.** The
billing account and `management.bootstrap: true` are merged into the
document as it is applied rather than written into the file. Billing is
discovered from the identity's own binding and is wanted only at the
moment a project is created; `bootstrap` is true of the boot plane and
not of the installation. So the file stays the thing Argo reads, and
the two planes cannot disagree about what the installation is.

**Why the push waits for the pivot.** The apply reads your working
tree, so pushing at step 2 changes nothing about what step 3 does. From
step 5 the management plane reconciles the installation from the
repository instead, and a manifest that never got there leaves it with
nothing to reconcile. On a rebuild, pushing while the boot plane is
still applying is the same two-planes hazard arriving through git
rather than through `bootstrap`.

**Why the capability moves four times.** Each step is bounded by what
it writes to rather than by who is running it: creating the seed sets
the billing
account's own IAM policy, which lives outside the organisation's
hierarchy; granting it six roles at the organisation needs Organization
Administrator; steps 1 to 7 here act as that identity, which
[gcp-bootstrap](gcp-bootstrap.md) grants `platformAdmin`
impersonation of; and closing it removes an organisation binding, which
is
`organizations.setIamPolicy` — the same right as adding one, which is
why `grp-gcp-org-admin@` comes back for one step at the end.

**Why organisation policy is not composed.** `orgpolicy.policyAdmin` is
granted at the organisation and nowhere else — GCP refuses it at folder
scope — so the management plane cannot hold it without being able to
weaken any constraint anywhere in that organisation. That is also why
closing the seed identity leaves it standing alongside
`cloudasset.viewer` and `browser`: the three build roles go because the
plane creates instance projects and binds folder IAM as the platform
identity from then on, which the composite grants on the folder, and
the organisation-scoped three have nowhere else to live. Where you were
given a folder these are the organisation's acts and not yours — ask
for them.

**Why reopening is `-open` rather than re-creating the seed.** Creating
it is idempotent
and would also reopen the identity, but it wants billing admin and
project creation for work that is already done. `-open` needs only the
seed project.

## References

- [gcp-bootstrap](gcp-bootstrap.md) — the seed identity this
  impersonates, and closing it afterwards.
- [boundary-install](boundary-install.md) — the folder this plane is
  built inside.
- [organisation-foundation](organisation-foundation.md) — the browser-only
  half, before any
  of this.
- [ADR-0023](../../adr/0023-installation-naming-and-access.md) — what a
  capability is, and how an organisation answers one.
- [management-plane-install](management-plane-install.md) — the manifest
  this plane then reads.
- [ADR-0022](../../adr/0022-cloud-foundation-and-environment-lifecycle.md) —
  the folder as the installation, and what protects a foundation.
- [ADR-0024](../../adr/0024-instances-are-their-own-composites.md) —
  what the plane composes, and what Argo installs.
- [gcp-iam](gcp-iam.md) — Workload Identity's two halves, and the
  scopes a role may be granted at.
