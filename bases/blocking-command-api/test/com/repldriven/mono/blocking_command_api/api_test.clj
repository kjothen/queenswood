(ns ^:eftest/synchronized com.repldriven.mono.blocking-command-api.api-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.avro.interface

    [com.repldriven.mono.blocking-command-api.api :as api]

    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing]]))

(def ^:dynamic *base-url* "http://localhost:{PORT}")

(defn send-http-command
  "Simulates Client - synchronous command request"
  [command-name data]
  (http/request {:method :post
                 :url (str *base-url* "/api/command")
                 :headers {"Content-Type" "application/json"
                           "Idempotency-Key" (str (util/uuidv7))}
                 :body (json/write-str {"command" command-name "data" data})}))

(defn command-processor
  "Simulates Processor - receives command envelopes and
  replies via message-bus"
  [sys]
  (let [bus (system/instance sys [:message-bus :bus])]
    (message-bus/subscribe
     bus
     :command
     (fn [data]
       (let [parent-ctx (telemetry/extract-parent-context data)]
         (telemetry/with-span-parent
          "process-command"
          parent-ctx
          (select-keys data
                       ["id" "command" "correlation_id"
                        "causation_id"])
          (fn []
            (message-bus/send
             bus
             :command-response
             (command/command-response data {"record_id" "test-123"})))))))
    {:stop (fn [] (message-bus/unsubscribe bus :command))}))

(deftest request-reply-test
  (testing "Request-Reply via Pulsar"
    (with-test-system
     [sys
      ["classpath:blocking-command-api/application-test.yml"
       #(assoc-in % [:system/defs :server :handler] api/app)]]
     (let [jetty (system/instance sys [:server :jetty-adapter])
           {:keys [stop]} (command-processor sys)]
       (binding [*base-url* (server/http-local-url jetty)]
         (telemetry/with-span-tests
          [_ ["process-command"]]
          (nom-test> [res (send-http-command "open-account"
                                             {"account_id" "acc-test"
                                              "name" "Test Account"
                                              "currency" "GBP"})
                      _ (is (= 200 (:status res)) "Should receive 200 OK")
                      actual (http/res->body res)
                      _ (is (= "ACCEPTED" (get actual "status"))
                            "Should have ACCEPTED status")
                      _ (is (= "test-123" (get actual "record_id"))
                            "Should have record_id")])))
       (stop)))))
