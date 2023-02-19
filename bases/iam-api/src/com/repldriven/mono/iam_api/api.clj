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
            [reitit.http.coercion :as coercion]
            [reitit.http.interceptors.muuntaja :as muuntaja]))

;;;; Reitit routes

(defn routes
  [ctx]
  [["/v1" {:interceptors (:interceptors ctx)}
    ["/projects/{project-id}" (projects-service-accounts/routes)]]])

;;;; Ring application

(def router-data
  {:syntax :bracket
   :data {:muuntaja muuntaja.core/instance
          :interceptors [(muuntaja/format-response-interceptor)
                         (coercion/coerce-response-interceptor)]
          :coercion reitit.coercion.malli/coercion
          :exception pretty/exception
          ;;:middleware
          ;;[muuntaja/format-middleware rrc/coerce-exceptions-middleware
          ;; rrc/coerce-request-middleware rrc/coerce-response-middleware]
         }})

(defn app
  [ctx]
  (ring/ring-handler (ring/router (routes ctx) router-data)
                     (ring/create-default-handler)
                     {:executor sieppari/executor}))

;;;; Development

(comment
  (log/init)
  (log/info "Hi there!")
  ((app nil) {:request-method :get :uri "/v1/projects/123/service-accounts"})
  ((app)
   {:request-method :post
    :uri "/v1/projects/12345/service-accounts/kieran.othen@chase.io:enable"})
  ((app)
   {:request-method :patch
    :uri "/v1/projects/12345/service-accounts/kieran.othen@chase.io"
    :body
    "{\"service-account\": {\"display-name\": \"foo\", \"description\": \"bar\"}, \"update-mask\": \"display_name,description\"}"
    :headers {"content-type" "application/json"}})
  ((app)
   {:request-method :post
    :uri "/v1/projects/12345/service-accounts"
    :body
    "{\"account-id\": \"kieran.othen@chase.io\", \"service-account\": {\"display-name\": \"foo\", \"description\": \"bar\"}}"
    :headers {"content-type" "application/json"}}))
