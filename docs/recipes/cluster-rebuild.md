# Rebuilding an instance's cluster

<!-- tessl-plugin: deployment -->

## Status

Run once, on this installation's test instance: a planned rebuild onto
Dataplane V2, and the first time anything here read a backup back.

- **The backups are readable.** `fdbrestore` completed against a fresh
  cluster — 142 files, five blocks, `ApplyVersionLag: 0`. Until a
  restore completes, the encryption key is an assumption: a wrong key
  lists in a bucket exactly like a right one.
- **Data back in a little over half an hour.** The managed `Cluster`
  was recomposed, and the restore Job completed thirty-two minutes
  later, five of which were the restore itself. That is not an RTO. The
  dataset was around 137KB across those 142 files, so the number says
  the path works, not what it costs at size.
- **Dataplane V2 came up as asked**, confirmed by `anetd` running in
  `kube-system` rather than by the field reading back.

Still unmeasured: anything at real data volume, and a clean
delete-to-sign-in wall clock — the first run's was dominated by a
credential problem that is now a precondition below rather than a step.
The steps themselves have now been walked; the timings have not been
earned twice.

## Problem

Some fields identify the composed `Cluster` rather than configure it —
`zone`, `region`, `datapathProvider`, and anything else the provider
treats as ForceNew. Changing one cannot be applied in place, and the
provider refuses rather than performing it: the cluster carries on
unchanged and the refusal lands in `LastAsyncOperation` on the managed
resource, so `Synced` alone does not tell you it happened.

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

### Who you need to be

Assumed throughout, and the only two groups anybody stands in day to
day:

- **`grp-gcp-<code>-platform-viewer@`** — reads the installation.
  `roles/browser`, `compute.viewer`, `container.viewer`,
  `iam.serviceAccountViewer`, `logging.viewer` and `monitoring.viewer`
  at the folder, plus a project custom role carrying
  `container.pods.getLogs` — which `container.viewer` lacks and only
  `container.developer` otherwise provides, along with exec and every
  write.
- **`grp-gcp-security-reviewer@`** — reads IAM policy across the
  organisation and changes nothing. Organisation-scoped, so no
  installation code in the name.

Joined for one step and left:

- **`grp-gcp-<code>-cluster-admin@`** — `roles/container.admin` at the
  folder. Needed twice, and only twice: deleting the managed cluster,
  and `kubectl exec` to verify the restore. `container.viewer` carries
  neither delete nor exec, which is the point of it.

Not needed, and worth saying so:

- **`grp-gcp-<code>-platform-admin@`** — impersonates the identity that
  builds a plane. Nothing here impersonates anything.
- **`grp-gcp-<code>-secrets-admin@`** — nothing here writes a secret
  version. The backup key already exists, and writing a second one to
  that entry would strand every backup taken under the first. The
  bootstrap admin entry is the one thing that must already hold a
  version, which is a check before starting rather than a step.

One observed gap rather than a granted capability: the day-to-day
capabilities list the backups bucket but cannot read an object out of
it — `storage.objects.get` is denied. That is enough for every step
here, since nothing needs an object's contents, but it is why
`sop-fdb-describe` prints a command rather than running one.

### Before starting

Confirm the three things the rest depends on:

```bash
gcloud auth application-default login
just sop-fdb-list-backups <env> <label>
kubectl --context <code>-<env>-<label> -n queenswood get pods
gcloud secrets versions list sec-<code>-<env>-<label>-keycloak-admin \
  --project <instance-project>
```

The listing gives the current generation and the restorable span.
`gcloud auth login` on its own does not refresh application-default
credentials, and a `kubectl` call that asks to reauthenticate mid-way
through this is worth avoiding.

The last one is the difference between a rebuild that comes back
serving and one that comes back with a realm nothing can administer.
Cloud SQL survives this procedure, so Keycloak's database keeps the
admin account it was created with, and an operator-generated credential
is reborn with the cluster as something that account has never heard
of. An instance taking its bootstrap admin from Secret Manager is
unaffected — the same value comes back. One that has not adopted it
yet needs `secrets-admin` and `just gcp-keycloak-admin-secret <env>
<label>` **before** starting, not after, since the account has to be
reachable to be changed to match. See
[external-secrets](external-secrets.md).

