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
