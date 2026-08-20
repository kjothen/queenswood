{{- /*
  The instance, as one string, and the guard that stops it being a
  broken one.

  YAML reads a bare `n`, `y`, `no` and `yes` as booleans, so an
  unquoted `env: n` arrives here as false rather than "n". printf "%s"
  renders that as `%!s(bool=false)`, and what fails is the API server
  rejecting a resource name containing a '%' -- which reads as a
  templating fault in Argo rather than as a missing pair of quotes in
  the values.

  Failing here says so instead. Coercing would be worse: `false` is a
  name that applies cleanly and is wrong.
*/}}
{{- define "queenswood-config.instance" -}}
{{- range $field := (list "code" "env" "label") -}}
{{- $value := index $.Values $field -}}
{{- if not (kindIs "string" $value) -}}
{{- fail (printf "%s must be a string, and is %s. A bare n, y, no or yes in YAML is a boolean -- quote it." $field (kindOf $value)) -}}
{{- end -}}
{{- if not $value -}}
{{- fail (printf "%s is required" $field) -}}
{{- end -}}
{{- end -}}
{{- printf "%s-%s-%s" .Values.code .Values.env .Values.label -}}
{{- end -}}
