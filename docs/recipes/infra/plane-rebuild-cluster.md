# Rebuilding the plane's cluster

<!-- tessl-plugin: deployment -->

## Status

**Untested.** Every step below is derived from the compositions, the
chart and the provider's own behaviour rather than from having done it,
and the first person to follow it should correct it as they go. The
timings are unknown.

## Problem

You need to replace the cluster a management plane runs on — a field
that identifies the cluster has changed, or its name is wrong — while
the folder, the projects, the identities, the network and every instance
the plane manages stay where they are.

## Solution

The plane builds its own successor, the successor takes the estate over,
and the cluster it replaces is deleted afterwards. No boot plane and no
seed identity: the platform identity already holds `container.admin` and
`compute.admin` inside the folder, which is all a second cluster in the
management project needs. A boot plane exists because nothing inside the
folder can act before the folder does, which is not the case here.

### Prerequisites

- A plane that is up, with nothing unready, and `MGMT_CTX` reaching it.
- The change forcing the rebuild ready to merge, giving the cluster a
  name **and** a composition slot the composite is not already using.
  Step 2 is that check.
- Write access to this repository. The installations repository is only
  read.
- The capability each step names. Ours is a Google group; yours may
  differ.
- Nothing in flight on an instance: for the length of this two planes
  reconcile one estate, and then one of them stops.

Where no plane is running at all — its cluster already gone — this is
not the procedure. Nothing is left to build a successor, so raise a boot
plane and install, which adopts the folder, the projects and the
identities that survived; see
[management-plane-install](management-plane-install.md).

Nothing is restored here. The plane holds no state of its own: every
fact it reconciles from is in GCP, in git or in Secret Manager, and the
managed resources on it are a cache of what the cloud already says. What
stands in for a restore is adoption, and adoption is by name — which is
why the naming rule in [cloud-naming](../practices/cloud-naming.md) is
load-bearing rather than tidy.

### 1. Record what the plane holds

**As the installation's platform viewer.** Ours is
`grp-gcp-<code>-platform-viewer@`, populated rather than joined.

```bash
just crossplane-slots > slots-before.txt
just crossplane-external-names > names-before.txt
just crossplane-unready
just argo-apps-status
```

Every slot with its management policies, every resource whose cloud
identifier is not its Kubernetes name, and a plane with nothing
outstanding. Step 7 diffs against these two files: each line is a cloud
resource the successor has to adopt rather than create.

Start from a plane that is already healthy. A resource that was not
`Synced` before the rebuild will not be diagnosable after it.

### 2. Confirm the successor gets a name and a slot of its own

**No cloud capability.** The composition, and `slots-before.txt`.

Two names have to differ from what is live, and for different reasons.

**The cluster's**, because GCP holds one name once. A change that keeps
it — a ForceNew field such as `zone`, `region`, `datapathProvider` or
`inTransitEncryptionConfig`, applied on its own — cannot be done this
way at all: nothing can stand a second cluster up beside the first, so
that is a delete and an install rather than a swap.

**The composition slot's**, because a composed resource is identified by
it. Reuse the slot the live cluster sits in and Crossplane matches the
existing object to it and keeps its `metadata.name`: the new name is
ignored, the composite reports `Synced`, and nothing whatever happens.
See [crossplane-design](crossplane-design.md).

### 3. Merge the change, and let the plane build the successor

**No cloud capability.** Write access to this repository.

```bash
just check-versions
```

Merge. The plane's Argo picks the composition up and its Crossplane
composes the successor — the cluster, its pool and the node identity —
in the same project, on the same subnet, from the same manifest.

The cluster it is running on leaves its slot at the same moment. The
managed resource is deleted from the plane; the cluster is not, because
`retain` withholds `Delete` from it. From here it runs with nothing
reconciling it, which is what makes it the way back.

```bash
just crossplane-unready
kubectl --context "$QW_CODE-mgmt" -n crossplane-system \
  get cluster.container.gcp.m.upbound.io
```

Ten minutes or so for the cluster, and the pool after it. The successor
is empty when this finishes: the `Release`s that install Crossplane and
Argo are `Observe` on the plane reconciling them, deliberately, so no
plane installs onto anything.

### 4. Reach the successor

**As the installation's cluster admin.** Ours is
`grp-gcp-<code>-cluster-admin@` — join for steps 4 and 5, then leave.

