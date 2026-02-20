(ns com.repldriven.mono.server.interface
  (:require
    [com.repldriven.mono.server.core :as core]))

(def standard-router-data core/standard-router-data)
(def standard-executor core/standard-executor)
(def default-exception-handlers core/default-exception-handlers)

(defn router-data
  ([] (core/router-data))
  ([exception-handlers] (core/router-data exception-handlers)))

(defn standard-swagger-ui-handler [] (core/standard-swagger-ui-handler))

(defn standard-swagger-handler [] (core/standard-swagger-handler))

(defn http-local-url
  "Get the local HTTP URL from a Jetty Server instance."
  [server]
  (core/http-local-url server))
