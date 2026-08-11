# Justfile recipes
<!-- tessl-plugin: deployment -->

## Problem

A recipe that fails silently is worse than one that fails loudly, and
`set -e` makes silence the default for three common shapes.

## Solution

Consume the failures you expect, and never rediscover what the caller
told you.

### set -e aborts more than it looks

Under `set -e`, each of these ends the *recipe*, not the loop or the
line:

- `cmd && break` — the list returns `cmd`'s status, so a loop written
  to wait through a transient condition aborts on its first failure.
- `VAR=$(cmd)` — a bare assignment whose command substitution fails.
  `kubectl get <name>` exits 1 when the resource does not exist, so a
  loop waiting *for* a resource aborts the moment it is absent.
- `[[ test ]] && cmd` — the same shape as the first.

The symptom is an instant exit with no output, because the recipe dies
before reaching its own `echo`. It then passes on the next run, once
the thing exists.

Consume the failure instead: `if cmd; then break; fi`, or `|| true`
where emptiness is handled on the following lines. Four retry loops in
this repository were not retrying at all, and each looked fine because
its first attempt had always happened to succeed.

### Capture before printing

`CMD | sed ...` takes `sed`'s exit status, so a denial prints nothing
and reads as an empty result. Capture into a variable, test it, then
print.

### Do not rediscover what you were given

A recipe that takes a parent and then discovers the organisation
anyway ignores what the caller supplied — and discovery can *fail*
where the argument would have succeeded, because it refuses when more
than one is visible. An expired token has the same shape.

### Variables

`VAR := "default"` ignores the environment. Declare anything an
operator may need to override as
`env_var_or_default("VAR", "default")`, which leaves `just --set` as
the per-run override and the environment as the per-shell one.

### Names

Name a recipe for what it acts on, not for what it is made of, so the
list reads as a set of actions.

## Rules

**MUST:**

- Consume a failure you expect: `if cmd; then break; fi`, or `|| true`
  where emptiness is handled explicitly.
- Capture a command's output into a variable before piping it, so a
  denial is not read as an empty result.
- Use whatever the caller supplied, and discover only what they did
  not.
- Declare an overridable variable with `env_var_or_default`.

**MUST NOT:**

- Write `cmd && break`, `[[ test ]] && cmd`, or a bare `VAR=$(cmd)`
  whose command may fail, inside a `set -e` recipe.
- Treat an instant exit with no output as anything other than `set -e`
  aborting before the recipe's first `echo`.
- Add a lookup for a value the caller already named. Discovery fails
  where an argument would have worked.

## References

- [git-workflow](git-workflow.md) — the other conventions around
  running things in this repository.
