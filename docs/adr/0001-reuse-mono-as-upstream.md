# 1. Reuse `mono` as upstream for shared infrastructure

## Status

Accepted.

## Context

Queenswood is a banking system. Like any backend of meaningful size it
needs a long list of infrastructure: persistence, message bus, HTTP
server, environment loading, error handling, logging, telemetry,
testcontainers, system lifecycle, code generation, JSON, encryption,
vault integration. None of it is bank-specific.

That infrastructure already exists as a separate, domain-independent
Polylith workspace with tests: [`mono`](https://github.com/kjothen/mono).
Mono is new — it does not have years of production behind it — but it is
the only place this code lives, and the bricks were built from the start
to be reusable substrate rather than carved out of an existing
application. The question is *whether and how* to reuse it. Everything
else about Queenswood's organisation — workspace structure, dependency
management, deployment shape — flows from the answer.

The options:

- **Re-implement infrastructure inside Queenswood.** Duplication of code
  that already exists; ongoing divergence as mono evolves; no upside.
- **Mono as a published Maven artifact.** Adds a release cadence between
  us and infra changes, and turns "fix a bug in `bank-payment` that
  exposes a `pulsar` issue" into a two-PR-two-repos dance.
- **Mono as a git submodule.** Friction-heavy and most tooling doesn't
  expect it.
- **Domain fork.** Pull mono's bricks directly into Queenswood's
  workspace via the standard git upstream-remote pattern; add `bank-*`
  components on top.

## Decision

We will reuse mono via a **domain fork**. Queenswood's workspace is a
*superset* of mono's: mono's bricks live at identical paths and
namespaces. Mono updates are pulled with `git merge upstream/main`.
Improvements that are not bank-specific are made in mono and pulled
down; bank-specific bricks are prefixed `bank-*` and live only in
Queenswood.

This decision implies Polylith as the workspace structure, since mono
is a Polylith workspace. Polylith brings clear interface boundaries,
brick-level test scoping, and projects-as-deployment-targets — all of
which serve a system at Queenswood's scale well, but they are
downstream of the mono-reuse decision rather than independently chosen.
We do not re-argue Polylith here; see
[the Polylith documentation](https://polylith.gitbook.io/polylith).

## Consequences

Easier:

- All of mono's infrastructure available immediately, with full source
  visibility and its existing tests. Every brick is editable,
  debuggable, and testable as if it were native to Queenswood —
  because, in the workspace, it is.
- "Fix the bug at the right level" becomes natural. A flaw exposed in
  `bank-payment` that traces to `pulsar` is fixed in `pulsar` upstream
  and pulled down. No two-PR-two-repos dance.
- Polylith's brick-level test scoping (`clojure -M:poly test
  brick:<x>`) and explicit dep graph fall out for free.
- The line between "domain" and "infrastructure" is enforced by the
  `bank-*` prefix. Touching an unprefixed brick is a signal the change
  should probably go upstream.

Harder:

- Merge cost. Local changes to a mono-origin brick conflict with
  upstream merges. The discipline is: avoid forking mono bricks
  in-place — change them upstream and pull the change down — unless a
  bank-specific divergence is genuinely justified. So far this has
  held.
- No reproducible "version" of mono. The mono code present in
  Queenswood is whatever was last merged. Acceptable for a
  single-developer research project; would need revisiting for
  multi-team production with separate release trains.
- New contributors must understand the dual-repo model. CLAUDE.md and
  this ADR set should make that navigable; the README links to mono.
- The workspace is larger than typical Polylith examples. Brick
  discoverability (`workspace.edn`, `bank-*` naming) matters more than
  in a from-scratch Polylith repo.
