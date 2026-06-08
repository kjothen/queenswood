(ns com.repldriven.mono.bank-ledger-account.core
  (:require
    [com.repldriven.mono.bank-ledger-account.domain :as domain]
    [com.repldriven.mono.bank-ledger-account.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-policy.interface :as policy]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- get-policies
  [txn bank-id opts]
  (or (:policies opts)
      (policy/get-effective-policies txn {:bank-id bank-id})))

(defn new-account
  ([txn bank-id currency row]
   (new-account txn bank-id currency row {}))
  ([txn bank-id currency row opts]
   (let-nom>
     [policies (get-policies txn bank-id opts)
      account (domain/new-ledger-account bank-id currency row policies)
      _ (store/save-account txn account)
      _ (balances/new-balances txn [(domain/opening-balance account)])]
     account)))

(defn get-account
  [txn bank-id ledger-account-id]
  (store/find-by-id txn bank-id ledger-account-id))

(defn find-by-code
  [txn bank-id gl-code]
  (store/find-by-code txn bank-id gl-code))

(defn list-accounts
  [txn bank-id]
  (store/list-by-bank txn bank-id))

(defn- control-leg
  "If `leg` is a customer default-posted leg, resolve the control
  ledger account for its product type and return a same-side mirror
  leg targeting it, tagged `:control` so the double-entry balance
  check skips the roll-up. Nil for legs that don't fan out, legs
  without a customer product type, and (currently) when the control
  account isn't seeded yet."
  [txn bank-id leg]
  (when (domain/fans-out? leg)
    (when-let [code (get domain/product-type->control-code
                         (:product-type leg))]
      (let-nom>
        [control (store/find-by-code txn bank-id code)]
        (when control
          {:account-id (:ledger-account-id control)
           :balance-type :balance-type-default
           :balance-status :balance-status-posted
           :side (:side leg)
           :amount (:amount leg)
           :control true})))))

(defn add-control-legs
  [txn bank-id legs]
  (reduce (fn [acc leg]
            (let [extra (control-leg txn bank-id leg)]
              (cond
               (error/anomaly? extra)
               (reduced extra)

               extra
               (conj acc leg extra)

               :else
               (conj acc leg))))
          []
          legs))
