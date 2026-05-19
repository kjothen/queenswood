# Deployment

## Problem

You want to deploy a Queenswood service to Kubernetes —
build the image, render the chart, install, and (during
development) iterate quickly under Tilt on a local kind
cluster.

## Solution

Each service is a Polylith project that produces a uberjar
via a shared parameterised Dockerfile. A single Helm chart
at `infra/helm/queenswood` deploys every service plus the
two one-shot Jobs that bring the system up from cold.

### The deployable shape

A deployable service is a triple:

- **Project** at `projects/<name>-service/` — pure config:
  `deps.edn` listing components and bases as `:local/root`,
  plus `resources/` for `application.yml` and logback
  config. No Clojure source, no `-main`. See
  [projects.md](projects.md).
- **Base** at `bases/<name>/` — owns `main.clj` and
  `(:gen-class)`. The project's `:build` alias points at
  the base's `-main` entry. See [bases.md](bases.md).
- **Image** built from the shared Dockerfile at
  `infra/docker/service/Dockerfile` with a `PROJECT_NAME`
  build-arg; the build stage runs `clojure -X:build uber`
  for that project, the runtime stage adds `libfdb_c.so`
  and runs the uberjar with
  `-c classpath:application.yml -p default`.

Naming: HTTP services keep their bare name
(`bank-api-service`,
`bank-clearbank-{adapter,simulator}-service`,
`bank-onfido-{adapter,simulator}-service`). Pulsar
processors are `bank-<domain>-processor-service`. The two
one-shots are `bank-migrator-service` and
`bank-bootstrap-service`.

### The chart

`infra/helm/queenswood` is one chart that deploys the whole
platform:

- One `Deployment` + (if HTTP) one `Service` per entry in
  `values.yaml :services`.
- A `FoundationDBCluster` CR (handled by the vendored
  `fdb-operator` subchart at
  `infra/helm/queenswood/charts/fdb-operator`).
- A Pulsar subchart (dev-grade single-replica config; not
  production-ready as shipped).
- Two one-shot Jobs that gate the rest of the system:
  `bank-migrator` and `bank-bootstrap`.

Each pod's spec carries an `initContainers` chain:

1. **`wait-for-fdb-cluster`** — polls the K8s API for the
   FDB-operator-written cluster-file ConfigMap, writes it
   into an `emptyDir` mounted at `/etc/fdb/fdb.cluster`.
   Polls via direct `curl` against the K8s API rather than
   `kubectl` in a loop, because each `kubectl` invocation
   opens an fsnotify watcher and dozens of pods polling
   exhausts the node's `fs.inotify.max_user_instances`.
2. **`wait-for-bootstrap`** — `kubectl wait
   --for=condition=complete` against the bootstrap Job. The
   Job's name embeds `image.tag`, so a `helm upgrade` with a
   new tag spawns a fresh Job.
3. Optional **`wait-for-<dep>`** — polls a sibling service's
   `/actuator/health/liveness` endpoint, used by adapters
   that need to register webhooks against their simulator
   on startup.

### Migrator and bootstrap

These two Jobs split the cold-start work along two axes —
metadata vs data, and platform-wide vs tenant-specific:

- **`bank-migrator-service`** — opens FDB and applies
  record metadata via the FDB YAML in `bank-resources`;
  declares the Pulsar tenant, namespace, topics, and
  schemas via the Pulsar YAML in the same component.
  Idempotent: skips
  already-created Pulsar topology, treats FDB
  "meta-data version must increase" as a no-op. Exits
  non-zero with `:pulsar/topics-audit` if any declared
  topic is missing after creation.
- **`bank-bootstrap-service`** — runs after the migrator
  completes; idempotently seeds the singleton internal
  Queenswood organisation and the platform/micro Policy
  records. Services discover the seeded organisation by
  reading FDB at startup.

K8s Job specs are immutable. Re-running bootstrap on code
change requires deleting the old Job; embedding
`image.tag` in the Job name means each `helm upgrade`
spawns a fresh one. Old Jobs age out via
`ttlSecondsAfterFinished: 86400` so completed pods stick
around for log inspection.

### Building images

```bash
# One service:
just docker-build bank-api-service dev

# Every service in parallel via docker buildx bake (shares
# the Clojure base layer across targets — much faster than
# the per-image loop):
just docker-build-all dev
```

`bake` targets are declared in `infra/docker/bake.hcl`.

### Deploying to a remote cluster

```bash
just helm-install dev
```

### kind end-to-end

```bash
just kind-up dev      # create cluster, build all images,
                      # load into kind, install chart
just kind-down        # tear it all down
```

`kind-up` does the full chain: creates the cluster if
missing, builds every service image, loads each into the
kind node's containerd, then `helm-install`s the chart.

### Tilt dev loop

```bash
just tilt-up          # creates kind cluster if needed,
                      # then `tilt up`
just tilt-down        # tears down Tilt's resources;
                      # leaves the kind cluster running
just tilt-prune       # nukes accumulated tilt-built images
```

The `Tiltfile` builds every service from the same shared
Dockerfile, renders the chart with `helm()`, applies it,
and groups resources into `bootstrap` / `http` /
`processors` for the Tilt UI. HTTP-fronted services
port-forward to their declared ports
(8080–8084).

