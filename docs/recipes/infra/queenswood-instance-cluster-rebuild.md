# Rebuilding an instance's cluster

<!-- tessl-plugin: deployment -->

## Status

**Untested.** Every step below is derived from the compositions, the
chart and the provider's own behaviour rather than from having done it,
and the first person to follow it should correct it as they go. The
timings in particular are unknown: this is the procedure that produces
this installation's first RTO measurement, and its first restore.

## Problem

You need to destroy a Queenswood instance's cluster and rebuild it,
restoring its data from backups.

## Solution

### Prerequisites

- A restorable backup and a running instance, confirmed below.
- Steps 3 and 4 — write access to the installations repository and to
  this one, and a merge.
- The capability each step names. Ours is a Google group; yours may differ.

```bash
gcloud auth application-default login
just sop-fdb-list-backups <env> <label>
kubectl --context <code>-<env>-<label> -n queenswood get pods
```

The listing gives the current generation and the restorable span.

### 1. Stop writing to it

**No cloud capability.** These are the bank's own workloads.

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

**As the installation's platform viewer.** Ours is
`grp-gcp-<code>-platform-viewer@`, populated rather than joined.

Let the mutation log catch up, then name the moment:

```bash
just sop-fdb-version-at <env> <label> <YYYY-MM-DDTHH:MM:SSZ>
```

Record the **version** and the **generation** it belongs to. A version
without its generation cannot be used: version numbers restart near
zero on a rebuild, so they only mean anything inside the container that
wrote them.

### 3. Set the restore, and open a new generation

**No cloud capability.** Write access to the installations repository.

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

**No cloud capability.** Write access to this repository.

The manifest or values edit that started this — the new `zone`,
`region` or `datapathProvider`. Merging it changes nothing yet, by
design.

### 5. Delete the managed cluster

**As the installation's cluster admin.** Ours is
`grp-gcp-<code>-cluster-admin@` — join for this step, then leave.

Break-glass, and the point of no return.

Delete the workloads holding volumes before the cluster goes. The GKE
CSI driver only runs `DeleteDisk` while the cluster is alive, and
Crossplane has to still be reconciling to clear namespaced finalizers,
so a cluster destroyed with PVCs still bound leaves its persistent
disks behind as orphans — which cost, count against the regional disk
quota, and are not what the next bring-up adopts.

```bash
kubectl --context <code>-mgmt -n crossplane-system \
  delete cluster.container.gcp.m.upbound.io <name>
```

Crossplane recomposes it from the composite. Expect the node pool to go
with it and be recomposed too, since a node pool cannot outlive its
cluster.

### 5b. Expect the node pool's first create to fail

**As the installation's cluster admin.** Ours is
`grp-gcp-<code>-cluster-admin@` — join for this step, then leave.

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

**As the installation's platform viewer.** Ours is
`grp-gcp-<code>-platform-viewer@`, populated rather than joined.

The composite writes Argo's cluster registration from the cluster's own
reported endpoint and certificate authority, so there is a window where
the registration names an endpoint that no longer answers. It corrects
itself on the next reconcile once the new cluster reports; nothing
needs doing, but an Argo sync failing during that window is expected
rather than a fault.

### 7. Let the bring-up restore

**No capability at all.** Argo and the chart do this unattended.

Nothing further is needed. The restore Job renders because
`fdb.restore` is set, the migrator's `wait-for-restore` initContainer
gates on it, bootstrap gates on the migrator, and every service gates
on bootstrap. The destination is empty, so the Job restores rather than
taking its do-nothing branch.

### 8. Verify the restore, not the Job

**As the installation's cluster admin.** Ours is
`grp-gcp-<code>-cluster-admin@` — join for this step, then leave.

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

## Discussion

We destroy and rebuild because some fields identify the composed
`Cluster` rather than configure it — `zone`, `region`,
`datapathProvider`, and anything else the provider treats as ForceNew.
Changing one cannot be applied in place, and the provider refuses
rather than performing it: the cluster carries on unchanged and the
refusal lands in `LastAsyncOperation` on the managed resource, so
`Synced` alone never says it happened. The only way the value moves is
for the cluster to go and be recomposed, which takes the volumes under
it and every record FoundationDB holds — hence the restore.

**What this rebuilds, and what it does not.** The cluster, not the
instance. The instance keeps its name, its project and everything
addressable about it, so there is no second instance, no cutover and no
second name to invent, and everything else it is made of survives: the
project, the database, the address, the certificates, the DNS records,
the secrets and the backups. Rebuilding the *instance* is a different
procedure — the project goes, so Cloud SQL goes, so Keycloak must be
recovered alongside FoundationDB to points chosen together. Rebuilding
the *installation* takes the recovery project and the backups with it.
Neither is written down.

**Why the restore point is chosen rather than inherited.** This is the
`specific × new` cell of [fdb-recovery](fdb-recovery.md): the moment to
come back to is known, because you picked it, which is what makes
stopping writes first worth the trouble and what rules out restoring to
latest.

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

**MUST NOT:**

- Expect a ForceNew field to apply itself. The composite reports
  `Synced` while nothing happens; read `LastAsyncOperation`.
- Treat this as reversible. The volumes go first and there is no second
  instance standing.
- Use this for an instance or installation rebuild. Both take the
  database or the backups with them.

## References

- [fdb-recovery](fdb-recovery.md) — the scenarios, and what each costs
- [ADR-0026](../../adr/0026-recovering-data-and-the-states-that-do-it.md)
  — why in place commits at the moment the volumes go
- [crossplane-providers](crossplane-providers.md) — why a ForceNew
  change is refused rather than performed
- [data-recovery](../../compliance/data-recovery.md) — the obligations a
  completed run of this would satisfy
