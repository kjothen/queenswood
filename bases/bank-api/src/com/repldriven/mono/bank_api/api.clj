(ns com.repldriven.mono.bank-api.api
  (:require
    [com.repldriven.mono.bank-api.auth :as auth]
    [com.repldriven.mono.bank-api.examples :as examples]
    [com.repldriven.mono.bank-api.schema :as schema]

    [com.repldriven.mono.bank-api.api-key.components :as api-key.components]
    [com.repldriven.mono.bank-api.api-key.examples :as api-key.examples]
    [com.repldriven.mono.bank-api.api-key.routes :as api-key]
    [com.repldriven.mono.bank-api.balance.components :as balance.components]
    [com.repldriven.mono.bank-api.balance.examples :as balance.examples]
    [com.repldriven.mono.bank-api.balance.routes :as balance]
    [com.repldriven.mono.bank-api.cash-account-product.components :as
     cash-account-product.components]
    [com.repldriven.mono.bank-api.cash-account-product.examples :as
     cash-account-product.examples]
    [com.repldriven.mono.bank-api.cash-account-product.routes :as
     cash-account-product]
    [com.repldriven.mono.bank-api.cash-account.components :as
     cash-account.components]
    [com.repldriven.mono.bank-api.cash-account.examples :as
     cash-account.examples]
    [com.repldriven.mono.bank-api.cash-account.routes :as cash-account]
    [com.repldriven.mono.bank-api.organization.components :as
     organization.components]
    [com.repldriven.mono.bank-api.organization.examples :as
     organization.examples]
    [com.repldriven.mono.bank-api.organization.routes :as organization]
    [com.repldriven.mono.bank-api.party.components :as party.components]
    [com.repldriven.mono.bank-api.party.examples :as party.examples]
    [com.repldriven.mono.bank-api.party.routes :as party]
    [com.repldriven.mono.bank-api.payment.components :as payment.components]
    [com.repldriven.mono.bank-api.payment.examples :as payment.examples]
    [com.repldriven.mono.bank-api.payment.routes :as payment]
    [com.repldriven.mono.bank-api.policy.components :as policy.components]
    [com.repldriven.mono.bank-api.policy.examples :as policy.examples]
    [com.repldriven.mono.bank-api.policy.routes :as policy]
    [com.repldriven.mono.bank-api.shared.components :as shared.components]
    [com.repldriven.mono.bank-api.shared.interceptors :as shared.interceptors]
    [com.repldriven.mono.bank-api.shared.parameters :as shared.parameters]
    [com.repldriven.mono.bank-api.simulate.components :as simulate.components]
    [com.repldriven.mono.bank-api.simulate.examples :as simulate.examples]
    [com.repldriven.mono.bank-api.simulate.routes :as simulate]
    [com.repldriven.mono.bank-api.payee-check.components :as
     payee-check.components]
    [com.repldriven.mono.bank-api.payee-check.examples :as
     payee-check.examples]
    [com.repldriven.mono.bank-api.payee-check.routes :as payee-check]
    [com.repldriven.mono.bank-api.tier.components :as tier.components]
    [com.repldriven.mono.bank-api.tier.examples :as tier.examples]
    [com.repldriven.mono.bank-api.tier.routes :as tier]
    [com.repldriven.mono.bank-api.transaction.components :as
     transaction.components]

    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.telemetry.interface :as telemetry]

    [malli.core :as m]
    [malli.transform :as mt]
    [reitit.coercion.malli :as malli-coercion]
    [reitit.http :as http]
    [reitit.ring :as ring]))

(def ^:private api-transformer
  "Transformer for :decode/api and :encode/api properties
  on malli schemas. Composed with the base transformers
  to coerce API-friendly enum values to/from internal
  prefixed keywords."
  (mt/transformer {:name :api}))

