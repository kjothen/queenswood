# Plan: reuse one FDB container across the test suite

Move test isolation from the container to the FDB keyspace, so the suite
starts one FoundationDB container instead of 68.

## The cost today

`with-test-system` starts and stops a system per invocation, with no
caching. There are 68 call sites across 23 namespaces, and 22 of the 25
test rigs include `fdb-test.yml`. So a full `project:dev :all` run
creates and destroys roughly 68 containers.

It shows in the timings: almost every FDB-touching test sits at a floor
of 3.5–4s regardless of what it asserts, against 25s / 43s / 47s for the
three genuinely heavy scenario tests. Around 60 tests are paying ~4s of
container startup each — roughly four minutes of a five-minute run.

## Reuse is configured but not happening

`TESTCONTAINERS_REUSE_ENABLE=TRUE` is exported from `flake.nix` and set
in CI, but nothing is reused. Three reasons, compounding:

- No `.withReuse(true)` on the container. The env var only *permits*
  reuse; the container must opt in.
- `:system/stop` calls `.stop`, which tears down a reused container.
- The port is random. Testcontainers identifies a reusable container by
  hashing its configuration, and `withFixedExposedPort(free-port,
  free-port)` changes that hash on every start, so no existing container
  could ever match.

Measured: the same brick run twice takes 4.78s and 4.73s, and no
container survives either run.

## Why it is built this way

Two constraints, both real.

**The fixed host port is load-bearing.** The cluster file is generated
inside the container and names the port FDB listens on. Under
Testcontainers' normal dynamic mapping the host port differs from the
container port, so a client outside reading that cluster file dials the
wrong one. Binding host==container is what makes one cluster file valid
on both sides.

**The container is the isolation boundary.** `fdb-test.yml` opens
`path: meta` with no per-system namespacing, and `keyspace/path` builds
a directory-layer path from the store name alone. Nothing distinguishes
one test system's `banks` store from another's. A fresh container per
system is what keeps 68 systems off each other.

The two are coupled: the random port both causes the race *and* prevents
the reuse that would remove it.

## Will one FDB be overloaded?

The honest answer is that within a single JVM it should see the same
load it sees now, and the risk is concentrated somewhere else.

The runner sets `:multithread? :namespaces`, so namespaces run in
parallel — but **all 23 namespaces that touch FDB are marked
`^:eftest/synchronized`**, which serialises them. One test system is
live at a time today, and that does not change under reuse. What changes
is that the container stops being destroyed and rebuilt between them.

Where it could bite is parallel *projects* — `poly test :all` runs ~14
JVMs, and they would share one container rather than having their own.
There is direct precedent for that hurting: `fdb.yml` in production
carries `async-to-sync-timeout-ms: 30000` with the note that a
single-process FDB serialises the directory-layer resolution
`FDBMetaDataStore.<init>` performs, and 12+ services hitting it
concurrently at boot "routinely tipped past 5s and crashed pods".

That is the same failure mode, from the same cause, already observed
once. Two consequences for this plan: carry the raised timeout into the
test rig, and keep an eye on the keyspace count. A UUIDv7 per system
start means ~68 directory-layer *writes* per run, and directory-layer
resolution is precisely what serialises. That is the price of the
strongest isolation, and it is the first thing to measure if stage 3
disappoints.

## Stages

Each stage is independently valuable and independently revertable.

### 1. A read-time unique-value tag in `mono`

The `meta-store` path is a plain `path: meta` in both `fdb.yml` and
`fdb-test.yml`. It becomes `!profile`-driven: production keeps `meta`,
and other profiles get `meta` plus a UUIDv7, generated fresh each time
the config is read.

That needs a new reader tag in `mono`'s env brick — `yml-reader` in
`components/env/src/.../reader/yml.clj`, alongside `!profile`, `!env`,
`!keyword` and the rest. There is an exact precedent for a tag that
*generates* rather than looks up: `!port`, which resolves `0` by binding
a socket at read time. A `!uuidv7` tag is the same shape in the same
place. It composes with the existing `!concat`, or produces the full
suffixed string itself.

A `mono` change under ADR-0001, so: made there, released, pulled down by
bumping the `deps/mono` and `deps/mono-test` shims in lockstep.

### 2. Point the test rig at it

`fdb-test.yml` sets the path per profile. Every `with-test-system` then
reads config afresh and lands in its own keyspace, so systems cannot see
each other regardless of which container they are on.

Two things to settle here:

- **`dev` is not `test`.** `just monolith-start` runs the *test* rig
  under the `dev` profile, so "every profile except production" would
  give the dev loop a new keyspace on every restart. Once the container
  is reused that means losing your data every time you restart the
  monolith, which is worse than today. `dev` probably wants a stable
  path and only `test` a generated one.
- **A fresh keyspace per system is ~68 directory-layer writes per run**,
  and that is the operation the production comment says serialises. It
  buys the strongest isolation available and is the right default, but
  it is the thing to watch first if stage 3 turns out slow.

At this point tests are isolated by keyspace and the container could be
shared, but nothing has changed operationally yet. Run the suite and
confirm it is still green.

### 3. Pin the port and reuse

- Replace `free-port` with a fixed port, so the container's config hash
  is stable.
- Add `.withReuse(true)`.
- Stop calling `.stop` on a reused container.
- Carry `async-to-sync-timeout-ms: 30000` into `fdb-test.yml`.

The port race disappears here, because nothing ever binds a second
container.

### 4. Clean up keyspaces

Not optional once the path carries a UUIDv7. Every system start mints a
keyspace that is never revisited, and the container now survives between
runs, so a developer's FDB grows without bound.

Either drop the keyspace on system stop — the natural place, and it
keeps the container's contents proportional to what is running — or
prune everything under the parent directory at start. Stop-time deletion
is tidier but is skipped when a test crashes, so a start-time sweep is
the backstop.

## Verification

- `clojure -M:poly test project:dev :all` green, and materially faster —
  the ~4s floor should collapse for the ~60 tests that only need FDB.
- `docker ps` shows exactly one FDB container, surviving between runs.
- Run two projects concurrently and confirm neither the port collision
  nor a directory-layer timeout appears.
- Run the suite twice in a row: the second run must be green against the
  container the first left behind. This is the stage-4 question, and
  failing it means keyspace cleanup is required rather than optional.

## Abandon criteria

If stage 3 produces directory-layer timeouts under parallel projects
that the raised timeout does not absorb, stop. Stages 1 and 2 are still
worth keeping — keyspace isolation is correct regardless — and the
container-per-system model can stay.

## Not in scope

- The `free-port` TOCTOU race has a cheap independent fix: retry
  `.start()` on "address already in use" with a fresh port. Worth doing
  on its own if this plan is not taken, but stage 3 deletes the random
  port entirely, so doing both means writing code to throw away.
- `bases/monolith`'s rig is the dev loop, not a test — it has no
  `*_test.clj`. It would pick up reuse for free but is not a target.
