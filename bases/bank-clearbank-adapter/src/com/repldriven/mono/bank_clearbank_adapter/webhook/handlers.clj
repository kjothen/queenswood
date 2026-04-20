(ns com.repldriven.mono.bank-clearbank-adapter.webhook.handlers
  (:require
    [com.repldriven.mono.bank-clearbank-adapter.nonce :as nonce]
    [com.repldriven.mono.bank-clearbank-adapter.publisher :as publisher]

    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.bank-party.interface :as parties]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]))

(defn- request-config
  [request]
  (select-keys request [:bus :avro :event-channel]))

(defn transaction-settled
  [_config]
  (fn [request]
    (let [{:keys [parameters]} request
          {:keys [body]} parameters
          {:keys [Payload Nonce]} body
          {:keys [EndToEndTransactionId Scheme DebitCreditCode]} Payload
          config (request-config request)]
      (log/info "transaction-settled webhook received"
                {:e2e-id EndToEndTransactionId
                 :scheme Scheme
                 :debit-credit-code DebitCreditCode})
      (nonce/record Nonce)
      (let [result
            (case DebitCreditCode
              "Credit" (publisher/publish-inbound-payment-settled config
                                                                  Payload)
              "Debit" (publisher/publish-outbound-payment-settled config
                                                                  Payload))]
        (when (error/anomaly? result)
          (log/error "Failed to publish event" result)))
      {:status 200
       :body {:Nonce Nonce}})))

(defn transaction-rejected
  [config]
  (fn [request]
    (let [{:keys [parameters]} request
          {:keys [body]} parameters
          {:keys [Payload Nonce]} body
          {:keys [EndToEndTransactionId CancellationCode]} Payload]
      (log/info "transaction-rejected webhook received"
                {:e2e-id EndToEndTransactionId
                 :code CancellationCode})
      (nonce/record Nonce)
      (publisher/publish-outbound-payment-rejected config Payload)
      {:status 200
       :body {:Nonce Nonce}})))

(defn payment-message-assessment-failed
  [config]
  (fn [request]
    (let [{:keys [parameters]} request
          {:keys [body]} parameters
          {:keys [Payload Nonce]} body]
      (log/info "payment-message-assessment-failed webhook received"
                {:payload Payload})
      (nonce/record Nonce)
      (publisher/publish-outbound-payment-assessment-failed config Payload)
      {:status 200
       :body {:Nonce Nonce}})))

(defn inbound-held-transaction
  [config]
  (fn [request]
    (let [{:keys [parameters]} request
          {:keys [body]} parameters
          {:keys [Payload Nonce]} body]
      (log/info "inbound-held-transaction webhook received"
                {:payload Payload})
      (nonce/record Nonce)
      (publisher/publish-inbound-payment-held config Payload)
      {:status 200
       :body {:Nonce Nonce}})))

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
          {:keys [Payload Nonce]} body
          {:keys [RequestId AccountHolderName AccountDetails]} Payload
          {:keys [SortCode AccountNumber]} AccountDetails
          bban (str SortCode AccountNumber)
          config {:record-db record-db
                  :record-store record-store}]
      (log/info "inbound-cop-request-received webhook"
                {:request-id RequestId
                 :bban bban
                 :name AccountHolderName})
      (nonce/record Nonce)
      (let [account (cash-accounts/get-account-by-bban config bban)]
        (if (or (nil? account) (error/anomaly? account))
          {:status 200
           :body {:matchResult "NoMatch"
                  :reasonCode "ACNS"
                  :reason "Account not found"}}
          (let [{:keys [display-name]}
                (let [party (parties/get-party config
                                               (:organization-id account)
                                               (:party-id account))]
                  (when-not (error/anomaly? party) party))
                result (if display-name
                         (parties/match-name display-name
                                             AccountHolderName)
                         :no-match)]
            {:status 200
             :body (cop-result result display-name)}))))))
