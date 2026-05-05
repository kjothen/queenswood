(ns com.repldriven.mono.bank-onfido-adapter.main
  (:require
    com.repldriven.mono.avro.interface
    com.repldriven.mono.bank-onfido-adapter.system
    com.repldriven.mono.bank-onfido-webhook.interface
    com.repldriven.mono.bank-schema.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.server.interface
    com.repldriven.mono.telemetry.interface

    [com.repldriven.mono.bank-onfido-adapter.api :as api]

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
         (assoc-in [:system/defs :onfido-adapter-server :handler] api/app)
         system/start))

(defn stop [system] (system/stop system))

(defn -main
  [& args]
  (log/info args)
  (let [{:keys [options exit-message ok?]}
        (cli/validate-args "bank-onfido-adapter" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            result (start config-file (keyword profile))]
        (if (error/anomaly? result)
          (cli/exit false result)
          (log/info "System started successfully"))))))
