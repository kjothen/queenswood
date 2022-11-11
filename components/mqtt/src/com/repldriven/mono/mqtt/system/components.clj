(ns com.repldriven.mono.mqtt.system.components
  (:require [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system]
            [clojurewerkz.machine-head.client :as mh]))

(def client
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [uri options]} config]
                         (try (log/info "Opening mqtt connection:" uri options)
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
