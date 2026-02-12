(ns com.repldriven.mono.iam-api.main
  (:require
    ;; system components
    com.repldriven.mono.server.interface
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.iam-api.api :as api]

    [com.repldriven.mono.cli.interface :as cli]
    [com.repldriven.mono.db.interface :as db]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.iam.interface :as iam]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(defn- migrate-db
  [sys]
  (error/let-nom [datasource (system/instance sys [:db :datasource])
                  _ (iam/migrate (db/get-datasource datasource))]
    sys))

(defn start
  [config-file profile]
  (error/nom-> (env/config config-file profile)
               system/defs
               (assoc-in [:system/defs :server :handler] (partial api/app))
               system/start
               migrate-db))

(defn stop [sys] (system/stop sys))

(defn -main
  [& args]
  (log/init)
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
