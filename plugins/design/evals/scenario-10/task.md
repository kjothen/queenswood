# Wire up a testcontainer-backed widget-store group

## Background

`inputs/bases/bank-monolith/test-resources/bank/widget-store-test.yml`
declares test infrastructure for a new `widget-store` component that
needs a running container in tests. The `widget/container` component
kind (declared by the `testcontainers` brick) is already wired. The
high-level `widget/record-db` component kind needs a `host` value at
runtime, read off the started container.

## Task

Complete the YAML: add the `widget/host-reader` component kind
(registered in the `widget` brick's own `system/` folder — it reads
the running container's host) between the container and the
high-level `record-db` component, wiring it with `!system/local-ref`
the way `fdb`'s equivalent chain does. `record-db` should consume the
host reader's value, not the container directly.

Edit
`inputs/bases/bank-monolith/test-resources/bank/widget-store-test.yml`
in place.
