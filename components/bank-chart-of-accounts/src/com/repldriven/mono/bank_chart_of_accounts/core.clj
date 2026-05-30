(ns com.repldriven.mono.bank-chart-of-accounts.core
  (:require
    [com.repldriven.mono.bank-chart-of-accounts.domain :as domain]

    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.bank-cash-account-product.interface :as products]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- new-product
  "Create + publish a chart-of-accounts seed product in `currency`,
  returning the published version map."
  [txn bank-id currency policies]
  (let-nom>
    [version (products/new-product
              txn
              bank-id
              {:name (str "Chart of Accounts (" currency ")")
               :product-type :product-type-chart-of-accounts
               :currency currency}
              {:policies policies})
     _ (products/publish txn
                         bank-id
                         (:product-id version)
                         (:version-id version)
                         {:policies policies})]
    version))

(defn- seed-row
  "Open one CoA account on the seed product for the given template
  row + currency. Returns the account map or an anomaly."
  [txn bank-id party-id product currency policies row]
  (cash-accounts/new-account
   txn
   {:bank-id bank-id
    :party-id party-id
    :product-id (:product-id product)
    :name (:name row)
    :currency currency
    :gl-code (:gl-code row)
    :gl-account-type (:gl-account-type row)
    :gl-account-class (:gl-account-class row)
    :required (:required row)}
   {:policies policies}))

(defn- seed-currency
  "Provision the full seven-row template for one currency. Creates
  one seed product and seven accounts under it."
  [txn bank-id party-id currency policies]
  (let-nom>
    [product (new-product txn bank-id currency policies)
     accounts (reduce (fn [acc row]
                        (let [result (seed-row txn
                                               bank-id
                                               party-id
                                               product
                                               currency
                                               policies
                                               row)]
                          (if (error/anomaly? result)
                            (reduced result)
                            (conj acc result))))
                      []
                      domain/template)]
    accounts))

(defn seed!
  "Provision the canonical chart of accounts for `bank-id`. Creates
  one seed product per currency and the seven template accounts on
  each. All accounts are owned by `party-id` (the bank's own
  organization-party). Returns a flat vector of all created
  accounts, or an anomaly on the first failure (the surrounding
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

(defn- fans-out?
  "Only customer-deposit legs touching the default-posted bucket roll
  up into a control. Non-default buckets (interest-accrued,
  interest-paid) and non-posted statuses are sub-ledger-only and
  don't have a GL counterpart."
  [leg]
  (and (= :balance-type-default (:balance-type leg))
       (= :balance-status-posted (:balance-status leg))))

(defn- control-leg-for
  "If `leg`'s `account-id` resolves to a customer cash account
  carrying a `:gl-control-code` AND the leg is on the
  default-posted bucket, return a synthetic control-side leg
  targeting that bank's GL control account. Nil for GL accounts,
  legs whose accounts don't carry a control code, and legs that
  don't touch the default-posted bucket."
  [txn bank-id leg]
  (when (fans-out? leg)
    (let-nom>
      [account (cash-accounts/get-account txn bank-id (:account-id leg))]
      (when-let [control-code (:gl-control-code account)]
        (let-nom>
          [control (cash-accounts/get-account-by-gl-code txn
                                                         bank-id
                                                         control-code)]
          (when control
            {:account-id (:account-id control)
             :balance-type :balance-type-default
             :balance-status :balance-status-posted
             :side (:side leg)
             :amount (:amount leg)}))))))

(defn expand-legs
  "Walk `legs` and append a matching control-side leg for every
  customer sub-ledger leg. Used by posting sites (payment, interest)
  to materialise the sub-ledger ↔ control reconciliation in the
  same FDB transaction. Returns the expanded leg vector, or an
  anomaly if any lookup fails.

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
