(ns com.repldriven.mono.accounts.core
  (:require
    [com.repldriven.mono.accounts.commands.account-lifecycle :as
     account-lifecycle]
    [com.repldriven.mono.accounts.commands.reporting-operations :as reporting]

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
          "open-account" (account-lifecycle/open config data)
          "close-account" (account-lifecycle/close config data)
          "get-account-status" (reporting/get-account-status config data)
          {:status "REJECTED" :message (str "Unknown command: " command)})))))

(defrecord AccountProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
