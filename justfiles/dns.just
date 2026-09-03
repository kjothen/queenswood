# =========================================================================
# What a domain is answering, as data.
#
# Neither GCP's nor Queenswood's: dig is neither, and the whole point of
# these is that a recipe which shapes a manifest reads facts from here
# rather than a recipe about a registrar knowing what a manifest is.
# See docs/recipes/infra/gcp-dns.md.
# =========================================================================

# The record types a domain carries at the apex, and the underscore
# names that carry policy. Read by dns-records, and the list to add to.
DNS_TYPES := "NS A AAAA MX TXT CAA DS SOA"
DNS_NAMES := "_dmarc _domainconnect _acme-challenge"

# Print what a domain answers today, as JSON.
dns-records domain resolver="":
    #!/usr/bin/env zsh
    set -e
    # A resolver may be named, which is how a delegation is checked
    # against an authority rather than against whatever is cached.
    if [[ -z "{{ domain }}" ]]; then
      echo "a domain is needed" >&2
      exit 1
    fi
    AT=""
    if [[ -n "{{ resolver }}" ]]; then
      AT="@{{ resolver }}"
    fi
    # An apex sweep alone misses what matters most: the records carrying
    # policy sit under names nothing advertises.
    #
    # Each list is sorted. A resolver rotates the order it answers in,
    # so an unsorted sweep diffs against itself.
    {
      printf '{"domain":"%s","apex":{' "{{ domain }}"
      FIRST=1
      for T in {{ DNS_TYPES }}; do
        OUT=$(dig +short $AT "$T" "{{ domain }}" 2>/dev/null || true)
        if [[ $FIRST -eq 0 ]]; then printf ','; fi
        FIRST=0
        printf '"%s":' "$T"
        printf '%s' "$OUT" | python3 -c 'import sys,json; print(json.dumps(sorted(l for l in sys.stdin.read().split(chr(10)) if l)), end="")'
      done
      printf '},"names":{'
      FIRST=1
      for N in {{ DNS_NAMES }}; do
        OUT=$(dig +short $AT TXT "$N.{{ domain }}" 2>/dev/null || true)
        if [[ $FIRST -eq 0 ]]; then printf ','; fi
        FIRST=0
        printf '"%s":' "$N"
        printf '%s' "$OUT" | python3 -c 'import sys,json; print(json.dumps(sorted(l for l in sys.stdin.read().split(chr(10)) if l)), end="")'
      done
      printf '}}\n'
    }

# Print what a new zone has to carry over: tokens, SPF and DMARC.
dns-carried domain:
    #!/usr/bin/env zsh
    set -e
    # What a placeholder site serves is not among them.
    # Decided here rather than by whoever reads the sweep, because the
    # answer is the same every time: a token proves ownership and
    # removing one takes that account's ownership with it, SPF states
    # the domain sends no mail, and DMARC is what makes that
    # enforceable. Everything else describes a page nobody depends on.
    just dns-records "{{ domain }}" | jq '{
      domain: .domain,
      verification: [.apex.TXT[] | ltrimstr("\"") | rtrimstr("\"")
                     | select(startswith("google-site-verification="))
                     | sub("^google-site-verification="; "")],
      spf: [.apex.TXT[] | ltrimstr("\"") | rtrimstr("\"")
            | select(startswith("v=spf1"))],
      dmarc: [.names._dmarc[] | ltrimstr("\"") | rtrimstr("\"")]
    }'

# =========================================================================
# The apex zone: declared in git, reconciled by nobody, applied here.
#
# It belongs to no installation, so these read a file rather than a
# manifest under an installation's directory, and none of them creates
# or deletes a record without being asked. See docs/adr/0028.
# =========================================================================

APEX := INSTALLATIONS_REPO / "apex.yml"

