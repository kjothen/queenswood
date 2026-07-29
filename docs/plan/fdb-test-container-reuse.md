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
test rig, and treat per-run keyspace prefixes as a cost — each new
prefix is a directory-layer *write*, which is precisely the operation
that serialises. Prefer few, coarse prefixes over one per system.

## Stages

Each stage is independently valuable and independently revertable.

### 1. Namespace the keyspace

`keyspace/path` takes a store name and builds
`DirectoryLayerDirectory(name) → .path name name`. Add an optional root
prefix so a path becomes `<prefix>/<store-name>`, defaulting to no
prefix so production is untouched.

Thread it from config, not from call sites: the `store` and `meta-store`
components already build the open-fn that `fdb/open` calls, so the
prefix belongs in their config and the 104 `fdb/open` call sites do not
change.

### 2. Give each test rig a prefix

Set the prefix in `fdb-test.yml` from something stable per rig rather
than per system — the brick name is the natural choice, and it keeps the
directory-layer write count at ~22 for the suite instead of ~68.

At this point tests are isolated by keyspace and the container could be
shared, but nothing has changed operationally yet. Run the suite and
confirm it is still green with the prefix in place.

### 3. Pin the port and reuse

- Replace `free-port` with a fixed port, so the container's config hash
  is stable.
- Add `.withReuse(true)`.
- Stop calling `.stop` on a reused container.
- Carry `async-to-sync-timeout-ms: 30000` into `fdb-test.yml`.

The port race disappears here, because nothing ever binds a second
container.

### 4. Decide about cleanup

A reused container accumulates keyspaces across runs. Either clear the
prefix at system start, or accept the growth and let developers prune by
hand. Clearing at start is safer and costs one directory-layer delete
per rig.

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