By hand rather than with `just plane-ctx`, which renames whatever it
fetches to `MGMT_CTX` — and `MGMT_CTX` still has to reach the plane in
charge until step 8.

```bash
NEXT_CLUSTER=<the name the composition now derives>
PROJECT=$(just _mgmt-project)
ZONE=$(gcloud container clusters list --project="$PROJECT" \
         --filter="name=$NEXT_CLUSTER" --format='value(location)')
gcloud container clusters get-credentials "$NEXT_CLUSTER" \
  --zone="$ZONE" --project="$PROJECT"
NEXT="gke_${PROJECT}_${ZONE}_${NEXT_CLUSTER}"
kubectl config use-context "$QW_CODE-mgmt"
```

`get-credentials` makes its own context current, so put the plane in
charge back before doing anything else.

### 5. Install Crossplane and Argo onto it

**As the installation's cluster admin.**

The successor's composite will `Observe` these two releases rather than
install them, so they have to be the releases it expects: same chart,
same version, same values, same release name, same namespace. All five
come off the plane in charge, which composes the objects describing
them.

```bash
CP=crossplane-$QW_CODE-c-mgmt
ARGO=argo-$QW_CODE-c-mgmt

for REL in "$CP" "$ARGO"; do
  kubectl --context "$QW_CODE-mgmt" -n crossplane-system \
    get "release.helm.m.crossplane.io/$REL" \
    -o jsonpath='{.spec.forProvider.values}' > "$REL.values.json"
  kubectl --context "$QW_CODE-mgmt" -n crossplane-system \
    get "release.helm.m.crossplane.io/$REL" \
    -o jsonpath='{.spec.forProvider.chart.version}{"\n"}'
done
```

Spell the kind `release.helm.m.crossplane.io`: the short name resolves
to provider-helm's cluster-scoped `Release` and reports the object as
not found. Read the release names from each object's
`crossplane.io/external-name` rather than assuming — they are
`crossplane` and `argocd`, not the Kubernetes names above, because a
release names one release inside one cluster.

Crossplane first, because Argo's own bootstrap installs resources of
kinds Crossplane owns:

```bash
helm repo add crossplane-stable https://charts.crossplane.io/stable
helm repo update crossplane-stable
helm --kube-context "$NEXT" upgrade --install crossplane \
  crossplane-stable/crossplane --version <version> \
  -n crossplane-system --create-namespace -f "$CP.values.json"
```

Then Argo:

```bash
helm repo add argo https://argoproj.github.io/argo-helm
helm repo update argo
helm --kube-context "$NEXT" upgrade --install argocd argo/argo-cd \
  --version <version> -n argocd --create-namespace \
  -f "$ARGO.values.json"
```

Never without `-f`. The values file carries `extraObjects`, and the
`management-plane` Application lives there — it is what pulls the
providers, the provider configuration, this repository's XRDs and
finally the installation's own manifest. Argo installed without it comes
up reconciling nothing at all.

### 6. Let the successor take the estate

**As the installation's platform viewer.**

```bash
just argo-apps-status "$NEXT"
just crossplane-unready "$NEXT"
```

Four waves, in order: the provider and function packages, the provider
configuration and secret store, the XRDs and Compositions, then the
installation's own manifest from the private repository. Crossplane on
the successor then composes a fresh managed resource for everything the
estate is made of, and each one observes its cloud resource by external
name and adopts it.

The identities need nothing. Workload Identity's pool is per project
rather than per cluster, and the Kubernetes service account names are
pinned by the runtime configuration, so the successor's pods
authenticate as the same identities the plane's did.

Both planes are reconciling the estate at this point, from the same
revision and to the same desired state. That is expected, and step 8 is
where it stops.

### 7. Check it adopted rather than recreated

**As the installation's platform viewer.**

```bash
just crossplane-slots "$NEXT" > slots-after.txt
just crossplane-external-names "$NEXT" > names-after.txt
diff slots-before.txt slots-after.txt
diff names-before.txt names-after.txt
```

Every slot from step 1 present with the same policies, and the only
differences the ones the change accounts for — the cluster, its pool and
its node identity, plus whatever the old slot names were. A new slot
nothing renamed is a resource being built beside one that already
exists, which is what this step is for. Stop there rather than after it
finishes.

The instances are the half nobody thinks of. The successor reapplies
each unit's composite from the manifests repository, so every instance's
project, cluster, database, buckets, zone and secrets are adopted by
managed resources that have never seen them — and an instance that was
`down` stays down, because its state is a field in a file.

