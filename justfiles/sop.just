# =========================================================================
# Support operating procedures.
#
# Read-only answers to the questions asked while something is wrong, and
# the deliberate acts taken afterwards. Nothing here is part of building
# or running an installation -- gcp.just does that -- and nothing here
# runs on a schedule.
#
# Two properties every recipe holds to:
#
# It reads, unless its name says otherwise. A support procedure invoked
# during an incident is invoked by someone who has not read it, so a
# recipe that answers a question must not also change one.
#
# It needs no cluster access. The capabilities in ADR-0023 grant a
# person logs and nothing else -- no exec, no secrets -- so a procedure
# that depends on getting into a pod is one nobody on call can run.
# Where only a pod can answer, the recipe says so rather than failing on
# a permission.
# =========================================================================

# List an instance's restorable backup points, most recent last.
sop-fdb-list-backups env label count="20":
    #!/usr/bin/env zsh
    set -e
    # From the bucket rather than from `fdbbackup describe`. The
    # authoritative answer is the one FDB gives, and it needs a pod with
    # the backup agent's image and the encryption key mounted, which the
    # viewer capability cannot get into -- see sop-fdb-describe.
    #
    # What the object layout says on its own is enough to choose a
    # version: a continuous backup writes
    # data/<generation>/snapshots/snapshot,<begin>,<end>,<bytes>, one
    # object per completed snapshot, and <end> is a restorable version.
    # The log objects beside them are what make every version between
    # two snapshots restorable as well, which is what continuous means
    # and why the newest snapshot is a floor rather than the only
    # choice.
    BUCKET=$(just _sop-backup-bucket "{{ env }}" "{{ label }}")
    if [[ -z "$BUCKET" ]]; then
      echo "no backups bucket for {{ CODE }}-{{ env }}-{{ label }}" >&2
      echo "the instance composes it -- check spec.recovery is set" >&2
      exit 1
    fi
    echo ">>> $BUCKET"
    # Captured before it is piped: a pipeline takes the last command's
    # status, so a denial would otherwise read as an empty bucket.
    # `ls` on a pattern matching nothing exits non-zero saying so, which
    # is a real answer here rather than a failure -- a backup has no
    # snapshot until its first period elapses. Recursive-and-grep tells
    # the two apart: an empty result is empty, and a denial is an error.
    if ! OUT=$(gcloud storage ls -r "gs://$BUCKET/data/**" 2>&1); then
      if printf '%s\n' "$OUT" | grep -q "matched no objects"; then
        OUT=""
      else
        echo "cannot list gs://$BUCKET:" >&2
        printf '%s\n' "$OUT" | sed 's/^/  /' >&2
        exit 1
      fi
    fi
    ROWS=$(printf '%s\n' "$OUT" | grep '/snapshots/snapshot,' || true)
    if [[ -z "$ROWS" ]]; then
      echo "no completed snapshots yet" >&2
      echo "a continuous backup is not restorable until its first one" >&2
      echo "lands, which takes up to fdb.backup.snapshotPeriodSeconds" >&2
      exit 0
    fi
    printf '%s\n' "$ROWS" \
      | awk -F'/' '{ split($NF, f, ",");
                     printf "%s/%s\t%s\t%s\n", $(NF-3), $(NF-2), f[3], f[4] }' \
      | sort -k2 -n \
      | tail -n {{ count }} \
      | awk 'BEGIN { printf "%-14s %-22s %s\n", "GENERATION", "VERSION", "BYTES" }
             { printf "%-14s %-22s %s\n", $1, $2, $3 }'
    echo
    echo ">>> restore to one of these by setting, in the instance's values:"
    echo "      fdb.restore.backupName: <generation>"
    echo "      fdb.restore.version:    <version>"
    echo ">>> a version without a generation is refused: version numbers"
    echo ">>> restart near zero on a rebuild, so they only mean anything"
    echo ">>> inside the generation that wrote them."

