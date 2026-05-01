# 10. Code generation via `:deps/prep-lib` and co-located `build.clj`

## Status

Accepted.

## Context

Some bricks need code generated from a source artefact rather than
hand-written. The current case is `bank-schema`: protobuf record
definitions and the Clojure interop code that talks to FoundationDB's
Record Layer are generated from `.proto` files. Other generation
needs may appear later (for example, bindings to a published API
schema).

Generation has well-known failure modes:

- **Committed generated code** goes stale, drifts across
  contributors and machines, and clutters every diff with hundreds
  of auto-gen lines.
- **Build-system plugins** (Maven, Gradle) drag a second build DAG
  alongside Clojure's deps tooling, with its own knowledge tax.
- **Custom run-scripts** (shell, Babashka) work but live outside the
  well-known `clj -X:deps prep` idiom that Clojure tooling already
  understands.

Clojure's `tools.deps` ships a `:deps/prep-lib` mechanism precisely
for this case: a brick declares it needs preparation, the work is
defined in code, and the standard deps tooling runs it on demand.

## Decision

We will use Clojure's standard `:deps/prep-lib` mechanism for all
code generation. The pattern, applied per brick:

- The brick's `deps.edn` declares `:deps/prep-lib` with an `:fn` and
  the `:ensure` path that signals the prep is up-to-date.
- A `build.clj` co-located in the brick implements the generation
  logic. The function named in `:fn` is the entry point.
- Generated code lands in a `gen/` source folder inside the brick.
- The `gen/` folder is gitignored *locally* via a per-brick
  `.gitignore`. Generated code is never committed.
- Regeneration is a deliberate command:

  ```
  clj -X:deps prep :aliases '[:dev]'
  ```

  After source-schema changes, force regeneration:

  ```
  clj -X:deps prep :aliases '[:dev]' :force true
  ```

For Queenswood's bank-specific generation (currently the proto
bricks under `bank-schema`), the alias set is `:+bank :dev` rather
than just `:dev`, because the bank-specific source files are gated
behind the `:+bank` alias.

## Consequences

Easier:

- One command regenerates everything that needs it. `clj -X:deps
  prep` is the same shape across bricks; the tooling does the rest.
- Generated code stays out of git. Reviews show source-schema diffs,
  not the thousands of auto-gen lines downstream of them.
- Consistency across machines and contributors: the tool is the
  Clojure deps version we all run, not a vendored binary or a
  Maven plugin.
- Standard mechanism. New Clojure contributors recognise the
  `:deps/prep-lib` shape; no custom build vocabulary to learn.

Harder:

- Prep is a manual or first-time-build step. New contributors must
  remember to run it; CI must run it before tests. Forgetting it
  produces runtime errors that look like real bugs.
- The `:force true` flag is non-obvious. After a source-schema
  change, the prep marker may indicate everything is up-to-date
  even though the inputs changed. Forgetting `:force` produces
  stale generated code with no warning.
- Each brick that needs generation ships its own `build.clj`. There
  is light duplication between them; an extracted helper in `mono`
  could reduce this, but each brick still needs an entry point.
- The `gen/` folder being gitignored means IDEs and REPLs need it
  on disk to navigate or autocomplete. First-time setup includes a
  prep step.

The convention is inherited from `mono` (ADR-0001); the generated
artefacts are Queenswood-specific.
