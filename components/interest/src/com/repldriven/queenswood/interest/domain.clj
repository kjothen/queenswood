(ns com.repldriven.queenswood.interest.domain
  (:require
    [com.repldriven.queenswood.policy.interface :as policy]

    [com.repldriven.mono.utility.interface :as utility]))

(def ^:private micro-scale 1000000)

;; A daily-count limit is one `interest` policy limit scoped to a single
;; action via the limit filter; map the internal run kind to that action.
(def ^:private kind->action
  {:accrual :interest-action-accrue :capitalize :interest-action-capitalize})

(defn check-daily-count
  [policies kind aggregates]
  (policy/check-limit
   policies
   :interest
   {:action (kind->action kind)
    :aggregate :count
    :window :time-window-daily
    :value (inc (get-in aggregates [kind #{:bank-id :business-day}]))}))

(defn new-interest-run
  [bank-id business-day kind state]
  {:bank-id bank-id
   :business-day business-day
   :kind kind
   :state state
   :created-at (utility/now)})

(defn close-interest-run
  [run]
  (assoc run
         :state :interest-run-state-closed
         :closed-at (utility/now)))

(defn new-account-run
  [bank-id business-day kind account-id]
  {:bank-id bank-id
   :business-day business-day
   :kind kind
   :account-id account-id
   :state :interest-account-run-state-pending
   :created-at (utility/now)})

(defn account-run-done
  [account-run]
  (assoc account-run
         :state :interest-account-run-state-done
         :updated-at (utility/now)))

(defn account-run-failed
  "Marks the row failed so enumeration can move past it. `reason` is the
  anomaly's category, kept short — the anomaly itself is logged."
  [account-run reason]
  (assoc account-run
         :state :interest-account-run-state-failed
         :failure-reason (str reason)
         :updated-at (utility/now)))

(defn pending?
  [account-run]
  (= :interest-account-run-state-pending (:state account-run)))

(defn- net-balance
  [balance]
  (- (:credit balance 0) (:debit balance 0)))

(defn daily-interest
  [balance interest-rate-bps]
  (let [{:keys [credit-carry]} balance]
    (when-not (zero? interest-rate-bps)
      (let [net (net-balance balance)
            total-micro (+ (* net
                              interest-rate-bps
                              (quot micro-scale 10000))
                           (* credit-carry 365))
            daily-micro (quot total-micro 365)
            whole-units (quot daily-micro micro-scale)
            new-carry (rem daily-micro micro-scale)]
        {:whole-units whole-units :carry new-carry}))))

(defn accrual-idempotency-key
  [account-id as-of-date]
  (str "accrue-" account-id "-" as-of-date))

(defn capitalization-idempotency-key
  [account-id as-of-date]
  (str "capitalize-" account-id "-" as-of-date))

(defn accrual-transaction
  "Daily accrual double-entry: DR 5100 Interest expense / CR the
  customer's interest-accrued bucket. The expense leg posts directly
  (5100 is a detail GL account); the customer leg fans out to the 2400
  Interest payable control. Both legs carry the same `whole-units`."
  [interest-expense-id account-id currency whole-units
   as-of-date]
  (when-not (zero? whole-units)
    {:idempotency-key (accrual-idempotency-key
                       account-id
                       as-of-date)
     :transaction-type :transaction-type-interest-accrual
     :currency currency
     :reference (str "Daily interest accrual "
                     (utility/epoch-day->iso-date as-of-date))
     :legs [{:account-id interest-expense-id
             :balance-type :balance-type-default
             :balance-status :balance-status-posted
             :side :leg-side-debit
             :amount whole-units}
            {:account-id account-id
             :balance-type :balance-type-interest-accrued
             :balance-status :balance-status-posted
             :side :leg-side-credit
             :amount whole-units}]}))

(defn capitalization-transaction
  "Capitalisation double-entry: DR the customer's interest-accrued
  bucket / CR its default bucket, for the whole accrued amount. The
  accrued leg fans out to 2400 (clearing the payable back toward zero);
  the default leg fans out to the deposit control (2100/2200/2300), so
  the capitalised interest becomes spendable. `balance` is the
  account's interest-accrued balance."
  [account-id currency balance as-of-date]
  (let [accrued (net-balance balance)]
    (when-not (zero? accrued)
      {:idempotency-key (capitalization-idempotency-key
                         account-id
                         as-of-date)
       :transaction-type
       :transaction-type-interest-capital
       :currency currency
       :reference (str "Monthly interest capitalization "
                       (utility/epoch-day->iso-date as-of-date))
       :legs [{:account-id account-id
               :balance-type :balance-type-interest-accrued
               :balance-status :balance-status-posted
               :side :leg-side-debit
               :amount accrued}
              {:account-id account-id
               :balance-type :balance-type-default
               :balance-status :balance-status-posted
               :side :leg-side-credit
               :amount accrued}]})))
