(ns com.repldriven.mono.bank-clearbank-simulator.api
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.fps.components
     :as fps.components]
    [com.repldriven.mono.bank-clearbank-simulator.fps.examples
     :as fps.examples]
    [com.repldriven.mono.bank-clearbank-simulator.fps.routes :as fps]
    [com.repldriven.mono.bank-clearbank-simulator.schema :as schema]
    [com.repldriven.mono.bank-clearbank-simulator.simulate.components
     :as simulate.components]
    [com.repldriven.mono.bank-clearbank-simulator.simulate.examples
     :as simulate.examples]
    [com.repldriven.mono.bank-clearbank-simulator.simulate.routes
     :as simulate]
    [com.repldriven.mono.bank-clearbank-simulator.webhooks.components
     :as webhooks.components]
    [com.repldriven.mono.bank-clearbank-simulator.webhooks.examples
     :as webhooks.examples]
    [com.repldriven.mono.bank-clearbank-simulator.webhooks.routes
     :as webhooks]

    [com.repldriven.mono.bank-clearbank.interface :as clearbank]

    [com.repldriven.mono.server.interface :as server]

    [malli.core :as m]
    [malli.transform :as mt]
    [reitit.coercion.malli :as malli-coercion]
    [reitit.http :as http]
    [reitit.ring :as ring]))

(defn- ->provider
  [base-transformer]
  (reify
   malli-coercion/TransformationProvider
     (-transformer [_ {:keys [strip-extra-keys default-values]}]
       (mt/transformer
        (when strip-extra-keys
          (mt/strip-extra-keys-transformer))
        base-transformer
        (when default-values
          (mt/default-value-transformer))))))

(def ^:private coercion
  (malli-coercion/create
   {:transformers {:body {:default (->provider (mt/json-transformer))}
                   :string {:default (->provider (mt/string-transformer))}
                   :response {:default (->provider nil)}}
    :options {:registry (merge (m/default-schemas)
                               {"ErrorResponse" schema/ErrorResponseSchema}
                               fps.components/registry
                               simulate.components/registry
                               webhooks.components/registry
                               clearbank/webhook-components-registry)}}))

(def ^:private simulator-config {:webhooks (atom {}) :webhook-delay-ms 2000})

(defn- routes
  [_ctx]
  [["/openapi.json"
    {:get {:no-doc true
           :openapi
           {:info
            {:title "ClearBank Simulator"
             :description
             "Simulates ClearBank payment APIs for testing"
             :version "1.0.0"}
            :components
            {:examples (merge fps.examples/registry
                              simulate.examples/registry
                              webhooks.examples/registry
                              clearbank/webhook-examples-registry)}}
           :handler (server/standard-openapi-handler)}}]
   (into []
         (concat (fps/routes simulator-config)
                 (simulate/routes simulator-config)
                 (webhooks/routes simulator-config)))])

(defn app
  [ctx]
  (http/ring-handler
   (http/router (routes ctx)
                (assoc-in server/standard-router-data
                 [:data :coercion]
                 coercion))
   (ring/routes (server/standard-openapi-ui-handler)
                (ring/create-default-handler))
   server/standard-executor))
