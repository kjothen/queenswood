(ns com.repldriven.mono.blocking-command-api.main
  (:require
   com.repldriven.mono.mqtt.interface
   com.repldriven.mono.pubsub.interface
   com.repldriven.mono.server.interface

   [com.repldriven.mono.blocking-command-api.api :as api]

   [com.repldriven.mono.cli.interface :as cli]
   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.log.interface :as log]
   [com.repldriven.mono.system.interface :as system])

  (:gen-class))

(def system (atom nil))

(defn start
  [environment]
  (let [config (-> (:system environment)
                   (assoc-in [:server :jetty-adapter :handler] (partial api/app))
                   (system/definition))]
    (system/start! system config)))

(defn stop
  [system]
  (system/stop system))

(defn -main
  [& args]
  (log/init)
  (log/info args)
  (let [{:keys [options exit-message ok?]} (cli/validate-args "blocking-command-api" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options]
        (start (env/env config-file (keyword profile)))))))

