(ns com.repldriven.mono.bank-api.main
  (:require
    com.repldriven.mono.avro.interface
    com.repldriven.mono.bank-api-key.interface
    com.repldriven.mono.bank-balance.interface
    com.repldriven.mono.bank-cash-account.interface
    com.repldriven.mono.bank-cash-account-product.interface
    com.repldriven.mono.bank-idv.interface
    com.repldriven.mono.bank-organization.interface
    com.repldriven.mono.bank-party.interface
    com.repldriven.mono.bank-payee-check.interface
    com.repldriven.mono.bank-payment.interface
    com.repldriven.mono.bank-policy.interface
    com.repldriven.mono.bank-schema.interface
    com.repldriven.mono.bank-transaction.interface
    com.repldriven.mono.command.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.server.interface
    com.repldriven.mono.telemetry.interface
    [com.repldriven.mono.bank-api.api :as api]
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
         (assoc-in [:system/defs :server :handler] api/app)
         system/start))

(defn stop [system] (system/stop system))

(defn -main
  [& args]
  (log/info args)
  (let [{:keys [options exit-message ok?]} (cli/validate-args "bank-api" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            result (start config-file (keyword profile))]
        (if (error/anomaly? result)
          (cli/exit false result)
          (log/info "System started successfully"))))))







