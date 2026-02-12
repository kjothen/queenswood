(ns com.repldriven.mono.iam-api.main
  (:require
   ;; system components
   com.repldriven.mono.db.interface
   com.repldriven.mono.server.interface
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.iam-api.api :as api]

   [com.repldriven.mono.cli.interface :as cli]
   [com.repldriven.mono.db.interface :as sql]
   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.iam.interface :as iam]
   [com.repldriven.mono.log.interface :as log]
   [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(def system (atom nil))

(defn db-spec
  [sys]
  (let [datasource (system/instance sys [:db :datasource])]
    (sql/get-datasource datasource)))

(defn start
  [config-file profile]
  (error/let-nom [sys (error/nom-> (env/config config-file profile)
                                   :system
                                   system/definition
                                   (assoc-in [:system/defs :server :handler] (partial api/app))
                                   system/start)
                  _ (iam/migrate (db-spec sys))]
    (reset! system sys)
    sys))

(defn stop
  [sys]
  (system/stop sys))

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
          (cli/exit false (str "Failed to start [" (error/kind result) "]: "
                               (or (:message result) "Unknown error")))
          (log/info "System started successfully"))))))
