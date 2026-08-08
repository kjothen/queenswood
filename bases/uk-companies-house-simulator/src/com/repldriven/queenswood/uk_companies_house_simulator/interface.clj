(ns com.repldriven.queenswood.uk-companies-house-simulator.interface
  "Companies House stand-in: the Reitit handler that answers company
  lookups from a fixture set. Composed into a service by an aggregator
  base, which injects `app` into the simulator server's handler slot;
  requiring this namespace also registers the simulator's own system
  component-kinds."
  (:require
    [com.repldriven.queenswood.uk-companies-house-simulator.system]

    [com.repldriven.queenswood.uk-companies-house-simulator.api :as api]))

(defn app
  "Ring handler for the Companies House simulator's HTTP surface.

  Args:
  - ctx: the started system's interceptor context, supplied by the
    server component the handler is injected into."
  [ctx]
  (api/app ctx))
