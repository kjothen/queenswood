(ns ^:eftest/synchronized com.repldriven.mono.bank-interest.interface-test
  (:require
    [com.repldriven.mono.bank-interest.interface]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system]]

    [clojure.test :refer [deftest is testing]]))

(defn- send-command
  [proc schemas command-name data]
  (let [payload (avro/serialize (get schemas command-name) data)]
    (if (error/anomaly? payload)
      payload
      (processor/process proc
                         {:command command-name
                          :id (:idempotency-key data)
                          :payload payload}))))

(defn- test-unknown-command
  [proc schemas]
  (testing "unknown command returns rejection"
    (let [result (send-command proc
                               schemas
                               "unknown-interest-command"
                               {:idempotency-key "idem-999"
                                :organization-id "org-1"
                                :as-of-date 20260324})]
      (is (error/rejection? result))
      (is (= :interest/unknown-command (error/kind result))))))

(deftest process-interest-test
  (with-test-system [sys "classpath:bank-interest/application-test.yml"]
                    (let [proc (system/instance sys [:interest :processor])
                          schemas (system/instance sys [:avro :serde])]
                      (test-unknown-command proc schemas))))
