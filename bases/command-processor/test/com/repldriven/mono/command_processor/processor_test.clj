(ns ^:eftest/synchronized com.repldriven.mono.command-processor.processor-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.migrator.interface

    [com.repldriven.mono.command-processor.processor :as SUT]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn send-command
  "Simulates Sender - Avro-serializes payload, sends a
  command envelope via message-bus, and blocks until the
  result is received."
  [sys command-name data]
  (let [bus (system/instance sys [:message-bus :bus])
        schemas (system/instance sys [:avro :serde])
        schema (get schemas command-name)
        payload (avro/serialize schema data)
        cmd-id (str (java.util.UUID/randomUUID))]
    (telemetry/with-span ["send-command" {}]
                         (command/send bus
                                       {"id" cmd-id
                                        "command" command-name
                                        "correlation_id" cmd-id
                                        "causation_id" nil
                                        "traceparent"
                                        (telemetry/inject-traceparent)
                                        "tracestate" nil
                                        "payload" payload
                                        "reply_to" nil}))))

(deftest process-command-test
  (testing "Commands sent are processed and replied to via message-bus"
    (with-test-system
     [sys "classpath:command-processor/application-test.yml"]
     (let [{:keys [stop]} (SUT/run sys)]
       (telemetry/with-span-tests
        [_ ["send-command" "process-command"]]
        (nom-test> [result (send-command sys
                                         "open-account"
                                         {"account_id" "acc-api-test"
                                          "name" "API Test Account"
                                          "currency" "GBP"})
                    _ (is (= "ACCEPTED" (get result "status")))
                    _ (is (= "acc-api-test" (get result "record_id")))]))
       (stop)))))
