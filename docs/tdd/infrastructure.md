# Infrastructure

## Objective

Spin Queenswood up from a fresh GCP project to a fully working
GKE deployment with one command (`just gcp-up`), and tear
it back down with another (`just gcp-down`). Drive every
cloud resource through Crossplane on a small management-plane
cluster, with Argo CD as the GitOps front-end — see
[ADR-0016](../adr/0016-crossplane-over-terraform.md) for the
decision rationale.

In scope: how the management plane and the workload plane are
organised, the bootstrap chain (what creates what, in what
order), the Composites we own, the patterns we keep
re-applying.

Out of scope: building service images and iterating on a local
kind cluster — see [recipes/deployment.md](../recipes/deployment.md).
The Polylith brick layout for the application itself — see the
per-capability TDDs.

## Two clusters

The deployment splits across two Kubernetes clusters:

```mermaid
flowchart LR
  subgraph mgmt["xp-mp (local kind)"]
    argo[Argo CD]
    xp[Crossplane]
    providers["provider-gcp-*<br/>provider-helm"]
    xrds["XRDs + Compositions<br/>(XQueenswoodApex,<br/>XQueenswoodCertificate)"]
    db["CloudSQL MRs<br/>(infra/gcp/database/)"]
  end
  subgraph gke["queenswood-gke (GKE)"]
    qw[queenswood chart<br/>bank-api, processors,<br/>frontend, Pulsar, FDB]
    kc[queenswood-keycloak<br/>Keycloak CR + proxy]
    kco[keycloak-operator chart]
  end
  subgraph gcp["GCP project"]
    vpc[VPC + subnets]
    cluster[GKE cluster]
    ip[Static IP]
    dns[DNS zone + records]
    cert[Cert + DNSAuth]
    sql[CloudSQL Postgres]
  end
  argo --> xrds
  xp --> providers
  providers --> vpc
  providers --> cluster
  providers --> ip
  providers --> dns
  providers --> cert
  providers --> sql
  providers --> qw
  providers --> kc
  providers --> kco
```

- **`xp-mp` (management plane)** is a local kind cluster. It
  carries Argo CD, Crossplane, the GCP provider family
  (`provider-gcp-{compute,container,sql,dns,certificatemanager,
  cloudplatform}`), and `provider-helm`. Nothing application
  -level runs here.
- **`queenswood-gke` (workload plane)** is the actual GKE
  cluster. Every workload — banking services, frontend, Keycloak,
  the cloud-sql-proxy — lives here. Crossplane on `xp-mp`
  reaches into it via a `provider-helm` ProviderConfig
  (`queenswood-gke`) authenticated by a Workload-Identity-bound
  service account.

The split keeps GCP-state reconciliation isolated from the
workloads. We can destroy the workload cluster without losing
the orchestrator that knows how to rebuild it.

## Bootstrap chain

`just gcp-up` does the cold-start sequence. Once kind is up
and Argo's `root-app` is applied, the apps in
`infra/bootstrap/apps/` reconcile in this order:

