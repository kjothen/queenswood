{{/*
Refuse a half-restore.

The two stores are restored from independent values, and restoring one
without the other is the failure ADR-0022's pairing property exists to
prevent -- not a degraded outcome but a silently wrong one. FDB records
reference the Keycloak subject, so a restored bank beside a freshly
minted realm has no user who can reach it: the first login creates a
new subject, the bank treats it as a new customer, and builds a second
one alongside the first. A missing realm is obvious; an orphaned bank
is not. It is how the original bank was lost.

Checked here because this chart is the only place that sees both
halves, and checked at render time because the alternative is a Job
that can only complain once a realm already exists. A refused render
applies nothing.
*/}}
{{- define "queenswood-platform.assertRestorePairing" -}}
{{- $fdb := .Values.fdbRestore.version | default "" -}}
{{- $kc := .Values.keycloakRestore.realms | default "" -}}
{{- if and $fdb (not $kc) -}}
{{- fail (printf "fdbRestore.version is set (%v) but keycloakRestore.realms is empty. FDB would be restored beside a freshly minted realm, whose user ids match nothing in it -- the first login mints a new subject and the bank duplicates itself silently. Set both restore targets, or neither. `just gcp-down` records both together; a mismatch means one was cleared by hand." $fdb) -}}
{{- end -}}
{{- if and $kc (not $fdb) -}}
{{- fail (printf "keycloakRestore.realms is set (%v) but fdbRestore.version is empty. The realm would be restored over an empty FDB, so its users would reference banks that no longer exist. Set both restore targets, or neither." $kc) -}}
{{- end -}}
{{- end -}}
