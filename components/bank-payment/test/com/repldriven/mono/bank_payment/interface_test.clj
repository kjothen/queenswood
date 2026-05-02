(ns com.repldriven.mono.bank-payment.interface-test
  "Pure unit test for the dispatcher's unknown-command rejection.
  The settlement-event handlers and happy-path command paths are
  covered by the scenario suite (payment-event-idempotency.edn,
  outbound-payment.edn, intra-org-internal-transfer.edn) and the
  500-trial property test in bank-scenario-runner — no system boot
  is needed at the brick layer."
  (:require
    [com.repldriven.mono.bank-payment.commands :as commands]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(deftest unknown-command-test
  (testing "dispatch rejects command names not in the handler registry"
    (let [result (#'commands/dispatch
                  {:schemas {}}
                  {:command "unknown-payment-command" :id "x" :payload nil})]
      (is (error/rejection? result))
      (is (= :payment/unknown-command (error/kind result)))))
  (testing
    "rejection happens before schema lookup — even a populated
            schema map for OTHER commands doesn't change the result"
    (let [result (#'commands/dispatch
                  {:schemas {"submit-internal-payment" :placeholder}}
                  {:command "unknown-payment-command" :id "x" :payload nil})]
      (is (error/rejection? result))
      (is (= :payment/unknown-command (error/kind result))))))
