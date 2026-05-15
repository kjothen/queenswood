set shell := ["zsh", "-cu"]

DOMAIN_ALIASES := ":+bank"
DOCKER_REGISTRY := "ghcr.io/repldriven"

XP_CLUSTER := "xp-mp"
KIND_XP_CLUSTER := "kind-xp-mp"

# Active environment for cloud.just recipes. Override per invocation
# with `ENV=queenswood just <recipe>`. Must match the valueFiles entry
# in `infra/bootstrap/apps/queenswood-platform.yml`; both flip together
# when changing envs.
ENV := env_var_or_default("ENV", "queenswood-test")
ENV_DOMAIN := ENV + ".repldriven.com"
ENV_ZONE := ENV + "-zone"

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
