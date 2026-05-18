# XPlatform Implementation Plan

Collapse the ~25 flat MRs in `infra/helm/queenswood-gcp/` into a
single namespaced Composite Resource (`XPlatform`), backed by a v2
Composition that owns every project-scoped MR. Argo manages **one**
resource per environment (the XR); Crossplane owns the reconcile
loop for everything underneath.

This is the "Compose any Kubernetes resource" payoff from
Crossplane v2 — Argo never touches the underlying MRs, which
removes a whole class of cross-cluster drift questions and gives
us proper SSA-driven field ownership at the Composition level.

**Nothing is in production.** We can `gcp-down` and `gcp-up` to
verify; no need to keep the chart in a half-and-half state during
migration. The plan is two phases — one big PR, one small follow-up.

## What this does NOT affect

Three local-dev workflows the migration **must keep working
unchanged**:

- **REPL via Testcontainers** — `just repl`, drives the full
  system inside FDB + Pulsar testcontainers. No kind, no
  Crossplane.
- **`just kind-up`** — local kind cluster with
  `infra/helm/queenswood` (the application chart: Polylith
  services + FDB + Pulsar via operators). No Crossplane.
- **`helm install queenswood` against any cluster** — the
  released OCI chart at `ghcr.io/repldriven/queenswood`. Same
  chart as kind-up; deployable anywhere.

Only `infra/helm/queenswood-gcp/` is in scope. The application
chart (`infra/helm/queenswood/`) doesn't currently use Crossplane
MRs and won't gain a dependency on them through this plan —
queenswood is already split on Pattern B (separate app and
platform charts), so the local story stays clean.

If a future need arises for a label-selected
`xplatform-gcp` + `xplatform-local` pair (Pattern A — one XRD
used in both environments), it's an additive change to this
shape, not a precondition.

## Target shape

```
infra/platform/crossplane-xrds/
├── xplatform-xrd.yml                # CompositeResourceDefinition
└── xplatform-composition.yml        # Composition (pipeline + p&t)

infra/platform/crossplane-providers/
├── providers.yml                    # unchanged
└── mrap.yml                         # NEW — pin only the kinds we use

infra/helm/queenswood-gcp/
└── templates/
    ├── xplatform.yaml               # ONE XR per install
    ├── cluster-provider-config.yaml # v2 ClusterProviderConfig
    └── database-password.yaml       # static Secret for User MR
```

Eight templates → three. Argo's view of queenswood-gcp shows the
XR + Secret + ClusterProviderConfig; the ~19 composed MRs are
inside the XR's Composition, invisible to Argo.

## Phase 1 — the whole migration (one PR)

- [ ] **1.1 XRD.** Draft
  `infra/platform/crossplane-xrds/xplatform-xrd.yml`:
  - `apiVersion: apiextensions.crossplane.io/v1`
  - `kind: CompositeResourceDefinition`
  - `spec.group: platform.queenswood.repldriven.com`
  - `spec.names: { kind: XPlatform, plural: xplatforms }`
  - `spec.scope: Namespaced` (v2 default)
  - Schema: `spec` with `projectId` (required), `envNamespace`,
    `region`, `zone`, `crossplaneSaName`, `sqlProxyGcpSa`,
    `gkeNodeSa`, `keycloakDbPassword`.
  - Status: `connectionName`, `workloadPool`, `clusterEndpoint`,
    `kubeconfigSecretName`.

- [ ] **1.2 Composition.** Draft
  `infra/platform/crossplane-xrds/xplatform-composition.yml`:
  - `mode: Pipeline`
  - One step: `function-patch-and-transform`
  - `resources:` list with all 19 MRs the chart currently
    renders.
  - Per-resource patches:
    - `forProvider.project` ← `spec.projectId` (on every IAM
      member + ProjectService)
    - `forProvider.member` ← string transform from
      `spec.projectId` + SA name (on every ProjectIAMMember +
      ServiceAccountIAMMember)
    - `forProvider.region` ← `spec.region` (subnets, db)
    - `forProvider.location` ← `spec.zone` (cluster, nodepool)
    - `forProvider.workloadIdentityConfig.workloadPool` ←
      `spec.projectId` + `.svc.id.goog` (cluster)
    - `nodeConfig.serviceAccount` ← derived gke-nodes SA email
      (nodepool)
  - Status pivots (`ToCompositeFieldPath`):
    - `DatabaseInstance.status.atProvider.connectionName` →
      `XR.status.connectionName`
    - `Cluster.status.atProvider.endpoint` →
      `XR.status.clusterEndpoint`

