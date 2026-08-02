(ns com.repldriven.queenswood.interest.domain
  (:require
    [com.repldriven.queenswood.balance-domain.interface :as balance-math]
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

(defn bucket
  "One balance bucket out of an account's set, by type, currency and
  status, or nil when the account holds none of that kind.

  Nil is not an ordinary reading. The product types the pass admits —
  current, savings, term deposit — all declare the default and
  interest-accrued posted buckets in `balance-products`, and that list
  is copied from a seeded template rather than supplied by a caller,
  so an account opens holding both. A nil here is a template that
  omitted a bucket or an account that has lost one, and the caller
  should say so rather than read the absence as a zero."
  [balances balance-type currency balance-status]
  (first (filter (fn [b]
                   (and (= balance-type (:balance-type b))
                        (= currency (:currency b))
                        (= balance-status (:balance-status b))))
                 balances)))

(defn net-balance
  "Credit-positive total of a balance bucket. Public because the run
  records the figure it computed from, not just the result."
  [balance]
  (- (:credit balance 0) (:debit balance 0)))

(defn principal
  "What a day's interest is earned on: the account's available balance,
  not its posted one. Money reserved against a pending outgoing payment
  is already committed, so it stops earning when it is reserved rather
  than when it settles — and money still pending inbound has not
  arrived, so it has not started earning.

  Computed from the buckets the scan froze, through the same
  `available-balance` the limit checks use. Interest does not get its
  own definition of available."
  [balances currency]
  (:value (balance-math/available-balance balances currency)))

(defn daily-interest
  "A day's interest on `net` at `interest-rate-bps`, given `carry` — the
  sub-unit remainder the previous day left behind. Returns the whole
  units earned and the new remainder, or nil at a zero rate.

  The two inputs come off different places: `net` is the available
  balance across the account's spendable buckets, and `carry` comes
  from its interest-accrued bucket, where the rest of the accrual
  state already lives.

  Multiplying before dividing is load-bearing. Dividing the rate by 365
  first truncates it — at 100 bps that is 27 rather than 27.397, a 1.4%
  error on every account."
  [net carry interest-rate-bps]
  (when-not (zero? interest-rate-bps)
    (let [total-micro (+ (* net interest-rate-bps (quot micro-scale 10000))
                         (* carry 365))
          daily-micro (quot total-micro 365)]
      {:whole-units (quot daily-micro micro-scale)
       :carry (rem daily-micro micro-scale)})))

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
