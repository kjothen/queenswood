(ns com.repldriven.mono.mqtt.system
  (:require
    [com.repldriven.mono.mqtt.core :as core]

    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]

    [clojurewerkz.machine-head.client :as mh]))

(def client
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [uri _options]} config]
                         (try (log/info "Opening mqtt connection:" uri)
                              (mh/connect uri)
                              (catch Exception e
                                (log/error (format
                                            "Failed to open mqtt connection, %s"
                                            e)))))))
   :system/stop
   (fn [{:system/keys [instance]}]
     (when (some? instance)
       (try (log/info "Closing mqtt connection")
            (mh/disconnect-and-close instance)
            (catch Exception e
              (log/error (format "Failed to close mqtt connection, %s" e))))))
   :system/config {:uri system/required-component :options {}}})

(def producers
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (into {}
                             (map (fn [[k v]]
                                    (log/info "Creating MQTT producer:"
                                              (name k))
                                    [k (core/producer v)])
                                  config))))
   :system/config system/required-component})

(def consumers
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (into {}
                             (map (fn [[k v]]
                                    (log/info "Creating MQTT consumer:"
                                              (name k))
                                    [k (core/consumer v)])
                                  config))))
   :system/config system/required-component})

(system/defcomponents
 :mqtt
 {:client client :producers producers :consumers consumers})
