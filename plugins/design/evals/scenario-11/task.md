# Wire up code generation for bank-widget-schema

## Background

`inputs/components/bank-widget-schema/deps.edn` is a new brick whose
protobuf record definitions need generating into a `gen/` folder.
`inputs/bases/build/src/com/repldriven/mono/build/proto.clj` already
exposes `gen-proto`, the shared generation logic every proto-backed
brick delegates to (see `bank-schema`'s existing `build.clj` and
`deps.edn` for the pattern to mirror).

## Task

- Add a `build.clj` to `bank-widget-schema` that delegates to
  `build/proto`'s `gen-proto`, matching `bank-schema`'s shim.
- Complete `bank-widget-schema/deps.edn`'s `:deps/prep-lib` entry and
  `:build` alias, matching `bank-schema`'s shape.

Create `inputs/components/bank-widget-schema/build.clj` and edit
`inputs/components/bank-widget-schema/deps.edn` in place.
