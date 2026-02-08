(ns com.repldriven.mono.server.system.core
  (:require
   [com.repldriven.mono.server.system.components :as components]

   [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :server
  {:interceptors components/interceptors
   :jetty-adapter components/jetty-adapter})
