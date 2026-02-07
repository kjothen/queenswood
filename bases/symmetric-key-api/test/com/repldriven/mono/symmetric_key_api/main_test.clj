(ns com.repldriven.mono.symmetric-key-api.main-test
  (:require [clojure.test :as test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.symmetric-key-api.main :as SUT]
            [com.repldriven.mono.system.interface :as system]
            [org.httpkit.client :as http]))

(deftest main-test
  (testing
   "Ops should be able to start the system from the main entry point"
   (try
     (SUT/-main "-c" "classpath:symmetric-key-api/test-application.yml"
                "-p" "test")
     (is (some? @SUT/system))
     (catch Exception e (assert false (format "Unable to start system, %s" e)))
     (finally (SUT/stop @SUT/system)))))

(defn- wait-for-port
  "Wait for the server to bind to a port, retry up to max-attempts times"
  [server max-attempts]
  (loop [attempts 0]
    (let [port (.getLocalPort (first (.getConnectors server)))]
      (cond
        (pos? port) port
        (>= attempts max-attempts) (throw (Exception. "Server failed to bind to port"))
        :else (do (Thread/sleep 100)
                  (recur (inc attempts)))))))

(deftest development-test
  (testing
   "Devs should be able to start the system from the REPL"
   (let [environment (env/env "classpath:symmetric-key-api/test-application.yml" :test)]
     (try
       (SUT/start environment)
       (let [server (system/instance @SUT/system [:server :jetty-adapter])
             port (wait-for-port server 50)
             url (str "http://localhost:" port "/api/identities/123/keys")
             res @(http/get url {:headers {"accept" "application/json"}})]
         (is (= 200 (:status res)) (str "URL: " url " Response: " (pr-str res))))
       (catch Exception e
         (println "Test exception:" (.getMessage e))
         (.printStackTrace e)
         (assert false (format "Unable to start system, %s" e)))
       (finally (SUT/stop @SUT/system))))))
