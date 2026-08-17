# Queenswood deployment

How Queenswood ships and runs — the Helm chart, the kind dev loop, and
the Crossplane-managed cloud infrastructure underneath it.

## Deploy each service as project + base + shared Dockerfile

A deployable service is a project (pure config, `deps.edn`) plus a
base (owns `main.clj`) plus an image built from the shared
`infra/docker/service/Dockerfile` with a `PROJECT_NAME` build-arg —
never a bespoke Dockerfile. Every service Deployment waits on the
bootstrap Job via a `wait-for-bootstrap` initContainer before
starting; neither the migrator nor the bootstrap Job may be skipped
in any deployment flow. A cross-pod startup dependency is expressed
via the deployment's `waitFor` list, which adds a `wait-for-<dep>`
initContainer polling the target's `/actuator/health/liveness`.
Deploy flows share the same Helm release name (`queenswood`) so resource
names don't diverge between them. Never bake an environment name into
a resource name — discriminate via `values.yaml` overrides and env
vars. A project MAY carry a service-specific `application.yml`; a
service MAY listen on more than one port, with `port` the primary
(probes and the Service's `http` port target it) and further
listeners in `extraPorts`; a service MAY set `replicas > 1` if it's
HTTP-fronted or a processor that doesn't own a changelog cursor.
`exclusive-dispatchers-service` stays at 1 — it owns every changelog
cursor and every cron trigger, and each admits exactly one
dispatcher; scale the relay tier by sharding stores across
deployments. Raising replicas elsewhere buys standbys
rather than throughput until `message-bus/send` carries a partition
key and topics have more than one partition.
See [deployment](../../../docs/recipes/deployment.md).

## A folder is an installation, and its foundations are not deleted

A GCP folder is what an installation is: an IAM boundary, the one place
an org-policy exemption is expressed, and the only stable handle, since
project ids carry random suffixes and everything else is discovered from
the folder down. Inside it, a management project running Crossplane and
Argo, never torn down, and one disposable project per instance. There
may be as many folders as the installer wants; each is independent and
identically shaped, with its own bootstrap identity and management
project because those rights are folder-scoped.

Protect a foundation in GCP rather than in a manifest. Projects, DNS
zones and backup buckets carry `managementPolicies` without `Delete` and
a project lien; a folder cannot carry a lien, so what protects it is
that nobody holds `resourcemanager.folders.delete`. The lien matters
more than the policy — a policy is a convention a later edit undoes, and
the hazard is not a deleted control plane, which leaves managed
resources with no finalizers running, but a live one watching its
resources vanish through a prune and doing what it was told. Only the
disposable tier — clusters, databases, addresses, certificates — is
fully managed with `Delete`.

Express off as a desired state rather than an absence of one:
Crossplane reconciles toward what is declared and has no notion of
stopped.
See [ADR-0022](../../../docs/adr/0022-cloud-foundation-and-environment-lifecycle.md).

## Build the plane before a merge can install anything

A control plane running another toolchain cannot apply an
`XManagementPlane` at all — the manifest names a kind its API
server has never heard of — so the plane comes before any merge that
would install something. Grant the bootstrap identity its rights on the
folder or the parent, never a key. Commit the manifest before applying
it, and before any plane takes over reading it from git, and pivot the
composite off a throwaway plane before discarding that plane. Never
grant a person `serviceAccountTokenCreator` on the platform identity or
create a key for any of the four identities, never assume you may create
a folder — ids are required and one may be handed to you instead — and
never delete a project as a side effect of an edit. Deploying with
`instances: []` is valid, asking a platform team for the folder and the
identity shortens this path without changing it, and another XRD and
composition loaded onto the plane deploys something else the same way.
See [crossplane-app-deployment](../../../docs/recipes/crossplane-app-deployment.md).

## An installation is one file, and changing it is a merge

Change what exists by editing the manifest rather than by acting on GCP,
and apply from merged state only, since a `pull_request` trigger gets no
cloud identity. Push the manifest before a plane takes over reading it
from git. Supply `management.projectId` always and
`createFolder.folderId` wherever the folder already exists, give
`metadata.name` and `spec.code` the same string, keep the manifests
repository private, and read `status` back rather than committing it.
Never commit anything secret beside the manifest, never name a principal
in `access` that does not exist — IAM rejects the binding, not the
manifest — never create a key for an identity the installation
composes, and never leave a new XRD field required when the manifest
that sets it lives in another repository. `management.source` may name
upstream, a fork, or a mirror that vendors the layout, pinned to a tag;
an empty `access` mapping installs and capabilities may be added later;
and one manifest per folder allows more than one installation.
See [queenswood-installation](../../../docs/recipes/queenswood-installation.md).

## Cloud infrastructure is Crossplane, not Terraform

Cloud infrastructure is declared via Crossplane, not Terraform. A
small local kind cluster (`xp-mp`, the management plane) runs
Crossplane plus the GCP upjet providers and `provider-helm`; every
cloud resource is a Crossplane Managed Resource or a Composite of
them (XRDs + Compositions). Argo CD on the same kind cluster applies
the manifests from the repo, and workloads on GKE are themselves
Crossplane `Release` resources of `provider-helm`.
See [ADR-0016](../../../docs/adr/0016-crossplane-over-terraform.md).

## A composed resource is identified by its composition name

Change a resource's `- name:` to rebuild it under a new
`metadata.name`: Crossplane matches on the composition resource name
and recreates from the composite's record, so renaming the object alone
rebuilds the old one. Set `policy.fromFieldPath: Required` where a
missing source is a mistake rather than a meaning — a patch whose
source is absent is skipped silently — and put constants in `base`,
because a `Format` transform with no verb for its input corrupts the
value. Server-side apply gives every field a manager, and a manager
that stops declaring a field it solely owns removes it — so the
composition owns everything it patches, deleting a patch deletes the
field rather than leaving it, a field the composition never set stays
free for the provider to late-initialise or for a hand patch to hold,
and two managers declaring one field make it stable. Check
`metadata.managedFields` before assuming either way.
Withhold `Delete` from `managementPolicies` for anything whose loss is
unrecoverable: deleting the managed resource then orphans the cloud
resource rather than destroying it, and deleting a composite destroys
whatever its resources permit. Install a provider for every kind the
composite composes, on every plane that composes it, and never compose
a cluster-scoped kind from a namespaced composite. Neither failure
belongs to the resource it names: one pipeline step failing — an
unparseable template, a kind with no CRD — stops every composed
resource and reports on the composite. Read `Synced`, `Ready` and
`LastAsyncOperation` before concluding anything.
See [crossplane](../../../docs/recipes/crossplane.md).

## Provider resources are Terraform underneath

Read the CRD with `kubectl explain` before writing a composed resource,
not the provider's documentation — shapes differ between versions and
from what Terraform documents. Delete the managed resource to change
anything that identifies it: a ForceNew change is refused rather than
performed, and the refusal appears in `LastAsyncOperation`, so
diagnosing from `Synced` alone misreads it. Use the `.m.` API group.
Set the external name explicitly where it must differ from the
Kubernetes name or where something else spells the same string, and
feed a generated id back as an adopt value where the external name is
empty after create, or the resource never completes. Do not re-add a
patch for a field late-initialisation now owns.
See [crossplane-providers](../../../docs/recipes/crossplane-providers.md).

## An automation identity is granted, never inherited

Give every node pool its own service account holding
`roles/container.defaultNodeServiceAccount`, and never rely on the
default compute service account being powerless — that is an org policy
enforced somewhere else. Grant both halves of Workload Identity, the
GCP binding and the `iam.gke.io/gcp-service-account` annotation, and
pin the Kubernetes service account name so the binding matches
something. Grant `iam.serviceAccounts.actAs` on any account something
must attach to a resource. Audit an inheriting identity against every
resource it must manage before the identity that created them is
discarded: whoever creates a project owns it, so a bootstrap identity
holds rights nothing declared. Prefer a project custom role over a
predefined role granting writes you do not want, naming it with
underscores because a custom role id takes no hyphens, and do not
assume a role can be granted at the scope its feature acts on.
`gcloud auth login` does not refresh ADC.
See [gcp-iam](../../../docs/recipes/gcp-iam.md).

## A parent Application holds only kinds that already exist

Keep concrete resources out of a parent Application: anything whose
kind a child installs belongs in a child of its own, or the parent
fails building a task for an unknown kind and never applies the child
that would install it. Sync waves do not resolve a missing kind, and
`SkipDryRunOnMissingResource` skips the dry run rather than making an
apply succeed. Set `ServerSideApply=true` for charts with large CRDs,
whose client-side apply exceeds the annotation limit. Set retry budgets
that outlast an operator install, since an Application that exhausts
its retries waits for a revision change or a manual sync — a merged fix
does not reach it. Set `prune: false` where pruning would delete
something a missing file should not delete. Merge a change before
expecting Argo to apply it: it reads the revision an Application names,
never a working tree.
See [argocd](../../../docs/recipes/argocd.md).

## Argo reads a private repository as a GitHub App

Use a GitHub App, never a deploy key or a personal access token: it
belongs to the organisation rather than to a person, is revoked by
uninstalling it, and needs no SSH egress. Grant it Contents read-only
and install it only on the repositories Argo reads. Keep the private key
in a secret store and let an operator place the Secret, and add a new
key before deleting the one it replaces, since an App may hold two at
once. Never commit the `.pem` or pass it on a command line, and never
give the App webhook access or any write permission. One App may serve
several repositories in the same organisation where the same reader
should reach all of them.
See [argocd-github](../../../docs/recipes/argocd-github.md).

## A recipe fails loudly or not at all

Under `set -e`, `cmd && break`, `[[ test ]] && cmd` and a bare
`VAR=$(cmd)` whose command may fail each end the recipe rather than the
line — and before its first `echo`, so the symptom is an instant exit
with no output. Consume the failure you expect instead: `if cmd; then
break; fi`, or `|| true` where emptiness is handled explicitly. Capture
a command's output into a variable before piping it, since a pipeline
takes the last command's status and a denial otherwise reads as an
empty result. Use whatever the caller supplied and discover only what
they did not, because discovery fails where an argument would have
worked. Declare an overridable variable with `env_var_or_default`, and
name a recipe for what it acts on.
See [justfile-recipes](../../../docs/recipes/justfile-recipes.md).
