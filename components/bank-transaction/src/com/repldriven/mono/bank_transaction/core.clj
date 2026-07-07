(ns com.repldriven.mono.bank-transaction.core
  (:require
    [com.repldriven.mono.bank-transaction.domain :as domain]
    [com.repldriven.mono.bank-transaction.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn record
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (let [{:keys [legs]} data
           transaction (domain/new-transaction data)
           {:keys [transaction-id currency]} transaction
           legs' (mapv (fn [leg]
                         (domain/new-leg leg transaction-id currency))
                       legs)]
       (let-nom>
         [_ (domain/validate-legs legs)
          _ (store/save-transaction txn transaction)
          _ (store/save-legs txn legs')]
         (assoc transaction :legs legs'))))))

(defn- or-already-recorded
  "On a uniqueness violation — a redelivered record-transaction command
  carrying an already-seen idempotency-key — read the existing
  transaction back and return it, so the caller gets the original
  resource instead of a bare rejection. Any other value passes through
  unchanged."
  [txn data result]
  (if (and (store/uniqueness-violation? result)
           (:idempotency-key data))
    (let-nom> [existing (store/find-transaction-by-idempotency-key
                         txn
                         (:transaction-type data)
                         (:idempotency-key data))]
      (or existing result))
    result))

(defn record-and-post
  [txn data]
  (or-already-recorded
   txn
   data
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [result (record txn data)
         _ (balances/apply-legs txn
                                (:legs result)
                                (:transaction-type result))]
        result)))))
