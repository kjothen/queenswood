{{- /*
Hostname for the realm endpoint, combining the per-env subdomain
with env.domain (e.g. `keycloak.queenswood.repldriven.com`).
*/ -}}
{{- define "queenswood-keycloak.host" -}}
{{ .Values.host.subdomain }}.{{ .Values.env.domain }}
{{- end -}}

{{- /*
Common labels stamped on every Queenswood-owned resource (the Bitnami
subchart owns its own labels separately).
*/ -}}
{{- define "queenswood-keycloak.labels" -}}
app.kubernetes.io/name: queenswood-keycloak
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}
