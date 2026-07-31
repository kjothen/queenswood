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
