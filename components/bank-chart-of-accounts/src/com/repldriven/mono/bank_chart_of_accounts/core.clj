(ns com.repldriven.mono.bank-chart-of-accounts.core
  (:require
    [com.repldriven.mono.bank-chart-of-accounts.domain :as domain]

    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.bank-cash-account-product.interface :as products]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- seed-product
  "Create + publish one GL seed product for a single template row in
  a single currency, returning the published version map."
  [txn bank-id currency policies row]
  (let-nom>
    [version (products/new-product
              txn
              bank-id
              {:name (:name row)
               :currency currency
               :kind {:general-ledger
                      {:gl-code (:gl-code row)
                       :gl-account-type (:gl-account-type row)
                       :gl-account-class (:gl-account-class row)
                       :required (:required row)}}}
              {:policies policies})
     _ (products/publish txn
                         bank-id
                         (:product-id version)
                         (:version-id version)
                         {:policies policies})]
    version))

(defn- seed-account
  "Open the single CashAccount under a GL seed product."
  [txn bank-id party-id currency policies product row]
  (cash-accounts/new-account
   txn
   {:bank-id bank-id
    :party-id party-id
    :product-id (:product-id product)
    :name (:name row)
    :currency currency}
   {:policies policies}))

(defn- seed-row
  "Provision one GL product + its CashAccount for the given template
  row in the given currency."
  [txn bank-id party-id currency policies row]
  (let-nom>
    [product (seed-product txn bank-id currency policies row)
     account (seed-account txn
                           bank-id
                           party-id
                           currency
                           policies
                           product
                           row)]
    account))

(defn- seed-currency
  "Provision the full template (seven GL products + seven GL
  accounts) for one currency."
  [txn bank-id party-id currency policies]
  (reduce (fn [acc row]
            (let [result (seed-row txn
                                   bank-id
                                   party-id
                                   currency
                                   policies
                                   row)]
              (if (error/anomaly? result)
                (reduced result)
                (conj acc result))))
          []
          domain/template))

(defn seed!
  "Provision the canonical chart of accounts for `bank-id`. For each
  currency, creates one GL product per template row (seven total) and
  the single CashAccount under each. All accounts owned by `party-id`
  (the bank's own organization-party). Returns a flat vector of every
  created account, or an anomaly on the first failure (the surrounding
  `store/transact` rolls the whole thing back).

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - party-id: the bank's own organization-party id.
  - currencies: vector of ISO 4217 currency strings.
  - policies: effective policies for capability and limit checks."
  [txn bank-id party-id currencies policies]
  (reduce (fn [acc currency]
            (let [result (seed-currency txn
                                        bank-id
                                        party-id
                                        currency
                                        policies)]
              (if (error/anomaly? result)
                (reduced result)
                (into acc result))))
          []
          currencies))

(defn find-gl-account-by-code
  "Resolve a GL account from its code by composing the product lookup
  with the account-by-product lookup. Returns the CashAccount map (or
  nil if the GL product or its account isn't seeded yet). Used by
  posting sites (`bank-payment`, `bank-interest`) to find counter-leg
  accounts like 1100 (cash at correspondent), 2400 (interest payable),
  4100 (fee income).

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - gl-code: GL account code string (e.g. \"1100\")."
  [txn bank-id gl-code]
  (let-nom>
    [product (products/find-product-by-gl-code txn bank-id gl-code)]
    (when product
      (cash-accounts/find-account-by-product txn
                                             bank-id
                                             (:product-id product)))))

;; ---------------------------------------------------------------------------
;; Paired-leg construction (sub-ledger ↔ control)

(defn- fans-out?
  "Only customer-deposit legs touching the default-posted bucket roll
  up into a control. Non-default buckets (interest-accrued,
  interest-paid) and non-posted statuses are sub-ledger-only and don't
  have a GL counterpart."
  [leg]
  (and (= :balance-type-default (:balance-type leg))
       (= :balance-status-posted (:balance-status leg))))

(defn- control-leg-for
  "If `leg`'s account-id resolves to a customer cash account carrying
  a `:gl-control-account-id` AND the leg is on the default-posted
  bucket, return a synthetic control-side leg targeting that bank's
  GL control account. Same-side mirror — debit pairs with debit,
  credit with credit, because the customer cash-account is the
  sub-ledger of the control liability (both move in lockstep). Nil
  for GL legs, customer legs whose account doesn't carry a control
  pointer, and legs that don't touch the default-posted bucket."
  [txn bank-id leg]
  (when (fans-out? leg)
    (let-nom>
      [account (cash-accounts/get-account txn bank-id (:account-id leg))]
      ;; proto2 optional strings deserialize as "" when unset; treat
      ;; empty as "no control account" (GL legs and customer legs on
      ;; products without a control pointer).
      (when-let [control-account-id (not-empty
                                     (:gl-control-account-id account))]
        {:account-id control-account-id
         :balance-type :balance-type-default
         :balance-status :balance-status-posted
         :side (:side leg)
         :amount (:amount leg)}))))

(defn expand-legs
  "Walk `legs` and append a matching control-side leg for every
  customer sub-ledger leg whose account carries a
  `:gl-control-account-id`. Posting sites (`bank-payment`,
  `bank-interest`) call this BEFORE
  `transactions/record-transaction` so the synthetic control leg
  lands atomically in the same FDB transaction as the originals.
  GL-only legs pass through unchanged. Returns the expanded leg
  vector, or an anomaly on lookup failure.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: owning bank id.
  - legs: original transaction legs."
  [txn bank-id legs]
  (reduce (fn [acc leg]
            (let [extra (control-leg-for txn bank-id leg)]
              (cond
               (error/anomaly? extra)
               (reduced extra)

               extra
               (conj acc leg extra)

               :else
               (conj acc leg))))
          []
          legs))
