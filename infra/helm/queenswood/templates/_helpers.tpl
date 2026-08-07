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
use this: the operator assembles the same URL from the structured
fields of `blobStoreConfiguration`. What keeps the two in step is that
both derive from `fdb.backup.*` -- so a URL that disagrees means a
value was changed for one and not the other, and it names a container
that does not exist rather than the wrong one.

Plaintext by construction (port 80, secure_connection=0): FDB's
blobstore client has no usable trust store and the operator cannot pass
it a CA, so the files are encrypted instead. See fdb-backup.yaml.
*/}}
{{- define "queenswood.fdbBackupUrl" -}}
{{- $b := .Values.fdb.backup -}}
{{- $params := printf "bucket=%s&region=%s&secure_connection=0" $b.bucket $b.region -}}
{{- printf "blobstore://%s@%s:%v/%s?%s" $b.accessKeyId $b.endpoint $b.port $b.backupName $params -}}
{{- end -}}

{{/*
Names of the one-shot Jobs, defined once because four templates refer
to them and a name that disagrees is a gate that waits forever.

The suffix is what makes an upgrade possible at all: a Job's pod
template is immutable, so anything that changes the template must also
change the name, or `helm upgrade` fails trying to patch it. The image
tag and chart version cover new code and new template content. The
gate values cover the wait-for-restore and wait-for-realm-import
initContainers, which appear and disappear as they are declared -- a
change in template with no change in either of the other two.
*/}}
{{- define "queenswood.jobSuffix" -}}
{{- $gates := printf "%s|%s" (.Values.fdb.restore.version | default "") (.Values.keycloak.waitForRealmImport | default "") -}}
{{- if eq $gates "|" -}}
{{- printf "%s-%s" .Values.image.tag .Chart.Version -}}
{{- else -}}
{{- printf "%s-%s-%s" .Values.image.tag .Chart.Version (sha256sum $gates | trunc 6) -}}
{{- end -}}
{{- end -}}

{{- define "queenswood.migratorJobName" -}}
{{- printf "%s-migrator-%s" .Release.Name (include "queenswood.jobSuffix" .) -}}
{{- end -}}

{{- define "queenswood.bootstrapJobName" -}}
{{- printf "%s-bootstrap-%s" .Release.Name (include "queenswood.jobSuffix" .) -}}
{{- end -}}
