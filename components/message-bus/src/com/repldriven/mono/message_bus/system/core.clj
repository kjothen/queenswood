(ns com.repldriven.mono.message-bus.system.core
  (:require
    [com.repldriven.mono.message-bus.system.components
     :as components]

    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :message-bus
                      {:bus components/bus
                       :producers components/producers
                       :consumers components/consumers})
