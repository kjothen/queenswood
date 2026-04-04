(ns com.repldriven.mono.bank-clearbank-simulator.main
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.api :as api]

    [com.repldriven.mono.cli.interface :as cli]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error :refer [nom->]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.server.interface]
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
  (let [{:keys [options exit-message ok?]}
        (cli/validate-args "bank-clearbank-simulator" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            result (start config-file (keyword profile))]
        (if (error/anomaly? result)
          (cli/exit
           false
           (str "Failed to start [" (error/kind result)
                "]: " (or (:message result) "Unknown error")))
          (log/info "System started successfully"))))))
