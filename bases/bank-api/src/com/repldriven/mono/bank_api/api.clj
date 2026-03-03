(ns com.repldriven.mono.bank-api.api
  (:require
    [com.repldriven.mono.bank-api.accounts.routes :as accounts]
    [com.repldriven.mono.bank-api.auth :as auth]
    [com.repldriven.mono.bank-api.organizations.routes :as organizations]
    [com.repldriven.mono.bank-api.schema :as schema]

    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.telemetry.interface :as telemetry]

    [malli.core :as m]
    [reitit.coercion.malli :as malli-coercion]
    [reitit.http :as http]
    [reitit.ring :as ring]))

(def ^:private coercion
  (malli-coercion/create {:options {:registry (merge (m/default-schemas)
                                                     schema/registry)}}))

(def ^:private exception-handlers
  {:reitit.coercion/request-coercion
   (fn [ex _req]
     {:status 400
      :body {:type "request-validation"
             :details (select-keys (ex-data ex) [:humanized :in])}})
   :reitit.coercion/response-coercion
   (fn [ex _req]
     {:status 500
      :body {:type "response-coercion"
             :details (select-keys (ex-data ex) [:humanized :in])}})})

(defn- routes
  [ctx]
  [["/openapi.json"
    {:get
     {:no-doc true
      :openapi
      {:info {:title "Bank API" :description "Banking API" :version "1.0.0"}
       :components
       {:securitySchemes
        {"adminAuth" {:type :http :scheme :bearer :description "Admin API key"}
         "orgAuth"
         {:type :http :scheme :bearer :description "Organization API key"}}}}
      :handler (server/standard-openapi-handler)}}]
   (into ["/v1"
          {:interceptors (concat telemetry/trace-span
                                 (:interceptors ctx)
                                 [auth/authenticate])}]
         (concat accounts/routes organizations/routes))])

(defn app
  [ctx]
  (http/ring-handler (http/router (routes ctx)
                                  (assoc-in (server/router-data
                                             exception-handlers)
                                   [:data :coercion]
                                   coercion))
                     (ring/routes (server/standard-openapi-ui-handler)
                                  (ring/create-default-handler))
                     server/standard-executor))
