(ns com.repldriven.mono.bank-payment.commands
  (:require
    [com.repldriven.mono.bank-payment.core :as core]
    [com.repldriven.mono.bank-payment.events :as events]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- ->response
  [config schema-name result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      (let-nom> [payload (avro/serialize (schemas schema-name) result)]
        {:status "ACCEPTED" :payload payload}))))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (error/fail :payment/process-command
                  {:message "No schema found for command"
                   :command command})
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case command
          "submit-internal-payment"
          (->response config
                      "internal-payment"
                      (core/submit-internal config data))

          "submit-outbound-payment"
          (->response config
                      "outbound-payment"
                      (core/submit-outbound config data))

          (error/reject :payment/unknown-command
                        (str "Unknown command: " command)))))))

(defrecord PaymentProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))

(defn- dispatch-event
  [config message]
  (let [{:keys [event payload]} message
        {:keys [schemas]} config
        schema (get schemas event)]
    (if-not schema
      (error/fail :payment/schema-not-found
                  {:message "Event schema not found"
                   :event event})
      (let-nom> [data (avro/deserialize-same schema payload)
                 {:keys [debit-credit-code]} data]
        (case event
          "transaction-settled"
          (case debit-credit-code
            :debit-credit-code-credit
            (events/settle-inbound config data)

            :debit-credit-code-debit
            (events/settle-outbound config data)

            (error/fail :payment/unknown-debit-credit-code
                        {:message "Unknown debit-credit-code"
                         :debit-credit-code debit-credit-code}))

          (error/fail :payment/unknown-event
                      {:message "Unknown event"
                       :event event}))))))

(defrecord PaymentEventProcessor [config]
  processor/Processor
    (process [_ message] (dispatch-event config message)))
