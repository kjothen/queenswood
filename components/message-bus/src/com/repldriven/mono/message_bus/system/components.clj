(ns com.repldriven.mono.message-bus.system.components
  (:require
    [com.repldriven.mono.message-bus.core :as core]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.log.interface :as log]))

(def producers
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (into {}
                             (map (fn [[k v]]
                                    (log/info "Creating message-bus producer:"
                                              (name k))
                                    [k (core/producer v)])
                                  config))))
   :system/config system/required-component
   :system/instance-schema map?})

(def consumers
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (into {}
                             (map (fn [[k v]]
                                    (log/info "Creating message-bus consumer:"
                                              (name k))
                                    [k (core/consumer v)])
                                  config))))
   :system/config system/required-component
   :system/instance-schema map?})

(def bus
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (core/->Bus (:producers config) (:consumers config))))
   :system/config {:producers system/required-component
                   :consumers system/required-component}
   :system/config-schema [:map [:producers map?] [:consumers map?]]
   :system/instance-schema some?})
