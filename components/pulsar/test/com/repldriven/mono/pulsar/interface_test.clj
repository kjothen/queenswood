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
    [clojure.java.io :as io]
    [clojure.string :as string]

    [clojure.test :refer [deftest is testing]])
  (:import
    (org.apache.pulsar.client.admin PulsarAdmin)
    (org.apache.pulsar.client.api Consumer Message Reader)))

(defn- test-system
  []
  (error/nom-> (env/config "classpath:pulsar/test-application.yml" :test)
               system/defs
               system/start))

(deftest namespace-configuration-test
  (testing "Pulsar namespace configuration enforces encryption and topic schema"
    (system/with-system [sys (test-system)]
      (let [^PulsarAdmin admin (system/instance sys [:pulsar :admin])
            service-url (.getServiceUrl admin)
            namespaces-url (string/join "/" [service-url "admin/v2/namespaces"])
            namespace-url (string/join "/"
                                       [namespaces-url "tenant-1/namespace-1"])
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
            (is (= v (http/res->body res)))))
        expected))))

(deftest encrypted-message-matching-consumer-key-test
  (testing "Pulsar consumer with a matching decryption key can consume"
    (system/with-system [sys (test-system)]
      (let [producer (system/instance sys [:pulsar :producer])
            ^Consumer consumer (system/instance sys [:pulsar :consumers :c1])
            schemas (system/instance sys [:pulsar :schemas])
            schema (SUT/schema->avro (get-in schemas [:user :schema]))
            data {:name "hardcastle" :age 19}
            props {"message" "user-msg"}]
        (SUT/send producer (avro/serialize schema data) {"properties" props})
        (let [{:keys [c stop]} (SUT/receive consumer schema 50)
              timeout (async/timeout 5000)
              [received _] (async/alts!! [c timeout])]
          (async/>!! stop :stop)
          (is (some? received) "Should receive a message")
          (when received
            (let [{:keys [message data]} received]
              (is (not (error/anomaly? data)))
              (.acknowledge consumer message)
              (is (= data {:name "hardcastle" :age 19}))
              (is (= (get (.getProperties ^Message message) "message")
                     "user-msg")))))))))

(deftest encrypted-message-mismatched-consumer-key-test
  (testing "Pulsar consumer with a mismatching decryption key cannot consume"
    (system/with-system [sys (test-system)]
      (let [producer (system/instance sys [:pulsar :producer])
            consumer (system/instance sys [:pulsar :consumers :c2])
            schemas (system/instance sys [:pulsar :schemas])
            schema (SUT/schema->avro (get-in schemas [:user :schema]))
            data {:name "hardcastle" :age 19}
            props {"message" "user-msg"}]
        (SUT/send producer (avro/serialize schema data) {"properties" props})
        (let [{:keys [c stop]} (SUT/receive consumer schema 50)
              timeout (async/timeout 5000)
              [received _] (async/alts!! [c timeout])]
          (async/>!! stop :stop)
          (is (some? received) "Should receive a message")
          (when received
            (let [{:keys [data]} received]
              (is (= :pulsar/message-decrypt (error/kind data))
                  "Should return decrypt anomaly for mismatched key"))))))))


(deftest reader-test
  (testing "Pulsar reader should consume messages published by producer"
    (system/with-system [sys (test-system)]
      (let [producer (system/instance sys [:pulsar :producer])
            ^Reader reader (system/instance sys [:pulsar :reader])
            user-schema (avro/json->schema (slurp (io/resource
                                                   "avro/user.avsc.json")))
            test-messages [{:name "Alice" :age 30} {:name "Bob" :age 25}
                           {:name "Charlie" :age 35}]]
        ;; Send messages to pulsar
        (doseq [msg test-messages]
          (let [serialized (avro/serialize user-schema msg)]
            (SUT/send producer serialized)))
        ;; Read messages from pulsar reader channel
        (let [{:keys [c stop]} (SUT/read reader user-schema 50)
              timeout (async/timeout 5000)
              [received-messages _]
              (async/alts!!
               [(async/into [] (async/take (count test-messages) c)) timeout])]
          (async/>!! stop :stop) ; Send stop signal
          (is (= test-messages received-messages) "Messages don't match"))))))
