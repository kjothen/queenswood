(ns ^:eftest/synchronized com.repldriven.mono.pulsar.interface-test
  (:refer-clojure :exclude [send])
  (:require
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.pulsar.interface :as SUT]

   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.http-client.interface :as http]
   [com.repldriven.mono.avro.interface :as avro]
   [com.repldriven.mono.system.interface :as system]

   [clojure.core.async :as async]
   [clojure.string :as string]

   [clojure.test :refer [deftest is testing]])
  (:import
   (org.apache.pulsar.client.admin PulsarAdmin)))

(defn- test-system
  []
  (error/nom-> (env/config "classpath:pulsar/application-test.yml" :test)
               system/defs
               system/start))

(deftest namespace-configuration-test
  (testing "Pulsar namespace configuration enforces encryption and topic schema"
    (system/with-system [sys (test-system)]
      (let [admin (system/instance sys [:pulsar :admin])
            namespace-url (SUT/admin-namespace-url admin "tenant-1" "namespace-1")
            expected {"autoTopicCreation" {"topicType" "string"
                                           "allowAutoTopicCreation" false
                                           "defaultNumPartitions" 1}
                      "encryptionRequired" true
                      "isAllowAutoUpdateSchema" false
                      "schemaCompatibilityStrategy" "FULL"
                      "schemaValidationEnforced" true}]
        ;; Send requests to pulsar admin
        (doseq [[k v] expected]
          (let [url (string/join "/" [namespace-url k])
                res (http/request {:url url :method :get})]
            (is (= v (http/res->body res)))))))))

(deftest encrypted-messages-matching-consumer-key-test
  (testing "Pulsar consumer with a matching decryption key can consume"
    (system/with-system [sys (test-system)]
      (let [producer (system/instance sys [:pulsar :producers :user])
            consumer (system/instance sys [:pulsar :consumers :user-1])
            schemas (system/instance sys [:pulsar :schemas])
            schema (SUT/schema->avro (get-in schemas [:user :schema]))
            msgs [{:name "Alice" :age 30} {:name "Bob" :age 25}
                  {:name "Charlie" :age 35}]
            props {"message" "user-msg"}]
        ;; Send messages to pulsar
        (doseq [msg msgs]
          (SUT/send producer msg {"properties" props}))
        ;; Receive messages from pulsar
        (let [{:keys [c stop]} (SUT/receive consumer schema 50)
              timeout (async/timeout 5000)
              [recv-msgs _] (async/alts!!
                             [(async/into [] (async/take (count msgs) c))
                              timeout])]
          (async/>!! stop :stop)
          (is (some? recv-msgs) "Should receive messages")
          (when recv-msgs
            (doseq [{:keys [message data]} recv-msgs]
              (is (not (error/anomaly? data)))
              (.acknowledge consumer message))
            (is (= msgs (mapv :data recv-msgs)) "Messages don't match")))))))

(deftest encrypted-messages-mismatched-consumer-key-test
  (testing "Pulsar consumer with a mismatching decryption key cannot consume"
    (system/with-system [sys (test-system)]
      (let [producer (system/instance sys [:pulsar :producers :user])
            consumer (system/instance sys [:pulsar :consumers :user-2])
            schemas (system/instance sys [:pulsar :schemas])
            schema (SUT/schema->avro (get-in schemas [:user :schema]))
            msgs [{:name "Alice" :age 30} {:name "Bob" :age 25}
                  {:name "Charlie" :age 35}]
            props {"message" "user-msg"}]
        ;; Send messages to pulsar
        (doseq [msg msgs]
          (SUT/send producer msg {"properties" props}))
        ;; Receive messages from pulsar
        (let [{:keys [c stop]} (SUT/receive consumer schema 50)
              timeout (async/timeout 5000)
              [recv-msgs _] (async/alts!!
                             [(async/into [] (async/take (count msgs) c))
                              timeout])]
          (async/>!! stop :stop)
          (is (some? recv-msgs) "Should receive messages")
          (when recv-msgs
            (for [{:keys [data]} recv-msgs]
              (is (= :pulsar/message-decrypt (error/kind data))
                  "Should return decrypt anomaly for mismatched key"))))))))

(deftest encrypted-messages-reader-test
  (testing "Pulsar reader with a matching decryption key can receive"
    (system/with-system [sys (test-system)]
      (let [producer (system/instance sys [:pulsar :producers :user])
            reader (system/instance sys [:pulsar :readers :user])
            schemas (system/instance sys [:pulsar :schemas])
            schema (SUT/schema->avro (get-in schemas [:user :schema]))
            msgs [{:name "Alice" :age 30} {:name "Bob" :age 25}
                  {:name "Charlie" :age 35}]
            props {"message" "user-msg"}]
        ;; Send messages to pulsar
        (doseq [msg msgs]
          (SUT/send producer msg {"properties" props}))
        ;; Read messages from pulsar
        (let [{:keys [c stop]} (SUT/read reader schema 50)
              timeout (async/timeout 5000)
              [recv-msgs _] (async/alts!!
                             [(async/into [] (async/take (count msgs) c))
                              timeout])]
          (async/>!! stop :stop)
          (is (some? recv-msgs) "Should receive messages")
          (is (= msgs (mapv :data recv-msgs)) "Messages don'tmatch"))))))
