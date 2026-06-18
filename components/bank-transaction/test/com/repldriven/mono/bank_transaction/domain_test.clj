(ns com.repldriven.mono.bank-transaction.domain-test
  "Pure-function tests for the leg validation that record /
  record-transaction run before persisting. Pins the double-entry
  invariant (debits = credits over non-roll-up legs) and the
  positive-amount guard."
  (:require
    [com.repldriven.mono.bank-transaction.domain :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(defn- debit
  [amount]
  {:side :leg-side-debit :amount amount})

(defn- credit
  [amount]
  {:side :leg-side-credit :amount amount})

(deftest validate-legs-test
  (testing "a balanced two-leg posting passes"
    (is (nil? (SUT/validate-legs [(debit 1000) (credit 1000)]))))
  (testing "a balanced multi-leg posting passes"
    (is (nil? (SUT/validate-legs [(debit 500) (debit 500) (credit 300)
                                  (credit 700)]))))
  (testing "unbalanced debits and credits are rejected"
    (let [result (SUT/validate-legs [(debit 1000) (credit 999)])]
      (is (error/rejection? result))
      (is (= :transaction/legs-unbalanced (error/kind result)))))
  (testing "a non-positive amount is rejected before the balance check"
    (let [result (SUT/validate-legs [(debit 1000) (credit 0)])]
      (is (error/rejection? result))
      (is (= :transaction/invalid-amount (error/kind result)))))
  (testing "a control mirror of a posting leg is excluded from the balance"
    ;; The control mirror is same-side / same-amount as its source, so
    ;; counting it would unbalance an otherwise-balanced posting.
    (is (nil? (SUT/validate-legs [(debit 1000) (credit 1000)
                                  (assoc (credit 1000) :control true)]))))
  (testing "a control leg that mirrors no posting is rejected"
    (let [result (SUT/validate-legs [(debit 1000) (credit 1000)
                                     (assoc (debit 500) :control true)])]
      (is (error/rejection? result))
      (is (= :transaction/control-leg-mismatch (error/kind result))))))

(deftest new-transaction-status-test
  (testing "internal and inbound transfers are born posted"
    (is (= :transaction-status-posted
           (:status (SUT/new-transaction {:transaction-type
                                          :transaction-type-internal-transfer
                                          :currency "GBP"}))))
    (is (= :transaction-status-posted
           (:status (SUT/new-transaction {:transaction-type
                                          :transaction-type-inbound-transfer
                                          :currency "GBP"})))))
  (testing "an outbound transfer is born pending — in-flight at the scheme"
    (is (= :transaction-status-pending
           (:status (SUT/new-transaction {:transaction-type
                                          :transaction-type-outbound-transfer
                                          :currency "GBP"}))))))
