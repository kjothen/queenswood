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
  "A run starts RUNNING. There is no state for a run that finished with
  failures — it closes like any other, and the residue is the count of
  FAILED rows."
  [bank-id business-day kind]
  {:bank-id bank-id
   :business-day business-day
   :kind kind
   :state :interest-run-state-running
   :created-at (utility/now)})

(defn close-interest-run
  [run]
  (assoc run
         :state :interest-run-state-closed
         :closed-at (utility/now)))

(defn new-account-run
  [bank-id business-day kind account-id currency]
  {:bank-id bank-id
   :business-day business-day
   :kind kind
   :account-id account-id
   :currency currency
   :state :interest-account-run-state-pending
   :created-at (utility/now)})

(defn account-run-done
  "Marks the row done and records what the account earned and what it
  was computed from. `result` carries `:amount`, `:input-balance` and
  `:input-carry`; each is omitted when absent rather than written as
  nil, since an optional proto scalar wants the key gone."
  [account-run result]
  (-> account-run
      (assoc :state :interest-account-run-state-done
             :updated-at (utility/now))
      (utility/assoc-some :amount (:amount result))
      (utility/assoc-some :input-balance (:input-balance result))
      (utility/assoc-some :input-carry (:input-carry result))))

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

(defn net-balance
  "Credit-positive total of a balance bucket. Public because the run
  records the figure it computed from, not just the result."
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

(defn accrual-leg
  "The customer's side of a day's accrual: a credit to the
  interest-accrued bucket.

  There is no matching debit and no control leg. The bank's side is
  posted once for the whole run, because a per-account double entry
  makes every accrual in the bank read and write the same two GL
  balance rows — 5100 and the 2400 control — and they then contend with
  each other whatever else is done to spread them out.

  Carries `product-type` and `currency` for the case where the bucket
  does not exist yet and the posting has to open it."
  [account-id product-type currency whole-units]
  {:account-id account-id
   :product-type product-type
   :balance-type :balance-type-interest-accrued
   :balance-status :balance-status-posted
   :side :leg-side-credit
   :amount whole-units
   :currency currency})

(defn accrual-run-transaction
  "The bank's side of an accrual run, posted once per currency: DR
  interest expense, CR the interest-payable control, for everything the
  run accrued. This is what the per-account double entry used to do a
  million times over the same two rows.

  Both legs name GL accounts directly, so neither fans out to a
  control. The idempotency key is the run's identity, so a retry that
  reaches close twice posts once."
  [interest-expense-id interest-payable-id bank-id currency total
   as-of-date]
  (when-not (zero? total)
    {:idempotency-key (str "accrue-run-" bank-id "-" as-of-date "-" currency)
     :transaction-type :transaction-type-interest-accrual
     :currency currency
     :reference (str "Daily interest accrual "
                     (utility/epoch-day->iso-date as-of-date))
     :legs [{:account-id interest-expense-id
             :balance-type :balance-type-default
             :balance-status :balance-status-posted
             :side :leg-side-debit
             :amount total
             :currency currency}
            {:account-id interest-payable-id
             :balance-type :balance-type-default
             :balance-status :balance-status-posted
             :side :leg-side-credit
             :amount total
             :currency currency}]}))

(defn capitalization-idempotency-key
  [account-id as-of-date]
  (str "capitalize-" account-id "-" as-of-date))

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
