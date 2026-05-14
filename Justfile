set shell := ["zsh", "-cu"]

DOMAIN_ALIASES := ":+bank"
DOCKER_REGISTRY := "ghcr.io/repldriven"

XP_CLUSTER := "xp-mp"
KIND_XP_CLUSTER := "kind-xp-mp"

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
