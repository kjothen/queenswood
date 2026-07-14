# Add GET /v1/widgets/:id

## Background

`inputs/src/com/repldriven/mono/bank_api/routes.clj` defines
`bank-api`'s Reitit routes. The existing `GET /v1/accounts/:id` route
is a fully-specced sibling: its response body is a named, reusable
Malli schema (registered in `schemas.clj` and referenced by key, not
inlined), it declares the `api-key` security requirement, and it
carries a response example.

## Task

Add `GET /v1/widgets/:id`, following the same conventions as the
accounts route: a named response schema registered alongside
`::account`, the same security requirement, and an example.

Edit `inputs/src/com/repldriven/mono/bank_api/routes.clj`,
`inputs/src/com/repldriven/mono/bank_api/schemas.clj`, and add a
`get-widget` handler to
`inputs/src/com/repldriven/mono/bank_api/handlers.clj`.
