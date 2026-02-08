(ns com.repldriven.mono.server.router
  (:require [muuntaja.core]
            [reitit.coercion.malli]
            [reitit.dev.pretty :as pretty]
            [reitit.http.coercion :as coercion]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.swagger :as swagger]))

(def standard-router-data
  "Default Reitit router configuration with Malli coercion, Muuntaja, and Swagger support."
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
                         (coercion/coerce-request-interceptor)]}})

(def standard-executor
  "Default Sieppari executor for Reitit ring handler."
  {:executor sieppari/executor})

