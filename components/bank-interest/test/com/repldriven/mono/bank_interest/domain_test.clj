(ns com.repldriven.mono.bank-interest.domain-test
  "Pure-function tests for the daily-interest math and the accrual /
  capitalisation transaction builders. No FDB, no processor — these
  exercise the integer arithmetic at sub-minor-unit precision and
  the leg shapes the brick relies on."
  (:require
    [com.repldriven.mono.bank-interest.domain :as SUT]

    [clojure.test :refer [deftest is testing]]))

(deftest daily-interest-test
  (testing "zero rate returns nil — production posts nothing"
    (is (nil? (SUT/daily-interest {:credit 1000 :debit 0 :credit-carry 0} 0))))
  (testing "small balance and rate accumulate sub-minor carry only"
    ;; £10.00 (1000 minor units) at 100 bps (1%) APR:
    ;; total-micro = 1000 * 100 * 100      + 0  = 10_000_000
    ;; daily-micro = 10_000_000 / 365            = 27_397
    ;; whole-units = 0; carry = 27_397
    (let [r (SUT/daily-interest {:credit 1000 :debit 0 :credit-carry 0} 100)]
      (is (= 0 (:whole-units r)))
      (is (= 27397 (:carry r)))))
  (testing "carry from prior day rolls into today's calculation"
    ;; Same £10.00 / 100 bps with 27_397 carry:
    ;; total-micro = 1000 * 100 * 100 + 27_397 * 365
    ;;             = 10_000_000 + 9_999_905 = 19_999_905
    ;; daily-micro = 19_999_905 / 365 = 54_794
    ;; whole-units = 0; carry = 54_794
    (let [r (SUT/daily-interest {:credit 1000 :debit 0 :credit-carry 27397}
                                100)]
      (is (= 0 (:whole-units r)))
      (is (= 54794 (:carry r)))))
  (testing "large balance and rate cross the whole-unit threshold"
    ;; £100,000 (10_000_000 minor units) at 365 bps (3.65%):
    ;; total-micro = 10_000_000 * 365 * 100 + 0 = 365_000_000_000
    ;; daily-micro = 365_000_000_000 / 365      = 1_000_000_000
    ;; whole-units = 1000; carry = 0
    (let [r (SUT/daily-interest {:credit 10000000 :debit 0 :credit-carry 0}
                                365)]
      (is (= 1000 (:whole-units r)))
      (is (= 0 (:carry r)))))
  (testing "net balance is credit minus debit"
    ;; credit 2000, debit 1000, rate 365 bps → net 1000
    ;; total-micro = 1000 * 365 * 100 = 36_500_000
    ;; daily-micro = 100_000; whole-units 0; carry 100_000
    (let [r (SUT/daily-interest {:credit 2000 :debit 1000 :credit-carry 0} 365)]
      (is (= 0 (:whole-units r)))
      (is (= 100000 (:carry r))))))

(deftest idempotency-keys-test
  (testing "accrual key composes account-id + as-of-date deterministically"
    (is (= "accrue-acc-1-20260501"
           (SUT/accrual-idempotency-key "acc-1" 20260501)))
    (is (= (SUT/accrual-idempotency-key "acc-1" 20260501)
           (SUT/accrual-idempotency-key "acc-1" 20260501))))
  (testing "capitalisation key composes the same way under a different prefix"
    (is (= "capitalize-acc-1-20260501"
           (SUT/capitalization-idempotency-key "acc-1" 20260501))))
  (testing "accrual and capitalisation keys never collide for same args"
    (is (not= (SUT/accrual-idempotency-key "acc-1" 20260501)
              (SUT/capitalization-idempotency-key "acc-1" 20260501)))))

(deftest accrual-transaction-test
  (testing "zero whole-units returns nil — no transaction posted"
    (is (nil? (SUT/accrual-transaction "settle" "cust" "GBP" 0 20260501))))
  (testing "positive whole-units builds a 2-leg accrual"
    (let [tx (SUT/accrual-transaction "settle" "cust" "GBP" 75 20260501)]
      (is (= "accrue-cust-20260501" (:idempotency-key tx)))
      (is (= :transaction-type-interest-accrual (:transaction-type tx)))
      (is (= "GBP" (:currency tx)))
      (is (= 2 (count (:legs tx))))
      (testing "settlement debit on interest-payable / posted"
        (let [debit (first (:legs tx))]
          (is (= "settle" (:account-id debit)))
          (is (= :balance-type-interest-payable (:balance-type debit)))
          (is (= :balance-status-posted (:balance-status debit)))
          (is (= :leg-side-debit (:side debit)))
          (is (= 75 (:amount debit)))))
      (testing "customer credit on interest-accrued / posted"
        (let [credit (second (:legs tx))]
          (is (= "cust" (:account-id credit)))
          (is (= :balance-type-interest-accrued (:balance-type credit)))
          (is (= :balance-status-posted (:balance-status credit)))
          (is (= :leg-side-credit (:side credit)))
          (is (= 75 (:amount credit))))))))

(deftest capitalization-transaction-test
  (testing "zero accrued returns nil — no transaction posted"
    (is (nil? (SUT/capitalization-transaction "settle"
                                              "cust"
                                              "GBP"
                                              {:credit 0 :debit 0}
                                              20260501))))
  (testing "credit minus debit equal to zero also returns nil"
    (is (nil? (SUT/capitalization-transaction "settle"
                                              "cust"
                                              "GBP"
                                              {:credit 100 :debit 100}
                                              20260501))))
  (testing "positive accrued builds the 6-leg capitalisation"
    (let [tx (SUT/capitalization-transaction "settle"
                                             "cust"
                                             "GBP"
                                             {:credit 110 :debit 0}
                                             20260501)
          legs (:legs tx)]
      (is (= "capitalize-cust-20260501" (:idempotency-key tx)))
      (is (= :transaction-type-interest-capital (:transaction-type tx)))
      (is (= 6 (count legs)))
      (testing "every leg uses the accrued amount (110)"
        (is (every? (fn [leg] (= 110 (:amount leg))) legs)))
      (testing "every leg is on :balance-status-posted"
        (is (every? (fn [leg] (= :balance-status-posted (:balance-status leg)))
                    legs)))
      (testing "legs net to zero per side per account-balance-type"
        ;; The 6 legs should pair up — total debits == total credits
        ;; for the transaction as a whole.
        (let [debit-total (transduce
                           (comp (filter (fn [l] (= :leg-side-debit (:side l))))
                                 (map :amount))
                           +
                           legs)
              credit-total
              (transduce (comp (filter (fn [l] (= :leg-side-credit (:side l))))
                               (map :amount))
                         +
                         legs)]
          (is (= debit-total credit-total)))))))
