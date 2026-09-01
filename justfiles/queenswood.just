# =========================================================================
# Queenswood's own manifests: the documents an installation is declared
# by, rendered from the templates in infra/platform/templates.
#
# Here rather than in gcp.just because these write files and touch no
# cloud resource -- they read GCP to discover what the plane already
# built, and change nothing in it. See
# docs/recipes/infra/queenswood-instance.md.
# =========================================================================

# The instance these recipes act on, and the folder's parent for a first
# installation. Set once in the environment rather than passed to each
# recipe: an argument is a value you can get wrong on one command in a
# sequence and right on the next. Pass one to override for a single run.
ENV := env_var_or_default("QW_ENV", "")
LABEL := env_var_or_default("QW_LABEL", "")
PARENT := env_var_or_default("QW_PARENT", "")

# Which set of templates a unit is rendered from. minimal leans on the
# XRD's defaults, so the file says what this instance chose rather than
# what it inherited; full states them, which is what anything
# long-lived wants, since an immutable field whose default moves later
# is refused rather than applied. Two complete sets rather than one
# with a hole in it: each renders to a document you can read whole.
SPEC := env_var_or_default("QW_SPEC", "minimal")

# Print the installation manifest, resolved. Takes the folder's parent.
queenswood-installation-manifest parent=PARENT name="" folder="" mgmt="" billing="" recovery="":
    #!/usr/bin/env zsh
    set -e
    # stdout carries the manifest and nothing else, so this can be
    # redirected straight into a file; every message below goes to
    # stderr. That file is the installation's whole record -- see
    # docs/plan/cloud-just-migration.md -- and it lives in a private
    # repository, because two of the values here are identifiers worth
    # keeping out of a public one.
    #
    # Anything generated rather than derived is minted per call, so
    # capture the output rather than re-running this and expecting the
    # same answer.
    #
    # Checked here rather than only on the branch that scopes a folder
    # lookup: the value is wanted either way.
    if [[ -z "{{ parent }}" ]]; then
      echo "no parent for the folder" >&2
      echo "export QW_PARENT, or pass it:" >&2
      echo "  just queenswood-installation-manifest organizations/<org-id>" >&2
      exit 1
    fi
    # Deterministic, because the code is what tells one installation
    # from another -- a suffix on top of it would only obscure that.
    # Pass a name where the folder is handed to you already named.
    NAME="{{ name }}"
    if [[ -z "$NAME" ]]; then
      NAME="fldr-{{ CODE }}"
      echo ">>> naming the folder $NAME" >&2
    fi
    # Refuse to make a second installation by accident. GCP allows the
    # duplicate name, so this is the only thing that would notice -- and
    # with a deterministic name it is what catches a rebuild that meant
    # to adopt.
    if [[ -z "{{ folder }}" ]]; then
      case "{{ parent }}" in
        organizations/*) SCOPE="--organization=${${:-{{ parent }}}#organizations/}" ;;
        folders/*)       SCOPE="--folder=${${:-{{ parent }}}#folders/}" ;;
        *) echo "parent must be organizations/<id> or folders/<id>" >&2; exit 1 ;;
      esac
      EXISTING=$(gcloud resource-manager folders list $SCOPE \
                   --filter="displayName=$NAME" \
                   --format='value(name)' 2>/dev/null | head -1 || true)
      if [[ -n "$EXISTING" ]]; then
        echo "a folder named $NAME already exists under {{ parent }}: folders/$EXISTING" >&2
        echo "adopt it:  just gcp-boot-mgmt-apply {{ parent }} $NAME folders/$EXISTING" >&2
        echo "or choose a different name" >&2
        exit 1
      fi
    fi
    # Omitted unless passed, and normally not passed. The account is a
    # property of the identity -- it holds billing.user on exactly one --
    # so the manifest does not record it. Once a project exists the
    # provider's late-initialisation owns forProvider.billingAccount, so
    # a manifest without this leaves billing alone rather than unsetting
    # it. gcp-boot-mgmt-apply supplies it, because creating a project is the
    # one moment it is needed.
    BILLING_LINE=""
    if [[ -n "{{ billing }}" ]]; then
      BILLING_LINE="billingAccountId: \"{{ billing }}\""
    fi

    # A project id is consumed permanently and cannot be undeleted into
    # usefulness, so it is generated once and then carried in the
    # manifest. That is what stops a rebuilt plane minting a second one.
    MGMT="{{ mgmt }}"
    if [[ -z "$MGMT" ]]; then
      MGMT="prj-{{ CODE }}-c-mgmt-$(openssl rand -hex 3)"
      echo ">>> naming the management project $MGMT" >&2
    fi

    # Minted here rather than added when backups are first wanted, so a
    # from-scratch installation has somewhere to keep what it would be
    # rebuilt from without anyone deciding to. Its own id for the same
    # reason the management project's is minted once: consumed
    # permanently, and not derivable afterwards -- and minted in the
    # same call, so the file records both permanent ids or neither.
    # The plane is the only thing that reads it, so it is authored
    # here rather than in the environment: an instance reads the id
    # off the Project this composes.
    RECOVERY="{{ recovery }}"
    if [[ -z "$RECOVERY" ]]; then
      RECOVERY="prj-{{ CODE }}-c-recovery-$(openssl rand -hex 3)"
      echo ">>> naming the recovery project $RECOVERY" >&2
    fi
    RECOVERY_BLOCK=$(printf '  recovery:\n    projectId: "%s"' "$RECOVERY")

    # Set only when the project already exists, for the same reason the
    # folder's adoption field is: a pre-set external name turns a
    # first-run create into a failed observe.
    MGMT_ADOPT=""
    if [[ -n "{{ mgmt }}" ]]; then
      # Prefixed: that is the form Crossplane records as the external
      # name, and adoption matches on it exactly.
      case "$MGMT" in
        projects/*) MGMT_ADOPT="adopt: \"$MGMT\""; MGMT="${MGMT#projects/}" ;;
        *)          MGMT_ADOPT="adopt: \"projects/$MGMT\"" ;;
      esac
    fi

    # Resolved from the parent where that names an organisation, rather
    # than rediscovered. An argument the caller supplied and the recipe
    # then ignores is a run that fails on a question already answered --
    # and discovery fails outright where more than one organisation is
    # visible, or where a token has expired.
    PARENT="{{ parent }}"
    ORG_ARG=""
    if [[ "$PARENT" == organizations/* ]]; then
      ORG_ARG="${PARENT#organizations/}"
    fi
    DOMAIN=$(just _gcp-domain "$ORG_ARG")

    FOLDER="{{ folder }}"
    if [[ -n "$FOLDER" && "$FOLDER" != folders/* ]]; then
      FOLDER="folders/$FOLDER"
    fi
    ADOPT=""
    if [[ -n "$FOLDER" ]]; then
      ADOPT="folderId: \"$FOLDER\""
    fi

    # region, zone and regionCode are stated rather than left to the
    # XRD's defaults. They are load-bearing -- a name carries the
    # region abbreviation, and moving either rebuilds the subnet and
    # the cluster -- so a default that changed underneath a live
    # installation would move it silently.
    # The document lives in infra/platform/templates/installation.yml.tmpl so it
    # can be read and reviewed as YAML rather than as shell.
    CODE="{{ CODE }}" REGION="{{ REGION }}" REGION_CODE="{{ REGION_CODE }}" \
    ZONE="{{ ZONE }}" BILLING_LINE="$BILLING_LINE" DOMAIN="$DOMAIN" \
    RECOVERY_BLOCK="$RECOVERY_BLOCK" MGMT="$MGMT" MGMT_ADOPT="$MGMT_ADOPT" \
    SOURCE_REPO="{{ SOURCE_REPO }}" SOURCE_REVISION="{{ SOURCE_REVISION }}" \
    SOURCE_PATH_PREFIX="{{ SOURCE_PATH_PREFIX }}" \
    MANIFEST_REPO_URL="{{ MANIFEST_REPO_URL }}" \
    PARENT="{{ parent }}" NAME="$NAME" ADOPT="$ADOPT" \
    python3 -c 'import os,sys,string; d=string.Template(open(sys.argv[1]).read()).substitute(os.environ); sys.stdout.write("\n".join(l for l in d.split("\n") if l.strip()) + "\n")' \
      infra/platform/templates/installation.yml.tmpl

# Render a whole unit into the installations repository.
queenswood-instance-manifest env=ENV label=LABEL project="" domain="" spec=SPEC:
    #!/usr/bin/env zsh
    set -e
    # One recipe rather than six redirects, because what makes this safe
    # is that the project id is minted once and written into three of
    # the files identically. Rendering them separately is how they come
    # to disagree, and a wrong id in the external-secrets annotation
    # yields a service account nothing is bound to rather than an error.
    if ! INSTANCE=$(just _queenswood-instance "{{ env }}" "{{ label }}"); then
      exit 1
    fi
    # Before anything is deleted or minted, so a typo costs nothing.
    TEMPLATES=infra/platform/templates/unit/{{ spec }}
    if [[ ! -d "$TEMPLATES" ]]; then
      echo "no template set {{ spec }}" >&2
      echo "sets are:" >&2
      ls infra/platform/templates/unit | sed 's/^/  /' >&2
      exit 1
    fi
    ROOT={{ INSTALLATIONS_REPO }}/{{ CODE }}
    UNIT_FILE="$ROOT/{{ label }}.unit.yml"
    UNIT_DIR="$ROOT/units/{{ label }}"

    # Committed is the line, not merely present. A plane reads the unit
    # from git, so one that has never been committed cannot have been
    # applied: nothing was built, the minted id means nothing, and
    # re-rendering costs nothing. A committed one may already be merged
    # and built, and then the file is the only record of a project id
    # GCP has consumed.
    if [[ -e "$UNIT_FILE" || -d "$UNIT_DIR" ]]; then
      COMMITTED=""
      if git -C {{ INSTALLATIONS_REPO }} rev-parse --git-dir >/dev/null 2>&1; then
        for TRACKED in "{{ CODE }}/{{ label }}.unit.yml" \
                       "{{ CODE }}/units/{{ label }}/instance.yml"; do
          if git -C {{ INSTALLATIONS_REPO }} cat-file -e "HEAD:$TRACKED" 2>/dev/null; then
            COMMITTED="$TRACKED"
          fi
        done
      fi
      if [[ -n "$COMMITTED" ]]; then
        echo "$INSTANCE already has a unit, committed:" >&2
        echo "  $COMMITTED" >&2
        echo "edit it, or choose another label" >&2
        exit 1
      fi
      echo ">>> replacing the uncommitted unit for $INSTANCE" >&2
      echo ">>> nothing was built from it, so the id is minted afresh" >&2
      rm -rf "$UNIT_DIR"
    fi
    if [[ ! -d "$ROOT" ]]; then
      echo "no installation at $ROOT" >&2
      echo "the plane's manifest is committed there first:" >&2
      echo "  just queenswood-installation-manifest organizations/<org-id>" >&2
      exit 1
    fi

    # Minted here, like the management project's and for the same
    # reason: consumed permanently, and not derivable afterwards.
    PROJECT="{{ project }}"
    if [[ -z "$PROJECT" ]]; then
      PROJECT="prj-$INSTANCE-$(openssl rand -hex 3)"
      echo ">>> naming the project $PROJECT" >&2
    fi

    # The installation's own apex, read from the manifest the plane
    # reconciles rather than rediscovered: it is what the zone is named
    # for, and a second answer here would name a zone nothing composed.
    INSTALLATION="$ROOT/installation.yml"
    DNS_BLOCK=$(grep -A1 '^  dns:' "$INSTALLATION" || true)
    APEX=$(printf '%s\n' "$DNS_BLOCK" \
             | sed -n 's/^ *domain: *"\{0,1\}\([^"]*\)"\{0,1\}$/\1/p')
    if [[ -z "$APEX" ]]; then
      echo "no dns.domain in $INSTALLATION" >&2
      echo "an instance answers on a subdomain of the installation's" >&2
      exit 1
    fi

    DOMAIN="{{ domain }}"
    if [[ -z "$DOMAIN" ]]; then
      DOMAIN="{{ label }}.$APEX"
    fi
    # The zone is the installation's, in the management project. Named
    # the way the plane composes it, so both spell one name.
    ZONE_NAME="dz-{{ CODE }}-c-${APEX//./-}"

    if ! MGMT=$(just _gcp-mgmt-project); then
      exit 1
    fi
    # Empty is a real answer -- an installation keeping nothing has no
    # recovery project -- but an instance composes a bucket and a backup
    # key in it, so this one is required.
    RECOVERY=$(just _gcp-recovery-project || true)
    if [[ -z "$RECOVERY" ]]; then
      echo "no recovery project for {{ CODE }}" >&2
      echo "the plane composes it from spec.recovery.projectId --" >&2
      echo "merge that into the installation's manifest first" >&2
      exit 1
    fi
    # The bucket carries the recovery project's suffix, not its own: a
    # bucket named for a project that may be rebuilt changes name when
    # it is, which is a backup nobody can find under the name recorded.
    RECOVERY_SUFFIX="${RECOVERY##*-}"

    # Pinned once, on the plane, so a unit cannot install a different
    # operator from the one the plane runs.
    ES_VERSION=$(sed -n '/^externalSecrets:/,/^[^ ]/s/^ *version: *"\{0,1\}\([^"]*\)"\{0,1\}$/\1/p' \
                   infra/helm/management-plane/values.yaml | head -1)
    if [[ -z "$ES_VERSION" ]]; then
      echo "no externalSecrets.version in the management-plane values" >&2
      exit 1
    fi

    mkdir -p "$UNIT_DIR"
    for T in unit instance external-secrets config queenswood values; do
      if [[ "$T" == "unit" ]]; then
        OUT="$UNIT_FILE"
      else
        OUT="$UNIT_DIR/$T.yml"
      fi
      CODE="{{ CODE }}" ENV="{{ env }}" LABEL="{{ label }}" \
      INSTANCE="$INSTANCE" PROJECT="$PROJECT" \
      REGION="{{ REGION }}" REGION_CODE="{{ REGION_CODE }}" \
      GCP_ZONE="{{ ZONE }}" DOMAIN="$DOMAIN" ZONE_NAME="$ZONE_NAME" \
      MGMT="$MGMT" RECOVERY="$RECOVERY" \
      BACKUP_BUCKET="bkt-$INSTANCE-backups-$RECOVERY_SUFFIX" \
      BACKUP_GENERATION="fdb/continuous/$(date +%Y-%m-%d)" \
      ORG_DOMAIN="$APEX" \
      SOURCE_REPO="{{ SOURCE_REPO }}" SOURCE_REVISION="{{ SOURCE_REVISION }}" \
      MANIFEST_REPO_URL="{{ MANIFEST_REPO_URL }}" \
      EXTERNAL_SECRETS_VERSION="$ES_VERSION" \
      python3 -c 'import os,sys,string; sys.stdout.write(string.Template(open(sys.argv[1]).read()).substitute(os.environ))' \
        "$TEMPLATES/$T.yml.tmpl" > "$OUT"
      echo ">>> wrote $OUT"
    done

    echo
    echo "$INSTANCE"
    echo "  project   $PROJECT"
    echo "  domain    $DOMAIN"
    echo "  zone      $ZONE_NAME in $MGMT"
    echo "  recovery  $RECOVERY"
    echo "  spec      {{ spec }}"
    echo
    echo "commit these, then merge. The composite builds while you"
    echo "create the OAuth client and write the three secret versions;"
    echo "googleClientId in values.yml is empty until you have one."

# Validate an instance's env and label, and print <code>-<env>-<label>.
[private]
_queenswood-instance env label:
    #!/usr/bin/env zsh
    set -e
    # The XRD's own patterns, checked while a name is still a string.
    # just has no named arguments, so a QW_SPEC=full written after the
    # recipe lands in env and would otherwise reach GCP as a name.
    if [[ -z "{{ env }}" || -z "{{ label }}" ]]; then
      echo "no instance named; export QW_ENV and QW_LABEL, or pass them" >&2
      exit 1
    fi
    if [[ ! "{{ env }}" =~ ^[a-z]$ ]]; then
      echo "env must be one lowercase letter, not {{ env }}" >&2
      echo "a variable goes before the recipe: QW_SPEC=full just ..." >&2
      exit 1
    fi
    if [[ ! "{{ label }}" =~ ^[a-z][a-z0-9-]{0,10}[a-z0-9]$ ]]; then
      echo "label must match [a-z][a-z0-9-]{0,10}[a-z0-9], not {{ label }}" >&2
      exit 1
    fi
    printf '%s\n' "{{ CODE }}-{{ env }}-{{ label }}"

# Put the Google OAuth client's secret in an instance's entry, read off
# the prompt. The client itself is made by hand in the console.
queenswood-instance-google-secret env=ENV label=LABEL file="":
    #!/usr/bin/env zsh
    set -e
    # A thin naming of gcp-secret-version: the entry is derived from the
    # instance rather than typed, which is the whole of what this adds.
    if ! INSTANCE=$(just _queenswood-instance "{{ env }}" "{{ label }}"); then
      exit 1
    fi
    just gcp-secret-version "sec-$INSTANCE-google-oauth" "" "{{ file }}"

# Generate the bootstrap admin an instance's Keycloak database is created
# with, and put it in that instance's entry. Once, per database.
queenswood-instance-keycloak-admin env=ENV label=LABEL username="admin" supersede="":
    #!/usr/bin/env zsh
    set -e
    # Its own recipe rather than a value typed into gcp-secret-version,
    # because the payload is two properties in one entry -- `username`
    # and `password`, read by name, as sec-<code>-c-github-app is. The
    # Keycloak CRD wants a kubernetes.io/basic-auth Secret carrying
    # both, and two entries would be two things to keep in step.
    #
    # Generated rather than typed: nothing reads a version back out of
    # Secret Manager -- secretsAdmin carries no versions.access,
    # deliberately -- so a value somebody chose is a value they keep
    # somewhere else. Whoever needs to sign in to the admin console
    # reads it from the Secret external-secrets writes on the cluster.
    #
    # Alphanumeric, which costs nothing at this length and spares every
    # consumer a quoting question: the realm-import Job urlencodes it,
    # and the next thing to read it might not.
    SECRET="sec-{{ CODE }}-{{ env }}-{{ label }}-keycloak-admin"
    PROJECT=$(just _gcp-instance-project {{ env }} {{ label }} || true)

    # Refused rather than versioned. Keycloak honours a bootstrap admin
    # only while the master realm is absent, so the database keeps the
    # account it was created with, and a later version reaches the
    # running realm not at all -- it materialises a credential nothing
    # accepts, which is the failure this entry exists to prevent: a
    # realm serving perfectly well that nothing can administer.
    #
    # supersede=yes is for the two cases that are not that -- an entry
    # written before any database existed, and an account you are about
    # to change to match. For the second the order is: write, let
    # external-secrets sync, read the Secret, then set that password on
    # the account. There is nothing to type, because the value is
    # generated here.
    # Not silenced, and checked separately from whether it is empty: a
    # denial exits non-zero with empty stdout, which would otherwise
    # read as an entry holding nothing.
    VERSIONS=""
    if [[ -n "$PROJECT" ]]; then
      if ! VERSIONS=$(gcloud secrets versions list "$SECRET" --project="$PROJECT" \
                        --filter="state=ENABLED" --format='value(name)' 2>&1); then
        echo "cannot list versions of $SECRET:" >&2
        printf '%s\n' "$VERSIONS" | sed 's/^/  /' >&2
        echo "secretsAdmin carries the viewer role this needs" >&2
        exit 1
      fi
    fi
    if [[ -n "$VERSIONS" ]]; then
      if [[ "{{ supersede }}" != "yes" ]]; then
        echo "$SECRET already holds a credential, and a later version does" >&2
        echo "not change the account the database was created with. Reset" >&2
        echo "that account in Keycloak to match, or reset the realm beside" >&2
        echo "a fresh FoundationDB -- then say so:" >&2
        echo "  just queenswood-instance-keycloak-admin {{ env }} {{ label }} {{ username }} yes" >&2
        exit 1
      fi
      echo ">>> superseding the credential in $SECRET, on your word that the" >&2
      echo ">>> account in the database is being changed to match" >&2
    fi

    # Thirty-two alphanumerics out of forty-eight random bytes, so the
    # strip cannot run its source dry.
    PASSWORD=$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)
    jq -n --arg u "{{ username }}" --arg p "$PASSWORD" \
      '{username: $u, password: $p}' \
      | just gcp-secret-version "$SECRET" "$PROJECT"
    echo ">>> $SECRET now holds a bootstrap admin. Nothing reads it until"
    echo ">>> keycloak.bootstrapAdmin.secretName in the instance's values"
    echo ">>> names the Secret the ExternalSecret writes."

# Generate the key an instance's FDB backups are encrypted under, and
# put it in that instance's entry. Once, ever.
queenswood-recovery-backup-key env=ENV label=LABEL supersede="":
    #!/usr/bin/env zsh
    set -e
    # Its own recipe rather than a value typed into gcp-secret-version,
    # because the encoding is not a thing to leave to whoever is
    # holding the terminal.
    #
    # fdbbackup reads encryptionKeyPath as raw bytes. The ExternalSecret
    # base64s the Secret Manager payload on its way into the Secret, so
    # what lands on disk is the payload verbatim -- meaning the entry
    # holds 32 raw bytes and not the base64 text of them. The previous
    # generation held the text, because a chart writing into `data`
    # needed base64 and Kubernetes decoded it again. Same key, two
    # encodings, and the one that is wrong fails at a restore.
    #
    # Piped, never written down: a file you delete afterwards is not
    # deleted on a copy-on-write filesystem. And not through
    # gcp-secret-version, which strips a trailing newline -- correct for
    # a typed secret, and one key in 256 ends in 0x0a.
    SECRET="sec-{{ CODE }}-{{ env }}-{{ label }}-fdb-backup-key"
    PROJECT=$(just _gcp-recovery-project || true)
    if [[ -z "$PROJECT" ]]; then
      echo "no recovery project for {{ CODE }}" >&2
      echo "the plane composes it from spec.recovery.projectId --" >&2
      echo "merge that into the installation's manifest first" >&2
      exit 1
    fi
    if ! gcloud secrets describe "$SECRET" --project="$PROJECT" >/dev/null 2>&1; then
      echo "no secret $SECRET in $PROJECT" >&2
      echo "the composite makes the container -- apply it first" >&2
      exit 1
    fi
    # Refused rather than versioned. An existing backup is readable only
    # under the key it was written with, so a later version does not
    # rotate anything -- it strands every backup taken before it. Rotate
    # by starting a new generation under a new entry and keeping this
    # one until nothing readable is left under it.
    #
    # supersede=yes is for the one case that is not a rotation: a key
    # nothing has been backed up under, so there is nothing to strand.
    # An entry written before this recipe existed holds base64 text
    # rather than raw bytes, and nobody can read it back to check --
    # secretsAdmin carries no versions.access, deliberately. So the way
    # out of a wrong value is to write a right one over it.
    #
    # It supersedes rather than replaces: the entry is read at `latest`
    # and nothing pins a version, so the old one is left enabled and
    # unused. Destroying it would need a role that can also destroy the
    # key, which is the thing this entry exists to keep.
    # Not silenced, and checked separately from whether it is empty. A
    # denial exits non-zero with empty stdout, and reading that as "no
    # versions" would add the one version that strands every backup.
    if ! VERSIONS=$(gcloud secrets versions list "$SECRET" --project="$PROJECT" \
                      --filter="state=ENABLED" --format='value(name)' 2>&1); then
      echo "cannot list versions of $SECRET:" >&2
      printf '%s\n' "$VERSIONS" | sed 's/^/  /' >&2
      echo "secretsAdmin carries the viewer role this needs" >&2
      exit 1
    fi
    if [[ -n "$VERSIONS" ]]; then
      if [[ "{{ supersede }}" != "yes" ]]; then
        echo "$SECRET already holds a key, and a later version does not" >&2
        echo "rotate it -- it strands every backup taken under the current" >&2
        echo "one. Where nothing has been backed up yet, say so:" >&2
        echo "  just queenswood-recovery-backup-key {{ env }} {{ label }} yes" >&2
        exit 1
      fi
      echo ">>> superseding the key in $SECRET, on your word that nothing" >&2
      echo ">>> has been backed up under it" >&2
    fi
    openssl rand 32 \
      | gcloud secrets versions add "$SECRET" --project="$PROJECT" --data-file=- \
        >/dev/null
    echo ">>> $SECRET now holds a key. It is the only copy, deliberately."

# The folder and the capabilities bound on it, which is what a plane is
# later built against. Separate from the plane's manifest because the
# folder outlives it and may not be ours at all -- see ADR-0027. Which
# folder it is, and who holds what, are stated in the environment.
#
# Print the subsidiary manifest. Takes nothing: it is the code alone.
queenswood-subsidiary-manifest:
    #!/usr/bin/env zsh
    set -e
    # stdout carries the manifest and nothing else, so this can be
    # redirected straight into a file.
    #
    # The code and nothing else. Which folder, and whether we made it,
    # is stated in environment.yml, so a handover edits one file --
    # queenswood-environment-manifest is what takes the parent, the name
    # and the id.
    CODE="{{ CODE }}" \
    python3 -c 'import os,sys,string; d=string.Template(open(sys.argv[1]).read()).substitute(os.environ); sys.stdout.write("\n".join(l for l in d.split("\n") if l.strip()) + "\n")' \
      infra/platform/templates/subsidiary.yml.tmpl

# Render the installation's environment, beside its manifest. Takes the
# folder's parent to compose one, or a folder id to adopt one instead.
queenswood-environment-manifest parent=PARENT name="" folder="" recovery="" billing="":
    #!/usr/bin/env zsh
    set -e
    # The installation's first file, and the one the rest is derived
    # against, so it makes its own directory rather than expecting a
    # manifest to have made one. What it declares -- who holds which
    # capability, and which folder this is -- is known before anything
    # is built, which is why it comes before the boundary rather than
    # after the plane.
    ROOT={{ INSTALLATIONS_REPO }}/{{ CODE }}
    FILE="$ROOT/environment.yml"
    mkdir -p "$ROOT"
    # Committed is the line, as it is for a unit: nothing has read an
    # uncommitted file, so re-rendering costs nothing, and a committed
    # one may carry an edit this cannot reproduce.
    if [[ -e "$FILE" ]]; then
      if git -C {{ INSTALLATIONS_REPO }} rev-parse --git-dir >/dev/null 2>&1 \
         && git -C {{ INSTALLATIONS_REPO }} cat-file -e \
              "HEAD:{{ CODE }}/environment.yml" 2>/dev/null; then
        echo "{{ CODE }} already has a committed environment:" >&2
        echo "  $FILE" >&2
        echo "edit it" >&2
        exit 1
      fi
      echo ">>> replacing the uncommitted $FILE" >&2
    fi

    # Read off the management project where there is one, and asked of
    # the identity where there is not. This file is written before the
    # plane is built, so on a first installation there is no project to
    # read -- and the account is a property of the identity anyway, which
    # holds billing.user on exactly one. Not silenced either way: a
    # denial exits non-zero with empty stdout, and an empty billing
    # account renders a file that composes projects nothing pays for.
    BILLING="{{ billing }}"
    if [[ -z "$BILLING" ]]; then
      if MGMT=$(just _gcp-mgmt-project 2>/dev/null) && [[ -n "$MGMT" ]]; then
        if ! OUT=$(gcloud billing projects describe "$MGMT" \
                     --format='value(billingAccountName)' 2>&1); then
          echo "cannot read the billing account of $MGMT:" >&2
          printf '%s\n' "$OUT" | sed 's/^/  /' >&2
          exit 1
        fi
        BILLING="${OUT#billingAccounts/}"
      else
        BILLING=$(just _gcp-billing-account)
      fi
    fi
    if [[ -z "$BILLING" ]]; then
      echo "no billing account; link one, or pass it, then re-run" >&2
      exit 1
    fi

    # Which folder, and whether we made it. folderId adopts an existing
    # one and is the only field wanted in that mode: an adopted folder
    # is named and placed by whoever owns it, and the composite reads
    # both back rather than asserting them. Composing one needs the
    # parent and the name instead, and the two sets are exclusive.
    FOLDER="{{ folder }}"
    if [[ -n "$FOLDER" ]]; then
      case "$FOLDER" in
        folders/*) : ;;
        *) FOLDER="folders/$FOLDER" ;;
      esac
      FOLDER_BLOCK=$(printf '    folderId: "%s"' "$FOLDER")
      echo ">>> adopting $FOLDER" >&2
    else
      if [[ -z "{{ parent }}" ]]; then
        echo "no folder to adopt and no parent to compose one under" >&2
        echo "export QW_PARENT, or pass a folder to adopt:" >&2
        echo "  just queenswood-environment-manifest '' '' folders/<folder-id>" >&2
        exit 1
      fi
      # Deterministic, because the code is what tells one subsidiary
      # from another. Pass a name where the folder is handed to you
      # already named.
      NAME="{{ name }}"
      if [[ -z "$NAME" ]]; then
        NAME="fldr-{{ CODE }}"
        echo ">>> naming the folder $NAME" >&2
      fi
      # Refuse to compose a second folder by accident. GCP allows the
      # duplicate display name, so this is the only thing that would
      # notice -- and with a deterministic name it is what catches a
      # rebuild that meant to adopt.
      case "{{ parent }}" in
        organizations/*) SCOPE="--organization=${${:-{{ parent }}}#organizations/}" ;;
        folders/*)       SCOPE="--folder=${${:-{{ parent }}}#folders/}" ;;
        *) echo "parent must be organizations/<id> or folders/<id>" >&2; exit 1 ;;
      esac
      EXISTING=$(gcloud resource-manager folders list $SCOPE \
                   --filter="displayName=$NAME" \
                   --format='value(name)' 2>/dev/null | head -1 || true)
      if [[ -n "$EXISTING" ]]; then
        echo "a folder named $NAME already exists under {{ parent }}: folders/$EXISTING" >&2
        echo "adopt it:  just queenswood-environment-manifest '' '' folders/$EXISTING" >&2
        echo "or choose a different name" >&2
        exit 1
      fi
      FOLDER_BLOCK=$(printf '    parent: "%s"\n    displayName: "%s"' "{{ parent }}" "$NAME")
    fi

    # Captured before it is used rather than substituted inline: a
    # denial exits non-zero with empty stdout, and an empty domain
    # renders every principal as `group:grp-...@`, which IAM rejects
    # only once the manifest is applied.
    if ! DOMAIN=$(just _gcp-domain); then
      exit 1
    fi
    if [[ -z "$DOMAIN" ]]; then
      echo "no organisation domain; the access mapping would name" >&2
      echo "principals with no domain at all" >&2
      exit 1
    fi

    CODE="{{ CODE }}" BILLING="$BILLING" \
    MANIFEST_REPO_URL="{{ MANIFEST_REPO_URL }}" \
    DOMAIN="$DOMAIN" FOLDER_BLOCK="$FOLDER_BLOCK" \
    PLATFORM_VIEWER_GROUP="{{ PLATFORM_VIEWER_GROUP }}" \
    PLATFORM_ADMIN_GROUP="{{ PLATFORM_ADMIN_GROUP }}" \
    CLUSTER_ADMIN_GROUP="{{ CLUSTER_ADMIN_GROUP }}" \
    SECRETS_ADMIN_GROUP="{{ SECRETS_ADMIN_GROUP }}" \
    python3 -c 'import os,sys,string; d=string.Template(open(sys.argv[1]).read()).substitute(os.environ); sys.stdout.write("\n".join(l for l in d.split("\n") if l.strip()) + "\n")' \
      infra/platform/templates/environment.yml.tmpl > "$FILE"

    echo ">>> wrote $FILE"
    echo
    echo "  billing   $BILLING"
    echo "  access    4 capabilities on $DOMAIN"

# Print the nameservers Cloud DNS assigned the installation's zone.
queenswood-zone-nameservers domain="":
    #!/usr/bin/env zsh
    set -e
    # The domain is read from the installation's own manifest, which
    # names it before the plane composes anything: two answers here
    # would name a zone nothing made.
    DOMAIN="{{ domain }}"
    if [[ -z "$DOMAIN" ]]; then
      INSTALLATION={{ INSTALLATIONS_REPO }}/{{ CODE }}/installation.yml
      if [[ ! -f "$INSTALLATION" ]]; then
        echo "no manifest at $INSTALLATION" >&2
        exit 1
      fi
      DNS_BLOCK=$(grep -A1 '^  dns:' "$INSTALLATION" || true)
      DOMAIN=$(printf '%s\n' "$DNS_BLOCK" \
                 | sed -n 's/^ *domain: *"\{0,1\}\([^"]*\)"\{0,1\}$/\1/p')
    fi
    if [[ -z "$DOMAIN" ]]; then
      echo "no dns.domain for {{ CODE }}; merge it, then re-run" >&2
      exit 1
    fi
    # Named the way the composition names it, so nobody has to know the
    # scheme to read the answer.
    ZONE="dz-{{ CODE }}-c-${DOMAIN//./-}"
    if ! OUT=$(kubectl --context {{ MGMT_CTX }} -n crossplane-system \
                 get managedzone "$ZONE" \
                 -o jsonpath='{.status.atProvider.nameServers}' 2>&1); then
      echo "cannot read $ZONE on {{ MGMT_CTX }}:" >&2
      printf '%s\n' "$OUT" | sed 's/^/  /' >&2
      exit 1
    fi
    if [[ -z "$OUT" || "$OUT" == "[]" ]]; then
      echo "$ZONE has no nameservers yet; it is still being composed" >&2
      exit 1
    fi
    printf '%s\n' "$OUT" | tr -d '[]"' | tr ',' '\n'

# Print the installation's dns block, to paste into its manifest.
queenswood-dns-manifest-snippet domain:
    #!/usr/bin/env zsh
    set -e
    # A snippet rather than a manifest: every other -manifest here
    # writes the file it names, and this one cannot.
    # The records come from what the domain answers today rather than
    # from anyone retyping them: dns-carried decides what a new zone has
    # to keep, and this only shapes it. stdout carries the block and
    # nothing else.
    #
    # Printed rather than written, unlike every other manifest here: the
    # file it belongs in is committed and carries comments, and nothing
    # can insert into that safely.
    if ! FACTS=$(just dns-carried "{{ domain }}"); then
      exit 1
    fi
    COUNT=$(printf '%s' "$FACTS" | jq '.verification | length')
    if [[ "$COUNT" -eq 0 ]]; then
      echo "{{ domain }} carries no verification token" >&2
      echo "a zone cannot be composed for a domain nobody has proved" >&2
      exit 1
    fi
    # One TXT entry at the apex carrying every token and SPF, and _dmarc
    # beside it. Built here because a record list varies in length,
    # which a template cannot. The two quote characters are passed in
    # rather than escaped: an rrdata needs both, and shell is the wrong
    # place to spell that.
    RECORDS=$(printf '%s' "$FACTS" | jq -r --arg q "'" --arg d '"' '
      "      - type: TXT",
      "        rrdatas:",
      ([.verification[]
        | "          - " + $q + $d + "google-site-verification=" + . + $d + $q]
       | join("\n")),
      ([.spf[] | "          - " + $q + $d + . + $d + $q] | join("\n")),
      "      - name: " + $d + "_dmarc" + $d,
      "        type: TXT",
      "        rrdatas:",
      ([.dmarc[] | "          - " + $q + $d + . + $d + $q] | join("\n"))
      | select(. != "")')

    DOMAIN="{{ domain }}" RECORDS="$RECORDS" \
    python3 -c 'import os,sys,string; sys.stdout.write(string.Template(open(sys.argv[1]).read()).substitute(os.environ))' \
      infra/platform/templates/dns.yml.tmpl

    echo >&2
    echo ">>> paste this into spec in {{ CODE }}/installation.yml" >&2