Tilt rewrites every rebuild's manifest to point at a fresh
`:tilt-<hex>` tag. These accumulate on the host Docker
daemon and inside the kind node's containerd — `tilt-prune`
nukes both. Safe vs your `:dev` and version tags; the glob
only matches `*:tilt-*`.

A `bootstrap-reset` button in the Tilt UI deletes the
existing bootstrap Job so Tilt can re-apply a fresh one
after a code change.

## Rules

**MUST:**

- Every deployable project under `projects/*-service/` is
  pure config (`deps.edn` + `resources/`); the
  corresponding base owns `main.clj`. See
  [projects.md](projects.md) and [bases.md](bases.md).
- Service images are built from
  `infra/docker/service/Dockerfile` with a `PROJECT_NAME`
  build-arg.
- Service Deployments wait on the bootstrap Job via the
  `wait-for-bootstrap` initContainer before starting their
  main container.
- Tilt and Justfile flows use the same Helm release name
  (`bank`) so resource names don't diverge between the two
  flows.
- Cross-pod startup dependencies (e.g. an adapter waiting
  for its simulator) are expressed via the deployment's
  `waitFor` list, which adds a `wait-for-<dep>`
  initContainer polling the target's
  `/actuator/health/liveness`.

**MUST NOT:**

- Bake environment names (`prod`, `dev`) into resource
  names. Discriminate environments via `values.yaml`
  overrides and env vars; resource names should be
  environment-agnostic. See the no-env-in-resource-names
  memory.
- Skip the migrator or bootstrap Jobs in any deployment
  flow. The internal organisation, the Pulsar topology,
  and the FDB metadata are all preconditions to any
  service's startup.
- Build a service from anything other than the shared
  Dockerfile. The arch-aware `libfdb_c.so` install and the
  shared base layer are the parts that need to stay
  consistent across services.

**MAY:**

- A project under `projects/*-service/` carry a
  service-specific `application.yml` in `resources/` (the
  Dockerfile loads it via `-c classpath:application.yml -p
  default`).
- A service set `replicas > 1` in `values.yaml` —
  HTTP-fronted services and processors that don't depend
  on changelog watchers scale freely. Watchers and
  websocket-style consumers don't horizontally scale today
  without leader election (per ADR-0008).

## Discussion

The split into per-processor projects is a consequence of
operational independence. Each processor — party,
cash-account, payment, interest, transaction, idv — owns
its Pulsar consumer group and its own scaling profile. A
spike in inbound payments shouldn't require restarting
the interest accrual processor. One project per processor
also keeps the deployable's deps tight: the cash-account
processor doesn't ship the IDV processor's Avro schemas,
adapter HTTP client, or watcher wiring.

The shared parameterised Dockerfile is what makes the
per-service split tolerable. Without it, every deployable
would mean another Dockerfile to maintain.
With `PROJECT_NAME` as a build-arg and the Clojure base
layer shared across `buildx bake` targets, the marginal
cost of adding a service is the project, the base, and the
chart entry — not a fresh image-build pipeline.

The migrator/bootstrap split is about ownership and
re-runnability:

- The migrator handles **platform metadata** — FDB record
  types, Pulsar topics, Avro schemas. These are
  schema-shaped and rarely change. Re-running the migrator
  is treated as authoritative.
- The bootstrap handles **tenant data** — the singleton
  internal organisation and the platform-tier policies.
  These are domain-shaped, idempotent on the singleton,
  and don't change often.

Conflating them would mean a bootstrap that has to know
about Pulsar schemas, or a migrator that knows what a
Policy is. The split keeps each Job's concern narrow.

The `wait-for-fdb-cluster` initContainer rolls its own
poll loop because of two Kubernetes-shaped costs.
`kubectl` in a tight loop opens an fsnotify watcher on its
kubeconfig per invocation, and a kind node running every
service pod polling at once exhausts
`fs.inotify.max_user_instances`. Direct API access via
`curl` plus the per-pod ServiceAccount token avoids both
costs and gives the same semantics.

The `bootstrap-reset` button is the ergonomic cost of K8s
Job spec immutability. We chose Jobs (not Deployments) for
the bootstrap because we want the
"runs-once-and-completes" semantics, but that means
in-place updates on edit-the-code aren't possible. The
manual-trigger Tilt resource papers over the rough edge.

## References

- [bases.md](bases.md) — base owns `main.clj`; the
  project depends on the base.
- [projects.md](projects.md) — projects are pure config;
  the per-service split.
- [system-configurations.md](system-configurations.md) —
  `application.yml` shape, profiles, env-var
  interpolation.
- [system-components.md](system-components.md) —
  bare-require pattern in `main.clj`.
- [ADR-0007](../adr/0007-system-as-data.md) — system-as-data
  via donut.system + YAML; the same bootstrap path runs
  under Testcontainers, kind, and production.
- [ADR-0008](../adr/0008-changelog-watchers.md) — the
  scaling caveat on changelog watchers.
- `infra/helm/queenswood/README.md` — chart user guide:
  `helm install`, `kind create`, port-forward, verifying.
- `Tiltfile` — the Tilt dev-loop entry point.
- `infra/docker/service/Dockerfile` — the shared service
  image.
- `Justfile` — the `docker-build*`, `helm-*`, `kind-*`,
  and `tilt-*` recipes.