### 8. Swap

**As the installation's cluster admin.** Ours is
`grp-gcp-<code>-cluster-admin@` — join for this step, then leave.

Break-glass, and the moment the successor becomes the plane. Only once
step 7 is clean.

```bash
kubectl --context "$QW_CODE-mgmt" -n crossplane-system \
  scale deploy crossplane --replicas=0
kubectl --context "$QW_CODE-mgmt" -n crossplane-system \
  scale deploy --all --replicas=0
kubectl --context "$QW_CODE-mgmt" -n crossplane-system get deploy
just plane-ctx
```

The core first and then everything: the core reconciles the provider
packages, so a provider scaled down while it runs comes straight back.
Nothing puts these back on its own — the `Release` that installed
Crossplane is `Observe` on the plane running it, and Argo manages no
`Deployment` in that namespace.

Scaling rather than deleting. Deleting the composite would delete what
it composed, subject to each resource's policies, and the ones carrying
`Delete` are the access bindings — including the Workload Identity
binding Crossplane authenticates with, taken away halfway through taking
it away.

`plane-ctx` now fetches the successor and renames its context to
`MGMT_CTX`, so every recipe and every habit points at the plane in
charge again.

### 9. Delete what it replaced

**As the installation's cluster admin.** Ours is
`grp-gcp-<code>-cluster-admin@` — join for this step, then leave.

Not until the successor has held the estate long enough to trust,
because until this runs there is a way back: scale the old plane's
controllers up, revert the merge, and it resumes.

```bash
gcloud container clusters delete <the cluster it replaced> \
  --project="$PROJECT" --zone=<zone>
```

The node pool goes with it, and so does the kubeconfig `Secret` it
wrote. Then whatever else the change renamed and nothing now holds — a
node identity and its two bindings, for a rename that moved them. They
are reported by nothing and reconciled by nothing, and they go on
costing.

### 10. Record what happened

- **RTO** — wall clock from the merge to the successor reconciling the
  estate. Nothing has measured it, and no service is down for any of it.
- **That adoption held.** Until a plane has adopted an estate, that it
  can is an assumption about every external name at once.
- **Anything here that was wrong**, which is likely, since nobody has
  run it.

## Failures

**The composite reports `Synced` and no second cluster appears.** The
change reused the live cluster's composition slot, so Crossplane matched
the existing object to it and kept its name. Give the slot a new name
and merge again.

**The successor is built and stays empty.** Expected until step 5. The
`Release`s are `Observe` on whichever plane reconciles them, so nothing
installs onto a cluster it is not on.

**The successor's composite reports the Crossplane or Argo `Release` not
found.** The helm release is named something other than the object's
`crossplane.io/external-name`, or is in another namespace. An `Observe`
release it cannot find is one it will never install.

**Argo comes up on the successor with no Applications.** The values file
was omitted or rebuilt by hand, so `extraObjects` went with it and the
`management-plane` Application was never created.

**Provider pods on the successor cannot authenticate.** Expected while
the second wave is still syncing: the runtime configuration pinning the
Kubernetes service account name has to land before the pods that name
it. Persisting past that, the name is wrong and Workload Identity is
bound to something nothing runs as.

**An instance's resource is created rather than adopted.** Its external
name is not what the composition derives, so a fresh managed resource
observed nothing and built a second one. Stop before it finishes: the
first is still there, and two of them is worse than either.

**The plane you scaled down comes back.** Something is reconciling those
`Deployment`s that should not be — the composite's `Release` is meant to
be `Observe` on the plane it installed. Read `crossplane-slots` on the
successor before scaling anything again.

## Rules

**MUST:**

- Give the successor a cluster name and a composition slot of their own.
  A reused slot keeps the live object and ignores the new name, and the
  composite reports `Synced` while nothing happens.
- Record the slot list and the external names before, and diff them
  after. Adoption is the whole procedure, and nothing else reports
  whether it happened.
- Install Crossplane and Argo onto the successor with the release name,
  namespace, chart version and values the composed `Release`s carry, and
  never without `-f`: `extraObjects` holds the Application that pulls
  everything else.
- Install Crossplane before Argo, and read the release names from each
  object's `crossplane.io/external-name` rather than from its Kubernetes
  name.
