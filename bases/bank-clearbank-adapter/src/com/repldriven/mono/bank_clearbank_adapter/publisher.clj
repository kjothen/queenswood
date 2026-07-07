(ns com.repldriven.mono.bank-clearbank-adapter.publisher
  "Maps ClearBank webhook payloads to bus-event descriptors
  `{:event-name :dedup-key :data}`. The webhook handler serialises and
  persists these to the outbox; the relay publishes them. `dedup-key` is
  the event's logical identity (end-to-end id + kind) so a redelivered
  webhook does not double-enqueue. Each mapper returns a seq of
  descriptors (one, except assessment-failed which fans out per
  instruction)."
  (:require
    [clojure.string :as str]
    [com.repldriven.mono.utility.interface :as utility])
  (:import
    (java.time Instant)))

(defn- iso->epoch-millis
  [s]
  (.toEpochMilli (Instant/parse s)))

(defn- amount->minor-units
  [amount]
  (long (* amount 100)))

(defn inbound-payment-settled
  [payload]
  (let [{:keys [EndToEndTransactionId TransactionId Amount
                CurrencyCode Scheme Reference TimestampSettled
                Account CounterpartAccount]}
        payload
        {:keys [BBAN]} Account
        {:keys [OwnerName]} CounterpartAccount]
    [{:event-name "transaction-settled"
      :dedup-key (str EndToEndTransactionId ":settled")
      :data {:scheme-transaction-id TransactionId
             :end-to-end-id EndToEndTransactionId
             :scheme Scheme
             :debit-credit-code :debit-credit-code-credit
             :amount (amount->minor-units Amount)
             :currency CurrencyCode
             :creditor-bban BBAN
             :debtor-name OwnerName
             :reference Reference
             :timestamp-settled (iso->epoch-millis TimestampSettled)}}]))

(defn outbound-payment-settled
  [payload]
  (let [{:keys [EndToEndTransactionId TransactionId Amount
                CurrencyCode Scheme TimestampSettled]}
        payload]
    [{:event-name "transaction-settled"
      :dedup-key (str EndToEndTransactionId ":settled")
      :data {:scheme-transaction-id TransactionId
             :end-to-end-id EndToEndTransactionId
             :scheme Scheme
             :debit-credit-code :debit-credit-code-debit
             :amount (amount->minor-units Amount)
             :currency CurrencyCode
             :timestamp-settled (iso->epoch-millis TimestampSettled)}}]))

(defn- debit-credit-code->kw
  [code]
  (if (= "Credit" code)
    :debit-credit-code-credit
    :debit-credit-code-debit))

(defn outbound-payment-rejected
  [payload]
  (let [{:keys [EndToEndTransactionId Scheme DebitCreditCode CancellationCode
                CancellationReason IsReturn TimestampModified]}
        payload]
    [{:event-name "transaction-rejected"
      :dedup-key (str EndToEndTransactionId ":rejected")
      :data {:end-to-end-id EndToEndTransactionId
             :scheme (or Scheme "FasterPayments")
             :debit-credit-code (debit-credit-code->kw DebitCreditCode)
             :cancellation-code CancellationCode
             :cancellation-reason CancellationReason
             :is-return IsReturn
             :timestamp-rejected (if TimestampModified
                                   (iso->epoch-millis TimestampModified)
                                   (utility/now))}}]))

(defn outbound-payment-assessment-failed
  "ClearBank assessed the message and rejected it before settlement. One
  webhook can carry several failed instructions; each `EndToEndId` is an
  OutboundPayment to reverse. Routed through `transaction-rejected` — the
  pre-flight failure has the same outcome as a scheme decline. Fans out
  to one descriptor per instruction."
  [payload]
  (let [{:keys [PaymentMethodType AssessmentFailure]} payload]
    (mapv
     (fn [{:keys [EndToEndId Reasons]}]
       {:event-name "transaction-rejected"
        :dedup-key (str EndToEndId ":rejected")
        :data {:end-to-end-id EndToEndId
               :scheme (or PaymentMethodType "FasterPayments")
               :debit-credit-code :debit-credit-code-debit
               :cancellation-code "CB_AssessmentFailed"
               :cancellation-reason (str/join "; " Reasons)
               :is-return false
               :timestamp-rejected (utility/now)}})
     AssessmentFailure)))

(defn inbound-payment-held
  [payload]
  (let [{:keys [EndToEndTransactionId TransactionAmount
                Scheme TimestampCreated Account]}
        payload
        {:keys [BBAN]} Account]
    [{:event-name "transaction-held"
      :dedup-key (str EndToEndTransactionId ":held")
      :data {:end-to-end-id EndToEndTransactionId
             :scheme Scheme
             :debit-credit-code :debit-credit-code-credit
             :amount (amount->minor-units TransactionAmount)
             :currency "GBP"
             :creditor-bban BBAN
             :timestamp-held (iso->epoch-millis TimestampCreated)}}]))

(defn outbound-payment-held
  [payload]
  (let [{:keys [EndToEndTransactionId TransactionAmount
                Scheme TimestampCreated CounterpartAccount]}
        payload
        {:keys [BBAN]} CounterpartAccount]
    [{:event-name "transaction-held"
      :dedup-key (str EndToEndTransactionId ":held")
      :data {:end-to-end-id EndToEndTransactionId
             :scheme Scheme
             :debit-credit-code :debit-credit-code-debit
             :amount (amount->minor-units TransactionAmount)
             :currency "GBP"
             :creditor-bban BBAN
             :timestamp-held (iso->epoch-millis TimestampCreated)}}]))
