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
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.test.interface :as test]
    [com.repldriven.mono.utility.interface :as util]

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
  [command-name]
  (http/request {:method :post
                 :url (str *base-url* "/api/command")
                 :headers {"Content-Type" "application/json"
                           "Idempotency-Key" (str (util/uuidv7))}
                 :body (json/write-str {"command" command-name})}))

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
             (fn [] data))))]
    (command/process consumer
                     mqtt-client
                     schema
                     process-fn
                     {:timeout-ms 1000})))

(deftest request-pulsar-reply-mqtt-test
  (testing "Request-Reply with Pulsar and MQTT"
    (system/with-system [sys (test-system)]
      (is (system/system? sys) "System should be valid")
      (let [jetty (system/instance sys [:server :jetty-adapter])
            {:keys [stop]} (command-processor sys)]
        (binding [*base-url* (server/http-local-url jetty)]
          (telemetry/with-span-tests [_ ["process-command"]]
                                     (error/with-let-anomaly?
                                       [res (send-http-command "test-command")
                                        _ (is (= 200 (:status res))
                                              "Should receive 200 OK")
                                        actual (http/res->body res)
                                        _ (is (= "test-command"
                                                 (get-in actual
                                                         ["result" "command"]))
                                              "Should receive command name")]
                                       test/refute-anomaly)))
        (async/>!! stop :stop)))))