- Prove the instances adopted, not only the plane, before swapping.
- Scale the old plane's Crossplane core down before its provider pods —
  the core puts a provider back — and only after the successor is
  holding the estate.
- Keep the cluster it replaced until the successor has been trusted:
  while it stands, scaling it up and reverting the merge is the way
  back.

**MUST NOT:**

- Never rebuild a plane this way where the change keeps the cluster's
  name. Nothing can stand a second cluster up under one name, so a
  ForceNew field on its own is a delete and an install.
- Never use `just plane-ctx` before the swap: it renames whatever it
  fetches to `MGMT_CTX`, which until then has to reach the plane in
  charge.
- Never delete the old plane's composite to stop it. Its access
  bindings carry `Delete`, including the one its Crossplane
  authenticates with.
- Never use this on an instance's cluster, which has a live plane above
  it and data under it — that is
  [instance-rebuild-cluster](instance-rebuild-cluster.md) — and never as
  a way to retire an installation, which takes the recovery project and
  the backups with it.

Commands: `just crossplane-slots`, `just crossplane-external-names`,
`just crossplane-unready`, `just argo-apps-status`, `just
check-versions`, `just plane-ctx`.

## Discussion

A plane cannot delete and recompose the cluster it is running on, which
is why this is a swap rather than the rebuild an instance gets. Every
other cluster in an installation has something above it: an instance's
is composed by the plane, so deleting the managed resource is enough and
Crossplane recomposes it — that is
[instance-rebuild-cluster](instance-rebuild-cluster.md), easier because
the thing doing the work is not the thing being replaced. The plane's
cluster has nothing above it, so the work is done by the plane itself,
before it hands over.

**What makes it cheap is that everything else is project-scoped.** The
successor sits in the management project, on the management subnet,
under the same folder. Workload Identity's pool is the project's, the
IAM bindings name identities rather than clusters, and the secrets are
in Secret Manager — so the successor inherits every capability the plane
had by being in the same project, and none of it is composed twice.

**Why two releases are installed by hand.** The composed `Release`s are
`Observe` on the plane that reconciles them, which is the seam this uses
rather than a hole in it: a plane watches what installed it and never
acts on it, precisely so that a composition edit cannot have Crossplane
upgrading itself mid-reconcile. Something outside always installs those
two — a boot plane at install time, a person here — and
[argocd-upgrades](argocd-upgrades.md) already builds a values file from
the composed object for the same reason.

**Adoption is the recovery.** What makes this survivable is that nothing
on the plane is the source of anything: the folder id is in the
environment, the project ids are in the manifest as `adopt`, and every
other name the composition derives from the code. A successor observes
each of them and takes them over. The corollary is that a name that
cannot be derived cannot be adopted, which is why
`crossplane.io/external-name` is for a name Kubernetes cannot express
rather than one that is merely tidier.

**The slot is what makes this a one-off rather than a routine.** A
successor needs a new slot, and slots are written in the composition, so
each rebuild is an edit to this repository rather than a value in a
manifest. A plane that could declare a generation — the slot named from
it by a templated step, and the cluster with it — would make this a
merge and a swap with no composition edit at all, and would let a
ForceNew change take the path above instead of a delete and an install.
It needs one more thing than the generation: `XCluster` derives its
network and subnet names from its own label, so a successor sharing the
network it is replacing a cluster in needs the network's name to come
from a field of its own.

**What this does not rebuild.** The cluster, not the plane: the folder,
the management and recovery projects, the identities, the network, the
secrets and every instance survive it, and the installation keeps its
code, its name and its domain. Rebuilding the management *project* is a
different procedure and its id is consumed permanently. Retiring an
installation is a third, and nothing writes it down.

## References

- [management-plane-install](management-plane-install.md) — building a
  plane where none is running, which adopts what survived
- [instance-rebuild-cluster](instance-rebuild-cluster.md) — the same act
  under a live plane, with data under it
- [crossplane-design](crossplane-design.md) — a composed resource is
  identified by its composition name
- [crossplane-live](crossplane-live.md) — what a change to a live
  resource does, and what a rename is
- [argocd-upgrades](argocd-upgrades.md) — building a values file from
  the composed `Release`, and what omitting one costs
- [ADR-0024](../../adr/0024-instances-are-their-own-composites.md) — the
  plane and the instances on it, and what each composes
- [plane-cluster-naming](../../plan/plane-cluster-naming.md) — the
  rename this was first written for
