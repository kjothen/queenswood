(ns com.repldriven.mono.mqtt.system.core
  (:require [com.repldriven.mono.mqtt.system.components :as components]
            [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :mqtt
  {:client components/client})
