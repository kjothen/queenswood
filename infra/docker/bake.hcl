// Build every Queenswood service image in parallel from the
// shared parameterised Dockerfile. `docker buildx bake` reuses
// the heavy Clojure base layer across all targets.
//
//   TAG=dev docker buildx bake -f infra/docker/bake.hcl

variable "REGISTRY" { default = "ghcr.io/repldriven" }
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
  targets = concat(services, ["bank-app", "bank-console"])
}

// Multi-platform builds are gated on the `MULTI_ARCH` variable so
// local `docker buildx bake` (the just docker-build-* recipes) stays
// fast and single-arch, while CI publishes both linux/amd64 and
// linux/arm64. Single-arch is selected by leaving the variable empty
// (the default) — `platforms = []` lets BuildKit fall back to the
// host platform.
variable "MULTI_ARCH" { default = "" }

platforms_default = MULTI_ARCH == "1" ? ["linux/amd64", "linux/arm64"] : []

target "service" {
  name       = svc
  matrix     = { svc = services }
  context    = "."
  dockerfile = "infra/docker/service/Dockerfile"
  args       = { PROJECT_NAME = svc }
  tags       = ["${REGISTRY}/${svc}:${TAG}"]
  platforms  = platforms_default
  output     = ["type=docker"]
}

// Frontend SPA — Node build → nginx serve. Separate target because
// the Dockerfile and base images are unrelated to the JVM services
// above; the Clojure base layer wouldn't share.
target "bank-app" {
  context    = "."
  dockerfile = "infra/docker/bank-app/Dockerfile"
  tags       = ["${REGISTRY}/bank-app:${TAG}"]
  platforms  = platforms_default
  output     = ["type=docker"]
}

// Human-identity SPA: sign-in + onboarding + dashboard. Same Node →
// nginx pattern as bank-app, with the SPA reading /env.js at runtime
// so a single image works under both kind (Keycloak port-forward)
// and GKE (Keycloak at https://keycloak.<env>.repldriven.com).
target "bank-console" {
  context    = "."
  dockerfile = "infra/docker/bank-console/Dockerfile"
  tags       = ["${REGISTRY}/bank-console:${TAG}"]
  platforms  = platforms_default
  output     = ["type=docker"]
}
