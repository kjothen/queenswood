(ns com.repldriven.mono.blocking-command-api.api
  (:require
    [com.repldriven.mono.blocking-command-api.commands.routes :as commands]

    [com.repldriven.mono.server.interface :as server]

    [reitit.http :as http]
    [reitit.ring :as ring]))

(defn app
  [ctx]
  (http/ring-handler (http/router (commands/routes ctx)
                                  server/standard-router-data)
                     (ring/create-default-handler)
                     server/standard-executor))
