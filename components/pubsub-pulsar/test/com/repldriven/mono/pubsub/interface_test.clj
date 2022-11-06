(ns com.repldriven.mono.pubsub.interface-test
  (:refer-clojure :exclude [send])
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.http.interface :as http]
            [com.repldriven.mono.pubsub.interface :as SUT]
            [com.repldriven.mono.pubsub.pulsar.env-reader]
            [com.repldriven.mono.schema-avro.interface :as schema-avro]
            [com.repldriven.mono.system.interface :as system
             :refer [with-system]])
  (:import (java.util HashMap)
           (java.util.concurrent TimeUnit)
           (org.apache.pulsar.client.admin PulsarAdmin)
           (org.apache.pulsar.client.api Consumer Message MessageId Producer)
           (org.apache.pulsar.common.api EncryptionContext)
           (org.apache.pulsar.common.policies.data
             SchemaAutoUpdateCompatibilityStrategy)))

(def ^:dynamic ^:private *sys* nil)

(defn sys-fixture
  [f]
  (env/set-env! (io/resource "pubsub/test-env.edn") :test)
  (with-system [sys (SUT/configure-system (get-in @env/env [:system :pubsub]))]
    (binding [*sys* sys]
      (f))))

(use-fixtures :once sys-fixture)

(defn- get-schema
  [schemas k]
  (-> (get-in schemas [k :schema])
    .getSchemaInfo
    .toString
    json/read-str
    (get "schema")))

(deftest developer-test
  (testing "Developers should be able to start a pulsar system from a REPL"
    (is (some? *sys*))))

(deftest encrypted-message-matching-consumer-key-test
  (testing "Pulsar consumer with a matching decryption key can consume"
    (let [producer (system/instance *sys* [:pubsub :producer])
          consumer (system/instance *sys* [:pubsub :consumer])
          schemas (system/instance *sys* [:pubsub :schemas])]

      (let [schema (schema-avro/json->schema
                     (json/write-str (get-schema schemas :user)))
            data {:name "hardcastle" :age 19}
            props (HashMap. {"message" "user-msg"})]
        (SUT/send producer (schema-avro/serialize schema data)
          (HashMap. {"properties" props}))

        (let [^Message recv-msg (SUT/receive consumer 500)
              recv-data (some-> recv-msg .getData)
              recv-props (some-> recv-msg .getProperties)]
          (some->> recv-msg (.acknowledge consumer))
          (is (= data (some->> recv-data (schema-avro/deserialize-same schema))))
          (is (= (get props "message") (get recv-props "message"))))))))

(deftest encrypted-message-mismatched-consumer-key-test
  (testing "Pulsar consumer with a mismatching decryption key cannot consume"
    (let [producer (system/instance *sys* [:pubsub :producer])
          consumer (system/instance *sys* [:pubsub :consumer-2])
          schemas (system/instance *sys* [:pubsub :schemas])]

      (let [schema (schema-avro/json->schema
                     (json/write-str (get-schema schemas :user)))
            data {:name "hardcastle" :age 19}
            props {"message" "user-msg"}]
        (SUT/send producer (schema-avro/serialize schema data)
          (HashMap. {"properties" props}))

        (let [^Message recv-msg  (SUT/receive consumer 500)
              recv-data (some-> recv-msg .getData)
              recv-props (some-> recv-msg .getProperties)]
          (is (nil? recv-msg)))))))

(deftest namespace-configuration-test
  (testing "Pulsar namespace configuration enforces encryption and topic schema"
    (let [admin (system/instance *sys* [:pubsub :admin])]

      (let [service-url (.getServiceUrl admin)
            namespaces-url (string/join
                             "/" [service-url "admin/v2/namespaces"])
            namespace-url (string/join
                            "/" [namespaces-url "tenant-1/namespace-1"])
            expected {"autoTopicCreation" {"topicType" "string"
                                           "defaultNumPartitions" 1
                                           "allowAutoTopicCreation" false}
                      "encryptionRequired" true
                      "isAllowAutoUpdateSchema" false
                      "schemaCompatibilityStrategy" "FULL"
                      "schemaValidationEnforced" true}]
        (dorun
          (map (fn [[k v]]
                 (let [url (string/join "/" [namespace-url k])]
                   (is (= v (-> (http/request {:url url :method :get})
                                (http/res->json))))))
            expected))))))

(comment
  (env/set-env! (io/resource "pubsub/test-env.edn") :test)
  (def system-config (SUT/configure-system
                      (get-in @env/env [:system :pubsub])))
  (def running-system (system/start system-config))
  (system/stop running-system)
 )
