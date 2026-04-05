(ns com.repldriven.mono.bank-payment.commands
  (:require
    [com.repldriven.mono.bank-payment.domain :as domain]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as
     cash-accounts]
    [com.repldriven.mono.bank-schema.interface :as schema]
    [com.repldriven.mono.bank-transaction.interface :as
     transactions]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- ->response
  [config schema-name result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      (let-nom> [payload
                 (avro/serialize (schemas schema-name)
                                 result)]
        {:status "ACCEPTED" :payload payload}))))

(defn submit-internal
  "Submits an internal payment. Records the underlying
  transaction and persists the payment record atomically."
  [config data]
  (->response
   config
   "internal-payment"
   (let [{:keys [record-db record-store]} config]
     (fdb/transact-multi
      record-db
      record-store
      (fn [open-store]
        (let-nom>
          [balances-store (open-store "balances")
           internal-payments-store (open-store "internal-payments")
           payment-transaction (domain/internal-payment->transaction data)
           transaction (transactions/record-transaction open-store
                                                        payment-transaction)
           {:keys [transaction-id legs]} transaction
           _ (balances/apply-legs balances-store legs)
           payment (domain/new-internal-payment data transaction-id)
           _ (fdb/save-record internal-payments-store
                              (schema/InternalPayment->java payment))]
          payment))))))

(defn- publish-scheme-command
  "Fire-and-forget: publishes a submit-payment command to
  the scheme payment channel."
  [config payment data]
  (let [{:keys [bus schemas scheme-payment-command-channel]}
        config
        {:keys [payment-id end-to-end-id]} payment
        {:keys [organization-id debtor-account-id
                creditor-bban creditor-name
                currency amount reference]}
        data
        debtor-account (cash-accounts/get-account
                        config
                        organization-id
                        debtor-account-id)
        bban (when-not (error/anomaly? debtor-account)
               (:bban debtor-account))
        schema (get schemas "submit-payment")]
    (when (and bus schema scheme-payment-command-channel)
      (let [payload (avro/serialize schema
                                    {:payment-id payment-id
                                     :end-to-end-id end-to-end-id
                                     :debtor-bban bban
                                     :creditor-bban creditor-bban
                                     :creditor-name creditor-name
                                     :amount amount
                                     :currency (or currency "GBP")
                                     :reference reference})]
        (if (error/anomaly? payload)
          (log/error "Failed to serialize submit-payment"
                     payload)
          (let [envelope {:command "submit-payment"
                          :id (str (utility/uuidv7))
                          :correlation-id (str (utility/uuidv7))
                          :causation-id payment-id
                          :payload payload}]
            (message-bus/send bus
                              scheme-payment-command-channel
                              envelope)))))))

(defn submit-outbound
  "Submits an outbound payment. Debits the customer
  account, credits the settlement suspense, persists the
  OutboundPayment record in pending status, and publishes
  a submit-payment command to the scheme adapter."
  [config data]
  (let [{:keys [record-db record-store settlement-account-id]}
        config
        end-to-end-id (str (utility/uuidv7))
        result
        (fdb/transact-multi
         record-db
         record-store
         (fn [open-store]
           (let-nom>
             [balances-store (open-store "balances")
              outbound-payments-store (open-store
                                       "outbound-payments")
              txn-data (domain/outbound-payment->transaction
                        data
                        settlement-account-id)
              result (transactions/record-transaction
                      open-store
                      txn-data)
              {:keys [transaction-id legs]} result
              _ (balances/apply-legs balances-store legs)
              payment (domain/new-outbound-payment
                       data
                       end-to-end-id
                       transaction-id)
              _ (fdb/save-record
                 outbound-payments-store
                 (schema/OutboundPayment->java payment))]
             payment)))]
    (when-not (error/anomaly? result)
      (publish-scheme-command config result data))
    (->response config "outbound-payment" result)))
