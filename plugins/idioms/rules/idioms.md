# Queenswood Clojure idioms

Write new Clojure to these conventions — the load-bearing "Critical
guardrails" from CLAUDE.md, stated as *how to write*, not what to avoid.
Deterministic linters (semgrep in the pre-commit hook) catch
regressions; these rules keep you from introducing them.

## Return anomalies, don't throw across a boundary

A component `interface.clj` returns a value or an anomaly — it never
raises. Produce a domain rejection with `error/reject` (originating in
`domain.clj`); convert an exception at a library edge with
`error/try-nom` / `error/try-nom-ex`. Thread fallible steps with
`let-nom>` / `nom->`. A genuinely unrecoverable `throw` is rare and
carries `;; nosemgrep: no-raw-throw` on the line above.
See [error-handling](../../../docs/recipes/error-handling.md),
[ADR-0005](../../../docs/adr/0005-error-handling-with-anomalies.md).

## IDs and timestamps come from `utility`

`util/uuidv7` for IDs, `util/now` for timestamps. Never `random-uuid`,
`UUID/randomUUID`, `Instant/now`, or `System/currentTimeMillis` outside
`components/utility/` — that brick is the only place those primitives are
called. For any non-`clojure.core` helper, check `utility` first.
See [common-helpers](../../../docs/recipes/common-helpers.md).

## Tests drive the system with `with-test-system`

Manage system lifecycle with `with-test-system`; assert
anomaly-freeness with `nom-test>`. Never `use-fixtures`.
See [testing](../../../docs/recipes/testing.md).

## Cross a brick boundary only through `interface.clj`

Reach another component or base through its `interface.clj` (a base may
require another base's `.system`). Never require another unit's `.core`
/ `.store` / `.domain`; if the symbol you need isn't on the interface,
add it there first. Wrap every third-party library in exactly one brick
and consume the wrapper, never the library — and never list a component
in `deps.edn`.
See [components](../../../docs/recipes/components.md),
[ADR-0011](../../../docs/adr/0011-one-component-per-third-party-library.md).

## Comment the why, not the what

`interface.clj` docstrings are the documentation surface; impl files
stay bare. An inline `;;` comment is exceptional — only a load-bearing
*why* (invariant, workaround, upstream constraint), never the *what* the
code already says. Promote real context to a docstring; drop the rest.
See [ADR-0015](../../../docs/adr/0015-comments-and-docstrings.md).

## Everyday shape

kebab-case keyword keys throughout (string ISO-4217 currency the one
deliberate exception); `cond->` with `utility/assoc-some` /
`assoc-seq` over chains of optional `assoc`; destructure one map level
per `let` binding.
See [code-style](../../../docs/recipes/code-style.md),
[ADR-0006](../../../docs/adr/0006-kebab-case-keyword-keys.md).
