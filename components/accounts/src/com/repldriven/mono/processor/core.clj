(ns com.repldriven.mono.processor.core
  (:require
    [com.repldriven.mono.processor.commands.account-lifecycle :as
     account-lifecycle]
    [com.repldriven.mono.processor.commands.reporting-operations :as
     reporting]

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
  "Wrap a handler result into a response map with
  record_id for the command response."
  [result]
  (if (error/anomaly? result) result {"record_id" (:account_id result)}))

(defn process
  "Process an account command and return result or anomaly.

  Config structure:
  {:datasource ...
   :schemas    {command-name -> lancaster-schema}}

  Command envelope structure:
  {\"id\" \"cmd-123\"
   \"command\" \"command-name\"
   \"payload\" <avro-bytes>
   \"correlation_id\" \"corr-456\"
   \"causation_id\" \"cause-789\"
   \"traceparent\" \"00-trace-span-01\"
   \"tracestate\" \"vendor=value\"}

  Returns {\"record_id\" ...} on success, or anomaly."
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
