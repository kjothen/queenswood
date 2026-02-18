(ns com.repldriven.mono.server.router
  (:require
    [muuntaja.core :as m]
    [reitit.coercion.malli]

    [reitit.dev.pretty :as pretty]
    [reitit.http.coercion :as coercion]
    [reitit.http.interceptors.exception :as exception]
    [reitit.http.interceptors.muuntaja :as muuntaja]
    [reitit.http.interceptors.parameters :as parameters]
    [reitit.interceptor.sieppari :as sieppari]
    [reitit.swagger :as swagger]))

(def muuntaja-instance
  "Muuntaja instance configured to decode JSON with string keys."
  (m/create (assoc-in m/default-options
             [:formats "application/json" :decoder-opts :decode-key-fn]
             identity)))

(def default-exception-handlers
  "Default exception handlers for Reitit router.

  Handles Malli coercion failures for both request (400) and response (500),
  returning structured JSON error bodies with type and details fields.
  Falls back to reitit's default handler for all other exceptions."
  (merge exception/default-handlers
         {:reitit.coercion/request-coercion
          (fn [ex _req]
            {:status 400
             :body {"type" "request-validation"
                    "details" (select-keys (ex-data ex) [:humanized :in])}})
          :reitit.coercion/response-coercion
          (fn [ex _req]
            {:status 500
             :body {"type" "response-coercion"
                    "details" (select-keys (ex-data ex) [:humanized :in])}})}))

(defn router-data
  "Build Reitit router data, merging the given exception handlers with defaults.

  Args:
  - exception-handlers: Map of exception type -> handler fn, merged with
    default-exception-handlers. Pass {} or omit for defaults only.

  Returns router data map suitable for reitit.http/router."
  ([] (router-data {}))
  ([exception-handlers]
   {:exception pretty/exception
    :syntax :bracket
    :data {:coercion reitit.coercion.malli/coercion
           :muuntaja muuntaja-instance
           :interceptors [;; swagger feature
                          swagger/swagger-feature
                          ;; query-params & form-params
                          (parameters/parameters-interceptor)
                          ;; content-negotiation
                          (muuntaja/format-negotiate-interceptor)
                          ;; encoding response body
                          (muuntaja/format-response-interceptor)
                          ;; exception handling
                          (exception/exception-interceptor
                           (merge default-exception-handlers
                                  exception-handlers))
                          ;; decoding request body
                          (muuntaja/format-request-interceptor)
                          ;; coercing response bodys
                          (coercion/coerce-response-interceptor)
                          ;; coercing request parameters
                          (coercion/coerce-request-interceptor)]}}))

(def standard-router-data
  "Default Reitit router configuration with Malli coercion, Muuntaja, and Swagger support."
  (router-data))

(def standard-executor
  "Default Sieppari executor for Reitit ring handler."
  {:executor sieppari/executor})
