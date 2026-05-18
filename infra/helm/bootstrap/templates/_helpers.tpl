{{- define "bootstrap.projectId" -}}
{{- required "bootstrap: .Values.gcpProjectId must be set (root-app passes this via --set or helm.parameters)" .Values.gcpProjectId -}}
{{- end -}}
