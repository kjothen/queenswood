(ns com.repldriven.queenswood.uk-companies-house-adapter.main
  (:require
    com.repldriven.queenswood.uk-companies-house-adapter.system

    com.repldriven.queenswood.company.interface
    com.repldriven.queenswood.fdb.interface
    com.repldriven.queenswood.schema.interface
    com.repldriven.mono.avro.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.kafka.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.telemetry.interface

    [com.repldriven.mono.cli.interface :as cli]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error :refer [nom->]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(defn start
  [config-file profile]
  (nom-> (env/config config-file profile) system/defs system/start))

(defn -main
  [& args]
  (log/info args)
  (let [{:keys [options exit-message ok?]}
        (cli/validate-args "uk-companies-house-adapter-service" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            sys (start config-file (keyword profile))]
        (if (error/anomaly? sys)
          (cli/exit false sys)
          (do (log/info "System started successfully") @(promise)))))))
