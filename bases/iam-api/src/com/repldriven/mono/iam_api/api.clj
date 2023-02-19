(ns com.repldriven.mono.iam-api.api
  (:require [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.iam-api.v1.projects.service-accounts.routes :as
             projects-service-accounts]
            [malli.generator :as mg]
            [muuntaja.core]
            [reitit.coercion]
            [reitit.coercion.malli :as malli]
            [reitit.dev.pretty :as pretty]
            [reitit.http :as http]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]))

;;;; Reitit routes

(defn routes
  []
  [["/v1" ["/projects/{project-id}" (projects-service-accounts/routes)]]])

;;;; Ring application

(def router-data
  {:syntax :bracket
   :data {:muuntaja muuntaja.core/instance
          :coercion reitit.coercion.malli/coercion
          :exception pretty/exception
          :middleware
          [muuntaja/format-middleware rrc/coerce-exceptions-middleware
           rrc/coerce-request-middleware rrc/coerce-response-middleware]}})

(def dev-router #(ring/router (routes) router-data))
(def prod-router (constantly (ring/router (routes) router-data)))

(defn app ([] (app nil)) ([_] (ring/ring-handler (dev-router))))

;;;; Development

(comment
  (log/init)
  (log/info "Hi there!")
  ((app) {:request-method :get :uri "/v1/projects/123/service-accounts"})
  ((app)
   {:request-method :post
    :uri "/v1/projects/12345/service-accounts/kieran.othen@chase.io:enable"})
  ((app)
   {:request-method :patch
    :uri "/v1/projects/12345/service-accounts/kieran.othen@chase.io"
    :body
    "{\"service-account\": {\"display-name\": \"foo\", \"description\": \"bar\"}, \"update-mask\": \"display_name,description\"}"
    :headers {"content-type" "application/json"}})
  (app
   {:request-method :post
    :uri "/v1/projects/12345/service-accounts"
    :body
    "{\"account-id\": \"kieran.othen@chase.io\", \"service-account\": {\"display-name\": \"foo\", \"description\": \"bar\"}}"
    :headers {"content-type" "application/json"}}))
