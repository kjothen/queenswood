# Keycloak Operator (vendored)

Cluster-wide install of the official Keycloak Operator, wrapped as a
thin Helm chart. Installed onto GKE by a Crossplane provider-helm
`Release` resource templated from `queenswood-platform`
(`templates/keycloak-operator-release.yaml`, sync-wave `3`) so its
CRDs are present before the queenswood-keycloak chart (sync-wave `6`)
emits a `Keycloak` CR.

## Files

- `crds/crd-keycloaks.yaml` — `Keycloak` CRD (cluster scope).
- `crds/crd-realmimports.yaml` — `KeycloakRealmImport` CRD (cluster
  scope). Both live under `crds/` so Helm installs them once,
  ahead of templates, and skips template rendering on them.
- `templates/operator.yaml` — the operator itself: Namespace,
  ServiceAccount, ClusterRoles, ClusterRoleBindings, Service,
  Deployment.

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
