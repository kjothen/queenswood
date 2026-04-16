(ns com.repldriven.mono.bank-payment.events
  (:require
    [com.repldriven.mono.bank-payment.domain :as domain]
    [com.repldriven.mono.bank-payment.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.bank-transaction.interface :as transactions]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]))

(defn settle-inbound
  "Processes an inbound payment settlement event.

  Validates credit direction, looks up creditor account
  by BBAN, checks idempotency, then atomically records
  the transaction and persists the InboundPayment."
  [config data]
  (let [{:keys [debit-credit-code creditor-bban scheme-transaction-id]} data
        {:keys [internal-account-id]} config]
    (if (not= :debit-credit-code-credit debit-credit-code)
      (error/fail
       :payment/settle-inbound
       {:message "Inbound payment settlement for non-credit is not permissible"
        :debit-credit-code debit-credit-code})
      (let-nom>
        [account (cash-accounts/get-account-by-bban config creditor-bban)
         _ (when-not account
             (error/fail :payment/settle-inbound
                         {:message "No account found for creditor BBAN"
                          :bban creditor-bban}))
         settled (store/get-inbound-payment config scheme-transaction-id)]
        (if settled
          (do (log/infof
               "Inbound payment settlement already processed: %s"
               scheme-transaction-id)
              settled)
          (let [{:keys [account-id]} account]
            (store/transact
             config
             (fn [txn]
               (let-nom>
                 [transaction (domain/inbound-payment->transaction
                               data
                               account-id
                               internal-account-id)
                  transaction+legs (transactions/record-transaction
                                    txn
                                    transaction)
                  {:keys [transaction-id legs]} transaction+legs
                  _ (balances/apply-legs txn legs)
                  payment (domain/new-inbound-payment
                           data
                           account-id
                           transaction-id)
                  _ (store/save-inbound-payment txn payment)]
                 payment)))))))))

(defn settle-outbound
  "Processes an outbound payment settlement event.

  Looks up OutboundPayment by end-to-end-id and updates
  its status to completed."
  [config data]
  (let [{:keys [end-to-end-id]} data]
    (let-nom> [payment (store/get-outbound-payment config end-to-end-id)]
      (cond
       (nil? payment)
       (error/fail
        :payment/settle-outbound
        {:message "Failed to find corresponding outbound payment for settlement"
         :end-to-end-id end-to-end-id})

       (= :outbound-payment-status-completed (:payment-status payment))
       (do (log/infof "Outbound payment settlement already completed: %s"
                      end-to-end-id)
           payment)

       :else
       (let-nom>
         [completed (domain/completed-outbound-payment payment)
          _ (store/save-outbound-payment config completed)]
         (log/infof "Outbound payment settlement now completed: %s"
                    {:payment-id (:payment-id payment)
                     :end-to-end-id end-to-end-id})
         completed)))))
