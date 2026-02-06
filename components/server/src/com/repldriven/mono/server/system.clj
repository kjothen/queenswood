(ns com.repldriven.mono.server.system
  (:require [com.repldriven.mono.server.components :as components]
            [com.repldriven.mono.system.interface :as system]))

(defmethod system/component :server/interceptors
  [_ v]
  (system/merge-component-config components/interceptors (dissoc v :annotation)))

(defmethod system/component :server/jetty-adapter
  [_ v]
  (system/merge-component-config components/jetty-adapter (dissoc v :annotation)))
