# Justfile recipes

<!-- tessl-plugin: deployment -->

## Problem

You are writing or changing a `just` recipe: where it lives, what it
takes, how it reads, and how it fails.

## Solution

Fail loudly, take what the caller knows rather than discovering it, and
say the rest in the recipe under `docs/` instead of in the file.

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

The identity a recipe acts as is the same thing. A recipe that reaches
for a particular service account works only while that account exists
and can be impersonated, which couples it to one moment in a bootstrap:
`_gcp-billing-account sa` takes the identity and asks it, where the
same lookup that found the seed itself would work for nothing else.
Where a recipe can act as you and needs a fallback, the fallback is
named too, and with none given it stops rather than guessing.

### Variables

`VAR := "default"` ignores the environment. Declare anything an
operator may need to override as
`env_var_or_default("VAR", "default")`, which leaves `just --set` as
the per-run override and the environment as the per-shell one. That
covers a default that is only ours: the group answering a capability is
`env_var_or_default("QW_PLATFORM_VIEWER", …)` because an established
organisation answers it with something else entirely.

Justfile imports share one namespace, so where a constant is declared
is about where a reader looks. Declare it in the file that reads it,
and in `vars.just` — which holds nothing else — where more than one
does, or where it has to agree with one that is already there. `REGION`,
`REGION_CODE` and `ZONE` say one thing three times, so they sit
together and a change to one is visibly a change to all three.

A private helper goes with the domain it is about whoever calls it:
`_seed-sa` is `seed.just`'s even though `gcp.just` calls it three
times, and `_gcp-project filter` is generic, so it stays in `gcp.just`
and takes what varies as an argument.

### Names

Name a recipe for what it acts on, not for what it is made of, so the
list reads as a set of actions.

The name and the file agree: a recipe lives in the justfile for its
domain and carries that domain's prefix — `crossplane-` in
`crossplane.just`, `argo-` in `argo.just`, `gcp-` in `gcp.just`,
`tessl-` in `tessl.just`. `just --list` is one flat list, so the prefix
is what groups a domain's recipes in it, and the file is where somebody
looks for the one they half-remember. A recipe filed by what it uses
rather than by what it acts on is findable by neither.

### One line each, naming the parameters

Every recipe carries a one-line comment above it, and that line is what
`just --list` shows. Name the parameters in it, and the values a
parameter takes where they are fixed.

```
;; Bad
# Print the billing account.
# Print the capabilities.

;; OK
# Print the billing account `sa` may bill projects to.
# Print the capabilities in `scope` (org or installation, default both).
```

### A comment in a body guards an edit

A recipe body carries no commentary except where a reader would
otherwise make a specific edit that breaks it: silencing a redirect
that tells a denial from an absence, moving a check later than the call
it protects, adding a trailing newline to a credential, dropping a
capture that puts a kubectl context back. Each is one or two lines and
says what not to do.

Everything else — why an identity holds what it holds, what a
constraint prevents, which capability to join and when — is the
recipe's under `docs/`, where somebody reading about the act will find
it. A justfile that explains itself is a second copy of that recipe,
and it drifts.

### Order is call order

Constants first, then private helpers, then the recipes in the order
somebody runs them, with the ad-hoc ones last. `gcp.just` reads
capabilities, binds them, enforces the organisation's constraints,
writes a secret, and ends with the CIS scan — which nothing in a
bootstrap calls.

## Rules

**MUST:**

- Consume a failure you expect: `if cmd; then break; fi`, or `|| true`
  where emptiness is handled explicitly.
- Capture a command's output into a variable before piping it, so a
  denial is not read as an empty result.
- Use whatever the caller supplied, and discover only what they did
  not.
- Declare an overridable variable with `env_var_or_default`.
- Put a recipe in the justfile for the domain it acts on, prefixed with
  that domain's name. The prefix is what groups it in `just --list`.
- Give every recipe a one-line comment naming its parameters, and the
  values a parameter takes where they are fixed. That line is what
  `just --list` shows.
- Order a file as constants, private helpers, then recipes in the order
  they are run, with the ad-hoc ones last.
- Declare a constant in the file that reads it, and in `vars.just`
  where more than one does or where it has to agree with one already
  there. Put a private helper with the domain it is about, whoever
  calls it.
- Pass the identity a recipe acts as rather than discovering it, and
  stop rather than guessing where none is given.

**MUST NOT:**

- Write `cmd && break`, `[[ test ]] && cmd`, or a bare `VAR=$(cmd)`
  whose command may fail, inside a `set -e` recipe.
- Treat an instant exit with no output as anything other than `set -e`
  aborting before the recipe's first `echo`.
- Add a lookup for a value the caller already named. Discovery fails
  where an argument would have worked.
- Comment a recipe body except where a reader would otherwise make an
  edit that breaks it. Why it is that way belongs in the recipe under
  `docs/`.

## References

- [git-workflow](git-workflow.md) — the other conventions around
  running things in this repository.
