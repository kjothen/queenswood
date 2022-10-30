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
           (org.apache.pulsar.client.api Consumer MessageId Producer)
           (org.apache.pulsar.common.api EncryptionContext)
           (org.apache.pulsar.common.policies.data
            SchemaAutoUpdateCompatibilityStrategy)))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "pulsar/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(defn http-get-json
  [url]
  (json/read-str
   (:body @(httpkit/get url {:headers {"Accept" "application/json"}}))))

(deftest development-test
  (testing "Developers should be able to start/stop a pulsar system from a REPL"
    (with-system [sys (SUT/configure-system (get-in @env/env [:system :pulsar]))]
      (let [admin (system/instance sys [:pulsar :admin])
            producer (system/instance sys [:pulsar :producer])
            consumer (system/instance sys [:pulsar :consumer])]

        ;; produce/consume a complex schema message
        (let [schemas  (system/instance sys [:pulsar :schemas])
              schema (-> (get-in schemas [:user :schema])
                         .getSchemaInfo
                         .toString
                         json/read-str
                         (get "schema"))
              user-schema (avro/json->schema (json/write-str schema))
              user {:name "hardcastle" :age 19}]

          (.. producer (send (avro/serialize user-schema user)))
          (let [user-msg (.. consumer (receive 500 TimeUnit/MILLISECONDS) getData)]
            (is (= user (avro/deserialize-same user-schema user-msg)))))

        ;; check namespace topic settings enfore encryption on registered schema
        (let [service-url (.getServiceUrl admin)
              namespaces-url (string/join "/" [service-url "admin/v2/namespaces"])
              namespace-url (string/join "/" [namespaces-url "tenant-1/namespace-1"])
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
                expected)))
        ))))

(comment
  (env/set-env! (io/resource "pulsar/test-env.edn") :test)
  (def system-config (SUT/configure-system (get-in @env/env [:system :pulsar])))
  (def running-system (system/start system-config))

  (system/stop running-system)
)
