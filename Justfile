set shell := ["zsh", "-cu"]

DOMAIN_ALIASES := ":+bank"
DOCKER_REGISTRY := "ghcr.io/repldriven"

XP_CLUSTER := "xp-mp"
KIND_XP_CLUSTER := "kind-xp-mp"

# GCP region + compute zone. Single-zone footprint -- cluster and
# regional/zonal resources all live in europe-west2-a.
REGION := "europe-west2"
ZONE := REGION + "-a"

# Active queenswood environment for cloud.just recipes. Override per
# invocation with `QUEENSWOOD_ENV=queenswood just <recipe>`. Must match
# the valueFiles entry in `infra/bootstrap/apps/queenswood-platform.yml`;
# both flip together when changing envs.
QUEENSWOOD_ENV := env_var_or_default("QUEENSWOOD_ENV", "queenswood-test")
QUEENSWOOD_DOMAIN := QUEENSWOOD_ENV + ".repldriven.com"
# Cloud DNS ManagedZone resource name (not a compute zone).
QUEENSWOOD_DNS_ZONE := QUEENSWOOD_ENV + "-zone"

# List all available recipes
list:
    just --list

import 'justfiles/github.just'
import 'justfiles/build.just'
import 'justfiles/cloud.just'
import 'justfiles/docker.just'
import 'justfiles/deploy.just'
import 'justfiles/bank.just'
import 'justfiles/telemetry.just'
import 'justfiles/test.just'
import 'justfiles/dev.just'
