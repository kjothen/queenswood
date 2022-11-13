(ns com.repldriven.mono.blocking-command-api.main
  (:require [com.repldriven.mono.cli.interface :as cli]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.blocking-command-api.api :as api]
            [com.repldriven.mono.blocking-command-api.system :as
             blocking-command-api-system]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.eclipse.jetty.server Server))
  (:gen-class))

(def system (atom nil))

(defn start!
  []
  (log/info "Starting system")
  (let [config (-> (:system @env/env)
                   (assoc-in [:ring :jetty-adapter :handler] (partial api/app))
                   (blocking-command-api-system/configure))]
    (system/start! system config)))

(defn stop!
  []
  (log/info "Stopping system")
  (when-let [_ @system] (system/stop! system)))

(defn -main
  [& args]
  (log/init)
  (log/info args)
  (let [{:keys [options exit-message ok?]}
        (cli/validate-args "blocking-command-api" args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (do (env/set-env! (:config-file options) (keyword (:profile options)))
          (start!)))))


(comment
  (require '[clojure.string :as string]
           '[com.repldriven.mono.http.interface :as http]
           '[clojure.data.json :as json]
           '[clj-ulid :as ulid])
  (defn run
    []
    (defn start
      []
      (-main
       "-c"
       "bases/blocking-command-api/test-resources/blocking-command-api/test-env.edn"
       "-p" "dev"))
    (defn query
      []
      (let [^Server web-server (system/instance @system [:ring :jetty-adapter])
            base-uri (as-> (.. web-server getURI toString) url-str
                       (when (= (last url-str) \/)
                         (apply str (drop-last url-str))))
            uri (clojure.string/join "/" [base-uri "api" "command"])]
        (let [{:keys [status error body]}
              (http/request {:url uri
                             :method :post
                             :headers {"Content-Type" "application/json"}
                             :body (json/write-str {:data {:type "example"
                                                           :id (ulid/ulid)}})})]
          (if error
            (println "Failed, exception is " error)
            (println "Async HTTP POST: " status body)))))
    (start)
    (query))
  (run)
  (stop!))
