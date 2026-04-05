(ns com.repldriven.mono.bank-payment.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn internal-payment->transaction
  "Builds the transaction data for an internal payment."
  [data]
  (let [{:keys [idempotency-key debtor-account-id
                creditor-account-id currency amount
                reference]}
        data]
    {:idempotency-key idempotency-key
     :transaction-type :transaction-type-internal-transfer
     :currency currency
     :reference reference
     :legs [{:account-id debtor-account-id
             :balance-type :balance-type-default
             :balance-status :balance-status-posted
             :side :leg-side-debit
             :amount amount}
            {:account-id creditor-account-id
             :balance-type :balance-type-default
             :balance-status :balance-status-posted
             :side :leg-side-credit
             :amount amount}]}))

(defn inbound-payment->transaction
  "Builds the transaction data for an inbound payment.

  Debits the settlement (suspense) account and credits
  the customer account."
  [data creditor-account-id settlement-account-id]
  (let [{:keys [scheme-transaction-id currency amount
                reference]}
        data]
    {:idempotency-key scheme-transaction-id
     :transaction-type :transaction-type-inbound-transfer
     :currency currency
     :reference reference
     :legs [{:account-id settlement-account-id
             :balance-type :balance-type-suspense
             :balance-status :balance-status-posted
             :side :leg-side-debit
             :amount amount}
            {:account-id creditor-account-id
             :balance-type :balance-type-default
             :balance-status :balance-status-posted
             :side :leg-side-credit
             :amount amount}]}))

(defn new-inbound-payment
  "Creates a new inbound payment map."
  [data creditor-account-id transaction-id]
  (let [{:keys [scheme-transaction-id end-to-end-id scheme
                currency amount debtor-name reference]}
        data
        now (System/currentTimeMillis)]
    {:payment-id (encryption/generate-id "pmt")
     :scheme-transaction-id scheme-transaction-id
     :end-to-end-id end-to-end-id
     :scheme scheme
     :creditor-account-id creditor-account-id
     :currency currency
     :amount amount
     :transaction-id transaction-id
     :debtor-name debtor-name
     :reference reference
     :created-at now
     :updated-at now}))

(defn outbound-payment->transaction
  "Builds the transaction data for an outbound payment.

  Debits the customer account and credits the settlement
  (suspense) account."
  [data settlement-account-id]
  (let [{:keys [idempotency-key debtor-account-id
                currency amount reference]}
        data]
    {:idempotency-key idempotency-key
     :transaction-type :transaction-type-outbound-transfer
     :currency currency
     :reference reference
     :legs [{:account-id debtor-account-id
             :balance-type :balance-type-default
             :balance-status :balance-status-posted
             :side :leg-side-debit
             :amount amount}
            {:account-id settlement-account-id
             :balance-type :balance-type-suspense
             :balance-status :balance-status-posted
             :side :leg-side-credit
             :amount amount}]}))

(defn new-outbound-payment
  "Creates a new outbound payment map in pending status."
  [data end-to-end-id transaction-id]
  (let [{:keys [idempotency-key debtor-account-id
                creditor-bban creditor-name scheme
                currency amount reference]}
        data
        now (System/currentTimeMillis)]
    {:payment-id (encryption/generate-id "pmt")
     :idempotency-key idempotency-key
     :end-to-end-id end-to-end-id
     :scheme scheme
     :debtor-account-id debtor-account-id
     :creditor-bban creditor-bban
     :creditor-name creditor-name
     :currency currency
     :amount amount
     :payment-status :outbound-payment-status-pending
     :transaction-id transaction-id
     :reference reference
     :created-at now
     :updated-at now}))

(defn new-internal-payment
  "Creates a new internal payment map."
  [data transaction-id]
  (let [{:keys [idempotency-key debtor-account-id
                creditor-account-id currency amount
                reference]}
        data
        now (System/currentTimeMillis)]
    {:payment-id (encryption/generate-id "pmt")
     :idempotency-key idempotency-key
     :debtor-account-id debtor-account-id
     :creditor-account-id creditor-account-id
     :currency currency
     :amount amount
     :transaction-id transaction-id
     :reference reference
     :created-at now
     :updated-at now}))
