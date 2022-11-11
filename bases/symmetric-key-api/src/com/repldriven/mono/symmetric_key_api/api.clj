(ns com.repldriven.mono.symmetric-key-api.api
  (:require [com.repldriven.mono.symmetric-key-api.handler :as h]
            [com.repldriven.mono.symmetric-key-api.middleware :as m]
            [com.repldriven.mono.log.interface :as log]
            [compojure.core :refer
             [routes wrap-routes defroutes GET POST PUT DELETE ANY OPTIONS]]
            [ring.logger.timbre :as logger]
            [ring.middleware.json :as js]
            [ring.middleware.keyword-params :as kp]
            [ring.middleware.multipart-params :as mp]
            [ring.middleware.nested-params :as np]
            [ring.middleware.params :as pr]))

(defroutes public-routes
           (OPTIONS "/**" [] h/options)
           (GET "/identities/:identity-id/keys" [] h/symmetric-keys)
           (GET "/identities/:identity-id/keys/:key-id" [] h/symmetric-keys))

(defroutes other-routes (ANY "/**" [] h/other))

(def ^:private app-routes (routes public-routes other-routes))

(defn app
  [_]
  (-> app-routes
      logger/wrap-with-logger
      kp/wrap-keyword-params
      pr/wrap-params
      mp/wrap-multipart-params
      js/wrap-json-params
      np/wrap-nested-params
      m/wrap-exceptions
      js/wrap-json-response
      m/wrap-cors))

(defn init
  []
  (try (log/info "Initialized server.")
       (catch Exception e (log/error e "Could not start server."))))

(defn destroy [] (log/info "Destroying server."))
