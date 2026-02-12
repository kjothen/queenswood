(ns com.repldriven.mono.symmetric-key-api.main
  (:require
   com.repldriven.mono.server.interface

   [com.repldriven.mono.symmetric-key-api.api :as api]

   [com.repldriven.mono.cli.interface :as cli]
   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.log.interface :as log]
   [com.repldriven.mono.system.interface :as system])
  (:gen-class))

(def system (atom nil))

(defn start
  [environment]
  (log/info "Starting system")
  (let [config (-> environment
                   (system/definition)
                   (assoc-in [:system/defs :server :handler] (partial api/app)))]
    (system/start! system config)))

(defn stop
  [sys]
  (log/info "Stopping system")
  (system/stop sys))

(defn -main
  [& args]
  (log/init)
  (log/info args)
  (let [{:keys [options exit-message ok?]}
        (cli/validate-args "symmetric-key-api" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options]
        (start (env/config config-file (keyword profile)))))))

(comment
  (-main "-c" "classpath:symmetric-key-api/test-application.yml"
         "-p" "test")
  (stop @system)
  (start (env/config "classpath:symmetric-key-api/test-application.yml" :test))
  (stop @system))
