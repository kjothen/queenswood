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
