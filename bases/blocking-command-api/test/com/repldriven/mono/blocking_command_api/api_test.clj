(ns ^:eftest/synchronized com.repldriven.mono.blocking-command-api.api-test
  (:require
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.blocking-command-api.api :as api]

   [com.repldriven.mono.command.interface :as command]
   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.http-client.interface :as http]
   [com.repldriven.mono.pulsar.interface :as pulsar]
   [com.repldriven.mono.server.interface :as server]
   [com.repldriven.mono.system.interface :as system]
   [com.repldriven.mono.test.interface :as test]

   [clojure.core.async :as async]
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing]]))

(def ^:dynamic *base-url* "http://localhost:{PORT}")

(defn- test-system
  []
  (error/nom-> (env/config "classpath:blocking-command-api/application-test.yml"
                           :test)
               system/defs
               (assoc-in [:system/defs :server :handler] api/app)
               system/start))

(defn send-http-command
  "Simulates Client - synchronous command request"
  [command]
  (http/request {:method :post
                 :url (str *base-url* "/api/command")
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str {:data command})}))

(defn command-processor
  "Simulates Processor - reads commands from Pulsar and replies via MQTT"
  [sys]
  (let [consumer (system/instance sys [:pulsar :consumers :command])
        mqtt-client (system/instance sys [:mqtt :client])
        schemas (system/instance sys [:pulsar :schemas])
        schema (pulsar/schema->avro (get-in schemas [:command :schema]))
        process-fn identity]
    (command/process consumer mqtt-client schema process-fn {:timeout-ms 1000})))

(deftest request-pulsar-reply-mqtt-test
  (testing "Request-Reply with Pulsar and MQTT"
    (system/with-system [sys (test-system)]
      (is (system/system? sys) "System should be valid")
      (let [jetty (system/instance sys [:server :jetty-adapter])
            {:keys [stop]} (command-processor sys)
            command {:correlation_id "1" :type "test-command" :id "123"}
            expected {:data command}]
        (binding [*base-url* (server/http-local-url jetty)]
          (error/with-let-anomaly?
            [res (send-http-command command)
             _ (is (= 200 (:status res))
                   "Should receive 200 OK")
             actual (http/res->edn res)
             _ (is (= expected actual)
                   "Should receive echoed command")]
            test/refute-anomaly))
        (async/>!! stop :stop)))))
