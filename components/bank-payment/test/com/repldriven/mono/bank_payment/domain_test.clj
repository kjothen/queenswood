(ns com.repldriven.mono.bank-payment.domain-test
  "Pure-function tests for the payment-to-transaction builders. No
  FDB, no processor — these pin the leg shapes (which balance-type,
  which status, which side) the brick's handlers rely on."
  (:require
    [com.repldriven.mono.bank-payment.domain :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(defn- side
  "Returns the leg on the requested side, or nil."
  [tx leg-side]
  (some (fn [leg] (when (= leg-side (:side leg)) leg)) (:legs tx)))

(defn- allow-all
  "Minimal policy fixture that allow-lists every payment action and
  permits a high daily count, so the leg-shape assertions don't get
  short-circuited by a capability denial or a limit breach."
  []
  [{:enabled true
    :capabilities
    [{:effect :effect-allow
      :kind {:internal-payment
             {:action :internal-payment-action-submit}}}
     {:effect :effect-allow
      :kind {:inbound-payment
             {:action :inbound-payment-action-receive}}}
     {:effect :effect-allow
      :kind {:outbound-payment
             {:action :outbound-payment-action-send}}}]
    :limits
    [{:kind {:internal-payment {}}
      :bound {:kind {:max {:aggregate {:kind {:count {:value 1000000
                                                      :window
                                                      :time-window-daily}}}}}}}
     {:kind {:inbound-payment {}}
      :bound {:kind {:max {:aggregate {:kind {:count {:value 1000000
                                                      :window
                                                      :time-window-daily}}}}}}}
     {:kind {:outbound-payment {}}
      :bound {:kind {:max {:aggregate
                           {:kind {:count {:value 1000000
                                           :window :time-window-daily}}}}}}}]}])

(defn- empty-aggregates
  "Aggregates fixture where today's count is zero for the given
  payment kind. Combined with `allow-all`, leg-shape tests stay clear
  of any limit boundary."
  [kind]
  {kind {#{:organization-id :business-day} 0}})

(defn- account
  "Minimal cash-account fixture — just the fields the domain guards
  read."
  [account-id currency]
  {:account-id account-id :currency currency})

(deftest internal-payment->transaction-test
  (let [tx (SUT/internal-payment->transaction {:idempotency-key "idem-1"
                                               :debtor-account-id "debtor"
                                               :creditor-account-id "creditor"
                                               :currency "GBP"
                                               :amount 500
                                               :reference "Test"}
                                              (account "debtor" "GBP")
                                              (account "creditor" "GBP")
                                              (allow-all)
                                              (empty-aggregates
                                               :internal-payment))]
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
                                             (account "creditor" "GBP")
                                             "internal"
                                             (allow-all)
                                             (empty-aggregates
                                              :inbound-payment))]
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
                                              (account "debtor" "GBP")
                                              "internal"
                                              (allow-all)
                                              (empty-aggregates
                                               :outbound-payment))]
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

(deftest currency-mismatch-test
  (testing "internal-payment: debtor currency must match payment currency"
    (let [result (SUT/internal-payment->transaction
                  {:idempotency-key "idem-2"
                   :debtor-account-id "debtor"
                   :creditor-account-id "creditor"
                   :currency "EUR"
                   :amount 100}
                  (account "debtor" "GBP")
                  (account "creditor" "EUR")
                  (allow-all)
                  (empty-aggregates :internal-payment))]
      (is (error/anomaly? result))
      (is (= :payment/currency-mismatch (error/kind result)))))
  (testing "internal-payment: creditor currency must match payment currency"
    (let [result (SUT/internal-payment->transaction
                  {:idempotency-key "idem-3"
                   :debtor-account-id "debtor"
                   :creditor-account-id "creditor"
                   :currency "GBP"
                   :amount 100}
                  (account "debtor" "GBP")
                  (account "creditor" "EUR")
                  (allow-all)
                  (empty-aggregates :internal-payment))]
      (is (error/anomaly? result))
      (is (= :payment/currency-mismatch (error/kind result)))))
  (testing "inbound-payment: creditor currency must match payment currency"
    (let [result (SUT/inbound-payment->transaction
                  {:scheme-transaction-id "stx-2" :currency "USD" :amount 100}
                  (account "creditor" "GBP")
                  "internal"
                  (allow-all)
                  (empty-aggregates :inbound-payment))]
      (is (error/anomaly? result))
      (is (= :payment/currency-mismatch (error/kind result)))))
  (testing "outbound-payment: debtor currency must match payment currency"
    (let [result (SUT/outbound-payment->transaction {:idempotency-key "ob-2"
                                                     :debtor-account-id "debtor"
                                                     :currency "USD"
                                                     :amount 100}
                                                    (account "debtor" "GBP")
                                                    "internal"
                                                    (allow-all)
                                                    (empty-aggregates
                                                     :outbound-payment))]
      (is (error/anomaly? result))
      (is (= :payment/currency-mismatch (error/kind result))))))

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

(defn- ts
  "Epoch-millis from an ISO-8601 instant string."
  ^long [s]
  (.toEpochMilli (java.time.Instant/parse s)))

(defn- day
  "Epoch-day from an ISO-8601 local-date string."
  ^long [s]
  (.toEpochDay (java.time.LocalDate/parse s)))

(deftest current-business-day-test
  (testing "UTC midnight cutoff = calendar epoch-day"
    (is (= (day "2026-01-15")
           (SUT/current-business-day (ts "2026-01-15T12:00:00Z")
                                     {:zone "UTC" :hour-of-day 0}))))
  (testing "Europe/London 17:00 cutoff: 16:00 GMT rolls to previous day"
    ;; 2026-01-15 is winter — Europe/London = GMT, so 16:00 UTC =
    ;; 16:00 London, before the cutoff.
    (is (= (day "2026-01-14")
           (SUT/current-business-day (ts "2026-01-15T16:00:00Z")
                                     {:zone "Europe/London" :hour-of-day 17}))))
  (testing "Europe/London 17:00 cutoff: 17:00 GMT counts as current day"
    (is (= (day "2026-01-15")
           (SUT/current-business-day (ts "2026-01-15T17:00:00Z")
                                     {:zone "Europe/London" :hour-of-day 17}))))
  (testing "zone shifts the date boundary"
    ;; 2026-01-15 23:30 UTC. In Asia/Tokyo (UTC+9) that's 2026-01-16 08:30.
    ;; With cutoff 0, business-day = 2026-01-16.
    (is (= (day "2026-01-16")
           (SUT/current-business-day (ts "2026-01-15T23:30:00Z")
                                     {:zone "Asia/Tokyo" :hour-of-day 0})))))
