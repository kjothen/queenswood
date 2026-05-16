# Keycloak Operator (vendored)

Cluster-wide install of the official Keycloak Operator. Synced into
the cluster by `infra/bootstrap/apps/keycloak-operator.yml` (Argo
Application, sync-wave `2` so the CRDs land before any chart that
emits Keycloak / KeycloakRealmImport CRs).

## Files

- `crd-keycloaks.yml` — `Keycloak` CRD (cluster scope).
- `crd-realmimports.yml` — `KeycloakRealmImport` CRD (cluster scope).
- `operator.yml` — the operator itself: Namespace, ServiceAccount,
  ClusterRoles, ClusterRoleBindings, Service, Deployment.

## Sourced from

- Tag `26.6.1` of [`keycloak/keycloak-k8s-resources`](https://github.com/keycloak/keycloak-k8s-resources/tree/26.6.1/kubernetes).
- The two `.yml` files come from `kubernetes/keycloaks.k8s.keycloak.org-v1.yml` and
  `kubernetes/keycloakrealmimports.k8s.keycloak.org-v1.yml` verbatim.
- `operator.yml` derives from `kubernetes/kubernetes.yml` with three
  deviations:
  1. Adds a `keycloak-operator` `Namespace` at the top.
  2. Lifts the namespace-scoped `keycloak-operator-role` and its
     four `RoleBinding`s to `ClusterRole` / `ClusterRoleBinding` so
     the operator can manage Keycloak instances anywhere on the
     cluster.
  3. Switches both
     `QUARKUS_OPERATOR_SDK_CONTROLLERS_*_NAMESPACES` env vars from
     `JOSDK_WATCH_CURRENT` to `JOSDK_WATCH_ALL_NAMESPACES`.

## Refreshing

```
TAG=26.6.1
BASE=https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/$TAG/kubernetes
curl -sLo crd-keycloaks.yml      $BASE/keycloaks.k8s.keycloak.org-v1.yml
curl -sLo crd-realmimports.yml   $BASE/keycloakrealmimports.k8s.keycloak.org-v1.yml
curl -sLo operator.yml.upstream  $BASE/kubernetes.yml
# Reapply the three patches above, then drop `operator.yml.upstream`.
```