# Find the version to restore to for a moment in wall-clock time.
sop-fdb-version-at env label time:
    #!/usr/bin/env zsh
    set -e
    # A restore point is a version, and nobody thinks in versions. FDB
    # numbers them at a million a second, so they are a clock -- but one
    # that starts near zero on every rebuild and reads in units nobody
    # has an intuition for.
    #
    # No arithmetic here, and no assumed rate. A continuous backup ships
    # its mutation log as objects named
    # log,<begin>,<end>,<uuid>,<block>, and the object carrying a moment
    # is the one written just after it. So the answer is read off the
    # listing rather than computed from a model that could be wrong:
    # find the last object written at or before the time asked for, and
    # take the version its range begins at.
    #
    # Which is conservative in the direction that matters. An object is
    # uploaded after the versions it covers, so its begin version is
    # slightly earlier than its write time -- and someone asking for a
    # version at a moment is asking to land before what happened next.
    #
    # ISO-8601 UTC, compared as strings, because that is what GCS
    # reports and string order is time order. No date parsing, and none
    # of the ways it differs between a laptop and a runner.
    TARGET="{{ time }}"
    case "$TARGET" in
      [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z) ;;
      *) echo "time must be ISO-8601 UTC, e.g. 2026-08-20T16:50:00Z" >&2; exit 1 ;;
    esac
    BUCKET=$(just _sop-backup-bucket "{{ env }}" "{{ label }}")
    if [[ -z "$BUCKET" ]]; then
      echo "no backups bucket for {{ CODE }}-{{ env }}-{{ label }}" >&2
      exit 1
    fi
    if ! OUT=$(gcloud storage ls -l "gs://$BUCKET/data/**/logs/**" 2>&1); then
      if printf '%s\n' "$OUT" | grep -q "matched no objects"; then
        echo "no mutation log yet, so no version to name" >&2
        exit 1
      fi
      echo "cannot list gs://$BUCKET:" >&2
      printf '%s\n' "$OUT" | sed 's/^/  /' >&2
      exit 1
    fi
    ROWS=$(printf '%s\n' "$OUT" | grep '/log,' || true)
    if [[ -z "$ROWS" ]]; then
      echo "no mutation log objects found" >&2
      exit 1
    fi
    printf '%s\n' "$ROWS" \
      | awk -v target="$TARGET" '
          match($0, /[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z/) {
            when = substr($0, RSTART, RLENGTH)
            n = split($NF, p, "/"); split(p[n], f, ",")
            # data/<service>/<type>/logs/<dir>/<dir>/log,... -- two
            # levels deeper than a snapshot, which sits directly under
            # the generation.
            gen = p[n-5] "/" p[n-4]
            if (when <= target) { bgen=gen; bwhen=when; bver=f[2] }
            else if (awhen == "") { agen=gen; awhen=when; aver=f[2] }
          }
          END {
            if (bver == "") {
              print "nothing was written at or before " target > "/dev/stderr"
              if (awhen != "") print "the earliest is " awhen > "/dev/stderr"
              exit 1
            }
            printf "generation: %s\n", bgen
            printf "version:    %s\n", bver
            printf "written:    %s\n", bwhen
            if (awhen != "") printf "next log:   %s\n", awhen
          }'
    echo
    echo ">>> restoring to this version lands just before {{ time }}."
    echo ">>> every version between two snapshots is restorable, which is"
    echo ">>> what continuous means -- this one is chosen for the moment"
    echo ">>> asked for rather than for being a snapshot boundary."

# Say how to get FDB's own account of what is restorable.
sop-fdb-describe env label:
    #!/usr/bin/env zsh
    set -e
    # Not run from here, and this is the honest version of that. FDB's
    # own answer comes from `fdbbackup describe`, which needs the backup
    # image, the cluster file, the blobstore credentials and the
    # encryption key -- so it runs in a pod, and the viewer capability
    # holds no exec. Printing the command is worth more than a recipe
    # that fails on a permission and leaves someone guessing which one.
    #
    # It is also the only check that reads the metadata *through* the
    # key. A wrong key lists in a bucket exactly like a right one.
    BUCKET=$(just _sop-backup-bucket "{{ env }}" "{{ label }}")
    echo "fdbbackup describe reads the backup's own metadata, decrypting"
    echo "it, which listing the bucket cannot. It runs in a pod:"
    echo
    echo "  kubectl -n queenswood exec -it \\"
    echo "    deploy/queenswood-fdb-backup-agents -- \\"
    echo "    fdbbackup describe \\"
    echo "      -d 'blobstore://queenswood@queenswood-s3proxy:80/<generation>?bucket=${BUCKET}&region={{ REGION }}&secure_connection=0' \\"
    echo "      --encryption-key-file /var/backup-key/encryption-key \\"
    echo "      -C /etc/fdb/fdb.cluster"
    echo
    echo "exec is a break-glass capability. Where it is not available,"
    echo "sop-fdb-list-backups answers the same question from the object"
    echo "layout, without confirming the key."

# The instance's backups bucket, found by name prefix.
[private]
_sop-backup-bucket env label:
    #!/usr/bin/env zsh
    set -e
    # By prefix, because the suffix belongs to the recovery project and
    # is not derivable from the instance. Empty rather than an error
    # where there is none, so a caller can say what that means.
    PROJECT=$(just _gcp-recovery-project || true)
    if [[ -z "$PROJECT" ]]; then
      exit 0
    fi
    if ! OUT=$(gcloud storage buckets list \
                 --project="$PROJECT" --format='value(name)' 2>&1); then
      echo "cannot list buckets in $PROJECT:" >&2
      printf '%s\n' "$OUT" | sed 's/^/  /' >&2
      exit 1
    fi
    printf '%s\n' "$OUT" \
      | grep "^bkt-{{ CODE }}-{{ env }}-{{ label }}-backups-" \
      | head -1 || true
