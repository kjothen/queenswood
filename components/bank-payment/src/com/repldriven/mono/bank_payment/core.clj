(ns com.repldriven.mono.bank-payment.core
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

(defn ->response
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
   (fdb/transact
    config
    (fn [txn]
      (let-nom>
        [payment-transaction (domain/internal-payment->transaction data)
         transaction (transactions/record-transaction txn
                                                      payment-transaction)
         {:keys [transaction-id legs]} transaction
         _ (balances/apply-legs txn legs)
         payment (domain/new-internal-payment data transaction-id)
         _ (fdb/save-record (fdb/open txn "internal-payments")
                            (schema/InternalPayment->java payment))]
        payment)))))

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
  (let [{:keys [internal-account-id]} config
        end-to-end-id (str (utility/uuidv7))
        result (fdb/transact
                config
                (fn [txn]
                  (let-nom>
                    [txn-data (domain/outbound-payment->transaction
                               data
                               internal-account-id)
                     result (transactions/record-transaction txn txn-data)
                     {:keys [transaction-id legs]} result
                     _ (balances/apply-legs txn legs)
                     payment (domain/new-outbound-payment
                              data
                              end-to-end-id
                              transaction-id)
                     _ (fdb/save-record
                        (fdb/open txn "outbound-payments")
                        (schema/OutboundPayment->java payment))]
                    payment)))]
    (when-not (error/anomaly? result)
      (publish-scheme-command config result data))
    (->response config "outbound-payment" result)))
