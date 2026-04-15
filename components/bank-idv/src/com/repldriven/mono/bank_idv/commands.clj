(ns com.repldriven.mono.bank-idv.commands
  (:require
    [com.repldriven.mono.bank-idv.core :as core]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (error/fail :idv/process-command
                  {:message "No schema found for command" :command command})
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case command
          "initiate-idv" (core/initiate config data)
          "get-idv" (core/get config data)
          (error/reject :idv/unknown-command
                        (str "Unknown command: " command)))))))

(defrecord IdvProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
