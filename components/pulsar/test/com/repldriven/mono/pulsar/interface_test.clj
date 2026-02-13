(ns ^:eftest/synchronized com.repldriven.mono.pulsar.interface-test
  (:refer-clojure :exclude [send])
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.pulsar.interface :as SUT]

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.schema-avro.interface :as schema-avro]
    [com.repldriven.mono.system.interface :as system]

    [clojure.core.async :as async]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.string :as string]

    [clojure.test :refer [deftest is testing use-fixtures]])
  (:import
    (org.apache.pulsar.client.admin PulsarAdmin)
    (org.apache.pulsar.client.api Consumer Message Reader Schema)
    (org.apache.pulsar.common.api EncryptionContext)
    (org.apache.pulsar.common.schema SchemaInfo)

    (java.util Optional)))

(defn- get-schema
  [schemas k]
  (let [^Schema schema (get-in schemas [k :schema])
        ^SchemaInfo schema-info (.getSchemaInfo schema)]
    (-> schema-info
        .toString
        json/read-str
        (get "schema"))))

;; Shared system for all tests
(def ^:dynamic *sys* nil)

(defn system-fixture
  [f]
  (let [sys (error/nom-> (env/config "classpath:pulsar/test-application.yml"
                                     :test)
                         system/defs
                         system/start)]
    (is (not (error/anomaly? sys)) "System should start without errors")
    (is (system/system? sys) "System should be valid")
    (when (system/system? sys)
      (try (binding [*sys* sys] (f)) (finally (system/stop sys))))))

(use-fixtures :each system-fixture)

(deftest encrypted-message-matching-consumer-key-test
  (testing "Pulsar consumer with a matching decryption key can consume"
    (let [producer (system/instance *sys* [:pulsar :producer])
          ^Consumer consumer (system/instance *sys* [:pulsar :consumers :c1])
          schemas (system/instance *sys* [:pulsar :schemas])
          schema (schema-avro/json->schema (json/write-str (get-schema schemas
                                                                       :user)))
          data {:name "hardcastle" :age 19}
          props {"message" "user-msg"}]
      (SUT/send producer
                (schema-avro/serialize schema data)
                {"properties" props})
      (let [^Message recv-msg (SUT/receive consumer 1000)]
        (is (some? recv-msg) "Should receive a message")
        (when recv-msg
          (.acknowledge consumer recv-msg)
          (is (= data
                 (schema-avro/deserialize-same schema (.getData recv-msg))))
          (is (= (get props "message")
                 (get (.getProperties recv-msg) "message"))))))))

(deftest encrypted-message-mismatched-consumer-key-test
  (testing "Pulsar consumer with a mismatching decryption key cannot consume"
    (let [producer (system/instance *sys* [:pulsar :producer])
          consumer (system/instance *sys* [:pulsar :consumers :c2])
          schemas (system/instance *sys* [:pulsar :schemas])
          schema (schema-avro/json->schema (json/write-str (get-schema schemas
                                                                       :user)))
          data {:name "hardcastle" :age 19}
          props {"message" "user-msg"}]
      (SUT/send producer
                (schema-avro/serialize schema data)
                {"properties" props})
      (let [^Message recv-msg (SUT/receive consumer 1000)]
        (is (some? recv-msg) "Should receive a message")
        (when recv-msg
          (let [^Optional ctx (.getEncryptionCtx recv-msg)]
            (is (true? (.isPresent ctx)))
            (when (.isPresent ctx)
              (is (true? (.isEncrypted ^EncryptionContext (.get ctx)))))))))))

(deftest namespace-configuration-test
  (testing "Pulsar namespace configuration enforces encryption and topic schema"
    (let [^PulsarAdmin admin (system/instance *sys* [:pulsar :admin])
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
          (tap> [k v res])
          (is (= v (http/res->body res)))))
      expected)))

(deftest reader-test
  (testing "Pulsar reader should consume messages published by producer"
    (let [producer (system/instance *sys* [:pulsar :producer])
          ^Reader reader (system/instance *sys* [:pulsar :reader])
          user-schema (schema-avro/json->schema
                       (slurp (io/resource "schema-avro/user.avsc.json")))
          test-messages [{:name "Alice" :age 30} {:name "Bob" :age 25}
                         {:name "Charlie" :age 35}]]
      ;; Send messages to pulsar
      (doseq [msg test-messages]
        (let [serialized (schema-avro/serialize user-schema msg)]
          (SUT/send producer serialized)))
      ;; Read messages from pulsar reader channel
      (let [{:keys [c stop]} (SUT/read reader user-schema 50)
            timeout (async/timeout 5000)
            [received-messages _]
            (async/alts!! [(async/into [] (async/take (count test-messages) c))
                           timeout])]
        (async/>!! stop :stop) ; Send stop signal
        (is (= test-messages received-messages) "Messages don't match")))))
