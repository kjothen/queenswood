(ns com.repldriven.mono.bank-clearbank-adapter.commands
  (:require
    [com.repldriven.mono.bank-clearbank-adapter.clearbank
     :as clearbank]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (do (log/warnf "No schema found for command: %s"
                     command)
          nil)
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case command
          "submit-payment"
          (clearbank/submit-payment config data)
          (do (log/warnf "Unknown command: %s" command)
              nil))))))

(defrecord ClearBankCommandProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
