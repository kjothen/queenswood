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
  "Continuously read messages from a Pulsar reader and put them on a channel.
  Returns a map with :out-chan (receives messages) and :stop-chan (send anything to stop).
  The reader will stop when :stop-chan receives a value, and :out-chan will close."
  [^Reader reader schema timeout-ms]
  (let [out-chan (async/chan)
        stop-chan (async/chan)]
    (async/thread
      (try
        (loop []
          (let [[v ch] (async/alts!!
                         [stop-chan
                          (async/thread
                            (when (.hasMessageAvailable reader)
                              (when-let [^Message msg (.readNext reader timeout-ms java.util.concurrent.TimeUnit/MILLISECONDS)]
                                (avro/deserialize-same schema (.getData msg)))))])]
            (cond
              (= ch stop-chan) nil  ; Stop signal received
              (some? v) (do (async/>!! out-chan v) (recur))
              :else (recur))))  ; Timeout or no message, try again
        (finally
          (async/close! out-chan)
          (async/close! stop-chan))))
    {:out-chan out-chan
     :stop-chan stop-chan}))

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
            (let [{:keys [out-chan stop-chan]} (read-messages reader user-schema 500)
                  take-chan (async/take (count test-messages) out-chan)
                  received-messages (async/<!! (async/into [] take-chan))]
              (async/>!! stop-chan :stop)
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
