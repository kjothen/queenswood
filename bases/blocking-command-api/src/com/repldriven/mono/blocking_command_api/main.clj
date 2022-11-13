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
  (let [config (-> (blocking-command-api-system/configure (:system @env/env))
                   (assoc-in [:system/defs :ring :jetty-adapter :system/config
                              :handler]
                             (partial api/app)))]
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
  (require '[clojure.walk :as walk])
  (def mono-system-ns "system")
  (def donut-system-ns "donut.system")
  (defn match-ns-keyword?
    [x match-ns]
    (and (keyword? x) (= match-ns (namespace x))))
  (defn- nsmap->nsmap
    [m from-ns to-ns]
    (walk/postwalk
     (fn [x]
       (if (match-ns-keyword? x from-ns)
         (keyword to-ns (name x))
         (if (fn? x)
           (fn [to-ns-map]
             (x (reduce-kv (fn [m k v]
                             (assoc m
                                    (if (match-ns-keyword? k to-ns)
                                      (keyword from-ns (name k))
                                      k)
                                    v))
                           {}
                           to-ns-map)))
           x)))
     m))
  (def config
    (-> (blocking-command-api-system/configure (:system @env/env))
        (assoc-in [:system/defs :ring :jetty-adapter :system/config :handler]
                  (partial api/app))))
  (.. (system/instance @system [:ring :jetty-adapter]) getConnectors))


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
        (prn uri)
        (let [{:keys [status error body]}
              (http/request {:url uri
                             :headers {"Content-Type" "application/json"}
                             :body (json/write-str {:data {:type "example"
                                                           :id (ulid/ulid)}})})]
          (if error
            (println "Failed, exception is " error)
            (println "Async HTTP POST: " status body)))))
    (defn stop [] (stop!))
    (start)
    (query)
    (assoc-in (:system @env/env)
     [:ring :jetty-adapter :handler]
     (partial api/app))
    (system/instance @system [:ring :jetty-adapter])
    (stop))
  (run))
