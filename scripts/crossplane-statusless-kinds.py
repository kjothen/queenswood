#!/usr/bin/env python3
"""Kinds a plane's Crossplane CRDs can never give a status to.

Reads `kubectl get crd -o json` on stdin. A kind can never carry a
status when no served version declares a `status` subresource and none
declares a `status` property: nothing will ever write one, so a health
check that waits for one waits for good.

Argo CD's compiled-in health scripts enumerate these by hand, in a
`has_no_status` list. Both scripts also read as `A or (B and C)` where
`(A or B) and C` was meant, which makes a nil status answer Healthy
before the list is consulted -- so a kind missing from the list is
invisible until that precedence is corrected, and then permanently
Progressing.

The plane does not run those scripts. It carries corrected copies, in
infra/helm/management-plane/templates/argocd-cm.yaml, and the lists
below are theirs -- so the diff is what this plane serves and the
copies do not name. When the upstream fix reaches a release this plane
runs, the copies go and these lists become Argo's again.
"""

import json
import sys

# What the chart's copies list, keyed by the group pattern they check.
# Update alongside argocd-cm.yaml.
HAS_NO_CONDITIONS = {
    "*.crossplane.io/*": [
        "Composition",
        "CompositionRevision",
        "DeploymentRuntimeConfig",
        "EnvironmentConfig",
        "ImageConfig",
        "ProviderConfig",
        "ProviderConfigUsage",
        "ControllerConfig",
        "StoreConfig",
        "ClusterProviderConfig",
        "ClusterProviderConfigUsage",
    ],
    "*.upbound.io/*": [
        "ProviderConfig",
        "ProviderConfigUsage",
        "StoreConfig",
        "ClusterProviderConfig",
        "ClusterProviderConfigUsage",
    ],
}


def script_for(group):
    """Which of the chart's two copies checks a group."""
    return "*.upbound.io/*" if group.endswith("upbound.io") else "*.crossplane.io/*"


def statusless(crd):
    served = [v for v in crd["spec"]["versions"] if v.get("served")]
    subresource = any("status" in (v.get("subresources") or {}) for v in served)
    schema = any(
        "status"
        in ((v.get("schema", {}).get("openAPIV3Schema", {}) or {}).get("properties", {}) or {})
        for v in served
    )
    return not subresource and not schema


def main():
    crds = json.load(sys.stdin)["items"]
    if not crds:
        print("no CRDs on stdin", file=sys.stderr)
        return 1

    found = {}
    for crd in crds:
        group = crd["spec"]["group"]
        kind = crd["spec"]["names"]["kind"]
        if statusless(crd):
            found.setdefault(script_for(group), set()).add(kind)

    print(f"read {len(crds)} CRDs")
    print()
    print("never carries a status:")
    rows = sorted(
        (script_for(c["spec"]["group"]), c["spec"]["group"], c["spec"]["names"]["kind"])
        for c in crds
        if statusless(c)
    )
    if not rows:
        print("  none")
    seen = set()
    for _, group, kind in rows:
        if (group, kind) in seen:
            continue
        seen.add((group, kind))
        print(f"  {group:<32} {kind}")

    incomplete = False
    for script, listed in sorted(HAS_NO_CONDITIONS.items()):
        print()
        missing = sorted(found.get(script, set()) - set(listed))
        print(f"missing from the {script} list:")
        if missing:
            incomplete = True
            for kind in missing:
                print(f"  {kind}")
        else:
            print("  none")

        # Listed but not status-less is not a fault. A ProviderConfig
        # carries a status only once something uses it, and listing it
        # is what stops an unused one reading as Progressing.
        spurious = sorted(set(listed) - found.get(script, set()))
        if spurious:
            print("listed, not status-less here (harmless):")
            for kind in spurious:
                print(f"  {kind}")

    if incomplete:
        print()
        print("A kind above reports Healthy while it provisions, and goes")
        print("Progressing for good once the precedence is corrected. Add it")
        print("to has_no_conditions in argocd-cm.yaml, and upstream.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
