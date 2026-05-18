# XPlatform Implementation Plan

The next move on the Crossplane v2 path is to collapse the
~25 flat MRs in `infra/helm/queenswood-gcp/` into a single
namespaced Composite Resource (`XPlatform`), backed by a v2
Composition that owns every project-scoped MR. Argo manages
**one** resource per environment (the `XPlatform` XR); Crossplane
owns the reconcile loop for everything underneath.

This is the "Compose any Kubernetes resource" payoff from
Crossplane v2 — Argo never touches the underlying MRs, which
removes a whole class of cross-cluster drift questions and gives
us proper SSA-driven field ownership at the Composition level.

The discipline of this plan is the usual: keep failure radius
small, stop and verify at each phase boundary, split a step if
it grows. **Nothing is in production**; we can torch the live
cluster between phases without worry — that's how we'll verify
each landing.

## Target shape

```
infra/platform/crossplane-xrds/
├── xplatform-xrd.yml           # CompositeResourceDefinition
└── xplatform-composition.yml   # Composition (pipeline + p&t function)

infra/helm/queenswood-gcp/
├── values.yaml                 # gcpProjectId + a few env knobs
└── templates/
    └── xplatform.yaml          # ONE XR per install; everything else is gone
```

That's 8 templates collapsing to 1 — and the cluster surface
shrinks from "Argo manages 25 MRs" to "Argo manages 1 XR;
Crossplane manages 25 composed resources internally".

## Phase 1: XRD + empty Composition

Goal: get an `XPlatform` XRD and an empty `xplatform-composition.yml`
landed via `crossplane-xrds` Argo App. No MRs composed yet. The
chart `infra/helm/queenswood-gcp/` keeps rendering the flat MRs
exactly as today — this phase is *additive*, no behavioural change.

- [ ] **1.1** Draft `infra/platform/crossplane-xrds/xplatform-xrd.yml`:
  - `apiVersion: apiextensions.crossplane.io/v1` `kind: CompositeResourceDefinition`
  - `spec.group: platform.queenswood.repldriven.com`
  - `spec.names: { kind: XPlatform, plural: xplatforms }`
  - `spec.scope: Namespaced` (v2 default)
  - `spec.versions[0].schema.openAPIV3Schema` defines `spec.parameters`
    with `projectId` (string, required), `envNamespace` (string,
    default `queenswood-test`), `region` (string, default
    `europe-west2`), `zone` (string, default `europe-west2-a`),
    plus SA name knobs (`crossplaneSaName`, `sqlProxyGcpSa`,
    `gkeNodeSa`).
  - `status` pivots: `connectionName` (CloudSQL), `workloadPool`
    (GKE), `kubeconfigSecretRef`, `clusterEndpoint`.
- [ ] **1.2** Draft `infra/platform/crossplane-xrds/xplatform-composition.yml`:
  - `mode: Pipeline`
  - One pipeline step using `function-patch-and-transform`
  - `resources: []` for now (empty list — Composition exists but
    composes nothing).
- [ ] **1.3** Sanity-check render + `kubeconform`. The
  `crossplane-xrds` Argo App should sync the new XRD +
  Composition without touching anything else.
- [ ] **1.4** Apply a single dummy `XPlatform` instance manually
  in `crossplane-system` to verify the XRD validates the schema
  and the empty Composition is selected. Delete it after.

**Stop and review.** XRD lands cleanly, Composition exists but
composes nothing. No production behaviour changed.

## Phase 2: compose the foundation block (services + roles + identity + nodes)

Goal: the Composition starts owning the kinds that don't depend
on each other. These are the cheap, fast resources — pure IAM +
API enablement + standalone GCP SAs.

This phase keeps the existing queenswood-gcp chart in place but
deletes the corresponding templates from it *only after* the
Composition is doing the same job. Per-step: add to Composition,
verify Crossplane composes it correctly under a test XR, then
remove the corresponding template from queenswood-gcp.

