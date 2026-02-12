(ns com.repldriven.mono.pulsar-reader.reader-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.pulsar.interface :as pulsar]
    [com.repldriven.mono.schema-avro.interface :as avro]
    [com.repldriven.mono.system.interface :as system]

    [clojure.core.async :as async]
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]])
  (:import
    (org.apache.pulsar.client.api Reader)))

(deftest reader-test
  (testing "Pulsar reader should consume messages published by producer"
    (let [sys (error/nom->
               (env/config "classpath:pulsar-reader/test-application.yml" :test)
               system/defs
               system/start)]
      (is (not (error/anomaly? sys)) "System should start")
      (is (system/system? sys) "System should be valid")
      (when (system/system? sys)
        (system/with-system sys
          (let [producer (system/instance sys [:pulsar :producer])
                ^Reader reader (system/instance sys [:pulsar :reader])
                user-schema (avro/json->schema
                             (slurp (io/resource "schema-avro/user.avsc.json")))
                test-messages [{:name "Alice" :age 30} {:name "Bob" :age 25}
                               {:name "Charlie" :age 35}]]
            ;; Send messages to pulsar
            (doseq [msg test-messages]
              (let [serialized (avro/serialize user-schema msg)]
                (pulsar/send producer serialized)))
            ;; Read messages from pulsar reader channel
            (let [{:keys [c stop]} (pulsar/read reader user-schema 50)
                  timeout (async/timeout 5000)
                  [received-messages _]
                  (async/alts!! [(async/into []
                                             (async/take (count test-messages)
                                                         c)) timeout])]
              (async/>!! stop :stop) ; Send stop signal
              (is (= test-messages received-messages)
                  "Messages don't match"))))))))
