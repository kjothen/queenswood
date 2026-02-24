(ns ^:eftest/synchronized com.repldriven.mono.message-bus.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.message-bus.interface :as SUT]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(deftest message-bus-test
  (testing "Send command via bus producer, receive via bus consumer"
    (with-test-system
     [sys "classpath:message-bus/application-test.yml"]
     (let [bus (system/instance sys [:message-bus :bus])
           received (promise)]
       (SUT/subscribe bus :command (fn [data] (deliver received data)))
       (nom-test> [_ (SUT/send bus
                               :command
                               {:id "test-1"
                                :command "test-command"
                                :correlation_id "corr-1"
                                :causation_id nil
                                :traceparent nil
                                :tracestate nil
                                :data "{}"
                                :reply_to "mqtt://test/reply"})])
       (let [data (deref received 10000 ::timeout)]
         (is (not= ::timeout data) "Should receive message within timeout")
         (when (not= ::timeout data)
           (is (= "test-1" (get data "id")))
           (is (= "test-command" (get data "command")))))
       (SUT/unsubscribe bus :command)))))
