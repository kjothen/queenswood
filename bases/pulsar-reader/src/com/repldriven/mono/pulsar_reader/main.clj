(ns com.repldriven.mono.pulsar-reader.main
  (:require
   com.repldriven.mono.pulsar.interface

   [com.repldriven.mono.cli.interface :as cli]
   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.log.interface :as log]
   [com.repldriven.mono.system.interface :as system]

   [clojure.core.async :as async])
  (:import (org.apache.pulsar.client.api Message Reader))
  (:gen-class))

(defn read-messages
  [^Reader reader c]
  (let []; schema (.getSchema (UserEventAvroSerde/INSTANCE))
    (async/go (while (some? c) (async/>! c (.readNext reader))))
    (async/go-loop []
                   (when-let [^Message m (async/<! c)]
                     ;; (log/info (avro/decode schema (.getData m)))
                     (recur)))))

(defn start
  [config-file profile]
  (error/nom-> (env/config config-file profile)
               system/defs
               system/start))

(defn stop [sys] (system/stop sys))

(defn -main
  [& args]
  (log/init)
  (log/info args)
  (let [{:keys [options exit-message ok?]} (cli/validate-args "pulsar-reader"
                                                              args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            result (start config-file (keyword profile))]
        (if (error/anomaly? result)
          (cli/exit false
                    (str "Failed to start [" (error/kind result)
                         "]: " (or (:message result) "Unknown error")))
          (log/info "System started successfully"))))))
