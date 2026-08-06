<!-- tessl-plugin: deployment -->

# Recovery procedures

What to run when data has to come back. Both stores restore the same
way — a version or dump recorded at teardown, acted on before anything
writes — so the routine cycle needs no commands at all.

Everything here assumes `QUEENSWOOD_ENV` is set to the environment you
mean, since every recipe keys `pass` and the GCP project off it.

## The routine cycle: nothing to run

A teardown and rebuild restores itself.

```bash
just gcp-down    # records what to restore, while it can still be known
just gcp-up      # the rebuilt environment restores to it
```

`gcp-down` records two things: FDB's restore version, and the Keycloak
dump it exported. `gcp-up` re-renders the root Application from both,
and each store's restore Job acts before its own writer runs.

`gcp-down` runs `gcp-fdb-export` **before** draining the workload
namespace, because the backup agents live in that namespace and read
from a live cluster. It waits for the backup to be restorable, stops
it cleanly so the last mutations land, and writes the version to
`<env>/backup/fdb-restore-version` in `pass`. It refuses to continue
if the backup is not restorable — draining past that point is what
makes the data unrecoverable.

`gcp-up` re-renders the root Application with that version, and the
chart's restore Job acts on it before the migrator writes anything.

## Restoring FDB to a chosen point

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

## Keycloak

Same shape as FDB, with one difference worth knowing: an import
overwrites, where FDB refuses a non-empty destination. The Job asks
the database whether a realm exists and leaves it alone if so, but
that guard is the only one — there is nothing underneath it.

Once per project, create the identity the import runs as:

```bash
just gcp-keycloak-restore-sa-create
```

To restore a chosen dump rather than the one `gcp-down` recorded:

```bash
just gcp-keycloak-restore-points        # last 5; pass a count for more
pass insert -e -f "queenswood/gcp/$QUEENSWOOD_ENV/backup/keycloak-restore-dump"
```

The value is the prefix it prints, not the object —
`keycloak/export/2026/08/05/161016Z`. A `*` marks the one `gcp-down`
last recorded.

Order matters for a reason that is easy to miss. A rebuilt environment
mints a fresh admin signing key and bootstrap registers its public half
on the `queenswood-admin` client. Importing a dump after that reverts
the realm to the key the dump was taken with, while the pods hold the
new private half, and `private_key_jwt` stops verifying. The import is
therefore gated ahead of that registration, and bootstrap re-registers
the current key onto the restored realm. The operator's realm import
runs with `--override=false`, so it leaves the restored realm alone.

## When the deployment appears to hang

Either restore blocks the chain by design, so a stuck one looks like a
stalled deploy. Services wait on bootstrap, which waits on both the
migrator and the Keycloak import, and the migrator waits on the FDB
restore.

```bash
kubectl -n "$QUEENSWOOD_ENV" get jobs
kubectl -n "$QUEENSWOOD_ENV" logs \
  -l app.kubernetes.io/component=fdb-restore
kubectl -n "$QUEENSWOOD_ENV" logs \
  -l app.kubernetes.io/component=keycloak-restore
```

Neither gate has a timeout escape, on purpose. Both writers are
destructive to the thing being restored: saving record metadata makes
FDB non-empty and it refuses to restore into that, and registering the
admin signing key is what a late Keycloak import would undo. A gate
that gave up and ran anyway would not be carrying on safely.

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

For Keycloak, the Job says what it did — either the import command or
a line saying it found a realm already there:

```bash
kubectl -n "$QUEENSWOOD_ENV" logs \
  -l app.kubernetes.io/component=keycloak-restore
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
