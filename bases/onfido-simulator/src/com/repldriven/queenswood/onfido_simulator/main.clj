(ns com.repldriven.queenswood.onfido-simulator.main
  (:require
    [com.repldriven.queenswood.onfido-simulator.system]

    [com.repldriven.mono.server.interface]
    [com.repldriven.mono.telemetry.interface]

    [com.repldriven.queenswood.onfido-simulator.api :as api]

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
         (assoc-in [:system/defs :onfido-simulator-server :handler] api/app)
         system/start))

(defn -main
  [& args]
  (log/info args)
  (let [{:keys [options exit-message ok?]}
        (cli/validate-args "bank-onfido-simulator" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            result (start config-file (keyword profile))]
        (if (error/anomaly? result)
          (cli/exit false result)
          (log/info "System started successfully"))))))
