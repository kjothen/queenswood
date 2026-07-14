# Register bank-widget-service for the dev environment

## Background

`inputs/infra/helm/queenswood/values.yaml` lists every deployable
service. You're setting up `bank-widget-service` for the dev
environment — it needs `bank-widget-simulator` ready before it
starts, the same `waitFor` shape already used by
`bank-clearbank-adapter` and `bank-onfido-adapter` for their
simulators.

## Task

Add an entry for the new service to `values.yaml`'s `services` list,
following the existing adapter entries' shape.

Edit `inputs/infra/helm/queenswood/values.yaml` in place.
