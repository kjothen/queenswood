(ns com.repldriven.mono.server.system
  (:require [com.repldriven.mono.server.components :as components]
            [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :server
  {:interceptors components/interceptors
   :jetty-adapter components/jetty-adapter})
