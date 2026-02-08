(ns com.repldriven.mono.server.interface
  (:require
   com.repldriven.mono.server.system.core

   [com.repldriven.mono.server.router :as router]))

(def standard-router-data router/standard-router-data)
(def standard-executor router/standard-executor)
(def standard-swagger-ui-handler router/standard-swagger-ui-handler)
(def standard-swagger-handler router/standard-swagger-handler)
