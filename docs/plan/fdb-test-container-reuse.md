# Plan: reuse one FDB container across the test suite — not taken

This proposed moving test isolation from the container to the FDB
keyspace, so the suite started one FoundationDB container instead of 68.

**It is not being done.** A container per test system is the model, and
it stays. The analysis is kept because the cost it measured is real and
knowingly paid, and because the reasoning is what stops the idea coming
back on the strength of that cost alone.

## Why not

**The problem it was going to fix has been fixed at its cause.** The
plan was written when the port race and the reuse blocker were the same
problem, so fixing either meant fixing both — reuse was the way to
remove a race, not merely a speed-up. That is no longer true. The
container now takes a Docker-assigned host port and advertises it
separately from the one it binds, so nothing pre-binds a port, nothing
collides, and containers run side by side safely. Reuse would now buy
startup time and nothing else.

**Independent containers are easier to reason about.** The container
*is* the isolation boundary, and that is a boundary with no bookkeeping:
no shared keyspace, nothing to clean up, no way for one test's residue
to reach another, and no shared-state question to ask when something
fails. Reuse replaces it with isolation that has to be constructed and
then maintained — a generated keyspace per system, and a sweep to stop a
developer's FDB growing without bound. That is a permanent tax on
debugging in exchange for minutes.

**It needs a `mono` change to even start.** Keyspace isolation wanted a
read-time generating tag (`!uuidv7`, in the shape of the existing
`!port`) in `mono`'s env brick — so made there, released, and pulled
down by bumping the `deps/mono` and `deps/mono-test` shims in lockstep,
before any of the local work could begin.

**And it concentrates a failure mode already seen once.** `poly test
:all` runs ~14 JVMs, which under reuse would share one container.
`fdb.yml` in production carries `async-to-sync-timeout-ms: 30000` with
the note that a single-process FDB serialises the directory-layer
resolution `FDBMetaDataStore.<init>` performs, and 12+ services hitting
it concurrently at boot "routinely tipped past 5s and crashed pods".
Generating a keyspace per system adds ~68 directory-layer *writes* per
run — precisely the operation that serialises. The plan's own abandon
criteria named this as the thing most likely to stop it.

## What it cost to decline

Worth writing down, because it is the number that will tempt someone to
reopen this.

`with-test-system` starts and stops a system per invocation, with no
caching. There are 68 call sites across 23 namespaces, and 22 of the 25
test rigs include `fdb-test.yml`, so a full `project:dev :all` run
creates and destroys roughly 68 containers.

It shows in the timings: almost every FDB-touching test sits at a floor
of 3.5–4s regardless of what it asserts, against 25s / 43s / 47s for the
three genuinely heavy scenario tests. Around 60 tests pay ~4s of
container startup each — roughly four minutes of a five-minute run.

`TESTCONTAINERS_REUSE_ENABLE=TRUE` is exported from `flake.nix` and set
in CI. It permits reuse; nothing opts in, and nothing should. The
container is stopped on system stop, deliberately.

## What was done instead

The port is no longer pinned, and no longer pre-bound.

`--public-address` and `--listen-address` are independent, which is the
standard answer to NAT, and Docker bridge networking is a NAT. The
container binds 4500 — the only port the image exposes — and advertises
whatever host port Docker assigned, so one cluster file is valid on both
sides without the two numbers matching. Nothing chooses a port and hopes
it is still free when the container starts, which is what the old
`free-port` did and what made concurrent starts collide.

It costs two things, both small and both contained:

- **A two-phase start.** Docker only assigns the host port once the
  container is running, so the entrypoint waits to be told it rather
  than taking it from the environment.
- **A loopback bridge inside the container.** Everything in there reads
  the same cluster file and so re-dials the advertised port, including
  the `fdbcli` that runs `configure new single memory`. That port is the
  host's and nothing listens on it inside, so it is bridged back to the
  bound one.

The bridge was tested rather than assumed, twice. Without it: 22
failures. With it removed but the bootstrap `fdbcli` given its own
cluster file naming the *bound* port: the same 22 failures — the client
re-dials the advertised address whichever file it started from. Moving
the bootstrap to the host was preferred and is not available: the
`fdbcli` in the image is a Linux binary and the dev host is macOS, and
driving `configure new` through the Java client's special key space does
not work against a database that has not been created yet.

Verified by two consecutive clean `clojure -M:poly test :all` runs — 14
projects, ~14 JVMs starting containers simultaneously — against a
baseline that had been failing intermittently.

## What is still worth taking from this

- **Keyspace isolation is correct regardless**, and would be worth
  having if a reason other than container reuse ever calls for it. The
  `!uuidv7` reader tag it needs is the only part that has to happen in
  `mono`.
- **The ~4s floor is the price of the model.** If it ever stops being
  affordable, the honest options are fewer test systems or cheaper
  startup, not a shared container.
- `bases/monolith`'s rig is the dev loop, not a test — it has no
  `*_test.clj` — and gets a fresh container per start, which is wanted.
