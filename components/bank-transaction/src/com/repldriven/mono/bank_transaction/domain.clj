(ns com.repldriven.mono.bank-transaction.domain
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.utility.interface :as utility]))

(def ^:private type->status
  {:transaction-type-internal-transfer :transaction-status-posted
   :transaction-type-inbound-transfer :transaction-status-posted})

(defn new-transaction
  [data]
  (let [{:keys [idempotency-key transaction-type currency
                reference]}
        data
        now (utility/now)
        status (get type->status
                    transaction-type
                    :transaction-status-pending)]
    (utility/assoc-some
     {:transaction-id (utility/generate-id "txn")
      :idempotency-key idempotency-key
      :transaction-type transaction-type
      :currency currency
      :status status
      :created-at now
      :updated-at now}
     :reference
     reference)))

(defn validate-legs
  [legs]
  (when (some #(<= (:amount %) 0) legs)
    (error/reject :transaction/invalid-amount
                  "Transaction amount must be positive")))

(defn new-leg
  [leg transaction-id currency]
  (let [{:keys [account-id balance-type balance-status
                side amount]}
        leg]
    {:leg-id (utility/generate-id "leg")
     :transaction-id transaction-id
     :account-id account-id
     :balance-type balance-type
     :balance-status balance-status
     :side side
     :amount amount
     :currency currency
     :created-at (utility/now)}))