# Write apex.yml for `domain`, from what it answers today. Takes the project holding the zone.
dns-apex-manifest domain project file=APEX:
    #!/usr/bin/env zsh
    set -e
    # Refused rather than replaced. The delegations below the apex are
    # added a name at a time and are not regenerated from anything, so
    # re-rendering over a live file drops every one of them.
    if [[ -e "{{ file }}" ]]; then
      echo "{{ file }} already exists" >&2
      echo "edit it: the NS records in it are not re-rendered" >&2
      exit 1
    fi
    if ! FACTS=$(just dns-carried "{{ domain }}"); then
      exit 1
    fi
    COUNT=$(printf '%s' "$FACTS" | jq '.verification | length')
    if [[ "$COUNT" -eq 0 ]]; then
      echo "{{ domain }} carries no verification token" >&2
      echo "a zone cannot be composed for a domain nobody has proved" >&2
      exit 1
    fi
    RECORDS=$(printf '%s' "$FACTS" | jq -r --arg q "'" --arg d '"' '
      "  - type: TXT",
      "    rrdatas:",
      ([.verification[]
        | "      - " + $q + $d + "google-site-verification=" + . + $d + $q]
       | join("\n")),
      ([.spf[] | "      - " + $q + $d + . + $d + $q] | join("\n")),
      "  - name: " + $d + "_dmarc" + $d,
      "    type: TXT",
      "    rrdatas:",
      ([.dmarc[] | "      - " + $q + $d + . + $d + $q] | join("\n"))
      | select(. != "")')

    # Derived, not taken: the name follows the domain by rule, and a
    # parameter called zone invites the compute one.
    # See docs/recipes/practices/cloud-naming.md.
    ZONE="dz-c-${${:-{{ domain }}}//./-}"

    DOMAIN="{{ domain }}" PROJECT="{{ project }}" ZONE="$ZONE" \
    RECORDS="$RECORDS" \
    python3 -c 'import os,sys,string; sys.stdout.write(string.Template(open(sys.argv[1]).read()).substitute(os.environ))' \
      infra/platform/templates/apex.yml.tmpl > "{{ file }}"

    echo ">>> wrote {{ file }}"
    echo ">>> $ZONE in {{ project }}"
    echo ">>> read it, then create the zone: just dns-apex-zone-create"

# Create the apex zone named by `file`, as the verified domain owner. Refuses a second.
dns-apex-zone-create file=APEX:
    #!/usr/bin/env zsh
    set -e
    # Cloud DNS refuses a create by an identity that does not own the
    # name. That is the operator here, never a service account: the apex
    # is the one name no automation is made an owner of.
    read -r DOMAIN PROJECT ZONE <<<"$(python3 -c '
    import sys, yaml
    s = yaml.safe_load(open(sys.argv[1]))
    print(s["domain"], s["projectId"], s["zone"])' "{{ file }}")"

    # Captured rather than discarded. A denial and an absence both make
    # describe fail, and reading the first as the second creates a
    # second apex zone -- which draws a nameserver set that is never
    # returned. Only an explicit not-found is allowed to proceed.
    if OUT=$(gcloud dns managed-zones describe "$ZONE" \
               --project="$PROJECT" 2>&1); then
      echo ">>> $ZONE already exists; not creating a second"
      exit 0
    fi
    if ! grep -qiE 'not found|does not exist|NOT_FOUND' <<<"$OUT"; then
      echo "cannot tell whether $ZONE exists in $PROJECT:" >&2
      printf '%s\n' "$OUT" | sed 's/^/  /' >&2
      exit 1
    fi

    echo ">>> creating $ZONE for $DOMAIN as $(gcloud config get-value account 2>/dev/null)"
    echo ">>> this draws a nameserver set once and does not return it"
    if ! gcloud dns managed-zones create "$ZONE" \
           --dns-name="$DOMAIN." --description="$DOMAIN" \
           --visibility=public --project="$PROJECT" 2>/tmp/apex-err; then
      if grep -q verifyManagedZoneDnsNameOwnership /tmp/apex-err; then
        echo ">>> $DOMAIN is not verified for this account; opening Search Console" >&2
        gcloud domains verify "$DOMAIN"
        echo ">>> after verifying, rerun: just dns-apex-zone-create" >&2
        exit 1
      fi
      cat /tmp/apex-err >&2
      exit 1
    fi

# Report what the apex zone holds against what `file` says.
dns-apex-diff file=APEX:
    @python3 scripts/dns-apex.py diff "{{ file }}"

# Create and update the apex zone's records to match `file`. Deletes nothing.
dns-apex-apply file=APEX:
    @python3 scripts/dns-apex.py apply "{{ file }}"

# Print the four nameservers the apex zone named by `file` answers on.
dns-apex-nameservers file=APEX:
    #!/usr/bin/env zsh
    set -e
    read -r PROJECT ZONE <<<"$(python3 -c '
    import sys, yaml
    s = yaml.safe_load(open(sys.argv[1]))
    print(s["projectId"], s["zone"])' "{{ file }}")"

    if ! OUT=$(gcloud dns managed-zones describe "$ZONE" \
                 --project="$PROJECT" --format='value(nameServers)' 2>&1); then
      echo "cannot read $ZONE in $PROJECT:" >&2
      printf '%s\n' "$OUT" | sed 's/^/  /' >&2
      exit 1
    fi
    # gcloud joins a repeated field with ';', and has used ',' and a
    # space elsewhere. Split on any of them rather than on the one seen.
    printf '%s\n' "$OUT" | tr ';, \t' '\n\n\n\n' | sed '/^$/d'

# Create the organisation's DNS project. Reuses one where it exists.
dns-apex-project-create billing="" yes="":
    #!/usr/bin/env zsh
    set -e
    ORG=$(just _gcp-org-id)
    ME=$(gcloud config get-value account)

    BILLING="{{ billing }}"
    if [[ -z "$BILLING" ]]; then
      BILLING=$(gcloud billing accounts list --filter='OPEN=True' \
                  --format='value(ACCOUNT_ID)' | head -1 || true)
    fi
    if [[ -z "$BILLING" ]]; then
      echo "no open billing account; pass one as the first argument" >&2
      exit 1
    fi

    # Not silenced: a swallowed denial reads as "none exists" and mints
    # a second project, consuming an id for ever.
    if ! OUT=$(gcloud projects list --filter="labels.queenswood-tier=dns" \
                 --format='value(projectId)' 2>&1); then
      echo "cannot list projects:" >&2
      printf '%s\n' "$OUT" | sed 's/^/  /' >&2
      exit 1
    fi
    PROJECT=$(printf '%s\n' "$OUT" | head -1)

    if [[ -n "$PROJECT" ]]; then
      echo ">>> reusing $PROJECT"
    else
      PROJECT="prj-c-dns-$(openssl rand -hex 3)"
      echo "organization    $ORG"
      echo "billing         $BILLING"
      echo "as              $ME"
      echo "project         $PROJECT"
      echo
      echo "creates it outside every folder, links that billing account,"
      echo "and enables the DNS API on it. The id is consumed for good."
      echo
      if [[ -z "{{ yes }}" ]]; then
        printf 'proceed? [y/N] '
        read -r REPLY || REPLY=""
        if [[ "$REPLY" != [yY]* ]]; then
          echo "abandoned"
          exit 1
        fi
      fi
      gcloud projects create "$PROJECT" --name=prj-c-dns \
        --organization="$ORG" --labels=queenswood-tier=dns
      gcloud billing projects link "$PROJECT" \
        --billing-account="$BILLING" >/dev/null
    fi

    echo ">>> enabling the DNS API on $PROJECT"
    gcloud services enable --project="$PROJECT" dns.googleapis.com

    DNS_ADMIN=$(just _gcp-member "{{ DNS_ADMIN }}" "$(just _gcp-domain)")
    DNS_ROLE=$(jq -r '.capabilities[]
                      | select(.key == "dnsAdmin")
                      | .roles[].role' "{{ ROLES_FILE }}")
    if [[ -z "$DNS_ROLE" ]]; then
      echo "no role for dnsAdmin in {{ ROLES_FILE }}" >&2
      exit 1
    fi
    echo ">>> granting $DNS_ROLE to $DNS_ADMIN on $PROJECT"
    gcloud projects add-iam-policy-binding "$PROJECT" \
      --member="$DNS_ADMIN" --role="$DNS_ROLE" >/dev/null

    echo
    echo ">>> name $PROJECT in apex.yml as projectId"
