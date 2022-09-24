(ns com.repldriven.mono.blocking-command-api.api
  (:require [clojure.string :as string]
            [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.coercion.malli :as malli]
            [reitit.dev.pretty :as pretty]
            [reitit.http :as http]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]))


(defmacro RequestData [data] [:map [:data data]])
(defmacro ResponseData [data] [:map [:data data]])

(def Command [:map [:type string?] [:id string?]])
(def CommandRequest (RequestData Command))

(def CommandResult [:map [:result string?]])
(def CommandResponse (ResponseData CommandResult))

(defn app [{:keys [pulsar-client mqtt-client]}]
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/command" {:post {:summary "negotiated request & response (json, edn, transit)"
                           :parameters {:body CommandRequest}
                           :responses {200 {:body CommandResponse}}
                           :handler (fn [{{{{:keys [type id]} :data} :body} :parameters}]
                                      {:status 200
                                       :body {:data {:result (string/join "/" [type id])}}})}}]]
      ;; router data affecting all routes
      {:data {:muuntaja m/instance
              :coercion malli/coercion
              :exception pretty/exception
              :middleware [muuntaja/format-middleware
                           rrc/coerce-exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]}})))