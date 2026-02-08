(ns com.repldriven.mono.symmetric-key-api.main-test
  (:require
   [com.repldriven.mono.symmetric-key-api.main :as SUT]

   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.system.interface :as system]

   [org.httpkit.client :as http]

   [clojure.test :as test :refer [deftest is testing use-fixtures]]))

(def ^:dynamic *test-system* nil)
(def ^:dynamic *test-port* nil)

(defn- wait-for-port
  "Wait for the server to bind to a port and start, retry up to max-attempts times"
  [server max-attempts]
  (loop [attempts 0]
    (let [port (.getLocalPort (first (.getConnectors server)))]
      (cond
        (and (pos? port) (.isStarted server))
        (do
          ;; Give the server a bit more time to fully initialize connectors
          (Thread/sleep 200)
          port)
        (>= attempts max-attempts) (throw (Exception. "Server failed to start"))
        :else (do (Thread/sleep 100)
                  (recur (inc attempts)))))))

(defn- http-get-with-retry
  "Make an HTTP GET request with retries for connection errors"
  [url headers max-attempts]
  (loop [attempts 0]
    (let [response @(http/get url {:headers headers})]
      (if (or (:status response) (>= attempts max-attempts))
        response
        (do
          (Thread/sleep 100)
          (recur (inc attempts)))))))

(defn system-fixture [f]
  (let [environment (env/env "classpath:symmetric-key-api/test-application.yml" :test)]
    (try
      (SUT/start environment)
      (let [server (system/instance @SUT/system [:server :jetty-adapter])
            port (wait-for-port server 50)]
        (binding [*test-system* @SUT/system
                  *test-port* port]
          (f)))
      (finally
        (SUT/stop @SUT/system)))))

(use-fixtures :once system-fixture)

(deftest main-test
  (testing "System is started via fixture"
    (is (some? *test-system*))
    (is (pos? *test-port*))))

(deftest development-test
  (testing "Devs should be able to make HTTP requests to the API"
    (let [url (str "http://localhost:" *test-port* "/api/identities/123/keys")
          res (http-get-with-retry url {"accept" "application/json"} 20)]
      (is (= 200 (:status res)) (str "URL: " url " Response: " (pr-str res))))))
