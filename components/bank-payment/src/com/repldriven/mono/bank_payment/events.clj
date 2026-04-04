(ns com.repldriven.mono.bank-payment.events
  (:require
    [com.repldriven.mono.bank-payment.domain :as domain]

    [com.repldriven.mono.bank-cash-account.interface :as
     cash-accounts]
    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-schema.interface :as schema]
    [com.repldriven.mono.bank-transaction.interface :as
     transactions]

    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.log.interface :as log]))

(defn- existing-payment
  "Returns existing InboundPayment for the given
  scheme-transaction-id, or nil if not found."
  [{:keys [record-db record-store]} scheme-transaction-id]
  (fdb/transact
   record-db
   record-store
   "inbound-payments"
   (fn [store]
     (first (mapv schema/pb->InboundPayment
                  (fdb/query-records
                   store
                   "InboundPayment"
                   "scheme_transaction_id"
                   scheme-transaction-id))))))

(defn settle-inbound
  "Processes an inbound payment settlement event.

  Validates credit direction, looks up creditor account
  by BBAN, checks idempotency, then atomically records
  the transaction and persists the InboundPayment."
  [config data]
  (let [{:keys [debit-credit-code creditor-bban
                scheme-transaction-id]}
        data
        {:keys [record-db record-store
                settlement-account-id]}
        config]
    (if (not= :debit-credit-code-credit debit-credit-code)
      (do (log/warnf "settle-inbound: ignoring non-credit: %s"
                     debit-credit-code)
          nil)
      (let-nom>
        [account (cash-accounts/get-account-by-bban
                  config
                  creditor-bban)
         _ (when-not account
             (error/fail :payment/unknown-creditor
                         {:message "No account found for BBAN"
                          :bban creditor-bban}))]
        (let [existing (existing-payment config
                                         scheme-transaction-id)]
          (if existing
            (do (log/info "settle-inbound: idempotent skip"
                          {:scheme-transaction-id
                           scheme-transaction-id})
                existing)
            (let [{:keys [account-id]} account]
              (fdb/transact-multi
               record-db
               record-store
               (fn [open-store]
                 (let-nom>
                   [txn-data (domain/inbound-payment->transaction
                              data
                              account-id
                              settlement-account-id)
                    result (transactions/record-transaction
                            open-store
                            txn-data)
                    {:keys [transaction-id legs]} result
                    balances-store (open-store "balances")
                    _ (balances/apply-legs balances-store legs)
                    payment (domain/new-inbound-payment
                             data
                             account-id
                             transaction-id)
                    inbound-store (open-store "inbound-payments")
                    _ (fdb/save-record
                       inbound-store
                       (schema/InboundPayment->java
                        payment))]
                   payment))))))))))
