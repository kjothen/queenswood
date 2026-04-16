(ns com.repldriven.mono.bank-transaction.commands
  (:require
    [com.repldriven.mono.bank-transaction.core :as core]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- ->response
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      (let-nom> [payload (avro/serialize (schemas "transaction") result)]
        {:status "ACCEPTED" :payload payload}))))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (error/fail :transaction/process-command
                  {:message "No schema found for command"
                   :command command})
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case command
          "record-transaction"
          (->response config (core/record-transaction config data))
          (error/reject
           :transaction/unknown-command
           (str "Unknown command: " command)))))))

(defrecord TransactionProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
