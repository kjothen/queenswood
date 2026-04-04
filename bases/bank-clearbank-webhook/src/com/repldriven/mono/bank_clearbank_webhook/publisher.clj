(ns com.repldriven.mono.bank-clearbank-webhook.publisher
  (:require
    [com.repldriven.mono.bank-clearbank-webhook.events :as events]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.utility.interface :as utility])
  (:import
    (java.time Instant)))

(defn- iso->epoch-millis
  [s]
  (.toEpochMilli (Instant/parse s)))

(defn- amount->minor-units
  [amount]
  (long (* amount 100)))

(defn publish-inbound-payment-settled
  [config payload]
  (let [{:keys [bus avro event-channel]} config
        {:keys [EndToEndTransactionId TransactionId Amount
                CurrencyCode Scheme Reference TimestampSettled
                Account CounterpartAccount]}
        payload
        {:keys [BBAN]} Account
        {:keys [OwnerName]} CounterpartAccount]
    (events/publish bus
                    avro
                    "transaction-settled"
                    (str (utility/uuidv7))
                    (str (utility/uuidv7))
                    {:scheme-transaction-id TransactionId
                     :end-to-end-id EndToEndTransactionId
                     :scheme Scheme
                     :debit-credit-code :debit-credit-code-credit
                     :amount (amount->minor-units Amount)
                     :currency CurrencyCode
                     :creditor-bban BBAN
                     :debtor-name OwnerName
                     :reference Reference
                     :timestamp-settled (iso->epoch-millis
                                         TimestampSettled)}
                    {:event-channel event-channel})))

(defn publish-outbound-payment-settled
  [_config payload]
  (let [{:keys [EndToEndTransactionId TransactionId Amount
                CurrencyCode Scheme TimestampSettled]}
        payload]
    (log/info "TODO publish outbound-payment-settled"
              {:end-to-end-id EndToEndTransactionId
               :transaction-id TransactionId
               :amount Amount
               :currency CurrencyCode
               :scheme Scheme
               :timestamp TimestampSettled})))

(defn publish-outbound-payment-rejected
  [_config payload]
  (let [{:keys [EndToEndTransactionId CancellationCode
                CancellationReason TimestampModified]}
        payload]
    (log/info "TODO publish outbound-payment-rejected"
              {:end-to-end-id EndToEndTransactionId
               :cancellation-code CancellationCode
               :cancellation-reason CancellationReason
               :timestamp TimestampModified})))

(defn publish-outbound-payment-assessment-failed
  [_config payload]
  (let [{:keys [MessageId AssessmentFailure]} payload]
    (log/info "TODO publish outbound-payment-assessment-failed"
              {:message-id MessageId
               :failures AssessmentFailure})))

(defn publish-inbound-payment-held
  [config payload]
  (let [{:keys [bus avro]} config
        {:keys [EndToEndTransactionId TransactionAmount
                Scheme TimestampCreated Account]}
        payload
        {:keys [BBAN]} Account]
    (events/publish bus
                    avro
                    "transaction-held"
                    (str (utility/uuidv7))
                    (str (utility/uuidv7))
                    {:end-to-end-id EndToEndTransactionId
                     :scheme Scheme
                     :amount TransactionAmount
                     :currency "GBP"
                     :creditor-bban BBAN
                     :timestamp-held TimestampCreated})))
