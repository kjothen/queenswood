# Scaffold a rate-limiter component

## Background

The workspace needs a new component wrapping a generic token-bucket
rate-limiting library. It is not specific to banking — any Polylith
workspace built on `mono` could use it, and it will eventually be
proposed upstream. `inputs/components/` is the components directory;
it currently has one existing, unrelated component
(`inputs/components/utility/`) for reference on shape.

## Task

Scaffold the new component at `inputs/components/<name>/`, matching
`utility`'s file layout: `deps.edn` and an `interface.clj` exposing a
single function `allow?` that delegates to `core.clj`. Choose `<name>`
and use it consistently across the directory path and namespace.

Create the new files under `inputs/components/`.
