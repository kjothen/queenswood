(ns com.repldriven.mono.bank-payment.core
  (:require
    [com.repldriven.mono.bank-payment.commands :as commands]
    [com.repldriven.mono.bank-payment.events :as events]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.processor.interface :as processor]))

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
          (commands/submit-internal config data)
          (error/reject :payment/unknown-command
                        (str "Unknown command: "
                             command)))))))

(defrecord PaymentProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))

(defn- dispatch-event
  [config message]
  (let [{:keys [event payload]} message
        {:keys [schemas]} config
        schema (get schemas event)]
    (if-not schema
      (do (log/warnf "No schema found for event: %s" event)
          nil)
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case event
          "transaction-settled"
          (events/settle-inbound config data)
          (do (log/warnf "Unknown event: %s" event)
              nil))))))

(defrecord PaymentEventProcessor [config]
  processor/Processor
    (process [_ message] (dispatch-event config message)))
