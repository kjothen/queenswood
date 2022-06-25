(ns com.repldriven.mono.mqtt.components
  (:require [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system]
            [clojurewerkz.machine-head.client :as mh]))

(def client
  {:start (fn [{:keys [uri options]} instance _]
            (or instance
                (try
                  (log/info "Opening mqtt connection:" uri options)
                  (mh/connect uri)
                  (catch Exception e
                    (log/error (format "Failed to open mqtt connection, %s" e)))))),
   :stop (fn [_ instance _]
           (when (some? instance)
             (try
               (log/info "Closing mqtt connection")
               (mh/disconnect-and-close instance)
               (catch Exception e
                 (log/error (format "Failed to close mqtt connection, %s" e)))))),
   :conf {:uri system/required-component
          :options {}}})