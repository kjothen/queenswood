(ns com.repldriven.mono.symmetric-key-api.api
  (:require
   [com.repldriven.mono.symmetric-key-api.identities.routes :as identities]

   [com.repldriven.mono.server.interface :as server]

   [reitit.http :as http]
   [reitit.ring :as ring]))

(defn app
  [_ctx]
  (http/ring-handler
   (http/router (into ["/api"] identities/routes) server/standard-router-data)
   (ring/routes (ring/create-default-handler))
   server/standard-executor))
