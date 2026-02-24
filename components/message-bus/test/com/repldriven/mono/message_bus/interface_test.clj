(ns ^:eftest/synchronized com.repldriven.mono.message-bus.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.message-bus.interface :as SUT]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- send-receive-test
  ([sys channel]
   (let [bus (system/instance sys [:message-bus :bus])
         received (promise)]
     (SUT/subscribe bus channel (fn [data] (deliver received data)))
     (nom-test> [_ (SUT/send bus
                             channel
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
     (SUT/unsubscribe bus channel)))
  ([sys send-channel reply-channel]
   (let [bus (system/instance sys [:message-bus :bus])
         received (promise)]
     (SUT/subscribe bus reply-channel (fn [data] (deliver received data)))
     (SUT/subscribe bus
                    send-channel
                    (fn [data] (SUT/send bus reply-channel data)))
     (nom-test> [_ (SUT/send bus
                             send-channel
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
     (SUT/unsubscribe bus send-channel)
     (SUT/unsubscribe bus reply-channel))))

(deftest message-bus-pulsar-pulsar-test
  (testing "Send and receive via Pulsar"
    (with-test-system
     [sys "classpath:message-bus/application-pulsar-pulsar-test.yml"]
     (send-receive-test sys :command))))

(deftest message-bus-pulsar-mqtt-test
  (testing "Send via Pulsar, receive reply via MQTT"
    (with-test-system [sys
                       "classpath:message-bus/application-pulsar-mqtt-test.yml"]
                      (send-receive-test sys :command :reply))))

(deftest message-bus-mqtt-pulsar-test
  (testing "Send via MQTT, receive reply via Pulsar"
    (with-test-system [sys
                       "classpath:message-bus/application-mqtt-pulsar-test.yml"]
                      (send-receive-test sys :command :reply))))

(deftest message-bus-mqtt-mqtt-test
  (testing "Send and receive via MQTT"
    (with-test-system [sys
                       "classpath:message-bus/application-mqtt-mqtt-test.yml"]
                      (send-receive-test sys :command))))
