(ns com.repldriven.mono.iam-api.main
  (:require [com.repldriven.mono.cli.interface :as cli]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.iam.interface :as iam]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.iam-api.api :as api]
            [com.repldriven.mono.iam-api.system :as iam-api-system]
            [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(def system (atom nil))

(defn system-config
  []
  (-> (iam-api-system/configure (:system @env/env))
      (assoc-in [:system/defs :ring :jetty-adapter :system/config :handler]
                api/app)))

(defn db-spec
  []
  (let [datasource (system/instance @system [:sql :datasource])]
    ;; TODO remove sleep until datasource is available
    (Thread/sleep 5000)
    (next.jdbc/get-datasource datasource)))

(defn start!
  []
  (log/info "Starting system")
  (system/start! system (system-config))
  (iam/migrate (db-spec)))

(defn stop!
  []
  (log/info "Stopping system")
  (when-let [_ @system] (system/stop! system)))

(defn -main
  [& args]
  (log/init)
  (log/info args)
  (let [{:keys [options exit-message ok?]} (cli/validate-args "iam-api" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (do (env/set-env! (:config-file options) (keyword (:profile options)))
          (start!)))))

(comment
  (-main "-c" "bases/iam-api/test-resources/iam-api/test-env.edn" "-p" "dev")
  (stop!)
  (start!)
  (stop!))
