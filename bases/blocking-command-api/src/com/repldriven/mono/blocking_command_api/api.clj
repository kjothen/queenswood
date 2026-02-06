(ns com.repldriven.mono.blocking-command-api.api
  (:require [com.repldriven.mono.server.interface :as server]
            [com.repldriven.mono.blocking-command-api.commands.routes :as commands]
            [reitit.ring :as ring]
            [reitit.http :as http]))

(defn app
  [{:keys [pulsar-client mqtt-client]}]
  (http/ring-handler
   (http/router commands/routes server/standard-router-data)
   (ring/routes (ring/create-default-handler))
   {:executor server/standard-executor}))
