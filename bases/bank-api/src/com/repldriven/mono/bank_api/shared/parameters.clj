(ns com.repldriven.mono.bank-api.shared.parameters
  "Reusable OpenAPI `components.parameters` entries. Header parameters
  can't be produced as `$ref`s by reitit's malli expansion, so they
  live here as raw OpenAPI fragments, registered once in `api.clj`
  under `:components {:parameters parameters/registry}` and referenced
  per-route via `:openapi {:parameters parameters/ref-...}`."
  (:require
    [com.repldriven.mono.bank-api.shared.components :as shared.components]

    [malli.json-schema :as mjs]))

(def IdempotencyKey
  "`components.parameters` entry for the `Idempotency-Key` header.
  The JSON Schema is derived from the shared malli `IdempotencyKey`
  and inlined — reitit's openapi assembler clobbers any manually
  provided `components.schemas` with its auto-walked set, so we don't
  rely on a `$ref` to a shared component schema here."
  {:name "Idempotency-Key"
   :in "header"
   :required true
   :schema (mjs/transform shared.components/IdempotencyKey)
   :example "01jsx6k7h0abfdv8qpm2ytn3we"})

(def ref-idempotency-key
  "Splice into `:openapi {:parameters ...}` on any route that uses
  `require-idempotency-key`. Carries `^:replace` meta so meta-merge
  discards any auto-generated parameter of the same name."
  ^:replace [{:$ref "#/components/parameters/IdempotencyKey"}])

(def PageQuery
  "Cursor-paginated `page` query parameter. Uses OpenAPI 3's
  `deepObject` / `explode: true` so clients wire-serialise as
  `page[after]=x&page[size]=20`. `additionalProperties: false`
  matches the malli `[:map {:closed true} ...]` that validates the
  incoming request after the nest-bracket interceptor rewrites it.

  `size` is declared as an integer so fuzzers don't feed non-numeric
  strings through to the handler; `after`/`before` have a min length
  so blank cursors are rejected at validation rather than silently
  treated as \"no cursor\"."
  {:name "page"
   :in "query"
   :required false
   :style "deepObject"
   :explode true
   :schema {:type "object"
            :additionalProperties false
            :properties {:after {:type "string"
                                 :minLength 1
                                 :maxLength 200
                                 :description "Cursor for next page"}
                         :before {:type "string"
                                  :minLength 1
                                  :maxLength 200
                                  :description "Cursor for previous page"}
                         :size {:type "integer"
                                :minimum 1
                                :maximum 100
                                :description "Page size"}}}})

(def EmbedQuery
  "`embed` query parameter for optional sub-resource embedding on
  cash-account GET endpoints. deepObject-styled so clients send
  `embed[balances]=true&embed[transactions]=false`."
  {:name "embed"
   :in "query"
   :required false
   :style "deepObject"
   :explode true
   :schema {:type "object"
            :additionalProperties false
            :properties {:balances {:type "boolean"
                                    :description "Embed balances"}
                         :transactions {:type "boolean"
                                        :description "Embed transactions"}}}})

(def ref-page
  "Splice into `:openapi {:parameters ...}` for list-style endpoints
  that accept cursor pagination via `page[*]`. Carries `^:replace`
  meta so meta-merge drops the auto-walked `page` parameter reitit
  would otherwise emit from the malli schema."
  ^:replace [{:$ref "#/components/parameters/PageQuery"}])

(def ref-embed
  "Splice into `:openapi {:parameters ...}` for cash-account GETs
  that support `embed[*]` sub-resource expansion. Carries `^:replace`
  meta so meta-merge drops the auto-walked `embed` parameter reitit
  would otherwise emit from the malli schema."
  ^:replace [{:$ref "#/components/parameters/EmbedQuery"}])

(def ref-page-and-embed
  "Combined `$ref` splice for list-style endpoints that accept both
  `page` and `embed` deepObject params (e.g. `GET /v1/cash-accounts`).
  Carries `^:replace` meta — `into` would otherwise strip the metadata
  from `ref-page` via `transient`."
  ^:replace
  [{:$ref "#/components/parameters/PageQuery"}
   {:$ref "#/components/parameters/EmbedQuery"}])

(def registry
  "Map of OpenAPI parameter component name → parameter object. Merged
  into the top-level OpenAPI `:components :parameters` in `api.clj`."
  {"IdempotencyKey" IdempotencyKey
   "PageQuery" PageQuery
   "EmbedQuery" EmbedQuery})