| Sync wave | App                  | What it lands                                            |
| --------: | -------------------- | -------------------------------------------------------- |
|         0 | crossplane-providers | The provider Packages (gcp-* + helm)                     |
|         1 | crossplane-configs   | ProviderConfigs binding providers to GCP SA / kubeconfig |
|         1 | crossplane-xrds      | XRDs + Compositions (see [Composites](#composites))      |
|         1 | gcp-services         | `Service` MRs enabling project-scoped GCP APIs           |
|         1 | gcp-roles            | `ProjectIAMMember` MRs binding workload roles to the SA  |
|         1 | gcp-identity         | `ServiceAccount` + WI binding for the keycloak SQL proxy |
|         2 | gcp-network          | VPC, subnets (managed resources)                         |
|         3 | gcp-cluster          | GKE cluster + node pool                                  |
|         3 | gcp-database         | CloudSQL Postgres (DatabaseInstance + Database + User)   |
|         4 | queenswood-platform  | The platform Helm chart — everything else                |

Within `queenswood-platform` itself, per-resource sync-waves take
over:

| Wave | Resource (in `queenswood-platform`)                        |
| ---: | ---------------------------------------------------------- |
|    3 | Namespace + `keycloak-operator` Release                    |
|    4 | DNS zone                                                   |
|    5 | Cert (XQueenswoodCertificate)                              |
|    6 | Apex DNS records (XQueenswoodApex), `queenswood-keycloak` Release |
|    7 | `queenswood` Release (bank-api, processors, etc.)          |

The waves let Crossplane start expensive long-running work
(CloudSQL provisioning takes 5–10 min, cert validation can take
minutes) as early as possible, in parallel with everything else
spinning up.

## Composites

`infra/platform/crossplane-xrds/` owns three composites. Each is
a thin patch-and-transform pipeline — no KCL, no Go — that lets
us model an end-to-end concept as one CR.

### `XQueenswoodCertificate`

Bundles the `DnsAuthorization` + `Certificate` resources Google
needs for a managed TLS certificate. One XR per environment.

### `XQueenswoodApex`

Wraps the static `Address` + the DNS `RecordSet`(s) that point
the apex hostname (`<env>.repldriven.com`) and Keycloak
subdomain (`keycloak.<env>.repldriven.com`) at it. Both records
share the same managed IP — they're patched from the
`Address.status.atProvider.address` field that the composite
lifts onto `XR.status.ip`.

We *don't* put the Keycloak subdomain on the
`queenswood-keycloak` workload chart, because `RecordSet` is a
Crossplane Managed Resource — its CRD only exists on the
management plane, not on the GKE target. Keeping all DNS in the
apex composite avoids the cross-cluster-CRD trap.

## CloudSQL Postgres (flat MRs)

`infra/gcp/database/database.yml` provisions the Keycloak database
as three flat Managed Resources — same shape as `infra/gcp/network/`
and `infra/gcp/cluster/`:

- `DatabaseInstance` (`sql.gcp.upbound.io/v1beta2`) — the
  instance itself. ENTERPRISE edition + PD_HDD + `db-custom-1-3840`,
  the cheapest legal Postgres combo (ENTERPRISE_PLUS rejects
  custom shapes; PD_SSD is overkill for the Keycloak workload).
- `Database` (`sql.gcp.upbound.io/v1beta1`) — the `keycloak`
  database inside the instance.
- `User` (`sql.gcp.upbound.io/v1beta1`) — the `keycloak` user,
  with `passwordSecretRef` pointing at the static Secret in
  `infra/gcp/database/password-secret.yaml`.

We use the v1 (cluster-scoped) `sql.gcp.upbound.io` API group
here rather than the v2 (namespaced) `sql.gcp.m.upbound.io` —
keeps the layout symmetric with VPC + cluster, and we lost
nothing by dropping the per-env composite (only one instance per
project anyway).

`status.atProvider.connectionName` on the DatabaseInstance is what
the `gcp-cloudsql-wire` recipe waits on; once set,
it pins the connection name + Workload-Identity-bound GCP SA email
into `queenswood-platform/values.yaml` for the workload-side
cloud-sql-proxy.

## Workload deployment via Crossplane Releases

`queenswood-platform/templates/` renders three
`helm.crossplane.io/Release` resources, each targeting the
`queenswood-gke` ProviderConfig:

- **`keycloak-operator`** (sync-wave 3) — the vendored upstream
  operator wrapped in a thin chart (`infra/helm/keycloak-operator/`).
  CRDs in `crds/` so Helm installs them ahead of templates and
  skips template rendering. The `JOSDK_ALL_NAMESPACES` env var
  is the operator-sdk sentinel for cluster-wide watch (not
  `JOSDK_WATCH_ALL_NAMESPACES`, which some upstream docs cite
  and which Quarkus silently treats as a literal namespace
  name).
- **`queenswood-keycloak`** (sync-wave 6) — emits the `Keycloak`
  CR + `KeycloakRealmImport` + `HTTPRoute` + the cloud-sql-proxy
  Deployment. Renders its own DB-credentials Secret on GKE
  (chart-rendered, not Crossplane-synced cross-cluster — see
  the *DB credentials* section below).
- **`queenswood`** (sync-wave 7) — the application itself.

The Releases all pull OCI artifacts from
`ghcr.io/repldriven/<chart>`. The `Release Chart` GitHub Actions
workflow packages and pushes all three on `workflow_dispatch`.

## Keycloak topology

Two URLs come into play:

- **Public URL** — `https://keycloak.<env-domain>` — what the
  Keycloak CR's `hostname.hostname` is set to. Embedded as the
  `iss` claim in every JWT Keycloak mints. Routed via the
  shared `queenswood-gateway` HTTPRoute and the apex composite's
  DNS record.
- **In-cluster Service URL** —
  `http://queenswood-keycloak-keycloak-service:8080` — what
  bank-api uses as `base-url` for the admin REST API and token
  exchange. No LB round-trip; no `hostname-strict` constraint
  to worry about.

The mismatch (tokens have `iss=public`, bank-api calls
`base-url=internal`) is bridged by the keycloak component's
`:expected-issuer` config, which overrides the default
base-url-derived iss the verifier expects. See
`components/keycloak/src/com/repldriven/mono/keycloak/identity_provider.clj`.

This is the natural deployed shape: public for what users see,
internal for what services see each other through. Envoy egress
or a kube-dns rewrite to fold both into one URL is a possible
future evolution; the `expected-issuer` split keeps the doors
open without forcing it.

### DB credentials

The CloudSQL instance has a single `keycloak` user. The
credentials need to land on GKE (where Keycloak runs), but the
User MR lives on the management plane.

We don't sync the Secret across clusters. Instead, the
`queenswood-keycloak` chart renders its own
`queenswood-keycloak-conn` Secret on GKE, populated from literal
values in the `queenswood-platform` Release template. The same
literal lives in `infra/gcp/database/password-secret.yaml`, which
the User MR's `passwordSecretRef` consumes. Both sides share a
single source-of-truth at deploy time; live rotations need both
files updated. Cross-cluster Secret sync would require installing
`provider-kubernetes` pointed at GKE just for this credential —
not worth the complexity.

### Cloud SQL Auth Proxy

A `cloud-sql-proxy` Deployment in the workload namespace fronts
CloudSQL. It authenticates to GCP via Workload Identity: a K8s
ServiceAccount annotated with
`iam.gke.io/gcp-service-account: keycloak-sql-proxy@<project>.iam`,
bound to a GCP SA that has `roles/cloudsql.client`.

The SA name is shortened to `keycloak-sql-proxy` (18 chars) to
fit GCP IAM's 30-char `account-id` limit;
`queenswood-keycloak-cloudsql-proxy` (33 chars) was rejected at
SA-create time.

The proxy's `connectionName` and the GCP SA email get pinned
into `queenswood-platform/values.yaml` by
`just gcp-cloudsql-wire` once Crossplane has
published `XR.status.connectionName`. The pin is committed +
pushed so the next reconcile of `queenswood-platform` picks up
the values.

## Down-to-zero

`just gcp-down` is the reverse path. The non-obvious step is
the first one:

1. **Drain workload namespaces on GKE.** `kubectl delete ns
   queenswood-test --wait=true --timeout=5m` *before* killing
   kind. The GKE CSI driver only runs `DeleteDisk` while the
   cluster (and Crossplane managing it from kind) is alive; if
   we destroy GKE with PVCs still bound, the underlying PDs
   leak as orphan disks in `europe-west2`. Two
   `gcp-up/gcp-down` cycles of the FDB + Pulsar
   StatefulSets is enough to exhaust the default 250 GiB
   regional SSD_TOTAL_GB quota and block the next cold start
   on a pending PVC.
2. Kill kind. Crossplane stops reconciling.
3. Delete the GKE cluster.
4. Delete the static IP, certificate, DNS records, VPC.
5. Prune the local kubeconfig context.

The DNS zone itself is kept (the `dnsName` claim is sticky for
30 days after a delete); `just gcp-project-delete` is the
nuclear option that also drops the zone and the project.

## Operational notes

A few patterns that recur in this codebase, kept here so
they're discoverable from the topic router rather than
scattered across commit messages:

- **Squash-merges + a long-running feature branch don't mix
  cleanly.** When PRs from the branch are squashed onto main,
  the original commits on the branch retain their old SHAs and
  later rebases conflict on the squashed range. The fix is to
  `git reset --hard origin/main` and cherry-pick the *new*
  commits — the squash captures the merged content correctly,
  the cherry-pick replays only what isn't on main yet.
- **GKE NEG-readiness gate.** Pods behind a Gateway-routed
  Service get a `cloud.google.com/load-balancer-neg-ready`
  readiness gate. Until the LB's backend health check passes,
  the pod's `Ready` condition stays False and kube-proxy won't
  route to it. If the LB's default `/`-expects-200 health check
  doesn't match the pod (Keycloak returns 302 from `/`), the
  gate stays unfulfilled for ~10 min until GKE times out and
  forces it True. A `HealthCheckPolicy` pointing at a real
  health endpoint (Keycloak's `/health/ready` on port 9000)
  fixes it cleanly, but only when the Service is referenced by
  a Gateway-attached HTTPRoute that the policy can attach to.
- **Chart version bumps trigger reinstall, value-only changes
  don't.** Crossplane provider-helm reconciles Releases on
  spec drift only — a values-only change to a chart doesn't
  trigger a re-install. Bumping the chart version (even
  patch-level) is the reliable trigger.

## Out of scope

- The local-kind / Tilt dev loop —
  [recipes/deployment.md](../recipes/deployment.md).
- Per-service image build pipeline — `infra/docker/service/`
  shared Dockerfile, `release-images.yml` workflow.
- Realm content (clients, scopes, audience mappers) —
  `components/bank-resources/resources/bank/keycloak-realm.json`
  and its testcontainer-fixture sibling under
  `components/bank-test-resources/`.
