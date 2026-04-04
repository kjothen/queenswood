(ns com.repldriven.mono.bank-clearbank-webhook.handlers
  (:require
    [com.repldriven.mono.bank-clearbank-webhook.nonce :as nonce]
    [com.repldriven.mono.bank-clearbank-webhook.publisher :as publisher]

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
      (log/info "inbound-held-transaction webhook received" {:payload Payload})
      (nonce/record Nonce)
      (publisher/publish-inbound-payment-held config Payload)
      {:status 200
       :body {:Nonce Nonce}})))
