(ns com.repldriven.queenswood.clearbank-adapter.interface
  "Inbound half of the ClearBank adapter: the Reitit handler serving
  ClearBank's webhook callbacks and the outbound Confirmation of Payee
  route the payee-check processor calls. Composed into a service by an
  aggregator base, which injects `app` into the adapter server's
  handler slot; requiring this namespace also registers the adapter's
  own system component-kinds."
  (:require
    [com.repldriven.queenswood.clearbank-adapter.system]

    [com.repldriven.queenswood.clearbank-adapter.api :as api]))

(defn app
  "Ring handler for the ClearBank adapter's HTTP surface.

  Args:
  - ctx: the started system's interceptor context, supplied by the
    server component the handler is injected into."
  [ctx]
  (api/app ctx))
