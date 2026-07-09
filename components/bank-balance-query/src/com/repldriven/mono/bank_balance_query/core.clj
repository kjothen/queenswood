(ns com.repldriven.mono.bank-balance-query.core
  (:require
    [com.repldriven.mono.bank-balance-query.store :as store]

    [com.repldriven.mono.bank-balance-domain.interface :as domain]
    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn get-balances
  [txn account-id]
  (let-nom>
    [result (store/get-balances txn account-id)]
    (let [currency (:currency (first result) "")]
      {:balances result
       :posted-balance (domain/posted-balance result currency)
       :available-balance (domain/available-balance result currency)})))
