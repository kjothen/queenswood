(ns com.repldriven.mono.pulsar.interface-test
  (:refer-clojure :exclude [send])
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.pulsar.env-reader]
            [com.repldriven.mono.pulsar.interface :as SUT]
            [com.repldriven.mono.system.interface :as system
             :refer [with-system]]
            [deercreeklabs.lancaster :as avro]
            [org.httpkit.client :as httpkit])
  (:import (java.util.concurrent TimeUnit)
           (org.apache.pulsar.client.admin PulsarAdmin)
           (org.apache.pulsar.client.api Consumer Message MessageId Producer)
           (org.apache.pulsar.common.api EncryptionContext)
           (org.apache.pulsar.common.policies.data
            SchemaAutoUpdateCompatibilityStrategy)))


(def ^:dynamic ^:private *sys* nil)

(defn sys-fixture
  [f]
  (env/set-env! (io/resource "pulsar/test-env.edn") :test)
  (with-system [sys (SUT/configure-system
                     (get-in @env/env [:system :pulsar]))]
    (binding [*sys* sys]
      (f))))

(use-fixtures :once sys-fixture)

(defn- http-get-json
  [url]
  (json/read-str
   (:body @(httpkit/get url {:headers {"Accept" "application/json"}}))))

(defn- get-schema
  [schemas k]
  (-> (get-in schemas [k :schema])
      .getSchemaInfo
      .toString
      json/read-str
      (get "schema")))

(defn- publish-message
  [producer props schema data]
  (.. producer newMessage
      (properties props)
      (value (avro/serialize schema data))
      send))

(deftest developer-test
  (testing "Developers should be able to start a pulsar system from a REPL"
    (is (some? *sys*))))

(deftest encrypted-message-matching-consumer-key-test
  (testing "Pulsar consumer with a matching decryption key can consume"
    (let [producer (system/instance *sys* [:pulsar :producer])
          consumer (system/instance *sys* [:pulsar :consumer])
          schemas (system/instance *sys* [:pulsar :schemas])]

      (let [schema (avro/json->schema (json/write-str (get-schema schemas :user)))
            data {:name "hardcastle" :age 19}
            props {"message" "user-msg"}]
        (publish-message producer props schema data)

        (let [^Message recv-msg (.. consumer (receive 500 TimeUnit/MILLISECONDS))
              recv-data (some-> recv-msg .getData)
              recv-props (some-> recv-msg .getProperties)]
          (some->> recv-msg (.acknowledge consumer))
          (is (= data (some->> recv-data (avro/deserialize-same schema))))
          (is (= (get props "message") (get recv-props "message"))))))))

(deftest encrypted-message-mismatched-consumer-key-test
  (testing "Pulsar consumer with a mismatching decryption key cannot consume"
    (let [producer (system/instance *sys* [:pulsar :producer])
          consumer (system/instance *sys* [:pulsar :consumer-2])
          schemas (system/instance *sys* [:pulsar :schemas])]

      (let [schema (avro/json->schema (json/write-str (get-schema schemas :user)))
            data {:name "hardcastle" :age 19}
            props {"message" "user-msg"}]
        (publish-message producer props schema data)

        (let [^Message recv-msg (.. consumer (receive 500 TimeUnit/MILLISECONDS))
              recv-data (some-> recv-msg .getData)
              recv-props (some-> recv-msg .getProperties)]
          (is (nil? recv-msg)))))))

(deftest namespace-configuration-test
  (testing "Pulsar namespace configuration enforces encryption and topic schema"
    (let [admin (system/instance *sys* [:pulsar :admin])]

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
                  (is (= v (http-get-json url)))))
              expected))))))

(comment
  (env/set-env! (io/resource "pulsar/test-env.edn") :test)
  (def system-config (SUT/configure-system
                      (get-in @env/env [:system :pulsar])))
  (def running-system (system/start system-config))

  (system/stop running-system)
 )
