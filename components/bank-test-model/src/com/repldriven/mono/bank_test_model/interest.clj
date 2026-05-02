(ns com.repldriven.mono.bank-test-model.interest
  "Daily-interest math + accrue/capitalize command specs. Pure
  re-implementation of `bank-interest.domain/daily-interest` and
  the surrounding accrual/capitalisation transactions.

  The math is integer-only with a per-account `:credit-carry` (in
  micro-minor-units) carried forward day-to-day, mirroring the
  production formula exactly. The legs the production transaction
  posts are summarised in the model state as four moves:

    accrue, per customer account with rate > 0:
      - account.interest-accrued += whole-units
      - account.credit-carry     := new-carry
      - settlement.available     -= whole-units (mirrors
        settlement.interest-payable going negative)

    capitalize, per customer account with accrued > 0:
      - account.available        += accrued
      - account.interest-accrued := 0
      (settlement.default decreases by accrued, settlement.interest-
       payable increases by the same amount → net zero on
       settlement.available)

  Both verbs also bump `:transaction-legs` consistent with how
  `bank-transaction/get-transactions` would count legs touching
  each account."
  (:require
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(def ^:private micro-scale 1000000)

(defn daily-interest
  "Pure mirror of `bank-interest.domain/daily-interest`. `account`
  is `{:available <net> :credit-carry <micro>}`. Returns
  `{:whole-units :carry}` or nil if `interest-rate-bps` is zero."
  [{:keys [available credit-carry] :or {credit-carry 0}} interest-rate-bps]
  (when-not (zero? interest-rate-bps)
    (let [net available
          total-micro (+ (* net interest-rate-bps (quot micro-scale 10000))
                         (* credit-carry 365))
          daily-micro (quot total-micro 365)
          whole-units (quot daily-micro micro-scale)
          new-carry (rem daily-micro micro-scale)]
      {:whole-units whole-units :carry new-carry})))

(defn- customer-account?
  "Production filters customer accounts as
  `:product-type != :internal AND :product-type != :settlement
   AND :account-status = :opened`. The model mirrors all three
  checks: product-type and account `:status :open` (closed
  accounts are skipped)."
  [state acct]
  (let [account (get-in state [:accounts acct])
        product (:product account)
        ptype (get-in state [:products product :product-type])]
    (and ptype
         (not (#{:settlement :internal} ptype))
         (= :open (:status account)))))

(defn- accrue-account
  [state settlement-acct customer-acct]
  (let [account (get-in state [:accounts customer-acct])
        product-id (:product account)
        rate (get-in state [:products product-id :interest-rate-bps] 0)
        result (daily-interest account rate)]
    (cond
     ;; Rate is zero — production's daily-interest returns nil and
     ;; nothing is posted.
     (nil? result)
     state

     ;; Whole-units rounds to zero — production updates carry only.
     (zero? (:whole-units result))
     (assoc-in state [:accounts customer-acct :credit-carry] (:carry result))

     ;; Whole-units is positive — full accrual transaction posts.
     :else
     (let [{:keys [whole-units carry]} result]
       (-> state
           (update-in [:accounts customer-acct :interest-accrued]
                      (fnil + 0)
                      whole-units)
           (assoc-in [:accounts customer-acct :credit-carry] carry)
           (update-in [:accounts customer-acct :transaction-legs]
                      (fnil inc 0))
           (update-in [:accounts settlement-acct :available]
                      -
                      whole-units)
           (update-in [:accounts settlement-acct :transaction-legs]
                      (fnil inc 0)))))))

(defn- accrue-org
  [state org-id]
  (let [org (get-in state [:orgs org-id])
        settlement (:settlement-account org)
        custs (filter (fn [a] (customer-account? state a))
                      (:accounts org))]
    (reduce (fn [s a] (accrue-account s settlement a)) state custs)))

(def accrue-interest
  "Accrues daily interest for every customer account in the org.
  Args are `[org-id as-of-date]`. The args generator pulls the
  next sequential date from `:next-interest-date` and `:next-state`
  advances it, so every accrue/capitalize call is on a fresh date —
  the brick's date-keyed idempotency never fires."
  {:run? (fn [state] (seq (state/known-orgs state)))
   :args (fn [state]
           (gen/tuple (gen/elements (state/known-orgs state))
                      (gen/return (state/next-interest-date state))))
   :next-state (fn [state {[org-id _date] :args}]
                 (-> state
                     (accrue-org org-id)
                     (update :next-interest-date inc)))
   :valid? (fn [state {[org-id] :args}] (contains? (:orgs state) org-id))})

(defn- capitalize-account
  [state customer-acct]
  (let [accrued (get-in state [:accounts customer-acct :interest-accrued] 0)]
    (if (zero? accrued)
      state
      (-> state
          (update-in [:accounts customer-acct :available] + accrued)
          (assoc-in [:accounts customer-acct :interest-accrued] 0)
          ;; Capitalisation transaction touches the customer in 4
          ;; legs (interest-paid credit/debit + interest-accrued
          ;; debit + default credit).
          (update-in [:accounts customer-acct :transaction-legs]
                     (fnil + 0)
                     4)))))

(defn- capitalize-org
  [state org-id]
  (let [org (get-in state [:orgs org-id])
        settlement (:settlement-account org)
        custs (filter (fn [a] (customer-account? state a))
                      (:accounts org))
        capitalised (filter
                     (fn [a]
                       (pos? (get-in state
                                     [:accounts a :interest-accrued]
                                     0)))
                     custs)]
    (-> (reduce capitalize-account state custs)
        ;; Each capitalised customer triggers a transaction with 2
        ;; legs touching settlement (default debit, interest-payable
        ;; credit). settlement.available stays unchanged net.
        (update-in [:accounts settlement :transaction-legs]
                   (fnil + 0)
                   (* 2 (count capitalised))))))

(def capitalize-interest
  "Capitalises every customer account's accrued interest in the
  org. Args are `[org-id as-of-date]`. Shares the
  `:next-interest-date` counter with `accrue-interest`, so dates
  are unique across both commands."
  {:run? (fn [state] (seq (state/known-orgs state)))
   :args (fn [state]
           (gen/tuple (gen/elements (state/known-orgs state))
                      (gen/return (state/next-interest-date state))))
   :next-state (fn [state {[org-id _date] :args}]
                 (-> state
                     (capitalize-org org-id)
                     (update :next-interest-date inc)))
   :valid? (fn [state {[org-id] :args}] (contains? (:orgs state) org-id))})