### 1. Stop writing to it

*No cloud capability — this is the bank's own workloads.*

Not for consistency. A continuous backup is transactionally consistent
at any version, which is what the mutation log is for, so the restore
point does not need a quiet cluster to be sound. Stopping writes is an
RPO measure: it makes the version taken in the next step still current
when the volumes go, instead of losing whatever arrived between the
two.

Five deployments write, and all five have to stop:

- `api-service` — synchronously, for every write that has not earned
  command status
- `financial-processors-service` and `operational-processors-service` —
  consuming commands off the bus
- `external-adapters-service` — a provider's webhook arrives whether or
  not anybody is using the bank
- `exclusive-dispatchers-service` — the one that writes with nobody
  asking, owning every changelog cursor and the Quartz scheduler, so
  anything scheduled fires regardless

The console is not among them; it proxies to the API and writes
nothing itself. Changelog relays go quiet on their own, having no
events to checkpoint.

Scale those five to zero and leave FoundationDB, the backup agents and
s3proxy running, so the log can ship what was last written. There is no
command for this. `state: down` is **not** it: node pool to zero takes
FoundationDB and the backup agents down with the writers, so whatever
was in flight never ships and the restorable span ends earlier than it
needed to.

On an instance with one operator and no traffic, not using it is the
whole of it, and none of the above is necessary. What is missing for an
instance with users is a way to refuse writes at the edge while the
data tier stays up long enough to flush — the deployments are the
mechanism, and nothing wraps them.

### 2. Take the restore point

*`platform-viewer`.*

Let the mutation log catch up, then name the moment:

```bash
just sop-fdb-version-at <env> <label> <YYYY-MM-DDTHH:MM:SSZ>
```

It answers with both halves, read off the mutation log rather than
computed:

```
generation: fdb/continuous/2026-08-20
version:    <version>
written:    2026-08-20T16:49:31Z
next log:   2026-08-20T16:50:04Z
```

Record the **version** and the **generation** it belongs to — they are
what step 3 pastes. A version without its generation cannot be used:
version numbers restart near zero on a rebuild, so they only mean
anything inside the container that wrote them.

### 3. Turn the restore on, and open a new generation

*Write access to the installations repository — no cloud capability.*

Four fields in the instance's values, in the same merge, taking the two
recorded above:

```yaml
fdb:
  backup:
    enabled: true
    # A NEW generation. Not the one being restored from.
    backupName: fdb/continuous/2026-08-21
  restore:
    # On for the rebuild, off again at step 9.
    enabled: true
    backupName: fdb/continuous/2026-08-20
    version: "<version>"
```

`restore.enabled` is what renders the Job at all — the target on its own
does nothing, which is what lets the target stay behind afterwards as a
record. Quote the version: it is a string, and YAML reads a bare one as
a number.

`backup.backupName` is the field that is easy to miss and expensive to
get wrong. A rebuilt cluster numbers its versions from near zero, so if
it writes into the container it read from, `fdbbackup describe` starts
answering with the dead cluster's higher numbers.

### 4. Merge the change that forces the rebuild

*Write access to this repository — no cloud capability.*

The manifest or values edit that started this — the new `zone`,
`region` or `datapathProvider`. Merging it changes nothing yet, by
design.

### 5. Delete the managed cluster

*`cluster-admin`, joined for this step.*

Break-glass, and the point of no return:

```bash
kubectl --context <code>-mgmt -n crossplane-system \
  delete cluster.container.gcp.m.upbound.io <name>
```

Crossplane recomposes it from the composite. Expect the node pool to go
with it and be recomposed too, since a node pool cannot outlive its
cluster.

### 5b. Expect the node pool's first create to fail

*`cluster-admin`.*

`Can only set pod_ipv4_cidr_block if create_pod_range is true`, on the
`NodePool`, repeating every reconcile while the cluster sits there with
no nodes.

Late-initialisation is the cause. Where the composition does not own
`networkConfig`, upjet observes the live pool and writes `podRange` and
`podIpv4CidrBlock` into the spec. That is only a description while the
pool exists; when the pool has to be created again the CIDR is sent as
a create parameter, and asking for a pod range without asking to create
one is refused.

