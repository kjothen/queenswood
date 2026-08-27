# Crossplane kinds, what they compose, and what Crossplane may do to
# each composed resource.
#
# The first two read the XRDs and Compositions in this repository: the
# question a design asks is what exists here, not what one cluster is
# running. `crossplane-slots` reads a plane, because a slot name has to
# be checked against the objects that already carry one.

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

# The slot name every live managed resource was composed under.
crossplane-slots ctx=MGMT_CTX:
    #!/usr/bin/env zsh
    set -e
    # Crossplane matches a composed resource by this annotation, not by
    # metadata.name, so a name already in this list belongs to whatever
    # composed it.
    kubectl --context {{ ctx }} get managed -A -o json \
      | jq -r '["SLOT","KIND","NAME"],
          (.items[] | [
            (.metadata.annotations["crossplane.io/composition-resource-name"]
              // "-"),
            .kind,
            .metadata.name
          ]) | @tsv' \
      | column -t -s $'\t'
