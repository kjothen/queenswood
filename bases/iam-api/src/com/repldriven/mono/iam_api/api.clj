(ns com.repldriven.mono.iam-api.api
  (:require [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.iam-api.v1.projects.service-accounts.routes :as
             projects-service-accounts]
            [muuntaja.core]
            [reitit.coercion]
            [reitit.coercion.malli :as malli]
            [reitit.dev.pretty :as pretty]
            [reitit.http :as http]
            [reitit.ring :as ring]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.http.coercion :as coercion]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.multipart :as multipart]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]))


;;;; Reitit routes

(defn routes
  [ctx]
  (tap> ctx)
  [["/swagger.json"
    {:get {:no-doc true
           :swagger {:info {:title "iam-api" :description "with reitit-http"}}
           :handler (swagger/create-swagger-handler)}}]
   ["/v1" {:interceptors (:interceptors ctx)}
    ["/projects/{project-id}" (projects-service-accounts/routes)]]])


;;;; Ring application

(def router-data
  {:exception pretty/exception
   :syntax :bracket
   :data {:coercion reitit.coercion.malli/coercion
          :muuntaja muuntaja.core/instance
          :interceptors [;; swagger feature
                         swagger/swagger-feature
                         ;; query-params & form-params
                         (parameters/parameters-interceptor)
                         ;; content-negotiation
                         (muuntaja/format-negotiate-interceptor)
                         ;; encoding response body
                         (muuntaja/format-response-interceptor)
                         ;; exception handling
                         (exception/exception-interceptor)
                         ;; decoding request body
                         (muuntaja/format-request-interceptor)
                         ;; coercing response bodys
                         (coercion/coerce-response-interceptor)
                         ;; coercing request parameters
                         (coercion/coerce-request-interceptor)
                         ;; multipart
                         (multipart/multipart-interceptor)]}})

(defn app
  [ctx]
  (http/ring-handler (http/router (routes ctx) router-data)
                     (ring/routes (swagger-ui/create-swagger-ui-handler
                                   {:path "/"
                                    :config {:validatorUrl nil
                                             :operationsSorter "alpha"}})
                                  (ring/create-default-handler))
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
