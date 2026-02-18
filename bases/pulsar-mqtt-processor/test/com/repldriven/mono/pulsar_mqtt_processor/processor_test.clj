(ns ^:eftest/synchronized com.repldriven.mono.pulsar-mqtt-processor.processor-test
  (:require
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.pulsar-mqtt-processor.processor :as SUT]

   [com.repldriven.mono.db.interface :as db]
   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.migrator.interface :as migrator]
   [com.repldriven.mono.mqtt.interface :as mqtt]
   [com.repldriven.mono.pulsar.interface :as pulsar]
   [com.repldriven.mono.system.interface :as system]
   [com.repldriven.mono.telemetry.interface :as telemetry]

   [clojure.core.async :as async]
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing]]))

(defn- test-system
  []
  (error/nom-> (env/config "classpath:pulsar-mqtt-processor/application-test.yml" :test)
               system/defs
               system/start))

(defn- migrate
  [sys]
  (migrator/migrate
   (db/get-datasource (system/instance sys [:db :datasource]))
   "accounts/init-changelog.sql"))

(defn send-command
  "Simulates Sender - sends a command via Pulsar and blocks until the result is received via MQTT"
  [sys command-name data]
  (let [producer    (system/instance sys [:pulsar :producers :command])
        mqtt-client (system/instance sys [:mqtt :client])
        cmd-id      (str (java.util.UUID/randomUUID))
        reply-topic (str "replies/" cmd-id)
        reply-to    (str "mqtt://" reply-topic)
        p           (promise)]
    (telemetry/with-span ["send-command" {}]
      (mqtt/subscribe mqtt-client
                      {reply-topic 0}
                      (fn [_ _topic ^bytes payload]
                        (deliver p (json/read-str (String. payload "UTF-8")))))
      (pulsar/send producer
                   {:id             cmd-id
                    :command        command-name
                    :correlation_id cmd-id
                    :causation_id   nil
                    :traceparent    (telemetry/inject-traceparent)
                    :tracestate     nil
                    :data           (json/write-str data)
                    :reply_to       reply-to}))
    (deref p 5000 ::timeout)))

(deftest process-command-test
  (testing "Commands sent via Pulsar are processed and replied to via MQTT"
    (system/with-system [sys (test-system)]
      (migrate sys)
      (let [{:keys [stop]} (SUT/run sys)]
        (telemetry/with-span-tests [_ ["send-command" "process-command"]]
          (let [result (send-command sys "open-account" {"account-id" "acc-api-test"
                                                         "name"       "API Test Account"
                                                         "currency"   "GBP"})]
            (is (not= ::timeout result) "Should receive a reply within timeout")
            (is (= "ok" (get result "status")))
            (is (= "acc-api-test" (get result "account-id")))))
        (async/>!! stop :stop)))))
