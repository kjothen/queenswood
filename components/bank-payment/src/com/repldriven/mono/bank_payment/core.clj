(ns com.repldriven.mono.bank-payment.core
  (:require
    [com.repldriven.mono.bank-payment.domain :as domain]
    [com.repldriven.mono.bank-payment.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as
     cash-accounts]
    [com.repldriven.mono.bank-transaction.interface :as
     transactions]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.utility.interface :as utility]))

(defn submit-internal
  [config data]
  (store/transact
   config
   (fn [txn]
     (let [{:keys [organization-id debtor-account-id
                   creditor-account-id]}
           data]
       (let-nom>
         [_ (cash-accounts/get-account txn
                                       organization-id
                                       debtor-account-id)
          _ (cash-accounts/get-account txn
                                       organization-id
                                       creditor-account-id)
          payment-transaction (domain/internal-payment->transaction data)
          transaction (transactions/record-transaction txn
                                                       payment-transaction)
          {:keys [transaction-id transaction-type legs]} transaction
          _ (balances/apply-legs txn legs transaction-type)
          payment (domain/new-internal-payment data transaction-id)
          _ (store/save-internal-payment txn payment)]
         payment)))))

(defn- publish-scheme-command
  [config payment data]
  (let [{:keys [bus schemas scheme-payment-command-channel]} config
        {:keys [payment-id]} payment
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
                                     :end-to-end-id payment-id
                                     :debtor-bban bban
                                     :creditor-bban creditor-bban
                                     :creditor-name creditor-name
                                     :amount amount
                                     :currency currency
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
  [config data]
  (let [{:keys [internal-account-id]} config
        {:keys [organization-id debtor-account-id]} data
        result (store/transact
                config
                (fn [txn]
                  (let-nom>
                    [_ (cash-accounts/get-account txn
                                                  organization-id
                                                  debtor-account-id)
                     transaction (domain/outbound-payment->transaction
                                  data
                                  internal-account-id)
                     transaction+legs (transactions/record-transaction
                                       txn
                                       transaction)
                     {:keys [transaction-id transaction-type legs]}
                     transaction+legs
                     _ (balances/apply-legs txn legs transaction-type)
                     payment (domain/new-outbound-payment data transaction-id)
                     _ (store/save-outbound-payment txn payment)]
                    payment)))]
    (when-not (error/anomaly? result)
      (publish-scheme-command config result data))
    result))
