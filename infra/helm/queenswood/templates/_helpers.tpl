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

{{- define "queenswood.serviceFullname" -}}
{{- $svc := .svc -}}
{{- $root := .root -}}
{{- printf "%s-%s" $root.Release.Name $svc | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Pulsar broker URLs assembled from the subchart's release name.
The Apache Pulsar chart prefixes its services with both the
release name and the chart name (`pulsar`), so the broker
Service is `<release>-pulsar-broker`, exposing:
  - :6650 (binary)
  - :8080 (admin)
*/}}
{{- define "queenswood.pulsarServiceUrl" -}}
{{- if .Values.pulsar.enabled -}}
pulsar://{{ .Release.Name }}-pulsar-broker:6650
{{- else -}}
{{- required "pulsar.serviceUrl required when pulsar.enabled=false" .Values.pulsar.serviceUrl -}}
{{- end -}}
{{- end -}}

{{- define "queenswood.pulsarAdminUrl" -}}
{{- if .Values.pulsar.enabled -}}
http://{{ .Release.Name }}-pulsar-broker:8080
{{- else -}}
{{- required "pulsar.adminUrl required when pulsar.enabled=false" .Values.pulsar.adminUrl -}}
{{- end -}}
{{- end -}}

{{/*
Pulsar cluster name. The Apache Pulsar chart names its cluster
`<release>-pulsar`; tenants must be created with this exact
name in their allowedClusters list or the broker rejects the
tenant on creation.
*/}}
{{- define "queenswood.pulsarCluster" -}}
{{- if .Values.pulsar.enabled -}}
{{ .Release.Name }}-pulsar
{{- else -}}
{{- required "pulsar.cluster required when pulsar.enabled=false" .Values.pulsar.cluster -}}
{{- end -}}
{{- end -}}

{{/*
FDB cluster file ConfigMap name (the operator writes one with this
name once the FoundationDBCluster CR is reconciled).
*/}}
{{- define "queenswood.fdbClusterConfigMap" -}}
{{- printf "%s-fdb-config" .Release.Name -}}
{{- end -}}
