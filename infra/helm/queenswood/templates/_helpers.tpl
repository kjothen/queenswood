{{/*
Common naming + labels for Queenswood templates.
*/}}

{{- define "queenswood.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "queenswood.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{/* Force Always when tag is `:latest` so re-pulls actually happen */}}
{{- define "queenswood.imagePullPolicy" -}}
{{- if eq .Values.image.tag "latest" -}}Always{{- else -}}{{ .Values.image.pullPolicy }}{{- end -}}
{{- end -}}

{{- define "queenswood.serviceFullname" -}}
{{- $svc := .svc -}}
{{- $root := .root -}}
{{- printf "%s-%s" $root.Release.Name $svc | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
OTLP traces endpoint. An explicit otel.endpoint wins; otherwise the
in-chart Jaeger's Service is used when enabled. Empty disables the SDK
rather than failing, so no fallback is required.
*/}}
{{- define "queenswood.otelEndpoint" -}}
{{- if .Values.otel.endpoint -}}
{{ .Values.otel.endpoint }}
{{- else if .Values.jaeger.enabled -}}
http://{{ .Release.Name }}-jaeger:4318/v1/traces
{{- end -}}
{{- end -}}

{{/*
Kafka bootstrap servers. When the in-chart broker is enabled its
Service is `<release>-kafka` on :9092; otherwise services point at
an external broker via kafka.bootstrapServers.
*/}}
{{- define "queenswood.kafkaBootstrapServers" -}}
{{- if .Values.kafka.enabled -}}
{{ .Release.Name }}-kafka:9092
{{- else -}}
{{- required "kafka.bootstrapServers required when kafka.enabled=false" .Values.kafka.bootstrapServers -}}
{{- end -}}
{{- end -}}

{{/*
FDB cluster file ConfigMap name (the operator writes one with this
name once the FoundationDBCluster CR is reconciled).
*/}}
{{- define "queenswood.fdbClusterConfigMap" -}}
{{- printf "%s-fdb-config" .Release.Name -}}
{{- end -}}

{{/*
The blobstore URL the restore Job reads from. The backup CR does not
use this: the operator assembles its own URL from the structured fields
of `blobStoreConfiguration`.

The container is deliberately NOT the one this cluster backs up to.
`fdb.restore.backupName` names the generation the recorded version
belongs to, and only `fdb.backup.backupName` names where this cluster
writes. Sharing one container across generations is what made a restore
unreliable: a rebuilt cluster numbers its versions again from near
zero, so `fdbbackup describe` -- which picks the highest restorable
version -- keeps answering with the OLDEST generation present, not the
newest. Empty falls back to the backup container, which is correct only
where there has been exactly one generation.

Plaintext by construction (port 80, secure_connection=0): FDB's
blobstore client has no usable trust store and the operator cannot pass
it a CA, so the files are encrypted instead. See fdb-backup.yaml.
*/}}
{{- define "queenswood.fdbRestoreUrl" -}}
{{- $b := .Values.fdb.backup -}}
{{- $r := .Values.fdb.restore -}}
{{- if and $r.version (not $r.backupName) -}}
{{- fail "fdb.restore.version was given without fdb.restore.backupName. A version names a point inside one backup container, and each cluster generation now has its own -- so a version alone cannot say which container to look in. Falling back to the container this cluster writes to would read a different generation and restore the wrong data, or silently find nothing. Record the generation alongside the version (gcp-fdb-export does), or clear the version to restore that container's latest point." -}}
{{- end -}}
{{- $container := $r.backupName | default $b.backupName -}}
{{- $params := printf "bucket=%s&region=%s&secure_connection=0" $b.bucket $b.region -}}
{{- printf "blobstore://%s@%s:%v/%s?%s" $b.accessKeyId $b.endpoint $b.port $container $params -}}
{{- end -}}

{{/*
Names of the one-shot Jobs, defined once because four templates refer
to them and a name that disagrees is a gate that waits forever.

Each name carries a hash of that Job's own rendered pod spec. A pod
template is immutable once the Job exists, so anything changing what
the Job runs must also change its name or `helm upgrade` fails trying
to patch it -- and the spec is the only thing that tracks that without
being told. The previous suffix listed the inputs it knew about (image
tag, chart version, the restore and realm-import gates), which held
until a value outside the list reached a Job: `keycloak.baseUrl` did,
twice, and each time the release failed mid-upgrade and had to be
cleared by deleting the Job by hand.

A consequence worth knowing: a chart version bump that leaves both pod
specs untouched now reuses the same names, because there is genuinely
nothing to replace.
*/}}
{{- define "queenswood.migratorJobName" -}}
{{- printf "%s-migrator-%s" .Release.Name (include "queenswood.migratorPodSpec" . | sha256sum | trunc 10) -}}
{{- end -}}

{{- define "queenswood.bootstrapJobName" -}}
{{- printf "%s-bootstrap-%s" .Release.Name (include "queenswood.bootstrapPodSpec" . | sha256sum | trunc 10) -}}
{{- end -}}

{{/*
Name of the FDB restore Job. Defined once because the migrator gates on
it: the restore Job renders it as its own name, and `wait-for-restore`
resolves the same string. Two derivations of one name is a gate that
waits for a Job nobody created -- which is exactly what happened when
the name became content-addressed here and stayed version-derived
there.

Content-addressed on (container, version) rather than version alone,
because a restore can now be requested with no version at all -- the
container's latest point -- and two such restores from different
generations must not collide on one name.
*/}}
{{- define "queenswood.fdbRestoreJobName" -}}
{{- $r := .Values.fdb.restore -}}
{{- printf "%s-fdb-restore-%s" .Release.Name (printf "%s|%s" ($r.backupName | default "") ($r.version | default "") | sha256sum | trunc 10) -}}
{{- end -}}

{{/*
Whether a restore is being asked for at all. A container with no
version means "this container's latest point", so the version alone no
longer answers the question.
*/}}
{{- define "queenswood.fdbRestoreRequested" -}}
{{- if or .Values.fdb.restore.version .Values.fdb.restore.backupName -}}true{{- end -}}
{{- end -}}
