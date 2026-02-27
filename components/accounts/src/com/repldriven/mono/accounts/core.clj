(ns com.repldriven.mono.accounts.core
  (:require
    [com.repldriven.mono.accounts.commands.account-lifecycle :as
     account-lifecycle]
    [com.repldriven.mono.accounts.commands.reporting-operations :as reporting]

    [com.repldriven.mono.processor.interface :as processor]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.spec.interface :as spec]

    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(def ^:private specs
  (-> "schemas/accounts/accounts.edn"
      io/resource
      slurp
      edn/read-string))

(defn- ->result
  [result]
  (if (error/anomaly? result) result {"record_id" (:account_id result)}))

(defn- dispatch
  [config {:strs [command payload]}]
  (if-not command
    (error/fail :accounts/process-command "Missing command")
    (let [{:keys [schemas]} config
          avro-schema (get schemas command)]
      (if-not avro-schema
        (error/fail :accounts/process-command
                    {:message "No Avro schema for command" :command command})
        (error/let-nom> [data (avro/deserialize-same avro-schema payload)
                         malli-schema (get specs command)
                         _ (when (and malli-schema
                                      (not (spec/validate malli-schema data)))
                             (error/fail :accounts/process-command
                                         {:message "Invalid command data"
                                          :command command
                                          :details (spec/humanize (spec/explain
                                                                   malli-schema
                                                                   data))}))]
          (->result
           (case command
             "open-account" (account-lifecycle/open config data)
             "close-account" (account-lifecycle/close config data)
             "reopen-account" (account-lifecycle/reopen config data)
             "suspend-account" (account-lifecycle/suspend config data)
             "unsuspend-account" (account-lifecycle/unsuspend config data)
             "archive-account" (account-lifecycle/archive config data)
             "get-account-status" (reporting/get-account-status config data)
             (error/fail :accounts/process-command
                         {:message "Unknown command" :command command}))))))))

(defrecord AccountProcessor [config]
  processor/Processor
    (process [_ command] (dispatch config command)))