(defn- ->provider
  "Creates a reitit TransformationProvider that composes
  base-transformer with api-transformer."
  [base-transformer]
  (reify
   malli-coercion/TransformationProvider
     (-transformer [_ {:keys [strip-extra-keys default-values]}]
       (mt/transformer (when strip-extra-keys
                         (mt/strip-extra-keys-transformer))
                       base-transformer
                       api-transformer
                       (when default-values (mt/default-value-transformer))))))

(def ^:private coercion
  (malli-coercion/create
   {:transformers {:body {:default (->provider (mt/json-transformer))}
                   :string {:default (->provider (mt/string-transformer))}
                   :response {:default (->provider nil)}}
    ;; Keep `:compile mu/closed-schema` (reitit default) effective by
    ;; turning off `:strip-extra-keys`; otherwise the strip transformer
    ;; removes unknown keys before validation runs, so closed maps
    ;; never reject them. We want 400s for unexpected fields on both
    ;; query-params and request bodies.
    :strip-extra-keys false
    :options {:registry (merge (m/default-schemas)
                               {:unique-vector
                                shared.components/unique-vector-schema
                                :unique-vector-lax
                                shared.components/unique-vector-lax-schema
                                "ErrorResponse" schema/ErrorResponseSchema}
                               api-key.components/registry
                               balance.components/registry
                               cash-account-product.components/registry
                               cash-account.components/registry
                               organization.components/registry
                               party.components/registry
                               payee-check.components/registry
                               payment.components/registry
                               policy.components/registry
                               shared.components/registry
                               simulate.components/registry
                               tier.components/registry
                               transaction.components/registry)}}))

(defn- routes
  [ctx]
  [["/openapi.json"
    {:get {:no-doc true
           :openapi {:info {:title "Queenswood"
                            :description "Queenswood Banking API"
                            :version "1.0.0"}
                     :components
                     {:securitySchemes
                      {"adminAuth" {:type :http
                                    :scheme :bearer
                                    :description "Admin API key"}
                       "orgAuth" {:type :http
                                  :scheme :bearer
                                  :description "Organization API key"}}
                      :parameters shared.parameters/registry
                      :examples (merge
                                 examples/registry
                                 balance.examples/registry
                                 cash-account-product.examples/registry
                                 cash-account.examples/registry
                                 api-key.examples/registry
                                 organization.examples/registry
                                 party.examples/registry
                                 payee-check.examples/registry
                                 payment.examples/registry
                                 policy.examples/registry
                                 simulate.examples/registry
                                 tier.examples/registry)}}
           :handler (server/standard-openapi-handler)}}]
   (into ["/v1"
          {:interceptors (concat telemetry/trace-span
                                 (:interceptors ctx)
                                 [auth/authenticate
                                  auth/authorize])
           :responses {400 (schema/ErrorResponse [#'examples/BadRequest])
                       401 (schema/ErrorResponse [#'examples/Unauthorized])
                       403 (schema/ErrorResponse [#'examples/Forbidden])
                       500 (schema/ErrorResponse [#'examples/InternalServerError
                                                  #'examples/BadResponse])}}]
         (concat balance/routes
                 cash-account-product/routes
                 cash-account/routes
                 api-key/routes
                 organization/routes
                 party/routes
                 payee-check/routes
                 payment/routes
                 policy/routes
                 simulate/routes
                 tier/routes))])

(defn- add-interceptor-before-coerce
  "Splices `icept` into the router's global interceptor chain just
  before the coerce-request interceptor, so it can rewrite the
  query-params before malli sees them."
  [router-data icept]
  (update-in router-data
             [:data :interceptors]
             (fn [xs]
               (vec (mapcat (fn [i]
                              (if (= :reitit.http.coercion/coerce-request
                                     (:name i))
                                [icept i]
                                [i]))
                     xs)))))

(defn app
  [ctx]
  (http/ring-handler
   (http/router (routes ctx)
                (-> server/standard-router-data
                    (assoc-in [:data :coercion] coercion)
                    (add-interceptor-before-coerce
                     shared.interceptors/nest-bracket-query-params)))
   (ring/routes (server/standard-openapi-ui-handler)
                (server/standard-default-handler))
   server/standard-executor))
