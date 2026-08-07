{{- define "bootstrap.projectId" -}}
{{- required "bootstrap: .Values.gcpProjectId must be set (root-app passes this via --set or helm.parameters)" .Values.gcpProjectId -}}
{{- end -}}

{{- /*
Retry policy shared by every Application this chart renders.

Argo's default gives up after 5 attempts over a few minutes, and a
failed automated sync is not retried again afterwards -- selfHeal
corrects drift, not a sync that failed. That turns an ordinary
cold-start ordering problem into a permanent stop: provider-helm cannot
build a client until GKE publishes its kubeconfig connection secret
("currentContext not set in kubeconfig"), which is minutes away on a
cluster still being created, so the platform wave burns its budget and
stays Failed. Everything downstream then waits on resources that will
never be applied.

Backing off to 5 minutes over 20 attempts spans about an hour and a
half, which covers cluster creation and certificate issuance. A
genuinely broken manifest still surfaces immediately as a SyncError --
this only changes how long Argo keeps trying, not what it reports.

Declared here rather than driven by whatever is watching: the
management plane is a local kind cluster today and will not always be
one, and a recovery that depends on an operator running `gcp-up` stops
working the moment nobody is.
*/ -}}
{{- define "bootstrap.syncRetry" -}}
retry:
  limit: 20
  backoff:
    duration: 15s
    factor: 2
    maxDuration: 5m
{{- end -}}
