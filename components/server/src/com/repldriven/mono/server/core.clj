(ns com.repldriven.mono.server.core
  (:require
    com.repldriven.mono.server.system.core

    [com.repldriven.mono.server.jetty :as jetty]
    [com.repldriven.mono.server.router :as router]
    [com.repldriven.mono.server.swagger :as swagger]))

(def standard-router-data router/standard-router-data)
(def standard-executor router/standard-executor)
(def default-exception-handlers router/default-exception-handlers)

(defn router-data
  ([] (router/router-data))
  ([exception-handlers] (router/router-data exception-handlers)))

(defn standard-swagger-ui-handler [] (swagger/standard-ui-handler))

(defn standard-swagger-handler [] (swagger/standard-handler))

(defn http-local-url
  "Get the local HTTP URL from a Jetty Server instance."
  [server]
  (jetty/http-local-url server))
