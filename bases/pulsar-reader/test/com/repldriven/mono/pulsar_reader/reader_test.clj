(ns com.repldriven.mono.pulsar-reader.reader-test
  (:require
    com.repldriven.mono.pulsar.interface
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
    (org.apache.pulsar.client.api Reader Message)))

(defn read-messages
  "Read n messages from a Pulsar reader and deserialize them onto a channel.
  Returns a channel that will receive deserialized messages and close after n messages."
  [^Reader reader schema n timeout-ms]
  (let [out-chan (async/chan n)]
    (async/thread
      (try
        (dotimes [_ n]
          (when (.hasMessageAvailable reader)
            (when-let [^Message msg (.readNext reader timeout-ms java.util.concurrent.TimeUnit/MILLISECONDS)]
              (let [deserialized (avro/deserialize-same schema (.getData msg))]
                (async/>!! out-chan deserialized)))))
        (finally
          (async/close! out-chan))))
    out-chan))

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
            (doseq [msg test-messages]
              (let [serialized (avro/serialize user-schema msg)]
                (pulsar/send producer serialized)))
            ;; Read messages from channel
            (let [msg-chan (read-messages reader user-schema (count test-messages) 500)
                  received-messages (async/<!! (async/into [] msg-chan))]
              ;; Verify we received all messages
              (is (= (count test-messages) (count received-messages))
                  (str "Should receive " (count test-messages)
                       " messages, got " (count received-messages)))
              ;; Verify message contents
              (doseq [[expected received]
                      (map vector test-messages received-messages)]
                (is (= (:name expected) (:name received))
                    (str "Message name should match: expected " (:name expected)
                         ", got " (:name received)))
                (is (= (:age expected) (:age received))
                    (str "Message age should match: expected " (:age expected)
                         ", got " (:age received)))))))))))
