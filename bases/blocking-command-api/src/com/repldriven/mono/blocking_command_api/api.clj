(ns com.repldriven.mono.blocking-command-api.api
  (:require
    [com.repldriven.mono.blocking-command-api.commands.routes :as commands]
    [com.repldriven.mono.blocking-command-api.errors :as errors]

    [com.repldriven.mono.server.interface :as server]

    [reitit.http :as http]
    [reitit.ring :as ring]))

(def ^:private command-exception-handlers
  {:reitit.coercion/request-coercion
   (fn [ex req]
     {:status 400
      :body (errors/coercion-ex->command-response ex
                                                  req
                                                  :command/request-validation)})
   :reitit.coercion/response-coercion (fn [ex req]
                                        {:status 500
                                         :body
                                         (errors/coercion-ex->command-response
                                          ex
                                          req
                                          :command/response-coercion)})})

(defn app
  [ctx]
  (http/ring-handler (http/router (commands/routes ctx)
                                  (server/router-data
                                   command-exception-handlers))
                     (ring/create-default-handler)
                     server/standard-executor))
