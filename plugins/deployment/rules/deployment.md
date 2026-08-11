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
Deploy flows share the same Helm release name (`bank`) so resource
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
source is absent is skipped silently. Put constants in `base`, because
a `Format` transform with no verb for its input corrupts the value.
Install a provider for every kind the composite composes, on every
plane that composes it, and never compose a cluster-scoped kind from a
namespaced composite: either failure stops the whole pipeline rather
than the one resource. Do not patch a field the composition sets and
expect it to hold, and do not delete a composite to tidy up — it
deletes what it composes, subject to each resource's
`managementPolicies`. Read `Synced`, `Ready` and `LastAsyncOperation`
before concluding anything; they report different failures.
See [crossplane](../../../docs/recipes/crossplane.md).

## Provider resources are Terraform underneath

Read the CRD with `kubectl explain` before writing a composed resource,
not the provider's documentation — shapes differ between versions and
from what Terraform documents. Delete the managed resource to change
anything that identifies it: a ForceNew change is refused rather than
performed, and the refusal appears in `LastAsyncOperation`, so
diagnosing from `Synced` alone misreads it. Use the `.m.` API group.
Set the external name explicitly where it must differ from the
Kubernetes name, or where something else spells the same string. Do not
re-add a patch for a field late-initialisation now owns.
See [crossplane-providers](../../../docs/recipes/crossplane-providers.md).

## An automation identity is granted, never inherited

Give every node pool its own service account holding
`roles/container.defaultNodeServiceAccount`, and never rely on the
default compute service account being powerless — that is an org policy
enforced somewhere else. Grant both halves of Workload Identity, the
GCP binding and the `iam.gke.io/gcp-service-account` annotation, and
pin the Kubernetes service account name so the binding matches
something. Audit an inheriting identity against every resource it must
manage before the identity that created them is discarded: whoever
creates a project owns it, so a bootstrap identity holds rights nothing
declared. Prefer a project custom role over a predefined role that
grants writes you do not want, and do not assume a role can be granted
at the scope its feature acts on. `gcloud auth login` does not refresh
ADC.
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
something a missing file should not delete.
See [argocd](../../../docs/recipes/argocd.md).
