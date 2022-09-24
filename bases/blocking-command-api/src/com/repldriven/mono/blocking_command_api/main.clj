(ns com.repldriven.mono.blocking-command-api.main
  (:require [com.repldriven.mono.cli.interface :as cli]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.blocking-command-api.api :as api]
            [com.repldriven.mono.blocking-command-api.system :as blocking-command-api-system]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.eclipse.jetty.server Server))
  (:gen-class))

(def system (atom nil))

(defn start!
  []
  (log/info "Starting system")
  (let [system-config (blocking-command-api-system/configure (:system @env/env))
        booted-system (system/start system-config #{:boot})
        pulsar-client (system/instance booted-system [:pulsar :client])
        mqtt-client (system/instance booted-system [:mqtt :client])
        ring-handler (api/app {:pulsar-client pulsar-client
                               :mqtt-client mqtt-client})]
    (system/start! system (assoc-in system-config
                                    [:system/defs :ring :jetty-adapter :system/config :handler]
                                    ring-handler))))

(defn stop!
  []
  (log/info "Stopping system")
  (when-let [_ @system]
    (system/stop! system)))

(defn -main
  [& args]
  (log/init)
  (log/info args)
  (let [{:keys [options exit-message ok?]} (cli/validate-args "blocking-command-api" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (do
        (env/set-env! (:config-file options) (keyword (:profile options)))
        (start!)))))

(comment
  (-main "-c" "bases/blocking-command-api/test-resources/blocking-command-api/test-env.edn" "-p" "dev")
  (def ^Server web-server (system/instance @system [:ring :jetty-adapter]))
  (def base-uri (as-> (.. web-server getURI toString) url-str
                  (when (= (last url-str) \/)
                    (apply str (drop-last url-str)))))
  (def uri (clojure.string/join "/" [base-uri "api" "command"]))
  (tap> uri)

  (require '[org.httpkit.client :as http]
           '[clojure.data.json :as json]
           '[clj-ulid :as ulid])
  (let [{:keys [status error body]} @(http/post uri {:headers {"Content-Type" "application/json"}
                                                     :body (json/write-str {:data {:type "example"
                                                                                   :id (ulid/ulid)}})})]
    (if error
      (println "Failed, exception is " error)
      (println "Async HTTP POST: " status body)))
  (stop!)
  (start!)
  (stop!)
  (ulid/ulid)
  )
