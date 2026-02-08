(ns com.repldriven.mono.pulsar-reader.main
  (:require
   com.repldriven.mono.pulsar.interface

   [com.repldriven.mono.cli.interface :as cli]
   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.log.interface :as log]
   [com.repldriven.mono.system.interface :as system]

   [clojure.core.async :as async])
  (:import (org.apache.pulsar.client.api Message Reader))
  (:gen-class))

(def system (atom nil))
(def channel (atom nil))

(defn read-messages
  [^Reader reader c]
  (let []; schema (.getSchema (UserEventAvroSerde/INSTANCE))
    (async/go (while (some? c) (async/>! c (.readNext reader))))
    (async/go-loop []
                   (when-let [^Message m (async/<! c)]
                     ;; (log/info (avro/decode schema (.getData m)))
                     (recur)))))

(defn start
  [environment]
  (log/info "Starting system")
  (let [config (system/definition (:system environment))]
    (system/start! system config)
    (reset! channel (async/chan))
    (read-messages (system/instance @system [:pulsar :reader]) @channel)))

(defn stop
  [sys]
  (log/info "Stopping system")
  (when (some? @channel) (reset! channel (async/close! @channel)))
  (system/stop sys))

(defn -main
  [& args]
  (log/init)
  (log/info args)
  (let [{:keys [options exit-message ok?]} (cli/validate-args "pulsar-reader"
                                                              args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options]
        (start (env/env config-file (keyword profile)))))))

(comment
  (-main "-c" "classpath:pulsar-reader/test-application.yml"
         "-p" "test")
  (stop @system)
  (start (env/env "classpath:pulsar-reader/test-application.yml" :test))
  (stop @system))
