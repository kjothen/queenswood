(ns com.repldriven.mono.iam-api.main
  (:require
   [com.repldriven.mono.iam-api.api :as api]

   [com.repldriven.mono.cli.interface :as cli]
   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.iam.interface :as iam]
   [com.repldriven.mono.log.interface :as log]
   [com.repldriven.mono.db.interface :as sql]
   [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(def system (atom nil))

(defn db-spec
  [sys]
  (let [datasource (system/instance sys [:db :datasource])]
    (sql/get-datasource datasource)))

(defn start
  [environment]
  (let [config (-> (:system environment)
                   (assoc-in [:server :handler] (partial api/app))
                   (system/definition))]
    (system/start! system config)
    (iam/migrate (db-spec @system))))

(defn stop
  [system]
  (system/stop system))

(defn -main
  [& args]
  (log/init)
  (log/info args)
  (let [{:keys [options exit-message ok?]} (cli/validate-args "iam-api" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options]
        (start (env/env config-file (keyword profile)))))))
