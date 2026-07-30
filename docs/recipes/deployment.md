# Deployment
<!-- tessl-plugin: deployment -->

## Problem

You want to deploy a Queenswood service to Kubernetes —
build the image, render the chart, install, and (during
development) iterate quickly on a local kind
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
(`api-service`,
`clearbank-{adapter,simulator}-service`,
`onfido-{adapter,simulator}-service`). Message-bus
processors are grouped along the financial boundary into
`financial-processors-service` and
`operational-processors-service`, with the Quartz
singleton in `scheduler-processor-service` — see
[ADR-0019](../adr/0019-processor-packaging.md). The two
one-shots are `migrator-service` and
`bootstrap-service`.

### The chart

`infra/helm/queenswood` is one chart that deploys the whole
platform:

- One `Deployment` + (if HTTP) one `Service` per entry in
  `values.yaml :services`.
- A `FoundationDBCluster` CR (handled by the vendored
  `fdb-operator` subchart at
  `infra/helm/queenswood/charts/fdb-operator`).
- A single-broker Kafka Deployment (KRaft, dev-grade; not
  production-ready as shipped — set `kafka.enabled=false`
  and point at an external broker for production).
- Two one-shot Jobs that gate the rest of the system:
  `migrator` and `bootstrap`.

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

- **`migrator-service`** — opens FDB and applies
  record metadata via the FDB YAML in `resources`;
  creates the Kafka topics via the kafka-bootstrap YAML
  in the same component. Idempotent: skips
  already-existing topics, treats FDB
  "meta-data version must increase" as a no-op. Exits
  non-zero if topic creation fails.
- **`bootstrap-service`** — runs after the migrator
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
# Once per builder, and again after a `docker buildx prune`:
just docker-warm-cache

# One service:
just docker-build api-service dev

# Every service in parallel via docker buildx bake (shares
# the Clojure base layer across targets — much faster than
# the per-image loop):
just docker-build-all dev
```

`bake` targets are declared in `infra/docker/bake.hcl`.

`docker-warm-cache` copies the host `~/.m2` into the
BuildKit cache mount the build reads. The two are unrelated:
a `type=cache` mount is a volume inside the builder VM, with
no bind to the host repo, so a fresh or pruned builder
re-resolves every artifact from Maven Central and Clojars
however many times the same coordinates have been resolved
locally. Skipping the seed doesn't fail the build — it makes
it slow, and it makes it fail on any transient DNS blip,
because a cold cache has nothing to fall back on. BuildKit
also discards a failed step's cache-mount writes, so one
blip mid-download throws away everything fetched so far and
the next attempt starts cold again.

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
- Deploy flows use the same Helm release name
  (`queenswood`) so resource names don't diverge between the two
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
  flow. The internal organisation, the Kafka topics,
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
- A service set `replicas > 1` in `values.yaml`.
  `relay-service` must stay at 1 — it owns every changelog
  cursor, and a cursor has exactly one owner. Scale that
  tier by sharding stores across deployments, not by
  adding replicas. Other services are free of the cursor
  constraint, but raising their replicas buys standbys
  rather than throughput until `message-bus/send` carries
  a partition key and topics have more than one partition
  (per ADR-0021).

## Discussion

Processors were originally one project each; at current
volume that meant ten under-utilised JVMs, so they are
packaged into boundary groups instead, per
[ADR-0019](../adr/0019-processor-packaging.md). Each
processor still owns its Kafka consumer group and changelog
cursors — the group a processor runs in is deployment-time
YAML composition, so a domain that develops its own scaling
profile can be promoted back to a dedicated deployment
without touching code.

The shared parameterised Dockerfile is what makes the
per-service split tolerable. Without it, every deployable
would mean another Dockerfile to maintain.
With `PROJECT_NAME` as a build-arg and the Clojure base
layer shared across `buildx bake` targets, the marginal
cost of adding a service is the project, the base, and the
chart entry — not a fresh image-build pipeline.

More of that Dockerfile is shared than the base image, but
only because the stages are split to make it so. The `deps`
stage — base image, code-gen tooling, `COPY`, prep, dependency
resolution — must never reference `PROJECT_NAME`. A build arg
declared in a stage joins the cache key of everything after
it, so naming it there gives all fifteen targets a distinct
key for byte-identical work, and they each run it rather than
sharing one result. The arg first appears in a thin `build`
stage layered on top, whose only job is the uberjar. That
split is what the two cache-mount sharing modes track. The shared
step is the only writer and holds `sharing=locked`, which
costs nothing because one build holds it rather than
fifteen. The per-service step takes `sharing=shared`, so the
uberjars actually run in parallel — locking there would
serialise them behind one mutex and give back most of what
`bake` buys. Prefetching each project's `:build` deps with
`clojure -P` inside the locked step is what makes that safe:
it leaves the parallel steps with reads only, and Maven
Resolver's concurrency problem needs a writer to bite.

The migrator/bootstrap split is about ownership and
re-runnability:

- The migrator handles **platform metadata** — FDB record
  types, Kafka topics, Avro schemas. These are
  schema-shaped and rarely change. Re-running the migrator
  is treated as authoritative.
- The bootstrap handles **tenant data** — the singleton
  internal organisation and the platform-tier policies.
  These are domain-shaped, idempotent on the singleton,
  and don't change often.

Conflating them would mean a bootstrap that has to know
about Kafka topics, or a migrator that knows what a
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
in-place updates on edit-the-code aren't possible. 
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
- [ADR-0021](../adr/0021-changelog-relay.md) — the
  scaling caveat on changelog cursors.
- `infra/helm/queenswood/README.md` — chart user guide:
  `helm install`, `kind create`, port-forward, verifying.
- `infra/docker/service/Dockerfile` — the shared service
  image.
- `Justfile` — the `docker-build*`, `helm-*`, and
  `kind-*` recipes.
