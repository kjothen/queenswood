(ns com.repldriven.mono.mqtt.system
  (:require
    [com.repldriven.mono.mqtt.core :as core]

    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]

    [clojurewerkz.machine-head.client :as mh]))

(def client
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [uri]} config]
                         (log/info "Opening mqtt connection:" uri)
                         (mh/connect uri))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (log/info "Closing mqtt connection")
                    (mh/disconnect-and-close instance)))
   :system/config {:uri system/required-component :options {}}
   :system/config-schema [:map [:uri string?]]
   :system/instance-schema some?})

(def producers
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (into {}
                             (map (fn [[k v]]
                                    (log/info "Creating MQTT producer:"
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
                                    (log/info "Creating MQTT consumer:"
                                              (name k))
                                    [k (core/consumer v)])
                                  config))))
   :system/config system/required-component
   :system/instance-schema map?})

(system/defcomponents
 :mqtt
 {:client client :producers producers :consumers consumers})
