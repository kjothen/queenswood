# 26. Recovering data, and the states that do it

<!-- tessl-plugin: deployment -->

## Status

Proposed. Extends
[ADR-0022](0022-cloud-foundation-and-environment-lifecycle.md), which
made off a declared state and an instance's project durable, and
[ADR-0024](0024-instances-are-their-own-composites.md), which made an
instance its own composite. Nothing here is built.

It records a gap first and a direction second. The gap is real now:
FoundationDB is backed up continuously and there is no defined way to
use a backup.

## Context

The previous generation recovered data by destroying it. Its recipe
says so plainly — *the restore only runs against an empty cluster, so
the data has to go first* — and the two commands that did it,
`gcp-down` and `gcp-up`, tore an environment down and built it back.
The restore Job was then the thing that filled the empty cluster, which
is why it is a build-time gate: the migrator waits for it, bootstrap
waits for the migrator, and every service waits for bootstrap.

ADR-0022 removed the first half of that cycle on purpose. An instance's
project is durable, and `down` is *the node pool at zero* — the
cluster, the project, its data, the network and the identities all
stand. What stops is what costs money.

So the destructive act that the restore depended on no longer exists,
and nothing replaced it. Setting `fdb.restore.version` on a running
instance does nothing at all: the Job checks emptiness itself and
leaves a cluster that holds data alone. That check is right — it is
what stops a reconcile destroying live data — but it means the field
that names a restore point cannot cause a restore.

The result is an installation that takes backups faithfully and has no
procedure for reading one back. Backups nobody has restored are a
belief rather than a control, and the belief is cheapest to correct
before there is anything in the bank worth losing.

## Decision

**Down and rebuild are different kinds of state, and must not share a
word.**

`down` is reversible and preserves what it stops. Whatever replaces the
old destructive cycle is irreversible and destroys what it replaces.
Both are legitimately "a declared state" in the sense ADR-0022 means,
and putting them in one enum is how someone eventually recovers an
environment by selecting the wrong value from a list.

**Recovery has two shapes and they are for different situations.**

*In place.* Empty the data and let the restore fill it. It is what the
chart already supports and it needs `clusterAdmin`, which is right —
recovering from corruption is break-glass. It is correct when the
current data is worthless: a test environment, or a rebuild after total
loss.

*Beside it, then cut over.* Build a second instance, restore it to a
point before the damage, verify what it holds, move traffic. It is
heavier and needs a cutover story this model does not have.

**For corruption, restoring beside it is the one to build toward**, and
not for convenience. The corrupted data is evidence — the first act of
the in-place path destroys the only record of what happened and when.
And it is reversible until the cutover, where in place commits at the
moment the volumes go: a restore that comes back wrong, or to the wrong
point, has nothing behind it.

**A destructive state must be self-limiting.** The restore Job embeds
its version in its own name, so re-applying the same value resolves to
a Job that has already completed and the restore does not run twice.
Anything that empties data needs that property or something as strong.
A field meaning "destroy this and rebuild it" that stays true is a
field that destroys on every reconcile, which is the hazard ADR-0022
names from the other end — *a live plane watching its resources vanish
through a prune and doing what it was told.*

**States belong to parts, not only to instances.** `state: up | down`
is instance-wide, and the parts of an instance have independent
lifecycles: the data tier, the database behind Keycloak, the services.
Recovering data should not require declaring the whole environment off,
and the parts that can be stopped independently are the ones that can
be recovered independently.

## Consequences

**A restore point is a version, and nobody thinks in versions.**
`sop-fdb-list-backups` lists what exists, and FDB versions are
versionstamps rather than timestamps. *Restore to before yesterday
afternoon* has no answer today: mapping wall-clock to version needs
`fdbbackup describe`, which needs a pod, which needs a capability the
person holding the incident does not have. Whichever shape is built,
this is the part that decides whether it can be used under pressure.

**Cutting over is unbuilt, and larger than it looks.** DNS, the realm
the console signs into, the Keycloak user ids that FDB records
reference. ADR-0024 already defers `draining` for a related reason —
an unattended Keycloak restore is its precondition and is not met.

**Keycloak and FoundationDB recover to different points.** Restoring
one and not the other leaves a user who exists in the realm with no
party in the bank, and onboarding gives them a second one. The
`recovery-procedures` recipe records this from the previous generation
and it is unchanged.

**The in-place path stays available and stays undocumented until
someone writes it down.** It is what a test environment should use, and
testing it is how the first evidence arrives that a backup can be read
at all.

## References

- [ADR-0022](0022-cloud-foundation-and-environment-lifecycle.md) — off
  as a declared state, and why an instance's project is durable
- [ADR-0024](0024-instances-are-their-own-composites.md) — the instance
  as its own composite, and what `down` leaves standing
- [recovery-procedures](../recipes/recovery-procedures.md) — the
  previous generation's mechanics, accurate where they do not assume
  `QUEENSWOOD_ENV`
- [the plan](../plan/cloud-just-migration.md) — where the backup path
  was rebuilt
