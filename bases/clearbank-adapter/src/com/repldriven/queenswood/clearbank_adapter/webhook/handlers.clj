(ns com.repldriven.queenswood.clearbank-adapter.webhook.handlers
  (:require
    [com.repldriven.queenswood.clearbank-adapter.publisher :as publisher]

    [com.repldriven.queenswood.cash-account-query.interface :as cash-accounts]
    [com.repldriven.queenswood.clearbank-relay.interface :as relay]
    [com.repldriven.queenswood.party-query.interface :as parties]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- persist-one
  "Serialise one event descriptor and write it to the outbox. A
  duplicate `dedup-key` (redelivered webhook) counts as success — the
  event is already recorded. Returns `:ok` or a non-dedup anomaly."
  [config schemas {:keys [event-name dedup-key data]}]
  (let [schema (get schemas event-name)]
    (if (nil? schema)
      (error/fail :clearbank-adapter/unknown-event
                  {:message "No schema for event" :event event-name})
      (let-nom> [payload (avro/serialize schema data)]
        (let [res (relay/save-event
                   config
                   {:outbox-id (str (utility/uuidv7))
                    :dedup-key dedup-key
                    :event-name event-name
                    :payload payload
                    :correlation-id (str (utility/uuidv7))
                    :causation-id (str (utility/uuidv7))
                    :created-at (utility/now)})]
          (if (relay/uniqueness-violation? res)
            :ok
            res))))))

(defn- record-webhook
  "Persist every event descriptor a webhook produces to the outbox in
  order, stopping at the first real failure. Returns `:ok` or an
  anomaly. The relay publishes them to the bus separately, so 'webhook
  received' and 'downstream told' cannot diverge."
  [request descriptors]
  (let [{:keys [record-db record-store avro]} request
        config {:record-db record-db :record-store record-store}]
    (reduce (fn [_ descriptor]
              (let [res (persist-one config avro descriptor)]
                (if (error/anomaly? res) (reduced res) :ok)))
            :ok
            descriptors)))

(defn- webhook-response
  "200 with the Nonce echoed when the webhook was durably recorded; 500
  otherwise so ClearBank redelivers rather than the event being lost."
  [what result nonce]
  (if (error/anomaly? result)
    (do (log/error (str "Failed to record " what " webhook") result)
        {:status 500 :body {:error "webhook not recorded"}})
    {:status 200 :body {:Nonce nonce}}))

(defn transaction-settled
  [_config]
  (fn [request]
    (let [{:keys [parameters]} request
          {:keys [body]} parameters
          {:keys [Payload Nonce]} body
          {:keys [EndToEndTransactionId Scheme DebitCreditCode]} Payload]
      (log/info "transaction-settled webhook received"
                {:e2e-id EndToEndTransactionId
                 :scheme Scheme
                 :debit-credit-code DebitCreditCode})
      (let [descriptors (case DebitCreditCode
                          "Credit" (publisher/inbound-payment-settled Payload)
                          "Debit" (publisher/outbound-payment-settled Payload))]
        (webhook-response "transaction-settled"
                          (record-webhook request descriptors)
                          Nonce)))))

(defn transaction-rejected
  [_config]
  (fn [request]
    (let [{:keys [parameters]} request
          {:keys [body]} parameters
          {:keys [Payload Nonce]} body
          {:keys [EndToEndTransactionId CancellationCode]} Payload]
      (log/info "transaction-rejected webhook received"
                {:e2e-id EndToEndTransactionId
                 :code CancellationCode})
      (webhook-response "transaction-rejected"
                        (record-webhook
                         request
                         (publisher/outbound-payment-rejected Payload))
                        Nonce))))

(defn payment-message-assessment-failed
  [_config]
  (fn [request]
    (let [{:keys [parameters]} request
          {:keys [body]} parameters
          {:keys [Payload Nonce]} body
          {:keys [MessageId]} Payload]
      (log/info "payment-message-assessment-failed webhook received"
                {:message-id MessageId})
      (webhook-response
       "payment-message-assessment-failed"
       (record-webhook
        request
        (publisher/outbound-payment-assessment-failed Payload))
       Nonce))))

(defn inbound-held-transaction
  [_config]
  (fn [request]
    (let [{:keys [parameters]} request
          {:keys [body]} parameters
          {:keys [Payload Nonce]} body]
      (log/info "inbound-held-transaction webhook received"
                {:payload Payload})
      (webhook-response "inbound-held-transaction"
                        (record-webhook
                         request
                         (publisher/inbound-payment-held Payload))
                        Nonce))))

(defn outbound-held-transaction
  [_config]
  (fn [request]
    (let [{:keys [parameters]} request
          {:keys [body]} parameters
          {:keys [Payload Nonce]} body
          {:keys [EndToEndTransactionId Scheme]} Payload]
      (log/info "outbound-held-transaction webhook received"
                {:e2e-id EndToEndTransactionId
                 :scheme Scheme})
      (webhook-response "outbound-held-transaction"
                        (record-webhook
                         request
                         (publisher/outbound-payment-held Payload))
                        Nonce))))

(defn- cop-result
  [match-keyword display-name]
  (case match-keyword
    :match
    {:matchResult "Match"}

    :close-match
    {:matchResult "CloseMatch"
     :actualName display-name
     :reasonCode "PANM"
     :reason "Partial name match"}

    :no-match
    {:matchResult "NoMatch"
     :reasonCode "ANNM"
     :reason "Account name does not match"}))

(defn inbound-cop-request-received
  [_config]
  (fn [request]
    (let [{:keys [parameters record-db record-store]} request
          {:keys [body]} parameters
          {:keys [Payload]} body
          {:keys [RequestId AccountHolderName AccountDetails]} Payload
          {:keys [SortCode AccountNumber]} AccountDetails
          bban (str SortCode AccountNumber)
          config {:record-db record-db
                  :record-store record-store}]
      (log/info "inbound-cop-request-received webhook"
                {:request-id RequestId
                 :bban bban
                 :name AccountHolderName})
      (let [account (cash-accounts/get-account-by-bban config bban)]
        (if (or (nil? account) (error/anomaly? account))
          {:status 200
           :body {:matchResult "NoMatch"
                  :reasonCode "ACNS"
                  :reason "Account not found"}}
          (let [{:keys [display-name]}
                (let [party (parties/get-party config
                                               (:bank-id account)
                                               (:party-id account))]
                  (when-not (error/anomaly? party) party))
                result (if display-name
                         (parties/match-name display-name
                                             AccountHolderName)
                         :no-match)]
            {:status 200
             :body (cop-result result display-name)}))))))
