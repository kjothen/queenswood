{{/*
Common naming + labels for Queenswood templates.
*/}}

{{- define "queenswood.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "queenswood.labels" -}}
{{ include "queenswood.podLabels" . }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{/*
Labels for a pod template, which is everything above except the chart
version. A pod template is immutable on a Job, so a label that moves
with the chart makes every version bump a rejected apply -- and the
Job names hash the pod spec, which these labels are not part of, so
the name does not move to absorb it. On a Deployment the same label
costs a rolling restart per bump. No selector references it: they
match on instance and component.
*/}}
{{- define "queenswood.podLabels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
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
specs untouched reuses the same names, because there is genuinely
nothing to replace. That only holds while nothing outside the spec
moves with the version -- `helm.sh/chart` in the pod template labels
did, and made every bump a rejected apply against an immutable
`spec.template`. Pod templates take `queenswood.podLabels` for that
reason.
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

{{- /*
Which Keycloak this release talks to.

  dev       the bundled single-pod Keycloak, H2 and start-dev, for kind
  operator  an instance the Keycloak Operator manages, on `postgres`
  external  Keycloak lives elsewhere; nothing is rendered for it and
            baseUrl is supplied

Checked once so an unknown value stops the render rather than falling
through to whichever branch happens to be last.
*/ -}}
{{- define "queenswood.keycloakMode" -}}
{{- $m := .Values.keycloak.mode -}}
{{- if not (has $m (list "dev" "operator" "external")) -}}
{{- fail (printf "keycloak.mode %q is not one of dev, operator, external" $m) -}}
{{- end -}}
{{ $m }}
{{- end -}}

{{- /*
The Service both modes publish Keycloak on. The operator names it after
its `Keycloak` resource and the dev Deployment is given the same name,
so nothing that reaches Keycloak has to know which mode is running.
*/ -}}
{{- define "queenswood.keycloakServiceName" -}}
{{ .Release.Name }}-keycloak-service
{{- end -}}

{{- /*
Where this release reaches Keycloak from inside the cluster: bank-api's
JWKS fetch and admin REST calls, and the bootstrap Job. The dev instance
serves under /keycloak so the console SPA's same-origin proxy carries a
consistent prefix; the operator's serves at the root of its Service.
*/ -}}
{{- define "queenswood.keycloakBaseUrl" -}}
{{- if .Values.keycloak.baseUrl -}}
{{ .Values.keycloak.baseUrl }}
{{- else if eq (include "queenswood.keycloakMode" .) "dev" -}}
http://{{ include "queenswood.keycloakServiceName" . }}:8080/keycloak
{{- else if eq (include "queenswood.keycloakMode" .) "operator" -}}
http://{{ include "queenswood.keycloakServiceName" . }}:8080
{{- else -}}
{{ required "keycloak.baseUrl is required when keycloak.mode is external" .Values.keycloak.baseUrl }}
{{- end -}}
{{- end -}}

{{- /*
Keycloak's published hostname. The subdomain is fixed -- Keycloak is at
`keycloak.` wherever it runs -- and the domain is supplied, because
where this installation lives is not the chart's to know.
*/ -}}
{{- define "queenswood.keycloakHost" -}}
{{ .Values.keycloak.host.subdomain }}.{{ required "keycloak.host.domain is required to publish Keycloak on a hostname" .Values.keycloak.host.domain }}
{{- end -}}

{{- /*
The issuer: what Keycloak embeds in every token's `iss` claim however
the request reached it, and therefore the exact string every verifier
compares against. Derived once here so the instance and the services
reading its tokens cannot disagree -- which is the whole reason
Keycloak lives in this chart rather than beside it.

A published hostname is the issuer when there is one, because that is
what a browser is redirected to; without one, everything that reads a
token is in this cluster and the Service is. Supplying a domain
therefore moves the issuer, which is intended -- tokens minted under
the old one stop verifying.
*/ -}}
{{- define "queenswood.keycloakIssuer" -}}
{{- if .Values.keycloak.issuer -}}
{{ .Values.keycloak.issuer }}
{{- else if and (eq (include "queenswood.keycloakMode" .) "operator") .Values.keycloak.host.domain -}}
https://{{ include "queenswood.keycloakHost" . }}
{{- else -}}
{{ include "queenswood.keycloakBaseUrl" . }}
{{- end -}}
{{- end -}}

{{- /*
The Secret holding admin REST credentials for the bootstrap Job's
signing-key push. The operator mints one for its own instance; the dev
instance has none and the dev credentials are used instead.
*/ -}}
{{- define "queenswood.keycloakAdminSecret" -}}
{{- if .Values.keycloak.adminSecret.name -}}
{{ .Values.keycloak.adminSecret.name }}
{{- else if eq (include "queenswood.keycloakMode" .) "operator" -}}
{{ .Release.Name }}-keycloak-initial-admin
{{- end -}}
{{- end -}}

{{- /*
Postgres, for whatever in this release wants a relational database.
Instance-scoped rather than Keycloak's: a Cloud SQL instance hosts many
databases, and the proxy in front of it is shared by everything that
reaches one.

  off      no database is provisioned by or for this release
  local    an in-chart StatefulSet, for kind
  cloudsql the Cloud SQL Auth Proxy in front of an instance the
           installation's composite provisioned
*/ -}}
{{- define "queenswood.postgresProvider" -}}
{{- $p := .Values.postgres.provider -}}
{{- if not (has $p (list "off" "local" "cloudsql")) -}}
{{- fail (printf "postgres.provider %q is not one of off, local, cloudsql" $p) -}}
{{- end -}}
{{ $p }}
{{- end -}}

{{- define "queenswood.postgresHost" -}}
{{- $p := include "queenswood.postgresProvider" . -}}
{{- if eq $p "local" -}}
{{ .Release.Name }}-postgres
{{- else if eq $p "cloudsql" -}}
{{ .Release.Name }}-cloudsql
{{- else -}}
{{- fail "postgres.provider is off, so nothing in this release may ask for a database host" -}}
{{- end -}}
{{- end -}}

{{- /*
The database user. Cloud SQL names an IAM service account user after
the account's address with the `.gserviceaccount.com` suffix removed,
so it is derived from the one value that also annotates the proxy's
Kubernetes service account -- the two cannot then name different
principals, and no password exists on either side.
*/ -}}
{{- define "queenswood.postgresUser" -}}
{{- $p := include "queenswood.postgresProvider" . -}}
{{- if eq $p "cloudsql" -}}
{{- $sa := required "postgres.cloudsql.proxy.serviceAccount is required" .Values.postgres.cloudsql.proxy.serviceAccount -}}
{{- trimSuffix ".gserviceaccount.com" $sa -}}
{{- else -}}
{{ .Values.postgres.local.user }}
{{- end -}}
{{- end -}}

{{- define "queenswood.keycloakExpectedIssuer" -}}
{{- default (printf "%s/realms/%s" (include "queenswood.keycloakIssuer" .) .Values.keycloak.realm) .Values.keycloak.expectedIssuer -}}
{{- end -}}

{{- define "queenswood.keycloakOpsExpectedIssuer" -}}
{{- default (printf "%s/realms/%s" (include "queenswood.keycloakIssuer" .) .Values.keycloak.opsRealm) .Values.keycloak.opsExpectedIssuer -}}
{{- end -}}
