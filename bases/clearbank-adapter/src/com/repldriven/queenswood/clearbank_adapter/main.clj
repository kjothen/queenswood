(ns com.repldriven.queenswood.clearbank-adapter.main
  (:require
    com.repldriven.mono.avro.interface
    com.repldriven.queenswood.clearbank-adapter.system
    com.repldriven.queenswood.clearbank-relay.interface
    com.repldriven.queenswood.clearbank-webhook.interface
    com.repldriven.queenswood.schema.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.server.interface
    com.repldriven.mono.telemetry.interface

    [com.repldriven.queenswood.clearbank-adapter.api :as api]

    [com.repldriven.mono.cli.interface :as cli]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error :refer [nom->]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(defn start
  [config-file profile]
  (nom-> (env/config config-file profile)
         system/defs
         (assoc-in [:system/defs :clearbank-adapter-server :handler] api/app)
         system/start))

(defn stop [system] (system/stop system))

(defn -main
  [& args]
  (log/info args)
  (let [{:keys [options exit-message ok?]}
        (cli/validate-args "bank-clearbank-adapter" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            result (start config-file (keyword profile))]
        (if (error/anomaly? result)
          (cli/exit false result)
          (log/info "System started successfully"))))))
