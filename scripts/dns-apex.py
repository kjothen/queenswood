#!/usr/bin/env python3
"""Compare an apex manifest against the zone it describes, and apply it.

The apex zone is declared in git and reconciled by nobody: this is what
a person runs instead. It never creates or deletes a zone, and it never
deletes a record -- extras are reported and left, because the zone this
acts on is the one that cannot be rebuilt. See docs/adr/0028.
"""

import json
import subprocess
import sys

import yaml

# Cloud DNS owns these at the apex and refuses to be told about them.
ZONE_OWNED = {"SOA", "NS"}


def fail(message):
    print(message, file=sys.stderr)
    sys.exit(1)


def gcloud(*args):
    result = subprocess.run(
        ["gcloud", *args], capture_output=True, text=True
    )
    if result.returncode != 0:
        fail(result.stderr.strip() or f"gcloud {' '.join(args)} failed")
    return result.stdout


def load(path):
    try:
        with open(path) as handle:
            spec = yaml.safe_load(handle)
    except OSError as error:
        fail(f"cannot read {path}: {error.strerror}")

    for field in ("domain", "projectId", "zone"):
        if not spec.get(field):
            fail(f"{path} states no {field}")

    domain = spec["domain"].rstrip(".")
    desired = {}

    for record in spec.get("records") or []:
        name = record.get("name", "")
        fqdn = f"{name}.{domain}." if name else f"{domain}."
        rrdatas = record["rrdatas"]

        if any("," in rrdata for rrdata in rrdatas):
            fail(f"{fqdn} {record['type']}: an rrdata holds a comma, "
                 "which gcloud reads as a separator")

        desired[(fqdn, record["type"])] = {
            "ttl": record.get("ttl", 300),
            "rrdatas": rrdatas,
        }

    return spec, desired


def live(spec):
    out = gcloud(
        "dns", "record-sets", "list",
        f"--zone={spec['zone']}",
        f"--project={spec['projectId']}",
        "--format=json",
    )
    apex = spec["domain"].rstrip(".") + "."

    return {
        (record["name"], record["type"]): {
            "ttl": record["ttl"],
            "rrdatas": record["rrdatas"],
        }
        for record in json.loads(out)
        if not (record["name"] == apex and record["type"] in ZONE_OWNED)
    }


def compare(desired, actual):
    missing = [key for key in desired if key not in actual]
    extra = [key for key in actual if key not in desired]
    differing = [
        key for key in desired
        if key in actual and actual[key] != desired[key]
    ]
    return missing, differing, extra


def show(label, keys, desired, actual):
    if not keys:
        return
    print(f"\n{label}:")
    for fqdn, rrtype in sorted(keys):
        print(f"  {fqdn} {rrtype}")
        if (fqdn, rrtype) in actual:
            print(f"    is:   {actual[(fqdn, rrtype)]}")
        if (fqdn, rrtype) in desired:
            print(f"    want: {desired[(fqdn, rrtype)]}")


def write(spec, verb, fqdn, rrtype, record):
    gcloud(
        "dns", "record-sets", verb, fqdn,
        f"--type={rrtype}",
        f"--ttl={record['ttl']}",
        "--rrdatas=" + ",".join(record["rrdatas"]),
        f"--zone={spec['zone']}",
        f"--project={spec['projectId']}",
    )
    print(f"  {verb}d {fqdn} {rrtype}")


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ("diff", "apply"):
        fail("usage: dns-apex.py diff|apply <apex.yml>")

    action, path = sys.argv[1], sys.argv[2]
    spec, desired = load(path)
    actual = live(spec)
    missing, differing, extra = compare(desired, actual)

    if not (missing or differing or extra):
        print(f"{spec['zone']} matches {path}")
        return

    show("absent from the zone", missing, desired, actual)
    show("different in the zone", differing, desired, actual)
    show("in the zone and not in the file", extra, desired, actual)

    if action == "diff":
        return

    print()
    for key in missing:
        write(spec, "create", key[0], key[1], desired[key])
    for key in differing:
        write(spec, "update", key[0], key[1], desired[key])

    if extra:
        print("\nleft alone. Removing a record from the apex is a "
              "deliberate act, not a consequence of editing a file.")


if __name__ == "__main__":
    main()