Composing `networkConfig.podRange` makes the range explicit and
correct, and does **not** stop this happening again. Late-init fills
any field that is unset, and the composition sets only `podRange` — so
once the pool exists the provider writes `podIpv4CidrBlock` back beside
it, and the next rebuild is refused the same way. Different fields,
different managers; owning one does not deny the other.

Stopping it for good means dropping `LateInitialize` from the pool's
`managementPolicies`, so the provider never writes observed values into
the spec. That is a wider change than it looks, since late-init also
fills provider-assigned defaults, and it can only be tested by another
rebuild.

Until then this step is required every time. Drop the field by hand:

```bash
kubectl --context <code>-mgmt -n crossplane-system \
  patch nodepool.container.gcp.m.upbound.io <pool> \
  --type=json -p='[{"op":"remove",
       "path":"/spec/forProvider/networkConfig/podIpv4CidrBlock"}]'
```

The same shape is worth suspecting for anything else late-init owns: a
value that reads correctly against a resource that exists can still be
one nothing may ask for at create.

### 6. Wait for Argo to find the new cluster

*`platform-viewer`.*

The composite writes Argo's cluster registration from the cluster's own
reported endpoint and certificate authority, so there is a window where
the registration names an endpoint that no longer answers. It corrects
itself on the next reconcile once the new cluster reports; nothing
needs doing, but an Argo sync failing during that window is expected
rather than a fault.

### 7. Let the bring-up restore

*Nothing. Argo and the chart do this unattended.*

Nothing further is needed. The restore Job renders because
`fdb.restore` is set, the migrator's `wait-for-restore` initContainer
gates on it, bootstrap gates on the migrator, and every service gates
on bootstrap. The destination is empty, so the Job restores rather than
taking its do-nothing branch.

### 8. Verify the restore, not the Job

*`cluster-admin` again — `exec` is not a viewer capability.*

```bash
kubectl --context <code>-<env>-<label> -n queenswood exec -it \
  deploy/queenswood-fdb-backup-agents -- \
  fdbrestore status --dest-cluster-file /etc/fdb/fdb.cluster
```

`State: completed` with `LastError: None`. Then count keys against what
was there before, and finally sign in through the console and confirm a
party resolves — Keycloak was never rebuilt, so a user whose party is
missing means the restore, not the realm.

### 9. Turn the restore off again

*Write access to the installations repository — no cloud capability.*

One field, once the verification above passes:

```yaml
fdb:
  restore:
    enabled: false
    # Left as the record of where this cluster's data came from.
    backupName: fdb/continuous/2026-08-20
    version: "<version>"
```

Nothing breaks today if this is forgotten: the Job reads the
destination first and exits without restoring where it already holds
data, so a re-run is a no-op and the migrator simply waits on a Job that
does nothing.

It breaks at the *next* rebuild, which starts against an empty
destination — and an `enabled` restore then quietly restores **this**
generation and **this** version, rather than whichever one that rebuild
chose. The reason it survives review is that everything looks right:
the Job runs, the Job succeeds, and the data that comes back is real
data, just from the wrong moment.

Leave the target beneath it. It costs nothing, it is not live, and it
is the only place the cluster records where its data came from.

### 10. Record what happened

This procedure produces numbers nothing else can, and the first run
produced only some of them:

- **RTO** — wall clock from the delete to a working sign-in. The first
  run's is not usable, and a clean one is most of what a second run is
  worth.
- **What it costs at size.** Half an hour against 137KB says the path
  works and nothing about a bank with records in it, so record the
  dataset beside the duration or the number means nothing later.
- **Anything here that is still wrong.** One run corrects a procedure's
  shape rather than its details.

## Rules

**MUST:**

- Record the generation with the version. A version alone cannot say
  which container to read.
- Point `fdb.backup.backupName` at a new generation before the rebuilt
  cluster starts writing.
- Stop writes before taking the restore point, and delete promptly
  afterwards. Not for consistency — any version is consistent — but so
  the point taken is still current when the volumes go.
- Verify with `fdbrestore status` and a key count, never with the
  restore Job's exit status.
- Set `fdb.restore.enabled: false` once the restore is verified, leaving
  the target beneath it. A restore left enabled is inert against a
  destination that holds data and silently authoritative against the
  next empty one.

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
