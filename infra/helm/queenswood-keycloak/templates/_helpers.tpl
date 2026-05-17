{{- /*
Hostname for the realm endpoint, combining the per-env subdomain
with env.domain (e.g. `keycloak.queenswood.repldriven.com`).
*/ -}}
{{- define "queenswood-keycloak.host" -}}
{{ .Values.host.subdomain }}.{{ .Values.env.domain }}
{{- end -}}

{{- /*
Common labels stamped on every queenswood-owned resource.
*/ -}}
{{- define "queenswood-keycloak.labels" -}}
app.kubernetes.io/name: queenswood-keycloak
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- /*
Hostname the Keycloak CR talks to for its database. Local mode
points at the in-chart Postgres Service; external mode points at
the in-chart cloud-sql-proxy Service that fronts CloudSQL.
*/ -}}
{{- define "queenswood-keycloak.dbHost" -}}
{{- if eq .Values.postgresql.mode "local" -}}
{{ .Release.Name }}-postgres
{{- else -}}
{{ .Values.postgresql.external.serviceName }}
{{- end -}}
{{- end -}}

{{- /*
Name of the Secret the Keycloak CR pulls db credentials from. Local
mode renders one alongside the Postgres StatefulSet; external mode
takes the caller-provided name.
*/ -}}
{{- define "queenswood-keycloak.dbSecret" -}}
{{- if eq .Values.postgresql.mode "local" -}}
{{ .Release.Name }}-postgres
{{- else -}}
{{ .Values.postgresql.external.secretName }}
{{- end -}}
{{- end -}}

{{- /*
Database name used by the Keycloak CR. Hard-coded to `keycloak` in
local mode (matches what the Postgres StatefulSet bootstraps).
*/ -}}
{{- define "queenswood-keycloak.dbName" -}}
{{- if eq .Values.postgresql.mode "local" -}}
keycloak
{{- else -}}
{{ .Values.postgresql.external.database }}
{{- end -}}
{{- end -}}

{{- /*
Database port (defaults to 5432 in local mode).
*/ -}}
{{- define "queenswood-keycloak.dbPort" -}}
{{- if eq .Values.postgresql.mode "local" -}}
5432
{{- else -}}
{{ .Values.postgresql.external.port }}
{{- end -}}
{{- end -}}
