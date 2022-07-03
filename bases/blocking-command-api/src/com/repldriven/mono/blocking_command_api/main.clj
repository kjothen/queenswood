(ns com.repldriven.mono.blocking-command-api.main
  (:require [clojure.core.async :as async]
            [com.repldriven.mono.cli.interface :as cli]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.blocking-command-api.system :as blocking-command-api-system]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.apache.pulsar.client.api Message Reader))
  (:gen-class))

(def system (atom nil))
(def channel (atom nil))

(defn read-messages
  [^Reader reader c]
  (let [];; schema (.getSchema (UserEventAvroSerde/INSTANCE))

    (async/go (while true (async/>! c (.readNext reader))))
    (async/go-loop []
      (when-let [^Message m (async/<! c)]
        ;; (log/info (avro/decode schema (.getData m)))
        (recur)))))

(defn start!
  []
  (log/info "Starting system")
  (when-let [system-config (blocking-command-api-system/configure (:system @env/env))]
    (system/start! system system-config)
    (reset! channel (async/chan))
    (read-messages (system/instance @system [:pulsar :reader]) @channel)))

(defn stop!
  []
  (log/info "Stopping system")
  (when-let [_ @system]
    (when (some? @channel)
      (reset! channel (async/close! @channel)))
    (system/stop! system)))

(defn -main
  [& args]
  (log/init)
  (log/info args)
  (let [{:keys [options exit-message ok?]} (cli/validate-args "blocking-command-api" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (do
        (env/set-env! (:config-file options) (keyword (:profile options)))
        (start!)))))

(comment
  (-main "-c" "bases/blocking-command-api/test-resources/blocking-command-api/test-env.edn" "-p" "dev")
  (stop!)
  (start!)
  (stop!))