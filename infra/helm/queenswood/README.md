# Queenswood Helm chart

Deploys the Queenswood core-banking platform on Kubernetes:

- **Apache Pulsar** (subchart, dev-grade single-replica
  configuration)
- **FoundationDB** cluster managed by the FDB Kubernetes operator
  (subchart provides the operator + CRDs; this chart provides the
  `FoundationDBCluster` CR)
- **bank-api-service** (HTTP REST API + dispatchers)
- **bank-clearbank-{adapter,simulator}-service** and
  **bank-onfido-{adapter,simulator}-service** (HTTP)
- **bank-{cash-account,party,payment,interest,transaction,idv}-service**
  (Pulsar processors)

## Quick start

```bash
helm dependency update infra/helm/queenswood

helm install bank infra/helm/queenswood \
  --set image.tag=$(git rev-list --count HEAD) \
  --set secrets.adminApiKey=$(openssl rand -hex 16)
```

For dev/CI, point at a `kind` cluster:

```bash
kind create cluster
for svc in bank-bootstrap-service bank-api-service \
           bank-cash-account-service \
           bank-party-service bank-payment-service \
           bank-interest-service bank-transaction-service \
           bank-idv-service bank-clearbank-adapter-service \
           bank-clearbank-simulator-service \
           bank-onfido-adapter-service \
           bank-onfido-simulator-service; do
  docker buildx build --build-arg PROJECT_NAME=$svc \
    -t ghcr.io/kjothen/$svc:dev \
    -f infra/docker/service/Dockerfile .
  kind load docker-image ghcr.io/kjothen/$svc:dev
done
helm install bank infra/helm/queenswood \
  --set image.tag=dev \
  --set secrets.adminApiKey=test
```

## Bootstrap

`bank-bootstrap-service` runs as a one-shot Job (named
`<release>-bank-bootstrap-<image.tag>`) before any service starts.
It opens FDB (which applies record metadata via `bank-fdb-resources`),
declares the Pulsar tenant/namespace/topics/schemas via
`bank-pulsar-resources`, and idempotently seeds the internal
Organization. Service Deployments block on its completion via a
`kubectl wait --for=condition=complete` initContainer; services
discover the seeded organization by querying FDB on startup.

The Job name embeds `image.tag`, so `helm upgrade` with a new tag
produces a fresh Job rather than failing on the immutable Job spec;
old Jobs age out via `ttlSecondsAfterFinished`.

## Verifying

```bash
kubectl get foundationdbclusters
kubectl get jobs                    # bank-bootstrap-<tag> should complete
kubectl port-forward svc/bank-bank-api-service 8080
curl http://localhost:8080/openapi.json
```

## v1 limitations

- **FDB cluster ConfigMap timing**: each app pod's `initContainer`
  polls for the FDB-operator-written ConfigMap. The first install
  may take several minutes for the operator to provision FDB.
- **Pulsar subchart dev defaults**: no persistence, single replica
  for ZK/BK/broker. Override `pulsar.*.persistence` and replicaCount
  for production.
- **Bootstrap RBAC**: the chart binds a `get/watch/list jobs` Role
  to the namespace's `default` ServiceAccount so service init
  containers can poll the Job. If your services run under a custom
  ServiceAccount, extend the RoleBinding accordingly.

## Cleaning up

```bash
helm uninstall bank
kubectl delete foundationdbcluster bank-fdb
kubectl delete pvc -l app.kubernetes.io/instance=bank
```
