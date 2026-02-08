(ns com.repldriven.mono.blocking-command-api.api
  (:require
   [com.repldriven.mono.blocking-command-api.commands.routes :as commands]

   [com.repldriven.mono.server.interface :as server]

   [reitit.http :as http]
   [reitit.ring :as ring]))

(defn app
  [{:keys [pulsar-client mqtt-client]}]
  (http/ring-handler
   (http/router commands/routes server/standard-router-data)
   (ring/routes (ring/create-default-handler))
   server/standard-executor))
