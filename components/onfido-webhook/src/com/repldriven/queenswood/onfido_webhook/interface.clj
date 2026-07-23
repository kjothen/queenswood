(ns com.repldriven.queenswood.onfido-webhook.interface
  "Malli schemas and worked examples for the Onfido-shaped
  `check.completed` webhook payload that the bank's IDV adapter
  receives. Exposes a `component-registry` and `example-registry`
  consumed by the OpenAPI surface and request validation."
  (:require
    [com.repldriven.queenswood.onfido-webhook.components
     :as components]))

(def
  ^{:doc
    "Map of Onfido webhook schema name to Malli schema.
  Covers the `check.completed` envelope, its payload, and the
  embedded check object."}
  component-registry
  components/component-registry)

(def
  ^{:doc
    "Map of Onfido webhook schema name to a worked sample
  payload, used by Malli `:json-schema/example` annotations and the
  OpenAPI surface."}
  example-registry
  components/example-registry)
