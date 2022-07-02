(ns com.repldriven.mono.pulsar-reader.main
  (:require [abracad.avro :as avro]
            [clojure.core.async :as async]
            [com.repldriven.mono.cli.interface :as cli]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.pulsar.interface :as pulsar]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.apache.pulsar.client.api Message Reader))
  (:gen-class))

(def system (atom nil))
(def channel (atom nil))

(defn read-messages
  [^Reader reader c]
  (let [;; schema (.getSchema (UserEventAvroSerde/INSTANCE))
        ]
    (async/go (while true (async/>! c (.readNext reader))))
    (async/go-loop []
      (if-let [^Message m (async/<! c)]
        (do
          ;; (log/info (avro/decode schema (.getData m)))
          (recur))))))

(defn start!
  ([] (start! nil))
  ([booted-system]
   (log/info "Starting system")
   (let [system-config (pulsar/configure-system (get-in @env/env [:system :pulsar]))]
     (system/start! system (if (some? booted-system) booted-system system-config))
     (reset! channel (async/chan))
     (read-messages (system/instance @system [:pulsar :reader]) @channel))))

(defn stop!
  []
  (when-let [_ @system]
    (do (log/info "Stopping system")
        (when (some? @channel)
          (reset! channel (async/close! @channel)))
        (system/stop! system))))

(defn -main
  [& args]
  (log/init)
  (log/info args)
  (let [{:keys [options exit-message ok?]} (cli/validate-args "pulsar-reader" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (do
        (env/set-env! (:config-file options) (keyword (:profile options)))
        (start!)))))

(comment
  (-main "-c" "bases/pulsar-reader/test-resources/pulsar-reader/test-env.edn" "-p" "dev")
  (stop!)
  (start!)
  (stop!)
)
