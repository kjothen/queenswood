(ns com.repldriven.mono.bank-test-model.interest
  (:require
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(def ^:private micro-scale 1000000)

(defn daily-interest
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
     (nil? result)
     state

     (zero? (:whole-units result))
     (assoc-in state [:accounts customer-acct :credit-carry] (:carry result))

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
  {:run? (fn [state] (seq (state/known-orgs state)))
   :args (fn [state]
           (gen/tuple (gen/elements (state/known-orgs state))
                      (gen/return (state/next-interest-date state))))
   :next-state (fn [state {[org-id _date] :args}]
                 (-> state
                     (capitalize-org org-id)
                     (update :next-interest-date inc)))
   :valid? (fn [state {[org-id] :args}] (contains? (:orgs state) org-id))})
