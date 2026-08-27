# Argo CD on a management plane.
#
# These read a cluster through a kubectl context and call no cloud API,
# so they take `ctx` rather than a project: MGMT_CTX is only the default.

# What each Application on a plane last did, and whether it finished.
argo-apps-status ctx=MGMT_CTX:
    #!/usr/bin/env zsh
    set -e
    # SYNC and HEALTH describe the comparison between git and the
    # cluster; PHASE and FINISHED describe the operation. An Application
    # whose sync never ended reads green in the first two and shows a
    # phase that is not Succeeded with no finish time.
    kubectl --context {{ ctx }} -n argocd get applications -o json \
      | jq -r '["NAME","SYNC","HEALTH","REV","PHASE","FINISHED"],
          (.items[] | [
            .metadata.name,
            (.status.sync.status // "-"),
            (.status.health.status // "-"),
            (.status.operationState.syncResult.revision // "-"
              | if . == "" then "-" else .[0:7] end),
            (.status.operationState.phase // "-"),
            (.status.operationState.finishedAt // "-")
          ]) | @tsv' \
      | column -t -s $'\t'

# What each Application's sync policy carries.
argo-apps-sync-policy ctx=MGMT_CTX:
    #!/usr/bin/env zsh
    set -e
    # An empty syncOptions list prints as a dash rather than an empty
    # field, or column shifts every column after it on that row.
    kubectl --context {{ ctx }} -n argocd get applications -o json \
      | jq -r '["NAME","SSA","RETRY","PRUNE"],
          (.items[] | [
            .metadata.name,
            (.spec.syncPolicy.syncOptions // []
              | any(. == "ServerSideApply=true")),
            (.spec.syncPolicy.retry.limit // "-"),
            .spec.syncPolicy.automated.prune
          ]) | @tsv' \
      | column -t -s $'\t'

# =========================================================================
# Which Crossplane kinds can never carry a status.
#
# Argo CD's compiled-in health scripts enumerate these by hand, and both
# read as `A or (B and C)` where `(A or B) and C` was meant -- so a nil
# status answers Healthy before the list is consulted, and a kind
# missing from the list is invisible until that precedence is corrected
# and then Progressing for good.
#
# Managed resources are not read. upjet gives every one of them a
# status, and their CRDs are large enough that fetching all of them
# costs hundreds of megabytes; the config-shaped kinds in a provider
# group are the ones that can lack a status, so those are fetched and
# the rest are not.
# =========================================================================

# Check the health scripts' kind lists against a plane's Crossplane CRDs.
argo-health-kinds ctx=MGMT_CTX:
    #!/usr/bin/env zsh
    set -e
    # What the copies in argocd-cm.yaml list. Update alongside them.
    # Plain variables rather than an associative array: a zsh subscript
    # containing a `*` is taken as a pattern and matches nothing.
    LISTED_CROSSPLANE="Composition CompositionRevision DeploymentRuntimeConfig
      EnvironmentConfig ImageConfig ProviderConfig ProviderConfigUsage
      ControllerConfig StoreConfig ClusterProviderConfig
      ClusterProviderConfigUsage"
    LISTED_UPBOUND="ProviderConfig ProviderConfigUsage StoreConfig
      ClusterProviderConfig ClusterProviderConfigUsage"

    # The pipeline's status is awk's, so a denial arrives as no names
    # rather than as a failure. Handled below rather than by set -e.
    NAMES=$(kubectl --context {{ ctx }} get crd \
              -o custom-columns='NAME:.metadata.name,GROUP:.spec.group' \
              --no-headers 2>/dev/null \
            | awk '$2 ~ /crossplane\.io$/ \
                   || ($2 ~ /upbound\.io$/ && $1 ~ /providerconfig|storeconfig/) \
                   { print $1 }')

    if [[ -z "$NAMES" ]]; then
      echo "no Crossplane CRDs readable on {{ ctx }}" >&2
      echo "check the context, and that you hold container.viewer" >&2
      exit 1
    fi

    # A kind can never carry a status when no served version declares a
    # status subresource and none declares a status property.
    ROWS=$(kubectl --context {{ ctx }} get crd ${(f)NAMES} -o json \
      | jq -r '
          def served: [.spec.versions[] | select(.served)];
          def statusless:
            (served | map(.subresources.status // empty) | length) == 0
            and (served | map(.schema.openAPIV3Schema.properties.status
                              // empty) | length) == 0;
          .items[]
          | select(statusless)
          | (if (.spec.group | endswith("upbound.io"))
             then "*.upbound.io/*" else "*.crossplane.io/*" end)
            + "\t" + .spec.group + "\t" + .spec.names.kind
        ' | sort -u)

    echo "never carries a status:"
    if [[ -z "$ROWS" ]]; then echo "  none"; fi
    printf '%s\n' "$ROWS" | awk -F'\t' 'NF { printf "  %-32s %s\n", $2, $3 }'

    INCOMPLETE=0
    for family in crossplane upbound; do
      case $family in
        crossplane) script='*.crossplane.io/*'; listed=$LISTED_CROSSPLANE ;;
        upbound)    script='*.upbound.io/*';    listed=$LISTED_UPBOUND ;;
      esac
      found=$(printf '%s\n' "$ROWS" \
              | awk -F'\t' -v s="$script" '$1 == s { print $3 }' | sort -u)
      listed=$(printf '%s\n' ${=listed} | sort -u)
      echo
      echo "missing from the $script list:"
      MISSING=$(comm -23 <(printf '%s\n' "$found") <(printf '%s\n' "$listed"))
      if [[ -z "$MISSING" ]]; then
        echo "  none"
      else
        INCOMPLETE=1
        printf '  %s\n' ${(f)MISSING}
      fi
      # Listed but not status-less is not a fault: a ProviderConfig
      # carries a status only once something uses it, and listing it is
      # what stops an unused one reading as Progressing.
      SPURIOUS=$(comm -13 <(printf '%s\n' "$found") <(printf '%s\n' "$listed"))
      if [[ -n "$SPURIOUS" ]]; then
        echo "listed, not status-less here (harmless):"
        printf '  %s\n' ${(f)SPURIOUS}
      fi
    done

    if (( INCOMPLETE )); then
      echo
      echo "A kind above reports Healthy while it provisions, and goes"
      echo "Progressing for good once the precedence is corrected. Add it"
      echo "to has_no_conditions in argocd-cm.yaml and to LISTED above."
    fi
