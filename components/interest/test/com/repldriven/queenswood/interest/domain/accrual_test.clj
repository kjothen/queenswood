(ns com.repldriven.queenswood.interest.domain.accrual-test
  "The daily-interest arithmetic at sub-minor-unit precision, the
  outcome one account's accrual settles on, and the bank's side of a
  run."
  (:require
    [com.repldriven.queenswood.interest.domain.accrual :as SUT]

    [clojure.test :refer [deftest is testing]]))

(def ^:private earning-balances
  "A current account holding 1500 available (2000 posted less a 500
  outgoing reservation) and an accrued balance carrying yesterday's
  sub-minor remainder."
  [{:product-type :product-type-sub-ledger-current
    :balance-type :balance-type-default
    :balance-status :balance-status-posted
    :currency "GBP"
    :credit 2000
    :debit 0}
   {:product-type :product-type-sub-ledger-current
    :balance-type :balance-type-default
    :balance-status :balance-status-pending-outgoing
    :currency "GBP"
    :credit 0
    :debit 500}
   {:product-type :product-type-sub-ledger-current
    :balance-type :balance-type-interest-accrued
    :balance-status :balance-status-posted
    :currency "GBP"
    :credit 40
    :debit 0
    :credit-carry 27397}])

(def ^:private spendable-only (vec (take 2 earning-balances)))

(deftest day-interest-test
  (let [day-interest #'SUT/day-interest]
    (testing "zero rate returns nil — nothing is earned"
      (is (nil? (day-interest 1000 0 0))))
    (testing "a balance too small to earn a unit closes with a remainder"
      ;; £10.00 (1000 minor units) at 100 bps (1%) APR, opening at 0:
      ;; total-micro = 1000 * 100 * 100      + 0  = 10_000_000
      ;; daily-micro = 10_000_000 / 365            = 27_397
      (let [r (day-interest 1000 0 100)]
        (is (= 0 (:amount r)))
        (is (= 27397 (:closing-carry r)))))
    (testing "the opening remainder is spent, and a larger one closes"
      ;; Same £10.00 / 100 bps, opening at yesterday's 27_397:
      ;; total-micro = 1000 * 100 * 100 + 27_397 * 365
      ;;             = 10_000_000 + 9_999_905 = 19_999_905
      ;; daily-micro = 19_999_905 / 365 = 54_794
      (let [r (day-interest 1000 27397 100)]
        (is (= 0 (:amount r)))
        (testing "opening and closing are different numbers, and both matter"
          (is (= 54794 (:closing-carry r))))))
    (testing "an opening remainder eventually tips a whole unit over"
      ;; The same tiny balance, opened with a remainder just under the
      ;; line: the day's interest pushes it past a whole unit, which is
      ;; the entire point of carrying it.
      (let [r (day-interest 1000 999000 100)]
        (is (= 1 (:amount r)))
        (is (= 26397 (:closing-carry r)))))
    (testing "large balance and rate cross the whole-unit threshold"
      ;; £100,000 (10_000_000 minor units) at 365 bps (3.65%):
      ;; total-micro = 10_000_000 * 365 * 100 + 0 = 365_000_000_000
      ;; daily-micro = 365_000_000_000 / 365      = 1_000_000_000
      (let [r (day-interest 10000000 0 365)]
        (is (= 1000 (:amount r)))
        (is (= 0 (:closing-carry r)))))))

(deftest accrue-test
  (testing "a product paying no interest accrues nothing"
    (is (nil? (SUT/accrue "acc.1" "GBP" earning-balances 0))))
  (testing "a rate with nowhere to put it accrues nothing either"
    ;; Logged rather than rejected — nothing the account did caused it.
    (is (nil? (SUT/accrue "acc.1" "GBP" spendable-only 100))))
  (testing "a rate and a balance yield the advance and the row together"
    ;; 1500 available at 100 bps opening at 27_397: Total-micro = 1500 *
    ;; 100 * 100 + 27_397 * 365 = 24_999_905 daily-micro = 24_999_905 / 365
    ;; = 68_492
    (let [{:keys [balance amount closing-carry principal opening-carry]}
          (SUT/accrue "acc.1" "GBP" earning-balances 100)]
      (testing "the balance handed back is the accrued one"
        (is (= :balance-type-interest-accrued (:balance-type balance)))
        (is (= 40 (:credit balance))))
      (testing "what the account earned, and the remainder it leaves"
        (is (= 0 amount))
        (is (= 68492 closing-carry)))
      (testing "and what those were computed from, so the row explains itself"
        (testing "interest is earned on available, not posted"
          (is (= 1500 principal)))
        (is (= 27397 opening-carry)))))
  (testing "a zero amount still accrues, because the remainder moved"
    (let [{:keys [amount opening-carry closing-carry]}
          (SUT/accrue "acc.1" "GBP" earning-balances 100)]
      (is (= 0 amount))
      (is (not= opening-carry closing-carry)))))

(deftest entries-test
  (testing "one entry per currency — product type is not a distinction"
    (is (= #{"GBP"}
           (SUT/entries #{["GBP" :product-type-sub-ledger-current]
                          ["GBP" :product-type-sub-ledger-savings]})))
    (is (= #{"GBP" "EUR"}
           (SUT/entries #{["GBP" :product-type-sub-ledger-current]
                          ["EUR" :product-type-sub-ledger-current]})))))

(def ^:private gl {:expense "led.expense" :payable "led.payable"})

(deftest ledger-transaction-test
  (testing "a currency that accrued nothing posts nothing"
    (is (nil? (SUT/ledger-transaction gl "org.1" "GBP" 0 20260501))))
  (testing "DR interest expense, CR interest payable"
    (let [tx (SUT/ledger-transaction gl "org.1" "GBP" 5000 20260501)
          [debit credit] (:legs tx)]
      (is (= :transaction-type-interest-accrual (:transaction-type tx)))
      (is (= "led.expense" (:account-id debit)))
      (is (= :leg-side-debit (:side debit)))
      (is (= "led.payable" (:account-id credit)))
      (is (= :leg-side-credit (:side credit)))
      (testing "both legs carry the run's total, so the entry balances"
        (is (= 5000 (:amount debit)))
        (is (= 5000 (:amount credit))))))
  (testing "the key separates currencies and repeats within one"
    (let [key-for (fn [currency]
                    (:idempotency-key (SUT/ledger-transaction gl
                                                              "org.1" currency
                                                              5000 20260501)))]
      (is (not= (key-for "GBP") (key-for "EUR")))
      (is (= (key-for "GBP") (key-for "GBP"))))))
