set shell := ["zsh", "-cu"]

DOMAIN_ALIASES := ":+bank"
DOCKER_REGISTRY := "ghcr.io/kjothen"

# List all available recipes
list:
    just --list

import 'justfiles/git.just'
import 'justfiles/build.just'
import 'justfiles/docker.just'
import 'justfiles/deploy.just'
import 'justfiles/bank.just'
import 'justfiles/telemetry.just'
import 'justfiles/test.just'
import 'justfiles/dev.just'
