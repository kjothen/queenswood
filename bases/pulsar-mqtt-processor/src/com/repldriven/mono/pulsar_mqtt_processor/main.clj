(ns com.repldriven.mono.pulsar-mqtt-processor.main
  (:require
    [com.repldriven.mono.pulsar-mqtt-processor.processor :as processor]

    [com.repldriven.mono.cli.interface :as cli]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]

    [clojure.core.async :as async])
  (:gen-class))

(defn start
  [config-file profile]
  (error/nom-> (env/config config-file profile) system/defs system/start))

(defn -main
  [& args]
  (log/info args)
  (let [{:keys [options exit-message ok?]}
        (cli/validate-args "pulsar-mqtt-processor" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            sys (start config-file (keyword profile))]
        (if (error/anomaly? sys)
          (cli/exit false
                    (str "Failed to start [" (error/kind sys)
                         "]: " (or (:message sys) "Unknown error")))
          (let [{:keys [stop]} (processor/run sys)]
            (log/info "System started successfully")
            (async/<!! stop)))))))
