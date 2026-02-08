(ns com.repldriven.mono.iam-api.api
  (:require
   [com.repldriven.mono.server.interface :as server]

   [com.repldriven.mono.iam-api.v1.projects.service-accounts.routes :as
    service-accounts]

   [reitit.http :as http]
   [reitit.ring :as ring]))

(defn routes
  [ctx]
  [["/swagger.json"
    {:get {:no-doc true
           :swagger {:info {:title "IAM API"
                            :description "Identity and Access Managment API"}}
           :handler (server/standard-swagger-handler)}}]
   ["/v1" {:interceptors (:interceptors ctx)}
    ["/projects/{project-id}" {:parameters {:path {:project-id string?}}}
     (vec (concat (service-accounts/routes)))]]])

(defn app
  [ctx]
  (http/ring-handler (http/router (routes ctx) server/standard-router-data)
                     (ring/routes (server/standard-swagger-ui-handler)
                                  (ring/create-default-handler))
                     server/standard-executor))
