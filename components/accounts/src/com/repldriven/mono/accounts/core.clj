(ns com.repldriven.mono.accounts.core
  (:require
    [com.repldriven.mono.accounts.commands :as commands]

    [com.repldriven.mono.processor.interface :as processor]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (error/fail :accounts/process-command
                  {:message "No schema found for command" :command command})
      (error/let-nom> [data (avro/deserialize-same schema payload)]
        (case command
          "open-account" (commands/open config data)
          "close-account" (commands/close config data)
          "get-account" (commands/get config data)
          (error/reject :accounts/unknown-command
                        (str "Unknown command: " command)))))))

(defrecord AccountProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
