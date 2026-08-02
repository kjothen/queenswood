(ns com.repldriven.queenswood.interest.domain-test
  "Pure-function tests for the daily-interest math, the bucket
  invariant, and the capitalisation transaction builder. No FDB, no
  processor — these exercise the integer arithmetic at sub-minor-unit
  precision and the shapes the brick relies on."
  (:require
    [com.repldriven.queenswood.interest.domain :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(deftest daily-interest-test
  (testing "zero rate returns nil — production posts nothing"
    (is (nil? (SUT/daily-interest 1000 0 0))))
  (testing "small balance and rate accumulate sub-minor carry only"
    ;; £10.00 (1000 minor units) at 100 bps (1%) APR:
    ;; total-micro = 1000 * 100 * 100      + 0  = 10_000_000
    ;; daily-micro = 10_000_000 / 365            = 27_397
    ;; whole-units = 0; carry = 27_397
    (let [r (SUT/daily-interest 1000 0 100)]
      (is (= 0 (:whole-units r)))
      (is (= 27397 (:carry r)))))
  (testing "carry from prior day rolls into today's calculation"
    ;; Same £10.00 / 100 bps with 27_397 carry:
    ;; total-micro = 1000 * 100 * 100 + 27_397 * 365
    ;;             = 10_000_000 + 9_999_905 = 19_999_905
    ;; daily-micro = 19_999_905 / 365 = 54_794
    ;; whole-units = 0; carry = 54_794
    (let [r (SUT/daily-interest 1000 27397 100)]
      (is (= 0 (:whole-units r)))
      (is (= 54794 (:carry r)))))
  (testing "large balance and rate cross the whole-unit threshold"
    ;; £100,000 (10_000_000 minor units) at 365 bps (3.65%):
    ;; total-micro = 10_000_000 * 365 * 100 + 0 = 365_000_000_000
    ;; daily-micro = 365_000_000_000 / 365      = 1_000_000_000
    ;; whole-units = 1000; carry = 0
    (let [r (SUT/daily-interest 10000000 0 365)]
      (is (= 1000 (:whole-units r)))
      (is (= 0 (:carry r))))))

(deftest net-balance-test
  (testing "net is credit minus debit — the principal interest earns on"
    (is (= 1000 (SUT/net-balance {:credit 2000 :debit 1000})))
    (is (= 0 (SUT/net-balance {:credit 100 :debit 100}))))
  (testing "a missing side reads as zero rather than blowing up"
    (is (= 500 (SUT/net-balance {:credit 500})))))

(def ^:private account-balances
  [{:balance-type :balance-type-default
    :balance-status :balance-status-posted
    :currency "GBP"
    :credit 2000
    :debit 500
    :credit-carry 0}
   {:balance-type :balance-type-interest-accrued
    :balance-status :balance-status-posted
    :currency "GBP"
    :credit 40
    :debit 0
    :credit-carry 27397}])

(deftest principal-test
  (testing "interest is earned on the available balance, not the posted one"
    ;; 2000 posted less a 500 outgoing reservation.
    (is (= 1500
           (SUT/principal [{:product-type :product-type-sub-ledger-current
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
                            :debit 500}]
                          "GBP"))))
  (testing "money still pending inbound has not arrived, so it does not earn"
    (is (= 2000
           (SUT/principal [{:product-type :product-type-sub-ledger-current
                            :balance-type :balance-type-default
                            :balance-status :balance-status-posted
                            :currency "GBP"
                            :credit 2000
                            :debit 0}
                           {:product-type :product-type-sub-ledger-current
                            :balance-type :balance-type-default
                            :balance-status :balance-status-pending-incoming
                            :currency "GBP"
                            :credit 900
                            :debit 0}]
                          "GBP"))))
  (testing "the accrued bucket is not itself principal — interest is simple"
    (is (= 2000
           (SUT/principal [{:product-type :product-type-sub-ledger-current
                            :balance-type :balance-type-default
                            :balance-status :balance-status-posted
                            :currency "GBP"
                            :credit 2000
                            :debit 0}
                           {:product-type :product-type-sub-ledger-current
                            :balance-type :balance-type-interest-accrued
                            :balance-status :balance-status-posted
                            :currency "GBP"
                            :credit 40
                            :debit 0}]
                          "GBP")))))

(deftest bucket-test
  (testing "picks the bucket matching type, currency and status"
    (let [b (SUT/bucket account-balances
                        :balance-type-interest-accrued
                        "GBP"
                        :balance-status-posted)]
      (is (= 40 (:credit b)))
      (is (= 27397 (:credit-carry b)))))
  (testing "a bucket the account does not hold is nil, not a zeroed stand-in"
    ;; Nil is the caller's cue to say something. A zeroed bucket would
    ;; read as a real balance of nothing and accrue silently against a
    ;; figure nobody wrote.
    (is (nil? (SUT/bucket account-balances
                          :balance-type-interest-accrued
                          "USD"
                          :balance-status-posted))))
  (testing "status is part of the match, not just type and currency"
    (is (nil? (SUT/bucket account-balances
                          :balance-type-default
                          "GBP"
                          :balance-status-pending-outgoing)))))

(deftest idempotency-keys-test
  (testing "capitalisation key composes account-id + as-of-date"
    (is (= "capitalize-acc-1-20260501"
           (SUT/capitalization-idempotency-key "acc-1" 20260501)))
    (is (= (SUT/capitalization-idempotency-key "acc-1" 20260501)
           (SUT/capitalization-idempotency-key "acc-1" 20260501)))))

(deftest capitalization-transaction-test
  (testing "zero accrued returns nil — no transaction posted"
    (is (nil? (SUT/capitalization-transaction "cust"
                                              "GBP"
                                              {:credit 0 :debit 0}
                                              20260501))))
  (testing "credit minus debit equal to zero also returns nil"
    (is (nil? (SUT/capitalization-transaction "cust"
                                              "GBP"
                                              {:credit 100 :debit 100}
                                              20260501))))
  (testing "positive accrued builds the 2-leg capitalisation"
    (let [tx (SUT/capitalization-transaction "cust"
                                             "GBP"
                                             {:credit 110 :debit 0}
                                             20260501)
          legs (:legs tx)]
      (is (= "capitalize-cust-20260501" (:idempotency-key tx)))
      (is (= :transaction-type-interest-capital (:transaction-type tx)))
      (is (= 2 (count legs)))
      (testing "every leg uses the accrued amount (110) on posted"
        (is (every? (fn [leg] (= 110 (:amount leg))) legs))
        (is (every? (fn [leg] (= :balance-status-posted (:balance-status leg)))
                    legs)))
      (testing
        "DR the customer interest-accrued bucket — clears it, fans to 2400"
        (let [debit (first legs)]
          (is (= "cust" (:account-id debit)))
          (is (= :balance-type-interest-accrued (:balance-type debit)))
          (is (= :leg-side-debit (:side debit)))))
      (testing "CR the customer default bucket — fans to the deposit control"
        (let [credit (second legs)]
          (is (= "cust" (:account-id credit)))
          (is (= :balance-type-default (:balance-type credit)))
          (is (= :leg-side-credit (:side credit)))))
      (testing "legs balance — Σdebit == Σcredit"
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

(def ^:private accrual-limit-policies
  "One platform policy: at most one accrual run per org per day."
  [{:enabled true
    :limits [{:kind {:interest {:filters [{:action :interest-action-accrue}]}}
              :bound {:kind {:max {:aggregate {:kind {:count
                                                      {:value 1
                                                       :window
                                                       :time-window-daily}}}}}}
              :reason "at most one accrual run per day"}]}])

(deftest check-daily-count-test
  (testing "first run of the day passes — post-state count 1 is within max 1"
    (is (true? (SUT/check-daily-count accrual-limit-policies
                                      :accrual
                                      {:accrual {#{:bank-id :business-day}
                                                 0}}))))
  (testing "second run of the day is rejected — post-state 2 exceeds max 1"
    (let [result (SUT/check-daily-count accrual-limit-policies
                                        :accrual
                                        {:accrual {#{:bank-id :business-day}
                                                   1}})]
      (is (error/rejection? result))
      (is (= :policy/limit-exceeded (error/kind result)))))
  (testing "a kind with no matching limit is unconstrained"
    (is (true? (SUT/check-daily-count accrual-limit-policies
                                      :capitalize
                                      {:capitalize {#{:bank-id :business-day}
                                                    5}})))))

(deftest new-interest-run-test
  (testing "a new run carries org, business-day and kind, and starts running"
    (let [run (SUT/new-interest-run "org.1" 20260501 :interest-run-kind-accrue)]
      (is (= "org.1" (:bank-id run)))
      (is (= 20260501 (:business-day run)))
      (is (= :interest-run-kind-accrue (:kind run)))
      (is (= :interest-run-state-running (:state run)))
      (is (number? (:created-at run)))
      (is (nil? (:closed-at run)))))
  (testing "closing stamps the state and the time"
    (let [run
          (SUT/close-interest-run
           (SUT/new-interest-run "org.1" 20260501 :interest-run-kind-accrue))]
      (is (= :interest-run-state-closed (:state run)))
      (is (number? (:closed-at run))))))

(deftest account-run-lifecycle-test
  (testing "a new account row starts pending"
    (let [row (SUT/new-account-run "org.1"
                                   20260501 :interest-account-run-kind-accrue
                                   "acc.1" "GBP")]
      (is (SUT/pending? row))
      (is (= "acc.1" (:account-id row)))
      (is (number? (:created-at row)))))
  (testing "done and failed both leave pending, and failed keeps a reason"
    (let [row (SUT/new-account-run "org.1"
                                   20260501 :interest-account-run-kind-accrue
                                   "acc.1" "GBP")
          done (SUT/account-run-done row {:amount 7 :input-balance 100000})
          failed (SUT/account-run-failed row :interest/boom)]
      (is (not (SUT/pending? done)))
      (is (= :interest-account-run-state-done (:state done)))
      (is (not (SUT/pending? failed)))
      (is (= :interest-account-run-state-failed (:state failed)))
      (is (= ":interest/boom" (:failure-reason failed)))))
  (testing "a done row carries what was earned and what it came from"
    (let [row (SUT/new-account-run "org.1"
                                   20260501 :interest-account-run-kind-accrue
                                   "acc.1" "GBP")
          done (SUT/account-run-done
                row
                {:amount 7 :input-balance 100000 :input-carry 12345})]
      (is (= 7 (:amount done)))
      (is (= 100000 (:input-balance done)))
      (is (= 12345 (:input-carry done)))))
  (testing
    "absent inputs stay absent, since an optional proto scalar
            wants the key gone rather than nil"
    (let [row (SUT/new-account-run "org.1"
                                   20260501 :interest-account-run-kind-accrue
                                   "acc.1" "GBP")
          done (SUT/account-run-done row {:amount 0})]
      (is (= 0 (:amount done)))
      (is (not (contains? done :input-balance)))
      (is (not (contains? done :input-carry))))))
