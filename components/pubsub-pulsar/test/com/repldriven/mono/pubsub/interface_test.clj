(ns com.repldriven.mono.pubsub.interface-test
  (:refer-clojure :exclude [send])
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.http-client.interface :as http]
            [com.repldriven.mono.pubsub.interface :as SUT]
            [com.repldriven.mono.schema-avro.interface :as schema-avro]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.test-system.interface :as test-system])
  (:import (java.security Security)
           (java.util HashMap)
           (org.apache.pulsar.client.api Message)
           (org.bouncycastle.jce.provider BouncyCastleProvider)))

;; Register Bouncy Castle security provider for encryption
(when (nil? (Security/getProvider "BC"))
  (Security/addProvider (BouncyCastleProvider.)))

(use-fixtures :once
  (test-system/fixture "classpath:pubsub/test-application.yml" :test)
  (fn [f] (system/with-*sys* test-system/*sysdef* (f))))

(defn- get-schema
  [schemas k]
  (-> (get-in schemas [k :schema])
      .getSchemaInfo
      .toString
      json/read-str
      (get "schema")))

(deftest system-test
  (testing "Developers should be able to start a pulsar system from a REPL"
    (is (some? system/*sys*))))

(deftest ^:repl encrypted-message-matching-consumer-key-test
  (testing "Pulsar consumer with a matching decryption key can consume"
    ;; SKIPPED: This test works in REPL but times out in test runners
    (let [producer (system/instance system/*sys* [:pubsub :producer])
          consumer (system/instance system/*sys* [:pubsub :consumers :c1])
          schemas (system/instance system/*sys* [:pubsub :schemas])
          schema (schema-avro/json->schema
                  (json/write-str (get-schema schemas :user)))
          data {:name "hardcastle" :age 19}
          props (HashMap. {"message" "user-msg"})]
      (SUT/send producer
                (schema-avro/serialize schema data)
                (HashMap. {"properties" props}))
      (let [^Message recv-msg (SUT/receive consumer 1000)
            recv-data (some-> recv-msg
                              .getData)
            recv-props (some-> recv-msg
                               .getProperties)]
        (some->> recv-msg
                 (.acknowledge consumer))
        (is (= data
               (some->> recv-data
                        (schema-avro/deserialize-same schema))))
        (is (= (get props "message") (get recv-props "message")))))))

(deftest ^:repl encrypted-message-mismatched-consumer-key-test
  (testing "Pulsar consumer with a mismatching decryption key cannot consume"
    ;; SKIPPED: This test works in REPL but times out in test runners
    (let [producer (system/instance system/*sys* [:pubsub :producer])
          consumer (system/instance system/*sys* [:pubsub :consumers :c2])
          schemas (system/instance system/*sys* [:pubsub :schemas])
          schema (schema-avro/json->schema
                  (json/write-str (get-schema schemas :user)))
          data {:name "hardcastle" :age 19}
          props {"message" "user-msg"}]

      (SUT/send producer
                (schema-avro/serialize schema data)
                (HashMap. {"properties" props}))
      (let [^Message recv-msg (SUT/receive consumer 1000)]
        (is (nil? recv-msg))))))

(deftest namespace-configuration-test
  (testing
   "Pulsar namespace configuration enforces encryption and topic schema"
    (let [admin (system/instance system/*sys* [:pubsub :admin])
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
              res (-> (http/request {:url url :method :get}))]
          (tap> [k v res])
          (is (= v (http/res->body res)))))
      expected)))


