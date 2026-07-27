// Build every Queenswood service image in parallel from the
// shared parameterised Dockerfile. `docker buildx bake` reuses
// the heavy Clojure base layer across all targets.
//
//   TAG=dev docker buildx bake -f infra/docker/bake.hcl

variable "REGISTRY" { default = "ghcr.io/repldriven/queenswood" }
variable "TAG"      { default = "dev" }

services = [
  "migrator-service",
  "bootstrap-service",
  "api-service",
  "monolith-service",
  "financial-processors-service",
  "operational-processors-service",
  "scheduler-processor-service",
  "relay-service",
  "clearbank-adapter-service",
  "clearbank-simulator-service",
  "onfido-adapter-service",
  "onfido-simulator-service",
  "uk-companies-house-simulator-service",
]

group "default" {
  targets = concat(services, ["console"])
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

// Human-identity SPA: sign-in + onboarding + dashboard. Node →
// nginx, with the SPA reading /env.js at runtime so a single image
// works under both kind (Keycloak port-forward) and GKE (Keycloak
// at https://keycloak.<env>.repldriven.com).
target "console" {
  context    = "."
  dockerfile = "infra/docker/console/Dockerfile"
  tags       = ["${REGISTRY}/console:${TAG}"]
  platforms  = platforms_default
  output     = ["type=docker"]
}
