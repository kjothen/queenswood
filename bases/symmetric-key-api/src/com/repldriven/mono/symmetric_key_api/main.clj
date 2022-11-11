(ns com.repldriven.mono.symmetric-key-api.main
  (:require [com.repldriven.mono.cli.interface :as cli]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.symmetric-key-api.api :as api]
            [com.repldriven.mono.symmetric-key-api.system :as
             symmetric-key-api-system]
            [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(def system (atom nil))

(defn start!
  []
  (log/info "Starting system")
  (api/init)
  (let [system-config (symmetric-key-api-system/configure (:system @env/env))]
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
  (let [{:keys [options exit-message ok?]}
        (cli/validate-args "symmetric-key-api" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (do (env/set-env! (:config-file options) (keyword (:profile options)))
          (start!)))))

(comment
  (-main "-c"
         "bases/symmetric-key-api/test-resources/symmetric-key-api/test-env.edn"
         "-p" "dev")
  (stop!)
  (start!)
  (stop!))
