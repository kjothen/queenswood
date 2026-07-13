# Queenswood Polylith framework

How to use Polylith itself in this workspace — the brick shape, the
boundary discipline, and the assemblies built from bricks. Independent
of Queenswood's own architecture; would apply to any Polylith
workspace built the same way.

## Cross a brick boundary only through `interface.clj`

Reach another component through its `interface.clj`; bases don't have
one, and the single base that composes other bases (`bank-monolith`)
reaches them via `.system` / `.api`, never `interface.clj`. Never
require another unit's `.core` / `.store` / `.domain`; if the symbol
you need isn't on the interface, add it there first. That reaching
happens from your own component's `core`/`domain`/`store`/etc. — never
from your own `interface.clj`, which requires only this component's
own local namespaces (plus `clojure.core`) and nothing from any other
brick, not even the `error`/`utility` wrapper bricks. Wrap every
third-party library in exactly one brick and consume the wrapper,
never the library — and never list a component in `deps.edn`.
See [components](../../../docs/recipes/components.md),
[bases](../../../docs/recipes/bases.md),
[ADR-0011](../../../docs/adr/0011-one-component-per-third-party-library.md).

## Bases are application entry points

A base owns `-main` and `(:gen-class)` in its entry namespace, parses
CLI args, builds the system definition, and starts it. It accesses
components the same way any component reaches a peer — through
`interface.clj` — and bare-requires every brick whose system
multimethods need to extend at startup. Bases never depend on other
bases and share nothing between them except through components; the
one bounded exception is `bank-monolith`, the designated multi-base
aggregator, which bare-requires other bases' `.system` namespaces to
extend their multimethods and requires their `.api` namespaces to
compose an in-process system for end-to-end tests.
See [bases](../../../docs/recipes/bases.md).

## Projects are pure config

A project in `projects/` is a `deps.edn` listing its components and
bases as `:local/root` paths — nothing else. Projects carry no
Clojure source and never define `-main` (a base does that); a
deployable project points its `:build` alias at `bases/build`. A
project MAY carry a `resources/` folder for deployment-scoped files
(`application.yml`, `logback.xml` / `logback-test.xml`, an optional
`bank/` subfolder of domain-scoped includes). Projects never depend
on other projects.
See [projects](../../../docs/recipes/projects.md).
