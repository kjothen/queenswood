(ns com.repldriven.mono.bank-transaction.interface-test
  "Pure unit test for the dispatcher's unknown-command rejection.
  The record-transaction happy path is exercised on every
  property-test trial — every transfer, payment, fee, accrual,
  and capitalisation routes through transactions/record-transaction
  via the runner — and by every EDN scenario that posts balances."
  (:require
    [com.repldriven.mono.bank-transaction.commands :as commands]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(deftest unknown-command-test
  (testing "dispatch rejects command names not in the handler registry"
    (let [result
          (#'commands/dispatch
           {:schemas {}}
           {:command "unknown-transaction-command" :id "x" :payload nil})]
      (is (error/rejection? result))
      (is (= :transaction/unknown-command (error/kind result))))))
