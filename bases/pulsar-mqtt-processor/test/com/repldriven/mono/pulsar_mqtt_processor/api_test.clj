(ns ^:eftest/synchronized com.repldriven.mono.pulsar-mqtt-processor.api-test
  (:require
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.pulsar-mqtt-processor.main :as SUT]

   [com.repldriven.mono.db.interface :as db]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.migrator.interface :as migrator]
   [com.repldriven.mono.mqtt.interface :as mqtt]
   [com.repldriven.mono.pulsar.interface :as pulsar]
   [com.repldriven.mono.system.interface :as system]
   [com.repldriven.mono.test.interface :as test]

   [clojure.core.async :as async]
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing]]))

(defn- migrate
  [sys]
  (migrator/migrate
   (db/get-datasource (system/instance sys [:db :datasource]))
   "accounts/init-changelog.sql"))

(deftest process-command-test
  (testing "Commands sent via Pulsar are processed and replied to via MQTT"
    (let [sys (SUT/start "classpath:pulsar-mqtt-processor/application-test.yml" :test)]
      (is (not (error/anomaly? sys)) "System should start")
      (is (system/system? sys) "System should be valid")
      (when (system/system? sys)
        (migrate sys)
        (let [{:keys [stop]} (SUT/run sys)]
          (error/with-let-anomaly?
            [producer    (system/instance sys [:pulsar :producers :command])
             mqtt-client (system/instance sys [:mqtt :client])
             cmd-id      (str (java.util.UUID/randomUUID))
             reply-topic (str "replies/" cmd-id)
             p           (promise)
             _ (mqtt/subscribe mqtt-client
                               {reply-topic 0}
                               (fn [_ _topic ^bytes payload]
                                 (deliver p (json/read-str (String. payload "UTF-8")))))
             _ (pulsar/send producer
                            {:id             cmd-id
                             :command        "open-account"
                             :correlation_id cmd-id
                             :causation_id   nil
                             :traceparent    nil
                             :tracestate     nil
                             :data           (json/write-str {"account-id" "acc-api-test"
                                                              "name"       "API Test Account"
                                                              "currency"   "GBP"})})
             result (deref p 5000 ::timeout)
             _      (is (not= ::timeout result) "Should receive a reply within timeout")
             _      (is (= "ok" (get result "status")))
             _      (is (= "acc-api-test" (get result "account-id")))]
            test/refute-anomaly)
          (async/>!! stop :stop)
          (system/stop sys))))))
