(ns com.repldriven.mono.accounts-api.api
  (:require
    [com.repldriven.mono.accounts-api.accounts.routes :as accounts]

    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.server.interface :as server]

    [reitit.http :as http]
    [reitit.ring :as ring]))

(def ^:private exception-handlers
  {:reitit.coercion/request-coercion
   (fn [ex req]
     {:status 400
      :body (command/req->command-response
             req
             (error/fail :accounts-api/request-validation
                         (select-keys (ex-data ex) [:humanized :in])))})
   :reitit.coercion/response-coercion
   (fn [ex req]
     {:status 500
      :body (command/req->command-response
             req
             (error/fail :accounts-api/response-coercion
                         (select-keys (ex-data ex) [:humanized :in])))})})

(defn app
  [ctx]
  (http/ring-handler (http/router (accounts/routes ctx)
                                  (server/router-data exception-handlers))
                     (ring/create-default-handler)
                     server/standard-executor))
