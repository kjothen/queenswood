# Rebuilding an instance's cluster

<!-- tessl-plugin: deployment -->

## Status

Not yet run. Every step below is derived from the compositions, the
chart and the provider's own behaviour rather than from having done it,
and the first person to follow it should correct it as they go. The
timings in particular are unknown: this is the procedure that produces
this installation's first RTO measurement, and its first restore.

## Problem

Some fields identify the composed `Cluster` rather than configure it —
`zone`, `region`, `datapathProvider`, and anything else the provider
treats as ForceNew. Changing one cannot be applied in place, and the
provider refuses rather than performing it: the composite goes on
reporting `Synced` and `Ready` while the cluster carries on unchanged,
and the refusal appears only in `LastAsyncOperation` on the managed
resource.

So the change needs the cluster deleted and recomposed, which destroys
the volumes under it and every record FoundationDB holds. Everything
else the instance is made of survives — the project, the database, the
address, the certificates, the DNS records, the secrets, and the
backups.

That makes this the `specific × new` cell of
[fdb-recovery](fdb-recovery.md): a planned cluster rebuild, where the
restore point is chosen rather than inherited.

## Solution

### What this is and is not

It rebuilds the **cluster**, not the instance. The instance keeps its
name, its project and everything addressable about it, so there is no
second instance, no cutover, and no second name to invent.

Rebuilding the *instance* is a different procedure — the project goes,
so Cloud SQL goes, so Keycloak must be recovered alongside FoundationDB
to points chosen together. Rebuilding the *installation* takes the
recovery project and the backups with it. Neither is written down.

### Before starting

Confirm the three things the rest depends on:

```bash
gcloud auth application-default login
just sop-fdb-list-backups <env> <label>
kubectl --context <code>-<env>-<label> -n queenswood get pods
```

The listing gives the current generation and the restorable span.
`gcloud auth login` on its own does not refresh application-default
credentials, and a `kubectl` call that asks to reauthenticate mid-way
through this is worth avoiding.

### 1. Quiesce writes

Stop anything that writes to FoundationDB. The restore point is only
as clean as the moment nothing is writing, and this is the one recovery
scenario where that is available.

### 2. Take the restore point

Let the mutation log catch up, then name the moment:

```bash
just sop-fdb-version-at <env> <label> <YYYY-MM-DDTHH:MM:SSZ>
```

Record the **version** and the **generation** it belongs to. A version
without its generation cannot be used: version numbers restart near
zero on a rebuild, so they only mean anything inside the container that
wrote them.

### 3. Set the restore, and open a new generation

In the instance's values, in the same merge:

- `fdb.restore.backupName` — the generation just recorded
- `fdb.restore.version` — the version just recorded
- `fdb.backup.backupName` — a **new** generation, not the one being
  restored from

The third is the one that is easy to miss and expensive to get wrong.
A rebuilt cluster numbers its versions from near zero, so if it writes
into the container it read from, `fdbbackup describe` starts answering
with the dead cluster's higher numbers.

### 4. Merge the change that forces the rebuild

The manifest or values edit that started this — the new `zone`,
`region` or `datapathProvider`. Merging it changes nothing yet, by
design.

### 5. Delete the managed cluster

Break-glass, and the point of no return:

```bash
kubectl --context <code>-mgmt -n crossplane-system \
  delete cluster.container.gcp.m.upbound.io <name>
```

Crossplane recomposes it from the composite. Expect the node pool to go
with it and be recomposed too, since a node pool cannot outlive its
cluster.

### 6. Wait for Argo to find the new cluster

The composite writes Argo's cluster registration from the cluster's own
reported endpoint and certificate authority, so there is a window where
the registration names an endpoint that no longer answers. It corrects
itself on the next reconcile once the new cluster reports; nothing
needs doing, but an Argo sync failing during that window is expected
rather than a fault.

### 7. Let the bring-up restore

Nothing further is needed. The restore Job renders because
`fdb.restore` is set, the migrator's `wait-for-restore` initContainer
gates on it, bootstrap gates on the migrator, and every service gates
on bootstrap. The destination is empty, so the Job restores rather than
taking its do-nothing branch.

### 8. Verify the restore, not the Job

```bash
kubectl --context <code>-<env>-<label> -n queenswood exec -it \
  deploy/queenswood-fdb-backup-agents -- \
  fdbrestore status --dest-cluster-file /etc/fdb/fdb.cluster
```

`State: completed` with `LastError: None`. Then count keys against what
was there before, and finally sign in through the console and confirm a
party resolves — Keycloak was never rebuilt, so a user whose party is
missing means the restore, not the realm.

### 9. Record what happened

This procedure exists partly to produce numbers nothing else can:

- **RTO** — wall clock from the delete to a working sign-in, which
  [fdb-recovery](fdb-recovery.md) currently admits has never been
  measured.
- **That the key works.** Until a restore completes, the FoundationDB
  backup key is an assumption. A wrong key lists in a bucket exactly
  like a right one.
- **Anything here that was wrong**, which is likely, since nobody has
  run it.

## Rules

**MUST:**

- Record the generation with the version. A version alone cannot say
  which container to read.
- Point `fdb.backup.backupName` at a new generation before the rebuilt
  cluster starts writing.
- Quiesce writes before taking the restore point, since this is the one
  scenario where the point is chosen rather than inherited.
- Verify with `fdbrestore status` and a key count, never with the
  restore Job's exit status.

**MUST NOT:**

- Expect a ForceNew field to apply itself. The composite reports
  `Synced` while nothing happens; read `LastAsyncOperation`.
- Treat this as reversible. The volumes go first and there is no second
  instance standing.
- Use this for an instance or installation rebuild. Both take the
  database or the backups with them.

## References

- [fdb-recovery](fdb-recovery.md) — the scenarios, and what each costs
- [ADR-0026](../adr/0026-recovering-data-and-the-states-that-do-it.md)
  — why in place commits at the moment the volumes go
- [crossplane-providers](crossplane-providers.md) — why a ForceNew
  change is refused rather than performed
- [data-recovery](../compliance/data-recovery.md) — the obligations a
  completed run of this would satisfy
