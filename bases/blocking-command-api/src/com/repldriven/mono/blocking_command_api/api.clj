(ns com.repldriven.mono.blocking-command-api.api
  (:require [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.coercion.spec :as rcs]
            [reitit.coercion.malli :as malli]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]))

(def Command
  [:map
   [:data
    [:map
     [:type string?]
     [:id string?]]]])

(def app
  (ring/ring-handler
    (ring/router
      ["/api"
       ["/command" {:post {:summary "negotiated request & response (json, edn, transit)"
                           :parameters {:body [:map [:data [:map [:type string?] [:id string?]]]]}
                           :responses {200 {:body [:map [:result string?]]}}
                           :handler (fn [{{{{:keys [type id]} :data} :body} :parameters}]
                                      {:status 200
                                       :body {:result (clojure.string/join "/" [type id])}})}}]]
      ;; router data affecting all routes
      {:data {:muuntaja m/instance
              :coercion malli/coercion
              :exception pretty/exception
              :middleware [muuntaja/format-middleware
                           rrc/coerce-exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]}})))
