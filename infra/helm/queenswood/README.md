# Queenswood Helm chart

Deploys the full Queenswood core-banking platform on
Kubernetes:

- **Apache Pulsar** (subchart, dev-grade single-replica
  configuration)
- **FoundationDB** cluster managed by the FDB Kubernetes
  operator (subchart provides the operator + CRDs; this
  chart provides the `FoundationDBCluster` CR)
- **bank-api-service** (HTTP REST API + dispatchers)
- **bank-clearbank-{adapter,simulator}-service**,
  **bank-onfido-{adapter,simulator}-service**, and
  **bank-uk-companies-house-simulator-service** (HTTP)
- **bank-{cash-account,party,payment,interest,transaction,idv}-processor-service**
  (Pulsar processors) and
  **bank-scheduler-processor-service** (cron-driven, no
  Pulsar)
- **bank-console** (Svelte SPA served via nginx)
- **Keycloak** with embedded H2 for standalone installs
  (`keycloak.dev.enabled: true` by default). GKE
  deployments turn this off and use the operator-driven
  `queenswood-keycloak` chart instead.

## Quick start

Install straight from the published OCI artifact:

```bash
helm install queenswood \
  oci://ghcr.io/repldriven/queenswood \
  -n queenswood --create-namespace \
  --wait --timeout 10m
```

`helm install`'s `NOTES.txt` prints the four
`kubectl port-forward` commands you need to reach the API,
both SPAs, and the bundled Keycloak from your host.

To install from a checkout instead (useful while iterating
locally):

```bash
helm dependency update infra/helm/queenswood
helm install queenswood infra/helm/queenswood \
  -n queenswood --create-namespace \
  --wait --timeout 10m
```

For full kind-loop workflows (build images, load into
kind, install, port-forward, tear down), see
[`docs/recipes/deployment.md`](../../../docs/recipes/deployment.md)
and the `kind-*` / `tilt-*` recipes in `Justfile`.

## Bootstrap

`bank-bootstrap-service` runs as a one-shot Job (named
`<release>-bank-bootstrap-<image.tag>`) before any service
starts. It opens FDB (applying record metadata declared in
`bank-resources`), declares the Pulsar
tenant/namespace/topics/schemas from the same component,
and idempotently seeds the internal Organization. Service
Deployments block on its completion via a
`kubectl wait --for=condition=complete` initContainer;
services discover the seeded organization by querying FDB
on startup.

The Job name embeds `image.tag`, so `helm upgrade` with a
new tag produces a fresh Job rather than failing on the
immutable Job spec; old Jobs age out via
`ttlSecondsAfterFinished`.

## Verifying

```bash
kubectl -n queenswood get foundationdbclusters
kubectl -n queenswood get jobs   # bank-bootstrap-<tag> should complete
kubectl -n queenswood port-forward svc/queenswood-bank-api-service 8080:8080
curl http://localhost:8080/openapi.json
```

## v1 limitations

- **FDB cluster ConfigMap timing**: each app pod's
  `initContainer` polls for the FDB-operator-written
  ConfigMap. The first install may take several minutes
  for the operator to provision FDB.
- **Pulsar subchart dev defaults**: no persistence, single
  replica for ZK/BK/broker. Override `pulsar.*.persistence`
  and replicaCount for production.
- **Bootstrap RBAC**: the chart binds a
  `get/watch/list jobs` Role to the namespace's `default`
  ServiceAccount so service init containers can poll the
  Job. If your services run under a custom
  ServiceAccount, extend the RoleBinding accordingly.

## Cleaning up

```bash
helm uninstall queenswood -n queenswood
kubectl delete foundationdbcluster queenswood-fdb -n queenswood
kubectl delete pvc -l app.kubernetes.io/instance=queenswood -n queenswood
```
