(ns com.repldriven.mono.message-bus.system.components
  (:require
    [com.repldriven.mono.message-bus.core :as core]

    [com.repldriven.mono.system.interface :as system]))

(def bus
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (core/->Bus (:producers config) (:consumers config))))
   :system/config {:producers system/required-component
                   :consumers system/required-component}
   :system/config-schema [:map [:producers map?] [:consumers map?]]
   :system/instance-schema some?})
