(ns com.repldriven.mono.server.interface
  (:require
   com.repldriven.mono.server.system.core

   [com.repldriven.mono.server.swagger :as swagger]
   [com.repldriven.mono.server.router :as router]))

(def standard-router-data router/standard-router-data)
(def standard-executor router/standard-executor)

(defn standard-swagger-ui-handler [] (swagger/standard-ui-handler))
(defn standard-swagger-handler [] (swagger/standard-handler))
