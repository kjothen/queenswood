(ns com.repldriven.mono.iam-api.api
  (:require
    [com.repldriven.mono.iam-api.v1.projects.service-accounts.routes :as
     service-accounts]
    [com.repldriven.mono.iam-api.v1.projects.service-accounts.schema :as schema]

    [com.repldriven.mono.server.interface :as server]

    [malli.core :as m]
    [reitit.coercion.malli :as malli-coercion]
    [reitit.http :as http]
    [reitit.ring :as ring]))

(def ^:private coercion
  (malli-coercion/create {:options {:registry (merge (m/default-schemas)
                                                     schema/registry)}}))

(defn routes
  [ctx]
  [["/openapi.json"
    {:get {:no-doc true
           :openapi {:info {:title "IAM API"
                            :description "Identity and Access Management API"
                            :version "1.0.0"}}
           :handler (server/standard-openapi-handler)}}]
   ["/v1" {:interceptors (:interceptors ctx)}
    ["/projects/{project-id}"
     {:parameters {:path {:project-id [:ref "ProjectId"]}}}
     (vec (concat (service-accounts/routes)))]]])

(defn app
  [ctx]
  (http/ring-handler (http/router (routes ctx)
                                  (assoc-in server/standard-router-data
                                   [:data :coercion]
                                   coercion))
                     (ring/routes (server/standard-openapi-ui-handler)
                                  (ring/create-default-handler))
                     server/standard-executor))
