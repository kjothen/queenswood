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

    [clojure.test :refer [deftest is testing]])
  (:import
    (org.apache.pulsar.client.api Message Reader)
    (org.apache.pulsar.common.api EncryptionContext)
    (org.bouncycastle.jce.provider BouncyCastleProvider)

    (java.security Security)
    (java.util HashMap)))

;; Register Bouncy Castle security provider for encryption
(when (nil? (Security/getProvider "BC"))
  (Security/addProvider (BouncyCastleProvider.)))

(defn- get-schema
  [schemas k]
  (-> (get-in schemas [k :schema])
      .getSchemaInfo
      .toString
      json/read-str
      (get "schema")))

(deftest pulsar-component-test
  (testing "Pulsar component should start successfully"
    (let [sys (error/nom-> (env/config "classpath:pulsar/test-application.yml"
                                       :test)
                           system/defs
                           system/start)]
      (is (not (error/anomaly? sys)) (str "System should start: " (pr-str sys)))
      (when (system/system? sys) (system/with-system sys (is (some? sys)))))))

(deftest encrypted-message-matching-consumer-key-test
  (testing "Pulsar consumer with a matching decryption key can consume"
    (let [sys (error/nom-> (env/config "classpath:pulsar/test-application.yml"
                                       :test)
                           system/defs
                           system/start)]
      (is (not (error/anomaly? sys)) (str "System should start: " (pr-str sys)))
      (when (system/system? sys)
        (system/with-system sys
          (let [producer (system/instance sys [:pulsar :producer])
                consumer (system/instance sys [:pulsar :consumers :c1])
                schemas (system/instance sys [:pulsar :schemas])
                schema (schema-avro/json->schema (json/write-str
                                                  (get-schema schemas :user)))
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
              (is (= (get props "message") (get recv-props "message"))))))))))

(deftest encrypted-message-mismatched-consumer-key-test
  (testing "Pulsar consumer with a mismatching decryption key cannot consume"
    (let [sys (error/nom-> (env/config "classpath:pulsar/test-application.yml"
                                       :test)
                           system/defs
                           system/start)]
      (is (not (error/anomaly? sys)) (str "System should start: " (pr-str sys)))
      (when (system/system? sys)
        (system/with-system sys
          (let [producer (system/instance sys [:pulsar :producer])
                consumer (system/instance sys [:pulsar :consumers :c2])
                schemas (system/instance sys [:pulsar :schemas])
                schema (schema-avro/json->schema (json/write-str
                                                  (get-schema schemas :user)))
                data {:name "hardcastle" :age 19}
                props {"message" "user-msg"}]
            (SUT/send producer
                      (schema-avro/serialize schema data)
                      (HashMap. {"properties" props}))
            (let [^Message recv-msg (SUT/receive consumer 1000)
                  ^EncryptionContext ctx (.getEncryptionCtx recv-msg)]
              (is (true? (.isPresent ctx)))
              (is (true? (some-> (.get ctx)
                                 .isEncrypted))))))))))

(deftest namespace-configuration-test
  (testing "Pulsar namespace configuration enforces encryption and topic schema"
    (let [sys (error/nom-> (env/config "classpath:pulsar/test-application.yml"
                                       :test)
                           system/defs
                           system/start)]
      (is (not (error/anomaly? sys)) (str "System should start: " (pr-str sys)))
      (when (system/system? sys)
        (system/with-system sys
          (let [admin (system/instance sys [:pulsar :admin])
                service-url (.getServiceUrl admin)
                namespaces-url (string/join "/"
                                            [service-url "admin/v2/namespaces"])
                namespace-url
                (string/join "/" [namespaces-url "tenant-1/namespace-1"])
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
            expected))))))

(deftest reader-test
  (testing "Pulsar reader should consume messages published by producer"
    (let [sys (error/nom-> (env/config "classpath:pulsar/test-application.yml"
                                       :test)
                           system/defs
                           system/start)]
      (is (not (error/anomaly? sys)) "System should start")
      (is (system/system? sys) "System should be valid")
      (when (system/system? sys)
        (system/with-system sys
          (let [producer (system/instance sys [:pulsar :producer])
                ^Reader reader (system/instance sys [:pulsar :reader])
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
                  (async/alts!! [(async/into []
                                             (async/take (count test-messages)
                                                         c)) timeout])]
              (async/>!! stop :stop) ; Send stop signal
              (is (= test-messages received-messages)
                  "Messages don't match"))))))))
