(ns com.repldriven.mono.iam-api.main
  (:require [com.repldriven.mono.cli.interface :as cli]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.iam.interface :as iam]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.iam-api.api :as api]
            [com.repldriven.mono.server.interface]
            [com.repldriven.mono.sql.interface]
            [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(def system (atom nil))

(defn db-spec
  [sys]
  (let [datasource (system/instance sys [:sql :datasource])]
    (next.jdbc/get-datasource datasource)))

(defn start!
  [environment]
  (log/info "Starting system")
  (let [config (-> (:system environment)
                   (assoc-in [:server :jetty-adapter :handler] (partial api/app))
                   (system/definition))]
    (system/start! system config)
    (iam/migrate (db-spec @system))))

(defn stop!
  [sys]
  (log/info "Stopping system")
  (when sys (system/stop sys)))

(defn restart! [environment] (stop! @system) (start! environment))

(defn -main
  [& args]
  (log/init)
  (log/info args)
  (let [{:keys [options exit-message ok?]} (cli/validate-args "iam-api" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [environment (env/env (:config-file options) (keyword (:profile options)))]
        (start! environment)))))
