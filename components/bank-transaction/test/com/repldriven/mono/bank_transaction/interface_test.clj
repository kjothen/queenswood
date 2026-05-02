(ns ^:eftest/synchronized com.repldriven.mono.bank-transaction.interface-test
  (:require
    [com.repldriven.mono.bank-transaction.interface]

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
                               "unknown-transaction-command"
                               {:idempotency-key "idem-999"
                                :transaction-type :transaction-type-fee
                                :currency "GBP"
                                :legs [{:account-id "acc_001"
                                        :balance-type :balance-type-default
                                        :balance-status :balance-status-posted
                                        :side :leg-side-debit
                                        :amount 100}]})]
      (is (error/rejection? result))
      (is (= :transaction/unknown-command (error/kind result))))))

(deftest process-transaction-test
  (with-test-system [sys "classpath:bank-transaction/application-test.yml"]
                    (let [proc (system/instance sys [:transactions :processor])
                          schemas (system/instance sys [:avro :serde])]
                      (test-unknown-command proc schemas))))
