# Crossplane kinds, what they compose, and what Crossplane may do to
# each composed resource.
#
# `crossplane-kinds` and `crossplane-policies` read the XRDs and
# Compositions in this repository: the question a design asks is what
# exists here, not what one cluster is running. The rest read a plane,
# because what is wrong with an installation is only there -- and each
# captures kubectl's output before piping, since a pipeline takes jq's
# status and a failed read would otherwise print a header and pass for
# an empty result.

# What each kind composes, by XRs and managed resources.
crossplane-kinds:
    #!/usr/bin/env zsh
    set -e
    python3 - <<'PY'
    import re, pathlib, collections
    # A composed resource is any `kind:` in a Composition that is not the
    # Composition itself, a provider config reference, or a function's own
    # input shape.
    SKIP = {"Composition", "ClusterProviderConfig", "GoTemplate",
            "Resources", "Input", "CompositeResourceDefinition"}
    rows = []
    for p in sorted(pathlib.Path("infra/platform/crossplane-xrds").glob("*-composition.yml")):
        t = p.read_text()
        own = re.search(r"compositeTypeRef:\s*\n\s*apiVersion:.*\n\s*kind:\s*(\w+)", t)
        own = own.group(1) if own else p.stem
        kinds = [k for k in re.findall(r"^\s*kind:\s+(\w+)\s*$", t, re.M)
                 if k not in SKIP and k != own]
        xrs = collections.Counter(k for k in kinds if k.startswith("X"))
        mrs = collections.Counter(k for k in kinds if not k.startswith("X"))
        composed = ", ".join(f"{k}x{v}" if v > 1 else k
                             for k, v in sorted(mrs.items()))
        # Readiness comes from whichever function sets it, so the last
        # step in the pipeline decides whether anything does.
        steps = re.findall(r"functionRef:\s*\n\s*name:\s*(\S+)", t)
        ready = "yes" if steps and steps[-1] == "function-auto-ready" else "NO"
        rows.append((own, sum(xrs.values()), sum(mrs.values()), ready, composed))
    w = max(len(r[0]) for r in rows)
    print(f"{'KIND':<{w}}  XRS  MRS  AUTO-READY  COMPOSED")
    for name, x, m, ready, composed in sorted(rows, key=lambda r: (r[1], r[2])):
        print(f"{name:<{w}}  {x:>3}  {m:>3}  {ready:>10}  {composed}")
    PY

# What Crossplane may do to each composed resource.
crossplane-policies:
    #!/usr/bin/env zsh
    set -e
    python3 - <<'PY'
    import re, pathlib, collections
    SKIP = {"GoTemplate", "Input", "Resources", "Composition",
            "ClusterProviderConfig"}
    rows, observed = [], []
    for p in sorted(pathlib.Path("infra/platform/crossplane-xrds").glob("*-composition.yml")):
        raw = p.read_text()
        own = re.search(r"compositeTypeRef:\s*\n\s*apiVersion:.*\n\s*kind:\s*(\w+)", raw)
        own = own.group(1) if own else ""
        t = raw.splitlines()
        deletes = managed = obs = 0
        for i, l in enumerate(t):
            if "managementPolicies:" not in l:
                continue
            pol = []
            for j in range(i + 1, min(i + 8, len(t))):
                m = re.match(r"\s*-\s+(\w+)\s*$", t[j])
                if not m:
                    break
                pol.append(m.group(1))
            kind = next((t[k].split(":", 1)[1].strip() for k in range(i, 0, -1)
                         if re.match(r"^\s*kind:\s+\w+\s*$", t[k])
                         and t[k].split(":", 1)[1].strip() not in SKIP | {own}), "?")
            if pol == ["Observe"]:
                obs += 1
                observed.append((p.stem.replace("-composition", ""), kind))
            elif "Delete" in pol:
                deletes += 1
            else:
                managed += 1
        rows.append((p.stem.replace("-composition", ""), deletes, managed, obs))
    w = max(len(r[0]) for r in rows)
    print(f"{'KIND':<{w}}  DELETES  MANAGED  OBSERVED")
    for name, d, m, o in rows:
        print(f"{name:<{w}}  {d:>7}  {m:>7}  {o:>8}")
    print()
    print("DELETES   Crossplane may destroy the cloud resource.")
    print("MANAGED   created and updated, never deleted.")
    print("OBSERVED  read only. Something else owns it.")
    if observed:
        print()
        for kind, what in observed:
            print(f"observed only: {kind} / {what}")
    PY

# Live managed resources by slot, with their management policies.
crossplane-slots ctx=MGMT_CTX:
    #!/usr/bin/env zsh
    set -e
    # Crossplane matches a composed resource by this annotation, not by
    # metadata.name, so a name already in this list belongs to whatever
    # composed it. POLICIES is the live value, which is what governs a
    # deletion -- the repository may already say something else.
    JSON=$(kubectl --context {{ ctx }} get managed -A -o json)
    jq -r '["SLOT","KIND","NAME","POLICIES"],
          (.items[] | [
            (.metadata.annotations["crossplane.io/composition-resource-name"]
              // "-"),
            .kind,
            .metadata.name,
            ((.spec.managementPolicies // ["*"]) | join(","))
          ]) | @tsv' <<< "$JSON" \
      | column -t -s $'\t'

# Every composite and managed resource not both synced and ready.
crossplane-unready ctx=MGMT_CTX:
    #!/usr/bin/env zsh
    set -e
    # A header line with nothing under it is the healthy answer.
    JSON=$(kubectl --context {{ ctx }} get composite,managed -A -o json)
    jq -r '["KIND","NAME","SYNCED","READY"],
          (.items[]
           | {k: .kind, n: .metadata.name,
              s: ([.status.conditions[]?
                    | select(.type=="Synced")][0].status // "-"),
              r: ([.status.conditions[]?
                    | select(.type=="Ready")][0].status // "-")}
           | select(.s != "True" or .r != "True")
           | [.k, .n, .s, .r]) | @tsv' <<< "$JSON" \
      | column -t -s $'\t'

# What one object's conditions say, as kind.group/name.
crossplane-conditions obj ctx=MGMT_CTX:
    #!/usr/bin/env zsh
    set -e
    # Synced, Ready and LastAsyncOperation report different failures.
    JSON=$(kubectl --context {{ ctx }} -n crossplane-system \
      get {{ obj }} -o json)
    jq -r '["TYPE","STATUS","REASON","MESSAGE"],
          (.status.conditions[] | [
            .type, .status, .reason, (.message // "-")
          ]) | @tsv' <<< "$JSON" \
      | column -t -s $'\t'

# Which field manager owns each of one object's forProvider fields.
crossplane-owners obj ctx=MGMT_CTX:
    #!/usr/bin/env zsh
    set -e
    # kubectl strips managedFields unless asked for them.
    JSON=$(kubectl --context {{ ctx }} -n crossplane-system \
      get {{ obj }} -o json --show-managed-fields)
    jq -r '["OWNER","FIELD"],
          (.metadata.managedFields[]
           | (if (.manager
                  | startswith("apiextensions.crossplane.io/composed"))
              then "composition"
              elif (.manager | contains("reference-resolver"))
              then "resolver"
              else .manager end) as $owner
           | (.fieldsV1["f:spec"]["f:forProvider"] // {} | keys[])
           | [$owner, ltrimstr("f:")]) | @tsv' <<< "$JSON" \
      | column -t -s $'\t'
