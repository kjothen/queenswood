# Plan: the pivot, and retiring cloud.just

## Context

[ADR-0016](../adr/0016-crossplane-over-terraform.md) chose declared
infrastructure over scripted, and
[ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
found that the shape it left behind does not hold that line: the plane
that reconciles everything is a kind cluster on one laptop, the
lifecycle is a shell script, and there is one environment with no answer
for a second. [ADR-0023](../adr/0023-installation-naming-and-access.md)
then settled what the replacement's resources are called and who may
touch them.

`justfiles/cloud.just` is that previous generation — keyed to one
environment whose project id, secrets and pointers live in `pass` on one
machine. `justfiles/gcp.just` is where the replacement lands. Nothing
calls across from `gcp.just` into `cloud.just`, so `cloud.just` only
ever shrinks rather than becoming a file of two generations.

The foundation is built. The next act is the pivot: moving the
installation from the throwaway plane onto the management cluster it
created. This plan is that, and what follows it.

## Picking this up cold

**Where the work is: step 2 of [The pivot](#the-pivot)** — the folder
and management-project bindings for `sa-qw01-platform`, declared in the
composition and not yet applied, so the identity still holds nothing
inside the folder. Billing is done. Read that step, then verify against
the live account rather than against this line, because a sentence
naming a step is the first thing here to go stale.

Read "How the machinery fits together" below before touching the
composition. It is the hour that does not need spending twice.

The names, so a session starting fresh does not rediscover them.
`xxxxxx` stands for a project id's random suffix, and the numeric
organisation and folder ids are not written down at all. Neither is
withheld to be difficult: the recipes discover every one of them from
whoever is logged in locally, so writing them here would add a second
copy that can only go stale — and for the reason
[cloud-naming](../recipes/cloud-naming.md) gives, a public document
should carry names, where an account identifier is what somebody
pretexting a support call would want. `just gcp-preflight` and
`just gcp-boot-status` print the real values.

- domain `queenswood.io`
- folder `fldr-qw01`, the installation
- management project `prj-qw01-c-mgmt-xxxxxx`
- seed project `prj-b-seed-xxxxxx`, holding `sa-qw01-boot`. Outside the
  folder, created by `gcloud` rather than by the composite, and not the
  composite's to adopt
- kubectl context `qw01-mgmt`, added by `just gcp-mgmt-cluster-ctx`

The files this plan acts on:

- `infra/platform/crossplane-xrds/xqueenswoodinstallation-xrd.yml` and
  `-composition.yml` — the API and what it builds
- `infra/platform/crossplane-providers/providers.yml` — the package set
- `infra/helm/xp-mp/` — Crossplane and Argo, as a chart
- `justfiles/gcp.just` — the new recipes
- `justfiles/cloud.just` — the ones being retired

To re-read the live state rather than trusting this document:
`just gcp-preflight`, `just gcp-boot-status`, `just gcp-platform-status`,
`kubectl --context qw01-mgmt get crd`, and
`gcloud resource-manager folders get-iam-policy <folder-id>`. Who is in
which access group is read in the Admin console, not here — see
[cloud-account](../recipes/cloud-account.md).

To get back to a working control plane from nothing, join
`grp-gcp-qw01-platform-admin` and:

```
just gcp-adc-boot                 # interactive, opens a browser
just gcp-plane-up
just gcp-plane-apply \
  organizations/<org-id> fldr-qw01 \
  folders/<folder-id> <mgmt-project-id>
```

The last two arguments are the adopt values, and they are what make
that an adoption rather than a second installation. Then
`just gcp-plane-down`, `just gcp-adc-revoke`, and leave the group.
Nothing reconciles between sessions, which is what the pivot is for.

## How the machinery fits together

Written down because reconstructing it from the manifests takes an hour
and it is the thing every decision below turns on. Kubernetes
understands none of this: the API server is a database, and everything
here is a controller reading and writing it.

Three files, three different kinds of object:

- **XRD** — `xqueenswoodinstallation-xrd.yml`. A schema. Crossplane
  reads it and generates a CRD, which teaches the API server to accept
  `kind: XQueenswoodInstallation`. That is all it does.
- **Composition** — `xqueenswoodinstallation-composition.yml`. A
  program, in `mode: Pipeline`. It says what an installation is made of.
- **XR** — the composite itself, `name: qw01`. The manifest. Today it
  exists only as a heredoc inside `gcp-plane-apply`.

The flow, once all three are in a cluster:

```
XR applied
  -> Crossplane matches it to the Composition (defaultCompositionRef)
  -> runs the pipeline: function-patch-and-transform,
     function-go-templating
  -> functions emit desired managed resources
     (Folder/fldr-qw01, Project/prj-qw01-c-mgmt, ...)
  -> Crossplane writes those as objects in the API server
  -> each provider's controller sees its own kind, reads the
     ClusterProviderConfig for credentials, calls the GCP API
  -> status returns, and MR readiness rolls up into the XR's Ready
```

A provider package also installs the CRDs for its own kinds. So a
cluster without the GCP providers has no `Folder` kind, cannot store a
`Folder` object, and therefore cannot reconcile one — which is the whole
of what "no Crossplane CRDs on the management cluster" means.

**Argo CD interprets none of this.** It watches a git directory and
makes the cluster match it. Its job is to put the XRD, Composition and
XR into the cluster; Crossplane does the work. The two are named
together in
[ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
because they are the halves of "what git says, exists": Argo makes the
cluster match git, Crossplane makes GCP match the cluster.

**Something undeclared has to place the first Crossplane**, because
Crossplane cannot install itself onto a cluster that has none. That
irreducible step is `gcp-plane-up`: a kind cluster, Crossplane by Helm,
providers by manifest. It is the floor, and the design's aim is to keep
it at a scratch cluster on a laptop rather than at anything inside the
installation.

## Where this stands

The installation `qw01` exists in GCP and matches the recipes: folder
`fldr-qw01`; management project with six APIs; `vpc-qw01-c-mgmt` and
`sb-qw01-c-mgmt-euw2`; `gke-qw01-c-mgmt` and `np-qw01-c-mgmt`, zonal,
one node; `sa-qw01-platform` with a Workload Identity binding to
`crossplane-system/crossplane-provider-gcp`; folder bindings for
`platformViewer` and `clusterAdmin`, and a management-project binding
for `secretsAdmin`.

Five facts about that shape everything below.

**Nothing reconciles it.** The throwaway kind plane has been discarded,
so the composite exists nowhere. The GCP resources survived because
deleting a cluster deletes nothing in GCP — the controllers simply
stopped.

**It is recorded, and nothing reads the record.** The private
`installations` repository exists, with `qw01/installation.yaml` on main
and a README stating the repository's rules. So what git says and what
GCP holds agree, and the installation is reproducible from the file
rather than from somebody's scrollback. Argo is not pointed at it yet —
that waits on the management cluster having Argo at all, which is step 5.

**The management cluster is stock.** `kubectl get crd` returns GKE's own
CRDs and nothing else. No `crossplane-system` namespace, no Argo, no
provider packages.

**The platform identity holds one role.** `sa-qw01-platform` has
`roles/billing.user` on the billing account, granted by
`gcp-platform-billing-role`, and nothing at all on the folder or the
management project — those are step 2 below, declared in the
composition and waiting on a plane to apply them.
`just gcp-platform-status` prints both. The only identity with real
rights inside the installation is still `sa-qw01-boot`, which GCP
granted `resourcemanager.folderAdmin` for having created the folder.

**Nothing is liened.** `liens list` on the management project returns
nothing. The declarations are all in place —
`managementPolicies` without `Delete`, `deletionProtection`,
`deletionPolicy: PREVENT` — with nothing underneath them, which inverts
[ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)'s
own position that the lien matters more than the policy, because a
policy is a convention a later edit can undo.

## Why the XR is reusable, and what makes it fragile

The pivot re-applies the same XR to a different cluster. What makes that
an adoption rather than a second installation is one annotation.

`crossplane.io/external-name` maps a managed resource to the cloud
object it stands for. It lived in the kind cluster's etcd. Applied to a
fresh cluster, every MR is created with that annotation empty, and the
provider has no idea the GCP object exists.

For most resources this is harmless, because the external name defaults
to `metadata.name`, and the composition pins those deterministically:
`vpc-qw01-c-mgmt`, `gke-qw01-c-mgmt`, `np-qw01-c-mgmt`. The provider
observes, finds the object, and adopts it. This is the second reason for
[cloud-naming](../recipes/cloud-naming.md)'s rule that Kubernetes names
are the same names — the recipe gives the cross-reference reason, and
re-adoption is the one that bites at a pivot.

Only three resources set the annotation explicitly, and two of them are
the exceptions that fail in opposite directions:

- **Folder** — its GCP identifier is a number GCP assigned, not the
  display name, so an empty annotation leaves nothing to look up and the
  provider creates a **second folder**. GCP permits the duplicate
  display name, so nothing objects. `spec.createFolder.folderId` is what
  prevents it.
- **Project** — GCP answers permission denied, never not found, for a
  project id you do not own, so the provider cannot distinguish absent
  from someone-else's and never gets past its first observation to
  create. `spec.management.adopt` is what prevents it, and it is
  deliberately a separate field from `projectId` because creation and
  adoption cannot share one.
- **Platform identity** — set explicitly because the Workload Identity
  member string elsewhere in the composition has to spell the same
  address.

So the XR is reusable exactly to the extent that it carries its own
adopt values, and today those exist only as arguments somebody typed.
That is why committing the manifest is part of the pivot rather than
tidying afterwards.

The rest of what must perpetuate is in the XR already — billing account,
parent, code, region, zone, the access mapping, the management project
id — and perpetuates for the same reason: it is in the file, or it is
lost.

## The pivot

### 1. Widen the boot plane

`gcp-plane-up` installed a narrowed set of packages, chosen when the
composition stopped at a folder. It now takes `provider-helm` as well,
which is what lets the boot plane install onto the management cluster
in step 3, and applies a `ProviderConfig` for it.

**How that config reaches the cluster without a key**, which was an
open question and is not: `provider-helm` splits the problem in two.
`credentials` is a kubeconfig Secret carrying the endpoint and the CA
and no token, and `identity`, typed `GoogleApplicationCredentials`, is
what actually authenticates — pointed at the same impersonated ADC the
GCP providers already use. So the boot plane holds no key for the
cluster either, and the pattern is not new: `helm-provider-config.yml`
has used exactly this shape against the current deployment.

The kubeconfig is the composed `Cluster`'s own connection Secret, which
the provider writes on every reconcile, so nothing has to run
`get-credentials` and no recipe holds the endpoint. That is one more
reason a v2 namespaced MR suits this: it writes the Secret into its own
namespace, which is where the config looks.

`providers.yml` itself was short of two the durable tier will need:
storage and secretmanager, for the `Bucket` and `BucketIAMMember`
ADR-0022 names by hand. They are in the manifest rather than in the
boot plane's install set, which stays narrow — the durable tier is the
management plane's to run, not a cluster that lives for minutes.

ADR-0022 names a third, orgpolicy, for the `Policy` that exempts a
project from the key-creation ban and for `HMACKey` alongside it. Left
out: the exemption needs a role granted only at the organisation, which
[the durable tier](#the-key-ban-and-the-proxy-that-answers-it)
takes as unavailable and designs around, and the other org policy in
play — `skipDefaultNetworkCreation` — is already answered by the
project's own `autoCreateNetwork: false`. A package the composition
composes nothing from is a provider pod for nothing.

### 2. The platform identity's rights

The management plane runs as `sa-qw01-platform` through Workload
Identity. **This is where the work now starts.**

**Done: the billing account.** `roles/billing.user` is bound, by
`gcp-platform-billing-role`. It could not come from the composition — a
billing account sits above the folder, and `sa-qw01-boot` holds
`billing.user` rather than `billing.admin`, so it cannot delegate what
it was given. That made it the same seam as `gcp-boot-org-roles`: a
recipe run once by a billing administrator, which is a person and
deliberately so.

**Declared: everything inside the folder**, which the identity does not
hold until a plane applies it. `just gcp-platform-status` prints all
three scopes. Derived from what the composition creates now and what an
instance adds — project, network, cluster, database, service accounts,
address, certificate:

- On the folder — `projectCreator`, `folderIamAdmin`, and compute,
  container, storage, DNS and CloudSQL administration. Assembled from
  predefined roles rather than `folderAdmin`, which is what GCP gave the
  boot identity and is more than this should hold.
- On the management project — `secretmanager.admin`.

Storage is bound once, on the folder, rather than at both scopes. A
folder binding inherits into every project beneath it, so the
project-scoped grant this plan first listed alongside it would have
been a second copy of a right the identity already held — and the
folder form is the one that also covers a bucket an instance turns out
to want.

`projectCreator` is what makes the rest narrower than it reads. GCP
makes the creator of a project its owner, so an instance project is
administered by having built it; the folder-scoped roles are what
reach the projects this identity did not build.

**`orgpolicy.policyAdmin` is not here at all, and cannot be.** It was
written project-scoped, then folder-scoped, and GCP refused both with
`Role roles/orgpolicy.policyAdmin is not supported for this resource` —
a 400 declining the scope rather than a permission the boot identity
was missing, so nothing granted upstream would have made it work. The
role is organisation-only, which puts it on the same seam as
`billing.user`: granted above the folder, by a person, and therefore a
recipe rather than a composed resource.

Nothing needs it until the durable tier, so it is settled there rather
than guessed at here — see [After the pivot](#after-the-pivot). Note
that `roles/owner` deliberately excludes org policy administration, so
creating the project that needs the exemption does not confer the right
to exempt it.

The general form is worth carrying. A role has a set of resource types
it may be granted on, that set is not the same as the set of resources
the feature acts on, and a scope outside it fails at the API with a 400
rather than by producing a binding that silently grants nothing. Loud,
but only once something applies it — which is the argument for applying
a composition change rather than reading it.

Both are `FolderIAMMember` and `ProjectIAMMember`, which the access step
has proven against this provider version. They are applied by the boot
plane, which holds `folderAdmin` and so can grant them.

Note the shape this repeats: the composition's `access` step already
emits exactly these kinds for the four human capabilities, keyed off
`spec.access`. The platform identity is a fifth principal with a fixed
role set rather than a configurable one, so it belongs in
`patch-and-transform` beside `platform-identity` rather than in the
go-templated access step.

### 3. Crossplane and Argo onto the management cluster, declared

Two `Release` resources of `provider-helm`, composed by
`XQueenswoodInstallation` and applied from the boot plane: Crossplane
and Argo CD. **Both are written.** What Argo reconciles from is not
part of them — putting Argo on the cluster and pointing it at a
repository are separate, and only the first is a `Release`.

This is what keeps the installer at four commands. `gcp-plane-apply`
applies the composite, so anything the composite composes arrives in
that one step — where a recipe running `helm upgrade` against the new
cluster would be a fifth, and an imperative one in the middle of the
path [ADR-0016](../adr/0016-crossplane-over-terraform.md) exists to
keep declarative.

The `DeploymentRuntimeConfig` and the `ClusterProviderConfig` do not
travel with that Release, because a `Release` installs a chart and
neither is a value of Crossplane's. They arrive the way everything else
in a cluster running Argo arrives: applied from the repository, as
[infrastructure](../tdd/infrastructure.md) already has
`crossplane-configs` do for the current deployment.

**Who owns these chart versions.** Each is pinned twice — the `xp-mp`
chart's dependency, which is what Renovate bumps, and the composed
Release — and `scripts/check-versions.sh` holds the two equal. Renovate
sees the dependency and not the Composition, so a bump it made alone
would fail that check rather than land; both copies move by hand, in
one change. Renovate is turned off for both charts rather than left to
produce a PR that cannot go green.

Crossplane carries a second reason, and it is the sharper one. The boot
plane runs one Crossplane and the management plane runs the other, and
step 5 hands a live composite from one to the other, so a skew surfaces
at the handover rather than at an upgrade — and a control-plane version
also has to agree with the provider and function packages it runs,
which Renovate bumps separately.

Declared rather than helm-installed by a recipe, because the management
plane's own existence is part of what the manifest describes — and
because a rebuild then means applying the manifest rather than
remembering a sequence.

What the management cluster needs beyond those two releases does not
travel in a release, because a `Release` installs a chart. It arrives
the way everything in a cluster running Argo arrives — from a
repository — and `infra/helm/management-plane/` is the chart that
carries it:

- A `DeploymentRuntimeConfig` pinning the GCP providers' Kubernetes
  service account to `crossplane-provider-gcp`. The XRD's
  `management.providerServiceAccount` field already assumes this and the
  Workload Identity binding already spells it, but nothing creates it.
  Left to Crossplane the name is generated, and the binding matches
  nothing. Each GCP provider names it through `runtimeConfigRef`.
- **The annotation that binding is useless without.** Workload Identity
  is two halves, and only one of them was ever written down here:
  `roles/iam.workloadIdentityUser` on the GCP side, from step 2, and
  `iam.gke.io/gcp-service-account` on the Kubernetes service account.
  Without the second, a token minted for that account is exchangeable
  for nothing. It goes on the runtime config's
  `serviceAccountTemplate`.
- A GCP `ClusterProviderConfig` with `credentials.source:
  InjectedIdentity` rather than a secret. No key, and no ADC — which is
  the whole point of the platform identity existing.
- A helm `ClusterProviderConfig`, so this plane can observe the two
  releases it inherits. It has the same name as the boot plane's,
  because the composed releases name it by value, and deliberately not
  the same content: the boot plane reaches this cluster across a
  network with a kubeconfig and an impersonated identity, and this one
  is already inside it.
- Argo `Application`s for the provider packages and for the XRD and
  Composition. That is what gives the plane the kinds it reconciles
  with and the API it reconciles — and nothing else. The manifest
  naming an actual installation is in a private repository and arrives
  in step 5, so this plane knows the shape of an installation and holds
  none.

Waves order it: configurations at 0, packages at 1, the API at 2. A
provider Deployment takes whatever service account exists when it
starts, so the runtime config cannot arrive after the package that
uses it — which is the reverse of the order the previous generation's
bootstrap chart used.

**Nothing applies that chart by hand.** The root `Application` is
carried in the Argo release's own `values.extraObjects`, and the
composition patches the installation's code and management project into
it. So a management plane is still one act rather than a sequence, and
the only repository involved is the public one — nothing here needs a
credential.

**Which repository is a manifest value, not a constant.**
`management.source` carries the URL and the revision, defaulted to this
project. A hard-coded URL would be a defect rather than an
inconvenience for anyone running a fork: the composition travels with
the fork, so their boot plane would build their cluster and then point
Argo at *upstream's* manifests, and every change they had made would be
invisible on it. The revision matters for a different reason — `main`
is a moving target for a plane meant to be durable, and a tag is not
expressible without this. Both are patched twice, into the root
Application's own source and into the values it hands the chart, so an
installation has one answer rather than two.

**The self-management loop, and its answer.** Once the management plane
adopts the composite it owns the Release that installs it. A composition
edit that removed or upgraded that Release would have Crossplane acting
on itself mid-reconcile, and a delete would be unrecoverable without
raising the boot plane again. So those Releases carry
`managementPolicies: [Observe]` on the management plane: it watches its
own installation and never acts on it, and changing Crossplane or Argo
becomes a deliberate act from the boot plane. That is
[ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)'s
"foundations are observed" applied one level up.

**How one document does both**, since step 5 applies the same composite
to both planes and a composition cannot ask which plane is running it.
`management.bootstrap` is set by the plane installing the management
plane and left out of the committed manifest — the same seam
`billingAccountId` uses, and for the same reason: it is true of an act
of creation rather than of the installation. The base carries
`[Observe]`, because a patch whose source is absent is skipped, so the
safe value is what a manifest without the field produces. Both cases
are spelled in the patch, so setting it to `false` says what leaving it
out says.

`gcp-plane-apply` passes it, and nothing else does. That is what makes
"the boot plane may install the management plane, and the management
plane may not reinstall itself" a property of who is applying rather
than a rule someone has to remember.

**Both Releases are `helm.m.crossplane.io`, never `helm.crossplane.io`.**
provider-helm installs both groups, and the composite is namespaced, so
the legacy cluster-scoped kind fails the whole pipeline with `cannot
apply cluster scoped composed resource ... for a namespaced composite
resource` — every composed resource stops, not just the offending one.
It also survives one reconcile before doing so, since a create and an
apply are different paths, which makes a first green run no evidence at
all. `kubectl api-resources | grep -i helm` distinguishes them: the
NAMESPACED column, not the version. The rule generalises — prefer the
`.m.` group for every provider, and treat an API group copied from
`crossplane-configs/` as belonging to the previous generation.

### 4. The manifest in git — done

The private `installations` repository holds
`qw01/installation.yaml`, rendered by `gcp-plane-manifest` and carrying
its own `createFolder.folderId` and `management.adopt`. What remains of
this step is one change here: `gcp-plane-apply` should apply that file
rather than assemble a composite from five arguments, so the boot plane
and Argo consume the same document and cannot disagree about what the
installation is.

Branch protection requiring review is the control that repository has,
deliberately, since it carries nothing executable — a direct push to its
main changes infrastructure with nothing in the way.

Note that the three values `gcp-plane-apply` prints are not the whole
set that has to be frozen. The rest — the code, the domain the group
addresses are built from, the billing account — are *discovered* at
apply time, from a constant in the justfile, from the organisation
lookup, and from whichever billing account happens to be open first.
That holds while there is one organisation and one open billing
account, and stops holding silently rather than loudly.

Argo syncs from merged state only, and a `pull_request` trigger gets no
cloud identity: a merge is the privileged action, and otherwise a fork's
pull request runs as the platform identity.

#### Which repository, and why it is a second one

The manifest is nothing but identifiers and this repository is public.
The most sensitive of them, the billing account id, leaves the composite
altogether rather than being hidden — see below. What remains is the
project hierarchy: the organisation id in `createFolder.parent`, the
folder id, and the management project id with its random suffix. So the
manifests live in a private `installations`, which Argo reconciles
from with a deploy key. **Public is how, private is what**:
the XRD, the Composition, `providers.yml`, the charts, the recipes and
the ADRs stay here, because they are the part with value to a reader;
what moves is a list of identifiers with value to nobody but the
operator.

**That repository holds data and nothing executable** — no workflows,
no actions, no scripts. Argo pulls, so nothing in it needs to run and
nothing in it needs a credential: the deploy key is read-only, lives in
the cluster, and points outward. The only thing able to act on GCP is
the management cluster.

That is stronger than the rule
[cloud-foundation](../recipes/cloud-foundation.md) states today. "A
merge is the privileged action, so merged state applies and a
`pull_request` trigger gets no cloud identity" exists because the
alternative was push-based CI holding one. Pull-based and data-only
means no cloud identity in GitHub at all, and the caveat stops needing
to be remembered.

It costs pre-merge validation: with no workflow, an invalid manifest
merges and fails at Argo rather than in the pull request. Nothing
half-applies — the API server rejects a composite that violates the
XRD's schema — but the failure surfaces in the cluster. A local recipe
doing a server-side dry-run against the XRD keeps the check without
putting anything executable in the repository, and branch protection
requiring review is the control that belongs there, a merge being what
reaches production.

This is a seam the design already has rather than a new one.
[cloud-foundation](../recipes/cloud-foundation.md) says the manifest
lives "in whichever repository the applier reconciles from", allowing
that it is not this one, and
[ADR-0023](../adr/0023-installation-naming-and-access.md) assumes
installations built in organisations we do not own, from folders handed
to us, with groups named nothing like ours. A manifest for one of those
would never live here. Queenswood's own installation has simply been
getting special treatment as the only tenant.

The alternative considered and rejected was keeping the manifest here
and sourcing the sensitive fields from an `EnvironmentConfig` or a
Secret. It works, and for two fields it is proportionate, but it spends
three things that a second repository does not:

- **The record thins.** The manifest is meant to be the whole account of
  what exists. Fields held elsewhere leave it the account minus those
  fields, without making anything secret — everything it describes stays
  visible to anyone holding Browser on the folder.
- **`pass` comes back.** An out-of-band object with no history and no
  review is the previous generation's single machine with a better
  mechanism, which is what
  [ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
  set out to escape.
- **Review is lost, and review was the point.**
  [ADR-0023](../adr/0023-installation-naming-and-access.md) puts the
  access mapping in the manifest precisely so two capabilities
  collapsing onto one principal "is written in the manifest and read in
  a pull request, rather than emerging from a policy nobody opens".

It also does not scale to what comes next: instance manifests want
domains, address ranges and database sizing, and each of those reopens
the argument about which fields are exempt. A private repository settles
it once.

The cost is cross-repository atomicity. A composition change needing a
simultaneous manifest change spans two repositories and cannot land as
one commit. Keeping new XRD fields optional with defaults is what makes
that rare, and is good discipline regardless.

The test that decided this still governs what goes in *this* repository:
**does exposure cost anything?** The code, the region, the folder
display name and the access group addresses all fail it — the ADRs and
[cloud-account](../recipes/cloud-account.md) publish the naming
convention in full, so a redacted worked example may stay here and be
useful. The billing account id and the project-hierarchy identifiers
pass it.

On what has already leaked: management and seed project ids are printed
in [cloud-naming](../recipes/cloud-naming.md)'s worked example. A
rebuilt installation takes new random suffixes, at the cost of consuming
the old project ids permanently — which
[ADR-0023](../adr/0023-installation-naming-and-access.md) already
accepted once, for this reason. So that is an argument for rebuilding
eventually rather than for treating the current ids as recoverable
secrets.

#### The billing account is a property of the identity

It does not go in the manifest at all, and the reason is
[ADR-0020](../adr/0020-providers-are-deployment-facts.md)'s one layer
down: a value that selects behaviour belongs in deployment rather than
in a request. The identity must hold `billing.user` on an account
before it can link a project to it, so that binding already settles
which account is used. A field restating it is a second copy that can
disagree with the first.

Three facts make this cleaner than it first looks:

- **A folder has no billing account.** Billing attaches to projects, and
  a folder is only a hierarchy and IAM node — which is why projects are
  portable. There is nothing to inherit.
- **`billing.user` is bound on the billing account, not the folder**,
  which `gcp-boot-identity` already does. Linking a project needs
  `resourcemanager.projects.createBillingAssignment` on the project and
  `billing.resourceAssociations.create` on the account.
- **The identity can list what it is bound to.** Where an organisation
  hands over a folder and an identity, that list has exactly one entry.
  Discovery through the identity is therefore *more* reliable than the
  current `gcp-plane-apply` behaviour, which takes the first open
  account visible to the operator, who may see several.

Discovery is confirmed rather than assumed: `gcloud billing accounts
list`, run as the boot identity, returns exactly one open account. Run
as the operator it may return several, which is why `gcp-plane-apply`
taking the first one is fragile today and stops being so here.

The design is one shape, not a choice between mechanisms:

```
composition   never declares billingAccount
creation      the boot plane links, the account discovered from the
              identity's own binding
steady state  late-initialisation owns the field
the manifest  says nothing about billing at all
```

**The composition never declaring it is the invariant**, and what
forces that is server-side apply. Field ownership on the live Project
shows two managers: the composition owns `autoCreateNetwork`,
`billingAccount`, `deletionPolicy`, `folderIdRef`, `name` and
`projectId`, while `folderId` is owned by Crossplane's reference
resolver. `folderId` survives precisely because the composition has
never once set it. A field the composition *does* own and then stops
setting is deleted on the apply that relinquishes it, since SSA removes
a field its sole owner no longer declares.

That makes removing the patch from the *existing* resource a one-time
transition rather than a steady state: the field is dropped, late-init
writes it back under its own manager, and subsequent applies never
reclaim it. The hazard is only inside that window, and only because the
composition owns the field today — a composition that had never set it
would look like `folderId` from the start.

**Creation is the case late-initialisation cannot cover**, because it
can only reflect a link that already exists. A GCP project may be
created unbilled and linked immediately after, so the boot plane does
that once, as the identity, against the account it just discovered.

`billingAccountId` then leaves the XRD's `required` list, and its
docstring says it is discovered from the identity's bindings.

A general hazard worth carrying beyond billing: **every field this
composition sets, it owns.** Deleting a patch later deletes the field
from the resource, so a patch is not a free thing to add.

##### Tested, and it holds

Run against the live management project with its `managementPolicies`
cut to `[Observe, LateInitialize]`, so nothing could reach GCP — both,
because `LateInitialize` is itself a policy and `[Observe]` alone would
have disabled the thing under test.

Dropping the patch moved the field to a third manager:

```
before   composition   autoCreateNetwork billingAccount deletionPolicy
                       folderIdRef name projectId
         resolver      folderId

after    composition   autoCreateNetwork deletionPolicy folderIdRef
                       name projectId
         resolver      folderId
         provider      billingAccount
```

So late-initialisation does own it, under the provider's own field
manager, and the design holds: the composition stops declaring the
field, the provider keeps it, and the manifest carries nothing about
billing.

The value stayed populated in every three-second sample, so no
transition gap was observed — which is not the same as proving there
was none at finer resolution. Treat the window as real but very short,
and do the change once, deliberately, rather than assuming it is free.

Three field managers is the model to carry: the composition owns what
it patches, Crossplane's reference resolver owns what it resolves, and
the provider owns what it late-initialises. Ownership is what decides
whether a field survives a patch being deleted.

##### Done for qw01, and what it did not prove

The live installation has crossed over: the XRD makes the field
optional, the XR carries no `billingAccountId`, the provider owns
`forProvider.billingAccount` alone, and GCP still bills the project.
The composition keeps its patch, which now fires only when a manifest
supplies the field — which `gcp-plane-apply` does at creation and the
committed manifest never does.

Two things that run did **not** establish, both worth knowing before
trusting it again:

**The dangerous transition was not exercised, and it is not where it
first appeared to be.** Running the spike had left the field owned by
*both* the composition and the provider, since restoring the real
composition re-claimed it alongside late-init. With two owners, one
relinquishing cannot remove the field, so no window opened.

Where the window actually is: the composition owns `billingAccount`
only when a composite supplies `billingAccountId`, and the only thing
that supplies it is `gcp-plane-apply`, at creation. So the window is
the *first reconcile from the committed manifest* after a new
installation is created — the moment the composition stops declaring
what it declared at creation. That is a fresh-installation concern, on
every new installation, and it was never qw01's pivot: an existing
project reconciled from a manifest that never carries the field means
the composition never owns it in the first place.

qw01 is past it permanently for the same reason. Note also that the
handover's effect was entirely cluster-side — the XRD, the composite
and the field ownership all went with the boot plane when it was
deleted. What persists is the committed code and GCP itself, which is
the design working rather than a loss.

**Guarding it by patching the resource does not work.** The handover
script cut the project to `[Observe, LateInitialize]` with `kubectl
patch` and the composition reverted it within seconds, because
`managementPolicies` is set in the composition's `base` and is
therefore a field the composition owns. The guard was inert for the
whole run. The spike worked because it changed the *composition*; that
is the only thing that changes a composition-owned field.

So the ownership rule has now caught three separate things — the
billing account, the readiness of go-templated resources, and a safety
mechanism built to protect against it. Treat "who owns this field" as
the first question about any composed resource, not a detail.

This does not retire the private repository: `createFolder.parent`
still carries the organisation id, and a folder's parent is required
rather than late-initialisable. It does mean the repository holds one
identifier rather than the most sensitive one.

#### The layout

The repository is `installations`, named for what it holds — an
installation being the unit
[ADR-0023](../adr/0023-installation-naming-and-access.md) settled, and
naming a thing for what it acts on being the rule everything else here
follows. No product in the name: a second product's installations sit
beside Queenswood's without it lying, and the boundary that matters is
the audience rather than the product, since access is per repository.
One belonging to a different operator wants its own.

```
installations/
├── README.md          what this is, where the schema lives, and
│                      that it holds no credentials
└── qw01/
    └── installation.yaml     the XQueenswoodInstallation
```

`just gcp-plane-manifest` prints that file, resolved, on stdout with
every message on stderr, so it redirects straight into place. A second
installation is `qw02/`. `INSTALLATIONS_REPO` in `gcp.just` says where
the checkout is, defaulting beside this one.

**Argo's wiring stays in this repository**, beside the XRD it depends
on: one Application with `path: .` and `directory.recurse: true`. That
keeps the private repository purely data — not merely free of workflows
but free of Argo configuration too. The Application names the private
repository's URL, which is not sensitive.

An ApplicationSet with a git generator over `*/` earns its place when
installations need *different* sync policies, which
[ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
already anticipates in wanting manual rather than automatic sync for
prod. Until then it is machinery for one directory.

**This layout survives a second cloud**, because the design already put
the seam in the right place: the composite is `XQueenswoodInstallation`
with no provider in its name, and the Composition is
`xqueenswoodinstallation.gcp`, which the XRD names as its default. One
API, one Composition per provider, chosen per manifest. So a provider is
a property of an installation rather than of the tree, and there is no
`gcp/` level — an installation is on one cloud, and its code is already
unique.

Where a second cloud actually bites is the XRD, not the directory
structure. `billingAccountId`, `createFolder.parent` and
`management.projectId` are GCP nouns; AWS has organizational units and
accounts, Azure management groups and subscriptions. Either the XRD
grows a neutral core plus a per-provider block, or there is a second
XRD. The first is the more likely answer given the Composition is
already named for its provider, but it is a decision for when the
second cloud is real, and nothing here prejudges it.

**One repository holds installations that share an audience.** Access
control is per repository, so an installation belonging to a different
customer or operator wants its own rather than a directory. That is the
same seam [ADR-0023](../adr/0023-installation-naming-and-access.md)
draws in assuming a folder handed to us by an organisation whose groups
are named nothing like ours.

Nothing else belongs in there. In particular no credential, for the
reason the next section gives.

#### What the GitOps model says, and where it binds

Checked rather than assumed, because "identifiers in a private
repository" and "secrets in a repository" attract very different
advice and it matters which one this is.

The [GitOps Principles v1.0.0](https://opengitops.dev/) — declarative,
versioned and immutable, pulled automatically, continuously reconciled —
say nothing about everything living in git. They constrain how desired
state is expressed, stored and applied. Secrets are the acknowledged
gap beneath them, addressed by tooling rather than by the principles.

**On splitting the repository**, Argo CD's
[best practices](https://argo-cd.readthedocs.io/en/stable/user-guide/best_practices/)
recommend a separate repository for manifests on five grounds, none of
them about disclosure: separation of application code from application
config, not triggering a build to change a replica count, a cleaner
audit history free of development noise, separation of commit access
between people who write the application and people who may push to
production, and avoiding the loop where CI commits into the repository
that triggers CI. So the split here is an ordinary pattern that happens
to also answer the disclosure question.

**On secrets**, both major implementations say the same thing and it is
stronger than "use a private repository". Flux's
[secrets management guidance](https://fluxcd.io/flux/security/secrets-management/)
states plainly that storing plain-text secrets in desired state is not
recommended, offers Sealed Secrets, SOPS and external-secret operators,
and adds a rule worth carrying: do not co-locate ciphertext with the key
that decrypts it. Argo CD's
[secret management page](https://argo-cd.readthedocs.io/en/stable/operator-manual/secret-management/)
goes further and prefers destination-cluster secret management — Sealed
Secrets, External Secrets Operator, the Secrets Store CSI Driver, Vault
Secrets Operator — because then Argo never holds the secrets at all.

That page also carries a fact that settles the alternative rejected
above on independent grounds: **Argo stores generated manifests in
plaintext in its Redis cache**, so injecting a secret during manifest
generation leaks it into that cache. Encrypting the sensitive fields
into the public manifest and decrypting them through a config-management
plugin would have done exactly that.

Where this binds is not the manifest. Nothing in it is a credential:
these are identifiers, and the test is that if they *were* credentials a
private repository would be insufficient by every source above, which is
the whole reason for the distinction. It binds on what comes next.
Instances bring database passwords and the FDB backup encryption key,
and those must not enter `installations` even privately. They belong
in Secret Manager, which is already
[ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)'s
answer, reached either by an external-secrets operator or by Crossplane
writing connection details straight into a cluster Secret. Choosing
between those two is worth doing before the first instance rather than
after.

#### Two alternatives rejected, so they are not re-derived

**Reading the identifiers from Secret Manager.** An External Secrets
Operator on the management cluster, a `ClusterSecretStore` pointed at
the project holding them, an `ExternalSecret` materialising a Secret in
`crossplane-system`, and a `function-go-templating` step reading that
Secret and patching `Project.forProvider.billingAccount` and
`Folder.forProvider.parent`. Argo would apply the `ExternalSecret` and
the XR, and nothing else.

One property of it is genuinely good and worth remembering: the read
happens at reconcile time inside Crossplane rather than at manifest
generation, so Argo never sees the values and the plaintext Redis cache
does not come into it.

It fails on two counts. **The boot plane cannot do it** — Secret Manager
lives in the management project, and the boot plane runs before that
project exists, so the one scenario the adopt values exist for is the
one where the mechanism is absent. Moving the secret to the seed project
fixes that and makes the durable path depend on a project ADR-0022
describes as designed so it could be deleted. And **the root of a
discovery chain cannot be hidden**: the `ClusterSecretStore` needs a
`projectID`, in the public repository, to fetch the project ids being
hidden — one identifier published to conceal four. The justfile dodges
this by finding the seed project by label rather than by id, and no
operator has a label search to dodge with.

Against a private repository it gains one repository and loses review,
history and diff, for values whose whole risk is a phone call.

**Selecting by label instead of carrying an id.** Crossplane's
`matchLabels` selectors resolve against managed resources **already
present in the Kubernetes cluster** — the controller reads the
referenced resource's external-name and copies it in. There is no
Terraform-style data source, so nothing expresses "the GCP project
labelled `tier=seed`".

The composition already uses the by-name form of this for everything it
composes: `folderIdRef` on the Project, `projectRef` on each
ProjectService, `networkRef` on the Subnetwork. It cannot reach outward.
For `management.projectId` to resolve by label there would have to be a
`Project` resource in the cluster carrying that label, and that resource
needs the project id as its external-name — so the id moves to a
neighbouring manifest rather than disappearing.

The asymmetry underneath is worth stating plainly, because it is why the
identifiers have to be written down at all: a GCP-side label query is
real and `_gcp-seed-project` already uses one, but it is an imperative
capability. The justfile can discover, Crossplane cannot, and Argo
cannot run `gcloud`. The declarative layer carries only what it is told.

One thing survives the rejection. **Label the folder, the management
project and every instance project with the installation code.** Not for
Crossplane, which will not use it, but for the recovery path: today
`gcp-mgmt-cluster-ctx` matches a project-id prefix and says why — "the
suffix is random, so it is not derivable, and nothing labels the
project". A label turns finding an entire installation from a bare login
into one query, which is worth having when the management project is
what has been lost.

### 5. Move the XR, and verify the adoption

Apply the XRD, Composition and manifest to the management cluster, and
check every managed resource individually rather than trusting the
composite's `Ready`.

The commit criterion is `kubectl --context qw01-mgmt get managed`
listing the existing resources as Ready and Synced, with nothing created
or changed on the GCP side — no second folder, no second project, no
replaced node pool. Adoption is asserted resource by resource here
because the two known traps are both silent: a duplicate folder looks
like success, and a project stuck observing looks like a slow create.

Liens land here too, on the folder and the management project, so that
the plane taking over meets a refusal in GCP rather than a convention in
a manifest if a later edit removes a `managementPolicies` entry.

Labels land here as well, carrying the installation code on the folder,
the management project and later every instance project. Crossplane will
not use them; `gcloud` will, and it is what lets a bare login find a
whole installation without knowing a random suffix. It also retires the
project-id prefix match in `gcp-mgmt-cluster-ctx`.

### 6. Discard the boot plane

`just gcp-plane-down`, and `just gcp-adc-revoke`. After this the
management cluster reconciles its own project and folder, which the
liens are what make safe.

## After the pivot

**The durable tier.** `bkt-qw01-backups` in the **management** project,
not the instance project — today the bucket is `<instance>-backups` and
dies with the thing it protects, and moving it is the property this
buys. With it: the per-prefix lifecycle rules from
`gcp-backup-lifecycle-set` as `lifecycleRule`, and Secret Manager
entries for the FDB encryption key and database passwords.

How the data reaches that bucket is a redesign rather than a port of
what exists, and the reason is a permission we should not expect to
hold.

#### The key ban, and the proxy that answers it

FDB's backup agent speaks S3 rather than GCS, so it authenticates to
the interop endpoint with an HMAC key, which GCP counts as a
service-account key. The organisation bans those by default, so
`_gcp-allow-sa-keys` exempts the instance project from
`iam.disableServiceAccountKeyCreation` — and setting that exemption
needs `orgpolicy.policyAdmin`.

Step 2 established that GCP grants that role at the organisation and
nowhere else: a project binding and a folder binding are both refused
with `Role roles/orgpolicy.policyAdmin is not supported for this
resource`, a 400 declining the scope rather than a permission the
caller was missing. The old recipe worked by granting it to the
operator on our own organisation and then applying a *project*-scoped
override, which is the shape worth remembering — the grant is
organisation-wide, the policy it sets is not, and it is the grant that
is out of reach.

Under [ADR-0023](../adr/0023-installation-naming-and-access.md)'s
assumption, of a folder handed to us inside an organisation we do not
own, that grant is not ours to make. Asking for it means asking to be
able to weaken any constraint anywhere in that organisation, for the
sake of one HMAC key, and it should be treated as unavailable rather
than as a request that might succeed. `sa-qw01-boot` does hold the role
today, from `gcp-boot-org-roles`, so the exemption is reachable for
this installation — from the disposable plane only, which makes it a
property of our own organisation rather than of the design.

**So the transport changes, rather than the permission.** Run an
S3-to-GCS proxy in the instance cluster, and point the backup agents at
it instead of at `storage.googleapis.com`. The agents keep speaking
the only protocol they know; the proxy speaks GCS's own API and
authenticates through Workload Identity, so no exportable Google
credential exists anywhere in the path and the exemption stops being
needed at all.

What FDB presents to the proxy is a locally generated pair that grants
nothing in GCP. That is the point rather than a detail: a credential
the organisation's key policy has no opinion about, whose blast radius
is one proxy inside one cluster, and which can be rotated without
asking anyone.

**It also retires a compromise the chart currently documents.** FDB's
blobstore client has no usable trust store — it statically links
OpenSSL with an `OPENSSLDIR` absent from the image, so every public
certificate verifies as self-signed, and only `--tls-ca-file` loads a
CA, which the operator never passes and offers no way to add. So the
backup runs plaintext to `storage.googleapis.com` today, over the
public internet, with encrypted files and SigV4's promise that the
secret is never transmitted standing in for the transport. With a
proxy in the cluster the plaintext hop is pod to pod, and the leg that
crosses a network is the proxy's own HTTPS to GCS, made by a client
whose trust store works. The comment in
`infra/helm/queenswood/templates/fdb-backup.yaml` explaining why this
is tolerable stops needing to be true.

Continuity is what makes this better than moving the data. The backup
stays continuous and lands natively in GCS, so the object layout,
`fdbbackup expire`, the bucket's lifecycle rules and the restore path
are all exactly what
[recovery-procedures](../recipes/recovery-procedures.md) already
describes. The recovery point objective does not move, and restore
gains no step.

The encryption key is unaffected: FDB encrypts what it writes, the key
is generated once and must outlive every cluster, and Secret Manager in
the management project is still where it lives.

**Prove the restore before trusting it.** A translation layer's
failures live in the parts of the S3 API that GCS implements
differently — multipart semantics and list behaviour especially — and
they surface on read, not on write. A backup that writes cleanly and
will not restore is the failure this design has to rule out early,
against a real cluster, before the proxy is anywhere near a production
instance.

The proxy is also in the data path, so its failure stops backups
without stopping anything else. What that wants is an alarm on backup
staleness rather than on the pod: a healthy proxy that has written
nothing for an hour is the condition worth waking someone for. Being
stateless, it takes replicas at no cost beyond the pods.

#### What was rejected, and why

**MinIO as the store.** Trades GCS's durability and lifecycle rules for
a storage system to operate and back up in its own right. Worth it only
if MinIO is wanted for something else.

**`fdbdr` to a second cluster.** Continuous replication is disaster
recovery, not point-in-time restore, and
[recovery-procedures](../recipes/recovery-procedures.md) is built on
restoring to a recorded version. A second cluster running continuously
also contradicts the disposable tier's whole reason for existing. It
answers a different question, and could sit alongside a backup rather
than replace one.

**A scoped policy exception.** A dedicated backups project holding only
the bucket, one HMAC key, rotation automated, and the exception
written down. It does not reduce the ask: the override is already
project-scoped — `_gcp-allow-sa-keys` applies exactly that — and what
is out of reach is the organisation-level grant needed to set any
override at all. Worth knowing that
`constraints/storage.restrictAuthTypes` exists as a separate control on
HMAC access to GCS, which an organisation may enforce independently and
which the proxy also sidesteps.

**Disk snapshots** are the one not rejected. FDB supports binary
backups through volume snapshots, which involve no S3 protocol at all,
and CSI `VolumeSnapshot` of the storage servers' disks is a plausible
second line. Against it: the operator's support is thinner than for
blobstore backups, recovery points are coarser than a continuous
backup's, and quiescing has to be coordinated with FDB rather than
taken underneath it. A second line, once the first one restores.

Two decisions this leaves. **Where the object path carries the
instance**: one durable bucket per installation means a prefix per
instance, `<instance>/fdb/continuous/<generation>`, since the lifecycle
rules already discriminate by prefix and a bucket per instance would put
the durable tier back inside the disposable one. And **where the proxy
runs**: in the instance cluster it is disposable with everything else
and reaches the durable bucket across a project boundary, which is the
same cross-project binding the design already needs.

**Real secrets, at last.** An instance brings the first actual
credentials — database passwords and the FDB backup encryption key —
and neither goes into `installations`, private or not, because every
source in the survey above rejects a repository as protection for a
credential. They live in Secret Manager in the
management project, which is already
[ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)'s
answer, and reach the workloads one of two ways:

- **An external-secrets operator** on the instance cluster,
  authenticated by Workload Identity, with a `ClusterSecretStore`
  pointed at the management project and an `ExternalSecret` per
  credential. This is the sketch rejected above for identifiers, and it
  is correct here: the values are credentials, the read happens on the
  destination cluster, and Argo never holds them — which is precisely
  what Argo's own guidance asks for.
- **Crossplane writing connection details directly** into a cluster
  Secret, which is what it already does for a CloudSQL instance's
  generated credentials, and which needs no extra operator for the
  secrets Crossplane itself creates.

The split is likely both: Crossplane for what it generates, the operator
for what it does not — the FDB encryption key in particular, which is
generated once by a recipe and must outlive every cluster. Deciding it
before the first instance is what stops a credential landing in a
manifest by default.

**An instance.** `spec.instances` joins the XRD, and the instance
composite carries `state: up | draining | down`. Project, network,
cluster, CloudSQL, and — where it declares a `domain` — a static
address, a DNS zone and a certificate. This is where `gcp-up` and
`gcp-down` stop being scripts and become a field, and it is the step
that proves the installation. The workloads on that cluster are
`Release` resources of `provider-helm`, which is what the existing
`queenswood-platform` composites already do.

**Draining.** The `Usage`-gated export Jobs, which
[ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
says to treat as unproven until a cycle runs unattended. Its stated
precondition is that the Keycloak restore is unattended first — #349
needed four manual steps — so that gap closes before this is built.

## Recipe by recipe

**Already replaced.** `gcp-org-create`'s org-role half by
`gcp-boot-org-roles`; `gcp-iam-bootstrap`'s identity by the
composition's `platform-identity`; `gcp-crossplane-login` by
`gcp-adc-boot` for the boot path and by Workload Identity for the
durable one.

**Becomes a managed resource.** `gcp-project-create`;
`gcp-dns-zone-create`; `gcp-backup-bucket-create`;
`gcp-backup-lifecycle-set`; `gcp-keycloak-restore-sa-create`;
`gcp-keycloak-backup-sa-create`; the `skipDefaultNetworkCreation` half
of `gcp-org-create`.

**Dies with the key.** `gcp-backup-hmac-create` and
`_gcp-allow-sa-keys`, once the backup agents reach GCS through the
proxy the durable tier proposes — there is then no HMAC key to mint and
no exemption to apply. Both stay until that is built and a restore has
been proven through it, because they are what the current backup runs
on.

**Becomes a field.** `gcp-up` and `gcp-down` become `state`.

**Dies with the model.** `gcp-project-delete`, `gcp-gke-delete`,
`gcp-cloudsql-delete`, `gcp-ip-delete`, `gcp-cert-delete`,
`gcp-vpc-delete`, `gcp-dns-zone-delete`, `gcp-dns-records-delete` —
deletion is reconciliation, and a project is retired by lifting its lien
deliberately. `gcp-cloudsql-wire` dies too: the composition wires the
connection rather than a recipe reading it out of status.

**Moves to `gcp.just` as it stands.** The operational reads and
in-cluster actions, which have no declarative equivalent because they
act on a running system rather than on its shape:
`gcp-fdb-restore-points`, `gcp-fdb-export`,
`gcp-keycloak-restore-points`, `gcp-keycloak-export`,
`gcp-keycloak-idp`, `gcp-keycloak-vault-secret`,
`gcp-k8s-redeploy-svc`, `gcp-dns-check`, `gcp-health-check`. Each loses
its `pass` lookups in favour of Secret Manager and the manifest, and
each gains the installation code in place of `QUEENSWOOD_ENV`.

**Never migrates.** The directory work in
[cloud-account](../recipes/cloud-account.md) — the organisation, the
billing account, domain verification, the access groups — because Cloud
Identity has no API reachable before a project exists. `gcp-oauth-client`
stays a console step for the same reason, and only its capture changes.

## Open questions

- How a composition change and the manifest change it requires land
  together across two repositories. Keeping new XRD fields optional
  with defaults makes it rare rather than solved.
- Whether a redacted worked example stays in
  [cloud-naming](../recipes/cloud-naming.md), or the whole example
  follows the manifests into `installations`. It is the one piece of
  this documentation that reads better with real values in it.
- Whether `pass` is retired at the pivot or kept for the boot path.
  Nothing in the durable design reads it, but `gcp-adc-boot` runs before
  any of it exists.
- Whether the management cluster stays publicly reachable, which
  [ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
  leaves open and which interacts with putting a private network in
  front of the instances.
- Whether an S3-to-GCS proxy restores as well as it backs up. The
  design turns on it, and the answer comes from a real restore rather
  than from the proxy's documentation.

## References

- [ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
  — the folder as an installation, declared `state`, ordered draining,
  and why foundations are liened.
- [ADR-0023](../adr/0023-installation-naming-and-access.md) — the
  installation code, the naming scheme and the four capabilities.
- [ADR-0016](../adr/0016-crossplane-over-terraform.md) — why
  infrastructure is declared rather than scripted.
- [cloud-foundation](../recipes/cloud-foundation.md) — what a deployment
  builds, and the two identities that build it.
- [cloud-naming](../recipes/cloud-naming.md) — the inventory every new
  resource takes its name from.
- [cloud-deployment](../recipes/cloud-deployment.md) — the tier model
  and the up/down runbook this replaces.
- [recovery-procedures](../recipes/recovery-procedures.md) — what a
  restore actually does, which is what the durable tier exists for.
- [GitOps Principles v1.0.0](https://opengitops.dev/) — the four
  principles, published by the GitOps Working Group.
- [Argo CD best practices](https://argo-cd.readthedocs.io/en/stable/user-guide/best_practices/)
  — why a config repository is kept separate from a source repository.
- [Argo CD secret management](https://argo-cd.readthedocs.io/en/stable/operator-manual/secret-management/)
  — destination-cluster secret management, and the plaintext Redis
  cache that rules out injecting secrets at manifest generation.
- [Flux secrets management](https://fluxcd.io/flux/security/secrets-management/)
  — plain-text secrets in desired state, and not co-locating ciphertext
  with its key.
