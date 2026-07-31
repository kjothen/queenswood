# Queenswood Helm chart

Deploys the full Queenswood core-banking platform on
Kubernetes:

- **Apache Kafka** (single-broker KRaft, dev-grade; set
  `kafka.enabled=false` for an external broker)
- **FoundationDB** cluster managed by the FDB Kubernetes
  operator (subchart provides the operator + CRDs; this
  chart provides the `FoundationDBCluster` CR)
- **api-service** (HTTP REST API + dispatchers)
- **bank-clearbank-{adapter,simulator}-service**,
  **bank-onfido-{adapter,simulator}-service**, and
  **uk-companies-house-simulator-service** (HTTP)
- **bank-{cash-account,party,payment,interest,transaction,idv}-processor-service**
  (message-bus processors) and
  **scheduler-processor-service** (cron-driven, no
  message bus)
- **console** (Svelte SPA served via nginx)
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

`helm install`'s `NOTES.txt` prints the `kubectl port-forward`
commands you need to reach the API, the console SPA, and the
Jaeger UI from your host. The bundled Keycloak needs none — the
SPA reverse-proxies it at `/keycloak/*`.

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
and the `kind-*` recipes in `Justfile`.

## Bootstrap

`bootstrap-service` runs as a one-shot Job (named
`<release>-bootstrap-<image.tag>`) before any service
starts. It opens FDB (applying record metadata declared in
`bank-resources`), declares the Kafka topics from the same
component, and idempotently seeds the internal Organization. Service
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
kubectl -n queenswood get jobs   # bootstrap-<tag> should complete
kubectl -n queenswood port-forward svc/queenswood-api-service 8080:8080
curl http://localhost:8080/openapi.json
```

## Tracing

Every service ships OTLP spans to the in-chart Jaeger
(`jaeger.enabled`, on by default). Reach its UI with `just
telemetry-ui`, or port-forward directly:

```bash
kubectl -n queenswood port-forward svc/queenswood-jaeger 16686:16686
```

Pick a service in the UI and **Find Traces**. The usual way to
generate some is the console's **Sandbox > Scenarios** page, which
drives the platform for real against the cluster — run one there,
then read back the spans it produced.

Storage is in-memory, so spans are lost when the pod restarts. To
ship elsewhere, set `otel.endpoint` (it wins over the in-chart
Jaeger); to turn tracing off entirely, set `jaeger.enabled=false`
and leave `otel.endpoint` empty, which disables the SDK rather than
failing.

## v1 limitations

- **FDB cluster ConfigMap timing**: each app pod's
  `initContainer` polls for the FDB-operator-written
  ConfigMap. The first install may take several minutes
  for the operator to provision FDB.
- **Kafka dev broker**: single-broker KRaft, ephemeral (no
  persistence). Set `kafka.enabled=false` and
  `kafka.bootstrapServers` for an external broker in
  production.
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