- [ ] **2.1** Add to Composition `resources:`:
  - `services` × 5 (`ProjectService` MRs, project patched from XR)
  - `crossplane-provider` roles × 6 (`ProjectIAMMember` MRs)
  - `cloud-sql-proxy` SA + cloudsql-client `ProjectIAMMember` +
    Workload-Identity `ServiceAccountIAMMember`
  - `gke-nodes` SA + `defaultNodeServiceAccount` `ProjectIAMMember`
- [ ] **2.2** Deploy a test `XPlatform` (parallel to the live
  chart) in `crossplane-system`. Confirm composed MRs reach
  Ready=True alongside the existing flat ones. Their external
  GCP objects collide by design — the operation is idempotent
  (Crossplane will adopt rather than re-create).
- [ ] **2.3** Once the composed MRs are Healthy, **delete** the
  corresponding templates from `infra/helm/queenswood-gcp/templates/`:
  `services.yaml`, `roles.yaml`, `identity.yaml`, `nodes.yaml`.
  Argo will report those MRs as orphaned but the Composition's
  copies take ownership via Crossplane's
  `crossplane.io/composite` labelling.

**Stop and review.** `gcp-down` + `gcp-up`. The XR should
bring up services + roles + identity + nodes on its own; the
chart now contains only provider-configs + network + cluster +
database templates. Confirm the GCP project still ends up with
the same set of resources as before.

## Phase 3: compose the dependency-ordered block (network → cluster → database)

These have cross-resource refs. Composition v2's `patches` on
the `function-patch-and-transform` function let us drive them
all from the XR's spec without templating the dependent fields
manually.

- [ ] **3.1** Add `Network` + 2× `Subnetwork` to Composition.
  Patches: region from `spec.parameters.region`. No cross-refs
  outside the Composition. Verify, then delete `network.yaml`.
- [ ] **3.2** Add `Cluster` + `NodePool` to Composition. Patches:
  - location from `spec.parameters.zone`
  - `workloadIdentityConfig.workloadPool` from
    `spec.parameters.projectId` (with the `.svc.id.goog` suffix
    via a string transform)
  - `nodeConfig.serviceAccount` from the gke-nodes SA email
    (string-transform from `spec.parameters.projectId` +
    `spec.parameters.gkeNodeSa`)
  - `ToCompositeFieldPath` patches: lift
    `Cluster.status.atProvider.endpoint` into
    `XR.status.clusterEndpoint`; the kubeconfig secretRef name
    into `XR.status.kubeconfigSecretRef`.
  - Verify, then delete `cluster.yaml`.
- [ ] **3.3** Add `DatabaseInstance` + `Database` + `User` to
  Composition. Patches: region from XR. `ToCompositeFieldPath`:
  lift `DatabaseInstance.status.atProvider.connectionName` into
  `XR.status.connectionName`. Verify, then delete `database.yaml`
  *minus* the password Secret (Secrets aren't MRs — keep that as
  a chart-rendered resource alongside the XR).

**Stop and review.** Tear down + bring up. The Composition now
owns 19 MRs; the chart renders `provider-configs.yaml` +
`xplatform.yaml` + the password Secret. Confirm CloudSQL still
exposes `connectionName` and the kubeconfig Secret still appears
where provider-helm expects it.

## Phase 4: switch the chart to render only the XR

The queenswood-gcp chart's only job becomes "render one XR
manifest" plus the inputs the Composition needs from outside
(the password Secret, plus the ClusterProviderConfig which
still lives outside the Composition because the composed MRs
reference it).

- [ ] **4.1** Replace `templates/provider-configs.yaml` content
  with nothing — ClusterProviderConfig is foundation, not a
  Composition output. It belongs higher up, in the `bootstrap`
  chart, alongside the providers it configures.
- [ ] **4.2** Create `infra/helm/bootstrap/templates/cluster-provider-config.yaml`
  rendering the v2 `ClusterProviderConfig.default` from
  `bootstrap` chart values. This means the `bootstrap` chart's
  `gcpProjectId` now flows into the ClusterProviderConfig
  directly, not through queenswood-gcp's helm parameters.
- [ ] **4.3** Update queenswood-gcp chart to render just:
  - `xplatform.yaml` — one `XPlatform` XR in `crossplane-system`
    with `spec.parameters.projectId` from `.Values.gcpProjectId`.
  - `database-password.yaml` — the static Secret the
    Composition's User MR references.
