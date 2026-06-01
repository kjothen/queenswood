(ns com.repldriven.mono.bank-payment.core
  (:require
    [com.repldriven.mono.bank-payment.domain :as domain]
    [com.repldriven.mono.bank-payment.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as
     cash-accounts]
    [com.repldriven.mono.bank-ledger-account.interface :as
     ledger-accounts]
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.bank-transaction.interface :as
     transactions]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.utility.interface :as utility]))

(def ^:private gl-code-pending-outbound "1200")

(defn- or-already-submitted
  "Translate the store's low-level uniqueness anomaly into the
  domain-level `:payment/already-submitted` rejection. Pass any
  other value through unchanged."
  [result]
  (if (store/uniqueness-violation? result)
    (error/reject :payment/already-submitted
                  "Payment already submitted")
    result))

(defn submit-internal
  [config data]
  (or-already-submitted
   (store/transact
    config
    (fn [txn]
      (let [{:keys [bank-id debtor-account-id
                    creditor-account-id]}
            data
            business-day (domain/current-business-day
                          (utility/now)
                          (:business-day-cutoff config))
            policies (policy/get-effective-policies
                      txn
                      {:bank-id bank-id})]
        (let-nom>
          [debtor-account (cash-accounts/get-account
                           txn
                           bank-id
                           debtor-account-id)
           creditor-account (cash-accounts/get-account
                             txn
                             bank-id
                             creditor-account-id)
           today-count (store/count-internal-by-org-business-day
                        txn
                        bank-id
                        business-day)
           aggregates {:internal-payment
                       {#{:bank-id :business-day} today-count}}
           payment-transaction (domain/internal-payment->transaction
                                data
                                debtor-account
                                creditor-account
                                policies
                                aggregates)
           expanded-legs (ledger-accounts/expand-legs
                          txn
                          bank-id
                          (:legs payment-transaction))
           transaction (transactions/record-transaction
                        txn
                        (assoc payment-transaction :legs expanded-legs))
           {:keys [transaction-id transaction-type legs]} transaction
           _ (balances/apply-legs txn legs transaction-type)
           payment (domain/new-internal-payment data
                                                business-day
                                                transaction-id)
           _ (store/save-internal-payment txn payment)]
          payment))))))

(defn- publish-scheme-command
  [config payment data]
  (let [{:keys [bus schemas scheme-payment-command-channel]} config
        {:keys [payment-id]} payment
        {:keys [bank-id debtor-account-id
                creditor-bban creditor-name
                currency amount reference]}
        data
        debtor-account (cash-accounts/get-account
                        config
                        bank-id
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
  (let [{:keys [bank-id debtor-account-id]} data
        result (or-already-submitted
                (store/transact
                 config
                 (fn [txn]
                   (let [business-day (domain/current-business-day
                                       (utility/now)
                                       (:business-day-cutoff config))
                         policies (policy/get-effective-policies
                                   txn
                                   {:bank-id bank-id})]
                     (let-nom>
                       [debtor-account (cash-accounts/get-account
                                        txn
                                        bank-id
                                        debtor-account-id)
                        pending-outbound
                        (ledger-accounts/find-by-code
                         txn
                         bank-id
                         gl-code-pending-outbound)
                        _ (when (nil? pending-outbound)
                            (error/reject
                             :payment/no-pending-outbound-account
                             {:message
                              (str "Bank has no 1200 pending-outbound"
                                   " account in its chart of accounts")
                              :bank-id bank-id}))
                        today-count (store/count-outbound-by-org-business-day
                                     txn
                                     bank-id
                                     business-day)
                        aggregates {:outbound-payment
                                    {#{:bank-id :business-day}
                                     today-count}}
                        transaction (domain/outbound-payment->transaction
                                     data
                                     debtor-account
                                     (:ledger-account-id pending-outbound)
                                     policies
                                     aggregates)
                        expanded-legs (ledger-accounts/expand-legs
                                       txn
                                       bank-id
                                       (:legs transaction))
                        transaction+legs (transactions/record-transaction
                                          txn
                                          (assoc transaction
                                                 :legs
                                                 expanded-legs))
                        {:keys [transaction-id transaction-type legs]}
                        transaction+legs
                        _ (balances/apply-legs txn legs transaction-type)
                        payment (domain/new-outbound-payment data
                                                             business-day
                                                             transaction-id)
                        _ (store/save-outbound-payment txn payment)]
                       payment)))))]
    (when-not (error/anomaly? result)
      (publish-scheme-command config result data))
    result))
