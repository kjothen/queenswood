(ns com.repldriven.mono.iam-api.main
  (:require [com.repldriven.mono.cli.interface :as cli]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.iam-api.api :as api]
            [com.repldriven.mono.iam-api.system :as iam-api-system]
            [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(def system (atom nil))

(defn start!
  []
  (log/info "Starting system")
  (api/init)
  (let [system-config (iam-api-system/configure (:system @env/env))]
    (system/start! system
                   (assoc-in system-config
                    [:system/defs :ring :jetty-adapter :system/config :handler]
                    api/app))))

(defn stop!
  []
  (log/info "Stopping system")
  (when-let [_ @system]
    (api/destroy)
    (system/stop! system)))

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
