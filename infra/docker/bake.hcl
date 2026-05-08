// Build every Queenswood service image in parallel from the
// shared parameterised Dockerfile. `docker buildx bake` reuses
// the heavy Clojure base layer across all targets.
//
//   TAG=dev docker buildx bake -f infra/docker/bake.hcl

variable "REGISTRY" { default = "ghcr.io/kjothen" }
variable "TAG"      { default = "dev" }

services = [
  "bank-migrator-service",
  "bank-bootstrap-service",
  "bank-api-service",
  "bank-cash-account-processor-service",
  "bank-party-processor-service",
  "bank-payment-processor-service",
  "bank-interest-processor-service",
  "bank-transaction-processor-service",
  "bank-idv-processor-service",
  "bank-clearbank-adapter-service",
  "bank-clearbank-simulator-service",
  "bank-onfido-adapter-service",
  "bank-onfido-simulator-service",
]

group "default" {
  targets = concat(services, ["bank-app"])
}

target "service" {
  name       = svc
  matrix     = { svc = services }
  context    = "."
  dockerfile = "infra/docker/service/Dockerfile"
  args       = { PROJECT_NAME = svc }
  tags       = ["${REGISTRY}/${svc}:${TAG}"]
  output     = ["type=docker"]
}

// Frontend SPA — Node build → nginx serve. Separate target because
// the Dockerfile and base images are unrelated to the JVM services
// above; the Clojure base layer wouldn't share.
target "bank-app" {
  context    = "."
  dockerfile = "infra/docker/bank-app/Dockerfile"
  tags       = ["${REGISTRY}/bank-app:${TAG}"]
  output     = ["type=docker"]
}
