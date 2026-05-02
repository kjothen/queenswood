(ns ^:eftest/synchronized com.repldriven.mono.bank-payment.interface-test
  "Last surviving integration test for bank-payment — the dispatcher
  rejection on unknown command names. Settlement-event behaviour
  (inbound credit posting + outbound status flip + scheme-tx-id
  idempotency) lives in the scenario layer now: every property-
  test trial routes :inbound-transfer through `settle-inbound`
  and exercises :settle-outbound-payment when pending payments
  are available, and the payment-event-idempotency.edn scenario
  pins the re-delivery contract for both directions."
  (:require
    [com.repldriven.mono.bank-payment.interface]

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

(deftest unknown-command-test
  (with-test-system
   [sys "classpath:bank-payment/application-test.yml"]
   (let [proc (system/instance sys [:payment :processor])
         schemas (system/instance sys [:avro :serde])]
     (testing "unknown command returns rejection"
       (let [result (send-command proc
                                  schemas
                                  "unknown-payment-command"
                                  {:idempotency-key "pmt-idem-999"
                                   :organization-id "org-1"
                                   :debtor-account-id "acc-1"
                                   :creditor-account-id "acc-2"
                                   :currency "GBP"
                                   :amount 100})]
         (is (error/rejection? result))
         (is (= :payment/unknown-command (error/kind result))))))))
