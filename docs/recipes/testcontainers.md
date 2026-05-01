# Testcontainers

## Problem

You want to run brick infrastructure (FDB, Pulsar, Vault, and
so on) in tests, using the same configuration shape that
production uses.

## Solution

We treat testcontainers as a *source* of connection details —
mostly host and port, sometimes a cluster-file-path or similar
token — not as a parallel test-only runtime. The high-level
components that consume those details (the Pulsar client, the
FDB record store, the Vault client) see the same configuration
shape regardless of whether the source is a testcontainer or a
literal value in a production YAML.

This means the same brick interfaces work for both deployment
modes, and a `!profile` swap is enough to move between them.

### The three-layer pattern

A testcontainer-backed group has three layers, all declared in
YAML, wired by `!system/local-ref`:

1. **The container itself.** Started by the `testcontainers`
   brick. Builder-pattern setup methods (image name, env vars,
   startup timeout) happen at construction time.
2. **The extractor.** Lives in the *relevant* brick's `system/`
   folder. Reads runtime values (host, port, cluster-file-path)
   from the started container and exposes them.
3. **The high-level component.** The Pulsar client / FDB record
   store / Vault client / etc. Consumes the extracted values
   the same way production would consume them from a YAML
   literal or `!env`.

Adapted from `bases/bank-monolith/test-resources/bank/fdb-test.yml`:

```yaml
fdb:
  container: !system/component
    system/component-kind: fdb/container
    image-name: mono/foundationdb:7.3.75

  cluster-file-path: !system/component
    system/component-kind: fdb/cluster-file-path
    container: !system/local-ref container

  record-db: !system/component
    system/component-kind: fdb/record-db
    cluster-file-path: !system/local-ref cluster-file-path
```

`fdb/container` is declared by the `testcontainers` brick.
`fdb/cluster-file-path` is declared in
`components/fdb/.../system/` — the extractor that pulls the
cluster-file-path from the started container.
`fdb/record-db` is the production-shaped record-DB component;
it consumes a `cluster-file-path` value and doesn't know or
care that the value came from a testcontainer.

### Production analog

The same `fdb/record-db` component runs unchanged in production
— it just gets its `cluster-file-path` from a literal value
instead of an extractor. With profile-gating, both shapes live
in the same configuration:

```yaml
fdb: !profile
  default:                                # dev / test
    container: !system/component
      system/component-kind: fdb/container
    cluster-file-path: !system/component
      system/component-kind: fdb/cluster-file-path
      container: !system/local-ref container
    record-db: !system/component
      system/component-kind: fdb/record-db
      cluster-file-path: !system/local-ref cluster-file-path
  prod:
    record-db: !system/component
      system/component-kind: fdb/record-db
      cluster-file-path: /etc/fdb/fdb.cluster
```

`record-db` doesn't change shape. Neither does any consumer
referencing `fdb.record-db` from another group.

### Construction vs runtime

The `testcontainers` brick MAY call builder-pattern setup
methods during container *construction* (`.withVaultToken`,
`.addEnv`, `.withStartupTimeout`). It MUST NOT call library
methods on a *running* container instance to extract runtime
values.

If `testcontainers` reached into a started container to pull a
URL, it would acquire a hidden dependency on the container
library's API — coupling `testcontainers` to libraries it
shouldn't know about and breaking
[ADR-0011](../adr/0011-one-component-per-third-party-library.md).

Extraction goes in the relevant brick's `system/` folder
because that brick already legitimately depends on the library.
The `fdb` brick depends on the FDB Java client; extracting a
cluster-file-path is a natural extension of what it does.

## Rules

**MUST:**

- Testcontainer-backed infrastructure follows the three-layer
  pattern: container, extractor, high-level component.
- Extractor components — those that interrogate a running
  container — live in the relevant brick's `system/` folder.
- High-level components consume extracted values the same way
  they consume production literals; the consumer's shape does
  not vary by deployment.

**MUST NOT:**

- Call library methods on a *started* container instance from
  the `testcontainers` brick.
- Make high-level components testcontainer-aware. They should
  not branch on whether they're running against a container or
  a real cluster.

**MAY:**

- Call builder-pattern setup methods on a container instance
  during construction (before start).
- Profile-gate whole infrastructure groups so a single
  configuration serves dev/test and prod.

## Discussion

The orthogonality principle is what makes the rest of the
testing story work. If high-level components branched on "am
I in a test or in prod," every brick would have a test path
and a prod path that could quietly diverge. Forcing
testcontainers to look the same as a real deployment from the
consumer's perspective leaves the production code path as the
only code path; tests just feed it different connection
details.

The three-layer split is a direct consequence. The
`testcontainers` brick is generic — it knows nothing about
what's inside the container. The extractor is where library
knowledge lives, and it lives in the brick that already has
that library knowledge. The high-level component is library-
naïve and works the same in either world.

The `system/` folder pattern — described in
[components.md](components.md) and
[system-components.md](system-components.md) — exists
specifically for this kind of cross-cutting registration:
multiple component kinds related to the same library, one of
which interrogates running infrastructure.

The
[systems-as-data slides](../slides/systems-as-data/slides.md)
walk through the testcontainer construction in more detail.

## References

- [ADR-0007 — System-as-data](../adr/0007-system-as-data.md)
- [ADR-0011](../adr/0011-one-component-per-third-party-library.md) —
  One component per third-party library
- [components.md](components.md)
- [system-components.md](system-components.md)
- [system-configurations.md](system-configurations.md)
- [Systems-as-data slides](../slides/systems-as-data/slides.md)