- [ ] **4.4** Reconfirm the bootstrap chart's
  `helm.parameters.gcpProjectId` is set from root-app.

**Stop and review.** The chart should now be ~30 lines of
templates instead of ~250. `gcp-up` should look identical
externally but Argo's view of queenswood-gcp shows just one XR
(plus the Secret and the ClusterProviderConfig).

## Phase 5: MRAP — pin only the kinds we actually use

`provider-family-gcp` registers hundreds of CRDs. v2 ships a
default `ManagedResourceActivationPolicy` that activates `*`.
Pinning to just what we need reduces API-server memory and
controller reconcile load.

- [ ] **5.1** Inventory every kind referenced by the
  Composition + by the existing XQueenswoodApex /
  XQueenswoodCertificate compositions + by anything in
  `infra/platform/crossplane-configs/`. Should be ~16 kinds.
- [ ] **5.2** Add `infra/platform/crossplane-providers/mrap.yml`
  with a `ManagedResourceActivationPolicy` listing the
  inventory. Verify the existing providers App syncs it.
- [ ] **5.3** Confirm no MRs go missing after a full
  `gcp-down`/`gcp-up`.

**Stop and review.** Final state.

## Phase 6: docs + recipe sweep

- [ ] **6.1** Rewrite the queenswood-gcp section of
  `docs/tdd/infrastructure.md` to describe the Composition shape
  (XR + composed MRs) rather than the flat-MRs shape.
- [ ] **6.2** Update `gcp-cloudsql-wire` recipe to read
  `connectionName` from the XR (`xplatform.platform.queenswood.repldriven.com/...status.connectionName`)
  instead of the DatabaseInstance directly. Cleaner — the XR is
  the public surface, the composed MRs are implementation.
- [ ] **6.3** Update `gcp-health-check` recipe likewise where
  it currently queries individual MRs.
- [ ] **6.4** Add an Operational note covering the MRAP
  inventory + how to extend it when a new MR kind is added.

## Open questions

- **Where does the XR live?** v2 XRs are namespaced. Putting it
  in `crossplane-system` keeps it adjacent to its composed MRs.
  But it could also live in `queenswood-test` (the env namespace)
  alongside `XQueenswoodApex` etc. I lean `crossplane-system`
  because XPlatform is infrastructure-layer, not env-layer —
  but worth deciding.
- **ProjectID via Observe-only Project MR?** The user's
  earlier note mentioned the v2 idiom of *observing* a GCP
  project via Crossplane and patching `projectId` from the
  Project MR's `status.atProvider.projectID`. That eliminates
  the `gcpProjectId` parameter entirely — but the Project MR's
  own `metadata.name` (or `crossplane.io/external-name`
  annotation) still has to carry the project ID, so it's just
  moved the single imperative substitution one level. Not
  recommended for this PR; revisit if we add multi-project
  multi-tenancy.
- **Connection-secret name in Composition.** When the Cluster
  MR is composed, its `writeConnectionSecretToRef` needs to
  land somewhere provider-helm can find. Composition v2 has
  per-resource connection-detail propagation up to the XR,
  which then writes a single Secret at the XR level. Worth
  using — collapses two Secrets (kubeconfig + db-creds) into
  one XR-level output if desired.
- **MRAP scope.** Should MRAP cover *every* group's kinds or
  only the v2 `.m.` ones we actually use? v1 kinds still
  register their CRDs; an MRAP that omits them deactivates the
  controllers. Best to enumerate both to be explicit.

## Out of scope

- `XDataPlane` (FDB + Pulsar via operator CRDs in the
  Composition) — separate PR, depends on operator-CRD
  availability strategy.
- `XBankInstance` (per-tenant slice) — multi-tenant work,
  separate epic.
- Operations (alpha) for FDB backups + cert rotation —
  watch upstream graduation first.
- Migration of `XQueenswoodApex` / `XQueenswoodCertificate`
  XRs into XPlatform. They're env-layer (DNS + cert tied to
  domain), keep them separate.
