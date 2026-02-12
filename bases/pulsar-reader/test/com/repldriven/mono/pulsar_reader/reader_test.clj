(ns com.repldriven.mono.pulsar-reader.reader-test
  (:require
   com.repldriven.mono.pulsar.interface
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.pulsar.interface :as pulsar]
   [com.repldriven.mono.schema-avro.interface :as avro]
   [com.repldriven.mono.system.interface :as system]

   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]])
  (:import (org.apache.pulsar.client.api Reader Message)))

(deftest ^:repl reader-test
  (testing "Pulsar reader should consume messages published by producer"
    ;; NOTE: This test works in REPL but may timeout in automated test runners
    (let [sys (error/nom-> (env/config "classpath:pulsar-reader/test-application.yml" :test)
                           system/defs
                           system/start)]
      (is (not (error/anomaly? sys)) "System should start")
      (is (system/system? sys) "System should be valid")
      (when (system/system? sys)
        (system/with-system sys
          (let [producer (system/instance sys [:pulsar :producer])
                ^Reader reader (system/instance sys [:pulsar :reader])
                user-schema (avro/json->schema (slurp (io/resource "schema-avro/user.avsc.json")))
                test-messages [{:name "Alice" :age 30}
                               {:name "Bob" :age 25}
                               {:name "Charlie" :age 35}]]

            ;; Wait for Pulsar to be fully ready
            (Thread/sleep 2000)

            ;; Publish test messages
            (doseq [msg test-messages]
              (let [serialized (avro/serialize user-schema msg)]
                (pulsar/send producer serialized)))

            ;; Give messages time to be available
            (Thread/sleep 500)

            ;; Read messages back
            (let [received-messages (atom [])
                  timeout-ms 3000]
              (dotimes [_ (count test-messages)]
                (when (.hasMessageAvailable reader)
                  (let [^Message msg (.readNext reader timeout-ms java.util.concurrent.TimeUnit/MILLISECONDS)]
                    (when msg
                      (let [deserialized (avro/deserialize-same user-schema (.getData msg))]
                        (swap! received-messages conj deserialized))))))

              ;; Verify we received all messages
              (is (= (count test-messages) (count @received-messages))
                  (str "Should receive " (count test-messages) " messages, got " (count @received-messages)))

              ;; Verify message contents
              (doseq [[expected received] (map vector test-messages @received-messages)]
                (is (= (:name expected) (:name received))
                    (str "Message name should match: expected " (:name expected) ", got " (:name received)))
                (is (= (:age expected) (:age received))
                    (str "Message age should match: expected " (:age expected) ", got " (:age received)))))))))))
