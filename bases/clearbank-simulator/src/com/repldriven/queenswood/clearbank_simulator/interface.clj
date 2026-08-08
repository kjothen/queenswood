(ns com.repldriven.queenswood.clearbank-simulator.interface
  "ClearBank stand-in: the Reitit handler that accepts payment
  instructions, registers webhooks, and fires the callbacks a real
  ClearBank would. Composed into a service by an aggregator base, which
  injects `app` into the simulator server's handler slot; requiring
  this namespace also registers the simulator's own system
  component-kinds."
  (:require
    [com.repldriven.queenswood.clearbank-simulator.system]

    [com.repldriven.queenswood.clearbank-simulator.api :as api]))

(defn app
  "Ring handler for the ClearBank simulator's HTTP surface.

  Args:
  - ctx: the started system's interceptor context, supplied by the
    server component the handler is injected into."
  [ctx]
  (api/app ctx))
