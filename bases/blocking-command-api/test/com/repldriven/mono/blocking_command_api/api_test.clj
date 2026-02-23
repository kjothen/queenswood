(ns ^:eftest/synchronized com.repldriven.mono.blocking-command-api.api-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.blocking-command-api.api :as api]

    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.pulsar.interface :as pulsar]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.core.async :as async]
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing]]))

(def ^:dynamic *base-url* "http://localhost:{PORT}")

(defn send-http-command
  "Simulates Client - synchronous command request"
  [command]
  (http/request {:method :post
                 :url (str *base-url* "/api/command")
                 :headers {"Content-Type" "application/json"
                           "Idempotency-Key" (str (util/uuidv7))}
                 :body (json/write-str {"command" command})}))

(defn command-processor
  "Simulates Processor - reads commands from Pulsar and replies via MQTT"
  [sys]
  (let [consumer (system/instance sys [:pulsar :consumers :command])
        mqtt-client (system/instance sys [:mqtt :client])
        schemas (system/instance sys [:pulsar :schemas])
        schema (pulsar/schema->avro (get-in schemas [:command :schema]))
        process-fn
        (fn [data]
          (let [parent-ctx (telemetry/extract-parent-context data)]
            (telemetry/with-span-parent
             "process-command"
             parent-ctx
             (select-keys data ["id" "command" "correlation_id" "causation_id"])
             (fn [] {"ok" "computer"}))))]
    (command/process consumer
                     mqtt-client
                     schema
                     process-fn
                     {:timeout-ms 1000})))

(deftest request-pulsar-reply-mqtt-test
  (testing "Request-Reply with Pulsar and MQTT"
    (with-test-system
     [sys
      ["classpath:blocking-command-api/application-test.yml"
       #(assoc-in % [:system/defs :server :handler] api/app)]]
     (let [jetty (system/instance sys [:server :jetty-adapter])
           {:keys [stop]} (command-processor sys)]
       (binding [*base-url* (server/http-local-url jetty)]
         (telemetry/with-span-tests
          [_ ["process-command"]]
          (nom-test> [res (send-http-command "test-command")
                      _ (is (= 200 (:status res)) "Should receive 200 OK")
                      actual (http/res->body res)
                      _ (is (= {"ok" "computer"}
                               (json/read-str (get-in actual ["data"])))
                            "Should receive data")])))
       (async/>!! stop :stop)))))
