(ns com.repldriven.queenswood.onfido-adapter.interface
  "Inbound half of the Onfido adapter: the Reitit handler serving
  Onfido's check-completed webhook. Composed into a service by an
  aggregator base, which injects `app` into the adapter server's
  handler slot; requiring this namespace also registers the adapter's
  own system component-kinds."
  (:require
    [com.repldriven.queenswood.onfido-adapter.system]

    [com.repldriven.queenswood.onfido-adapter.api :as api]))

(defn app
  "Ring handler for the Onfido adapter's HTTP surface.

  Args:
  - ctx: the started system's interceptor context, supplied by the
    server component the handler is injected into."
  [ctx]
  (api/app ctx))
