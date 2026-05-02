(ns com.repldriven.mono.bank-payment.domain-test
  "Pure-function tests for the payment-to-transaction builders. No
  FDB, no processor — these pin the leg shapes (which balance-type,
  which status, which side) the brick's handlers rely on."
  (:require
    [com.repldriven.mono.bank-payment.domain :as SUT]

    [clojure.test :refer [deftest is testing]]))

(defn- side
  "Returns the leg on the requested side, or nil."
  [tx leg-side]
  (some (fn [leg] (when (= leg-side (:side leg)) leg)) (:legs tx)))

(deftest internal-payment->transaction-test
  (let [tx (SUT/internal-payment->transaction {:idempotency-key "idem-1"
                                               :debtor-account-id "debtor"
                                               :creditor-account-id "creditor"
                                               :currency "GBP"
                                               :amount 500
                                               :reference "Test"})]
    (testing "envelope carries idempotency-key, type, currency, reference"
      (is (= "idem-1" (:idempotency-key tx)))
      (is (= :transaction-type-internal-transfer (:transaction-type tx)))
      (is (= "GBP" (:currency tx)))
      (is (= "Test" (:reference tx))))
    (testing "two legs, both on :balance-type-default / :posted"
      (is (= 2 (count (:legs tx))))
      (is (every? (fn [leg]
                    (and (= :balance-type-default (:balance-type leg))
                         (= :balance-status-posted (:balance-status leg))))
                  (:legs tx))))
    (testing "debtor debited, creditor credited, both for `amount`"
      (is (= {:account-id "debtor" :amount 500}
             (select-keys (side tx :leg-side-debit) [:account-id :amount])))
      (is (= {:account-id "creditor" :amount 500}
             (select-keys (side tx :leg-side-credit) [:account-id :amount]))))))

(deftest inbound-payment->transaction-test
  (let [tx (SUT/inbound-payment->transaction {:scheme-transaction-id "stx-1"
                                              :currency "GBP"
                                              :amount 1000
                                              :reference "Invoice"}
                                             "creditor"
                                             "internal")]
    (testing "scheme-transaction-id becomes the idempotency-key"
      (is (= "stx-1" (:idempotency-key tx)))
      (is (= :transaction-type-inbound-transfer (:transaction-type tx))))
    (testing "settlement (suspense) debited, customer (default) credited"
      (let [debit (side tx :leg-side-debit)
            credit (side tx :leg-side-credit)]
        (is (= "internal" (:account-id debit)))
        (is (= :balance-type-suspense (:balance-type debit)))
        (is (= :balance-status-posted (:balance-status debit)))
        (is (= 1000 (:amount debit)))
        (is (= "creditor" (:account-id credit)))
        (is (= :balance-type-default (:balance-type credit)))
        (is (= :balance-status-posted (:balance-status credit)))
        (is (= 1000 (:amount credit)))))))

(deftest outbound-payment->transaction-test
  (let [tx (SUT/outbound-payment->transaction {:idempotency-key "ob-1"
                                               :debtor-account-id "debtor"
                                               :currency "GBP"
                                               :amount 250
                                               :reference "Outbound"}
                                              "internal")]
    (testing "envelope shape"
      (is (= "ob-1" (:idempotency-key tx)))
      (is (= :transaction-type-outbound-transfer (:transaction-type tx))))
    (testing "customer (default) debited, settlement (suspense) credited"
      (let [debit (side tx :leg-side-debit)
            credit (side tx :leg-side-credit)]
        (is (= "debtor" (:account-id debit)))
        (is (= :balance-type-default (:balance-type debit)))
        (is (= 250 (:amount debit)))
        (is (= "internal" (:account-id credit)))
        (is (= :balance-type-suspense (:balance-type credit)))
        (is (= 250 (:amount credit)))))))

(deftest completed-outbound-payment-test
  (testing "flips :payment-status to completed"
    (let [pending {:payment-id "pmt-1"
                   :payment-status :outbound-payment-status-pending
                   :amount 250
                   :created-at 1700000000000
                   :updated-at 1700000000000}
          completed (SUT/completed-outbound-payment pending)]
      (is (= :outbound-payment-status-completed (:payment-status completed)))
      (testing "preserves other fields"
        (is (= "pmt-1" (:payment-id completed)))
        (is (= 250 (:amount completed))))
      (testing "bumps :updated-at past the original"
        (is (>= (:updated-at completed) (:updated-at pending)))))))
