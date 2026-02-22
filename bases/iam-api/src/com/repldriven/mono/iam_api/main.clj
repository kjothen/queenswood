(ns com.repldriven.mono.iam-api.main
  (:require
    ;; system components
    com.repldriven.mono.db.interface
    com.repldriven.mono.migrator.interface
    com.repldriven.mono.server.interface

    [com.repldriven.mono.iam-api.api :as api]

    [com.repldriven.mono.cli.interface :as cli]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]
    [clojure.core :as c])
  (:gen-class))

(defn start
  [config-file profile]
  (error/nom-> (env/config config-file profile)
               system/defs
               (assoc-in [:system/defs :server :handler] api/app)
               system/start))

(defn stop [sys] (system/stop sys))

(defn -main
  [& args]
  (log/info args)
  (let [{:keys [options exit-message ok?]} (cli/validate-args "iam-api" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            result (start config-file (keyword profile))]
        (if (error/anomaly? result)
          (cli/exit false
                    (str "Failed to start [" (error/kind result)
                         "]: " (or (:message result) "Unknown error")))
          (log/info "System started successfully"))))))

(comment
  (require 'com.repldriven.mono.testcontainers.interface)
  (def sys (start "classpath:iam-api/application-test.yml" :test))
  (stop sys))



