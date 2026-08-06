<!-- tessl-plugin: deployment -->

# Recovery procedures

What to run when data has to come back. Covers FoundationDB only —
Keycloak's restore is still a manual `just gcp-cloudsql-import` and
carries a known ordering bug, tracked in issue #338.

Everything here assumes `QUEENSWOOD_ENV` is set to the environment you
mean, since every recipe keys `pass` and the GCP project off it.

## The routine cycle: nothing to run

A teardown and rebuild restores itself.

```bash
just gcp-down    # records the restore version while the cluster lives
just gcp-up      # the rebuilt cluster restores to it
```

`gcp-down` runs `gcp-fdb-export` **before** draining the workload
namespace, because the backup agents live in that namespace and read
from a live cluster. It waits for the backup to be restorable, stops
it cleanly so the last mutations land, and writes the version to
`<env>/backup/fdb-restore-version` in `pass`. It refuses to continue
if the backup is not restorable — draining past that point is what
makes the data unrecoverable.

`gcp-up` re-renders the root Application with that version, and the
chart's restore Job acts on it before the migrator writes anything.

## Restoring to a chosen point

When the newest data is the problem, or no teardown ran.

1. List what exists:

   ```bash
   just gcp-fdb-restore-points
   ```

   Any version between `oldest` and `newest` works, not only the
   snapshots it lists — the mutation log fills the gaps.

2. Record the version you want:

   ```bash
   pass insert -e -f "queenswood/gcp/$QUEENSWOOD_ENV/backup/fdb-restore-version"
   ```

3. Rebuild. The restore only runs against an empty cluster, so the
   data has to go first:

   ```bash
   just gcp-down
   just gcp-up
   ```

   `gcp-down` overwrites the version you just set with its own. To keep
   yours, run `gcp-down`, set the version again, then `gcp-up`.

## Applying a version to a plane that is already up

Only needed when changing the version without a rebuild — after a
`gcp-up` that predates the value, for instance.

```bash
just kind-xp-install-root
```

This re-renders the root Application from `pass` and Argo propagates
it down to the chart. Safe on a running cluster: the restore Job finds
the cluster non-empty and exits without doing anything.

## No recorded version and no backup agents

If the cluster is gone, `gcp-fdb-restore-points` has nothing to exec
into. Bring the environment up first, empty, then use the steps above:
the restore Job no-ops on the way up, agents start, and you can list
points and rebuild once more.

## When the deployment appears to hang

A restore blocks the migrator by design, so a stuck restore looks like
a stalled deploy. Services never start because they wait on bootstrap,
which waits on the migrator, which waits on the restore.

```bash
kubectl -n "$QUEENSWOOD_ENV" get jobs
kubectl -n "$QUEENSWOOD_ENV" logs -l app.kubernetes.io/component=fdb-restore
```

The gate has no timeout on purpose. Saving record metadata is a write,
and FDB refuses to restore into a non-empty destination — a migrator
that gave up waiting and ran anyway would destroy the ability to
restore rather than carry on safely.

To abandon the restore and boot empty, clear the version and re-render:

```bash
pass rm "queenswood/gcp/$QUEENSWOOD_ENV/backup/fdb-restore-version"
just kind-xp-install-root
kubectl -n "$QUEENSWOOD_ENV" delete job \
  -l app.kubernetes.io/component=fdb-restore
```

## Verifying a restore

`fdbrestore status` is the source of truth. The operator's
`FoundationDBRestore` resource reports `queued` for a restore that has
already finished, so do not gate on it.

```bash
POD=$(kubectl -n "$QUEENSWOOD_ENV" get pods -o name \
  | grep fdb-backup-agents | head -1 | cut -d/ -f2)
kubectl -n "$QUEENSWOOD_ENV" exec "$POD" -c foundationdb -- \
  fdbrestore status --dest-cluster-file /var/dynamic-conf/fdb.cluster
```

`State: completed` with `LastError: None` is success. Empty output
means no restore was ever submitted. To check the data rather than the
status, compare key counts against what you expect:

```bash
kubectl -n "$QUEENSWOOD_ENV" exec "$POD" -c foundationdb -- \
  fdbcli -C /var/dynamic-conf/fdb.cluster \
  --exec 'getrangekeys "" \xff 100000' | grep -c '^`'
```

## Helm refuses to upgrade after manual intervention

Applying chart resources by hand — during an incident, say — stops the
next Helm upgrade with `invalid ownership metadata`. Helm will not
import a resource it did not create.

```bash
kubectl -n "$QUEENSWOOD_ENV" annotate <resource> \
  meta.helm.sh/release-name="$QUEENSWOOD_ENV" \
  meta.helm.sh/release-namespace="$QUEENSWOOD_ENV" --overwrite
```

Annotate rather than delete: deleting a `FoundationDBBackup` stops the
backup and tears down its agents.

## Related

- [cloud-deployment](cloud-deployment.md) — the tiers, the teardown
  order, and the credential taxonomy the backup keys live in
- [ADR-0016](../adr/0016-crossplane-over-terraform.md) — why the
  management plane applies any of this at all