- [ ] **1.3 Chart collapse.** In
  `infra/helm/queenswood-gcp/templates/`:
  - **Add** `xplatform.yaml` — the `XPlatform` XR in
    `crossplane-system`, with `spec.projectId` from
    `.Values.gcpProjectId` (plus the other parameters from
    chart values).
  - **Add** `database-password.yaml` — the static Secret the
    composed User MR references.
  - **Rename + simplify** `provider-configs.yaml` →
    `cluster-provider-config.yaml` (only the v2
    `ClusterProviderConfig.default`; the chart already dropped
    the v1 ProviderConfig in #106).
  - **Delete:** services.yaml, roles.yaml, identity.yaml,
    nodes.yaml, network.yaml, cluster.yaml, database.yaml,
    `_helpers.tpl` (helpers move into the Composition).

- [ ] **1.4 Recipes.** Update to read XR-level status, not
  composed MRs:
  - `gcp-cloudsql-wire`: poll
    `xplatform.platform.queenswood.repldriven.com queenswood
    -n crossplane-system -o jsonpath='{.status.connectionName}'`.
  - `gcp-health-check`: replace the per-MR queries
    (`databaseinstance`, `nodepools`) with `xplatform` queries.

- [ ] **1.5 MRAP.** Inventory every kind the Composition + the
  XQueenswoodApex / XQueenswoodCertificate compositions
  reference; add
  `infra/platform/crossplane-providers/mrap.yml` listing them.

- [ ] **1.6 Docs.** Rewrite the queenswood-gcp section of
  `docs/tdd/infrastructure.md` to describe the XR + Composition
  shape; add an Operational note on the MRAP inventory and how
  to extend it.

- [ ] **1.7 Verify.** `gcp-down` → merge → chart publish →
  `gcp-up`. Confirm the XR reaches `Synced/Healthy` and brings
  up every composed MR. Spot-check
  `kubectl get xplatform -A` shows the right status fields
  pivoted up.

## Phase 2 — polish (only if needed)

Anything Phase 1 surfaces — drift fixes, missed patches, MRAP
gaps, recipe edge cases. The open questions below may also
slot in here if they don't fit naturally into Phase 1.

## Open questions

- **Where does the XR live?** v2 XRs are namespaced. Putting it
  in `crossplane-system` keeps it adjacent to its composed MRs
  and avoids the dependency that the env namespace
  (`queenswood-test`) exists first. Going with that unless
  there's a reason not to.
- **ProjectID via Observe-only Project MR?** Alternative to the
  `gcpProjectId` parameter: model the GCP project as a
  Crossplane Project MR with `managementPolicies: [Observe]`
  and patch the XR's resources from its
  `status.atProvider.projectID`. Still needs one imperative
  substitution (`metadata.name` or `crossplane.io/external-name`
  on the Project MR), so it's a re-shuffle not an elimination.
  Skip for this PR; revisit if multi-project tenancy lands.
- **XR-level connection-secret aggregation.** Composition v2
  can roll per-resource connection details up into one XR-level
  Secret. Currently we have two (kubeconfig from Cluster, DB
  creds from User) consumed by separate things. Worth aggregating
  if it simplifies provider-helm + the cloudsql-wire pin, but
  not a blocker.
- **MRAP scope.** Should the inventory cover both v1 + v2
  groups, or v2 only? v1 CRDs still register; an MRAP that
  omits them deactivates the controllers. Enumerate both to be
  explicit.

## Out of scope

- `XDataPlane` (FDB + Pulsar via operator CRDs in a Composition)
  — separate PR, depends on operator-CRD availability strategy.
- `XBankInstance` (per-tenant slice) — multi-tenant work,
  separate epic.
- Operations (alpha) for FDB backups + cert rotation — watch
  upstream graduation first.
- Folding `XQueenswoodApex` / `XQueenswoodCertificate` into
  XPlatform — they're env-layer (DNS + cert tied to the
  domain), keep them separate.
