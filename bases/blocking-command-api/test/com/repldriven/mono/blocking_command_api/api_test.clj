(ns ^:eftest/synchronized com.repldriven.mono.blocking-command-api.api-test
  (:require
   [com.repldriven.mono.mqtt.interface :as mqtt]
   [com.repldriven.mono.pulsar.interface :as pulsar]
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.blocking-command-api.api :as api]

   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.http-client.interface :as http]
   [com.repldriven.mono.log.interface :as log]
   [com.repldriven.mono.server.interface :as server]
   [com.repldriven.mono.system.interface :as system]
   [com.repldriven.mono.test.interface :as test]

   [clojure.core.async :as async]
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing]])
  (:import
   (org.apache.pulsar.client.api Consumer)))

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

(defn- process-pulsar-reply-mqtt
  "Simulates Processor - reads from Pulsar and replies via MQTT"
  [^Consumer consumer mqtt-client schemas]
  (let [schema (pulsar/schema->avro (get-in schemas [:command :schema]))
        {:keys [c stop]} (pulsar/receive consumer schema 1000)]
    (async/thread
      (loop []
       ;; Receive messages from pulsar
        (when-let [{:keys [message data]} (async/<!! c)]
          (let [correlation-id (:correlation-id data)
                reply-topic (str "replies/" correlation-id)
                response {:type (:type data) :id (:id data)}]
            (error/with-anomaly?
             [(mqtt/publish mqtt-client reply-topic (json/write-str response))
              (pulsar/acknowledge consumer message)]
             (log/anomaly {:message "Error processing command"})))
          (recur))))
    {:stop stop}))

(deftest request-pulsar-reply-mqtt-test
  (testing "Request-Reply with Pulsar and MQTT"
    (system/with-system [sys (test-system)]
      (is (system/system? sys) "System should be valid")
      (let [jetty (system/instance sys [:server :jetty-adapter])
            consumer (system/instance sys [:pulsar :consumers :c1])
            mqtt-client (system/instance sys [:mqtt :client])
            schemas (system/instance sys [:pulsar :schemas])
            {:keys [stop]}
            (process-pulsar-reply-mqtt consumer mqtt-client schemas)
            command {:type "test-command" :id "123"}
            command-response {:data command}]
        (binding [*base-url* (server/http-local-url jetty)]
          (error/with-let-anomaly?
           [res (send-http-command command)
            _ (is (= 200 (:status res))
                  "Should receive 200 OK")
            body (http/res->edn res)
            _ (is (= command-response body)
                  "Should receive echoed command")]
           test/refute-anomaly))
        (async/>!! stop :stop)))))
