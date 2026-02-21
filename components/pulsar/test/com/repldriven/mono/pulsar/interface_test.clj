(ns ^:eftest/synchronized com.repldriven.mono.pulsar.interface-test
  (:refer-clojure :exclude [send])
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.pulsar.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :as test]

    [clojure.core.async :as async]
    [clojure.string :as string]

    [clojure.test :refer [deftest is testing]]))

(deftest pulsar-test
  (test/with-test-system
   [sys "classpath:pulsar/application-test.yml"]
   (let [admin (system/instance sys [:pulsar :admin])
         producer (system/instance sys [:pulsar :producers :user])
         consumer-1 (system/instance sys [:pulsar :consumers :user-1])
         consumer-2 (system/instance sys [:pulsar :consumers :user-2])
         reader (system/instance sys [:pulsar :readers :user])
         schemas (system/instance sys [:pulsar :schemas])
         schema (SUT/schema->avro (get-in schemas [:user :schema]))
         msgs [{"name" "Alice" "age" 30} {"name" "Bob" "age" 25}
               {"name" "Charlie" "age" 35}]
         props {"message" "user-msg"}]
     (testing
       "Pulsar namespace configuration enforces encryption and topic schema"
       (let [namespace-url
             (SUT/admin-namespace-url admin "tenant-1" "namespace-1")
             expected {"autoTopicCreation" {"topicType" "string"
                                            "allowAutoTopicCreation" false
                                            "defaultNumPartitions" 1}
                       "encryptionRequired" true
                       "isAllowAutoUpdateSchema" false
                       "schemaCompatibilityStrategy" "FULL"
                       "schemaValidationEnforced" true}]
         (doseq [[k v] expected]
           (let [url (string/join "/" [namespace-url k])
                 res (http/request {:url url :method :get})]
             (is (= v (http/res->body res)))))))
     (testing "Pulsar consumer with a matching decryption key can consume"
       (doseq [msg msgs] (SUT/send producer msg {"properties" props}))
       (let [{:keys [c stop]} (SUT/receive consumer-1 schema 50)
             timeout (async/timeout 5000)
             [recv-msgs _] (async/alts!! [(async/into []
                                                      (async/take (count msgs)
                                                                  c)) timeout])]
         (async/>!! stop :stop)
         (is (some? recv-msgs) "Should receive messages")
         (when recv-msgs
           (doseq [{:keys [message data]} recv-msgs]
             (is (not (error/anomaly? data)))
             (.acknowledge consumer-1 message))
           (is (= msgs (mapv :data recv-msgs)) "Messages don't match"))))
     (testing "Pulsar consumer with a mismatching decryption key cannot consume"
       (doseq [msg msgs] (SUT/send producer msg {"properties" props}))
       (let [{:keys [c stop]} (SUT/receive consumer-2 schema 50)
             timeout (async/timeout 5000)
             [recv-msgs _] (async/alts!! [(async/into []
                                                      (async/take (count msgs)
                                                                  c)) timeout])]
         (async/>!! stop :stop)
         (is (some? recv-msgs) "Should receive messages")
         (when recv-msgs
           (for [{:keys [data]} recv-msgs]
             (is (= :pulsar/message-decrypt (error/kind data))
                 "Should return decrypt anomaly for mismatched key")))))
     (testing "Pulsar reader with a matching decryption key can receive"
       (doseq [msg msgs] (SUT/send producer msg {"properties" props}))
       (let [{:keys [c stop]} (SUT/read reader schema 50)
             timeout (async/timeout 5000)
             [recv-msgs _] (async/alts!! [(async/into []
                                                      (async/take (count msgs)
                                                                  c)) timeout])]
         (async/>!! stop :stop)
         (is (some? recv-msgs) "Should receive messages")
         (is (= msgs (mapv :data recv-msgs)) "Messages don't match"))))))
