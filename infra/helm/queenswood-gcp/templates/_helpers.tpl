{{/*
Fail fast if gcpProjectId is missing -- every downstream resource
embeds it and a "" project ID surfaces only as a Crossplane error
later.
*/}}
{{- define "queenswood-gcp.projectId" -}}
{{- required "queenswood-gcp: .Values.gcpProjectId must be set (e.g. --set gcpProjectId=$(gcloud config get project))" .Values.gcpProjectId -}}
{{- end -}}

{{- define "queenswood-gcp.crossplaneSaMember" -}}
serviceAccount:{{ .Values.crossplaneSaName }}@{{ include "queenswood-gcp.projectId" . }}.iam.gserviceaccount.com
{{- end -}}

{{- define "queenswood-gcp.sqlProxyGcpSaMember" -}}
serviceAccount:{{ .Values.sqlProxyGcpSa }}@{{ include "queenswood-gcp.projectId" . }}.iam.gserviceaccount.com
{{- end -}}

{{- define "queenswood-gcp.sqlProxyWorkloadIdentityMember" -}}
serviceAccount:{{ include "queenswood-gcp.projectId" . }}.svc.id.goog[{{ .Values.envNamespace }}/{{ .Values.sqlProxyK8sSa }}]
{{- end -}}
